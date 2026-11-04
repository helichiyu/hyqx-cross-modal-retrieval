package com.hsmty.cmr.domain.search.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

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

    /** 407 澄清选定后的改写 query */
    private String clarifiedQuery;

    private Boolean clarified = false;

    /** 显式覆盖：四模态权重合计 100 */
    private Map<String, Integer> weights;

    private Boolean rerank;

    @Min(1)
    @Max(100)
    private Integer topK;
}
