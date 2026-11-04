package com.hsmty.cmr.domain.search.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 检索历史详情（含结果集条目快照与策略快照）
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
}
