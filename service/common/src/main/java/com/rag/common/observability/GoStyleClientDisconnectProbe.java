package com.rag.common.observability;

import jakarta.servlet.ServletRequestWrapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

/**
 * <b>客户端断连的「主动探测」：往 socket 写一个合法的 HTTP/1.1 1xx 探针，把断连提前暴露出来。</b>
 *
 * <h3>Go 侧的判据（真源，consumption point）</h3>
 * <pre>
 *   Sea-RideTheWind_golang/service/common/observability/http.go:80-88
 *       case &lt;-ctx.Done():
 *           statusCode := http.StatusServiceUnavailable
 *           if errors.Is(ctx.Err(), context.Canceled) { statusCode = httpClientClosedRequest }  // 499
 *           MarkTimeout(ctx, ctx.Err(), duration, httpAttrs(serviceName, req, statusCode)...)
 *           http.Error(w, httpTimeoutBody, statusCode)
 * </pre>
 * <p>Go 的 499 由 <b>ctx 被取消</b>驱动；而 Go 的 {@code net/http} 在<b>客户端断开</b>时会取消
 * {@code r.Context()} ⇒ <b>断连 ⇒ 立刻 499</b>，且 ctx 取消会<b>同步传播</b>到所有 ctx-aware 的
 * 下游调用（{@code grpc.Dial} 的每一次调用都带 ctx）⇒ <b>在飞的下游 gRPC 立刻中止</b>。
 *
 * <h3>RAG 为什么需要「主动探测」（这正是本类存在的理由）</h3>
 * <p>RAG 跑在 Servlet 容器（Tomcat）上：<b>HTTP/1.1 下容器不向应用层发「客户端已断连」信号</b>
 * （Tomcat 自己的 {@code AbstractProcessorLight} 对 {@code SocketEvent.DISCONNECT} 明写
 * "Do nothing here, just wait for it to get recycled"）⇒ 容器线程能观察到断连的<b>唯一时刻</b>，
 * 就是「<b>往 socket 写</b>」的那一刻。
 * <p>⇒ 本类提供一条<b>提前</b>的观测通道：<b>周期性地往 socket 写一小段探针字节</b>，
 * 写失败（{@code Broken pipe} / {@code Connection reset}）⇒ 判定「客户端已断连」。
 *
 * <h3>⚠️ 有意偏离 Go（签字档）</h3>
 * <p><b>本机制在 Go 侧没有对应物</b>（Go 用的是 {@code net/http} 内建的 ctx 取消），
 * 且它带来两处<b>明确的 wire / 行为差异</b>：
 * <ol>
 *   <li><b>wire 差异</b>：客户端在最终响应之前会先收到 N 行
 *       {@code HTTP/1.1 103 Early Hints}（RFC 9110 要求客户端忽略 1xx ⇒ 业务语义不受影响，
 *       但<b>字节层面确实多了</b>）。N = 探测轮数。</li>
 *   <li><b>时序差异</b>：Go 在断连瞬间定型 499；RAG 要等「下一次探测」才发现
 *       ⇒ 499 晚于 Go，延迟 ≈ 探测间隔。</li>
 * </ol>
 * <p>⇒ 登记见 {@code docs/Intentional_Deviations/global/common-tails.md} 的
 * <b>{@code SD-2026-10-25-04}</b>（<b>✅ 已签字 · 2026-10-25 用户拍板</b>）。
 * ⚠️ **订正（2026-10-27）**：本类 javadoc 原先指向的 {@code SD-2026-10-25-02}
 * 已于 2026-10-25 第十轮<b>撤销</b>（那条记录的是「框架限制」这个定性，
 * 问题本身已被方案⑤修掉 ⇒ 该条作废、不需签字）⇒ ⇒ **签字档是 {@code SD-2026-10-25-04}**。
 * 本类实现的是该档里第 ⑤ 条路径
 * （「主动探测写」）的<b>改良版</b>：不是「提前提交响应」，而是<b>直接写 socket</b>
 * ⇒ 不提交响应 ⇒ {@code Content-Type} / {@code Content-Length} 全部保住。
 *
 * <h3>为什么探针字节必须是「合法的 HTTP/1.1 1xx」</h3>
 * <p>往 socket 写任何字节都会出现在响应流的<b>最前面</b> ⇒ 必须是 HTTP/1.1 客户端能解析的东西。
 * 选 {@code 103 Early Hints}（纯 ASCII，无 body）的原因：
 * <ul>
 *   <li>1xx 是「informational response」，RFC 9110 规定客户端<b>必须忽略</b>，
 *       且<b>可以出现多次</b> ⇒ 重复探测天然合法；</li>
 *   <li>1xx <b>不带 body</b> ⇒ 不会与最终响应的 {@code Content-Length} 打架。</li>
 * </ul>
 *
 * <h3>🔴 为什么必须有「行数上界」（2026-10-27 新增 · 本机制的第二个硬约束）</h3>
 * <p>上面的 1xx 偏离是<b>已签字</b>的（{@code SD-2026-10-25-04}）⇒ <b>机制保留</b>；
 * 但「<b>N = 探测轮数</b>」这个 N **原本没有上界** ⇒ ⇒ 必须补上界，否则会击穿客户端。
 * <pre>
 *   击穿点：JDK 的 java.net.http 对**每个响应**最多接受 8 个 interim(1xx) 响应
 *     java.net.http/jdk/internal/net/http/Exchange.java:71-72
 *         MAX_NON_FINAL_RESPONSES = Utils.getIntegerNetProperty("jdk.httpclient.maxNonFinalResponses", 8)
 *     java.net.http/jdk/internal/net/http/Exchange.java:544-550
 *         ⇒ 计数超限即抛 ProtocolException("Too many interim responses received: 9 &gt; 8")
 *     （上限可由系统属性 jdk.httpclient.maxNonFinalResponses 配置 ⇒ 但**默认就是 8**）
 * </pre>
 * <p><b>为什么这会真的炸</b>：{@link HttpMiddleware} 的「切片等待」每片醒一次、
 * 每片写一次探针 ⇒ <b>1xx 行数 ≈ 耗时 ÷ 探测间隔</b>，而探测间隔默认 500ms
 * ⇒ ⇒ <b>任何耗时 &gt; ~4s 的请求</b>（8 × 500ms）就会让 JDK HttpClient 客户端
 * <b>直接抛异常</b>，根本读不到最终响应。
 * <p>⚠️ <b>跨域暴露面</b>：暴露量由各域的 {@code observability.http-timeout} 决定 ⇒
 * <b>{@code article-api} = 70s</b>（Go 真源 {@code Timeout: 70000} 对齐值）⇒
 * <b>最多 140 行</b>；其余 9 域 = 3s ⇒ 最多 6 行 ⇒ <b>article 域最危险</b>。
 * <p>⇒ <b>本类的做法</b>：给<b>每个请求</b>的 1xx 行数设上界
 * {@link #maxProbeLines()}（默认 {@link #DEFAULT_MAX_PROBE_LINES} = 4，
 * <b>取 4 而不是 8</b>：留一半余量 ⇒ 即便客户端把上限配到 4 也不炸）。
 * <ul>
 *   <li>计数<b>按请求</b>（request attribute）⇒ 不会跨请求互相吃预算
 *       ⇒ 不会把「后一个请求」的断连探测能力悄悄关掉；</li>
 *   <li>预算用尽后 <b>不再写探针字节</b>，但<b>等待语义完全不变</b>
 *       （仍然等到 worker 完成或超时 ⇒ <b>总窗口既不延长也不缩短</b>）⇒ <b>fail-open</b>；</li>
 *   <li>⚠️ <b>代价（必须写清）</b>：预算用尽后<b>不再有「主动探测」</b>
 *       ⇒ 超长请求（耗时 ≫ 预算 × 探测间隔）里，客户端中途断连**不再被提前发现**
 *       ⇒ 在飞的下游 gRPC 要等到<b>写最终响应</b>时才被取消（而不是提前取消）
 *       ⇒ 这是「保住客户端能解析响应」换来的 ⇒ <b>宁可晚取消，不可让客户端失败</b>。</li>
 * </ul>
 *
 * <h3>为什么必须「write + flush」两步（§3.3 的实测结论，不是推测）</h3>
 * <ul>
 *   <li>{@code SocketWrapperBase.write(boolean, byte[], int, int)}
 *       （{@code org/apache/tomcat/util/net/SocketWrapperBase.java:708}，public final）
 *       在 {@code len == 0} 时<b>直接 return</b>（零字节写不触发 syscall）⇒ 探针不能是空写。</li>
 *   <li>{@code writeBlocking}（同文件 {@code :779}）先把数据塞进 Tomcat 的 socket 写缓冲，
 *       <b>只有缓冲区满时才 {@code doWrite(true)} 真正 syscall</b>
 *       ⇒ <b>几十字节的探针永远留在缓冲区里 ⇒ 错误检测不到</b>。</li>
 *   <li>⇒ 必须再调 {@code SocketWrapperBase.flush(boolean)}（同文件 {@code :918}，public）
 *       强制把缓冲推到底层 socket（{@code flushBlocking → doWrite(true)}）。
 *       真 Tomcat 11.0.24 实测：客户端已 RST ⇒ {@code write + flush} 抛
 *       {@code IOException: Broken pipe}；<b>只 write 不 flush ⇒ 不抛异常</b>。</li>
 * </ul>
 *
 * <h3>为什么不能改用 {@code response.flushBuffer()}</h3>
 * <p>本仓的 {@link HttpMiddleware} 用的是<b>缓冲式</b>的 {@code ObservabilityResponseWriter}
 * ⇒ 所有 header 都延迟到最后的 {@code flushTo} 才写给底层 response。
 * 在业务逻辑还在跑时调 {@code flushBuffer()} 会<b>提前提交响应</b>
 * ⇒ 之后 {@code setContentType} / {@code setHeader} 被 Tomcat <b>静默忽略</b>
 * （本仓 E-1 那个 bug，见 {@code HttpMiddleware} 里 {@code setHeader} 的注释）
 * ⇒ <b>会丢掉 Content-Type</b>，是比本条更严重的回归。
 * <p>⇒ 直接写 socket 绕开 Tomcat 的 {@code OutputBuffer} ⇒ <b>不提交响应</b>
 * ⇒ {@code Content-Type} 与 {@code Content-Length} 全部保住。
 *
 * <h3>fail-open 纪律（§3.3）</h3>
 * <p>本类<b>任何一步失败都不得抛给调用方</b>、<b>不得改变现有行为</b>：
 * 反射链取不到（Tomcat 换版本 / 不是 Tomcat / 走了别的 wrapper）⇒ 返回
 * {@link State#UNAVAILABLE} ⇒ 调用方<b>什么都不做</b>，继续按原来的超时语义等待。
 */
public final class GoStyleClientDisconnectProbe {

    /**
     * 探测结果。
     */
    public enum State {
        /** 客户端仍在线（写 socket 成功）。 */
        ALIVE,
        /** 客户端已断连（写 socket 失败）。 */
        DISCONNECTED,
        /**
         * 探测不可用 ⇒ 调用方必须 <b>fail-open</b>（什么都不做，继续等）。
         */
        UNAVAILABLE,
        /**
         * 本请求的 <b>1xx 行数预算已用尽</b> ⇒ <b>不再写探针字节</b>。
         *
         * <p>⇒ 调用方同样必须 <b>fail-open</b>（什么都不做，继续按原超时语义等待）
         * ⇒ 与 {@link #UNAVAILABLE} 的区别只在于「为什么不做」：
         * {@code UNAVAILABLE} = 机制不可用（反射链取不到 / 非 HTTP/1.1 / 响应已提交 /
         * 被运维显式关闭），{@code BUDGET_EXHAUSTED} = 机制可用但<b>本请求已经写够了</b>。
         */
        BUDGET_EXHAUSTED
    }

    private static final Logger log = LoggerFactory.getLogger(GoStyleClientDisconnectProbe.class);

    /**
     * 探测间隔的 system property（毫秒）。
     *
     * <p>⚠️ <b>刻意不是配置项</b>：本机制必须做到「<b>零 api 模块改动</b>」——
     * 往 10 个 {@code application.yml} 加一项就等于要求每个域都改配置 ⇒ 违反
     * 「共享层改动不许外溢到业务模块」的纪律。
     * ⇒ 用 <b>常量 + system property</b>：默认 {@link #DEFAULT_PROBE_INTERVAL_MILLIS}，
     * 需要调整时用 {@code -Drag.common.http.disconnect-probe-interval-ms=200} 覆盖。
     */
    public static final String PROBE_INTERVAL_PROPERTY = "rag.common.http.disconnect-probe-interval-ms";

    /** 默认探测间隔（毫秒）。 */
    public static final long DEFAULT_PROBE_INTERVAL_MILLIS = 500L;

    /**
     * 探测间隔下限（毫秒）—— 防止有人把间隔调到 0/1ms 把请求变成「写 socket 忙循环」。
     */
    public static final long MIN_PROBE_INTERVAL_MILLIS = 50L;

    /**
     * 每请求的 1xx 探针<b>行数上界</b>的 system property。
     *
     * <p>⚠️ <b>同样刻意不是配置项</b>：理由同 {@link #PROBE_INTERVAL_PROPERTY}
     * ⇒ 零 api 模块改动 ⇒ 用 <b>常量 + system property</b>。
     * 需要放宽/收紧时用 {@code -Drag.common.http.disconnect-probe-max-lines=2} 覆盖。
     *
     * <p>取值语义：<b>0 = 显式关闭探针</b>（运维逃生口 ⇒ 会<b>失去主动断连探测</b>
     * ⇒ 本层会打 WARN）；<b>&gt; 0 = 行数上界</b>；非法值 ⇒ 回退默认并打 WARN。
     */
    public static final String MAX_PROBE_LINES_PROPERTY = "rag.common.http.disconnect-probe-max-lines";

    /**
     * 默认的 1xx 行数上界。
     *
     * <p>⚠️ <b>为什么是 4 而不是 8</b>：客户端侧的硬上限是
     * <b>JDK {@code java.net.http} 默认 8</b> 个 interim 响应
     * （{@code jdk.httpclient.maxNonFinalResponses}，见类 javadoc 的取证段）
     * ⇒ 取它的一半 ⇒ <b>即便客户端把上限配到 4 也炸不了</b>。
     * ⇒ ⚠️ **不要写成 8**：那就没有余量了。
     */
    public static final int DEFAULT_MAX_PROBE_LINES = 4;

    /**
     * 1xx 行数计数器的 request attribute 名。
     *
     * <p>⚠️ <b>必须是 request attribute</b>（而不是静态字段）：
     * 计数器的语义是「<b>本请求</b>已经写了多少行」⇒
     * 用静态字段会让<b>前一个请求吃光后一个请求的预算</b>
     * ⇒ 后面的请求将彻底失去主动断连探测 ⇒ ⇒ 守门
     * {@code GoStyleClientDisconnectProbeBudgetGateTest} 有专属断言钉这一条。
     */
    private static final String PROBE_LINES_ATTRIBUTE = "rag.common.http.disconnect-probe-lines";

    /**
     * 探针字节：{@code HTTP/1.1 103 Early Hints\r\n\r\n}（ASCII）。
     *
     * <p>⚠️ 长度以 {@link #PROBE_BYTES} 的实际值为准（28 字节），<b>不要在这里写死数字</b>。
     */
    private static final byte[] PROBE_BYTES =
            "HTTP/1.1 103 Early Hints\r\n\r\n".getBytes(StandardCharsets.US_ASCII);

    /** 只在 HTTP/1.1 上探测（HTTP/2 是二进制帧协议，写 HTTP/1.1 文本会破坏帧流）。 */
    private static final String HTTP_11 = "HTTP/1.1";

    private GoStyleClientDisconnectProbe() {
    }

    /**
     * 当前生效的探测间隔（毫秒）。
     *
     * <p>每次调用都重新读 system property ⇒ 运维改值后<b>无需重启</b>即可生效。
     *
     * @return 探测间隔（毫秒），恒 {@code >= }{@link #MIN_PROBE_INTERVAL_MILLIS}
     */
    public static long probeIntervalMillis() {
        String raw = System.getProperty(PROBE_INTERVAL_PROPERTY);
        if (raw == null || raw.isBlank()) {
            return DEFAULT_PROBE_INTERVAL_MILLIS;
        }
        try {
            long parsed = Long.parseLong(raw.trim());
            if (parsed < MIN_PROBE_INTERVAL_MILLIS) {
                return MIN_PROBE_INTERVAL_MILLIS;
            }
            return parsed;
        } catch (NumberFormatException e) {
            log.warn("[HTTP] 非法探测间隔 {}（{}），回退到默认 {}ms",
                    raw, PROBE_INTERVAL_PROPERTY, DEFAULT_PROBE_INTERVAL_MILLIS);
            return DEFAULT_PROBE_INTERVAL_MILLIS;
        }
    }

    /**
     * 当前生效的 1xx 行数上界。
     *
     * <p>每次调用都重新读 system property ⇒ 运维改值后<b>无需重启</b>即可生效
     * ⇒ 与 {@link #probeIntervalMillis()} 同一套口径。
     *
     * @return 行数上界；<b>0 = 探针被显式关闭</b>；非法值 ⇒ 回退
     *         {@link #DEFAULT_MAX_PROBE_LINES}
     */
    public static int maxProbeLines() {
        String raw = System.getProperty(MAX_PROBE_LINES_PROPERTY);
        if (raw == null || raw.isBlank()) {
            return DEFAULT_MAX_PROBE_LINES;
        }
        try {
            int parsed = Integer.parseInt(raw.trim());
            if (parsed < 0) {
                log.warn("[HTTP] 非法 1xx 行数上界 {}（{}），回退到默认 {}",
                        raw, MAX_PROBE_LINES_PROPERTY, DEFAULT_MAX_PROBE_LINES);
                return DEFAULT_MAX_PROBE_LINES;
            }
            if (parsed == 0) {
                log.warn("[HTTP] 1xx 探针已被显式关闭（{}={}）⇒ **主动断连探测失效**"
                                + "（客户端断连将只能等写最终响应时才发现）",
                        MAX_PROBE_LINES_PROPERTY, raw);
                return 0;
            }
            return parsed;
        } catch (NumberFormatException e) {
            log.warn("[HTTP] 非法 1xx 行数上界 {}（{}），回退到默认 {}",
                    raw, MAX_PROBE_LINES_PROPERTY, DEFAULT_MAX_PROBE_LINES);
            return DEFAULT_MAX_PROBE_LINES;
        }
    }

    /**
     * 本请求<b>已经写出</b>的 1xx 行数。
     *
     * <p>只统计<b>真正写出去</b>的行（写失败那一次不算 ⇒ 写失败时客户端已经走了，
     * 响应已死 ⇒ 预算不再有意义）。
     */
    private static int probeLinesWritten(HttpServletRequest request) {
        Object attr = request.getAttribute(PROBE_LINES_ATTRIBUTE);
        return attr instanceof Integer written ? written : 0;
    }

    /**
     * 执行一次断连探测。
     *
     * <p><b>本方法绝不抛异常</b>（fail-open）：任何失败都折叠成 {@link State#UNAVAILABLE}。
     *
     * @param request  当前请求（用于取 socket + 判协议 + 记行数预算）
     * @param response 当前响应（用于判「是否已提交」）
     * @return 探测结果
     */
    public static State probe(HttpServletRequest request, HttpServletResponse response) {
        if (request == null) {
            return State.UNAVAILABLE;
        }
        // ⚠️ 前置守卫 1：**只在 HTTP/1.1 上探测**。
        //    HTTP/2 是二进制帧协议，往 socket 写 HTTP/1.1 文本会**破坏帧流** ⇒ 必须跳过。
        String protocol = request.getProtocol();
        if (protocol == null || !HTTP_11.equalsIgnoreCase(protocol)) {
            return State.UNAVAILABLE;
        }
        // ⚠️ 前置守卫 2：**只在响应尚未提交时探测**。
        //    已提交 ⇒ Tomcat 的 OutputBuffer 可能正在往同一个 socket 写 ⇒ 裸写会与它交错。
        if (response != null && response.isCommitted()) {
            return State.UNAVAILABLE;
        }
        // 🔴 预算闸门（2026-10-27）：**每请求的 1xx 行数必须有上界**，
        //    否则慢请求会写出 N 行 103 ⇒ 击穿客户端的 interim 上限
        //    （JDK java.net.http 默认 8 ⇒ 第 9 行即 ProtocolException）
        //    ⇒ 详见类 javadoc 的「为什么必须有行数上界」一节。
        //    ⚠️ 本闸门必须排在**反射链之前** ⇒ 预算用尽后不再做无谓的反射。
        final int maxLines = maxProbeLines();
        if (maxLines <= 0) {
            // 显式关闭 ⇒ 机制整体不可用 ⇒ fail-open（调用方继续按原语义等待）
            return State.UNAVAILABLE;
        }
        final int written = probeLinesWritten(request);
        if (written >= maxLines) {
            // 预算用尽 ⇒ 本请求不再写探针字节 ⇒ 等待语义完全不变（fail-open）
            return State.BUDGET_EXHAUSTED;
        }
        Object socketWrapper = resolveSocketWrapper(request);
        if (socketWrapper == null) {
            return State.UNAVAILABLE;
        }
        try {
            // ⚠️⚠️ write 与 flush **必须成对**：只 write 的话字节滞留在 Tomcat 的写缓冲里，
            //    永远不触发 syscall ⇒ 断连检测不到（真 Tomcat 11.0.24 实测）。
            Reflect.WRITE.invoke(socketWrapper, Boolean.TRUE, PROBE_BYTES,
                    Integer.valueOf(0), Integer.valueOf(PROBE_BYTES.length));
            Reflect.FLUSH.invoke(socketWrapper, Boolean.TRUE);
            // ★ 只有**真的写出去**才占预算（写失败 ⇒ 客户端已走 ⇒ 响应已死）
            request.setAttribute(PROBE_LINES_ATTRIBUTE, Integer.valueOf(written + 1));
            return State.ALIVE;
        } catch (InvocationTargetException ite) {
            Throwable cause = ite.getCause();
            if (cause instanceof IOException) {
                // 写 socket 失败 ⇒ 客户端已断连（Broken pipe / Connection reset）
                return State.DISCONNECTED;
            }
            log.debug("[HTTP] 断连探测异常（非 IOException），按不可用处理", cause);
            return State.UNAVAILABLE;
        } catch (Throwable t) {
            // 反射链在运行期变形（Tomcat 升级 / 代理类）⇒ fail-open
            log.debug("[HTTP] 断连探测不可用", t);
            return State.UNAVAILABLE;
        }
    }

    // ------------------------------------------------------------------ 反射链

    /**
     * 取出底层 {@code SocketWrapperBase}。
     *
     * <p>反射链（真 Tomcat 11.0.24 实测可达）：
     * <pre>
     *   HttpServletRequest（实际是 org.apache.catalina.connector.RequestFacade）
     *     → 字段 "request"（protected，声明在 RequestFacade）
     *     → org.apache.catalina.connector.Request
     *     → public getCoyoteRequest()          （catalina/connector/Request.java:169）
     *     → org.apache.coyote.Request
     *     → 字段 "hook"（private，类型 ActionHook；实际持有 AbstractProcessor）
     *     → org.apache.coyote.AbstractProcessor
     *     → protected getSocketWrapper()        （coyote/AbstractProcessor.java:189）
     *     → org.apache.tomcat.util.net.SocketWrapperBase
     * </pre>
     *
     * @return socket wrapper；取不到返回 {@code null}
     */
    private static Object resolveSocketWrapper(HttpServletRequest request) {
        Object catalinaRequest = findCatalinaRequest(request);
        if (catalinaRequest == null || Reflect.GET_COYOTE_REQUEST == null) {
            return null;
        }
        Object coyoteRequest;
        try {
            coyoteRequest = Reflect.GET_COYOTE_REQUEST.invoke(catalinaRequest);
        } catch (Throwable t) {
            return null;
        }
        if (coyoteRequest == null || Reflect.HOOK_FIELD == null) {
            return null;
        }
        Object hook;
        try {
            hook = Reflect.HOOK_FIELD.get(coyoteRequest);
        } catch (Throwable t) {
            return null;
        }
        if (hook == null) {
            return null;
        }
        Method getSocketWrapper = Reflect.socketWrapperMethod(hook.getClass());
        if (getSocketWrapper == null) {
            return null;
        }
        try {
            return getSocketWrapper.invoke(hook);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 从 Servlet 请求对象里挖出 {@code org.apache.catalina.connector.Request}。
     *
     * <p>兼容两种形态：① 直接就是 {@code RequestFacade}（字段 {@code request}）；
     * ② 被 {@link ServletRequestWrapper} 包了一层（先解包再找字段）。
     */
    private static Object findCatalinaRequest(Object request) {
        Object current = request;
        // 解包深度上限：只为防环，正常 1~2 层足够
        for (int depth = 0; current != null && depth < 8; depth++) {
            if (current instanceof ServletRequestWrapper wrapper) {
                current = wrapper.getRequest();
                continue;
            }
            Object found = readRequestField(current);
            if (found != null) {
                return found;
            }
            return null;
        }
        return null;
    }

    /**
     * 在对象类层次里找名为 {@code request} 的字段（Tomcat 的 {@code RequestFacade.request}）。
     */
    private static Object readRequestField(Object target) {
        for (Class<?> c = target.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            Field field;
            try {
                field = c.getDeclaredField("request");
            } catch (NoSuchFieldException e) {
                continue;
            }
            try {
                field.setAccessible(true);
                Object value = field.get(target);
                if (value != null
                        && (Reflect.CATALINA_REQUEST == null || Reflect.CATALINA_REQUEST.isInstance(value))) {
                    return value;
                }
            } catch (Throwable t) {
                // setAccessible 被模块系统拒绝 / 字段类型不对 ⇒ 继续往上找
                return null;
            }
        }
        return null;
    }

    /**
     * 反射对象的<b>一次性</b>解析结果。
     *
     * <p>全部在类初始化时解析一次（探测每 500ms 一次，不值得每次都查反射表）；
     * 任何一步失败都留 {@code null} ⇒ 探测恒为 {@link State#UNAVAILABLE}（fail-open）。
     */
    private static final class Reflect {

        /** {@code org.apache.catalina.connector.Request}（非 Tomcat ⇒ null）。 */
        static final Class<?> CATALINA_REQUEST = loadClass("org.apache.catalina.connector.Request");

        /** {@code org.apache.coyote.Request}（非 Tomcat ⇒ null）。 */
        static final Class<?> COYOTE_REQUEST = loadClass("org.apache.coyote.Request");

        /** {@code org.apache.tomcat.util.net.SocketWrapperBase}（非 Tomcat ⇒ null）。 */
        static final Class<?> SOCKET_WRAPPER_BASE = loadClass("org.apache.tomcat.util.net.SocketWrapperBase");

        /** {@code Request.getCoyoteRequest()}。 */
        static final Method GET_COYOTE_REQUEST =
                lookupMethod(CATALINA_REQUEST, "getCoyoteRequest");

        /** {@code coyote.Request.hook}（private）。 */
        static final Field HOOK_FIELD = lookupField(COYOTE_REQUEST, "hook");

        /** {@code SocketWrapperBase.write(boolean, byte[], int, int)}（public final）。 */
        static final Method WRITE = lookupMethod(SOCKET_WRAPPER_BASE,
                "write", boolean.class, byte[].class, int.class, int.class);

        /** {@code SocketWrapperBase.flush(boolean)}（public）。 */
        static final Method FLUSH = lookupMethod(SOCKET_WRAPPER_BASE, "flush", boolean.class);

        /**
         * {@code getSocketWrapper()}：声明在 {@code AbstractProcessor}（protected final），
         * 但 Tomcat 的实现类不同（Http11Processor / Http2Processor …）
         * ⇒ 按<b>实例的类层次</b>查，不能写死类名。
         */
        static final java.util.Map<Class<?>, Method> GET_SOCKET_WRAPPER_METHOD =
                new java.util.concurrent.ConcurrentHashMap<>();

        private Reflect() {
        }

        private static Class<?> loadClass(String name) {
            try {
                return Class.forName(name, false, Reflect.class.getClassLoader());
            } catch (Throwable t) {
                return null;
            }
        }

        private static Method lookupMethod(Class<?> owner, String name, Class<?>... params) {
            if (owner == null) {
                return null;
            }
            try {
                Method m = owner.getMethod(name, params);
                m.setAccessible(true);
                return m;
            } catch (Throwable t) {
                return null;
            }
        }

        private static Field lookupField(Class<?> owner, String name) {
            if (owner == null) {
                return null;
            }
            try {
                Field f = owner.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (Throwable t) {
                return null;
            }
        }

        /**
         * 在类层次里找 protected 的 {@code getSocketWrapper()} 并缓存。
         */
        static Method socketWrapperMethod(Class<?> concrete) {
            Method cached = GET_SOCKET_WRAPPER_METHOD.get(concrete);
            if (cached != null) {
                return cached;
            }
            Method found = null;
            for (Class<?> c = concrete; c != null && c != Object.class; c = c.getSuperclass()) {
                try {
                    Method m = c.getDeclaredMethod("getSocketWrapper");
                    m.setAccessible(true);
                    found = m;
                    break;
                } catch (NoSuchMethodException e) {
                    continue;
                } catch (Throwable t) {
                    break;
                }
            }
            if (found == null) {
                return null;
            }
            GET_SOCKET_WRAPPER_METHOD.put(concrete, found);
            return found;
        }
    }
}
