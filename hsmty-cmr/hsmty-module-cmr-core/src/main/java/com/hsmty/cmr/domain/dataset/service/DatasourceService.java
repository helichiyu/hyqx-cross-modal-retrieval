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
                "pushMode", "不支持 push；数据源只拉元数据，文件仍在来源方，检索按 ID 引用");
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
     * 接入（P4）：拉取上游数据集条目内容落到共享卷，登记本地数据集并触发 401→406 建库链。
     *
     * <p>与同步的分工：同步只发现元数据；接入才真正下载内容并建索引。
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

        // 去重：同一数据源的同一上游数据集只允许接入一次，避免重复下载与重复建库
        BizException.assertTrue(datasetService.existsUpstream(datasource.getId(), datasetId), "该上游数据集已接入");

        Path dir = fileService.datasetRoot().resolve("ds").resolve(datasource.getBizNo()).resolve(safeName(datasetId));
        int fetched = pullItems(datasource, base, datasetId, dir);
        BizException.assertTrue(fetched <= 0, "上游数据集无可用条目: " + datasetId);

        String logicalRoot = "dataset/ds/" + datasource.getBizNo() + "/" + safeName(datasetId);
        String name = dto.getName() != null && !dto.getName().isBlank()
                ? dto.getName() : String.valueOf(upstream.get("name"));
        DatasetVO dataset = datasetService.registerUpstream(datasource.getId(), datasetId, name,
                dto.getVersion(), dto.getScene(), logicalRoot);
        log.info("数据源接入: source={} datasetId={} files={} dataset={}",
                datasource.getBizNo(), datasetId, fetched, dataset.getBizNo());
        return dataset;
    }

    /** 逐条目下载模态内容到本地目录，返回落盘文件数 */
    private int pullItems(CmrDatasource datasource, String base, String datasetId, Path dir) {
        BizException.assertTrue(!dirBelongs(dir), "接入目录非法");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new BizException("接入目录创建失败: " + e.getMessage(), e);
        }
        int fetched = 0;
        for (int page = 1; page <= 50; page++) {
            String body = getJson(datasource, base + "/datasets/" + datasetId + "/items?page=" + page + "&size=100");
            List<java.util.Map<String, Object>> items = unwrapList(body, "items");
            if (items.isEmpty()) {
                break;
            }
            for (java.util.Map<String, Object> item : items) {
                fetched += saveItem(dir, item);
            }
            if (items.size() < 100) {
                break;
            }
        }
        return fetched;
    }

    private int saveItem(Path dir, java.util.Map<String, Object> item) {
        String itemId = safeName(str(item.get("item_id")));
        if (itemId.isBlank() || !(item.get("modals") instanceof List<?> modals)) {
            return 0;
        }
        int saved = 0;
        for (Object modal : modals) {
            if (!(modal instanceof java.util.Map<?, ?> m)) {
                continue;
            }
            String type = str(m.get("type"));
            if (type == null) {
                continue;
            }
            Object content = m.get("content");
            Object url = m.get("url");
            Path target = dir.resolve(itemId + "_" + type + (content != null ? ".txt" : extOf(str(url))));
            try {
                if (content != null) {
                    Files.writeString(target, String.valueOf(content), StandardCharsets.UTF_8);
                    saved++;
                } else if (url != null) {
                    download(str(url), target);
                    saved++;
                }
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                log.warn("条目模态下载失败 item={} type={}: {}", itemId, type, e.getMessage());
            }
        }
        return saved;
    }

    private void download(String url, Path target) throws IOException, InterruptedException {
        HttpResponse<Path> response = HTTP_CLIENT.send(
                HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).GET().build(),
                HttpResponse.BodyHandlers.ofFile(target));
        BizException.assertTrue(response.statusCode() < 200 || response.statusCode() >= 300,
                "条目内容下载失败 HTTP " + response.statusCode());
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
