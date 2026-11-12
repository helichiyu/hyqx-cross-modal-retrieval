package com.hsmty.cmr.domain.dataset;

import com.hsmty.core.common.entity.ApiResult;
import com.hsmty.cmr.domain.dataset.dto.DatasourceCreateDTO;
import com.hsmty.cmr.domain.dataset.dto.DatasourceIngestDTO;
import com.hsmty.cmr.domain.dataset.service.DatasourceService;
import com.hsmty.cmr.domain.dataset.vo.DatasourceTestVO;
import com.hsmty.cmr.domain.dataset.vo.DatasourceVO;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 库管理接口（P4）：数据源登记与列表
 *
 * @author cmr
 * @since 2026-11-03
 */
@Tag(name = "库管理-数据源")
@RestController
@RequestMapping("datasources")
@RequiredArgsConstructor
public class DatasourceController {

    private final DatasourceService datasourceService;

    @Operation(summary = "数据源列表")
    @PreAuthorize("hasAnyAuthority('cmr:dataset:page')")
    @GetMapping
    public ApiResult<List<DatasourceVO>> list() {
        return ApiResult.success(datasourceService.list());
    }

    @Operation(summary = "登记数据源")
    @PreAuthorize("hasAnyAuthority('cmr:dataset:create')")
    @PostMapping
    public ApiResult<DatasourceVO> create(@Valid @RequestBody DatasourceCreateDTO dto) {
        return ApiResult.success(datasourceService.create(dto));
    }

    @Operation(summary = "测试数据源连接")
    @PreAuthorize("hasAnyAuthority('cmr:dataset:create')")
    @PostMapping("{id}/test")
    public ApiResult<DatasourceTestVO> test(@PathVariable Long id) {
        return ApiResult.success(datasourceService.test(id));
    }

    @Operation(summary = "拉取上游数据集列表（含 4 项必填校验）")
    @PreAuthorize("hasAnyAuthority('cmr:dataset:page')")
    @GetMapping("{id}/datasets")
    public ApiResult<java.util.Map<String, Object>> datasets(@PathVariable Long id) {
        return ApiResult.success(datasourceService.remoteDatasets(id));
    }

    @Operation(summary = "同步元数据（只发现，不建库；黑名单来源整源跳过）")
    @PreAuthorize("hasAnyAuthority('cmr:dataset:update')")
    @PostMapping("{id}/sync")
    public ApiResult<java.util.Map<String, Object>> sync(@PathVariable Long id) {
        return ApiResult.success(datasourceService.sync(id));
    }

    @Operation(summary = "接入上游数据集（只落元数据 manifest，触发 401→405 建库链）")
    @PreAuthorize("hasAnyAuthority('cmr:dataset:create')")
    @PostMapping("{id}/ingest")
    public ApiResult<com.hsmty.cmr.domain.dataset.vo.DatasetVO> ingest(@PathVariable Long id,
                                                                      @Valid @RequestBody DatasourceIngestDTO dto) {
        return ApiResult.success(datasourceService.ingest(id, dto));
    }

    @Operation(summary = "数据源接入契约（只读面板）")
    @PreAuthorize("hasAnyAuthority('cmr:dataset:page')")
    @GetMapping("contract")
    public ApiResult<java.util.Map<String, Object>> contract() {
        return ApiResult.success(datasourceService.contract());
    }
}
