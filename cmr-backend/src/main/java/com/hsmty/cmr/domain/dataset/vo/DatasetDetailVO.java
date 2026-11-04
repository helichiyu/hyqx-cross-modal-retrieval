package com.hsmty.cmr.domain.dataset.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 数据集详情（P4：状态/评分/模态统计/报告/索引历史/任务列表）
 *
 * @author cmr
 * @since 2026-11-03
 */
@Data
@Builder
public class DatasetDetailVO {

    private DatasetVO dataset;

    private String metaJson;

    private List<IndexVO> indexes;

    /** 建库任务（含 bizNo，前端跳任务详情） */
    private List<com.hsmty.cmr.domain.task.vo.TaskVO> tasks;
}
