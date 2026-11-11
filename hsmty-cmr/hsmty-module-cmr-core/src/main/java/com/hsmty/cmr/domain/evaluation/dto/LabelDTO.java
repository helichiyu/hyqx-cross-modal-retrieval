package com.hsmty.cmr.domain.evaluation.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 评测集条目标注入参（itemId 或来源文件名）
 *
 * @author cmr
 * @since 2026-11-03
 */
@Data
public class LabelDTO {

    @NotEmpty(message = "标准答案不能为空")
    private List<String> answers;
}
