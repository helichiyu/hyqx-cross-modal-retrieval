package com.hsmty.cmr.operator;

import tools.jackson.databind.JsonNode;
import com.hsmty.cmr.config.CmrProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

/**
 * 算子服务客户端：四协议 + 408 检索专用端点；统一信封解析与失败分类
 *
 * @author cmr
 * @since 2026-11-03
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OperatorClient {

    private final CmrProperties properties;

    private RestClient searchClient;
    private RestClient batchClient;

    @PostConstruct
    void init() {
        searchClient = build(properties.getOperator().getTimeoutSearchMs());
        batchClient = build(properties.getOperator().getTimeoutBatchMs());
    }

    private RestClient build(int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(readTimeoutMs);
        return RestClient.builder()
                .baseUrl(properties.getOperator().getBaseUrl())
                .requestFactory(factory)
                .build();
    }

    /** 统一健康检查（GET /healthz 聚合全部算子） */
    public boolean health() {
        try {
            JsonNode node = searchClient.get().uri("/healthz").retrieve().body(JsonNode.class);
            return node != null && node.path("code").asInt(-1) == 0;
        } catch (Exception e) {
            log.warn("算子健康检查失败: {}", e.getMessage());
            return false;
        }
    }

    /** 上传文件（multipart，zip 自动解压），返回 data */
    public JsonNode uploadFile(String operatorId, String uniqueId, Path file) {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", new FileSystemResource(file));
        return guard(operatorId, uniqueId, () -> unwrap(batchClient.post()
                .uri("/{op}/uploadFile?uniqueId={uid}", operatorId, uniqueId)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(parts)
                .retrieve()
                .body(JsonNode.class)));
    }

    /** 执行算子（批量链超时） */
    public JsonNode predict(String operatorId, Map<String, Object> payload) {
        return guard(operatorId, uniqueIdOf(payload), () -> call(batchClient, "/{op}/predict", operatorId, payload));
    }

    /** 在线链调用（407 查询理解，短超时） */
    public JsonNode understand(Map<String, Object> payload) {
        return guard(OpIds.UNDERSTAND, uniqueIdOf(payload), () -> call(searchClient, "/" + OpIds.UNDERSTAND + "/predict", payload));
    }

    /** 408 检索主接口（专用端点，短超时） */
    public JsonNode search(Map<String, Object> payload) {
        return guard(OpIds.RETRIEVAL, uniqueIdOf(payload), () -> call(searchClient, "/" + OpIds.RETRIEVAL + "/search", payload));
    }

    private <T> T guard(String operatorId, String uniqueId, java.util.function.Supplier<T> body) {
        try {
            return body.get();
        } catch (Exception e) {
            OperatorException classified = classify(e);
            throw new OperatorException(classified.getSide(),
                    "算子[" + operatorId + "] 任务[" + uniqueId + "]: " + classified.getMessage(),
                    classified);
        }
    }

    private String uniqueIdOf(Map<String, Object> payload) {
        Object value = payload == null ? null : payload.get("uniqueId");
        return value == null ? "unknown" : value.toString();
    }

    /** 清理任务（幂等） */
    public void removeFile(String operatorId, String uniqueId) {
        try {
            batchClient.post().uri("/{op}/removeFile?uniqueId={uid}", operatorId, uniqueId)
                    .retrieve().toBodilessEntity();
        } catch (Exception e) {
            log.warn("清理算子任务失败 op={} uid={}: {}", operatorId, uniqueId, e.getMessage());
        }
    }

    /**
     * 算子请求统一 UTF-8：默认 {@code APPLICATION_JSON} 不带 charset，
     * 在部分请求工厂下会把中文按 ISO-8859-1 编码成 '?'，必须显式声明。
     */
    private static final MediaType JSON_UTF8 = new MediaType(MediaType.APPLICATION_JSON, java.nio.charset.StandardCharsets.UTF_8);

    private JsonNode call(RestClient client, String uriTemplate, String operatorId, Map<String, Object> payload) {
        return unwrap(client.post()
                .uri(uriTemplate, operatorId)
                .contentType(JSON_UTF8)
                .body(payload)
                .retrieve()
                .body(JsonNode.class));
    }

    private JsonNode call(RestClient client, String uri, Map<String, Object> payload) {
        return unwrap(client.post()
                .uri(uri)
                .contentType(JSON_UTF8)
                .body(payload)
                .retrieve()
                .body(JsonNode.class));
    }

    /** 信封解析：code=0 → data；code=1 → 数据侧异常 */
    private JsonNode unwrap(JsonNode envelope) {
        if (envelope == null) {
            throw new OperatorException(OperatorException.Side.ENGINE, "算子无响应");
        }
        int code = envelope.path("code").asInt(-1);
        if (code == 0) {
            return envelope.path("data");
        }
        throw new OperatorException(OperatorException.Side.DATA,
                envelope.path("msg").asText("算子业务失败"));
    }

    /** HTTP 层异常 → 失败分类（5xx/连接/超时=引擎侧，4xx=数据侧） */
    static OperatorException classify(Exception e) {
        if (e instanceof OperatorException oe) {
            return oe;
        }
        if (e instanceof RestClientResponseException re) {
            HttpStatusCode status = re.getStatusCode();
            if (status.is5xxServerError()) {
                return new OperatorException(OperatorException.Side.ENGINE,
                        "算子服务异常 HTTP " + status.value(), re);
            }
            return new OperatorException(OperatorException.Side.DATA,
                    "算子拒绝请求 HTTP " + status.value() + ": " + re.getResponseBodyAsString(), re);
        }
        if (e instanceof ResourceAccessException) {
            return new OperatorException(OperatorException.Side.ENGINE,
                    "算子服务不可达或超时: " + e.getMessage(), e);
        }
        return new OperatorException(OperatorException.Side.ENGINE, "算子调用失败: " + e.getMessage(), e);
    }
}
