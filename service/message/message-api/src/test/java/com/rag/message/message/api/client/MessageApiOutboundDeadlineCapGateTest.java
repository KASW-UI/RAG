package com.rag.message.message.api.client;

import com.rag.common.observability.GoStyleRequestDeadline;
import com.rag.common.protection.GoStyleOutboundDeadlineInterceptor;
import com.rag.common.protection.GoStyleProtectionProperties;
import com.rag.message.message.api.MessageApiApplication;
import com.rag.message.message.proto.MessageServiceGrpc;
import com.rag.message.message.proto.SendNotificationReq;
import com.rag.message.message.proto.SendNotificationResp;
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
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code message-api} 出向 gRPC 调用（→ {@code message-rpc}）的<b>客户端硬上界（2s）</b>守门。
 *
 * <h2>它守的是什么</h2>
 * <p>Go 侧 {@code message-api → message-rpc} 这个出向调用<b>恒带 2s 客户端超时</b>：
 * <ul>
 *   <li>{@code go-zero@v1.10.2/zrpc/config.go:31} ——
 *       {@code RpcClientConf.Timeout int64 `json:",default=2000"`} ⇒ <b>默认 2000ms</b>；</li>
 *   <li>Go 侧 {@code service/message/api/etc/messagecenter.yaml} 的 {@code MessageRpc:} 段（`:33`）
 *       <b>没有</b>显式 {@code Timeout} ⇒ ⇒ 走默认值；</li>
 *   <li><b>消费点</b>：{@code go-zero@v1.10.2/zrpc/internal/clientinterceptors/timeoutinterceptor.go:25}
 *       —— {@code ctx, cancel := context.WithTimeout(ctx, t)} ⇒ <b>取更早者</b>。</li>
 * </ul>
 * <p>⇒ RAG 侧 {@code message-api} 的出向 stub（{@code MessageRpcClient.java:98} 的
 * {@code @GrpcClient("message-rpc")}）<b>只做了「继承入向请求预算」</b>
 * （{@code GoStyleRequestDeadline.applyTo}）⇒ ⇒ <b>没有客户端硬上界</b>
 * ⇒ ⇒ 下游挂死时，出向调用<b>可以无限期挂着</b>（只要入向预算还没到）
 * ⇒ ⇒ 与 Go 的 2s <b>直接相反</b>。
 *
 * <h2>本轮做了什么（域侧只配参数 · 零代码改动）</h2>
 * <p>本域 {@code application.yml} 新增：
 * <pre>
 * rag:
 *   protection:
 *     outbound-deadline:
 *       cap-millis: ${MESSAGE_API_OUTBOUND_DEADLINE_CAP_MILLIS:2000}
 * </pre>
 * ⇒ ⇒ 封顶逻辑<b>唯一</b>由共享层 {@code GoStyleOutboundDeadlineInterceptor} 承担
 * （带 devh 的 {@code @GrpcGlobalClientInterceptor} ⇒ 装在<b>每个</b>出向 devh 调用上）
 * ⇒ ⇒ 本域<b>不写</b>任何私有 helper、<b>不写</b> {@code withDeadlineAfter}。
 *
 * <h2>本测试如何真正触发该机制（AGENTS.md §3.3）</h2>
 * <ol>
 *   <li><b>真拦截器</b>：用<b>生产类</b> {@code GoStyleOutboundDeadlineInterceptor} 装到<b>真 channel</b> 上
 *       （{@code ClientInterceptors.intercept(...)} —— 与 devh 的
 *       {@code AbstractChannelFactory.createChannel} 最后一行 {@code interceptForward} 同一条路）；</li>
 *   <li><b>真发调用</b>：起 <b>in-process 真实 gRPC server</b>，它的实现<b>挂起 8s 后才正常返回</b>
 *       ⇒ ⇒ 客户端必然在<b>自己的上界</b>处失败；而「若没有上界」它会在 8s 后<b>正常返回成功</b>
 *       ⇒ ⇒ 判据能干净地分辨「有上界」与「没上界」，<b>而不是</b>靠 {@code @Timeout} 把「挂死」包装成「测试超时」。</li>
 * </ol>
 *
 * <h2>⚠️ 诚实边界</h2>
 * <ul>
 *   <li>本类<b>不起</b>真实 message-rpc、<b>不连</b> Nacos ⇒ 它证的是「<b>本域配的上界真的被共享层拦截器
 *       读到了、且真的在出向调用上生效</b>」，<b>不</b>证「整条生产链路的端到端超时表现」；</li>
 *   <li>本类<b>不</b>带生产链路上的 {@code GoStyleGrpcClientBreakerInterceptor}；</li>
 *   <li>第 ⑤ 条（min 语义）用<b>裸 stub</b>（不经域 client 的 {@code applyTo}）⇒ ⇒ 那条验的是
 *       <b>共享层拦截器的 min 实现</b>，不是「域 client 的 applyTo 行为」。</li>
 * </ul>
 */
class MessageApiOutboundDeadlineCapGateTest {

    /** 本域的 env 覆盖旋钮（与本域 {@code application.yml} 里的占位符同名）。 */
    private static final String ENV_CAP = "MESSAGE_API_OUTBOUND_DEADLINE_CAP_MILLIS";

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
    private MessageServiceGrpc.MessageServiceBlockingStub cappedStub;

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
                        + "ms（实测 = " + raw + "）⇒ ⇒ 本域 Go 侧 messagecenter.yaml 的 MessageRpc: 段无显式 Timeout"
                        + "⇒ ⇒ 恒 2000ms。");
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
    @DisplayName("③ 【没有入向 deadline】⇒ 出向 message-rpc 必须在 ≤2000ms 处 DEADLINE_EXCEEDED（真发调用）")
    void outboundCallIsCappedWithoutInboundDeadline() throws Exception {
        GoStyleRequestDeadline.clear();
        startHangingMessageRpc(domainProperties(null));

        long startedAt = System.nanoTime();
        StatusRuntimeException thrown = assertThrows(StatusRuntimeException.class,
                () -> cappedStub.sendNotification(SendNotificationReq.newBuilder()
                        .addRecipientIds(1L)
                        .setSenderId(2L)
                        .build()),
                "下游不响应时，message-api 必须靠【客户端硬上界】收口，而不是一直挂着");
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
    @DisplayName("③ 域 client 层：messageRpc() 给出的 stub 也必须带同一上界（生产路径）")
    void domainClientExposesTheSameCap() throws Exception {
        GoStyleRequestDeadline.clear();
        MessageRpcClient client = startHangingMessageRpc(domainProperties(null));

        long startedAt = System.nanoTime();
        StatusRuntimeException thrown = assertThrows(StatusRuntimeException.class,
                () -> client.messageRpc().sendNotification(SendNotificationReq.newBuilder()
                        .addRecipientIds(1L)
                        .setSenderId(2L)
                        .build()),
                "🔴 域 client 暴露的 stub 必须同样带 2s 客户端硬上界"
                        + "⇒ ⇒ 否则业务调用点拿到的就是【没有上界】的 stub");
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;

        assertEquals(Status.Code.DEADLINE_EXCEEDED, thrown.getStatus().getCode(),
                "失败码必须是 DEADLINE_EXCEEDED（= 客户端硬上界到点）");
        assertTrue(elapsedMillis >= MIN_EXPECTED_WAIT_MILLIS,
                "必须是「等到 deadline 到期」而不是「立刻失败」，实际耗时 = " + elapsedMillis + "ms");
        assertTrue(elapsedMillis <= GO_DEFAULT_CAP_MILLIS + SLACK_MILLIS,
                "🔴 域 client 不能挂死：出向必须有 2s 客户端硬上界，实际耗时 = " + elapsedMillis + "ms"
                        + "⇒ ⇒ 若这里到了 8s 左右 ⇒ 说明【上界没生效】（那正是 P2-T04 要修的漂移）。");
    }

    // ────────────────────────────────────────────────────────────────────────────
    // ④ 运行期：env 覆盖【放宽】⇒ 上界【真的】跟着放宽
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @Timeout(value = 40, unit = TimeUnit.SECONDS)
    @DisplayName("④ env 把 cap 覆盖【到比 2000 更大】⇒ 出向上界必须跟着放宽 ⇒ 【证明域侧没有写死上界的私有副本】")
    void envOverrideWidensTheOutboundCap() throws Exception {
        GoStyleRequestDeadline.clear();
        // ⚠️ **必须走【域 client】（生产路径）**，不能走裸 stub ⇒ ⇒ 否则「域 client 里又写了一份
        //    写死 2000ms 的副本」这种漂移**根本不会被这条抓到**（裸 stub 不经过那份副本）
        //    ⇒ ⇒ 2026-10-31 变异实测：④ 改走裸 stub 时，M-D（域侧加回写死副本）**不红** ⇒ ⇒ 判据形态错 ⇒ 修正。
        //    （本域 client 的 messageRpc() = applyTo(stub)，而 applyTo 在【无入向预算】时原样返回 ⇒
        //     ⇒ 走域 client 后上界仍完全由 cap 决定 ⇒ 判据语义不变。）
        MessageRpcClient client = startHangingMessageRpc(domainProperties(
                Map.of(ENV_CAP, String.valueOf(OVERRIDE_CAP_MILLIS))));

        long startedAt = System.nanoTime();
        StatusRuntimeException thrown = assertThrows(StatusRuntimeException.class,
                () -> client.messageRpc().sendNotification(SendNotificationReq.newBuilder()
                        .addRecipientIds(1L)
                        .setSenderId(2L)
                        .build()),
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
    // ⑤ 运行期：min 语义 —— 入向预算比上界更早 ⇒ 必须取【更早者】
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @Timeout(value = 40, unit = TimeUnit.SECONDS)
    @DisplayName("⑤ 入向预算 300ms < cap 2000ms ⇒ 出向必须在【300ms】处失败（钉的是 min，不是覆盖）")
    void inboundBudgetWinsOverCap() throws Exception {
        GoStyleRequestDeadline.clear();
        GoStyleRequestDeadline.begin(System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(INBOUND_BUDGET_MILLIS));
        startHangingMessageRpc(domainProperties(null));

        long startedAt = System.nanoTime();
        StatusRuntimeException thrown = assertThrows(StatusRuntimeException.class,
                () -> cappedStub.sendNotification(SendNotificationReq.newBuilder()
                        .addRecipientIds(1L)
                        .setSenderId(2L)
                        .build()),
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
    //     (1) 声明面 —— 反射读**本域启动类**上的扫描声明（不复制、不手抄）；
    //     (2) 运行面 —— 真上下文 ＋ **本域磁盘那份 application.yml** ⇒ 拦截器成 Bean、cap 绑出 2000、
    //         且 devh 自己的收集器（生产里由 starter 的自动配置注册）真的把它收走。
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("⑦ 生产装配面：本域启动类的真实声明 + 本域磁盘 yml ⇒ 拦截器成 Bean、cap 绑出 2000、devh 真的收它")
    void productionWiringReallyProducesTheInterceptorWithTheDomainCap() {
        // ── (1) 声明面 ──
        Set<String> scanPackages = effectiveScanPackages(MessageApiApplication.class);
        assertTrue(scanPackages.contains("com.rag.common"),
                "🔴 本域启动类（MessageApiApplication）的**有效扫描范围**必须含 com.rag.common"
                        + "（实测 = " + scanPackages + "）"
                        + "⇒ ⇒ 否则本域进程扫不到 com.rag.common.protection.GoStyleProtectionConfiguration"
                        + "⇒ ⇒ 出向 deadline 拦截器在本域【不成 Bean】⇒ ⇒ 域 yml 的 cap 配了也不生效"
                        + "（「配置里没写 ≠ 没开」，AGENTS.md §3.3.1）。");

        // ── (2) 运行面 ──
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
            ctx.register(com.rag.common.protection.GoStyleProtectionConfiguration.class);
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
     * 起一个<b>挂起 {@link #HANG_MILLIS} 才响应</b>的 in-process {@code message-rpc} 下游，
     * 把<b>生产拦截器</b>装到 channel 上，并把该 stub 注入域 client 的 {@code @GrpcClient} 字段。
     */
    private MessageRpcClient startHangingMessageRpc(GoStyleProtectionProperties props) throws Exception {
        String serverName = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(serverName)
                .addService(new MessageServiceGrpc.MessageServiceImplBase() {
                    @Override
                    public void sendNotification(SendNotificationReq request,
                            StreamObserver<SendNotificationResp> responseObserver) {
                        // ⚠️ 挂起 HANG_MILLIS 才响应（既不立即响应，也**不是永不响应**）
                        // ⇒ 客户端在【客户端硬上界】处失败；
                        // ⇒ 而「若没有上界」它会在 HANG_MILLIS 后**正常返回**
                        // ⇒ ⇒ 判据能干净地分辨「有上界」与「没上界」。
                        try {
                            Thread.sleep(HANG_MILLIS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        responseObserver.onNext(SendNotificationResp.newBuilder().build());
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
        cappedStub = MessageServiceGrpc.newBlockingStub(capped);

        MessageRpcClient client = new MessageRpcClient();
        injectStub(client, "messageServiceBlockingStub", cappedStub);
        return client;
    }

    /** 把捕获型 stub 注入域 client 的 {@code @GrpcClient} 字段（生产是 devh 注入的）。 */
    private static void injectStub(MessageRpcClient client, String fieldName, Object stub) {
        try {
            Field f = MessageRpcClient.class.getDeclaredField(fieldName);
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
     * 读出某个启动类的<b>有效扫描范围</b>。
     *
     * <p>⚠️ <b>为什么不能简单 union 两个声明</b>：Spring 的规则是
     * 「<b>类上直接写的 {@code @ComponentScan} 会整体顶掉</b>
     * {@code @SpringBootApplication} 内建的那个」
     * ⇒ ⇒ 这里按「<b>有直接声明就用它，否则用 {@code @SpringBootApplication} 的 scanBasePackages</b>」取，
     * 与 Spring 的实际行为一致。
     */
    private static Set<String> effectiveScanPackages(Class<?> applicationClass) {
        Set<String> packages = new LinkedHashSet<>();

        ComponentScan direct = applicationClass.getDeclaredAnnotation(ComponentScan.class);
        if (direct != null) {
            packages.addAll(Arrays.asList(direct.basePackages()));
            return packages;
        }
        SpringBootApplication boot = applicationClass.getAnnotation(SpringBootApplication.class);
        if (boot != null) {
            packages.addAll(Arrays.asList(boot.scanBasePackages()));
        }
        return packages;
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
     * 定位本模块的源码根（含 {@code src/main/java}）—— <b>不依赖 surefire 的工作目录</b>。
     */
    private static Path moduleRoot() {
        try {
            Path loc = Path.of(MessageApiOutboundDeadlineCapGateTest.class.getProtectionDomain()
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
