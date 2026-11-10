package com.hsmty.cmr.config;

import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * JSON 序列化配置：Long/long 统一按字符串输出。
 * <p>雪花 ID 为 18/19 位整数，超出 JavaScript 安全整数范围（2^53-1），
 * 直接以数字下发会在浏览器端丢精度（如 245862969974591488 变成
 * 245862969974591500），导致按 ID 回查详情时命中不到记录。
 *
 * @author cmr
 * @since 2026-11-10
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer longToStringCustomizer() {
        return builder -> builder
                .serializerByType(Long.class, ToStringSerializer.instance)
                .serializerByType(Long.TYPE, ToStringSerializer.instance);
    }
}
