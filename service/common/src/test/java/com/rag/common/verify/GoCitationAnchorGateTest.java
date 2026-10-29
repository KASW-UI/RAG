package com.rag.common.verify;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <h2>共享层 Go 出处的两道守门</h2>
 *
 * <p><b>为什么要这道门</b>：2026-10-29 本层自查发现共享层 {@code protection/} 与
 * {@code http/} 里有 <b>10 处 Go 出处写错</b>（行号 / 区间指到相邻函数或空行）⇒
 * 而这些引用<b>全部只存在于 javadoc 里</b> ⇒ ⇒ <b>不改一行代码，也不会让任何测试变红</b>
 * ⇒ ⇒ 「跑绿」对这类缺陷<b>完全无效</b> ⇒ ⇒ 必须有一条<b>直接读 javadoc</b> 的网。
 *
 * <h2>两道门各管一半</h2>
 * <ol>
 *   <li><b>引用锁</b>（{@link #correctedGoCitationsAreLocked()}）——
 *       <b>自包含</b>：只读本仓源码文本，断言「已订正的引用形态在」「已知写错的形态不在」
 *       ⇒ ⇒ <b>不依赖任何外部输入</b> ⇒ ⇒ 任何机器上都会真正执行 ⇒ ⇒
 *       <b>防回退</b>（防止有人把行号改回旧值、或从旧副本粘贴回来）。</li>
 *   <li><b>内容锚定</b>（{@link #goCitationsAreContentAnchored()}）——
 *       把每条引用的区间拿到 <b>Go 真源</b> 上比对，断言区间里<b>确实含有 javadoc 声称的内容</b>
 *       ⇒ ⇒ 抓的是「引用指到哪一行」⇒ ⇒ <b>只做存在性/越界检查抓不到这一类</b>
 *       （例：{@code sheddingstat.go:64} 是个<b>非空</b>行 ⇒ 存在性检查放行 ⇒
 *       但那里根本不是 {@code c := stat.CpuUsage()}）。</li>
 * </ol>
 *
 * <h2>⚠️ 输入不可见时<b>跳过</b>（不是「通过」）</h2>
 * <p>内容锚定那一道需要 <b>Go 模块缓存</b>（{@code GOMODCACHE}/{@code GO_REPO}）。
 * 本机没有缓存时 ⇒ <b>整条测试标记为 skipped</b>（surefire 报告里可见 skipped 计数）
 * ⇒ ⇒ <b>skip ≠ 已验</b> ⇒ ⇒ 读报告时<b>必须把 skipped 计入</b>，不能只报「绿」。
 * ⇒ 第一道「引用锁」<b>不受影响</b>，仍会真正执行。
 *
 * <h2>版本不写死</h2>
 * <p>go-zero 的版本<b>从真源 {@code go.mod} 现读</b>（{@code GO_REPO} 环境变量优先，
 * 否则用默认路径）⇒ ⇒ 真源升版时本门自动跟着走，<b>不会对着旧副本验</b>。
 */
class GoCitationAnchorGateTest {

    /** 真源 Go 仓路径（与 {@code tools/g3-anchor-check/check-anchors.py} 同一套口径）。 */
    private static final String DEFAULT_GO_REPO = "/Users/pr/Documents/java&golang/Sea-RideTheWind_golang";

    private static final String GO_ZERO_MODULE = "github.com/zeromicro/go-zero";

    /**
     * 内容锚定表：(Go 相对路径, 起行, 止行, 必须在该区间内出现的锚点串)。
     *
     * <p>⚠️ 这张表就是<b>断言的期望值</b> ⇒ ⇒ 改它等于改断言 ⇒ ⇒ 必须先回真源核实再改。
     *
     * <p><b>2026-10-29（R3 轮）扩表</b>：本表从 **21 条**扩到 **45 条**
     * ⇒ 覆盖 {@code protection/} + {@code http/} 两个机制密集区里
     * <b>每一条</b> {@code .go:行} 引用（唯一 (文件,行) 对）⇒ ⇒ 该区间的引用<b>内容锚定覆盖率 = 100%</b>。
     * <p>扩表过程<b>又抓出 7 处真缺陷</b>（区间指到相邻函数 / 空行 / 错的 const 块）⇒ ⇒ 逐条见
     * {@code docs/DRIFT/global/vs-common.md} §4.18.2。
     */
    private static final String[][] CITATIONS = {
            // —— GoStyleCpuReader.java ——
            {"core/stat/usage.go", "51", "53", "func CpuUsage()"},
            {"core/load/sheddingstat.go", "59", "59", "c := stat.CpuUsage()"},
            {"core/load/adaptiveshedder.go", "41", "43", "cpuThreshold"},
            // —— GoStyleSheddingStat.java ——
            {"core/load/sheddingstat.go", "37", "49", "IncrementTotal"},
            {"core/load/sheddingstat.go", "51", "68", "stat.CpuUsage()"},
            {"zrpc/internal/serverinterceptors/sheddinginterceptor.go", "17", "20", "sheddingStat *load.SheddingStat"},
            {"zrpc/internal/serverinterceptors/sheddinginterceptor.go", "51", "57", "func ensureSheddingStat()"},
            {"zrpc/internal/serverinterceptors/sheddinginterceptor.go", "28", "28", "IncrementTotal()"},
            {"zrpc/internal/serverinterceptors/sheddinginterceptor.go", "33", "33", "IncrementDrop()"},
            {"zrpc/internal/serverinterceptors/sheddinginterceptor.go", "42", "42", "IncrementPass()"},
            // 🆕 2026-10-29 扩表（原先未锚定）：熔断计数写进 breaker 自己的滚动窗口
            {"core/breaker/googlebreaker.go", "121", "130", "func (b *googleBreaker) markDrop()"},
            // 🆕 serviceType 常量在 :15（原引用 :18 指到了 struct 字段）
            {"zrpc/internal/serverinterceptors/sheddinginterceptor.go", "15", "15", "const serviceType = \"rpc\""},
            // 🆕 logInterval 在 :14（原引用 :20 指到了 type 块）
            {"core/stat/metrics.go", "14", "14", "logInterval"},
            // 🆕 每分钟上报的 goroutine 起点
            {"core/load/sheddingstat.go", "30", "35", "go st.run()"},
            // —— GoStyleRpcFailurePolicy.java ——
            {"zrpc/internal/serverinterceptors/breakerinterceptor.go", "53", "58", "func serverSideAcceptable"},
            {"zrpc/internal/codes/accept.go", "9", "17", "func Acceptable"},
            // —— GoStyleAdaptiveShedder.java ——
            {"core/load/adaptiveshedder.go", "245", "252", "func (as *adaptiveShedder) systemOverloaded()"},
            {"core/load/adaptiveshedder.go", "237", "242", "coolOffDuration"},
            // 🆕 shouldDrop() 的三个条件在 :208-225（原引用 :216-234 只指到函数尾 + stillHot）
            {"core/load/adaptiveshedder.go", "208", "225", "func (as *adaptiveShedder) shouldDrop() bool"},
            // 🆕 常量块
            {"core/load/adaptiveshedder.go", "18", "30", "defaultCpuThreshold"},
            // 🆕 windowScale
            {"core/load/adaptiveshedder.go", "115", "115", "windowScale"},
            // 🆕 Allow() 的判据
            {"core/load/adaptiveshedder.go", "126", "136", "ErrServiceOverloaded"},
            // 🆕 promise.Pass()
            {"core/load/adaptiveshedder.go", "284", "289", "func (p *promise) Pass()"},
            // 🆕 RT 向上取整
            {"core/load/adaptiveshedder.go", "285", "287", "math.Ceil(rt)"},
            // 🆕 promise.Fail()（原引用 :247-249 指到了 systemOverloaded 的尾部）
            {"core/load/adaptiveshedder.go", "280", "282", "func (p *promise) Fail()"},
            // 🆕 addFlying()
            {"core/load/adaptiveshedder.go", "139", "152", "func (as *adaptiveShedder) addFlying"},
            // 🆕 highThru()
            {"core/load/adaptiveshedder.go", "154", "160", "maxFlight := as.maxFlight() * as.overloadFactor()"},
            // 🆕 maxFlight()
            {"core/load/adaptiveshedder.go", "162", "170", "mathx.AtLeast(maxFlight, 1)"},
            // 🆕 maxPass()
            {"core/load/adaptiveshedder.go", "172", "180", "passCounter.Reduce"},
            // 🆕 minRt()
            {"core/load/adaptiveshedder.go", "182", "196", "func (as *adaptiveShedder) minRt()"},
            // 🆕 overloadFactor()
            {"core/load/adaptiveshedder.go", "202", "206", "mathx.Between"},
            // 🆕 IgnoreCurrentBucket
            {"core/collection/rollingwindow.go", "70", "72", "ignore the current bucket"},
            // 🆕 rtCounter.Add（测试接缝）
            {"core/load/adaptiveshedder.go", "287", "287", "rtCounter.Add"},
            // —— GoStyleJsonBodyConverter.java ——
            {"rest/httpx/requests.go", "117", "124", "func ParseJsonBody"},
            {"rest/httpx/requests.go", "153", "155", "func withJsonBody"},
            {"rest/httpx/requests.go", "17", "22", "maxBodyLen"},
            // 🆕 maxBodyLen 的具体取值（8 MiB）
            {"rest/httpx/requests.go", "21", "21", "8 << 20"},
            // 🆕 LimitReader：Go 只读前 8 MiB
            {"rest/httpx/requests.go", "119", "119", "io.LimitReader(r.Body, maxBodyLen)"},
            // 🆕 MaxBytes 默认 1 MiB
            {"rest/config.go", "53", "53", "default=1048576"},
            // 🆕 MaxBytes 挂在引擎上全局生效
            {"rest/engine.go", "163", "164", "MaxBytesHandler"},
            // —— GoStyleRouterErrorResponses.java（2026-10-29 R3 轮补入 · A6 轴的第三个落点）——
            // 🆕 bindRoute 的链构造（native middlewares + auth + 用户中间件）
            {"rest/engine.go", "104", "118", "appendAuthHandler"},
            // 🆕 ServeHTTP 的「无匹配」与「方法不匹配」两条分支
            {"rest/router/patrouter.go", "59", "83", "func (pr *patRouter) ServeHTTP"},
            // 🆕 404 分支
            {"rest/router/patrouter.go", "73", "73", "pr.handleNotFound(w, r)"},
            {"rest/router/patrouter.go", "93", "97", "http.NotFound(w, r)"},
            // 🆕 405 分支（只 Set(Allow) + WriteHeader(405)，无 body）
            {"rest/router/patrouter.go", "79", "82", "http.StatusMethodNotAllowed"},
            // 🆕 methodsAllowed
            {"rest/router/patrouter.go", "101", "117", "func (pr *patRouter) methodsAllowed"},
            // —— GoStyleStatusCapturingResponse.java ——
            {"rest/internal/response/withcoderesponsewriter.go", "23", "26", "Code:   http.StatusOK"},
            {"rest/handler/breakerhandler.go", "33", "33", "cw.Code < http.StatusInternalServerError"},
            // 🆕 整个 defer 块（Accept / Reject 两支；原引用 :39 指到了 next.ServeHTTP）
            {"rest/handler/breakerhandler.go", "32", "38", "promise.Reject"},
            // —— GoStyleCpuReader.java 的「哨兵值论证」段 ——
            {"core/stat/usage.go", "22", "22", "var cpuUsage int64"},
            {"core/stat/usage.go", "35", "39", "cpuUsage"},
    };

    /**
     * 本门覆盖的源码文件（相对模块目录）—— 它们的 javadoc 引用必须全部落在已验表内。
     *
     * <p>⚠️ **这是「白名单」而非「全仓」** ⇒ ⇒ 只对这 6 个文件生效
     * ⇒ ⇒ 其余共享层文件的引用由 {@code tools/g3-anchor-check/} 与人工扫负责
     * ⇒ ⇒ **不要把本门读成「共享层引用全有网」**。
     */
    private static final String[] SOURCE_FILES = {
            "src/main/java/com/rag/common/protection/GoStyleCpuReader.java",
            "src/main/java/com/rag/common/protection/GoStyleSheddingStat.java",
            "src/main/java/com/rag/common/protection/GoStyleAdaptiveShedder.java",
            "src/main/java/com/rag/common/protection/GoStyleRpcFailurePolicy.java",
            "src/main/java/com/rag/common/protection/GoStyleStatusCapturingResponse.java",
            "src/main/java/com/rag/common/http/GoStyleJsonBodyConverter.java",
            "src/main/java/com/rag/common/http/GoStyleJsonBodyConverterConfig.java",
            // 🆕 2026-10-29 R3 轮补入（A6 轴的第三个共享层落点）
            "src/main/java/com/rag/common/http/GoStyleRouterErrorResponses.java",
    };

    /** 已订正的引用形态：文件路径 → 必须出现的字面量。 */
    private static final Object[][] LOCKS = {
            {"src/main/java/com/rag/common/protection/GoStyleCpuReader.java", "core/stat/usage.go:51"},
            {"src/main/java/com/rag/common/protection/GoStyleCpuReader.java", "core/load/sheddingstat.go:59"},
            {"src/main/java/com/rag/common/protection/GoStyleCpuReader.java", "cpuThreshold int64"},
            {"src/main/java/com/rag/common/protection/GoStyleCpuReader.java", "&gt;= cpuThreshold"},
            {"src/main/java/com/rag/common/protection/GoStyleSheddingStat.java", "core/load/sheddingstat.go:37-49"},
            {"src/main/java/com/rag/common/protection/GoStyleSheddingStat.java", "core/load/sheddingstat.go:51-68"},
            {"src/main/java/com/rag/common/protection/GoStyleSheddingStat.java", "sheddinginterceptor.go:17-20"},
            {"src/main/java/com/rag/common/protection/GoStyleSheddingStat.java", "sheddinginterceptor.go:51-57"},
            {"src/main/java/com/rag/common/protection/GoStyleAdaptiveShedder.java", "adaptiveshedder.go:245-252"},
            {"src/main/java/com/rag/common/protection/GoStyleAdaptiveShedder.java", "adaptiveshedder.go:237-242"},
            {"src/main/java/com/rag/common/http/GoStyleJsonBodyConverter.java", "requests.go:117-124,153-155"},
            {"src/main/java/com/rag/common/protection/GoStyleStatusCapturingResponse.java",
                    "rest/internal/response/withcoderesponsewriter.go:23-26"},
            {"src/main/java/com/rag/common/protection/GoStyleStatusCapturingResponse.java",
                    "rest/handler/breakerhandler.go:33"},
            // 🆕 2026-10-29 扩表锁（本轮内容锚定新抓出的 7 处）
            {"src/main/java/com/rag/common/protection/GoStyleSheddingStat.java",
                    "core/breaker/googlebreaker.go:121-130"},
            {"src/main/java/com/rag/common/protection/GoStyleSheddingStat.java",
                    "zrpc/internal/serverinterceptors/sheddinginterceptor.go:15"},
            {"src/main/java/com/rag/common/protection/GoStyleSheddingStat.java", "core/stat/metrics.go:14"},
            {"src/main/java/com/rag/common/protection/GoStyleSheddingStat.java", "sheddingstat.go:30-35"},
            {"src/main/java/com/rag/common/protection/GoStyleAdaptiveShedder.java",
                    "adaptiveshedder.go:208-225"},
            {"src/main/java/com/rag/common/protection/GoStyleAdaptiveShedder.java",
                    "adaptiveshedder.go:18-30"},
            {"src/main/java/com/rag/common/protection/GoStyleAdaptiveShedder.java",
                    "adaptiveshedder.go:280-282"},
            {"src/main/java/com/rag/common/protection/GoStyleAdaptiveShedder.java",
                    "adaptiveshedder.go:139-152"},
            {"src/main/java/com/rag/common/protection/GoStyleAdaptiveShedder.java",
                    "adaptiveshedder.go:154-160"},
            {"src/main/java/com/rag/common/protection/GoStyleAdaptiveShedder.java",
                    "adaptiveshedder.go:162-170"},
            {"src/main/java/com/rag/common/protection/GoStyleAdaptiveShedder.java",
                    "adaptiveshedder.go:172-180"},
            {"src/main/java/com/rag/common/protection/GoStyleAdaptiveShedder.java",
                    "adaptiveshedder.go:182-196"},
            {"src/main/java/com/rag/common/protection/GoStyleAdaptiveShedder.java",
                    "adaptiveshedder.go:202-206"},
            {"src/main/java/com/rag/common/protection/GoStyleAdaptiveShedder.java",
                    "adaptiveshedder.go:126-136"},
            {"src/main/java/com/rag/common/protection/GoStyleAdaptiveShedder.java",
                    "adaptiveshedder.go:284-289"},
            {"src/main/java/com/rag/common/protection/GoStyleAdaptiveShedder.java",
                    "adaptiveshedder.go:285-287"},
            {"src/main/java/com/rag/common/protection/GoStyleAdaptiveShedder.java", "adaptiveshedder.go:287"},
            {"src/main/java/com/rag/common/protection/GoStyleAdaptiveShedder.java",
                    "adaptiveshedder.go:115"},
            {"src/main/java/com/rag/common/protection/GoStyleAdaptiveShedder.java",
                    "collection/rollingwindow.go:70-72"},
            {"src/main/java/com/rag/common/protection/GoStyleStatusCapturingResponse.java",
                    "rest/handler/breakerhandler.go:32-38"},
            {"src/main/java/com/rag/common/http/GoStyleJsonBodyConverter.java", "requests.go:21"},
            {"src/main/java/com/rag/common/http/GoStyleJsonBodyConverter.java", "requests.go:119"},
            {"src/main/java/com/rag/common/http/GoStyleJsonBodyConverter.java", "rest/config.go:53"},
            {"src/main/java/com/rag/common/http/GoStyleJsonBodyConverter.java", "rest/engine.go:163-164"},
    };

    /** 已知写错的引用形态：文件路径 → 必须<b>不</b>再出现的字面量。 */
    private static final Object[][] FORBIDDEN = {
            {"src/main/java/com/rag/common/protection/GoStyleCpuReader.java", "core/load/sheddingstat.go:64"},
            {"src/main/java/com/rag/common/protection/GoStyleCpuReader.java", "func() bool"},
            {"src/main/java/com/rag/common/protection/GoStyleSheddingStat.java", "core/load/sheddingstat.go:37-47"},
            {"src/main/java/com/rag/common/protection/GoStyleSheddingStat.java", "core/load/sheddingstat.go:52-70"},
            {"src/main/java/com/rag/common/protection/GoStyleSheddingStat.java", "sheddinginterceptor.go:19-21,28-29,37"},
            {"src/main/java/com/rag/common/protection/GoStyleAdaptiveShedder.java", "adaptiveshedder.go:245-256"},
            {"src/main/java/com/rag/common/protection/GoStyleAdaptiveShedder.java", "adaptiveshedder.go:237-244"},
            {"src/main/java/com/rag/common/http/GoStyleJsonBodyConverter.java", "requests.go:117-125"},
            // 🆕 2026-10-29 扩表禁用名单（本轮内容锚定新抓出的 7 处写错形态）
            //    ① googlebreaker 的完整路径必须带 core/breaker/ 前缀（裸名在真源里找不到）
            {"src/main/java/com/rag/common/protection/GoStyleSheddingStat.java", "{@code googlebreaker.go:121-130}"},
            //    ② serviceType 常量在 :15，不在 :18
            {"src/main/java/com/rag/common/protection/GoStyleSheddingStat.java", "sheddinginterceptor.go:18"},
            //    ③ logInterval 在 :14，不在 :20
            {"src/main/java/com/rag/common/protection/GoStyleSheddingStat.java", "core/stat/metrics.go:20"},
            //    ④ 包级单例 var 块是 :17-20，不在 :19-21
            {"src/main/java/com/rag/common/protection/GoStyleSheddingStat.java", "sheddinginterceptor.go:19-21"},
            //    ⑤ breaker 判据在 :32-38/:33，不在 :39
            {"src/main/java/com/rag/common/protection/GoStyleStatusCapturingResponse.java", "breakerhandler.go:39"},
            //    ⑥ shouldDrop() 是 :208-225，不在 :216-234
            {"src/main/java/com/rag/common/protection/GoStyleAdaptiveShedder.java", "adaptiveshedder.go:216-234"},
            //    ⑦ promise.Fail() 是 :280-282，不在 :247-249
            {"src/main/java/com/rag/common/protection/GoStyleAdaptiveShedder.java", "adaptiveshedder.go:247-249"},
    };

    @Test
    @DisplayName("引用锁：已订正的 Go 出处形态必须在 · 已知写错的形态必须不在（自包含 · 不依赖外部输入）")
    void correctedGoCitationsAreLocked() throws IOException {
        List<String> missing = new ArrayList<>();
        List<String> regressed = new ArrayList<>();

        for (Object[] lock : LOCKS) {
            String text = readSource((String) lock[0]);
            if (!text.contains((String) lock[1])) {
                missing.add(lock[0] + " 缺：" + lock[1]);
            }
        }
        for (Object[] bad : FORBIDDEN) {
            String text = readSource((String) bad[0]);
            if (text.contains((String) bad[1])) {
                regressed.add(bad[0] + " 仍含写错形态：" + bad[1]);
            }
        }

        assertThat(missing).as("已订正的引用形态不得消失（回退即红）").isEmpty();
        assertThat(regressed).as("已知写错的引用形态不得复活").isEmpty();
    }

    /**
     * 第三道门：<b>javadoc 里的每一条 {@code .go:行} 引用，都必须落在「已验表」覆盖的那条引用上</b>。
     *
     * <p><b>它补的是哪条缝</b>：前两道门之间有一条缝 ——
     * 「内容锚定」验的是<b>表 vs Go</b>，「引用锁」验的是<b>javadoc vs 名单里的若干形态</b>
     * ⇒ ⇒ 若有人把 javadoc 改成<b>名单以外的第三个错值</b>（既不是已订正形态、也不在禁用名单里），
     * 前两道门都抓不到 ⇒ ⇒ 本道门把它抓住（因为它不在已验表里）。
     *
     * <p><b>🔴 2026-10-29（R3 轮）：本道门从「留痕不判红」升级为「判红」</b>。
     * <p><b>升级理由</b>：上一轮它是「留痕不判红」，理由是「这 7 个文件里 47 条引用只锚定了 21 条
     * ⇒ 若一律判红则本仓当前就是红的 ⇒ ⇒ 守门会变成常红的摆设」。
     * ⇒ ⇒ 本轮把那 **26 条全部锚定**（并因此又抓出 **7 处真缺陷**）⇒ ⇒ **覆盖 = 45/45 = 100%**
     * ⇒ ⇒ 「判红」现在<b>不会</b>让本仓变红 ⇒ ⇒ 判红才有意义 ⇒ ⇒ **本轮起它是真门**。
     * <p>⚠️ <b>回归风险</b>：若将来有人在这些文件里新增一条 {@code .go:行} 引用而<b>忘了</b>加进已验表
     * ⇒ ⇒ 本门会红 ⇒ ⇒ 这是<b>期望行为</b>（新增引用必须先内容锚定）。
     */
    @Test
    @DisplayName("引用白名单：javadoc 里的每条 .go:行 引用都必须落在已验表内（自包含 · 不依赖外部输入）")
    void everyJavadocCitationIsCoveredByTheVerifiedTable() throws IOException {
        // ⚠️ javadoc 里同一个文件可能写成**全路径**（`core/load/adaptiveshedder.go`）
        //    也可能写成**裸文件名**（`adaptiveshedder.go`）⇒ ⇒ 一律按 **basename** 比对
        //    ⇒ 否则裸写形态会因「对不上全路径」而**静默漏检**。
        java.util.Set<String> verified = new java.util.HashSet<>();
        java.util.Set<String> files = new java.util.HashSet<>();
        for (String[] c : CITATIONS) {
            String base = c[0].substring(c[0].lastIndexOf('/') + 1);
            verified.add(base + ":" + c[1]);
            files.add(base);
        }

        List<String> uncovered = new ArrayList<>();
        List<String> unknownFiles = new ArrayList<>();
        for (String relative : SOURCE_FILES) {
            String text = readSource(relative);
            java.util.regex.Matcher m = Pattern.compile(
                    "([A-Za-z0-9_./-]+\\.go):(\\d+)(?:\\s*[-–]\\s*(\\d+))?").matcher(text);
            while (m.find()) {
                String raw = m.group(1);
                String file = raw.substring(raw.lastIndexOf('/') + 1);
                String from = m.group(2);
                if (!files.contains(file)) {
                    // 文件本身不在已验表里 ⇒ 🔴 本轮起**判红**（新增引用必须先内容锚定）
                    unknownFiles.add(relative + " 引用了未纳入表的文件：" + raw + ":" + from);
                    continue;
                }
                if (!verified.contains(file + ":" + from)) {
                    uncovered.add(relative + " 的引用 " + file + ":" + from + " 不在已验表内");
                }
            }
        }
        System.out.println("[G4] ℹ️ 已验表覆盖 " + verified.size() + " 条引用；本组文件的其余引用（未锚定）共 "
                + uncovered.size() + " 条 + 未纳入表的文件引用 " + unknownFiles.size() + " 条 ⇒ 剩余项。");
        // 🔴 2026-10-29：覆盖已达 45/45 ⇒ 「判红」不再让本仓变红 ⇒ ⇒ 升级为真门（防新增引用漏锚定）。
        assertThat(uncovered).as("这些 .go:行 引用不在已验表内 ⇒ 必须先内容锚定再写进 javadoc").isEmpty();
        assertThat(unknownFiles).as("这些 .go:行 引用指向的文件不在已验表内 ⇒ 必须先内容锚定").isEmpty();
    }

    @Test
    @DisplayName("内容锚定：每条 Go 出处的区间里必须真的含有 javadoc 声称的内容（缓存不可见 ⇒ 跳过 ≠ 已验）")
    void goCitationsAreContentAnchored() throws IOException {
        Path goRepo = goRepo();
        if (!Files.isReadable(goRepo.resolve("go.mod"))) {
            skip("真源 Go 仓不可读（GO_REPO=" + goRepo + "）");
        }

        String version = pinnedGoZeroVersion(goRepo.resolve("go.mod"));
        if (version == null) {
            skip("真源 go.mod 里读不到 " + GO_ZERO_MODULE + " 的版本");
        }

        // ⚠️ 模块缓存目录名带 `v` 前缀（`go-zero@v1.10.2`）⇒ 版本号本体不带 `v` ⇒ 这里必须补回来。
        //    （2026-10-29 踩过：漏了这个 `v` ⇒ 目录拼成 `go-zero@1.10.2` ⇒ **永远跳过** ⇒
        //      守门看着是「绿的（skipped）」，实际**一次都没跑过** ⇒ 这就是「跑绿 ≠ 判据活着」。）
        Path moduleRoot = moduleCacheRoot().resolve(GO_ZERO_MODULE + "@v" + version);
        if (!Files.isReadable(moduleRoot.resolve("core/load/sheddingstat.go"))) {
            skip("Go 模块缓存不可读（" + moduleRoot + "）");
        }

        List<String> bad = new ArrayList<>();
        for (String[] c : CITATIONS) {
            Path goFile = moduleRoot.resolve(c[0]);
            if (!Files.isReadable(goFile)) {
                bad.add(c[0] + " 在 v" + version + " 里不存在");
                continue;
            }
            List<String> lines = Files.readAllLines(goFile, StandardCharsets.UTF_8);
            int from = Integer.parseInt(c[1]);
            int to = Integer.parseInt(c[2]);
            if (to > lines.size()) {
                bad.add(c[0] + ":" + c[1] + "-" + c[2] + " 越界（文件仅 " + lines.size() + " 行）");
                continue;
            }
            boolean hit = false;
            for (int i = from; i <= to; i++) {
                if (lines.get(i - 1).contains(c[3])) {
                    hit = true;
                    break;
                }
            }
            if (!hit) {
                bad.add(c[0] + ":" + c[1] + "-" + c[2] + " 区间内无锚点 " + c[3]);
            }
        }
        assertThat(bad).as("内容锚定失败的 Go 出处").isEmpty();
        System.out.println("[G4] 内容锚定已实跑：" + CITATIONS.length + " 条引用逐条比对 go-zero@v" + version);
    }

    /** 跳过必须<b>留痕</b>：surefire 的 XML 不记 `message` ⇒ ⇒ 显式打一行到 stdout。 */
    private static void skip(String reason) {
        System.out.println("[G4] ⚠️ 内容锚定**跳过**（≠ 已验）：" + reason);
        Assumptions.abort(reason);
    }

    // ————— 工具方法 —————

    private static String readSource(String relativePath) throws IOException {
        Path p = locate(relativePath);
        assertThat(p).as("源码必须可读：" + relativePath).isNotNull();
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    }

    /**
     * 定位仓内源码：先按相对路径（surefire 的 {@code user.dir} 通常 = 模块目录），
     * 找不到则<b>向上逐级</b>找 ⇒ ⇒ 不依赖 surefire 的工作目录约定。
     */
    private static Path locate(String relativePath) {
        Path direct = Paths.get(relativePath);
        if (Files.isReadable(direct)) {
            return direct;
        }
        Path base = Paths.get("").toAbsolutePath();
        for (int up = 0; up < 6; up++) {
            Path candidate = base.resolve(relativePath);
            if (Files.isReadable(candidate)) {
                return candidate;
            }
            base = base.getParent();
            if (base == null) {
                break;
            }
        }
        return null;
    }

    private static Path goRepo() {
        String env = System.getenv("GO_REPO");
        return Paths.get(env == null || env.isBlank() ? DEFAULT_GO_REPO : env);
    }

    private static Path moduleCacheRoot() {
        String env = System.getenv("GOMODCACHE");
        if (env != null && !env.isBlank()) {
            return Paths.get(env);
        }
        return Paths.get(System.getProperty("user.home"), "go", "pkg", "mod");
    }

    /** 从真源 {@code go.mod} 现读 go-zero 的版本 ⇒ **不写死** ⇒ 真源升版时本门自动跟着走。 */
    private static String pinnedGoZeroVersion(Path goMod) throws IOException {
        Pattern p = Pattern.compile("^\\s*" + Pattern.quote(GO_ZERO_MODULE) + "\\s+v(\\d+\\.\\d+\\.\\d+)\\s*$");
        for (String line : Files.readAllLines(goMod, StandardCharsets.UTF_8)) {
            Matcher m = p.matcher(line);
            if (m.matches()) {
                return m.group(1);
            }
        }
        return null;
    }
}
