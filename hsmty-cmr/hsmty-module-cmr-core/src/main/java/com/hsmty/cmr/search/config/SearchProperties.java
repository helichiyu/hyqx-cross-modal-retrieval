package com.hsmty.cmr.search.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 检索默认参数与索引回落目录。
 *
 * <p>默认值与 408m 算子说明书保持一致：权重四模态各 25、TopK 10、阈值 0.60。
 * 没有激活索引（{@code cmr_index.status=active}）时使用 {@link #indexDir}。
 *
 * @author cmr
 * @since 2026-11-11
 */
@Data
@Component
@ConfigurationProperties(prefix = "cmr.search")
public class SearchProperties {

    /** 无激活索引时使用的索引目录。 */
    private String indexDir = "runtime/demo_index";

    /** 默认返回条数。 */
    private Integer topK = 10;

    /** 默认四模态融合权重，合计 100。 */
    private Map<String, Integer> weights = new LinkedHashMap<>(Map.of(
            "text", 25, "image", 25, "audio", 25, "video", 25));

    /** 默认相似度阈值。 */
    private BigDecimal threshold = new BigDecimal("0.60");

    /** 摘要可回看天数。 */
    private Integer summaryDays = 30;
}
