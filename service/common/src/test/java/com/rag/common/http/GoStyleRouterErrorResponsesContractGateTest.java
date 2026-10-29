package com.rag.common.http;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 🔴 <b>共享层逐类契约守门 · http 包</b>：给 {@code GoStyleRouterErrorResponses} 补上行为断言。
 *
 * <h2>为什么补这个</h2>
 * <p>本类是 <b>A6 轴（错误码 / envelope）在共享层的第三个落点</b>
 * ⇒ ⇒ 此前它<b>零自有测试</b>（§4.17.3 登记为「共享层零自有测试」）
 * ⇒ ⇒ R3 判据 ⑤（RAG 侧有自动化测试断言该行为）<b>不满足</b>。
 *
 * <h2>Go 出处（逐条）</h2>
 * <ul>
 *   <li>404 —— {@code go-zero@v1.10.2/rest/router/patrouter.go:93-97}
 *       （{@code func (pr *patRouter) handleNotFound} ⇒ {@code http.NotFound(w, r)}）</li>
 *   <li>405 —— {@code go-zero@v1.10.2/rest/router/patrouter.go:79-82}
 *       （{@code w.Header().Set(allowHeader, allows)} ＋ {@code w.WriteHeader(http.StatusMethodNotAllowed)}
 *       ⇒ <b>无 body</b>）</li>
 * </ul>
 *
 * <h2>⚠️ 证据强度</h2>
 * <p><b>单测级（L1 / E3）</b>：断言本类产出的<b>响应形状</b>（状态码 / 头 / body 字节）
 * ⇒ ⇒ <b>不是</b>「真 Tomcat 里跑一遍」的 L3 证据
 * ⇒ ⇒ 容器归一化（{@code Content-Type} 的 charset 归一、{@code Allow} 头的实际下发）那一面
 * <b>不在本门覆盖范围</b>（真 Tomcat 那一面由域侧 {@code LikeApiRouterErrorRealTomcatTest} 覆盖）。
 */
class GoStyleRouterErrorResponsesContractGateTest {

    @Test
    @DisplayName("404：状态码必须是 404，body 必须是 Go 的 '404 page not found\\n'（20 字节，带尾换行）")
    void notFoundMatchesGoByteShape() {
        var resp = GoStyleRouterErrorResponses.notFound("/api/v1/whatever");

        assertThat(resp.getStatusCode().value()).as("Go: http.NotFound ⇒ 404").isEqualTo(404);
        assertThat(resp.getBody()).as("Go: http.Error 用 Fprintln ⇒ 尾随换行").isNotNull();
        assertThat(new String(resp.getBody(), StandardCharsets.UTF_8))
                .isEqualTo("404 page not found\n");
        assertThat(resp.getBody()).as("19 字节（18 字节正文 ＋ 1 字节尾换行）").hasSize(19);
    }

    @Test
    @DisplayName("404：必须带 nosniff 与 text/plain; charset=utf-8（Go net/http.Error 的头）")
    void notFoundCarriesGoHeaders() {
        var resp = GoStyleRouterErrorResponses.notFound("/api/v1/whatever");

        assertThat(resp.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE))
                .as("Go: http.Error 写的是 \"text/plain; charset=utf-8\"")
                .isEqualTo("text/plain; charset=utf-8");
        assertThat(resp.getHeaders().getFirst("X-Content-Type-Options"))
                .as("Go: http.Error 会加 nosniff")
                .isEqualTo("nosniff");
    }

    @Test
    @DisplayName("404：resourcePath 只进日志，不进响应体（Go 的 404 body 是固定的）")
    void notFoundBodyDoesNotLeakThePath() {
        var resp = GoStyleRouterErrorResponses.notFound("/secret/path/xyz");
        assertThat(new String(resp.getBody(), StandardCharsets.UTF_8))
                .as("路径不得出现在 body 里（Go 侧 body 是固定串）")
                .doesNotContain("/secret/path/xyz");
    }

    @Test
    @DisplayName("405：状态码必须是 405，body 必须为空（Go：只 WriteHeader，不写 body）")
    void methodNotAllowedHasNoBody() {
        var resp = GoStyleRouterErrorResponses.methodNotAllowed(List.of("GET", "POST"));

        assertThat(resp.getStatusCode().value())
                .as("Go: patrouter.go:81 ⇒ w.WriteHeader(http.StatusMethodNotAllowed)")
                .isEqualTo(HttpStatus.METHOD_NOT_ALLOWED.value());
        assertThat(resp.getBody()).as("Go 只写头不写 body ⇒ RAG 侧 body 也必须是 null").isNull();
    }

    @Test
    @DisplayName("405：必须带 Allow 头，值按调用方给定的顺序原样透传（Go: Set(allowHeader, allows)）")
    void methodNotAllowedCarriesAllowHeaderInGivenOrder() {
        var resp = GoStyleRouterErrorResponses.methodNotAllowed(List.of("GET", "POST"));
        assertThat(resp.getHeaders().getFirst(HttpHeaders.ALLOW))
                .as("Go 的 allows 是有序字符串切片 ⇒ 顺序原样透传")
                .isEqualTo("GET, POST");
    }

    @Test
    @DisplayName("405：body 为 null ⇒ 容器不会补 Content-Type（Go 侧 405 无 Content-Type）")
    void methodNotAllowedHasNoContentType() {
        var resp = GoStyleRouterErrorResponses.methodNotAllowed(List.of("GET"));
        assertThat(resp.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE))
                .as("⚠️ Go 的 405 分支只 Set(Allow) + WriteHeader ⇒ 无 Content-Type")
                .isNull();
    }
}
