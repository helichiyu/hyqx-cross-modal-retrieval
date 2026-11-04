package com.hsmty.cmr.domain.evaluation.mapper;

import com.mybatisflex.core.BaseMapper;
import com.hsmty.cmr.domain.evaluation.entity.CmrEvalItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 评测集条目 Mapper
 *
 * @author cmr
 * @since 2026-11-03
 */
@Mapper
public interface EvalItemMapper extends BaseMapper<CmrEvalItem> {
}
