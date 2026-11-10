package com.hsmty.cmr.domain.search.mapper;

import com.hsmty.cmr.domain.search.entity.CmrFavorite;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 收藏 Mapper
 *
 * @author cmr
 * @since 2026-11-10
 */
@Mapper
public interface FavoriteMapper extends BaseMapper<CmrFavorite> {
}
