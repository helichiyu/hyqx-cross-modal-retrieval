package com.hsmty.cmr.domain.operation.dto;

import lombok.Data;

/**
 * 反馈处理入参
 *
 * @author cmr
 * @since 2026-11-03
 */
@Data
public class FeedbackProcessDTO {

    /** strategy/datasource/backfill */
    private String target;

    private String resultNote;
}
