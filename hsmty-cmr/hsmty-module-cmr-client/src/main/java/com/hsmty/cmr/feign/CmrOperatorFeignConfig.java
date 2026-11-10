package com.hsmty.cmr.feign;

import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.codec.ErrorDecoder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.openfeign.support.FeignHttpMessageConverters;
import org.springframework.cloud.openfeign.support.SpringDecoder;
import org.springframework.cloud.openfeign.support.SpringEncoder;
import org.springframework.context.annotation.Bean;

/**
 * 算子 Feign 专用配置。
 *
 * <p>算子返回的是自有信封 {@code {code:0, msg, data}}，不是平台的
 * {@code ApiResult{code:"0000"}}。平台全局的 {@code FeignResultDecoder} 会按 ApiResult 解析并
 * 因 code 不符直接抛异常，因此本客户端必须使用标准 Spring 解码器，由
 * {@link com.hsmty.cmr.pojo.vo.OperatorResult} 自行承载算子信封。
 *
 * <p>刻意不加 {@code @Configuration}：Feign 的 {@code configuration} 类会被注册为子上下文配置，
 * 而加了注解后会被 {@code com.hsmty} 组件扫描提升为全局默认配置，影响其它平台 Feign 客户端。
 *
 * @author cmr
 * @since 2026-11-10
 */
public class CmrOperatorFeignConfig {

    /**
     * 使用标准 Spring 解码器，保留返回类型上的泛型信息。
     *
     * @param converters Feign 使用的 HTTP 消息转换器
     * @return Feign 解码器
     */
    @Bean
    public Decoder cmrOperatorDecoder(ObjectProvider<FeignHttpMessageConverters> converters) {
        return new SpringDecoder(converters);
    }

    /**
     * 请求体编码使用标准 Spring 编码器（算子契约不需要平台信封）。
     *
     * @param converters Feign 使用的 HTTP 消息转换器
     * @return Feign 编码器
     */
    @Bean
    public Encoder cmrOperatorEncoder(ObjectProvider<FeignHttpMessageConverters> converters) {
        return new SpringEncoder(converters);
    }

    /**
     * 非 2xx 响应统一抛 {@link feign.FeignException}，由调用方翻译为业务错误。
     *
     * @return 默认错误解码器
     */
    @Bean
    public ErrorDecoder cmrOperatorErrorDecoder() {
        return new ErrorDecoder.Default();
    }
}
