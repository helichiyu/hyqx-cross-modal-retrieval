package com.hsmty.cmr.domain.task;

import com.hsmty.cmr.common.exception.BizException;
import com.hsmty.cmr.common.id.BizNoGenerator;
import com.hsmty.cmr.common.id.SnowflakeIdWorker;
import com.hsmty.cmr.common.util.JsonUtils;
import com.hsmty.cmr.domain.task.entity.CmrTask;
import com.hsmty.cmr.domain.task.entity.CmrTaskLog;
import com.hsmty.cmr.domain.task.mapper.TaskLogMapper;
import com.hsmty.cmr.domain.task.mapper.TaskMapper;
import com.hsmty.cmr.operator.OperatorException;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

import static com.hsmty.cmr.domain.task.entity.table.CmrTaskTableDef.CMR_TASK;
import static com.hsmty.cmr.domain.task.entity.table.CmrTaskLogTableDef.CMR_TASK_LOG;

/**
 * 任务服务：任务生命周期 + 步骤日志（任务状态机由系统驱动，无人工改状态接口）
 *
 * @author cmr
 * @since 2026-11-03
 */
@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskMapper taskMapper;
    private final TaskLogMapper taskLogMapper;
    private final SnowflakeIdWorker idWorker;
    private final BizNoGenerator bizNoGenerator;

    /** 创建任务（status=ready，入参快照落 payloadJson） */
    public CmrTask create(String type, Long refId, Object payload) {
        CmrTask task = new CmrTask();
        task.setId(idWorker.next());
        task.setBizNo(bizNoGenerator.next(BizNoGenerator.PREFIX_TASK));
        task.setType(type);
        task.setRefId(refId);
        task.setStatus(CmrTask.STATUS_READY);
        task.setRetryCount(0);
        task.setPayloadJson(JsonUtils.write(payload));
        task.setDelFlag("F");
        task.setCreateTime(LocalDateTime.now());
        taskMapper.insert(task, true);
        return task;
    }

    /** 更新入参快照（如检索历史回填 refId） */
    public void updatePayload(CmrTask task, Object payload) {
        task.setPayloadJson(JsonUtils.write(payload));
        taskMapper.update(task);
    }

    public void markRunning(CmrTask task) {
        task.setStatus(CmrTask.STATUS_RUNNING);
        task.setStartedTime(task.getStartedTime() == null ? LocalDateTime.now() : task.getStartedTime());
        task.setFinishedTime(null);
        taskMapper.update(task);
    }

    public void updateStep(CmrTask task, String step) {
        task.setCurrentStep(step);
        taskMapper.update(task);
    }

    public void succeed(CmrTask task) {
        boolean retried = task.getRetryCount() != null && task.getRetryCount() > 0;
        task.setStatus(CmrTask.STATUS_DONE);
        task.setFailDisposal(retried ? CmrTask.DISPOSAL_RECOVERED : task.getFailDisposal());
        task.setFinishedTime(LocalDateTime.now());
        taskMapper.update(task);
    }

    /**
     * 失败落库。
     *
     * @param willRetry 引擎侧且未超上限时为 true（disposal=retrying），否则 isolated
     */
    public void fail(CmrTask task, OperatorException.Side side, String message, boolean willRetry, int attempt) {
        task.setStatus(willRetry ? CmrTask.STATUS_READY : CmrTask.STATUS_FAILED);
        task.setFailType(side == OperatorException.Side.ENGINE ? CmrTask.FAIL_ENGINE : CmrTask.FAIL_DATA);
        task.setFailDisposal(willRetry ? CmrTask.DISPOSAL_RETRYING : CmrTask.DISPOSAL_ISOLATED);
        task.setRetryCount(Math.max(task.getRetryCount() == null ? 0 : task.getRetryCount(), attempt));
        task.setErrorMsg(truncate(message, 1000));
        if (!willRetry) {
            task.setFinishedTime(LocalDateTime.now());
        }
        taskMapper.update(task);
    }

    public void log(Long taskId, String step, String status, Object operatorOutput, String message) {
        CmrTaskLog entry = new CmrTaskLog();
        entry.setId(idWorker.next());
        entry.setTaskId(taskId);
        entry.setStep(step);
        entry.setStatus(status);
        entry.setOperatorOutputJson(JsonUtils.writeQuietly(operatorOutput));
        entry.setMessage(truncate(message, 1000));
        entry.setDelFlag("F");
        entry.setCreateTime(LocalDateTime.now());
        taskLogMapper.insert(entry, true);
    }

    public CmrTask requireTask(Long id) {
        CmrTask task = taskMapper.selectOneById(id);
        BizException.assertNull(task, "任务不存在");
        return task;
    }

    public List<CmrTaskLog> logsOf(Long taskId) {
        return taskLogMapper.selectListByQuery(QueryWrapper.create()
                .where(CMR_TASK_LOG.TASK_ID.eq(taskId))
                .orderBy(CMR_TASK_LOG.ID.asc()));
    }

    public List<CmrTask> tasksOf(String type, Long refId) {
        return taskMapper.selectListByQuery(QueryWrapper.create()
                .where(CMR_TASK.TYPE.eq(type).and(CMR_TASK.REF_ID.eq(refId)))
                .orderBy(CMR_TASK.ID.desc()));
    }

    /** 任务分页 */
    public com.mybatisflex.core.paginate.Page<CmrTask> page(QueryWrapper qw, com.hsmty.cmr.common.api.PageQuery query) {
        return taskMapper.paginate(Math.max(query.getPageNum(), 1), Math.max(query.getPageSize(), 1), qw);
    }

    /** 开机恢复查询：崩溃时仍在运行/重试中的任务 */
    public List<CmrTask> findResumable() {
        return taskMapper.selectListByQuery(QueryWrapper.create()
                .where(CMR_TASK.STATUS.eq(CmrTask.STATUS_RUNNING)
                        .or(CMR_TASK.STATUS.eq(CmrTask.STATUS_READY)))
                .and(CMR_TASK.TYPE.ne(CmrTask.TYPE_SEARCH)));
    }

    public String bizNoOf(Long taskId) {
        if (taskId == null) {
            return null;
        }
        CmrTask t = taskMapper.selectOneById(taskId);
        return t == null ? null : t.getBizNo();
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
