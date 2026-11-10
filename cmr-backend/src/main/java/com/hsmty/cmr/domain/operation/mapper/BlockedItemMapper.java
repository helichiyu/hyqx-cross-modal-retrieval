package com.hsmty.cmr.domain.operation.mapper;

import com.hsmty.cmr.domain.operation.entity.CmrBlockedItem;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 条目屏蔽 Mapper
 *
 * @author cmr
 * @since 2026-11-10
 */
@Mapper
public interface BlockedItemMapper extends BaseMapper<CmrBlockedItem> {
}
