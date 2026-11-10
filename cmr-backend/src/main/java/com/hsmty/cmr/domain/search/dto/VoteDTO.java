package com.hsmty.cmr.domain.search.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 赞/踩入参
 *
 * @author cmr
 * @since 2026-11-10
 */
@Data
public class VoteDTO {

    @NotBlank(message = "条目不能为空")
    private String itemId;

    /** up/down/none（none=取消） */
    @NotBlank(message = "投票类型不能为空")
    @Pattern(regexp = "up|down|none", message = "投票只能是 up/down/none")
    private String vote;
}
