package com.hsmty.cmr.search.entity;

import com.hsmty.db.EntityChangeListener;
import com.hsmty.db.id.SzIdGenerator;
import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 结果集条目（ID 快照）。
 *
 * <p>按 05 决策记录只存「样本 ID + 名次 + 分数 + 名称/来源快照」，文本内容与媒体定位不落库。
 *
 * @author cmr
 * @since 2026-11-11
 */
@Data
@Table(value = "cmr_result_item", onInsert = EntityChangeListener.class, onUpdate = EntityChangeListener.class)
@Schema(description = "结果集条目")
public class CmrResultItem implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Generator, value = SzIdGenerator.NAME)
    @Schema(description = "主键")
    private Long id;

    @Schema(description = "检索历史 ID")
    private Long searchId;

    @Schema(description = "冗余结果集编号")
    private String resultSetId;

    @Schema(description = "名次")
    private Integer resultRank;

    @Schema(description = "算子条目 ID")
    private String itemId;

    @Schema(description = "条目名称快照")
    private String itemName;

    @Schema(description = "来源名快照")
    private String source;

    @Schema(description = "模态")
    private String modality;

    @Schema(description = "融合分")
    private BigDecimal score;

    @Schema(description = "对齐分")
    private BigDecimal alignScore;

    @Schema(description = "质量等级")
    private String qualityGrade;

    @Schema(description = "是否被点击")
    private Boolean clicked;

    @Schema(description = "创建人ID")
    private Long createId;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新人ID")
    private Long updateId;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @Column(isLogicDelete = true)
    @Schema(description = "删除标记")
    private String delFlag;
}
