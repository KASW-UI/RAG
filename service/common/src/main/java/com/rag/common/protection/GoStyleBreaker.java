package com.rag.common.protection;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;

/**
 * Go {@code core/breaker/googlebreaker.go} 的逐行 Java 复刻 —— <b>每个路由一个实例</b>。
 *
 * <p><b>Go 出处</b>：{@code rest/handler/breakerhandler.go:19}
 * {@code brk := breaker.NewBreaker(breaker.WithName(strings.Join([]string{method, path}, "://")))}
 * ⇒ <b>breaker 的隔离单位是 {@code METHOD://path}，不是全局</b>。
 *
 * <p><b>常量（{@code googlebreaker.go:12-21}，逐个照抄，不做「合理化」调整）</b>：
 * <table border="1">
 *   <caption>熔断常量</caption>
 *   <tr><th>Go 常量</th><th>值</th><th>含义</th></tr>
 *   <tr><td>{@code window}</td><td>10s</td><td>滚动窗口总长</td></tr>
 *   <tr><td>{@code buckets}</td><td>40</td><td>桶数（⇒ 每桶 250ms）</td></tr>
 *   <tr><td>{@code k}</td><td>1.5</td><td>加权系数上界</td></tr>
 *   <tr><td>{@code minK}</td><td>1.1</td><td>加权系数下界</td></tr>
 *   <tr><td>{@code protection}</td><td>5</td><td>冷启动保护请求数</td></tr>
 *   <tr><td>{@code forcePassDuration}</td><td>1s</td><td>最长拒绝间隔</td></tr>
 * </table>
 *
 * <p><b>accept() 的判据（{@code googlebreaker.go:52-79}，保持与 Go 同构的运算顺序）</b>：
 * <pre>
 * w              = k - (k-minK) * failingBuckets / buckets
 * weightedAccepts= max(w, minK) * accepts
 * dropRatio      = (total - protection - weightedAccepts) / (total + 1)
 * if dropRatio &lt;= 0            → 放行
 * if lastPass 距今 &gt; 1s         → 放行，并刷新 lastPass
 * dropRatio     *= (buckets - workingBuckets) / buckets
 * if rand() &lt; dropRatio         → 拒绝（503）；否则放行并刷新 lastPass
 * </pre>
 *
 * <p><b>⚠️ 最容易被「顺手改好」的三处语义，本类全部保持 Go 原样</b>：
 * <ol>
 *   <li><b>拒绝是概率性的，不是全拒</b>：命中 {@code rand() < dropRatio} 才拒绝。
 *       把它改成「一旦超阈值就全拒」在压测下会立刻放大成雪崩，
 *       而 Go 的渐进式拒绝恰恰是为了避免这个。</li>
 *   <li><b>force-pass 保底</b>：连续拒绝超过 1s 时，<b>必定放一个请求进去</b>做探测。
 *       去掉它会导致「熔断后永远不恢复」。</li>
 *   <li><b>只有已完成桶参与计算</b>：{@code collection/rollingwindow.go:65-80} 的
 *       {@code Reduce} 明确 {@code ignoreCurrent}（注释：<i>ignore the current bucket,
 *       because of partial data</i>）⇒ 统计窗口实际是 39 个完整桶 ≈ 9.75s。</li>
 * </ol>
 */
final class GoStyleBreaker {

    // ── 常量：与 googlebreaker.go:12-21 逐个对应 ──────────────────────────
    private static final int BUCKETS = 40;
    private static final long BUCKET_DURATION_MS = 250L;
    private static final long FORCE_PASS_DURATION_MS = 1_000L;
    private static final double K = 1.5D;
    private static final double MIN_K = 1.1D;
    private static final int PROTECTION = 5;

    /** 桶内计数，与 Go {@code bucket.go:10-15} 的四个字段一一对应。 */
    private static final class Bucket {
        long sum;
        long success;
        long failure;
        long drop;
        /** 该桶的起始时刻；与当前时间对不上即视为过期，需先清零。 */
        long bucketStartMs;

        void reset(long startMs) {
            sum = 0L;
            success = 0L;
            failure = 0L;
            drop = 0L;
            bucketStartMs = startMs;
        }
    }

    /** {@code history()} 的结果，对应 Go 的 {@code windowResult}（googlebreaker.go:30-36）。 */
    private static final class History {
        long accepts;
        long total;
        long failingBuckets;
        long workingBuckets;
    }

    private final String name;
    private final Bucket[] slots = new Bucket[BUCKETS];
    private final DoubleSupplier random;
    private final Object lock = new Object();

    /** {@code lastPass}；初值 0 表示「从未放行过」，与 Go 的 {@code syncx.AtomicDuration} 初值一致。 */
    private long lastPassMs;

    GoStyleBreaker(String name) {
        this(name, () -> ThreadLocalRandom.current().nextDouble());
    }

    /** 测试注入确定性随机源（§3.3：熔断是概率性的，必须能构造确定性用例）。 */
    GoStyleBreaker(String name, DoubleSupplier random) {
        this.name = name;
        this.random = random;
        for (int i = 0; i < BUCKETS; i++) {
            slots[i] = new Bucket();
        }
    }

    String name() {
        return name;
    }

    /**
     * 供测试与排障读取：窗口内（<b>只算已完成桶</b>）的请求总数（accepts + fails）。
     *
     * <p>⚠️ <b>因为 Go 的 {@code ignoreCurrent} 语义（{@code rollingwindow.go:70-72}），
     * 刚刚发生的失败不会出现在这里</b> —— 要看到它，必须<b>跨过一个桶边界</b>（250ms）。
     * ⇒ 写用例时若不跨桶，会看到「失败明明发生了，窗口却是空的」，那是<b>机制</b>不是 bug。
     */
    long historyTotal() {
        return history().total;
    }

    /**
     * {@code markSuccess()}：Go {@code googlebreaker.go:128-130}。
     * 由 {@code promise.Accept()} 调用。
     */
    void markSuccess() {
        add(Outcome.SUCCESS);
    }

    /**
     * {@code markFailure()}：Go {@code googlebreaker.go:124-126}。
     * 由 {@code promise.Reject(reason)} 调用。
     */
    void markFailure() {
        add(Outcome.FAILURE);
    }

    /**
     * {@code markDrop()}：Go {@code googlebreaker.go:120-122}。
     * 由「本次请求被熔断拒绝」时调用 —— <b>drop 也计入 {@code Sum}</b>
     * （{@code bucket.go:35-37} {@code drop()} 里 {@code Sum++; Drop++}）。
     */
    void markDrop() {
        add(Outcome.DROP);
    }

    private enum Outcome { SUCCESS, FAILURE, DROP }

    private void add(Outcome outcome) {
        synchronized (lock) {
            Bucket b = currentBucket();
            b.sum++;
            switch (outcome) {
                case SUCCESS -> b.success++;
                case FAILURE -> b.failure++;
                case DROP -> b.drop++;
                default -> throw new IllegalStateException("unreachable: " + outcome);
            }
        }
    }

    /** 取当前时间片对应的桶；若该片尚未初始化或已过期则先清零（对应 RollingWindow 的 Reset）。 */
    private Bucket currentBucket() {
        long now = System.currentTimeMillis();
        int idx = slotIndexOf(now);
        Bucket b = slots[idx];
        long start = bucketStartOf(now);
        if (b.bucketStartMs != start) {
            // 该槽属于更早的时间片（或从未用过）⇒ 清零复用；同一片内**不能**清，
            // 否则 250ms 内的多次计数会被逐次抹掉。
            b.reset(start);
        }
        return b;
    }

    /**
     * 时间片 → 槽下标。
     *
     * <p>⚠️ <b>必须先在 long 上取模再转型</b>：{@code currentTimeMillis()/250 ≈ 7e9}
     * 已超 {@code Integer.MAX_VALUE}，若写成
     * {@code (int) Math.floorDiv(now, D) % BUCKETS}，强转会先溢出成负数，
     * 再对 40 取模仍是负数 ⇒ {@code ArrayIndexOutOfBoundsException}。
     * （本条是 {@code GoStyleBreakerTest} 首轮跑出来的真实缺陷，非防御性注释。）
     */
    private static int slotIndexOf(long nowMs) {
        return (int) Math.floorMod(Math.floorDiv(nowMs, BUCKET_DURATION_MS), (long) BUCKETS);
    }

    /** 该时间片的起始毫秒（对齐桶边界）。 */
    private static long bucketStartOf(long nowMs) {
        return Math.floorDiv(nowMs, BUCKET_DURATION_MS) * BUCKET_DURATION_MS;
    }

    /**
     * {@code accept()}：Go {@code googlebreaker.go:52-79}。返回 {@code true} 表示<b>允许</b>。
     *
     * <p>⚠️ 判定顺序不可交换：{@code dropRatio <= 0} 的早退、
     * force-pass 的早退、{@code workingBuckets} 的缩放，三者的先后与 Go 完全一致。
     */
    boolean accept() {
        History h = history();
        double w = K - (K - MIN_K) * h.failingBuckets / BUCKETS;
        double weightedAccepts = Math.max(w, MIN_K) * h.accepts;
        // googlebreaker.go:60：分母用 total+1（注释明说是为性能/除零，不做改写）
        double dropRatio = (h.total - PROTECTION - weightedAccepts) / (h.total + 1);
        if (dropRatio <= 0) {
            return true;
        }

        synchronized (lock) {
            // googlebreaker.go:63-67：lastPass>0 且距今超过 forcePassDuration ⇒ 强制放一个探测请求
            if (lastPassMs > 0 && System.currentTimeMillis() - lastPassMs > FORCE_PASS_DURATION_MS) {
                lastPassMs = System.currentTimeMillis();
                return true;
            }
        }

        // googlebreaker.go:70：近期有成功请求的桶越多，越不 drop
        dropRatio *= (double) (BUCKETS - h.workingBuckets) / BUCKETS;

        // googlebreaker.go:72-78：命中即拒绝；否则放行并刷新 lastPass
        if (random.getAsDouble() < dropRatio) {
            return false;
        }
        synchronized (lock) {
            lastPassMs = System.currentTimeMillis();
        }
        return true;
    }

    /**
     * {@code history()}：Go {@code googlebreaker.go:133-151}。
     *
     * <p>⚠️ <b>跳过当前半桶</b>：Go 的 {@code RollingWindow.Reduce} 带
     * {@code ignoreCurrent}（{@code collection/rollingwindow.go:70-72}），
     * 注释为 <i>ignore the current bucket, because of partial data</i>。
     * 若把当前桶也算进去，会让「刚刚发生的失败」立刻拉低成功率 —— 熔断会比 Go 灵敏得多。
     *
     * <p>⚠️ {@code failingBuckets} / {@code workingBuckets} 在 Go 里<b>不是计数</b>，
     * 而是「从最后一个判定桶往前的<b>连续段长度</b>」：
     * 遇到反向结果就<b>归零</b>，遇到同向结果才 {@code ++}（例如
     * {@code googlebreaker.go:141-144}：{@code Failure>0 → workingBuckets=0}，
     * {@code else if Success>0 → workingBuckets++}）。本类照搬这个语义。
     */
    private History history() {
        History h = new History();
        long now = System.currentTimeMillis();
        long nowBucket = Math.floorDiv(now, BUCKET_DURATION_MS);
        int currentIdx = slotIndexOf(now);

        synchronized (lock) {
            // ⚠️ 必须按**时间顺序**（最旧 → 最新）遍历。
            // Go 的 collection/rollingwindow.go:75-77 是
            //   offset := (rw.offset + span + 1) % rw.size; rw.win.reduce(offset, diff, fn)
            // 即从窗口最旧的一桶开始顺序走；而下面的 failingBuckets/workingBuckets
            // 是「连续段长度」语义，**依赖顺序** —— 乱序遍历会算出完全不同的值。
            // 跳过 currentIdx（ignore current），共 BUCKETS-1 = 39 个已完成桶。
            for (int step = 1; step < BUCKETS; step++) {
                int i = Math.floorMod(currentIdx + step, BUCKETS);
                Bucket b = slots[i];
                // 只统计属于最近一个窗口的桶；陈旧槽（尚未在本窗口被写过）计 0
                if (b.bucketStartMs == 0L
                        || nowBucket - Math.floorDiv(b.bucketStartMs, BUCKET_DURATION_MS) >= BUCKETS) {
                    continue;
                }
                h.accepts += b.success;
                h.total += b.sum;
                if (b.failure > 0) {
                    h.workingBuckets = 0;
                } else if (b.success > 0) {
                    h.workingBuckets++;
                }
                if (b.success > 0) {
                    h.failingBuckets = 0;
                } else if (b.failure > 0) {
                    h.failingBuckets++;
                }
            }
        }
        return h;
    }

    /** 供测试与排障读取：清空全部统计。 */
    void reset() {
        synchronized (lock) {
            for (Bucket b : slots) {
                b.reset(0L);
            }
            lastPassMs = 0L;
        }
    }
}