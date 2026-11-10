package com.hsmty.cmr.search;

import com.hsmty.cmr.pojo.dto.QueryUnderstandDTO;
import com.hsmty.cmr.pojo.vo.QueryUnderstandVO;
import com.hsmty.cmr.search.service.SearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Disabled;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 联调用例：走真实 407 算子（127.0.0.1:6300），验证 Feign 编解码与真实响应字段。
 *
 * <p>默认禁用，避免 CI 依赖算子容器；本地算子启动后移除 {@code @Disabled} 单独执行：
 * {@code mvn -pl hsmty-module-cmr-start -am test -Dtest=OperatorLiveSmokeTest}
 */
@Disabled("需要本地 6300 算子容器，按需手动执行")
@SpringBootTest(properties = "cmr.operator.base-url=http://127.0.0.1:6300")
@ActiveProfiles("test")
class OperatorLiveSmokeTest {

    @Autowired
    private SearchService searchService;

    @Test
    void realOperatorReturnsClarifyOptions() {
        QueryUnderstandDTO dto = new QueryUnderstandDTO();
        dto.setQuery("苹果的照片");

        // 模拟已登录用户请求：平台 Feign 拦截器会透传 ALLSESSION Cookie；带 Cookie 时不再走 Redis 中转
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("ALLSESSION", "live-smoke"));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        QueryUnderstandVO vo;
        try {
            vo = searchService.understand(dto);
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }

        assertThat(vo.getNeedClarify()).isTrue();
        assertThat(vo.getClarifyOptions()).isNotEmpty();
        assertThat(vo.getAmbiguousTerm()).isEqualTo("苹果");
        assertThat(vo.getNormalizedQuery()).isEqualTo("苹果的照片");
        assertThat(vo.getQueryModality()).isEqualTo("text");
    }
}
