package com.hsmty.cmr.file;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 文件对象接口集成测试：覆盖上传、分页、详情、下载、逻辑删除与权限。
 *
 * <p>每个请求单独注入认证信息：平台安全过滤器会在请求结束时清理线程上下文，
 * 类级 {@code @WithMockUser} 无法覆盖同一用例内的多次请求。
 *
 * @author cmr
 * @since 2026-11-10
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FileObjectApiTest {

    private static final List<String> ALL_AUTHORITIES = List.of("cmr:file:upload", "cmr:file:page",
            "cmr:file:detail", "cmr:file:remove", "cmr:file:read");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void uploadPageDetailDownloadAndRemove() throws Exception {
        byte[] payload = "hello cross-modal".getBytes(StandardCharsets.UTF_8);
        MvcResult upload = performAsUser(multipart("/cmr/files")
                        .file(new MockMultipartFile("file", "demo.txt", "text/plain", payload))
                        .param("bizType", "demo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.id").isString())
                .andExpect(jsonPath("$.data.bizNo").isNotEmpty())
                .andExpect(jsonPath("$.data.bizType").value("demo"))
                .andReturn();

        String id = JsonPath.read(upload.getResponse().getContentAsString(), "$.data.id");

        performAsUser(get("/cmr/files").param("page", "1").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.current").value(1))
                .andExpect(jsonPath("$.data.limit").value(10))
                .andExpect(jsonPath("$.data.rows[0].id").value(id));

        performAsUser(get("/cmr/files/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.originalName").value("demo.txt"));

        MvcResult contentResult = performAsUser(get("/cmr/files/{id}/content", id))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(contentResult))
                .andExpect(status().isOk())
                .andExpect(content().bytes(payload));

        performAsUser(delete("/cmr/files")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[\"" + id + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"));

        performAsUser(get("/cmr/files").param("page", "1").param("limit", "10"))
                .andExpect(jsonPath("$.data.total").value(0));

        performAsUser(get("/cmr/files/{id}", id))
                .andExpect(jsonPath("$.code").value("C1003"));
    }

    @Test
    void rejectEmptyUpload() throws Exception {
        performAsUser(multipart("/cmr/files")
                        .file(new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0])))
                .andExpect(jsonPath("$.code").value("C100"));
    }

    @Test
    void rejectDetailWithoutPermission() throws Exception {
        performAsUser(get("/cmr/files/{id}", "1"), List.of("cmr:file:page"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("C108"));
    }

    private ResultActions performAsUser(AbstractMockHttpServletRequestBuilder<?> builder) throws Exception {
        return performAsUser(builder, ALL_AUTHORITIES);
    }

    /**
     * 平台安全链在测试白名单下被整体绕过，方法级鉴权直接读取线程上下文，
     * 因此这里显式注入认证信息，请求结束后清理，避免用例间相互污染。
     */
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
