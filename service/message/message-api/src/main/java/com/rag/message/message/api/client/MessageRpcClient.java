package com.rag.message.message.api.client;

import com.rag.message.message.proto.MessageServiceGrpc;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

/**
 * message-rpc gRPC client —— 持有 {@code MessageServiceBlockingStub}。
 *
 * <p><b>Go 出处</b>：{@code service/message/api/internal/svc/servicecontext.go}
 * <pre>
 * type ServiceContext struct {
 *     Config     config.Config
 *     MessageRpc messageservice.MessageService   // ← 本类对应物
 * }
 * func NewServiceContext(c config.Config) *ServiceContext {
 *     return &ServiceContext{ ..., MessageRpc: messageservice.NewMessageService(zrpc.MustNewClient(c.MessageRpc)) }
 * }
 * </pre>
 *
 * <p><b>框架职责</b>：
 * <ul>
 *   <li>{@code @GrpcClient("message-rpc")} 由 devh grpc-client-spring-boot-starter
 *       自动注入阻塞式 stub；寻址走 {@code grpc.client.message-rpc.address=discovery:///message-rpc}，
 *       即 Nacos 自动发现（对应 Go 端的 etcd key {@code message.rpc}）。</li>
 *   <li>取代了 Go 的 {@code zrpc.MustNewClient} + {@code messageservice.NewMessageService} 两层。</li>
 *   <li>Go 侧 api → rpc 的调用是<b>同步阻塞</b>的（无 goroutine / 无 errgroup），
 *       所以用 {@code BlockingStub} 而不是 future stub —— 这一点属于 L3
 *       （并发与失败传播语义），不是可以随手换的风格差异。</li>
 * </ul>
 *
 * <p><b>⚠️ retry：Go 侧确实没有，禁止加</b>。
 * {@code zrpc.RpcClientConf}（{@code zrpc/config.go}）<b>没有 {@code Retries} 字段</b>
 * （注意别与 {@code zrpc.ClientConf} 混淆，后者有 Retries，但 message api 用的是前者）。
 * 加 retry 会让「一次失败 → 业务码 1015」变成「重试 N 次后仍失败」，
 * 可观察的<b>调用次数与延迟</b>都变了。1015 = {@code CodeServerBusy}（{@code errmsg.go:31}）。
 *
 * <p><b>⚠️⚠️ circuit breaker：Go 侧<b>有</b>，且默认开启 —— 本类注释原先写「Go 侧没有」是<b>事实错误</b>，
 * 已于 2026-10-22 复核更正</b>。证据：
 * <ul>
 *   <li>{@code zrpc/internal/config.go:14}：
 *       {@code ClientMiddlewaresConf.Breaker bool json:",default=true"}</li>
 *   <li>{@code zrpc/internal/client.go:112-113}：
 *       {@code if c.middlewares.Breaker { interceptors = append(interceptors, clientinterceptors.BreakerInterceptor) }}</li>
 *   <li>{@code zrpc/internal/clientinterceptors/breakerinterceptor.go:13-18}：
 *       {@code breaker.DoWithAcceptableCtx(ctx, path.Join(cc.Target(), method), ...)}
 *       ⇒ <b>每个 RPC 方法一个 breaker</b>，算法与 HTTP 层同一个 googleBreaker。</li>
 * </ul>
 * Go 的 {@code etc/messagecenter.yaml} 未配置 {@code Middlewares} ⇒ 走 {@code default=true} ⇒ <b>是开的</b>。
 *
 * <p><b>但熔断只对「基础设施故障」生效，不对业务错误生效</b> ——
 * 这是让差异显得没那么严重的唯一原因，必须一起理解：
 * {@code zrpc/internal/codes/accept.go:9-17} 的 {@code codes.Acceptable} 只把
 * {@code DeadlineExceeded / Internal / Unavailable / DataLoss / Unimplemented / ResourceExhausted}
 * 判为失败，其余（含 {@code OK}）判为成功；而 message-rpc 的业务错误
 * （逐个 {@code errWithBizCode} 调用点核对）只用
 * {@code InvalidArgument / NotFound / PermissionDenied} 三个 code，
 * <b>全都不在失败名单里</b> ⇒ 业务错误在 Go 侧被记为 Accept，不参与熔断。
 *
 * <p><b>⇒ 当前 RAG 的实际状态（DRIFT-MSGAPI-004，<b>已修</b>）</b>：
 * 客户端熔断<b>已经接入</b>，但<b>不在本类里</b> ——
 * 由共享层 {@code com.rag.common.protection.GoStyleGrpcClientBreakerInterceptor} 承担
 * （{@code GoStyleGrpcClientBreakerInterceptor.java:103-105} = {@code @Component} +
 * {@code @GrpcGlobalClientInterceptor}）
 * ⇒ 本域的 {@code MessageApiApplication.java:63-66}（{@code scanBasePackages} 含
 * {@code com.rag.common}）<b>扫到即注册</b> ⇒ <b>本类的每一次下游调用都在熔断链上</b>。
 * <ul>
 *   <li><b>acceptable 判据</b>：共享层 {@code GoStyleGrpcClientBreakerInterceptor.java:336-345}
 *       逐条对齐 Go {@code zrpc/internal/codes/accept.go:9-17} 的那<b>六个</b>失败 code
 *       （DeadlineExceeded / Internal / Unavailable / DataLoss / Unimplemented / ResourceExhausted）
 *       ⇒ <b>业务错误不参与熔断</b>：message-rpc 的业务错误只用
 *       InvalidArgument / NotFound / PermissionDenied，<b>三个都不在失败名单里</b>
 *       ⇒ 与 Go 侧一致（Go 的 {@code codes.Acceptable} 默认分支就是「算成功」）。</li>
 *   <li><b>隔离单位</b>：共享层 {@code :286} = {@code 服务身份 + 方法名}
 *       ⇒ 对齐 Go {@code zrpc/internal/clientinterceptors/breakerinterceptor.go:14}
 *       的 {@code path.Join(cc.Target(), method)}（每个下游服务一份独立熔断器）。</li>
 *   <li><b>熔断打开时的可观察面</b>：共享层 {@code :299} 抛
 *       {@link com.rag.common.protection.GoStyleCircuitOpenException}
 *       ⇒ 本域 {@code GlobalExceptionHandler.java:275-279} 把它映成
 *       <b>1015 / "service busy"</b> ⇒ 与 Go 的 {@code CodeServerBusy=1015} <b>同码同文案</b>
 *       （Go 侧 {@code breaker.ErrServiceUnavailable} 是普通 error ⇒ 被
 *       {@code RPCError → BizCodeFromError} 转成同一个码）。</li>
 * </ul>
 *
 * <p>⚠️ <b>运行证据（不是「注解在不在」）</b>：本域守门
 * {@code GoStyleGrpcClientBreakerE2ETest}（真 in-process gRPC server + 真 channel + 真拦截器）
 * 断言「下游恒返 INTERNAL ⇒ 业务码<b>先 5001、跨桶后变 1015</b>，且 msg = {@code "service busy"}」
 * ⇒ 证明熔断<b>真的落在调用链上</b>（§3.3：注解存在 ≠ 机制生效）。
 *
 * <p>⚠️ <b>若将来要改本类</b>：<b>不要</b>在这里再加一个 {@code ClientInterceptor}。
 * 判据若写成「非 OK 即失败」，{@code NOT_FOUND}（会话不存在 5202）、
 * {@code PERMISSION_DENIED}（无权限 5205）这类<b>日常运营天天发生</b>的业务错误
 * 会被大量计成失败 ⇒ 熔断误开 ⇒ 打掉正常流量 —— 那是<b>新增行为且比现状更糟</b>。
 */
@Component
public class MessageRpcClient {

    @GrpcClient("message-rpc")
    private MessageServiceGrpc.MessageServiceBlockingStub messageServiceBlockingStub;

    /**
     * 返回<b>已套上请求级 deadline</b> 的 stub。
     *
     * <p>理由与 {@code LikeRpcClient#likeRpc()} 完全相同：Go 侧靠
     * {@code timeouthandler.go:68} 的 {@code r.WithContext(ctx)} 把 deadline 传进
     * <b>全部</b>下游调用；RAG 侧唯一能一处覆盖所有调用点的位置就是这个 getter。
     *
     * <p>⚠️ <b>不要把返回值缓存到字段上</b>：{@code withDeadlineAfter} 每次返回新实例，
     * 缓存会把「第一次调用时的预算」固化下来，之后每个请求都带着过期 deadline。
     */
    public MessageServiceGrpc.MessageServiceBlockingStub messageRpc() {
        return com.rag.common.observability.GoStyleRequestDeadline.applyTo(messageServiceBlockingStub);
    }
}