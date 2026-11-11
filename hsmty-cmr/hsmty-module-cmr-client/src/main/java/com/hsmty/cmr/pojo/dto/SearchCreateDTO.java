package com.hsmty.cmr.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 执行检索入参。
 *
 * <p>文本查询与查询文件二选一；权重、阈值、TopK 省略时由服务端取默认值。
 *
 * @author cmr
 * @since 2026-11-11
 */
@Data
@Schema(description = "执行检索入参")
public class SearchCreateDTO {

    @Schema(description = "查询文本，与 queryFile 二选一")
    private String query;

    @Schema(description = "查询文件逻辑路径，与 query 二选一")
    private String queryFile;

    @Schema(description = "业务场景")
    private String scene;

    @Schema(description = "目标模态：text/image/audio/video，省略表示全部")
    private List<String> targetModalities;

    @Schema(description = "是否已完成澄清")
    private Boolean clarified;

    @Schema(description = "澄清后的改写查询，clarified=true 时优先使用")
    private String clarifiedQuery;

    @Schema(description = "四模态融合权重，合计必须为 100")
    private Map<String, Integer> weights;

    @Schema(description = "是否开启精排")
    private Boolean rerank;

    @Schema(description = "返回条数，默认 10")
    private Integer topK;

    @Schema(description = "策略 ID，暂未接入策略域时忽略")
    private Long strategyId;

    @Schema(description = "相似度阈值，0.30–0.99，默认 0.60")
    private BigDecimal threshold;
}
