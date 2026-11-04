package com.hsmty.cmr.common.api;

/**
 * 分页接口统一返回
 *
 * @author cmr
 * @since 2026-11-03
 */
public final class ApiPageResult {

    private ApiPageResult() {
    }

    public static <T> ApiResult<PageResult<T>> success(PageResult<T> page) {
        return ApiResult.success(page);
    }
}
