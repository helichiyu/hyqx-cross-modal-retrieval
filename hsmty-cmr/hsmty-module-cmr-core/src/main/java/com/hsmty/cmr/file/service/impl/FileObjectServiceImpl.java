package com.hsmty.cmr.file.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.hsmty.cmr.common.id.BizNoGenerator;
import com.hsmty.cmr.file.FileStore;
import com.hsmty.cmr.file.entity.CmrFileObject;
import com.hsmty.cmr.file.mapper.CmrFileObjectMapper;
import com.hsmty.cmr.file.service.FileObjectService;
import com.hsmty.cmr.file.vo.FileContent;
import com.hsmty.cmr.pojo.dto.FileObjectListDTO;
import com.hsmty.cmr.pojo.vo.FileObjectVO;
import com.hsmty.core.common.entity.PageResult;
import com.hsmty.core.common.entity.SelectIdsDTO;
import com.hsmty.core.common.enums.CommonResponseEnum;
import com.hsmty.core.common.exception.common.BusinessException;
import com.hsmty.core.util.BeanCopyUtils;
import com.hsmty.core.util.PageUtils;
import com.hsmty.core.util.Utils;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;

/**
 * 文件对象业务实现。
 *
 * <p>上传时边写存储边算 SHA-256，避免大文件二次读取；元数据与文件本体分离。
 *
 * @author cmr
 * @since 2026-11-10
 */
@Service
@RequiredArgsConstructor
public class FileObjectServiceImpl extends ServiceImpl<CmrFileObjectMapper, CmrFileObject>
        implements FileObjectService {

    private static final String DEFAULT_STATUS = "active";
    private static final String BIZ_NO_PREFIX = "FILE";

    private final FileStore fileStore;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileObjectVO upload(MultipartFile file, String bizType) {
        CommonResponseEnum.VALID_ERROR.message("上传文件不能为空").assertTrue(file == null || file.isEmpty());
        String originalName = StringUtils.hasText(file.getOriginalFilename())
                ? file.getOriginalFilename()
                : "unnamed";
        String sha256;
        FileStore.StoredObject stored;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream inputStream = new DigestInputStream(file.getInputStream(), digest)) {
                stored = fileStore.put(inputStream, file.getSize(), file.getContentType(), originalName);
            }
            sha256 = HexFormat.of().formatHex(digest.digest());
        } catch (IOException e) {
            throw new BusinessException(CommonResponseEnum.FILE_UPLOAD_ERROR, new Object[0], "文件读取失败", e);
        } catch (NoSuchAlgorithmException e) {
            throw new BusinessException(CommonResponseEnum.FILE_UPLOAD_ERROR, new Object[0], "摘要算法不可用", e);
        }

        CmrFileObject entity = new CmrFileObject();
        entity.setBizNo(BizNoGenerator.generate(BIZ_NO_PREFIX));
        entity.setBizType(bizType);
        entity.setBucket(stored.bucket());
        entity.setObjectKey(stored.objectKey());
        entity.setOriginalName(originalName);
        entity.setContentType(file.getContentType());
        entity.setSize(file.getSize());
        entity.setSha256(sha256);
        entity.setStoreType(fileStore.storeType());
        entity.setStatus(DEFAULT_STATUS);
        save(entity);
        return BeanCopyUtils.copy(entity, FileObjectVO.class);
    }

    @Override
    public PageResult<FileObjectVO> page(FileObjectListDTO dto) {
        Page<FileObjectVO> page = pageAs(PageUtils.getPage(dto), buildQueryWrapper(dto), FileObjectVO.class);
        return PageUtils.getPageResult(page);
    }

    @Override
    public FileObjectVO detail(Long id) {
        return BeanCopyUtils.copy(getExisting(id), FileObjectVO.class);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(SelectIdsDTO dto) {
        CommonResponseEnum.INVALID_ID.assertTrue(dto == null || ObjectUtil.isEmpty(dto.getIds()));
        removeByIds(dto.getIds());
    }

    @Override
    public FileContent content(Long id) {
        CmrFileObject entity = getExisting(id);
        InputStream inputStream = fileStore.get(entity.getObjectKey());
        return new FileContent(inputStream, entity.getOriginalName(), entity.getContentType(),
                entity.getSize() == null ? 0L : entity.getSize());
    }

    @Override
    public List<FileObjectVO> getByIdList(Collection<Long> ids) {
        if (ObjectUtil.isEmpty(ids)) {
            return List.of();
        }
        return BeanCopyUtils.copyList(listByIds(ids), FileObjectVO.class);
    }

    private CmrFileObject getExisting(Long id) {
        CommonResponseEnum.INVALID_ID.assertTrue(id == null);
        CmrFileObject entity = getById(id);
        CommonResponseEnum.FILE_NOT_EXISTS.assertNull(entity);
        return entity;
    }

    private static QueryWrapper buildQueryWrapper(FileObjectListDTO dto) {
        QueryWrapper wrapper = QueryWrapper.create().from(CmrFileObject.class);
        if (Utils.isNotNull(dto.getKeyword())) {
            wrapper.like(CmrFileObject::getOriginalName, dto.getKeyword());
        }
        if (Utils.isNotNull(dto.getStatus())) {
            wrapper.eq(CmrFileObject::getStatus, dto.getStatus());
        }
        wrapper.orderBy(CmrFileObject::getCreateTime, false);
        return wrapper;
    }
}
