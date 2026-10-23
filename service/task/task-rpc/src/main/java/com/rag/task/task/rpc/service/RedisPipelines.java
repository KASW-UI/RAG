package com.rag.task.task.rpc.service;

import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.function.Consumer;

/**
 * Redis {@code MULTI/EXEC} 助手 —— 对应 Go 侧到处出现的
 * {@code pipe := rdb.Pipeline(); pipe.XXX(...); pipe.Exec(ctx)} 两行式写法。
 *
 * <h2>⚠️ 为什么不用低层 {@code RedisStringCommands}</h2>
 * <p>直觉写法是 {@code redis.executePipelined((RedisCallback<Object>) conn ->
 * conn.stringCommands().setNX(...))}，在本仓解析出的
 * <b>Spring Data Redis 4.0.7 下编译不过</b>：
 * 低层 {@code RedisStringCommands} 只有 {@code setNX(byte[], byte[])}，
 * <b>没有带 TTL 的重载</b>（带 TTL 的版本只在高层的
 * {@code ValueOperations.setIfAbsent(K, V, Duration)} 上），
 * 而 {@code hSet} 还要手工把 String map 转成 {@code Map<byte[],byte[]>}。
 * 高层 API 两边都覆盖。
 *
 * <h2>⚠️ 为什么用 {@link SessionCallback} 而不是 lambda</h2>
 * <p>{@code SessionCallback#execute} 是<b>泛型方法</b>
 * {@code <K,V> T execute(RedisOperations<K,V>)}，
 * <b>不能作为 lambda 的目标类型</b>（编译器报「lambda 表达式的函数描述符无效」）。
 * 这里用匿名类实现并做一次受控的 {@code (RedisOperations) operations} 转型 ——
 * 转型是安全的：调用方 {@code StringRedisTemplate} 的 K/V 恒为 {@code String}。
 */
public final class RedisPipelines {

    private RedisPipelines() {
        // 纯静态工具，禁止实例化
    }

    /**
     * 在<b>一个</b> pipeline 里执行一组命令，返回<b>与命令同序</b>的结果列表。
     *
     * <p>对应 Go {@code pipe.XXX(...)} + {@code _, err := pipe.Exec(ctx)}，
     * 并保留 Go 侧的关键用法：<b>exec 之后按下标读每条命令的结果</b>
     * （{@code like_sink.go:200-205} 的 {@code it.cmd.Result()}）。
     *
     * @param body 命令序列（会被包在 MULTI/EXEC 之间）
     */
    public static List<Object> exec(StringRedisTemplate redis,
                                    Consumer<RedisOperations<String, String>> body) {
        return redis.executePipelined(new SessionCallback<Object>() {
            @Override
            @SuppressWarnings({"unchecked", "rawtypes"})
            public <K, V> Object execute(RedisOperations<K, V> operations) {
                body.accept((RedisOperations) operations);
                return operations.exec();
            }
        });
    }

    /**
     * 执行一组命令并<b>丢弃</b>结果 —— 对应 Go {@code _, _ = pipe.Exec(ctx)}
     * 或 {@code _ = rdb.Expire(...).Err()}：错误被调用方显式吞掉。
     *
     * @param body 命令序列
     */
    public static void execQuietly(StringRedisTemplate redis,
                                   Consumer<RedisOperations<String, String>> body) {
        redis.execute(new SessionCallback<Object>() {
            @Override
            @SuppressWarnings({"unchecked", "rawtypes"})
            public <K, V> Object execute(RedisOperations<K, V> operations) {
                body.accept((RedisOperations) operations);
                return operations.exec();
            }
        });
    }

    /** 把 {@code List<Object>} 的结果转成按序的布尔列表（用于批量 SETNX 的闸门判定）。 */
    public static List<Boolean> toBooleans(List<Object> raw) {
        List<Boolean> out = new java.util.ArrayList<>(raw.size());
        for (Object o : raw) {
            out.add(Boolean.TRUE.equals(o));
        }
        return out;
    }
}