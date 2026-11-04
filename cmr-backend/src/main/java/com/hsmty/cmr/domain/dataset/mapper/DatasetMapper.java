package com.hsmty.cmr.domain.dataset.mapper;

import com.mybatisflex.core.BaseMapper;
import com.hsmty.cmr.domain.dataset.entity.CmrDataset;
import org.apache.ibatis.annotations.Mapper;

/**
 * 数据集 Mapper
 *
 * @author cmr
 * @since 2026-11-03
 */
@Mapper
public interface DatasetMapper extends BaseMapper<CmrDataset> {
}
