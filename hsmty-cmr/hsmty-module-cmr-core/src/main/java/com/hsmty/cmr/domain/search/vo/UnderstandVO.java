package com.hsmty.cmr.domain.search.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 查询理解结果（needClarify=true 时前端展示 clarifyOptions 浮层）
 *
 * @author cmr
 * @since 2026-11-03
 */
@Data
@Builder
public class UnderstandVO {

    private Boolean needClarify;

    private List<String> clarifyOptions;

    private String queryModality;

    private String hint;

    private String queryFile;
}
