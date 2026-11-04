package com.hsmty.cmr.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 数据源认证信息 AES-GCM 加解密
 *
 * @author cmr
 * @since 2026-11-03
 */
class AesUtilsTest {

    private static final String KEY = "unit-test-key-32-bytes-abcdefgh!";

    @Test
    void 加解密往返一致() {
        String plain = "{\"password\":\"P@ss中文\"}";
        String cipher = AesUtils.encrypt(plain, KEY);
        assertThat(cipher).isNotEqualTo(plain);
        assertThat(AesUtils.decrypt(cipher, KEY)).isEqualTo(plain);
    }

    @Test
    void 随机IV导致相同明文密文不同() {
        String plain = "same-plaintext";
        assertThat(AesUtils.encrypt(plain, KEY))
                .isNotEqualTo(AesUtils.encrypt(plain, KEY));
    }

    @Test
    void 错误密钥解密失败() {
        String cipher = AesUtils.encrypt("secret", KEY);
        assertThatThrownBy(() -> AesUtils.decrypt(cipher, "wrong-key-wrong-key-wrong-key-32b!!"))
                .isInstanceOf(Exception.class);
    }
}
