package com.hsmty.cmr.domain.operation;

import com.hsmty.core.common.entity.ApiPageResult;
import com.hsmty.core.common.entity.ApiResult;
import com.hsmty.cmr.common.api.PageQuery;
import com.hsmty.cmr.common.api.PageResult;
import com.hsmty.cmr.domain.operation.dto.FeedbackCreateDTO;
import com.hsmty.cmr.domain.operation.dto.FeedbackHandleDTO;
import com.hsmty.cmr.domain.operation.service.FeedbackService;
import com.hsmty.cmr.domain.operation.vo.FeedbackStatsVO;
import com.hsmty.cmr.domain.operation.vo.FeedbackVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 结果运营接口（P7）：两个入口 + 判断 + 三个去向
 *
 * @author cmr
 * @since 2026-11-03
 */
@Tag(name = "结果运营-反馈")
@RestController
@RequestMapping("feedbacks")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    @Operation(summary = "登记反馈（标记不匹配 / 零结果分析）")
    @PreAuthorize("hasAnyAuthority('cmr:feedback:create')")
    @PostMapping
    public ApiResult<FeedbackVO> create(@Valid @RequestBody FeedbackCreateDTO dto) {
        return ApiResult.success(feedbackService.create(dto));
    }

    @Operation(summary = "反馈统计（待处理/已处理/三去向）")
    @PreAuthorize("hasAnyAuthority('cmr:feedback:page')")
    @GetMapping("stats")
    public ApiResult<FeedbackStatsVO> stats() {
        return ApiResult.success(feedbackService.stats());
    }

    @Operation(summary = "反馈分页（入口/状态/来源任务筛选）")
    @PreAuthorize("hasAnyAuthority('cmr:feedback:page')")
    @GetMapping
    public ApiResult<PageResult<FeedbackVO>> page(PageQuery query,
                                                  @RequestParam(required = false) String entryType,
                                                  @RequestParam(required = false) String status,
                                                  @RequestParam(required = false) String sourceTaskNo) {
        return ApiPageResult.success(feedbackService.page(query, entryType, status, sourceTaskNo));
    }

    @Operation(summary = "处置（判断 + 去向；屏蔽数据同步写屏蔽列表）")
    @PreAuthorize("hasAnyAuthority('cmr:feedback:update')")
    @PostMapping("{id}/handle")
    public ApiResult<Void> handle(@PathVariable Long id, @Valid @RequestBody FeedbackHandleDTO dto) {
        feedbackService.handle(id, dto);
        return ApiResult.success();
    }
}
