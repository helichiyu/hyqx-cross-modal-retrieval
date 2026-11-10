package com.hsmty.cmr.domain.search.entity;

import com.hsmty.cmr.common.entity.BaseEntity;
import com.hsmty.cmr.config.AuditListener;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 单条赞/踩：按「任务号 + 条目号 + 用户」唯一，封存后只读（数据流 3.2）
 *
 * @author cmr
 * @since 2026-11-10
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(value = "cmr_item_vote", onInsert = AuditListener.class, onUpdate = AuditListener.class)
public class CmrItemVote extends BaseEntity {

    public static final String VOTE_UP = "up";
    public static final String VOTE_DOWN = "down";

    private String taskNo;

    private Long searchId;

    private String itemId;

    /** up/down */
    private String vote;

    private Long userId;
}
