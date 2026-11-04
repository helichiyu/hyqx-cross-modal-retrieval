package com.hsmty.cmr.file;

import com.hsmty.cmr.common.exception.BizException;
import com.hsmty.cmr.config.CmrProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 逻辑路径白名单与防穿越（P1 文件预览 / P3 压缩包入库共用的安全边界）
 *
 * @author cmr
 * @since 2026-11-03
 */
class FileServiceResolveTest {

    @TempDir
    Path tempDir;

    private FileService fileService;
    private Path datasetRoot;
    private Path runtimeRoot;

    @BeforeEach
    void setUp() throws IOException {
        CmrProperties properties = new CmrProperties();
        datasetRoot = Files.createDirectories(tempDir.resolve("dataset"));
        runtimeRoot = Files.createDirectories(tempDir.resolve("runtime"));
        properties.getVolume().setDataset(datasetRoot.toString());
        properties.getVolume().setRuntime(runtimeRoot.toString());
        fileService = new FileService(properties);
        Files.writeString(datasetRoot.resolve("a.txt"), "hello");
        Files.createDirectories(datasetRoot.resolve("ds"));
    }

    @Test
    void 合法dataset路径解析到卷内绝对路径() {
        LogicalFile file = fileService.resolve("dataset/a.txt");
        assertThat(file.absolute()).isEqualTo(datasetRoot.resolve("a.txt"));
        assertThat(file.logical()).isEqualTo("dataset/a.txt");
        assertThat(file.mime()).contains("text/plain");
    }

    @Test
    void 绝对路径被拒绝() {
        assertThatThrownBy(() -> fileService.resolve("/etc/passwd"))
                .isInstanceOf(BizException.class);
    }

    @Test
    void 父目录穿越被拒绝() {
        assertThatThrownBy(() -> fileService.resolve("dataset/../secret.txt"))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> fileService.resolve("dataset/ds/../../runtime/x.txt"))
                .isInstanceOf(BizException.class);
    }

    @Test
    void 盘符冒号被拒绝() {
        assertThatThrownBy(() -> fileService.resolve("dataset/c:/x.txt"))
                .isInstanceOf(BizException.class);
    }

    @Test
    void 白名单外的根前缀被拒绝() {
        assertThatThrownBy(() -> fileService.resolve("etc/passwd"))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> fileService.resolve("dataset"))
                .isInstanceOf(BizException.class);
    }

    @Test
    void 不存在的文件被拒绝() {
        assertThatThrownBy(() -> fileService.resolve("dataset/no-such.bin"))
                .isInstanceOf(BizException.class);
    }

    @Test
    void 目录不能按文件解析() {
        assertThatThrownBy(() -> fileService.resolve("dataset/ds"))
                .isInstanceOf(BizException.class);
    }
}
