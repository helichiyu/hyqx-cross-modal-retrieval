package com.hsmty.cmr.domain.search.entity;

import com.hsmty.cmr.common.entity.BaseEntity;
import com.hsmty.cmr.config.AuditListener;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 收藏：只记条目 ID 与元数据，不复制文件本体（数据不出域）
 *
 * @author cmr
 * @since 2026-11-10
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(value = "cmr_favorite", onInsert = AuditListener.class, onUpdate = AuditListener.class)
public class CmrFavorite extends BaseEntity {

    private Long userId;

    private String itemId;

    private String itemName;

    private String source;

    private String taskNo;
}
