package com.hsmty.cmr.common.id;

import org.springframework.stereotype.Component;

/**
 * 雪花 ID 生成器（内部主键，不对外暴露；对外一律业务编号）
 *
 * @author cmr
 * @since 2026-11-03
 */
@Component
public class SnowflakeIdWorker {

    private final long epoch = 1735660800000L; // 2025-01-01
    private final long workerIdBits = 5L;
    private final long datacenterIdBits = 5L;
    private final long sequenceBits = 12L;
    private final long maxWorkerId = -1L ^ (-1L << workerIdBits);
    private final long maxDatacenterId = -1L ^ (-1L << datacenterIdBits);
    private final long workerIdShift = sequenceBits;
    private final long datacenterIdShift = sequenceBits + workerIdBits;
    private final long timestampLeftShift = sequenceBits + workerIdBits + datacenterIdBits;
    private final long sequenceMask = -1L ^ (-1L << sequenceBits);

    private final long workerId;
    private final long datacenterId;
    private long sequence = 0L;
    private long lastTimestamp = -1L;

    public SnowflakeIdWorker() {
        this(workerIdFromEnv(), 1);
    }

    private static long workerIdFromEnv() {
        String v = System.getenv("CMR_WORKER_ID");
        return v == null ? 1 : Math.abs(v.hashCode()) % 32;
    }

    public SnowflakeIdWorker(long workerId, long datacenterId) {
        if (workerId > maxWorkerId || workerId < 0) {
            throw new IllegalArgumentException("workerId 非法");
        }
        if (datacenterId > maxDatacenterId || datacenterId < 0) {
            throw new IllegalArgumentException("datacenterId 非法");
        }
        this.workerId = workerId;
        this.datacenterId = datacenterId;
    }

    /** 下一枚雪花 ID（线程安全） */
    public synchronized long next() {
        long timestamp = System.currentTimeMillis();
        if (timestamp < lastTimestamp) {
            timestamp = lastTimestamp; // 时钟回拨时沿用旧时间戳，靠序列位撑过回拨窗口
        }
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & sequenceMask;
            if (sequence == 0) {
                while ((timestamp = System.currentTimeMillis()) == lastTimestamp) {
                    Thread.onSpinWait();
                }
            }
        } else {
            sequence = 0L;
        }
        lastTimestamp = timestamp;
        return ((timestamp - epoch) << timestampLeftShift)
                | (datacenterId << datacenterIdShift)
                | (workerId << workerIdShift)
                | sequence;
    }
}
