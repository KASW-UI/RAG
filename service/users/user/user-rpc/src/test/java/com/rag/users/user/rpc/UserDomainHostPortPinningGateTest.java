package com.rag.users.user.rpc;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 🟢 **users 域 · Testcontainers 宿主口钉段的守门**（G4 §4.10.39 ② 的域侧那一半）。
 *
 * <h2>它守什么</h2>
 * <ol>
 *   <li><b>覆盖（守恒）</b>：本域<b>每一个</b>容器起点都带「显式绑口 modifier」⇒ ⇒
 *       <b>漏一个 ⇒ 那个容器仍落回 OS 临时段随机口 ⇒ 撞口风险原样存在</b> ⇒ ⇒ 红。</li>
 *   <li><b>钉对口</b>：钉的必须是<b>该容器自己的</b>容器口（PG=5432 / Redis=6379）⇒ ⇒
 *       钉错口 ⇒ 容器启动失败或钉了个寂寞 ⇒ ⇒ 红。</li>
 *   <li><b>段正确</b>：各模块 helper 的段必须落在 G4 分配的 users 段
 *       <b>{@code 22700–22729}</b> 内，且<b>与 OS 临时段 {@code 49152–65535} 不相交</b>
 *       ⇒ ⇒ 这两条正是「把宿主口挪出临时段」的全部意义 ⇒ ⇒ 红。</li>
 *   <li><b>副本等价</b>：本域 <b>4 个模块</b>各有一份 {@code GoStyleHostPortPinning}
 *       （G4 §4.10.21.6 的处方：「各模块各守自己那份 ⇒ 避免跨模块的路径依赖」）
 *       ⇒ ⇒ 但<b>副本会漂</b> ⇒ ⇒ ⇒ <b>4 份的源码必须逐字等价</b>（只允许 package 与段常量不同）
 *       ⇒ ⇒ 漂一份 ⇒ 红。</li>
 * </ol>
 *
 * <h2>为什么第 ④ 条不可省</h2>
 * <p>「各模块各守自己那份」的代价 = <b>同一个逻辑有 N 份副本</b> ⇒ ⇒ N 个漂移点
 * ⇒ ⇒ 而漂移的后果是<b>静默</b>的（某域的容器又落回随机口 ⇒ 只是「撞口概率变高」，不会报错）
 * ⇒ ⇒ ⇒ 必须有一条<b>机器</b>判据把它们钉成一份。
 *
 * <h2>为什么不把这条守门放进共享层</h2>
 * <p>共享层（{@code service/common}）是 G4 的写权面 ⇒ ⇒ 本域零动作
 * ⇒ ⇒ 守门只能落在<b>域侧</b>（本类）⇒ ⇒ 而它的扫描面<b>只覆盖 users 域自己的模块</b>
 * ⇒ ⇒ 不对别的 lane 产生任何约束。
 *
 * @see com.rag.users.user.rpc.UserRpcTestcontainersHostPortSegmentGateTest
 *      —— 真容器那一层：钉口后 {@code getMappedPort()} == 那个固定口，且落在段内。
 */
@DisplayName("users 域 · Testcontainers 宿主口钉段守门（覆盖 / 钉对口 / 段正确 / 副本等价）")
class UserDomainHostPortPinningGateTest {

    /** G4 §4.10.39 ② 分配的 users 域宿主口段。 */
    private static final int DOMAIN_SEGMENT_START = 22700;
    private static final int DOMAIN_SEGMENT_END = 22729;

    /** OS 临时段（随机出向口默认落这里）—— 钉口的意义就是与之错开。 */
    private static final int OS_EPHEMERAL_START = 49152;
    private static final int OS_EPHEMERAL_END = 65535;

    /** 本域的 4 个模块（各持一份 helper 副本）。 */
    private static final List<String> DOMAIN_MODULES = List.of(
            "service/users/user/user-api",
            "service/users/user/user-rpc",
            "service/users/admin/admin-api",
            "service/users/admin/admin-rpc");

    /** 容器起点：类名 → 它自己暴露的容器口。 */
    private static final Map<String, Integer> CONTAINER_PORT = Map.of(
            "PostgreSQLContainer", 5432,
            "GenericContainer", 6379,
            "MySQLContainer", 3306);

    private static final Pattern CONTAINER_RE =
            Pattern.compile("new\\s+(PostgreSQLContainer|GenericContainer|MySQLContainer|MongoDBContainer)\\b");
    /** 钉口调用：最后一个数字实参 = 被钉的容器口（`pinContainerPort(cmd, 5432)` / `pinHostPort(cmd, hostPort, 5432)`）。 */
    private static final Pattern PIN_RE =
            Pattern.compile("withCreateContainerCmdModifier\\(\\s*cmd\\s*->\\s*\\w+\\(\\s*cmd\\s*,[^()]*?(\\d+)\\s*\\)");

    // ==================================================================
    // ① 覆盖（守恒）+ ② 钉对口
    // ==================================================================

    @Test
    @DisplayName("① 本域每个容器起点都带显式绑口 modifier，且钉的是该容器自己的容器口")
    void everyContainerStartPinsItsOwnContainerPort() {
        int containerPoints = 0;
        List<String> violations = new ArrayList<>();

        for (String module : DOMAIN_MODULES) {
            for (Path f : testSourcesOf(module)) {
                // ⚠️ 必须剥注释后再扫：helper 的 javadoc 里有一段带容器声明的 <pre> 示例
                //    ⇒ 不剥 ⇒ 说明文字被当成容器起点 ⇒ 假红（实测踩到）。
                String text = stripComments(read(f));
                List<int[]> chains = containerChains(text);
                for (int[] chain : chains) {
                    containerPoints++;
                    String kind = chainKind(text, chain[0]);
                    int expectedPort = CONTAINER_PORT.getOrDefault(kind, -1);
                    Matcher m = PIN_RE.matcher(text.substring(chain[0], chain[1]));
                    if (!m.find()) {
                        violations.add(rel(module, f) + " ⇒ 容器起点【没有】显式绑口 modifier"
                                + "（⇒ 它仍落回 OS 临时段随机口 ⇒ 撞口风险原样存在）");
                        continue;
                    }
                    int pinnedPort = Integer.parseInt(m.group(1));
                    if (pinnedPort != expectedPort) {
                        violations.add(rel(module, f) + " ⇒ 钉的是容器口 " + pinnedPort
                                + "，但该容器（" + kind + "）自己的口是 " + expectedPort
                                + "（⇒ 钉错口 ⇒ 容器启动失败或钉了个寂寞）");
                    }
                }
            }
        }

        assertThat(containerPoints)
                .as("users 域的容器起点数（守恒断言：漏扫 ⇒ 判据空转 ⇒ 直接失败）")
                .isGreaterThan(0);
        assertThat(violations)
                .as("users 域每个容器起点都必须显式绑口（这是「把宿主口挪出 OS 临时段」的全部落点）")
                .isEmpty();
    }

    // ==================================================================
    // ③ 段正确
    // ==================================================================

    @Test
    @DisplayName("③ 各模块 helper 的段必须落在 G4 分配的 users 段内，且与 OS 临时段不相交")
    void everyModuleSegmentIsInsideTheDomainSegmentAndOutsideTheEphemeralRange() {
        for (String module : DOMAIN_MODULES) {
            Path helper = helperOf(module);
            assertThat(helper)
                    .as("模块 %s 必须有一份 GoStyleHostPortPinning（落点 = 域侧改自己的测试类）", module)
                    .exists();
            String text = read(helper);

            int start = intConstant(text, "SEGMENT_START");
            int end = intConstant(text, "SEGMENT_END");

            assertThat(start)
                    .as("%s 的段起点必须 ≥ G4 分配的 users 段起点 %d", module, DOMAIN_SEGMENT_START)
                    .isGreaterThanOrEqualTo(DOMAIN_SEGMENT_START);
            assertThat(end)
                    .as("%s 的段终点必须 ≤ G4 分配的 users 段终点 %d", module, DOMAIN_SEGMENT_END)
                    .isLessThanOrEqualTo(DOMAIN_SEGMENT_END);
            assertThat(start)
                    .as("%s 的段起点必须 ≤ 段终点", module)
                    .isLessThanOrEqualTo(end);

            // ⇒ ⇒ 「挪出 OS 临时段」这条意义本身 ⇒ 必须断言不相交
            assertThat(start > OS_EPHEMERAL_END || end < OS_EPHEMERAL_START)
                    .as("%s 的段 %d-%d 与 OS 临时段 %d-%d <b>相交</b> ⇒ 钉口没有意义"
                            + "（⇒ 撞口风险原样存在）", module, start, end,
                            OS_EPHEMERAL_START, OS_EPHEMERAL_END)
                    .isTrue();
        }
    }

    // ==================================================================
    // ④ 副本等价（防漂）
    // ==================================================================

    @Test
    @DisplayName("④ 4 份 helper 副本必须逐字等价（只允许 package 与段常量不同）⇒ 防副本漂移")
    void helperCopiesAreEquivalent() {
        Map<String, String> normalized = new LinkedHashMap<>();
        for (String module : DOMAIN_MODULES) {
            normalized.put(module, normalize(read(helperOf(module))));
        }

        String reference = null;
        String referenceModule = null;
        List<String> violations = new ArrayList<>();
        for (Map.Entry<String, String> e : normalized.entrySet()) {
            if (reference == null) {
                reference = e.getValue();
                referenceModule = e.getKey();
                continue;
            }
            if (!reference.equals(e.getValue())) {
                violations.add(e.getKey() + " 与基准 " + referenceModule + " 不等价");
            }
        }

        assertThat(reference)
                .as("基准副本（%s）不能为空 ⇒ 扫描面失效 ⇒ 判据空转", referenceModule)
                .isNotBlank();
        assertThat(violations)
                .as("各模块的 GoStyleHostPortPinning 副本必须<b>逐字等价</b>"
                        + "（只允许 package 与段常量不同）⇒ ⇒ 「各模块各守自己那份」的漂移风险由此机器兜住")
                .isEmpty();
    }

    // ==================================================================
    // 辅助
    // ==================================================================

    /**
     * 把 package 行与段常量抹掉 ⇒ 只比「逻辑」⇒ 副本漂移必被抓住。
     *
     * <p>⚠️ <b>先剥注释</b> ⇒ ⇒ javadoc 的措辞差异<b>不算</b>漂移（那是无害的）
     * ⇒ ⇒ 只有<b>代码</b>不同才红 ⇒ ⇒ 判据精确到「逻辑」而不是「文本」。
     */
    private static String normalize(String text) {
        String stripped = stripComments(text);
        return stripped.replaceAll("(?m)^package .*$", "package <normalized>;")
                .replaceAll("(?m)^\\s*public static final int SEGMENT_(START|END) = \\d+;$",
                        "    public static final int SEGMENT_$1 = <normalized>;");
    }

    private static int intConstant(String text, String name) {
        Matcher m = Pattern.compile("public static final int " + name + " = (\\d+);").matcher(text);
        assertThat(m.find()).as("%s 常量必须存在", name).isTrue();
        return Integer.parseInt(m.group(1));
    }

    private static Path helperOf(String module) {
        Path dir = repoRoot().resolve(module).resolve("src/test/java");
        try (Stream<Path> walk = Files.walk(dir)) {
            return walk.filter(p -> p.getFileName().toString().equals("GoStyleHostPortPinning.java"))
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            throw new IllegalStateException("枚举 helper 失败：" + dir, e);
        }
    }

    private static List<Path> testSourcesOf(String module) {
        Path dir = repoRoot().resolve(module).resolve("src/test/java");
        try (Stream<Path> walk = Files.walk(dir)) {
            return walk.filter(p -> p.getFileName().toString().endsWith(".java")).sorted().toList();
        } catch (IOException e) {
            throw new IllegalStateException("枚举测试源失败：" + dir, e);
        }
    }

    /**
     * 找出所有容器起点的链区间（{@code [链首偏移, 链尾偏移)}）。
     *
     * <p>⚠️ <b>不能用「一行一个容器」的假设</b>：PG 是多行链 ⇒ ⇒ 必须扫到链尾的 {@code ;}。
     */
    private static List<int[]> containerChains(String text) {
        List<int[]> chains = new ArrayList<>();
        Matcher m = CONTAINER_RE.matcher(text);
        while (m.find()) {
            int end = text.indexOf(';', m.end());
            if (end < 0) {
                continue;
            }
            chains.add(new int[]{m.start(), end});
        }
        return chains;
    }

    private static String chainKind(String text, int start) {
        Matcher m = CONTAINER_RE.matcher(text);
        m.region(start, text.length());
        return m.find() ? m.group(1) : "?";
    }

    private static String read(Path p) {
        try {
            return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("读取失败：" + p, e);
        }
    }

    private static String rel(String module, Path f) {
        return repoRoot().relativize(f).toString();
    }

    /**
     * <b>剥掉注释</b>再扫描。
     *
     * <p>⚠️ <b>为什么必须剥</b>：helper 自己的 javadoc 里有一段 {@code <pre>} 示例，
     * 里面写着 {@code new PostgreSQLContainer<>("postgres:16-alpine")} ⇒ ⇒ 若不剥，
     * <b>说明文字会被当成容器起点</b> ⇒ ⇒ 守门报「这个容器没钉口」⇒ ⇒ <b>假红</b>。
     * ⇒ ⇒ 而且更危险的一面：若哪天示例与真实代码不同，**扫描面就会被说明文字污染**。
     *
     * <p>实现 = 一个极简状态机（块注释 / 行注释），<b>不动字符串字面量</b>
     * ⇒ ⇒ 字符串里的 {@code //} 不会被误当注释起点。
     */
    private static String stripComments(String src) {
        StringBuilder out = new StringBuilder(src.length());
        boolean inBlock = false;
        boolean inLine = false;
        boolean inString = false;
        boolean inChar = false;
        for (int i = 0; i < src.length(); i++) {
            char c = src.charAt(i);
            char next = i + 1 < src.length() ? src.charAt(i + 1) : '\0';
            if (inBlock) {
                if (c == '*' && next == '/') {
                    inBlock = false;
                    i++;
                }
                continue;
            }
            if (inLine) {
                if (c == '\n') {
                    inLine = false;
                    out.append(c);
                }
                continue;
            }
            if (inString) {
                out.append(c);
                if (c == '\\') {
                    if (i + 1 < src.length()) {
                        out.append(src.charAt(++i));
                    }
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (inChar) {
                out.append(c);
                if (c == '\\') {
                    if (i + 1 < src.length()) {
                        out.append(src.charAt(++i));
                    }
                } else if (c == '\'') {
                    inChar = false;
                }
                continue;
            }
            if (c == '/' && next == '*') {
                inBlock = true;
                i++;
                continue;
            }
            if (c == '/' && next == '/') {
                inLine = true;
                i++;
                continue;
            }
            if (c == '"') {
                inString = true;
                out.append(c);
                continue;
            }
            if (c == '\'') {
                inChar = true;
                out.append(c);
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }

    /**
     * 仓库根 —— 先按「本类的 code source」往上找（与工作目录无关），再退回 {@code user.dir}。
     */
    private static Path repoRoot() {
        Path cur = null;
        try {
            Path cs = Path.of(UserDomainHostPortPinningGateTest.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            cur = Files.isDirectory(cs) ? cs : cs.getParent();
        } catch (Exception ignored) {
            // 退回 user.dir
        }
        Path probe = cur != null ? cur : Path.of(System.getProperty("user.dir"));
        while (probe != null) {
            if (Files.isRegularFile(probe.resolve("pom.xml")) && Files.isDirectory(probe.resolve("service"))) {
                return probe;
            }
            probe = probe.getParent();
        }
        throw new IllegalStateException("找不到仓库根（从 " + cur + " / " + System.getProperty("user.dir") + " 往上找）");
    }
}
