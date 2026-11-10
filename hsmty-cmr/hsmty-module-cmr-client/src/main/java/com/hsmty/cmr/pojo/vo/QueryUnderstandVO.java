package com.hsmty.cmr.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 查询理解出参。
 *
 * @author cmr
 * @since 2026-11-10
 */
@Data
@Schema(description = "查询理解结果")
public class QueryUnderstandVO {

    @Schema(description = "是否需要歧义澄清")
    private Boolean needClarify;

    @Schema(description = "澄清选项，needClarify=true 时下发")
    private List<ClarifyOptionVO> clarifyOptions;

    @Schema(description = "查询模态：text/image/audio/video")
    private String queryModality;

    @Schema(description = "补充提示（如查询过短）")
    private String hint;
}
