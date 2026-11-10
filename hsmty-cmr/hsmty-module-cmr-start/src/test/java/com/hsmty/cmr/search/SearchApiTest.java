package com.hsmty.cmr.search;

import com.hsmty.cmr.feign.CmrOperatorFeignClient;
import com.hsmty.cmr.pojo.dto.QueryUnderstandDTO;
import com.hsmty.cmr.pojo.vo.OperatorResult;
import com.hsmty.cmr.pojo.vo.QueryUnderstandVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 检索接口集成测试：覆盖查询理解代理的成功、参数校验、算子失败与权限不足。
 *
 * @author cmr
 * @since 2026-11-10
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SearchApiTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CmrOperatorFeignClient operatorFeignClient;

    @Test
    void understandReturnsOperatorResult() throws Exception {
        QueryUnderstandVO vo = new QueryUnderstandVO();
        vo.setNeedClarify(false);
        vo.setQueryModality("text");
        when(operatorFeignClient.queryUnderstand(any(QueryUnderstandDTO.class))).thenReturn(success(vo));

        performAsUser(post("/cmr/search/understand")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"一只狗在沙滩上奔跑\"}"), List.of("cmr:search:create"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.needClarify").value(false))
                .andExpect(jsonPath("$.data.queryModality").value("text"));
    }

    @Test
    void understandRejectsEmptyQuery() throws Exception {
        performAsUser(post("/cmr/search/understand")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"), List.of("cmr:search:create"))
                .andExpect(jsonPath("$.code").value("C100"));
    }

    @Test
    void understandReportsOperatorFailure() throws Exception {
        when(operatorFeignClient.queryUnderstand(any(QueryUnderstandDTO.class)))
                .thenReturn(OperatorResult.failure("检索算子服务不可用"));

        performAsUser(post("/cmr/search/understand")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"apple\"}"), List.of("cmr:search:create"))
                .andExpect(jsonPath("$.code").value("C9999"));
    }

    @Test
    void understandRequiresSearchPermission() throws Exception {
        performAsUser(post("/cmr/search/understand")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"apple\"}"), List.of("cmr:file:page"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("C108"));
    }

    private static OperatorResult<QueryUnderstandVO> success(QueryUnderstandVO vo) {
        OperatorResult<QueryUnderstandVO> result = new OperatorResult<>();
        result.setCode(OperatorResult.SUCCESS_CODE);
        result.setMsg("success");
        result.setData(vo);
        return result;
    }

    private ResultActions performAsUser(AbstractMockHttpServletRequestBuilder<?> builder,
                                        List<String> authorities) throws Exception {
        var authentication = new UsernamePasswordAuthenticationToken("tester", "n/a",
                authorities.stream().map(SimpleGrantedAuthority::new).toList());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        try {
            return mockMvc.perform(builder);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
