package com.hsmty.cmr.domain.operation.entity;

import com.hsmty.cmr.common.entity.BaseEntity;
import com.hsmty.cmr.config.AuditListener;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 反馈单（FBK；pending→processing→closed，closed 终态）
 *
 * @author cmr
 * @since 2026-11-03
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(value = "cmr_feedback", onInsert = AuditListener.class, onUpdate = AuditListener.class)
public class CmrFeedback extends BaseEntity {

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_HANDLED = "handled";

    public static final String ENTRY_MISMATCH = "mismatch";
    public static final String ENTRY_ZERO = "zero";

    public static final String JUDGMENT_DATA_BAD = "data_bad";
    public static final String JUDGMENT_MATCH_BAD = "match_bad";
    public static final String JUDGMENT_NEED_DATA = "need_data";

    public static final String DISPOSITION_BLOCK = "block";
    public static final String DISPOSITION_STRATEGY = "strategy";
    public static final String DISPOSITION_SUPPLEMENT = "supplement";

    private String bizNo;

    private Long searchId;

    /** 来源任务号 */
    private String sourceTaskNo;

    /** 来源搜索词 */
    private String sourceQuery;

    /** 兼容字段：mismatch/zero */
    private String type;

    /** 入口类型：mismatch/zero，只读 */
    private String entryType;

    /** 勾选的不匹配条目 JSON */
    private String mismatchItemsJson;

    private String description;

    private String status;

    /** 兼容字段：strategy/datasource/backfill */
    private String target;

    /** 处理判断：data_bad/match_bad/need_data */
    private String judgment;

    /** 去向：block/strategy/supplement */
    private String disposition;

    /** 是否已写屏蔽 */
    private Boolean blocked;

    private String resultNote;

    private Long handlerId;
}
