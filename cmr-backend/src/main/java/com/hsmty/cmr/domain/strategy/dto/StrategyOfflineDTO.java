package com.hsmty.cmr.domain.strategy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
}
