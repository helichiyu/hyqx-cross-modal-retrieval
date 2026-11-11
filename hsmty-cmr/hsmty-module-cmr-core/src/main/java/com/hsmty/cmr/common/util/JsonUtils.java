package com.hsmty.cmr.common.util;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * JSON 工具（静态持有 ObjectMapper，禁止散落 new）。
 *
 * <p>平台已升级 Jackson 3（{@code tools.jackson}），与旧版的工具方法名保持一致，
 * 方便从旧工程迁移的代码直接复用。
 *
 * @author cmr
 * @since 2026-11-03
 */
public final class JsonUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonUtils() {
    }

    public static String write(Object obj) {
        return MAPPER.writeValueAsString(obj);
    }

    /** 序列化失败返回 null（用于摘要落库等可失败场景） */
    public static String writeQuietly(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            return null;
        }
    }

    public static <T> T read(String json, Class<T> clazz) {
        return MAPPER.readValue(json, clazz);
    }

    public static <T> T read(String json, TypeReference<T> typeRef) {
        return MAPPER.readValue(json, typeRef);
    }

    public static JsonNode readTree(String json) {
        return MAPPER.readTree(json);
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }
}
