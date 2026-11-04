package com.hsmty.cmr.domain.dataset.service;

import com.hsmty.cmr.common.exception.BizException;
import com.hsmty.cmr.common.id.BizNoGenerator;
import com.hsmty.cmr.common.id.SnowflakeIdWorker;
import com.hsmty.cmr.common.util.AesUtils;
import com.hsmty.cmr.config.CmrProperties;
import com.hsmty.cmr.domain.dataset.dto.DatasourceCreateDTO;
import com.hsmty.cmr.domain.dataset.entity.CmrDatasource;
import com.hsmty.cmr.domain.dataset.mapper.DatasourceMapper;
import com.hsmty.cmr.domain.dataset.vo.DatasourceVO;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

import static com.hsmty.cmr.domain.dataset.entity.table.CmrDatasourceTableDef.CMR_DATASOURCE;

/**
 * 数据源服务：登记（认证信息 AES 落库）与列表
 *
 * @author cmr
 * @since 2026-11-03
 */
@Service
@RequiredArgsConstructor
public class DatasourceService {

    private final DatasourceMapper datasourceMapper;
    private final SnowflakeIdWorker idWorker;
    private final BizNoGenerator bizNoGenerator;
    private final CmrProperties properties;

    public List<DatasourceVO> list() {
        return datasourceMapper.selectListByQuery(
                        QueryWrapper.create().orderBy(CMR_DATASOURCE.ID.desc()))
                .stream().map(DatasourceVO::from).toList();
    }

    public DatasourceVO create(DatasourceCreateDTO dto) {
        if ("http".equals(dto.getProtocol()) || "api".equals(dto.getProtocol())) {
            BizException.assertNull(dto.getEndpoint(), "接口协议必须填写 endpoint");
            BizException.assertTrue(!dto.getEndpoint().startsWith("http://") && !dto.getEndpoint().startsWith("https://"),
                    "endpoint 必须以 http:// 或 https:// 开头");
        }
        CmrDatasource ds = new CmrDatasource();
        ds.setId(idWorker.next());
        ds.setBizNo(bizNoGenerator.next(BizNoGenerator.PREFIX_DATASOURCE));
        ds.setName(dto.getName());
        ds.setEndpoint(dto.getEndpoint());
        ds.setProtocol(dto.getProtocol());
        ds.setAuthType(dto.getAuthType());
        ds.setAuthConfig(encryptAuth(dto));
        ds.setRemark(dto.getRemark());
        ds.setDelFlag("F");
        ds.setCreateTime(LocalDateTime.now());
        datasourceMapper.insert(ds, true);
        return DatasourceVO.from(ds);
    }

    private String encryptAuth(DatasourceCreateDTO dto) {
        if (dto.getAuthConfig() == null || dto.getAuthConfig().isBlank() || "none".equals(dto.getAuthType())) {
            return null;
        }
        return AesUtils.encrypt(dto.getAuthConfig(), properties.getCrypto().getKey());
    }
}
