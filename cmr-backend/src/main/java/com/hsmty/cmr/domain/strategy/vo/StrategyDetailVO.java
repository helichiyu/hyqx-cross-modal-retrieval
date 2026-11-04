package com.hsmty.cmr.domain.strategy.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 策略详情（含当前生效版本参数与向量段只读回显）
 *
 * @author cmr
 * @since 2026-11-03
 */
@Data
@Builder
public class StrategyDetailVO {

    private StrategyVO strategy;

    /** 当前生效版本参数 */
    private StrategyVersionVO current;

    /** 草稿（未发布改动） */
    private StrategyVersionVO draft;

    /** 向量段只读回显：激活索引模型版本（数据流 3.4"策略只有三段参数"） */
    private String modelVersion;

    private List<StrategyVersionVO> versions;
}
