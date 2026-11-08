package com.hsmty.cmr.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 字典项新增入参（当前用于业务场景维护）。
 */
@Data
public class DictCreateDTO {

    @NotBlank(message = "显示名称不能为空")
    @Size(max = 128, message = "显示名称不能超过 128 字")
    private String label;

    @Size(max = 64, message = "别名不能超过 64 字")
    private String alias;

    @Size(max = 256, message = "描述不能超过 256 字")
    private String remark;
}