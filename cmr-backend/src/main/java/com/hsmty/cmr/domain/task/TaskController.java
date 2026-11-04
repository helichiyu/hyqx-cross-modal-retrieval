package com.hsmty.cmr.domain.task;

import com.hsmty.cmr.common.api.ApiPageResult;
import com.hsmty.cmr.common.api.ApiResult;
import com.hsmty.cmr.common.api.PageQuery;
import com.hsmty.cmr.common.api.PageResult;
import com.hsmty.cmr.domain.dataset.entity.CmrDataset;
import com.hsmty.cmr.domain.dataset.mapper.DatasetMapper;
import com.hsmty.cmr.domain.evaluation.entity.CmrEvalRecord;
import com.hsmty.cmr.domain.evaluation.mapper.EvalRecordMapper;
import com.hsmty.cmr.domain.search.entity.CmrSearchHistory;
import com.hsmty.cmr.domain.search.mapper.SearchHistoryMapper;
import com.hsmty.cmr.domain.task.entity.CmrTask;
import com.hsmty.cmr.domain.task.vo.TaskDetailVO;
import com.hsmty.cmr.domain.task.vo.TaskLogVO;
import com.hsmty.cmr.domain.task.vo.TaskVO;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.hsmty.cmr.domain.task.entity.table.CmrTaskTableDef.CMR_TASK;

/**
 * 任务接口（只读展示：失败处置系统自动，不设人工改状态接口，数据流 3.3）
 *
 * @author cmr
 * @since 2026-11-03
 */
@Tag(name = "任务")
@RestController
@RequestMapping("tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;
    private final DatasetMapper datasetMapper;
    private final EvalRecordMapper evalRecordMapper;
    private final SearchHistoryMapper searchHistoryMapper;

    @Operation(summary = "任务分页（type/status/failDisposal 筛选）")
    @PreAuthorize("hasAnyAuthority('cmr:task:page')")
    @GetMapping
    public ApiResult<PageResult<TaskVO>> page(PageQuery query,
                                              @RequestParam(required = false) String type,
                                              @RequestParam(required = false) String status,
                                              @RequestParam(required = false) String failDisposal,
                                              @RequestParam(required = false) String keyword) {
        QueryWrapper qw = QueryWrapper.create().from(CMR_TASK);
        if (type != null && !type.isBlank()) {
            qw.where(CMR_TASK.TYPE.eq(type));
        }
        if (status != null && !status.isBlank()) {
            qw.where(CMR_TASK.STATUS.eq(status));
        }
        if (failDisposal != null && !failDisposal.isBlank()) {
            qw.where(CMR_TASK.FAIL_DISPOSAL.eq(failDisposal));
        }
        if (keyword != null && !keyword.isBlank()) {
            qw.where(CMR_TASK.BIZ_NO.like(keyword));
        }
        qw.orderBy(CMR_TASK.ID.desc());
        Page<CmrTask> page = taskService.page(qw, query);
        List<TaskVO> vos = page.getRecords().stream().map(this::toVO).toList();
        return ApiPageResult.success(PageResult.of(vos, page.getTotalRow(), query.getPageNum(), query.getPageSize()));
    }

    @Operation(summary = "任务详情（含步骤日志）")
    @PreAuthorize("hasAnyAuthority('cmr:task:detail')")
    @GetMapping("{id}")
    public ApiResult<TaskDetailVO> detail(@PathVariable Long id) {
        CmrTask task = taskService.requireTask(id);
        List<TaskLogVO> logs = taskService.logsOf(id).stream()
                .map(l -> TaskLogVO.builder()
                        .id(l.getId()).taskId(l.getTaskId()).step(l.getStep()).status(l.getStatus())
                        .operatorOutputJson(l.getOperatorOutputJson()).message(l.getMessage())
                        .createTime(l.getCreateTime())
                        .build())
                .toList();
        return ApiResult.success(TaskDetailVO.builder()
                .task(toVO(task))
                .logs(logs)
                .build());
    }

    private TaskVO toVO(CmrTask task) {
        return TaskVO.builder()
                .id(task.getId())
                .bizNo(task.getBizNo())
                .type(task.getType())
                .refId(task.getRefId())
                .refBizNo(refBizNo(task))
                .status(task.getStatus())
                .failType(task.getFailType())
                .failDisposal(task.getFailDisposal())
                .retryCount(task.getRetryCount())
                .currentStep(task.getCurrentStep())
                .errorMsg(task.getErrorMsg())
                .startedTime(task.getStartedTime())
                .finishedTime(task.getFinishedTime())
                .createTime(task.getCreateTime())
                .build();
    }

    private String refBizNo(CmrTask task) {
        if (task.getRefId() == null) {
            return null;
        }
        return switch (task.getType() == null ? "" : task.getType()) {
            case CmrTask.TYPE_BUILD -> {
                CmrDataset ds = datasetMapper.selectOneById(task.getRefId());
                yield ds == null ? null : ds.getBizNo();
            }
            case CmrTask.TYPE_EVAL -> {
                CmrEvalRecord rec = evalRecordMapper.selectOneById(task.getRefId());
                yield rec == null ? null : rec.getBizNo();
            }
            case CmrTask.TYPE_SEARCH -> {
                CmrSearchHistory h = searchHistoryMapper.selectOneById(task.getRefId());
                yield h == null ? null : h.getBizNo();
            }
            default -> null;
        };
    }
}
