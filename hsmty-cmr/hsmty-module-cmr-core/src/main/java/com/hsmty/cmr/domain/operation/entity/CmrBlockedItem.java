package com.hsmty.cmr.domain.operation.entity;

import com.hsmty.cmr.common.entity.BaseEntity;
import com.hsmty.cmr.config.AuditListener;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 条目屏蔽：结果运营「屏蔽数据」写入，结果页渲染前排除（数据流 3.7）
 *
 * @author cmr
 * @since 2026-11-10
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(value = "cmr_blocked_item", onInsert = AuditListener.class, onUpdate = AuditListener.class)
public class CmrBlockedItem extends BaseEntity {

    private String itemId;

    private String itemName;

    private String source;

    private String reason;

    private Long feedbackId;
}
