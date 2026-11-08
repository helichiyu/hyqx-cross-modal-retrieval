package com.hsmty.cmr.domain.auth;

import com.hsmty.cmr.common.api.ApiResult;
import com.hsmty.cmr.domain.auth.entity.CmrDict;
import com.hsmty.cmr.domain.auth.mapper.DictMapper;
import com.mybatisflex.core.query.QueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.hsmty.cmr.domain.auth.entity.table.CmrDictTableDef.CMR_DICT;

/**
 * 字典接口：场景/状态/类型枚举统一来源（登录即可访问）
 *
 * @author cmr
 * @since 2026-11-03
 */
@Tag(name = "字典")
@RestController
@RequestMapping({"dict", "dicts"})
@RequiredArgsConstructor
public class DictController {

    private final DictMapper dictMapper;

    @Operation(summary = "按类型取启用字典项")
    @GetMapping("{type}")
    public ApiResult<List<CmrDict>> list(@PathVariable String type) {
        return ApiResult.success(dictMapper.selectListByQuery(QueryWrapper.create()
                .where(CMR_DICT.TYPE.eq(type).and(CMR_DICT.ENABLED.eq(true)))
                .orderBy(CMR_DICT.SORT.asc())));
    }
}
