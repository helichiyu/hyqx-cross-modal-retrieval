package com.hsmty.cmr.file.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 共享卷文件打包下载入参
 *
 * @author cmr
 * @since 2026-11-09
 */
@Data
public class FilePackageDTO {

    @NotEmpty(message = "打包文件路径不能为空")
    @Size(max = 200, message = "单次最多打包 200 个文件")
    private List<String> paths;
}
