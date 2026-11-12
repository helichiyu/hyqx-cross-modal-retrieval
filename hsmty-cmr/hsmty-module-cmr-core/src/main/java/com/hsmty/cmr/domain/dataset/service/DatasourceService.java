package com.hsmty.cmr.domain.dataset.service;

import com.hsmty.cmr.common.exception.BizException;
import com.hsmty.cmr.common.id.BizNoGenerator;
import com.hsmty.cmr.common.id.SnowflakeIdWorker;
import com.hsmty.cmr.common.util.AesUtils;
import com.hsmty.cmr.config.CmrProperties;
import com.hsmty.cmr.domain.dataset.dto.DatasourceCreateDTO;
import com.hsmty.cmr.domain.dataset.dto.DatasourceIngestDTO;
import com.hsmty.cmr.domain.dataset.entity.CmrDataset;
import com.hsmty.cmr.domain.dataset.entity.CmrDatasource;
import com.hsmty.cmr.domain.dataset.mapper.DatasourceBlacklistMapper;
import com.hsmty.cmr.domain.dataset.mapper.DatasourceMapper;
import com.hsmty.cmr.domain.dataset.mapper.DatasetMapper;
import com.hsmty.cmr.domain.dataset.vo.DatasetVO;
import com.hsmty.cmr.domain.dataset.vo.DatasourceCheckVO;
import com.hsmty.cmr.domain.dataset.vo.DatasourceTestVO;
import com.hsmty.cmr.domain.dataset.vo.DatasourceVO;
import com.hsmty.cmr.file.FileService;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hsmty.cmr.domain.dataset.entity.table.CmrDatasetTableDef.CMR_DATASET;
import static com.hsmty.cmr.domain.dataset.entity.table.CmrDatasourceTableDef.CMR_DATASOURCE;

/**
 * 数据源服务：登记（认证信息 AES 落库）与列表
 *
 * @author cmr
 * @since 2026-11-03
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatasourceService {

    private static final Set<String> PULL_PROTOCOLS = Set.of("pull", "http", "api");
    private static final Set<String> PUSH_PROTOCOLS = Set.of("push", "upload");

    /** 回源代理只放行媒体路径，避免借已登记主机读取数据集等受保护接口 */
    private static final Set<String> MEDIA_EXTENSIONS = Set.of(
            ".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp",
            ".wav", ".mp3", ".flac", ".ogg", ".m4a", ".aac",
            ".mp4", ".webm", ".mov", ".mkv", ".avi");

    /** 上游 4 项必填的固定顺序（与契约 dataset_id|name|item_count|modals 一致） */
    private static final List<String> REQUIRED_FIELDS = List.of("dataset_id", "name", "item_count", "modals");

    /** 4 项必填的中文名（测试连接面板逐字段展示） */
    private static final java.util.Map<String, String> REQUIRED_FIELD_LABELS;

    static {
        java.util.Map<String, String> labels = new LinkedHashMap<>();
        labels.put("dataset_id", "数据集 ID");
        labels.put("name", "名称");
        labels.put("item_count", "条目数");
        labels.put("modals", "模态字段");
        REQUIRED_FIELD_LABELS = java.util.Collections.unmodifiableMap(labels);
    }

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final DatasourceMapper datasourceMapper;
    private final DatasetMapper datasetMapper;
    private final DatasourceBlacklistMapper blacklistMapper;
    private final DatasetService datasetService;
    private final FileService fileService;
    private final SnowflakeIdWorker idWorker;
    private final BizNoGenerator bizNoGenerator;
    private final CmrProperties properties;

    /** 上游数据源契约（P4 只读面板）：数据集与条目两级必填字段 */
    public java.util.Map<String, Object> contract() {
        java.util.Map<String, Object> dataset = new java.util.LinkedHashMap<>();
        dataset.put("required", List.of("dataset_id", "name", "item_count"));
        dataset.put("optional", List.of("modal_types"));
        java.util.Map<String, Object> item = new java.util.LinkedHashMap<>();
        item.put("required", List.of("item_id", "modals"));
        item.put("modalRule", "modals 1-4 个，每项为 {type,url} 或 {type,content}，type ∈ text/image/audio/video");
        return java.util.Map.of(
                "endpoints", List.of("GET {base}/datasets", "GET {base}/datasets/{dataset_id}",
                        "GET {base}/datasets/{dataset_id}/items?page=1&size=20"),
                "dataset", dataset,
                "item", item,
                "pushMode", "不支持 push；平台只拉元数据与条目定位，图片/音频/视频按 modals[].url 回源，"
                        + "不落原始内容；media URL 必须自包含鉴权（预签名）或公开可读，"
                        + "并在接入与检索期间持续可访问");
    }

    /** 拉取上游数据集列表（P4 数据源库列表 + 4 项必填校验） */
    public java.util.Map<String, Object> remoteDatasets(Long id) {
        CmrDatasource datasource = datasourceMapper.selectOneById(id);
        BizException.assertNull(datasource, "数据源不存在");
        BizException.assertTrue(!isPullProtocol(datasource.getProtocol()), "仅接口协议支持拉取上游数据集");
        BizException.assertNull(datasource.getEndpoint(), "数据源未配置接口地址");
        return fetchDatasets(datasource);
    }

    /**
     * 同步（只做元数据发现，不触发建库链）：已连接数据源才允许，黑名单来源整源跳过。
     *
     * <p>连接状态只由测试连接驱动，同步不再改写，避免「字段不全仍转已连接」。
     */
    public java.util.Map<String, Object> sync(Long id) {
        CmrDatasource datasource = requireDatasource(id);
        BizException.assertTrue(!"connected".equals(datasource.getConnectStatus()),
                "数据源未连接，请先测试连接");
        java.util.Map<String, Object> result;
        try {
            result = fetchDatasets(datasource);
        } catch (BizException e) {
            datasource.setRequiredOk(false);
            datasource.setLastTestMessage(e.getMessage());
            datasource.setLastTestTime(LocalDateTime.now());
            datasourceMapper.update(datasource, false);
            throw e;
        }
        datasource.setRequiredOk(Boolean.TRUE.equals(result.get("requiredOk")));
        datasource.setMissingFields(joinFields(result.get("missingFields")));
        datasource.setUpstreamDatasetId((String) result.get("firstDatasetId"));
        datasource.setTotalCount((Integer) result.get("totalItems"));
        datasource.setLastSyncTime(LocalDateTime.now());
        datasource.setLastTestMessage("同步成功，数据集 " + result.get("datasetCount") + " 个"
                + (intOf(result.get("skippedBlacklisted")) > 0
                ? "（黑名单跳过 " + result.get("skippedBlacklisted") + " 个）" : ""));
        datasource.setLastTestTime(LocalDateTime.now());
        datasourceMapper.update(datasource, false);
        return result;
    }

    /**
     * 接入（P4）：只拉取上游元数据与条目定位，落 manifest；原始内容仍在上游，按 URL 回源。
     *
     * <p>与同步的分工：同步只发现数据集；接入登记数据集并生成 403 可解析的 manifest，
     * 触发 401→405 建库链。共享卷不保存图片/音频/视频本体。
     */
    public DatasetVO ingest(Long id, DatasourceIngestDTO dto) {
        CmrDatasource datasource = requireDatasource(id);
        BizException.assertNull(datasource.getEndpoint(), "数据源未配置接口地址");
        BizException.assertTrue(!"connected".equals(datasource.getConnectStatus()),
                "数据源未连接，请先测试连接");
        BizException.assertTrue(!Boolean.TRUE.equals(datasource.getRequiredOk()), "4 项必填校验未通过，不可接入");

        String base = trimBase(datasource.getEndpoint());
        String datasetId = dto.getDatasetId();
        java.util.Map<String, Object> probe = fetchDatasets(datasource);
        java.util.Map<String, Object> upstream = ((List<?>) probe.get("datasets")).stream()
                .filter(node -> node instanceof java.util.Map<?, ?> m && datasetId.equals(m.get("datasetId")))
                .map(node -> (java.util.Map<String, Object>) node)
                .findFirst()
                .orElseThrow(() -> new BizException("上游数据集不存在或已被黑名单排除: " + datasetId));

        // 去重：同一数据源的同一上游数据集只允许接入一次，避免重复建库
        BizException.assertTrue(datasetService.existsUpstream(datasource.getId(), datasetId), "该上游数据集已接入");

        Path dir = fileService.datasetRoot().resolve("ds").resolve(datasource.getBizNo()).resolve(safeName(datasetId));
        int itemCount = writeManifest(datasource, base, datasetId, dir, upstream);
        BizException.assertTrue(itemCount <= 0, "上游数据集无可用条目: " + datasetId);

        String logicalRoot = "dataset/ds/" + datasource.getBizNo() + "/" + safeName(datasetId);
        String name = dto.getName() != null && !dto.getName().isBlank()
                ? dto.getName() : String.valueOf(upstream.get("name"));
        DatasetVO dataset = datasetService.registerUpstream(datasource.getId(), datasetId, name,
                dto.getVersion(), dto.getScene(), logicalRoot);
        log.info("数据源接入: source={} datasetId={} items={} dataset={}",
                datasource.getBizNo(), datasetId, itemCount, dataset.getBizNo());
        return dataset;
    }

    /**
     * 分页拉取条目元数据，写 manifest.jsonl 与 metadata.json（不下载任何媒体文件）。
     *
     * <p>manifest 每行 {@code {"itemId","modals":[{"type","content"|"url","ext"}]}}；
     * 403 据此按 URL 流式取用原始内容，解析完即清理临时文件。
     */
    private int writeManifest(CmrDatasource datasource, String base, String datasetId, Path dir,
                              java.util.Map<String, Object> upstream) {
        BizException.assertTrue(!dirBelongs(dir), "接入目录非法");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new BizException("接入目录创建失败: " + e.getMessage(), e);
        }
        List<String> lines = new ArrayList<>();
        Set<String> modalities = new LinkedHashSet<>();
        Integer declaredCount = intOf(upstream.get("itemCount"));
        // 按上游声明条数推算翻页上限；未声明时保留 1000 页硬保护，避免上游分页异常导致死循环
        int maxPages = declaredCount != null && declaredCount > 0
                ? (declaredCount + 99) / 100 + 5 : 1000;
        int page = 1;
        for (; page <= maxPages; page++) {
            String body = getJson(datasource, base + "/datasets/" + datasetId + "/items?page=" + page + "&size=100");
            List<java.util.Map<String, Object>> items = unwrapList(body, "items");
            if (items.isEmpty()) {
                break;
            }
            for (java.util.Map<String, Object> item : items) {
                String line = manifestLine(item, modalities);
                if (line != null) {
                    lines.add(line);
                }
            }
            if (items.size() < 100) {
                break;
            }
        }
        BizException.assertTrue(page > maxPages, "上游条目分页超过安全上限 " + maxPages + " 页，请检查分页契约");
        if (declaredCount != null && declaredCount > 0) {
            BizException.assertTrue(lines.size() != declaredCount,
                    "manifest 条目数与上游声明不一致：声明 " + declaredCount + " 条，实际 " + lines.size() + " 条");
        }
        try {
            Files.writeString(dir.resolve("manifest.jsonl"), String.join("\n", lines) + (lines.isEmpty() ? "" : "\n"),
                    StandardCharsets.UTF_8);
            Files.writeString(dir.resolve("metadata.json"), com.hsmty.cmr.common.util.JsonUtils.write(
                            java.util.Map.of("name", String.valueOf(upstream.get("name")),
                                    "count", declaredCount != null && declaredCount > 0 ? declaredCount : lines.size(),
                                    "modalities", new ArrayList<>(modalities),
                                    "scenes", List.of("upstream"))),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BizException("manifest 写入失败: " + e.getMessage(), e);
        }
        return lines.size();
    }

    private String manifestLine(java.util.Map<String, Object> item, Set<String> modalities) {
        String itemId = str(item.get("item_id"));
        if (itemId == null || !(item.get("modals") instanceof List<?> modals) || modals.isEmpty()) {
            return null;
        }
        List<java.util.Map<String, Object>> normalized = new ArrayList<>();
        for (Object modal : modals) {
            if (!(modal instanceof java.util.Map<?, ?> m)) {
                continue;
            }
            String type = str(m.get("type"));
            if (type == null || !Set.of("text", "image", "audio", "video").contains(type)) {
                continue;
            }
            java.util.Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("type", type);
            // 契约：文本只写 content，图片/音频/视频只写 url；401/403 按同一口径校验
            if ("text".equals(type)) {
                Object content = m.get("content");
                if (content == null || String.valueOf(content).isBlank()) {
                    continue;
                }
                entry.put("content", String.valueOf(content));
            } else {
                Object url = m.get("url");
                if (url == null) {
                    continue;
                }
                String urlText = String.valueOf(url);
                entry.put("url", urlText);
                entry.put("ext", extOf(urlText));
            }
            normalized.add(entry);
            modalities.add(type);
        }
        if (normalized.isEmpty()) {
            return null;
        }
        return com.hsmty.cmr.common.util.JsonUtils.write(
                java.util.Map.of("itemId", itemId, "modals", normalized));
    }

    private String extOf(String url) {
        if (url == null) {
            return ".bin";
        }
        int slash = url.lastIndexOf('/');
        String name = slash >= 0 ? url.substring(slash + 1) : url;
        int query = name.indexOf('?');
        if (query >= 0) {
            name = name.substring(0, query);
        }
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot) : ".bin";
    }

    private String safeName(String value) {
        return value == null ? "" : value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    /** 接入目录必须落在 dataset/ds 下，防止越界写 */
    private boolean dirBelongs(Path dir) {
        return dir.toAbsolutePath().normalize().startsWith(fileService.datasetRoot().resolve("ds"));
    }

    /**
     * 媒体 URL 是否属于已登记数据源（回源代理的 SSRF 白名单）。
     *
     * @param url 上游媒体地址
     * @return 命中已登记数据源返回 true
     */
    public boolean isTrustedMediaUrl(String url) {
        return matchingDatasource(url) != null;
    }

    /**
     * 回源请求头：命中数据源且配置了 token/basic 认证时返回 Authorization 值。
     *
     * @param url 上游媒体地址
     * @return Authorization 值；无需认证或不在白名单时返回 null
     */
    public String authHeaderFor(String url) {
        CmrDatasource datasource = matchingDatasource(url);
        if (datasource == null) {
            return null;
        }
        String authConfig = decryptAuth(datasource);
        if (authConfig == null || authConfig.isBlank()) {
            return null;
        }
        if ("token".equals(datasource.getAuthType())) {
            return "Bearer " + authConfig;
        }
        if ("basic".equals(datasource.getAuthType())) {
            String credential = authConfig.contains(":") ? authConfig : authConfig + ":";
            return "Basic " + Base64.getEncoder()
                    .encodeToString(credential.getBytes(StandardCharsets.UTF_8));
        }
        return null;
    }

    /**
     * 按协议 + 主机 + 端口 + 路径匹配已登记数据源，避免回源代理访问任意地址。
     *
     * <p>除主机一致外还要求：URL 必须位于数据源接口地址之下，且扩展名为媒体类型。
     * 这样即使上游的 /datasets 等受保护接口与媒体同主机，也无法借回源代理读取。
     */
    private CmrDatasource matchingDatasource(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        URI target;
        try {
            target = URI.create(url);
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (!"http".equalsIgnoreCase(target.getScheme()) && !"https".equalsIgnoreCase(target.getScheme())) {
            return null;
        }
        if (!isMediaPath(target.getPath())) {
            return null;
        }
        int targetPort = effectivePort(target);
        List<CmrDatasource> sources = datasourceMapper.selectListByQuery(QueryWrapper.create());
        for (CmrDatasource source : sources) {
            if (source.getEndpoint() == null || source.getEndpoint().isBlank()) {
                continue;
            }
            try {
                URI base = URI.create(trimBase(source.getEndpoint()));
                if (target.getScheme().equalsIgnoreCase(base.getScheme())
                        && target.getHost() != null && target.getHost().equalsIgnoreCase(base.getHost())
                        && targetPort == effectivePort(base)
                        && pathUnderBase(target.getPath(), base.getPath())) {
                    return source;
                }
            } catch (IllegalArgumentException ignored) {
                // 跳过格式非法的数据源地址
            }
        }
        return null;
    }

    /** 路径必须带媒体扩展名，且不能包含路径穿越片段 */
    private boolean isMediaPath(String path) {
        if (path == null || path.indexOf("..") >= 0) {
            return false;
        }
        int dot = path.lastIndexOf('.');
        return dot >= 0 && MEDIA_EXTENSIONS.contains(path.substring(dot).toLowerCase(java.util.Locale.ROOT));
    }

    /** 目标路径需位于数据源接口路径之下（数据源地址无路径时不限制） */
    private boolean pathUnderBase(String targetPath, String basePath) {
        if (basePath == null || basePath.isBlank() || "/".equals(basePath)) {
            return true;
        }
        String prefix = basePath.endsWith("/") ? basePath : basePath + "/";
        return targetPath != null && (targetPath.equals(basePath) || targetPath.startsWith(prefix));
    }

    private int effectivePort(URI uri) {
        if (uri.getPort() > 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private CmrDatasource requireDatasource(Long id) {
        CmrDatasource datasource = datasourceMapper.selectOneById(id);
        BizException.assertNull(datasource, "数据源不存在");
        return datasource;
    }

    private java.util.Map<String, Object> fetchDatasets(CmrDatasource datasource) {
        String base = trimBase(datasource.getEndpoint());
        String body = getJson(datasource, base + "/datasets");
        java.util.List<java.util.Map<String, Object>> datasets = unwrapList(body, "datasets");
        BizException.assertTrue(datasets.isEmpty(), "上游未返回任何数据集");

        // 黑名单：来源名命中整源排除；dataset_id 命中单个数据集排除
        Set<String> blackSourceNames = new HashSet<>();
        Set<String> blackDatasetIds = new HashSet<>();
        blacklistMapper.selectListByQuery(QueryWrapper.create()).forEach(b -> {
            if (b.getSourceName() != null && !b.getSourceName().isBlank()) {
                blackSourceNames.add(b.getSourceName());
            }
            if (b.getDatasetId() != null && !b.getDatasetId().isBlank()) {
                blackDatasetIds.add(b.getDatasetId());
            }
        });
        boolean wholeSourceBlacklisted = blackSourceNames.contains(datasource.getName());
        java.util.List<java.util.Map<String, Object>> allowed = new java.util.ArrayList<>();
        int skippedBlacklisted = 0;
        for (java.util.Map<String, Object> ds : datasets) {
            String datasetId = str(ds.get("dataset_id"));
            String name = str(ds.get("name"));
            boolean black = wholeSourceBlacklisted || (datasetId != null && blackDatasetIds.contains(datasetId))
                    || (name != null && blackSourceNames.contains(name));
            if (black) {
                skippedBlacklisted++;
            } else {
                allowed.add(ds);
            }
        }
        BizException.assertTrue(allowed.isEmpty(), "上游数据集全部在数据源黑名单中");

        java.util.List<java.util.Map<String, Object>> briefs = new java.util.ArrayList<>();
        boolean requiredOk = true;
        Integer totalItems = 0;
        String firstDatasetId = null;
        // 缺失必填项取各数据集并集（同一字段只需列一次），最终按契约固定顺序输出
        Set<String> missingFields = new LinkedHashSet<>();
        for (java.util.Map<String, Object> ds : allowed) {
            String datasetId = str(ds.get("dataset_id"));
            String name = str(ds.get("name"));
            Integer itemCount = intOf(ds.get("item_count"));
            List<String> datasetMissing = new ArrayList<>();
            if (datasetId == null) {
                datasetMissing.add("dataset_id");
            }
            if (name == null) {
                datasetMissing.add("name");
            }
            if (itemCount == null) {
                datasetMissing.add("item_count");
            }
            boolean ok = datasetMissing.isEmpty();
            requiredOk = requiredOk && ok;
            missingFields.addAll(datasetMissing);
            if (firstDatasetId == null) {
                firstDatasetId = datasetId;
            }
            totalItems += itemCount == null ? 0 : itemCount;
            java.util.Map<String, Object> brief = new java.util.LinkedHashMap<>();
            brief.put("datasetId", datasetId);
            brief.put("name", name);
            brief.put("itemCount", itemCount);
            brief.put("modalTypes", ds.get("modal_types"));
            brief.put("requiredOk", ok);
            brief.put("missingFields", datasetMissing);
            briefs.add(brief);
        }
        // 抽样校验条目契约：item_id + modals（1-4 项）
        ItemCheck itemCheck = sampleItemCheck(datasource, base, firstDatasetId);
        if (itemCheck.field() != null) {
            missingFields.add(itemCheck.field());
        }
        List<String> orderedMissing = REQUIRED_FIELDS.stream().filter(missingFields::contains).toList();
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("datasetCount", briefs.size());
        result.put("datasets", briefs);
        result.put("requiredOk", requiredOk && itemCheck.detail() == null);
        result.put("missingFields", orderedMissing);
        result.put("firstDatasetId", firstDatasetId);
        result.put("firstDataset", briefs.isEmpty() ? null : briefs.get(0));
        result.put("totalItems", totalItems);
        result.put("itemIssue", itemCheck.detail());
        result.put("skippedBlacklisted", skippedBlacklisted);
        return result;
    }

    /**
     * 抽样校验条目契约（item_id + modals，1-4 项）。
     *
     * @return 命中字段与说明；字段为 null 表示问题不在 4 项必填之内
     */
    private ItemCheck sampleItemCheck(CmrDatasource datasource, String base, String datasetId) {
        if (datasetId == null) {
            return new ItemCheck("dataset_id", "缺少 dataset_id，无法校验条目契约");
        }
        try {
            String body = getJson(datasource, base + "/datasets/" + datasetId + "/items?page=1&size=1");
            java.util.List<java.util.Map<String, Object>> items = unwrapList(body, "items");
            if (items.isEmpty()) {
                return ItemCheck.OK;
            }
            java.util.Map<String, Object> item = items.get(0);
            if (str(item.get("item_id")) == null) {
                return new ItemCheck(null, "条目缺少 item_id");
            }
            Object modals = item.get("modals");
            if (!(modals instanceof java.util.List<?> list) || list.isEmpty() || list.size() > 4) {
                return new ItemCheck("modals", "条目 modals 必须为 1-4 项");
            }
            return ItemCheck.OK;
        } catch (BizException e) {
            return new ItemCheck(null, e.getMessage());
        }
    }

    /** 条目契约抽样结果：field 为命中的必填字段（可为 null），detail 为说明 */
    private record ItemCheck(String field, String detail) {

        private static final ItemCheck OK = new ItemCheck(null, null);
    }

    /**
     * 缺失项落库：逗号分隔；齐全为空串，未知为 null。
     *
     * @param value fetchDatasets 返回的 missingFields 列表
     * @return 落库字符串
     */
    @SuppressWarnings("unchecked")
    private String joinFields(Object value) {
        if (!(value instanceof List<?> list)) {
            return null;
        }
        List<String> fields = (List<String>) list;
        return fields.isEmpty() ? "" : String.join(",", fields);
    }

    private String getJson(CmrDatasource datasource, String url) {
        String authConfig = decryptAuth(datasource);
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(8))
                    .header("Accept", "application/json")
                    .GET();
            if ("token".equals(datasource.getAuthType()) && authConfig != null && !authConfig.isBlank()) {
                request.header("Authorization", "Bearer " + authConfig);
            } else if ("basic".equals(datasource.getAuthType()) && authConfig != null && !authConfig.isBlank()) {
                String credential = authConfig.contains(":") ? authConfig : authConfig + ":";
                request.header("Authorization", "Basic " + Base64.getEncoder()
                        .encodeToString(credential.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            }
            HttpResponse<String> response = HTTP_CLIENT.send(request.build(),
                    HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8));
            BizException.assertTrue(response.statusCode() < 200 || response.statusCode() >= 300,
                    "上游返回 HTTP " + response.statusCode());
            return response.body();
        } catch (IllegalArgumentException e) {
            throw new BizException("接口地址格式无效");
        } catch (IOException e) {
            throw new BizException("数据源不可达或连接超时");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException("同步被中断");
        }
    }

    @SuppressWarnings("unchecked")
    private java.util.List<java.util.Map<String, Object>> unwrapList(String body, String listKey) {
        Object parsed = com.hsmty.cmr.common.util.JsonUtils.read(body, Object.class);
        Object listNode = parsed;
        if (parsed instanceof java.util.Map<?, ?> map) {
            for (String key : List.of("data", listKey)) {
                if (map.get(key) instanceof List<?>) {
                    listNode = map.get(key);
                    break;
                }
            }
        }
        BizException.assertTrue(!(listNode instanceof java.util.List<?>), "上游响应不是数据集列表");
        java.util.List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();
        for (Object node : (java.util.List<?>) listNode) {
            if (node instanceof java.util.Map<?, ?> m) {
                result.add((java.util.Map<String, Object>) m);
            }
        }
        return result;
    }

    private String trimBase(String endpoint) {
        String base = endpoint.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }

    private String str(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? null : text;
    }

    private Integer intOf(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public List<DatasourceVO> list() {
        List<DatasourceVO> sources = datasourceMapper.selectListByQuery(
                        QueryWrapper.create().orderBy(CMR_DATASOURCE.ID.desc()))
                .stream().map(DatasourceVO::from).toList();
        for (DatasourceVO source : sources) {
            List<CmrDataset> datasets = datasetMapper.selectListByQuery(QueryWrapper.create()
                    .where(CMR_DATASET.DATASOURCE_ID.eq(source.getId())));
            source.setDatasetCount((long) datasets.size());
            // 向量化完成数 = 该数据源下已可检索数据集的实际条目数
            source.setVectorizedCount(datasets.stream()
                    .filter(ds -> CmrDataset.STATUS_SEARCHABLE.equals(ds.getStatus()))
                    .mapToInt(ds -> ds.getItemCount() == null ? 0 : ds.getItemCount())
                    .sum());
        }
        return sources;
    }

    public DatasourceVO create(DatasourceCreateDTO dto) {
        if (isPullProtocol(dto.getProtocol())) {
            BizException.assertNull(dto.getEndpoint(), "接口协议必须填写 endpoint");
            BizException.assertTrue(!dto.getEndpoint().startsWith("http://") && !dto.getEndpoint().startsWith("https://"),
                    "endpoint 必须以 http:// 或 https:// 开头");
        }
        boolean authenticated = "token".equals(dto.getAuthType()) || "basic".equals(dto.getAuthType());
        BizException.assertTrue(authenticated && (dto.getAuthConfig() == null || dto.getAuthConfig().isBlank()),
                "认证信息不能为空");
        BizException.assertTrue("basic".equals(dto.getAuthType())
                        && (dto.getAuthConfig() == null || !dto.getAuthConfig().contains(":")),
                "basic 认证信息格式必须为 username:password");
        CmrDatasource ds = new CmrDatasource();
        ds.setId(idWorker.next());
        ds.setBizNo(bizNoGenerator.next(BizNoGenerator.PREFIX_DATASOURCE));
        ds.setName(dto.getName());
        ds.setEndpoint(dto.getEndpoint());
        ds.setProtocol(dto.getProtocol());
        ds.setAuthType(dto.getAuthType());
        ds.setAuthConfig(encryptAuth(dto));
        ds.setConnectStatus("unconnected");
        ds.setRequiredOk(false);
        ds.setRemark(dto.getRemark());
        ds.setDelFlag("F");
        ds.setCreateTime(LocalDateTime.now());
        datasourceMapper.insert(ds, true);
        return DatasourceVO.from(ds);
    }

    /**
     * 测试数据源连接并逐字段校验 4 项必填。
     *
     * <p>连接状态按数据流状态机流转：地址不可达或接口报错 → {@code failed}；
     * 连接成功且 4/4 齐全 → {@code connected}；仅缺必填字段（接口可达）→ 状态保持不变。
     *
     * @param id 数据源 ID
     * @return 测试结果（含逐字段 checks）
     */
    public DatasourceTestVO test(Long id) {
        CmrDatasource datasource = requireDatasource(id);
        DatasourceTestVO result;
        boolean unreachable = false;
        if (isPushProtocol(datasource.getProtocol())) {
            result = DatasourceTestVO.builder()
                    .connected(null)
                    .statusCode(null)
                    .latencyMs(null)
                    .message("推送接收型数据源不适用主动连接测试，请通过推送方发起探测")
                    .build();
        } else if (!isPullProtocol(datasource.getProtocol())) {
            result = DatasourceTestVO.builder()
                    .connected(false)
                    .statusCode(null)
                    .latencyMs(null)
                    .message("未知数据源协议: " + datasource.getProtocol())
                    .build();
        } else {
            result = testEndpoint(datasource);
            if (Boolean.TRUE.equals(result.getConnected())) {
                try {
                    result = checkRequiredFields(datasource, result);
                } catch (BizException e) {
                    // 接口可达但拉取上游数据集报错：按数据流状态机转为连接失败
                    result.setConnected(false);
                    result.setMessage(e.getMessage());
                    result.setChecks(failedChecks(e.getMessage()));
                    unreachable = true;
                }
            } else {
                // 连不上时仍返回 4 行结果，明确标出无法判定
                result.setChecks(failedChecks(result.getMessage()));
                unreachable = true;
            }
        }
        return saveTestResult(datasource, result, unreachable);
    }

    /**
     * 测试连接第二步：逐字段检测 4 项必填（dataset_id/name/item_count/modals），缺任一项判接入失败。
     *
     * <p>拉取上游数据集失败（地址不可达/接口报错）时抛出 {@link BizException}，由调用方决定连接状态。
     */
    private DatasourceTestVO checkRequiredFields(CmrDatasource datasource, DatasourceTestVO result) {
        java.util.Map<String, Object> probe = fetchDatasets(datasource);
        boolean ok = Boolean.TRUE.equals(probe.get("requiredOk"));
        result.setConnected(ok);
        result.setChecks(buildChecks(probe));
        datasource.setRequiredOk(ok);
        datasource.setMissingFields(joinFields(probe.get("missingFields")));
        if (ok) {
            result.setMessage("连接成功，4 项必填齐全");
        } else {
            Object issue = probe.get("itemIssue");
            List<?> missing = probe.get("missingFields") instanceof List<?> list ? list : List.of();
            String suffix = issue != null ? String.valueOf(issue)
                    : "缺少 " + (missing.isEmpty() ? "必填字段"
                    : String.join("/", missing.stream().map(String::valueOf).toList()));
            result.setMessage("4 项必填缺失：" + suffix);
        }
        return result;
    }

    /**
     * 把 fetchDatasets 的缺失项转成逐字段检测结果（4 行，顺序固定）。
     *
     * @param probe fetchDatasets 返回结果
     * @return 逐字段检测结果
     */
    @SuppressWarnings("unchecked")
    private List<DatasourceCheckVO> buildChecks(java.util.Map<String, Object> probe) {
        List<String> missing = probe.get("missingFields") instanceof List<?> list
                ? (List<String>) list : List.of();
        java.util.Map<String, Object> first = probe.get("firstDataset") instanceof java.util.Map<?, ?> map
                ? (java.util.Map<String, Object>) map : java.util.Map.of();
        String itemIssue = (String) probe.get("itemIssue");
        Object modalTypes = first.get("modalTypes");

        boolean modalsOk = itemIssue == null && !missing.contains("modals");
        List<DatasourceCheckVO> checks = new ArrayList<>();
        checks.add(check("dataset_id", !missing.contains("dataset_id"),
                missing.contains("dataset_id") ? "上游数据集缺少 dataset_id" : str(first.get("datasetId"))));
        checks.add(check("name", !missing.contains("name"),
                missing.contains("name") ? "上游数据集缺少 name" : str(first.get("name"))));
        checks.add(check("item_count", !missing.contains("item_count"),
                missing.contains("item_count") ? "上游数据集缺少 item_count"
                        : (first.get("itemCount") == null ? null : String.valueOf(first.get("itemCount")))));
        checks.add(check("modals", modalsOk, modalsOk ? modalDetail(modalTypes) : itemIssue));
        return checks;
    }

    private DatasourceCheckVO check(String field, boolean ok, String detail) {
        return DatasourceCheckVO.builder()
                .field(field)
                .label(REQUIRED_FIELD_LABELS.get(field))
                .ok(ok)
                .detail(detail)
                .build();
    }

    /** 模态字段说明：优先展示上游 modal_types，缺省返回 null */
    private String modalDetail(Object modalTypes) {
        if (modalTypes instanceof List<?> list) {
            return list.isEmpty() ? null : list.stream().map(String::valueOf).collect(Collectors.joining(","));
        }
        return modalTypes == null ? null : String.valueOf(modalTypes);
    }

    /** 连接失败时的 4 行结果：均标为不通过，说明统一取连接失败原因 */
    private List<DatasourceCheckVO> failedChecks(String detail) {
        return REQUIRED_FIELDS.stream().map(field -> check(field, false, detail)).toList();
    }

    private DatasourceTestVO saveTestResult(CmrDatasource datasource, DatasourceTestVO result, boolean unreachable) {
        LocalDateTime testedAt = LocalDateTime.now();
        result.setTestedAt(testedAt);
        datasource.setLastTestConnected(result.getConnected());
        datasource.setLastTestStatusCode(result.getStatusCode());
        datasource.setLastTestLatencyMs(result.getLatencyMs());
        datasource.setLastTestMessage(result.getMessage());
        datasource.setLastTestTime(testedAt);
        // 状态机：测试通过 → 已连接；地址不可达/接口报错 → 连接失败；仅缺必填字段 → 状态不变
        if (Boolean.TRUE.equals(result.getConnected())) {
            datasource.setConnectStatus("connected");
        } else if (unreachable) {
            datasource.setConnectStatus("failed");
        }
        datasourceMapper.update(datasource, false);
        return result;
    }

    private DatasourceTestVO testEndpoint(CmrDatasource datasource) {
        if (datasource.getEndpoint() == null || datasource.getEndpoint().isBlank()
                || !datasource.getEndpoint().startsWith("http://") && !datasource.getEndpoint().startsWith("https://")) {
            return failure(null, null, "接口地址必须以 http:// 或 https:// 开头");
        }
        String authConfig = decryptAuth(datasource);
        if ((("token".equals(datasource.getAuthType()) || "basic".equals(datasource.getAuthType())))
                && (authConfig == null || authConfig.isBlank())) {
            return failure(null, null, "认证信息未配置");
        }

        long start = System.currentTimeMillis();
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(datasource.getEndpoint()))
                    .timeout(Duration.ofSeconds(5))
                    .GET();
            if ("token".equals(datasource.getAuthType()) && authConfig != null && !authConfig.isBlank()) {
                request.header("Authorization", "Bearer " + authConfig);
            } else if ("basic".equals(datasource.getAuthType()) && authConfig != null && !authConfig.isBlank()) {
                String credential = authConfig.contains(":") ? authConfig : authConfig + ":";
                request.header("Authorization", "Basic " + Base64.getEncoder()
                        .encodeToString(credential.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            }
            HttpResponse<Void> response = HTTP_CLIENT.send(request.build(), HttpResponse.BodyHandlers.discarding());
            long latency = System.currentTimeMillis() - start;
            int status = response.statusCode();
            boolean connected = status >= 200 && status < 400;
            return DatasourceTestVO.builder()
                    .connected(connected)
                    .statusCode(status)
                    .latencyMs(latency)
                    .message(messageOf(status))
                    .build();
        } catch (IllegalArgumentException e) {
            return failure(null, System.currentTimeMillis() - start, "接口地址格式无效");
        } catch (IOException e) {
            return failure(null, System.currentTimeMillis() - start, "数据源不可达或连接超时");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return failure(null, System.currentTimeMillis() - start, "连接测试被中断");
        }
    }

    private boolean isPullProtocol(String protocol) {
        return protocol != null && PULL_PROTOCOLS.contains(protocol);
    }

    private boolean isPushProtocol(String protocol) {
        return protocol != null && PUSH_PROTOCOLS.contains(protocol);
    }

    private String decryptAuth(CmrDatasource datasource) {
        if (datasource.getAuthConfig() == null || datasource.getAuthConfig().isBlank()
                || "none".equals(datasource.getAuthType())) {
            return null;
        }
        try {
            return AesUtils.decrypt(datasource.getAuthConfig(), properties.getCrypto().getKey());
        } catch (RuntimeException e) {
            return null;
        }
    }

    private DatasourceTestVO failure(Integer status, Long latency, String message) {
        return DatasourceTestVO.builder()
                .connected(false)
                .statusCode(status)
                .latencyMs(latency)
                .message(message)
                .build();
    }

    private String messageOf(int status) {
        if (status >= 200 && status < 300) {
            return "连接成功";
        }
        if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
            return "连接成功（已跟随重定向）";
        }
        if (status == 401 || status == 403) {
            return "连接失败（认证被拒绝）";
        }
        if (status >= 500) {
            return "连接失败（数据源服务异常）";
        }
        return "连接失败（HTTP " + status + "）";
    }

    private String encryptAuth(DatasourceCreateDTO dto) {
        if (dto.getAuthConfig() == null || dto.getAuthConfig().isBlank() || "none".equals(dto.getAuthType())) {
            return null;
        }
        return AesUtils.encrypt(dto.getAuthConfig(), properties.getCrypto().getKey());
    }
}
