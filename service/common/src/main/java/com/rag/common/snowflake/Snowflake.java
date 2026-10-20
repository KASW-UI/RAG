package com.rag.common.snowflake;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 对应 Go 版 service/common/snowflake/snowflake.go 的整个包（102 行）。
 *
 * <p>Go 版用 {@code github.com/bwmarrin/snowflake v0.3.0}；Java 版按 bwmarrin/snowflake 的位布局
 * （41 位时间戳 + 10 位 workerId + 12 位 sequence）自己实现，保证 1:1 同位宽的 int64。
 *
 * <p><b>bwmarrin v0.3.0 位布局取证</b>（{@code snowflake.go} 15-33 行）：
 * <pre>
 *   Epoch    int64 = 1288834974657
 *   NodeBits uint8 = 10   → nodeMask / nodeShift = StepBits
 *   StepBits uint8 = 12   → stepMask / timeShift = NodeBits + StepBits
 * </pre>
 *
 * <p>框架职责：
 * <ul>
 *   <li>Go 版：bwmarrin/snowflake 是分布式 ID 生成器（Twitter Snowflake 风格的 Go 实现）</li>
 *   <li>Java 版：纯 JDK 实现，无第三方依赖；用 {@code synchronized(state)} 串行化
 *       （对应 bwmarrin 内部的 {@code n.mu.Lock()}）</li>
 * </ul>
 */
public final class Snowflake {

    /** 对齐 Go 版 {@code logx.Infof("Snowflake initialized with nodeId: %d", nodeId)}。 */
    /**
     * Spring 化 internal：仅供 {@link com.rag.common.spring.SnowflakeBean} 复用启动日志输出。
     * 业务代码请用 {@link #init()} / {@link #nextId()}，不要直接引用此 Logger。
     */
    public static final Logger LOG = LoggerFactory.getLogger(Snowflake.class);

    /**
     * 对应 Go 版 {@code snowflake.Epoch}（bwmarrin v0.3.0 snowflake.go:17）：
     * <pre>
     *     // Epoch is set to the twitter snowflake epoch of Nov 04 2010 01:42:54 UTC in milliseconds
     *     Epoch int64 = 1288834974657
     * </pre>
     */
    static final long EPOCH = 1288834974657L;

    /** bwmarrin v0.3.0 {@code NodeBits uint8 = 10}。 */
    static final long WORKER_ID_BITS = 10L;

    /** bwmarrin v0.3.0 {@code StepBits uint8 = 12}。 */
    static final long SEQUENCE_BITS = 12L;

    /** 对应 bwmarrin {@code nodeMax = -1 ^ (-1 << NodeBits)} = 1023。 */
    public static final long MAX_WORKER_ID = -1L ^ (-1L << WORKER_ID_BITS);

    /** 对应 bwmarrin {@code stepMask = -1 ^ (-1 << StepBits)} = 4095。 */
    static final long SEQUENCE_MASK = -1L ^ (-1L << SEQUENCE_BITS);

    /** 对应 bwmarrin {@code nodeShift = StepBits} = 12。 */
    static final long WORKER_ID_SHIFT = SEQUENCE_BITS;

    /** 对应 bwmarrin {@code timeShift = NodeBits + StepBits} = 22。 */
    static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;

    // =========================================================================
    // 进程级唯一状态（对应 Go 版的包级变量 sf / once / sfErr）
    // =========================================================================

    /**
     * <b>M11 修复</b>：全进程<b>只有一份</b>生成状态。
     *
     * <p>Go 版只有 {@code var sf *snowflake.Node} 一个 node，其内部持有 {@code time} 与 {@code step}，
     * 进程内所有 {@code GetID()} 共用它。旧 Java 版有两份互不同步的状态：
     * <ul>
     *   <li>{@code Snowflake.HOLDER.state}（静态，workerId 取 hash 兜底）</li>
     *   <li>{@code SnowflakeBean.state}（Bean 实例，workerId 取自 Spring 配置）</li>
     * </ul>
     * {@code nextId()} 靠「Bean 有没有注册」在两者间切换，于是同一个 JVM 里可能出现
     * <b>两个不同 workerId 的生成器交替产出 ID</b>，且两者各自维护 lastTimestamp/sequence ——
     * workerId 相同时可撞 ID，不同时则同一条业务流里出现不同节点的 ID。
     *
     * <p>现在收敛为单一 {@link #STATE} + 单一 {@link #workerId}，与 Go 版一一对应。
     */
    private static final State STATE = new State();

    /**
     * 已解析的 workerId；{@code null} 表示尚未初始化（对应 Go 版 {@code sf == nil}）。
     *
     * <p>用包装类型而非 {@code long}，是为了区分「workerId=0」（合法）与「未解析」——
     * 用 primitive 会让 0 同时代表两种状态。
     */
    private static volatile Long workerId;

    /** 初始化失败时的原因，对应 Go 版 {@code sfErr}。 */
    private static volatile Throwable initError;

    /**
     * 生成器状态，对应 bwmarrin {@code Node.time} / {@code Node.step} 两个字段。
     *
     * <p>Spring 化 internal：仅供本类持有。
     */
    public static final class State {
        /** bwmarrin {@code n.time}：上次生成 ID 的毫秒时间戳（相对 epoch）。 */
        long lastTimestamp;
        /** bwmarrin {@code n.step}：当前毫秒内的序号（0~4095）。 */
        long sequence;
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
     * <p><b>已知有意偏离（需 review 签字）</b>：Go 用 {@code sync.Once} 保证只跑一次；
     * Java 用 synchronized + 判空做同样的幂等，语义一致。
     */
    public static synchronized void init() {
        if (workerId != null || initError != null) {
            return; // 已初始化过，等价 sync.Once 的第二次调用直接返回
        }
        long id;
        try {
            id = generateNodeId();
        } catch (Throwable t) {
            initError = t;
            LOG.error("Snowflake initialize failed", t);
            return;
        }
        workerId = id;
        LOG.info("Snowflake initialized with nodeId: {}", id);
    }

    /**
     * 显式指定 workerId（对应 Go 版「运维用 {@code SNOWFLAKE_NODE_ID} 指定固定 node」这一档）。
     *
     * <p><b>唯一调用者是 {@link com.rag.common.spring.SnowflakeBean}</b>，用于承载
     * {@code snowflake.worker-id} 这一 Spring 配置档。必须在容器 refresh 期、
     * 任何 {@link #nextId()} 调用<b>之前</b>完成；重复调用会被忽略（first-wins），
     * 避免运行中途 workerId 漂移导致 ID 断层。
     *
     * @param id 期望的 workerId；{@code null} 或负数表示放弃显式指定
     */
    public static synchronized void setWorkerId(Long id) {
        if (id == null || id < 0) {
            return;
        }
        if (workerId != null) {
            // 已解析过：保持 first-wins，不再改写（否则已产出的 ID 与新 workerId 不连续）
            LOG.warn("Snowflake workerId already resolved ({}), ignore explicit set {}", workerId, id);
            return;
        }
        workerId = id % (MAX_WORKER_ID + 1);
        LOG.info("Snowflake initialized with nodeId: {}", workerId);
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
     * @return 生成的 64 位 ID
     * @throws IllegalStateException 当初始化失败时（对应 Go 返回 error）
     */
    public static long nextId() {
        if (workerId == null && initError == null) {
            init(); // 对应 Go 的 GetID() 第一行 Init()
        }
        if (initError != null) {
            throw new IllegalStateException("snowflake node is not initialized", initError);
        }
        Long id = workerId;
        if (id == null) {
            throw new IllegalStateException("snowflake node is not initialized");
        }
        return nextIdInternal(id, STATE);
    }

    /**
     * 当前生效的 workerId（未初始化时返回 {@code null}）。供 {@link SnowflakeBean} 与测试观测用。
     */
    public static Long currentWorkerId() {
        return workerId;
    }

    /**
     * 对应 bwmarrin v0.3.0 {@code func (n *Node) Generate() ID}（snowflake.go:135-160）。
     *
     * <pre>
     * n.mu.Lock()
     * now := time.Since(n.epoch).Nanoseconds() / 1000000
     * if now == n.time {
     *         n.step = (n.step + 1) &amp; n.stepMask
     *         if n.step == 0 {
     *                 for now &lt;= n.time { now = time.Since(n.epoch).Nanoseconds() / 1000000 }
     *         }
     * } else {
     *         n.step = 0
     * }
     * n.time = now
     * return ID((now &lt;&lt; timeShift) | (node &lt;&lt; nodeShift) | step)
     * </pre>
     *
     * <p><b>M12 修复：时钟回拨的处理必须按 v0.3.0 的真实算法，不能按「更安全」的想象写。</b>
     * v0.3.0 <b>没有</b>任何回拨保护 —— {@code now &lt; n.time} 时直接落进 {@code else} 分支：
     * {@code step} 归零、{@code time} 回退到更小的值、照常产出 ID。
     * 旧 Java 版写成「sleep 直到追上 + 抛 IllegalStateException 拒绝生成」，那是<b>凭空多出来的</b>行为：
     * <ul>
     *   <li>会阻塞调用线程（NTP 校正跳变几秒 = 整个进程卡几秒）</li>
     *   <li>Go 侧永不失败的路径，Java 侧会抛异常</li>
     * </ul>
     * 现按 v0.3.0 原文实现。
     *
     * <p><b>继承自 Go 的已知风险（不修，如实留档）</b>：时钟回拨时 {@code n.time} 回退且 {@code step}
     * 归零，若恰好与回拨前某毫秒重合，会产出重复 ID。这是 bwmarrin v0.3.0 自带的性质，
     * RAG 作为复刻<b>不擅自加保护</b> —— 加了就与 Go 不等价。
     * 若将来要加，须按 AGENTS.md §3.4 走「有意偏离」留档并单独评审。
     *
     * <p>{@code synchronized (state)} 对应 bwmarrin 的 {@code n.mu.Lock()}。
     *
     * <p>Spring 化 internal：算法体；业务请用 {@link #nextId()}。
     */
    public static long nextIdInternal(long workerIdArg, State state) {
        synchronized (state) {
            long now = System.currentTimeMillis() - EPOCH;

            if (now == state.lastTimestamp) {
                state.sequence = (state.sequence + 1) & SEQUENCE_MASK;
                if (state.sequence == 0) {
                    // 当前毫秒序号耗尽：自旋到下一毫秒（对齐 v0.3.0 的 for now <= n.time）
                    while (now <= state.lastTimestamp) {
                        now = System.currentTimeMillis() - EPOCH;
                    }
                }
            } else {
                // 含 now < lastTimestamp（时钟回拨）：v0.3.0 与 now > lastTimestamp 走同一分支
                state.sequence = 0L;
            }

            state.lastTimestamp = now;

            return (now << TIMESTAMP_SHIFT)
                    | (workerIdArg << WORKER_ID_SHIFT)
                    | state.sequence;
        }
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
     * {@code (hash[0] &lt;&lt; 8) | hash[1]}（默认大端）等价实现。
     *
     * <p><b>mod 顺序等价性</b>：Go 是「先 {@code uint16(id)} 截断，再 {@code % 1024}」，
     * Java 是「先 {@code parseLong}，再 {@code % 1024}」。因为 {@code 65536 = 1024 × 64}，
     * {@code (x mod 65536) mod 1024 == x mod 1024}，两条路径结果恒等。
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
                // parse 失败被忽略，按 Go 版语义继续走 hash 兜底
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

    private Snowflake() {
    }
}