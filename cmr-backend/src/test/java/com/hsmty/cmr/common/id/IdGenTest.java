package com.hsmty.cmr.common.id;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 业务号 / 雪花 ID 生成规则
 *
 * @author cmr
 * @since 2026-11-03
 */
class IdGenTest {

    private static final Pattern DS_NO = Pattern.compile("DS\\d{6}[23456789ABCDEFGHJKMNPQRSTUVWXYZ]{6}");

    @Test
    void 业务号格式为前缀加日期加6位去混淆随机串() {
        BizNoGenerator generator = new BizNoGenerator();
        String no = generator.next(BizNoGenerator.PREFIX_DATASET);
        assertThat(no).matches(DS_NO);
        // 6 位随机串仅使用去混淆字符集（无 0/1/I/O/L）
        String randomPart = no.substring(8);
        assertThat(randomPart).doesNotContain("0", "1", "I", "O", "L");
    }

    @Test
    void 业务号并发内不重复() {
        BizNoGenerator generator = new BizNoGenerator();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 2000; i++) {
            seen.add(generator.next(BizNoGenerator.PREFIX_TASK));
        }
        assertThat(seen).hasSize(2000);
    }

    @Test
    void 雪花ID单调递增且不重复() {
        SnowflakeIdWorker worker = new SnowflakeIdWorker(1, 1);
        long prev = 0;
        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < 10000; i++) {
            long id = worker.next();
            assertThat(id).isGreaterThan(prev);
            seen.add(id);
            prev = id;
        }
        assertThat(seen).hasSize(10000);
    }
}
