package com.hsmty.cmr.operator;

import lombok.Getter;

/**
 * 算子调用异常（失败分类，裁决 11）：
 * ENGINE=引擎侧（连不上/超时/5xx，可自动重试≤3）；DATA=数据侧（信封 code=1/4xx，直接转隔离）
 *
 * @author cmr
 * @since 2026-11-03
 */
@Getter
public class OperatorException extends RuntimeException {

    public enum Side {ENGINE, DATA}

    private final Side side;

    public OperatorException(Side side, String message) {
        super(message);
        this.side = side;
    }

    public OperatorException(Side side, String message, Throwable cause) {
        super(message, cause);
        this.side = side;
    }

    public boolean engineSide() {
        return side == Side.ENGINE;
    }
}
