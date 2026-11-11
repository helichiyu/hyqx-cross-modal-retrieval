package com.hsmty.cmr.domain.evaluation.mapper;

import com.hsmty.cmr.domain.evaluation.vo.HardCaseVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 难例样本 Mapper（P8）：按检索任务归集点踩条目，按被踩条目数倒序。
 *
 * <p>被踩条目数按 {@code item_id} 去重，不统计点踩次数（数据流 3.6）。
 *
 * @author cmr
 * @since 2026-11-11
 */
@Mapper
public interface HardCaseMapper {

    /**
     * 统计有点踩的检索任务数。
     *
     * @return 任务数
     * @author cmr
     * @since 2026-11-11
     */
    @Select("""
            SELECT COUNT(DISTINCT v.search_id)
            FROM cmr_item_vote v
            JOIN cmr_search_history h ON h.id = v.search_id AND h.del_flag = 'F'
            WHERE v.del_flag = 'F' AND v.vote = 'down'
            """)
    long countDownSearches();

    /**
     * 分页查询有点踩的检索任务，按被踩条目数倒序、同数量按最近时间倒序。
     *
     * @param offset 偏移量
     * @param size   每页条数
     * @return 难例样本
     * @author cmr
     * @since 2026-11-11
     */
    @Select("""
            SELECT COALESCE(t.biz_no, h.biz_no) AS task_no,
                   h.query_text AS query,
                   COUNT(DISTINCT v.item_id) AS down_count,
                   h.result_set_id AS result_set_id,
                   h.create_time AS create_time
            FROM cmr_item_vote v
            JOIN cmr_search_history h ON h.id = v.search_id AND h.del_flag = 'F'
            LEFT JOIN cmr_task t ON t.id = h.task_id AND t.del_flag = 'F'
            WHERE v.del_flag = 'F' AND v.vote = 'down'
            GROUP BY h.id, COALESCE(t.biz_no, h.biz_no), h.query_text, h.result_set_id, h.create_time
            ORDER BY down_count DESC, h.create_time DESC
            LIMIT #{size} OFFSET #{offset}
            """)
    List<HardCaseVO> selectHardCases(@Param("offset") int offset, @Param("size") int size);
}
