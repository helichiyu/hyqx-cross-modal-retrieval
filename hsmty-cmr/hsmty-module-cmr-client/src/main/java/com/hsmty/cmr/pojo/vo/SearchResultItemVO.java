package com.hsmty.cmr.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 检索结果条目。
 *
 * <p>{@code locator}/{@code text} 只用于本次响应展示，不落库（见 02 库表设计保留策略）。
 *
 * @author cmr
 * @since 2026-11-11
 */
@Data
@Schema(description = "检索结果条目")
public class SearchResultItemVO {

    @Schema(description = "名次")
    private Integer rank;

    @Schema(description = "融合分")
    private BigDecimal score;

    @Schema(description = "模态：text/image/audio/video")
    private String modality;

    @Schema(description = "算子条目 ID")
    private String itemId;

    @Schema(description = "源文件逻辑路径")
    private String sourceFile;

    @Schema(description = "来源")
    private String source;

    @Schema(description = "对齐分（原始余弦）")
    private BigDecimal alignScore;

    @Schema(description = "质量等级，继承数据集评定结果，暂无则为空")
    private String qualityGrade;

    @Schema(description = "媒体定位：切片/帧位置")
    private Map<String, Object> locator;

    @Schema(description = "文本段内容")
    private String text;
}
