package com.hsmty.cmr.domain.dataset;

import com.hsmty.core.common.entity.ApiResult;
import com.hsmty.cmr.domain.dataset.entity.CmrDatasourceBlacklist;
import com.hsmty.cmr.domain.dataset.service.DatasourceBlacklistService;
import com.hsmty.cmr.domain.dataset.vo.DatasourceBlacklistSummaryVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 数据源黑名单接口（P4）：整源排除，可随时移出
 *
 * @author cmr
 * @since 2026-11-10
 */
@Tag(name = "库管理-黑名单")
@RestController
@RequestMapping("datasource-blacklist")
@RequiredArgsConstructor
public class DatasourceBlacklistController {

    private final DatasourceBlacklistService blacklistService;

    @Operation(summary = "黑名单列表")
    @PreAuthorize("hasAnyAuthority('cmr:dataset:page')")
    @GetMapping
    public ApiResult<List<CmrDatasourceBlacklist>> list() {
        return ApiResult.success(blacklistService.list());
    }

    /**
     * 按来源名聚合黑名单条数，供抽屉按来源展示。
     *
     * @return 来源名与条数
     */
    @Operation(summary = "按来源名汇总黑名单条数")
    @PreAuthorize("hasAnyAuthority('cmr:dataset:page')")
    @GetMapping("summary")
    public ApiResult<List<DatasourceBlacklistSummaryVO>> summary() {
        return ApiResult.success(blacklistService.summary());
    }

    @Operation(summary = "加入黑名单（按 datasetId 去重，检索按来源名整源排除）")
    @PreAuthorize("hasAnyAuthority('cmr:dataset:update')")
    @PostMapping
    public ApiResult<CmrDatasourceBlacklist> add(@Valid @RequestBody BlacklistDTO dto) {
        return ApiResult.success(blacklistService.add(
                dto.getDatasetId(), dto.getName(), dto.getSourceName(), dto.getEndpoint()));
    }

    @Operation(summary = "移出黑名单")
    @PreAuthorize("hasAnyAuthority('cmr:dataset:update')")
    @DeleteMapping("{id}")
    public ApiResult<Void> remove(@PathVariable Long id) {
        blacklistService.remove(id);
        return ApiResult.success();
    }

    /**
     * 黑名单入参：datasetId 必填，其余字段可选（null 表示不更新/未知）。
     */
    @Data
    public static class BlacklistDTO {

        @Schema(description = "上游数据集 ID", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "datasetId 不能为空")
        private String datasetId;

        @Schema(description = "数据集名称")
        private String name;

        @Schema(description = "来源名，结果页按此匹配排除")
        private String sourceName;

        @Schema(description = "数据源地址，须以 http:// 或 https:// 开头且不含空格")
        private String endpoint;
    }
}
