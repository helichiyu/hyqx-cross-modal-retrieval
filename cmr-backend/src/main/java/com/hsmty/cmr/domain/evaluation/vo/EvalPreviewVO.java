package com.hsmty.cmr.domain.evaluation.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评测集创建前候选预览
 *
 * @author cmr
 * @since 2026-11-09
 */
@Data
@Builder
public class EvalPreviewVO {

    private Long count;

    private List<Item> items;

    @Data
    @Builder
    public static class Item {
        private Long searchId;
        private String searchBizNo;
        private String query;
        private String queryModality;
        private String targetModality;
        private Integer itemCount;
        private LocalDateTime createTime;
    }
}
