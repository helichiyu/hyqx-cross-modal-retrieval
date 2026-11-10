package com.hsmty.cmr.common.id;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * 业务编号生成：前缀 + yyMMdd + 6 位 CSPRNG 大写字母数字。
 *
 * <p>对外一律使用业务编号；雪花主键只用于内部关联。
 *
 * @author cmr
 * @since 2026-11-10
 */
public final class BizNoGenerator {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyMMdd");
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private BizNoGenerator() {
    }

    public static String generate(String prefix) {
        StringBuilder builder = new StringBuilder(prefix.toUpperCase(Locale.ROOT))
                .append(LocalDate.now().format(DATE_FORMAT));
        for (int i = 0; i < 6; i++) {
            builder.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return builder.toString();
    }
}
