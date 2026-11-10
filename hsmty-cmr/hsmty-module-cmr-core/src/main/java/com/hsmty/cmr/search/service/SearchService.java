package com.hsmty.cmr.search.service;

import com.hsmty.cmr.feign.CmrOperatorFeignClient;
import com.hsmty.cmr.pojo.dto.QueryUnderstandDTO;
import com.hsmty.cmr.pojo.vo.OperatorResult;
import com.hsmty.cmr.pojo.vo.QueryUnderstandVO;
import com.hsmty.core.common.enums.CommonResponseEnum;
import com.hsmty.core.common.exception.common.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * 检索域服务：当前承接在线链的第一段（407 查询理解）。
 *
 * <p>算子是独立容器，本类只做参数校验、远程调用与错误翻译，不伪造检索结果。
 *
 * @author cmr
 * @since 2026-11-10
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

    private final CmrOperatorFeignClient operatorFeignClient;

    /**
     * 查询理解：文本或查询文件二选一。
     *
     * @param dto 查询理解入参
     * @return 澄清标记、澄清选项与查询模态
     */
    public QueryUnderstandVO understand(QueryUnderstandDTO dto) {
        CommonResponseEnum.VALID_ERROR.assertTrue(dto == null
                || (!StringUtils.hasText(dto.getQuery()) && !StringUtils.hasText(dto.getQueryFile())));
        if (!StringUtils.hasText(dto.getUniqueId())) {
            dto.setUniqueId(UUID.randomUUID().toString());
        }

        OperatorResult<QueryUnderstandVO> result = operatorFeignClient.queryUnderstand(dto);
        if (result == null || result.getCode() == null || result.getCode() != OperatorResult.SUCCESS_CODE) {
            String reason = result == null || !StringUtils.hasText(result.getMsg())
                    ? "算子未返回有效响应" : result.getMsg();
            log.warn("查询理解失败: uniqueId={}, reason={}", dto.getUniqueId(), reason);
            throw new BusinessException(CommonResponseEnum.UNKNOWN, new Object[0], "查询理解失败：" + reason);
        }
        return result.getData();
    }
}
