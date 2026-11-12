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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
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



    private static final BigDecimal THRESHOLD_MIN = new BigDecimal("0.30");

    private static final BigDecimal THRESHOLD_MAX = new BigDecimal("0.99");

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

    /**
     * 全局一致性检查（P5）：固定 5 项，覆盖全部策略的当前生效版本与草稿。
     *
     * <p>返回 {@code {checks:[{key,label,ok,detail}], passed:boolean}}；
     * 与单策略的 {@link #consistency(Long)} 相互独立，后者结构保持不变。
     *
     * @return 全局一致性结果
     * @author cmr
     * @since 2026-11-11
     */
    public Map<String, Object> globalConsistency() {
        List<CmrStrategy> strategies = strategyMapper.selectListByQuery(
                QueryWrapper.create().orderBy(CMR_STRATEGY.ID.asc()));
        int checked = 0;
        int thresholdBad = 0;
        int sumBad = 0;
        int negativeBad = 0;
        int rerankBad = 0;
        for (CmrStrategy strategy : strategies) {
            List<CmrStrategyVersion> versions = new ArrayList<>(2);
            CmrStrategyVersion current = currentOf(strategy);
            if (current != null) {
                versions.add(current);
            }
            CmrStrategyVersion draft = draftOf(strategy.getId());
            if (draft != null) {
                versions.add(draft);
            }
            if (versions.isEmpty()) {
                continue;
            }
            checked++;
            boolean thresholdInvalid = false;
            boolean sumInvalid = false;
            boolean negativeInvalid = false;
            boolean rerankInvalid = false;
            for (CmrStrategyVersion version : versions) {
                if (!validThreshold(version.getThreshold())) {
                    thresholdInvalid = true;
                }
                if (version.getRerank() == null) {
                    rerankInvalid = true;
                }
                Map<String, Integer> weights = parseWeightsQuietly(version);
                if (weights == null || weights.isEmpty()) {
                    sumInvalid = true;
                    negativeInvalid = true;
                    continue;
                }
                int sum = 0;
                for (Integer value : weights.values()) {
                    if (value == null || value < 0) {
                        negativeInvalid = true;
                    }
                    sum += value == null ? 0 : value;
                }
                if (sum != 100) {
                    sumInvalid = true;
                }
            }
            thresholdBad += thresholdInvalid ? 1 : 0;
            sumBad += sumInvalid ? 1 : 0;
            negativeBad += negativeInvalid ? 1 : 0;
            rerankBad += rerankInvalid ? 1 : 0;
        }

        CmrStrategy defaultStrategy = defaultStrategyActive();
        boolean defaultOk = defaultStrategy != null;

        List<Map<String, Object>> checks = new ArrayList<>(5);
        checks.add(checkItem("threshold", "阈值区间 0.30–0.99", thresholdBad == 0,
                thresholdBad == 0 ? "已检查 " + checked + " 个策略，阈值均在区间内"
                        : thresholdBad + " 个策略阈值越界"));
        checks.add(checkItem("weightsSum", "权重合计 100%", sumBad == 0,
                sumBad == 0 ? "已检查 " + checked + " 个策略，四模态权重合计均为 100%"
                        : sumBad + " 个策略权重合计不为 100%"));
        checks.add(checkItem("weightsNegative", "权重非负", negativeBad == 0,
                negativeBad == 0 ? "已检查 " + checked + " 个策略，权重均非负"
                        : negativeBad + " 个策略存在负权重"));
        checks.add(checkItem("rerank", "重排开关有值", rerankBad == 0,
                rerankBad == 0 ? "已检查 " + checked + " 个策略，重排开关均有值"
                        : rerankBad + " 个策略重排开关缺失"));
        checks.add(checkItem("defaultStrategy", "默认策略存在且生效", defaultOk,
                defaultOk ? "默认策略生效中：" + defaultStrategy.getBizNo()
                        : "未找到场景 all 的生效预置策略"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("checks", checks);
        result.put("passed", thresholdBad == 0 && sumBad == 0 && negativeBad == 0
                && rerankBad == 0 && defaultOk);
        result.put("checkedCount", checked);
        return result;
    }

    private Map<String, Object> checkItem(String key, String label, boolean ok, String detail) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("key", key);
        item.put("label", label);
        item.put("ok", ok);
        item.put("detail", detail);
        return item;
    }

    /**
     * 新建策略：存草稿（version_no=0 占位，发布时生成首个版本）。
     *
     * <p>未传 threshold 时取默认 0.60；未传权重时取 50/30/10/10；未传 rerank 时取 true。
     *
     * @param dto 新建入参
     * @return 新建后的策略视图
     * @author cmr
     * @since 2026-11-11
     */
    @Transactional(rollbackFor = Exception.class)
    public StrategyVO create(StrategySaveDTO dto) {
        Map<String, Integer> weights = dto.getWeights() == null ? initialWeights() : dto.getWeights();
        validateWeights(weights);
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
        saveDraft(strategy, weights,
                dto.getRerank() == null ? Boolean.TRUE : dto.getRerank(),
                dto.getTopK(),
                dto.getThreshold() == null ? properties.getSearch().getThreshold() : dto.getThreshold());
        return toVO(strategy);
    }

    /**
     * 编辑草稿参数：已下线策略同样允许编辑（契约第 4 节）。
     *
     * <p>未传 threshold 时保留库中现值，绝不回落 0.60；未传权重、重排、条数时
     * 依次沿用草稿、当前生效版本的值。
     *
     * @param id  策略主键
     * @param dto 编辑入参
     * @author cmr
     * @since 2026-11-11
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateDraft(Long id, StrategySaveDTO dto) {
        CmrStrategy strategy = requireStrategy(id);
        CmrStrategyVersion draft = draftOf(id);
        CmrStrategyVersion current = currentOf(strategy);

        Map<String, Integer> weights = dto.getWeights();
        if (weights == null) {
            weights = draft != null ? readWeights(draft)
                    : current != null ? readWeights(current) : initialWeights();
        }
        validateWeights(weights);

        BigDecimal threshold = dto.getThreshold();
        if (threshold == null) {
            threshold = draft != null && draft.getThreshold() != null ? draft.getThreshold()
                    : current != null && current.getThreshold() != null ? current.getThreshold()
                    : properties.getSearch().getThreshold();
        }
        validateThreshold(threshold);

        Boolean rerank = dto.getRerank();
        if (rerank == null) {
            rerank = draft != null ? Boolean.TRUE.equals(draft.getRerank())
                    : current != null && Boolean.TRUE.equals(current.getRerank());
        }

        Integer topK = dto.getTopK();
        if (topK == null) {
            topK = draft != null && draft.getTopK() != null ? draft.getTopK()
                    : current != null && current.getTopK() != null ? current.getTopK() : 20;
        }

        if (!strategy.getName().equals(dto.getName())) {
            Long exists = strategyMapper.selectCountByQuery(QueryWrapper.create()
                    .where(CMR_STRATEGY.NAME.eq(dto.getName()).and(CMR_STRATEGY.ID.ne(id))));
            BizException.assertTrue(exists != null && exists > 0, "策略名称已存在");
            strategy.setName(dto.getName());
        }
        strategy.setRemark(dto.getRemark());
        strategyMapper.update(strategy);
        saveDraft(strategy, weights, rerank, topK, threshold);
    }

    /**
     * 放弃草稿：只丢弃待发布参数，不改策略当前状态。
     *
     * <p>数据流 3.5：已下线策略放弃草稿后仍是已下线，需上线才回检索台；从未发布过的草稿策略
     * 放弃后整条移除，避免留下空策略。
     *
     * @param id 策略主键
     * @author cmr
     * @since 2026-11-11
     */
    @Transactional(rollbackFor = Exception.class)
    public void discardDraft(Long id) {
        CmrStrategy strategy = requireStrategy(id);
        CmrStrategyVersion draft = draftOf(strategy.getId());
        BizException.assertNull(draft, "没有待发布的草稿");
        versionMapper.deleteById(draft.getId());
        if (strategy.getCurrentVersion() == null || strategy.getCurrentVersion() <= 0) {
            // 从未发布过的草稿策略：放弃草稿即整条删除
            strategyMapper.deleteById(id);
        }
    }

    /**
     * 发布：沿用草稿版本号（有草稿则提升草稿版本，不无脑 +1）、立即生效；同场景其它策略转草稿。
     *
     * <p>已下线策略同样允许发布（契约第 4 节）。发布后仅保留最近 10 个版本，超出的物理清理。
     *
     * @param id 策略主键
     * @return 发布后的版本视图
     * @author cmr
     * @since 2026-11-11
     */
    @Transactional(rollbackFor = Exception.class)
    public StrategyVersionVO publish(Long id) {
        CmrStrategy strategy = requireStrategy(id);
        CmrStrategyVersion draft = draftOf(id);
        BizException.assertNull(draft, "没有待发布的草稿，请先编辑保存");

        int currentVersion = strategy.getCurrentVersion() == null ? 0 : strategy.getCurrentVersion();
        // 草稿若已携带更高版本号则直接提升沿用，避免重复 +1 造成版本空洞
        int nextVersion = draft.getVersionNo() != null && draft.getVersionNo() > currentVersion
                ? draft.getVersionNo() : currentVersion + 1;
        // 版本号唯一性兜底：仅在极端并发/历史数据异常时顺延，正常路径不会触发
        while (versionNumberTaken(id, nextVersion, draft.getId())) {
            nextVersion++;
        }
        draft.setVersionNo(nextVersion);
        draft.setName(strategy.getName());
        draft.setStatus(CmrStrategyVersion.STATUS_PUBLISHED);
        draft.setSource("new");
        draft.setPublishedTime(LocalDateTime.now());
        versionMapper.update(draft);

        boolean staysOffline = CmrStrategy.STATUS_OFFLINE.equals(strategy.getStatus());
        strategy.setCurrentVersion(nextVersion);
        if (!staysOffline) {
            strategy.setStatus(CmrStrategy.STATUS_ACTIVE);
            clearOfflineTrace(strategy);
        }
        // 全字段更新：由已下线转生效时需清空下线原因/备注/时间
        strategyMapper.update(strategy, false);
        demoteSameSceneOthers(strategy);
        pruneVersions(id);
        log.info("策略发布生效: {} v{} scene={}", strategy.getBizNo(), nextVersion, strategy.getScene());
        return toVersionVO(versionMapper.selectOneById(draft.getId()));
    }

    /**
     * 已发布版本只保留最近 10 个，超出的最旧版本物理清理。
     *
     * <p>契约第 4 节允许版本表物理删除；业务表不受影响，故此处绕过逻辑删除直接 DELETE。
     *
     * @param strategyId 策略主键
     * @author cmr
     * @since 2026-11-11
     */
    private void pruneVersions(Long strategyId) {
        List<CmrStrategyVersion> published = versionMapper.selectListByQuery(QueryWrapper.create()
                .where(CMR_STRATEGY_VERSION.STRATEGY_ID.eq(strategyId)
                        .and(CMR_STRATEGY_VERSION.STATUS.ne(CmrStrategyVersion.STATUS_DRAFT)))
                .orderBy(CMR_STRATEGY_VERSION.VERSION_NO.desc()));
        for (int i = VERSION_KEEP; i < published.size(); i++) {
            versionMapper.physicalDeleteById(published.get(i).getId());
        }
    }

    /** 下线：不再参与日常检索；系统预置策略不可下线 */
    @Transactional(rollbackFor = Exception.class)
    public void offline(Long id, String reason, String remark) {
        CmrStrategy strategy = requireStrategy(id);
        BizException.assertTrue(Boolean.TRUE.equals(strategy.getPreset()), "系统预置策略不可下线");
        BizException.assertTrue(CmrStrategy.STATUS_OFFLINE.equals(strategy.getStatus()), "策略已下线");
        BizException.assertTrue(!notBlank(reason), "下线原因不能为空");
        strategy.setStatus(CmrStrategy.STATUS_OFFLINE);
        strategy.setOfflineReason(reason);
        strategy.setOfflineRemark(notBlank(remark) ? remark.trim() : null);
        strategy.setOfflineTime(LocalDateTime.now());
        // 全字段更新：备注留空时需清掉上一次下线备注
        strategyMapper.update(strategy, false);
        log.info("策略下线: {} reason={} remark={}", strategy.getBizNo(), reason, remark);
    }

    /**
     * 重新上线：直接置为生效（active），不再回落草稿（契约第 4 节）。
     *
     * <p>仅对已有生效版本的策略成立；尚无生效版本的策略需先编辑并发布。
     *
     * @param id 策略主键
     * @author cmr
     * @since 2026-11-11
     */
    @Transactional(rollbackFor = Exception.class)
    public void online(Long id) {
        CmrStrategy strategy = requireStrategy(id);
        BizException.assertTrue(!CmrStrategy.STATUS_OFFLINE.equals(strategy.getStatus()), "策略未下线");
        BizException.assertTrue(strategy.getCurrentVersion() == null || strategy.getCurrentVersion() <= 0,
                "策略尚无生效版本，请编辑并发布后再上线");
        strategy.setStatus(CmrStrategy.STATUS_ACTIVE);
        clearOfflineTrace(strategy);
        // 全字段更新：上线时需真正清空下线原因/备注/时间
        strategyMapper.update(strategy, false);
        demoteSameSceneOthers(strategy);
        log.info("策略重新上线（直接生效）: {} v{}", strategy.getBizNo(), strategy.getCurrentVersion());
    }

    /**
     * 对比策略下拉：包含全部状态（草稿 / 生效 / 已下线）的策略，供 P5 版本对比选择任意对象。
     *
     * @return 策略选项列表（id / 名称 / 状态 / 可用版本号）
     * @author cmr
     * @since 2026-11-11
     */
    public List<Map<String, Object>> compareOptions() {
        List<CmrStrategy> strategies = strategyMapper.selectListByQuery(QueryWrapper.create()
                .orderBy(CMR_STRATEGY.ID.asc()));
        return strategies.stream().map(s -> {
            CmrStrategyVersion v = effectiveVersionOf(s);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", s.getId());
            item.put("bizNo", s.getBizNo());
            item.put("name", s.getName());
            item.put("scene", s.getScene());
            item.put("status", s.getStatus());
            item.put("versionNo", v == null ? null : v.getVersionNo());
            return item;
        }).toList();
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
            item.put("threshold", v == null || v.getThreshold() == null
                    ? properties.getSearch().getThreshold() : v.getThreshold());
            item.put("rerank", v != null && Boolean.TRUE.equals(v.getRerank()));
            item.put("topK", v == null || v.getTopK() == null ? properties.getSearch().getTopK() : v.getTopK());
            item.put("paramSummary", paramSummary(v));
            return item;
        }).toList();
    }

    /**
     * 回滚到所选版本：配置/状态/版本号一起回写，产生 source=rollback 的新版本记录。
     *
     * <p>新记录同时落当前策略名快照（{@code name}）。
     *
     * @param id              策略主键
     * @param targetVersionNo 目标版本号
     * @return 回滚产生的新版本视图
     * @author cmr
     * @since 2026-11-11
     */
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
        rollbackVersion.setName(strategy.getName());
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

        boolean staysOffline = CmrStrategy.STATUS_OFFLINE.equals(strategy.getStatus());
        strategy.setCurrentVersion(nextVersion);
        if (!staysOffline) {
            strategy.setStatus(CmrStrategy.STATUS_ACTIVE);
            clearOfflineTrace(strategy);
        }
        // 全字段更新：回滚到生效态时需清空下线原因/备注/时间
        strategyMapper.update(strategy, false);
        demoteSameSceneOthers(strategy);
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

    /**
     * 两版本参数 diff（变更评估）。
     *
     * <p>{@code compareId} 为空或等于自身时，按 {@code from}/{@code to} 两个版本号对比；
     * 指定其它策略时，对比两条策略各自的当前生效版本。两条策略都必须存在（逻辑删除后视为不存在），
     * 否则抛 {@link BizException}。返回结构保持原样，并增补 {@code threshold} 与
     * {@code vector}（向量模型/型号）两行。
     *
     * @param id        基准策略主键
     * @param from      起始版本号（compareId 缺省/自身时必填）
     * @param to        目标版本号（compareId 缺省/自身时必填）
     * @param compareId 对比策略主键，缺省或等于 {@code id} 时对比自身
     * @return diff 结果
     * @author cmr
     * @since 2026-11-11
     */
    public Map<String, Object> diff(Long id, Integer from, Integer to, Long compareId) {
        CmrStrategy base = requireStrategy(id);
        Long targetId = compareId == null ? id : compareId;
        CmrStrategyVersion v1;
        CmrStrategyVersion v2;
        if (!id.equals(targetId)) {
            CmrStrategy other = requireStrategy(targetId);
            // 跨策略对比取“可用版本”：生效版本 > 草稿 > 最近已发布版本，草稿/未发布策略同样可比
            v1 = effectiveVersionOf(base);
            v2 = effectiveVersionOf(other);
            BizException.assertNull(v1, "策略「" + base.getName() + "」尚无任何版本，无法对比");
            BizException.assertNull(v2, "策略「" + other.getName() + "」尚无任何版本，无法对比");
            from = v1.getVersionNo();
            to = v2.getVersionNo();
        } else {
            BizException.assertNull(from, "起始版本号不能为空");
            BizException.assertNull(to, "目标版本号不能为空");
            v1 = requireVersion(id, from);
            v2 = requireVersion(id, to);
        }

        Map<String, Object> weights = new LinkedHashMap<>();
        Map<String, Integer> w1 = readWeights(v1);
        Map<String, Integer> w2 = readWeights(v2);
        for (String key : List.of("text", "image", "audio", "video")) {
            weights.put(key, pair(w1.getOrDefault(key, 0), w2.getOrDefault(key, 0)));
        }
        String model = activeModelVersion();
        String vector = model == null ? "" : model;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("from", from);
        result.put("to", to);
        result.put("compareId", targetId);
        result.put("weights", weights);
        result.put("rerank", pair(v1.getRerank(), v2.getRerank()));
        result.put("topK", pair(v1.getTopK(), v2.getTopK()));
        result.put("threshold", pair(thresholdOf(v1), thresholdOf(v2)));
        result.put("vector", pair(vector, vector));
        return result;
    }

    /** from/to 对比行（允许 null，便于前端展示未知值） */
    private Map<String, Object> pair(Object from, Object to) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("from", from);
        row.put("to", to);
        return row;
    }

    /** 版本号是否已被同策略的其它版本占用（草稿行自身除外） */
    private boolean versionNumberTaken(Long strategyId, int versionNo, Long excludeVersionId) {
        Long count = versionMapper.selectCountByQuery(QueryWrapper.create()
                .where(CMR_STRATEGY_VERSION.STRATEGY_ID.eq(strategyId)
                        .and(CMR_STRATEGY_VERSION.VERSION_NO.eq(versionNo))
                        .and(CMR_STRATEGY_VERSION.ID.ne(excludeVersionId))));
        return count != null && count > 0;
    }

    /** 取当前生效版本，缺失则抛业务异常 */
    private CmrStrategyVersion requireCurrent(CmrStrategy strategy) {
        CmrStrategyVersion current = currentOf(strategy);
        BizException.assertNull(current, "策略无生效版本，无法对比: " + strategy.getBizNo());
        return current;
    }

    /** 场景生效策略（检索台回显/检索链取参）：先精确场景再 all 兜底 */
    public EffectiveStrategy effectiveOf(String scene) {
        CmrStrategy match = null;
        if (notBlank(scene)) {
            match = strategyMapper.selectOneByQuery(QueryWrapper.create()
                    .where(CMR_STRATEGY.SCENE.eq(scene).and(CMR_STRATEGY.STATUS.eq(CmrStrategy.STATUS_ACTIVE)))
                    .orderBy(CMR_STRATEGY.ID.asc())
                    .limit(1));
        }
        if (match == null) {
            match = strategyMapper.selectOneByQuery(QueryWrapper.create()
                    .where(CMR_STRATEGY.SCENE.eq("all").and(CMR_STRATEGY.STATUS.eq(CmrStrategy.STATUS_ACTIVE)))
                    .orderBy(CMR_STRATEGY.ID.asc())
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

    /** 把策略置为生效前的公共动作：清空下线原因/备注/时间三件套 */
    private void clearOfflineTrace(CmrStrategy strategy) {
        strategy.setOfflineReason(null);
        strategy.setOfflineRemark(null);
        strategy.setOfflineTime(null);
    }

    /**
     * 同场景同时只能有一条生效策略：把同场景其它生效策略降为草稿。
     *
     * <p>仅在当前策略确实处于生效态时执行；已下线/草稿策略不影响其它策略。
     *
     * @param strategy 当前策略
     */
    private void demoteSameSceneOthers(CmrStrategy strategy) {
        if (!CmrStrategy.STATUS_ACTIVE.equals(strategy.getStatus())
                || strategy.getScene() == null || strategy.getScene().isBlank()) {
            return;
        }
        List<CmrStrategy> sameScene = strategyMapper.selectListByQuery(QueryWrapper.create()
                .where(CMR_STRATEGY.SCENE.eq(strategy.getScene())
                        .and(CMR_STRATEGY.ID.ne(strategy.getId()))
                        .and(CMR_STRATEGY.STATUS.eq(CmrStrategy.STATUS_ACTIVE))));
        for (CmrStrategy other : sameScene) {
            other.setStatus(CmrStrategy.STATUS_DRAFT);
            strategyMapper.update(other);
        }
    }

    private EffectiveStrategy effectiveOfEntity(CmrStrategy match) {
        if (match == null || match.getCurrentVersion() == null || match.getCurrentVersion() <= 0) {
            return new EffectiveStrategy(null, null, null, null, defaultWeights(), false,
                    properties.getSearch().getTopK(), properties.getSearch().getThreshold());
        }
        CmrStrategyVersion v = currentOf(match);
        if (v == null) {
            return new EffectiveStrategy(match.getId(), null, match.getBizNo(), match.getCurrentVersion(),
                    defaultWeights(), false, properties.getSearch().getTopK(), properties.getSearch().getThreshold());
        }
        return new EffectiveStrategy(match.getId(), v.getId(), match.getBizNo(), match.getCurrentVersion(),
                readWeights(v), Boolean.TRUE.equals(v.getRerank()),
                v.getTopK() == null ? properties.getSearch().getTopK() : v.getTopK(), thresholdOf(v));
    }

    /** 生效策略参数（weights/rerank/topK/threshold + 策略编号与版本引用） */
    public record EffectiveStrategy(Long strategyId, Long versionId, String strategyNo, Integer versionNo,
                                    Map<String, Integer> weights, boolean rerank, int topK,
                                    java.math.BigDecimal threshold) {
    }

    /**
     * 写入草稿行：参数取调用方已归一化的值（未提供的项已在调用侧回落）。
     *
     * @param strategy  所属策略
     * @param weights   四模态权重
     * @param rerank    重排开关
     * @param topK      返回条数
     * @param threshold 相似度阈值
     * @author cmr
     * @since 2026-11-11
     */
    private void saveDraft(CmrStrategy strategy, Map<String, Integer> weights, Boolean rerank,
                           Integer topK, BigDecimal threshold) {
        CmrStrategyVersion draft = draftOf(strategy.getId());
        // 草稿行只要已存在就必须 update：其 version_no 始终为 0，不能用它判断是否新行，
        // 否则“保存草稿 → 再次保存”会重复 insert 同一主键。
        boolean isNew = draft == null;
        if (isNew) {
            draft = new CmrStrategyVersion();
            draft.setId(idWorker.next());
            draft.setStrategyId(strategy.getId());
            draft.setVersionNo(0);
            draft.setStatus(CmrStrategyVersion.STATUS_DRAFT);
            draft.setDelFlag("F");
            draft.setCreateTime(LocalDateTime.now());
        }
        // 发布前的策略名快照，发布时若改名会再次覆盖
        draft.setName(strategy.getName());
        draft.setWeightsJson(JsonUtils.write(weights));
        draft.setRerank(Boolean.TRUE.equals(rerank));
        draft.setTopK(topK == null ? 50 : topK);
        draft.setThreshold(threshold == null ? properties.getSearch().getThreshold() : threshold);
        if (isNew) {
            draft.setSource("new");
            versionMapper.insert(draft, true);
        } else {
            versionMapper.update(draft);
        }
    }

    /** 对比用可用版本：生效版本 > 草稿 > 最近已发布版本 */
    private CmrStrategyVersion effectiveVersionOf(CmrStrategy strategy) {
        CmrStrategyVersion current = currentOf(strategy);
        if (current != null) {
            return current;
        }
        CmrStrategyVersion draft = draftOf(strategy.getId());
        if (draft != null) {
            return draft;
        }
        return versionMapper.selectOneByQuery(QueryWrapper.create()
                .where(CMR_STRATEGY_VERSION.STRATEGY_ID.eq(strategy.getId())
                        .and(CMR_STRATEGY_VERSION.STATUS.ne(CmrStrategyVersion.STATUS_DRAFT)))
                .orderBy(CMR_STRATEGY_VERSION.VERSION_NO.desc())
                .limit(1));
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
                && threshold.compareTo(THRESHOLD_MIN) >= 0
                && threshold.compareTo(THRESHOLD_MAX) <= 0;
    }

    /** 权重 JSON 解析失败时视为非法，避免全局检查因单条脏数据中断 */
    private Map<String, Integer> parseWeightsQuietly(CmrStrategyVersion version) {
        try {
            return readWeights(version);
        } catch (RuntimeException e) {
            log.warn("策略版本权重解析失败: versionId=" + version.getId(), e);
            return null;
        }
    }

    /** 场景 all 的生效预置策略（默认兜底策略） */
    private CmrStrategy defaultStrategyActive() {
        return strategyMapper.selectOneByQuery(QueryWrapper.create()
                .where(CMR_STRATEGY.SCENE.eq("all")
                        .and(CMR_STRATEGY.PRESET.eq(true))
                        .and(CMR_STRATEGY.STATUS.eq(CmrStrategy.STATUS_ACTIVE)))
                .orderBy(CMR_STRATEGY.ID.asc())
                .limit(1));
    }

    private java.math.BigDecimal thresholdOf(CmrStrategyVersion v) {
        return v == null || v.getThreshold() == null ? properties.getSearch().getThreshold() : v.getThreshold();
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
                new tools.jackson.core.type.TypeReference<Map<String, Integer>>() {
                });
        for (String key : List.of("text", "image", "audio", "video")) {
            weights.putIfAbsent(key, 0);
        }
        return weights;
    }

    /** 兜底策略的默认四模态权重（cmr.search.weights，缺省四模态各 25） */
    private Map<String, Integer> defaultWeights() {
        Map<String, Integer> weights = new LinkedHashMap<>(properties.getSearch().getWeights());
        for (String key : List.of("text", "image", "audio", "video")) {
            weights.putIfAbsent(key, 25);
        }
        return weights;
    }

    /** 新建策略的默认四模态权重 50/30/10/10（契约第 4 节） */
    private Map<String, Integer> initialWeights() {
        Map<String, Integer> weights = new LinkedHashMap<>();
        weights.put("text", 50);
        weights.put("image", 30);
        weights.put("audio", 10);
        weights.put("video", 10);
        return weights;
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
                .offlineRemark(e.getOfflineRemark())
                .currentVersion(e.getCurrentVersion())
                .paramSummary(current == null ? null : paramSummary(current))
                .hasDraft(draftOf(e.getId()) != null)
                .remark(e.getRemark())
                .createTime(e.getCreateTime()).updateTime(e.getUpdateTime())
                .build();
    }

    private StrategyVersionVO toVersionVO(CmrStrategyVersion e) {
        if (e == null) {
            return null;
        }
        return StrategyVersionVO.builder()
                .id(e.getId()).strategyId(e.getStrategyId()).name(e.getName()).versionNo(e.getVersionNo())
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
