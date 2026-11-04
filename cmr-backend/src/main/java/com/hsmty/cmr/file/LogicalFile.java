package com.hsmty.cmr.file;

import java.nio.file.Path;

/**
 * 解析后的逻辑文件（绝对路径 + 逻辑路径 + MIME）
 *
 * @param absolute 绝对路径（白名单内）
 * @param logical  逻辑路径（dataset/... 或 runtime/...）
 * @param mime     MIME 类型
 * @author cmr
 * @since 2026-11-03
 */
public record LogicalFile(Path absolute, String logical, String mime) {
}
