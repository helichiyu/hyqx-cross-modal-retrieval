package com.hsmty.cmr.domain.search.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 结果集条目视图（sourceFile 为逻辑路径，可经 /file?path= 预览）
 *
 * @author cmr
 * @since 2026-11-03
 */
@Data
@Builder
public class ResultItemVO {

    private Long id;

    private Integer rank;

    private String itemId;

    /** 实时回查的展示名；条目缺失时为“已删除” */
    private String name;

    private String modality;

    private BigDecimal score;

    /** 来源名（摘要/导出展示） */
    private String source;

    /** 对齐分 */
    private BigDecimal alignScore;

    /** 当前用户赞踩：up/down/null */
    private String vote;

    /** 当前用户是否收藏 */
    private Boolean favorited;

    private String sourceFile;

    private String locatorJson;

    private String text;

    private Boolean clicked;

    /** true=检索时索引元数据或条目已不存在 */
    private Boolean deleted;
}
