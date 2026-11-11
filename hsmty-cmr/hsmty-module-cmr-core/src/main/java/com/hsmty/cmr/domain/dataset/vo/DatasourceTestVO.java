package com.hsmty.cmr.domain.dataset.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据源连接测试结果
 *
 * @author cmr
 * @since 2026-11-08
 */
@Data
@Builder
public class DatasourceTestVO {

    /** true=可连通；false=失败；null=该协议不适用主动测试 */
    private Boolean connected;

    private Integer statusCode;

    private Long latencyMs;

    private String message;

    private LocalDateTime testedAt;
}
