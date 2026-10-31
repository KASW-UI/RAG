package com.rag.favorite.favorite.rpc.client;

import com.rag.users.user.proto.UserServiceGrpc;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

/**
 * user-rpc gRPC client —— 持有 {@code UserServiceBlockingStub}。
 *
 * <p><b>Go 出处</b>：{@code service/favorite/rpc/internal/svc/service_context.go:37}
 * <pre>
 * UserRpc: userservice.NewUserService(zrpc.MustNewClient(c.UserRpc))
 * </pre>
 * 对应 {@code etc/favorite.yaml:37-39} 的 {@code Etcd.Key: user.rpc} ⇒ 服务发现名 {@code user-rpc}。
 *
 * <p><b>唯一消费者</b>是 {@code FavoriteUserGuard.ensureExists}
 * （Go 的 {@code logic/helper.go:92-124}）。它<b>参与判定</b>：
 * 五条分支各自决定返回哪个业务码，所以它的失败**不能**被降级吞掉
 * —— 与 article-api 那个「只用于作者昵称补全、失败就退回占位名」的 client 完全不同。
 *
 * <p>⚠️ <b>不要用 {@code Optional} 表达「用户不存在」</b>：Go 侧 {@code GetUser} 的
 * 「找不到」有<b>两条</b>路径（RPC 返回 NotFound / resp.found=false），
 * 且两条都翻译成同一个 {@code NotFound/5101}。本 client 返回原始响应，
 * 由 {@code FavoriteUserGuard} 逐条翻译。
 */
@Component
public class UserRpcClient {

    @GrpcClient("user-rpc")
    private UserServiceGrpc.UserServiceBlockingStub userServiceBlockingStub;

    /**
     * 出向 stub —— <b>deadline 由共享层的全局客户端拦截器统一施加</b>（2026-10-31 · G3 第十一轮）。
     *
     * <p>Go 侧靠 {@code observability} 的 unary interceptor 给整条服务端链路加超时
     * （{@code service/common/observability/rpc.go:19}），
     * 并由 context 把 deadline 传进<b>全部</b>下游调用；
     * RAG 侧对应的是<b>拦截器级</b>的统一施加（共享层
     * {@code GoStyleOutboundDeadlineInterceptor}），本 getter 只负责把<b>注入的 stub 交出去</b>
     * （先例：{@code article-api/client/UserRpcClient.java} 同款注释）。
     *
     * <p>🔴 <b>Go 出处（行为层）</b>：Go 侧 {@code zrpc.MustNewClient(c.UserRpc)} 的客户端
     * <b>无条件</b>装 {@code TimeoutInterceptor}
     * （{@code zrpc/internal/client.go:113-117} ＋ {@code zrpc/internal/config.go:15} 的
     * {@code default=true}）⇒ 每次调用都有 <b>2000ms</b> 上限
     * （{@code zrpc/config.go:31} 的 {@code json:",default=2000"}；Go 侧
     * {@code etc/favorite.yaml} 的 {@code UserRpc:} 段未配 {@code Timeout} ⇒ 走这个默认值）。
     *
     * <p>🔴 <b>为什么本类不再自己套 deadline</b>：Go 的客户端超时是<b>拦截器级别</b>的
     * ⇒ ⇒ RAG 侧同形也应是拦截器级 ⇒ ⇒ 改由共享层统一施加，本域<b>只配参数</b>：
     * {@code rag.protection.outbound-deadline.cap-millis=2000}
     * （见本组件 {@code application.yml}；共享层默认 {@code capMillis=0} ⇒ <b>不配 = 不启用</b>
     * ⇒ ⇒ 原先只有 {@code applyTo}（无预算时<b>原样返回</b>）时本域是纯 gRPC 进程
     * ⇒ ⇒ 出向零期限 ⇒ 与 Go 可观察行为分叉 —— 这一条现在由共享层补上）。
     *
     * <p>⚠️ <b>域内私有副本 {@code GoStyleClientCallTimeout} 已删除</b>（本轮）：
     * <b>副本数 = 漂移面</b> ⇒ ⇒ 共享层守门 {@code GoStyleClientCallTimeoutSingleSourceGateTest}
     * 的终态口径 = 「域侧 0 份，只配参数」。
     */
    public UserServiceGrpc.UserServiceBlockingStub userRpc() {
        return userServiceBlockingStub;
    }
}