package com.rag.comment.comment.api.client;

import com.rag.common.protection.GoStyleProtectionProperties;
import com.rag.comment.comment.proto.CommentServiceGrpc;
import io.grpc.Channel;
import io.grpc.ClientInterceptors;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 🔴 <b>MISMATCH-API-03 的守门网</b> —— 锁住「comment-api → comment-rpc 的 2 秒 deadline 上限」。
 *
 * <h2>本轮修掉的漂移</h2>
 * <p><b>Go 事实</b>：{@code go-zero@v1.10.2/zrpc/config.go:31}
 * {@code RpcClientConf.Timeout int64 `json:",default=2000"}` ⇒ <b>2000ms</b>；
 * {@code zrpc/internal/client.go:115-116} 装 {@code TimeoutInterceptor(timeout)}；
 * {@code etc/commentservice.yaml:21-23} <b>没覆盖</b> {@code Timeout} ⇒ 生效值 = 2s。
 * <p><b>修复前 RAG 现状</b>：{@code CommentRpcClient.commentRpc()} 只套
 * {@code GoStyleRequestDeadline.applyTo(...)} ⇒ 取自 HTTP 预算 <b>3s</b>
 * （{@code application.yml:140} {@code http-timeout: 3s}）⇒ <b>有效 deadline = 3s，无 2s 上限</b>。
 * <p><b>可观察后果</b>：下游耗时落在 <b>2.0–3.0s</b> 时 Go 返 {@code DEADLINE_EXCEEDED}、<b>RAG 成功</b>。
 *
 * <h2>🆕 2026-10-31 · G3 lane 第十三轮：上界的<b>承担者</b>也换了</h2>
 * <p>上一轮（补 2s 上限）是<b>域侧自带</b>一份封顶（{@code min(2s, 请求剩余预算)}）
 * ⇒ ⇒ 那是<b>第二个上界来源</b> ⇒ ⇒ 与共享层「唯一实现」的形态冲突
 * （共享层棘轮 {@code GoStyleClientCallTimeoutSingleSourceGateTest} 判据 ④：
 * 「cap 的来源必须是共享层参数，域侧私有常量 = 违规」）。
 * ⇒ ⇒ 本轮：<b>域侧那份删掉</b>，上界改由<b>共享层</b>承担
 * （{@code GoStyleOutboundDeadlineInterceptor}，channel 级 ⇒ 装在<b>每个</b>出向调用上），
 * 本域<b>只配参数</b>：{@code rag.protection.outbound-deadline.cap-millis = 2000}。
 * ⇒ ⇒ <b>本类的四个行为用例全部改走共享层路径</b>（stub 建在
 * 「装了生产拦截器的 channel」上）⇒ ⇒ 它们现在同时证明：
 * <b>① 域 yml 的那个键真被读到；② 共享层真在出向调用上生效。</b>
 *
 * <h2>为什么断言「行为」而不是「CallOptions 上挂了 deadline」</h2>
 * <p>本仓自己的纪律（{@code service/common/.../GoStyleRequestDeadlineTest.java:56}）：
 * 「断言 stub 上挂了 CallOptions」是断言<b>配置</b>，不是断言<b>行为</b>。
 * ⇒ 本类起一个<b>真 in-process gRPC server</b>，服务端方法<b>故意睡到 deadline 之后</b>，
 * 观察客户端调用**以什么结局收场**。
 *
 * <h2>三个用例各自能区分什么</h2>
 * <table border="1">
 *   <caption>「有 2s 上界」与「没有 2s 上界」的判别设计</caption>
 *   <tr><th>用例</th><th>HTTP 预算</th><th>服务端睡眠</th><th>有 2s 上界</th><th>回退成 applyTo（变异）</th></tr>
 *   <tr><td>①</td><td>无</td><td>2.5s</td><td><b>DEADLINE_EXCEEDED</b></td><td><b>OK</b>（无 deadline ⇒ 调用成功）</td></tr>
 *   <tr><td>②</td><td>30s</td><td>2.5s</td><td><b>DEADLINE_EXCEEDED</b></td><td><b>OK</b>（30s 预算够用）</td></tr>
 *   <tr><td>③</td><td>3s</td><td>5s</td><td><b>DEADLINE_EXCEEDED</b> 且 <b>≈2s</b></td>
 *       <td>DEADLINE_EXCEEDED 但 <b>≈3s</b> ⇒ 靠<b>耗时</b>区分</td></tr>
 * </table>
 *
 * <p>⚠️ 用例 ①② 的判别靠<b>结局码不同</b>（DEADLINE_EXCEEDED vs OK）⇒ 不依赖计时，稳定。
 * <p>⚠️ 用例 ③ 与生产形态完全一致（HTTP 预算 3s），但两边的结局码<b>相同</b>
 * ⇒ 只能靠耗时区分（2s 上限 vs 3s 预算）⇒ 阈值取 2.6s，两侧各留 0.4s 以上余量。
 *
 * <p>🧬 <b>变异实测</b>：① 把共享层 {@code GoStyleOutboundDeadlineInterceptor} 的
 * {@code capMillis} 写死成 0（= 不启用）⇒ 用例 ①② 变红（拿到 OK 而不是 DEADLINE_EXCEEDED）；
 * ② 把本域 yml 的 {@code cap-millis} 行删掉 ⇒ 同样变红（cap 落到默认 0）
 * ⇒ ⇒ 守门网不是假绿。
 */
class CommentApiRpcDeadlineGateTest {

    /** 本域的 env 覆盖旋钮（与本域 {@code application.yml} 里的占位符同名）。 */
    private static final String ENV_CAP = "COMMENT_API_OUTBOUND_DEADLINE_CAP_MILLIS";

    /** Go 侧 {@code zrpc/config.go:31} 的默认值。 */
    private static final long GO_DEFAULT_CAP_MILLIS = 2000L;

    /** env 收紧后的取值 —— 必须<b>小于</b> Go 默认 2000 ⇒ 才能分辨「键被读到」与「键没被读到」。 */
    private static final long TIGHTENED_CAP_MILLIS = 1_000L;

    private static Server server;
    private static ManagedChannel channel;

    /**
     * <b>每次调用一个令牌</b> —— 用来把「上一个用例残留的服务端 handler」与本用例隔开。
     *
     * <p>⚠️ <b>为什么不能用「类级共享的完成标志」</b>（🆕 2026-10-31 · G3 lane 第十三轮踩到并修掉）：
     * 服务端是 <b>{@code @BeforeAll} 静态</b>的 ⇒ ⇒ 上一个用例的 handler 可能<b>还在睡</b>
     * （本类的服务端睡眠有 2.5s / 5s 两种）⇒ ⇒ 它醒来后会把共享标志置真
     * ⇒ ⇒ **污染下一个用例**的「服务端不应把调用跑完」断言。
     * <p>⚠️ 原实现之所以没暴露：那条断言<b>恰好</b>落在第一个行为用例上 ⇒ ⇒ 靠<b>排序运气</b>成立。
     * ⇒ ⇒ 本轮新增用例后顺序变了 ⇒ ⇒ 它立刻变红 ⇒ ⇒ 这是<b>判据自身的缺陷</b>
     * （不是被测物的问题）⇒ ⇒ 现在用「令牌」把跨用例的残留隔开。
     */
    private static final AtomicLong callToken = new AtomicLong();

    /** 本用例的令牌（每个用例开始时更新）。 */
    private static volatile long currentToken;

    /** 服务端是否真的把<b>本用例</b>的调用跑完了（用来证明「没被 deadline 打断」）。 */
    private static final AtomicBoolean serverCompleted = new AtomicBoolean(false);

    /** 服务端的睡眠时长 —— 可按用例调整。 */
    private static volatile long serverSleepMillis = 2_500L;

    @BeforeAll
    static void startServer() throws Exception {
        String name = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(name)
                // ⚠️ **不能** directExecutor()：那会让服务端 handler **跑在客户端线程上**
                // ⇒ 服务端的 Thread.sleep 会把客户端线程一起堵住 ⇒
                //    deadline 虽然准时触发了（结局码仍是 DEADLINE_EXCEEDED），
                //    但客户端**要等服务端睡完才返回** ⇒ 测不出「2s 就掐断」。
                // ⇒ 服务端跑在自己的线程池上，客户端才能在 2s 处**立即**返回。
                .addService(io.grpc.ServerServiceDefinition.builder("comment.CommentService")
                        .addMethod(
                                CommentServiceGrpc.getGetCommentMethod(),
                                io.grpc.stub.ServerCalls.asyncUnaryCall((req, obs) -> {
                                    // ⚠️ 进入 handler 时取一次令牌 ⇒ ⇒ 「上一个用例残留的 handler」
                                    //    醒来后不会污染本用例的断言
                                    long myToken = currentToken;
                                    try {
                                        Thread.sleep(serverSleepMillis);
                                        if (myToken == currentToken) {
                                            serverCompleted.set(true);
                                        }
                                        obs.onNext(com.rag.comment.comment.proto.GetCommentResp
                                                .getDefaultInstance());
                                        obs.onCompleted();
                                    } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                        obs.onError(new StatusRuntimeException(Status.CANCELLED
                                                .withDescription("server interrupted")));
                                    } catch (RuntimeException e) {
                                        obs.onError(e);
                                    }
                                }))
                        .build())
                .build()
                .start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
    }

    @AfterAll
    static void stopServer() throws Exception {
        if (channel != null) {
            channel.shutdownNow();
        }
        if (server != null) {
            server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @BeforeEach
    void beginCase() {
        // ⚠️ 推进令牌 ⇒ ⇒ 上一个用例残留的服务端 handler 醒来后**不能**再置本用例的标志
        currentToken = callToken.incrementAndGet();
        serverCompleted.set(false);
    }

    @AfterEach
    void clearDeadline() {
        // 测试之间绝不能残留 deadline —— 残留会把下一个用例变成假绿/假红
        com.rag.common.observability.GoStyleRequestDeadline.clear();
        currentToken = callToken.incrementAndGet();
        serverCompleted.set(false);
    }

    /**
     * 造一个<b>装了生产拦截器</b>的域 client。
     *
     * <p>⚠️ <b>与 devh 同一条装配动作</b>：devh 的 {@code AbstractChannelFactory.createChannel}
     * 最后一行是 {@code ClientInterceptors.interceptForward(channel, interceptors)}
     * ⇒ ⇒ 全局拦截器是装在 <b>channel</b> 上的 ⇒ ⇒ 「裸 stub」也经过它。
     * <p>⚠️ <b>拦截器读的是【域 yml 绑出来的属性】</b> ⇒ ⇒ 这条链同时证明
     * 「域 yml 的那个键真被读到」＋「共享层真在出向调用上生效」。
     */
    private static CommentRpcClient client(Map<String, Object> envOverrides) {
        Channel capped = ClientInterceptors.intercept(channel,
                new com.rag.common.protection.GoStyleOutboundDeadlineInterceptor(domainProperties(envOverrides)));
        CommentRpcClient c = new CommentRpcClient();
        c.rawStubField(CommentServiceGrpc.newBlockingStub(capped));
        return c;
    }

    /** 真发一次调用，返回观察到的状态码；成功则返回 {@link Status.Code#OK}。 */
    private static Status.Code call(CommentRpcClient client) {
        try {
            client.commentRpc().getComment(
                    com.rag.comment.comment.proto.GetCommentReq.newBuilder()
                            .setTargetType("article").setTargetId("a-diff").build());
            return Status.Code.OK;
        } catch (StatusRuntimeException e) {
            return e.getStatus().getCode();
        }
    }

    // ==================== 配置面 ====================

    @Test
    @DisplayName("① 域 yml 必须声明 rag.protection.outbound-deadline.cap-millis，且是 env 可覆盖形态")
    void domainYmlDeclaresEnvOverridableCap() {
        String raw = rawYamlValue("rag.protection.outbound-deadline.cap-millis");

        assertNotNull(raw,
                "🔴 本域 application.yml 必须配 rag.protection.outbound-deadline.cap-millis "
                        + "⇒ ⇒ 共享层 GoStyleOutboundDeadlineInterceptor 的 cap 取默认值 0 ＝ 不启用"
                        + "⇒ ⇒ 出向 gRPC 调用【没有客户端硬上界】（Go 侧恒有 2000ms）"
                        + "⇒ ⇒ 域侧那份自带的封顶逻辑已于本轮删除 ⇒ ⇒ 这半就等于没做。");
        assertTrue(raw.startsWith("${") && raw.endsWith("}"),
                "🔴 cap-millis 必须是 ${ENV:默认} 形态（实测 = " + raw + "）⇒ ⇒ 否则【不可 env 覆盖】");
        assertTrue(raw.contains(ENV_CAP),
                "🔴 占位符里必须点名本域的 env 旋钮 " + ENV_CAP + "（实测 = " + raw + "）"
                        + "⇒ ⇒ 各域独立可覆盖（Go 侧 RpcClientConf.Timeout 本身就是域级字段）。");
        assertTrue(raw.endsWith(":" + GO_DEFAULT_CAP_MILLIS + "}"),
                "🔴 占位符的默认值必须是 Go 的 zrpc/config.go:31 默认值 " + GO_DEFAULT_CAP_MILLIS
                        + "ms（实测 = " + raw + "）⇒ ⇒ 本域 Go 侧 commentservice.yaml:21-23 的 "
                        + "CommentRpc: 段无显式 Timeout ⇒ ⇒ 恒 2000ms。");
    }

    @Test
    @DisplayName("② 域 yml 绑出的 cap 必须 == Go 默认 2000ms，且传播开关仍开")
    void capBindsToGoDefault() {
        GoStyleProtectionProperties props = domainProperties(null);

        assertEquals(GO_DEFAULT_CAP_MILLIS, props.getOutboundDeadline().getCapMillis(),
                "🔴 域 yml 的 rag.protection.outbound-deadline.cap-millis 必须真的绑定到"
                        + " GoStyleProtectionProperties.OutboundDeadline#capMillis"
                        + "⇒ ⇒ 键名写错就是【配了不生效】（AGENTS.md §3.3.1）。");
        assertTrue(props.getOutboundDeadline().isEnabled(),
                "🔴 出向 deadline 传播开关默认开（Go 侧 zrpc/internal/config.go:15 的 Timeout 默认 true）"
                        + "⇒ ⇒ 本域不得把它关掉。");
    }

    // ==================== 三个行为用例 ====================

    @Test
    @DisplayName("③ Go zrpc/config.go:31 default=2000 —— **无 HTTP 预算**时下游调用也必须有 2s 上限")
    void noHttpBudgetStillGetsTwoSecondCap() {
        serverSleepMillis = 2_500L;
        assertEquals(-1L, com.rag.common.observability.GoStyleRequestDeadline.remainingMillis(), "本用例的前提：当前线程没有 HTTP 预算");

        long start = System.nanoTime();
        Status.Code code = call(client(null));
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertEquals(Status.Code.DEADLINE_EXCEEDED, code,
                "Go 的 timeout 中间件是**永远装着**的（zrpc/internal/config.go:15 default=true）"
                        + "⇒ 即使没有 HTTP 预算，下游调用也必须在 2s 处被掐断。"
                        + "若观察到 OK，说明这次调用根本没有 deadline。");
        assertTrue(elapsedMillis < 2_400L,
                "必须由 **2s 上限**掐断（不是服务端 2.5s 后自己返回）⇒ 实测 " + elapsedMillis + "ms");
        assertTrue(!serverCompleted.get(), "服务端不应把调用跑完 —— 跑完说明客户端没掐断");
    }

    @Test
    @DisplayName("④ Go http.go:53 的 3s 预算 vs zrpc 的 2s 上限 ⇒ **2s 生效**（上限小于预算）")
    void twoSecondCapWinsOverLongerHttpBudget() {
        serverSleepMillis = 2_500L;
        com.rag.common.observability.GoStyleRequestDeadline.begin(System.nanoTime() + TimeUnit.SECONDS.toNanos(30));

        long start = System.nanoTime();
        Status.Code code = call(client(null));
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertEquals(Status.Code.DEADLINE_EXCEEDED, code,
                "HTTP 预算 30s，但 Go 的下游上限是 2s ⇒ 必须在 2s 处 DEADLINE_EXCEEDED。"
                        + "若观察到 OK，说明 2s 上限没生效。");
        assertTrue(elapsedMillis < 2_400L,
                "必须由 **2s 上限**掐断 ⇒ 实测 " + elapsedMillis + "ms");
    }

    @Test
    @DisplayName("⑤ 生产形态（HTTP 预算 3s + 上限 2s）⇒ **2s 处**掐断，不是 3s")
    void productionShapeCapsAtTwoSecondsNotThree() {
        serverSleepMillis = 5_000L;
        com.rag.common.observability.GoStyleRequestDeadline.begin(System.nanoTime() + TimeUnit.SECONDS.toNanos(3));

        long start = System.nanoTime();
        Status.Code code = call(client(null));
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertEquals(Status.Code.DEADLINE_EXCEEDED, code, "两边的结局码相同，只能靠耗时区分");
        assertTrue(elapsedMillis < 2_600L,
                "必须在 **2s** 处掐断；若实测接近 3000ms，说明用的是 HTTP 的 3s 预算、"
                        + "Go 的 2s 上限没生效 ⇒ 实测 " + elapsedMillis + "ms");
    }

    @Test
    @DisplayName("⑥ 预算已耗尽 ⇒ **立刻**失败（1ms），不是「没有 deadline」")
    void exhaustedBudgetMeansImmediateNotAbsent() {
        serverSleepMillis = 5_000L;
        com.rag.common.observability.GoStyleRequestDeadline.begin(System.nanoTime() - 1L);   // 已经过去
        assertEquals(0L, com.rag.common.observability.GoStyleRequestDeadline.remainingMillis());

        long start = System.nanoTime();
        Status.Code code = call(client(null));
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertEquals(Status.Code.DEADLINE_EXCEEDED, code);
        assertTrue(elapsedMillis < 500L,
                "预算耗尽后必须立刻失败；等满服务端睡眠时间说明根本没有 deadline ⇒ 实测 "
                        + elapsedMillis + "ms");
    }

    @Test
    @DisplayName("⑦ env 把 cap 覆盖【到比 2000 更小】⇒ 出向上界必须跟着收紧 ⇒ 【证明域 yml 的这个键真被读到】")
    void envOverrideTightensTheOutboundCap() {
        serverSleepMillis = 5_000L;

        long start = System.nanoTime();
        Status.Code code = call(client(Map.of(ENV_CAP, String.valueOf(TIGHTENED_CAP_MILLIS))));
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertEquals(Status.Code.DEADLINE_EXCEEDED, code, "失败码必须是 DEADLINE_EXCEEDED（= deadline 到点）");
        assertTrue(elapsedMillis < TIGHTENED_CAP_MILLIS + 700L,
                "🔴 env 把 cap 覆盖成 " + TIGHTENED_CAP_MILLIS + "ms ⇒ 出向必须在它附近收口"
                        + "（实测耗时 = " + elapsedMillis + "ms）"
                        + "⇒ ⇒ 若这里等到了 5s 左右 ⇒ 说明【域 yml 的这个键根本没被读到】"
                        + "（cap 仍是默认 0 ＝ 不启用）⇒ ⇒ 键名写错就是【配了不生效】。");
    }

    // ==================== 形态面 ====================

    @Test
    @DisplayName("⑧ 本域 withDeadlineAfter 的【代码行】必须为 0 ⇒ 不得再出现域侧自带的上界")
    void domainSideMustNotReintroduceAHardcodedOutboundCap() throws Exception {
        Map<String, Integer> hits = new LinkedHashMap<>();

        try (var walk = Files.walk(moduleRoot().resolve("src").resolve("main").resolve("java"))) {
            for (Path p : (Iterable<Path>) walk.filter(f -> f.toString().endsWith(".java"))::iterator) {
                for (String raw : Files.readString(p, StandardCharsets.UTF_8).split("\\R")) {
                    String line = raw.strip();
                    // ⚠️ 只认【代码行】：javadoc / 行注释里提到 withDeadlineAfter 不算
                    if (line.isEmpty() || line.startsWith("*") || line.startsWith("//") || line.startsWith("/*")) {
                        continue;
                    }
                    if (line.contains("withDeadlineAfter")) {
                        hits.merge(p.getFileName().toString(), 1, Integer::sum);
                    }
                }
            }
        }

        assertEquals(Map.of(), hits,
                "🔴 本域 withDeadlineAfter 的【代码行】分布 = " + hits
                        + "，而已登记的基线 = {}（域侧那一份已于本轮删除）"
                        + "⇒ ⇒ 出向【客户端硬上界】的合法来源"
                        + " = 共享层的 GoStyleOutboundDeadlineInterceptor（域侧只配 cap-millis）"
                        + "⇒ ⇒ 副本数 = 漂移面 ⇒ ⇒ 这条判据是**点位级棘轮**：只能变紧，不能变松。");
    }

    // ================== 装置 ==================

    /**
     * 用本域<b>自己的</b> {@code application.yml}（<b>磁盘上的那份</b>）绑出
     * {@link GoStyleProtectionProperties}。
     *
     * @param envOverrides 模拟的 OS 环境变量（{@code null} ＝ 不覆盖）
     */
    private static GoStyleProtectionProperties domainProperties(Map<String, Object> envOverrides) {
        ConfigurableEnvironment env = new StandardEnvironment();
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        try {
            for (PropertySource<?> ps : loader.load("domain-application-yml",
                    new FileSystemResource(domainYamlPath()))) {
                env.getPropertySources().addLast(ps);
            }
        } catch (IOException e) {
            throw new IllegalStateException("读不到本域的 application.yml（" + domainYamlPath() + "）", e);
        }
        if (envOverrides != null && !envOverrides.isEmpty()) {
            // ⚠️ addFirst ⇒ 模拟 OS 环境变量的优先级（env 高于 yml）
            env.getPropertySources().addFirst(new MapPropertySource("simulated-os-env", envOverrides));
        }
        ConfigurationPropertySources.attach(env);
        Binder binder = new Binder(ConfigurationPropertySources.get(env),
                new PropertySourcesPlaceholdersResolver(env));
        return binder.bind("rag.protection", GoStyleProtectionProperties.class)
                .orElseGet(GoStyleProtectionProperties::new);
    }

    /** 本域 {@code application.yml} 里某个键的<b>未解析</b>原值（占位符仍在）。 */
    private static String rawYamlValue(String key) {
        try {
            List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                    .load("domain-application-yml", new FileSystemResource(domainYamlPath()));
            for (PropertySource<?> ps : sources) {
                Object v = ps.getProperty(key);
                if (v != null) {
                    return String.valueOf(v);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("读不到本域的 application.yml（" + domainYamlPath() + "）", e);
        }
        return null;
    }

    /** 本域 {@code application.yml} 的<b>磁盘路径</b>。 */
    private static Path domainYamlPath() {
        return moduleRoot().resolve("src").resolve("main").resolve("resources").resolve("application.yml");
    }

    /**
     * 定位本模块的源码根（含 {@code src/main/java}）—— <b>不依赖 surefire 的工作目录</b>。
     */
    private static Path moduleRoot() {
        try {
            Path loc = Path.of(CommentApiRpcDeadlineGateTest.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            Path p = Files.isDirectory(loc) ? loc : loc.getParent();
            if (p != null && "target".equals(p.getFileName().toString())) {
                p = p.getParent();
            }
            if (p != null && Files.isDirectory(p.resolve("src").resolve("main").resolve("java"))) {
                return p;
            }
        } catch (Exception ignored) {
            // 落到下面的兜底
        }
        Path cur = Path.of("").toAbsolutePath();
        for (int up = 0; up < 6 && cur != null; up++) {
            if (Files.isDirectory(cur.resolve("src").resolve("main").resolve("java"))) {
                return cur;
            }
            cur = cur.getParent();
        }
        throw new IllegalStateException("定位不到本模块的源码根（src/main/java）"
                + "⇒ ⇒ 形态面判据失效（判据自己的问题，不是被测物的问题）");
    }
}
