package com.hsmty.cmr.domain.search;

import com.hsmty.cmr.common.api.ApiPageResult;
import com.hsmty.cmr.common.api.ApiResult;
import com.hsmty.cmr.common.api.PageQuery;
import com.hsmty.cmr.common.api.PageResult;
import com.hsmty.cmr.domain.search.service.SearchService;
import com.hsmty.cmr.domain.search.vo.HistoryDetailVO;
import com.hsmty.cmr.domain.search.vo.HistoryVO;
import com.hsmty.cmr.domain.search.vo.SearchResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 检索历史接口（P9）
 *
 * @author cmr
 * @since 2026-11-03
 */
@Tag(name = "检索历史")
@RestController
@RequestMapping("search-history")
@RequiredArgsConstructor
public class SearchHistoryController {

    private final SearchService searchService;

    @Operation(summary = "检索历史分页（query 模糊/模态/采用状态/零结果）")
    @PreAuthorize("hasAnyAuthority('cmr:search:page')")
    @GetMapping
    public ApiResult<PageResult<HistoryVO>> page(PageQuery query,
                                                 @RequestParam(required = false) String keyword,
                                                 @RequestParam(required = false) String modality,
                                                 @RequestParam(required = false) String adoptStatus,
                                                 @RequestParam(required = false) Boolean zeroResult) {
        return ApiPageResult.success(searchService.page(query, keyword, modality, adoptStatus, zeroResult));
    }

    @Operation(summary = "检索历史详情（含结果集条目）")
    @PreAuthorize("hasAnyAuthority('cmr:search:page')")
    @GetMapping("{id}")
    public ApiResult<HistoryDetailVO> detail(@PathVariable Long id) {
        return ApiResult.success(searchService.detail(id));
    }

    @Operation(summary = "再次执行（原参数重放，新 SRH）")
    @PreAuthorize("hasAnyAuthority('cmr:search:create')")
    @PostMapping("{id}/rerun")
    public ApiResult<SearchResultVO> rerun(@PathVariable Long id) {
        return ApiResult.success(searchService.rerun(id));
    }
}
