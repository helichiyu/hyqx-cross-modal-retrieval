package com.hsmty.cmr.file;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/**
 * 对象键生成：yyyy/MM/dd/{uuid}{ext}。
 *
 * @author cmr
 * @since 2026-11-10
 */
public final class FileKeys {

    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private FileKeys() {
    }

    public static String build(String originalName) {
        return LocalDate.now().format(DAY_FORMAT)
                + "/"
                + UUID.randomUUID().toString().replace("-", "")
                + extension(originalName);
    }

    private static String extension(String originalName) {
        if (originalName == null) {
            return "";
        }
        int dot = originalName.lastIndexOf('.');
        if (dot < 0 || dot == originalName.length() - 1) {
            return "";
        }
        String candidate = originalName.substring(dot + 1).replaceAll("[^A-Za-z0-9]", "");
        if (candidate.isEmpty() || candidate.length() > 10) {
            return "";
        }
        return "." + candidate.toLowerCase(Locale.ROOT);
    }
}
