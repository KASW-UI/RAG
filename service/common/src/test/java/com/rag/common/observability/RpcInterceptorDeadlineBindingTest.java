package com.rag.common.observability;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.ServerCalls;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <h2>本类钉死什么</h2>
 *
 * <p><b>一条 javadoc 断言的运行时证据</b>：
 * {@code service/users/user/user-rpc} 的 {@code UserDomainService#checkDeadline} 的 javadoc
 * 原文声称「配合 {@code DeadlineState#markExpired(long)}（由 {@code RpcInterceptor} 调度）」——
 * <b>那是假断言</b>（{@code markExpired} 无任何生产调用方，且设在超时调度线程上对 handler 线程不可见）。
 * 2026-10-25 由 {@code align/global} 收口订正为：
 * <b>deadline 由 {@code onHalfClose} 在真正跑 handler 的线程上经 {@code bindToCurrent} 绑定。</b>
 *
 * <p>⇒ <b>订正后的断言必须为真，否则等于把一句假话换成另一句假话</b>（AGENTS.md §3.2 的精神）。
 * 本类就是那句话的<b>运行时证据</b>：真 in-process gRPC 管线 + 真拦截器 + 真 handler 线程。
 *
 * <h2>为什么不能用 mock 单测</h2>
 *
 * <p>AGENTS.md §3.3.3：「纯 Mockito mock 数据层的单测不算 —— 它不会执行 SQL，也不会装配 Spring 上下文」。
 * 本类的断言对象是<b>框架机制</b>（gRPC 的 {@code onHalfClose} 线程模型 + ThreadLocal 作用域），
 * 只有<b>真管线</b>才能证明。
 *
 * <h2>Go 出处（真源 = Sea-RideTheWind_golang + go-zero v1.10.2）</h2>
 *
 * <p>Go 版 gRPC 超时：{@code zrpc/internal/serverinterceptors/timeoutinterceptor.go}：
 * <pre>
 * ctx, cancel := context.WithTimeout(ctx, t)
 * ...
 * select {
 * case &lt;-done:      return resp, err
 * case &lt;-ctx.Done(): err := ctx.Err(); ...   // DeadlineExceeded
 * }
 * </pre>
 * ⇒ Go 侧 deadline <b>随 ctx 传递</b>，handler goroutine 里 {@code ctx.Done()} 直接可读。
 * ⇒ Java 侧<b>无 ctx 传播</b>（grpc-java 限制）⇒ 用 {@code ThreadLocal} + {@code bindToCurrent} 表达，
 * <b>但必须绑在真正跑 handler 的线程上</b>（M7 修复的由来）⇒ 本类守的就是这一条。
 *
 * <p>⚠️ 本类<b>不</b>断言「RAG 与 Go 的 deadline 语义完全等价」—— 那是 DRIFT-018 已登记的 Java 化差异。
 * 本类只断言：<b>Javadoc 里写的那句机制描述，在运行时确实成立。</b>
 */
@DisplayName("§3.2/§3.3：deadline 的 javadoc 断言必须有运行时证据（markExpired 收口闭环）")
class RpcInterceptorDeadlineBindingTest {

    /** handler 线程名（证明绑定发生在 handler 线程，而非测试线程）。 */
    private static final AtomicReference<String> HANDLER_THREAD = new AtomicReference<>();
    /** handler 入口处的剩余毫秒（证明 deadline <b>已绑定</b>：不是 Long.MAX_VALUE）。 */
    private static final AtomicLong REMAINING_AT_ENTRY = new AtomicLong(-1L);
    /** handler 入口处是否已过期（应当是 false —— 刚进来还没到点）。 */
    private static final AtomicBoolean EXPIRED_AT_ENTRY = new AtomicBoolean(true);
    /** handler 睡过 deadline 之后是否已过期（<b>核心断言</b>：应当是 true）。 */
    private static final AtomicBoolean EXPIRED_AFTER_SLEEP = new AtomicBoolean(false);
    /** handler 睡过 deadline 之后的剩余毫秒（应当是 0）。 */
    private static final AtomicLong REMAINING_AFTER_SLEEP = new AtomicLong(-1L);
    /** handler 结束信号。 */
    private static final CountDownLatch HANDLER_DONE = new CountDownLatch(1);

    /** 拦截器超时：200ms；handler 睡 600ms ⇒ 必然跨过 deadline。 */
    private static final Duration INTERCEPTOR_TIMEOUT = Duration.ofMillis(200L);
    /** handler 睡多久（必须 > INTERCEPTOR_TIMEOUT，否则跨不过 deadline）。 */
    private static final long HANDLER_SLEEP_MS = 600L;
    /** 本守门文件自身的文件名（扫描时排除，见 {@link #allServiceJavaFiles()}）。 */
    private static final String SELF_FILE_NAME = "RpcInterceptorDeadlineBindingTest.java";

    private static final MethodDescriptor<byte[], byte[]> HANG =
            MethodDescriptor.<byte[], byte[]>newBuilder()
                    .setType(MethodDescriptor.MethodType.UNARY)
                    .setFullMethodName("test.DeadlineBindingSvc/Hang")
                    .setRequestMarshaller(new BytesMarshaller())
                    .setResponseMarshaller(new BytesMarshaller())
                    .build();

    private static Server server;
    private static Channel channel;

    @BeforeAll
    static void startServer() throws Exception {
        String name = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(name)
                // ⚠️ 这里刻意<b>不</b>用 directExecutor()：要让 handler 跑在 gRPC 自己的
                //    executor 线程上，否则 handler 与测试线程同线程，
                //    「绑定发生在 handler 线程」这条断言就失去鉴别力。
                .addService(ServerServiceDefinition.builder("test.DeadlineBindingSvc")
                        .addMethod(HANG, ServerCalls.asyncUnaryCall((req, obs) -> {
                            // ── 以下三行跑在<b>handler 线程</b>上 ──
                            HANDLER_THREAD.set(Thread.currentThread().getName());
                            REMAINING_AT_ENTRY.set(RpcInterceptor.DeadlineState.remainingMillis());
                            EXPIRED_AT_ENTRY.set(RpcInterceptor.DeadlineState.isExpired());
                            try {
                                Thread.sleep(HANDLER_SLEEP_MS);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                            EXPIRED_AFTER_SLEEP.set(RpcInterceptor.DeadlineState.isExpired());
                            REMAINING_AFTER_SLEEP.set(RpcInterceptor.DeadlineState.remainingMillis());
                            HANDLER_DONE.countDown();
                            try {
                                obs.onError(new StatusRuntimeException(
                                        Status.CANCELLED.withDescription("server: handler finished")));
                            } catch (RuntimeException ignored) {
                                // 超时可能已经把 call 关了 ⇒ 这里忽略是预期路径
                            }
                        }))
                        .build())
                // ★ 装上真正的拦截器 —— 这才是被测对象
                .intercept(RpcInterceptor.newUnaryServerInterceptor(
                        INTERCEPTOR_TIMEOUT, Duration.ofSeconds(5L)))
                .build()
                .start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
    }

    @AfterAll
    static void stopServer() throws Exception {
        if (channel != null) {
            ((ManagedChannel) channel).shutdownNow();
        }
        if (server != null) {
            server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    // ==================== ① 机制断言：订正后的 javadoc 必须为真 ====================

    @Test
    @DisplayName("① onHalfClose 把 deadline 绑在<b>handler 线程</b>上 ⇒ javadoc 的新表述为真")
    void deadlineIsBoundOnHandlerThread() throws Exception {
        // 客户端这一侧会拿到 DEADLINE_EXCEEDED（超时调度器关掉了 call）
        StatusRuntimeException thrown = assertThrows(StatusRuntimeException.class, () ->
                ClientCalls.blockingUnaryCall(channel, HANG, CallOptions.DEFAULT,
                        "call-1".getBytes(StandardCharsets.UTF_8)));
        assertEquals(Status.Code.DEADLINE_EXCEEDED, thrown.getStatus().getCode(),
                "客户端应见 DEADLINE_EXCEEDED（与 Go 的 ctx.Done() → DeadlineExceeded 同形）");

        // 等 handler 自己跑完（它不会被中断 —— grpc-java 不主动中断 handler）
        assertTrue(HANDLER_DONE.await(5, TimeUnit.SECONDS), "handler 未在 5s 内结束");

        // ── 断言 A：绑定发生在 <b>handler 线程</b>，不是测试线程 ──
        assertNotNull(HANDLER_THREAD.get(), "handler 未记录线程名");
        assertNotEquals(Thread.currentThread().getName(), HANDLER_THREAD.get(),
                "handler 与测试线程同名 ⇒ 断言失去鉴别力（in-process + directExecutor 的坑）");

        // ── 断言 B：handler 入口处 deadline <b>已绑定</b>（不是「未设置」）──
        //   未绑定时 remainingMillis() == Long.MAX_VALUE
        assertNotEquals(Long.MAX_VALUE, REMAINING_AT_ENTRY.get(),
                "handler 入口 remainingMillis() == Long.MAX_VALUE ⇒ deadline <b>没有</b>绑到 handler 线程");
        assertTrue(REMAINING_AT_ENTRY.get() > 0L,
                "刚进 handler 就该还有剩余时间，实际=" + REMAINING_AT_ENTRY.get());
        assertTrue(REMAINING_AT_ENTRY.get() <= INTERCEPTOR_TIMEOUT.toMillis(),
                "剩余时间不应超过拦截器超时，实际=" + REMAINING_AT_ENTRY.get());
        assertFalse(EXPIRED_AT_ENTRY.get(), "刚进 handler 不应已过期");

        // ── 断言 C（核心）：睡过 deadline 后，handler 线程能读到已过期 ──
        assertTrue(EXPIRED_AFTER_SLEEP.get(),
                "handler 睡过 deadline 后 isExpired() 仍为 false ⇒ onHalfClose 的绑定未生效");
        assertEquals(0L, REMAINING_AFTER_SLEEP.get(),
                "过期后 remainingMillis() 应为 0，实际=" + REMAINING_AFTER_SLEEP.get());
    }

    // ==================== ②③ 守恒断言：markExpired 已彻底消失 ====================

    @Test
    @DisplayName("② 守恒：DeadlineState 上已无 markExpired 声明（删除确实生效）")
    void markExpiredDeclarationIsGone() {
        for (Method m : RpcInterceptor.DeadlineState.class.getDeclaredMethods()) {
            assertNotEquals("markExpired", m.getName(),
                    "DeadlineState 仍声明 markExpired —— 收口删除未生效（且它无生产调用方）");
        }
    }

    @Test
    @DisplayName("③ 守恒：全仓 service/** 的 Java <b>代码</b>里零 markExpired 引用（注释与字符串已剔除）")
    void noCodeReferencesMarkExpired() throws Exception {
        List<String> offenders = new ArrayList<>();
        for (Path java : allServiceJavaFiles()) {
            String src = Files.readString(java, StandardCharsets.UTF_8);
            String code = stripCommentsAndStrings(src);
            if (code.contains("markExpired")) {
                offenders.add(relativize(java));
            }
        }
        assertTrue(offenders.isEmpty(),
                "以下文件在<b>代码</b>里仍引用 markExpired（注释/字符串已剔除）：" + offenders);
    }

    @Test
    @DisplayName("④ 守恒：没有 {@link ...markExpired} 的 javadoc 链接（避免悬空链接被当成证据）")
    void noJavadocLinkToMarkExpired() throws Exception {
        Pattern linkToMarkExpired = Pattern.compile("\\{@link[^}]*markExpired");
        List<String> offenders = new ArrayList<>();
        for (Path java : allServiceJavaFiles()) {
            String src = Files.readString(java, StandardCharsets.UTF_8);
            Matcher m = linkToMarkExpired.matcher(src);
            if (m.find()) {
                offenders.add(relativize(java));
            }
        }
        assertTrue(offenders.isEmpty(),
                "以下文件仍用 {@link} 指向已删除的 markExpired（链接会悬空）：" + offenders);
    }

    // ==================== 辅助 ====================

    /** 仓库根：向上找同时含 {@code service/} 与 {@code docs/} 的目录。 */
    private static Path repoRoot() {
        Path p = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        while (p != null) {
            if (Files.isDirectory(p.resolve("service")) && Files.isDirectory(p.resolve("docs"))) {
                return p;
            }
            p = p.getParent();
        }
        throw new IllegalStateException(
                "定位仓库根失败：user.dir=" + System.getProperty("user.dir"));
    }

    private static String relativize(Path p) {
        return repoRoot().relativize(p).toString();
    }

    private static List<Path> allServiceJavaFiles() throws IOException {
        try (Stream<Path> walk = Files.walk(repoRoot().resolve("service"))) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    // ⚠️ 排除守门文件自身：本类的源码<b>必然</b>含 "markExpired" 这个字面量
                    //   （断言与 Pattern 都用到它）⇒ 不排除就是自指的假红。
                    //   ⚠️ 只排除<b>这一个文件</b>：其它测试文件仍受扫描约束
                    //   （否则「换个文件再引入一次」就能绕过守门）。
                    .filter(p -> !p.getFileName().toString().equals(SELF_FILE_NAME))
                    .sorted()
                    .toList();
        }
    }

    /**
     * 剔除注释与字符串字面量，只留「可执行代码」。
     *
     * <p>为什么必须剔除：本仓的台账纪律要求把历史写成注释（如「原 markExpired 已删除」），
     * 若不剔除，{@code ③} 会把这些<b>说明性文字</b>误判成「代码引用」⇒ 假红。
     */
    static String stripCommentsAndStrings(String src) {
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
                    i++;
                }
                i = Math.min(i + 2, n);
            } else if (c == '"' && i + 2 < n && src.charAt(i + 1) == '"' && src.charAt(i + 2) == '"') {
                // text block（""" ... """）
                i += 3;
                while (i + 2 < n && !(src.charAt(i) == '"' && src.charAt(i + 1) == '"'
                        && src.charAt(i + 2) == '"')) {
                    i++;
                }
                i = Math.min(i + 3, n);
                out.append("\"\"\"");
            } else if (c == '"') {
                i++;
                while (i < n) {
                    char d = src.charAt(i);
                    if (d == '\\') {
                        i += 2;
                        continue;
                    }
                    if (d == '"') {
                        i++;
                        break;
                    }
                    i++;
                }
                out.append('"');
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    private static final class BytesMarshaller implements MethodDescriptor.Marshaller<byte[]> {
        @Override
        public InputStream stream(byte[] value) {
            return new ByteArrayInputStream(value);
        }

        @Override
        public byte[] parse(InputStream stream) {
            try {
                return stream.readAllBytes();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
