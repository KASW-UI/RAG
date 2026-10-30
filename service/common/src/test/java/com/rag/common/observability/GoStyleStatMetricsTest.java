package com.rag.common.observability;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 🔴 <b>Stat 聚合语义的守门</b> —— 逐项对照 <b>Go 真源 {@code core/stat} 的真实实现</b>。
 *
 * <h3>⚠️ 期望值的来源：<b>双侧活体对拍</b>（不是「照着 Go 源码手算」）</h3>
 * <p>Go 侧用一个<b>独立进程</b>的探针跑<b>真的</b> {@code github.com/zeromicro/go-zero/core/stat}
 * （v1.10.2，与真源 {@code go.mod} 同版本），把 {@code stat.StatReport} 以 JSON 打出来；
 * 本类的期望值 = <b>那份 JSON 的逐字抄录</b>。
 * <pre>
 *   探针（/tmp/statprobe，<b>不入库</b>：tools/ 无 lane 拥有 ⇒ 见台账的转派单）：
 *     m := stat.NewMetrics("0.0.0.0:8082"); m.SetName("case-xxx")
 *     m.Add(stat.Task{Duration: …})   // 固定样本
 *     m.AddDrop()                     // 固定 drop 数
 *     stat.SetReportWriter(w)         // 60s 一轮的报告直接吐 JSON
 *
 *   Go 侧实际输出（2026-10-30 · pid 96230）：
 *     case-one     {"qps":0.016666668,"drops":0,"avg":5,"med":5,"t90":5,"t99":5,"t99p9":5}
 *     case-frac    {"qps":0.05,"drops":0,"avg":0.6666667,"med":0.9,"t90":0.9,"t99":0.9,"t99p9":0.9}
 *     case-ten     {"qps":0.16666667,"drops":0,"avg":5.5,"med":6,"t90":10,"t99":10,"t99p9":10}
 *     case-hundred {"qps":1.6666666,"drops":0,"avg":50.5,"med":51,"t90":91,"t99":100,"t99p9":100}
 *     case-drops   {"qps":0.033333335,"drops":3,"avg":2.5,"med":3,"t90":3,"t99":3,"t99p9":3}
 *     case-zero    （无样本 ⇒ 全 0）
 * </pre>
 * ⇒ ⇒ <b>本类的每个数字都来自 Go 真源的真实输出</b>，不是「按 Go 源码推出来的应该值」。
 *
 * <h3>🔴 三个怪癖各自对应哪条 Go 出处</h3>
 * <ol>
 *   <li><b>avg 先整除再除</b>：{@code core/stat/metrics.go:135}
 *       {@code report.Average = float32(duration/time.Millisecond) / float32(size)}
 *       ⇒ 三个 0.9ms 的样本：总 2.7ms ⇒ <b>整除成 2ms</b> ⇒ avg = 2/3 = 0.6666667（<b>不是</b> 0.9）。
 *       ⇒ 本仓若写成「先求平均再截断」⇒ 0.9 ⇒ <b>漂移</b>。</li>
 *   <li><b>median 是「上偏分位数」</b>：{@code core/stat/metrics.go:137-141} ＋ {@code core/stat/topk.go:31-44}
 *       ⇒ 十个样本 1..10ms：{@code topK(tasks, 5)[0]} = <b>6</b>（不是 5.5，也不是 5）。</li>
 *   <li><b>小样本退化最大值</b>：{@code core/stat/metrics.go:157-176}
 *       ⇒ 单样本 ⇒ median/90th/99th/99.9th 全 = 5；两样本 ⇒ 90th 起全 = 3。</li>
 * </ol>
 */
@DisplayName("🔴 Stat 聚合语义必须与 Go 真源 core/stat 逐值一致（双侧活体对拍基准）")
class GoStyleStatMetricsTest {

    private static GoStyleStatMetrics metricsWith(String name) {
        GoStyleStatMetrics metrics = new GoStyleStatMetrics(name);
        return metrics;
    }

    private static void addMs(GoStyleStatMetrics metrics, long... millis) {
        for (long m : millis) {
            metrics.add(m * 1_000_000L);
        }
    }

    private static void addSubMs(GoStyleStatMetrics metrics, long nanos) {
        metrics.add(nanos);
    }

    /** 跑一轮「取样本 → 聚合」（Go 的 {@code RemoveAll() + Execute()}）。 */
    private static GoStyleStatMetrics.StatReport round(GoStyleStatMetrics metrics) {
        return metrics.execute(metrics.removeAll());
    }

    // ==================================================================
    // ① 单样本：全部退化成最大值
    // ==================================================================

    @Test
    @DisplayName("① 单样本 ⇒ median/90th/99th/99.9th 全部退化成最大值（Go: metrics.go:171-177）")
    void singleSampleDegeneratesToMax() {
        GoStyleStatMetrics metrics = metricsWith("case-one");
        addMs(metrics, 5L);

        GoStyleStatMetrics.StatReport report = round(metrics);

        assertThat(report.reqsPerSecond()).isEqualTo(0.016666668f);        // Go: 1/60
        assertThat(report.drops()).isZero();
        assertThat(report.average()).isEqualTo(5F);              // Go: 5/1
        assertThat(report.median()).isEqualTo(5F);
        assertThat(report.top90th()).isEqualTo(5F);
        assertThat(report.top99th()).isEqualTo(5F);
        assertThat(report.top99p9th()).isEqualTo(5F);
    }

    // ==================================================================
    // ② avg 的整除截断（最容易「顺手优化」掉的一处）
    // ==================================================================

    @Test
    @DisplayName("② avg 必须【先整除截断再除样本数】（三个 0.9ms ⇒ 0.6666667，不是 0.9）")
    void averageTruncatesTotalMillisecondsFirst() {
        GoStyleStatMetrics metrics = metricsWith("case-frac");
        addSubMs(metrics, 900_000L);
        addSubMs(metrics, 900_000L);
        addSubMs(metrics, 900_000L);

        GoStyleStatMetrics.StatReport report = round(metrics);

        assertThat(report.average())
                .as("Go: metrics.go:135 —— duration/time.Millisecond 是**整型除法**（2.7ms ⇒ 2ms）"
                        + "⇒ 再除以样本数 3 ⇒ 0.6666667。"
                        + "若写成「先求平均再截断」⇒ 0.9 ⇒ 与 Go 不一致。")
                .isEqualTo(0.6666667f);
        assertThat(report.median()).isEqualTo(0.9F);
        assertThat(report.top90th()).isEqualTo(0.9F);
        assertThat(report.top99th()).isEqualTo(0.9F);
        assertThat(report.top99p9th()).isEqualTo(0.9F);
    }

    // ==================================================================
    // ③ median 是上偏分位数
    // ==================================================================

    @Test
    @DisplayName("③ median 实为【上偏分位数】（十个样本 1..10ms ⇒ 6，不是 5.5）")
    void medianIsUpperBiasedQuartile() {
        GoStyleStatMetrics metrics = metricsWith("case-ten");
        addMs(metrics, 1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L);

        GoStyleStatMetrics.StatReport report = round(metrics);

        assertThat(report.average()).isEqualTo(5.5F);            // Go: 55/10
        assertThat(report.median())
                .as("Go: metrics.go:137-141 —— topK(tasks, 5)[0] = 第 5 大 = 6（**不是**教科书中位数 5.5）")
                .isEqualTo(6F);
        assertThat(report.top90th()).isEqualTo(10F);              // Go: topK(top50p,1)[0]
        assertThat(report.top99th()).isEqualTo(10F);              // Go: onePercent=0 ⇒ 取 top10p 的最大值
        assertThat(report.top99p9th()).isEqualTo(10F);
        assertThat(report.reqsPerSecond()).isEqualTo(0.16666667f);          // Go: 10/60
    }

    @Test
    @DisplayName("③' 完整分位链（一百个样本 1..100ms ⇒ med 51 / t90 91 / t99 100 / t99p9 100）")
    void fullPercentileChain() {
        GoStyleStatMetrics metrics = metricsWith("case-hundred");
        for (long i = 1; i <= 100; i++) {
            addMs(metrics, i);
        }

        GoStyleStatMetrics.StatReport report = round(metrics);

        assertThat(report.average()).isEqualTo(50.5F);           // Go: 5050/100
        assertThat(report.median()).isEqualTo(51F);
        assertThat(report.top90th()).isEqualTo(91F);
        assertThat(report.top99th()).isEqualTo(100F);
        assertThat(report.top99p9th()).isEqualTo(100F);
        assertThat(report.reqsPerSecond()).isEqualTo(1.6666666f);          // Go: 100/60
    }

    // ==================================================================
    // ④ drops 与 qps/avg 互斥
    // ==================================================================

    @Test
    @DisplayName("④ drops 与 qps/avg 的分母【互斥】（两个样本 + 三次 drop ⇒ qps 按 2 算、drops=3）")
    void dropsAreExcludedFromQpsAndAverage() {
        GoStyleStatMetrics metrics = metricsWith("case-drops");
        addMs(metrics, 2L, 3L);
        metrics.addDrop();
        metrics.addDrop();
        metrics.addDrop();

        GoStyleStatMetrics.StatReport report = round(metrics);

        assertThat(report.drops()).isEqualTo(3);
        assertThat(report.reqsPerSecond())
                .as("Go: metrics.go:130 —— qps = size/60，size 只数**非 drop** 的样本")
                .isEqualTo(0.033333335f);
        assertThat(report.average())
                .as("Go: metrics.go:107-114 —— drop 样本不进 duration ⇒ avg 的分母不含 drop")
                .isEqualTo(2.5F);
        assertThat(report.median()).isEqualTo(3F);
    }

    // ==================================================================
    // ⑤ 无样本
    // ==================================================================

    @Test
    @DisplayName("⑤ 无样本 ⇒ qps/avg/分位全为 0（Go: metrics.go:134 的 size > 0 分支不成立）")
    void noSamplesYieldsZeroReport() {
        GoStyleStatMetrics metrics = metricsWith("case-zero");

        GoStyleStatMetrics.StatReport report = round(metrics);

        assertThat(report.reqsPerSecond()).isZero();
        assertThat(report.average()).isZero();
        assertThat(report.median()).isZero();
        assertThat(report.top90th()).isZero();
        assertThat(report.top99th()).isZero();
        assertThat(report.top99p9th()).isZero();
    }

    // ==================================================================
    // ⑥ 报告行格式（逐字对照 Go 的 logx.Statf）
    // ==================================================================

    @Test
    @DisplayName("⑥ 报告行必须与 Go 的 logx.Statf 逐字一致（格式串 + %.1f 渲染）")
    void reportLineMatchesGoFormat() {
        GoStyleStatMetrics metrics = metricsWith("case-hundred");
        for (long i = 1; i <= 100; i++) {
            addMs(metrics, i);
        }

        String line = GoStyleStatMetrics.formatReport(round(metrics));

        assertThat(line)
                .as("Go 侧实际输出（2026-10-30 探针）："
                        + "(case-hundred) - qps: 1.7/s, drops: 0, avg time: 50.5ms, med: 51.0ms, "
                        + "90th: 91.0ms, 99th: 100.0ms, 99.9th: 100.0ms")
                .isEqualTo("(case-hundred) - qps: 1.7/s, drops: 0, avg time: 50.5ms, med: 51.0ms, "
                        + "90th: 91.0ms, 99th: 100.0ms, 99.9th: 100.0ms");
    }

    @Test
    @DisplayName("⑥' 报告行里的 qps 渲染（小样本 ⇒ 0.0/s，Go 的 %.1f 同款）")
    void qpsRenderingMatchesGo() {
        GoStyleStatMetrics metrics = metricsWith("case-frac");
        addSubMs(metrics, 900_000L);
        addSubMs(metrics, 900_000L);
        addSubMs(metrics, 900_000L);

        assertThat(GoStyleStatMetrics.formatReport(round(metrics)))
                .as("Go 侧实际输出：(case-frac) - qps: 0.1/s, drops: 0, avg time: 0.7ms, med: 0.9ms, …")
                .startsWith("(case-frac) - qps: 0.1/s, drops: 0, avg time: 0.7ms, med: 0.9ms,");
    }

    // ==================================================================
    // ⑦ 周期边界：取走样本后必须清零
    // ==================================================================

    @Test
    @DisplayName("⑦ 取走样本后必须清零（Go: metrics.go:183-196 RemoveAll ⇒ 下一周期从 0 开始）")
    void removeAllClearsThePeriod() {
        GoStyleStatMetrics metrics = metricsWith("case-period");
        addMs(metrics, 5L);

        assertThat(metrics.pendingSampleCount()).isEqualTo(1L);
        round(metrics);
        assertThat(metrics.pendingSampleCount())
                .as("取走样本后当前周期必须为空 ⇒ 否则下一周期的 qps/avg 会把上一周期的也算进去")
                .isZero();
        assertThat(metrics.pendingDurationNanos()).isZero();
        assertThat(metrics.pendingDrops()).isZero();

        GoStyleStatMetrics.StatReport empty = round(metrics);
        assertThat(empty.reqsPerSecond()).isZero();
    }

    // ==================================================================
    // ⑧ topK 的语义（第 k 大）
    // ==================================================================

    @Test
    @DisplayName("⑧ topK 取【最大的 k 个】且 [0] 是其中最小者（Go: core/stat/topk.go:31-44）")
    void topKSemanticsMatchGo() {
        long[] all = {1, 9, 3, 7, 5};
        long[] top3 = GoStyleStatMetrics.topK(all, all.length, 3);
        assertThat(top3).hasSize(3);
        assertThat(top3[0])
                .as("第 3 大 = 5（小顶堆的堆顶）")
                .isEqualTo(5L);
        assertThat(java.util.Arrays.stream(top3).boxed().sorted().toList())
                .containsExactly(5L, 7L, 9L);

        long[] top1 = GoStyleStatMetrics.topK(all, all.length, 1);
        assertThat(top1[0]).as("k=1 ⇒ 就是最大值 9").isEqualTo(9L);
    }
}
