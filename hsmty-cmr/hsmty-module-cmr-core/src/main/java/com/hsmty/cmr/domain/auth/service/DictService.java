package com.hsmty.cmr.domain.auth.service;

import com.hsmty.cmr.common.exception.BizException;
import com.hsmty.cmr.common.id.SnowflakeIdWorker;
import com.hsmty.cmr.domain.auth.dto.DictCreateDTO;
import com.hsmty.cmr.domain.auth.entity.CmrDict;
import com.hsmty.cmr.domain.auth.mapper.DictMapper;
import com.hsmty.cmr.domain.auth.vo.DictVO;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.hsmty.cmr.domain.auth.entity.table.CmrDictTableDef.CMR_DICT;

/**
 * 字典服务：场景/状态等枚举的统一来源，供前端下拉与运营维护。
 *
 * @author cmr
 * @since 2026-11-11
 */
@Service
@RequiredArgsConstructor
public class DictService {

    private final DictMapper dictMapper;
    private final SnowflakeIdWorker idWorker;

    /**
     * 按类型取启用字典项，按 sort 升序。
     *
     * @param type 字典类型
     * @return 启用字典项
     */
    public List<DictVO> list(String type) {
        return dictMapper.selectListByQuery(QueryWrapper.create()
                        .where(CMR_DICT.TYPE.eq(type).and(CMR_DICT.ENABLED.eq(true)))
                        .orderBy(CMR_DICT.SORT.asc()))
                .stream().map(DictVO::from).toList();
    }

    /**
     * 新增字典项（当前用于业务场景维护）。
     *
     * @param type 字典类型
     * @param dto  入参
     * @return 新增后的字典项
     */
    @Transactional(rollbackFor = Exception.class)
    public DictVO create(String type, DictCreateDTO dto) {
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
        return DictVO.from(item);
    }
}
