package com.rag.favorite.favorite.api.support;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 🔴 真 Tomcat 守门用的 HTTP 传输层 —— <b>只用 JDK {@link HttpClient}，刻意不回落</b>。
 *
 * <h2>🔴 归因订正（2026-10-28 · G3）：1xx 是<b>共享层探针</b>写的，<b>不是 Tomcat</b></h2>
 * <p>本域 4 条真 Tomcat 用例（{@code FavoriteApiRealTomcatGateTest} 3 条 ＋
 * {@code FavoriteApiEnvelopeParityTest} 1 条）曾长期红在
 * {@code java.net.ProtocolException: Too many interim responses received: 9 > 8}。
 * ⇒ ⇒ <b>裸 socket 实测（不是推测）</b>：
 * <pre>
 *   GET /favorite/v1/item/list?folderId=0&amp;…（2049 个参数）
 *   ← HTTP/1.1 103 Early Hints   × 11 条
 *   ← HTTP/1.1 200  {"code":5003,…}
 * </pre>
 * ⇒ ⇒ ⚠️ <b>原归因写「Tomcat 11.0.24 在这类请求上连发 {@code 103 Early Hints}」是错的</b>
 * （2026-10-28 订正）：那些 1xx 是<b>共享层的「客户端断连主动探测」</b>写的 ——
 * {@code service/common/src/main/java/com/rag/common/observability/GoStyleClientDisconnectProbe.java:234}
 * 裸写 {@code "HTTP/1.1 103 Early Hints\r\n\r\n"}（<b>已签字偏离 SD-2026-10-25-04</b>）。
 * ⇒ ⇒ <b>独立佐证</b>（共享层自己的订正 · {@code 15cf93a}）：<b>无 Authorization 的 401 路径
 * 实测零 1xx</b> ⇒ ⇒ 若是容器自发，401 也该有 ⇒ ⇒ 1xx 只出现在「进了共享层中间件的请求」上。
 *
 * <h2>🔴 为什么现在不需要回落</h2>
 * <p>共享层已把 1xx 行数设为<b>每请求上界</b>（{@code 15cf93a} · 2026-10-27 19:06）：
 * {@code DEFAULT_MAX_PROBE_LINES = 4}（<b>取 4 而非客户端上限 8 ⇒ 留一半余量</b>）
 * ⇒ ⇒ 1xx 行数 ≤ 4 &lt; JDK 客户端上限 8 ⇒ ⇒ <b>JDK HttpClient 读得到最终响应</b>。
 *
 * <h2>🔴 本类<b>刻意没有</b>「撞上限就回落 {@code HttpURLConnection}」的分支</h2>
 * <p>回落分支会<b>吞掉共享层的回归</b>：共享层若把 1xx 行数上界撤掉，本域这 4 条用例
 * <b>照样绿</b>（回落让客户端读到了响应）⇒ ⇒ 违反「<b>跑绿 ≠ 判据活着</b>」。
 * ⇒ ⇒ <b>约定：再看到 {@code ProtocolException: Too many interim responses received: N > 8}
 * ⇒ 那是【共享层 1xx 行数上界回归】，<b>不要在本域加回落兜底</b> ⇒ 修共享层。</b>
 * ⇒ ⇒ 旧回落实现（{@code HttpURLConnection} 分支 ＋ 其响应包装类）已在 2026-10-28 移除。
 *
 * <h2>⚠️ 跑本域这 4 条用例<b>必须带 {@code -am}</b></h2>
 * <p>它们同时依赖共享层的两处改动：① 1xx 行数上界（{@code 15cf93a} · 2026-10-27 19:06）
 * ② 容器参数上限抬到 10001（{@code cd893d1} · 2026-10-27 13:08）
 * ⇒ ⇒ 若 {@code ~/.m2} 里是旧快照（本地实测旧快照 = <b>2026-10-27 12:00</b>，早于两处改动）
 * ⇒ ⇒ 用例会<b>假红</b>（不是本域坏了）。
 * ⇒ ⇒ 复现命令必须带 {@code -am}（从源码树构建共享层）。
 */
public final class RealTomcatHttp {

    private RealTomcatHttp() {
    }

    /**
     * 发一个请求 —— <b>只有 JDK {@link HttpClient} 一条路</b>（刻意不回落，理由见类注释）。
     *
     * @param req  JDK 侧构造的请求
     * @return 应用层的真实响应
     */
    public static HttpResponse<String> send(HttpRequest req) throws Exception {
        return jdkClient().send(req, HttpResponse.BodyHandlers.ofString());
    }

    private static HttpClient jdkClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .version(HttpClient.Version.HTTP_1_1)   // 与 Go 侧默认一致，且避免 h2c 升级干扰响应头
                .build();
    }
}
