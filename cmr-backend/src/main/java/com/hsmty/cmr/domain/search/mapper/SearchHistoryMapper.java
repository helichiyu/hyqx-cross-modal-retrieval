package com.hsmty.cmr.domain.search.mapper;

import com.mybatisflex.core.BaseMapper;
import com.hsmty.cmr.domain.search.entity.CmrSearchHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 检索历史 Mapper（含评测集三类筛选 SQL）
 *
 * @author cmr
 * @since 2026-11-03
 */
@Mapper
public interface SearchHistoryMapper extends BaseMapper<CmrSearchHistory> {

    /** 零结果检索 */
    @Select("SELECT * FROM cmr_search_history WHERE del_flag='F' AND item_count = 0 "
            + "ORDER BY id DESC LIMIT #{limit}")
    List<CmrSearchHistory> selectZeroResult(int limit);

    /** 有结果但零点击（依赖点击埋点） */
    @Select("SELECT h.* FROM cmr_search_history h WHERE h.del_flag='F' AND h.item_count > 0 "
            + "AND NOT EXISTS (SELECT 1 FROM cmr_click_event c WHERE c.search_id = h.id AND c.del_flag='F') "
            + "ORDER BY h.id DESC LIMIT #{limit}")
    List<CmrSearchHistory> selectNoClick(int limit);

    /** 复搜：归一化后同 query 出现 ≥2 次 */
    @Select("SELECT h.* FROM cmr_search_history h WHERE h.del_flag='F' "
            + "AND (SELECT COUNT(*) FROM cmr_search_history h2 WHERE h2.del_flag='F' "
            + "AND LOWER(TRIM(h2.query_text)) = LOWER(TRIM(h.query_text))) >= 2 "
            + "GROUP BY h.id ORDER BY h.id DESC LIMIT #{limit}")
    List<CmrSearchHistory> selectReSearch(int limit);
}
