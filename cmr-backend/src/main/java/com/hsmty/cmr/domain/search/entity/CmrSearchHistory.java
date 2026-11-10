package com.hsmty.cmr.domain.search.entity;

import com.hsmty.cmr.common.entity.BaseEntity;
import com.hsmty.cmr.config.AuditListener;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 检索历史（SRH；每次检索必落一条，结果集可回看）
 *
 * @author cmr
 * @since 2026-11-03
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(value = "cmr_search_history", onInsert = AuditListener.class, onUpdate = AuditListener.class)
public class CmrSearchHistory extends BaseEntity {

    public static final String ADOPT_UNADOPTED = "unadopted";
    public static final String ADOPT_ADOPTED = "adopted";
    public static final String ADOPT_DISCARDED = "discarded";

    private String bizNo;

    /** 算子返回 RST 结果集编号 */
    private String resultSetId;

    private Long taskId;

    /** 检索时激活索引（名称/预览回查锚点） */
    private Long indexId;

    private String queryText;

    /** 查询文件逻辑路径（可 null） */
    private String queryFile;

    /** text/image/audio/video */
    private String queryModality;

    /** 首个目标模态：text/image/audio/video */
    private String targetModality;

    private Boolean clarified;

    private String scene;

    private Long strategyVersionId;

    /** weights/rerank/topK 快照 JSON */
    private String strategySnapshotJson;

    private Integer itemCount;

    private Integer elapsedMs;

    /** 兼容字段：原型 v5 起由赞/踩替代采用/废弃 */
    private String adoptStatus;

    private Long userId;

    /** 操作人（搜索行为流水展示） */
    private Long operatorId;

    private String operatorName;

    /** 策略业务编号与版本快照 */
    private String strategyNo;

    private Integer strategyVersion;

    /** 本次相似度阈值 */
    private java.math.BigDecimal threshold;

    /** 本次运行是否封存：封存后赞/踩不可改 */
    private String sealed;

    /** 重放自原任务号 */
    private String replayOfTaskNo;

    /** 归属评测 id（效果评测按 query 归集重放） */
    private Long evalId;

    /** Top-20 摘要过期时间（30 天） */
    private java.time.LocalDateTime summaryExpireTime;
}
