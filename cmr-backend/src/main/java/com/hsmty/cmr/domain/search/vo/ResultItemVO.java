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

    private String modality;

    private BigDecimal score;

    private String sourceFile;

    private String locatorJson;

    private String text;

    private Boolean clicked;
}
