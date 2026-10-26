package com.rag.users.user.api.client;

import com.rag.users.user.proto.UserServiceGrpc;
import net.devh.boot.grpc.client.config.GrpcChannelsProperties;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * user-rpc gRPC client —— 持有 UserServiceBlockingStub。
 *
 * 对应 Go: service/user/user/api/internal/svc/servicecontext.go 中的 UserRpc（go-zero 自动生成）。
 *
 * 框架职责:
 *   - @GrpcClient("user-rpc") 自动注入阻塞式 stub（grpc.client.user-rpc.address=discovery:///user-rpc）
 *   - devh grpc-client-spring-boot-starter 通过 Nacos 自动发现 user-rpc 多实例（修 DRIFT-NEW-001）
 *   - 取代了原 internal/internal.svc.ServiceContext.UserRpc 字段
 *   - 所有 Service 类通过 UserRpcClient 调用下游 user-rpc
 *
 * Phase 2b 填肉阶段：可加 retry / circuit breaker / deadline propagation（DRIFT-018）
 *
 * <h2>🔴 DRIFT-G1-002：启动期必须能连上 user-rpc，否则进程起不来</h2>
 *
 * <p><b>Go 出处（逐层到消费点）</b>：
 * <pre>
 *   service/user/user/api/internal/svc/servicecontext.go:26
 *       UserRpc: userservice.NewUserService(zrpc.MustNewClient(c.UserRpc)),
 *   go-zero v1.10.2 zrpc/client.go:50-54
 *       func MustNewClient(c RpcClientConf, ...) Client {
 *           cli, err := NewClient(c, options...)
 *           logx.Must(err)          ← 失败即 panic
 *           return cli
 *       }
 *   go-zero v1.10.2 zrpc/internal/client.go:122-142（消费点）
 *       timeCtx, cancel := context.WithTimeout(context.Background(), dialTimeout)   // dialTimeout = 3s（:18）
 *       conn, err := grpc.DialContext(timeCtx, server, options...)                 // options 含 WithBlock()
 *       if err != nil { return fmt.Errorf("rpc dial: %s, ... make sure rpc service %q is already started", ...) }
 * </pre>
 *
 * <p>⇒ Go 侧 {@code grpc.DialContext} 带 {@code WithBlock()} ⇒ <b>启动时阻塞拨号直到连上，
 * 或 3s 超时后返回 error ⇒ {@code logx.Must} ⇒ panic ⇒ 进程根本起不来</b>。
 *
 * <p>⇒ <b>RAG 侧必须复刻这个「启动期硬失败」语义</b>。修之前：{@code @GrpcClient} 是<b>懒连接</b>，
 * 下游不可达时进程照样启动、就绪探针报 Ready，但每个请求各自失败 ⇒ K8s 里表现为
 * 「Pod Ready 但 100% 请求失败」，而 Go 表现为「Pod 起不来」——<b>就绪探针语义不同</b>。
 *
 * <h3>实现方式：用 devh 的<b>原生</b> {@code immediate-connect-timeout}，不自己写拨号检查</h3>
 *
 * <p>devh grpc-client-spring-boot-starter 3.1.0 的
 * {@code AbstractChannelFactory.newManagedChannel(name)} 里已经有这套机制：
 * <pre>
 *   final Duration timeout = this.properties.getChannel(name).getImmediateConnectTimeout();
 *   if (!timeout.isZero()) { connectOnStartup(name, channel, timeout); }
 *   ...
 *   private void connectOnStartup(String name, ManagedChannel channel, Duration timeout) {
 *       channel.getState(true);
 *       final CountDownLatch readyLatch = new CountDownLatch(1);
 *       waitForReady(channel, readyLatch);
 *       boolean connected = readyLatch.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
 *       if (!connected) { throw new IllegalStateException("Can't connect to channel " + name); }
 *   }
 * </pre>
 * ⇒ <b>与 Go 的 {@code zrpc.MustNewClient} 逐条同型</b>：都是「连不上 ⇒ 进程起不来」，
 * 而不是「起来但请求全失败」。
 *
 * <p>本模块在 {@code application.yml} 里<b>只对 user-rpc 这一个 channel</b> 打开它
 * （{@code grpc.client.user-rpc.immediate-connect-timeout: 3s}），<b>不设全局</b> ⇒ 理由：
 * Go 侧 user-api 的 {@code ServiceContext} 里<b>只有</b> {@code UserRpc} 一个下游依赖
 * （{@code service/user/user/api/internal/svc/servicecontext.go:16-28}）
 * ⇒ 若把超时设成全局，就会凭空多出一条 Go 侧不存在的启动期硬依赖。
 *
 * <h3>🔴 踩坑：<b>不能</b>用 {@code stub.getChannel()} 自己读连通性状态</h3>
 *
 * <p>判据必须是 devh 自己持有的<b>原始</b> {@code ManagedChannel}
 * （{@code getState} 只存在于 {@code io.grpc.ManagedChannel}，
 * 而生产里 channel 会被 {@code ClientInterceptors.interceptForward} 包成
 * {@code io.grpc.ClientInterceptors$InterceptorChannel}，它<b>不暴露</b> {@code getState}）
 * ⇒ 手写检查在生产<b>永远为 false</b> ⇒ <b>user-api 在任何环境下都起不来</b>。
 * ⇒ 结论：用 devh 的 {@code immediate-connect-timeout}，不要自己摸 channel。
 * （完整经过见 admin-api 的 {@code AdminRpcClient} 同款注释。）
 *
 * <h3>为什么还要留 {@link #afterPropertiesSet()} 这一道自检</h3>
 *
 * <p>{@code immediate-connect-timeout} 的<b>默认值是 {@code Duration.ZERO}（= 关闭）</b>
 * ⇒ <b>「yml 里没写」就等于「机制没武装」</b>。
 * 若有人删掉那一行，启动期硬失败会<b>静默消失</b>，而现象是
 * 「Pod Ready 但 100% 请求失败」——<b>与 Go 的差异又回来了，而且没人会发现</b>。
 * ⇒ 本方法在上下文启动的最后一刻断言<b>机制确实已武装</b>，否则直接让启动失败。
 * 这是 §3.3「配置里没写 ≠ 没开」的直接落地。
 */
@Component
public class UserRpcClient implements InitializingBean {

    /** devh 里的 channel 名 = {@code @GrpcClient("user-rpc")} 的值。 */
    private static final String CHANNEL_NAME = "user-rpc";

    /**
     * 拨号预算 —— 对齐 go-zero {@code zrpc/internal/client.go:18} 的 {@code dialTimeout = time.Second * 3}。
     *
     * <p>⚠️ 这个值<b>不</b>由本类持有，而是由 {@code application.yml} 的
     * {@code grpc.client.user-rpc.immediate-connect-timeout} 持有 ⇒
     * 本常量只是<b>断言用</b>的期望值（{@code UserApiStartupFailFastTest} 会核对 yml 里的真实值）。
     */
    public static final Duration DIAL_TIMEOUT = Duration.ofSeconds(3);

    private static final Logger LOG = LoggerFactory.getLogger(UserRpcClient.class);

    @GrpcClient("user-rpc")
    private UserServiceGrpc.UserServiceBlockingStub userServiceBlockingStub;

    /**
     * 仅为「拿到 channel 名对应的有效配置」而存在 —— 用来断言
     * {@code immediate-connect-timeout} 确实已武装（见类注释末段）。
     */
    private final GrpcChannelsProperties channelProperties;

    public UserRpcClient(GrpcChannelsProperties channelProperties) {
        this.channelProperties = channelProperties;
    }

    @Override
    public void afterPropertiesSet() {
        Duration timeout = channelProperties.getChannel(CHANNEL_NAME).getImmediateConnectTimeout();
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalStateException(String.format(
                    "user-rpc 的启动期拨号检查**未武装**：grpc.client.%s.immediate-connect-timeout=%s。"
                            + "对齐 Go service/user/user/api/internal/svc/servicecontext.go:26 的 "
                            + "zrpc.MustNewClient（连不上 ⇒ 进程起不来）需要它为正数；"
                            + "devh 的默认值是 0（= 关闭），"
                            + "机制一旦被静默关掉，现象会退化成「Pod Ready 但 100%% 请求失败」。"
                            + "请在 service/users/user/user-api/src/main/resources/application.yml 的 "
                            + "grpc.client.%s.immediate-connect-timeout 处显式写 %dms。",
                    CHANNEL_NAME, timeout, CHANNEL_NAME, DIAL_TIMEOUT.toMillis()));
        }
        LOG.info("user-rpc 启动期拨号检查已武装：immediate-connect-timeout={}ms"
                + "（对齐 go-zero zrpc/internal/client.go:18 dialTimeout=3s）", timeout.toMillis());
    }

    public UserServiceGrpc.UserServiceBlockingStub userRpc() {
        return userServiceBlockingStub;
    }
}