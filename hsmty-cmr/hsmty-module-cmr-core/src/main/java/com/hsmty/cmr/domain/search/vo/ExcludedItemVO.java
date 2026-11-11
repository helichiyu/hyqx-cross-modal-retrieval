package com.hsmty.cmr.domain.search.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 结果集下发前被剔除的条目明细（P2「本次共排除 N 条」）。
 *
 * @author cmr
 * @since 2026-11-11
 */
@Data
@Builder
@Schema(description = "结果集下发前被剔除的条目")
public class ExcludedItemVO {

    @Schema(description = "条目业务编号")
    private String itemId;

    @Schema(description = "条目名称")
    private String name;

    @Schema(description = "来源名")
    private String source;

    @Schema(description = "剔除原因：blacklist / quality / strategy")
    private String reason;
}
