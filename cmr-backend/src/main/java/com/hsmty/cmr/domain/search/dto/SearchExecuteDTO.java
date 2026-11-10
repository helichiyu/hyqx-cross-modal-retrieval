package com.hsmty.cmr.domain.search.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 执行检索入参（query/queryFile 二选一；weights/rerank/topK 不传用场景生效策略）
 *
 * @author cmr
 * @since 2026-11-03
 */
@Data
public class SearchExecuteDTO {

    private String query;

    /** 查询文件逻辑路径（/file/upload 返回值） */
    private String queryFile;

    private String scene;

    /** 检索台选定的策略（不传则按场景取生效策略） */
    private Long strategyId;

    /** 407 澄清选定后的改写 query */
    private String clarifiedQuery;

    /** 目标模态；不传时由 408 按四模态召回 */
    @NotEmpty(message = "目标模态不能为空")
    private List<@Pattern(regexp = "text|image|audio|video", message = "目标模态只能是 text/image/audio/video") String> targetModalities;

    private Boolean clarified = false;

    /** 显式覆盖：四模态权重合计 100 */
    private Map<String, Integer> weights;

    private Boolean rerank;

    @Min(1)
    @Max(100)
    private Integer topK;

    /** 显式覆盖阈值（0.30-0.99），不传用策略阈值 */
    private java.math.BigDecimal threshold;
}
