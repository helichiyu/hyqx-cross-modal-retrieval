package com.hsmty.cmr.domain.dataset.service;

import com.hsmty.cmr.domain.dataset.entity.CmrDatasource;
import com.hsmty.cmr.domain.dataset.mapper.DatasourceMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

/**
 * 回源白名单回归：只允许已登记数据源下的媒体路径，不能借代理读取数据集等接口。
 *
 * @author cmr
 * @since 2026-11-12
 */
@ExtendWith(MockitoExtension.class)
class DatasourceMediaUrlTest {

    @Mock
    private DatasourceMapper datasourceMapper;
    @InjectMocks
    private DatasourceService datasourceService;

    @Test
    void allowsMediaUnderRegisteredEndpoint() {
        stubDatasource("http://127.0.0.1:7001/api");

        assertThat(datasourceService.isTrustedMediaUrl("http://127.0.0.1:7001/api/media/dog.jpg")).isTrue();
        assertThat(datasourceService.isTrustedMediaUrl("http://127.0.0.1:7001/api/media/clip.mp4?x=1")).isTrue();
    }

    @Test
    void rejectsNonMediaPathOnSameHost() {
        stubDatasource("http://127.0.0.1:7001/api");

        assertThat(datasourceService.isTrustedMediaUrl("http://127.0.0.1:7001/api/datasets")).isFalse();
        assertThat(datasourceService.isTrustedMediaUrl("http://127.0.0.1:7001/api/items?page=1")).isFalse();
    }

    @Test
    void rejectsMediaOutsideEndpointPath() {
        stubDatasource("http://127.0.0.1:7001/api");

        assertThat(datasourceService.isTrustedMediaUrl("http://127.0.0.1:7001/other/dog.jpg")).isFalse();
        assertThat(datasourceService.isTrustedMediaUrl("http://127.0.0.1:7001/api/media/../../etc/passwd.jpg")).isFalse();
    }

    @Test
    void rejectsUnknownHostAndNonHttpScheme() {
        stubDatasource("http://127.0.0.1:7001/api");

        assertThat(datasourceService.isTrustedMediaUrl("http://evil.example:7001/api/media/dog.jpg")).isFalse();
        assertThat(datasourceService.isTrustedMediaUrl("file:///tmp/dog.jpg")).isFalse();
    }

    private void stubDatasource(String endpoint) {
        CmrDatasource source = new CmrDatasource();
        source.setId(1L);
        source.setEndpoint(endpoint);
        // 部分用例在路径校验阶段即被拒绝，不会走到数据源查询，故用 lenient 避免严格桩校验失败
        lenient().when(datasourceMapper.selectListByQuery(any())).thenReturn(List.of(source));
    }
}
