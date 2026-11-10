package com.hsmty.cmr.domain.operation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 反馈处置入参：判断（数据问题/匹配问题/缺数据）+ 去向（屏蔽/调策略/补数据）
 *
 * @author cmr
 * @since 2026-11-10
 */
@Data
public class FeedbackHandleDTO {

    /** data_bad/match_bad/need_data */
    @NotBlank(message = "处理判断不能为空")
    @Pattern(regexp = "data_bad|match_bad|need_data", message = "处理判断只能是 data_bad/match_bad/need_data")
    private String judgment;

    /** block/strategy/supplement */
    @NotBlank(message = "处置去向不能为空")
    @Pattern(regexp = "block|strategy|supplement", message = "处置去向只能是 block/strategy/supplement")
    private String disposition;

    private String resultNote;
}
