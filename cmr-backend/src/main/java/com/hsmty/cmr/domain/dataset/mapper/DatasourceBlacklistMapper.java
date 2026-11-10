package com.hsmty.cmr.domain.dataset.mapper;

import com.hsmty.cmr.domain.dataset.entity.CmrDatasourceBlacklist;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 数据源黑名单 Mapper
 *
 * @author cmr
 * @since 2026-11-10
 */
@Mapper
public interface DatasourceBlacklistMapper extends BaseMapper<CmrDatasourceBlacklist> {
}
