package com.hsmty.cmr.domain.search.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 收藏入参
 *
 * @author cmr
 * @since 2026-11-10
 */
@Data
public class FavoriteDTO {

    @NotBlank(message = "条目不能为空")
    private String itemId;

    private String itemName;

    private String source;

    private String taskNo;
}
