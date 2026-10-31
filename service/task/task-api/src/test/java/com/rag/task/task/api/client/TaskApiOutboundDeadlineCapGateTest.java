package com.rag.task.task.api.client;

import com.rag.common.observability.GoStyleRequestDeadline;
import com.rag.common.protection.GoStyleOutboundDeadlineInterceptor;
import com.rag.common.protection.GoStyleProtectionProperties;
import com.rag.task.task.proto.GetTaskReq;
import com.rag.task.task.proto.GetTaskResp;
import com.rag.task.task.proto.TaskServiceGrpc;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code task-api} 出向 gRPC 调用（→ {@code task-rpc}）的<b>客户端硬上界（2s）</b>守门。
 *
 * <h2>它守的是什么</h2>
 * <p>Go 侧 {@code message-api → message-rpc} 这个出向调用<b>恒带 2s 客户端超时</b>：
 * <ul>
 *   <li>{@code go-zero@v1.10.2/zrpc/config.go:31} ——
 *       {@code RpcClientConf.Timeout int64 `json:",default=2000"`} ⇒ <b>默认 2000ms</b>；</li>
 *   <li>Go 侧 {@code service/task/api/etc/task.yaml} 的 {@code TaskRpc:} 段（`:5`）
 *       <b>没有</b>显式 {@code Timeout}（只有 {@code Target} / {@code NonBlock}）⇒ ⇒ 走默认值；</li>
 *   <li><b>消费点</b>：{@code go-zero@v1.10.2/zrpc/internal/clientinterceptors/timeoutinterceptor.go:25}
 *       —— {@code ctx, cancel := context.WithTimeout(ctx, t)} ⇒ <b>取更早者</b>。</li>
 * </ul>
 * <p>⇒ RAG 侧 {@code task-api} 的出向 stub（{@code TaskRpcClient.java:68} 的
 * {@code @GrpcClient("task-rpc")}）经由 {@code withDeadline(...)}（`:106`）套了
 * <b>域侧自带的一份上界</b>（{@code min(写死的 2000ms, 入向剩余预算)}，常量在 {@code :66}）
 * ⇒ ⇒ 那份<b>值与 Go 同为 2000ms</b>（不是零上界）⇒ ⇒ 但它是**第二个来源**
 * ⇒ ⇒ 下游挂死时，出向调用<b>可以无限期挂着</b>（只要入向预算还没到）
 * ⇒ ⇒ 与 Go 的 2s <b>直接相反</b>。
 *
 * <h2>本轮做了什么（域侧只配参数 · 零代码改动）</h2>
 * <p>本域 {@code application.yml} 新增：
 * <pre>
 * rag:
 *   protection:
 *     outbound-deadline:
 *       cap-millis: ${TASK_API_OUTBOUND_DEADLINE_CAP_MILLIS:2000}
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
 *   <li>本类<b>不起</b>真实 task-rpc、<b>不连</b> Nacos ⇒ 它证的是「<b>本域配的上界真的被共享层拦截器
 *       读到了、且真的在出向调用上生效</b>」，<b>不</b>证「整条生产链路的端到端超时表现」；</li>
 *   <li>本类<b>不</b>带生产链路上的 {@code GoStyleGrpcClientBreakerInterceptor}；</li>
 *   <li>第 ④ 条用<b>收紧</b>方向（本域有域侧既有副本 ⇒ 放宽方向结构性不可测）⇒ ⇒ 那条验的是
 *       「<b>域 yml 的这个键真被读到了</b>」，<b>不</b>证「域侧没有副本」；
 *       「没有副本」这一条由第 ⑥ 条的<b>点位级棘轮</b>承担；</li>
 *   <li>第 ⑤ 条（min 语义）用<b>裸 stub</b> ⇒ ⇒ 那条验的是<b>共享层拦截器的 min 实现</b>。</li>
 *   <li>⚠️ 本域<b>已有一份域侧上界</b>（{@code TaskRpcClient.java:106} + 常量 {@code :66}）⇒ ⇒ 本轮<b>不删</b>（协调员要求零代码改动）
 *       ⇒ ⇒ 「放宽」这一维在本域<b>尚未闭合</b>，已登记待收敛；</li>
 * </ul>
 */
class TaskApiOutboundDeadlineCapGateTest {

    /** 本域的 env 覆盖旋钮（与本域 {@code application.yml} 里的占位符同名）。 */
    private static final String ENV_CAP = "TASK_API_OUTBOUND_DEADLINE_CAP_MILLIS";

    /** Go 侧 {@code zrpc/config.go:31} 的默认值。 */
    private static final long GO_DEFAULT_CAP_MILLIS = 2000L;

    /** 下游挂起时长（毫秒）—— 必须<b>大于</b>第 ④ 条的覆盖值 ⇒ 才能分辨「有上界」与「没上界」。 */
    private static final long HANG_MILLIS = 8_000L;

    /** 真发调用那条用例的下界 —— 防止「秒失败」被误判成「超时生效」。 */
    private static final long MIN_EXPECTED_WAIT_MILLIS = 1_500L;

    /** 允许的额外耗时（毫秒）—— 排除「别的机制提前掐断」与墙钟抖动。 */
    private static final long SLACK_MILLIS = 1_500L;

    /** 第 ④ 条的 env 覆盖值 —— <b>故意取【小于】Go 默认 2000</b>（本域有域侧既有副本 ⇒ 放宽方向不可测）。 */
    private static final long TIGHTENED_CAP_MILLIS = 1_000L;

    /** 第 ⑤ 条：注入的入向预算（毫秒）—— 必须<b>远小于</b> 2s ⇒ 才能分辨「取更早者」与「取更晚者」。 */
    private static final long INBOUND_BUDGET_MILLIS = 300L;

    /** 第 ⑤ 条：出向<b>应当</b>在这个窗口内失败（钉的是 min，不是覆盖）。 */
    private static final long INBOUND_MIN_WAIT_MILLIS = 120L;
    private static final long INBOUND_MAX_WAIT_MILLIS = 1_200L;

    private Server server;
    private ManagedChannel channel;

    /** 装了生产拦截器的裸 stub —— 用于断「失败码 + 耗时窗口」这一层。 */
    private TaskServiceGrpc.TaskServiceBlockingStub cappedStub;

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
                        + "ms（实测 = " + raw + "）⇒ ⇒ 本域 Go 侧 task.yaml 的 TaskRpc: 段无显式 Timeout"
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
        startHangingDomainRpc(domainProperties(null));

        long startedAt = System.nanoTime();
        StatusRuntimeException thrown = assertThrows(StatusRuntimeException.class,
                () -> cappedStub.getTask(GetTaskReq.newBuilder().setUserId(1L).build()),
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
    @DisplayName("③ 域 client 层：getTask() 必须在 ≤2000ms 处失败（不挂死）")
    void domainClientDoesNotHang() throws Exception {
        GoStyleRequestDeadline.clear();
        TaskRpcClient client = startHangingDomainRpc(domainProperties(null));

        long startedAt = System.nanoTime();
        StatusRuntimeException thrown = assertThrows(StatusRuntimeException.class,
                () -> client.getTask(1L),
                "🔴 域 client 的出向调用必须按时失败"
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
    //
    // ⚠️ **本域为什么用「收紧」而不是 G1 那种「放宽」**：
    //   本域 TaskRpcClient.withDeadline(...)（:106）**自带**一份域侧上界
    //   （min(写死的 2000ms, 入向剩余预算)）⇒ ⇒ 那是**第二个来源**，
    //   它会把【想放宽】的上界压回 2000ms ⇒ ⇒ 放宽方向在本域**结构性不可测**
    //   （⇒ 这正是「副本数 = 漂移面」的形态，已在台账登记）。
    //   ⇒ ⇒ 本域改从**收紧方向**取证：cap 改成 1000ms ⇒ 出向必须跟着变成 ~1000ms
    //   ⇒ ⇒ 若域 yml 的这个键没被读到（cap 仍取默认 0）⇒ 出向会挂满 8s ⇒ 断言红。
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @Timeout(value = 40, unit = TimeUnit.SECONDS)
    @DisplayName("④ env 把 cap 覆盖【到比 2000 更小】⇒ 出向上界必须跟着收紧 ⇒ 【证明域 yml 的这个键真被读到】")
    void envOverrideTightensTheOutboundCap() throws Exception {
        GoStyleRequestDeadline.clear();
        startHangingDomainRpc(domainProperties(
                Map.of(ENV_CAP, String.valueOf(TIGHTENED_CAP_MILLIS))));

        long startedAt = System.nanoTime();
        StatusRuntimeException thrown = assertThrows(StatusRuntimeException.class,
                () -> cappedStub.getTask(GetTaskReq.newBuilder().setUserId(1L).build()),
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
        startHangingDomainRpc(domainProperties(null));

        long startedAt = System.nanoTime();
        StatusRuntimeException thrown = assertThrows(StatusRuntimeException.class,
                () -> cappedStub.getTask(GetTaskReq.newBuilder().setUserId(1L).build()),
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
    // ⑥ 形态面：**点位级**棘轮 —— 本域既有的那一份（TaskRpcClient）可以留，
    //    但**不得新增**第二处（豁免清单只能变紧，不能变松）
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("⑥ 本域 withDeadlineAfter 的代码行必须只落在【已登记的 TaskRpcClient】⇒ 不得新增第二份上界实现")
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
        registered.put("TaskRpcClient.java", 1);

        assertEquals(registered, hits,
                "🔴 本域 withDeadlineAfter 的【代码行】分布 = " + hits
                        + "，而已登记的基线 = " + registered
                        + "⇒ ⇒ 已登记的那一处（TaskRpcClient 自带的域侧上界）是**既有事实**，本轮不删"
                        + "（协调员要求零代码改动）"
                        + "⇒ ⇒ 但**不得新增第二处** ⇒ ⇒ 出向【客户端硬上界】的合法来源"
                        + " = 共享层的 GoStyleOutboundDeadlineInterceptor（域侧只配 cap-millis）"
                        + "⇒ ⇒ 副本数 = 漂移面 ⇒ ⇒ 这条判据是**点位级棘轮**：只能变紧，不能变松。");
    }

    // ================== 装置 ==================

    /**
     * 起一个<b>挂起 {@link #HANG_MILLIS} 才响应</b>的 in-process {@code message-rpc} 下游，
     * 把<b>生产拦截器</b>装到 channel 上，并把该 stub 注入域 client 的 {@code @GrpcClient} 字段。
     */
    private TaskRpcClient startHangingDomainRpc(GoStyleProtectionProperties props) throws Exception {
        String serverName = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(serverName)
                .addService(new TaskServiceGrpc.TaskServiceImplBase() {
                    @Override
                    public void getTask(GetTaskReq request,
                            StreamObserver<GetTaskResp> responseObserver) {
                        // ⚠️ 挂起 HANG_MILLIS 才响应（既不立即响应，也**不是永不响应**）
                        // ⇒ 客户端在【客户端硬上界】处失败；
                        // ⇒ 而「若没有上界」它会在 HANG_MILLIS 后**正常返回**
                        // ⇒ ⇒ 判据能干净地分辨「有上界」与「没上界」。
                        try {
                            Thread.sleep(HANG_MILLIS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        responseObserver.onNext(GetTaskResp.newBuilder().build());
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
        cappedStub = TaskServiceGrpc.newBlockingStub(capped);

        TaskRpcClient client = new TaskRpcClient();
        injectStub(client, "taskServiceBlockingStub", cappedStub);
        return client;
    }

    /** 把捕获型 stub 注入域 client 的 {@code @GrpcClient} 字段（生产是 devh 注入的）。 */
    private static void injectStub(TaskRpcClient client, String fieldName, Object stub) {
        try {
            Field f = TaskRpcClient.class.getDeclaredField(fieldName);
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
            Path loc = Path.of(TaskApiOutboundDeadlineCapGateTest.class.getProtectionDomain()
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
