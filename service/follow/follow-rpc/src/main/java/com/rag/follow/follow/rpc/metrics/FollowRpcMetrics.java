package com.rag.follow.follow.rpc.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * follow-rpc 业务指标 —— 对应 Go 端
 * {@code service/follow/rpc/internal/metrics/metrics.go:13-43} 注册的 5 个 prometheus 指标：
 *
 * <pre>
 * RPCRequestCounter       subsystem=follow_rpc name=request_total              labels=[method, result]
 * RPCRequestDuration      subsystem=follow_rpc name=request_duration_seconds   labels=[method]
 * RelationActionCounter   subsystem=follow_rpc name=relation_action_total      labels=[action, result]
 * DBErrorCounter          subsystem=follow_rpc name=db_error_total             labels=[action, type]
 * RecommendationSizeGauge subsystem=follow_rpc name=recommendation_size        (无 label)
 * </pre>
 *
 * <p>Spring Boot 项目用 Micrometer 代替 prometheus client_golang，
 * <b>指标名与标签逐条 1:1</b> ⇒ 迁移完成后 Prometheus 抓取端不需要改配置。
 *
 * <p><b>⚠️ 四个 Counter 里的 {@code result} 标签取值只有两个</b>：
 * {@code "success"} / {@code "fail"}，判定标准是 {@code err == null}。
 * 这与 Go 的 {@code ObserveRPC} / {@code ObserveRelation} 完全一致，
 * 不要引入第三种取值。
 *
 * <p><b>⚠️ {@code ObserveRelation} 只在 4 个写方法上调用</b>
 * （Follow / Unfollow / Block / Unblock，Go 各 logic 的 defer 第 2 行），
 * 三个列表方法与 {@code GetRecommendations} <b>不</b>调用它 ——
 * 所以 {@code relation_action_total} 永远不会有 {@code get_follow_list} 这类 action。
 */
@Component
public class FollowRpcMetrics {

    /** 对应 Go {@code RPCRequestCounter}：{@code follow_rpc_request_total} labels=[method, result]。 */
    private static final String RPC_REQUEST_TOTAL = "follow_rpc_request_total";

    /** 对应 Go {@code RPCRequestDuration}：{@code follow_rpc_request_duration_seconds} labels=[method]。 */
    private static final String RPC_REQUEST_DURATION = "follow_rpc_request_duration_seconds";

    /** 对应 Go {@code RelationActionCounter}：{@code follow_rpc_relation_action_total} labels=[action, result]。 */
    private static final String RELATION_ACTION_TOTAL = "follow_rpc_relation_action_total";

    /** 对应 Go {@code DBErrorCounter}：{@code follow_rpc_db_error_total} labels=[action, type]。 */
    private static final String DB_ERROR_TOTAL = "follow_rpc_db_error_total";

    /** 对应 Go {@code RecommendationSizeGauge}：{@code follow_rpc_recommendation_size}。 */
    private static final String RECOMMENDATION_SIZE = "follow_rpc_recommendation_size";

    private final MeterRegistry registry;

    /** 缓存已注册的 timer（按 method 名缓存），避免每次 observeRpc 都重新 query。 */
    private final ConcurrentHashMap<String, Timer> timerCache = new ConcurrentHashMap<>();

    /**
     * Gauge 的后备状态。
     *
     * <p><b>为什么需要它</b>：Micrometer 的 {@code Gauge} 只持有一个<b>弱引用</b>到数值对象
     * （{@code AtomicInteger} 会被 {@code Gauge.builder(...)} 的默认 {@code reference} 语义弱引用化）。
     * 若把 {@code new AtomicInteger()} 直接内联传进去，GC 后 gauge 会读回 NaN。
     * 提升为字段即可保证与 Meter 同生命周期。
     */
    private final AtomicInteger recommendationSize = new AtomicInteger(0);

    public FollowRpcMetrics(MeterRegistry registry) {
        this.registry = registry;
        Gauge.builder(RECOMMENDATION_SIZE, recommendationSize, AtomicInteger::doubleValue)
                .description("Last generated recommendation size")
                .register(registry);
    }

    /**
     * 对应 Go {@code ObserveRPC(method, started, err)}（{@code metrics.go:55-63}）：
     * Counter +1（method + result 标签）＋ Timer 记录耗时（method 标签）。
     *
     * <p><b>8 个方法全部调用</b>：Go 侧每个 logic 的 defer 第 1 行。
     * method 名是 Go 的字符串字面量（{@code "Follow"} / {@code "Unfollow"} / …），
     * <b>首字母大写</b> —— 与 proto 的 RPC 名一致，不要改成小写。
     */
    public void observeRpc(String method, long startedAtNanos, Throwable err) {
        String resultLabel = (err == null) ? "success" : "fail";
        Counter.builder(RPC_REQUEST_TOTAL)
                .description("Total number of follow rpc requests")
                .tag("method", method)
                .tag("result", resultLabel)
                .register(registry)
                .increment();

        long durationNanos = System.nanoTime() - startedAtNanos;
        // ⚠️ **用 publishPercentileHistogram()，不用 publishPercentiles()**：
        //   Go 侧是 prometheus.NewHistogramVec(..., Buckets: prometheus.DefBuckets)
        //   （service/follow/rpc/internal/metrics/metrics.go:19-24）⇒ 暴露 `_bucket{le=...}`。
        //   `publishPercentiles()` 是**分位数**开关，Prometheus 文本格式**不支持**
        //   （只有 OpenMetrics 才有 quantile）⇒ 对着 Prometheus 抓取端它是无效配置，
        //   而 `publishPercentileHistogram()` 才是产出 `_bucket` 的那个开关，
        //   且其桶边界与 Go 的 prometheus.DefBuckets 逐个相同。
        timerCache.computeIfAbsent(method, name -> Timer.builder(RPC_REQUEST_DURATION)
                        .description("Follow rpc request duration in seconds")
                        .tag("method", name)
                        .publishPercentileHistogram()
                        .register(registry))
                .record(durationNanos, java.util.concurrent.TimeUnit.NANOSECONDS);
    }

    /**
     * 对应 Go {@code ObserveRelation(action, err)}（{@code metrics.go:65-72}）。
     *
     * <p>action 取值：{@code "follow"} / {@code "unfollow"} / {@code "block"} / {@code "unblock"}
     * —— <b>全小写</b>，与 proto RPC 名的大小写约定相反，别抄串。
     */
    public void observeRelation(String action, Throwable err) {
        String resultLabel = (err == null) ? "success" : "fail";
        Counter.builder(RELATION_ACTION_TOTAL)
                .description("Total number of relation actions")
                .tag("action", action)
                .tag("result", resultLabel)
                .register(registry)
                .increment();
    }

    /**
     * 对应 Go {@code ObserveDBError(action, typ)}（{@code metrics.go:74-76}）。
     *
     * <p><b>type 取值逐条对应 Go 各 logic 里传的第二个字符串</b>，共 11 个，
     * 少一个就少一个可观测维度：
     * <pre>
     *   exists_any_block      （followlogic.go:50）
     *   create_follow         （followlogic.go:61）
     *   delete_follow         （unfollowlogic.go:44）
     *   create_block_and_cleanup（blocklogic.go:49）
     *   delete_block          （unblocklogic.go:44）
     *   list_follow_targets   （getfollowlistlogic.go:50）
     *   list_block_targets    （getblocklistlogic.go:50）
     *   list_follower_users   （getfollowerlistlogic.go:50）
     *   list_direct_follows   （getrecommendationslogic.go:58）
     *   list_block_targets    （getrecommendationslogic.go:69）  ← 与上面同名，Go 侧就是重复的
     *   list_blocker_users    （getrecommendationslogic.go:76）
     *   list_layer_follow_targets（getrecommendationslogic.go:113）
     * </pre>
     */
    public void observeDbError(String action, String type) {
        Counter.builder(DB_ERROR_TOTAL)
                .description("Total number of follow rpc database errors")
                .tag("action", action)
                .tag("type", type)
                .register(registry)
                .increment();
    }

    /**
     * 对应 Go {@code ObserveRecommendationSize(size)}（{@code metrics.go:78-80}）—— 是个 Gauge，
     * 记的是<b>最近一次</b>推荐的条数，不是累计。
     *
     * <p><b>⚠️ 有 3 个上报点，其中 2 个是 {@code 0}</b>：
     * <ul>
     *   <li>直关为空（{@code getrecommendationslogic.go:63}）⇒ 0</li>
     *   <li>offset 越界（{@code :167}）⇒ 0</li>
     *   <li>正常返回（{@code :184}）⇒ 实际条数</li>
     * </ul>
     * 漏掉前两个会让「推不出推荐」的两种情形在图上<b>看不出区别</b>（值停留在上一次成功）。
     */
    public void observeRecommendationSize(int size) {
        recommendationSize.set(size);
    }

    /**
     * 读取 Gauge 当前值 —— 对应 Go 的 {@code RecommendationSizeGauge} 单值语义。
     *
     * <p><b>public 而非 package-private</b>：{@code recommendation_size} 是本域<b>唯一</b>
     * 需要断言「上报点有没有真的被走到」的指标 ——
     * 它有 3 个上报点，其中 2 个上报 0（直关为空、offset 越界），
     * 而这 2 条路径返回的是 {@code null} 响应、<b>单靠返回值断言不出来</b>。
     * 测试类在 {@code service} 包、指标在 {@code metrics} 包，故必须公开。
     */
    public int currentRecommendationSize() {
        return recommendationSize.get();
    }
}