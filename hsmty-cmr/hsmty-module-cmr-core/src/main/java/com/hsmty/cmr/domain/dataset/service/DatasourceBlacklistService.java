package com.hsmty.cmr.domain.dataset.service;

import com.hsmty.cmr.common.exception.BizException;
import com.hsmty.cmr.common.id.SnowflakeIdWorker;
import com.hsmty.cmr.domain.dataset.entity.CmrDatasourceBlacklist;
import com.hsmty.cmr.domain.dataset.mapper.DatasourceBlacklistMapper;
import com.hsmty.cmr.domain.dataset.vo.DatasourceBlacklistSummaryVO;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.hsmty.cmr.domain.dataset.entity.table.CmrDatasourceBlacklistTableDef.CMR_DATASOURCE_BLACKLIST;

/**
 * 数据源黑名单（P4）：按数据集维度登记、按来源名整源排除，可随时移出
 *
 * @author cmr
 * @since 2026-11-10
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatasourceBlacklistService {

    private final DatasourceBlacklistMapper blacklistMapper;
    private final SnowflakeIdWorker idWorker;

    /**
     * 黑名单列表（按创建顺序倒序）。
     *
     * @return 黑名单记录
     */
    public List<CmrDatasourceBlacklist> list() {
        return blacklistMapper.selectListByQuery(QueryWrapper.create()
                .orderBy(CMR_DATASOURCE_BLACKLIST.ID.desc()));
    }

    /**
     * 加入黑名单：按 datasetId 去重，已存在则更新名称/来源/地址。
     *
     * <p>检索排除仍按来源名（数据流 3.4）；endpoint 可选，填写时必须为 http/https 且不含空格。
     *
     * @param datasetId  上游数据集 ID（必填）
     * @param name       数据集名称
     * @param sourceName 来源名，缺省为空则该条仅按 datasetId 排除
     * @param endpoint   数据源地址
     * @return 新增或更新后的黑名单记录
     */
    @Transactional(rollbackFor = Exception.class)
    public CmrDatasourceBlacklist add(String datasetId, String name, String sourceName, String endpoint) {
        BizException.assertTrue(datasetId == null || datasetId.isBlank(), "datasetId 不能为空");
        validateEndpoint(endpoint);
        CmrDatasourceBlacklist entity = blacklistMapper.selectOneByQuery(QueryWrapper.create()
                .where(CMR_DATASOURCE_BLACKLIST.DATASET_ID.eq(datasetId))
                .orderBy(CMR_DATASOURCE_BLACKLIST.ID.desc())
                .limit(1));
        if (entity != null) {
            if (name != null) {
                entity.setName(name);
            }
            if (sourceName != null) {
                entity.setSourceName(sourceName);
            }
            if (endpoint != null) {
                entity.setEndpoint(endpoint);
            }
            blacklistMapper.update(entity, false);
            log.info("数据源黑名单更新: {} ({})", entity.getSourceName(), datasetId);
            return entity;
        }
        CmrDatasourceBlacklist created = new CmrDatasourceBlacklist();
        created.setId(idWorker.next());
        created.setDatasetId(datasetId);
        created.setName(name);
        created.setSourceName(sourceName);
        created.setEndpoint(endpoint);
        created.setDelFlag("F");
        created.setCreateTime(LocalDateTime.now());
        blacklistMapper.insert(created, true);
        log.info("数据源黑名单新增: {} ({})", sourceName, datasetId);
        return created;
    }

    /**
     * 按来源名汇总黑名单条数（抽屉展示）。
     *
     * <p>来源名为空的记录无法按来源聚合，不参与统计。
     *
     * @return 来源名与条数，按条数倒序、来源名升序
     */
    public List<DatasourceBlacklistSummaryVO> summary() {
        List<CmrDatasourceBlacklist> rows = blacklistMapper.selectListByQuery(QueryWrapper.create());
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (CmrDatasourceBlacklist row : rows) {
            String sourceName = row.getSourceName();
            if (sourceName == null || sourceName.isBlank()) {
                continue;
            }
            counts.merge(sourceName, 1, Integer::sum);
        }
        List<DatasourceBlacklistSummaryVO> result = new ArrayList<>(counts.size());
        counts.forEach((sourceName, count) -> result.add(new DatasourceBlacklistSummaryVO(sourceName, count)));
        result.sort((left, right) -> {
            int byCount = Integer.compare(right.getCount(), left.getCount());
            return byCount != 0 ? byCount : left.getSourceName().compareTo(right.getSourceName());
        });
        return result;
    }

    /**
     * 移出黑名单。
     *
     * @param id 黑名单记录 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id) {
        CmrDatasourceBlacklist entity = blacklistMapper.selectOneById(id);
        BizException.assertNull(entity, "黑名单记录不存在");
        blacklistMapper.deleteById(id);
    }

    /** endpoint 可选；填写时必须 http(s) 开头且不含空白字符 */
    private void validateEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return;
        }
        BizException.assertTrue(!endpoint.startsWith("http://") && !endpoint.startsWith("https://"),
                "endpoint 必须以 http:// 或 https:// 开头");
        BizException.assertTrue(endpoint.chars().anyMatch(Character::isWhitespace),
                "endpoint 不能包含空格");
    }
}
