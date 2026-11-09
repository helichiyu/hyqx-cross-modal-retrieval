package com.hsmty.cmr.domain.search;

import com.hsmty.cmr.common.api.ApiResult;
import com.hsmty.cmr.domain.operation.service.WordService;
import com.hsmty.cmr.domain.search.dto.ClickDTO;
import com.hsmty.cmr.domain.search.dto.SearchExecuteDTO;
import com.hsmty.cmr.domain.search.dto.UnderstandDTO;
import com.hsmty.cmr.domain.search.service.SearchService;
import com.hsmty.cmr.domain.search.vo.SearchResultVO;
import com.hsmty.cmr.domain.search.vo.UnderstandVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 检索接口（P1 检索台 / P2 结果页）
 *
 * @author cmr
 * @since 2026-11-03
 */
@Tag(name = "检索")
@RestController
@RequestMapping("search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;
    private final WordService wordService;

    @Operation(summary = "推荐词联想")
    @GetMapping("suggest")
    public ApiResult<List<String>> suggest(@RequestParam(required = false) String scene,
                                           @RequestParam(required = false) String prefix) {
        return ApiResult.success(wordService.suggest(scene, prefix));
    }

    @Operation(summary = "查询理解（407 代理：澄清判定）")
    @PreAuthorize("hasAnyAuthority('cmr:search:create')")
    @PostMapping("understand")
    public ApiResult<UnderstandVO> understand(@RequestBody UnderstandDTO dto) {
        return ApiResult.success(searchService.understand(dto));
    }

    @Operation(summary = "执行检索（408；返回结果集+SRH+任务+策略快照）")
    @PreAuthorize("hasAnyAuthority('cmr:search:create')")
    @PostMapping
    public ApiResult<SearchResultVO> execute(@Valid @RequestBody SearchExecuteDTO dto) {
        return ApiResult.success(searchService.execute(dto));
    }

    @Operation(summary = "结果集回看")
    @PreAuthorize("hasAnyAuthority('cmr:search:page')")
    @GetMapping("results/{resultSetId}")
    public ApiResult<com.hsmty.cmr.domain.search.vo.HistoryDetailVO> results(@PathVariable String resultSetId) {
        return ApiResult.success(searchService.resultsOf(resultSetId));
    }

    @Operation(summary = "结果集重跑（新 SRH / 新结果集）")
    @PreAuthorize("hasAnyAuthority('cmr:search:create')")
    @PostMapping("results/{resultSetId}/rerun")
    public ApiResult<SearchResultVO> rerun(@PathVariable String resultSetId) {
        return ApiResult.success(searchService.rerunByResultSetId(resultSetId));
    }

    @Operation(summary = "结果集采用")
    @PreAuthorize("hasAnyAuthority('cmr:search:adopt')")
    @PostMapping("results/{resultSetId}/adopt")
    public ApiResult<Void> adopt(@PathVariable String resultSetId) {
        searchService.adopt(resultSetId, true);
        return ApiResult.success();
    }

    @Operation(summary = "结果集废弃（与采用互斥，一次性终态）")
    @PreAuthorize("hasAnyAuthority('cmr:search:adopt')")
    @PostMapping("results/{resultSetId}/discard")
    public ApiResult<Void> discard(@PathVariable String resultSetId) {
        searchService.adopt(resultSetId, false);
        return ApiResult.success();
    }

    @Operation(summary = "条目点击埋点")
    @PreAuthorize("hasAnyAuthority('cmr:search:page')")
    @PostMapping("results/{resultSetId}/click")
    public ApiResult<Void> click(@PathVariable String resultSetId, @Valid @RequestBody ClickDTO dto) {
        searchService.click(resultSetId, dto.getRank());
        return ApiResult.success();
    }

}
