package com.hsmty.cmr.search;

import com.hsmty.cmr.feign.CmrOperatorFeignClient;
import com.hsmty.cmr.pojo.dto.OperatorSearchDTO;
import com.hsmty.cmr.pojo.vo.OperatorResult;
import com.hsmty.cmr.pojo.vo.OperatorSearchItemVO;
import com.hsmty.cmr.pojo.vo.OperatorSearchResultVO;
import com.hsmty.cmr.search.entity.CmrResultItem;
import com.hsmty.cmr.search.entity.CmrSearchHistory;
import com.hsmty.cmr.search.mapper.CmrResultItemMapper;
import com.hsmty.cmr.search.mapper.CmrSearchHistoryMapper;
import com.mybatisflex.core.query.QueryWrapper;
import feign.FeignException;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 408 检索接口集成测试：算子代理、结果落库、校验失败与算子不可用。
 *
 * @author cmr
 * @since 2026-11-11
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SearchExecuteApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CmrSearchHistoryMapper searchHistoryMapper;

    @Autowired
    private CmrResultItemMapper resultItemMapper;

    @MockitoBean
    private CmrOperatorFeignClient operatorFeignClient;

    @Test
    void searchPersistsHistoryAndResultItems() throws Exception {
        String resultSetId = "RST" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
        when(operatorFeignClient.retrieve(any(OperatorSearchDTO.class)))
                .thenReturn(success(resultSetId));

        performAsUser(post("/cmr/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"苹果的照片\",\"clarified\":true,"
                                + "\"clarifiedQuery\":\"苹果 照片\",\"scene\":\"default\"}"),
                        List.of("cmr:search:create"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.resultSetId").value(resultSetId))
                .andExpect(jsonPath("$.data.searchNo").isNotEmpty())
                .andExpect(jsonPath("$.data.count").value(2))
                .andExpect(jsonPath("$.data.query").value("苹果 照片"))
                .andExpect(jsonPath("$.data.items[0].rank").value(1))
                .andExpect(jsonPath("$.data.items[0].text").value("苹果照片片段"))
                .andExpect(jsonPath("$.data.items[1].modality").value("image"));

        CmrSearchHistory history = searchHistoryMapper.selectOneByQuery(
                QueryWrapper.create().eq(CmrSearchHistory::getResultSetId, resultSetId));
        assertThat(history).isNotNull();
        assertThat(history.getBizNo()).startsWith("SRH");
        assertThat(history.getQueryText()).isEqualTo("苹果 照片");
        assertThat(history.getClarified()).isTrue();
        assertThat(history.getItemCount()).isEqualTo(2);
        assertThat(history.getThreshold()).isEqualByComparingTo("0.60");
        // H2 的 JSON 列会把文本存成 JSON 字符串（带外层引号），这里只断言内容存在；
        // MySQL 下为正常 JSON 文本。
        assertThat(history.getStrategySnapshotJson()).contains("topK").contains("weights");

        long itemCount = resultItemMapper.selectCountByQuery(
                QueryWrapper.create().eq(CmrResultItem::getSearchId, history.getId()));
        assertThat(itemCount).isEqualTo(2);
    }

    @Test
    void searchRejectsEmptyQuery() throws Exception {
        performAsUser(post("/cmr/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"), List.of("cmr:search:create"))
                .andExpect(jsonPath("$.code").value("C100"));
    }

    @Test
    void searchRejectsInvalidWeights() throws Exception {
        performAsUser(post("/cmr/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"apple\",\"weights\":{\"text\":25,\"image\":25}}"),
                        List.of("cmr:search:create"))
                .andExpect(jsonPath("$.code").value("C100"));
    }

    @Test
    void searchReportsOperatorFailure() throws Exception {
        when(operatorFeignClient.retrieve(any(OperatorSearchDTO.class)))
                .thenReturn(OperatorResult.failure("index not found"));

        performAsUser(post("/cmr/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"apple\"}"), List.of("cmr:search:create"))
                .andExpect(jsonPath("$.code").value("C9999"));
    }

    @Test
    void searchTranslatesFeignException() throws Exception {
        FeignException cause = mock(FeignException.class);
        when(operatorFeignClient.retrieve(any(OperatorSearchDTO.class))).thenThrow(cause);

        performAsUser(post("/cmr/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"apple\"}"), List.of("cmr:search:create"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("C9999"))
                .andExpect(jsonPath("$.message").value("检索算子服务不可用"));
    }

    private static OperatorResult<OperatorSearchResultVO> success(String resultSetId) {
        OperatorSearchItemVO first = new OperatorSearchItemVO();
        first.setRank(1);
        first.setScore(new BigDecimal("0.872300"));
        first.setAlignScore(new BigDecimal("0.651200"));
        first.setModality("text");
        first.setItemId("ITM-1");
        first.setSourceFile("data/demo/apple.txt");
        first.setSource("demo");
        first.setText("苹果照片片段");
        first.setLocator(Map.of("chunk", 1));

        OperatorSearchItemVO second = new OperatorSearchItemVO();
        second.setRank(2);
        second.setScore(new BigDecimal("0.801000"));
        second.setAlignScore(new BigDecimal("0.612000"));
        second.setModality("image");
        second.setItemId("ITM-2");
        second.setSourceFile("data/demo/apple.jpg");
        second.setSource("demo");

        OperatorSearchResultVO data = new OperatorSearchResultVO();
        data.setResultSetId(resultSetId);
        data.setQuery("苹果 照片");
        data.setQueryModality("text");
        data.setCount(2);
        data.setItems(List.of(first, second));
        data.setStrategy(Map.of("weights", Map.of("text", 25, "image", 25, "audio", 25, "video", 25),
                "rerank", false, "topK", 10));
        data.setModelVersion("demo-v1");
        data.setElapsedMs(23.6);

        OperatorResult<OperatorSearchResultVO> result = new OperatorResult<>();
        result.setCode(OperatorResult.SUCCESS_CODE);
        result.setMsg("success");
        result.setData(data);
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
