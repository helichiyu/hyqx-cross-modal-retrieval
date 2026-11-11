package com.hsmty.cmr.domain.search.entity;

import com.hsmty.cmr.common.entity.BaseEntity;
import com.hsmty.cmr.config.AuditListener;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 点击事件（"无点击"筛选依据，P2 埋点）
 *
 * @author cmr
 * @since 2026-11-03
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(value = "cmr_click_event", onInsert = AuditListener.class, onUpdate = AuditListener.class)
public class CmrClickEvent extends BaseEntity {

    private Long searchId;

    private Integer resultRank;

    private String itemId;

    private Long userId;

    private LocalDateTime clickedTime;
}
