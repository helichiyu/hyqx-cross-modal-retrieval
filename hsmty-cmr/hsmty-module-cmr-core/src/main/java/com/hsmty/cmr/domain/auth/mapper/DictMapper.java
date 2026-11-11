package com.hsmty.cmr.domain.auth.mapper;

import com.mybatisflex.core.BaseMapper;
import com.hsmty.cmr.domain.auth.entity.CmrDict;
import org.apache.ibatis.annotations.Mapper;

/**
 * 字典 Mapper
 *
 * @author cmr
 * @since 2026-11-03
 */
@Mapper
public interface DictMapper extends BaseMapper<CmrDict> {
}
