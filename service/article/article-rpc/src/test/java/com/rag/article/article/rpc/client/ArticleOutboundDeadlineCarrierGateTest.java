package com.rag.article.article.rpc.client;

import com.rag.article.article.proto.ArticleServiceGrpc;
import com.rag.article.article.proto.GetArticleRequest;
import com.rag.article.article.proto.GetArticleResponse;
import com.rag.article.article.rpc.support.RealGrpcPort;
import com.rag.common.observability.GoStyleRequestDeadline;
import com.rag.message.message.proto.MessageServiceGrpc;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerInterceptors;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.ServerCalls;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.yaml.snakeyaml.Yaml;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 🔴 <b>域侧守门</b>：本域出向出口（{@code MessageRpcClient} → {@code message-rpc}）的
 * <b>2s 客户端封顶真的生效</b>，且计算精度是<b>纳秒</b>（不是毫秒）。
 *
 *
 * <h2>🔴 2026-10-31 第十一轮 · 判据主体换成<b>共享层的全局客户端拦截器</b></h2>
 * <p>本类此前测的是<b>域内私有副本</b> {@code GoStyleClientCallTimeout}
 * （{@code capByClientTimeout(applyTo(stub))}）的算术 ⇒ ⇒ 本轮那份副本<b>已删除</b>
 * （<b>副本数 = 漂移面</b>：每份副本各自实现一遍「取 min」语义 ⇒ 极易有的取 min、
 * 有的写成覆盖 ⇒ ⇒ 共享层守门 {@code GoStyleClientCallTimeoutSingleSourceGateTest}
 * 的终态口径 = 「域侧 0 份，只配参数」）。
 * <br>⇒ ⇒ 本类现在测的是<b>生产同款</b>的施加上界的那一层：
 * <ul>
 *   <li><b>共享层</b> {@code GoStyleOutboundDeadlineInterceptor}
 *       （devh 的 {@code @GrpcGlobalClientInterceptor} ⇒ 装在<b>每个</b>出向 devh 调用上
 *       ⇒ ⇒ 连<b>裸 stub</b> 都过链）⇒ 上界 =
 *       {@code min(入向剩余预算, 客户端硬上界, 调用点自带)}；</li>
 *   <li><b>本域</b> 只配参数：{@code rag.protection.outbound-deadline.cap-millis=2000}
 *       （共享层默认 {@code capMillis=0} = <b>不启用</b> ⇒ ⇒ 不配 = 出向零期限 = 比 Go 松）
 *       ⇒ ⇒ 「配置面」那条用例把<b>配置面</b>也钉住（直接读本域 {@code application.yml}）。</li>
 * </ul>
 * <h2>🔴 2026-10-31 第十二轮复核：Go 的这个超时是<b>按 client</b> 生效的</h2>
 * <p>Go 的 {@code zrpc.RpcClientConf.Timeout}（{@code zrpc/config.go:31} 声明 ＋
 * {@code zrpc/client.go:70-71} 消费）挂在<b>每个 client 各自的</b>配置上
 * ⇒ ⇒ 本域<b>两个</b>出向客户端真值<b>不同</b>（{@code MessageRpc} ⇒ default <b>2000</b>；
 * {@code SecurityConf} ⇒ 显式 <b>10000</b>）
 * ⇒ ⇒ 而共享层那个 {@code cap-millis} 是<b>模块级一个数</b> ⇒ ⇒ 装哪个值都会有一侧与 Go 不同
 * ⇒ ⇒ 本域取 <b>2000</b>（= <b>本域唯一活着的出向出口</b>那个方向 ⇒ <b>活路径与 Go 精确同形</b>；
 * 另一个方向当前<b>无真实调用</b> ⇒ <b>不可观测</b>）
 * ⇒ ⇒ ⚠️ **「模块级一个数表达不了 Go 的 per-client 语义」= 共享层的模型缺口** ⇒ 归 <b>G4 写权</b>
 * ⇒ ⇒ 本域<b>不擅自</b>用调用点自带 cap 绕开（共享层棘轮
 * {@code GoStyleClientCallTimeoutSingleSourceGateTest} 判据 ④ <b>明令禁止</b>域侧自带 cap）
 * ⇒ ⇒ 本轮另加一条<b>放宽方向</b>的变异用例 ⇒ 证明这个上界<b>是配置驱动</b>、不是写死 2000。
 *
 * <h2>🔴 为什么必须新写这一条（2026-10-31 · G3 域侧自修）</h2>
 * <p>本域此前的出向出口是<b>裸 stub 调用</b>（{@code stub.sendNotification(req.build())}）
 * ⇒ ⇒ <b>零期限</b>：既无 2s 客户端封顶，又因跑在 Kafka consumer 线程上、
 * 共享层 {@code RpcInterceptor.onHalfClose} 绑的 ThreadLocal 载体<b>不可见</b>
 * ⇒ ⇒ 连「继承入向 deadline」也不成立
 * ⇒ ⇒ 而 Go 侧同款出口<b>有</b> 2000ms 客户端封顶 ⇒ ⇒ <b>可观察行为分叉</b>
 * （下游 {@code message-rpc} 挂死时 Go 2s 返回、RAG 无限期阻塞 ⇒ 消费线程被占死）。
 * <br>⇒ 本类补上这条缺失的运行证据（修前只有 <b>L2 源码级</b>）。
 *
 * <h2>🔴 本类证的是「<b>机制</b>」，不是「生产链路的最终数值」</h2>
 * <p>⚠️ <b>必须说清楚，否则会误读</b>：本域唯一的出向出口跑在 <b>Kafka consumer 线程</b>上
 * ⇒ ⇒ 生产上<b>载体不可见</b> ⇒ ⇒ 净效果 = <b>恰好 2s 封顶</b>
 * ⇒ ⇒ <b>对应本类第 ⑤ 条</b>（无载体 ⇒ 仍套 2s）。
 * <br>⇒ 而第 ①②③④ 条证的是<b>同一个计算点的机制</b>：
 * 「载体可见时 ⇒ 出向上界 = min(载体剩余, 2s)」⇒ ⇒ 这是<b>同一段代码</b>的另一半，
 * 用来证明「这个计算点本身是对的」（否则 ⑤ 条单独存在时，一个「写死 2s、完全不看载体」
 * 的实现也能过 ⑤ ⇒ ⇒ 那样 ⑤ 就成了<b>假绿</b>）。
 *
 * <h2>🔴 八条用例的分工（每条各有<b>专属</b>判别维度 · 缺一条都不够）</h2>
 * <ol>
 *   <li><b>真 wire + 真共享层拦截器</b> ⇒ 证明<b>载体在 handler 线程真的绑上了</b>
 *       （把证据从 L2 源码级升到 <b>L4 活体</b>）⇒ 且证明客户端的 deadline
 *       <b>真的穿过 wire 到了服务端</b>；</li>
 *   <li><b>入向预算 &gt; 2s</b> ⇒ 证明 <b>2s 客户端封顶真的生效</b>
 *       （这一条专治「不压任何 deadline」的实现）；</li>
 *   <li><b>入向预算 &lt; 2s</b> ⇒ 证明出向上界<b>真的继承了入向剩余</b>
 *       （这一条专治「无脑套 2s、根本不继承」的实现）；</li>
 *   <li><b>已知绝对纳秒</b> ⇒ 证明<b>域内出向计算点不截断亚毫秒</b>
 *       （这一条<b>可确定性分辨</b>「毫秒版」与「纳秒版」）；</li>
 *   <li><b>无载体</b> ⇒ 证明 2s 客户端封顶<b>无条件</b>在
 *       （对齐 Go 侧拦截器级别的 {@code TimeoutInterceptor}）
 *       ⇒ ⇒ <b>这一条才是本域生产出口真正落的那一档</b>；</li>
 *   <li><b>变异（放宽方向）</b>（第十二轮新增）⇒ 把 {@code cap-millis} <b>放宽</b>到 4s
 *       ⇒ 实测上界<b>必须跟着放宽</b>
 *       ⇒ ⇒ 专治「这个上界是<b>写死</b>的」假实现
 *       （⚠️ <b>调小</b>方向对它<b>结构性不可见</b> ⇒ 必须放宽）；</li>
 * </ol>
 *
 * <h2>🔴 为什么必须拆成「长预算」与「短预算」两个场景</h2>
 * <p>入向预算<b>短于</b> 2s 时，「继承入向预算」与「原样返回（不压任何 deadline）」
 * 算出来的上界<b>完全相同</b> ⇒ ⇒ 单一「短预算」场景<b>分辨不了</b>这两种实现
 * ⇒ ⇒ 必须再加一个「长于 2s」的场景（正确实现 = 2s，不压的实现 = 入向预算）才可分辨。
 *
 * <h2>🔴 为什么精度那条能<b>确定性</b>分辨毫秒版与纳秒版</h2>
 * <p><b>wire 的 deadline 精度是毫秒</b>（grpc 协议的 {@code grpc-timeout} 单位就是毫秒）
 * ⇒ ⇒ 服务端读到的入向 deadline <b>天然毫秒对齐</b>
 * ⇒ ⇒ 用它来测「亚毫秒有没有被截断」⇒ 截断量<b>随机落在 [0,1)ms</b>
 * ⇒ ⇒ 阈值判据<b>必然</b>时红时绿 ⇒ <b>不能</b>当守门。
 * <br>⇒ 因此精度那条用<b>手动绑定一个已知的绝对纳秒时刻</b>（{@code now + 10.9ms}）：
 * <ul>
 *   <li><b>纳秒版</b>（本类要求）：上界 = {@code min(10.9ms, 2s)} = 10.9ms ⇒ 精度损失 ≈ μs 级；</li>
 *   <li><b>毫秒版</b>（旧实现）：{@code remainingMillis()} = {@code toMillis(10.9ms)} = <b>10ms</b>
 *       ⇒ 上界 = 10ms ⇒ 精度损失 ≈ <b>0.9ms</b> ⇒ 必然超出 0.4ms 判据 ⇒ <b>必然红</b>。</li>
 * </ul>
 *
 * <h2>🔴 为什么必须<b>热身</b>（第二处「看起来像精度损失」的坑）</h2>
 * <p>整条路径第一次跑要付<b>类加载 + JIT</b> 的钱（本机实测 ≈ <b>0.15ms</b>）
 * ⇒ ⇒ 那笔钱会<b>混进</b>「出向相对载体窄了多少」⇒ ⇒ 不热身就会读到
 * 「loss ≈ 0.15ms」而<b>误判成精度损失</b> ⇒ ⇒ 本类先跑一遍<b>同款路径</b>热身再测。
 *
 * <h2>Go 出处（行为层，逐条对应）</h2>
 * <ul>
 *   <li><b>出向取 min</b>：{@code go-zero@v1.10.2/zrpc/internal/clientinterceptors/timeoutinterceptor.go:25}
 *       {@code ctx, cancel := context.WithTimeout(ctx, t)} ⇒ 父 ctx 更早则<b>先到者生效</b>；</li>
 *   <li><b>纳秒精度</b>：同上一行 ⇒ {@code t} 是 <b>纳秒</b> ⇒ Go 侧<b>不</b>做毫秒截断；</li>
 *   <li><b>无条件 2s</b>：{@code go-zero@v1.10.2/zrpc/internal/client.go:113-117}（装拦截器）
 *       ＋ {@code go-zero@v1.10.2/zrpc/internal/config.go:15}（{@code Timeout bool json:",default=true"}）
 *       ＋ {@code go-zero@v1.10.2/zrpc/config.go:31}（{@code Timeout int64 json:",default=2000"}）
 *       ⇒ ⚠️ 本域 {@code service/article/rpc/etc/article.yaml} 的 {@code MessageRpc} 段
 *       <b>没写 Timeout</b> ⇒ 取的就是这个 default ⇒ <b>2000</b>；</li>
 *   <li><b>调用点</b>：{@code service/article/rpc/internal/mqs/article_sync_result_consumer.go:197}
 *       {@code l.svcCtx.MessageRpc.SendNotification(ctx, …)} ⇒ ctx 是 <b>Kafka 消费 ctx（无 deadline）</b>
 *       ⇒ ⇒ Go 侧这条出口<b>也只有 2s 封顶</b> ⇒ ⇒ 与本域生产形态<b>同形</b>。</li>
 * </ul>
 *
 * <h2>🔴 为什么第一条用<b>真 socket</b> 而不是 in-process</h2>
 * <p>in-process transport <b>不走 wire</b> ⇒ 没有 {@code grpc-timeout} 头
 * ⇒ ⇒ 服务端读到的 deadline 是 <b>null</b> ⇒ 载体<b>根本不会绑</b>
 * ⇒ ⇒ 用 in-process 写这条守门 ⇒ <b>必然红</b>（假红）。
 * <br>⇒ 本类用 {@link RealGrpcPort#pick()}（本域专用段 22400–22479）起<b>真 socket</b>。
 *
 * <h2>🔴 为什么 handler 与 {@code onHalfClose} <b>同线程</b>（载体可见的前提）</h2>
 * <p>载体是 <b>ThreadLocal</b>（{@code GoStyleRequestDeadline.java:64}）
 * ⇒ 只在<b>绑它的那个线程</b>可见 ⇒ 共享层在 {@code RpcInterceptor.onHalfClose}
 * （{@code RpcInterceptor.java:494}）里绑定 ⇒ ⇒ handler 必须<b>跑在同一个线程</b>上。
 * <br>⇒ 链路：grpc-java 的 {@code ServerBuilder} <b>默认 executor = directExecutor</b>
 * ⇒ {@code ServerCalls.asyncUnaryCall} 最终用 {@code call.getExecutor()}
 * ⇒ ⇒ 本类<b>不</b>覆写 executor ⇒ 与生产形态<b>同款</b>（同线程）。
 *
 * <h2>🔴 范式来源</h2>
 * <p>本类<b>照抄</b>本仓 comment 域的同款守门
 * （{@code service/comment/comment-rpc/.../client/CommentOutboundDeadlineCarrierGateTest.java}，
 * 5 例 + 变异矩阵已验证判别力）⇒ ⇒ <b>不发明第三种写法</b>（AGENTS.md §3.2）。
 */
class ArticleOutboundDeadlineCarrierGateTest {

    /** 客户端给的 deadline（毫秒）—— 必须<b>穿过 wire</b>，才能证明服务端真的收到了。 */
    private static final long CLIENT_DEADLINE_MILLIS = 1_500L;

    /**
     * 另一个场景：客户端给的 deadline <b>长于</b> 2s 客户端封顶（5s）。
     *
     * <p>⚠️ <b>为什么必须有这个场景</b>：入向预算<b>短于</b> 2s 时，「继承入向预算」与
     * 「原样返回（不压任何 deadline）」算出来的上界<b>完全相同</b>
     * ⇒ ⇒ 单一「短预算」场景<b>分辨不了</b>这两种实现 ⇒ 必须再加一个「长于 2s」的场景：
     * 此时正确实现的上界是 <b>2s</b>，而「不压」的实现是 <b>5s</b> ⇒ ⇒ 两者可分辨。
     */
    private static final long LONG_CLIENT_DEADLINE_MILLIS = 5_000L;

    /**
     * 精度那条用例里手动绑定的剩余预算：<b>10.9ms</b>（刻意取非整毫秒 ⇒ 亚毫秒部分 = 0.9ms）。
     *
     * <p>⚠️ 亚毫秒部分要<b>足够大</b>才能与测量开销拉开距离
     * ⇒ 毫秒版（读 {@code remainingMillis()}）会把 10.9ms 砍成 <b>10ms</b> ⇒ 精度损失 ≈ 0.9ms
     * ⇒ 而纳秒版损失 ≈ 几 μs ⇒ ⇒ 两者相差两个数量级 ⇒ 判据稳。
     */
    private static final long KNOWN_BUDGET_NANOS = 10_900_000L;

    /**
     * 精度损失的判据上界（纳秒）：<b>0.4ms</b>。
     *
     * <p>⚠️ <b>为什么测「相对损失」而不是「绝对剩余值」</b>：本机实测「两次 {@code nanoTime()}
     * 调用之间」的机器开销约 <b>0.3–0.5ms</b>（类加载 + JIT）⇒ ⇒ 绝对剩余值里混着这笔开销
     * ⇒ ⇒ 用绝对值当判据会**时红时绿**（假信号）⇒ 必须拿「载体当下的剩余」当基线，
     * 只测「出向比它窄了多少」。
     */
    private static final long PRECISION_LOSS_TOLERANCE_NANOS = 400_000L;

    private static Server server;
    private static ManagedChannel channel;

    /** handler 内观测到的载体状态（跨线程传递 ⇒ 必须用引用类型）。 */
    private static final AtomicReference<Boolean> carrierPresent = new AtomicReference<>();
    private static final AtomicReference<Long> carrierRemainingNanos = new AtomicReference<>();
    private static final AtomicReference<Long> outboundRemainingNanos = new AtomicReference<>();

    /**
     * 本域配置的客户端硬上界（毫秒）—— <b>必须等于 Go 侧的 {@code zrpc} client Timeout</b>。
     *
     * <p>⚠️ <b>这条常量与本域 {@code application.yml} 的
     * {@code rag.protection.outbound-deadline.cap-millis} 是同一个数的两处写法</b>
     * ⇒ ⇒ 「配置面」那条用例专门直接读 yml ⇒ ⇒ 两者不会悄悄分叉。
     */
    private static final long CONFIGURED_CAP_MILLIS = 2_000L;

    /**
     * 共享层的出向 deadline 拦截器（<b>生产同款类</b>）。
     *
     * <p>⚠️ <b>为什么这里直接 new 而不是从 Spring 上下文里取</b>：本类要测的是
     * <b>「这个拦截器 + 这条配置值 ⇒ 算出什么上界」</b> ⇒ ⇒ 用<b>同一个类</b> +
     * <b>同一个配置键的值</b>构造即可 ⇒ ⇒ 而「它真的被装配进 devh 的 registry 并装到
     * 每个 channel 上」那半由<b>共享层</b>的守门负责
     * （{@code GoStyleOutboundDeadlineRpcAssemblyTest} + 主干
     * {@code GoStyleDevhOutboundDeadlineAssemblyGateTest}）⇒ ⇒ 两边合起来才是完整证据链。
     */
    private static com.rag.common.protection.GoStyleOutboundDeadlineInterceptor sharedInterceptor;

    @BeforeAll
    static void startServer() throws Exception {
        int port = RealGrpcPort.pick();

        // 🔴 共享层的出向拦截器：**用本域配置的那个值**（2000ms）构造
        //    ⇒ ⇒ 「域侧只配参数」这条纪律在测试里也成立（不是另写一份算术）。
        com.rag.common.protection.GoStyleProtectionProperties props = new com.rag.common.protection.GoStyleProtectionProperties();
        props.getOutboundDeadline().setCapMillis(CONFIGURED_CAP_MILLIS);
        sharedInterceptor = new com.rag.common.protection.GoStyleOutboundDeadlineInterceptor(props);

        // ⚠️ 这里**只给 service name**（不给 `getServiceDescriptor()`）：
        //    带 descriptor 构造 ⇒ grpc-java 会要求「descriptor 里的每个方法都必须被绑定」
        //    ⇒ 而本类只关心 deadline 载体 ⇒ 只绑 `GetArticle` 一个方法。
        ServerServiceDefinition service = ServerServiceDefinition.builder(
                        ArticleServiceGrpc.getServiceDescriptor().getName())
                .addMethod(ArticleServiceGrpc.getGetArticleMethod(),
                        ServerCalls.asyncUnaryCall((GetArticleRequest request,
                                                   io.grpc.stub.StreamObserver<GetArticleResponse> observer) -> {
                            // 🔴 下面三行跑在 **gRPC handler 线程** ⇒ 共享层 `RpcInterceptor.onHalfClose`
                            //    在**同一线程**上绑的 ThreadLocal 载体**在这里可见**。
                            carrierPresent.set(GoStyleRequestDeadline.isPresent());
                            carrierRemainingNanos.set(GoStyleRequestDeadline.remainingNanos());

                            // 域内真实消费点：**共享层拦截器算出来的那个出向上界**
                            //（生产同款：devh 把 `@GrpcGlobalClientInterceptor` 装到**每个**
                            //  出向 channel 上 ⇒ 连裸 stub 都过链 ⇒ 域侧调用点不再自己套 deadline）
                            // ⇒ 这里用**同一个拦截器** + 一个「只记 CallOptions、不真发」的假 channel
                            //    取它的产物（真发会打回本服务 ⇒ 递归）。
                            io.grpc.Deadline deadline = sharedOutboundDeadline();
                            outboundRemainingNanos.set(deadline == null
                                    ? -1L
                                    : deadline.timeRemaining(TimeUnit.NANOSECONDS));

                            observer.onNext(GetArticleResponse.getDefaultInstance());
                            observer.onCompleted();
                        }))
                .build();

        // 🔴 真装共享层的拦截器：`Duration.ZERO` = 服务端**不配**自己的预算
        //    ⇒ 生效预算 = min(无, 客户端 deadline) = **客户端 deadline**
        //    ⇒ 与本仓生产的 rpc 组件形态一致（本域未配 `grpc.server.timeout`）。
        ServerServiceDefinition intercepted = ServerInterceptors.intercept(service,
                com.rag.common.observability.RpcInterceptor.newUnaryServerInterceptor(Duration.ZERO, Duration.ofSeconds(1)));

        server = ServerBuilder.forPort(port).addService(intercepted).build().start();
        // 🔴 **必须用显式 `dns:///` scheme，不能用 `forAddress(...)`**（2026-10-31 实测踩坑）：
        //    devh 的 `GrpcClientAutoConfiguration:124` 会在**起 Spring 上下文**时
        //    把自己的 `discovery` NameResolverProvider 注册进 **grpc-java 的进程级全局**
        //    `NameResolverRegistry.getDefaultRegistry()` ⇒ ⇒ **一旦本 JVM 里起过一次 devh 上下文，
        //    该注册就是进程级的** ⇒ ⇒ 之后**任何**无显式 scheme 的手搓 channel
        //    （`ManagedChannelBuilder.forAddress("127.0.0.1", P)`）都会被 devh 的 discovery resolver
        //    **接管** ⇒ ⇒ 把 `127.0.0.1:P` 当**服务名**去 Nacos 查 ⇒ ⇒ `UNAVAILABLE`
        //    ⇒ ⇒ 实测表现：同一个模块里，某个 `@SpringBootTest` 先跑 ⇒ 本条就红；
        //    单独跑本类 ⇒ 5/5 全绿 ⇒ ⇒ **「单跑绿、同跑红」＝ 上下文污染，不是判据问题**。
        //    ⇒ 显式 `dns:///` 直接选定 grpc-java 自带的 DNS resolver ⇒ 不受 devh 注册影响。
        channel = ManagedChannelBuilder.forTarget("dns:///127.0.0.1:" + port).usePlaintext().build();
    }


    /**
     * 取「<b>共享层拦截器</b>算出来的那个出向 deadline」—— 生产同款。
     *
     * <p>⚠️ <b>为什么用假 channel 而不是真发一次</b>：拦截器在 {@code interceptCall} 里就把
     * deadline 算好并交给 {@code next.newCall(...)} ⇒ ⇒ 只要 {@code next} 是「只记不真发」，
     * 就能取到<b>真正落到 wire 的那个</b> {@code CallOptions}
     * ⇒ ⇒ 既不用起第二个服务、也不会自递归。
     */
    private static io.grpc.Deadline sharedOutboundDeadline() {
        return sharedOutboundDeadline(sharedInterceptor);
    }

    /**
     * 同上，但允许换掉拦截器（供「放宽方向」的变异用例用）。
     *
     * @param interceptor 共享层出向拦截器（**生产同款类**；用不同参数构造 ⇒ 读数随参数变）
     */
    private static io.grpc.Deadline sharedOutboundDeadline(com.rag.common.protection.GoStyleOutboundDeadlineInterceptor interceptor) {
        RecordingChannel recorder = new RecordingChannel();
        interceptor.interceptCall(MessageServiceGrpc.getSendNotificationMethod(), CallOptions.DEFAULT, recorder);
        CallOptions captured = recorder.captured();
        return captured == null ? null : captured.getDeadline();
    }

    /**
     * 只记 {@code CallOptions} 的假 channel（取拦截器产物用）。
     *
     * <p>⚠️ {@code Channel#newCall} 的 {@code next} 语义：拦截器算完 deadline 后
     * 调 {@code next.newCall(method, effectiveOptions)} ⇒ ⇒ 这里收到的就是
     * <b>真正会落到 wire 的那份 options</b>。
     */
    private static final class RecordingChannel extends Channel {

        private final AtomicReference<CallOptions> captured = new AtomicReference<>();

        @Override
        public <ReqT, RespT> ClientCall<ReqT, RespT> newCall(
                MethodDescriptor<ReqT, RespT> methodDescriptor, CallOptions callOptions) {
            captured.set(callOptions);
            return new ClientCall<ReqT, RespT>() {
                @Override
                public void start(Listener<RespT> responseListener, Metadata headers) {
                    // 本守门只取 CallOptions ⇒ 不真发 ⇒ 什么都不做
                }

                @Override
                public void request(int numMessages) {
                }

                @Override
                public void cancel(String message, Throwable cause) {
                }

                @Override
                public void halfClose() {
                }

                @Override
                public void sendMessage(ReqT message) {
                }
            };
        }

        @Override
        public String authority() {
            return "recording-channel";
        }

        CallOptions captured() {
            return captured.get();
        }
    }

    @AfterAll
    static void stopServer() throws Exception {
        if (channel != null) {
            channel.shutdownNow();
            channel.awaitTermination(5, TimeUnit.SECONDS);
        }
        if (server != null) {
            server.shutdownNow();
            server.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    // ====================================================================================
    // ① 真 wire + 真共享层拦截器 ⇒ 载体在 handler 线程真的绑上了（L4 活体）
    //    ⚠️ 拆成**三个场景**，因为单场景判别力不足（详见 LONG_CLIENT_DEADLINE_MILLIS 的注释）：
    //       ①a 载体可见       ⇒ 证「共享层在 handler 线程真的绑了」
    //       ①b 长预算 → 2s   ⇒ 证「2s 客户端封顶真的生效」
    //       ①c 短预算 → 继承 ⇒ 证「出向上界真的继承了入向剩余，而不是无脑套 2s」
    // ====================================================================================

    /** 一次「客户端带 deadline 调 → 读 handler 侧观测」的结果。 */
    private static final class Observation {
        final boolean carrierPresent;
        final long carrierRemainingNanos;
        final long outboundRemainingNanos;
        final Status.Code statusCode;

        Observation(boolean carrierPresent, long carrierRemainingNanos,
                    long outboundRemainingNanos, Status.Code statusCode) {
            this.carrierPresent = carrierPresent;
            this.carrierRemainingNanos = carrierRemainingNanos;
            this.outboundRemainingNanos = outboundRemainingNanos;
            this.statusCode = statusCode;
        }
    }

    /** 客户端带 deadline 调一次 ⇒ 返回 handler 线程上的观测。 */
    private static Observation callAndObserve(long clientDeadlineMillis) {
        carrierPresent.set(null);
        carrierRemainingNanos.set(null);
        outboundRemainingNanos.set(null);

        ArticleServiceGrpc.ArticleServiceBlockingStub stub =
                ArticleServiceGrpc.newBlockingStub(channel)
                        .withDeadlineAfter(clientDeadlineMillis, TimeUnit.MILLISECONDS);

        Status.Code code = Status.Code.OK;
        try {
            stub.getArticle(GetArticleRequest.getDefaultInstance());
        } catch (StatusRuntimeException e) {
            code = e.getStatus().getCode();
        }

        return new Observation(
                Boolean.TRUE.equals(carrierPresent.get()),
                carrierRemainingNanos.get() == null ? -1L : carrierRemainingNanos.get(),
                outboundRemainingNanos.get() == null ? -1L : outboundRemainingNanos.get(),
                code);
    }

    @Test
    @DisplayName("🔴 真 wire + 真共享层拦截器 ⇒ 出向载体在 gRPC handler 线程真的绑上，且客户端的 deadline 真的到了服务端")
    void carrierIsReallyBoundOnHandlerThreadOverRealWire() {
        Observation o = callAndObserve(CLIENT_DEADLINE_MILLIS);

        assertThat(o.statusCode)
                .as("客户端调用必须成功返回（服务端没有超时调度器，超时只可能来自客户端自己的 deadline）")
                .isEqualTo(Status.Code.OK);

        assertThat(o.carrierPresent)
                .as("🔴 共享层 `RpcInterceptor.onHalfClose` 绑的载体必须在 **gRPC handler 线程**上可见"
                        + "⇒ 否则「继承入向 deadline」这一半在机制上就不成立")
                .isTrue();

        assertThat(o.carrierRemainingNanos)
                .as("载体剩余预算必须是**纳秒**且 > 0（-1 = 载体在位但没有预算 ⇒ 异常）")
                .isPositive();

        assertThat(o.carrierRemainingNanos)
                .as("🔴 客户端给的 %dms 必须**穿过 wire** 到了服务端"
                        + "（若服务端读到的是毫秒级 1500 或更少，说明 grpc-timeout 没传过来 ⇒ 载体绑了个空预算）",
                        CLIENT_DEADLINE_MILLIS)
                .isGreaterThan(TimeUnit.MILLISECONDS.toNanos(CLIENT_DEADLINE_MILLIS) - 300_000_000L);
    }

    @Test
    @DisplayName("🔴 入向预算**长于** 2s ⇒ 出向上界被压到 2s（客户端封顶真的生效，不是原样返回）")
    void outboundIsCappedAtTwoSecondsWhenInboundBudgetIsLonger() {
        Observation o = callAndObserve(LONG_CLIENT_DEADLINE_MILLIS);

        assertThat(o.statusCode)
                .as("客户端调用必须成功返回")
                .isEqualTo(Status.Code.OK);
        assertThat(o.carrierPresent)
                .as("载体必须在 handler 线程可见")
                .isTrue();
        assertThat(o.carrierRemainingNanos)
                .as("载体剩余应 ≈ %dms（客户端给了 > 2s 的预算 ⇒ 载体上界是它、不是 2s）",
                        LONG_CLIENT_DEADLINE_MILLIS)
                .isGreaterThan(TimeUnit.MILLISECONDS.toNanos(LONG_CLIENT_DEADLINE_MILLIS) - 300_000_000L);

        long twoSeconds = TimeUnit.MILLISECONDS.toNanos(CONFIGURED_CAP_MILLIS);
        assertThat(o.outboundRemainingNanos)
                .as("🔴 入向预算（%dms）> 2s ⇒ 出向上界必须是 **2s 客户端封顶**"
                        + "（若等于入向预算 ⇒ 说明「根本没压 2s」）", LONG_CLIENT_DEADLINE_MILLIS)
                .isLessThanOrEqualTo(twoSeconds);
        assertThat(o.outboundRemainingNanos)
                .as("出向上界必须贴近 2s（不能是别的数）")
                .isGreaterThan(twoSeconds - 100_000_000L);
    }

    @Test
    @DisplayName("🔴 入向预算**短于** 2s ⇒ 出向上界继承入向剩余（不是无脑套 2s）")
    void outboundInheritsInboundBudgetWhenItIsShorter() {
        Observation o = callAndObserve(CLIENT_DEADLINE_MILLIS);

        assertThat(o.outboundRemainingNanos)
                .as("🔴 域内出向出口的上界必须**继承**了载体预算（不是无脑套 2s）")
                .isPositive();
        assertThat(o.outboundRemainingNanos)
                .as("出向上界 = min(载体剩余, 2s) ⇒ 必须**不宽于**载体剩余")
                .isLessThanOrEqualTo(o.carrierRemainingNanos);
        assertThat(o.outboundRemainingNanos)
                .as("🔴 入向预算只有 %dms ⇒ 出向上界必须**明显小于 2s**（否则就是「无脑套 2s」）",
                        CLIENT_DEADLINE_MILLIS)
                .isLessThan(TimeUnit.MILLISECONDS.toNanos(2_000L));
    }

    // ====================================================================================
    // ② 纳秒精度：域内出向计算点**不截断**亚毫秒（可确定性分辨毫秒版）
    // ====================================================================================

    @Test
    @DisplayName("🔴 域内出向计算点按**纳秒**取预算（毫秒版会把 10.9ms 砍成 10ms ⇒ 本条转红）")
    void outboundBudgetKeepsNanosecondPrecision() {
        long carrierNow;
        long outbound;
        long budgetNanos = KNOWN_BUDGET_NANOS;

        // 🔴🔴 **必须先热身**：整条路径（`bindAbsoluteNanos` → 拦截器读载体 →
        //    `Deadline.after` → `timeRemaining`）第一次跑要付
        //    **类加载 + JIT** 的钱（本机实测 ≈ 0.15ms）⇒ ⇒ 那笔钱会**混进 loss**
        //    ⇒ ⇒ 不热身就会读到「loss ≈ 0.15ms」而误判成精度损失。
        //    ⇒ 热身跑的是**同一条路径**（同样在 scope 内、同样带载体）。
        try (GoStyleRequestDeadline.Scope ignored = GoStyleRequestDeadline.bindAbsoluteNanos(
                System.nanoTime() + budgetNanos)) {
            sharedOutboundDeadline();
        }

        try (GoStyleRequestDeadline.Scope ignored = GoStyleRequestDeadline.bindAbsoluteNanos(
                System.nanoTime() + budgetNanos)) {
            // 🔴 **基线**：此刻载体自己还剩多少（在同一线程、同一载体上直接读）。
            //    之所以要拿它当基线，是因为「两次 nanoTime 调用之间的机器开销」
            //    （本机实测约 0.3–0.5ms：类加载 + JIT）会**混进**绝对值里
            //    ⇒ ⇒ 判据必须测**相对损失**（出向比载体窄了多少），
            //    而不是测绝对剩余值（那会把测量开销误读成精度损失）。
            carrierNow = GoStyleRequestDeadline.remainingNanos();

            io.grpc.Deadline deadline = sharedOutboundDeadline();
            outbound = deadline == null ? -1L : deadline.timeRemaining(TimeUnit.NANOSECONDS);
        }

        assertThat(outbound)
                    .as("出向必须拿到 deadline（无 deadline = 出向完全没上限 ⇒ 与 Go 相反）")
                    .isPositive();
            assertThat(outbound)
                    .as("出向上界**不得比载体预算更宽**（Go 侧是 min(父 ctx 剩余, 2s)）")
                    .isLessThanOrEqualTo(carrierNow);

            long loss = carrierNow - outbound;
            assertThat(loss)
                    .as("🔴 出向上界相对载体预算的**精度损失**必须 < %d ns（0.4ms）"
                            + "；预算 %d ns（10.9ms）的亚毫秒部分若被毫秒截断 ⇒ 损失 ≈ %d ns"
                            + " ⇒ 旧实现（读 remainingMillis）会转红",
                            PRECISION_LOSS_TOLERANCE_NANOS, budgetNanos, 900_000L)
                    .isLessThan(PRECISION_LOSS_TOLERANCE_NANOS);
    }

    // ====================================================================================
    // ③ 无载体 ⇒ 2s 客户端封顶仍然无条件在（对齐 Go 的拦截器级别 TimeoutInterceptor）
    //    ⚠️ 这一条才是**本域生产出口真正落的那一档**（Kafka consumer 线程 ⇒ 载体不可见）
    // ====================================================================================

    @Test
    @DisplayName("🔴 线程上没有载体 ⇒ 出向仍套 2s 客户端封顶（Go 侧 TimeoutInterceptor 是拦截器级别的）")
    void noCarrierStillGetsTwoSecondClientCap() {
        assertThat(GoStyleRequestDeadline.isPresent())
                    .as("本用例的前提：当前线程**没有**载体（否则测的就不是「无预算」这条路径）"
                            + "；本域生产出口正是在 Kafka consumer 线程上 ⇒ 落的就是这一档")
                    .isFalse();

        io.grpc.Deadline deadline = sharedOutboundDeadline();

    assertThat(deadline)
                .as("🔴 没有请求预算时**也必须**有 deadline（否则出向调用完全没有上限 ⇒ 与 Go 相反）")
                .isNotNull();

        long remaining = deadline.timeRemaining(TimeUnit.NANOSECONDS);
        long twoSeconds = TimeUnit.MILLISECONDS.toNanos(CONFIGURED_CAP_MILLIS);
        assertThat(remaining)
                .as("出向上界必须是 2s 封顶（%d ns）", twoSeconds)
                .isLessThanOrEqualTo(twoSeconds);
        assertThat(remaining)
                .as("出向上界必须贴近 2s（不能是别的数）")
                .isGreaterThan(twoSeconds - 100_000_000L);
    }

    // ====================================================================================
    // ④ 配置面：域侧只配参数（共享层默认 0 = 不启用 ⇒ 不配 = 出向零期限 = 比 Go 松）
    // ====================================================================================

    @Test
    @DisplayName("🔴 本域 yml 配了 `rag.protection.outbound-deadline.cap-millis=2000`（不配 = 共享层机制没开）")
    @SuppressWarnings("unchecked")
    void domainConfiguresTheSharedTwoSecondCap() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/application.yml")) {
            assertThat(in)
                    .as("本域 `application.yml` 必须在测试类路径上（src/main/resources）")
                    .isNotNull();

            Map<String, Object> root = new Yaml().load(in);
            assertThat(root).as("`application.yml` 必须能解析成 Map").isNotNull();

            Map<String, Object> rag = (Map<String, Object>) root.get("rag");
            assertThat(rag).as("🔴 `rag:` 段必须存在").isNotNull();

            Map<String, Object> protection = (Map<String, Object>) rag.get("protection");
            assertThat(protection).as("🔴 `rag.protection:` 段必须存在（共享层属性前缀）").isNotNull();

            Map<String, Object> outboundDeadline = (Map<String, Object>) protection.get("outbound-deadline");
            assertThat(outboundDeadline).as("🔴 `rag.protection.outbound-deadline:` 段必须存在").isNotNull();

            assertThat(outboundDeadline.get("cap-millis"))
                    .as("🔴 客户端硬上界必须是 **2000**（Go 侧 `zrpc/config.go:31` 的 "
                            + "`Timeout int64 json:\",default=2000\"`；本域 Go 侧 `etc/article.yaml` 的 `MessageRpc:` 段**没写** `Timeout` ⇒ 走这个默认值）"
                            + "；⚠️ 共享层默认 `capMillis=0` = 不启用 ⇒ 漏配 = 出向零期限 = 比 Go 松")
                    .isEqualTo(2000);
        }
    }

    // ====================================================================================
    // ⑤ 无副本：域侧不得再有第二份「客户端硬上界」实现（副本数 = 漂移面）
    // ====================================================================================

    @Test
    @DisplayName("🔴 本域不得再有「客户端硬上界」的私有副本（终态 = 域侧 0 份，只配参数）")
    void noDomainSidePrivateCopyOfTheClientCap() {
        Path copy = Path.of("service/article/article-rpc/src/main/java/com/rag/article/article/rpc/client/GoStyleClientCallTimeout.java");

        assertThat(Files.exists(copy))
                .as("🔴 域内又出现了 `GoStyleClientCallTimeout` 私有副本 ⇒ ⇒ **副本数 = 漂移面** ⇒ ⇒ "
                        + "正确形态 = **共享层给唯一实现**"
                        + "（`service/common/src/main/java/com/rag/common/protection/"
                        + "GoStyleOutboundDeadlineInterceptor.java`）**＋ 域侧只配参数**"
                        + "（`rag.protection.outbound-deadline.cap-millis`）⇒ ⇒ 请改成配参数，别再复制一份逻辑")
                .isFalse();
    }

    // ====================================================================================
    // ④ 配置面**真绑定**：不是「yml 文本里写了」，而是「真能绑进共享层读的那个对象」
    // ====================================================================================

    @Test
    @DisplayName("🔴 本域 yml 的 `cap-millis` 真的能绑定进共享层拦截器读的那个属性对象")
    void domainYamlBindsIntoSharedProtectionProperties() throws Exception {
        com.rag.common.protection.GoStyleProtectionProperties props = bindProtectionProperties(null);

        assertThat(props.getOutboundDeadline().getCapMillis())
                .as("🔴 域侧 yml 的 `rag.protection.outbound-deadline.cap-millis` 必须真的绑定进共享层的 "
                        + "`GoStyleProtectionProperties.OutboundDeadline#capMillis`"
                        + "（共享层 `GoStyleOutboundDeadlineInterceptor` 的构造器读的就是它）"
                        + "；本域的值 = **2000**（Go `article.yaml` 的 `MessageRpc:` 段**没写** Timeout ⇒ "
                        + "走 `zrpc/config.go:31` 的 default 2000；⚠️ 另一个方向 `SecurityConf.Timeout=10000` "
                        + "当前**无真实调用** ⇒ 详见本域台账「第十二轮」小节）"
                        + "；⚠️ 默认 0 = 不启用 ⇒ 绑不上 = 出向零期限 = 比 Go 松")
                .isEqualTo(2_000L);

        assertThat(props.getOutboundDeadline().isEnabled())
                .as("🔴 出向 deadline 传播开关必须仍是**开**（共享层默认 `enabled=true`；"
                        + "Go 侧 `zrpc/internal/config.go:15` 的 `Timeout bool json:\",default=true\"`）"
                        + "⇒ ⇒ 本域不得把它关掉 ⇒ 否则 = 出向零期限 = 比 Go 松")
                .isTrue();
    }

    // ====================================================================================
    // ⑥ 变异（**放宽方向**）：把 `cap-millis` 覆盖成 4000 ⇒ 实测上界必须跟着放宽
    //    ⚠️ 为什么必须「放宽」而不是「调小」：判据要证明「这个上界**真在生效**」
    //       ⇒ ⇒ 「调小」方向上，真实现与「写死 2000」的假实现算出来的上界**完全相同**
    //       ⇒ ⇒ 判据**无牙** ⇒ ⇒ 只有**放宽**方向才分得开
    // ====================================================================================

    @Test
    @DisplayName("🔴 变异（放宽方向）：`cap-millis` 覆盖成 4000 ⇒ 实测上界跟着放宽（证 cap 是配置驱动，不是写死 2000）")
    void capIsConfigDrivenNotHardCoded() throws Exception {
        // ① 真按生产链读配置（yml ＋ 一条**放宽**的覆盖）
        com.rag.common.protection.GoStyleProtectionProperties props = bindProtectionProperties(4_000L);
        assertThat(props.getOutboundDeadline().getCapMillis())
                .as("覆盖值必须真的绑进 `GoStyleProtectionProperties`（否则这条测的就不是生产链）")
                .isEqualTo(4_000L);

        // ② 用**生产同款**的拦截器 + 该值构造（共享层唯一实现，不是本类另写一遍算术）
        com.rag.common.protection.GoStyleOutboundDeadlineInterceptor relaxed = new com.rag.common.protection.GoStyleOutboundDeadlineInterceptor(props);

        // ③ 绑定一个**长于** 4s 的载体 ⇒ ⇒ 载体不是限制项 ⇒ 读数只可能来自 cap
        long carrierNanos = TimeUnit.SECONDS.toNanos(30L);
        try (GoStyleRequestDeadline.Scope ignored = GoStyleRequestDeadline.bindAbsoluteNanos(
                System.nanoTime() + carrierNanos)) {

            io.grpc.Deadline deadline = sharedOutboundDeadline(relaxed);
            long remaining = deadline == null ? -1L : deadline.timeRemaining(TimeUnit.NANOSECONDS);

            assertThat(remaining)
                    .as("🔴 把 `cap-millis` **放宽**到 4000ms ⇒ 实测上界必须**跟着放宽**（≥3s）"
                            + " ⇒ ⇒ 若仍读到 ≈2s ⇒ 说明这个上界是**写死**的（假实现）"
                            + " ⇒ ⇒ 这就是「从放宽方向取证」的判别力"
                            + "（⚠️ 「调小」方向对它**结构性不可见**：min 链里写死的 2000 与真实现算出来一样）")
                    .isGreaterThan(TimeUnit.MILLISECONDS.toNanos(3_000L));
            assertThat(remaining)
                    .as("实测上界不得宽于放宽后的 cap（4000ms）")
                    .isLessThanOrEqualTo(TimeUnit.MILLISECONDS.toNanos(4_000L));
        }
    }

    /**
     * 按生产链读本域的共享层配置（{@code rag.protection.*}）⇒ 并可**覆盖** {@code cap-millis}。
     *
     * <p>⚠️ <b>为什么要走真绑定</b>：本类要证明的是「配置 ⇒ 共享层属性对象 ⇒ 拦截器 ⇒ 实际 deadline」
     * 这条链 ⇒ ⇒ 若在测试里手搓一个属性对象再塞值 ⇒ ⇒ 中间那截「yml ⇒ 绑定」就没被测到
     * ⇒ ⇒ 配置面与生产面可能悄悄分叉（AGENTS.md §3.3.1：判据要打在**消费点**）。
     *
     * @param overrideCapMillis 覆盖值（毫秒）；{@code null} = 不覆盖（读 yml 原值）
     */
    private static com.rag.common.protection.GoStyleProtectionProperties bindProtectionProperties(Long overrideCapMillis) throws Exception {
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        java.util.List<PropertySource<?>> sources =
                loader.load("application", new ClassPathResource("application.yml"));
        assertThat(sources)
                .as("本域 `application.yml` 必须能被 Spring 的 yml 加载器读成 PropertySource")
                .isNotEmpty();

        MutablePropertySources mutable = new MutablePropertySources();
        sources.forEach(mutable::addLast);
        if (overrideCapMillis != null) {
            mutable.addFirst(new MapPropertySource(
                    "cap-mutation",
                    Map.of("rag.protection.outbound-deadline.cap-millis", overrideCapMillis)));
        }

        return new Binder(ConfigurationPropertySources.from(mutable))
                .bind("rag.protection", com.rag.common.protection.GoStyleProtectionProperties.class)
                .orElseThrow(() -> new IllegalStateException("`rag.protection` 段没绑上"));
    }
}
