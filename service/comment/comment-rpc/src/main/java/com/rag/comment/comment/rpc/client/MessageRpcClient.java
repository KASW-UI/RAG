package com.rag.comment.comment.rpc.client;

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
 * message-rpc 客户端 —— 对应 Go 端 {@code service/comment/rpc/internal/svc/service_context.go:36}
 * 的 {@code MessageRpc: messageservice.NewMessageService(zrpc.MustNewClient(c.MessageRpc))}。
 *
 * <p><b>全模块只有一个调用点</b>：{@code mqs/audit_consumer.go:101} 的
 * {@code SendNotification}（评论通过审核后通知 up 主 / 父评论作者）。
 * 因此本类也只暴露一个方法 —— 不为了「通用客户端」而泛化。
 *
 * <h2>⚠️ 失败语义：吞掉，不影响消费</h2>
 * <p>Go {@code audit_consumer.go:117-119}：
 * <pre>
 * if err != nil {
 *     l.Errorf("send comment notification failed, comment %d: %v", msg.CommentId, err)
 * }
 * </pre>
 * 通知发不出去<b>只记日志</b>。此时评论已经落库、缓存已失效，
 * 若把错误抛出去会让 Kafka 消息重投 ⇒ <b>重复落库 + 重复通知</b>。
 * ⇒ 本方法返回 {@code boolean}，调用方<b>不</b>据此抛错。
 *
 * <p><b>用 {@code BlockingStub}</b>：Go 的调用是同步阻塞的，
 * 换成 AsyncStub 会变成 fire-and-forget，改变「消费耗时包含 message-rpc 往返」这一可观察行为。
 *
 * <p>⚠️ 枚举常量名：proto 里是 {@code ARTICLE_COMMENT} / {@code USER}
 * （不是 Go 生成物的 {@code NotificationKind_ARTICLE_COMMENT}），
 * Java 生成器会把枚举值名原样保留、并从类型名上去掉前缀。
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
     * 对应 Go {@code notifyComment} 的完整 {@code SendNotification} 调用。
     *
     * @param recipientId 收件人（up 主或父评论作者）
     * @param kind        {@code ARTICLE_COMMENT} 或 {@code COMMENT_REPLY}
     * @return true = RPC 正常返回；false = 失败（已记日志，调用方<b>不应</b>因此报错）
     */
    public boolean sendNotification(long recipientId, long senderId,
                                    NotificationKind kind, String title, String content,
                                    String targetType, String targetId,
                                    long commentId, long rootId, long parentId) {
        try {
            stub.sendNotification(SendNotificationReq.newBuilder()
                    // Go: RecipientIds: []int64{recipientID}
                    .addRecipientIds(recipientId)
                    // Go: Broadcast: false —— 只通知这一个人。
                    // ⚠️ 这条若被误改成 true，message-rpc 会**误判成全量广播**
                    //    （DRIFT-MSGAPI-005 就是这么出事的），不可省略。
                    .setBroadcast(false)
                    .setSenderId(senderId)
                    // Go: SenderRole: messagepb.SenderRole_USER
                    .setSenderRole(SenderRole.USER)
                    .setKind(kind)
                    .setTitle(title)
                    .setContent(content)
                    .putExtra("target_type", targetType)
                    .putExtra("target_id", targetId)
                    // Go 用 fmt.Sprintf("%d", …) 把数值转字符串放进 map<string,string>
                    // ⇒ Java 侧必须转成 String，不能直接 putExtra(long)
                    .putExtra("comment_id", String.valueOf(commentId))
                    .putExtra("root_id", String.valueOf(rootId))
                    .putExtra("parent_id", String.valueOf(parentId))
                    .build());
            return true;
        } catch (StatusRuntimeException e) {
            LOG.error("send comment notification failed, comment {}: {}", commentId, e.getStatus(), e);
            return false;
        }
    }
}