package com.hsmty.cmr.domain.task.mapper;

import com.mybatisflex.core.BaseMapper;
import com.hsmty.cmr.domain.task.entity.CmrTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * 任务 Mapper
 *
 * @author cmr
 * @since 2026-11-03
 */
@Mapper
public interface TaskMapper extends BaseMapper<CmrTask> {
}
