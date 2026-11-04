package com.hsmty.cmr.domain.search.dto;

import lombok.Data;

/**
 * 查询理解入参（407 代理；query/queryFile 二选一）
 *
 * @author cmr
 * @since 2026-11-03
 */
@Data
public class UnderstandDTO {

    private String query;

    private String queryFile;
}
