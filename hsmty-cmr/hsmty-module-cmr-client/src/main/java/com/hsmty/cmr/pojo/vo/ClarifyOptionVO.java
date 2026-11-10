package com.hsmty.cmr.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 歧义澄清选项。
 *
 * @author cmr
 * @since 2026-11-10
 */
@Data
@Schema(description = "歧义澄清选项")
public class ClarifyOptionVO {

    @Schema(description = "意图说明")
    private String intent;

    @Schema(description = "改写后的查询")
    private String query;

    @Schema(description = "提示文案")
    private String hint;
}
