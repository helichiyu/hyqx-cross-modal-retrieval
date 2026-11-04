package com.hsmty.cmr.domain.strategy.mapper;

import com.mybatisflex.core.BaseMapper;
import com.hsmty.cmr.domain.strategy.entity.CmrStrategyVersion;
import org.apache.ibatis.annotations.Mapper;

/**
 * 策略版本 Mapper
 *
 * @author cmr
 * @since 2026-11-03
 */
@Mapper
public interface StrategyVersionMapper extends BaseMapper<CmrStrategyVersion> {
}
