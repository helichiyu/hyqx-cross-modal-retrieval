package com.hsmty.cmr.domain.operation.mapper;

import com.mybatisflex.core.BaseMapper;
import com.hsmty.cmr.domain.operation.entity.CmrFeedback;
import org.apache.ibatis.annotations.Mapper;

/**
 * 反馈单 Mapper
 *
 * @author cmr
 * @since 2026-11-03
 */
@Mapper
public interface FeedbackMapper extends BaseMapper<CmrFeedback> {
}
