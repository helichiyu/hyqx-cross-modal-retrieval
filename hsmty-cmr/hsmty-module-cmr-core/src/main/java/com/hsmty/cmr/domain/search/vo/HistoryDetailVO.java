package com.hsmty.cmr.domain.search.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 检索历史详情（含结果集条目快照、策略快照与下发前剔除明细）
 *
 * @author cmr
 * @since 2026-11-03
 */
@Data
@Builder
public class HistoryDetailVO {

    private HistoryVO history;

    private String strategySnapshotJson;

    private List<ResultItemVO> items;

    /** 本次下发前被剔除的条目总数（黑名单/质量/策略过滤） */
    @Schema(description = "本次下发前被剔除的条目总数（黑名单/质量/策略过滤）")
    private Integer excludedCount;

    /** 剔除明细，最多 100 条 */
    @Schema(description = "剔除明细，最多 100 条")
    private List<ExcludedItemVO> excludedItems;
}
