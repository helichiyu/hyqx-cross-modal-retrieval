package com.hsmty.cmr.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 408m 检索算子的单条结果。
 *
 * @author cmr
 * @since 2026-11-11
 */
@Data
@Schema(description = "408m 检索算子结果条目")
public class OperatorSearchItemVO {

    @Schema(description = "名次")
    private Integer rank;

    @Schema(description = "融合分")
    private BigDecimal score;

    @Schema(description = "模态")
    private String modality;

    @Schema(description = "算子条目 ID")
    private String itemId;

    @Schema(description = "源文件")
    private String sourceFile;

    @Schema(description = "来源")
    private String source;

    @Schema(description = "对齐分")
    private BigDecimal alignScore;

    @Schema(description = "媒体定位")
    private Map<String, Object> locator;

    @Schema(description = "文本内容")
    private String text;
}
