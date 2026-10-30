package com.rag.users.user.rpc.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 🔴 守门：datasource 口令**不得带默认值**（`S1-USERS-002` · 2026-10-30 新增）。
 *
 * <p><b>Go 侧出处（逐字）</b>：`service/user/user/rpc/etc/user.yaml:13` ⇒
 * {@code Password: "${POSTGRES_PASSWORD}"} ⇒ <b>无默认值</b>；
 * go-zero {@code core/conf/config.go:63} = {@code loader([]byte(os.ExpandEnv(string(content))), v)}
 * ⇒ {@code os.ExpandEnv} <b>不认</b> {@code ${VAR:default}} 语法 ⇒ 未设变量 ⇒ <b>展开成空串</b>
 * ⇒ 用空口令连库 ⇒ 认证失败 ⇒ <b>fail-fast</b>。
 *
 * <p><b>为什么 RAG 侧此前是真漂移</b>：写的是 {@code ${POSTGRES_PASSWORD:password}}
 * ⇒ 缺变量时**用公开默认口令连上** ⇒ ⇒ Go 连不上 / RAG 连得上 ⇒ 两侧<b>不同形</b>
 * ⇒ ⇒ 本轮取消 fallback ⇒ 与 Go 同形。
 *
 * <p>⚠️ <b>残余差异（RAG 更严的一侧）</b>：Go = 启动成功、<b>连库时</b>失败；
 * RAG = <b>启动即失败</b>（Spring 解析不到占位符）⇒ ⇒ <b>失败时刻</b>不同
 * ⇒ 属有意偏离候选（同 JWT fail-closed 守卫族）。
 *
 * <p><b>本守门为什么这样写</b>：断言打在<b>本模块自己的</b> yaml 上（按 code source 定位模块根
 * ⇒ 不依赖 CWD），而不是扫全仓 —— 全仓 10 处的其余 7 处属 G2/G3 面。
 * ⇒ 有人把 {@code :password} 兜底加回本模块 ⇒ 本类<b>立刻变红</b>。
 */
class UserRpcDatasourcePasswordFallbackGateTest {

    /** 本模块主配置文件（相对模块根）。 */
    private static final String MAIN_YAML = "application.yaml";

    /** 带默认值形态：{@code ${POSTGRES_PASSWORD:xxx}}。 */
    private static final Pattern WITH_DEFAULT =
            Pattern.compile("\\$\\{POSTGRES_PASSWORD:(?!\\})[^}]*\\}");

    /** Go 同形（无默认值）。 */
    private static final String GO_SHAPE = "password: ${POSTGRES_PASSWORD}";

    @Test
    @DisplayName("🔴 datasource 口令不得带默认值（Go 侧无默认值 ⇒ 缺变量即 fail-fast）")
    void datasourcePasswordHasNoFallback() {
        // ⚠️ 只扫**生效行**（排除整行注释）⇒ 注释里解释历史写法不会造成假红；
        //    只有生效行里出现带默认值的占位符才是真的回退。
        String yaml = effectiveLines(readMainYaml());
        Matcher m = WITH_DEFAULT.matcher(yaml);
        // ⚠️ 先取布尔再拼消息：`m.group()` 在**没有匹配**时抛 IllegalStateException
        //    ⇒ 直接写进 message 会让「本该绿」的用例抛异常而不是断言。
        boolean hasDefault = m.find();
        assertFalse(hasDefault,
                "application.yaml 里仍有带默认值的口令占位符（命中：" + (hasDefault ? m.group() : "")
                        + "）⇒ ⇒ 缺 POSTGRES_PASSWORD 时会用公开默认口令连上 ⇒ 与 Go（空串 ⇒ fail-fast）不同形。");

        assertTrue(yaml.contains(GO_SHAPE),
                "application.yaml 里找不到 Go 同形的 `" + GO_SHAPE + "` ⇒ 形态已变，请重新对照 Go 侧 `service/user/user/rpc/etc/user.yaml:13`");
    }

    @Test
    @DisplayName("🔴 只允许这一个口令占位符形态（防新增第二处带默认值的写法）")
    void onlyOnePasswordPlaceholderShapeExists() {
        String yaml = effectiveLines(readMainYaml());
        long placeholders = yaml.lines()
                .filter(l -> l.contains("POSTGRES_PASSWORD"))
                .count();
        assertTrue(placeholders <= 1,
                "application.yaml 里出现了 " + placeholders + " 处生效的 POSTGRES_PASSWORD 占位符 ⇒ "
                        + "预期只有 1 处（password 行）⇒ 请复查是否新增了第二块 datasource。");
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /** 只保留生效行（去掉整行注释）—— 注释里提到历史写法不算回退。 */
    private static String effectiveLines(String yaml) {
        return String.join("\n", yaml.lines()
                .filter(l -> !l.trim().startsWith("#"))
                .toList());
    }

    private static String readMainYaml() {
        Path file = moduleRoot().resolve("src/main/resources").resolve(MAIN_YAML);
        if (!Files.isRegularFile(file)) {
            throw new IllegalStateException("读不到主配置文件：" + file);
        }
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("读文件失败：" + file, e);
        }
    }

    /** target/test-classes ⇒ 上溯两级到模块根（与仓内既有守门同一套定位方式）。 */
    private static Path moduleRoot() {
        try {
            URL loc = UserRpcDatasourcePasswordFallbackGateTest.class.getProtectionDomain().getCodeSource().getLocation();
            Path p = Path.of(URI.create(loc.toString())).toAbsolutePath();
            return p.getParent() != null && p.getParent().getParent() != null
                    ? p.getParent().getParent()
                    : p;
        } catch (Exception e) {
            throw new IllegalStateException("定位模块根失败", e);
        }
    }
}
