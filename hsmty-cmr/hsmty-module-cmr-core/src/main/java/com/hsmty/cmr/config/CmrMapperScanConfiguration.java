package com.hsmty.cmr.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * Mapper 扫描：只扫本模块 mapper 包，避免多模块互扫。
 *
 * <p>新增业务域时在此追加 {@code com.hsmty.cmr.<domain>.mapper}。
 *
 * @author cmr
 * @since 2026-11-10
 */
@Configuration
@MapperScan(basePackages = {
        "com.hsmty.cmr.file.mapper"
})
public class CmrMapperScanConfiguration {
}
