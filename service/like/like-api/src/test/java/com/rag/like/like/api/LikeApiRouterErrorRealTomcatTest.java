package com.rag.like.like.api;

import com.rag.like.like.api.client.LikeRpcClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * like-api 的 <b>404 / 405 字节级守门</b>（真 Tomcat）。
 *
 * <h2>为什么必须是真容器</h2>
 *
 * <p>这两条断言的全部内容就是<b>响应头与 body 的字节</b>，而 MockMvc 在这三处都不可信：
 * <ol>
 *   <li>{@code Content-Type} —— MockMvc 不驱动容器选 converter，
 *       「我显式设的 text/plain 有没有真的发出去」测不到；</li>
 *   <li>{@code Content-Length} —— 由 Tomcat 依据实际写入字节数计算，MockMvc 不参与；</li>
 *   <li><b>body 是否被容器补写</b> —— 405 这类「body 为 null」的响应，
 *       容器会不会自作主张补一个空 body 或补 Content-Type，只有真容器说了算。
 *       这正是 DRIFT-024（text/plain 400 丢 Content-Type/nosniff）当初漏网的原因。</li>
 * </ol>
 * ⇒ 本类用 {@code RANDOM_PORT} + JDK HttpClient 读<b>原始</b>响应。
 *
 * <h2>Go 侧证据</h2>
 * <pre>
 *   404: router/patrouter.go:73,93-97 → http.NotFound → http.Error(w,"404 page not found",404)
 *        net/http.Error: Del("Content-Length") / Set("Content-Type","text/plain; charset=utf-8")
 *                        / Set("X-Content-Type-Options","nosniff") / WriteHeader / Fprintln
 *        ⇒ body = "404 page not found\n"，含尾随换行，全长 20 字节
 *
 *   405: router/patrouter.go:79-82 → w.Header().Set("Allow", allows); w.WriteHeader(405)
 *        ⇒ 裸状态码，**无 body、无 Content-Type**
 * </pre>
 *
 * <p>⚠️ <b>404 与 405 的形状完全相反</b>（一个有 body 有 Content-Type，一个都没有）。
 * 这是本次修复最容易「顺手统一」而造成新漂移的地方，故两条都逐项钉死。
 */
@SpringBootTest(
        classes = LikeApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                // ⚠️ 必须关掉 Nacos：RANDOM_PORT 会真的把 webServer 启起来，
                //   naming client 随即注册并在 STARTING 态报
                //   「Client not connected」而让 webServerStartStop 启动失败（实测）。
                //   MOCK 环境不启容器，所以不需要这一行 —— 两种环境测试的 property 集合不可互相照抄。
                "spring.cloud.nacos.discovery.enabled=false",
                "grpc.client.like-rpc.address=static://127.0.0.1:65535",
                "grpc.client.like-rpc.negotiation-type=plaintext",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN"
        })
class LikeApiRouterErrorRealTomcatTest {

    /** 与 application.yml 的 {@code like-api.user-auth.access-secret} 同一份。 */
    private static final String SECRET = "ae0536f9-6450-4606-8e13-5a19ed505da0";

    @LocalServerPort
    private int port;

    @MockitoBean
    private LikeRpcClient likeRpcClient;

    private HttpResponse<String> call(String method, String path) throws Exception {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10));
        return client.send(
                b.method(method, HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    @DisplayName("404：body 逐字节等于 \"404 page not found\\n\"，且带 text/plain + nosniff")
    void notFoundMatchesGoBytes() throws Exception {
        HttpResponse<String> r = call("GET", "/like/v1/no-such-endpoint");

        assertEquals(404, r.statusCode());
        assertEquals("404 page not found\n", r.body(),
                "Go 侧是 http.Error + fmt.Fprintln ⇒ body 带尾随换行；"
                        + "若少了这个 \\n，说明我们用的是 .build() 空 body 而不是共享实现");

        // ⚠️ 这里刻意**解析后**比对，而不是比原始字符串：真 Tomcat 会把头规范化成
        //    "text/plain;charset=utf-8"（去掉分号后的空格），而 Go 的 net/http 是逐字写出
        //    "text/plain; charset=utf-8"。按 RFC 9110，参数分隔符两旁的 OWS 不显著 ⇒ 两者语义相同。
        //    ⇒ 比原始字符串会得到一个「必然红、且红了也不代表有错」的断言（已实测）。
        //    真正要防的回归是「charset 整个丢了」或「变成 text/html」，那解析后比对都能抓到。
        String ctRaw = r.headers().firstValue("content-type").orElse(null);
        assertNotNull(ctRaw, "Go 的 http.Error 无条件写 Content-Type: text/plain; charset=utf-8");
        // 去空格后比对：Tomcat 规范化为 "text/plain;charset=utf-8"，Go 逐字写 "text/plain; charset=utf-8"
        assertEquals("text/plain;charset=utf-8", ctRaw.replace(" ", ""),
                "Go 的 http.Error 写死 text/plain + charset=utf-8；"
                        + "⚠️ charset 整个丢掉、或退化成 text/html，都是 DRIFT-024 同款问题");

        assertEquals("nosniff", r.headers().firstValue("X-Content-Type-Options").orElse(null),
                "net/http.Error 无条件写 X-Content-Type-Options: nosniff");
    }

    @Test
    @DisplayName("405：裸状态码 + Allow 头，**无 body、无 Content-Type**（与 404 形状相反）")
    void methodNotAllowedIsBareStatusWithAllowHeader() throws Exception {
        // /like/v1/getlikecount 只注册了 POST（Go likecenter 的 6 个端点全是 POST）
        HttpResponse<String> r = call("GET", "/like/v1/getlikecount");

        assertEquals(405, r.statusCode());
        assertEquals("POST", r.headers().firstValue("Allow").orElse(null),
                "Go patrouter.go:79-82 会写 Allow；"
                        + "⚠️ Allow 的**取值顺序**在 Go 侧由 map 遍历决定（不确定），本实现刻意不排序");

        assertEquals("", r.body(),
                "Go 侧只有 WriteHeader、没有 Fprintln ⇒ body 必须为空。"
                        + "若这里非空，说明我们多写了 body —— 那比缺 Allow 更偏离 Go");

        assertNull(r.headers().firstValue("Content-Type").orElse(null),
                "Go 侧 405 从未设置 Content-Type；body 为空时容器也不应补。"
                        + "⚠️ 这条与 404 恰好相反，两条一起钉就是为了防止「顺手统一」");
    }

    @Test
    @DisplayName("404 / 405 都**不经过鉴权**（Go 的 bindRoute 只把 chain 挂在匹配上的路由上）")
    void routerErrorsBypassAuth() throws Exception {
        // ⚠️ 刻意不带 Authorization。Go 侧 patrouter.go:73,79 的 404/405 分支
        // 不进 bindRoute 构造的 chain（engine.go:104-118 只挂到匹配路由上）⇒ 不经 auth。
        // 若 RAG 侧改成 401/405 带鉴权，就是把「路由没匹配上」伪装成「你没登录」。
        assertEquals(404, call("GET", "/like/v1/nope").statusCode(),
                "⚠️ 2026-10-22 修复前这条返回 **401**：Boot 的静态资源处理器匹配 /**，"
                        + "未注册路径也拿到了 handler ⇒ 鉴权拦截器先跑，404 被遮蔽。Go 侧恒 404。");

        // 反向对照：路径存在但没带 token ⇒ 必须仍是 401，不能被 405/404 分支吞掉
        assertEquals(401, call("POST", "/like/v1/getlikecount").statusCode());
    }

    @Test
    @DisplayName("Content-Length 与实际 body 长度一致（20）")
    void notFoundContentLengthMatchesBody() throws Exception {
        HttpResponse<String> r = call("GET", "/like/v1/nope2");
        String cl = r.headers().firstValue("Content-Length").orElse(null);
        assertNotNull(cl, "真容器对非空 body 必须给 Content-Length");
        assertEquals(r.body().getBytes(java.nio.charset.StandardCharsets.UTF_8).length, Integer.parseInt(cl),
                "Content-Length 必须等于实际写入字节数，否则分块编码/截断会让客户端读错");
        assertTrue(r.body().endsWith("\n"), "Go 用 Fprintln ⇒ body 以换行结尾");
    }
}