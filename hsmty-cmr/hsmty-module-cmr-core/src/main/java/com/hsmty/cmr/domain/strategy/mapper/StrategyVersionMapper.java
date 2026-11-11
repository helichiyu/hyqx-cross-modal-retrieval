package com.hsmty.cmr.domain.strategy.mapper;

import com.mybatisflex.core.BaseMapper;
import com.hsmty.cmr.domain.strategy.entity.CmrStrategyVersion;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 策略版本 Mapper
 *
 * @author cmr
 * @since 2026-11-03
 */
@Mapper
public interface StrategyVersionMapper extends BaseMapper<CmrStrategyVersion> {

    /**
     * 物理删除指定版本行。
     *
     * <p>版本表允许物理清理（契约第 4 节：发布后仅保留最近 10 个版本），
     * 因此绕过实体上的逻辑删除，直接 DELETE；业务表不受影响。
     *
     * @param id 版本主键
     * @return 受影响行数
     * @author cmr
     * @since 2026-11-11
     */
    @Delete("DELETE FROM cmr_strategy_version WHERE id = #{id}")
    int physicalDeleteById(@Param("id") Long id);
}
