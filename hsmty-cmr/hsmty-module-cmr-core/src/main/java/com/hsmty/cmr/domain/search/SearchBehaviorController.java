package com.hsmty.cmr.domain.search;

import com.hsmty.core.common.entity.ApiPageResult;
import com.hsmty.core.common.entity.ApiResult;
import com.hsmty.cmr.common.api.PageQuery;
import com.hsmty.cmr.common.api.PageResult;
import com.hsmty.cmr.domain.search.service.SearchService;
import com.hsmty.cmr.domain.search.vo.BehaviorVO;
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

    /**
     * 行为流水分页。
     *
     * @param query 分页参数（每页 10 条）
     * @param keyword 关键词：任务号 / 输入内容 / 操作人 / 重放来源任务号
     * @param operatorName 操作人（模糊）
     * @param zeroResult true 只看零结果
     * @param downCount true 只看踩数大于 0
     * @param taskNo 任务号（精确）
     * @param replayOfTaskNo 重放来源任务号（精确）
     * @param from 创建时间起
     * @param to 创建时间止
     * @return 行为流水分页
     * @author cmr
     * @since 2026-11-11
     */
    @Operation(summary = "行为流水分页（关键词/操作人/零结果/踩数/任务号/重放来源/时间范围）")
    @PreAuthorize("hasAnyAuthority('cmr:search:page')")
    @GetMapping
    public ApiResult<PageResult<BehaviorVO>> page(
            PageQuery query,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String operatorName,
            @RequestParam(required = false) Boolean zeroResult,
            @RequestParam(required = false) Boolean downCount,
            @RequestParam(required = false) String taskNo,
            @RequestParam(required = false) String replayOfTaskNo,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime to) {
        return ApiPageResult.success(searchService.behaviors(query, keyword, operatorName, zeroResult,
                downCount, taskNo, replayOfTaskNo, from, to));
    }

    @Operation(summary = "屏蔽条目列表")
    @PreAuthorize("hasAnyAuthority('cmr:search:page')")
    @GetMapping("blocked-items")
    public ApiResult<List<Map<String, Object>>> blockedItems() {
        return ApiResult.success(searchService.blockedItems());
    }

    @Operation(summary = "解除屏蔽")
    @PreAuthorize("hasAnyAuthority('cmr:feedback:update')")
    @PostMapping("blocked-items/{id}/unblock")
    public ApiResult<Void> unblock(@org.springframework.web.bind.annotation.PathVariable Long id) {
        searchService.unblock(id);
        return ApiResult.success();
    }
}
