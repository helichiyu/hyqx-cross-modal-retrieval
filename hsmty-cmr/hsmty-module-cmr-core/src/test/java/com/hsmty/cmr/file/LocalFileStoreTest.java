package com.hsmty.cmr.file;

import com.hsmty.core.common.enums.CommonResponseEnum;
import com.hsmty.core.common.exception.common.BaseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 本地文件存储单元测试。
 *
 * @author cmr
 * @since 2026-11-10
 */
class LocalFileStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void putGetDeleteRoundTrip() throws Exception {
        LocalFileStore store = new LocalFileStore(tempDir.toString());
        byte[] data = "hello cmr".getBytes(StandardCharsets.UTF_8);

        FileStore.StoredObject stored = store.put(
                new ByteArrayInputStream(data), data.length, "text/plain", "demo.txt");

        assertThat(store.storeType()).isEqualTo("local");
        assertThat(stored.bucket()).isEqualTo("local");
        assertThat(stored.objectKey()).endsWith(".txt");
        try (InputStream inputStream = store.get(stored.objectKey())) {
            assertThat(inputStream.readAllBytes()).isEqualTo(data);
        }

        store.delete(stored.objectKey());
        assertThatThrownBy(() -> store.get(stored.objectKey()))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getResponseEnum().getCode())
                .isEqualTo(CommonResponseEnum.FILE_NOT_EXISTS.getCode());
    }

    @Test
    void rejectPathTraversal() {
        LocalFileStore store = new LocalFileStore(tempDir.toString());

        assertThatThrownBy(() -> store.get("../../etc/passwd"))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getResponseEnum().getCode())
                .isEqualTo(CommonResponseEnum.VALID_ERROR.getCode());
    }
}
