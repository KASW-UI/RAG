package com.rag.favorite.favorite.rpc.client;

import com.rag.common.observability.GoStyleRequestDeadline;
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
     * 返回<b>已套上请求级 deadline</b> 的 stub。
     *
     * <p>Go 侧靠 {@code observability} 的 unary interceptor 给整条服务端链路加超时，
     * 并由 context 把 deadline 传进<b>全部</b>下游调用；
     * RAG 侧唯一能一处覆盖所有调用点的位置就是这个 getter
     * （先例：{@code article-api/client/UserRpcClient.java} 同款注释）。
     *
     * <p>⚠️ <b>不要把返回值缓存到字段上</b>：{@code withDeadlineAfter} 每次返回新实例，
     * 缓存会把「第一次调用时的预算」固化下来，之后每个请求都带着过期 deadline。
     */
    public UserServiceGrpc.UserServiceBlockingStub userRpc() {
        return GoStyleRequestDeadline.applyTo(userServiceBlockingStub);
    }
}