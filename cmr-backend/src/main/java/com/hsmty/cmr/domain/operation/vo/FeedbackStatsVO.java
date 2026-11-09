package com.hsmty.cmr.domain.operation.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 反馈全量状态统计
 *
 * @author cmr
 * @since 2026-11-09
 */
@Data
@Builder
public class FeedbackStatsVO {

    private Long total;

    private Long pending;

    private Long processing;

    private Long closed;
}
