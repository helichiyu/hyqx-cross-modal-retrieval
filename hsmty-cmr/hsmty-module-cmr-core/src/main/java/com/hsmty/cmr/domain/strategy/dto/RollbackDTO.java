package com.hsmty.cmr.domain.strategy.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 策略回滚入参
 *
 * @author cmr
 * @since 2026-11-03
 */
@Data
public class RollbackDTO {

    @NotNull(message = "目标版本号不能为空")
    private Integer versionNo;
}
