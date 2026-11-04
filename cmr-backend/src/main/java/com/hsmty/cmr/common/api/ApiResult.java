package com.hsmty.cmr.common.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一接口返回信封：{@code {"code":0,"msg":"success","data":...}}，code=1 业务失败
 *
 * @author cmr
 * @since 2026-11-03
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResult<T> {

    public static final int SUCCESS = 0;
    public static final int FAIL = 1;

    private int code;
    private String msg;
    private T data;

    public static <T> ApiResult<T> success() {
        return new ApiResult<>(SUCCESS, "success", null);
    }

    public static <T> ApiResult<T> success(T data) {
        return new ApiResult<>(SUCCESS, "success", data);
    }

    public static <T> ApiResult<T> fail(String msg) {
        return new ApiResult<>(FAIL, msg, null);
    }

    public static <T> ApiResult<T> fail(int code, String msg) {
        return new ApiResult<>(code, msg, null);
    }
}
