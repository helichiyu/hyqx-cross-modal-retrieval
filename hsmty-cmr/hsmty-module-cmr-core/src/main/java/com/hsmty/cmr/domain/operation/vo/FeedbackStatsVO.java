package com.hsmty.cmr.domain.operation.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 反馈全量状态统计（P7 顶部指标）
 *
 * @author cmr
 * @since 2026-11-09
 */
@Data
@Builder
public class FeedbackStatsVO {

    private Long total;

    private Long pending;

    private Long handled;

    /** 去向=屏蔽数据 */
    private Long blocked;

    /** 去向=调策略 */
    private Long strategy;

    /** 去向=补数据 */
    private Long supplement;
}
