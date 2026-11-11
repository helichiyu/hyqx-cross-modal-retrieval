package com.hsmty.cmr.domain.evaluation;

import com.hsmty.core.common.entity.ApiPageResult;
import com.hsmty.core.common.entity.ApiResult;
import com.hsmty.cmr.common.api.PageQuery;
import com.hsmty.cmr.common.api.PageResult;
import com.hsmty.cmr.domain.evaluation.dto.EvaluationCreateDTO;
import com.hsmty.cmr.domain.evaluation.dto.EvaluationReplayDTO;
import com.hsmty.cmr.domain.evaluation.service.EvaluationService;
import com.hsmty.cmr.domain.evaluation.vo.EvaluationDetailVO;
import com.hsmty.cmr.domain.evaluation.vo.EvaluationVO;
import com.hsmty.cmr.domain.evaluation.vo.HardCaseVO;
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
    public ApiResult<EvaluationVO> create(@Valid @RequestBody EvaluationCreateDTO dto) {
        return ApiResult.success(EvaluationVO.from(evaluationService.create(dto.getBaseTaskNo())));
    }

    /**
     * 难例样本集：有点踩的检索任务分页，按被踩条目数倒序。
     *
     * @param query 分页参数
     * @return 难例样本分页
     * @author cmr
     * @since 2026-11-11
     */
    @Operation(summary = "难例样本集（点踩任务分页，按被踩条目数倒序）")
    @PreAuthorize("hasAnyAuthority('cmr:eval:page')")
    @GetMapping("hard-cases")
    public ApiResult<PageResult<HardCaseVO>> hardCases(PageQuery query) {
        return ApiPageResult.success(evaluationService.hardCases(query));
    }

    /**
     * 评测详情：基准 vs 最近重放、退化原因、统一指标与 4 项只读数据检查。
     *
     * @param id 评测主键
     * @return 评测详情
     * @author cmr
     * @since 2026-11-11
     */
    @Operation(summary = "检查评测数据（4 项只读检查：样本够不够 / 重复用 / 有效性 / 评价矛盾）")
    @PreAuthorize("hasAnyAuthority('cmr:eval:page')")
    @GetMapping("check-data")
    public ApiResult<Map<String, Object>> checkData() {
        return ApiResult.success(Map.of("checks", evaluationService.checkData()));
    }

    @Operation(summary = "评测详情（基准 vs 重放 + 退化原因 + 数据检查）")
    @PreAuthorize("hasAnyAuthority('cmr:eval:page')")
    @GetMapping("{id}")
    public ApiResult<EvaluationDetailVO> detail(@PathVariable Long id) {
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
