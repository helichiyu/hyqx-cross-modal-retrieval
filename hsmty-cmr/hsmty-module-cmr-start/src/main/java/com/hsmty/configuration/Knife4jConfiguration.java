package com.hsmty.configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 基础信息与安全方案。
 *
 * <p>文档入口仅在 springdoc 存在时装配，生产环境通过配置关闭。
 *
 * @author cmr
 * @since 2026-11-10
 */
@Configuration
@ConditionalOnClass(name = "org.springdoc.webmvc.ui.SwaggerWelcomeWebMvc")
public class Knife4jConfiguration {

    private static final String BEARER_AUTH = "BearerAuth";

    @Bean
    public OpenAPI cmrOpenAPI() {
        return new OpenAPI()
                .info(new Info().title("跨模态检索中心 API")
                        .version("1.0")
                        .description("Cross-Modal Retrieval RESTful APIs"))
                .components(new Components().addSecuritySchemes(BEARER_AUTH,
                        new SecurityScheme().type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }
}
