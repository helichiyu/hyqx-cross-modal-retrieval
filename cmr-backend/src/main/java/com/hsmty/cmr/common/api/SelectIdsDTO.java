package com.hsmty.cmr.common.api;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 批量删除入参
 *
 * @author cmr
 * @since 2026-11-03
 */
@Data
public class SelectIdsDTO {

    @NotEmpty(message = "ids 不能为空")
    private List<Long> ids;
}
