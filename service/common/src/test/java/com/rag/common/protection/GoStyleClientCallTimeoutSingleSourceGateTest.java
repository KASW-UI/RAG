package com.rag.common.protection;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <b>守门：「客户端硬上界」只允许<b>一个</b>实现源 —— 域侧<b>只配参数</b>，不得再造私有副本。</b>
 *
 * <h2>🔴 它守的是什么（2026-10-31 · G4 lane · P2-T04 共享层那半）</h2>
 * <p>P2-T04 要把「零 deadline 的 11 个类补 2000ms 硬上界」交给<b>各域</b>逐域做。
 * ⇒ ⇒ 若<b>每个域各写一份</b>封顶逻辑 ⇒ ⇒ 每补一个域就多一份副本 ⇒ ⇒ <b>副本数 = 漂移面</b>：
 * <ul>
 *   <li>每份副本都要各自保证「取 min」语义（Go 的 {@code context.WithTimeout}）
 *       ⇒ ⇒ 极易有的写 min、有的写成覆盖 ⇒ ⇒ <b>方向反了</b>；</li>
 *   <li>每份副本都要各自读「剩余预算」的单位（纳秒 vs 毫秒）⇒ ⇒ 又是一处可分叉点；</li>
 *   <li>副本一多，就<b>没人说得清</b>「这个域的上界到底是哪一份在管」。</li>
 * </ul>
 * ⇒ ⇒ <b>正确形态 = 共享层给唯一实现 ＋ 域侧只配参数</b>：
 * <pre>
 *   共享层（唯一实现）: service/common/.../protection/GoStyleOutboundDeadlineInterceptor.java
 *       ⇒ 带 devh 的 @GrpcGlobalClientInterceptor ⇒ 进全局客户端拦截器 registry
 *       ⇒ 装在【每个】出向 devh 调用上 ⇒ ⇒ 域侧【零代码改动】
 *   域侧（只配参数）: rag.protection.outbound-deadline.cap-millis = &lt;Go 该域的 Timeout&gt;
 *       ⇒ ⚠️ 默认 0 = 不启用（Go 的 2000ms 是【默认值】且各域可覆盖
 *       ⇒ ⇒ 写成全局默认会让 article（Go 侧 70000）比 Go 严 35 倍）⇒ ⇒ 必须逐域配
 * </pre>
 *
 * <h2>⚠️ 为什么是「<b>例外集</b>」而不是「必须为零」</h2>
 * <p>仓里<b>已经</b>有 <b>2 份</b>域侧私有副本（comment-rpc / favorite-rpc 各一份）
 * ⇒ ⇒ 它们是<b>既成的漂移面</b>，删除是<b>域侧</b>的事（不在本层写权面）
 * ⇒ ⇒ 本守门按<b>棘轮</b>形态写：<b>已知的那两份允许存在</b>，<b>新增第三份 ⇒ 红</b>
 * ⇒ ⇒ 等域侧把两份删掉（改用共享实现）后，本守门<b>自动收紧</b>（allowlist 可收缩）。
 *
 * <h2>🆕 判据 ④：内联形态（2026-10-31 · G4 lane · 补 javadoc 原自陈的第二个盲区）</h2>
 * <p>本类原 javadoc 自陈两个盲区：① 按<b>类名</b>识别 ⇒ 改了名的副本看不见；② <b>api 侧内联</b>形态不在检测面。
 * ⇒ ⇒ <b>盲区 ② 已在本轮补上</b>（判据 ④）⇒ ⇒ <b>盲区 ① 仍在</b>（见「诚实边界」）。
 *
 * <h3>④ 的判据怎么定的（⚠️ 不是「有没有 withDeadlineAfter」）</h3>
 * <p><b>先验过一条错判据</b>：G1 曾建议「域内不得出现 {@code withDeadlineAfter} 的<b>代码行</b>」⇒ ⇒
 * <b>原样搬过来会误伤</b> ⇒ ⇒ 因为<b>「纯预算传播」也是合法形态</b>（它只把<b>剩余预算</b>往下传，不自算上界）。
 * ⇒ ⇒ <b>真正的判据 = 「cap（自带上界）从哪来」</b>：
 * <table border="1">
 *   <caption>实测分辨力（本轮逐文件实测）</caption>
 *   <tr><th>形态</th><th>cap 来源</th><th>判定</th></tr>
 *   <tr><td>域侧私有副本</td><td>域侧<b>私有常量</b>（{@code XXX_TIMEOUT_MILLIS = 2000L}）或域侧<b>私有配置</b>
 *       （{@code properties.getXxx().getTimeout()}）</td><td>🔴 <b>违规</b></td></tr>
 *   <tr><td>共享层唯一实现</td><td>{@code getOutboundDeadline().getCapMillis()}（共享层参数）</td><td>✅ 合法</td></tr>
 *   <tr><td>纯预算传播</td><td><b>无 cap</b></td><td>✅ 合法</td></tr>
 * </table>
 * ⇒ ⇒ <b>为什么不能按「有没有 cap」判</b>：实测 <b>api 侧的 5 处「合法传播」（article-api / comment-api /
 * hot-api / like-api / task-api）其实也自算 cap</b>（{@code Math.min(DEFAULT_CALL_TIMEOUT_MILLIS, remaining)}）
 * ⇒ ⇒ 它们与「副本」在<b>形态上同形</b> ⇒ ⇒ 唯一能分开的是<b>cap 的来源</b> ⇒ ⇒
 * <b>⚠️ 但「来源」判据本身有代价</b>：那 5 处拿到的是<b>域侧私有常量</b> ⇒ ⇒ 它们<b>也会被 ④ 判红</b>
 * ⇒ ⇒ 本守门按<b>棘轮</b>形态处理（<b>点位级</b>例外集，见下）。
 *
 * <h3>④ 的例外集为什么是<b>点位级</b>（不是文件级）</h3>
 * <p><b>点位级 = 文件 ＋ 归一化代码片段</b> ⇒ ⇒ <b>同一个文件里<b>新增</b>的副本仍会红</b>
 * ⇒ ⇒ 而文件级登记 ⇒ ⇒ 那个文件从此是<b>永久白地</b> ⇒ ⇒ 守门照样绿、却已经失守
 * ⇒ ⇒ （本轮已实测过文件级登记被自己的变异测试证伪：注入「同文件不同形态」的调用点仍全绿）
 * ⇒ ⇒ <b>且例外必须带「失效检测」</b>：条目指向的东西被修好 ⇒ ⇒ 守门要红（提示可以收缩例外）
 * ⇒ ⇒ ⇒ <b>棘轮只能变紧，不能变松</b>。
 *
 * <h2>⚠️ 本守门的<b>已知盲区</b>（诚实边界）</h2>
 * <ul>
 *   <li><b>盲区 ① 仍在</b>：判据 ① 按<b>类名</b>识别副本 ⇒ ⇒ 一份<b>改了名</b>的同款实现不在它的视野内
 *       ⇒ ⇒ 那类要靠 review 纪律（本仓已有 {@code GoCitationAnchorGateTest} 之类的源级守门互补）；</li>
 *   <li><b>判据 ④ 的固有边界</b>：它按「cap 的<b>来源标识符</b>」判 ⇒ ⇒
 *       一份副本若<b>刻意伪装</b>成读共享层参数、实际用自己的常量 ⇒ ⇒ 逃得过 ⇒ ⇒
 *       <b>静态形态门挡不住「刻意伪装」</b> ⇒ ⇒ 那一面只能靠 §3.3 的运行期证据（真进程读出实际生效的上界）。</li>
 * </ul>
 */
class GoStyleClientCallTimeoutSingleSourceGateTest {

    /**
     * <b>已知的域侧私有副本</b>（棘轮例外集）—— 允许存在，但**只允许这些**。
     *
     * <p>⚠️ 这两条是<b>漂移面</b>（不是模板）⇒ ⇒ 正确终态是<b>域侧删掉它们、改用共享实现</b>
     * ⇒ ⇒ 删掉之后本列表应当收缩（而不是新增）。
     */
    private static final Set<String> KNOWN_DOMAIN_SIDE_COPIES = Set.of();

    /** 共享层的<b>唯一实现</b>（域侧只配参数 ⇒ 落点就是这个拦截器）。 */
    private static final String SHARED_IMPLEMENTATION =
            "service/common/src/main/java/com/rag/common/protection/GoStyleOutboundDeadlineInterceptor.java";

    /** 副本的类名（按<b>文件名</b>识别 ⇒ 保守、可解释）。 */
    private static final String COPY_CLASS_FILE_NAME = "GoStyleClientCallTimeout.java";

    // ────────────────────────────────────────────────────────────────────────────
    // 🆕 判据 ④ · 内联形态（2026-10-31 · G4 lane）
    // ────────────────────────────────────────────────────────────────────────────

    /**
     * <b>内联形态</b>的域侧私有 cap 例外集 —— <b>点位级</b>（文件 ＋ 归一化代码片段）。
     *
     * <p>⚠️ <b>为什么是点位级</b>：文件级登记 ⇒ ⇒ 同文件<b>新增</b>的副本抓不到 ⇒ ⇒ 永久白地
     * ⇒ ⇒ 点位级让「同文件新增」仍然红。
     * <p>⚠️ <b>键的构造</b> = <code>相对路径</code> ＋ <code>§</code> ＋ <code>归一化的调用点文本</code>
     * ⇒ ⇒ 归一化 = 去空白 ⇒ ⇒ 换行/缩进差异不算新点位（否则域侧一格式化就假红）。
     * <p>⚠️ <b>这些不是模板，是漂移面</b> ⇒ ⇒ 终态 = 域侧删掉、改用共享层实现 ⇒ ⇒ 例外集应当<b>收缩</b>。
     */
    private static final Set<String> KNOWN_INLINE_CAP_CALL_SITES = Set.of(
            // ── G1 的 88c206ce 落码引入的三份私有副本（follow-rpc / user-api / admin-api）──
            "service/follow/follow-rpc/src/main/java/com/rag/follow/follow/rpc/client/"
                    + "UserRpcClient.java:withDeadlineAfter(capNanos, java.util.concurrent.TimeUnit.NANOSECONDS);",
            "service/users/user/user-api/src/main/java/com/rag/users/user/api/client/"
                    + "UserRpcClient.java:withDeadlineAfter(1L, java.util.concurrent.TimeUnit.NANOSECONDS);",
            "service/users/user/user-api/src/main/java/com/rag/users/user/api/client/"
                    + "UserRpcClient.java:withDeadlineAfter(Math.min(remainingNanos, capNanos), java.util.concurrent.TimeUnit.NANOSECONDS);",
            "service/users/admin/admin-api/src/main/java/com/rag/users/admin/api/client/"
                    + "AdminRpcClient.java:withDeadlineAfter(1L, java.util.concurrent.TimeUnit.NANOSECONDS);",
            "service/users/admin/admin-api/src/main/java/com/rag/users/admin/api/client/"
                    + "AdminRpcClient.java:withDeadlineAfter(Math.min(remainingNanos, capNanos), java.util.concurrent.TimeUnit.NANOSECONDS);",
            // ── api 侧 5 处「合法传播但自带域侧私有 cap」（见 ④ 的判据说明）──
            "service/article/article-api/src/main/java/com/rag/article/article/api/client/"
                    + "UserRpcClient.java:withDeadlineAfter(effective, TimeUnit.MILLISECONDS);",
            "service/comment/comment-api/src/main/java/com/rag/comment/comment/api/client/"
                    + "CommentRpcClient.java:withDeadlineAfter(Math.max(1L, budget), TimeUnit.MILLISECONDS);",
            "service/hot/hot-api/src/main/java/com/rag/hot/hot/api/client/"
                    + "GoStyleGrpcCallDeadline.java:withDeadlineAfter(Math.max(1L, budget), TimeUnit.MILLISECONDS);",
            "service/like/like-api/src/main/java/com/rag/like/like/api/client/"
                    + "LikeRpcClient.java:withDeadlineAfter(effectiveNanos, TimeUnit.NANOSECONDS);",
            "service/task/task-api/src/main/java/com/rag/task/task/api/client/"
                    + "TaskRpcClient.java:withDeadlineAfter(Math.max(1L, budget), TimeUnit.MILLISECONDS);",
            "service/favorite/favorite-api/src/main/java/com/rag/favorite/favorite/api/client/"
                    + "FavoriteRpcClient.java:withDeadlineAfter(1L, TimeUnit.NANOSECONDS);",
            "service/favorite/favorite-api/src/main/java/com/rag/favorite/favorite/api/client/"
                    + "FavoriteRpcClient.java:withDeadlineAfter(Math.min(remainingNanos, capNanos), TimeUnit.NANOSECONDS);",
            "service/follow/follow-api/src/main/java/com/rag/follow/follow/api/client/"
                    + "FollowRpcClient.java:withDeadlineAfter(1L, java.util.concurrent.TimeUnit.NANOSECONDS);",
            "service/follow/follow-api/src/main/java/com/rag/follow/follow/api/client/"
                    + "FollowRpcClient.java:withDeadlineAfter(Math.min(remainingNanos, capNanos), java.util.concurrent.TimeUnit.NANOSECONDS);");

    /**
     * <b>域侧私有 cap 的两种来源形态</b>（代码态 ⇒ 注释与字符串字面量不算）。
     *
     * <p>① <b>域侧私有常量</b>：形如 {@code XXX_TIMEOUT_MILLIS = 2000L} 的静态常量；
     * ② <b>域侧私有配置</b>：形如 {@code properties.getXxx().getTimeout()} 的域侧配置读取。
     */
    private static final Pattern PRIVATE_CAP_CONSTANT = Pattern.compile(
            "\\b[A-Z][A-Z0-9_]*(?:TIMEOUT|CAP)[A-Z0-9_]*\\b");

    private static final Pattern PRIVATE_CAP_CONFIG = Pattern.compile(
            "properties\\s*\\.\\s*get[A-Za-z0-9_]*\\(\\s*\\)\\s*\\.\\s*getTimeout\\s*\\(");

    /** 共享层参数的读取形态（⇒ ⇒ 它<b>是</b>「域侧只配参数」的合法形态）。 */
    private static final Pattern SHARED_CAP_PARAM = Pattern.compile(
            "getOutboundDeadline\\s*\\(\\s*\\)\\s*\\.\\s*getCapMillis\\s*\\(");

    /** 域侧调用点：{@code withDeadlineAfter(...)}（代码态）。 */
    private static final Pattern DEADLINE_CALL = Pattern.compile(
            "\\bwithDeadlineAfter\\s*\\([^;]*?\\)\\s*;");

    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("① 域侧不得出现第三份「客户端硬上界」私有副本（棘轮：已知 0 份允许存在 —— 两份副本已于 G3 第十一轮删净；新增即红）")
    void noThirdDomainSideCopy() {
        List<String> copies = domainSideCopies();
        List<String> unexpected = copies.stream()
                .filter(p -> !KNOWN_DOMAIN_SIDE_COPIES.contains(p))
                .collect(Collectors.toList());

        assertThat(unexpected)
                .as("🔴 域侧又出现了新的「客户端硬上界」私有副本 ⇒ ⇒ **副本数 = 漂移面** ⇒ ⇒ "
                        + "正确形态 = **共享层给唯一实现**（`%s`）**＋ 域侧只配参数**"
                        + "（`rag.protection.outbound-deadline.cap-millis`）⇒ ⇒ "
                        + "请改成配参数，别再复制一份逻辑", SHARED_IMPLEMENTATION)
                .isEmpty();

        // 允许存在的两份也要**如实可见**（⇒ 域侧删掉后这个列表会变短 ⇒ 棘轮可收缩）
        assertThat(copies)
                .as("域侧私有副本的现状（已知 %d 份）⇒ ⇒ 终态应当是 0 份（域侧改用共享实现）",
                        KNOWN_DOMAIN_SIDE_COPIES.size())
                .containsAll(KNOWN_DOMAIN_SIDE_COPIES.stream()
                        .filter(p -> Files.isReadable(repoRoot().resolve(p)))
                        .collect(Collectors.toList()));
    }

    @Test
    @DisplayName("② 共享层的唯一实现必须在位（否则「域侧只配参数」这条路没有落点）")
    void sharedImplementationExists() {
        Path shared = repoRoot().resolve(SHARED_IMPLEMENTATION);

        assertThat(Files.isReadable(shared))
                .as("🔴 共享层的唯一实现不见了 ⇒ ⇒ 域侧「只配参数」就没有落点 ⇒ ⇒ "
                        + "要么它被删了（那域侧副本就是唯一实现 ⇒ 副本数又回到漂移面）"
                        + "⇒ ⇒ 要么它被搬走了 ⇒ ⇒ 两者都不允许")
                .isTrue();
    }

    @Test
    @DisplayName("③ 共享实现必须是【全局客户端拦截器】且真的读 `capMillis`（不是另一份形态）")
    void sharedImplementationIsGlobalClientInterceptorReadingCap() {
        List<String> code = codeLines(read(repoRoot().resolve(SHARED_IMPLEMENTATION)));

        // ⚠️ **只认代码行，不认注释行** —— 该类的 javadoc 里也出现过 `@GrpcGlobalClientInterceptor`
        //    ⇒ ⇒ 若用 `src.contains(...)` ⇒ 拿掉注解后**仍然命中 javadoc 那一句** ⇒ ⇒ 判据恒绿
        //    ⇒ ⇒ 这是本轮变异实测抓出来的（见台账「弱判据」那条）。
        assertThat(code)
                .as("🔴 共享实现必须带 devh 的 `@GrpcGlobalClientInterceptor` ⇒ ⇒ 才会装到"
                        + "【每个】出向 devh 调用上（Go 侧同形：`zrpc/client.go:70-71`）")
                .contains("@GrpcGlobalClientInterceptor");
        assertThat(code)
                .as("🔴 共享实现必须真的读 `GoStyleProtectionProperties.OutboundDeadline#getCapMillis`"
                        + " ⇒ ⇒ 否则「域侧只配参数」配了也不生效（AGENTS.md §3.3.1）")
                .anyMatch(l -> l.contains("getOutboundDeadline().getCapMillis()"));
        assertThat(code)
                .as("🔴 cap 必须按 **min** 语义并入（Go 的 `context.WithTimeout` 取更早者）")
                .anyMatch(l -> l.contains(
                        "Math.min(budgetNanos, TimeUnit.MILLISECONDS.toNanos(capMillis))"));
    }

    @Test
    @DisplayName("④ 域侧不得新造「内联形态」的私有 cap（点位级棘轮：已知点位允许存在，新增点位即红）")
    void noNewInlineDomainSideCap() {
        Map<String, String> actual = inlineDomainSideCapCallSites();
        Set<String> known = KNOWN_INLINE_CAP_CALL_SITES;

        List<String> unexpected = actual.keySet().stream()
                .filter(k -> !known.contains(k))
                .sorted()
                .collect(Collectors.toList());

        assertThat(unexpected)
                .as("🔴 域侧又出现了新的「内联形态」私有 cap（自带上界却没走共享层参数）⇒ ⇒ "
                        + "**副本数 = 漂移面** ⇒ ⇒ 正确形态 = 共享层给唯一实现（`%s`）＋ 域侧只配参数"
                        + "（`rag.protection.outbound-deadline.cap-millis`）⇒ ⇒ "
                        + "请改成配参数，别再复制一份封顶逻辑", SHARED_IMPLEMENTATION)
                .isEmpty();

        // ⚠️ **例外失效检测**（棘轮只能变紧）：已登记的点位若被修好（不再出现）
        //    ⇒ ⇒ 守门要红 ⇒ ⇒ 提示「这条例外可以剪了」
        List<String> expired = known.stream()
                .filter(k -> !actual.containsKey(k))
                .sorted()
                .collect(Collectors.toList());

        assertThat(expired)
                .as("🟢 **例外已失效**（下面这些点位已被修好 ⇒ 不再是漂移面）⇒ ⇒ "
                        + "请把对应条目从 `KNOWN_INLINE_CAP_CALL_SITES` 里剪掉 ⇒ ⇒ "
                        + "**棘轮只能变紧，不能变松** ⇒ ⇒ 剪掉后本守门自动收紧")
                .isEmpty();
    }

    // ────────────────────────────────────────────────────────────────────────────
    // 工具
    // ────────────────────────────────────────────────────────────────────────────

    /**
     * 仓内所有<b>域侧</b>的「<b>内联形态</b>私有 cap」调用点。
     *
     * <p>⇒ 返回 <code>{点位键 → 相对路径}</code>；点位键 = <code>相对路径§归一化调用点文本</code>。
     * <p>⚠️ <b>判据的三个必要条件（缺一即不判红）</b>：
     * <ol>
     *   <li>代码态出现 {@code withDeadlineAfter(...)}（<b>剥注释与字符串字面量</b>，保留换行以免行号失真）；</li>
     *   <li>该文件<b>自带 cap</b>：出现<b>域侧私有常量</b>（{@code XXX_TIMEOUT_MILLIS}）或<b>域侧私有配置</b>
     *       （{@code properties.getXxx().getTimeout()}）；</li>
     *   <li><b>不是</b>读共享层参数（{@code getOutboundDeadline().getCapMillis()}）—— 那种是合法形态。</li>
     * </ol>
     */
    private static Map<String, String> inlineDomainSideCapCallSites() {
        Path root = repoRoot();
        Path service = root.resolve("service");
        Map<String, String> out = new LinkedHashMap<>();
        if (!Files.isDirectory(service)) {
            return out;
        }
        try (Stream<Path> walk = Files.walk(service)) {
            List<Path> files = walk.filter(p -> p.toString().contains("/src/main/java/"))
                    .filter(p -> p.getFileName() != null && p.getFileName().toString().endsWith(".java"))
                    .sorted()
                    .collect(Collectors.toList());
            for (Path p : files) {
                String rel = root.relativize(p).toString().replace('\\', '/');
                // 共享层（common / users-common）不是「域侧」⇒ 排除
                if (rel.startsWith("service/common/") || rel.startsWith("service/users/users-common/")) {
                    continue;
                }
                String code = stripCommentsAndStrings(read(p));
                if (!code.contains("withDeadlineAfter")) {
                    continue;
                }
                // 条件②：自带 cap？
                boolean privateCap = PRIVATE_CAP_CONSTANT.matcher(code).find()
                        || PRIVATE_CAP_CONFIG.matcher(code).find();
                if (!privateCap) {
                    continue;
                }
                // 条件③：读共享层参数 ⇒ 合法形态
                if (SHARED_CAP_PARAM.matcher(code).find()) {
                    continue;
                }
                // 收集调用点（归一化：去所有空白 ⇒ 换行/缩进差异不算新点位）
                // ⚠️ 键 = 相对路径 + ':' + 归一化调用点文本
                //    ⇒ ⇒ **同文件里两处相同文本会碰撞** ⇒ ⇒ 但那两处**是同一个副本的两条分支**
                //    ⇒ ⇒ 碰撞不影响判据（任一存在即命中）⇒ ⇒ 故不把行号入键（否则域侧一改行号就假红）
                Matcher m = DEADLINE_CALL.matcher(code);
                while (m.find()) {
                    String norm = m.group().replaceAll("\\s+", " ");
                    out.put(rel + ":" + norm, rel);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("扫不到 service/** ⇒ 守门判据本身失效", e);
        }
        return out;
    }

    /**
     * <b>词法级</b>剥离：注释与字符串字面量 → 空格，<b>但保留换行</b>。
     *
     * <p>⚠️ 保留换行是必须的 ⇒ ⇒ 否则行号失真（下游会按行号定位）。
     * <p>⚠️ 本轮新增：判据 ④ 必须看<b>代码态</b> ⇒ ⇒ 否则
     * 「javadoc 里写了 {@code withDeadlineAfter}」就会假命中。
     */
    private static String stripCommentsAndStrings(String src) {
        StringBuilder out = new StringBuilder(src.length());
        int i = 0;
        int n = src.length();
        while (i < n) {
            char c = src.charAt(i);
            if (c == '"' || c == '\'') {
                char q = c;
                out.append(c);
                i++;
                while (i < n && src.charAt(i) != q) {
                    if (src.charAt(i) == '\\') {
                        out.append(' ');
                        i++;
                    }
                    if (i < n) {
                        out.append(src.charAt(i) == '\n' ? '\n' : ' ');
                        i++;
                    }
                }
                if (i < n) {
                    out.append(q);
                    i++;
                }
                continue;
            }
            if (src.startsWith("//", i)) {
                while (i < n && src.charAt(i) != '\n') {
                    out.append(' ');
                    i++;
                }
                continue;
            }
            if (src.startsWith("/*", i)) {
                while (i < n && !src.startsWith("*/", i)) {
                    out.append(src.charAt(i) == '\n' ? '\n' : ' ');
                    i++;
                }
                if (i < n) {
                    out.append("  ");
                    i += 2;
                }
                continue;
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    /**
     * 只留<b>代码行</b>（去注释、去空行）—— 判据只认「真代码」，不认 javadoc 里提到的那句话。
     */
    private static List<String> codeLines(String src) {
        List<String> out = new ArrayList<>();
        for (String raw : src.split("\\R")) {
            String l = raw.strip();
            if (l.isEmpty() || l.startsWith("*") || l.startsWith("//") || l.startsWith("/*")) {
                continue;
            }
            out.add(l);
        }
        return out;
    }

    /**
     * 仓内所有<b>域侧</b>的 {@code GoStyleClientCallTimeout} 副本（仓根相对路径）。
     *
     * <p>⚠️ <b>「域侧」= 不在 {@code service/common} 与 {@code service/users/users-common} 下</b>
     * ⇒ ⇒ 那两处是<b>共享层</b>（G4 写权面），不是「域侧副本」。
     */
    private static List<String> domainSideCopies() {
        Path root = repoRoot();
        Path service = root.resolve("service");
        if (!Files.isDirectory(service)) {
            return List.of();
        }
        Set<String> out = new TreeSet<>();
        try (Stream<Path> walk = Files.walk(service)) {
            walk.filter(p -> p.getFileName() != null
                            && COPY_CLASS_FILE_NAME.equals(p.getFileName().toString()))
                    .filter(p -> p.toString().contains("/src/main/java/"))
                    .forEach(p -> {
                        String rel = root.relativize(p).toString().replace('\\', '/');
                        if (!rel.startsWith("service/common/")
                                && !rel.startsWith("service/users/users-common/")) {
                            out.add(rel);
                        }
                    });
        } catch (IOException e) {
            throw new IllegalStateException("扫不到 service/** ⇒ 守门判据本身失效", e);
        }
        return new ArrayList<>(out);
    }

    /** 仓根：向上找带 {@code service/common/pom.xml} 的那一层（不依赖 surefire 的工作目录）。 */
    private static Path repoRoot() {
        Path base = Paths.get("").toAbsolutePath();
        for (int up = 0; up < 6 && base != null; up++) {
            if (Files.isReadable(base.resolve("service/common/pom.xml"))) {
                return base;
            }
            base = base.getParent();
        }
        throw new IllegalStateException("找不到仓根（向上 6 层都没见到 service/common/pom.xml）");
    }

    private static String read(Path p) {
        try {
            return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("源码不可读：" + p, e);
        }
    }
}
