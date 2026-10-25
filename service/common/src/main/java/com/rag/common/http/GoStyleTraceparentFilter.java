package com.rag.common.http;

import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;

/**
 * 🔴 <b>T-8</b>：把 <b>W3C {@code traceparent}</b> 写进<b>响应头</b> —— 对齐 go-zero 的
 * {@code rest/handler/tracehandler.go}。
 *
 * <h2>Go 侧事实（真源，逐行）</h2>
 * <pre>
 * go-zero@v1.10.2/rest/handler/tracehandler.go:47-63
 *   ctx := propagator.Extract(r.Context(), propagation.HeaderCarrier(r.Header))   // ← 取入向 traceparent
 *   spanCtx, span := tracer.Start(ctx, spanName,
 *       oteltrace.WithSpanKind(oteltrace.SpanKindServer),
 *       oteltrace.WithAttributes(semconv.HTTPServerAttributesFromHTTPRequest(...)))
 *   defer span.End()
 *   // convenient for tracking error messages
 *   propagator.Inject(spanCtx, propagation.HeaderCarrier(w.Header()))            // ← 注入【响应头】
 *   next.ServeHTTP(trw, r.WithContext(spanCtx))
 * </pre>
 *
 * <h3>⇒ 三条必须对齐的语义</h3>
 * <ol>
 *   <li><b>注入的是「当前 server span」，不是回显入向的 parent</b> ——
 *       入向 {@code traceparent} 只是 <b>parent</b>（Extract → Start），
 *       写出去的是 <b>server span 自己</b>的 {@code span-id}。
 *       ⚠️ <b>回显入向头是最容易写错的一种「看起来对」的实现</b>。</li>
 *   <li><b>注入在业务执行之前</b>（Go 在 {@code next.ServeHTTP} 之前就 Inject）
 *       ⇒ 任何状态码（200 / 400 / 401 / 404 / 503）都带得上。</li>
 *   <li><b>trace-id 沿用入向</b>（server span 是入向 context 的子 span）
 *       ⇒ 客户端能把自己的 trace 与服务端串起来。</li>
 * </ol>
 *
 * <h3>为什么 <b>10 个 api 全都有</b>（Go 侧）</h3>
 * <pre>
 * go-zero@v1.10.2/rest/config.go:11-22
 *   MiddlewaresConf struct {
 *       Trace bool `json:",default=true"`      // ← 默认开
 *       ...
 *   }
 * go-zero@v1.10.2/rest/engine.go:134-138
 *   if ng.conf.Middlewares.Trace {
 *       chn = chn.Append(handler.TraceHandler(ng.conf.Name, route.Path, ...))
 *   }
 * go-zero@v1.10.2/rest/engine.go:245-251
 *   // notFoundHandler 也挂 ⇒ 连 404 都带 traceparent
 *   // ⚠️ 而它**默认就装着**：rest/server.go:58 的 NewServer 里
 *   //   opts = append([]RunOption{WithNotFoundHandler(nil)}, opts...)
 *   //   ⇒ 每个 api 都有 ⇒ 「未注册路径」的 404 也经过它 ⇒ 也带 traceparent
 * </pre>
 * ⇒ 本仓 10 个 api 的 yaml <b>都没配</b> {@code Middlewares} / {@code TraceIgnorePaths}
 * ⇒ Go 侧 {@code Trace=true}、无 ignore path ⇒ <b>每个响应都带</b>。
 *
 * <h2>🔴 为什么 RAG 侧此前<b>完全没有</b>这个头（根因，不是「忘了写一行」）</h2>
 * <p>写这行之前，RAG 侧<b>连「当前 server span」都不存在</b>：
 * <ul>
 *   <li>全仓 <b>没有</b> {@code micrometer-tracing-bridge-otel}、也没有
 *       {@code opentelemetry-sdk-extension-autoconfigure}
 *       ⇒ Spring Boot 的 tracing 自动配置<b>不生效</b>；</li>
 *   <li>⇒ {@code GlobalOpenTelemetry.get()} 是 <b>no-op</b>
 *       ⇒ {@code Span.fromContext(ctx).getSpanContext()} 恒为 <b>INVALID</b>
 *       ⇒ <b>任何</b>「从当前上下文取 traceparent 再写出去」的实现都写不出东西。</li>
 * </ul>
 * ⇒ 所以本类自带一个<b>私有</b> {@link SdkTracerProvider}（<b>不碰全局</b>，理由见下），
 * 每个请求起一个 <b>SERVER</b> span，再注入。
 *
 * <h2>🔴 为什么 <b>不</b>碰 {@code GlobalOpenTelemetry}</h2>
 * <ul>
 *   <li>{@code GlobalOpenTelemetry.set(...)} <b>全局只能设一次</b>
 *       ⇒ 一旦本类设了，<b>同 JVM 内</b>所有 {@code GlobalOpenTelemetry.get().getTracer(...)}
 *       的调用方（如 {@code RpcInterceptor} / {@code MqConsumer} / {@code AdminSpans}）
 *       的行为<b>全部改变</b>（从 no-op 变成真建 span）⇒ <b>那是另一件事，不该由本条带出来</b>。</li>
 *   <li>本类只需要「一个能产生<b>有效</b> SpanContext 的 tracer」⇒ 私有实例足够，
 *       且 {@code HttpMiddleware} 取当前 span 走的是 {@code Span.fromContext(Context.current())}
 *       ⇒ <b>不依赖全局</b>，照样拿得到。</li>
 * </ul>
 *
 * <h2>🔴 sampler 必须与 Go 一致</h2>
 * <pre>
 * go-zero@v1.10.2/core/trace/agent.go:110
 *   sdktrace.WithSampler(sdktrace.ParentBased(sdktrace.TraceIDRatioBased(c.Sampler)))
 * Go 侧 yaml：Telemetry: { Sampler: 1.0 }        （各 api 的 etc/*.yaml）
 * ⇒ 根 span 必采样；入向 parent 不采样时子 span 也不采样。
 * </pre>
 * ⇒ 本类用 {@code Sampler.parentBased(Sampler.traceIdRatioBased(1.0))}，
 * 与 RAG 各 api yaml 里已写的 {@code otel.traces.sampler: parentbased_always_on} <b>同义</b>
 * （那份 yaml 一直是对的，缺的只是「把 SDK 装上并接进请求」——本类补的就是这一步）。
 *
 * <h2>⚠️ 头名<b>大小写</b>：本类主动 canonical 化</h2>
 * <p>Go 的 {@code propagation.HeaderCarrier.Set} 走 {@code textproto.MIMEHeader.Set}
 * ⇒ 键被 canonical 成 {@code Traceparent}；而 OTel Java 的 propagator 写的是<b>小写</b>
 * {@code traceparent}，Tomcat 又<b>原样</b>发出 ⇒ 直接照抄会得到 {@code traceparent: ...}
 * （与 Go 的 {@code Traceparent: ...} <b>字节级不一致</b>）。
 * ⇒ 本类的 setter 把键 canonical 成首字母大写 ⇒ 与 Go 逐字节对齐。
 *
 * <h2>⚠️ 边界：本类<b>只</b>保证「响应头」这一维</h2>
 * <ul>
 *   <li>Go 还会把 span <b>导出</b>到 jaeger（{@code Telemetry.Endpoint}）；
 *       RAG 侧的 <b>trace 导出链路整体缺失</b>（没有 OTel 自动配置、没有 exporter 装配）
 *       ⇒ 那是<b>另一条独立缺口</b>，本轮<b>不碰</b>（见 global 台账 T-11）。</li>
 *   <li>Go 的 span 名 = <b>路由 pattern</b>（{@code route.Path}）；本类用请求 URI
 *       ⇒ <b>span 名不可 wire 观察</b>（只在导出端可见）⇒ 本轮不追求一致。</li>
 *   <li>🔴 <b>「方法不匹配」那个出口，RAG 比 Go 多带一个 {@code Traceparent}</b> ——
 *       Go 的该出口在 <b>router 层</b>（{@code rest/router/patrouter.go:66-72}
 *       的 {@code pr.notAllowed} / 默认 405）⇒ <b>不经过 TraceHandler</b> ⇒ <b>不带</b>；
 *       RAG 的同一出口落在 <b>DispatcherServlet 内</b> ⇒ filter 链<b>早已跑完</b> ⇒ <b>多带</b>。
 *       <br>⇒ 根因是<b>出口所在层级不同</b>，<b>共享层改不动</b>（filter 阶段无法知道
 *       「这个请求最终会不会落到方法不匹配」）⇒ 已登记为 global 台账 <b>T-12</b>。</li>
 * </ul>
 *
 * <h2>⚠️ 链上位置：<b>CORS 之后、HttpMiddleware 之前</b>（本类取 {@code HIGHEST_PRECEDENCE + 3}）</h2>
 * <p>三个约束<b>各自都有 Go 出处</b>，缺一个就会产生字节级差异：
 * <ol>
 *   <li><b>必须在 CORS 之后</b> ——
 *       Go {@code rest/internal/cors/handlers.go:53-67} 的 {@code Middleware} 对 {@code OPTIONS}
 *       <b>短路</b>（{@code :61-63} 直接 {@code WriteHeader(204)}，<b>不调 next</b>）
 *       ⇒ 预检响应<b>根本不经过 router</b> ⇒ <b>不经过 TraceHandler</b>
 *       ⇒ <b>Go 的 204 预检响应没有 traceparent</b>。
 *       <br>⇒ 本类若排在 CORS 之前 ⇒ 预检响应会<b>多带</b>一个 traceparent ⇒ 与 Go 不一致。</li>
 *   <li><b>必须在 MaxConns / Breaker / Shedding 之前</b> ——
 *       Go {@code rest/engine.go:130-152} 的 {@code buildChainWithNativeMiddlewares} 里
 *       {@code TraceHandler} 是 <b>native middleware 链里的第一个</b>（{@code :135}），
 *       MaxConns/Breaker/Shedding 在其后（{@code :146/:149/:152}）
 *       ⇒ <b>被它们拒成 503 的响应也带</b> traceparent（Trace 早就注入过了）。
 *       <br>⇒ 本类若排在它们之后 ⇒ 503 响应<b>丢头</b>。</li>
 *   <li><b>必须在 {@code HttpMiddlewareFilter} 之前</b>（<b>承重</b>）——
 *       {@code HttpMiddleware} 在<b>容器线程</b>上抓 {@code Context.current()}
 *       （{@code HttpMiddleware.java:269} 的 {@code parentOtelCtx}），
 *       再在 worker 线程里用 {@code parentOtelCtx.makeCurrent()} 复原（{@code :312}）
 *       ⇒ 它给 span 打的属性（{@code Span.fromContext(...)}，{@code :358/:455/:485}）
 *       落在<b>「进入 HttpMiddleware 时当前的那个 span」</b>上
 *       ⇒ 本类的 server span 必须<b>已经 current</b>，否则属性全落到 no-op span 上。</li>
 * </ol>
 * <p>⚠️ <b>与 {@code path.Clean} 的相对位置无关</b>：
 * RAG 的 {@code GoStylePathNormalizingFilter} <b>不短路</b>（只包装 request 后继续下传）
 * ⇒ 它自己<b>不产生响应</b> ⇒ 排前排后都不影响任何响应头。
 * ⇒ 之所以仍排在它之后，是因为它要覆写「路径」而本类要读 {@code getRequestURI()} 取 span 名。</p>
 *
 * <h2>📌 favorite 域</h2>
 * <p>本类是<b>共享层</b> ⇒ 10 个 api <b>全部</b>生效，<b>含 favorite</b>。
 * ⇒ 但这<b>不是</b>「给 favorite 加了一个 Go 没有的头」：
 * Go 的 favorite api 同样挂 {@code TraceHandler} ⇒ <b>Go 侧 favorite 也带这个头</b>
 * ⇒ 本次是<b>向 Go 收敛</b>，不是制造新的漂移。
 * ⇒ 按 §5.3c 记账并告知该域 owner（favorite 的代码/配置/测试/台账<b>零改动</b>）。
 * <p>⚠️ 但 favorite 也**同吃 T-12**（共享层 ⇒ 全 10 个 api 一致生效）
 * ⇒ 即「方法不匹配」那个出口多带一个 {@code Traceparent} ⇒ 同样<b>不是</b>给 favorite
 * 新造的漂移（Go 的 favorite 在该出口同样不带）⇒ 只是**两边的出口层级不同**而已。
 */
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Order(Ordered.HIGHEST_PRECEDENCE + 3)
public class GoStyleTraceparentFilter implements Filter {

    /**
     * 🔴 <b>私有</b> tracer provider —— <b>刻意不注册成全局</b>（理由见类注释）。
     *
     * <p>⚠️ <b>没有配任何 SpanProcessor</b> ⇒ span 会被创建（SpanContext 有效）
     * 但<b>不会导出</b> ⇒ 本类只负责「响应头」这一维（Go 的导出链路缺失是另一条缺口）。
     * ⇒ 好处：<b>不产生任何后台线程</b>（BatchSpanProcessor 才会）⇒ 不需要关闭钩子。
     */
    private static final SdkTracerProvider PROVIDER = SdkTracerProvider.builder()
            // Go: sdktrace.ParentBased(sdktrace.TraceIDRatioBased(c.Sampler))，c.Sampler = 1.0
            .setSampler(Sampler.parentBased(Sampler.traceIdRatioBased(1.0)))
            .build();

    private static final Tracer TRACER = PROVIDER.get("rag-http-server");

    /**
     * propagator 组成与 Go 一致：W3C TraceContext + W3C Baggage。
     *
     * <p>Go 侧 {@code otel.GetTextMapPropagator()} 在装了
     * {@code go.opentelemetry.io/otel/sdk/trace} 时默认就是
     * {@code TraceContext + Baggage} 的 composite。
     */
    private static final TextMapPropagator PROPAGATOR = TextMapPropagator.composite(
            W3CTraceContextPropagator.getInstance(),
            W3CBaggagePropagator.getInstance());

    /** 从入向请求取 {@code traceparent} / {@code tracestate} / baggage。 */
    private static final TextMapGetter<HttpServletRequest> REQUEST_GETTER =
            new TextMapGetter<>() {
                @Override
                public Iterable<String> keys(HttpServletRequest carrier) {
                    return carrier.getHeaderNames() == null
                            ? Collections.<String>emptyList()
                            : Collections.list(carrier.getHeaderNames());
                }

                @Override
                public String get(HttpServletRequest carrier, String key) {
                    return carrier == null ? null : carrier.getHeader(key);
                }
            };

    /**
     * 往响应头写。
     *
     * <p>🔴 <b>canonical 化键名</b>（与 Go 的 {@code textproto.CanonicalMIMEHeaderKey} 同形）：
     * {@code traceparent ⇒ Traceparent}、{@code tracestate ⇒ Tracestate}。
     * ⇒ 否则 Tomcat 会原样发出小写 {@code traceparent}，与 Go 的字节级不一致（见类注释）。
     */
    private static final TextMapSetter<HttpServletResponse> RESPONSE_SETTER =
            (carrier, key, value) -> carrier.setHeader(canonicalHeaderName(key), value);

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest httpReq)
                || !(response instanceof HttpServletResponse httpResp)) {
            chain.doFilter(request, response);
            return;
        }

        // ① Extract：入向 traceparent 成为 server span 的 **parent**（Go: propagator.Extract）
        Context parent = PROPAGATOR.extract(Context.current(), httpReq, REQUEST_GETTER);

        // ② Start：起一个 SERVER span（Go: tracer.Start(ctx, spanName, WithSpanKind(SpanKindServer), …)）
        Span span = TRACER.spanBuilder(spanName(httpReq))
                .setParent(parent)
                .setSpanKind(SpanKind.SERVER)
                .startSpan();

        try (Scope scope = span.makeCurrent()) {
            // ③ Inject：**业务执行之前**写响应头（Go: propagator.Inject 在 next.ServeHTTP 之前）
            //    ⚠️ 写的是 **server span 自己**的 context（不是入向 parent）——
            //       这是 Go 的语义，也是最容易写错的一处。
            PROPAGATOR.inject(Context.current(), httpResp, RESPONSE_SETTER);
            chain.doFilter(request, response);
        } finally {
            span.end();
        }
    }

    /**
     * span 名 —— Go 用的是<b>路由 pattern</b>（{@code route.Path}）。
     *
     * <p>本类用请求 URI：Spring 的 {@code BEST_MATCHING_PATTERN_ATTRIBUTE} 要等
     * DispatcherServlet 跑完才填 ⇒ filter 里拿不到；而 span 名<b>不可 wire 观察</b>
     * ⇒ 不为它引入额外耦合。
     */
    private static String spanName(HttpServletRequest req) {
        String uri = req.getRequestURI();
        return (uri == null || uri.isEmpty()) ? "http" : uri;
    }

    /**
     * HTTP 头名 canonical 化（首字母大写 + 每段首字母大写），与 Go 的
     * {@code textproto.CanonicalMIMEHeaderKey} 对齐。
     */
    static String canonicalHeaderName(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        StringBuilder sb = new StringBuilder(name.length());
        boolean upper = true;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '-') {
                sb.append(c);
                upper = true;
            } else if (upper) {
                sb.append(Character.toUpperCase(c));
                upper = false;
            } else {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }
}
