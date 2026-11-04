package com.hsmty.cmr.domain.evaluation.mapper;

import com.mybatisflex.core.BaseMapper;
import com.hsmty.cmr.domain.evaluation.entity.CmrEvalRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 评测历史 Mapper
 *
 * @author cmr
 * @since 2026-11-03
 */
@Mapper
public interface EvalRecordMapper extends BaseMapper<CmrEvalRecord> {
}
