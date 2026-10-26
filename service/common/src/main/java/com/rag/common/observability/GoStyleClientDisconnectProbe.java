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
 * <b>{@code SD-2026-10-25-02}</b>（<b>待签字</b>）。本类实现的是该档里第 ⑤ 条路径
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
        UNAVAILABLE
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
     * 执行一次断连探测。
     *
     * <p><b>本方法绝不抛异常</b>（fail-open）：任何失败都折叠成 {@link State#UNAVAILABLE}。
     *
     * @param request  当前请求（用于取 socket + 判协议）
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
