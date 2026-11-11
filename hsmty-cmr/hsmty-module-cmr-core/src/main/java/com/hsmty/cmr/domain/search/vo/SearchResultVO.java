package com.hsmty.cmr.domain.search.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 检索结果（含 SRH 编号/任务/策略快照/条目，前端可跳结果页回看）
 *
 * @author cmr
 * @since 2026-11-03
 */
@Data
@Builder
public class SearchResultVO {

    private Long id;

    private String srhNo;

    private String resultSetId;

    private Long taskId;

    private String taskBizNo;

    private String queryModality;

    /** 首个目标模态；为 null 表示本次检索不限定目标模态（四模态召回） */
    @Schema(description = "首个目标模态；为 null 表示不限定目标模态", nullable = true)
    private String targetModality;

    private Integer itemCount;

    private Integer elapsedMs;

    /** 本次策略快照（生效或覆盖后） */
    private Map<String, Object> strategy;

    private List<ResultItemVO> items;
}
