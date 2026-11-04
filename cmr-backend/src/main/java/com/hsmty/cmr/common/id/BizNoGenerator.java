package com.hsmty.cmr.common.id;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 业务编号生成器：{@code 前缀 + yyMMdd + 6 位 CSPRNG}（31 字符集，去 0/O/1/I/L，平台 ID 规范 32.1）
 *
 * <p>前缀登记见 docs/前后端方案/04 第四节：TASK/DS/SRC/STG/SRH/EVAL 平台已有，RST(算子)/FBK/REC 本项目自登记。
 *
 * @author cmr
 * @since 2026-11-03
 */
@Component
public class BizNoGenerator {

    public static final String PREFIX_TASK = "TASK";
    public static final String PREFIX_DATASET = "DS";
    public static final String PREFIX_DATASOURCE = "SRC";
    public static final String PREFIX_STRATEGY = "STG";
    public static final String PREFIX_SEARCH = "SRH";
    public static final String PREFIX_EVAL_SET = "EVAL";
    public static final String PREFIX_EVAL_RUN = "EVAL";
    public static final String PREFIX_FEEDBACK = "FBK";
    public static final String PREFIX_WORD = "REC";

    /** 31 字符集：0-9A-Z 去掉 0/1/I/L/O */
    private static final char[] ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter YYMMDD = DateTimeFormatter.ofPattern("yyMMdd");

    /** 生成业务编号，如 TASK261103A3F9KX */
    public String next(String prefix) {
        String date = LocalDate.now().format(YYMMDD);
        StringBuilder sb = new StringBuilder(prefix.length() + 12).append(prefix).append(date);
        for (int i = 0; i < 6; i++) {
            sb.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }
}
