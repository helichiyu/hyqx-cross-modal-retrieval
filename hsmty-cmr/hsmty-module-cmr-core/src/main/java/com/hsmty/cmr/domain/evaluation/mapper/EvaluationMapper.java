package com.hsmty.cmr.domain.evaluation.mapper;

import com.hsmty.cmr.domain.evaluation.entity.CmrEvaluation;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 效果评测 Mapper
 *
 * @author cmr
 * @since 2026-11-10
 */
@Mapper
public interface EvaluationMapper extends BaseMapper<CmrEvaluation> {

    /**
     * 评测总数（逻辑未删除）。
     *
     * @return 评测条数
     */
    @Select("SELECT COUNT(*) FROM cmr_evaluation WHERE del_flag = 'F'")
    Long countEvaluations();

    /**
     * 各评测基准结果条目的去重条目数。
     *
     * @return 基准样本去重条数
     */
    @Select("""
            SELECT COUNT(DISTINCT ri.item_id)
            FROM cmr_evaluation e
            JOIN cmr_result_item ri ON ri.search_id = e.base_search_id AND ri.del_flag = 'F'
            WHERE e.del_flag = 'F'
            """)
    Long countBaseItems();

    /**
     * 出现在多个评测基准中的条目数（重复使用样本）。
     *
     * @return 重复条目数
     */
    @Select("""
            SELECT COUNT(*) FROM (
                SELECT ri.item_id
                FROM cmr_evaluation e
                JOIN cmr_result_item ri ON ri.search_id = e.base_search_id AND ri.del_flag = 'F'
                WHERE e.del_flag = 'F'
                GROUP BY ri.item_id
                HAVING COUNT(DISTINCT e.id) > 1
            ) t
            """)
    Long countReusedItems();

    /**
     * 基准样本中已失效的条目数：条目被屏蔽，或来源已进数据源黑名单。
     *
     * @return 失效条目数
     */
    @Select("""
            SELECT COUNT(DISTINCT ri.item_id)
            FROM cmr_evaluation e
            JOIN cmr_result_item ri ON ri.search_id = e.base_search_id AND ri.del_flag = 'F'
            WHERE e.del_flag = 'F'
              AND (EXISTS (SELECT 1 FROM cmr_blocked_item bi
                           WHERE bi.item_id = ri.item_id AND bi.del_flag = 'F')
                OR EXISTS (SELECT 1 FROM cmr_datasource_blacklist bl
                           WHERE bl.del_flag = 'F'
                             AND (bl.source_name = ri.source OR bl.dataset_id = ri.source)))
            """)
    Long countInvalidItems();

    /**
     * 评价存在矛盾的条目数：同一条目在一个检索任务点有用、在另一个检索任务点无用（跨任务口径，
     * 同一任务内不同用户评价相反不计入）。
     *
     * @return 评价矛盾条目数
     */
    @Select("""
            SELECT COUNT(DISTINCT v1.item_id)
            FROM cmr_item_vote v1
            JOIN cmr_item_vote v2 ON v2.item_id = v1.item_id
                AND v2.del_flag = 'F' AND v2.vote = 'down'
                AND v2.search_id <> v1.search_id
            WHERE v1.del_flag = 'F' AND v1.vote = 'up'
            """)
    Long countContradictoryItems();
}
