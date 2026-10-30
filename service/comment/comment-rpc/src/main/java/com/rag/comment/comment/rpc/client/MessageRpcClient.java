package com.rag.comment.comment.rpc.client;

import com.rag.common.observability.GoStyleRequestDeadline;
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
     * 返回<b>已套上「min(2s, 请求剩余预算)」deadline</b> 的 stub。
     *
     * <p>🔴 <b>为什么必须有这一层</b>（2026-10-27 G3 域侧自修 · A1）：
     * Go 侧 {@code zrpc.MustNewClient(c.MessageRpc)} 的客户端<b>无条件</b>装
     * {@code TimeoutInterceptor}（{@code zrpc/internal/client.go:113-117} ＋
     * {@code zrpc/internal/config.go:15} 的 {@code default=true}）⇒ 每次调用都有
     * <b>2000ms</b> 上限（{@code zrpc/config.go:31} 的 {@code json:",default=2000"}）。
     * <br>⇒ 而本类原先<b>直接用注入的 stub 发起调用</b> ⇒ <b>零期限**
     * ⇒ 下游挂死时 RAG 侧会一路等到底，Go 侧 2s 就返回
     * ⇒ **可观察行为分叉**（调用耗时 + 失败时机都不同）。
     *
     * <p>🔴 <b>2026-10-30 订正 · 两段式（纳秒）</b>：本出口跑在 <b>Kafka consumer 线程</b>上
     * （调用点 {@code service/CommentAuditListener.java:249}，该类 {@code :99} 是 {@code @KafkaListener}）
     * ⇒ 共享层 {@code RpcInterceptor.onHalfClose} 绑的 {@code GoStyleRequestDeadline} 载体
     * <b>在本线程不可见</b> ⇒ ⇒ 第一段 {@code applyTo} <b>原样返回 stub</b>（不挂 deadline）
     * ⇒ ⇒ 第二段 {@code capByClientTimeout} 看到「无 deadline」⇒ 套 <b>2s</b> 客户端封顶
     * ⇒ ⇒ 与 Go 侧<b>同形</b>：Go 的 {@code service/comment/rpc/internal/mqs/audit_consumer.go:101}
     * 传的 ctx 是 <b>Kafka 消费 ctx（无 deadline）</b> ⇒ Go 侧这条出口同样只有 2s 封顶
     * ⇒ ⇒ <b>本出口不是漂移</b>（逐出口证据 ⇒ {@code docs/DRIFT/comment/rpc.md} 第五轮新增段）。
     *
     * <p>⚠️ **不要把返回值缓存到字段上**：{@code withDeadlineAfter} 每次返回新实例。
     */
    private MessageServiceGrpc.MessageServiceBlockingStub messageRpc() {
        return GoStyleClientCallTimeout.capByClientTimeout(
                GoStyleRequestDeadline.applyTo(stub));
    }

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
            messageRpc().sendNotification(SendNotificationReq.newBuilder()
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