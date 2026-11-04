package com.hsmty.cmr.config;

import org.springframework.boot.autoconfigure.web.servlet.WebMvcRegistrations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Map;

/**
 * Web 配置：为 com.hsmty.cmr 下所有 @RestController 统一加 /cmr 模块前缀
 * （最终路径 = context-path /hs-api + /cmr + @RequestMapping，等价 hsmty ApiPrefixRegister）
 *
 * @author cmr
 * @since 2026-11-03
 */
@Configuration
public class WebConfig {

    @Bean
    public WebMvcRegistrations cmrPrefixRegistrations() {
        return new WebMvcRegistrations() {
            @Override
            public RequestMappingHandlerMapping getRequestMappingHandlerMapping() {
                RequestMappingHandlerMapping mapping = new RequestMappingHandlerMapping();
                mapping.setPathPrefixes(Map.of("/cmr",
                        HandlerTypePredicate.forAnnotation(
                                        org.springframework.web.bind.annotation.RestController.class)
                                .and(HandlerTypePredicate.forBasePackage("com.hsmty.cmr"))));
                return mapping;
            }
        };
    }
}
