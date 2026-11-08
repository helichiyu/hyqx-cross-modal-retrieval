package com.hsmty.cmr.domain.auth;

import com.hsmty.cmr.common.api.ApiResult;
import com.hsmty.cmr.common.exception.BizException;
import com.hsmty.cmr.common.id.SnowflakeIdWorker;
import com.hsmty.cmr.domain.auth.dto.DictCreateDTO;
import com.hsmty.cmr.domain.auth.entity.CmrDict;
import com.hsmty.cmr.domain.auth.mapper.DictMapper;
import com.mybatisflex.core.query.QueryWrapper;
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

import static com.hsmty.cmr.domain.auth.entity.table.CmrDictTableDef.CMR_DICT;

/**
 * 字典接口：场景/状态/类型枚举统一来源。
 */
@Tag(name = "字典")
@RestController
@RequestMapping({"dict", "dicts"})
@RequiredArgsConstructor
public class DictController {

    private final DictMapper dictMapper;
    private final SnowflakeIdWorker idWorker;

    @Operation(summary = "按类型取启用字典项")
    @GetMapping("{type}")
    public ApiResult<List<CmrDict>> list(@PathVariable String type) {
        return ApiResult.success(dictMapper.selectListByQuery(QueryWrapper.create()
                .where(CMR_DICT.TYPE.eq(type).and(CMR_DICT.ENABLED.eq(true)))
                .orderBy(CMR_DICT.SORT.asc())));
    }

    @Operation(summary = "新增字典项（当前用于业务场景维护）")
    @PreAuthorize("hasAnyAuthority('cmr:strategy:create')")
    @PostMapping("{type}")
    public ApiResult<CmrDict> create(@PathVariable String type, @Valid @RequestBody DictCreateDTO dto) {
        String alias = dto.getAlias() == null || dto.getAlias().isBlank()
                ? "scene_" + Long.toString(System.currentTimeMillis(), 36)
                : dto.getAlias().trim();
        Long exists = dictMapper.selectCountByQuery(QueryWrapper.create()
                .where(CMR_DICT.TYPE.eq(type).and(CMR_DICT.ALIAS.eq(alias))));
        BizException.assertTrue(exists != null && exists > 0, "字典项已存在");

        CmrDict last = dictMapper.selectOneByQuery(QueryWrapper.create()
                .where(CMR_DICT.TYPE.eq(type))
                .orderBy(CMR_DICT.SORT.desc())
                .limit(1));
        CmrDict item = new CmrDict();
        item.setId(idWorker.next());
        item.setType(type);
        item.setAlias(alias);
        item.setLabel(dto.getLabel().trim());
        item.setSort(last == null || last.getSort() == null ? 1 : last.getSort() + 1);
        item.setEnabled(true);
        item.setRemark(dto.getRemark());
        dictMapper.insert(item, true);
        return ApiResult.success(item);
    }
}