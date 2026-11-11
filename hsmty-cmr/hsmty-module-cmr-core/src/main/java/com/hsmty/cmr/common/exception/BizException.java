package com.hsmty.cmr.common.exception;

import lombok.Getter;

/**
 * 业务异常：统一转 code=1 信封返回
 *
 * @author cmr
 * @since 2026-11-03
 */
@Getter
public class BizException extends RuntimeException {

    public BizException(String message) {
        super(message);
    }

    public BizException(String message, Throwable cause) {
        super(message, cause);
    }

    /** 条件成立时抛出 */
    public static void assertTrue(boolean condition, String message) {
        if (condition) {
            throw new BizException(message);
        }
    }

    /** 对象为空时抛出 */
    public static void assertNull(Object obj, String message) {
        if (obj == null) {
            throw new BizException(message);
        }
        if (obj instanceof String s && s.isBlank()) {
            throw new BizException(message);
        }
    }
}
