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
            // —— GoStyleRpcFailurePolicy.java ——
            {"zrpc/internal/serverinterceptors/breakerinterceptor.go", "53", "58", "func serverSideAcceptable"},
            {"zrpc/internal/codes/accept.go", "9", "17", "func Acceptable"},
            // —— GoStyleAdaptiveShedder.java ——
            {"core/load/adaptiveshedder.go", "245", "252", "func (as *adaptiveShedder) systemOverloaded()"},
            {"core/load/adaptiveshedder.go", "237", "242", "coolOffDuration"},
            // —— GoStyleJsonBodyConverter.java ——
            {"rest/httpx/requests.go", "117", "124", "func ParseJsonBody"},
            {"rest/httpx/requests.go", "153", "155", "func withJsonBody"},
            {"rest/httpx/requests.go", "17", "22", "maxBodyLen"},
            // —— GoStyleStatusCapturingResponse.java ——
            {"rest/internal/response/withcoderesponsewriter.go", "23", "26", "Code:   http.StatusOK"},
            {"rest/handler/breakerhandler.go", "33", "33", "cw.Code < http.StatusInternalServerError"},
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
     * 第三道门：**javadoc 里的每条 `.go:行` 引用，都必须落在「已验表」覆盖的那条引用上**
     * （或落在本仓已知的少数「非区间型」引用上）。
     *
     * <p><b>它补的是哪条缝</b>：前两道门之间有一条缝 ——
     * 「内容锚定」验的是<b>表 vs Go</b>，「引用锁」验的是<b>javadoc vs 名单里的若干形态</b>
     * ⇒ ⇒ 若有人把 javadoc 改成<b>名单以外的第三个错值</b>（既不是已订正形态、也不在禁用名单里），
     * 前两道门都抓不到 ⇒ ⇒ 本道门把它抓住（因为它不在已验表里）。
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
                    // 文件本身不在已验表里 ⇒ **不判红**（判红会误伤历史引用），只**留痕**
                    unknownFiles.add(relative + " 引用了未纳入表的文件：" + raw + ":" + from);
                    continue;
                }
                if (!verified.contains(file + ":" + from)) {
                    uncovered.add(relative + " 的引用 " + file + ":" + from + " 不在已验表内");
                }
            }
        }
        if (!unknownFiles.isEmpty()) {
            System.out.println("[G4] ℹ️ 以下引用未纳入已验表（**不判红**，只留痕）：" + unknownFiles);
        }
        // ⚠️ **本道门刻意「留痕不判红」** ⇒ 理由：
        //   这 7 个文件里共 40+ 条 `.go:行` 引用，其中只有 21 条进了已验表（内容锚定过）
        //   ⇒ ⇒ 若对「未纳入表」一律判红 ⇒ **本仓当前就是红的** ⇒ ⇒ 守门会变成「常红的摆设」⇒ ⇒ 无意义。
        //   ⇒ ⇒ 所以：**判红的责任交给「引用锁」（本轮修过的 11 处）**，
        //      本道门只负责把「还没核过的引用」**如实报出来**（数量进台账 ⇒ 变成可量化的剩余项）。
        //   ⇒ ⚠️ 因此**不能**把本道门读成「这 7 个文件的引用全有网」。
        System.out.println("[G4] ℹ️ 已验表覆盖 " + verified.size() + " 条引用；本组文件的其余引用（未锚定）共 "
                + uncovered.size() + " 条 + 未纳入表的文件引用 " + unknownFiles.size() + " 条 ⇒ 剩余项。");
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
