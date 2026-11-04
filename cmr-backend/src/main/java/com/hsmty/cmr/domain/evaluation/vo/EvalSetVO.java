package com.hsmty.cmr.domain.evaluation.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 评测集视图（P8 列表）
 *
 * @author cmr
 * @since 2026-11-03
 */
@Data
@Builder
public class EvalSetVO {

    private Long id;

    private String bizNo;

    private String name;

    private String filtersJson;

    private String status;

    private Integer queryCount;

    /** 已标注条数（进度展示） */
    private Long labeledCount;

    private LocalDateTime createTime;
}
