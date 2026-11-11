package com.hsmty.cmr.domain.dataset.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 黑名单来源汇总（P4 抽屉按来源显示条数）。
 *
 * @author cmr
 * @since 2026-11-11
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DatasourceBlacklistSummaryVO {

    @Schema(description = "来源名")
    private String sourceName;

    @Schema(description = "该来源下的黑名单记录数")
    private Integer count;
}
