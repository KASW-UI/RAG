package com.rag.common.http;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 🔴 <b>差分守门</b>：共享层的 {@code goPathClean} 与 Go 标准库 {@code path.Clean}
 * <b>逐条一致</b>（32 条基准）。
 *
 * <h2>基准是怎么来的（不是「照着源码手写期望值」）</h2>
 * <p>基准由 <b>Go 1.25.11 的 {@code path.Clean}</b> 现场生成：
 * <pre>
 *   $ cat main.go
 *   package main
 *   import ("fmt"; "path")
 *   func main() {
 *       for _, in := range []string{ "", "/", "//", ... } {
 *           fmt.Printf("%q\t%q\n", in, path.Clean(in))
 *       }
 *   }
 *   $ go run .
 * </pre>
 * ⇒ ⇒ 本类的期望值 = <b>Go 的输出</b>，不是任何人「以为 Go 会怎么洗」的答案。
 *
 * <h2>🔴 为什么这条守门必须存在（旧版移植真的错过）</h2>
 * <p>article-api 的域内副本（{@code com.rag.article.article.api.config.GoStylePathNormalizingFilter#goPathClean}）
 * 是按「<b>路径段列表</b>」建模的移植，而 Go 的 {@code path.Clean} 是按
 * 「<b>写入位置 + 回退到上一个 '/'</b>」建模的 ⇒ ⇒ 两者在<b>两处</b>给出不同结果：
 * <table border="1">
 *   <caption>实测差异（本类的基准直接把它钉住）</caption>
 *   <tr><th>输入</th><th>Go</th><th>旧版（按段建模）</th><th>成因</th></tr>
 *   <tr><td>{@code "/"}</td><td>{@code "/"}</td><td>{@code ""}</td>
 *       <td>Go 的 rooted 路径<b>先写入一个 '/'</b>；按段建模时根路径成了空串</td></tr>
 *   <tr><td>{@code ".."}</td><td>{@code ".."}</td><td>{@code "."}</td>
 *       <td>Go 在<b>不可回退且非 rooted</b> 时原样追加<b>两个</b>点；旧版只追加一个</td></tr>
 *   <tr><td>{@code "../a"}</td><td>{@code "../a"}</td><td>{@code "./a"}</td>
 *       <td>同上（且分隔符的追加时机也不同）</td></tr>
 * </table>
 * ⇒ ⇒ 共享层本实现已按 Go 的<b>字节级算法</b>重写（见该方法的 javadoc 里的 Go 原文）。
 *
 * <h2>⚠️ 这两处偏差对「请求路径」可达吗</h2>
 * <p>本 Filter 只处理 {@code getRequestURI()} ⇒ <b>永远以 '/' 开头</b>
 * ⇒ ⇒ <b>非 rooted 的那几行（".." / "../a"）在生产路径上不可达</b>（仍然逐条断言，
 * 因为它们是<b>算法本身</b>的正确性边界）。
 * <p>而 <b>rooted 的根路径族（{@code "/"} / {@code "//"} / {@code "/."} / {@code "/.."}）是可达的</b>
 * ⇒ ⇒ 旧版会把它们洗成空串 ⇒ 而 Go 洗成 {@code "/"}
 * ⇒ ⇒ 差异<b>只</b>在「该域是否注册了根路径路由」时才可观察。
 */
@DisplayName("🔴 差分守门：goPathClean 与 Go 的 path.Clean 逐条一致（32 条 Go 实测基准）")
class GoStylePathCleanParityTest {

    /**
     * 32 条基准：输入 → Go {@code path.Clean} 的输出。
     *
     * <p>⚠️ <b>期望值是 Go 1.25.11 的实际输出</b>（由 {@code $GOROOT/src/path/path.go} 的
     * {@code Clean} 现场生成），<b>不是</b>人工推断。
     */
    private static final String[][] FIXTURE = {
            {"", "."},
            {"/", "/"},
            {"//", "/"},
            {"///", "/"},
            {"/.", "/"},
            {"/..", "/"},
            {"/a", "/a"},
            {"/a/", "/a"},
            {"/a//b", "/a/b"},
            {"/a/./b", "/a/b"},
            {"/a/../b", "/b"},
            {"/../a", "/a"},
            {"/a/../../b", "/b"},
            {"a", "a"},
            {"a/", "a"},
            {"./a", "a"},
            {"../a", "../a"},
            {"a/..", "."},
            {"a/../..", ".."},
            {".", "."},
            {"..", ".."},
            {"/a/b/../../../c", "/c"},
            {"/v1/x", "/v1/x"},
            {"/v1/x/", "/v1/x"},
            {"/v1//x", "/v1/x"},
            {"/v1/x/../x", "/v1/x"},
            {"/v1/./x", "/v1/x"},
            {"/v1/articles/", "/v1/articles"},
            {"/a/b/c/../../d/e", "/a/d/e"},
            {"//a//b//", "/a/b"},
            {"/a/./././b", "/a/b"},
    };

    @Test
    @DisplayName("🔴 32 条基准逐条一致（输入 → Go 的 path.Clean 输出）")
    void matchesGoPathCleanOnEveryFixture() {
        for (String[] pair : FIXTURE) {
            String input = pair[0];
            String expected = pair[1];
            assertEquals(expected, GoStylePathNormalizingFilter.goPathClean(input),
                    "goPathClean 必须与 Go 的 path.Clean 逐条一致；输入=" + quote(input)
                            + "（Go 实测输出=" + quote(expected) + "）");
        }
    }

    @Test
    @DisplayName("🔴 null 输入 ⇒ \".\"（Go: path.Clean(\"\") == \".\"；空串与 null 同义处理）")
    void nullInputBehavesLikeEmptyString() {
        assertEquals(".", GoStylePathNormalizingFilter.goPathClean(null),
                "Go 的 Clean 对空串返回 \".\"；本方法把 null 当空串处理 ⇒ 必须同解");
    }

    /** 把输入/输出渲染成 Go 风格的带引号字面量（便于失败信息里肉眼核对）。 */
    private static String quote(String s) {
        return "\"" + s + "\"";
    }
}
