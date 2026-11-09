package com.hsmty.cmr.domain.search.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SearchExecuteDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void 目标模态使用合法枚举时校验通过() {
        SearchExecuteDTO dto = new SearchExecuteDTO();
        dto.setTargetModalities(List.of("image"));

        Set<ConstraintViolation<SearchExecuteDTO>> violations = validator.validate(dto);

        assertThat(violations).isEmpty();
    }

    @Test
    void 目标模态非法时校验失败() {
        SearchExecuteDTO dto = new SearchExecuteDTO();
        dto.setTargetModalities(List.of("file"));

        Set<ConstraintViolation<SearchExecuteDTO>> violations = validator.validate(dto);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
                .isEqualTo("目标模态只能是 text/image/audio/video");
    }
}
