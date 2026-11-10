package com.hsmty;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.autoconfigure.RefreshAutoConfiguration;
import org.springframework.cloud.client.discovery.simple.SimpleDiscoveryClientAutoConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * 跨模态检索中心启动类。
 *
 * <p>位于 {@code com.hsmty} 根包，保证平台（安全、数据、Jackson、全局异常）与
 * 本业务 {@code com.hsmty.cmr.**} 的组件都能被扫描到。项目不接服务发现，
 * Feign 地址只来自 {@code cmr.service.url}。
 *
 * @author cmr
 * @since 2026-11-10
 */
@SpringBootApplication(exclude = {SimpleDiscoveryClientAutoConfiguration.class, RefreshAutoConfiguration.class})
@EnableAspectJAutoProxy
@EnableFeignClients
public class RunApplication {

    public static void main(String[] args) {
        SpringApplication.run(RunApplication.class, args);
        System.out.println("""
                ============================================================
                  Cross-Modal Retrieval Server started (context-path: /hs-api)
                ============================================================
                """);
    }
}
