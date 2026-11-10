package com.hsmty.cmr.domain.search.mapper;

import com.hsmty.cmr.domain.search.entity.CmrItemVote;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 单条赞踩 Mapper
 *
 * @author cmr
 * @since 2026-11-10
 */
@Mapper
public interface ItemVoteMapper extends BaseMapper<CmrItemVote> {
}
