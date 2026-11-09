package com.hsmty.cmr.domain.dataset.service;

import com.hsmty.cmr.common.id.BizNoGenerator;
import com.hsmty.cmr.common.id.SnowflakeIdWorker;
import com.hsmty.cmr.common.util.AesUtils;
import com.hsmty.cmr.config.CmrProperties;
import com.hsmty.cmr.domain.dataset.dto.DatasourceCreateDTO;
import com.hsmty.cmr.domain.dataset.entity.CmrDatasource;
import com.hsmty.cmr.domain.dataset.mapper.DatasourceMapper;
import com.hsmty.cmr.domain.dataset.mapper.DatasetMapper;
import com.hsmty.cmr.domain.dataset.vo.DatasourceTestVO;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.InetSocketAddress;
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
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();
        try {
            DatasourceMapper mapper = mock(DatasourceMapper.class);
            CmrProperties properties = new CmrProperties();
            properties.getCrypto().setKey("unit-test-key-32-bytes-abcdefgh!");
            DatasourceService service = new DatasourceService(
                    mapper, mock(DatasetMapper.class), mock(SnowflakeIdWorker.class), mock(BizNoGenerator.class), properties);
            CmrDatasource datasource = datasource("pull", "token");
            datasource.setEndpoint("http://127.0.0.1:" + server.getAddress().getPort());
            when(mapper.selectOneById(1L)).thenReturn(datasource);

            DatasourceTestVO result = service.test(1L);

            assertThat(result.getConnected()).isTrue();
            assertThat(result.getStatusCode()).isEqualTo(204);
            assertThat(result.getLatencyMs()).isGreaterThanOrEqualTo(0);
            assertThat(result.getMessage()).isEqualTo("连接成功");
            assertThat(result.getTestedAt()).isNotNull();
            assertThat(authorization.get()).isEqualTo("Bearer test-token");
            verify(mapper).update(datasource, false);
            assertThat(datasource.getLastTestConnected()).isTrue();
            assertThat(datasource.getLastTestStatusCode()).isEqualTo(204);
            assertThat(datasource.getLastTestLatencyMs()).isGreaterThanOrEqualTo(0);
            assertThat(datasource.getLastTestMessage()).isEqualTo("连接成功");
            assertThat(datasource.getLastTestTime()).isNotNull();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void 推送型数据源不伪造主动连接结果() {
        DatasourceMapper mapper = mock(DatasourceMapper.class);
        CmrProperties properties = new CmrProperties();
        DatasourceService service = new DatasourceService(
                mapper, mock(DatasetMapper.class), mock(SnowflakeIdWorker.class), mock(BizNoGenerator.class), properties);
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
        DatasourceService service = new DatasourceService(mock(DatasourceMapper.class), mock(DatasetMapper.class),
                new SnowflakeIdWorker(1, 1), new BizNoGenerator(), new CmrProperties());
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
        DatasourceService service = new DatasourceService(mapper, mock(DatasetMapper.class),
                new SnowflakeIdWorker(1, 1), new BizNoGenerator(), properties);

        service.create(createDTO("basic", "user:password"));

        ArgumentCaptor<CmrDatasource> captor = ArgumentCaptor.forClass(CmrDatasource.class);
        verify(mapper).insert(captor.capture(), anyBoolean());
        assertThat(captor.getValue().getAuthConfig()).isNotEqualTo("user:password");
        assertThat(AesUtils.decrypt(captor.getValue().getAuthConfig(), "unit-test-key-32-bytes-abcdefgh!"))
                .isEqualTo("user:password");
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
