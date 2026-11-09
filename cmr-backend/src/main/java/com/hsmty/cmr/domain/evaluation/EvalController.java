package com.hsmty.cmr.domain.evaluation;

import com.hsmty.cmr.common.api.ApiPageResult;
import com.hsmty.cmr.common.api.ApiResult;
import com.hsmty.cmr.common.api.PageQuery;
import com.hsmty.cmr.common.api.PageResult;
import com.hsmty.cmr.domain.evaluation.dto.EvalRunDTO;
import com.hsmty.cmr.domain.evaluation.dto.EvalSetCreateDTO;
import com.hsmty.cmr.domain.evaluation.dto.LabelDTO;
import com.hsmty.cmr.domain.evaluation.service.EvalService;
import com.hsmty.cmr.domain.evaluation.vo.EvalPreviewVO;
import com.hsmty.cmr.domain.evaluation.vo.EvalRecordVO;
import com.hsmty.cmr.domain.evaluation.vo.EvalSetVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 评测接口（P8）：评测集创建/标注/完成、评测执行、历史与对比
 *
 * @author cmr
 * @since 2026-11-03
 */
@Tag(name = "评测")
@RestController
@RequestMapping
@RequiredArgsConstructor
public class EvalController {

    private final EvalService evalService;

    @Operation(summary = "评测集分页")
    @PreAuthorize("hasAnyAuthority('cmr:eval:page')")
    @GetMapping("eval-sets")
    public ApiResult<PageResult<EvalSetVO>> sets(PageQuery query,
                                                 @RequestParam(required = false) String name,
                                                 @RequestParam(required = false) String status) {
        return ApiPageResult.success(evalService.page(query, name, status));
    }

    @Operation(summary = "创建评测集前候选预览")
    @PreAuthorize("hasAnyAuthority('cmr:eval:create')")
    @GetMapping("eval-sets/preview")
    public ApiResult<EvalPreviewVO> preview(@RequestParam(required = false, defaultValue = "false") boolean zeroResult,
                                            @RequestParam(required = false, defaultValue = "false") boolean noClick,
                                            @RequestParam(required = false, defaultValue = "false") boolean reSearch) {
        return ApiResult.success(evalService.preview(zeroResult, noClick, reSearch));
    }

    @Operation(summary = "创建评测集（三类筛选至少一项）")
    @PreAuthorize("hasAnyAuthority('cmr:eval:create')")
    @PostMapping("eval-sets")
    public ApiResult<EvalSetVO> create(@Valid @RequestBody EvalSetCreateDTO dto) {
        return ApiResult.success(evalService.create(dto));
    }

    @Operation(summary = "评测集详情（含条目分页）")
    @PreAuthorize("hasAnyAuthority('cmr:eval:detail')")
    @GetMapping("eval-sets/{id}")
    public ApiResult<Map<String, Object>> detail(@PathVariable Long id, PageQuery query) {
        return ApiResult.success(evalService.detail(id, query));
    }

    @Operation(summary = "标注条目（写标准答案）")
    @PreAuthorize("hasAnyAuthority('cmr:eval:update')")
    @PutMapping("eval-sets/{id}/items/{itemId}")
    public ApiResult<Void> label(@PathVariable Long id, @PathVariable Long itemId,
                                 @Valid @RequestBody LabelDTO dto) {
        evalService.label(id, itemId, dto.getAnswers());
        return ApiResult.success();
    }

    @Operation(summary = "完成标注（每条已标才允许）")
    @PreAuthorize("hasAnyAuthority('cmr:eval:update')")
    @PostMapping("eval-sets/{id}/complete")
    public ApiResult<Void> complete(@PathVariable Long id) {
        evalService.complete(id);
        return ApiResult.success();
    }

    @Operation(summary = "执行评测（异步 409）")
    @PreAuthorize("hasAnyAuthority('cmr:eval:run')")
    @PostMapping("eval-runs")
    public ApiResult<EvalRecordVO> run(@Valid @RequestBody EvalRunDTO dto) {
        return ApiResult.success(evalService.run(dto));
    }

    @Operation(summary = "评测历史分页")
    @PreAuthorize("hasAnyAuthority('cmr:eval:page')")
    @GetMapping("eval-runs")
    public ApiResult<PageResult<EvalRecordVO>> records(PageQuery query,
                                                       @RequestParam(required = false) Long evalSetId) {
        return ApiPageResult.success(evalService.records(query, evalSetId));
    }

    @Operation(summary = "评测报告（指标/验收线/未达标/逐条明细）")
    @PreAuthorize("hasAnyAuthority('cmr:eval:detail')")
    @GetMapping("eval-runs/{id}")
    public ApiResult<EvalRecordVO> recordDetail(@PathVariable Long id) {
        return ApiResult.success(evalService.recordDetail(id));
    }
}
