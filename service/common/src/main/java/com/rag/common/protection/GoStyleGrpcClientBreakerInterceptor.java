package com.rag.common.protection;

import net.devh.boot.grpc.client.interceptor.GrpcGlobalClientInterceptor;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.ForwardingClientCallListener;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.DoubleSupplier;

/**
 * L4 idiomatization：**gRPC client 侧**按方法的熔断，对齐 Go
 * {@code zrpc/internal/clientinterceptors.BreakerInterceptor}。
 *
 * <p><b>⚠️ 两个注解缺一不可，且都不是可选项</b>（都是 {@code GoStyleGrpcClientBreakerWiringTest} 实测出来的）：
 * <ul>
 *   <li>{@code @GrpcGlobalClientInterceptor} —— devh starter 的
 *       {@code AnnotationGlobalClientInterceptorConfigurer} 用
 *       {@code applicationContext.getBeansWithAnnotation(...)} 收集拦截器，
 *       <b>它只认这个注解，不认「是不是 ClientInterceptor」</b>。
 *       漏了它 ⇒ Bean 存在但 {@code GlobalClientInterceptorRegistry} 列表为空
 *       ⇒ channel 工厂不会把它装进 channel ⇒ <b>熔断代码一次都不会执行</b>。</li>
 *   <li>{@code @Component} —— {@code @GrpcGlobalClientInterceptor} 本身
 *       <b>不是</b> Spring stereotype（javap 显示只有 {@code @Target} + {@code @Retention}），
 *       不加它 Bean 根本不会被创建。</li>
 * </ul>
 * ⚠️ 这正是 AGENTS.md §3.3「不能仅凭存在 {@code @Component} 就推断责任已被承担」的形态 ——
 * 纯单元测试直接 {@code new} 拦截器，8 条全绿，
 * 而真实的 Spring 装配下它<b>完全没生效</b>。
 *
 * <p><b>Go 出处（三段，缺一不可）</b>：
 * <pre>
 * // ① zrpc/internal/config.go:14     —— 默认开启
 * ClientMiddlewaresConf struct { ... Breaker bool `json:",default=true"` }
 *
 * // ② zrpc/internal/client.go:112-113 —— 装配
 * if c.middlewares.Breaker {
 *     interceptors = append(interceptors, clientinterceptors.BreakerInterceptor)
 * }
 *
 * // ③ zrpc/internal/clientinterceptors/breakerinterceptor.go:13-18 —— 本类的对位物
 * func BreakerInterceptor(ctx context.Context, method string, req, reply any,
 *     cc *grpc.ClientConn, invoker grpc.UnaryInvoker, opts ...grpc.CallOption) error {
 *     breakerName := path.Join(cc.Target(), method)
 *     return breaker.DoWithAcceptableCtx(ctx, breakerName, func() error {
 *         return invoker(ctx, method, req, reply, cc, opts...)
 *     }, codes.Acceptable)
 * }
 * </pre>
 * Go 的 {@code etc/messagecenter.yaml} 未配置 {@code Middlewares} ⇒ 走 ① 的 {@code default=true}
 * ⇒ <b>这层在 Go 侧是开的</b>。原 {@code MessageRpcClient} 注释写「Go 侧没有这层」是事实错误，已更正。
 *
 * <p><b>⚠️ 本层最关键的一行是「acceptable 判据」；写成「非 OK 即失败」会引入比现状更糟的缺陷</b>。
 * Go 的 {@code zrpc/internal/codes/accept.go:9-17}：
 * <pre>
 * func Acceptable(err error) bool {
 *     switch status.Code(err) {
 *     case codes.DeadlineExceeded, codes.Internal, codes.Unavailable, codes.DataLoss,
 *          codes.Unimplemented, codes.ResourceExhausted:
 *         return false                     // ← 只有这 6 个算失败
 *     default:
 *         return true                      // ← 其余（含 OK）一律算成功
 *     }
 * }
 * </pre>
 * message-rpc 的业务错误（逐个 {@code errWithBizCode} 调用点核对）只用
 * {@code InvalidArgument / NotFound / PermissionDenied}，<b>全都不在失败名单里</b>
 * ⇒ <b>业务错误不参与熔断</b>。
 * ⚠️ 若把判据写成「非 OK 即失败」，{@code NOT_FOUND}（会话不存在 5202）、
 * {@code PERMISSION_DENIED}（无权限 5205）这类<b>正常运营中天天发生</b>的业务错误
 * 会被大量计成失败 ⇒ 熔断误开 ⇒ 打掉正常流量。那是<b>新增行为且比现状更糟</b>。
 *
 * <p><b>隔离单位 = RPC 方法</b>：Go 用 {@code path.Join(cc.Target(), method)}。
 * 本类用 gRPC 的 {@code method.getFullMethodName()}（形如
 * {@code /message.v1.MessageService/SendChat}）。
 * ⚠️ 与 Go 唯一的差别是少了 {@code cc.Target()} 前缀 —— 本服务只有一个 message-rpc
 * channel，加前缀无区分价值；而 {@code target} 在 discovery 场景下是动态的，
 * 拿来做 map key 并不稳定。此处<b>显式留痕</b>，不作为隐藏缺口。
 *
 * <p><b>熔断算法直接复用 {@link GoStyleBreaker}</b>：go-zero 的 client breaker
 * 与 HTTP 层 breaker 是<b>同一个</b> googleBreaker
 * （{@code DoWithAcceptableCtx} 内部即 {@code NewBreaker(...)}），
 * 所以复用同一份实现是<b>对齐</b>，不是「顺手复用」。
 *
 * <p><b>为什么用 {@link ForwardingClientCall}</b> 而非自己实现 {@link ClientCall}：
 * gRPC 的结果通过 {@code Listener#onClose(status, trailers)} <b>异步</b>回来，
 * 判据必须落在这里；自己手写 {@link ClientCall} 意味着要手工转发
 * {@code request/sendMessage/halfClose/cancel} 五个方法，任何一个漏转发都会静默挂死调用。
 * ⇒ 用 gRPC 官方的 forwarding 基类，框架负责透传，本类只覆写 {@code start} 加一层 listener。
 */
@ConditionalOnClass({GrpcGlobalClientInterceptor.class, io.grpc.ManagedChannel.class})
@Component
@GrpcGlobalClientInterceptor
public class GoStyleGrpcClientBreakerInterceptor implements ClientInterceptor {

    private static final Logger log = LoggerFactory.getLogger(GoStyleGrpcClientBreakerInterceptor.class);

    private static final DoubleSupplier DEFAULT_RANDOM =
            () -> java.util.concurrent.ThreadLocalRandom.current().nextDouble();

    private final boolean enabled;
    private final Map<String, GoStyleBreaker> breakers = new ConcurrentHashMap<>();

    /**
     * 概率判定的随机源。
     *
     * <p>⚠️ <b>刻意做成可变、且由 breaker 每次动态读取</b>（而不是构造时固化进 breaker）：
     * 熔断的最后一步是概率判定，若用真随机，端到端测试<b>无法确定性断言</b>
     * 「熔断一定会打开」。测试通过 {@link #setRandomSource(DoubleSupplier)} 钉成 {@code 0.0}，
     * 使 {@code rand() < dropRatio} 在 {@code dropRatio > 0} 时恒成立。
     */
    private volatile DoubleSupplier randomSource;

    /**
     * Spring 注入点。
     *
     * <p>⚠️ <b>必须显式标注</b>：本类有第二个构造器（给测试注入随机源），
     * Spring 在<b>多个</b>构造器且都无 {@code @Autowired} 时会退回找无参构造器，
     * 找不到就抛 {@code No default constructor found} ⇒ <b>整个上下文起不来</b>。
     * 这个缺陷是 {@code GoStyleGrpcClientBreakerWiringTest} 抓出来的 ——
     * 纯单元测试（直接 {@code new}）全绿，完全看不到它。
     * 这正是「不能仅凭存在 {@code @Component} 就推断责任已被承担」（§3.3）的又一例。
     */
    @Autowired
    public GoStyleGrpcClientBreakerInterceptor(GoStyleProtectionProperties props) {
        this.enabled = props.getBreaker().isEnabled();
        this.randomSource = DEFAULT_RANDOM;
    }

    /** 测试构造：指定随机源，便于构造确定性熔断用例。 */
    GoStyleGrpcClientBreakerInterceptor(boolean enabled, DoubleSupplier random) {
        this.enabled = enabled;
        this.randomSource = random == null ? DEFAULT_RANDOM : random;
    }

    /**
     * <b>测试接缝</b>：注入确定性随机源（端到端用例需要它才能断言熔断必然打开）。
     *
     * <p>为什么是 {@code public} 而不是包级可见：端到端守门用例住在<b>业务模块</b>
     * （{@code message-api} 的 {@code GoStyleGrpcClientBreakerE2ETest}）——
     * 它要证明的是「拦截器在 message-api 的真实调用链上」，这 inherently 需要
     * 跨包访问。收窄成包级可见的唯一效果是把那条证据链删掉。
     * ⚠️ 生产代码不得调用本方法（生产路径的随机源是 {@link #DEFAULT_RANDOM}）。
     */
    public void setRandomSource(DoubleSupplier random) {
        this.randomSource = random == null ? DEFAULT_RANDOM : random;
    }

    private GoStyleBreaker breakerOf(String fullMethodName) {
        // 传<b>方法引用</b>而非当前值 ⇒ breaker 每次判定都读到最新的 randomSource
        return breakers.computeIfAbsent(fullMethodName,
                name -> new GoStyleBreaker(name, this::currentRandom));
    }

    /**
     * Go 侧的「channel 身份」= {@code cc.Target()}。
     *
     * <p>⚠️ <b>是 dial 时传入的 target 字符串，不是解析后的 IP:Port</b> ——
     * grpc-java 的 {@code cc.Target()} 也是原始 target（{@code ManagedChannelImpl} 保留它）。
     * ⇒ 对本项目（devh + {@code discovery:///xxx}）= **服务名**。
     *
     * <p>⚠️ 取不到时返回空串 ⇒ breaker 退化为「仅按方法名」⇒ 至少不会崩，
     * 但会退回修复前那种「跨服务共用」的行为 ⇒ 已记进台账的残余面。
     */
    static String targetOf(Channel channel) {
        if (channel == null) {
            return "";
        }
        String authority = channel.authority();
        return authority == null ? "" : authority;
    }

    /**
     * Go: {@code path.Join(cc.Target(), method)}。
     *
     * <p>⚠️ <b>字符串形式与 Go 不会逐字节相同</b>（表达层差异，不影响行为）：
     * <ul>
     *   <li>Go 的 target 是 {@code etcd:///etcd:2379?key=message.rpc}（含发现协议与 etcd 地址），
     *       RAG 的是服务名（{@code message-rpc}）⇒ <b>本身就不是同一个字符串</b>；</li>
     *   <li>Go 的 {@code path.Join} 会把 {@code ///} 折叠成 {@code /}
     *       （⇒ {@code etcd:/etcd:2379?key=message.rpc/foo.Bar/Baz}）
     *       ⇒ 本实现做<b>同样的最小折叠</b>（连续斜杠合并）。</li>
     * </ul>
     * ⇒ <b>可观察的语义完全一致</b>：breaker 按「<b>哪个下游服务</b> + 哪个方法」分组。
     * ⇒ breaker 名会出现在日志与熔断统计里（Go 侧同样会）⇒ 属**表达层差异**。
     *
     * <p>⚠️ <b>不</b>实现 {@code path.Clean} 的 {@code "." / ".."} 处理：
     * authority 与 gRPC 方法名里都不可能出现这两者。
     */
    static String goBreakerName(String target, String fullMethodName) {
        if (target == null || target.isEmpty()) {
            return fullMethodName;
        }
        String raw = target + "/" + fullMethodName;
        StringBuilder sb = new StringBuilder(raw.length());
        boolean prevSlash = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '/') {
                if (prevSlash) {
                    continue;
                }
                prevSlash = true;
            } else {
                prevSlash = false;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private double currentRandom() {
        return this.randomSource.getAsDouble();
    }

    /**
     * <b>测试接缝</b>：已建的 breaker 数。
     *
     * <p>⚠️ <b>语义在 T-9 修复后变了</b>：键从「方法名」变成「<b>服务 + 方法</b>」
     * ⇒ 这个数现在是「<b>(下游服务, 方法) 对</b>数」，不是「方法数」。
     * ⇒ 端到端用例断言它时，<b>必须同时固定住服务身份</b>，否则断言会失真。
     *
     * <p>端到端用例用它断言「熔断打开后不再为新方法建 breaker」，
     * 即<b>熔断确实生效、且短路真的不触达下游</b>。
     * 同 {@link #setRandomSource(DoubleSupplier)}：跨包证据链需要 public。
     */
    public int trackedMethods() {
        return breakers.size();
    }

    /** 测试用：取某方法的 breaker（**按方法名**，不含服务前缀 ⇒ 只在单服务场景下有意义）。 */
    GoStyleBreaker breakerFor(String fullMethodName) {
        return breakerOf(fullMethodName);
    }

    /**
     * 测试用：按<b>完整 breaker 名</b>取（服务 + 方法）。
     *
     * <p>⚠️ 跨服务场景下必须用这个 —— 否则拿到的是「没有服务前缀」的那一份，
     * 断言会假绿（§3.3.3：绿测试可能是错误答案的守卫）。
     */
    GoStyleBreaker breakerForName(String breakerName) {
        return breakerOf(breakerName);
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method,
            CallOptions callOptions,
            Channel next) {

        String fullMethodName = method.getFullMethodName();
        // ⚠️ 2026-10-25（T-9 修复）：breaker 的键必须**带 channel 身份**。
        //
        //   Go: breakerName := path.Join(cc.Target(), method)
        //       `cc.Target()` = gRPC **dial 时传入的 target 字符串**（不是解析后的 IP:Port）
        //       ⇒ 对本项目 = `resolver.BuildDiscovTarget(etcdHosts, key)` 的产物
        //         （`zrpc/resolver/target.go:20` ⇒ `etcd:///etcd:2379?key=message.rpc`）
        //       ⇒ 键里**含服务标识** ⇒ **每个下游服务一份独立熔断器**。
        //
        //   修复前本类只用 `method.getFullMethodName()`（**无 target 前缀**）
        //       ⇒ 两个不同下游服务调用**同名方法**时**共用一份熔断器**
        //       ⇒ A 服务的失败会把 B 服务的熔断器也拖开（跨服务污染）。
        //
        //   RAG 侧的对应物 = `Channel#authority()`：
        //       · `discovery:///message-rpc` ⇒ devh 的 `DiscoveryClientNameResolver`
        //         用**服务名**作 authority ⇒ "message-rpc"（与 Go 的 `key=message.rpc` 同义）
        //       · 直连地址 ⇒ "host:port"
        //   ⇒ **两者都标识「哪个服务」** ⇒ 语义与 Go 同形。
        //
        //   ⚠️ 第三个实参 `next` 确实是**底层 channel**（不是包装层）：
        //   grpc-java 的 `ClientInterceptors$InterceptorChannel.interceptCall`
        //   传的是 `this.channel` 字段（javap 实测）⇒ `next.authority()` 取得到。
        String breakerName = goBreakerName(targetOf(next), fullMethodName);

        if (!enabled) {
            return next.newCall(method, callOptions);
        }

        GoStyleBreaker breaker = breakerOf(breakerName);
        if (!breaker.accept()) {
            // googlebreaker.go:65-67：allow() 在拒绝前会 markDrop()
            breaker.markDrop();
            log.error("[grpc-breaker] dropped, circuit open, method={}, breaker={}",
                    fullMethodName, breakerName);
            // ⚠️ 抛普通异常而非 StatusRuntimeException —— 理由见 GoStyleCircuitOpenException 的类注释
            throw new GoStyleCircuitOpenException("circuit breaker is open");
        }

        return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(
                next.newCall(method, callOptions)) {

            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                super.start(new ForwardingClientCallListener.SimpleForwardingClientCallListener<RespT>(
                        responseListener) {

                    @Override
                    public void onClose(Status status, Metadata trailers) {
                        // DoWithAcceptableCtx 的 acceptable 判据：逐条对齐 codes.Acceptable
                        if (isAcceptable(status)) {
                            breaker.markSuccess();
                        } else {
                            breaker.markFailure();
                            log.error("[grpc-breaker] failure, method={}, code={}",
                                    fullMethodName, status == null ? "null" : status.getCode());
                        }
                        super.onClose(status, trailers);
                    }
                }, headers);
            }
        };
    }

    /**
     * 逐条对齐 Go {@code zrpc/internal/codes/accept.go:9-17} 的 {@code codes.Acceptable}。
     *
     * <p>⚠️ <b>这是本类唯一真正的语义判据</b>，改动前必须回 Go 侧确认。
     * 六个失败 code 一个都不能少，也<b>不能改成「非 OK 即失败」</b>（见类注释）。
     *
     * <p>{@code UNKNOWN} 归为成功，与 Go 一致：Go 的 {@code status.Code(err)} 对
     * 非 status 错误返回 {@code codes.Unknown}，而 {@code Unknown} 不在 Go 的失败名单里。
     */
    static boolean isAcceptable(Status status) {
        if (status == null) {
            return true;
        }
        return switch (status.getCode()) {
            case DEADLINE_EXCEEDED, INTERNAL, UNAVAILABLE, DATA_LOSS,
                 UNIMPLEMENTED, RESOURCE_EXHAUSTED -> false;
            default -> true;      // 含 OK / UNKNOWN / NOT_FOUND / PERMISSION_DENIED …
        };
    }
}