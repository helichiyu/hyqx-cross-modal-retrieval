package com.hsmty.cmr.domain.operation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 反馈处置入参：判断（数据问题/匹配问题/缺数据）+ 去向（屏蔽/调策略/补数据）
 * <p>
 * 固定映射（服务端强校验）：zero → need_data/supplement；mismatch → data_bad/block 或 match_bad/strategy。
 *
 * @author cmr
 * @since 2026-11-11
 */
@Data
public class FeedbackHandleDTO {

    /** 处理判断：data_bad/match_bad/need_data */
    @Schema(description = "处理判断：data_bad/match_bad/need_data", example = "data_bad")
    @NotBlank(message = "处理判断不能为空")
    @Pattern(regexp = "data_bad|match_bad|need_data", message = "处理判断只能是 data_bad/match_bad/need_data")
    private String judgment;

    /** 处置去向：block/strategy/supplement */
    @Schema(description = "处置去向：block/strategy/supplement", example = "block")
    @NotBlank(message = "处置去向不能为空")
    @Pattern(regexp = "block|strategy|supplement", message = "处置去向只能是 block/strategy/supplement")
    private String disposition;

    /** 处置备注 */
    @Schema(description = "处置备注")
    private String resultNote;
}
