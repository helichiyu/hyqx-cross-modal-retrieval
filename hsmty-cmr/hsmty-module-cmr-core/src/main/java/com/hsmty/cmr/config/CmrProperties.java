package com.hsmty.cmr.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * cmr.* 配置项绑定（见 application.yml 与 docs/前后端方案/04 第五节）
 *
 * @author cmr
 * @since 2026-11-03
 */
@Data
@Component
@ConfigurationProperties(prefix = "cmr")
public class CmrProperties {

    private final Environment environment;

    public CmrProperties(Environment environment) {
        this.environment = environment;
    }

    private final Auth auth = new Auth();
    private final Operator operator = new Operator();
    private final Volume volume = new Volume();
    private final Jwt jwt = new Jwt();
    private final Crypto crypto = new Crypto();
    private final Model model = new Model();
    private final Retention retention = new Retention();
    private final Eval eval = new Eval();
    private final Build build = new Build();
    private final Search search = new Search();

    /**
     * 启动即校验敏感配置：密钥缺失或沿用模板默认值在非 local 环境直接失败，避免带病上线。
     */
    @PostConstruct
    public void validateSecrets() {
        String key = crypto.getKey();
        boolean local = Arrays.asList(environment.getActiveProfiles()).contains("local");
        if (key == null || key.isBlank()) {
            throw new IllegalStateException(
                    "cmr.crypto.key 未配置：请通过环境变量 CMR_CRYPTO_KEY 注入数据源认证信息加密密钥");
        }
        if (!local && key.startsWith("change-me-")) {
            throw new IllegalStateException(
                    "cmr.crypto.key 仍为模板默认值，非 local 环境必须通过 CMR_CRYPTO_KEY 覆盖");
        }
    }

    /** 鉴权开关：false=临时匿名系统管理员，true=JWT 登录鉴权 */
    @Data
    public static class Auth {
        private boolean enabled = true;
    }

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
        /** JWT 签名密钥：无默认值，当前鉴权走平台体系，本字段保留给后续启用 */
        private String secret;
        private int expireHours = 12;
    }

    @Data
    public static class Crypto {
        /** 数据源认证信息 AES 密钥：无代码默认值，必须由配置注入（local profile 提供本机默认值） */
        private String key;
    }

    /** 检索默认参数与兜底索引（无激活索引时按 indexDir 直连算子） */
    @Data
    public static class Search {
        /** 无激活索引（cmr_index.status=active）时的兜底索引目录 */
        private String indexDir = "runtime/demo_index";
        /** 默认返回条数（策略与请求均未指定 topK 时） */
        private int topK = 50;
        /** 默认相似度阈值 */
        private BigDecimal threshold = new BigDecimal("0.60");
        /** 检索摘要保留天数 */
        private int summaryDays = 30;
        /** 兜底策略的四模态默认权重 */
        private Map<String, Integer> weights = new LinkedHashMap<>(
                Map.of("text", 25, "image", 25, "audio", 25, "video", 25));
    }

    /** 向量模型白名单：空列表表示暂未配置，不启用建库拦截 */
    @Data
    public static class Model {
        private List<String> whitelist = new ArrayList<>();
    }

    /** 检索日志保留策略 */
    @Data
    public static class Retention {
        private int searchLogDays = 30;
        private int searchLogBatchSize = 1000;
        private String searchLogCron = "0 30 2 * * *";
    }

    @Data
    public static class Eval {
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
