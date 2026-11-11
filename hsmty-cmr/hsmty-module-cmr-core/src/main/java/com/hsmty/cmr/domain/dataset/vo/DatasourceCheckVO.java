package com.hsmty.cmr.domain.dataset.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 数据源必填项逐字段检测结果（P4 测试连接面板）。
 *
 * @author cmr
 * @since 2026-11-11
 */
@Data
@Builder
public class DatasourceCheckVO {

    /** dataset_id/name/item_count/modals */
    @Schema(description = "字段名：dataset_id/name/item_count/modals")
    private String field;

    @Schema(description = "字段中文名")
    private String label;

    @Schema(description = "该字段是否齐全")
    private Boolean ok;

    @Schema(description = "命中值或缺失说明；无补充说明时为 null")
    private String detail;
}
