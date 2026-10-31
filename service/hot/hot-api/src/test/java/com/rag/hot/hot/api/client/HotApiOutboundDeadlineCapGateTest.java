package com.rag.hot.hot.api.client;

import com.rag.common.protection.GoStyleProtectionProperties;
import com.rag.hot.hot.proto.GetHotArticlesRequest;
import com.rag.hot.hot.proto.GetHotArticlesResponse;
import com.rag.hot.hot.proto.HotServiceGrpc;
import io.grpc.Channel;
import io.grpc.ClientInterceptors;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
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
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 🔴 {@code hot-api} 出向 gRPC 调用（→ {@code hot-rpc} / {@code article-rpc}）的
 * <b>客户端硬上界（2s）</b>守门。
 *
 * <h2>它守的是什么</h2>
 * <p>Go 侧 {@code hot-api → hot-rpc / article-rpc} 这两个出向调用<b>恒带 2s 客户端超时</b>：
 * <ul>
 *   <li>{@code go-zero@v1.10.2/zrpc/config.go:31} ——
 *       {@code RpcClientConf.Timeout int64 `json:",default=2000"`} ⇒ <b>默认 2000ms</b>；</li>
 *   <li>Go 侧 {@code service/hot/api/etc/hotcenter.yaml:25-35} 的
 *       {@code HotRpcConf:} 与 {@code ArticleRpcConf:} 两段<b>都没有</b>显式
 *       {@code Timeout} ⇒ ⇒ <b>两个方向都是</b> 2000ms（⇒ ⇒ 组件级上界表达得了）；</li>
 *   <li><b>消费点</b>：{@code go-zero@v1.10.2/zrpc/internal/clientinterceptors/timeoutinterceptor.go:25}
 *       —— {@code context.WithTimeout(ctx, t)} ⇒ <b>取更早者</b>。</li>
 * </ul>
 *
 * <h2>🆕 2026-10-31 · G3 lane 第十三轮：上界从「域侧自带」搬到「共享层参数」</h2>
 * <p>本域原先自带一份封顶（{@code GoStyleGrpcCallDeadline.apply} = min(2s, 请求剩余预算)）
 * ⇒ ⇒ 那是<b>第二个上界来源</b> ⇒ ⇒ 与共享层「唯一实现」的形态冲突
 * （共享层棘轮 {@code GoStyleClientCallTimeoutSingleSourceGateTest} 判据 ④：
 * 「cap 的来源必须是共享层参数，域侧私有常量 = 违规」）。
 * ⇒ ⇒ 本轮：域侧那份的<b>封顶</b>删掉，<b>只保留「请求预算的传播」</b>；
 * 2s 上界改由共享层承担（{@code GoStyleOutboundDeadlineInterceptor}，channel 级
 * ⇒ 装在<b>每个</b>出向调用上），本域<b>只配参数</b>：
 * {@code rag.protection.outbound-deadline.cap-millis = 2000}。
 *
 * <h2>⚠️ 为什么本组件<b>仍需</b>显式传播「请求预算」</h2>
 * <p>本组件的补全跑在<b>线程池 worker</b>上，而
 * {@link com.rag.common.observability.GoStyleRequestDeadline} 是 <b>ThreadLocal</b>
 * （由 {@code HttpMiddleware} 设在跑业务的 worker 线程上）
 * ⇒ ⇒ worker 上<b>读不到</b>载体 ⇒ ⇒ 共享层拦截器在那里<b>看不到</b>请求预算
 * ⇒ ⇒ 预算必须在<b>请求线程</b>上取一次（{@code HotArticleHydrator.remainingBudgetMs}）
 * ⇒ ⇒ <b>显式</b>传进来（这正是 {@code GoStyleGrpcCallDeadline} 现在的<b>唯一</b>职责）。
 * ⇒ ⇒ 二者合起来仍是 Go 的公式：min(now+2s, 请求 deadline)。
 *
 * <h2>本测试如何真正触发该机制（AGENTS.md §3.3）</h2>
 * <ol>
 *   <li><b>真拦截器</b>：用<b>生产类</b> {@code GoStyleOutboundDeadlineInterceptor} 装到<b>真 channel</b> 上
 *       （与 devh 的 {@code AbstractChannelFactory.createChannel} 最后一行同一条路）；</li>
 *   <li><b>真发调用</b>：起 <b>in-process 真实 gRPC server</b>，它的实现<b>挂起 8s 后才正常返回</b>
 *       ⇒ ⇒ 客户端必然在<b>自己的上界</b>处失败；而「若没有上界」它会在 8s 后<b>正常返回成功</b>
 *       ⇒ ⇒ 判据能干净地分辨「有上界」与「没上界」，<b>而不是</b>靠 {@code @Timeout} 把「挂死」包装成「测试超时」。</li>
 * </ol>
 *
 * <h2>⚠️ 诚实边界</h2>
 * <ul>
 *   <li>本类<b>不起</b>真实 hot-rpc / article-rpc、<b>不连</b> Nacos ⇒ 它证的是
 *       「<b>本域配的上界真的被共享层拦截器读到了、且真的在出向调用上生效</b>」，
 *       <b>不</b>证「整条生产链路的端到端超时表现」；</li>
 *   <li>本类<b>不</b>带生产链路上的 {@code GoStyleGrpcClientBreakerInterceptor}；</li>
 *   <li>第 ⑤ 条用<b>收紧</b>方向取证 ⇒ ⇒ 那条验的是「<b>域 yml 的这个键真被读到了</b>」，
 *       「域侧没有自带 cap」这一条由第 ⑥ 条的<b>点位级棘轮</b>承担。</li>
 * </ul>
 */
class HotApiOutboundDeadlineCapGateTest {

    /** 本域的 env 覆盖旋钮（与本域 {@code application.yml} 里的占位符同名）。 */
    private static final String ENV_CAP = "HOT_API_OUTBOUND_DEADLINE_CAP_MILLIS";

    /** Go 侧 {@code zrpc/config.go:31} 的默认值。 */
    private static final long GO_DEFAULT_CAP_MILLIS = 2000L;

    /** 下游挂起时长（毫秒）—— 必须<b>大于</b>上界 ⇒ 才能分辨「有上界」与「没上界」。 */
    private static final long HANG_MILLIS = 8_000L;

    /** 真发调用那条用例的下界 —— 防止「秒失败」被误判成「超时生效」。 */
    private static final long MIN_EXPECTED_WAIT_MILLIS = 1_500L;

    /** 允许的额外耗时（毫秒）—— 排除「别的机制提前掐断」与墙钟抖动。 */
    private static final long SLACK_MILLIS = 1_500L;

    /** env 收紧后的取值 —— 必须<b>小于</b> Go 默认 2000 ⇒ 才能分辨「键被读到」与「键没被读到」。 */
    private static final long TIGHTENED_CAP_MILLIS = 1_000L;

    /** 显式请求预算（毫秒）—— 必须<b>远小于</b> 2s ⇒ 才能分辨「取更早者」与「取更晚者」。 */
    private static final long REQUEST_BUDGET_MILLIS = 300L;

    /**
     * 域侧私有 cap 的形态 ①：形如 {@code XXX_TIMEOUT_MILLIS = 2000L} 的静态常量。
     *
     * <p>⚠️ 与共享层棘轮判据 ④ 的同一套口径（{@code GoStyleClientCallTimeoutSingleSourceGateTest}
     * 里的 {@code PRIVATE_CAP_CONSTANT}）—— 域侧这条是<b>本域自己的</b>副本，不依赖共享层那条是否在跑。
     */
    private static final Pattern PRIVATE_CAP_CONSTANT = Pattern.compile(
            "\\b[A-Z][A-Z0-9_]*(?:TIMEOUT|CAP)[A-Z0-9_]*\\b");

    private Server server;
    private ManagedChannel channel;

    @AfterEach
    void tearDown() {
        if (channel != null) {
            channel.shutdownNow();
        }
        if (server != null) {
            server.shutdownNow();
        }
    }

    // ────────────────────────────────────────────────────────────────────────────
    // ① 配置面
    // ────────────────────────────────────────────────────────────────────────────

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
                        + "ms（实测 = " + raw + "）⇒ ⇒ 本域 Go 侧 hotcenter.yaml:25-35 的 "
                        + "HotRpcConf: / ArticleRpcConf: 两段都无显式 Timeout ⇒ ⇒ 恒 2000ms。");
    }

    // ────────────────────────────────────────────────────────────────────────────
    // ② 绑定面
    // ────────────────────────────────────────────────────────────────────────────

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

    // ────────────────────────────────────────────────────────────────────────────
    // ③ 运行期：【没有请求预算】⇒ 出向仍必须带 ≤2000ms 上界
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @Timeout(value = 40, unit = TimeUnit.SECONDS)
    @DisplayName("③ 【没有请求预算】⇒ 出向 hot-rpc 必须在 ≤2000ms 处 DEADLINE_EXCEEDED（真发调用）")
    void outboundCallIsCappedWithoutRequestBudget() throws Exception {
        HotRpcClient client = startHangingDomainRpc(domainProperties(null));

        long startedAt = System.nanoTime();
        StatusRuntimeException thrown = assertThrows(StatusRuntimeException.class,
                () -> client.getHotArticles(100, -1L),
                "下游不响应时，hot-api 必须靠【客户端硬上界】收口，而不是一直挂着");
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;

        assertEquals(Status.Code.DEADLINE_EXCEEDED, thrown.getStatus().getCode(),
                "下游挂死 ⇒ 客户端 deadline 到点 ⇒ status 必须是 DEADLINE_EXCEEDED"
                        + "（Go 侧 ctx 到期同样是 DeadlineExceeded）");
        assertTrue(elapsedMillis >= MIN_EXPECTED_WAIT_MILLIS,
                "必须是「等到 deadline 到期」而不是「立刻失败」，实际耗时 = " + elapsedMillis + "ms");
        assertTrue(elapsedMillis <= GO_DEFAULT_CAP_MILLIS + SLACK_MILLIS,
                "不应比 2s 客户端硬上界晚太多（说明上界没生效或被别的机制顶掉），实际耗时 = "
                        + elapsedMillis + "ms");
    }

    // ────────────────────────────────────────────────────────────────────────────
    // ④ 运行期：min 语义 —— 显式请求预算比上界更早 ⇒ 必须取【更早者】
    //    （这一条同时证明「域侧那份传播」在删掉封顶之后**仍然生效**）
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @Timeout(value = 40, unit = TimeUnit.SECONDS)
    @DisplayName("④ 显式请求预算 300ms < cap 2000ms ⇒ 出向必须在【300ms】处失败（钉的是 min，不是覆盖）")
    void requestBudgetWinsOverCap() throws Exception {
        HotRpcClient client = startHangingDomainRpc(domainProperties(null));

        long startedAt = System.nanoTime();
        StatusRuntimeException thrown = assertThrows(StatusRuntimeException.class,
                () -> client.getHotArticles(100, REQUEST_BUDGET_MILLIS),
                "请求预算比 cap 更早 ⇒ 出向必须在请求预算处失败");
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;

        assertEquals(Status.Code.DEADLINE_EXCEEDED, thrown.getStatus().getCode(),
                "失败码必须是 DEADLINE_EXCEEDED（= deadline 到点）");
        assertTrue(elapsedMillis >= 120L && elapsedMillis <= 1_200L,
                "🔴 请求预算 " + REQUEST_BUDGET_MILLIS + "ms < cap " + GO_DEFAULT_CAP_MILLIS
                        + "ms ⇒ 有效值必须是【更早者】= " + REQUEST_BUDGET_MILLIS + "ms"
                        + "（实测耗时 = " + elapsedMillis + "ms）"
                        + "⇒ ⇒ 若实测落在 2s 附近 ⇒ 说明域侧的【请求预算传播】被本轮改坏了"
                        + "（或者变成了【覆盖】而不是【取更早者】）"
                        + "⇒ ⇒ 那就与 Go 的 context.WithTimeout(parent, 2s) 方向相反了。");
    }

    // ────────────────────────────────────────────────────────────────────────────
    // ⑤ 运行期：env 覆盖【收紧】⇒ 上界【真的】跟着变（证明域 yml 的这个键被读到了）
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @Timeout(value = 40, unit = TimeUnit.SECONDS)
    @DisplayName("⑤ env 把 cap 覆盖【到比 2000 更小】⇒ 出向上界必须跟着收紧 ⇒ 【证明域 yml 的这个键真被读到】")
    void envOverrideTightensTheOutboundCap() throws Exception {
        HotRpcClient client = startHangingDomainRpc(domainProperties(
                Map.of(ENV_CAP, String.valueOf(TIGHTENED_CAP_MILLIS))));

        long startedAt = System.nanoTime();
        StatusRuntimeException thrown = assertThrows(StatusRuntimeException.class,
                () -> client.getHotArticles(100, -1L),
                "下游不响应时必须靠【被 env 覆盖后的】客户端硬上界收口");
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;

        assertEquals(Status.Code.DEADLINE_EXCEEDED, thrown.getStatus().getCode(),
                "失败码必须是 DEADLINE_EXCEEDED（= deadline 到点）");
        assertTrue(elapsedMillis <= TIGHTENED_CAP_MILLIS + SLACK_MILLIS,
                "🔴 env 把 cap 覆盖成 " + TIGHTENED_CAP_MILLIS + "ms ⇒ 出向必须在它附近收口"
                        + "（实测耗时 = " + elapsedMillis + "ms）"
                        + "⇒ ⇒ 若这里等到了 8s 左右 ⇒ 说明【域 yml 的这个键根本没被读到】"
                        + "（cap 仍是默认 0 ＝ 不启用）⇒ ⇒ 键名写错就是【配了不生效】。");
    }

    // ────────────────────────────────────────────────────────────────────────────
    // ⑥ 形态面：**点位级**棘轮 —— 域侧只允许保留「纯传播」那一处，且它**不得**自带 cap
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("⑥ 本域 withDeadlineAfter 的【代码行】只允许落在 GoStyleGrpcCallDeadline（纯传播）⇒ 不得新增第二处")
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

        Map<String, Integer> registered = new LinkedHashMap<>();
        // ⚠️ **2 行** = 同一个传播方法里的<b>两个分支</b>：「预算耗尽 ⇒ 1ns」＋「按剩余预算传播」
        //   （与 favorite-api 那处 `capByClientTimeout` 同款：两分支 = 一个实现，不是两个实现）
        registered.put("GoStyleGrpcCallDeadline.java", 2);

        assertEquals(registered, hits,
                "🔴 本域 withDeadlineAfter 的【代码行】分布 = " + hits
                        + "，而已登记的基线 = " + registered
                        + "⇒ ⇒ 那 2 行是【同一个传播方法的两个分支】（worker 线程看不到载体 ⇒ 必须显式传）"
                        + "⇒ ⇒ **不得新增第二处** ⇒ ⇒ 出向【客户端硬上界】的合法来源"
                        + " = 共享层的 GoStyleOutboundDeadlineInterceptor（域侧只配 cap-millis）"
                        + "⇒ ⇒ 副本数 = 漂移面 ⇒ ⇒ 这条判据是**点位级棘轮**：只能变紧，不能变松。");
    }

    @Test
    @DisplayName("⑥ 之二：GoStyleGrpcCallDeadline 必须是【纯传播】⇒ 它自己不得再带任何 cap 常量")
    void propagationHelperMustNotCarryItsOwnCap() throws Exception {
        String code = Files.readString(
                moduleRoot().resolve("src").resolve("main").resolve("java")
                        .resolve("com/rag/hot/hot/api/client/GoStyleGrpcCallDeadline.java"),
                StandardCharsets.UTF_8);

        assertFalse(PRIVATE_CAP_CONSTANT.matcher(code).find(),
                "🔴 GoStyleGrpcCallDeadline 里出现了「域侧私有 cap 常量」"
                        + "（形如 XXX_TIMEOUT_MILLIS / XXX_CAP 的全大写标识符）"
                        + "⇒ ⇒ 那就是**第二个上界来源** ⇒ ⇒ 上界只该由共享层承担"
                        + "⇒ ⇒ 共享层棘轮判据 ④ 也会判红 ⇒ ⇒ 别在这里复活那份封顶逻辑。");
    }

    // ================== 装置 ==================

    /**
     * 起一个<b>挂起 {@link #HANG_MILLIS} 才响应</b>的 in-process {@code hot-rpc} 下游，
     * 把<b>生产拦截器</b>装到 channel 上，并把该 stub 注入域 client 的 {@code @GrpcClient} 字段。
     */
    private HotRpcClient startHangingDomainRpc(GoStyleProtectionProperties props) throws Exception {
        String serverName = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(serverName)
                .addService(new HotServiceGrpc.HotServiceImplBase() {
                    @Override
                    public void getHotArticles(GetHotArticlesRequest request,
                            StreamObserver<GetHotArticlesResponse> responseObserver) {
                        // ⚠️ 挂起 HANG_MILLIS 才响应（既不立即响应，也**不是永不响应**）
                        // ⇒ 客户端在【客户端硬上界】处失败；
                        // ⇒ 而「若没有上界」它会在 HANG_MILLIS 后**正常返回**
                        // ⇒ ⇒ 判据能干净地分辨「有上界」与「没上界」。
                        try {
                            Thread.sleep(HANG_MILLIS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        responseObserver.onNext(GetHotArticlesResponse.newBuilder().build());
                        responseObserver.onCompleted();
                    }
                })
                .build()
                .start();
        channel = InProcessChannelBuilder.forName(serverName).build();

        // ⚠️ 与 devh 的装配动作同一条：devh 的 AbstractChannelFactory.createChannel
        //    最后一行是 ClientInterceptors.interceptForward(channel, interceptors)
        Channel capped = ClientInterceptors.intercept(channel,
                new com.rag.common.protection.GoStyleOutboundDeadlineInterceptor(props));

        HotRpcClient client = new HotRpcClient();
        client.rawStubField(HotServiceGrpc.newBlockingStub(capped));
        return client;
    }

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
            Path loc = Path.of(HotApiOutboundDeadlineCapGateTest.class.getProtectionDomain()
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
