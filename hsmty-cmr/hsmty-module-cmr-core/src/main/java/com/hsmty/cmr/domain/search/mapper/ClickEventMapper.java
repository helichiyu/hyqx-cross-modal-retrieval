package com.hsmty.cmr.domain.search.mapper;

import com.mybatisflex.core.BaseMapper;
import com.hsmty.cmr.domain.search.entity.CmrClickEvent;
import org.apache.ibatis.annotations.Mapper;

/**
 * 点击事件 Mapper
 *
 * @author cmr
 * @since 2026-11-03
 */
@Mapper
public interface ClickEventMapper extends BaseMapper<CmrClickEvent> {
}
