package com.rag.follow.follow.rpc.client;

import com.rag.common.observability.GoStyleRequestDeadline;
import com.rag.common.protection.GoStyleOutboundDeadlineInterceptor;
import com.rag.common.protection.GoStyleProtectionProperties;
import com.rag.users.user.proto.GetUserReq;
import com.rag.users.user.proto.GetUserResp;
import com.rag.users.user.proto.UserServiceGrpc;
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
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code follow-rpc} 出向 user-rpc 调用的 <b>客户端硬上界（Go 侧 2s）</b>守门。
 *
 * <h2>🔴 2026-10-31（P2-T04 · G1 lane）：本类的守门对象<b>换了一代</b></h2>
 * <p><b>换之前</b>：域侧自己写一份 {@code capByClientTimeout} 私有副本 ⇒ 本类直接测那份副本。
 * <p><b>换之后</b>：<b>共享层唯一实现</b>
 * （{@code service/common/.../protection/GoStyleOutboundDeadlineInterceptor.java}
 * ＝ {@code @GrpcGlobalClientInterceptor} ⇒ 装在<b>每个</b>出向 devh 调用上）
 * ＋ <b>域侧只配参数</b>
 * （{@code rag.protection.outbound-deadline.cap-millis}，见本域
 * {@code src/main/resources/application.yml} 的 {@code rag.protection} 段）
 * ⇒ ⇒ 本类现在守的是「<b>域侧只配参数这条路真的通</b>」这条链。
 *
 * <h2>为什么必须删掉域侧私有副本（动作②「可 env 覆盖」的前提）</h2>
 * <p>私有副本把上界<b>写死</b>在 {@code CallOptions} 里，共享层再对它取 min ⇒ ⇒
 * <b>min 只压小、不压大</b> ⇒ ⇒ <b>「想放宽上界」会被副本压回 2000ms</b>
 * ⇒ ⇒ 「上界可 env 覆盖」在<b>放宽方向</b>上被<b>架空</b>
 * ⇒ ⇒ 这正是本类第 ④ 条要抓的形态：把 cap 覆盖到 <b>比 2000 大</b>，
 * 若出向仍在 2000ms 处收口 ⇒ ⇒ 说明域侧还藏着一份写死 2000 的副本。
 * <p>⚠️ <b>反过来（把 cap 调小）抓不到副本</b>：min 会把它压得更小 ⇒ ⇒ 看起来「生效了」
 * ⇒ ⇒ 这类「只测一个方向」的判据会把真缺陷洗成绿 ⇒ ⇒ 故第 ④ 条必须用<b>放宽</b>方向。
 * <p>⚠️ <b>副本还有第二处副作用</b>：旧副本（follow-rpc 版）用的是
 * {@code withDeadlineAfter}（<b>覆盖</b>语义）⇒ ⇒ 它会把<b>入站预算</b>放宽到 2000ms
 * ⇒ ⇒ 而 Go 的 {@code context.WithTimeout(ctx, 2s)} 是<b>取更早者</b>
 * ⇒ ⇒ 副本与 Go 在「入站预算比 2s 短」时<b>方向相反</b>。
 *
 * <h2>Go 出处（为什么「客户端硬上界」该是 2000ms）</h2>
 * <pre>
 * go-zero@v1.10.2/zrpc/config.go:31
 *     Timeout int64 `json:",default=2000"`   ⇒ 客户端硬上界的【默认值】= 2000ms
 * go-zero@v1.10.2/zrpc/client.go:70-71
 *     if c.Timeout &gt; 0 { opts = append(opts, WithTimeout(...)) }
 * go-zero@v1.10.2/zrpc/internal/clientinterceptors/timeoutinterceptor.go:25
 *     ctx, cancel := context.WithTimeout(ctx, t)   ⇒ 【取更早者】（min 语义）
 * Go 侧 service/follow/rpc/etc/follow.yaml:37-41
 *     UserRpc: { Etcd: { Hosts: …, Key: user.rpc } }   ⇒ 【无显式 Timeout】⇒ ⇒ 恒 2000ms
 * </pre>
 *
 * <h2>⚠️ 本类<b>不</b>证明什么（诚实边界）</h2>
 * <ul>
 *   <li><b>devh 装配链</b>（「全局客户端拦截器真的装在 devh channel 上」）由<b>共享层</b>的
 *       运行期守门承担（{@code GoStyleDevhOutboundDeadlineAssemblyGateTest} ＝ 真 devh 上下文
 *       ＋ 真 {@code @GrpcClient} stub ＋ <b>不调</b> {@code applyTo}）；
 *       本类用 {@code ClientInterceptors.intercept(channel, 生产拦截器)} ＝ 与 devh
 *       {@code AbstractChannelFactory.createChannel} 的装配动作<b>同一条</b>
 *       ⇒ ⇒ 本类证「<b>域配置 → 生产拦截器 → 出向调用封顶</b>」，
 *       <b>不</b>证「devh 的 registry 里有没有它」。</li>
 *   <li>本类<b>不</b>起真实 user-rpc、<b>不</b>连 Nacos ⇒ 它证的是
 *       「<b>客户端硬上界真在运行期封顶</b>」，<b>不</b>证「下游挂死时客户端真的收到 1015」
 *       （那需要双侧活体对拍）。</li>
 *   <li>第 ⑤ 条是<b>源码级</b>判据（静态扫本域 {@code src/main/java}）⇒ ⇒ 它守的是
 *       「<b>没有第二份写死上界的副本</b>」这个<b>形态</b>，不是运行期行为。</li>
 * </ul>
 */
class UserRpcClientDeadlineCapTest {

    /** 本域的 env 覆盖旋钮（与本域 {@code application.yml} 里的占位符同名）。 */
    private static final String ENV_CAP = "FOLLOW_RPC_OUTBOUND_DEADLINE_CAP_MILLIS";

    /** Go 侧 {@code zrpc/config.go:31} 的默认值。 */
    private static final long GO_DEFAULT_CAP_MILLIS = 2000L;

    /** 下游挂起时长（毫秒）—— 必须<b>大于</b>第 ④ 条的覆盖值 ⇒ 才能分辨「有上界」与「没上界」。 */
    private static final long HANG_MILLIS = 8_000L;

    /** 真发调用那条用例的下界 —— 防止「秒失败」被误判成「超时生效」。 */
    private static final long MIN_EXPECTED_WAIT_MILLIS = 1_500L;

    /** 允许的额外耗时（毫秒）—— 排除「别的机制提前掐断」与墙钟抖动。 */
    private static final long SLACK_MILLIS = 1_500L;

    /**
     * 第 ④ 条用例的 env 覆盖值 ——
     * <b>故意取【大于】Go 默认 2000</b> ⇒ 才能抓「域侧又写死了 2000 的私有副本」。
     */
    private static final long OVERRIDE_CAP_MILLIS = 4_000L;

    /**
     * 第 ④ 条：覆盖后出向<b>至少</b>要等到这个点。
     *
     * <p>若域侧藏着写死 {@link #GO_DEFAULT_CAP_MILLIS} 的副本 ⇒ 出向会在 2000ms 处收口
     * ⇒ ⇒ 等不到 3000ms ⇒ ⇒ 断言红。
     */
    private static final long OVERRIDE_MIN_WAIT_MILLIS = 3_000L;

    private Server server;
    private ManagedChannel channel;

    @AfterEach
    void tearDown() {
        // ⚠️ 必须清空：GoStyleRequestDeadline 用 ThreadLocal 存预算，
        // 不清会在线程池复用时串味（它自己的 javadoc 就警告过）。
        GoStyleRequestDeadline.clear();
        if (channel != null) {
            channel.shutdownNow();
        }
        if (server != null) {
            server.shutdownNow();
        }
    }

    // ────────────────────────────────────────────────────────────────────────────
    // ① 配置面：域侧必须「只配参数」，且该参数可被 env 覆盖
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("① 域 yml 必须声明 rag.protection.outbound-deadline.cap-millis，且是 env 可覆盖形态")
    void domainYmlDeclaresEnvOverridableCap() {
        String raw = rawYamlValue("rag.protection.outbound-deadline.cap-millis");

        assertNotNull(raw,
                "🔴 本域 application.yml 必须配 rag.protection.outbound-deadline.cap-millis "
                        + "⇒ ⇒ 否则出向 gRPC 调用【没有客户端硬上界】（Go 侧恒有 2000ms）"
                        + "⇒ ⇒ 共享层的 GoStyleOutboundDeadlineInterceptor 的 cap 取默认值 0 ＝ 不启用"
                        + "⇒ ⇒ 域侧那半就等于没做。");
        assertTrue(raw.startsWith("${") && raw.endsWith("}"),
                "🔴 cap-millis 必须是 ${ENV:默认} 形态（实测 = " + raw + "）⇒ ⇒ 否则【不可 env 覆盖】"
                        + "⇒ ⇒ 动作②「上界可 env 覆盖」不成立。");
        assertTrue(raw.contains(ENV_CAP),
                "🔴 占位符里必须点名本域的 env 旋钮 " + ENV_CAP + "（实测 = " + raw + "）"
                        + "⇒ ⇒ 各域独立可覆盖（Go 侧 RpcClientConf.Timeout 本身就是域级字段）。");
        assertTrue(raw.endsWith(":" + GO_DEFAULT_CAP_MILLIS + "}"),
                "🔴 占位符的默认值必须是 Go 的 zrpc/config.go:31 默认值 " + GO_DEFAULT_CAP_MILLIS
                        + "ms（实测 = " + raw + "）⇒ ⇒ 本域 Go 侧 follow.yaml:37-41 无显式 Timeout"
                        + "⇒ ⇒ 恒 2000ms。");
    }

    // ────────────────────────────────────────────────────────────────────────────
    // ② 绑定面：域 yml 的这个键【真的】被共享层的 properties 读到
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("② 域 yml 绑出的 cap 必须 == Go 默认 2000ms == 域内常量（双源一致）")
    void capBindsToGoDefaultAndMatchesDomainConstant() {
        GoStyleProtectionProperties props = domainProperties(null);

        assertEquals(GO_DEFAULT_CAP_MILLIS, props.getOutboundDeadline().getCapMillis(),
                "🔴 域 yml 的 rag.protection.outbound-deadline.cap-millis 必须真的绑定到"
                        + " GoStyleProtectionProperties.OutboundDeadline#capMillis"
                        + "⇒ ⇒ 键名写错就是【配了不生效】（AGENTS.md §3.3.1）。");
        assertEquals(GO_DEFAULT_CAP_MILLIS, UserRpcClient.CLIENT_TIMEOUT_MILLIS,
                "🔴 域内常量（断言用期望值）与 yml 的真实值必须一致"
                        + "⇒ ⇒ 两处必须同时改，否则就是一处漂移。");
        assertTrue(props.getOutboundDeadline().isEnabled(),
                "🔴 出向 deadline 传播开关默认开（Go 侧 zrpc/internal/config.go:15 的 Timeout 默认 true）"
                        + "⇒ ⇒ 本域不得把它关掉。");
    }

    // ────────────────────────────────────────────────────────────────────────────
    // ③ 运行期：【没有入向 deadline】⇒ 出向仍必须带 ≤2000ms 上界（P2-T04 动作③）
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    @DisplayName("③ 【没有入向 deadline】⇒ 域类出向调用必须在 ≤2000ms 处 DEADLINE_EXCEEDED（真发调用）")
    void outboundCallIsCappedWithoutInboundDeadline() throws Exception {
        GoStyleRequestDeadline.clear();
        UserRpcClient client = startHangingDownstream(domainProperties(null));

        long startedAt = System.nanoTime();
        StatusRuntimeException thrown = assertThrows(StatusRuntimeException.class,
                () -> client.getUser(1L),
                "下游不响应时，follow-rpc 必须靠【客户端硬上界】收口，而不是一直挂着");
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
    // ④ 运行期：env 覆盖【放宽】⇒ 上界【真的】跟着放宽（⇒ 抓「写死 2000 的私有副本」）
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    @DisplayName("④ env 把 cap 覆盖【到比 2000 更大】⇒ 出向上界必须跟着放宽 ⇒ 【证明域侧没有写死上界的私有副本】")
    void envOverrideWidensTheOutboundCap() throws Exception {
        GoStyleRequestDeadline.clear();
        UserRpcClient client = startHangingDownstream(domainProperties(
                Map.of(ENV_CAP, String.valueOf(OVERRIDE_CAP_MILLIS))));

        long startedAt = System.nanoTime();
        StatusRuntimeException thrown = assertThrows(StatusRuntimeException.class,
                () -> client.getUser(1L),
                "下游不响应时必须靠【被 env 覆盖后的】客户端硬上界收口");
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;

        assertEquals(Status.Code.DEADLINE_EXCEEDED, thrown.getStatus().getCode(),
                "失败码必须是 DEADLINE_EXCEEDED（= deadline 到点）");
        assertTrue(elapsedMillis >= OVERRIDE_MIN_WAIT_MILLIS,
                "🔴 env 把 cap 覆盖成 " + OVERRIDE_CAP_MILLIS + "ms ⇒ 出向必须在它附近收口"
                        + "（实测耗时 = " + elapsedMillis + "ms）⇒ ⇒ 若这里只等到 "
                        + GO_DEFAULT_CAP_MILLIS + "ms 左右，说明【本域又有人写了一份写死 "
                        + GO_DEFAULT_CAP_MILLIS + "ms 的私有副本】"
                        + "⇒ ⇒ min 只压小不压大 ⇒ ⇒ 那个副本会把【想放宽的上界】压回去"
                        + "⇒ ⇒ 动作②「上界可 env 覆盖」在放宽方向上被架空。");
        assertTrue(elapsedMillis <= OVERRIDE_CAP_MILLIS + SLACK_MILLIS,
                "出向不应比被覆盖后的上界（" + OVERRIDE_CAP_MILLIS + "ms）晚太多，实际耗时 = "
                        + elapsedMillis + "ms");
    }

    // ────────────────────────────────────────────────────────────────────────────
    // ⑤ 形态面：域侧【不得】再出现写死客户端上界的私有副本（单一来源）
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("⑤ 本域 src/main/java 不得出现 withDeadlineAfter ⇒ 【域侧只配参数，不造第二份上界实现】")
    void domainSideMustNotReintroduceAHardcodedOutboundCap() throws Exception {
        Path mainJava = moduleRoot().resolve("src").resolve("main").resolve("java");
        List<String> offenders = new ArrayList<>();

        try (Stream<Path> walk = Files.walk(mainJava)) {
            for (Path p : (Iterable<Path>) walk.filter(f -> f.toString().endsWith(".java"))::iterator) {
                for (String raw : Files.readString(p, StandardCharsets.UTF_8).split("\\R")) {
                    String line = raw.strip();
                    // ⚠️ 只认【代码行】：javadoc / 行注释里提到 withDeadlineAfter 不算
                    if (line.isEmpty() || line.startsWith("*") || line.startsWith("//") || line.startsWith("/*")) {
                        continue;
                    }
                    if (line.contains("withDeadlineAfter")) {
                        offenders.add(p.getFileName() + " :: " + line);
                    }
                }
            }
        }

        assertTrue(offenders.isEmpty(),
                "🔴 本域出现了写死出向 deadline 的代码：" + offenders
                        + "⇒ ⇒ 出向【客户端硬上界】只有一个合法实现 = 共享层的"
                        + " GoStyleOutboundDeadlineInterceptor（域侧只配 rag.protection.outbound-deadline.cap-millis）"
                        + "⇒ ⇒ 域侧再写一份 ⇒ 就是【第二个来源】⇒ ⇒ 副本数 = 漂移面"
                        + "⇒ ⇒ 参见共享层棘轮 GoStyleClientCallTimeoutSingleSourceGateTest。");
    }

    // ================== 装置 ==================

    /**
     * 起一个<b>故意不响应</b>的 in-process 下游，把<b>生产拦截器</b>（共享层那个，域侧只配参数）
     * 装到 channel 上 ⇒ ⇒ 返回一个「出向调用必然经过该拦截器」的域 client。
     */
    private UserRpcClient startHangingDownstream(GoStyleProtectionProperties props) throws Exception {
        String serverName = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(serverName)
                .addService(new UserServiceGrpc.UserServiceImplBase() {
                    @Override
                    public void getUser(GetUserReq request, StreamObserver<GetUserResp> responseObserver) {
                        // ⚠️ 故意**挂起 HANG_MILLIS 才响应**（既不立即响应，也**不是永不响应**）
                        // ⇒ 客户端在【客户端硬上界】处失败 ⇒ 这才是真正的触发点；
                        // ⇒ 而「若没有上界」它会在 HANG_MILLIS 后**正常返回**
                        // ⇒ ⇒ 判据能干净地分辨「有上界」与「没上界」，
                        //    而不是靠 @Timeout 把「挂死」包装成「测试超时」。
                        try {
                            Thread.sleep(HANG_MILLIS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        responseObserver.onNext(GetUserResp.newBuilder().build());
                        responseObserver.onCompleted();
                    }
                })
                .build()
                .start();
        channel = InProcessChannelBuilder.forName(serverName).build();

        // ⚠️ 与 devh 的装配动作同一条：devh 的 AbstractChannelFactory.createChannel
        //    最后一行是 ClientInterceptors.interceptForward(channel, interceptors)
        //    ⇒ ⇒ 这里用 intercept(...) 装【生产拦截器】，与生产走同一条路。
        Channel capped = ClientInterceptors.intercept(channel,
                new GoStyleOutboundDeadlineInterceptor(props));

        return new UserRpcClient(UserServiceGrpc.newBlockingStub(capped));
    }

    /**
     * 用本域<b>自己的</b> {@code application.yml} 绑出 {@link GoStyleProtectionProperties}。
     *
     * @param envOverrides 模拟的 OS 环境变量（{@code null} ＝ 不覆盖）
     */
    private static GoStyleProtectionProperties domainProperties(Map<String, Object> envOverrides) {
        ConfigurableEnvironment env = new StandardEnvironment();
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        try {
            for (PropertySource<?> ps : loader.load("domain-application-yml",
                    new ClassPathResource("application.yml"))) {
                env.getPropertySources().addLast(ps);
            }
        } catch (IOException e) {
            throw new IllegalStateException("读不到本域的 application.yml（classpath:/application.yml）", e);
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
                    .load("domain-application-yml", new ClassPathResource("application.yml"));
            for (PropertySource<?> ps : sources) {
                Object v = ps.getProperty(key);
                if (v != null) {
                    return String.valueOf(v);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("读不到本域的 application.yml（classpath:/application.yml）", e);
        }
        return null;
    }

    /**
     * 定位本模块的源码根（含 {@code src/main/java}）。
     *
     * <p>⚠️ <b>不依赖 surefire 的工作目录</b>：先从本类的 code source 反推模块目录
     * （{@code target/test-classes} → {@code target} → 模块目录）⇒ ⇒ 再用 {@code user.dir} 逐级上溯兜底。
     */
    private static Path moduleRoot() {
        try {
            Path loc = Path.of(UserRpcClientDeadlineCapTest.class.getProtectionDomain()
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
                + "⇒ ⇒ 第 ⑤ 条判据失效（判据自己的问题，不是被测物的问题）");
    }
}
