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

    /** unadopted/adopted/discarded，一次性终态 */
    private String adoptStatus;

    private Long userId;
}
