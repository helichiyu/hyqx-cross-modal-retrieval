package com.hsmty.cmr.domain.dataset.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 数据源登记入参
 *
 * @author cmr
 * @since 2026-11-03
 */
@Data
public class DatasourceCreateDTO {

    @NotBlank(message = "数据源名称不能为空")
    private String name;

    /** http/https 接口地址（protocol=http 时必填） */
    private String endpoint;

    /** pull/http/api=主动拉取；push/upload=推送接收 */
    private String protocol = "upload";

    /** none/token/basic */
    private String authType = "none";

    /** token 值或 basic 凭据（AES 加密落库） */
    private String authConfig;

    private String remark;
}
