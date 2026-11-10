package com.hsmty.cmr.domain.evaluation;

import com.hsmty.cmr.common.api.ApiPageResult;
import com.hsmty.cmr.common.api.ApiResult;
import com.hsmty.cmr.common.api.PageQuery;
import com.hsmty.cmr.common.api.PageResult;
import com.hsmty.cmr.domain.evaluation.dto.EvaluationCreateDTO;
import com.hsmty.cmr.domain.evaluation.dto.EvaluationReplayDTO;
import com.hsmty.cmr.domain.evaluation.entity.CmrEvaluation;
import com.hsmty.cmr.domain.evaluation.service.EvaluationService;
import com.hsmty.cmr.domain.search.vo.SearchResultVO;
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

import java.util.Map;

/**
 * 效果评测接口（P8）：评测列表、基准登记、重放、对比与退化判定
 *
 * @author cmr
 * @since 2026-11-10
 */
@Tag(name = "效果评测")
@RestController
@RequestMapping("evaluations")
@RequiredArgsConstructor
public class EvaluationsController {

    private final EvaluationService evaluationService;

    @Operation(summary = "评测分页（含结论与最近重放）")
    @PreAuthorize("hasAnyAuthority('cmr:eval:page')")
    @GetMapping
    public ApiResult<PageResult<Map<String, Object>>> page(PageQuery query,
                                                           @RequestParam(required = false) String keyword) {
        return ApiPageResult.success(evaluationService.page(query, keyword));
    }

    @Operation(summary = "以历史检索为基准登记评测")
    @PreAuthorize("hasAnyAuthority('cmr:eval:create')")
    @PostMapping
    public ApiResult<CmrEvaluation> create(@Valid @RequestBody EvaluationCreateDTO dto) {
        return ApiResult.success(evaluationService.create(dto.getBaseTaskNo()));
    }

    @Operation(summary = "评测详情（基准 vs 重放 + 退化原因）")
    @PreAuthorize("hasAnyAuthority('cmr:eval:page')")
    @GetMapping("{id}")
    public ApiResult<Map<String, Object>> detail(@PathVariable Long id) {
        return ApiResult.success(evaluationService.detail(id));
    }

    @Operation(summary = "重放（可指定待比较策略）")
    @PreAuthorize("hasAnyAuthority('cmr:eval:create')")
    @PostMapping("{id}/replay")
    public ApiResult<SearchResultVO> replay(@PathVariable Long id,
                                            @RequestBody(required = false) EvaluationReplayDTO dto) {
        return ApiResult.success(evaluationService.replay(id, dto == null ? null : dto.getStrategyId()));
    }
}
