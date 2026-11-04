package com.hsmty.cmr.domain.evaluation.vo;

import com.hsmty.cmr.domain.evaluation.entity.CmrEvalItem;
import lombok.Builder;
import lombok.Data;

/**
 * 评测集条目视图（query/答案/已标）
 *
 * @author cmr
 * @since 2026-11-03
 */
@Data
@Builder
public class EvalItemVO {

    private Long id;

    private Long evalSetId;

    private Long searchId;

    private String query;

    private String answersJson;

    private Boolean labeled;

    public static EvalItemVO from(CmrEvalItem e) {
        return EvalItemVO.builder()
                .id(e.getId()).evalSetId(e.getEvalSetId()).searchId(e.getSearchId())
                .query(e.getQuery()).answersJson(e.getAnswersJson()).labeled(e.getLabeled())
                .build();
    }
}
