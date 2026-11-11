package com.hsmty.cmr.domain.dataset.service;

import com.hsmty.cmr.common.exception.BizException;
import com.hsmty.cmr.common.id.SnowflakeIdWorker;
import com.hsmty.cmr.domain.dataset.entity.CmrDatasourceBlacklist;
import com.hsmty.cmr.domain.dataset.mapper.DatasourceBlacklistMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static com.hsmty.cmr.domain.dataset.entity.table.CmrDatasourceBlacklistTableDef.CMR_DATASOURCE_BLACKLIST;

/**
 * 数据源黑名单（P4）：按来源名整源排除，可随时移出
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

    public List<CmrDatasourceBlacklist> list() {
        return blacklistMapper.selectListByQuery(QueryWrapper.create()
                .orderBy(CMR_DATASOURCE_BLACKLIST.ID.desc()));
    }

    @Transactional(rollbackFor = Exception.class)
    public CmrDatasourceBlacklist add(String datasetId, String name, String sourceName, String endpoint) {
        BizException.assertTrue(sourceName == null || sourceName.isBlank(), "来源名不能为空");
        Long exists = blacklistMapper.selectCountByQuery(QueryWrapper.create()
                .where(CMR_DATASOURCE_BLACKLIST.SOURCE_NAME.eq(sourceName)));
        BizException.assertTrue(exists != null && exists > 0, "该来源已在黑名单中");
        CmrDatasourceBlacklist entity = new CmrDatasourceBlacklist();
        entity.setId(idWorker.next());
        entity.setDatasetId(datasetId);
        entity.setName(name);
        entity.setSourceName(sourceName);
        entity.setEndpoint(endpoint);
        entity.setDelFlag("F");
        entity.setCreateTime(LocalDateTime.now());
        blacklistMapper.insert(entity, true);
        log.info("数据源黑名单新增: {} ({})", sourceName, datasetId);
        return entity;
    }

    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id) {
        CmrDatasourceBlacklist entity = blacklistMapper.selectOneById(id);
        BizException.assertNull(entity, "黑名单记录不存在");
        blacklistMapper.deleteById(id);
    }
}
