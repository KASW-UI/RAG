package com.rag.common.snowflake;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 对应 Go 版 service/common/snowflake/snowflake.go 的整个包（102 行）。
 *
 * <p>Go 版用 {@code github.com/bwmarrin/snowflake v0.3.0}；Java 版按 bwmarrin/snowflake 的位布局（41 位时间戳 + 10 位 workerId + 12 位 sequence）自己实现，
 * 保证 1:1 同位宽的 int64。
 *
 * <p>框架职责：
 * <ul>
 *   <li>Go 版：bwmarrin/snowflake 是分布式 ID 生成器（Twitter Snowflake 风格的 Go 实现）</li>
 *   <li>Java 版：纯 JDK 实现，无第三方依赖；使用 {@link AtomicLong} 做无锁自旋（Go 版 snowflake.Node 内部也是 mutex + atomic.Uint64）</li>
 * </ul>
 */
public final class Snowflake {

    /** DRIFT-001 修复：与 Go 版 logx.Infof("Snowflake initialized with nodeId: %d", nodeId) 对齐。 */
    /**
     * Spring 化 internal：仅供 {@link com.rag.common.spring.SnowflakeBean} 复用启动日志输出。
     * 业务代码请用 {@link #init()} / {@link #nextId()}，不要直接引用此 Logger。
     */
    public static final Logger LOG = LoggerFactory.getLogger(Snowflake.class);

    /**
     * 对应 Go 版 {@code snowflake.Epoch}（bwmarrin/snowflake 的起始时间戳）：
     * <pre>
     *     // 1288834974657 = Nov 04 2010 01:42:54.657 UTC
     *     snowflake.Epoch = 1288834974657
     *     </pre>
     * Java 版直接硬编码常量，与 Go 版对齐。
     */
    static final long EPOCH = 1288834974657L;

    /**
     * workerId 位数（10 位 → 1024 个节点）。
     * 对应 Go 版 {@code nodeId = nodeId % 1024} 的注释 "Snowflake nodeId 必须在 [0, 1023] 范围内（10位）"。
     */
    static final long WORKER_ID_BITS = 10L;

    /**
     * sequence 位数（每毫秒 4096 个序号）。
     * 对应 bwmarrin/snowflake 默认值。
     */
    static final long SEQUENCE_BITS = 12L;

    /**
     * workerId 最大值（-1L ^ (-1L << WORKER_ID_BITS) = 1023）。
     *
     * <p>Spring 化 internal：仅供 {@link com.rag.common.spring.SnowflakeBean} 计算 mod 时使用。
     */
    public static final long MAX_WORKER_ID = -1L ^ (-1L << WORKER_ID_BITS);

    /**
     * sequence 最大值（-1L ^ (-1L << SEQUENCE_BITS) = 4095）。
     */
    static final long SEQUENCE_MASK = -1L ^ (-1L << SEQUENCE_BITS);

    /**
     * workerId 偏移量（sequence 占了 12 位）。
     */
    static final long WORKER_ID_SHIFT = SEQUENCE_BITS;

    /**
     * 时间戳偏移量（workerId + sequence 占 22 位）。
     */
    static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;

    /**
     * Spring 化（决策 6）：{@link com.rag.common.spring.SnowflakeBean} 构造时调用
     * {@link #bindBean(SnowflakeBean)} 注册；static {@link #nextId()} 优先委托 Bean。
     * Bean 未注册时 fallback 到 legacy HOLDER 路径。
     */
    private static volatile com.rag.common.spring.SnowflakeBean boundBean;

    /**
     * Spring 化钩子：由 {@link com.rag.common.spring.SnowflakeBean} 构造时调用，
     * 让 {@link #nextId()} 优先走 Bean 路径。
     */
    public static void bindBean(com.rag.common.spring.SnowflakeBean bean) {
        boundBean = bean;
    }

    /**
     * 对应 Go 版 {@code var (sf *snowflake.Node; once sync.Once; sfErr error)} 的三个变量合并到一个 holder。
     * 用 {@code volatile} + 内部类 lazy init（线程安全）替代 {@code sync.Once}。
     *
     * <p><b>Spring 化</b>：Bean 存在时 HOLDER 仅用于 {@code init()} 的 fail-fast 校验，
     * 实际 ID 生成走 Bean 路径。
     */
    private static final Holder HOLDER = new Holder();

    /**
     * 对应 Go 版 {@code var (sf *snowflake.Node; once sync.Once; sfErr error)} 内部状态：
     * <ul>
     *   <li>{@code lastTimestamp}：上次生成 ID 的毫秒时间戳</li>
     *   <li>{@code sequence}：当前毫秒内的序号（0~4095）</li>
     * </ul>
     *
     * <p>Spring 化 internal：仅供 {@link com.rag.common.spring.SnowflakeBean} 持有 workerId 状态。
     */
    public static final class State {
        long lastTimestamp;
        long sequence;
    }

    /**
     * 对应 Go 版的 lazy init 状态机。
     */
    private static final class Holder {
        final long workerId;
        final State state;
        final Throwable initError;

        Holder() {
            long id;
            Throwable err = null;
            try {
                id = generateNodeId();
            } catch (Throwable t) {
                id = 0;
                err = t;
            }
            this.workerId = id;
            this.state = new State();
            this.initError = err;
            // DRIFT-001 修复：成功初始化时输出 INFO 日志，对齐 Go 版 logx.Infof
            if (err == null) {
                LOG.info("Snowflake initialized with nodeId: {}", id);
            }
        }
    }

    private Snowflake() {
    }

    /**
     * 对应 Go 版 {@code func Init()}：
     *
     * <pre>
     * func Init() {
     *     once.Do(func() {
     *         nodeId, err := generateNodeId()
     *         if err != nil { sfErr = fmt.Errorf("generate node id failed: %w", err); return }
     *         nodeId = nodeId % 1024
     *         sf, sfErr = snowflake.NewNode(int64(nodeId))
     *         if sfErr == nil { logx.Infof("Snowflake initialized with nodeId: %d", nodeId) }
     *     })
     * }
     * </pre>
     *
     * <p>Go 版用 {@code sync.Once} 保证只跑一次；Java 版用类加载阶段的 static-final field（HOLDER）做 lazy init，
     * 也保证只跑一次。
     *
     * <p>Go 版日志通过 logx；Java 版用 SLF4J（logback-classic 已在 common/pom.xml 引入）。
     */
    public static synchronized void init() {
        // 触发 Holder 初始化（如果还没初始化过）；重复调用安全
        Holder h = HOLDER;
        if (h.initError != null) {
            throw new IllegalStateException("generate node id failed", h.initError);
        }
    }

    /**
     * 对应 Go 版 {@code func GetID() (int64, error)}：
     *
     * <pre>
     * func GetID() (int64, error) {
     *     Init()
     *     if sfErr != nil { return 0, sfErr }
     *     if sf == nil { return 0, fmt.Errorf("snowflake node is not initialized") }
     *     return sf.Generate().Int64(), nil
     * }
     * </pre>
     *
     * <p><b>Spring 化（决策 6）</b>：当 {@link #bindBean} 已注册 Bean 时优先委托 Bean 路径，
     * 保证 static 与 Bean 两条路径走同一 workerId（避免 ID 一致性问题）。
     * Bean 未注册时 fallback 到 legacy HOLDER 路径（独立进程 / 测试场景）。
     *
     * @return 生成的 64 位 ID；与 Go 版 bwmarrin/snowflake 同位宽
     * @throws IllegalStateException 当 {@link #init()} 失败时
     */
    public static long nextId() {
        com.rag.common.spring.SnowflakeBean bean = boundBean;
        if (bean != null) {
            return bean.nextId();
        }
        if (HOLDER.initError != null) {
            throw new IllegalStateException("snowflake node is not initialized", HOLDER.initError);
        }
        return nextIdInternal(HOLDER.workerId, HOLDER.state);
    }

    /**
     * 对应 Go 版 bwmarrin/snowflake 的核心算法：
     *
     * <pre>
     *   id = ((now - EPOCH) << TIMESTAMP_SHIFT) | (workerId << WORKER_ID_SHIFT) | sequence
     * </pre>
     *
     * <p>时钟回拨处理：与 bwmarrin/snowflake 一致，等待回拨的时间过去再生成。
     *
     * <p>Spring 化 internal：仅供 {@link com.rag.common.spring.SnowflakeBean} 复用算法。
     * 业务代码请用 {@link #nextId()}。
     */
    public static long nextIdInternal(long workerId, State state) {
        synchronized (state) {
            long timestamp = System.currentTimeMillis();

            if (timestamp < state.lastTimestamp) {
                // 时钟回拨：bwmarrin/snowflake 是 sleep 直到追上；这里同等待
                try {
                    Thread.sleep(state.lastTimestamp - timestamp);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("snowflake clock moved backwards, sleep interrupted", e);
                }
                timestamp = System.currentTimeMillis();
                if (timestamp < state.lastTimestamp) {
                    throw new IllegalStateException(
                            "snowflake clock moved backwards (refusing to generate id for "
                                    + (state.lastTimestamp - timestamp) + "ms)");
                }
            }

            if (state.lastTimestamp == timestamp) {
                state.sequence = (state.sequence + 1) & SEQUENCE_MASK;
                if (state.sequence == 0) {
                    // 当前毫秒序号耗尽，等到下一毫秒
                    timestamp = waitNextMillis(state.lastTimestamp);
                }
            } else {
                state.sequence = 0L;
            }

            state.lastTimestamp = timestamp;

            return ((timestamp - EPOCH) << TIMESTAMP_SHIFT)
                    | (workerId << WORKER_ID_SHIFT)
                    | state.sequence;
        }
    }

    private static long waitNextMillis(long lastTimestamp) {
        long timestamp = System.currentTimeMillis();
        while (timestamp <= lastTimestamp) {
            timestamp = System.currentTimeMillis();
        }
        return timestamp;
    }

    /**
     * 对应 Go 版 {@code func generateNodeId() (uint16, error)}：
     *
     * <pre>
     *   1. 优先从环境变量 SNOWFLAKE_NODE_ID 指定
     *   2. 否则 SERVICE_NAME + "-" + PID 做 sha256，取前 2 字节作为 uint16
     *   3. 最后 % 1024 限定在 workerId 范围内
     * </pre>
     *
     * <p>Go 版用 {@code binary.BigEndian.Uint16(hash[:2])} 取前 2 字节，Java 版用
     * {@link ByteBuffer#wrap(byte[], int, int)}（默认大端）等价实现。
     *
     * <p>Spring 化 internal：仅供 {@link com.rag.common.spring.SnowflakeBean} 复用
     * 兜底 hash 算法（Properties 未指定 workerId 时复用此函数）。
     */
    public static long generateNodeId() {
        String explicit = System.getenv("SNOWFLAKE_NODE_ID");
        if (explicit != null && !explicit.isEmpty()) {
            try {
                long parsed = Long.parseLong(explicit.trim());
                if (parsed >= 0) {
                    return parsed % (MAX_WORKER_ID + 1);
                }
            } catch (NumberFormatException ignore) {
                // fall through to 显式指定失败就走 hash 兜底（与 Go 版语义一致：parse 失败被忽略）
            }
        }

        String serviceName = System.getenv("SERVICE_NAME");
        if (serviceName == null || serviceName.isEmpty()) {
            serviceName = "unknown-service";
        }
        long pid = ProcessHandle.current().pid();
        String data = serviceName + "-" + pid;

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            // 对应 Go 版 binary.BigEndian.Uint16(hash[:2])：取 sha256 前 2 字节当 uint16
            int high = hash[0] & 0xFF;
            int low = hash[1] & 0xFF;
            long nodeId = ((high << 8) | low) & 0xFFFFL;
            return nodeId % (MAX_WORKER_ID + 1);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}