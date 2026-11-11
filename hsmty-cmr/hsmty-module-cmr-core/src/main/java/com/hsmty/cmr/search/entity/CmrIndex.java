package com.hsmty.cmr.search.entity;

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
 * 索引元数据。
 *
 * <p>同一场景同一时刻只有一条 {@code active}；检索时优先取激活索引的目录，
 * 没有激活索引才回落到 {@code cmr.search.index-dir} 配置。
 *
 * @author cmr
 * @since 2026-11-11
 */
@Data
@Table(value = "cmr_index", onInsert = EntityChangeListener.class, onUpdate = EntityChangeListener.class)
@Schema(description = "索引")
public class CmrIndex implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Generator, value = SzIdGenerator.NAME)
    @Schema(description = "主键")
    private Long id;

    @Schema(description = "所属数据集")
    private Long datasetId;

    @Schema(description = "逻辑目录")
    private String indexDir;

    @Schema(description = "向量模型版本")
    private String modelVersion;

    @Schema(description = "状态：candidate/active/retired")
    private String status;

    @Schema(description = "分模态条数 JSON")
    private String countsJson;

    @Schema(description = "投产校验结果 JSON")
    private String checkResultJson;

    @Schema(description = "激活时间")
    private LocalDateTime activatedTime;

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
