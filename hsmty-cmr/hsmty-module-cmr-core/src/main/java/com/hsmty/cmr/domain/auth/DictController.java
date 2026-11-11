package com.hsmty.cmr.domain.auth;

import com.hsmty.core.common.entity.ApiResult;
import com.hsmty.cmr.domain.auth.dto.DictCreateDTO;
import com.hsmty.cmr.domain.auth.service.DictService;
import com.hsmty.cmr.domain.auth.vo.DictVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 字典接口：场景/状态/类型枚举统一来源。
 */
@Tag(name = "字典")
@RestController
@RequestMapping({"dict", "dicts"})
@RequiredArgsConstructor
public class DictController {

    private final DictService dictService;

    @Operation(summary = "按类型取启用字典项")
    @GetMapping("{type}")
    public ApiResult<List<DictVO>> list(@PathVariable String type) {
        return ApiResult.success(dictService.list(type));
    }

    @Operation(summary = "新增字典项（当前用于业务场景维护）")
    @PreAuthorize("hasAnyAuthority('cmr:strategy:create')")
    @PostMapping("{type}")
    public ApiResult<DictVO> create(@PathVariable String type, @Valid @RequestBody DictCreateDTO dto) {
        return ApiResult.success(dictService.create(type, dto));
    }
}
