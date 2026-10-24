package com.rag.users.common.cryptx;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * DRIFT-004 回归网：{@link PasswordUtil} 与 Go {@code cryptx.CheckPassword} 的逐条等价。
 *
 * <p>Go 出处：{@code service/user/common/cryptx/password.go:15-18}
 * <pre>
 * func CheckPassword(dbHash, inputPassword string) bool {
 *     err := bcrypt.CompareHashAndPassword([]byte(dbHash), []byte(inputPassword))
 *     return err == nil
 * }
 * </pre>
 * <b>任何 err 都返回 false</b>，不区分「密码错」与「hash 非法」。
 *
 * <p><b>为什么这张表要写死</b>：修复前 {@code BCryptPasswordEncoder.matches} 对
 * 畸形 hash 抛 {@code IllegalArgumentException}，一路冒泡成 {@code 500 + 5001}。
 * 但「哪些形态会抛」是 Spring Security 版本的实现细节 —— 本类是 Spring Security
 * <b>7.0.7</b> 的实测结果（2026-10-21）。若将来升级版本导致更多/更少形态抛异常，
 * <b>本表会先红</b>，而不是等到生产登录 500 才发现。
 */
class PasswordUtilGoCompatibilityTest {

    /** 与 UserDomainServiceTest 一致，固定 hash 可重复。 */
    private static final String RAW = "pw123456";

    @Test
    @DisplayName("DRIFT-004 正向：密码匹配 → MATCHED")
    void matchedIsMatched() {
        String hash = PasswordUtil.encrypt(RAW);
        assertThat(PasswordUtil.checkDetailed(RAW, hash))
                .isEqualTo(PasswordUtil.Outcome.MATCHED);
        assertThat(PasswordUtil.check(RAW, hash)).isTrue();
    }

    @Test
    @DisplayName("DRIFT-004 正向：密码不匹配 → MISMATCHED，且 check 返回 false")
    void mismatchedIsFalse() {
        String hash = PasswordUtil.encrypt(RAW);
        assertThat(PasswordUtil.checkDetailed("wrong-password", hash))
                .isEqualTo(PasswordUtil.Outcome.MISMATCHED);
        assertThat(PasswordUtil.check("wrong-password", hash)).isFalse();
    }

    // ==================================================================
    // 以下形态在修复前全部「接受」或行为不一，逐条钉住。
    // 关键断言是 checkDetailed 的分类 + check 永不抛异常。
    // ==================================================================

    @Test
    @DisplayName("DRIFT-004 核心：cost 越界的 hash → MALFORMED_HASH，【不得抛异常】")
    void malformedCostYieldsMalformedHashNotException() {
        String hash = PasswordUtil.encrypt(RAW);
        String badCost = "$2a$99$" + hash.substring(7);

        // 修复前：BCrypt.checkpw 抛 IllegalArgumentException: Bad number of rounds
        assertThatCode(() -> PasswordUtil.check(RAW, badCost)).doesNotThrowAnyException();
        assertThat(PasswordUtil.checkDetailed(RAW, badCost))
                .isEqualTo(PasswordUtil.Outcome.MALFORMED_HASH);
        // 对外行为 = Go 的 false
        assertThat(PasswordUtil.check(RAW, badCost)).isFalse();
    }

    @Test
    @DisplayName("DRIFT-004：空串 / 非 bcrypt / 截断 hash —— 修复前本就返回 false（与 Go 已一致），不得回归成抛异常")
    void structurallyBrokenHashesStayFalse() {
        for (String bad : new String[]{"", "abc", "$2a$10$tooshort"}) {
            assertThatCode(() -> PasswordUtil.check(RAW, bad))
                    .as("hash=%s", bad)
                    .doesNotThrowAnyException();
            assertThat(PasswordUtil.check(RAW, bad)).as("hash=%s", bad).isFalse();
            // 这些形态被 BCRYPT_PATTERN 挡在 matchesNonNull 之前 ⇒ 属 MISMATCHED 而非 MALFORMED_HASH
            assertThat(PasswordUtil.checkDetailed(RAW, bad))
                    .as("hash=%s", bad)
                    .isEqualTo(PasswordUtil.Outcome.MISMATCHED);
        }
    }

    @Test
    @DisplayName("DRIFT-004：null hash / null 明文 → false 且不抛（Go 的 err==nil 同样返回 false）")
    void nullsAreFalseNotExceptions() {
        assertThat(PasswordUtil.check(RAW, null)).isFalse();
        assertThat(PasswordUtil.check(null, PasswordUtil.encrypt(RAW))).isFalse();
        assertThat(PasswordUtil.check(null, null)).isFalse();
    }

    /**
     * <b>前缀兼容性</b>：Spring 的 {@code BCRYPT_PATTERN} 是
     * {@code \A\$2(a|y|b)?\$(\d\d)\$[./0-9A-Za-z]{53}}，
     * Go 的 {@code decodeVersion} 只判 major ≤ '2' 且 minor 可选 ——
     * 两者接受的集合<b>相同</b>。
     *
     * <p>本用例的作用是<b>否证一个曾经被担心的伪漂移</b>：
     * 有人以为 Spring 只认 {@code $2a$}、会拒掉 {@code $2b$} ——
     * 实测不会，而且这不是 RAG 的问题。
     */
    @Test
    @DisplayName("DRIFT-004：$2b$ / $2y$ 合法 hash 与 Go 一样被接受（否证「RAG 不认 $2b$」的伪漂移）")
    void allPrefixVariantsAreAcceptedLikeGo() {
        String hash = PasswordUtil.encrypt(RAW);
        for (String prefix : new String[]{"$2b$", "$2y$"}) {
            String variant = prefix + hash.substring(4);
            assertThat(PasswordUtil.check(RAW, variant))
                    .as("prefix=%s", prefix)
                    .isTrue();
            assertThat(PasswordUtil.checkDetailed(RAW, variant))
                    .as("prefix=%s", prefix)
                    .isEqualTo(PasswordUtil.Outcome.MATCHED);
        }
    }

    /**
     * <b>已知的反向差异（不修，留档）</b>：无 minor 的 {@code $2$} 前缀。
     *
     * <p>实测 RAG 返回 {@code false}，而 Go 的 {@code decodeVersion} 在
     * {@code sbytes[2] == '$'} 时会接受（{@code n = 3} 直接跳到 cost）⇒ Go 侧应为 {@code true}。
     *
     * <p><b>为什么不修</b>：
     * <ol>
     *   <li>方向是「RAG 更严格」，登录场景下 fail-closed 更安全，不是漏洞；</li>
     *   <li>修它要绕过 {@code BCryptPasswordEncoder} 直接调 {@code BCrypt.checkpw}，
     *       等于放弃 Spring 的模式校验 —— 为一个几乎不会出现的旧格式牺牲掉防御；</li>
     *   <li>{@code $2$}（无 minor）是 bcrypt 最早的实验格式，
     *       生产库里几乎不可能存在。</li>
     * </ol>
     * 已记入 AGENTS.md §3.4.4 的 DRIFT-004 行。
     */
    @Test
    @DisplayName("DRIFT-004 留档：$2$（无 minor）RAG 返回 false 而 Go 会返回 true —— 已知反向差异，不修")
    void barePrefixIsFalseInRagButGoWouldAcceptIt() {
        String hash = PasswordUtil.encrypt(RAW);
        String bare = "$2$" + hash.substring(4);

        assertThat(PasswordUtil.check(RAW, bare))
                .as("这是【已知且刻意不修】的反向差异，Go 侧会返回 true")
                .isFalse();
    }
}
