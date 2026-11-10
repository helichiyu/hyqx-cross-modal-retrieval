package com.hsmty.cmr.pojo.dto;

import com.hsmty.core.common.entity.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 文件分页查询参数。
 *
 * <p>分页字段统一使用平台 {@link PageQuery} 的 {@code page} / {@code limit}。
 *
 * @author cmr
 * @since 2026-11-10
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "文件分页查询")
public class FileObjectListDTO extends PageQuery {

    @Schema(description = "文件名关键字")
    private String keyword;

    @Schema(description = "状态")
    private String status;
}
