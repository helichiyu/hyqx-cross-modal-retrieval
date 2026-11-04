package com.hsmty.cmr.domain.operation;

import com.hsmty.cmr.common.api.ApiPageResult;
import com.hsmty.cmr.common.api.ApiResult;
import com.hsmty.cmr.common.api.PageQuery;
import com.hsmty.cmr.common.api.PageResult;
import com.hsmty.cmr.domain.operation.dto.FeedbackCreateDTO;
import com.hsmty.cmr.domain.operation.dto.FeedbackProcessDTO;
import com.hsmty.cmr.domain.operation.service.FeedbackService;
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
 * 运营接口（P7）：反馈单四类型 + 去向流转
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

    @Operation(summary = "登记反馈（从检索结果页发起）")
    @PreAuthorize("hasAnyAuthority('cmr:feedback:create')")
    @PostMapping
    public ApiResult<FeedbackVO> create(@Valid @RequestBody FeedbackCreateDTO dto) {
        return ApiResult.success(feedbackService.create(dto));
    }

    @Operation(summary = "反馈分页（类型/状态筛选）")
    @PreAuthorize("hasAnyAuthority('cmr:feedback:page')")
    @GetMapping
    public ApiResult<PageResult<FeedbackVO>> page(PageQuery query,
                                                  @RequestParam(required = false) String type,
                                                  @RequestParam(required = false) String status) {
        return ApiPageResult.success(feedbackService.page(query, type, status));
    }

    @Operation(summary = "开始处理（回填去向）")
    @PreAuthorize("hasAnyAuthority('cmr:feedback:update')")
    @PostMapping("{id}/process")
    public ApiResult<Void> process(@PathVariable Long id, @RequestBody(required = false) FeedbackProcessDTO dto) {
        feedbackService.process(id, dto == null ? null : dto.getTarget());
        return ApiResult.success();
    }

    @Operation(summary = "处理闭环（终态）")
    @PreAuthorize("hasAnyAuthority('cmr:feedback:update')")
    @PostMapping("{id}/close")
    public ApiResult<Void> close(@PathVariable Long id, @RequestBody(required = false) FeedbackProcessDTO dto) {
        feedbackService.close(id, dto == null ? null : dto.getResultNote());
        return ApiResult.success();
    }
}
