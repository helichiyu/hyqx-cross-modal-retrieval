package com.hsmty.cmr.domain.strategy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 策略下线入参
 *
 * @author cmr
 * @since 2026-11-10
 */
@Data
public class StrategyOfflineDTO {

    /** tuning/poor/replaced/other */
    @NotBlank(message = "下线原因不能为空")
    @Pattern(regexp = "tuning|poor|replaced|other", message = "下线原因只能是 tuning/poor/replaced/other")
    private String reason;

    /** 选填备注，随下线动作留痕（最长 200 字） */
    @Size(max = 200, message = "下线备注不能超过 200 字")
    private String remark;
}
