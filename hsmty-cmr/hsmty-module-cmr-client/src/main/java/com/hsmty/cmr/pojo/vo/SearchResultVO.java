package com.hsmty.cmr.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 检索执行结果。
 *
 * @author cmr
 * @since 2026-11-11
 */
@Data
@Schema(description = "检索执行结果")
public class SearchResultVO {

    @Schema(description = "检索历史业务编号")
    private String searchNo;

    @Schema(description = "结果集编号")
    private String resultSetId;

    @Schema(description = "实际使用的查询文本")
    private String query;

    @Schema(description = "查询模态")
    private String queryModality;

    @Schema(description = "结果条数")
    private Integer count;

    @Schema(description = "结果条目")
    private List<SearchResultItemVO> items;

    @Schema(description = "模型版本")
    private String modelVersion;

    @Schema(description = "阈值")
    private BigDecimal threshold;

    @Schema(description = "耗时（毫秒）")
    private Long elapsedMs;
}
