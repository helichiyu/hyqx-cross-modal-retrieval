package com.hsmty.cmr.domain.search.service;

import com.hsmty.cmr.config.CmrProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SearchLogCleanupJobTest {

    @TempDir
    Path tempDir;

    @Test
    void 过期检索级联物理删除且反馈与评测引用永久保留() {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + tempDir.resolve("cleanup.db"));
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        createSchema(jdbc);
        Timestamp expired = Timestamp.valueOf(LocalDateTime.now().minusDays(31));
        Timestamp recent = Timestamp.valueOf(LocalDateTime.now());
        jdbc.update("INSERT INTO cmr_search_history(id,create_time) VALUES(1,?)", expired);
        jdbc.update("INSERT INTO cmr_search_history(id,create_time) VALUES(2,?)", expired);
        jdbc.update("INSERT INTO cmr_search_history(id,create_time) VALUES(3,?)", expired);
        jdbc.update("INSERT INTO cmr_search_history(id,create_time) VALUES(4,?)", recent);
        jdbc.update("INSERT INTO cmr_feedback(search_id) VALUES(2)");
        jdbc.update("INSERT INTO cmr_eval_item(search_id) VALUES(3)");
        jdbc.update("INSERT INTO cmr_result_item(search_id,result_rank) VALUES(1,1),(2,1)");
        jdbc.update("INSERT INTO cmr_click_event(search_id,result_rank) VALUES(1,1),(2,1)");
        jdbc.update("INSERT INTO cmr_task(id,type,ref_id) VALUES(101,'search',1),(102,'search',2),(103,'build',1)");
        jdbc.update("INSERT INTO cmr_task_log(task_id) VALUES(101),(102),(103)");

        CmrProperties properties = new CmrProperties();
        properties.getRetention().setSearchLogDays(30);
        properties.getRetention().setSearchLogBatchSize(10);
        new SearchLogCleanupJob(new NamedParameterJdbcTemplate(dataSource), properties).cleanup();

        assertThat(jdbc.queryForList("SELECT id FROM cmr_search_history ORDER BY id", Long.class))
                .containsExactly(2L, 3L, 4L);
        assertThat(jdbc.queryForList("SELECT search_id FROM cmr_result_item ORDER BY search_id", Long.class))
                .containsExactly(2L);
        assertThat(jdbc.queryForList("SELECT search_id FROM cmr_click_event ORDER BY search_id", Long.class))
                .containsExactly(2L);
        assertThat(jdbc.queryForList("SELECT id FROM cmr_task ORDER BY id", Long.class))
                .containsExactly(102L, 103L);
        assertThat(jdbc.queryForList("SELECT task_id FROM cmr_task_log ORDER BY task_id", Long.class))
                .containsExactly(102L, 103L);
    }

    private void createSchema(JdbcTemplate jdbc) {
        jdbc.execute("CREATE TABLE cmr_search_history(id INTEGER PRIMARY KEY, create_time TIMESTAMP)");
        jdbc.execute("CREATE TABLE cmr_feedback(search_id INTEGER)");
        jdbc.execute("CREATE TABLE cmr_eval_item(search_id INTEGER)");
        jdbc.execute("CREATE TABLE cmr_result_item(search_id INTEGER, result_rank INTEGER)");
        jdbc.execute("CREATE TABLE cmr_click_event(search_id INTEGER, result_rank INTEGER)");
        jdbc.execute("CREATE TABLE cmr_task(id INTEGER PRIMARY KEY, type TEXT, ref_id INTEGER)");
        jdbc.execute("CREATE TABLE cmr_task_log(task_id INTEGER)");
    }
}
