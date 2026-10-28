package com.rag.favorite.favorite.api.probe;

import com.rag.favorite.favorite.api.client.FavoriteRpcClient;
import com.rag.favorite.favorite.api.support.RealTomcatPort;
import com.rag.users.common.jwt.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * 🔴 **诊断探针**：把「query 段数闸门」这件事的**服务端行为**与**客户端行为**分开量。
 *
 * <h2>为什么需要它（2026-10-28 · G3 实测踩出来的）</h2>
 * <p>第一次跑 {@code FavoriteApiRealTomcatGateTest} 时，用例
 * {@code querySegmentLimitIsUniformAcrossEndpoints} 挂了 **982.5 s**
 * （{@code HttpTimeoutException}，而请求超时只配了 **30 s** ⇒ 超时**迟到了 950 s** 才触发）。
 * ⇒ ⇒ 那一次**无法区分**两种可能：
 * <ol>
 *   <li><b>服务端真的挂住了</b>（⇒ 那就是真缺陷，必须修代码）；</li>
 *   <li><b>客户端侧假挂死</b>（⇒ 那是环境/传输层的事，代码没毛病）。</li>
 * </ol>
 * ⇒ ⇒ **本探针就是那个「判别动作」**：用**同一个请求**换三种传输层（裸 socket · JDK
 * {@code HttpClient} · {@code HttpURLConnection}）各打一次 ⇒ ⇒ **服务端毫秒级/秒级返回正确响应**
 * ⇒ ⇒ 结论 = **服务端没问题 ⇒ 那次挂死是环境侧的**（当时同时有 3 条 maven 在跑 ⇒ JVM 饥饿 ⇒
 * JDK 的超时计时器线程拿不到 CPU ⇒ 超时迟迟不触发）。
 * ⇒ ⇒ 复跑（独占窗口）⇒ **13 条 / 0 failures / 0 errors / 42.87 s / BUILD SUCCESS** ⇒ 坐实。
 *
 * <h2>它测什么（5 个对照 ＋ 2 个传输层对照）</h2>
 * <pre>
 *   A  /folder/list 10001 段 + token  ⇒ 期望 200 + {"code":5003}
 *   B  /item/list   10001 段 + token  ⇒ 期望 200 + {"code":5003}（覆盖面与端点无关）
 *   C  /item/list   10000 段 + token  ⇒ 期望 200 + 业务信封（**不是** 5003 ⇒ 放行进了业务层）
 *   D  /item/list   10001 段 无 token  ⇒ 期望 401（鉴权优先于段数判据）
 *   E  /folder/list 10001 段 无 token  ⇒ 期望 401（同上）
 *   F  B 同一请求，JDK HttpClient       ⇒ 期望 200 + 5003（客户端侧也要能读到）
 *   G  B 同一请求，HttpURLConnection    ⇒ 期望 200 + 5003
 *   H  C 同一请求，JDK HttpClient       ⇒ 期望 200 + 业务信封
 * </pre>
 * ⇒ ⇒ **A/B/C/D/E 用裸 socket**（拿到的是**未经客户端加工的原始字节** ⇒ 能直接看到
 * 共享层 {@code GoStyleClientDisconnectProbe} 写的 {@code 103 Early Hints} 与最终响应）。
 *
 * <h2>🔴 为什么类名<b>不带</b> {@code Test} 后缀</h2>
 * <p>surefire 的<b>默认</b> includes 是四个「类名形状」：以 {@code Test} 开头、或以
 * {@code Test} / {@code Tests} / {@code TestCase} 结尾（文件名层面，<b>不含</b>目录前缀）
 * ⇒ ⇒ 本类<b>不进默认回归</b>
 * ⇒ ⇒ **对常规回归零影响**（它会起一个真 Tomcat + 一次 Redis 连接尝试，约 20~40 s）
 * ⇒ ⇒ 需要时**显式**跑：
 * <pre>
 *   mvn -B -o -pl service/favorite/favorite-api -am test \
 *       -Dtest='QuerySegmentRawSocketProbe' -Dsurefire.failIfNoSpecifiedTests=false \
 *       -DargLine="-Xmx2g"
 * </pre>
 * ⇒ ⇒ 仓内同款做法：`hot-rpc` 的产物层守门类用 {@code *IT} 后缀（同样是「默认不捞、按需显式跑」）。
 *
 * <h2>输出</h2>
 * <p>每条一行 {@code RAWPROBE <label> => …}（进 surefire 的 {@code system-out}）⇒ ⇒
 * 拿到 {@code target/surefire-reports/*.xml} 即可复看。
 *
 * <h2>它<b>不是</b>守门</h2>
 * <p>它是**诊断**探针，**不参与**任何「跑绿就算过」的判定 ⇒ ⇒ 真正的守门是
 * {@code FavoriteApiRealTomcatGateTest}（13 条 · 真 Tomcat · 真 JWT · 真 HTTP 客户端 · 变异 3/3）。
 */
@SpringBootTest(
        classes = com.rag.favorite.favorite.api.FavoriteApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
        properties = {
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "spring.cloud.nacos.discovery.enabled=false",
                "grpc.client.favorite-rpc.address=static://127.0.0.1:65535",
                "grpc.client.favorite-rpc.negotiation-type=plaintext",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN",
                "rag.protection.max-bytes=1024",
                "observability.http-timeout=30s",
                "rag.protection.cpu-shedding.enabled=false"
        })
public class QuerySegmentRawSocketProbe {

    private static final String ACCESS_SECRET = "ae0536f9-6450-4606-8e13-5a19ed505da0";
    private static final String ITEM_LIST = "/favorite/v1/item/list";
    private static final String FOLDER_LIST = "/favorite/v1/folder/list";

    @DynamicPropertySource
    static void serverPort(DynamicPropertyRegistry registry) {
        registry.add("server.port", RealTomcatPort::pick);
    }

    @MockitoBean
    private FavoriteRpcClient rpcClient;

    @org.springframework.boot.test.web.server.LocalServerPort
    private int port;

    /** 「1 个非空值 + N 个空值」—— 与守门里的 helper 同口径（段数 = N+1）。 */
    private static String oneNonEmptyPlusEmpty(int emptyCount) {
        StringBuilder sb = new StringBuilder("folderId=7");
        for (int i = 0; i < emptyCount; i++) {
            sb.append("&folderId=");
        }
        return sb.toString();
    }

    private String token() {
        return JwtUtil.getToken(ACCESS_SECRET, System.currentTimeMillis() / 1000L, 3600L, 42L);
    }

    @Test
    void probeAllTransports() {
        probeRaw("A_folder_10001_token", FOLDER_LIST, oneNonEmptyPlusEmpty(10000), true);
        probeRaw("B_item_10001_token", ITEM_LIST, oneNonEmptyPlusEmpty(10000), true);
        probeRaw("C_item_10000_token", ITEM_LIST, oneNonEmptyPlusEmpty(9999), true);
        probeRaw("D_item_10001_notoken", ITEM_LIST, oneNonEmptyPlusEmpty(10000), false);
        probeRaw("E_folder_10001_notoken", FOLDER_LIST, oneNonEmptyPlusEmpty(10000), false);
        probeJdk("F_jdk_item_10001", ITEM_LIST, oneNonEmptyPlusEmpty(10000), true);
        probeUrlConn("G_urlconn_item_10001", ITEM_LIST, oneNonEmptyPlusEmpty(10000), true);
        probeJdk("H_jdk_item_10000", ITEM_LIST, oneNonEmptyPlusEmpty(9999), true);
    }

    /** 裸 socket：拿到未经客户端加工的原始字节（能看到共享层写的 {@code 103 Early Hints}）。 */
    private void probeRaw(String label, String path, String query, boolean withToken) {
        String target = path + (query == null ? "" : "?" + query);
        StringBuilder req = new StringBuilder();
        req.append("GET ").append(target).append(" HTTP/1.1\r\n");
        req.append("Host: 127.0.0.1:").append(port).append("\r\n");
        if (withToken) {
            req.append("Authorization: Bearer ").append(token()).append("\r\n");
        }
        req.append("Connection: close\r\n\r\n");

        long t0 = System.nanoTime();
        String outcome;
        try (Socket sock = new Socket("127.0.0.1", port)) {
            sock.setSoTimeout(20000);
            OutputStream out = sock.getOutputStream();
            out.write(req.toString().getBytes(StandardCharsets.ISO_8859_1));
            out.flush();
            InputStream in = sock.getInputStream();
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            String raw = bos.toString(StandardCharsets.ISO_8859_1);
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            int bodyAt = raw.indexOf("\r\n\r\n");
            String head = raw.substring(0, Math.min(raw.length(), 120)).replace("\r\n", "\\r\\n");
            String body = bodyAt < 0 ? "<no-header-sep>" : raw.substring(bodyAt + 4);
            outcome = "OK bytes=" + raw.length() + " ms=" + ms + " head=[" + head + "] body=["
                    + body.replace("\r\n", "\\r\\n") + "]";
        } catch (Exception e) {
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            outcome = "EX ms=" + ms + " " + e.getClass().getSimpleName() + ": " + e.getMessage();
        }
        System.out.println("RAWPROBE " + label + " reqlineLen=" + target.length()
                + " withToken=" + withToken + " => " + outcome);
    }

    /** JDK {@code java.net.http.HttpClient}（守门默认走的传输层）。 */
    private void probeJdk(String label, String path, String query, boolean withToken) {
        String url = "http://127.0.0.1:" + port + path + "?" + query;
        long t0 = System.nanoTime();
        String outcome;
        try {
            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(5))
                    .version(java.net.http.HttpClient.Version.HTTP_1_1)
                    .build();
            java.net.http.HttpRequest.Builder b = java.net.http.HttpRequest
                    .newBuilder(java.net.URI.create(url))
                    .timeout(java.time.Duration.ofSeconds(30)).GET();
            if (withToken) {
                b.header("Authorization", "Bearer " + token());
            }
            java.net.http.HttpResponse<String> resp =
                    client.send(b.build(), java.net.http.HttpResponse.BodyHandlers.ofString());
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            outcome = "OK status=" + resp.statusCode() + " ms=" + ms + " body=["
                    + (resp.body() == null ? "" : resp.body()) + "]";
        } catch (Exception e) {
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            outcome = "EX ms=" + ms + " " + e.getClass().getSimpleName() + ": " + e.getMessage();
        }
        System.out.println("RAWPROBE " + label + " => " + outcome);
    }

    /** {@code HttpURLConnection}（透明忽略 1xx ⇒ 回落传输层）。 */
    private void probeUrlConn(String label, String path, String query, boolean withToken) {
        String url = "http://127.0.0.1:" + port + path + "?" + query;
        long t0 = System.nanoTime();
        String outcome;
        try {
            java.net.HttpURLConnection conn =
                    (java.net.HttpURLConnection) java.net.URI.create(url).toURL().openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(30000);
            conn.setRequestMethod("GET");
            if (withToken) {
                conn.setRequestProperty("Authorization", "Bearer " + token());
            }
            conn.connect();
            int status = conn.getResponseCode();
            java.io.InputStream in = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while (in != null && (n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            conn.disconnect();
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            outcome = "OK status=" + status + " ms=" + ms + " body=[" + bos.toString("UTF-8") + "]";
        } catch (Exception e) {
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            outcome = "EX ms=" + ms + " " + e.getClass().getSimpleName() + ": " + e.getMessage();
        }
        System.out.println("RAWPROBE " + label + " => " + outcome);
    }
}
