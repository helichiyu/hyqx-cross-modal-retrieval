package com.hsmty.cmr.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 408m 检索算子入参，与算子说明书字段一一对应。
 *
 * @author cmr
 * @since 2026-11-11
 */
@Data
@Schema(description = "408m 检索算子入参")
public class OperatorSearchDTO {

    @Schema(description = "查询文本")
    private String query;

    @Schema(description = "查询文件路径")
    private String queryFile;

    @Schema(description = "索引目录")
    private String indexDir;

    @Schema(description = "返回条数")
    private Integer topK;

    @Schema(description = "四模态权重")
    private Map<String, Integer> weights;

    @Schema(description = "是否精排")
    private Boolean rerank;

    @Schema(description = "相似度阈值")
    private BigDecimal threshold;

    @Schema(description = "目标模态")
    private List<String> targetModalities;

    @Schema(description = "词权重")
    private Double wordWeight;
}
