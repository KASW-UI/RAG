package com.rag.message.message.rpc.client;

import com.rag.common.observability.GoStyleRequestDeadline;
import com.rag.common.protection.GoStyleOutboundDeadlineInterceptor;
import com.rag.common.protection.GoStyleProtectionConfiguration;
import com.rag.common.protection.GoStyleProtectionProperties;
import com.rag.message.message.rpc.MessageRpcApplication;
import com.rag.message.message.rpc.exception.MessageExceptions;
import com.rag.users.admin.proto.AdminServiceGrpc;
import com.rag.users.admin.proto.GetUserListReq;
import com.rag.users.admin.proto.GetUserListResp;
import com.rag.users.user.proto.GetUserReq;
import com.rag.users.user.proto.GetUserResp;
import com.rag.users.user.proto.UserServiceGrpc;
import io.grpc.Channel;
import io.grpc.ClientInterceptor;
import io.grpc.ClientInterceptors;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.client.interceptor.AnnotationGlobalClientInterceptorConfigurer;
import net.devh.boot.grpc.client.interceptor.GlobalClientInterceptorRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code message-rpc} 出向 gRPC 调用（→ {@code user-rpc} / → {@code admin-rpc}）的
 * <b>客户端硬上界（2s）</b>守门。
 *
 * <h2>它守的是什么</h2>
 * <p>Go 侧这两个出向调用<b>恒带 2s 客户端超时</b>：
 * <ul>
 *   <li>{@code go-zero@v1.10.2/zrpc/config.go:31} ——
 *       {@code RpcClientConf.Timeout int64 `json:",default=2000"`} ⇒ <b>默认 2000ms</b>；</li>
 *   <li>Go 侧 {@code service/message/rpc/etc/message.yaml} 的 {@code UserRpc:}（`:36`）与
 *       {@code AdminRpc:}（`:42`）两段<b>都没有</b>显式 {@code Timeout}（只有 {@code Etcd}）⇒ ⇒ 走默认值；</li>
 *   <li><b>消费点</b>：{@code go-zero@v1.10.2/zrpc/internal/clientinterceptors/timeoutinterceptor.go:25}
 *       —— {@code ctx, cancel := context.WithTimeout(ctx, t)} ⇒ <b>取更早者</b>。</li>
 * </ul>
 * <p>⇒ 而 RAG 侧 {@code devh} 注入的<b>裸 stub</b>
 * （{@code UserRpcClient.java:28} 的 {@code @GrpcClient("user-rpc")} 与
 * {@code AdminRpcClient.java:42} 的 {@code @GrpcClient("admin-rpc")}）
 * <b>既不继承入向预算，也没有客户端上界</b> ⇒ ⇒ <b>可无限期挂起</b> ⇒ 与 Go 的 2s <b>直接相反</b>。
 *
 * <h2>本轮做了什么（域侧只配参数 · 零代码改动）</h2>
 * <p>本域 {@code application.yml} 新增：
 * <pre>
 * rag:
 *   protection:
 *     outbound-deadline:
 *       cap-millis: ${MESSAGE_RPC_OUTBOUND_DEADLINE_CAP_MILLIS:2000}
 * </pre>
 * ⇒ ⇒ 封顶逻辑<b>唯一</b>由共享层 {@code GoStyleOutboundDeadlineInterceptor} 承担
 * （带 devh 的 {@code @GrpcGlobalClientInterceptor} ⇒ 装在<b>每个</b>出向 devh 调用上）
 * ⇒ ⇒ 本域<b>不写</b>任何私有 helper、<b>不写</b> {@code withDeadlineAfter}。
 *
 * <h2>本测试如何真正触发该机制（AGENTS.md §3.3）</h2>
 * <ol>
 *   <li><b>真拦截器</b>：用<b>生产类</b> {@code GoStyleOutboundDeadlineInterceptor} 装到
 *       <b>真 channel</b> 上（{@code ClientInterceptors.intercept(...)} —— 与 devh 的
 *       {@code AbstractChannelFactory.createChannel} 最后一行 {@code interceptForward} 同一条路）；</li>
 *   <li><b>真发调用</b>：起 <b>in-process 真实 gRPC server</b>，它的实现<b>挂起 8s 后才正常返回</b>
 *       ⇒ ⇒ 客户端必然在<b>自己的上界</b>处失败
 *       ⇒ ⇒ 而「若没有上界」它会在 8s 后<b>正常返回成功</b>
 *       ⇒ ⇒ 判据能干净地分辨「有上界」与「没上界」，<b>而不是</b>靠 {@code @Timeout} 把「挂死」包装成「测试超时」。</li>
 * </ol>
 *
 * <h2>为什么同时断言「裸 stub」与「域 client」两层</h2>
 * <p>⚠️ 两个域 client 的<b>异常形态不同</b>，所以两层都要断：
 * <ul>
 *   <li>{@code UserRpcClient.getProfile}（`:37`）<b>吞掉</b> {@code StatusRuntimeException}
 *       ⇒ 失败时返回兜底数组 {@code ["User {id}", ""]}（Go 侧 profile 失败不阻塞业务）</li>
 *   <li>{@code AdminRpcClient.collectBroadcastRecipients}（`:84`）把 {@code StatusRuntimeException}
 *       <b>包成</b> {@code MessageExceptions.DbSelectException}（由 Logic 层映射成 INTERNAL + 5001）</li>
 * </ul>
 * ⇒ ⇒ 裸 stub 那层用来断<b>失败码</b>（{@code DEADLINE_EXCEEDED}）＋ 耗时窗口
 * ⇒ ⇒ 域 client 那层用来断「<b>域侧不会挂死</b>」（吞异常也必须<b>按时返回</b>）—— 这才是 P2-T04
 * 动作③「即使入向没有 deadline，出向也必须带 ≤2000ms 的上界」在域行为上的落点。
 *
 * <h2>⚠️ 诚实边界</h2>
 * <ul>
 *   <li>本类<b>不起</b>真实 user-rpc / admin-rpc、<b>不连</b> Nacos ⇒ 它证的是
 *       「<b>本域配的上界真的被共享层拦截器读到了、且真的在出向调用上生效</b>」，
 *       <b>不</b>证「整条生产链路的端到端超时表现」（那需要双侧活体对拍）；</li>
 *   <li>本类<b>不</b>带生产链路上的 {@code GoStyleGrpcClientBreakerInterceptor}；</li>
 *   <li>rpc 进程<b>生产上没有入向 ctx 预算载体</b>（{@code GoStyleRequestDeadline.begin} 的生产调用点
 *       全仓只有 HTTP 中间件那一处）⇒ ⇒ 第 ⑤ 条用 {@code begin(...)} <b>注入</b>一个入向预算
 *       来验证 <b>min 语义</b>（共享层拦截器读的就是这个 ThreadLocal）⇒ 那条验的是
 *       <b>共享层的 min 实现</b>，不是「rpc 进程真的有入向预算」。</li>
 * </ul>
 */
class MessageRpcOutboundDeadlineCapGateTest {

    /** 本域的 env 覆盖旋钮（与本域 {@code application.yml} 里的占位符同名）。 */
    private static final String ENV_CAP = "MESSAGE_RPC_OUTBOUND_DEADLINE_CAP_MILLIS";

    /** Go 侧 {@code zrpc/config.go:31} 的默认值。 */
    private static final long GO_DEFAULT_CAP_MILLIS = 2000L;

    /** 下游挂起时长（毫秒）—— 必须<b>大于</b>第 ④ 条的覆盖值 ⇒ 才能分辨「有上界」与「没上界」。 */
    private static final long HANG_MILLIS = 8_000L;

    /** 真发调用那条用例的下界 —— 防止「秒失败」被误判成「超时生效」。 */
    private static final long MIN_EXPECTED_WAIT_MILLIS = 1_500L;

    /** 允许的额外耗时（毫秒）—— 排除「别的机制提前掐断」与墙钟抖动。 */
    private static final long SLACK_MILLIS = 1_500L;

    /** 第 ④ 条的 env 覆盖值 —— <b>故意取【大于】Go 默认 2000</b> ⇒ 才能抓「域侧又写死了 2000 的私有副本」。 */
    private static final long OVERRIDE_CAP_MILLIS = 4_000L;

    /** 第 ④ 条：覆盖后出向<b>至少</b>要等到这个点（若域侧藏着写死 2000 的副本 ⇒ 等不到 ⇒ 断言红）。 */
    private static final long OVERRIDE_MIN_WAIT_MILLIS = 3_000L;

    /** 第 ⑤ 条：注入的入向预算（毫秒）—— 必须<b>远小于</b> 2s ⇒ 才能分辨「取更早者」与「取更晚者」。 */
    private static final long INBOUND_BUDGET_MILLIS = 300L;

    /** 第 ⑤ 条：出向<b>应当</b>在这个窗口内失败（钉的是 min，不是覆盖）。 */
    private static final long INBOUND_MIN_WAIT_MILLIS = 120L;
    private static final long INBOUND_MAX_WAIT_MILLIS = 1_200L;

    private Server server;
    private ManagedChannel channel;

    /** 装了生产拦截器的裸 stub —— 用于断「失败码 + 耗时窗口」这一层。 */
    private UserServiceGrpc.UserServiceBlockingStub cappedUserStub;
    private AdminServiceGrpc.AdminServiceBlockingStub cappedAdminStub;

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
                        + "ms（实测 = " + raw + "）⇒ ⇒ 本域 Go 侧 message.yaml 的 UserRpc:/AdminRpc: 段无显式 Timeout"
                        + "⇒ ⇒ 恒 2000ms。");
    }

    // ────────────────────────────────────────────────────────────────────────────
    // ② 绑定面：域 yml 的这个键【真的】被共享层的 properties 读到
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
    @DisplayName("③ 出向 user-rpc：裸 stub 必须 DEADLINE_EXCEEDED（真发调用）")
    void userRpcStubIsCappedWithoutInboundDeadline() throws Exception {
        GoStyleRequestDeadline.clear();
        startHangingUserRpc(domainProperties(null));

        long startedAt = System.nanoTime();
        StatusRuntimeException thrown = assertThrows(StatusRuntimeException.class,
                () -> cappedUserStub.getUser(GetUserReq.newBuilder().setUid(1L).build()),
                "下游不响应时，出向 user-rpc 必须靠【客户端硬上界】收口，而不是一直挂着");
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
    @DisplayName("③ 域 client 层：user-rpc 失败必须【按时】返回兜底（不挂死）")
    void userRpcDomainClientDoesNotHang() throws Exception {
        GoStyleRequestDeadline.clear();
        UserRpcClient client = startHangingUserRpc(domainProperties(null));

        long startedAt = System.nanoTime();
        String[] profile = client.getProfile(1L);
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;

        assertNotNull(profile, "getProfile 必须返回兜底数组（Go 侧 profile 失败不阻塞业务）");
        assertEquals("User 1", profile[0],
                "🔴 下游挂死时 getProfile 必须落到兜底（说明 StatusRuntimeException 被吞掉的那条路径真的走过了）");
        assertTrue(elapsedMillis >= MIN_EXPECTED_WAIT_MILLIS,
                "必须是「等到 deadline 到期」而不是「立刻失败」，实际耗时 = " + elapsedMillis + "ms");
        assertTrue(elapsedMillis <= GO_DEFAULT_CAP_MILLIS + SLACK_MILLIS,
                "🔴 域 client 不能挂死：出向必须有 2s 客户端硬上界，实际耗时 = " + elapsedMillis + "ms"
                        + "⇒ ⇒ 若这里到了 8s 左右 ⇒ 说明【上界没生效】（那正是 P2-T04 要修的漂移）。");
    }

    @Test
    @Timeout(value = 40, unit = TimeUnit.SECONDS)
    @DisplayName("③ 出向 admin-rpc：裸 stub 必须 DEADLINE_EXCEEDED（真发调用）")
    void adminRpcStubIsCappedWithoutInboundDeadline() throws Exception {
        GoStyleRequestDeadline.clear();
        startHangingAdminRpc(domainProperties(null));

        long startedAt = System.nanoTime();
        StatusRuntimeException thrown = assertThrows(StatusRuntimeException.class,
                () -> cappedAdminStub.getUserList(
                        GetUserListReq.newBuilder().setPage(1L).setPageSize(10L).build()),
                "下游不响应时，出向 admin-rpc 必须靠【客户端硬上界】收口，而不是一直挂着");
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;

        assertEquals(Status.Code.DEADLINE_EXCEEDED, thrown.getStatus().getCode(),
                "下游挂死 ⇒ 客户端 deadline 到点 ⇒ status 必须是 DEADLINE_EXCEEDED");
        assertTrue(elapsedMillis >= MIN_EXPECTED_WAIT_MILLIS,
                "必须是「等到 deadline 到期」而不是「立刻失败」，实际耗时 = " + elapsedMillis + "ms");
        assertTrue(elapsedMillis <= GO_DEFAULT_CAP_MILLIS + SLACK_MILLIS,
                "不应比 2s 客户端硬上界晚太多，实际耗时 = " + elapsedMillis + "ms");
    }

    @Test
    @Timeout(value = 40, unit = TimeUnit.SECONDS)
    @DisplayName("③ 域 client 层：admin-rpc 失败必须【按时】抛 DbSelectException（cause=DEADLINE_EXCEEDED）")
    void adminRpcDomainClientDoesNotHang() throws Exception {
        GoStyleRequestDeadline.clear();
        AdminRpcClient client = startHangingAdminRpc(domainProperties(null));

        long startedAt = System.nanoTime();
        MessageExceptions.DbSelectException thrown = assertThrows(MessageExceptions.DbSelectException.class,
                () -> client.collectBroadcastRecipients(10L),
                "下游不响应时，域 client 必须按时把 RPC 失败映射成 DbSelectException，而不是一直挂着");
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;

        StatusRuntimeException cause = assertInstanceOf(StatusRuntimeException.class, thrown.getCause(),
                "🔴 DbSelectException 的 cause 必须是原始的 StatusRuntimeException（⇒ 失败码可观测）");
        assertEquals(Status.Code.DEADLINE_EXCEEDED, cause.getStatus().getCode(),
                "🔴 失败码必须是 DEADLINE_EXCEEDED（= 客户端硬上界到点）"
                        + "⇒ ⇒ 若这里变成 UNAVAILABLE/INTERNAL ⇒ 说明不是上界收口，而是连接层问题。");
        assertTrue(elapsedMillis >= MIN_EXPECTED_WAIT_MILLIS,
                "必须是「等到 deadline 到期」而不是「立刻失败」，实际耗时 = " + elapsedMillis + "ms");
        assertTrue(elapsedMillis <= GO_DEFAULT_CAP_MILLIS + SLACK_MILLIS,
                "🔴 域 client 不能挂死：出向必须有 2s 客户端硬上界，实际耗时 = " + elapsedMillis + "ms");
    }

    // ────────────────────────────────────────────────────────────────────────────
    // ④ 运行期：env 覆盖【放宽】⇒ 上界【真的】跟着放宽（⇒ 抓「写死 2000 的私有副本」）
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @Timeout(value = 40, unit = TimeUnit.SECONDS)
    @DisplayName("④ env 把 cap 覆盖【到比 2000 更大】⇒ 出向上界必须跟着放宽 ⇒ 【证明域侧没有写死上界的私有副本】")
    void envOverrideWidensTheOutboundCap() throws Exception {
        GoStyleRequestDeadline.clear();
        // ⚠️ **必须走【域 client】（生产路径）**，不能走裸 stub ⇒ ⇒ 否则「域 client 里又写了一份
        //    写死 2000ms 的副本」这种漂移**根本不会被这条抓到**（裸 stub 不经过那份副本）
        //    ⇒ ⇒ 2026-10-31 变异实测：④ 改走裸 stub 时，M-D（域侧加回写死副本）**不红** ⇒ ⇒ 判据形态错 ⇒ 修正。
        UserRpcClient client = startHangingUserRpc(domainProperties(
                Map.of(ENV_CAP, String.valueOf(OVERRIDE_CAP_MILLIS))));

        long startedAt = System.nanoTime();
        client.getProfile(1L);
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;

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
    // ⑤ 运行期：min 语义 —— 入向预算比上界更早 ⇒ 必须取【更早者】
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @Timeout(value = 40, unit = TimeUnit.SECONDS)
    @DisplayName("⑤ 入向预算 300ms < cap 2000ms ⇒ 出向必须在【300ms】处失败（钉的是 min，不是覆盖）")
    void inboundBudgetWinsOverCap() throws Exception {
        GoStyleRequestDeadline.clear();
        // ⚠️ 模拟「入向请求带 300ms 预算」（rpc 进程生产上没有这个载体 ⇒ 这里用 begin 注入）
        GoStyleRequestDeadline.begin(System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(INBOUND_BUDGET_MILLIS));
        startHangingUserRpc(domainProperties(null));

        long startedAt = System.nanoTime();
        StatusRuntimeException thrown = assertThrows(StatusRuntimeException.class,
                () -> cappedUserStub.getUser(GetUserReq.newBuilder().setUid(1L).build()),
                "入向预算比 cap 更早 ⇒ 出向必须在入向预算处失败");
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;

        assertEquals(Status.Code.DEADLINE_EXCEEDED, thrown.getStatus().getCode(),
                "失败码必须是 DEADLINE_EXCEEDED（= deadline 到点）");
        assertTrue(elapsedMillis >= INBOUND_MIN_WAIT_MILLIS && elapsedMillis <= INBOUND_MAX_WAIT_MILLIS,
                "🔴 入向预算 " + INBOUND_BUDGET_MILLIS + "ms < cap " + GO_DEFAULT_CAP_MILLIS
                        + "ms ⇒ 有效值必须是【更早者】= " + INBOUND_BUDGET_MILLIS + "ms"
                        + "（实测耗时 = " + elapsedMillis + "ms）"
                        + "⇒ ⇒ 若实测落在 2s 附近 ⇒ 说明是【覆盖】而不是【取更早者】"
                        + "⇒ ⇒ 那就与 Go 的 context.WithTimeout(parent, 2s) 方向相反了。");
    }

    // ────────────────────────────────────────────────────────────────────────────
    // ⑥ 形态面：域侧【不得】再出现写死客户端上界的实现（单一来源）
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("⑥ 本域 src/main/java 不得出现 withDeadlineAfter ⇒ 【域侧只配参数，不造第二份上界实现】")
    void domainSideMustNotReintroduceAHardcodedOutboundCap() throws Exception {
        List<String> offenders = new ArrayList<>();

        try (var walk = Files.walk(moduleRoot().resolve("src").resolve("main").resolve("java"))) {
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
                        + "⇒ ⇒ 域侧再写一份 ⇒ 就是【第二个来源】⇒ ⇒ 副本数 = 漂移面。");
    }

    // ────────────────────────────────────────────────────────────────────────────
    // ⑦ 装配面：域侧启动类的**真实声明** ＋ 本域磁盘 yml ⇒ 共享层拦截器真成 Bean、真被 devh 收走
    //
    // ⚠️ **这一条补的是哪条缺口**：
    //   前面 ①~⑥ 全部是「把生产拦截器【手工】装到 channel 上」⇒ ⇒ 那只证
    //   「【假如】本域进程装上了它，它的行为对」，
    //   **不证**「本域进程真的装上了它」（AGENTS.md §3.3.1：配置里没写 ≠ 没开）。
    //   ⇒ ⇒ 本条拆成两半：
    //     (1) 声明面 —— 反射读**本域启动类**上的 {@code @Import} 声明（不复制、不手抄）；
    //     (2) 运行面 —— 真上下文 ＋ **本域磁盘那份 application.yml** ⇒ 拦截器成 Bean、cap 绑出 2000、
    //         且 devh 自己的收集器（生产里由 starter 的自动配置注册）真的把它收走。
    //
    // ⚠️ **rpc 侧的声明面形态与 api 侧不同**：rpc 模块**刻意不扫** {@code com.rag.common}
    //   （那一族里有 {@code jakarta.servlet.Filter}，全包扫描会把 web 类拖进纯 gRPC 进程）
    //   ⇒ ⇒ 共享层保护层只能靠 {@code @Import} 精确引入 ⇒ ⇒ 本条断言的是 {@code @Import} 名单。
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("⑦ 生产装配面：本域启动类 @Import 了共享层保护层 + 本域磁盘 yml ⇒ 拦截器成 Bean、cap 绑出 2000、devh 真的收它")
    void productionWiringReallyProducesTheInterceptorWithTheDomainCap() {
        // ── (1) 声明面 ──
        Set<Class<?>> imports = effectiveImports(MessageRpcApplication.class);
        assertTrue(imports.contains(GoStyleProtectionConfiguration.class),
                "🔴 本域启动类（MessageRpcApplication）的 @Import 必须含 GoStyleProtectionConfiguration"
                        + "（实测 = " + imports + "）"
                        + "⇒ ⇒ 本模块刻意不扫 com.rag.common ⇒ ⇒ 共享层保护层只能靠 @Import 进得来"
                        + "⇒ ⇒ 缺了这一行 ⇒ ⇒ 出向 deadline 拦截器在本域【不成 Bean】"
                        + "⇒ ⇒ 域 yml 的 cap 配了也不生效（「配置里没写 ≠ 没开」，AGENTS.md §3.3.1）。");

        // ── (2) 运行面 ──
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
            ctx.register(GoStyleProtectionConfiguration.class);
            // ⚠️ devh 自己的收集器（生产里由 grpc-client starter 的自动配置注册）
            ctx.registerBean(AnnotationGlobalClientInterceptorConfigurer.class);
            addDomainYamlTo(ctx);
            ctx.refresh();

            GoStyleProtectionProperties props = ctx.getBeanProvider(GoStyleProtectionProperties.class)
                    .getIfAvailable();
            assertNotNull(props,
                    "🔴 共享层的 GoStyleProtectionConfiguration 必须在本域上下文里产出 properties Bean"
                            + "（它带 @EnableConfigurationProperties）");
            assertEquals(GO_DEFAULT_CAP_MILLIS, props.getOutboundDeadline().getCapMillis(),
                    "🔴 本域磁盘 application.yml 的 rag.protection.outbound-deadline.cap-millis "
                            + "必须经【生产的绑定路径】绑到 capMillis 上（实测 = "
                            + props.getOutboundDeadline().getCapMillis() + "）"
                            + "⇒ ⇒ 若是 0 ⇒ ⇒ 键没被读到 ⇒ ⇒ 共享层默认不启用上界。");

            GoStyleOutboundDeadlineInterceptor interceptor = ctx.getBeanProvider(
                    GoStyleOutboundDeadlineInterceptor.class).getIfAvailable();
            assertNotNull(interceptor,
                    "🔴 共享层的 GoStyleOutboundDeadlineConfiguration 必须在本域上下文里产出拦截器 Bean");

            List<ClientInterceptor> collected = new GlobalClientInterceptorRegistry(ctx).getClientInterceptors();
            assertTrue(collected.contains(interceptor),
                    "🔴 devh 的 GlobalClientInterceptorRegistry 必须真的收它（实测名单 = " + collected + "）"
                            + "⇒ ⇒ 否则它只是个 Bean，channel factory 不会装配 ⇒ ⇒ 出向调用没有上界。");
        }
    }

    // ================== 装置 ==================

    /**
     * 起一个<b>挂起 {@link #HANG_MILLIS} 才响应</b>的 in-process {@code user-rpc} 下游，
     * 把<b>生产拦截器</b>装到 channel 上。
     */
    private UserRpcClient startHangingUserRpc(GoStyleProtectionProperties props) throws Exception {
        String serverName = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(serverName)
                .addService(new UserServiceGrpc.UserServiceImplBase() {
                    @Override
                    public void getUser(GetUserReq request, StreamObserver<GetUserResp> responseObserver) {
                        // ⚠️ 挂起 HANG_MILLIS 才响应（既不立即响应，也**不是永不响应**）
                        // ⇒ 客户端在【客户端硬上界】处失败；
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
        cappedUserStub = UserServiceGrpc.newBlockingStub(capped);

        return new UserRpcClient(cappedUserStub);
    }

    /**
     * 起一个<b>挂起 {@link #HANG_MILLIS} 才响应</b>的 in-process {@code admin-rpc} 下游。
     *
     * ⚠️ 挂的是 {@code getUserList}（{@code collectBroadcastRecipients} 真正调的那个方法）。
     */
    private AdminRpcClient startHangingAdminRpc(GoStyleProtectionProperties props) throws Exception {
        String serverName = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(serverName)
                .addService(new AdminServiceGrpc.AdminServiceImplBase() {
                    @Override
                    public void getUserList(GetUserListReq request,
                            StreamObserver<GetUserListResp> responseObserver) {
                        try {
                            Thread.sleep(HANG_MILLIS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        responseObserver.onNext(GetUserListResp.newBuilder().build());
                        responseObserver.onCompleted();
                    }
                })
                .build()
                .start();
        channel = InProcessChannelBuilder.forName(serverName).build();

        Channel capped = ClientInterceptors.intercept(channel,
                new GoStyleOutboundDeadlineInterceptor(props));
        cappedAdminStub = AdminServiceGrpc.newBlockingStub(capped);

        return new AdminRpcClient(cappedAdminStub);
    }

    /**
     * 用本域<b>自己的</b> {@code application.yml}（<b>磁盘上的那份</b>，不是 classpath 里任意一份）
     * 绑出 {@link GoStyleProtectionProperties}。
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

    /** 本域 {@code application.yml} 的<b>磁盘路径</b>（本模块源码根下那一份）。 */
    private static Path domainYamlPath() {
        return moduleRoot().resolve("src").resolve("main").resolve("resources").resolve("application.yml");
    }

    /** 读出某个启动类 {@code @Import} 的<b>直接导入名单</b>（rpc 侧靠它精确引入共享层 Bean）。 */
    private static Set<Class<?>> effectiveImports(Class<?> applicationClass) {
        Set<Class<?>> imports = new LinkedHashSet<>();

        Import importAnnotation = applicationClass.getDeclaredAnnotation(Import.class);
        if (importAnnotation != null) {
            imports.addAll(Arrays.asList(importAnnotation.value()));
        }
        return imports;
    }

    /** 把<b>本域磁盘那份</b> {@code application.yml} 加进给定上下文的 Environment。 */
    private static void addDomainYamlTo(AnnotationConfigApplicationContext ctx) {
        try {
            for (PropertySource<?> ps : new YamlPropertySourceLoader()
                    .load("domain-application-yml", new FileSystemResource(domainYamlPath()))) {
                ctx.getEnvironment().getPropertySources().addLast(ps);
            }
        } catch (IOException e) {
            throw new IllegalStateException("读不到本域的 application.yml（" + domainYamlPath() + "）", e);
        }
        // ⚠️ 与生产同一条路：Boot 启动时会 attach 这一层（relaxed binding 的载体）
        ConfigurationPropertySources.attach(ctx.getEnvironment());
    }

    /**
     * 定位本模块的源码根（含 {@code src/main/java}）。
     *
     * <p>⚠️ <b>不依赖 surefire 的工作目录</b>：先从本类的 code source 反推模块目录
     * （{@code target/test-classes} → {@code target} → 模块目录）⇒ ⇒ 再用 {@code user.dir} 逐级上溯兜底。
     */
    private static Path moduleRoot() {
        try {
            Path loc = Path.of(MessageRpcOutboundDeadlineCapGateTest.class.getProtectionDomain()
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
