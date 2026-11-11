package com.hsmty.cmr.domain.dataset.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

/**
 * 数据集登记接入（multipart：file 为数据集 zip 包；或 datasetPath 指向共享卷已有目录）
 *
 * @author cmr
 * @since 2026-11-03
 */
@Data
public class DatasetCreateDTO {

    @NotBlank(message = "数据集名称不能为空")
    private String name;

    private String version;

    @NotBlank(message = "业务场景不能为空")
    private String scene;

    private Long datasourceId;

    /** 数据集压缩包（与 datasetPath 二选一） */
    private MultipartFile file;

    /** 共享卷已有数据目录（dataset/... 逻辑路径，与 file 二选一） */
    private String datasetPath;

    private String remark;
}
