package com.hsmty.cmr.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 算子服务统一响应信封。
 *
 * <p>算子侧协议为 {@code {code, msg, data}}，成功时 {@code code=0}，
 * 与平台 {@code ApiResult} 不同，不能混用。
 *
 * @param <T> 业务数据类型
 * @author cmr
 * @since 2026-11-10
 */
@Data
@Schema(description = "算子服务响应")
public class OperatorResult<T> {

    /** 成功码 */
    public static final int SUCCESS_CODE = 0;

    @Schema(description = "算子返回码，0 为成功")
    private Integer code;

    @Schema(description = "算子返回信息")
    private String msg;

    @Schema(description = "业务数据")
    private T data;

    /**
     * 构造失败响应，用于降级实现，不伪造业务数据。
     *
     * @param msg 失败原因
     * @param <T> 数据类型
     * @return 失败响应
     */
    public static <T> OperatorResult<T> failure(String msg) {
        OperatorResult<T> result = new OperatorResult<>();
        result.setCode(-1);
        result.setMsg(msg);
        return result;
    }
}
