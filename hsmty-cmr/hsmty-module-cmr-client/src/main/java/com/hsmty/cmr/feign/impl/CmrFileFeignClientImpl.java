package com.hsmty.cmr.feign.impl;

import com.hsmty.cmr.feign.CmrFileFeignClient;
import com.hsmty.cmr.pojo.vo.FileObjectVO;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 文件契约降级默认实现：返回空集合，不抛异常、不伪造数据。
 *
 * @author cmr
 * @since 2026-11-10
 */
public class CmrFileFeignClientImpl implements CmrFileFeignClient {

    @Override
    public List<FileObjectVO> getByIdList(Collection<Long> idList) {
        return new ArrayList<>();
    }
}
