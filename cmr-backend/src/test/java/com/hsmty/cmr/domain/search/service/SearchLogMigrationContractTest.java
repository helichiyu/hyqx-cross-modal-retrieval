package com.hsmty.cmr.domain.search.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SearchLogMigrationContractTest {

    @Test
    void 迁移005使用独立编号和幂等前置条件() throws Exception {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/changelog/005_cmr_search_log_id_snapshot.xml"));
        String master = Files.readString(Path.of(
                "src/main/resources/db/changelog/changelog-master.xml"));

        assertThat(migration).contains("cmr-search-log-add-index-id-20261109");
        assertThat(migration).contains("columnExists tableName=\"cmr_search_history\" columnName=\"index_id\"");
        assertThat(migration).contains("columnExists tableName=\"cmr_result_item\" columnName=\"source_file\"");
        assertThat(migration).contains("columnExists tableName=\"cmr_result_item\" columnName=\"locator_json\"");
        assertThat(migration).contains("columnExists tableName=\"cmr_result_item\" columnName=\"text\"");
        assertThat(migration).contains("indexExists tableName=\"cmr_eval_item\" indexName=\"idx_search_id\"");
        assertThat(master).contains("005_cmr_search_log_id_snapshot.xml");
    }
}
