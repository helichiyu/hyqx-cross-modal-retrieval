package com.hsmty.cmr.common.util;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-GCM 加解密：数据源认证信息等敏感配置项落库加密
 *
 * @author cmr
 * @since 2026-11-03
 */
public final class AesUtils {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;

    private AesUtils() {
    }

    /** 加密：iv(12B) + 密文 base64 */
    public static String encrypt(String plaintext, String key) {
        try {
            byte[] iv = new byte[IV_LEN];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, deriveKey(key), new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[IV_LEN + ct.length];
            System.arraycopy(iv, 0, out, 0, IV_LEN);
            System.arraycopy(ct, 0, out, IV_LEN, ct.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("加密失败", e);
        }
    }

    public static String decrypt(String base64, String key) {
        try {
            byte[] all = Base64.getDecoder().decode(base64);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(key), new GCMParameterSpec(TAG_BITS, all, 0, IV_LEN));
            byte[] pt = cipher.doFinal(all, IV_LEN, all.length - IV_LEN);
            return new String(pt, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("解密失败", e);
        }
    }

    private static SecretKeySpec deriveKey(String key) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(digest, "AES");
    }
}
