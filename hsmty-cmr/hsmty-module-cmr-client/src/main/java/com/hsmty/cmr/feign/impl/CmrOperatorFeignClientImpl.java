package com.hsmty.cmr.feign.impl;

import com.hsmty.cmr.feign.CmrOperatorFeignClient;
import com.hsmty.cmr.pojo.dto.QueryUnderstandDTO;
import com.hsmty.cmr.pojo.vo.OperatorResult;
import com.hsmty.cmr.pojo.vo.QueryUnderstandVO;

/**
 * 算子契约降级实现：返回失败信封，不伪造业务数据。
 *
 * @author cmr
 * @since 2026-11-10
 */
public class CmrOperatorFeignClientImpl implements CmrOperatorFeignClient {

    @Override
    public OperatorResult<QueryUnderstandVO> queryUnderstand(QueryUnderstandDTO dto) {
        return OperatorResult.failure("检索算子服务不可用");
    }
}
