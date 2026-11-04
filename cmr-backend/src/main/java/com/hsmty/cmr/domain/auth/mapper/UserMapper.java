package com.hsmty.cmr.domain.auth.mapper;

import com.mybatisflex.core.BaseMapper;
import com.hsmty.cmr.domain.auth.entity.CmrUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户 Mapper
 *
 * @author cmr
 * @since 2026-11-03
 */
@Mapper
public interface UserMapper extends BaseMapper<CmrUser> {
}
