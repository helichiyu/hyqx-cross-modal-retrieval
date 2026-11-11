package com.hsmty.cmr.domain.dataset.vo;

import com.hsmty.cmr.domain.dataset.entity.CmrDatasourceBlacklist;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 数据源黑名单出参：只暴露前端需要的字段，不直接下发 PO。
 *
 * @author cmr
 * @since 2026-11-11
 */
@Data
@Builder
@Schema(description = "数据源黑名单条目")
public class DatasourceBlacklistVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "上游数据集 ID")
    private String datasetId;

    @Schema(description = "数据集名称")
    private String name;

    @Schema(description = "来源名，检索结果按此整源排除")
    private String sourceName;

    @Schema(description = "数据源地址")
    private String endpoint;

    @Schema(description = "加入时间")
    private LocalDateTime createTime;

    /**
     * PO 转 VO。
     *
     * @param entity 黑名单实体
     * @return 黑名单出参
     */
    public static DatasourceBlacklistVO from(CmrDatasourceBlacklist entity) {
        if (entity == null) {
            return null;
        }
        return DatasourceBlacklistVO.builder()
                .id(entity.getId())
                .datasetId(entity.getDatasetId())
                .name(entity.getName())
                .sourceName(entity.getSourceName())
                .endpoint(entity.getEndpoint())
                .createTime(entity.getCreateTime())
                .build();
    }
}
