package com.hsmty.cmr.feign.fallback;

import com.hsmty.cmr.feign.CmrFileFeignClient;
import com.hsmty.cmr.feign.impl.CmrFileFeignClientImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 文件契约降级工厂：记录原因后返回空集合。
 *
 * <p>降级不伪造业务数据，调用方必须自行判空。
 *
 * @author cmr
 * @since 2026-11-10
 */
@Slf4j
@Component
public class CmrFileFeignClientFallbackFactory implements FallbackFactory<CmrFileFeignClient> {

    @Override
    public CmrFileFeignClient create(Throwable cause) {
        log.error("CMR 文件契约调用失败，进入降级: {}", cause.getMessage(), cause);
        return new CmrFileFeignClientImpl();
    }
}
