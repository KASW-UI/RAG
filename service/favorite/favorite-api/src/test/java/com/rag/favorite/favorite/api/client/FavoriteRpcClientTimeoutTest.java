package com.rag.favorite.favorite.api.client;

import com.rag.common.observability.GoStyleRequestDeadline;
import com.rag.common.protection.GoStyleOutboundDeadlineInterceptor;
import com.rag.common.protection.GoStyleProtectionProperties;
import com.rag.favorite.favorite.proto.CreateFavoriteReq;
import com.rag.favorite.favorite.proto.CreateFavoriteResp;
import com.rag.favorite.favorite.proto.FavoriteServiceGrpc;
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
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 证明 favorite-api 的 <b>gRPC client 超时被压在 Go 的 2s</b>，而不是一路顶到 HTTP 超时。
 *
 * <h2>为什么这条不能省</h2>
 * <p>Go 侧 api → rpc 有<b>两层</b>预算，<b>取更小的生效</b>：
 * <ul>
 *   <li><b>ctx 预算 3s</b>：Go {@code rest/config.go:55} 的 {@code Timeout default=3000}
 *       （+ {@code timeouthandler.go:68} 的 {@code r.WithContext(ctx)}）；</li>
 *   <li><b>zrpc client 超时 2s</b>：Go {@code zrpc/config.go:31} 的
 *       {@code RpcClientConf.Timeout int64 `json:",default=2000"`}，
 *       经 {@code zrpc/internal/client.go:113-117} 的
 *       {@code clientinterceptors.TimeoutInterceptor(timeout)} 生效。</li>
 * </ul>
 * ⇒ Go 侧下游挂死时是 <b>2s 先到</b> ⇒ api 拿到 rpc error ⇒ 返回 <b>200 + 业务码 1015</b>；
 * 若只带 3s 的 ctx 预算，就会一路顶到 HTTP 超时分支 ⇒ 返回 <b>503 "Request Timeout"</b>，
 * 且状态码与 body 不自洽。
 *
 * <h2>🆕 2026-10-31 · G3 lane 第十三轮：上界的<b>承担者</b>换了</h2>
 * <p>本类原先断言的是<b>域侧自带</b>那份封顶（{@code FavoriteRpcClient.capByClientTimeout}，
 * 即 min(2s, ctx 预算)）⇒ ⇒ 那个 <b>2s</b> 是<b>第二个上界来源</b>
 * ⇒ ⇒ 与共享层「唯一实现」的形态冲突（共享层棘轮
 * {@code GoStyleClientCallTimeoutSingleSourceGateTest} 判据 ④：
 * 「cap 的来源必须是共享层参数，域侧私有常量 = 违规」）。
 * ⇒ ⇒ 本轮：<b>域侧那份删掉</b>，上界改由<b>共享层</b>承担
 * （{@code GoStyleOutboundDeadlineInterceptor}，channel 级 ⇒ 装在<b>每个</b>出向调用上），
 * 本域<b>只配参数</b>：{@code rag.protection.outbound-deadline.cap-millis = 2000}。
 * ⇒ ⇒ 本类现在把 stub 建在「<b>装了生产拦截器</b>的 channel」上
 * ⇒ ⇒ 它同时证明：<b>① 域 yml 的那个键真被读到；② 共享层真在出向调用上生效。</b>
 *
 * <p><b>⚠️ 为什么用真的 in-process channel</b>：直接断言常量「== 2000」是
 * 「拿 RAG 的值验 RAG 的值」（AGENTS.md §3.3：绿测试可能是错误答案的守卫）。
 * ⇒ 这里用<b>真实</b>的 {@link io.grpc.inprocess.InProcessServerBuilder} +
 * {@link io.grpc.inprocess.InProcessChannelBuilder} 造出真实的 stub，
 * 再断言<b>真的调用</b>在 ≤2s 处以 {@code DEADLINE_EXCEEDED} 收场 —— 验的是行为，不是数字。
 *
 * <p><b>⚠️ 证据边界</b>：本类验的是「出向调用的上界真的是 2s（且取更早者）」，
 * <b>不是</b>「整条生产链路的端到端超时表现」（不起真实 favorite-rpc、不连 Nacos）。
 *
 * <p>🧬 <b>变异实测</b>：① 把共享层拦截器的 {@code capMillis} 写死成 0（= 不启用）
 * ⇒ 用例③④ 变红（挂到服务端睡眠结束才返回 / 拿到 OK）；
 * ② 把本域 yml 的 {@code cap-millis} 行删掉 ⇒ 同样变红（cap 落到默认 0）。
 */
class FavoriteRpcClientTimeoutTest {

    /** 本域的 env 覆盖旋钮（与本域 {@code application.yml} 里的占位符同名）。 */
    private static final String ENV_CAP = "FAVORITE_API_OUTBOUND_DEADLINE_CAP_MILLIS";

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

    /** 入向预算（毫秒）—— 必须<b>远小于</b> 2s ⇒ 才能分辨「取更早者」与「取更晚者」。 */
    private static final long INBOUND_BUDGET_MILLIS = 300L;

    private Server server;
    private ManagedChannel channel;

    @AfterEach
    void tearDown() {
        GoStyleRequestDeadline.clear();
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
                        + "ms（实测 = " + raw + "）⇒ ⇒ 本域 Go 侧 favorite.yaml:34-38 的 "
                        + "FavoriteRpc: 段无显式 Timeout ⇒ ⇒ 恒 2000ms。");
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
    // ③ 运行期：【没有入向 deadline】⇒ 出向仍必须带 ≤2000ms 上界
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @Timeout(value = 40, unit = TimeUnit.SECONDS)
    @DisplayName("③ 【没有入向 deadline】⇒ 出向 favorite-rpc 必须在 ≤2000ms 处 DEADLINE_EXCEEDED（真发调用）")
    void outboundCallIsCappedWithoutInboundDeadline() throws Exception {
        GoStyleRequestDeadline.clear();
        FavoriteServiceGrpc.FavoriteServiceBlockingStub stub = startHangingDomainRpc(domainProperties(null));

        long startedAt = System.nanoTime();
        StatusRuntimeException thrown = assertThrows(StatusRuntimeException.class,
                () -> stub.createFavorite(CreateFavoriteReq.newBuilder().build()),
                "下游不响应时，favorite-api 必须靠【客户端硬上界】收口，而不是一直挂着");
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

    @Test
    @Timeout(value = 40, unit = TimeUnit.SECONDS)
    @DisplayName("③ 域 client 层：favoriteRpc() 给出的 stub 也必须在 ≤2000ms 处失败（不挂死）")
    void domainClientDoesNotHang() throws Exception {
        GoStyleRequestDeadline.clear();
        FavoriteRpcClient client = startHangingDomainClient(domainProperties(null));

        long startedAt = System.nanoTime();
        StatusRuntimeException thrown = assertThrows(StatusRuntimeException.class,
                () -> client.favoriteRpc().createFavorite(CreateFavoriteReq.newBuilder().build()),
                "🔴 域 client 暴露的 stub 必须按时失败"
                        + "⇒ ⇒ 否则业务调用点会一直挂着（那正是 P2-T04 要修的漂移）");
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;

        assertEquals(Status.Code.DEADLINE_EXCEEDED, thrown.getStatus().getCode(),
                "失败码必须是 DEADLINE_EXCEEDED（= 客户端硬上界到点）");
        assertTrue(elapsedMillis >= MIN_EXPECTED_WAIT_MILLIS,
                "必须是「等到 deadline 到期」而不是「立刻失败」，实际耗时 = " + elapsedMillis + "ms");
        assertTrue(elapsedMillis <= GO_DEFAULT_CAP_MILLIS + SLACK_MILLIS,
                "🔴 域 client 不能挂死：出向必须有 2s 客户端硬上界，实际耗时 = " + elapsedMillis + "ms");
    }

    // ────────────────────────────────────────────────────────────────────────────
    // ④ 运行期：env 覆盖【收紧】⇒ 上界【真的】跟着变（证明域 yml 的这个键被读到了）
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @Timeout(value = 40, unit = TimeUnit.SECONDS)
    @DisplayName("④ env 把 cap 覆盖【到比 2000 更小】⇒ 出向上界必须跟着收紧 ⇒ 【证明域 yml 的这个键真被读到】")
    void envOverrideTightensTheOutboundCap() throws Exception {
        GoStyleRequestDeadline.clear();
        FavoriteServiceGrpc.FavoriteServiceBlockingStub stub = startHangingDomainRpc(domainProperties(
                Map.of(ENV_CAP, String.valueOf(TIGHTENED_CAP_MILLIS))));

        long startedAt = System.nanoTime();
        StatusRuntimeException thrown = assertThrows(StatusRuntimeException.class,
                () -> stub.createFavorite(CreateFavoriteReq.newBuilder().build()),
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
    // ⑤ 运行期：min 语义 —— 入向预算比上界更早 ⇒ 必须取【更早者】
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @Timeout(value = 40, unit = TimeUnit.SECONDS)
    @DisplayName("⑤ 入向预算 300ms < cap 2000ms ⇒ 出向必须在【300ms】处失败（钉的是 min，不是覆盖）")
    void inboundBudgetWinsOverCap() throws Exception {
        GoStyleRequestDeadline.clear();
        GoStyleRequestDeadline.begin(System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(INBOUND_BUDGET_MILLIS));
        FavoriteServiceGrpc.FavoriteServiceBlockingStub stub = startHangingDomainRpc(domainProperties(null));

        long startedAt = System.nanoTime();
        StatusRuntimeException thrown = assertThrows(StatusRuntimeException.class,
                () -> stub.createFavorite(CreateFavoriteReq.newBuilder().build()),
                "入向预算比 cap 更早 ⇒ 出向必须在入向预算处失败");
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;

        assertEquals(Status.Code.DEADLINE_EXCEEDED, thrown.getStatus().getCode(),
                "失败码必须是 DEADLINE_EXCEEDED（= deadline 到点）");
        assertTrue(elapsedMillis >= 120L && elapsedMillis <= 1_200L,
                "🔴 入向预算 " + INBOUND_BUDGET_MILLIS + "ms < cap " + GO_DEFAULT_CAP_MILLIS
                        + "ms ⇒ 有效值必须是【更早者】= " + INBOUND_BUDGET_MILLIS + "ms"
                        + "（实测耗时 = " + elapsedMillis + "ms）"
                        + "⇒ ⇒ 若实测落在 2s 附近 ⇒ 说明是【覆盖】而不是【取更早者】"
                        + "⇒ ⇒ 那就与 Go 的 context.WithTimeout(parent, 2s) 方向相反了。");
    }

    // ────────────────────────────────────────────────────────────────────────────
    // ⑥ 形态面：**点位级**棘轮 —— 本域已无域侧自带上界 ⇒ 不得新增
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("⑥ 本域 withDeadlineAfter 的【代码行】必须为 0 ⇒ 不得再出现域侧自带的上界")
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
     * 起一个<b>挂起 {@link #HANG_MILLIS} 才响应</b>的 in-process {@code favorite-rpc} 下游，
     * 把<b>生产拦截器</b>装到 channel 上。
     */
    private FavoriteServiceGrpc.FavoriteServiceBlockingStub startHangingDomainRpc(
            GoStyleProtectionProperties props) throws Exception {
        String serverName = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(serverName)
                .addService(new FavoriteServiceGrpc.FavoriteServiceImplBase() {
                    @Override
                    public void createFavorite(CreateFavoriteReq request,
                            StreamObserver<CreateFavoriteResp> responseObserver) {
                        // ⚠️ 挂起 HANG_MILLIS 才响应（既不立即响应，也**不是永不响应**）
                        // ⇒ 客户端在【客户端硬上界】处失败；
                        // ⇒ 而「若没有上界」它会在 HANG_MILLIS 后**正常返回**
                        // ⇒ ⇒ 判据能干净地分辨「有上界」与「没上界」。
                        try {
                            Thread.sleep(HANG_MILLIS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        responseObserver.onNext(CreateFavoriteResp.newBuilder().build());
                        responseObserver.onCompleted();
                    }
                })
                .build()
                .start();
        channel = InProcessChannelBuilder.forName(serverName).build();

        // ⚠️ 与 devh 的装配动作同一条：devh 的 AbstractChannelFactory.createChannel
        //    最后一行是 ClientInterceptors.interceptForward(channel, interceptors)
        Channel capped = ClientInterceptors.intercept(channel,
                new GoStyleOutboundDeadlineInterceptor(props));
        return FavoriteServiceGrpc.newBlockingStub(capped);
    }

    /** 同上，但把 stub 注入域 client（域 client 暴露的那条出口也要有上界）。 */
    private FavoriteRpcClient startHangingDomainClient(GoStyleProtectionProperties props) throws Exception {
        FavoriteServiceGrpc.FavoriteServiceBlockingStub stub = startHangingDomainRpc(props);
        FavoriteRpcClient client = new FavoriteRpcClient();
        injectStub(client, "favoriteServiceBlockingStub", stub);
        return client;
    }

    /** 把捕获型 stub 注入域 client 的 {@code @GrpcClient} 字段（生产是 devh 注入的）。 */
    private static void injectStub(FavoriteRpcClient client, String fieldName, Object stub) {
        try {
            Field f = FavoriteRpcClient.class.getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(client, stub);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("注入 " + fieldName + " 失败（判据自己的问题，不是被测物的问题）", e);
        }
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
            Path loc = Path.of(FavoriteRpcClientTimeoutTest.class.getProtectionDomain()
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
