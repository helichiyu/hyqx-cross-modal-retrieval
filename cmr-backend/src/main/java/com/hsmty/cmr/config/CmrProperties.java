package com.hsmty.cmr.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * cmr.* 配置项绑定（见 application.yml 与 docs/前后端方案/04 第五节）
 *
 * @author cmr
 * @since 2026-11-03
 */
@Data
@ConfigurationProperties(prefix = "cmr")
public class CmrProperties {

    private final Operator operator = new Operator();
    private final Volume volume = new Volume();
    private final Jwt jwt = new Jwt();
    private final Crypto crypto = new Crypto();
    private final Eval eval = new Eval();
    private final Build build = new Build();

    /** 算子服务调用配置 */
    @Data
    public static class Operator {
        /** 算子服务地址，如 http://127.0.0.1:6300 */
        private String baseUrl = "http://127.0.0.1:6300";
        /** 在线链（407/408）超时 ms */
        private int timeoutSearchMs = 15000;
        /** 批量链（401–406/409）超时 ms */
        private int timeoutBatchMs = 1800000;
    }

    /** 共享卷根目录（文件服务白名单） */
    @Data
    public static class Volume {
        private String dataset = "../dataset";
        private String runtime = "../runtime";
    }

    @Data
    public static class Jwt {
        private String secret = "change-me-in-production-0123456789abcdef";
        private int expireHours = 12;
    }

    @Data
    public static class Crypto {
        /** 数据源认证信息 AES 密钥 */
        private String key = "change-me-crypto-32bytes-0123456789!";
    }

    @Data
    public static class Eval {
        /** 406 投产校验默认评测文件（相对项目根的逻辑路径） */
        private String defaultEvalFile = "dataset/eval/default.jsonl";
        private final Lines lines = new Lines();
    }

    @Data
    public static class Lines {
        private BigDecimal recall = new BigDecimal("0.8");
        private BigDecimal precision;
        private BigDecimal ndcg;
    }

    @Data
    public static class Build {
        /** 建库并发（单机算子资源有限，默认 1） */
        private int concurrency = 1;
        /** 引擎侧失败自动重试上限 */
        private int retryMax = 3;
    }
}
