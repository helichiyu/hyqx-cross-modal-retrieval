package com.hsmty.cmr.domain.search.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 结果条目质量等级：继承 P4 402 评定分的映射规则
 *
 * @author cmr
 * @since 2026-11-10
 */
class SearchServiceQualityGradeTest {

    @Test
    void 评定分映射为质量等级() {
        assertThat(SearchService.qualityGradeOf(new BigDecimal("1.0000"))).isEqualTo("A");
        assertThat(SearchService.qualityGradeOf(new BigDecimal("0.90"))).isEqualTo("A");
        assertThat(SearchService.qualityGradeOf(new BigDecimal("0.85"))).isEqualTo("B");
        assertThat(SearchService.qualityGradeOf(new BigDecimal("0.80"))).isEqualTo("B");
        assertThat(SearchService.qualityGradeOf(new BigDecimal("0.70"))).isEqualTo("C");
        assertThat(SearchService.qualityGradeOf(new BigDecimal("0.6999"))).isEqualTo("D");
        assertThat(SearchService.qualityGradeOf(null)).isNull();
    }
}
