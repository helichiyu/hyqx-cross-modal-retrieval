package com.hsmty.cmr.feign;

import com.hsmty.cmr.feign.fallback.CmrOperatorFeignClientFallbackFactory;
import com.hsmty.cmr.pojo.dto.QueryUnderstandDTO;
import com.hsmty.cmr.pojo.vo.OperatorResult;
import com.hsmty.cmr.pojo.vo.QueryUnderstandVO;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 检索算子服务契约。
 *
 * <p>算子以独立容器部署，地址只来自 {@code cmr.operator.base-url}：
 * 本地/测试默认 {@code http://127.0.0.1:6300}，生产由环境变量覆盖。
 *
 * @author cmr
 * @since 2026-11-10
 */
@FeignClient(name = "cmr-operator", url = "${cmr.operator.base-url}",
        contextId = "CmrOperatorFeignClient",
        fallbackFactory = CmrOperatorFeignClientFallbackFactory.class,
        configuration = CmrOperatorFeignConfig.class)
public interface CmrOperatorFeignClient {

    @Operation(summary = "407m 查询理解")
    @PostMapping("/407m_query_understand/predict")
    OperatorResult<QueryUnderstandVO> queryUnderstand(@RequestBody QueryUnderstandDTO dto);
}
