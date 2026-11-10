package com.hsmty.cmr.feign.fallback;

import com.hsmty.cmr.feign.CmrOperatorFeignClient;
import com.hsmty.cmr.feign.impl.CmrOperatorFeignClientImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 算子契约降级工厂：记录原因后返回失败信封，由业务层转成统一业务异常。
 *
 * <p>不返回伪造的检索结果，避免把“算子不可用”伪装成“没有结果”。
 *
 * @author cmr
 * @since 2026-11-10
 */
@Slf4j
@Component
public class CmrOperatorFeignClientFallbackFactory implements FallbackFactory<CmrOperatorFeignClient> {

    @Override
    public CmrOperatorFeignClient create(Throwable cause) {
        log.error("检索算子调用失败，进入降级: {}", cause.getMessage(), cause);
        return new CmrOperatorFeignClientImpl();
    }
}
