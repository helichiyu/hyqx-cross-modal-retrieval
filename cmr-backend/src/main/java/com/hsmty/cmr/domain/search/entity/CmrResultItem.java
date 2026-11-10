package com.hsmty.cmr.domain.search.entity;

import com.hsmty.cmr.common.entity.BaseEntity;
import com.hsmty.cmr.config.AuditListener;
import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 结果集条目 ID 快照（P2/P9 回看时按索引 meta.db 实时回查名称/预览）
 *
 * @author cmr
 * @since 2026-11-03
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(value = "cmr_result_item", onInsert = AuditListener.class, onUpdate = AuditListener.class)
public class CmrResultItem extends BaseEntity {

    private Long searchId;

    /** 冗余 RST，便于直接按结果集查询 */
    private String resultSetId;

    @Column("result_rank")
    private Integer rank;

    private String itemId;

    /** 条目名称快照（摘要 30 天内可直接展示，不必回查） */
    private String itemName;

    private String modality;

    /** 来源名快照 */
    private String source;

    private BigDecimal score;

    /** 对齐分（算子返回时） */
    private BigDecimal alignScore;

    /** 冗余：是否被点击（维护自事件表） */
    private Boolean clicked;
}
