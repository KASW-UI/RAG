package com.rag.like.like.rpc.client;

import com.rag.message.message.proto.MessageServiceGrpc;
import com.rag.message.message.proto.NotificationKind;
import com.rag.message.message.proto.SendNotificationReq;
import com.rag.message.message.proto.SenderRole;
import io.grpc.StatusRuntimeException;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * message-rpc 客户端 —— 对应 Go 端 {@code service/like/rpc/internal/svc/servicecontext.go:46}
 * 的 {@code MessageRpc: messageservice.NewMessageService(zrpc.MustNewClient(c.MessageRpc))}。
 *
 * <p><b>全模块只有一个调用点</b>：{@code notifyArticleLiked}（点赞成功后给作者发通知）。
 * 因此本类也只暴露一个方法 —— 不为了「通用客户端」而泛化。
 *
 * <h2>失败语义（关键）</h2>
 *
 * <p>Go {@code likeactionlogic.go:395-397}：
 * <pre>
 * if err != nil {
 *     logger.LogBusinessErr(l.ctx, errmsg.ErrorServerCommon, ...)
 * }
 * </pre>
 * 即<b>通知发不出去只记日志，不影响点赞 RPC 的返回值</b>。
 * 点赞已经在 Redis Lua 里原子生效，此时返回错误会让客户端以为「点赞失败」并可能重试 ⇒ 重复通知。
 * 本实现<b>吞掉所有异常并返回 false</b>，与 Go 同向。
 *
 * <h2>为什么是 {@code @GrpcClient} 而不是自己建 ManagedChannel</h2>
 *
 * <p>{@code grpc-client-spring-boot-starter} + Nacos discovery 已经承担了
 * 「按服务名寻址（{@code discovery://message-rpc}）+ channel 复用 + keepalive」，
 * 与 Go 的 {@code zrpc.MustNewClient} 等价。再手写 channel 属于重复实现框架已有的能力。
 *
 * <p>⚠️ 用的是 <b>BlockingStub</b>：Go 的 {@code SendNotification} 是同步阻塞调用，
 * 用 AsyncStub 会把调用变成 fire-and-forget，**改变可观察行为**
 * （Go 会一直等到 RPC 返回或超时，之后才继续）。见 AGENTS.md DRIFT-MSGAPI-004 同款提醒：
 * 别把业务错误变成「立即成功」。
 */
@Component
public class MessageRpcClient {

    private static final Logger LOG = LoggerFactory.getLogger(MessageRpcClient.class);

    /**
     * 对应 Go {@code zrpc.MustNewClient(c.MessageRpc)} 的寻址方式：
     * {@code MessageRpc.Etcd.Key: message.rpc} ⇒ 服务发现名 {@code message-rpc}。
     */
    @GrpcClient("message-rpc")
    private MessageServiceGrpc.MessageServiceBlockingStub stub;

    /**
     * 对应 Go {@code notifyArticleLiked} 的完整调用。
     *
     * @return true 表示 RPC 正常返回；false 表示失败（已记日志，调用方<b>不应</b>因此报错）
     */
    public boolean sendArticleLiked(long recipientId, long likerId,
                                    String targetType, String targetId) {
        SendNotificationReq.Builder req = SendNotificationReq.newBuilder()
                // Go: RecipientIds: []int64{in.AuthorId}
                .addRecipientIds(recipientId)
                // Go: Broadcast: false —— 只通知作者本人
                .setBroadcast(false)
                // Go: SenderId: in.UserId
                .setSenderId(likerId)
                // Go: SenderRole: messagepb.SenderRole_USER
                // 生成物里的 Java 常量名是 SenderRole.USER（proto 值名 USER，不是 SENDER_ROLE_USER）
                .setSenderRole(SenderRole.USER)
                // Go: Kind: messagepb.NotificationKind_ARTICLE_LIKED
                .setKind(NotificationKind.ARTICLE_LIKED)
                // Go: Title: "文章收获新点赞"
                .setTitle("文章收获新点赞")
                // Go: Content: fmt.Sprintf("用户 %d 点赞了你的文章。", in.UserId)
                .setContent(String.format("用户 %d 点赞了你的文章。", likerId))
                // Go: Extra: map[string]string{...} —— 四个键的值全部是字符串
                .putExtra("target_type", targetType)
                .putExtra("target_id", targetId)
                .putExtra("liker_id", String.valueOf(likerId))
                .putExtra("author_id", String.valueOf(recipientId));

        try {
            stub.sendNotification(req.build());
            return true;
        } catch (StatusRuntimeException e) {
            // Go: logger.LogBusinessErr(..., errmsg.ErrorServerCommon, ...) —— 吞掉，不影响点赞结果
            LOG.warn("send article liked notification failed: recipient={} liker={} target={}:{} err={}",
                    recipientId, likerId, targetType, targetId, e.getMessage());
            return false;
        }
    }
}