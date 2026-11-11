package com.hsmty.cmr.domain.task.mapper;

import com.mybatisflex.core.BaseMapper;
import com.hsmty.cmr.domain.task.entity.CmrTaskLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 任务步骤日志 Mapper
 *
 * @author cmr
 * @since 2026-11-03
 */
@Mapper
public interface TaskLogMapper extends BaseMapper<CmrTaskLog> {
}
