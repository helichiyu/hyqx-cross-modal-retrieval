package com.hsmty.cmr.domain.search.service;

import com.hsmty.cmr.config.CmrProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 检索日志保留清理：30 天外物理删除，反馈/评测/赞踩/收藏引用的记录永久保留。
 *
 * @author cmr
 * @since 2026-11-09
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SearchLogCleanupJob {

    static final String EXPIRED_SEARCH_SQL = """
            SELECT h.id
            FROM cmr_search_history h
            WHERE h.create_time < :cutoff
              AND NOT EXISTS (SELECT 1 FROM cmr_feedback f WHERE f.search_id = h.id)
              AND NOT EXISTS (SELECT 1 FROM cmr_eval_item e WHERE e.search_id = h.id)
              AND NOT EXISTS (SELECT 1 FROM cmr_item_vote v WHERE v.search_id = h.id)
              AND NOT EXISTS (SELECT 1 FROM cmr_favorite fav WHERE fav.task_no = h.biz_no)
            ORDER BY h.id
            LIMIT :limit
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final CmrProperties properties;

    @Scheduled(cron = "${cmr.retention.search-log-cron:0 30 2 * * *}")
    @Transactional(rollbackFor = Exception.class)
    public void cleanup() {
        int batchSize = Math.max(properties.getRetention().getSearchLogBatchSize(), 1);
        int days = Math.max(properties.getRetention().getSearchLogDays(), 1);
        Timestamp cutoff = Timestamp.valueOf(LocalDateTime.now().minusDays(days));
        int total = 0;
        while (true) {
            List<Long> ids = jdbc.queryForList(EXPIRED_SEARCH_SQL,
                    new MapSqlParameterSource().addValue("cutoff", cutoff).addValue("limit", batchSize),
                    Long.class);
            if (ids.isEmpty()) {
                break;
            }
            deleteBatch(ids);
            total += ids.size();
            if (ids.size() < batchSize) {
                break;
            }
        }
        if (total > 0) {
            log.info("检索日志清理完成: deleted={} cutoff={} days={}", total, cutoff, days);
        }
    }

    void deleteBatch(List<Long> searchIds) {
        MapSqlParameterSource params = new MapSqlParameterSource("ids", searchIds);
        List<Long> taskIds = jdbc.queryForList(
                "SELECT id FROM cmr_task WHERE type = 'search' AND ref_id IN (:ids)",
                params, Long.class);
        if (!taskIds.isEmpty()) {
            MapSqlParameterSource taskParams = new MapSqlParameterSource("taskIds", taskIds);
            jdbc.update("DELETE FROM cmr_task_log WHERE task_id IN (:taskIds)", taskParams);
            jdbc.update("DELETE FROM cmr_task WHERE id IN (:taskIds)", taskParams);
        }
        jdbc.update("DELETE FROM cmr_click_event WHERE search_id IN (:ids)", params);
        jdbc.update("DELETE FROM cmr_result_item WHERE search_id IN (:ids)", params);
        jdbc.update("DELETE FROM cmr_search_history WHERE id IN (:ids)", params);
    }
}
