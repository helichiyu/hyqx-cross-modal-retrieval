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
