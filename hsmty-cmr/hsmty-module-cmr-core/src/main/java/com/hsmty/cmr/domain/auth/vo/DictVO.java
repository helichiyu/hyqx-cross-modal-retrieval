package com.hsmty.cmr.domain.auth.vo;

import com.hsmty.cmr.domain.auth.entity.CmrDict;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 字典项出参：只暴露业务字段，不直接下发 PO（审计字段与 delFlag 不外泄）。
 *
 * @author cmr
 * @since 2026-11-11
 */
@Data
@Builder
@Schema(description = "字典项")
public class DictVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "字典类型")
    private String type;

    @Schema(description = "别名（业务值）")
    private String alias;

    @Schema(description = "显示名称")
    private String label;

    @Schema(description = "排序值")
    private Integer sort;

    @Schema(description = "是否启用")
    private Boolean enabled;

    @Schema(description = "描述")
    private String remark;

    /**
     * PO 转 VO。
     *
     * @param entity 字典实体
     * @return 字典项出参
     */
    public static DictVO from(CmrDict entity) {
        if (entity == null) {
            return null;
        }
        return DictVO.builder()
                .id(entity.getId())
                .type(entity.getType())
                .alias(entity.getAlias())
                .label(entity.getLabel())
                .sort(entity.getSort())
                .enabled(entity.getEnabled())
                .remark(entity.getRemark())
                .build();
    }
}
