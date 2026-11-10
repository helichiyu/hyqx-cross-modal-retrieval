package com.hsmty.cmr.domain.strategy;

import com.hsmty.cmr.common.api.ApiPageResult;
import com.hsmty.cmr.common.api.ApiResult;
import com.hsmty.cmr.common.api.PageQuery;
import com.hsmty.cmr.common.api.PageResult;
import com.hsmty.cmr.domain.strategy.dto.RollbackDTO;
import com.hsmty.cmr.domain.strategy.dto.StrategyOfflineDTO;
import com.hsmty.cmr.domain.strategy.dto.StrategySaveDTO;
import com.hsmty.cmr.domain.strategy.service.StrategyService;
import com.hsmty.cmr.domain.strategy.vo.StrategyDetailVO;
import com.hsmty.cmr.domain.strategy.vo.StrategyVO;
import com.hsmty.cmr.domain.strategy.vo.StrategyVersionVO;
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

import java.util.List;
import java.util.Map;

/**
 * 策略接口（P5）：草稿/发布/版本/回滚/变更评估/生效策略回显
 *
 * @author cmr
 * @since 2026-11-03
 */
@Tag(name = "策略")
@RestController
@RequestMapping("strategies")
@RequiredArgsConstructor
public class StrategyController {

    private final StrategyService strategyService;

    @Operation(summary = "策略分页")
    @PreAuthorize("hasAnyAuthority('cmr:strategy:page')")
    @GetMapping
    public ApiResult<PageResult<StrategyVO>> page(PageQuery query,
                                                  @RequestParam(required = false) String scene,
                                                  @RequestParam(required = false) String status) {
        return ApiPageResult.success(strategyService.page(query, scene, status));
    }

    @Operation(summary = "向量模型白名单与当前索引模型")
    @PreAuthorize("hasAnyAuthority('cmr:strategy:page')")
    @GetMapping("model-options")
    public ApiResult<Map<String, Object>> modelOptions() {
        return ApiResult.success(strategyService.modelOptions());
    }

    @Operation(summary = "策略一致性检查")
    @PreAuthorize("hasAnyAuthority('cmr:strategy:detail')")
    @GetMapping("{id}/consistency")
    public ApiResult<Map<String, Object>> consistency(@PathVariable Long id) {
        return ApiResult.success(strategyService.consistency(id));
    }

    @Operation(summary = "新建策略（存草稿）")
    @PreAuthorize("hasAnyAuthority('cmr:strategy:create')")
    @PostMapping
    public ApiResult<StrategyVO> create(@Valid @RequestBody StrategySaveDTO dto) {
        return ApiResult.success(strategyService.create(dto));
    }

    @Operation(summary = "策略详情（当前版本+草稿+向量段只读回显）")
    @PreAuthorize("hasAnyAuthority('cmr:strategy:detail')")
    @GetMapping("{id}")
    public ApiResult<StrategyDetailVO> detail(@PathVariable Long id) {
        return ApiResult.success(strategyService.detail(id));
    }

    @Operation(summary = "编辑草稿参数")
    @PreAuthorize("hasAnyAuthority('cmr:strategy:update')")
    @PutMapping("{id}")
    public ApiResult<Void> update(@PathVariable Long id, @Valid @RequestBody StrategySaveDTO dto) {
        strategyService.updateDraft(id, dto);
        return ApiResult.success();
    }

    @Operation(summary = "发布草稿（版本递增立即生效）")
    @PreAuthorize("hasAnyAuthority('cmr:strategy:publish')")
    @PostMapping("{id}/publish")
    public ApiResult<StrategyVersionVO> publish(@PathVariable Long id) {
        return ApiResult.success(strategyService.publish(id));
    }

    @Operation(summary = "放弃草稿")
    @PreAuthorize("hasAnyAuthority('cmr:strategy:update')")
    @PostMapping("{id}/discard-draft")
    public ApiResult<Void> discardDraft(@PathVariable Long id) {
        strategyService.discardDraft(id);
        return ApiResult.success();
    }

    @Operation(summary = "版本历史")
    @PreAuthorize("hasAnyAuthority('cmr:strategy:detail')")
    @GetMapping("{id}/versions")
    public ApiResult<List<StrategyVersionVO>> versions(@PathVariable Long id) {
        return ApiResult.success(strategyService.versionsOf(id));
    }

    @Operation(summary = "两版本参数 diff")
    @PreAuthorize("hasAnyAuthority('cmr:strategy:detail')")
    @GetMapping("{id}/diff")
    public ApiResult<Map<String, Object>> diff(@PathVariable Long id,
                                               @RequestParam Integer from,
                                               @RequestParam Integer to) {
        return ApiResult.success(strategyService.diff(id, from, to));
    }

    @Operation(summary = "回滚到指定版本")
    @PreAuthorize("hasAnyAuthority('cmr:strategy:publish')")
    @PostMapping("{id}/rollback")
    public ApiResult<StrategyVersionVO> rollback(@PathVariable Long id, @Valid @RequestBody RollbackDTO dto) {
        return ApiResult.success(strategyService.rollback(id, dto.getVersionNo()));
    }

    @Operation(summary = "检索台策略下拉（生效策略+参数摘要）")
    @PreAuthorize("hasAnyAuthority('cmr:search:create')")
    @GetMapping("options")
    public ApiResult<List<Map<String, Object>>> options() {
        return ApiResult.success(strategyService.options());
    }

    @Operation(summary = "策略下线（系统预置不可下线）")
    @PreAuthorize("hasAnyAuthority('cmr:strategy:update')")
    @PostMapping("{id}/offline")
    public ApiResult<Void> offline(@PathVariable Long id, @Valid @RequestBody StrategyOfflineDTO dto) {
        strategyService.offline(id, dto.getReason());
        return ApiResult.success();
    }

    @Operation(summary = "策略重新上线（转草稿，需重新发布生效）")
    @PreAuthorize("hasAnyAuthority('cmr:strategy:update')")
    @PostMapping("{id}/online")
    public ApiResult<Void> online(@PathVariable Long id) {
        strategyService.online(id);
        return ApiResult.success();
    }

    @Operation(summary = "场景生效策略（检索台回显）")
    @PreAuthorize("hasAnyAuthority('cmr:search:create')")
    @GetMapping("active")
    public ApiResult<StrategyService.EffectiveStrategy> active(@RequestParam(required = false) String scene) {
        return ApiResult.success(strategyService.effectiveOf(scene));
    }
}
