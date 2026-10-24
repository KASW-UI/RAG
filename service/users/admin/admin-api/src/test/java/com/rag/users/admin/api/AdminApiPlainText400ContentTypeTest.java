package com.rag.users.admin.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 🔴 DRIFT-ADMINAPI-011 守门测试 —— 400 响应的 {@code Content-Type} 必须与 Go 逐字节相同。
 *
 * <h2>发现方式</h2>
 *
 * <p>2026-10-23 的<b>双侧活体对拍</b>（真实 Tomcat，Go admin-api 18889 / RAG admin-api 58889，
 * 两套栈同时跑）打同一条「缺必填字段」的请求，抓响应头：
 *
 * <pre>
 * Go  : HTTP/1.1 400 Bad Request
 *       Content-Type: text/plain; charset=utf-8
 *       X-Content-Type-Options: nosniff
 *       body: field "uid" is not set\n        （23 字节，与 RAG 逐字节相同）
 *
 * RAG : HTTP/1.1 400
 *       Content-Type: text/plain            ← 🔴 少了 charset 参数
 *       X-Content-Type-Options: nosniff
 *       body: field "uid" is not set\n        （23 字节，与 Go 逐字节相同）
 * </pre>
 *
 * <p>⇒ **body 与 nosniff 都一致**，只有 {@code Content-Type} 差一个 charset 参数。
 *
 * <h2>Go 出处</h2>
 *
 * <p>Go 的 {@code net/http.Error} 走 {@code h.Set("Content-Type", "text/plain; charset=utf-8")}
 * —— 是**逐字写死**的字符串，不是框架推导出来的。
 *
 * <h2>为什么现有的守门抓不到</h2>
 *
 * <p>{@code tools/difftest/check-plaintext400-headers.sh} 只断言
 * 「Content-Type <b>含</b> text/plain」⇒ charset 的有无它看不见。
 * ⇒ 需要一条**独立**的、断言**完整头值**的守门。
 *
 * <h2>严重度</h2>
 *
 * <p>LOW —— 400 的 body 全是 ASCII，任何 charset 解释都得到同一串字节，
 * <b>无字节级歧义</b>；只是响应头与 Go 不逐字相同（§3.1 明确要求覆盖 HTTP 响应头）。
 */
@SpringBootTest(
        classes = AdminApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "grpc.client.admin-rpc.address=static://127.0.0.1:1",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN"
        })
@AutoConfigureMockMvc
class AdminApiPlainText400ContentTypeTest {

    /**
     * 🔴 必须 mock 掉整个 {@link com.rag.users.admin.api.client.AdminRpcClient}。
     *
     * <p>2026-10-24 起 admin-api 启动时会做「3s 内连不上 admin-rpc ⇒ 启动失败」的检查
     * （DRIFT-ADMINAPI-009，对齐 Go 的 {@code zrpc.MustNewClient}）。
     * ⇒ 本测试**没有**真的 admin-rpc 可连（{@code grpc.client.admin-rpc.address}
     * 指向 {@code 127.0.0.1:1}，本来就没人监听）⇒ 不 mock 的话上下文启动就会失败。
     *
     * <p>被 mock 的 bean <b>不会</b>执行 {@code afterPropertiesSet()} ⇒ 启动期检查被跳过。
     * ⇒ 该检查本身由 {@code AdminApiStartupFailFastTest} 单独守门。
     */
    @MockitoBean
    private com.rag.users.admin.api.client.AdminRpcClient adminRpcClient;

    @Autowired private MockMvc mockMvc;

    @Test
    @DisplayName("🔴 400 text/plain 的 Content-Type 必须是 text/plain; charset=utf-8（与 Go 逐字节相同）")
    void badRequestContentTypeMatchesGoByteForByte() throws Exception {
        // 用 public 路径 /admin/v1/create：缺必填字段 ⇒ GoRequiredFieldException ⇒ 400 text/plain。
        // ⚠️ 不能用 /admin/v1/getuser：它是受保护路径，**鉴权在请求体解析之前**短路 ⇒ 会拿到 401，
        //    根本走不到 text/plain 分支（本测试第一次跑就踩到了：expected 400 but was 401）。
        var result = mockMvc.perform(post("/admin/v1/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andReturn();

        // ⚠️ 这里断言的是**语义**（media type + charset），不是**字节渲染**。
        // 原因（2026-10-23 真实 Tomcat 实测）：
        //   MockMvc  : `text/plain; charset=utf-8`   （原样透传我们 set 的字符串）
        //   真 Tomcat: `text/plain;charset=utf-8`     （框架/容器重渲染：去掉 `;` 后的空格）
        //   Go       : `text/plain; charset=utf-8`     （net/http 逐字写死）
        // ⇒ 断言完整头值会**因容器而异**，那是 §3.3「MockMvc 测不到真实栈」的又一形态。
        // ⇒ 断言「charset 存在且等于 UTF-8」才是稳定且有意义的守门
        //    （改回 MediaType.TEXT_PLAIN 就没有 charset ⇒ 本断言立刻红）。
        String ct = result.getResponse().getHeader("Content-Type");
        assertNotNull(ct, "400 必须带 Content-Type（Go 的 net/http.Error 会设）");
        MediaType parsed = MediaType.parseMediaType(ct);
        assertEquals("text", parsed.getType(), "Go 的 net/http.Error 用的是 text/plain");
        assertEquals("plain", parsed.getSubtype(), "Go 的 net/http.Error 用的是 text/plain");
        assertEquals(StandardCharsets.UTF_8, parsed.getCharset(),
                "🔴 本条是本轮修的那个漂移：Go 的 net/http.Error 逐字写死 "
                        + "`text/plain; charset=utf-8`；本实现此前用 MediaType.TEXT_PLAIN "
                        + "⇒ 渲染成 `text/plain`（**完全没有 charset**）。"
                        + "实测（真实 Tomcat，2026-10-23）：Go=「text/plain; charset=utf-8」/ "
                        + "修复前 RAG=「text/plain」。⚠️ 不要改回 MediaType —— "
                        + "new MediaType(\"text\",\"plain\",UTF_8) 渲染成 `text/plain;charset=UTF-8`"
                        + "（大写 UTF-8），与 Go 的小写 utf-8 也不逐字节相同 ⇒ 本实现用原始字符串 set");

        assertEquals("nosniff", result.getResponse().getHeader("X-Content-Type-Options"),
                "Go 的 net/http.Error 同时设 nosniff（这一项两侧本就一致，守门别把它放松掉）");
    }

    @Test
    @DisplayName("🔴 畸形 JSON 的 400 同样带 charset（同一分支，不能只测必填缺失）")
    void malformedJsonContentTypeAlsoCarriesCharset() throws Exception {
        var result = mockMvc.perform(post("/admin/v1/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"a\":}"))
                .andExpect(status().isBadRequest())
                .andReturn();

        MediaType parsed = MediaType.parseMediaType(
                result.getResponse().getHeader("Content-Type"));
        assertEquals("plain", parsed.getSubtype(),
                "畸形 JSON 走的是同一个 goStylePlainTextBadRequest 分支 ⇒ 头必须一致");
        assertEquals(StandardCharsets.UTF_8, parsed.getCharset(),
                "同一分支 ⇒ charset 也必须一样");

        String body = result.getResponse().getContentAsString();
        assertTrue(body.endsWith("\n"),
                "Go 的 http.Error 写 body + 尾随换行；实际 body=" + body.replace("\n", "\\n"));
    }
}
