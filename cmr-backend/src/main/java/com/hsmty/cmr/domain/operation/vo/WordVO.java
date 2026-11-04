package com.hsmty.cmr.domain.operation.vo;

import com.hsmty.cmr.domain.operation.entity.CmrRecommendWord;
import lombok.Builder;
import lombok.Data;

/**
 * 推荐词视图（P7/P1 联想）
 *
 * @author cmr
 * @since 2026-11-03
 */
@Data
@Builder
public class WordVO {

    private Long id;

    private String bizNo;

    private String word;

    private String scene;

    private Boolean enabled;

    private Integer sort;

    public static WordVO from(CmrRecommendWord e) {
        return WordVO.builder()
                .id(e.getId()).bizNo(e.getBizNo()).word(e.getWord())
                .scene(e.getScene()).enabled(e.getEnabled()).sort(e.getSort())
                .build();
    }
}
