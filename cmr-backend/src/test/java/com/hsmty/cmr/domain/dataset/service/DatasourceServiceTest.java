package com.hsmty.cmr.domain.dataset.service;

import com.hsmty.cmr.common.id.BizNoGenerator;
import com.hsmty.cmr.common.id.SnowflakeIdWorker;
import com.hsmty.cmr.common.util.AesUtils;
import com.hsmty.cmr.config.CmrProperties;
import com.hsmty.cmr.domain.dataset.dto.DatasourceCreateDTO;
import com.hsmty.cmr.domain.dataset.entity.CmrDatasource;
import com.hsmty.cmr.domain.dataset.mapper.DatasetMapper;
import com.hsmty.cmr.domain.dataset.mapper.DatasourceBlacklistMapper;
import com.hsmty.cmr.domain.dataset.mapper.DatasourceMapper;
import com.hsmty.cmr.domain.dataset.vo.DatasourceTestVO;
import com.hsmty.cmr.file.FileService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DatasourceServiceTest {

    @Test
    void 拉取型数据源携带Token发起真实连接测试() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.startsWith("/datasets")) {
                authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            }
            if ("/datasets".equals(path)) {
                respond(exchange, 200, "{\"datasets\":[{\"dataset_id\":\"ds1\",\"name\":\"示例集\",\"item_count\":1}]}");
            } else if (path.startsWith("/datasets/ds1/items")) {
                respond(exchange, 200, "{\"items\":[{\"item_id\":\"i1\",\"modals\":[{\"type\":\"text\",\"content\":\"x\"}]}]}");
            } else {
                // 连接测试第一步：直接 ping endpoint 根地址
                respond(exchange, 204, "");
            }
        });
        server.start();
        try {
            DatasourceMapper mapper = mock(DatasourceMapper.class);
            CmrProperties properties = new CmrProperties();
            properties.getCrypto().setKey("unit-test-key-32-bytes-abcdefgh!");
            DatasourceService service = service(mapper, properties);
            CmrDatasource datasource = datasource("pull", "token");
            datasource.setEndpoint("http://127.0.0.1:" + server.getAddress().getPort());
            when(mapper.selectOneById(1L)).thenReturn(datasource);

            DatasourceTestVO result = service.test(1L);

            assertThat(result.getConnected()).isTrue();
            assertThat(result.getStatusCode()).isEqualTo(204);
            assertThat(result.getLatencyMs()).isGreaterThanOrEqualTo(0);
            assertThat(result.getMessage()).contains("连接成功");
            assertThat(result.getTestedAt()).isNotNull();
            assertThat(authorization.get()).isEqualTo("Bearer test-token");
            verify(mapper).update(datasource, false);
            assertThat(datasource.getLastTestConnected()).isTrue();
            assertThat(datasource.getLastTestStatusCode()).isEqualTo(204);
            assertThat(datasource.getLastTestLatencyMs()).isGreaterThanOrEqualTo(0);
            assertThat(datasource.getLastTestMessage()).contains("连接成功");
            assertThat(datasource.getLastTestTime()).isNotNull();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void 推送型数据源不伪造主动连接结果() {
        DatasourceMapper mapper = mock(DatasourceMapper.class);
        CmrProperties properties = new CmrProperties();
        DatasourceService service = service(mapper, properties);
        CmrDatasource datasource = datasource("push", "none");
        when(mapper.selectOneById(2L)).thenReturn(datasource);

        DatasourceTestVO result = service.test(2L);

        assertThat(result.getConnected()).isNull();
        assertThat(result.getStatusCode()).isNull();
        assertThat(result.getLatencyMs()).isNull();
        assertThat(result.getMessage()).contains("推送接收型");
        assertThat(result.getTestedAt()).isNotNull();
        verify(mapper).update(datasource, false);
        assertThat(datasource.getLastTestConnected()).isNull();
        assertThat(datasource.getLastTestStatusCode()).isNull();
        assertThat(datasource.getLastTestLatencyMs()).isNull();
        assertThat(datasource.getLastTestMessage()).contains("推送接收型");
        assertThat(datasource.getLastTestTime()).isNotNull();
    }

    @Test
    void basic认证缺少冒号会被拒绝() {
        DatasourceService service = service(mock(DatasourceMapper.class), new CmrProperties());
        DatasourceCreateDTO dto = createDTO("basic", "user-no-password");

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(com.hsmty.cmr.common.exception.BizException.class)
                .hasMessageContaining("username:password");
    }

    @Test
    void basic认证通过后会加密落库() {
        DatasourceMapper mapper = mock(DatasourceMapper.class);
        CmrProperties properties = new CmrProperties();
        properties.getCrypto().setKey("unit-test-key-32-bytes-abcdefgh!");
        DatasourceService service = service(mapper, properties);

        service.create(createDTO("basic", "user:password"));

        ArgumentCaptor<CmrDatasource> captor = ArgumentCaptor.forClass(CmrDatasource.class);
        verify(mapper).insert(captor.capture(), anyBoolean());
        assertThat(captor.getValue().getAuthConfig()).isNotEqualTo("user:password");
        assertThat(AesUtils.decrypt(captor.getValue().getAuthConfig(), "unit-test-key-32-bytes-abcdefgh!"))
                .isEqualTo("user:password");
    }

    /** 组装被测服务（黑名单/数据集服务/文件服务在连接测试路径上不参与，mock 即可） */
    private DatasourceService service(DatasourceMapper mapper, CmrProperties properties) {
        DatasourceBlacklistMapper blacklistMapper = mock(DatasourceBlacklistMapper.class);
        when(blacklistMapper.selectListByQuery(org.mockito.ArgumentMatchers.any())).thenReturn(java.util.List.of());
        return new DatasourceService(mapper, mock(DatasetMapper.class), blacklistMapper,
                mock(DatasetService.class), mock(FileService.class),
                new SnowflakeIdWorker(1, 1), new BizNoGenerator(), properties);
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, data.length == 0 ? -1 : data.length);
        if (data.length > 0) {
            exchange.getResponseBody().write(data);
        }
        exchange.close();
    }

    private DatasourceCreateDTO createDTO(String authType, String authConfig) {
        DatasourceCreateDTO dto = new DatasourceCreateDTO();
        dto.setName("联调-Basic数据源");
        dto.setEndpoint("https://ds.example/prod");
        dto.setProtocol("pull");
        dto.setAuthType(authType);
        dto.setAuthConfig(authConfig);
        return dto;
    }

    private CmrDatasource datasource(String protocol, String authType) {
        CmrDatasource datasource = new CmrDatasource();
        datasource.setId("push".equals(protocol) ? 2L : 1L);
        datasource.setName("单元测试数据源");
        datasource.setProtocol(protocol);
        datasource.setAuthType(authType);
        if (!"none".equals(authType)) {
            datasource.setAuthConfig(AesUtils.encrypt("test-token", "unit-test-key-32-bytes-abcdefgh!"));
        }
        return datasource;
    }
}
