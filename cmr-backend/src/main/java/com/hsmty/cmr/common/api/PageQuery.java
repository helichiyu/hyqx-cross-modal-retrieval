package com.hsmty.cmr.common.api;

import lombok.Data;

/**
 * 分页查询基类：pageNum 从 1 起
 *
 * @author cmr
 * @since 2026-11-03
 */
@Data
public class PageQuery {

    private int pageNum = 1;

    private int pageSize = 10;

    public int offset() {
        return (Math.max(pageNum, 1) - 1) * Math.max(pageSize, 1);
    }
}
