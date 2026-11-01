package com.rag.comment.comment.api.client;

import com.rag.comment.comment.proto.CommentServiceGrpc;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

/**
 * comment-rpc gRPC client —— 持有 {@code CommentServiceBlockingStub}。
 *
 * <p><b>Go 出处</b>：{@code service/comment/api/internal/svc/servicecontext.go:18-23}
 * <pre>
 * func NewServiceContext(c config.Config) *ServiceContext {
 *     conn := zrpc.MustNewClient(c.CommentRpc)
 *     return &amp;ServiceContext{
 *         Config:     c,
 *         CommentCli: commentpb.NewCommentServiceClient(conn.Conn()),
 *     }
 * }
 * </pre>
 *
 * <p><b>框架职责</b>：
 * <ul>
 *   <li>{@code @GrpcClient("comment-rpc")} 由 devh grpc-client-spring-boot-starter
 *       自动注入阻塞式 stub，取代了 Go 的 {@code zrpc.MustNewClient} +
 *       {@code NewCommentServiceClient} 两层手工装配。</li>
 *   <li>Go 侧 api → rpc 的调用是<b>同步阻塞</b>的（无 goroutine / 无 errgroup），
 *       所以用 {@code BlockingStub} 而不是 future stub ——
 *       这属于 L3（并发与失败传播语义），不是可随手换的风格差异。</li>
 * </ul>
 *
 * <p><b>⚠️ retry：Go 侧没有，禁止加</b>。{@code zrpc.RpcClientConf}
 * <b>没有</b> {@code Retries} 字段（别与有该字段的 {@code zrpc.ClientConf} 混淆）。
 * 加 retry 会改变可观察的<b>调用次数与延迟</b>。
 *
 * <h2>✅ deadline：2 秒上限现在<b>只由共享层</b>承担（2026-10-31 · G3 lane 收敛）</h2>
 *
 * <p><b>Go 出处</b>：{@code go-zero@v1.10.2/zrpc/config.go:31}
 * <pre>
 *   RpcClientConf.Timeout int64 `json:",default=2000"`     // ← 2000ms
 *   zrpc/internal/client.go:115-116                        // 装 TimeoutInterceptor(timeout)
 *   zrpc/internal/clientinterceptors/timeoutinterceptor.go:25   // context.WithTimeout(ctx, t)
 * </pre>
 * ⚠️ {@code service/comment/api/etc/commentservice.yaml:21-23} 的 {@code CommentRpc:} 段
 * <b>一个字都没写 Timeout</b>
 * ⇒ 2s 只能从 <b>struct tag 默认</b>推出来，<b>照 yaml 是推不出来的</b>（AGENTS.md §3.3.1 的典型形态）。
 *
 * <p><b>为什么本类不再自己算 deadline</b>（🆕 2026-10-31 · G3 lane · 第十三轮）：
 * <ul>
 *   <li>本类<b>原先</b>自带一份封顶逻辑（{@code min(2s, 入向剩余预算)}）⇒ ⇒ 那是
 *       <b>第二个来源</b> ⇒ ⇒ 与共享层的「唯一实现」形态冲突
 *       （共享层棘轮 {@code GoStyleClientCallTimeoutSingleSourceGateTest} 判据 ④
 *       「cap 的来源必须是共享层参数」⇒ 域侧私有常量 = 违规）；</li>
 *   <li>本类的调用发生在 <b>HTTP 中间件的 worker 线程</b>上 ⇒ ⇒ 那条线程
 *       <b>有</b>请求载体（{@code GoStyleRequestDeadline}，由
 *       {@code HttpMiddleware} 绑定）⇒ ⇒ 共享层拦截器
 *       （{@code GoStyleOutboundDeadlineInterceptor}）<b>读得到</b>入向剩余预算
 *       ⇒ ⇒ 域侧<b>不必</b>再算一遍。</li>
 * </ul>
 *
 * <p><b>现在生效的那一行</b>：本域 {@code application.yml} 的
 * {@code rag.protection.outbound-deadline.cap-millis: ${COMMENT_API_OUTBOUND_DEADLINE_CAP_MILLIS:2000}}
 * ⇒ ⇒ 由共享层拦截器以 <b>min</b> 语义并入（Go 的 {@code context.WithTimeout} 取更早者）。
 *
 * <p>⚠️ <b>不要再在本类写 {@code withDeadlineAfter}</b>：那会重新造出第二份上界
 * ⇒ ⇒ 共享层棘轮会判红（点位级）⇒ ⇒ 详见
 * {@code CommentApiRpcDeadlineGateTest}（本域 api 侧 cap 守门 · 8 例）。
 */
@Component
public class CommentRpcClient {

    @GrpcClient("comment-rpc")
    private CommentServiceGrpc.CommentServiceBlockingStub commentServiceBlockingStub;

    /**
     * 返回出向 stub（<b>不</b>带任何域侧 deadline）。
     *
     * <p>deadline 由共享层 {@code GoStyleOutboundDeadlineInterceptor} 在
     * <b>channel 层</b>统一施加（{@code @GrpcGlobalClientInterceptor} ⇒
     * devh 把它装到<b>每个</b>出向调用上）⇒ ⇒ 取值 = min(入向剩余预算, 客户端上界 2000ms)
     * ⇒ ⇒ 与 Go 侧 {@code context.WithTimeout(ctx, 2s)} 逐字同形。
     */
    public CommentServiceGrpc.CommentServiceBlockingStub commentRpc() {
        return commentServiceBlockingStub;
    }

    /** 仅供测试注入捕获型 stub（package-private，非生产 API）。 */
    CommentServiceGrpc.CommentServiceBlockingStub rawStub() {
        return commentServiceBlockingStub;
    }

    /** 仅供测试注入捕获型 stub（package-private，非生产 API）。 */
    void rawStubField(CommentServiceGrpc.CommentServiceBlockingStub stub) {
        this.commentServiceBlockingStub = stub;
    }
}
