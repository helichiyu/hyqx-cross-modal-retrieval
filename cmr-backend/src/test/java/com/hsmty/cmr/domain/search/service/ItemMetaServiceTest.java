package com.hsmty.cmr.domain.search.service;

import com.hsmty.cmr.config.CmrProperties;
import com.hsmty.cmr.domain.dataset.entity.CmrIndex;
import com.hsmty.cmr.domain.dataset.mapper.IndexMapper;
import com.hsmty.cmr.file.FileService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ItemMetaServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void 按检索时索引的metaDb回查条目名称与内容() throws Exception {
        Path dataset = Files.createDirectories(tempDir.resolve("dataset"));
        Path runtime = Files.createDirectories(tempDir.resolve("runtime"));
        Path indexDir = Files.createDirectories(runtime.resolve("index/test-index"));
        Path metaDb = indexDir.resolve("meta.db");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + metaDb);
             PreparedStatement statement = connection.prepareStatement(
                     "CREATE TABLE items (itemId TEXT PRIMARY KEY, modality TEXT, channel TEXT, sourceFile TEXT, locator TEXT, text TEXT)")) {
            statement.execute();
        }
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + metaDb);
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO items(itemId,modality,channel,sourceFile,locator,text) VALUES(?,?,?,?,?,?)")) {
            statement.setString(1, "ITM001");
            statement.setString(2, "image");
            statement.setString(3, "clip");
            statement.setString(4, "dataset/sample/dog.jpg");
            statement.setString(5, "{\"frame\":1}");
            statement.setString(6, "巡检犬只");
            statement.executeUpdate();
        }

        CmrProperties properties = new CmrProperties();
        properties.getVolume().setDataset(dataset.toString());
        properties.getVolume().setRuntime(runtime.toString());
        IndexMapper indexMapper = mock(IndexMapper.class);
        CmrIndex index = new CmrIndex();
        index.setId(1L);
        index.setIndexDir(indexDir.toString());
        when(indexMapper.selectOneById(1L)).thenReturn(index);

        ItemMetaService service = new ItemMetaService(indexMapper, new FileService(properties));
        var result = service.lookup(1L, java.util.List.of("ITM001", "ITM404"));

        assertThat(result).containsKey("ITM001");
        assertThat(result).doesNotContainKey("ITM404");
        assertThat(result.get("ITM001").name()).isEqualTo("dog.jpg");
        assertThat(result.get("ITM001").sourceFile()).isEqualTo("dataset/sample/dog.jpg");
        assertThat(result.get("ITM001").text()).isEqualTo("巡检犬只");
    }

    @Test
    void 索引不存在或磁盘元数据缺失时返回空() {
        IndexMapper indexMapper = mock(IndexMapper.class);
        CmrProperties properties = new CmrProperties();
        ItemMetaService service = new ItemMetaService(indexMapper, new FileService(properties));

        assertThat(service.lookup(99L, java.util.List.of("ITM001"))).isEmpty();
        CmrIndex index = new CmrIndex();
        index.setId(1L);
        index.setIndexDir(tempDir.resolve("missing-index").toString());
        when(indexMapper.selectOneById(1L)).thenReturn(index);
        assertThat(service.lookup(1L, java.util.List.of("ITM001"))).isEmpty();
    }
}
