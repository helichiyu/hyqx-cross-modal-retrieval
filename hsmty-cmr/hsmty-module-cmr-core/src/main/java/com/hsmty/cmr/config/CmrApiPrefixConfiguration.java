package com.hsmty.cmr.config;

import com.hsmty.core.common.web.ApiPrefixRegister;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 跨模态检索模块 API 前缀声明。
 *
 * <p>最终路径 = context-path {@code /hs-api} + 模块前缀 {@code /cmr} + Controller 资源名；
 * 前缀可在 {@code hsmty.api-prefix.modules.cmr} 覆盖。
 *
 * @author cmr
 * @since 2026-11-10
 */
@Configuration
public class CmrApiPrefixConfiguration {

    @Bean
    public ApiPrefixRegister cmrApiPrefixRegister() {
        return new ApiPrefixRegister() {

            @Override
            public String module() {
                return "cmr";
            }

            @Override
            public String prefix() {
                return "/cmr";
            }

            @Override
            public String[] basePackages() {
                // 用模块根包，新增 controller 子包（file/search/...）时无需再改这里
                return new String[]{"com.hsmty.cmr"};
            }
        };
    }
}
