package com.hsmty.cmr.domain.dataset;

import com.hsmty.core.common.entity.ApiPageResult;
import com.hsmty.core.common.entity.ApiResult;
import com.hsmty.cmr.common.api.PageQuery;
import com.hsmty.cmr.common.api.PageResult;
import com.hsmty.cmr.domain.dataset.dto.DatasetCreateDTO;
import com.hsmty.cmr.domain.dataset.service.DatasetService;
import com.hsmty.cmr.domain.dataset.vo.DatasetDetailVO;
import com.hsmty.cmr.domain.dataset.vo.DatasetVO;
import com.hsmty.cmr.domain.task.vo.TaskVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 库管理接口（P4）：数据集登记/重建
 *
 * @author cmr
 * @since 2026-11-03
 */
@Tag(name = "库管理-数据集")
@RestController
@RequestMapping("datasets")
@RequiredArgsConstructor
public class DatasetController {

    private final DatasetService datasetService;

    @Operation(summary = "数据集分页")
    @PreAuthorize("hasAnyAuthority('cmr:dataset:page')")
    @GetMapping
    public ApiResult<PageResult<DatasetVO>> page(PageQuery query,
                                                 @RequestParam(required = false) String status,
                                                 @RequestParam(required = false) String scene,
                                                 @RequestParam(required = false) String name) {
        return ApiPageResult.success(datasetService.page(query, status, scene, name));
    }

    @Operation(summary = "登记接入（multipart：file=zip 或 datasetPath 二选一，异步跑建库链）")
    @PreAuthorize("hasAnyAuthority('cmr:dataset:create')")
    @PostMapping
    public ApiResult<DatasetVO> create(@Valid @ModelAttribute DatasetCreateDTO dto) {
        return ApiResult.success(datasetService.create(dto));
    }

    @Operation(summary = "数据集详情（含索引历史与任务）")
    @PreAuthorize("hasAnyAuthority('cmr:dataset:detail')")
    @GetMapping("{id}")
    public ApiResult<DatasetDetailVO> detail(@PathVariable Long id) {
        return ApiResult.success(datasetService.detail(id));
    }

    @Operation(summary = "重建（全链重跑，新任务）")
    @PreAuthorize("hasAnyAuthority('cmr:dataset:create')")
    @PostMapping("{id}/rebuild")
    public ApiResult<TaskVO> rebuild(@PathVariable Long id) {
        return ApiResult.success(datasetService.rebuild(id));
    }
}
