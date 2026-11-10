package com.hsmty.cmr.domain.dataset.service;

import com.hsmty.cmr.common.exception.BizException;
import com.hsmty.cmr.common.id.BizNoGenerator;
import com.hsmty.cmr.common.id.SnowflakeIdWorker;
import com.hsmty.cmr.common.util.AesUtils;
import com.hsmty.cmr.config.CmrProperties;
import com.hsmty.cmr.domain.dataset.dto.DatasourceCreateDTO;
import com.hsmty.cmr.domain.dataset.entity.CmrDatasource;
import com.hsmty.cmr.domain.dataset.mapper.DatasourceMapper;
import com.hsmty.cmr.domain.dataset.mapper.DatasetMapper;
import com.hsmty.cmr.domain.dataset.vo.DatasourceTestVO;
import com.hsmty.cmr.domain.dataset.vo.DatasourceVO;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Set;

import static com.hsmty.cmr.domain.dataset.entity.table.CmrDatasetTableDef.CMR_DATASET;
import static com.hsmty.cmr.domain.dataset.entity.table.CmrDatasourceTableDef.CMR_DATASOURCE;

/**
 * 数据源服务：登记（认证信息 AES 落库）与列表
 *
 * @author cmr
 * @since 2026-11-03
 */
@Service
@RequiredArgsConstructor
public class DatasourceService {

    private static final Set<String> PULL_PROTOCOLS = Set.of("pull", "http", "api");
    private static final Set<String> PUSH_PROTOCOLS = Set.of("push", "upload");
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final DatasourceMapper datasourceMapper;
    private final DatasetMapper datasetMapper;
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

    /** 同步：拉元数据并回写连接状态、必填校验、数据集总数 */
    public java.util.Map<String, Object> sync(Long id) {
        CmrDatasource datasource = datasourceMapper.selectOneById(id);
        BizException.assertNull(datasource, "数据源不存在");
        java.util.Map<String, Object> result;
        try {
            result = fetchDatasets(datasource);
        } catch (BizException e) {
            datasource.setConnectStatus("failed");
            datasource.setRequiredOk(false);
            datasource.setLastTestConnected(false);
            datasource.setLastTestMessage(e.getMessage());
            datasource.setLastTestTime(LocalDateTime.now());
            datasourceMapper.update(datasource, false);
            throw e;
        }
        datasource.setConnectStatus("connected");
        datasource.setRequiredOk(Boolean.TRUE.equals(result.get("requiredOk")));
        datasource.setUpstreamDatasetId((String) result.get("firstDatasetId"));
        datasource.setTotalCount((Integer) result.get("totalItems"));
        datasource.setLastSyncTime(LocalDateTime.now());
        datasource.setLastTestConnected(true);
        datasource.setLastTestMessage("同步成功，数据集 " + result.get("datasetCount") + " 个");
        datasource.setLastTestTime(LocalDateTime.now());
        datasourceMapper.update(datasource, false);
        return result;
    }

    private java.util.Map<String, Object> fetchDatasets(CmrDatasource datasource) {
        String base = trimBase(datasource.getEndpoint());
        String body = getJson(datasource, base + "/datasets");
        java.util.List<java.util.Map<String, Object>> datasets = unwrapList(body);
        BizException.assertTrue(datasets.isEmpty(), "上游未返回任何数据集");

        java.util.List<java.util.Map<String, Object>> briefs = new java.util.ArrayList<>();
        boolean requiredOk = true;
        Integer totalItems = 0;
        String firstDatasetId = null;
        for (java.util.Map<String, Object> ds : datasets) {
            String datasetId = str(ds.get("dataset_id"));
            String name = str(ds.get("name"));
            Integer itemCount = intOf(ds.get("item_count"));
            boolean ok = datasetId != null && name != null && itemCount != null;
            requiredOk = requiredOk && ok;
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
            briefs.add(brief);
        }
        // 抽样校验条目契约：item_id + modals（1-4 项）
        String itemIssue = sampleItemCheck(datasource, base, firstDatasetId);
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("datasetCount", briefs.size());
        result.put("datasets", briefs);
        result.put("requiredOk", requiredOk && itemIssue == null);
        result.put("firstDatasetId", firstDatasetId);
        result.put("totalItems", totalItems);
        result.put("itemIssue", itemIssue);
        return result;
    }

    private String sampleItemCheck(CmrDatasource datasource, String base, String datasetId) {
        if (datasetId == null) {
            return "缺少 dataset_id，无法校验条目契约";
        }
        try {
            String body = getJson(datasource, base + "/datasets/" + datasetId + "/items?page=1&size=1");
            java.util.List<java.util.Map<String, Object>> items = unwrapList(body);
            if (items.isEmpty()) {
                return null;
            }
            java.util.Map<String, Object> item = items.get(0);
            if (str(item.get("item_id")) == null) {
                return "条目缺少 item_id";
            }
            Object modals = item.get("modals");
            if (!(modals instanceof java.util.List<?> list) || list.isEmpty() || list.size() > 4) {
                return "条目 modals 必须为 1-4 项";
            }
            return null;
        } catch (BizException e) {
            return e.getMessage();
        }
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
    private java.util.List<java.util.Map<String, Object>> unwrapList(String body) {
        Object parsed = com.hsmty.cmr.common.util.JsonUtils.read(body, Object.class);
        Object listNode = parsed;
        if (parsed instanceof java.util.Map<?, ?> map) {
            listNode = map.get("data") != null ? map.get("data") : map.get("datasets");
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
            Long count = datasetMapper.selectCountByQuery(QueryWrapper.create()
                    .where(CMR_DATASET.DATASOURCE_ID.eq(source.getId())));
            source.setDatasetCount(count == null ? 0 : count);
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

    public DatasourceTestVO test(Long id) {
        CmrDatasource datasource = datasourceMapper.selectOneById(id);
        BizException.assertNull(datasource, "数据源不存在");
        DatasourceTestVO result;
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
        }
        return saveTestResult(datasource, result);
    }

    private DatasourceTestVO saveTestResult(CmrDatasource datasource, DatasourceTestVO result) {
        LocalDateTime testedAt = LocalDateTime.now();
        result.setTestedAt(testedAt);
        datasource.setLastTestConnected(result.getConnected());
        datasource.setLastTestStatusCode(result.getStatusCode());
        datasource.setLastTestLatencyMs(result.getLatencyMs());
        datasource.setLastTestMessage(result.getMessage());
        datasource.setLastTestTime(testedAt);
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
