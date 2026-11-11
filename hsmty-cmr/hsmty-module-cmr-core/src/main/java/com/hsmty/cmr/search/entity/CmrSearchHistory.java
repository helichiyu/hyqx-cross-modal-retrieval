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
 * 检索历史。
 *
 * <p>保存一次检索的策略快照与结果集编号；条目只存 ID 快照，见 {@link CmrResultItem}。
 *
 * @author cmr
 * @since 2026-11-11
 */
@Data
@Table(value = "cmr_search_history", onInsert = EntityChangeListener.class, onUpdate = EntityChangeListener.class)
@Schema(description = "检索历史")
public class CmrSearchHistory implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Generator, value = SzIdGenerator.NAME)
    @Schema(description = "主键")
    private Long id;

    @Schema(description = "SRH 业务编号")
    private String bizNo;

    @Schema(description = "RST 结果集编号")
    private String resultSetId;

    @Schema(description = "关联任务")
    private Long taskId;

    @Schema(description = "检索时激活索引")
    private Long indexId;

    @Schema(description = "文本查询")
    private String queryText;

    @Schema(description = "查询文件逻辑路径")
    private String queryFile;

    @Schema(description = "查询模态")
    private String queryModality;

    @Schema(description = "是否经澄清改写")
    private Boolean clarified;

    @Schema(description = "业务场景")
    private String scene;

    @Schema(description = "所用策略版本")
    private Long strategyVersionId;

    @Schema(description = "权重/精排/TopK 快照 JSON")
    private String strategySnapshotJson;

    @Schema(description = "结果条数")
    private Integer itemCount;

    @Schema(description = "服务端耗时（毫秒）")
    private Integer elapsedMs;

    @Schema(description = "采用状态：unadopted/adopted/discarded")
    private String adoptStatus;

    @Schema(description = "发起人")
    private Long userId;

    @Schema(description = "操作人 ID")
    private Long operatorId;

    @Schema(description = "操作人显示名")
    private String operatorName;

    @Schema(description = "策略业务编号")
    private String strategyNo;

    @Schema(description = "策略版本号快照")
    private Integer strategyVersion;

    @Schema(description = "本次生效相似度阈值")
    private BigDecimal threshold;

    @Schema(description = "是否封存：T/F")
    private String sealed;

    @Schema(description = "重放来源任务号")
    private String replayOfTaskNo;

    @Schema(description = "关联评测 ID")
    private Long evalId;

    @Schema(description = "摘要可回看截止时间")
    private LocalDateTime summaryExpireTime;

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
