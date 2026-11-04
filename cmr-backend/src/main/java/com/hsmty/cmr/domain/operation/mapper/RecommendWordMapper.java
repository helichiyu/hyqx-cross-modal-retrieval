package com.hsmty.cmr.domain.operation.mapper;

import com.mybatisflex.core.BaseMapper;
import com.hsmty.cmr.domain.operation.entity.CmrRecommendWord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 推荐词 Mapper
 *
 * @author cmr
 * @since 2026-11-03
 */
@Mapper
public interface RecommendWordMapper extends BaseMapper<CmrRecommendWord> {
}
