package com.rag.user.user.rpc.internal.logic;

import org.springframework.stereotype.Component;

/**
 * 对应 Go: sea-try-go/service/common/snowflake.GetID()
 *
 * Go 端直接调 snowflake.GetID() 拿到全局唯一的 int64 ID。
 * Java 端用经典 TwitteR Snowflake 算法,1 bit 符号 + 41 bit 时间戳 + 10 bit 机器 + 12 bit 序列,
 * 单进程单例,synchronized 保证并发安全。
 *
 * 面试要点(常被问):
 *   - 为什么是 41 bit 时间戳?2^41 ms ≈ 69 年,从 epoch 起算可撑到 2090 多年
 *   - 为什么需要 workerId + datacenterId?分布式下多机器不能重复,通常用 zk/数据库分配
 *   - 时钟回拨怎么办?本实现简单抛错,生产可用百度 UidGenerator 或美团 Leaf 的"借用前一毫秒序列"策略
 *
 * 当前 user-rpc 是单体,workerId=0,datacenterId=0,跟 Go 端默认配置一致。
 */
@Component
public class SnowflakeGenerator {

    /** epoch 起算时间:2024-01-01 00:00:00 UTC,比 Java epoch 晚,使时间戳位用得久 */
    private static final long EPOCH = 1_704_067_200_000L;

    private static final long WORKER_ID_BITS = 5L;
    private static final long DATACENTER_ID_BITS = 5L;
    private static final long SEQUENCE_BITS = 12L;

    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);          // 31
    private static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_ID_BITS);   // 31
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);            // 4095

    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;

    private final long workerId;
    private final long datacenterId;

    private long lastTimestamp = -1L;
    private long sequence = 0L;

    public SnowflakeGenerator() {
        // 默认 0/0,对应 Go 端 snowflake 在同一台机器上的本地 worker 编号
        // 分布式部署时改成 @Value("${snowflake.worker-id}") 从配置读
        this(0L, 0L);
    }

    public SnowflakeGenerator(long workerId, long datacenterId) {
        if (workerId > MAX_WORKER_ID || workerId < 0) {
            throw new IllegalArgumentException("workerId out of range [0, " + MAX_WORKER_ID + "]");
        }
        if (datacenterId > MAX_DATACENTER_ID || datacenterId < 0) {
            throw new IllegalArgumentException("datacenterId out of range [0, " + MAX_DATACENTER_ID + "]");
        }
        this.workerId = workerId;
        this.datacenterId = datacenterId;
    }

    /**
     * 生成下一个 ID,对应 Go: snowflake.GetID() (int64)
     */
    public synchronized long nextId() {
        long timestamp = currentTimeMillis();

        // 时钟回拨 —— 简单拒绝,生产可以等待或借用序列
        if (timestamp < lastTimestamp) {
            throw new RuntimeException(
                    "Clock moved backwards. Refusing to generate id for "
                            + (lastTimestamp - timestamp) + " ms");
        }

        // 同一毫秒内,序列号自增
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            // 序列号溢出,等到下一毫秒
            if (sequence == 0L) {
                timestamp = waitNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = timestamp;

        return ((timestamp - EPOCH) << TIMESTAMP_SHIFT)
                | (datacenterId << DATACENTER_ID_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
    }

    private long waitNextMillis(long lastTimestamp) {
        long timestamp = currentTimeMillis();
        while (timestamp <= lastTimestamp) {
            timestamp = currentTimeMillis();
        }
        return timestamp;
    }

    private long currentTimeMillis() {
        return System.currentTimeMillis();
    }
}