package com.rag.task.task.rpc.mq;

import com.rag.task.task.rpc.config.TaskSinkProperties;
import com.rag.task.task.rpc.mapper.UserLikeCountMapper;
import com.rag.task.task.rpc.service.RedisPipelines;
import com.rag.task.task.rpc.service.TaskProgressWriter;
import com.rag.task.task.rpc.service.TaskRedisKeys;
import com.rag.task.task.rpc.service.TaskRewardPublisher;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import com.rag.task.task.rpc.support.RealContainerPort;

/**
 * {@link RedisPipelines} 的<b>真 Redis</b>守门测试。
 *
 * <h2>🔴 为什么必须有这个类（2026-10-23 Kafka 端到端对拍时补上）</h2>
 * <p>在这之前，本模块的 81 条测试<b>没有任何一条</b>用真 Redis 跑过 pipeline 路径：
 * <ul>
 *   <li>{@code TaskSinkConsumeTest} 把 {@code StringRedisTemplate} 整个 mock 掉，
 *       只验内存 delta ⇒ <b>根本不碰 Redis</b>；</li>
 *   <li>{@code TaskProgressUpsertParityTest} 只验 PG，Redis 指着 {@code 127.0.0.1:1}（不可达）。</li>
 * </ul>
 * <p>于是 {@code RedisPipelines.exec} 里那个
 * <b>{@code executePipelined} 内再调 {@code operations.exec()}}</b> 的写法
 * —— 违反 Spring 框架契约、且让整条消息消费链路静默死掉 —— 一路绿灯放行。
 *
 * <h2>🔴 被它掩盖的缺陷（DRIFT-TASK-RPC-004）</h2>
 * <p><b>Go 的 {@code Pipeline()} 不是事务</b>：
 * <ul>
 *   <li>go-redis v9.20.0 {@code redis.go:1508-1513} {@code Client.Pipeline()} 的
 *       {@code exec} 是 {@code pipelineExecer(c.processPipelineHook)} ⇒ <b>不包 MULTI/EXEC</b>；</li>
 *   <li>同文件 {@code redis.go:1521-1528} 的 {@code Client.TxPipeline()} 才调
 *       {@code wrapMultiExec(ctx, cmds)}；</li>
 *   <li>{@code pipeline.go:18-20} 源码注释原文：
 *       <i>"Pay attention, that Pipeline is not a transaction"</i>；</li>
 *   <li>Go 两个 sink 用的都是 {@code c.rdb.Pipeline()}
 *       （{@code like_sink.go:167}、{@code like_sink.go:210}）。</li>
 * </ul>
 * <p>而 Spring 侧：
 * <ul>
 *   <li>{@code LettuceConnection.java:567-575} 的 {@code openPipeline()} <b>只置标志位</b>，
 *       <b>不发 MULTI</b>；</li>
 *   <li>{@code LettuceConnection.java:688-719} 的 {@code exec()} 在 pipeline 模式下
 *       直接向服务端发 {@code EXEC} ⇒ 服务端回 {@code ERR EXEC without MULTI}；</li>
 *   <li>{@code RedisOperations.java:118-128} 的 {@code executePipelined(SessionCallback)}
 *       javadoc 明文：<i>"the callback <b>cannot</b> return a non-null value"</i>、
 *       <i>"SessionCallback#execute(RedisOperations) itself <b>must return null</b>"</i>。</li>
 * </ul>
 * <p>⇒ 那个多出来的 {@code operations.exec()} 让
 * <b>{@code UserLikeProgressSink.lazyInitTaskIfNeeded} 的第一步 SETNX 就抛</b>
 * ⇒ 抛出 {@code flushOnce} ⇒ 被 {@code flushTick} catch ⇒ <b>整轮 flush 中止</b>
 * ⇒ {@code like:total:*}、{@code task_progress} 写入、任务完成、奖励事件<b>全部不发生</b>。
 * <p>换句话说：<b>RAG 的 task 消息处理链路此前实际是死的</b>。
 *
 * <h2>⚠️ 本类必须带阳性对照</h2>
 * <p>{@link #buggyExecWithoutMultiStillFails()} 把<b>修复前的写法原样复现一遍</b>，
 * 并断言它确实以 {@code ERR EXEC without MULTI} 失败。
 * 没有这一条，就无法区分「测试真的能发现这个缺陷」与「测试压根没碰到这条路径」。
 */
@Testcontainers
// ⚠️ 刻意**不加** @EnabledIfDockerAvailable（与 TaskProgressUpsertParityTest 同款理由）：
//    该注解在类级求值时会去拿 Docker client，而 Testcontainers 2.x 的 client() 会顺带
//    确保 Ryuk 资源回收容器可用；本机拉不到 ryuk 镜像时它抛的 ContainerFetchException
//    会被 DockerAvailableDetector 的 `catch (Throwable) -> false` 压成「Docker is not available」
//    ⇒ 整类静默 skip 而 reactor 报 BUILD SUCCESS —— 那正是本类要防的事故本身。
//    Ryuk 已在 task-rpc/pom.xml 的 surefire environmentVariables 里禁用。
class RedisPipelinesRealRedisGateTest {

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
/**
     * 本类的真容器**固定宿主口**（task 域段 22820–22849 内 · 全局段表 §4.10.19.3 / §4.10.39.2 分配）。
     *
     * <p>🔴 为什么不用 Testcontainers 的默认随机口：随机宿主口落在 **OS 临时段 49152–65535**
     * （= Docker published 段）⇒ ⇒ 与 {@code tools/difftest/ddl-parity.sh} 的固定口 {@code 55560–55569}
     * 同属一个池子 ⇒ ⇒ 全局段表把该段标为**禁占**。
     *
     * <p>🔴 为什么端口写死在本类里、而不是轮询分配：Testcontainers 的 surefire **每个 fork 一个独立 JVM**
     * ⇒ ⇒ 静态计数器在每个 fork 里都从头开始 ⇒ ⇒ 两个并发 fork 会拿到同一个端口 ⇒ ⇒ 撞车。
     */
    private static final int FIXED_HOST_PORT = 22825;
    static {
        RealContainerPort.withFixedHostPort(REDIS, FIXED_HOST_PORT,
                RealContainerPort.REDIS_CONTAINER_PORT);
    }

    private LettuceConnectionFactory factory;
    private StringRedisTemplate redis;

    @BeforeEach
    void setUp() {
        factory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        factory.afterPropertiesSet();
        factory.start();
        redis = new StringRedisTemplate(factory);
        redis.afterPropertiesSet();
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    // ==================================================================
    // 阳性对照：修复前的写法必须仍然失败
    // ==================================================================

    @Test
    @DisplayName("🔴 阳性对照：executePipelined 内调 operations.exec() ⇒ ERR EXEC without MULTI")
    void buggyExecWithoutMultiStillFails() {
        Exception thrown = assertThrows(Exception.class, () ->
                redis.executePipelined(new SessionCallback<Object>() {
                    @Override
                    @SuppressWarnings({"unchecked", "rawtypes"})
                    public <K, V> Object execute(RedisOperations<K, V> operations) {
                        ((RedisOperations) operations).opsForValue()
                                .setIfAbsent("gate:buggy", "1", Duration.ofSeconds(60));
                        return operations.exec();      // ← 修复前的写法
                    }
                }));

        // ⚠️ 必须沿 cause 链找：Spring 把底层错误包成 RedisPipelineException，
        //    真正的原因在 cause 里（生产实测栈：
        //    Caused by: io.lettuce.core.RedisCommandExecutionException: ERR EXEC without MULTI
        //      at LettuceConnection.closePipeline(LettuceConnection.java:596)）。
        StringBuilder chain = new StringBuilder();
        for (Throwable t = thrown; t != null; t = t.getCause()) {
            chain.append(t.getClass().getName()).append(": ").append(t.getMessage()).append(" | ");
        }
        assertTrue(chain.toString().contains("EXEC without MULTI"),
                "★ 阳性对照失效：修复前的写法没有以 'EXEC without MULTI' 失败 ⇒ "
                        + "说明这条断言链已经抓不到原缺陷了。实际异常链：" + chain);
    }

    // ==================================================================
    // RedisPipelines.exec
    // ==================================================================

    @Test
    @DisplayName("exec 返回与命令同序的布尔结果（SETNX 语义：首次 true，再来 false）")
    void execReturnsOrderedBooleans() {
        List<Boolean> first = RedisPipelines.toBooleans(RedisPipelines.exec(redis, ops -> {
            ops.opsForValue().setIfAbsent("gate:a", "1", Duration.ofSeconds(60));
            ops.opsForValue().setIfAbsent("gate:b", "1", Duration.ofSeconds(60));
            ops.opsForValue().setIfAbsent("gate:c", "1", Duration.ofSeconds(60));
        }));
        assertEquals(List.of(true, true, true), first, "★ 三个新 key 都该是 true");

        List<Boolean> second = RedisPipelines.toBooleans(RedisPipelines.exec(redis, ops -> {
            ops.opsForValue().setIfAbsent("gate:a", "1", Duration.ofSeconds(60));
            ops.opsForValue().setIfAbsent("gate:b", "1", Duration.ofSeconds(60));
            ops.opsForValue().setIfAbsent("gate:c", "1", Duration.ofSeconds(60));
        }));
        assertEquals(List.of(false, false, false), second,
                "★ SETNX 不是 SET：已存在的 key 必须返回 false（= 幂等闸门）");
    }

    @Test
    @DisplayName("exec 的结果元素是 Boolean 而不是事务模式的 [value,ok] 数组")
    void execResultElementsArePlainBooleans() {
        List<Object> raw = RedisPipelines.exec(redis, ops ->
                ops.opsForValue().setIfAbsent("gate:bool", "1", Duration.ofSeconds(60)));
        assertEquals(1, raw.size());
        assertTrue(raw.get(0) instanceof Boolean,
                "★ pipeline（非事务）模式下每条结果是单个 Boolean；"
                        + "若是 List，说明有人把这里改成了 MULTI/EXEC 事务，toBooleans 会静默返回全 false");
    }

    @Test
    @DisplayName("exec 的命令真的落地了（带 TTL），不是「静默什么都没做」）")
    void execActuallyWritesKeysWithTtl() {
        RedisPipelines.exec(redis, ops -> {
            ops.opsForValue().setIfAbsent("gate:ttl", "1", Duration.ofSeconds(90));
            ops.opsForHash().putAll("gate:h", Map.of("status", "doing"));
        });

        assertEquals("1", redis.opsForValue().get("gate:ttl"));
        Long ttl = redis.getExpire("gate:ttl");
        assertNotNull(ttl, "★ TTL 必须已设置");
        assertTrue(ttl > 0 && ttl <= 90, "★ TTL 应在 (0, 90] 秒内，实际 " + ttl);
        assertEquals("doing", redis.opsForHash().get("gate:h", "status"));
    }

    @Test
    @DisplayName("🔴 exec 遇命令错误必须抛出（Go 的 return err，不是吞掉）")
    void execPropagatesCommandError() {
        // ⚠️ 错误源必须是「对 string key 做 HGET」。
        //    踩过的坑：曾用「对 hash key 做 SET」当错误源 ——
        //    但 Redis 的 **SET 是类型无关的**（它会直接覆盖任何类型的 key），
        //    压根不会 WRONGTYPE ⇒ 那条断言当时是**因为错误的原因而通过**的。
        //    非 pipeline 对照组同样不抛，正是这个原因暴露出来的。
        redis.opsForValue().set("gate:wrongtype", "i am a string");

        Exception thrown = assertThrows(Exception.class, () ->
                RedisPipelines.exec(redis, ops -> ops.opsForHash().get("gate:wrongtype", "f")));

        StringBuilder chain = new StringBuilder();
        for (Throwable t = thrown; t != null; t = t.getCause()) {
            chain.append(t.getClass().getName()).append(": ").append(t.getMessage()).append(" | ");
        }
        assertTrue(chain.toString().contains("WRONGTYPE"),
                "★ 应当抛 WRONGTYPE，实际异常链：" + chain);
    }

    // ==================================================================
    // RedisPipelines.execQuietly
    // ==================================================================

    @Test
    @DisplayName("execQuietly 吞掉错误，但同批的其它命令仍然执行（Go 的 `_, _ = pipe.Exec`）")
    void execQuietlySwallowsErrorButOtherCommandsStillLand() {
        redis.opsForValue().set("gate:q-wrongtype", "i am a string");

        // 不抛异常即通过
        RedisPipelines.execQuietly(redis, ops -> {
            ops.opsForHash().get("gate:q-wrongtype", "f");                        // 这条会 WRONGTYPE
            ops.opsForValue().setIfAbsent("gate:q-ok", "1", Duration.ofSeconds(60)); // 这条必须仍然生效
        });

        assertEquals("1", redis.opsForValue().get("gate:q-ok"),
                "★ pipeline 已一次性刷给服务端 ⇒ 失败的那条不影响同批其它命令落地");
    }

    // ==================================================================
    // RedisPipelines.toBooleans 的防呆
    // ==================================================================

    @Test
    @DisplayName("🔴 toBooleans 遇到 List 元素必须报错（防止悄悄退化成「全 false」）")
    void toBooleansRejectsTransactionStyleResults() {
        List<Object> transactionStyle = List.of(List.of("1", true));
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> RedisPipelines.toBooleans(transactionStyle));
        assertTrue(e.getMessage().contains("事务模式"),
                "★ 报错信息应点明「MULTI/EXEC 事务模式」这一根因，实际：" + e.getMessage());
    }

    // ==================================================================
    // 端到端：真 Redis 驱动真实的 sink
    // ==================================================================

    /**
     * 用真 Redis 驱动**真实的** {@link UserLikeProgressSink}，走完
     * {@code onMessage → flushOnce} 的整条链路。
     *
     * <p>⚠️ 这条是本类的核心：它验的正是 2026-10-23 之前<b>静默死掉</b>的那条路径。
     * 若 {@code RedisPipelines} 再退回 {@code operations.exec()}，
     * 这里会在第一步 SETNX 就抛异常 ⇒ 用例失败。
     */
    @Test
    @DisplayName("🔴 真 Redis 驱动真实 sink：init 标记 + 进度 Hash + like:total 全部落地")
    void userSinkFlushOnceWritesEverything() {
        UserLikeProgressSink sink = newSink();

        sink.onMessage(new ConsumerRecord<>("t", 0, 0L, "5001", "1"));
        sink.flushOnce();

        long uid = 5001L;
        long taskId = 1902L;

        assertEquals("1", redis.opsForValue().get(TaskRedisKeys.likeTaskInit(uid, taskId)),
                "★ init 幂等标记必须写入（Go like_sink.go:188 的 SETNX）");
        Long initTtl = redis.getExpire(TaskRedisKeys.likeTaskInit(uid, taskId));
        assertNotNull(initTtl, "★ init 标记必须带 TTL（Go 是 90 天）");
        assertTrue(initTtl > 0, "★ init 标记的 TTL 必须为正，实际 " + initTtl);

        assertEquals("doing", redis.opsForHash().get(TaskRedisKeys.likeTaskProgress(uid, taskId), "status"),
                "★ 进度 Hash 必须写入 status=doing（Go like_sink.go:214-221）");
        assertEquals("0", redis.opsForHash().get(TaskRedisKeys.likeTaskProgress(uid, taskId), "progress"));
        assertEquals("5", redis.opsForHash().get(TaskRedisKeys.likeTaskProgress(uid, taskId), "target"));

        assertEquals("1", redis.opsForValue().get(TaskRedisKeys.userLikeTotal(uid)),
                "★ like:total 必须写入（Go like_sink.go:284）");
        Long totalTtl = redis.getExpire(TaskRedisKeys.userLikeTotal(uid));
        assertNotNull(totalTtl);
        assertTrue(totalTtl > 0, "★ like:total 必须带 90 天 TTL");
    }

    @Test
    @DisplayName("🔴 真 Redis：total 达标 ⇒ 走完成分支（done 标记 + status=done）")
    void userSinkCompletesWhenTargetReached() {
        UserLikeProgressSink sink = newSink();

        sink.onMessage(new ConsumerRecord<>("t", 0, 0L, "5002", "5"));   // target = 5
        sink.flushOnce();

        long uid = 5002L;
        long taskId = 1902L;

        assertEquals("1", redis.opsForValue().get(TaskRedisKeys.likeTaskDone(uid, taskId)),
                "★ 完成标记必须写入（Go like_sink.go:338 的 SETNX）");
        assertEquals("done", redis.opsForHash().get(TaskRedisKeys.likeTaskProgress(uid, taskId), "status"),
                "★ status 必须变成 done");
        assertEquals("5", redis.opsForHash().get(TaskRedisKeys.likeTaskProgress(uid, taskId), "progress"),
                "★ Go 写进 Hash 的 progress 是 target（like_sink.go 的源码注释明说）");
    }

    private UserLikeProgressSink newSink() {
        return new UserLikeProgressSink(
                redis,
                mock(TaskProgressWriter.class),       // PG 侧不在本类验收范围
                mock(UserLikeCountMapper.class),
                mock(TaskRewardPublisher.class),
                new TaskSinkProperties());            // 默认值即 Go 的 1902 / target 5 / TTL 90d
    }
}
