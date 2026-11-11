package com.hsmty.cmr.domain.evaluation.mapper;

import com.hsmty.cmr.domain.evaluation.entity.CmrEvaluation;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 效果评测 Mapper
 *
 * @author cmr
 * @since 2026-11-10
 */
@Mapper
public interface EvaluationMapper extends BaseMapper<CmrEvaluation> {
}
