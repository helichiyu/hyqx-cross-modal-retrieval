package com.hsmty.cmr.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文件对象出参。
 *
 * @author cmr
 * @since 2026-11-10
 */
@Data
@Schema(description = "文件对象")
public class FileObjectVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键（JSON 中按字符串下发）")
    private Long id;

    @Schema(description = "业务编号")
    private String bizNo;

    @Schema(description = "业务类型")
    private String bizType;

    @Schema(description = "原始文件名")
    private String originalName;

    @Schema(description = "MIME 类型")
    private String contentType;

    @Schema(description = "字节数")
    private Long size;

    @Schema(description = "SHA-256 摘要")
    private String sha256;

    @Schema(description = "存储类型：local/s3")
    private String storeType;

    @Schema(description = "状态")
    private String status;

    @Schema(description = "创建时间")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
