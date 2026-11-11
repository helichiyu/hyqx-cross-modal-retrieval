package com.hsmty.cmr.domain.dataset.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

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

    /**
     * 4 项必填（dataset_id/name/item_count/modals）逐字段检测结果。
     *
     * <p>推送型/未知协议无法主动探测时为 null（未知）。
     */
    @Schema(description = "4 项必填逐字段检测结果；null=该协议无法主动探测")
    private List<DatasourceCheckVO> checks;
}
