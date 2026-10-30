package com.rag.common.protection;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
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
 * <h2>⚠️ 本守门的<b>已知盲区</b>（诚实边界）</h2>
 * <ul>
 *   <li>它按<b>类名</b>识别副本 ⇒ ⇒ 一份<b>改了名</b>的同款实现不在它的视野内
 *       ⇒ ⇒ 那类要靠 review 纪律（本仓已有 {@code GoCitationAnchorGateTest} 之类的源级守门互补）；</li>
 *   <li><b>api 侧</b>另有<b>内联</b>形态的封顶（如 {@code like-api} / {@code favorite-api}
 *       在 client 类里直接算 min）⇒ ⇒ 它们<b>不</b>声明 {@code GoStyleClientCallTimeout}
 *       ⇒ ⇒ 不在本守门的检测面 ⇒ ⇒ 属<b>另一条轴</b>（P2-T03 / P2-T04 的 api 侧），本守门不假装覆盖它。</li>
 * </ul>
 */
class GoStyleClientCallTimeoutSingleSourceGateTest {

    /**
     * <b>已知的域侧私有副本</b>（棘轮例外集）—— 允许存在，但**只允许这些**。
     *
     * <p>⚠️ 这两条是<b>漂移面</b>（不是模板）⇒ ⇒ 正确终态是<b>域侧删掉它们、改用共享实现</b>
     * ⇒ ⇒ 删掉之后本列表应当收缩（而不是新增）。
     */
    private static final Set<String> KNOWN_DOMAIN_SIDE_COPIES = Set.of(
            "service/comment/comment-rpc/src/main/java/com/rag/comment/comment/rpc/client/"
                    + "GoStyleClientCallTimeout.java",
            "service/favorite/favorite-rpc/src/main/java/com/rag/favorite/favorite/rpc/client/"
                    + "GoStyleClientCallTimeout.java");

    /** 共享层的<b>唯一实现</b>（域侧只配参数 ⇒ 落点就是这个拦截器）。 */
    private static final String SHARED_IMPLEMENTATION =
            "service/common/src/main/java/com/rag/common/protection/GoStyleOutboundDeadlineInterceptor.java";

    /** 副本的类名（按<b>文件名</b>识别 ⇒ 保守、可解释）。 */
    private static final String COPY_CLASS_FILE_NAME = "GoStyleClientCallTimeout.java";

    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("① 域侧不得出现第三份「客户端硬上界」私有副本（棘轮：已知 2 份允许存在，新增即红）")
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

    // ────────────────────────────────────────────────────────────────────────────
    // 工具
    // ────────────────────────────────────────────────────────────────────────────

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
