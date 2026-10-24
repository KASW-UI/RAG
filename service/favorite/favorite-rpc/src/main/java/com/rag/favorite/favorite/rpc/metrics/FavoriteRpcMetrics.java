package com.rag.favorite.favorite.rpc.metrics;

import com.rag.favorite.favorite.rpc.config.FavoriteRpcLabels;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * favorite-rpc 指标 —— 对应 Go {@code service/favorite/rpc/internal/metrics/metrics.go} 的
 * <b>4 个 {@code CounterVec}</b>。
 *
 * <table border="1">
 *   <caption>Go ↔ Micrometer 对照</caption>
 *   <tr><th>Go（metrics.go）</th><th>Micrometer 名</th><th>labels</th></tr>
 *   <tr><td>{@code FavoriteRequestCounterMetric}（:14-19）</td>
 *       <td>{@code favorite_request_total}</td><td>{@code module / action / result}</td></tr>
 *   <tr><td>{@code FavoriteRequestSecondsCounterMetric}（:22-27）</td>
 *       <td>{@code favorite_request_seconds_counter}</td><td>{@code module / action}</td></tr>
 *   <tr><td>{@code FavoriteOpsCounterMetric}（:30-35）</td>
 *       <td>{@code favorite_ops_total}</td><td>{@code module / action / result}</td></tr>
 *   <tr><td>{@code FavoriteDBErrorCounterMetric}（:38-43）</td>
 *       <td>{@code favorite_db_error_total}</td><td>{@code module / action / type}</td></tr>
 * </table>
 *
 * <p>⚠️ <b>Go 的 {@code InitMetrics}（:46-54）不迁移</b>：它先
 * {@code prometheus.Unregister(collectors.NewGoCollector())} 再注册自己的 4 个。
 * 「注销 JVM collector」是 Go client 的行为，Micrometer 无对应物 ⇒ 不实现。
 *
 * <h2>🔴 三处必须保留的怪癖（修正即指标契约变更）</h2>
 *
 * <p><b>① {@code favorite_request_seconds_counter} 是 Counter 不是 Histogram。</b>
 * Go 用 {@code CounterVec} + {@code Add(time.Since(started).Seconds())}（:63）。
 * Micrometer 的 {@code Counter} 在 Prometheus 侧会<b>自动追加 {@code _total}</b>
 * ⇒ 输出名变成 {@code favorite_request_seconds_counter_total}。
 * <b>这是已知的指标面差异，不改</b>：把它换成 Timer 会让「累计耗时」的语义变成「分布」，
 * 那是<b>另一个</b>指标而不是同一个指标的修正。
 *
 * <p><b>② 🔴 Go 侧<b>没有</b>「错误被记成 success」这回事 —— 2026-10-25 实测证伪。</b>
 * 每个 logic 头部是 {@code defer func(){ metrics.ObserveRPC(module, action, started, err) }}()}，
 * 读的是<b>命名返回值</b> {@code err}；而错误分支写成
 * {@code return nil, favoritecommon.GRPCError(...)} ——
 * ⚠️ <b>Go 的 return 语句会把返回值赋给对应的命名返回值</b>
 * ⇒ {@code err} <b>非 nil</b> ⇒ {@code ObserveRPC} 记的是 <b>{@code fail}</b>，不是 {@code success}。
 * <p>⚠️ <b>此前本注释写的是「Go 侧 21 处错误返回被记成 success」—— 那是错的</b>：
 * ① 那个数字是 {@code return nil, GRPCError(...)} 的出现处数（实测 <b>22</b> 处，不是 21）；
 * ② 更关键的是<b>语义判断反了</b>：这些 return <b>都会</b>赋值命名返回值 {@code err}。
 * ⇒ <b>Go 与 RAG 在这一点上本来就一致</b>（都按真实结果记 success/fail），
 * <b>不存在「有意变更」，也不需要签字</b>。
 * <p>⇒ 2026-10-25 已撤销原 U5 条目（见 {@code docs/DRIFT/favorite/rpc.md} §9.3），
 * 并用<b>实测</b>（Go 侧最小复刻程序，见 {@code tools/metrics-probe/favorite/}）钉死这条语义。
 * <p>⚠️ <b>真正需要守的不是「Go 的缺陷」，而是「RAG 侧别漏设」</b>：
 * Java 没有「命名返回值 + defer」这套组合，RAG 侧靠 try/catch/finally +
 * 每个失败分支显式 {@code result = RESULT_FAIL} 来达成同一语义
 * ⇒ <b>新增失败分支时漏设 RESULT_FAIL，指标就会把失败记成 success</b>。
 * ⇒ 由 {@code FavoriteRpcMetricsResultTagTest} 逐条守住。
 *
 * <p><b>③ 两处 {@code module} label 写错</b>：{@code create_favorite_logic.go:89} 与
 * {@code get_favorites_by_folder_logic.go:68} 都在 <b>item</b> 流程里用了
 * {@code folderModule}。本实现<b>按 Go 原样保留</b>（改 label 代价低但属于契约变更，
 * 需另行拍板），并在调用点用注释标出。
 */
@Component
public class FavoriteRpcMetrics {

    private final MeterRegistry registry;

    public FavoriteRpcMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * 对应 Go {@code ObserveRPC(module, action, started, err)}（metrics.go:56-64）。
     *
     * <p>一次调用记两个指标：请求计数（带 result）+ 累计耗时秒数（不带 result）。
     *
     * @param module {@link FavoriteRpcLabels#MODULE_FOLDER} / {@link FavoriteRpcLabels#MODULE_ITEM}
     * @param action 见 {@link FavoriteRpcLabels}
     * @param started 逻辑开始时刻
     * @param result {@link FavoriteRpcLabels#RESULT_SUCCESS} / {@link FavoriteRpcLabels#RESULT_FAIL}
     */
    public void observeRpc(String module, String action, Instant started, String result) {
        Counter.builder("favorite_request_total")
                .description("Total favorite RPC requests")
                .tags("module", module, "action", action, "result", result)
                .register(registry)
                .increment();

        // ⚠️ 怪癖 ①：Go 用 CounterVec 累加秒数，不是 Histogram。名字保留 Go 的字面量。
        Counter.builder("favorite_request_seconds_counter")
                .description("Accumulated favorite RPC seconds")
                .tags("module", module, "action", action)
                .register(registry)
                .increment(Duration.between(started, Instant.now()).toNanos() / 1_000_000_000.0d);
    }

    /**
     * 对应 Go {@code ObserveOp(module, action, result)}（metrics.go:66-68）。
     *
     * <p>⚠️ <b>label 取值不做白名单过滤</b>：Go 直接把 module/action/result 塞进 label。
     * 本实现照搬 —— 加白名单会让 {@code dependency} 这类值消失，改变告警可见性。
     */
    public void observeOp(String module, String action, String result) {
        Counter.builder("favorite_ops_total")
                .description("Total favorite business operations")
                .tags("module", module, "action", action, "result", result)
                .register(registry)
                .increment();
    }

    /**
     * 对应 Go {@code ObserveDBError(module, action, typ)}（metrics.go:70-72）。
     *
     * @param type {@link FavoriteRpcLabels#DB_TYPE_DB} / {@link FavoriteRpcLabels#DB_TYPE_DUPLICATE}
     */
    public void observeDbError(String module, String action, String type) {
        Counter.builder("favorite_db_error_total")
                .description("Total favorite database errors")
                .tags("module", module, "action", action, "type", type)
                .register(registry)
                .increment();
    }
}