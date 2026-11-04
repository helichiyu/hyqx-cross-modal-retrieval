package com.hsmty.cmr.domain.evaluation.entity;

import com.hsmty.cmr.common.entity.BaseEntity;
import com.hsmty.cmr.config.AuditListener;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 评测集条目（来源检索历史的问题 query，人工标注标准答案）
 *
 * @author cmr
 * @since 2026-11-03
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(value = "cmr_eval_item", onInsert = AuditListener.class, onUpdate = AuditListener.class)
public class CmrEvalItem extends BaseEntity {

    private Long evalSetId;

    /** 溯源检索历史 */
    private Long searchId;

    private String query;

    /** 标准答案 JSON 数组（itemId/文件名） */
    private String answersJson;

    private Boolean labeled;
}
