package com.hsmty;

import com.hsmty.core.common.web.ApiPrefixRegister;
import com.hsmty.cmr.file.service.FileObjectService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 启动装配测试：验证启动类注解、核心 Bean 与前缀注册可用。
 *
 * @author cmr
 * @since 2026-11-10
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RunApplicationTest {

    @Autowired
    private FileObjectService fileObjectService;

    @Autowired
    private ApiPrefixRegister cmrApiPrefixRegister;

    @Test
    void contextLoads() {
        assertThat(fileObjectService).isNotNull();
    }

    @Test
    void applicationAnnotationsPresent() {
        SpringBootApplication annotation = RunApplication.class.getAnnotation(SpringBootApplication.class);
        assertThat(annotation).isNotNull();
        assertThat(RunApplication.class.getPackageName()).isEqualTo("com.hsmty");
    }

    @Test
    void apiPrefixRegistered() {
        assertThat(cmrApiPrefixRegister.module()).isEqualTo("cmr");
        assertThat(cmrApiPrefixRegister.prefix()).isEqualTo("/cmr");
    }
}
