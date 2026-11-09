package com.hsmty.cmr.domain.search.service;

import com.hsmty.cmr.domain.dataset.entity.CmrIndex;
import com.hsmty.cmr.domain.dataset.mapper.IndexMapper;
import com.hsmty.cmr.file.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 结果条目元数据回查：按检索时索引的 meta.db 读取名称、来源、定位和文本。
 *
 * @author cmr
 * @since 2026-11-09
 */
@Service
@RequiredArgsConstructor
public class ItemMetaService {

    private static final int MAX_CACHE_ENTRIES = 10000;

    private final IndexMapper indexMapper;
    private final FileService fileService;

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public Map<String, ItemMeta> lookup(Long indexId, List<String> itemIds) {
        if (indexId == null || itemIds == null || itemIds.isEmpty()) {
            return Collections.emptyMap();
        }
        CmrIndex index = indexMapper.selectOneById(indexId);
        if (index == null) {
            return Collections.emptyMap();
        }
        Path metaDb = resolveMetaDb(index.getIndexDir());
        if (metaDb == null || !Files.isRegularFile(metaDb)) {
            return Collections.emptyMap();
        }
        long lastModified;
        long size;
        try {
            lastModified = Files.getLastModifiedTime(metaDb).toMillis();
            size = Files.size(metaDb);
        } catch (Exception e) {
            return Collections.emptyMap();
        }

        String pathKey = metaDb.toAbsolutePath().normalize().toString();
        Map<String, ItemMeta> result = new LinkedHashMap<>();
        List<String> missing = new ArrayList<>();
        for (String itemId : itemIds) {
            if (itemId == null || itemId.isBlank()) {
                continue;
            }
            CacheEntry cached = cache.get(cacheKey(pathKey, lastModified, size, itemId));
            if (cached != null) {
                if (cached.found()) {
                    result.put(itemId, cached.meta());
                }
            } else if (!missing.contains(itemId)) {
                missing.add(itemId);
            }
        }
        if (missing.isEmpty()) {
            return result;
        }

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + metaDb.toAbsolutePath());
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT itemId, modality, sourceFile, locator, text FROM items WHERE itemId IN (" +
                             String.join(",", Collections.nCopies(missing.size(), "?")) + ")")) {
            for (int i = 0; i < missing.size(); i++) {
                statement.setString(i + 1, missing.get(i));
            }
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    String itemId = rows.getString("itemId");
                    ItemMeta meta = new ItemMeta(
                            itemId,
                            displayName(itemId, rows.getString("sourceFile"), rows.getString("text")),
                            rows.getString("modality"),
                            rows.getString("sourceFile"),
                            rows.getString("locator"),
                            rows.getString("text"));
                    result.put(itemId, meta);
                    remember(pathKey, lastModified, size, itemId, true, meta);
                }
            }
            for (String itemId : missing) {
                if (!result.containsKey(itemId)) {
                    remember(pathKey, lastModified, size, itemId, false, null);
                }
            }
        } catch (Exception e) {
            return result;
        }
        return result;
    }

    private Path resolveMetaDb(String indexDir) {
        if (indexDir == null || indexDir.isBlank()) {
            return null;
        }
        String normalized = indexDir.replace('\\', '/').replaceAll("/+$", "");
        if (normalized.startsWith("runtime/") || normalized.startsWith("dataset/")) {
            try {
                return fileService.resolve(normalized + "/meta.db").absolute();
            } catch (RuntimeException e) {
                return null;
            }
        }
        Path directory = Path.of(indexDir).toAbsolutePath().normalize();
        Path runtime = fileService.runtimeRoot().toAbsolutePath().normalize();
        Path dataset = fileService.datasetRoot().toAbsolutePath().normalize();
        if (!directory.startsWith(runtime) && !directory.startsWith(dataset)) {
            return null;
        }
        return directory.resolve("meta.db").normalize();
    }

    private String displayName(String itemId, String sourceFile, String text) {
        if (sourceFile != null && !sourceFile.isBlank()) {
            String normalized = sourceFile.replace('\\', '/');
            int slash = normalized.lastIndexOf('/');
            String name = slash >= 0 ? normalized.substring(slash + 1) : normalized;
            if (!name.isBlank()) {
                return name;
            }
        }
        if (text != null && !text.isBlank()) {
            return text.length() > 48 ? text.substring(0, 48) + "…" : text;
        }
        return itemId;
    }

    private String cacheKey(String path, long lastModified, long size, String itemId) {
        return path + "|" + lastModified + "|" + size + "|" + itemId;
    }

    private void remember(String path, long lastModified, long size, String itemId, boolean found, ItemMeta meta) {
        if (cache.size() >= MAX_CACHE_ENTRIES) {
            cache.clear();
        }
        cache.put(cacheKey(path, lastModified, size, itemId), new CacheEntry(found, meta));
    }

    public record ItemMeta(String itemId, String name, String modality, String sourceFile,
                           String locatorJson, String text) {
    }

    private record CacheEntry(boolean found, ItemMeta meta) {
    }
}
