package com.hsmty.cmr.domain.strategy.service;

import com.hsmty.cmr.common.api.PageQuery;
import com.hsmty.cmr.common.api.PageResult;
import com.hsmty.cmr.common.exception.BizException;
import com.hsmty.cmr.common.id.BizNoGenerator;
import com.hsmty.cmr.config.CmrProperties;
import com.hsmty.cmr.common.id.SnowflakeIdWorker;
import com.hsmty.cmr.common.util.JsonUtils;
import com.hsmty.cmr.domain.dataset.entity.CmrIndex;
import com.hsmty.cmr.domain.dataset.mapper.IndexMapper;
import com.hsmty.cmr.domain.strategy.dto.StrategySaveDTO;
import com.hsmty.cmr.domain.strategy.entity.CmrStrategy;
import com.hsmty.cmr.domain.strategy.entity.CmrStrategyVersion;
import com.hsmty.cmr.domain.strategy.mapper.StrategyMapper;
import com.hsmty.cmr.domain.strategy.mapper.StrategyVersionMapper;
import com.hsmty.cmr.domain.strategy.vo.StrategyDetailVO;
import com.hsmty.cmr.domain.strategy.vo.StrategyVO;
import com.hsmty.cmr.domain.strategy.vo.StrategyVersionVO;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.hsmty.cmr.domain.dataset.entity.table.CmrIndexTableDef.CMR_INDEX;
import static com.hsmty.cmr.domain.strategy.entity.table.CmrStrategyTableDef.CMR_STRATEGY;
import static com.hsmty.cmr.domain.strategy.entity.table.CmrStrategyVersionTableDef.CMR_STRATEGY_VERSION;

/**
 * 策略服务：三段参数（权重/重排/topK）草稿-发布-回滚；发布后下一次检索即生效（数据流 3.4）
 *
 * <p>同一场景同时只有一条生效策略（发布时同场景其它策略转草稿）。
 *
 * @author cmr
 * @since 2026-11-03
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StrategyService {

    /** 版本历史只保留最近 10 个已发布版本 */
    private static final int VERSION_KEEP = 10;

    private static final java.math.BigDecimal DEFAULT_THRESHOLD = new java.math.BigDecimal("0.60");

    private final StrategyMapper strategyMapper;
    private final StrategyVersionMapper versionMapper;
    private final IndexMapper indexMapper;
    private final SnowflakeIdWorker idWorker;
    private final BizNoGenerator bizNoGenerator;
    private final CmrProperties properties;

    public PageResult<StrategyVO> page(PageQuery query, String scene, String status) {
        QueryWrapper qw = QueryWrapper.create().from(CMR_STRATEGY);
        if (notBlank(scene)) {
            qw.where(CMR_STRATEGY.SCENE.eq(scene));
        }
        if (notBlank(status)) {
            qw.where(CMR_STRATEGY.STATUS.eq(status));
        }
        qw.orderBy(CMR_STRATEGY.ID.desc());
        Page<CmrStrategy> page = strategyMapper.paginate(
                Math.max(query.getPageNum(), 1), Math.max(query.getPageSize(), 1), qw);
        return PageResult.of(page.getRecords().stream().map(this::toVO).toList(),
                page.getTotalRow(), query.getPageNum(), query.getPageSize());
    }

    /** 向量模型选项：白名单优先；未配置时回退展示当前索引模型 */
    public Map<String, Object> modelOptions() {
        String activeModel = activeModelVersion();
        List<String> whitelist = properties.getModel().getWhitelist();
        List<String> options = whitelist == null ? List.of() : whitelist;
        if ((options.isEmpty()) && activeModel != null && !activeModel.isBlank()) {
            options = List.of(activeModel);
        }
        boolean whitelistEnabled = whitelist != null && !whitelist.isEmpty();
        boolean activeAllowed = activeModel != null && !activeModel.isBlank()
                && (options.isEmpty() || options.contains(activeModel));
        return Map.of(
                "models", options,
                "activeModel", activeModel == null ? "" : activeModel,
                "whitelistEnabled", whitelistEnabled,
                "activeAllowed", activeAllowed);
    }

    /** 策略一致性检查：5 项（权重/重排/阈值/topK/向量模型） */
    public Map<String, Object> consistency(Long id) {
        CmrStrategy strategy = requireStrategy(id);
        CmrStrategyVersion current = currentOf(strategy);
        CmrStrategyVersion draft = draftOf(id);
        Map<String, Object> model = modelOptions();
        boolean currentValid = current == null || weightsSumOf(current) == 100;
        boolean draftValid = draft == null || weightsSumOf(draft) == 100;
        boolean topKValid = (current == null || validTopK(current.getTopK()))
                && (draft == null || validTopK(draft.getTopK()));
        boolean thresholdValid = (current == null || validThreshold(current.getThreshold()))
                && (draft == null || validThreshold(draft.getThreshold()));
        boolean rerankValid = current == null || current.getRerank() != null;
        boolean modelAllowed = Boolean.TRUE.equals(model.get("activeAllowed"));

        java.util.List<Map<String, Object>> checks = new java.util.ArrayList<>();
        checks.add(check("weights", "权重合计 100", currentValid && draftValid));
        checks.add(check("rerank", "重排参数已定义", rerankValid));
        checks.add(check("threshold", "阈值在 0.30-0.99", thresholdValid));
        checks.add(check("topK", "返回条数在 1-100", topKValid));
        checks.add(check("model", "向量模型与激活索引一致", modelAllowed));
        return Map.of(
                "passed", currentValid && draftValid && rerankValid && thresholdValid && topKValid && modelAllowed,
                "checks", checks,
                "model", model,
                "currentValid", currentValid,
                "draftValid", draftValid,
                "topKValid", topKValid,
                "thresholdValid", thresholdValid,
                "currentVersion", strategy.getCurrentVersion() == null ? 0 : strategy.getCurrentVersion(),
                "hasDraft", draft != null);
    }

    private Map<String, Object> check(String key, String label, boolean passed) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("key", key);
        item.put("label", label);
        item.put("passed", passed);
        return item;
    }

    /** 新建：存草稿（version_no=0 占位，发布时生成 v1） */
    @Transactional(rollbackFor = Exception.class)
    public StrategyVO create(StrategySaveDTO dto) {
        validateWeights(dto.getWeights());
        validateThreshold(dto.getThreshold());
        Long exists = strategyMapper.selectCountByQuery(QueryWrapper.create()
                .where(CMR_STRATEGY.NAME.eq(dto.getName())));
        BizException.assertTrue(exists != null && exists > 0, "策略名称已存在");

        CmrStrategy strategy = new CmrStrategy();
        strategy.setId(idWorker.next());
        strategy.setBizNo(bizNoGenerator.next(BizNoGenerator.PREFIX_STRATEGY));
        strategy.setName(dto.getName());
        strategy.setScene(dto.getScene());
        strategy.setStatus(CmrStrategy.STATUS_DRAFT);
        strategy.setPreset(false);
        strategy.setCurrentVersion(0);
        strategy.setRemark(dto.getRemark());
        strategy.setDelFlag("F");
        strategy.setCreateTime(LocalDateTime.now());
        strategyMapper.insert(strategy, true);
        saveDraft(strategy, dto);
        return toVO(strategy);
    }

    /** 编辑草稿参数 */
    @Transactional(rollbackFor = Exception.class)
    public void updateDraft(Long id, StrategySaveDTO dto) {
        CmrStrategy strategy = requireStrategy(id);
        BizException.assertTrue(CmrStrategy.STATUS_OFFLINE.equals(strategy.getStatus()),
                "已下线策略不可编辑，请先重新上线");
        validateWeights(dto.getWeights());
        validateThreshold(dto.getThreshold());
        if (!strategy.getName().equals(dto.getName())) {
            Long exists = strategyMapper.selectCountByQuery(QueryWrapper.create()
                    .where(CMR_STRATEGY.NAME.eq(dto.getName()).and(CMR_STRATEGY.ID.ne(id))));
            BizException.assertTrue(exists != null && exists > 0, "策略名称已存在");
            strategy.setName(dto.getName());
        }
        strategy.setRemark(dto.getRemark());
        strategyMapper.update(strategy);
        saveDraft(strategy, dto);
    }

    /** 放弃草稿（回到生效版本态） */
    @Transactional(rollbackFor = Exception.class)
    public void discardDraft(Long id) {
        CmrStrategy strategy = requireStrategy(id);
        CmrStrategyVersion draft = draftOf(strategy.getId());
        BizException.assertNull(draft, "没有待发布的草稿");
        versionMapper.deleteById(draft.getId());
        if (strategy.getCurrentVersion() != null && strategy.getCurrentVersion() > 0) {
            strategy.setStatus(CmrStrategy.STATUS_ACTIVE);
        } else {
            strategy.setStatus(CmrStrategy.STATUS_DRAFT);
        }
        strategyMapper.update(strategy);
    }

    /** 发布：版本递增、立即生效；同场景其它策略转草稿 */
    @Transactional(rollbackFor = Exception.class)
    public StrategyVersionVO publish(Long id) {
        CmrStrategy strategy = requireStrategy(id);
        BizException.assertTrue(CmrStrategy.STATUS_OFFLINE.equals(strategy.getStatus()),
                "已下线策略不可发布，请先重新上线");
        CmrStrategyVersion draft = draftOf(id);
        BizException.assertNull(draft, "没有待发布的草稿，请先编辑保存");

        int nextVersion = (strategy.getCurrentVersion() == null ? 0 : strategy.getCurrentVersion()) + 1;
        draft.setVersionNo(nextVersion);
        draft.setStatus(CmrStrategyVersion.STATUS_PUBLISHED);
        draft.setSource("new");
        draft.setPublishedTime(LocalDateTime.now());
        versionMapper.update(draft);

        strategy.setStatus(CmrStrategy.STATUS_ACTIVE);
        strategy.setCurrentVersion(nextVersion);
        strategyMapper.update(strategy);

        // 同场景同时只有一条生效策略
        List<CmrStrategy> sameScene = strategyMapper.selectListByQuery(QueryWrapper.create()
                .where(CMR_STRATEGY.SCENE.eq(strategy.getScene()).and(CMR_STRATEGY.ID.ne(id))
                        .and(CMR_STRATEGY.STATUS.eq(CmrStrategy.STATUS_ACTIVE))));
        for (CmrStrategy other : sameScene) {
            other.setStatus(CmrStrategy.STATUS_DRAFT);
            strategyMapper.update(other);
        }
        pruneVersions(id);
        log.info("策略发布生效: {} v{} scene={}", strategy.getBizNo(), nextVersion, strategy.getScene());
        return toVersionVO(versionMapper.selectOneById(draft.getId()));
    }

    /** 已发布版本只保留最近 10 个，超出的最旧版本删除 */
    private void pruneVersions(Long strategyId) {
        List<CmrStrategyVersion> published = versionMapper.selectListByQuery(QueryWrapper.create()
                .where(CMR_STRATEGY_VERSION.STRATEGY_ID.eq(strategyId)
                        .and(CMR_STRATEGY_VERSION.STATUS.ne(CmrStrategyVersion.STATUS_DRAFT)))
                .orderBy(CMR_STRATEGY_VERSION.VERSION_NO.desc()));
        for (int i = VERSION_KEEP; i < published.size(); i++) {
            versionMapper.deleteById(published.get(i).getId());
        }
    }

    /** 下线：不再参与日常检索；系统预置策略不可下线 */
    @Transactional(rollbackFor = Exception.class)
    public void offline(Long id, String reason) {
        CmrStrategy strategy = requireStrategy(id);
        BizException.assertTrue(Boolean.TRUE.equals(strategy.getPreset()), "系统预置策略不可下线");
        BizException.assertTrue(CmrStrategy.STATUS_OFFLINE.equals(strategy.getStatus()), "策略已下线");
        BizException.assertTrue(!notBlank(reason), "下线原因不能为空");
        strategy.setStatus(CmrStrategy.STATUS_OFFLINE);
        strategy.setOfflineReason(reason);
        strategy.setOfflineTime(LocalDateTime.now());
        strategyMapper.update(strategy);
        log.info("策略下线: {} reason={}", strategy.getBizNo(), reason);
    }

    /** 重新上线：恢复为草稿态，需重新发布后才生效 */
    @Transactional(rollbackFor = Exception.class)
    public void online(Long id) {
        CmrStrategy strategy = requireStrategy(id);
        BizException.assertTrue(!CmrStrategy.STATUS_OFFLINE.equals(strategy.getStatus()), "策略未下线");
        strategy.setStatus(CmrStrategy.STATUS_DRAFT);
        strategy.setOfflineReason(null);
        strategy.setOfflineTime(null);
        strategyMapper.update(strategy);
        log.info("策略重新上线（待发布）: {}", strategy.getBizNo());
    }

    /** 检索台策略下拉：生效策略 + 可预览参数的选项 */
    public List<Map<String, Object>> options() {
        List<CmrStrategy> actives = strategyMapper.selectListByQuery(QueryWrapper.create()
                .where(CMR_STRATEGY.STATUS.eq(CmrStrategy.STATUS_ACTIVE))
                .orderBy(CMR_STRATEGY.ID.asc()));
        return actives.stream().map(s -> {
            CmrStrategyVersion v = currentOf(s);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", s.getId());
            item.put("bizNo", s.getBizNo());
            item.put("name", s.getName());
            item.put("scene", s.getScene());
            item.put("preset", Boolean.TRUE.equals(s.getPreset()));
            item.put("versionNo", s.getCurrentVersion());
            item.put("threshold", v == null || v.getThreshold() == null ? DEFAULT_THRESHOLD : v.getThreshold());
            item.put("rerank", v != null && Boolean.TRUE.equals(v.getRerank()));
            item.put("topK", v == null || v.getTopK() == null ? 20 : v.getTopK());
            item.put("paramSummary", paramSummary(v));
            return item;
        }).toList();
    }

    /** 回滚到所选版本：配置/状态/版本号一起回写，产生 source=rollback 的新版本记录 */
    @Transactional(rollbackFor = Exception.class)
    public StrategyVersionVO rollback(Long id, Integer targetVersionNo) {
        CmrStrategy strategy = requireStrategy(id);
        CmrStrategyVersion target = versionMapper.selectOneByQuery(QueryWrapper.create()
                .where(CMR_STRATEGY_VERSION.STRATEGY_ID.eq(id)
                        .and(CMR_STRATEGY_VERSION.VERSION_NO.eq(targetVersionNo))));
        BizException.assertNull(target, "目标版本不存在");

        int nextVersion = (strategy.getCurrentVersion() == null ? 0 : strategy.getCurrentVersion()) + 1;
        CmrStrategyVersion rollbackVersion = new CmrStrategyVersion();
        rollbackVersion.setId(idWorker.next());
        rollbackVersion.setStrategyId(id);
        rollbackVersion.setVersionNo(nextVersion);
        rollbackVersion.setWeightsJson(target.getWeightsJson());
        rollbackVersion.setRerank(target.getRerank());
        rollbackVersion.setTopK(target.getTopK());
        rollbackVersion.setThreshold(target.getThreshold());
        rollbackVersion.setStatus(CmrStrategyVersion.STATUS_PUBLISHED);
        rollbackVersion.setSource("rollback");
        rollbackVersion.setPublishedTime(LocalDateTime.now());
        rollbackVersion.setDelFlag("F");
        rollbackVersion.setCreateTime(LocalDateTime.now());
        versionMapper.insert(rollbackVersion, true);

        // 丢弃未发布草稿
        CmrStrategyVersion draft = draftOf(id);
        if (draft != null) {
            versionMapper.deleteById(draft.getId());
        }

        strategy.setStatus(CmrStrategy.STATUS_ACTIVE);
        strategy.setCurrentVersion(nextVersion);
        strategyMapper.update(strategy);

        List<CmrStrategy> sameScene = strategyMapper.selectListByQuery(QueryWrapper.create()
                .where(CMR_STRATEGY.SCENE.eq(strategy.getScene()).and(CMR_STRATEGY.ID.ne(id))
                        .and(CMR_STRATEGY.STATUS.eq(CmrStrategy.STATUS_ACTIVE))));
        for (CmrStrategy other : sameScene) {
            other.setStatus(CmrStrategy.STATUS_DRAFT);
            strategyMapper.update(other);
        }
        pruneVersions(id);
        log.info("策略回滚: {} v{} -> v{}", strategy.getBizNo(), targetVersionNo, nextVersion);
        return toVersionVO(rollbackVersion);
    }

    public StrategyDetailVO detail(Long id) {
        CmrStrategy strategy = requireStrategy(id);
        CmrStrategyVersion current = null;
        if (strategy.getCurrentVersion() != null && strategy.getCurrentVersion() > 0) {
            current = versionMapper.selectOneByQuery(QueryWrapper.create()
                    .where(CMR_STRATEGY_VERSION.STRATEGY_ID.eq(id)
                            .and(CMR_STRATEGY_VERSION.VERSION_NO.eq(strategy.getCurrentVersion()))));
        }
        List<StrategyVersionVO> versions = versionsOf(id);
        return StrategyDetailVO.builder()
                .strategy(toVO(strategy))
                .current(toVersionVO(current))
                .draft(toVersionVO(draftOf(id)))
                .modelVersion(activeModelVersion())
                .versions(versions)
                .build();
    }

    public List<StrategyVersionVO> versionsOf(Long strategyId) {
        return versionMapper.selectListByQuery(QueryWrapper.create()
                        .where(CMR_STRATEGY_VERSION.STRATEGY_ID.eq(strategyId)
                                .and(CMR_STRATEGY_VERSION.STATUS.ne(CmrStrategyVersion.STATUS_DRAFT)))
                        .orderBy(CMR_STRATEGY_VERSION.VERSION_NO.desc()))
                .stream().map(this::toVersionVO).toList();
    }

    /** 两版本参数 diff（变更评估） */
    public Map<String, Object> diff(Long id, Integer from, Integer to) {
        CmrStrategyVersion v1 = requireVersion(id, from);
        CmrStrategyVersion v2 = requireVersion(id, to);
        Map<String, Object> weights = new LinkedHashMap<>();
        Map<String, Integer> w1 = readWeights(v1);
        Map<String, Integer> w2 = readWeights(v2);
        for (String key : List.of("text", "image", "audio", "video")) {
            weights.put(key, Map.of("from", w1.getOrDefault(key, 0), "to", w2.getOrDefault(key, 0)));
        }
        return Map.of(
                "from", from, "to", to,
                "weights", weights,
                "rerank", Map.of("from", v1.getRerank(), "to", v2.getRerank()),
                "topK", Map.of("from", v1.getTopK(), "to", v2.getTopK()),
                "threshold", Map.of("from", thresholdOf(v1), "to", thresholdOf(v2)));
    }

    /** 场景生效策略（检索台回显/检索链取参）：先精确场景再 all 兜底 */
    public EffectiveStrategy effectiveOf(String scene) {
        CmrStrategy match = null;
        if (notBlank(scene)) {
            match = strategyMapper.selectOneByQuery(QueryWrapper.create()
                    .where(CMR_STRATEGY.SCENE.eq(scene).and(CMR_STRATEGY.STATUS.eq(CmrStrategy.STATUS_ACTIVE)))
                    .limit(1));
        }
        if (match == null) {
            match = strategyMapper.selectOneByQuery(QueryWrapper.create()
                    .where(CMR_STRATEGY.SCENE.eq("all").and(CMR_STRATEGY.STATUS.eq(CmrStrategy.STATUS_ACTIVE)))
                    .limit(1));
        }
        return effectiveOfEntity(match);
    }

    /** 检索台显式选定策略：必须为生效态 */
    public EffectiveStrategy effectiveById(Long strategyId) {
        CmrStrategy strategy = requireStrategy(strategyId);
        BizException.assertTrue(!CmrStrategy.STATUS_ACTIVE.equals(strategy.getStatus()),
                "策略未生效，不可用于检索");
        return effectiveOfEntity(strategy);
    }

    private EffectiveStrategy effectiveOfEntity(CmrStrategy match) {
        if (match == null || match.getCurrentVersion() == null || match.getCurrentVersion() <= 0) {
            return new EffectiveStrategy(null, null, null, null, defaultWeights(), false, 20, DEFAULT_THRESHOLD);
        }
        CmrStrategyVersion v = currentOf(match);
        if (v == null) {
            return new EffectiveStrategy(match.getId(), null, match.getBizNo(), match.getCurrentVersion(),
                    defaultWeights(), false, 20, DEFAULT_THRESHOLD);
        }
        return new EffectiveStrategy(match.getId(), v.getId(), match.getBizNo(), match.getCurrentVersion(),
                readWeights(v), Boolean.TRUE.equals(v.getRerank()),
                v.getTopK() == null ? 20 : v.getTopK(), thresholdOf(v));
    }

    /** 生效策略参数（weights/rerank/topK/threshold + 策略编号与版本引用） */
    public record EffectiveStrategy(Long strategyId, Long versionId, String strategyNo, Integer versionNo,
                                    Map<String, Integer> weights, boolean rerank, int topK,
                                    java.math.BigDecimal threshold) {
    }

    private void saveDraft(CmrStrategy strategy, StrategySaveDTO dto) {
        CmrStrategyVersion draft = draftOf(strategy.getId());
        if (draft == null) {
            draft = new CmrStrategyVersion();
            draft.setId(idWorker.next());
            draft.setStrategyId(strategy.getId());
            draft.setVersionNo(0);
            draft.setStatus(CmrStrategyVersion.STATUS_DRAFT);
            draft.setDelFlag("F");
            draft.setCreateTime(LocalDateTime.now());
        }
        draft.setWeightsJson(JsonUtils.write(dto.getWeights()));
        draft.setRerank(Boolean.TRUE.equals(dto.getRerank()));
        draft.setTopK(dto.getTopK() == null ? 20 : dto.getTopK());
        draft.setThreshold(dto.getThreshold() == null ? DEFAULT_THRESHOLD : dto.getThreshold());
        if (draft.getVersionNo() != null && draft.getVersionNo() == 0) {
            draft.setSource("new");
            versionMapper.insert(draft, true);
        } else {
            versionMapper.update(draft);
        }
    }

    private CmrStrategyVersion currentOf(CmrStrategy strategy) {
        if (strategy == null || strategy.getCurrentVersion() == null || strategy.getCurrentVersion() <= 0) {
            return null;
        }
        return versionMapper.selectOneByQuery(QueryWrapper.create()
                .where(CMR_STRATEGY_VERSION.STRATEGY_ID.eq(strategy.getId())
                        .and(CMR_STRATEGY_VERSION.VERSION_NO.eq(strategy.getCurrentVersion()))));
    }

    private int weightsSumOf(CmrStrategyVersion v) {
        return readWeights(v).values().stream().mapToInt(Integer::intValue).sum();
    }

    private boolean validTopK(Integer topK) {
        return topK != null && topK >= 1 && topK <= 100;
    }

    private boolean validThreshold(java.math.BigDecimal threshold) {
        return threshold != null
                && threshold.compareTo(new java.math.BigDecimal("0.30")) >= 0
                && threshold.compareTo(new java.math.BigDecimal("0.99")) <= 0;
    }

    private java.math.BigDecimal thresholdOf(CmrStrategyVersion v) {
        return v == null || v.getThreshold() == null ? DEFAULT_THRESHOLD : v.getThreshold();
    }

    private String paramSummary(CmrStrategyVersion v) {
        java.math.BigDecimal threshold = thresholdOf(v);
        boolean rerank = v != null && Boolean.TRUE.equals(v.getRerank());
        return "阈值 " + threshold.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()
                + " · 重排" + (rerank ? "开" : "关");
    }

    private CmrStrategyVersion draftOf(Long strategyId) {
        return versionMapper.selectOneByQuery(QueryWrapper.create()
                .where(CMR_STRATEGY_VERSION.STRATEGY_ID.eq(strategyId)
                        .and(CMR_STRATEGY_VERSION.STATUS.eq(CmrStrategyVersion.STATUS_DRAFT))));
    }

    private CmrStrategyVersion requireVersion(Long strategyId, Integer versionNo) {
        CmrStrategyVersion v = versionMapper.selectOneByQuery(QueryWrapper.create()
                .where(CMR_STRATEGY_VERSION.STRATEGY_ID.eq(strategyId)
                        .and(CMR_STRATEGY_VERSION.VERSION_NO.eq(versionNo))));
        BizException.assertNull(v, "版本不存在: v" + versionNo);
        return v;
    }

    private String activeModelVersion() {
        CmrIndex active = indexMapper.selectOneByQuery(QueryWrapper.create()
                .where(CMR_INDEX.STATUS.eq(CmrIndex.STATUS_ACTIVE))
                .orderBy(CMR_INDEX.ID.desc())
                .limit(1));
        return active == null ? null : active.getModelVersion();
    }

    private void validateThreshold(java.math.BigDecimal threshold) {
        if (threshold != null) {
            BizException.assertTrue(!validThreshold(threshold), "阈值必须在 0.30-0.99");
        }
    }

    private void validateWeights(Map<String, Integer> weights) {
        BizException.assertNull(weights, "权重不能为空");
        int sum = 0;
        for (String key : List.of("text", "image", "audio", "video")) {
            Integer v = weights.get(key);
            BizException.assertNull(v, "权重缺少模态: " + key);
            BizException.assertTrue(v < 0 || v > 100, "权重必须在 0-100: " + key);
            sum += v;
        }
        BizException.assertTrue(sum != 100, "四模态权重合计必须为 100，当前 " + sum);
    }

    private Map<String, Integer> readWeights(CmrStrategyVersion v) {
        if (v == null || v.getWeightsJson() == null) {
            return defaultWeights();
        }
        Map<String, Integer> weights = JsonUtils.read(v.getWeightsJson(),
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Integer>>() {
                });
        for (String key : List.of("text", "image", "audio", "video")) {
            weights.putIfAbsent(key, 0);
        }
        return weights;
    }

    private Map<String, Integer> defaultWeights() {
        return new LinkedHashMap<>(Map.of("text", 25, "image", 25, "audio", 25, "video", 25));
    }

    public CmrStrategy requireStrategy(Long id) {
        CmrStrategy strategy = strategyMapper.selectOneById(id);
        BizException.assertNull(strategy, "策略不存在");
        return strategy;
    }

    private StrategyVO toVO(CmrStrategy e) {
        if (e == null) {
            return null;
        }
        CmrStrategyVersion current = currentOf(e);
        return StrategyVO.builder()
                .id(e.getId()).bizNo(e.getBizNo()).name(e.getName()).scene(e.getScene())
                .status(e.getStatus()).preset(Boolean.TRUE.equals(e.getPreset()))
                .offlineReason(e.getOfflineReason()).offlineTime(e.getOfflineTime())
                .currentVersion(e.getCurrentVersion())
                .paramSummary(current == null ? null : paramSummary(current))
                .remark(e.getRemark())
                .createTime(e.getCreateTime()).updateTime(e.getUpdateTime())
                .build();
    }

    private StrategyVersionVO toVersionVO(CmrStrategyVersion e) {
        if (e == null) {
            return null;
        }
        return StrategyVersionVO.builder()
                .id(e.getId()).strategyId(e.getStrategyId()).versionNo(e.getVersionNo())
                .weightsJson(e.getWeightsJson()).rerank(e.getRerank()).topK(e.getTopK())
                .threshold(e.getThreshold())
                .status(e.getStatus()).source(e.getSource())
                .publishedTime(e.getPublishedTime()).createTime(e.getCreateTime())
                .build();
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
