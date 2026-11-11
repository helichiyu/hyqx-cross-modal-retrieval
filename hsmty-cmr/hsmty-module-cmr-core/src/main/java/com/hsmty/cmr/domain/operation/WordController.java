package com.hsmty.cmr.domain.operation;

import com.hsmty.core.common.entity.ApiPageResult;
import com.hsmty.core.common.entity.ApiResult;
import com.hsmty.cmr.common.api.PageQuery;
import com.hsmty.cmr.common.api.PageResult;
import com.hsmty.cmr.domain.operation.dto.WordSaveDTO;
import com.hsmty.cmr.domain.operation.service.WordService;
import com.hsmty.cmr.domain.operation.vo.WordVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 运营接口（P7）：推荐词维护（开关即时生效）
 *
 * @author cmr
 * @since 2026-11-03
 */
@Tag(name = "结果运营-推荐词")
@RestController
@RequestMapping("recommend-words")
@RequiredArgsConstructor
public class WordController {

    private final WordService wordService;

    @Operation(summary = "推荐词分页")
    @PreAuthorize("hasAnyAuthority('cmr:word:page')")
    @GetMapping
    public ApiResult<PageResult<WordVO>> page(PageQuery query,
                                              @RequestParam(required = false) String keyword) {
        return ApiPageResult.success(wordService.page(query, keyword));
    }

    @Operation(summary = "新增推荐词")
    @PreAuthorize("hasAnyAuthority('cmr:word:create')")
    @PostMapping
    public ApiResult<WordVO> create(@Valid @RequestBody WordSaveDTO dto) {
        return ApiResult.success(wordService.create(dto));
    }

    @Operation(summary = "编辑/开关推荐词")
    @PreAuthorize("hasAnyAuthority('cmr:word:update')")
    @PutMapping("{id}")
    public ApiResult<WordVO> update(@PathVariable Long id, @Valid @RequestBody WordSaveDTO dto) {
        return ApiResult.success(wordService.update(id, dto));
    }

    @Operation(summary = "删除推荐词（逻辑删）")
    @PreAuthorize("hasAnyAuthority('cmr:word:remove')")
    @DeleteMapping("{id}")
    public ApiResult<Void> remove(@PathVariable Long id) {
        wordService.remove(id);
        return ApiResult.success();
    }
}
