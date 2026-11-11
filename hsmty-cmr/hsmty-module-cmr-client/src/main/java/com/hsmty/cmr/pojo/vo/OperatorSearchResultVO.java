package com.hsmty.cmr.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 408m 检索算子响应 {@code data} 部分。
 *
 * @author cmr
 * @since 2026-11-11
 */
@Data
@Schema(description = "408m 检索算子响应")
public class OperatorSearchResultVO {

    @Schema(description = "结果集编号")
    private String resultSetId;

    @Schema(description = "查询文本")
    private String query;

    @Schema(description = "查询文件")
    private String queryFile;

    @Schema(description = "查询模态")
    private String queryModality;

    @Schema(description = "结果条数")
    private Integer count;

    @Schema(description = "结果条目")
    private List<OperatorSearchItemVO> items;

    @Schema(description = "策略快照")
    private Map<String, Object> strategy;

    @Schema(description = "模型版本")
    private String modelVersion;

    @Schema(description = "耗时（毫秒）")
    private Double elapsedMs;
}
