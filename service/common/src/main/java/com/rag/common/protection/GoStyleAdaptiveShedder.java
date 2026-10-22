package com.rag.common.protection;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.function.IntSupplier;

/**
 * Go {@code core/load/adaptiveshedder.go} 的 Java 复刻 —— CPU 自适应降载的<b>判据</b>部分。
 *
 * <p><b>⚠️ 先说清一件事：Go 侧这不是「CPU 超阈值就 503」</b>。
 * 很多「简化移植」会写成 {@code if cpu > 90 then reject}，那是<b>另一个行为</b>：
 * Go 的 {@code shouldDrop()}（{@code adaptiveshedder.go:216-234}）需要
 * <b>同时</b>满足三个条件才丢请求：
 * <ol>
 *   <li>{@code systemOverloaded() || stillHot()}：CPU ≥ 阈值，<b>或</b>刚降过载仍在冷却期；</li>
 *   <li>{@code highThru()}：<b>在途请求数</b>超过一个用 QPS × RT 算出来的动态上限。</li>
 * </ol>
 * 单看 CPU 会在「CPU 高但在途很少」时误丢请求（此时服务其实扛得住），
 * 也会在「CPU 尚可但在途堆积」时该丢不丢。本类按 Go 原条件实现。
 *
 * <p><b>常量（{@code adaptiveshedder.go:18-30}，逐个照抄）</b>：
 * <table border="1">
 *   <caption>降载常量</caption>
 *   <tr><th>Go 常量</th><th>值</th><th>Java 对应</th></tr>
 *   <tr><td>{@code defaultBuckets}</td><td>50</td><td>{@link #BUCKETS}</td></tr>
 *   <tr><td>{@code defaultWindow}</td><td>5s</td><td>⇒ 桶宽 100ms</td></tr>
 *   <tr><td>{@code defaultCpuThreshold}</td><td>900（千分比）</td><td>来自配置</td></tr>
 *   <tr><td>{@code defaultMinRt}</td><td>1000.0（ms）</td><td>{@link #DEFAULT_MIN_RT_MS}</td></tr>
 *   <tr><td>{@code flyingBeta}</td><td>0.9</td><td>{@link #FLYING_BETA}</td></tr>
 *   <tr><td>{@code coolOffDuration}</td><td>1s</td><td>{@link #COOL_OFF_MS}</td></tr>
 *   <tr><td>{@code cpuMax}</td><td>1000</td><td>{@link #CPU_MAX}</td></tr>
 *   <tr><td>{@code overloadFactorLowerBound}</td><td>0.1</td><td>{@link #OVERLOAD_FACTOR_LOWER_BOUND}</td></tr>
 * </table>
 *
 * <p><b>⚠️ 与 Go 的一处刻意的表达层差异：CPU 采样源</b>：
 * Go 的 {@code systemOverloadChecker}（{@code adaptiveshedder.go:41-43}）读
 * {@code stat.CpuUsage()}，Linux 下来自 {@code /proc/stat}，是<b>整机千分比</b>。
 * Java 侧用 {@link OperatingSystemMXBean}：
 * <ul>
 *   <li>{@code getSystemCpuLoad()}（默认）—— 同样是整机口径，<b>与 Go 对齐</b>；</li>
 *   <li>{@code getProcessCpuLoad()} —— 仅本 JVM，偏离 Go，需显式配置。</li>
 * </ul>
 * 二者在某些容器里都可能返回 <b>{@code -1}（不可用）</b>。
 * 本类的处理是 <b>fail-open：-1 视作「未过载」，不丢任何请求</b>，
 * 与 Go 侧 {@code systemOverloaded()} 的首个分支（{@code !systemOverloadChecker → false}）
 * 语义一致 —— <b>宁可少降载，不可在读不到指标时误杀全部流量</b>。
 */
final class GoStyleAdaptiveShedder {

    private static final int BUCKETS = 50;
    private static final long BUCKET_DURATION_MS = 100L;      // defaultWindow 5s / 50
    private static final long WINDOW_MS = 5_000L;
    /** {@code windowScale = 1s / bucketDuration / 1000}（adaptiveshedder.go:115） ⇒ 0.01 */
    private static final double WINDOW_SCALE =
            1_000D / BUCKET_DURATION_MS / 1_000D;
    private static final double DEFAULT_MIN_RT_MS = 1_000D;   // time.Second / time.Millisecond
    private static final double FLYING_BETA = 0.9D;
    private static final long COOL_OFF_MS = 1_000L;
    private static final double CPU_MAX = 1_000D;              // millicpu
    private static final double OVERLOAD_FACTOR_LOWER_BOUND = 0.1D;

    private final long cpuThresholdPermille;
    private final GoStyleProtectionProperties.CpuSource cpuSource;

    private final Object lock = new Object();

    private long flying;
    private double avgFlying;
    private boolean droppedRecently;
    private long overloadTimeMs;

    private final OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
    /** 注入式 CPU 读数（千分比，-1 表示不可用）；测试可覆盖为确定性值。 */
    private volatile IntSupplier cpuPermilleReader;

    GoStyleAdaptiveShedder(long cpuThresholdPermille, GoStyleProtectionProperties.CpuSource cpuSource) {
        this.cpuThresholdPermille = cpuThresholdPermille;
        this.cpuSource = cpuSource;
    }

    void setCpuPermilleReader(IntSupplier reader) {
        this.cpuPermilleReader = reader;
    }

    /**
     * {@code Allow()} 的判据（{@code adaptiveshedder.go:126-136}）：
     * {@code shouldDrop()} 为真 ⇒ 拒绝；否则在途 +1 并返回一个可回调的 promise。
     *
     * @return {@code true} = 允许（调用方随后必须调 {@link #pass} 或 {@link #fail}）
     */
    boolean allow() {
        if (shouldDrop()) {
            synchronized (lock) {
                droppedRecently = true;
            }
            return false;
        }
        addFlying(1);
        return true;
    }

    /**
     * {@code promise.Pass()}（{@code adaptiveshedder.go:249-254}）：
     * 在途 -1，并把本次 RT 与 QPS 记进窗口。
     *
     * <p>⚠️ <b>只有 Pass 才记 RT 与 QPS</b>，Fail 只减在途。照搬这一点很关键：
     * 若让 Fail 也记 QPS，降载期的 QPS 会被「被丢的请求」抬高，
     * 进而抬高 {@code maxPass} → 抬高飞行上限 → <b>越降载越不降</b>，正好抵消降载效果。
     */
    void pass(long rtMs) {
        addFlying(-1);
        synchronized (lock) {
            rollTo(System.currentTimeMillis());
            rtSlots[currentSlot][0] += Math.max(1L, (long) Math.ceil(rtMs));
            rtSlots[currentSlot][1] += 1L;
            passSlots[currentSlot] += 1L;
        }
    }

    /** {@code promise.Fail()}（{@code adaptiveshedder.go:247-249}）：只减在途。 */
    void fail() {
        addFlying(-1);
    }

    /**
     * {@code addFlying()}（{@code adaptiveshedder.go:139-152}）：
     * {@code avgFlying = avgFlying*beta + flying*(1-beta)}，<b>只在 delta<0 时更新</b>。
     *
     * <p>Go 注释解释了为什么要有这个滞后：<i>when the flying requests increase rapidly,
     * avgFlying increase slower, accept more requests; when the flying requests drop rapidly,
     * avgFlying drop slower, accept fewer requests</i>。
     */
    private void addFlying(long delta) {
        synchronized (lock) {
            this.flying += delta;
            if (delta < 0) {
                this.avgFlying = this.avgFlying * FLYING_BETA + this.flying * (1 - FLYING_BETA);
            }
        }
    }

    /** {@code shouldDrop()}（{@code adaptiveshedder.go:216-234}）。 */
    boolean shouldDrop() {
        if (!(systemOverloaded() || stillHot())) {
            return false;
        }
        return highThru();
    }

    /** {@code systemOverloaded()}（{@code adaptiveshedder.go:245-256}）。 */
    boolean systemOverloaded() {
        int cpu = readCpuPermille();
        if (cpu < 0) {
            // fail-open：读不到 ⇒ 视作未过载（Go 侧等价于 systemOverloadChecker 返回 false）
            return false;
        }
        if (cpu < cpuThresholdPermille) {
            return false;
        }
        synchronized (lock) {
            this.overloadTimeMs = System.currentTimeMillis();
        }
        return true;
    }

    /** {@code stillHot()}（{@code adaptiveshedder.go:237-244}）：冷却期内仍算过载。 */
    boolean stillHot() {
        synchronized (lock) {
            if (!this.droppedRecently) {
                return false;
            }
            if (this.overloadTimeMs == 0L) {
                return false;
            }
            if (System.currentTimeMillis() - this.overloadTimeMs < COOL_OFF_MS) {
                return true;
            }
            this.droppedRecently = false;
            return false;
        }
    }

    /**
     * {@code highThru()}（{@code adaptiveshedder.go:154-160}）：
     * <b>avgFlying 与 flying 都</b>超过 {@code maxFlight * overloadFactor} 才算「真的过载」。
     */
    boolean highThru() {
        double cap = maxFlight() * overloadFactor();
        synchronized (lock) {
            return this.avgFlying > cap && this.flying > cap;
        }
    }

    /** {@code maxFlight()}（{@code adaptiveshedder.go:162-170}）：Little 定律 QPS×RT，下界 1。 */
    double maxFlight() {
        return Math.max(maxPass() * minRt() * WINDOW_SCALE, 1D);
    }

    /** {@code maxPass()}（{@code adaptiveshedder.go:172-180}）：窗口内<b>单桶最大</b> QPS。 */
    long maxPass() {
        synchronized (lock) {
            rollTo(System.currentTimeMillis());
            long result = 1L;
            for (int i : completedSlots()) {
                if (passSlots[i] > result) {
                    result = passSlots[i];
                }
            }
            return result;
        }
    }

    /**
     * {@code minRt()}（{@code adaptiveshedder.go:182-196}）：
     * 窗口内<b>单桶最小平均 RT</b>，无数据时取 {@code defaultMinRt = 1000ms}。
     *
     * <p>Go 注释明说这个缺省值是 <i>a reasonable large value to avoid dropping requests</i>
     * ⇒ 起步阶段 {@code maxFlight} 很大 ⇒ <b>不会误丢</b>。这个「冷启动不误杀」的性质必须保住。
     */
    double minRt() {
        synchronized (lock) {
            rollTo(System.currentTimeMillis());
            double result = DEFAULT_MIN_RT_MS;
            for (int i : completedSlots()) {
                if (rtSlots[i][1] <= 0) {
                    continue;
                }
                double avg = Math.round((double) rtSlots[i][0] / (double) rtSlots[i][1]);
                if (avg < result) {
                    result = avg;
                }
            }
            return result;
        }
    }

    /**
     * {@code overloadFactor()}（{@code adaptiveshedder.go:202-206}）：
     * {@code clamp((1000 - cpu)/(1000 - threshold), 0.1, 1)}。
     *
     * <p>CPU 恰好等于阈值时 factor=1（不削减上限）；越往上越低，但<b>保底 0.1</b> ——
     * Go 注释：<i>at least accept 10% of acceptable requests, even cpu is highly overloaded</i>。
     * ⇒ <b>即使 CPU 打满，也永远留 10% 的通过率</b>，这是防雪崩的底线。
     */
    double overloadFactor() {
        int cpu = readCpuPermille();
        if (cpu < 0) {
            return 1D;
        }
        double span = CPU_MAX - cpuThresholdPermille;
        if (span <= 0D) {
            return OVERLOAD_FACTOR_LOWER_BOUND;
        }
        double factor = (CPU_MAX - cpu) / span;
        return Math.max(OVERLOAD_FACTOR_LOWER_BOUND, Math.min(1D, factor));
    }

    private int readCpuPermille() {
        IntSupplier injected = this.cpuPermilleReader;
        if (injected != null) {
            return injected.getAsInt();
        }
        double load = switch (cpuSource) {
            case PROCESS -> processCpuLoad();
            case SYSTEM -> systemCpuLoad();
        };
        if (load < 0D) {
            return -1;
        }
        // load 是 0..1 的比例；Go 用千分比（900 = 90%）
        return (int) Math.round(load * CPU_MAX);
    }

    /**
     * 整机 CPU 口径，等价 Go 的 {@code stat.CpuUsage()}（Linux 下读 {@code /proc/stat}）。
     *
     * <p>⚠️ {@code getSystemCpuLoad()} <b>不在</b> {@link OperatingSystemMXBean} 上，
     * 而在 {@code com.sun.management.OperatingSystemMXBean} 这个 JDK 扩展接口上。
     * 若运行时不提供该扩展（非 HotSpot 系），返回 {@code -1} ⇒ 上层 fail-open。
     */
    private double systemCpuLoad() {
        OperatingSystemMXBean bean = osBean;
        if (bean instanceof com.sun.management.OperatingSystemMXBean sun) {
            return sun.getSystemCpuLoad();
        }
        return -1D;
    }

    /** 仅本 JVM 进程的 CPU 口径（偏离 Go，需显式配置）。 */
    private double processCpuLoad() {
        OperatingSystemMXBean bean = osBean;
        if (bean instanceof com.sun.management.OperatingSystemMXBean sun) {
            return sun.getProcessCpuLoad();
        }
        return -1D;
    }

    // ── 窗口推进：等价 Go 的 RollingWindow + IgnoreCurrentBucket ──────────────────

    /**
     * 把两个窗口按时间片切成 {@link #BUCKETS} 个槽；槽内计数与 {@link GoStyleBreaker}
     * 用同一套「按时间片寻址 + 过期清零」的写法，避免出队式推进的边界歧义。
     */
    private final long[][] rtSlots = new long[BUCKETS][2];   // [0]=RT 之和, [1]=请求数
    private final long[] passSlots = new long[BUCKETS];      // 单桶 QPS
    private final long[] slotStartMs = new long[BUCKETS];

    /** 当前时间片的起始毫秒；{@link #currentSlot} 指向该片对应的槽。 */
    private long currentBucketStartMs = -1L;
    private int currentSlot = -1;

    private int slotIndexOf(long startMs) {
        return (int) Math.floorMod(startMs / BUCKET_DURATION_MS, (long) BUCKETS);
    }

    /** 定位（或初始化）当前时间片的槽；返回该片起始毫秒。 */
    private long rollTo(long now) {
        long start = Math.floorDiv(now, BUCKET_DURATION_MS) * BUCKET_DURATION_MS;
        int idx = slotIndexOf(start);
        if (currentBucketStartMs == start) {
            return start;
        }
        if (slotStartMs[idx] != start) {
            // 该槽属于更早的时间片（或从未用过）⇒ 清零复用
            passSlots[idx] = 0L;
            rtSlots[idx][0] = 0L;
            rtSlots[idx][1] = 0L;
            slotStartMs[idx] = start;
        }
        currentBucketStartMs = start;
        currentSlot = idx;
        return start;
    }

    /**
     * 收集窗口内<b>已完成</b>桶的槽下标（跳过当前半桶与陈旧槽）。
     *
     * <p>⚠️ 对应 Go {@code collection/rollingwindow.go:70-72} 的 {@code IgnoreCurrentBucket}
     * （注释：<i>ignore the current bucket, because of partial data</i>），
     * 以及它把陈旧槽视为 0 的行为。
     *
     * <p><b>必须持有 {@link #lock}</b>（调用方均已持有）。
     */
    private int[] completedSlots() {
        long nowBucket = Math.floorDiv(System.currentTimeMillis(), BUCKET_DURATION_MS);
        int[] out = new int[BUCKETS];
        int n = 0;
        for (int i = 0; i < BUCKETS; i++) {
            if (i == currentSlot) {
                continue;                                   // ignore current
            }
            if (slotStartMs[i] == 0L
                    || nowBucket - Math.floorDiv(slotStartMs[i], BUCKET_DURATION_MS) >= BUCKETS) {
                continue;                                   // 陈旧槽，计 0
            }
            out[n++] = i;
        }
        int[] trimmed = new int[n];
        System.arraycopy(out, 0, trimmed, 0, n);
        return trimmed;
    }

    /** 排障用：当前在途数。 */
    long flying() {
        synchronized (lock) {
            return flying;
        }
    }

    /** 排障用：窗口长度（ms）。 */
    static long windowMs() {
        return WINDOW_MS;
    }
}