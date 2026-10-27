package com.rag.common.redis;

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
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>守门：域内不得自己再声明 {@code LettuceClientOptionsBuilderCustomizer}</b>
 * —— 因为那会<b>遮蔽</b>共享层那份（不是「叠加」）。
 *
 * <h2>🔴 为什么这条要机器守（台账禁令拦不住人）</h2>
 * <p>{@link GoStyleRedisClientAutoConfiguration} 的 {@code @Bean} 带 {@code @ConditionalOnMissingBean}
 * ⇒ ⇒ <b>域内一旦已定义该类型的 bean，共享层那份根本不会被创建</b>
 * ⇒ ⇒ 容器里<b>只有一份</b> customizer，且是<b>域内那份</b>
 * ⇒ ⇒ 净效果：断连行为<b>碰巧</b>还是 {@code REJECT_COMMANDS}（两份逐字同值）⇒ <b>对外行为零变化</b>
 * ⇒ ⇒ <b>但代价 = 遮蔽</b>：共享层的<b>任何后续改动</b>（例如 E66 的 {@code timeoutOptions}）
 * 在<b>仍有域内副本的域</b>会<b>静默不生效</b>
 * ⇒ ⇒ ⇒ <b>域内副本必须删除，删除才是真正的收编</b>（见 global 台账 §4.10.24）。
 *
 * <h3>⇒ 那为什么不能只靠台账写「禁止新建域内副本」？</h3>
 * <ul>
 *   <li>台账禁止项只能<b>事后</b>被下一个 agent 读到 ⇒ ⇒ 而域侧 agent 的写权面<b>不包括</b>本文件
 *       ⇒ ⇒ 它「读到没读到」全凭自觉；</li>
 *   <li>更糟的是：域侧 agent <b>看不到</b>共享层的这条台账（各域各一份台账 ⇒ 零重叠 ⇒ 不自动送达）⇒ ⇒
 *       ⇒ 「域内新建一份更保险」这种直觉很容易被执行 ⇒ ⇒ ⇒ <b>必须有机器闸门</b>。</li>
 * </ul>
 * ⇒ ⇒ <b>本类就是那个闸门</b>：域内一旦出现第二处声明 ⇒ ⇒ <b>真构建变红</b> ⇒ ⇒ 删掉才能过。
 *
 * <h2>⚠️ 为什么要按「源文件」扫，而不是「运行时查 Bean」</h2>
 * <ul>
 *   <li>运行时查 Bean 需要<b>起每个域的应用上下文</b> ⇒ ⇒ 那是 10 个域的装配面（重、且各域写权不同）
 *       ⇒ ⇒ 而且<b>不覆盖</b>「那个域根本没被测到」的情况；</li>
 *   <li>源文件扫 ⇒ ⇒ <b>零启动成本</b>、<b>全覆盖</b>（所有域的<b>生产</b>源码都在仓里）⇒ ⇒
 *       ⇒ 而且判据<b>可被人眼复核</b>（扫出来的是文件名，不是计数）。</li>
 * </ul>
 * ⇒ ⇒ 只扫<b>生产</b>源码（{@code src/main/java}）⇒ ⇒ 域侧<b>测试</b>里引用该类型是允许的
 * （测试不产生生产 Bean）⇒ ⇒ 共享层自己的测试替身（{@code DomainLikeRedisDisconnectedBehaviorConfig}）也在允许范围内。
 *
 * <h2>🔴 断言自检（§3.3 的纪律：<b>要断言「断言会不会执行」</b>）</h2>
 * <ol>
 *   <li><b>①</b>：白名单之外<b>零</b>声明 ⇒ ⇒ 这是「闸门有效」的主断言；</li>
 *   <li><b>②</b>：<b>在临时目录</b>造一份假副本 ⇒ ⇒ 扫描器<b>必须</b>报出它
 *       ⇒ ⇒ 证明扫描器<b>不是空转</b>（否则①可能是「什么都没扫到」的假绿）；</li>
 *   <li><b>③</b>：<b>真实仓</b>上的自检（两个锚点，<b>都不依赖域内副本是否已被删</b>）：
 *       <ul>
 *         <li>(a) <b>关掉共享层排除</b> ⇒ ⇒ 必须报出<b>共享层自己那份</b>
 *             ⇒ ⇒ 证明扫描器在<b>真实仓</b>里确实能看见声明（不是只在临时目录里行）；</li>
 *         <li>(b) <b>白名单中仍然存在的每一份</b> ⇒ ⇒ 必须都能被扫到
 *             ⇒ ⇒ 白名单项被删（收编完成）后这一半自动为空 ⇒ ⇒ <b>不会因收编完成而变红</b>。</li>
 *       </ul></li>
 * </ol>
 *
 * <h2>白名单（<b>只登记、不放过</b>）</h2>
 * <p>当前登记：{@code user-api} 那份（2026-10-24 第九轮修 {@code DRIFT-USERAPI-014} 时落的原件）。
 * ⇒ ⇒ <b>它是收编前的遗留，不是「允许」</b> ⇒ ⇒ <b>复开/移除触发条件</b>：
 * G1 把域内副本删干净后 ⇒ ⇒ <b>本白名单项即可从本文件移除</b>（本层写权）。
 * ⇒ ⇒ <b>白名单项失效（文件已不存在）不会让本守门变红</b>，但会出现在断言消息里提示清理。
 *
 * <h2>Go 出处</h2>
 * <pre>
 *   go-zero@v1.10.2/core/stores/redis/redisclientmanager.go:14   maxRetries = 3
 *     ⇒ 连接被拒 / 被关时**立即失败**（不排队）⇒ ⇒ 这正是 REJECT_COMMANDS 要对齐的语义
 *   service/user/user/api/internal/middleware/checkblacklistmiddleware.go:35-36
 *     ⇒ err != nil ⇒ **放行**（fail-open）⇒ ⇒ 快速失败才进得了这段
 * </pre>
 */
@DisplayName("🔴 域内不得再声明 Lettuce 断连行为定制器（否则遮蔽共享层那份）")
class GoStyleRedisClientShadowingGateTest {

    /**
     * 域内 Redis 断连副本的**白名单**（收编前的遗留）⇒ **键 = 仓库相对路径**。
     *
     * <p>⚠️ **每加一项都必须是「已登记、待删除」的遗留** ⇒ ⇒ **不得**把它变成「长期允许」。
     */
    private static final Map<String, String> LEGACY_DOMAIN_COPIES = Map.of(
            "service/users/user/user-api/src/main/java/com/rag/users/user/api/config/RedisDisconnectedBehaviorConfig.java",
            "2026-10-24 第九轮修 DRIFT-USERAPI-014 落的原件；共享层收编后由 G1 删除（global 台账 §4.10.24）");

    /** 域内声明这个类型 ⇒ 就会遮蔽共享层那份。 */
    private static final String CUSTOMIZER_TYPE = "LettuceClientOptionsBuilderCustomizer";

    /** 共享层自己的位置 ⇒ 排除在外（它**就是**那个「一份」）。 */
    private static final String SHARED_LAYER_DIR = "service/common";

    /** 共享层那份的相对路径 ⇒ 断言③(a) 的锚点（它永远存在 ⇒ 恒有断言）。 */
    private static final String SHARED_LAYER_DECLARATION =
            "service/common/src/main/java/com/rag/common/redis/GoStyleRedisClientAutoConfiguration.java";

    // ==================================================================
    // ① 主断言：白名单之外零声明
    // ==================================================================

    @Test
    @DisplayName("🔴 除白名单那份遗留外，域内**零** Lettuce 断连行为定制器（否则遮蔽共享层）")
    void noDomainSideShadowingCopyExists() {
        Path root = repoRoot();
        List<String> found = scanForDomainCopies(root, LEGACY_DOMAIN_COPIES.keySet(), true);

        assertTrue(found.isEmpty(),
                "域内出现了遮蔽共享层的 Lettuce 断连行为定制器 ⇒ ⇒ 共享层那份"
                        + "（GoStyleRedisClientAutoConfiguration）在这个域**不会生效**"
                        + " ⇒ ⇒ 共享层的后续改动（例如 E66 的 timeoutOptions）会**静默失效**。"
                        + "\n处理方式 = **删掉域内副本**（共享层那份已存在且值相同 ⇒ 纯删除、零行为变化），"
                        + "**不要**在域内再写一份。"
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
        Path fakeRoot = Files.createTempDirectory("g4-shadowing-selftest");
        try {
            Path fake = fakeRoot.resolve("service/fake-domain/src/main/java/com/rag/fake/config/FakeRedisCopy.java");
            Files.createDirectories(fake.getParent());
            Files.writeString(fake,
                    "package com.rag.fake.config;\n"
                            + "import org.springframework.boot.data.redis.autoconfigure.LettuceClientOptionsBuilderCustomizer;\n"
                            + "public class FakeRedisCopy { }\n",
                    StandardCharsets.UTF_8);

            List<String> found = scanForDomainCopies(fakeRoot, Set.of(), true);

            assertEquals(List.of("service/fake-domain/src/main/java/com/rag/fake/config/FakeRedisCopy.java"), found,
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

        // (b) 白名单中**仍然存在**的每一份 ⇒ 必须都能被扫到（白名单项被删后本半条自动为空 ⇒ 不会变红）
        Set<String> stillExisting = stillExistingWhitelistEntries(root);
        List<String> all = scanForDomainCopies(root, Set.of(), false);
        for (String rel : stillExisting) {
            assertTrue(all.contains(rel),
                    "白名单里登记的「仍存在」的遗留副本必须能被扫描器看到 ⇒ ⇒ 实际扫到：" + all);
        }
    }

    // ==================================================================
    // helpers
    // ==================================================================

    /**
     * 扫出「域内声明了断连行为定制器」的文件（生产源码）。
     *
     * @param repoRoot          仓库根
     * @param allowed           允许存在的相对路径（白名单）
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
                        if (!content.contains(CUSTOMIZER_TYPE)) {
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

    /**
     * 仓库根目录 —— <b>从本类的 code source 向上找</b>（不依赖工作目录）。
     *
     * <p>⚠️ 不能用 {@code user.dir}：surefire 的工作目录是<b>模块目录</b>（{@code service/common}），
     * 而从 IDE 跑时又是仓库根 ⇒ ⇒ 两种布局都对 ⇒ ⇒ 必须走 code source。
     */
    private static Path repoRoot() {
        Path here = Path.of(GoStyleRedisClientShadowingGateTest.class.getProtectionDomain()
                .getCodeSource().getLocation().getPath());
        for (Path p = here; p != null; p = p.getParent()) {
            if (Files.isDirectory(p.resolve("service")) && Files.isRegularFile(p.resolve("pom.xml"))) {
                return p;
            }
        }
        throw new IllegalStateException("找不到仓库根（从 " + here + " 向上没有同时含 service/ 与 pom.xml 的目录）");
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
