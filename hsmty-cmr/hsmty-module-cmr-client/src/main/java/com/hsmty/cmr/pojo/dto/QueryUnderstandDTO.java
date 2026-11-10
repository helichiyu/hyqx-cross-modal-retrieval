package com.hsmty.cmr.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 查询理解入参（407m 算子代理）。
 *
 * <p>{@code query} 与 {@code queryFile} 二选一；文件查询以扩展名识别模态。
 *
 * @author cmr
 * @since 2026-11-10
 */
@Data
@Schema(description = "查询理解入参")
public class QueryUnderstandDTO {

    @Schema(description = "请求唯一标识，缺省由后端生成")
    private String uniqueId;

    @Schema(description = "查询文本，与 queryFile 二选一")
    private String query;

    @Schema(description = "查询文件路径（共享卷相对路径），与 query 二选一")
    private String queryFile;
}
