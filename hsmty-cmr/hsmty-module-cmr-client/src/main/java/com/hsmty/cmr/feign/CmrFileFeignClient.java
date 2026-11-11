package com.hsmty.cmr.feign;

import com.hsmty.cmr.feign.fallback.CmrFileFeignClientFallbackFactory;
import com.hsmty.cmr.pojo.vo.FileObjectVO;
import com.hsmty.core.feign.FeignConfig;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Collection;
import java.util.List;

/**
 * 文件元数据跨服务契约。
 *
 * <p>供其它服务按 ID 批量查询文件元数据；地址只来自 {@code cmr.service.url}。
 *
 * @author cmr
 * @since 2026-11-10
 */
@FeignClient(name = "hsmty-module-cmr-server", url = "${cmr.service.url}",
        contextId = "CmrFileFeignClient",
        fallbackFactory = CmrFileFeignClientFallbackFactory.class,
        configuration = FeignConfig.class)
public interface CmrFileFeignClient {

    @Operation(summary = "根据 ID 批量查询文件元数据")
    @PostMapping("/cmr/files/by-ids")
    List<FileObjectVO> getByIdList(@RequestBody Collection<Long> idList);
}
