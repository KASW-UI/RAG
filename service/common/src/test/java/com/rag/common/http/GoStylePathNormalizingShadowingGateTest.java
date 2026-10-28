package com.rag.common.http;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>守门：域内不得再声明 {@code GoStylePathNormalizingFilter}</b> —— 因为那会<b>遮蔽</b>共享层那份，
 * 且被遮蔽的<b>还是旧实现</b>（与 Go 的 {@code path.Clean} 模型不同）。
 *
 * <h2>🔴 为什么这条比「一般遮蔽」更严重：不只是「静默失效」，是<b>错版接管</b></h2>
 * <p>共享层那份由 {@link GoStylePathNormalizingAutoConfiguration} 注册，条件是
 * {@code @ConditionalOnMissingBean(name = "goStylePathNormalizingFilter")} ⇒ ⇒
 * <b>域内只要还有一份同名 Bean，共享层这份就整个不注册</b> ⇒ ⇒
 * <b>该域的路径归一由域内副本独干</b>。
 * <p>⇒ 而域内副本（article-api 的旧版）用的是<b>「路径段列表」模型</b>，
 * 共享层那份才是 Go {@code path.Clean} 的<b>「写入位置 + 回退」模型</b>
 * （差异见 {@code GoStylePathNormalizingFilter} 类 javadoc 的对照表：
 * 根路径 {@code "/"}、{@code ".."} 段的回退边界两处都不同）⇒ ⇒
 * <b>域内副本在位 ⇒ 该域的路径归一与 Go 不一致</b> ⇒ ⇒ ⇒ <b>这是行为漂移，不是「重复注册」</b>。
 *
 * <h2>⚠️ 为什么要按「源文件」扫，而不是「运行时查 Bean」</h2>
 * <ul>
 *   <li>运行时查 Bean 需要<b>起每个域的应用上下文</b> ⇒ ⇒ 那是 10 个域的装配面（重、且各域写权不同）
 *       ⇒ ⇒ 而且<b>不覆盖</b>「那个域根本没被测到」的情况；</li>
 *   <li>源文件扫 ⇒ ⇒ <b>零启动成本</b>、<b>全覆盖</b>（所有域的<b>生产</b>源码都在仓里）⇒ ⇒
 *       而且判据<b>可被人眼复核</b>（扫出来的是文件名，不是计数）。</li>
 * </ul>
 * ⇒ ⇒ 只扫<b>生产</b>源码（{@code src/main/java}）⇒ ⇒ 域侧<b>测试</b>里出现该类名是允许的。
 *
 * <h2>🔴 断言自检（§3.3 的纪律：<b>要断言「断言会不会执行」</b>）</h2>
 * <ol>
 *   <li><b>①</b>：白名单之外<b>零</b>声明 ⇒ ⇒ 这是「闸门有效」的主断言；</li>
 *   <li><b>②</b>：<b>在临时目录</b>造一份假副本 ⇒ ⇒ 扫描器<b>必须</b>报出它
 *       ⇒ ⇒ 证明扫描器<b>不是空转</b>（否则①可能是「什么都没扫到」的假绿）；</li>
 *   <li><b>③</b>：<b>真实仓</b>上的自检（两个锚点，<b>都不依赖域内副本是否已被删</b>）：
 *       <ul>
 *         <li>(a) <b>关掉共享层排除</b> ⇒ ⇒ 必须报出<b>共享层自己那份</b>
 *             ⇒ ⇒ 证明扫描器在<b>真实仓</b>里确实能看见声明；</li>
 *         <li>(b) <b>白名单中仍然存在的每一份</b> ⇒ ⇒ 必须都能被扫到
 *             ⇒ ⇒ 白名单项被删（收编完成）后这一半自动为空 ⇒ ⇒ <b>不会因收编完成而变红</b>。</li>
 *       </ul></li>
 *   <li><b>④</b>：<b>遮蔽机制本身必须在</b> —— 共享层的注册点必须仍带
 *       {@code @ConditionalOnMissingBean(name = "goStylePathNormalizingFilter")}
 *       ⇒ ⇒ 否则本守门的整个前提（域内副本会遮蔽共享层）<b>不成立</b>
 *       ⇒ ⇒ ⇒ 有人把条件去掉时，必须<b>红</b>（那时域内副本会<b>双注册</b>
 *       ⇒ ⇒ 而 Spring 的 {@code allow-bean-definition-overriding} 默认关闭 ⇒ <b>启动直接失败</b>）。</li>
 * </ol>
 *
 * <h2>白名单（<b>只登记、不放过</b>）</h2>
 * <p>当前登记：{@code article-api} 那份（共享层收编前就存在的<b>旧实现</b>）。
 * ⇒ ⇒ <b>它是收编前的遗留，不是「允许」</b> ⇒ ⇒ <b>复开/移除触发条件</b>：
 * G3 把 article-api 的域内副本删干净后 ⇒ ⇒ <b>本白名单项即可从本文件移除</b>（本层写权）。
 * ⇒ ⇒ <b>白名单项失效（文件已不存在）不会让本守门变红</b>，但会出现在断言消息里提示清理。
 *
 * <h2>Go 出处</h2>
 * <pre>
 *   Go 标准库 path/path.go 的 Clean（写入位置 = out.length() 的 lazybuf 模型）
 *     ⇒ 共享层 GoStylePathNormalizingFilter.goPathClean 是它的逐字移植
 *     ⇒ 域内旧副本用「路径段列表」建模 ⇒ 两处结果不同（"/" 与 ".." 回退边界）
 *   Go 网关侧读取路径后即以 path.Clean 归一 ⇒ ⇒ 域内旧版在位 ⇒ 归一结果与 Go 不一致
 * </pre>
 */
@DisplayName("🔴 域内不得再声明路径归一 Filter（否则遮蔽共享层那份，且接管的是旧实现）")
class GoStylePathNormalizingShadowingGateTest {

    /**
     * 域内路径归一副本的**白名单**（收编前的遗留）⇒ **键 = 仓库相对路径**。
     *
     * <p>⚠️ **每加一项都必须是「已登记、待删除」的遗留** ⇒ ⇒ **不得**把它变成「长期允许」。
     */
    private static final Map<String, String> LEGACY_DOMAIN_COPIES = Map.of(
            "service/article/article-api/src/main/java/com/rag/article/article/api/config/GoStylePathNormalizingFilter.java",
            "共享层收编前的旧实现（按「路径段列表」建模，与 Go path.Clean 的「写入位置+回退」模型有两处差异）"
                    + "；article 域的写权 ⇒ 由 G3 删除（global 台账 §4.10.67）");

    /** 域内声明这个类 ⇒ 就会遮蔽共享层那份（同名 Bean）。 */
    private static final String FILTER_CLASS = "GoStylePathNormalizingFilter";

    /** 判「真的声明了这个类」⇒ 光提一句名字不算（注释/import 不算）。 */
    private static final Pattern CLASS_DECLARATION =
            Pattern.compile("\\bclass\\s+" + FILTER_CLASS + "\\b");

    /** 共享层自己的位置 ⇒ 排除在外（它**就是**那个「一份」）。 */
    private static final String SHARED_LAYER_DIR = "service/common";

    /** 共享层那份的相对路径 ⇒ 断言③(a) 的锚点（它永远存在 ⇒ 恒有断言）。 */
    private static final String SHARED_LAYER_DECLARATION =
            "service/common/src/main/java/com/rag/common/http/GoStylePathNormalizingFilter.java";

    /** 共享层注册点的相对路径 ⇒ 断言④ 的锚点。 */
    private static final String SHARED_LAYER_REGISTRATION =
            "service/common/src/main/java/com/rag/common/http/GoStylePathNormalizingAutoConfiguration.java";

    /** 断言④ 要找的那行条件（按 Bean 名判 ⇒ 因为两份的**类**不同，按类型判匹配不上）。 */
    private static final String REQUIRED_CONDITION =
            "@ConditionalOnMissingBean(name = \"goStylePathNormalizingFilter\")";

    // ==================================================================
    // ① 主断言：白名单之外零声明
    // ==================================================================

    @Test
    @DisplayName("🔴 除白名单那份遗留外，域内**零**路径归一 Filter 声明（否则遮蔽共享层那份）")
    void noDomainSideShadowingCopyExists() {
        Path root = repoRoot();
        List<String> found = scanForDomainCopies(root, LEGACY_DOMAIN_COPIES.keySet(), true);

        assertTrue(found.isEmpty(),
                "域内出现了遮蔽共享层的路径归一 Filter ⇒ ⇒ 共享层那份"
                        + "（GoStylePathNormalizingFilter / GoStylePathNormalizingAutoConfiguration）在这个域"
                        + "**不会注册** ⇒ ⇒ 该域的路径归一由**域内副本独干**"
                        + " ⇒ ⇒ 而域内副本是**旧实现**（按「路径段列表」建模）"
                        + " ⇒ ⇒ 与 Go 的 path.Clean（写入位置 + 回退模型）**结果不同** ⇒ ⇒ **行为漂移**。"
                        + "\n处理方式 = **删掉域内副本**（共享层那份已是 Go 逐字移植 ⇒ 纯删除）。"
                        + "\n命中文件：\n  " + String.join("\n  ", found)
                        + "\n（若该文件确属「已登记待删的遗留」⇒ 请把它登记进本类的 LEGACY_DOMAIN_COPIES，"
                        + "并写明删除的触发条件；**不要**直接删掉白名单项。）"
                        + staleWhitelistHint(root));
    }

    // ==================================================================
    // ② 判据自检：扫描器不是空转
    // ==================================================================

    @Test
    @DisplayName("🔴 判据自检：临时目录里造一份假副本 ⇒ 扫描器**必须**报出它")
    void scannerActuallyReportsACopy() throws IOException {
        Path fakeRoot = Files.createTempDirectory("g4-pathnorm-shadowing-selftest");
        try {
            Path fake = fakeRoot.resolve(
                    "service/fake-domain/src/main/java/com/rag/fake/config/FakePathNormalizingFilter.java");
            Files.createDirectories(fake.getParent());
            Files.writeString(fake,
                    "package com.rag.fake.config;\n"
                            + "public class FakePathNormalizingFilter { }\n"
                            + "class GoStylePathNormalizingFilter { }\n",
                    StandardCharsets.UTF_8);

            List<String> found = scanForDomainCopies(fakeRoot, Set.of(), true);

            assertEquals(List.of("service/fake-domain/src/main/java/com/rag/fake/config/FakePathNormalizingFilter.java"),
                    found,
                    "扫描器必须在临时目录里报出那份假副本 ⇒ ⇒ 否则主断言①可能是「什么都没扫到」的**假绿**");
        } finally {
            deleteRecursively(fakeRoot);
        }
    }

    // ==================================================================
    // ③ 真实仓自检：①的绿不是「扫不到域内代码」
    // ==================================================================

    @Test
    @DisplayName("🔴 真实仓自检：关掉共享层排除 ⇒ 必须扫到共享层那份（锚点恒存在）")
    void scannerSeesTheRealRepository() {
        Path root = repoRoot();

        // (a) 关掉共享层排除 ⇒ 必须看见共享层自己那份声明
        List<String> withoutSharedExclusion = scanForDomainCopies(root, Set.of(), false);
        assertTrue(withoutSharedExclusion.contains(SHARED_LAYER_DECLARATION),
                "关掉共享层排除后必须能扫到共享层那份真实存在的声明（" + SHARED_LAYER_DECLARATION + "）"
                        + " ⇒ ⇒ 否则说明扫描器在**真实仓**里根本扫不到声明 ⇒ ⇒ 断言①是**假绿**。"
                        + "\n实测扫到：" + withoutSharedExclusion);

        // (b) 白名单里**仍然存在**的每一份 ⇒ 必须都能被扫到（白名单项被删后本半条自动为空 ⇒ 不会变红）
        Set<String> stillExisting = stillExistingWhitelistEntries(root);
        List<String> all = scanForDomainCopies(root, Set.of(), false);
        for (String rel : stillExisting) {
            assertTrue(all.contains(rel),
                    "白名单里登记的「仍存在」的遗留副本必须能被扫描器看到 ⇒ ⇒ 实际扫到：" + all);
        }
    }

    // ==================================================================
    // ④ 遮蔽机制本身必须在（否则本守门的前提不成立）
    // ==================================================================

    @Test
    @DisplayName("🔴 遮蔽机制自检：共享层注册点必须仍带 @ConditionalOnMissingBean(name=…)")
    void theShadowingMechanismItselfIsStillInPlace() {
        Path root = repoRoot();
        String registration = readOrFail(root.resolve(SHARED_LAYER_REGISTRATION));

        assertTrue(registration.contains(REQUIRED_CONDITION),
                "共享层的路径归一注册点**必须**带 " + REQUIRED_CONDITION
                        + " ⇒ ⇒ 否则「域内副本会遮蔽共享层」这个前提**不成立**"
                        + " ⇒ ⇒ 本守门的①会**失去意义**（域内副本与共享层那份会**双注册**"
                        + " ⇒ ⇒ 而 allow-bean-definition-overriding 默认关闭 ⇒ ⇒ 上下文**直接起不来**）。"
                        + "\n⇒ ⇒ 若确实要改成「不遮蔽」的设计，**必须同时**把本守门①改成"
                        + "「域内零声明」（那时两处会冲突）⇒ ⇒ 改这里之前先想清楚。");
    }

    // ==================================================================
    // helpers
    // ==================================================================

    /**
     * 扫出「域内声明了路径归一 Filter 这个类」的文件（生产源码）。
     *
     * @param repoRoot           仓库根
     * @param allowed            允许存在的相对路径（白名单）
     * @param excludeSharedLayer 是否排除共享层自己那份
     * @return 相对路径列表（已排除白名单与共享层）
     */
    private static List<String> scanForDomainCopies(Path repoRoot, Set<String> allowed, boolean excludeSharedLayer) {
        List<String> hits = new ArrayList<>();
        Path serviceDir = repoRoot.resolve("service");
        if (!Files.isDirectory(serviceDir)) {
            hits.add("<no-service-dir: " + serviceDir + ">");
            return hits;
        }
        try (Stream<Path> stream = Files.walk(serviceDir, 10)) {
            for (Path p : (Iterable<Path>) stream.filter(Files::isDirectory)
                    .filter(d -> d.getFileName() != null && "src".equals(d.getFileName().toString()))::iterator) {
                // 只取 <module>/src/main/java 这一层（src 下面还有 test/resources）
                Path javaDir = p.resolve("main").resolve("java");
                if (!Files.isDirectory(javaDir)) {
                    continue;
                }
                // ⚠️ 这里**故意不**要求「src 的父目录必须有 pom.xml」：
                //    那是「变异 M2」的注入点（专门用来证明断言②不是假绿）⇒ ⇒ 实现层不得带这条约束。
                String rel = repoRoot.relativize(javaDir).toString();
                if (excludeSharedLayer
                        && (rel.equals(SHARED_LAYER_DIR) || rel.startsWith(SHARED_LAYER_DIR + "/"))) {
                    continue;
                }
                try (Stream<Path> files = Files.walk(javaDir)) {
                    for (Path f : (Iterable<Path>) files.filter(f -> f.toString().endsWith(".java"))::iterator) {
                        String content;
                        try {
                            content = Files.readString(f, StandardCharsets.UTF_8);
                        } catch (IOException e) {
                            continue;
                        }
                        // ⚠️ 判「真的声明了这个类」⇒ 光提一句名字不算（import / 注释 / javadoc 都不算）
                        if (!CLASS_DECLARATION.matcher(content).find()) {
                            continue;
                        }
                        String relFile = repoRoot.relativize(f).toString();
                        if (allowed.contains(relFile)) {
                            continue;
                        }
                        hits.add(relFile);
                    }
                }
            }
        } catch (IOException e) {
            // 扫不动 ⇒ 不能当成「干净」⇒ 用一条可读的失败项顶上去
            hits.add("<scan-failed: " + e.getMessage() + ">");
        }
        hits.sort(String::compareTo);
        return hits;
    }

    /** 白名单里登记、且**此刻仍然存在**的那些相对路径。 */
    private static Set<String> stillExistingWhitelistEntries(Path repoRoot) {
        Set<String> out = new LinkedHashSet<>();
        for (String rel : LEGACY_DOMAIN_COPIES.keySet()) {
            if (Files.isRegularFile(repoRoot.resolve(rel))) {
                out.add(rel);
            }
        }
        return out;
    }

    /** 白名单里已经失效（文件已不存在）的项 ⇒ 提示收编已完成、该清白名单了。 */
    private static String staleWhitelistHint(Path repoRoot) {
        Set<String> stale = new LinkedHashSet<>(LEGACY_DOMAIN_COPIES.keySet());
        stale.removeAll(stillExistingWhitelistEntries(repoRoot));
        if (stale.isEmpty()) {
            return "";
        }
        return "\n（提示：白名单里已失效的项 ⇒ " + stale
                + " ⇒ ⇒ 收编已完成 ⇒ 请把它们从 LEGACY_DOMAIN_COPIES 移除。）";
    }

    /** 读文件 ⇒ 读不到就**当场失败**（读不到 ≠ 内容为空）。 */
    private static String readOrFail(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("读不到 " + p + " ⇒ ⇒ 无法核「遮蔽机制是否还在」", e);
        }
    }

    /**
     * 仓库根目录 —— <b>从本类的 code source 向上找</b>（不依赖工作目录）。
     *
     * <p>⚠️ 不能用 {@code user.dir}：surefire 的工作目录是<b>模块目录</b>（{@code service/common}），
     * 而从 IDE 跑时又是仓库根 ⇒ ⇒ 两种布局都对 ⇒ ⇒ 必须走 code source。
     */
    private static Path repoRoot() {
        Path here = Path.of(GoStylePathNormalizingShadowingGateTest.class.getProtectionDomain()
                .getCodeSource().getLocation().getPath());
        for (Path p = here; p != null; p = p.getParent()) {
            if (Files.isDirectory(p.resolve("service")) && Files.isRegularFile(p.resolve("pom.xml"))) {
                return p;
            }
        }
        throw new IllegalStateException("找不到仓库根（从 " + here + "向上没有同时含 service/ 与 pom.xml 的目录）");
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(root)) {
            for (Path p : (Iterable<Path>) stream.sorted((a, b) -> b.getNameCount() - a.getNameCount())::iterator) {
                Files.deleteIfExists(p);
            }
        }
    }
}
