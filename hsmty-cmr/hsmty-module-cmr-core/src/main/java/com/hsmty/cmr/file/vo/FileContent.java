package com.hsmty.cmr.file.vo;

import java.io.InputStream;

/**
 * 文件内容流。
 *
 * <p>由 Controller 负责关闭流并写响应头。
 *
 * @author cmr
 * @since 2026-11-10
 */
public record FileContent(InputStream inputStream, String filename, String contentType, long size) {
}
