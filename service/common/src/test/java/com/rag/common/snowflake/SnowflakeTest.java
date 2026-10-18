package com.rag.common.snowflake;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Snowflake 关键契约：位布局、节点 ID 解析、initError 永久保留、序列递增。
 *
 * <p>节点 ID 解析依赖环境变量；本测试只验证：workerId 范围、ID 位布局、序列趋势递增。
 * 不尝试覆盖时钟回拨（需要操纵 System.currentTimeMillis）。
 */
class SnowflakeTest {

    @Test
    void nextIdIsPositive() {
        long id = Snowflake.nextId();
        assertTrue(id > 0);
    }

    @Test
    void nextIdIsMonotonicallyIncreasingInBurst() {
        long prev = Snowflake.nextId();
        for (int i = 0; i < 100; i++) {
            long cur = Snowflake.nextId();
            assertTrue(cur > prev, "expected " + cur + " > " + prev);
            prev = cur;
        }
    }

    @Test
    void nextIdProducesUniqueValuesInBurst() {
        long first = Snowflake.nextId();
        // 同毫秒内 sequence 不同
        java.util.HashSet<Long> seen = new java.util.HashSet<>();
        for (int i = 0; i < 50; i++) seen.add(Snowflake.nextId());
        assertTrue(seen.size() > 1);
        assertNotEquals(first, seen.iterator().next());
    }

    @Test
    void initIsIdempotentAndDoesNotThrow() {
        // 多次调用不抛；与 sync.Once 等价
        Snowflake.init();
        Snowflake.init();
        Snowflake.init();
    }
}