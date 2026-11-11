package com.hsmty.cmr.domain.search;

import com.hsmty.core.common.entity.ApiResult;
import com.hsmty.cmr.domain.search.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 搜索结果摘要接口（P3）：Top-20 条目、赞踩、封存
 *
 * @author cmr
 * @since 2026-11-10
 */
@Tag(name = "搜索结果摘要")
@RestController
@RequestMapping("search-summary")
@RequiredArgsConstructor
public class SearchSummaryController {

    private final SearchService searchService;

    @Operation(summary = "按任务号取摘要（Top-20，30 天保留）")
    @PreAuthorize("hasAnyAuthority('cmr:search:page')")
    @GetMapping("{taskNo}")
    public ApiResult<Map<String, Object>> summary(@PathVariable String taskNo) {
        return ApiResult.success(searchService.summary(taskNo));
    }

    @Operation(summary = "封存任务（锁定赞/踩）")
    @PreAuthorize("hasAnyAuthority('cmr:search:create')")
    @PostMapping("{taskNo}/seal")
    public ApiResult<Void> seal(@PathVariable String taskNo) {
        searchService.seal(taskNo);
        return ApiResult.success();
    }
}
