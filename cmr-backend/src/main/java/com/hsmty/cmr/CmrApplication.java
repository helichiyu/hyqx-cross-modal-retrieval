package com.hsmty.cmr;

import com.hsmty.cmr.config.CmrProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 跨模态检索中心后端启动类
 *
 * <p>fallback 技术栈：裸 Spring Boot 3 + MyBatis-Flex（hsmty 父工程不可用时），命名与分层遵循 hsmty 规范。
 *
 * @author cmr
 * @since 2026-11-03
 */
@SpringBootApplication
@EnableConfigurationProperties(CmrProperties.class)
@EnableMethodSecurity
@EnableScheduling
public class CmrApplication {

    public static void main(String[] args) {
        SpringApplication.run(CmrApplication.class, args);
    }
}
