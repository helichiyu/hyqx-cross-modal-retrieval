package com.hsmty.cmr.file.service;

import com.hsmty.cmr.file.entity.CmrFileObject;
import com.hsmty.cmr.file.vo.FileContent;
import com.hsmty.cmr.pojo.dto.FileObjectListDTO;
import com.hsmty.cmr.pojo.vo.FileObjectVO;
import com.hsmty.core.common.entity.PageResult;
import com.hsmty.core.common.entity.SelectIdsDTO;
import com.mybatisflex.core.service.IService;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collection;
import java.util.List;

/**
 * 文件对象业务接口。
 *
 * @author cmr
 * @since 2026-11-10
 */
public interface FileObjectService extends IService<CmrFileObject> {

    /**
     * 上传文件并登记元数据。
     *
     * @param file    上传文件
     * @param bizType 业务类型
     * @return 文件元数据
     */
    FileObjectVO upload(MultipartFile file, String bizType);

    /**
     * 分页查询文件元数据。
     */
    PageResult<FileObjectVO> page(FileObjectListDTO dto);

    /**
     * 文件详情。
     */
    FileObjectVO detail(Long id);

    /**
     * 批量逻辑删除。
     */
    void remove(SelectIdsDTO dto);

    /**
     * 读取文件内容流。
     */
    FileContent content(Long id);

    /**
     * 按 ID 批量查询文件元数据。
     */
    List<FileObjectVO> getByIdList(Collection<Long> ids);
}
