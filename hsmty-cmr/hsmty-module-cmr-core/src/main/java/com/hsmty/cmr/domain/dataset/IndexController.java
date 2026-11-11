package com.hsmty.cmr.domain.dataset;

import com.hsmty.core.common.entity.ApiResult;
import com.hsmty.cmr.domain.dataset.service.DatasetService;
import com.hsmty.cmr.domain.dataset.vo.IndexVO;
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

import java.util.List;

/**
 * 库管理接口（P4）：索引历史与回滚激活
 *
 * @author cmr
 * @since 2026-11-03
 */
@Tag(name = "库管理-索引")
@RestController
@RequestMapping("indexes")
@RequiredArgsConstructor
public class IndexController {

    private final DatasetService datasetService;

    @Operation(summary = "索引历史（candidate/active/retired + 406 结果）")
    @PreAuthorize("hasAnyAuthority('cmr:dataset:detail')")
    @GetMapping
    public ApiResult<List<IndexVO>> list(@RequestParam Long datasetId) {
        return ApiResult.success(datasetService.indexesOf(datasetId));
    }

    @Operation(summary = "回滚激活（旧 retired/candidate 索引重新激活）")
    @PreAuthorize("hasAnyAuthority('cmr:dataset:confirm')")
    @PostMapping("{id}/activate")
    public ApiResult<IndexVO> activate(@PathVariable Long id) {
        return ApiResult.success(datasetService.activateIndex(id));
    }
}
