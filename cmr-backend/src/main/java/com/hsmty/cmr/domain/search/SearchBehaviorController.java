package com.hsmty.cmr.domain.search;

import com.hsmty.cmr.common.api.ApiPageResult;
import com.hsmty.cmr.common.api.ApiResult;
import com.hsmty.cmr.common.api.PageQuery;
import com.hsmty.cmr.common.api.PageResult;
import com.hsmty.cmr.domain.search.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 搜索行为流水接口（P6）：任务级流水、失败任务、赞踩与封存
 *
 * @author cmr
 * @since 2026-11-10
 */
@Tag(name = "搜索行为")
@RestController
@RequestMapping("search-behaviors")
@RequiredArgsConstructor
public class SearchBehaviorController {

    private final SearchService searchService;

    @Operation(summary = "行为流水分页（关键词/操作人/零结果/时间范围）")
    @PreAuthorize("hasAnyAuthority('cmr:search:page')")
    @GetMapping
    public ApiResult<PageResult<Map<String, Object>>> page(
            PageQuery query,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String operatorName,
            @RequestParam(required = false) Boolean zeroResult,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime to) {
        return ApiPageResult.success(searchService.behaviors(query, keyword, operatorName, zeroResult, from, to));
    }

    @Operation(summary = "屏蔽条目列表")
    @PreAuthorize("hasAnyAuthority('cmr:search:page')")
    @GetMapping("blocked-items")
    public ApiResult<List<Map<String, Object>>> blockedItems() {
        return ApiResult.success(searchService.blockedItems());
    }

    @Operation(summary = "解除屏蔽")
    @PreAuthorize("hasAnyAuthority('cmr:operation:update')")
    @PostMapping("blocked-items/{id}/unblock")
    public ApiResult<Void> unblock(@org.springframework.web.bind.annotation.PathVariable Long id) {
        searchService.unblock(id);
        return ApiResult.success();
    }
}
