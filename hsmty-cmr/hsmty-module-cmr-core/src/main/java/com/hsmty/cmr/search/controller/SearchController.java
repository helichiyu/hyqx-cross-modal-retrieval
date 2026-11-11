package com.hsmty.cmr.search.controller;

import com.hsmty.cmr.pojo.dto.QueryUnderstandDTO;
import com.hsmty.cmr.pojo.dto.SearchCreateDTO;
import com.hsmty.cmr.pojo.vo.QueryUnderstandVO;
import com.hsmty.cmr.pojo.vo.SearchResultVO;
import com.hsmty.cmr.search.service.SearchService;
import com.hsmty.core.common.entity.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 检索接口。
 *
 * <p>路径 {@code /hs-api/cmr/search/**}，权限码与菜单数据保持三处一致。
 *
 * @author cmr
 * @since 2026-11-10
 */
@Tag(name = "检索")
@RestController
@RequestMapping("search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @Operation(summary = "查询理解（407 算子代理）")
    @PreAuthorize("hasAnyAuthority('cmr:search:create')")
    @PostMapping("understand")
    public ApiResult<QueryUnderstandVO> understand(@Valid @RequestBody QueryUnderstandDTO dto) {
        return ApiResult.success(searchService.understand(dto));
    }

    @Operation(summary = "执行检索（408 算子代理并落库）")
    @PreAuthorize("hasAnyAuthority('cmr:search:create')")
    @PostMapping
    public ApiResult<SearchResultVO> search(@Valid @RequestBody SearchCreateDTO dto) {
        return ApiResult.success(searchService.search(dto));
    }
}
