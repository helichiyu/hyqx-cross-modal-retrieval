package com.hsmty.cmr.file.entity;

import com.hsmty.db.EntityChangeListener;
import com.hsmty.db.id.SzIdGenerator;
import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文件对象元数据。
 *
 * <p>只存对象存储位置与摘要，文件本体在 S3/MinIO；审计字段由平台
 * {@link EntityChangeListener} 统一填充，业务代码不手写。
 *
 * @author cmr
 * @since 2026-11-10
 */
@Data
@Table(value = "cmr_file_object", onInsert = EntityChangeListener.class, onUpdate = EntityChangeListener.class)
@Schema(description = "文件对象")
public class CmrFileObject implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Generator, value = SzIdGenerator.NAME)
    @Schema(description = "主键")
    private Long id;

    @Schema(description = "业务编号")
    private String bizNo;

    @Schema(description = "业务类型")
    private String bizType;

    @Schema(description = "桶")
    private String bucket;

    @Schema(description = "对象键")
    private String objectKey;

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

    @Schema(description = "创建人ID")
    private Long createId;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新人ID")
    private Long updateId;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @Column(isLogicDelete = true)
    @Schema(description = "删除标记")
    private String delFlag;
}
