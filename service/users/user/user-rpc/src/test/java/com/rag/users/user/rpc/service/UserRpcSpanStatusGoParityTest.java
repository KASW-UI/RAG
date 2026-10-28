package com.rag.users.user.rpc.service;

import com.rag.common.spring.LoggerFactoryBean;
import com.rag.users.user.proto.CreateUserReq;
import com.rag.users.user.proto.DeleteUserReq;
import com.rag.users.user.proto.LoginReq;
import com.rag.users.user.rpc.config.UserRpcProperties;
import com.rag.users.user.rpc.domain.UserEntity;
import com.rag.users.user.rpc.mapper.UserAvatarHistoryMapper;
import com.rag.users.user.rpc.mapper.UserMapper;
import com.rag.users.user.rpc.metrics.UserRpcMetrics;
import io.grpc.StatusRuntimeException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 🔴 Go ↔ RAG 对照（span **状态** 面）：Go 的 {@code span.RecordError(err)} **不**把 span 状态置成 ERROR
 * ⇒ RAG 侧也不得置 —— 否则导出到 trace 里的 span 状态与 Go 不一致（遥测面漂移）。
 *
 * <p><b>Go 出处（三段：机制 + 逐字源码 + 运行时实测）</b>
 * <ol>
 *   <li><b>机制</b>：otel-go SDK v1.44.0 {@code sdk@v1.44.0/trace/span.go:631-656}
 *       {@code recordingSpan.RecordError} 的**方法注释原文**：
 *       “SetStatus is required if the Status of the Span should be set to Error,
 *       <b>this method does not change the Span status</b>.”
 *       ⇒ {@code RecordError} 只 {@code addEvent("exception")}，**不碰 status**。</li>
 *   <li><b>逐字源码</b>：Go logic 层 {@code span.RecordError(err)} 出现处
 *       <ul>
 *         <li>{@code service/user/user/rpc/internal/logic/loginlogic.go:54}</li>
 *         <li>{@code service/user/user/rpc/internal/logic/registerlogic.go:53,60,69,85}</li>
 *         <li>{@code service/user/user/rpc/internal/logic/deleteuserlogic.go:48,56}</li>
 *         <li>（admin 侧另有 16 处，见 {@code service/user/admin/rpc/internal/logic/}）</li>
 *       </ul>
 *       ⇒ 这些位置**全部没有** {@code span.SetStatus(...)} ⇒ Go 的 span 状态恒为 <b>UNSET</b>。
 *       ⚠️ 只有共享层 {@code service/common/observability/observability.go:103-105}
 *       才<b>显式</b>调 {@code span.SetStatus(otelcodes.Error, msg)} —— 那是另一条路径。</li>
 *   <li><b>运行时实测</b>（Go 腿，otel-go v1.44.0 真跑，见台账
 *       {@code docs/DRIFT/users/vs-common.md} §59）：形态 A（只 {@code RecordError}）⇒
 *       导出 span 的 {@code status.Code = Unset} ＋ 1 条 {@code exception} 事件；
 *       形态 B（{@code observability.go:103-105} 三句）⇒ {@code status.Code = Error}。</li>
 * </ol>
 *
 * <p><b>为什么本测试是「运行期证据」而不是「代码里写了」（AGENTS.md §3.3）</b>
 * <ul>
 *   <li>本测试<b>自己装一个 OTel SDK</b> ＋ 收集型 {@link SpanExporter}，把
 *       {@link GlobalOpenTelemetry} 指过去 ⇒ 不装 SDK 时 span 是 no-op、什么也断言不到；</li>
 *   <li>断言对象是<b>导出的 span 数据</b>（{@link SpanData}）⇒ 走的是真实的
 *       {@code SdkSpan} 实现，不是 mock；</li>
 *   <li>每条用例都<b>先断言 span 真的被导出、且带 exception 事件</b>（证明机制活着），
 *       <b>再</b>断言状态是 UNSET ⇒ 「没产生 span」不会被误读成「状态正确」。</li>
 * </ul>
 *
 * <p><b>变异实证</b>：把任意一处 {@code span.setStatus(StatusCode.ERROR, …)} 加回去
 * ⇒ 对应用例必须<b>红在状态断言上</b>（红在「期望 UNSET 实得 ERROR」）⇒ 证明本测试不是假绿。
 */
class UserRpcSpanStatusGoParityTest {

    /**
     * 自建收集型 exporter —— <b>不引 {@code opentelemetry-sdk-testing}</b>
     * （该包不在 user-rpc 的依赖里，引入它只为测试反而扩大依赖面）。
     */
    static final class CollectingExporter implements SpanExporter {
        final List<SpanData> spans = Collections.synchronizedList(new ArrayList<>());

        @Override
        public CompletableResultCode export(Collection<SpanData> batch) {
            spans.addAll(batch);
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode flush() {
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode shutdown() {
            return CompletableResultCode.ofSuccess();
        }

        void clear() {
            spans.clear();
        }
    }

    private static final CollectingExporter EXPORTER = new CollectingExporter();

    private UserMapper userMapper;
    private UserAvatarHistoryMapper avatarHistoryMapper;
    private UserRpcMetrics metrics;
    private StringRedisTemplate bizRedis;
    private LoggerFactoryBean logger;
    private UserRpcProperties properties;
    private UserDomainService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        // 🔴 关键①：把 GlobalOpenTelemetry 指向本测试的收集器，否则 span 是 no-op。
        //            必须在**构造 UserDomainService 之前**装 —— 它的构造函数里就取了 tracer。
        GlobalOpenTelemetry.resetForTest();
        GlobalOpenTelemetry.set(OpenTelemetrySdk.builder()
                .setTracerProvider(SdkTracerProvider.builder()
                        .addSpanProcessor(SimpleSpanProcessor.create(EXPORTER))
                        .build())
                .build());

        userMapper = mock(UserMapper.class);
        avatarHistoryMapper = mock(UserAvatarHistoryMapper.class);
        metrics = new UserRpcMetrics(new SimpleMeterRegistry());
        bizRedis = mock(StringRedisTemplate.class);
        logger = mock(LoggerFactoryBean.class);
        properties = new UserRpcProperties();
        properties.getUserAuth().setAccessSecret("test-secret");
        properties.getUserAuth().setAccessExpire(2592000L);
        service = new UserDomainService(userMapper, avatarHistoryMapper, metrics, bizRedis,
                logger, properties, new AvatarDomainService());
        EXPORTER.clear();
    }

    @AfterEach
    void tearDown() {
        GlobalOpenTelemetry.resetForTest();
    }

    // ==================================================================
    // ① 登录：Go loginlogic.go:54（DB 查询失败分支）
    // ==================================================================

    @Test
    @DisplayName("① login 的 DB 失败分支：导出 span 的 status 必须与 Go 同为 Unset（且 exception 事件在）")
    void loginDbError_exportedSpanStatusIsUnsetLikeGo() {
        // Go: loginlogic.go:51-55 —— 非 NotFound 的 DB 错误 ⇒ span.RecordError(err) + 返回 Internal
        doThrow(new RuntimeException("db down"))
                .when(userMapper).findOneByUserName("alice");

        assertThatThrownBy(() -> service.login(LoginReq.newBuilder()
                .setUsername("alice").setPassword("password").build()))
                .isInstanceOf(StatusRuntimeException.class);

        assertSpanStatusUnsetAndHasException("Action-User-Login");
    }

    // ==================================================================
    // ② 注册：Go registerlogic.go:53（DB 查询失败分支）
    // ==================================================================

    @Test
    @DisplayName("② register 的 DB 失败分支：导出 span 的 status 必须与 Go 同为 Unset（且 exception 事件在）")
    void registerDbError_exportedSpanStatusIsUnsetLikeGo() {
        // Go: registerlogic.go:51-55 —— 「非 ErrorNotFound」单独成一支 ⇒ span.RecordError(err)
        doThrow(new RuntimeException("db down"))
                .when(userMapper).findOneByUserName("bob");

        assertThatThrownBy(() -> service.register(CreateUserReq.newBuilder()
                .setUsername("bob").setPassword("password").build()))
                .isInstanceOf(StatusRuntimeException.class);

        assertSpanStatusUnsetAndHasException("Action-User-Register");
    }

    // ==================================================================
    // ③ 删除：Go deleteuserlogic.go:48（DB 查询失败分支）
    // ==================================================================

    @Test
    @DisplayName("③ delete 的 DB 失败分支：导出 span 的 status 必须与 Go 同为 Unset（且 exception 事件在）")
    void deleteDbError_exportedSpanStatusIsUnsetLikeGo() {
        // Go: deleteuserlogic.go:47-52 —— 非 NotFound 的 DB 错误 ⇒ span.RecordError(err)
        doThrow(new RuntimeException("db down"))
                .when(userMapper).findOneByUid(anyLong());

        assertThatThrownBy(() -> service.deleteUser(DeleteUserReq.newBuilder().setUid(7701L).build()))
                .isInstanceOf(StatusRuntimeException.class);

        assertSpanStatusUnsetAndHasException("Action-User-Delete");
    }

    // ==================================================================
    // ④ 源扫守门：域内主源码**不得**把 OTel span 状态置成 ERROR
    // ==================================================================

    @Test
    @DisplayName("④ 域内主源码零处 setStatus(StatusCode.*) ⇒ Go 的 RecordError 不置状态，域内也不许置")
    void domainMainSourcesMustNotSetOtelSpanStatus() throws IOException {
        ScanResult result = scanForOtelStatusWrites(repoRoot());

        // 🔴 防空转：扫描器必须**真的扫到文件**。否则「零命中」是假信号
        //    （本守门第一版就踩过：路径写成 service/users/user/src/main/java，
        //     而真实模块在 service/users/user/user-rpc/src/main/java ⇒ 扫了 0 个文件却报绿）。
        assertThat(result.visitedFiles)
                .as("扫描器必须真的读到域内主源码文件（否则「零命中」是假信号）")
                .isGreaterThan(20);
        assertThat(result.scannedRoots)
                .as("必须扫到 user-rpc / admin-rpc / follow-* 这几个真实模块的主源码目录")
                .isNotEmpty();

        assertThat(result.hits)
                .as("域内主源码出现了 OTel span 状态写入 ⇒ ⇒ Go 的 span.RecordError **不**置状态"
                        + "（otel-go SDK v1.44.0 sdk@v1.44.0/trace/span.go:631-656 的方法注释原文："
                        + "“this method does not change the Span status”）⇒ ⇒ Go 的 span 状态恒为 Unset"
                        + " ⇒ ⇒ RAG 侧多置 ERROR ⇒ 导出到 trace 的 span 状态与 Go 不一致（遥测面漂移）"
                        + " ⇒ ⇒ 处置：删掉这处 setStatus（记录 exception 事件本身是对的，保留）")
                .isEmpty();
    }

    // ==================================================================
    // ⑤ 判据自检：扫描器不是空转（造一份假文件 ⇒ 必须报出来）
    // ==================================================================

    @Test
    @DisplayName("⑤ 判据自检：临时目录里造一份含 setStatus(StatusCode. 的假源码 ⇒ 扫描器必须报出它")
    void scannerActuallyReportsAStatusWrite() throws IOException {
        Path fakeRoot = Files.createTempDirectory("g1-span-status-selftest");
        Path javaDir = fakeRoot.resolve("service/users/user/user-rpc/src/main/java/com/rag/fake");
        Files.createDirectories(javaDir);
        Files.writeString(javaDir.resolve("Fake.java"),
                "package com.rag.fake;\n"
                        + "class Fake {\n"
                        + "    void f(io.opentelemetry.api.trace.Span span) {\n"
                        + "        span.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR, \"x\");\n"
                        + "    }\n"
                        + "}\n",
                StandardCharsets.UTF_8);

        ScanResult result = scanForOtelStatusWrites(fakeRoot);
        assertThat(result.visitedFiles).isEqualTo(1);
        assertThat(result.hits)
                .as("扫描器必须报出假源码里的那一处（否则本守门是空转 ⇒ 零命中是假信号）")
                .hasSize(1);
        assertThat(result.hits.get(0)).contains("Fake.java");

        // ⚠️ 反例自检：把同一处写进**注释**里 ⇒ 必须**不**报（否则 javadoc 引用会假阳性）
        Path commentOnly = javaDir.resolve("CommentOnly.java");
        Files.writeString(commentOnly,
                "package com.rag.fake;\n"
                        + "/** 说明：Go 那边是 span.setStatus(StatusCode.ERROR, msg) —— 见 observability.go:103。 */\n"
                        + "// span.setStatus(StatusCode.ERROR, \"注释里的\")\n"
                        + "class CommentOnly { }\n",
                StandardCharsets.UTF_8);
        ScanResult result2 = scanForOtelStatusWrites(fakeRoot);
        assertThat(result2.hits)
                .as("注释里的字面量必须被剥掉（否则本守门会对着 javadoc 假阳性）")
                .hasSize(1);
        assertThat(result2.hits.get(0)).doesNotContain("CommentOnly.java");
    }

    // ==================================================================
    // 内部工具
    // ==================================================================

    /** 一次扫描的结果 —— <b>带「扫了多少个文件」</b>，好让「零命中」可判真假。 */
    static final class ScanResult {
        final List<String> hits = new ArrayList<>();
        final List<String> scannedRoots = new ArrayList<>();
        int visitedFiles;
    }

    /**
     * OTel span 状态写入的模式 —— 短式与全式都吃。
     *
     * <p>⚠️ 不能只写 {@code setStatus(StatusCode.}：全限定写法
     * {@code setStatus(io.opentelemetry.api.trace.StatusCode.ERROR} 就漏了。
     */
    private static final Pattern STATUS_WRITE =
            Pattern.compile("setStatus\\([^)]*StatusCode\\.");

    /**
     * 扫「域内主源码」里有没有 OTel span 状态写入。
     *
     * <p>⚠️ <b>三个容易踩空的地方，本实现都处理了</b>：
     * <ol>
     *   <li><b>路径</b>：{@code service/users/user} 是<b>聚合目录</b>，真实模块在
     *       {@code service/users/user/user-rpc/src/main/java} ⇒ 所以这里<b>递归</b>找
     *       {@code /src/main/java/} 里的 .java，而不是拼一个固定路径（第一版拼死路径 ⇒ 扫了 0 个文件 ⇒ 假绿）。</li>
     *   <li><b>注释</b>：先剥注释再扫 ⇒ javadoc 里引用 {@code setStatus(StatusCode.ERROR}
     *       不会假阳性。</li>
     *   <li><b>共享层</b>：{@code service/users/users-common/**} 是<b>共享层</b>（写权归 G4）⇒ 不纳入本域守门。</li>
     * </ol>
     */
    private static ScanResult scanForOtelStatusWrites(Path root) throws IOException {
        ScanResult result = new ScanResult();
        for (String dir : new String[]{"service/users/user", "service/users/admin", "service/follow"}) {
            Path base = root.resolve(dir);
            if (!Files.isDirectory(base)) {
                continue;
            }
            try (Stream<Path> stream = Files.walk(base)) {
                for (Path p : (Iterable<Path>) stream.filter(Files::isRegularFile)::iterator) {
                    String rel = root.relativize(p).toString();
                    if (!rel.endsWith(".java") || !rel.contains("/src/main/java/")) {
                        continue;
                    }
                    if (rel.startsWith("service/users/users-common/")) {
                        continue; // 共享层 ⇒ 写权归 G4 ⇒ 不由本域守门管
                    }
                    result.visitedFiles++;
                    String module = rel.substring(0, rel.indexOf("/src/main/java/"));
                    if (!result.scannedRoots.contains(module)) {
                        result.scannedRoots.add(module);
                    }
                    String code = stripComments(Files.readString(p, StandardCharsets.UTF_8));
                    // ⚠️ 模式必须同时吃「短式」`setStatus(StatusCode.ERROR` 与「全式」
                    //    `setStatus(io.opentelemetry.api.trace.StatusCode.ERROR`
                    //    —— 第一版只写短式，被判据自检（⑤）当场抓出是**漏报**。
                    Matcher m = STATUS_WRITE.matcher(code);
                    while (m.find()) {
                        int line = (int) code.substring(0, m.start()).chars().filter(c -> c == '\n').count() + 1;
                        result.hits.add(rel + ":" + line
                                + "  →  " + code.substring(m.start(), Math.min(code.length(), m.end() + 20)).trim());
                    }
                }
            }
        }
        return result;
    }

    /**
     * 断言「导出的那个 span」：状态 = UNSET ＋ 至少一条 {@code exception} 事件。
     *
     * <p>⚠️ 顺序有意义：<b>先</b>断言 span 真的存在且带 exception 事件（证明 recordException 真的发生了），
     * <b>再</b>断言状态是 UNSET ⇒ ⇒ 「压根没产生 span」不可能被误判成「状态对了」。
     */
    private void assertSpanStatusUnsetAndHasException(String spanName) {
        List<SpanData> exported = EXPORTER.spans.stream()
                .filter(s -> spanName.equals(s.getName()))
                .toList();
        assertThat(exported)
                .as("span %s 必须真的被导出（否则下面的状态断言是假信号）", spanName)
                .hasSize(1);
        SpanData span = exported.get(0);

        assertThat(span.getEvents())
                .as("span %s 必须带一条 exception 事件（Go 的 RecordError 只加事件、不改状态）", spanName)
                .isNotEmpty()
                .anyMatch(e -> "exception".equals(e.getName()));

        assertThat(span.getStatus().getStatusCode())
                .as("span %s 的 status 必须与 Go 同为 UNSET"
                        + "（Go 侧 otel-go v1.44.0 实测：只调 span.RecordError ⇒ status.Code=Unset）", spanName)
                .isEqualTo(StatusCode.UNSET);
    }

    /** 去掉 {@code /* … *}{@code /} 与 {@code // …} 两类注释，避免 javadoc 里的字面量造成假阳性。 */
    private static String stripComments(String src) {
        StringBuilder out = new StringBuilder(src.length());
        int i = 0;
        int n = src.length();
        while (i < n) {
            char c = src.charAt(i);
            if (c == '/' && i + 1 < n && src.charAt(i + 1) == '/') {
                while (i < n && src.charAt(i) != '\n') {
                    i++;
                }
            } else if (c == '/' && i + 1 < n && src.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < n && !(src.charAt(i) == '*' && src.charAt(i + 1) == '/')) {
                    if (src.charAt(i) == '\n') {
                        out.append('\n');
                    }
                    i++;
                }
                i += 2;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    /** 仓库根：从本类的 class 文件位置向上找同时含 {@code service/} 与 {@code pom.xml} 的目录。 */
    private static Path repoRoot() {
        Path here = Path.of(UserRpcSpanStatusGoParityTest.class.getProtectionDomain()
                .getCodeSource().getLocation().getPath());
        for (Path p = here; p != null; p = p.getParent()) {
            if (Files.isDirectory(p.resolve("service")) && Files.isRegularFile(p.resolve("pom.xml"))) {
                return p;
            }
        }
        throw new IllegalStateException("找不到仓库根（从 " + here + " 向上没有同时含 service/ 与 pom.xml 的目录）");
    }
}
