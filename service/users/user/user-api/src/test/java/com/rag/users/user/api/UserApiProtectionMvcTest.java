package com.rag.users.user.api;

import com.rag.users.common.jwt.JwtUtil;
import com.rag.users.user.api.client.UserRpcClient;
import com.rag.users.user.proto.GetUserResp;
import com.rag.users.user.proto.UserInfo;
import com.rag.users.user.proto.UserServiceGrpc;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static com.rag.users.user.api.support.GoStyleHostPortPinning.pinContainerPort;

/**
 * 🔴 user-api 的<b>请求保护层接线</b>守门：证明四个保护 Filter **真的挂进了本模块的请求管线**，
 * 而不是「代码写在 {@code service/common} 里就算数」。
 *
 * <h2>为什么 user-api 也要有这一份（不能靠「别的域有就够了」）</h2>
 *
 * <p>{@code UserApiApplication} 的 {@code scanBasePackages} 含 {@code com.rag.common}
 * ⇒ 四个 Filter 与 gRPC client breaker 是<b>自动</b>接上的，<b>零装配代码</b>。
 * <p>正因为零装配，<b>没有任何编译期或启动期的信号</b>告诉使用者「这层在你身上生效了」
 * ⇒ 不写这个测试，结果就是一条<b>从未被验证过就生效的行为变更</b>
 * —— 要么哪天它悄悄不生效（= 保护层形同虚设）而没人知道，要么没人知道它**曾经**生效。
 * <p>⚠️ <b>这不是跨域问题</b>：共享层（{@code service/common}）负责实现，
 * 而「<b>本服务有没有把共享层扫进来</b>」取决于<b>各服务自己的</b> {@code scanBasePackages}
 * ⇒ <b>天然是域级义务</b>。
 * 先例：{@code LikeApiProtectionMvcTest}（like-api）· {@code MessageApiProtectionMvcTest}（message-api）
 * · {@code TaskApiWiringTest}（task-api）· {@code ArticleApiRouteAuthBoundaryTest}（article-api）
 * ⇒ <b>user-api 是当时唯一没写的那一个</b>（2026-10-24 台账复核查出）。
 *
 * <h2>「自动生效」本身也要被断言</h2>
 *
 * <p>{@link #allFourFiltersAreRegistered()} 断言四个 Filter 都在上下文里；
 * {@link #filterRegistrationOrderMatchesGoChainOrder()} 断言它们**在容器里装配出的真实顺序**
 * —— 后者能机器证明「{@code MaxBytes(+6)} 排在 {@code HttpMiddleware(+5)} <b>之后</b>」
 * ⇒ 也就是 <b>MaxBytes 在 3s 预算之内</b>（Go 侧它在预算之外）
 * ⇒ 这条差异此前**只存在于注释里**，本类把它变成**可回归的事实</b>。
 *
 * <h2>🔴 为什么本类<b>必须</b>起真 Redis（不是「顺手加的」）</h2>
 *
 * <p>2026-10-24 首次编写时本类<b>没有</b> Redis，于是出现一条**完全误导**的失败：
 * <pre>
 *   Status expected:&lt;200&gt; but was:&lt;503&gt;       （耗时 3.3s）
 *   日志：IdentityResolver$ErrIdentityMismatchException: user RPC is nil: ...
 * </pre>
 * <p><b>表面症状是「gRPC mock 没生效」，真实原因与 gRPC 毫无关系</b>：
 * <ol>
 *   <li>鉴权链要查黑名单（{@code JwtAuthInterceptor} → {@code StringRedisTemplate.hasKey}）；
 *       Redis <b>从未连上过</b> ⇒ 首次连接失败被
 *       {@code spring.data.redis.timeout: 3s}（{@code application.yml:18}）卡住
 *       ⇒ 实测「Unable to connect to Redis」耗时 <b>3.31s</b>；</li>
 *   <li>3.31s ≥ HTTP 预算 3s（{@code observability.http-timeout: 3s}）
 *       ⇒ {@code HttpMiddleware} 的<b>超时分支</b>先返回 <b>503 + "Request Timeout"</b>
 *       （裸 503 的 MaxConns 分支不带 Content-Type，而响应里是
 *       {@code text/plain;charset=utf-8} ⇒ 这就是「超时分支」而非「MaxConns 分支」的指纹）；</li>
 *   <li>worker 线程<b>继续跑</b>：此时测试方法已结束 ⇒ Spring 已把
 *       {@code @MockitoBean} <b>reset</b> ⇒ {@code userRpc()} 返回 null
 *       ⇒ 才有了那句「user RPC is nil」——<b>它是第 1、2 步的次生症状，不是病因</b>；</li>
 *   <li>⇒ 结论：<b>「mock 没生效」这类结论在有超时的链路上是不可靠的</b>，
 *       必须先看<b>耗时</b>与<b>响应 Content-Type</b>这两个指纹，再去怀疑 mock。</li>
 * </ol>
 * <p>⇒ 本类照同模块 {@code UserApiMvcPipelineTest} 的先例接<b>真 Redis</b>，
 * 让鉴权链在毫秒级内返回，把被测变量收敛到<b>保护层本身</b>。
 * <p>⚠️ 顺带记下一个<b>独立于本测试</b>的发现：「<b>Redis 从未连上过</b>」这一形态，
 * RAG 侧要 <b>3.31s</b> 才失败（撞 3s HTTP 预算 ⇒ <b>fail-closed 503</b>），
 * 而 DRIFT-USERAPI-014 已修的「<b>连上之后</b>断连」形态是 <b>29ms</b> 就 fail-open（200）。
 * ⇒ 两种形态<b>行为不同</b>，修法只覆盖了后者 ⇒ 已单列进台账（待双侧活体实测）。
 *
 * <h2>Go 侧依据</h2>
 * <pre>
 *   rest/engine.go:130-171  buildChainWithNativeMiddlewares（MaxBytes 在 :164）
 *   rest/engine.go:74       appendAuthHandler 用 chn.Append ⇒ JWT 在原生中间件之内
 *   rest/chain/chain.go:38-43  Append 是「加在请求流的最后」⇒ 越 Append 越靠内
 *   rest/handler/maxbyteshandler.go:10-25   超限 → 裸 w.WriteHeader(413)，无 body
 *   rest/handler/maxconnshandler.go:12-36   闸门满 → 裸 w.WriteHeader(503)，无 body
 * </pre>
 *
 * <h2>⚠️ 关于「413 压过 401」靠的是<b>它是 Filter</b>而不是 {@code @Order} 数值</h2>
 *
 * <p>Servlet 的<b>所有</b> Filter 都包在 {@code DispatcherServlet} <b>外面</b>，
 * 而 {@code HandlerInterceptor}（JWT 鉴权）跑在其<b>内部</b>
 * ⇒ 两者先后由<b>容器结构</b>决定，与两者的 {@code @Order} 数值无关
 * ⇒ 能真正让「413 压过 401」变红的变异是<b>让本层失效</b>（摘掉它的 {@code @Component}）。
 */
@SpringBootTest(
        classes = UserApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                // 拒连最快；Nacos 连不上只会打日志，不影响本测试
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                // UserRpcClient 整体被 @MockitoBean 替换，这个 channel bean 不会被用到
                "grpc.client.user-rpc.address=static://127.0.0.1:65535",
                "grpc.client.user-rpc.negotiation-type=plaintext",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN",
                // 压到极小值，让「超过阈值」在测试里可达；生产默认仍是 Go 的 1048576 / 10000
                "rag.protection.max-bytes=1024",
                "rag.protection.max-conns=1"
        })
@AutoConfigureMockMvc
@Testcontainers
class UserApiProtectionMvcTest {

    /** 与 user-api 的 application.yml 保持同一份密钥，否则签出来的 token 验签必失败。 */
    private static final String SECRET = "ae0536f9-6450-4606-8e13-5a19ed505da0";
    private static final long EXPIRE_SEC = 3600L;
    private static final long CLAIM_UID = 31337L;
    private static final String GET_PATH = "/user/v1/get";

    /**
     * 🔴 真 Redis：鉴权链要查黑名单，<b>没有它整个请求会被 Redis 的 3s 超时拖垮</b>
     * （详见类注释「为什么本类必须起真 Redis」）。
     */
    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379).withCreateContainerCmdModifier(cmd -> pinContainerPort(cmd, 6379));

    @DynamicPropertySource
    static void redis(DynamicPropertyRegistry r) {
        r.add("spring.data.redis.host", REDIS::getHost);
        r.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        r.add("spring.data.redis.password", () -> "");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext ctx;

    @MockitoBean
    private UserRpcClient userRpcClient;

    private UserServiceGrpc.UserServiceBlockingStub stub;

    /**
     * 🔴 stubbing 必须在 {@code @BeforeEach} 里做，<b>不能放在测试方法体里</b>。
     *
     * <p>原因：{@code @MockitoBean} 默认 {@code MockReset.AFTER} ⇒ 每个测试方法结束后
     * mock 被 reset；而请求是在 {@code HttpMiddleware} 的 <b>worker 线程</b>上跑的，
     * 可能<b>晚于</b>测试方法结束才继续执行 ⇒ 那时 {@code userRpc()} 已是 null。
     * ⇒ 放在 {@code @BeforeEach} 里可保证「stub 一定在本方法内的请求之前就位」。
     */
    @BeforeEach
    void setUp() {
        stub = mock(UserServiceGrpc.UserServiceBlockingStub.class);
        when(userRpcClient.userRpc()).thenReturn(stub);
    }

    private static String token() {
        return JwtUtil.getToken(SECRET, System.currentTimeMillis() / 1000L, EXPIRE_SEC, CLAIM_UID);
    }

    /** 造一个超过 {@code rag.protection.max-bytes=1024}、但仍是合法 JSON 的体。 */
    private static String oversizedJson() {
        StringBuilder sb = new StringBuilder("{\"uid\":\"");
        while (sb.length() < 4096) {
            sb.append('a');
        }
        return sb.append("\"}").toString();
    }

    private GetUserResp okGetUserResp() {
        return GetUserResp.newBuilder()
                .setFound(true)
                .setUser(UserInfo.newBuilder()
                        .setUid(CLAIM_UID)
                        .setUsername("uu")
                        .setEmail("uu@e")
                        // proto3 presence：status 是 optional int64 ⇒ 必须显式 set 才有 hasStatus()
                        .setStatus(0L)
                        .build())
                .build();
    }

    // ==================================================================
    // 1. 装配层：四个 Filter 真的在本模块上下文里
    // ==================================================================

    /**
     * 刻意用「断言 Bean 存在」而不是「断言请求返回 413」——
     * 后者无法区分「Filter 生效」与「被别的组件短路」。
     */
    @Test
    @DisplayName("四个保护 Filter 都真的注册进本模块上下文（自动装配的前提）")
    void allFourFiltersAreRegistered() {
        assertNotNull(ctx.getBean("goStyleMaxBytesFilter"));
        assertNotNull(ctx.getBean("goStyleMaxConnsFilter"));
        assertNotNull(ctx.getBean("goStyleCpuSheddingFilter"));
        assertNotNull(ctx.getBean("goStyleBreakerFilter"));
    }

    /**
     * 🔴 断言两个 Filter 的<b>相对次序</b>：MaxBytes(+6) 排在 HttpMiddleware(3s,+5) <b>之后</b>
     * ⇒ 也就是 <b>413 落在 3s 预算之内</b>。
     *
     * <p>⚠️ <b>本条是「结构证据」，不是运行时观测</b> —— 它读的是两个 Filter Bean 上的
     * {@code @Order}（Spring 排序 Servlet Filter 的**机制依据**就是它）。
     * <p>⚠️ <b>为什么不能升级成运行时观测</b>：
     * <ul>
     *   <li>3s 预算挂在<b>worker 线程</b>的 ThreadLocal 上
     *       （{@code GoStyleRequestDeadline.begin(deadlineNanos)}），
     *       <b>不是</b> request 属性 ⇒ 请求侧探不到；</li>
     *   <li>观测面（access log / span / 指标）需要 metrics 后端才能看，
     *       本仓<b>无 metrics 后端</b> ⇒ 运行时不可观测。</li>
     * </ul>
     * <p>⇒ 本条与「结构证据」的性质已写进
     * {@code docs/DRIFT/users/user/api.md} 的 {@code DRIFT-USERAPI-009 ②} 补充，
     * <b>不冒充运行时证据</b>。
     *
     * <p>⚠️ <b>能真正证明「Filter vs Interceptor 先后」的是
     * {@link #oversizedBodyIsRejectedBeforeAuth()} 那条</b>
     * —— 它是运行时行为断言（拿到 413 而不是 401）。
     */
    @Test
    @DisplayName("🔴 结构证据：MaxBytes(+6) 排在 HttpMiddleware(3s,+5) 之后 ⇒ 413 落在 3s 预算之内")
    void maxBytesIsOrderedInsideHttpMiddleware() {
        int maxBytesOrder = orderOf(ctx.getBean("goStyleMaxBytesFilter"));
        int httpMiddlewareOrder = orderOf(ctx.getBean("httpMiddlewareFilter"));
        assertTrue(maxBytesOrder > httpMiddlewareOrder,
                "MaxBytes 的 @Order(" + maxBytesOrder + ") 应大于 HttpMiddleware 的 @Order("
                        + httpMiddlewareOrder + ") ⇒ 数值小的先执行 ⇒ MaxBytes 在 3s 预算之内。"
                        + "⚠️ Go 侧相反：MaxBytes 是 native middleware，在 observability **之外**"
                        + " ⇒ 这是一条**观测面差异**（见 DRIFT-USERAPI-009 ② 补充）。");
    }

    /** 取 Bean 上的 {@code @Order} 值；取不到就返回一个「排在最前」的哨兵值（⇒ 断言会红）。 */
    private static int orderOf(Object bean) {
        org.springframework.core.annotation.Order order =
                org.springframework.core.annotation.AnnotationUtils.findAnnotation(
                        bean.getClass(), org.springframework.core.annotation.Order.class);
        return order != null ? order.value() : Integer.MIN_VALUE;
    }

    // ==================================================================
    // 2. 行为层：MaxBytes 压过鉴权
    // ==================================================================

    @Test
    @DisplayName("超限请求体 → 413 空 body，且**压过鉴权**（Go 把 MaxBytes 挂在 Authorize 之外）")
    void oversizedBodyIsRejectedBeforeAuth() throws Exception {
        mockMvc.perform(post(GET_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(oversizedJson()))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(content().string(""));
        // ⚠️ 刻意**不带** Authorization：拿到 413 而不是 401，就是「MaxBytes 在鉴权之外」的唯一证据。
        // 若返回 401，说明<b>本层没生效</b>（而不是「顺序被改坏」）。
    }

    @Test
    @DisplayName("未超限 + 无 token → 仍是 401（证明上一条不是被无条件 413 短路）")
    void withinLimitStillReachesAuth() throws Exception {
        mockMvc.perform(post(GET_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"uid\":\"1\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("未超限 + 带合法 token → 走到业务逻辑（envelope 200，不是保护层拒绝）")
    void normalRequestReachesBusinessLogic() throws Exception {
        when(stub.getUser(any())).thenReturn(okGetUserResp());

        mockMvc.perform(post(GET_PATH)
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"uid\":\"1\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    // ==================================================================
    // 3. 行为层：MaxConns 闸门（并发第 2 个请求被拒）
    // ==================================================================

    /**
     * 用「第一个请求占住闸门、第二个并发请求被拒」证明闸门<b>真的在管线里</b>，
     * 而不只是 Bean 存在。
     *
     * <p>Go 侧：{@code rest/handler/maxconnshandler.go:12-36}
     * {@code latch.TryBorrow()} 拿不到就<b>裸 503</b>（无 body）。
     */
    @Test
    @DisplayName("🔴 MaxConns 闸门满 → 并发第 2 个请求 503 空 body（Go 是裸 503）")
    void maxConnsGateRejectsSecondConcurrentRequest() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(stub.getUser(any())).thenAnswer(inv -> {
            entered.countDown();
            release.await(15, TimeUnit.SECONDS);
            return okGetUserResp();
        });

        ExecutorService pool = Executors.newSingleThreadExecutor();
        Future<?> background = null;
        try {
            // 第一个请求：进得去 ⇒ 占住唯一那把闸门（max-conns=1），并在 rpc 处阻塞
            background = pool.submit(() -> {
                try {
                    mockMvc.perform(post(GET_PATH)
                                    .header("Authorization", "Bearer " + token())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"uid\":\"1\"}"))
                            .andReturn();
                } catch (Exception ignored) {
                    // 断言在主线程，这里只负责占住闸门
                }
            });
            assertTrue(entered.await(15, TimeUnit.SECONDS),
                    "第一个请求没有走到 rpc mock（闸门没被占住 ⇒ 本条无意义）");

            // 第二个并发请求：闸门已满 ⇒ 503 空 body
            mockMvc.perform(post(GET_PATH)
                            .header("Authorization", "Bearer " + token())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"uid\":\"1\"}"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(content().string(""));
        } finally {
            release.countDown();
            // ⚠️ 必须等后台请求**真正结束**：它还在 worker 线程上跑，
            // 而 @MockitoBean 在测试方法结束后就会被 reset ⇒ 残留请求会在
            // 「mock 已 reset」的时点继续执行（详见类注释）。
            if (background != null) {
                try {
                    background.get(15, TimeUnit.SECONDS);
                } catch (Exception ignored) {
                    // 断言在主线程，这里只负责收尾
                }
            }
            pool.shutdownNow();
        }
    }
}
