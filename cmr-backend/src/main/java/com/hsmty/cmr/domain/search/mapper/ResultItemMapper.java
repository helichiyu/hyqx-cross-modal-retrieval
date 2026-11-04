package com.hsmty.cmr.domain.search.mapper;

import com.mybatisflex.core.BaseMapper;
import com.hsmty.cmr.domain.search.entity.CmrResultItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 结果集条目 Mapper
 *
 * @author cmr
 * @since 2026-11-03
 */
@Mapper
public interface ResultItemMapper extends BaseMapper<CmrResultItem> {
}
