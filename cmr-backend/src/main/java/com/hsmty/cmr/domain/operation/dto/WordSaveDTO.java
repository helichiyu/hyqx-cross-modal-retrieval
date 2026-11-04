package com.hsmty.cmr.domain.operation.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 推荐词新增/编辑入参
 *
 * @author cmr
 * @since 2026-11-03
 */
@Data
public class WordSaveDTO {

    @NotBlank(message = "推荐词不能为空")
    private String word;

    private String scene = "all";

    private Boolean enabled = true;

    private Integer sort = 0;
}
