package com.hsmty.cmr.domain.task;

import com.hsmty.cmr.common.api.PageQuery;
import com.hsmty.cmr.common.api.PageResult;
import com.hsmty.cmr.common.exception.BizException;
import com.hsmty.cmr.common.id.BizNoGenerator;
import com.hsmty.cmr.common.id.SnowflakeIdWorker;
import com.hsmty.cmr.common.util.JsonUtils;
import com.hsmty.cmr.domain.search.mapper.SearchHistoryMapper;
import com.hsmty.cmr.domain.task.entity.CmrTask;
import com.hsmty.cmr.domain.task.entity.CmrTaskLog;
import com.hsmty.cmr.domain.task.mapper.TaskLogMapper;
import com.hsmty.cmr.domain.task.mapper.TaskMapper;
import com.hsmty.cmr.domain.task.vo.FailedTaskVO;
import com.hsmty.cmr.operator.OperatorException;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.hsmty.cmr.domain.search.entity.table.CmrSearchHistoryTableDef.CMR_SEARCH_HISTORY;
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
    private final SearchHistoryMapper searchHistoryMapper;
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

    /**
     * 失败任务分页（P6）：覆盖重试中、已恢复、已转隔离三类处置状态。
     *
     * <p>查询口径：
     * </p>
     * <ul>
     *   <li>重试中：status 为 ready/running 且 retryCount &gt; 0；</li>
     *   <li>已恢复：status 为 done 且 retryCount &gt; 0；</li>
     *   <li>已转隔离：failDisposal 为 isolate 或 status 为 failed。</li>
     * </ul>
     * queryText 通过 refId 关联 cmr_search_history 取得，非检索任务为空。
     *
     * @param query 分页参数（沿用 PageQuery，默认每页 10 条）
     * @param keyword 任务号模糊（P3 按任务号回查失败原因时使用）
     * @return 失败任务分页
     * @author cmr
     * @since 2026-11-11
     */
    public PageResult<FailedTaskVO> failedPage(PageQuery query, String keyword) {
        // 显式括号避免与逻辑删除条件 AND 拼接时 OR 优先级错乱
        QueryWrapper qw = QueryWrapper.create()
                .from(CMR_TASK)
                .where("((status IN (?, ?) AND retry_count > 0)"
                                + " OR (status = ? AND retry_count > 0)"
                                + " OR fail_disposal = ? OR status = ?)",
                        CmrTask.STATUS_READY, CmrTask.STATUS_RUNNING, CmrTask.STATUS_DONE,
                        CmrTask.DISPOSAL_ISOLATED, CmrTask.STATUS_FAILED);
        if (keyword != null && !keyword.isBlank()) {
            qw.and(CMR_TASK.BIZ_NO.like(keyword.trim()));
        }
        qw.orderBy(CMR_TASK.ID.desc());
        Page<CmrTask> page = taskMapper.paginate(
                Math.max(query.getPageNum(), 1), Math.max(query.getPageSize(), 1), qw);
        Map<Long, String> queryTexts = queryTextsOf(page.getRecords());
        List<FailedTaskVO> records = page.getRecords().stream()
                .map(task -> FailedTaskVO.builder()
                        .id(task.getId())
                        .bizNo(task.getBizNo())
                        .queryText(queryTexts.get(task.getRefId()))
                        .failType(task.getFailType())
                        .failDisposal(task.getFailDisposal())
                        .handling(handlingOf(task))
                        .errorMsg(task.getErrorMsg())
                        .createTime(task.getCreateTime())
                        .build())
                .toList();
        return PageResult.of(records, page.getTotalRow(), query.getPageNum(), query.getPageSize());
    }

    /**
     * 计算失败任务的处置进展：retrying / recovered / isolated。
     *
     * @param task 任务
     * @return 处置进展
     * @author cmr
     * @since 2026-11-11
     */
    private String handlingOf(CmrTask task) {
        boolean retried = task.getRetryCount() != null && task.getRetryCount() > 0;
        String status = task.getStatus();
        if (retried && (CmrTask.STATUS_READY.equals(status) || CmrTask.STATUS_RUNNING.equals(status))) {
            return CmrTask.DISPOSAL_RETRYING;
        }
        if (retried && CmrTask.STATUS_DONE.equals(status)) {
            return CmrTask.DISPOSAL_RECOVERED;
        }
        if (CmrTask.DISPOSAL_ISOLATED.equals(task.getFailDisposal()) || CmrTask.STATUS_FAILED.equals(status)) {
            return CmrTask.DISPOSAL_ISOLATED;
        }
        return task.getFailDisposal();
    }

    /**
     * 批量读取检索任务的 queryText，避免逐条查询。
     *
     * @param tasks 当前页任务
     * @return refId → queryText
     * @author cmr
     * @since 2026-11-11
     */
    private Map<Long, String> queryTextsOf(List<CmrTask> tasks) {
        List<Long> refIds = tasks.stream()
                .filter(task -> CmrTask.TYPE_SEARCH.equals(task.getType()))
                .map(CmrTask::getRefId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (refIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> result = new HashMap<>();
        searchHistoryMapper.selectListByQuery(QueryWrapper.create()
                        .where(CMR_SEARCH_HISTORY.ID.in(refIds)))
                .forEach(history -> result.put(history.getId(), history.getQueryText()));
        return result;
    }

    /** 开机恢复查询：崩溃时仍在运行/重试中的任务 */
    public List<CmrTask> findResumable() {
        return taskMapper.selectListByQuery(QueryWrapper.create()
                .where(CMR_TASK.STATUS.eq(CmrTask.STATUS_RUNNING)
                        .or(CMR_TASK.STATUS.eq(CmrTask.STATUS_READY)))
                .and(CMR_TASK.TYPE.ne(CmrTask.TYPE_SEARCH)));
    }

    public String statusOf(Long taskId) {
        if (taskId == null) {
            return null;
        }
        CmrTask task = taskMapper.selectOneById(taskId);
        return task == null ? null : task.getStatus();
    }

    public String bizNoOf(Long taskId) {
        if (taskId == null) {
            return null;
        }
        CmrTask t = taskMapper.selectOneById(taskId);
        return t == null ? null : t.getBizNo();
    }

    /**
     * 按任务号（TASK）反查任务 ID，供检索历史以 SRH/TASK 双形态解析。
     *
     * @param bizNo 任务号
     * @return 任务 ID；不存在时返回 null
     * @author cmr
     * @since 2026-11-11
     */
    public Long idOfByBizNo(String bizNo) {
        if (bizNo == null || bizNo.isBlank()) {
            return null;
        }
        CmrTask task = taskMapper.selectOneByQuery(QueryWrapper.create()
                .where(CMR_TASK.BIZ_NO.eq(bizNo.trim())));
        return task == null ? null : task.getId();
    }

    /**
     * 读取任务错误信息（P3 失败空态展示）。
     *
     * @param taskId 任务 ID
     * @return 错误信息；任务不存在时返回 null
     * @author cmr
     * @since 2026-11-11
     */
    public String errorMsgOf(Long taskId) {
        if (taskId == null) {
            return null;
        }
        CmrTask t = taskMapper.selectOneById(taskId);
        return t == null ? null : t.getErrorMsg();
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
