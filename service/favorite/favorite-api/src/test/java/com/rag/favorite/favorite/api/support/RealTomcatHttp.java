package com.rag.favorite.favorite.api.support;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.ProtocolException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.net.ssl.SSLSession;

/**
 * 🔴 真 Tomcat 守门用的 HTTP 传输层 —— <b>JDK {@code HttpClient} 优先，撞 1xx 上限则回落
 * {@link HttpURLConnection}</b>。
 *
 * <h2>🔴 为什么需要这个回落（2026-10-27 实测抓到的真凶）</h2>
 * <p>本域 4 条真 Tomcat 用例（{@code FavoriteApiRealTomcatGateTest} 3 条 ＋
 * {@code FavoriteApiEnvelopeParityTest} 1 条）长期红在
 * {@code java.net.ProtocolException: Too many interim responses received: 9 > 8}。
 * ⇒ ⇒ <b>裸 socket 实测（不是推测）</b>：
 * <pre>
 *   GET /favorite/v1/item/list?folderId=0&amp;…（2049 个参数）
 *   ← HTTP/1.1 103 Early Hints   × 11 条
 *   ← HTTP/1.1 200  {"code":5003,…}
 * </pre>
 * ⇒ ⇒ <b>Tomcat 11.0.24 在这类请求上连发 {@code 103 Early Hints}</b>（<b>不是</b> {@code 100 Continue}）
 * ⇒ ⇒ 而 <b>JDK {@code java.net.http.HttpClient} 对 interim(1xx) 响应的上限是 8 条且硬编码、不可配</b>
 * （实测 {@code jdk.httpclient.*} 属性表里<b>没有</b>对应开关）⇒ ⇒ 第 9 条就抛
 * {@code ProtocolException} ⇒ ⇒ <b>客户端根本读不到应用层的响应</b>。
 *
 * <h2>🔴 关键：<b>被测行为本身是对的</b></h2>
 * <p>裸 socket 实测最终响应 = <b>{@code HTTP 200 + {"code":5003,…}}</b>
 * ⇒ ⇒ 正是 Go 语义（2049 个非空 form 值 ⇒ {@code maxFormParamCount} 触发 ⇒ 200 + 5003 业务信封）
 * ⇒ ⇒ <b>红的是「测试的传输层客户端」，不是应用层、也不是共享层</b>。
 * ⇒ ⇒ {@link HttpURLConnection} 会<b>透明忽略 1xx</b> ⇒ ⇒ 回落分支拿到的就是应用层的真实响应。
 *
 * <h2>⚠️ 本回落只掩盖「客户端上限」，<b>不掩盖 wire 层偏差</b></h2>
 * <p>Go 的 {@code net/http} 服务端<b>不发</b> {@code 103 Early Hints} ⇒ ⇒ RAG 这条 1xx 是
 * <b>wire 层与 Go 的差异</b> ⇒ ⇒ 已作为跨域发现上报（收口在共享层）。
 * ⇒ ⇒ 本类的存在<b>只是</b>让本域守门能观测到应用层响应 ⇒ <b>不是</b>「该偏差已修」。
 */
public final class RealTomcatHttp {

    private RealTomcatHttp() {
    }

    /**
     * 发一个请求：JDK {@link HttpClient} 优先 ⇒ 撞 1xx 上限时回落 {@link HttpURLConnection}。
     *
     * @param req  JDK 侧构造的请求（回落分支只支持<b>无请求体</b>的 GET/HEAD）
     * @return 应用层的真实响应（1xx 已被透明忽略）
     */
    public static HttpResponse<String> send(HttpRequest req) throws Exception {
        try {
            return jdkClient().send(req, HttpResponse.BodyHandlers.ofString());
        } catch (ProtocolException e) {
            if (!isInterimLimit(e) || req.bodyPublisher().isPresent()) {
                // ⚠️ 只在「1xx 上限」这一种形态上回落；
                //    带请求体的请求回落不了（bodyPublisher 不可重放）⇒ ⇒ 原样上抛。
                throw e;
            }
            return viaUrlConnection(req);
        }
    }

    /** JDK 的 1xx 上限异常长这样：{@code Too many interim responses received: 9 > 8}。 */
    private static boolean isInterimLimit(ProtocolException e) {
        return e.getMessage() != null && e.getMessage().contains("Too many interim responses received");
    }

    private static HttpClient jdkClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .version(HttpClient.Version.HTTP_1_1)   // 与 Go 侧默认一致，且避免 h2c 升级干扰响应头
                .build();
    }

    /**
     * 回落传输：{@link HttpURLConnection} —— 它<b>透明忽略 1xx</b>（含 {@code 103 Early Hints}）
     * ⇒ ⇒ 拿到的是最终响应。
     */
    private static HttpResponse<String> viaUrlConnection(HttpRequest req) throws Exception {
        URL url = URI.create(req.uri().toString()).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(30000);
        conn.setRequestMethod(req.method());
        req.headers().map().forEach((k, v) -> {
            if (!"host".equalsIgnoreCase(k) && !"content-length".equalsIgnoreCase(k)
                    && !"connection".equalsIgnoreCase(k)) {
                conn.setRequestProperty(k, String.join(",", v));
            }
        });
        conn.setRequestProperty("Connection", "close");
        conn.connect();

        int status = conn.getResponseCode();
        InputStream in = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
        String body = "";
        if (in != null) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            in.close();
            body = bos.toString(StandardCharsets.UTF_8);
        }
        Map<String, List<String>> headerMap = conn.getHeaderFields();
        conn.disconnect();
        return new UrlConnectionResponse(req, status, body, headerMap);
    }

    /**
     * 把 {@link HttpURLConnection} 的结果包成 JDK 的 {@link HttpResponse} ——
     * 这样<b>调用点一行都不用改</b>（它们只读 {@code statusCode()} / {@code body()} / {@code headers()}）。
     */
    private static final class UrlConnectionResponse implements HttpResponse<String> {

        private final HttpRequest request;
        private final int status;
        private final String body;
        private final HttpHeaders headers;

        UrlConnectionResponse(HttpRequest request, int status, String body,
                              Map<String, List<String>> rawHeaders) {
            this.request = request;
            this.status = status;
            this.body = body;
            // ⚠️ JDK 没有公开的 HttpHeaders 构造器 ⇒ 用 of(Map, predicate) 静态工厂。
            //    ⚠️ HttpURLConnection 的 getHeaderFields() 里**状态行**那一项的 key 是 null ⇒ 必须剔掉。
            java.util.Map<String, List<String>> clean = new java.util.LinkedHashMap<>();
            rawHeaders.forEach((k, v) -> {
                if (k != null && v != null) {
                    clean.put(k, v);
                }
            });
            this.headers = HttpHeaders.of(clean, (k, v) -> true);
        }

        @Override
        public int statusCode() {
            return status;
        }

        @Override
        public HttpRequest request() {
            return request;
        }

        @Override
        public Optional<HttpResponse<String>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return headers;
        }

        @Override
        public String body() {
            return body;
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return request.uri();
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }
}
