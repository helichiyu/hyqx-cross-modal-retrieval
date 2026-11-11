package com.hsmty.cmr.domain.dataset.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 数据源接入入参：把上游某个 dataset_id 的内容拉到共享卷并触发建库链
 *
 * @author cmr
 * @since 2026-11-10
 */
@Data
@Schema(description = "数据源接入入参")
public class DatasourceIngestDTO {

    @Schema(description = "上游数据集 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "datasetId 不能为空")
    private String datasetId;

    @Schema(description = "本地数据集名称，缺省取上游名称")
    private String name;

    @Schema(description = "版本号，缺省 v1")
    private String version = "v1";

    @Schema(description = "业务场景，缺省 general")
    private String scene = "general";
}
