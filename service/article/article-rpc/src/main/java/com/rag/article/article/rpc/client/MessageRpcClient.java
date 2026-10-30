package com.rag.article.article.rpc.client;

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
 * message-rpc 客户端 —— 对应 Go 端
 * {@code mqs/article_sync_result_consumer.go:185-212} 的 {@code notifyArticlePublished}。
 *
 * <p><b>全模块只有一个调用点</b>：文章首次发布成功后给作者发通知。
 * 因此本类也只暴露一个方法，不为了"通用客户端"而泛化。
 *
 * <h2>⚠️ 失败语义：吞掉异常，<b>不影响</b>发布结果</h2>
 * Go 侧：
 * <pre>
 * _, err = l.svcCtx.MessageRpc.SendNotification(...)
 * if err != nil {
 *     logger.LogBusinessErr(ctx, errmsg.ErrorServerCommon, ...)   // ← 只记日志
 * }
 * </pre>
 * 注意<b>通知是在事务提交之后</b>发的（Go {@code article_sync_result_consumer.go:160-163}
 * 在 {@code WithArticleTx} 闭包返回 nil 之后才调用），此时文章<b>已经发布成功</b>。
 * 若通知失败让整个消费失败，Kafka 会重投，重投会命中
 * {@code ExtPublishedOnce} 守卫变成 no-op —— 于是通知<b>永远补不上</b>，
 * 而日志里会出现误导性的"发布失败"。
 * ⇒ 本方法<b>吞掉所有异常并返回 false</b>，与 Go 同向。
 *
 * <h2>为什么是 {@code @GrpcClient} 而不是自己建 ManagedChannel</h2>
 * {@code grpc-client-spring-boot-starter} + Nacos discovery 已经承担了
 * 「按服务名寻址（{@code discovery://message-rpc}）+ channel 复用 + keepalive」，
 * 与 Go 的 {@code zrpc.MustNewClient} 等价。再手写 channel 属于重复实现框架已有的能力。
 *
 * <p>⚠️ 用的是 <b>BlockingStub</b>：Go 的 {@code SendNotification} 是同步阻塞调用，
 * 用 AsyncStub 会把调用变成 fire-and-forget，<b>改变可观察行为</b>。
 * 见 AGENTS.md DRIFT-MSGAPI-004 同款提醒：别把业务错误变成"立即成功"。
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
     * 对应 Go {@code notifyArticlePublished} 的完整调用。
     *
     * @param recipientId 作者 uid（Go 侧从 {@code article.AuthorID} 解析出的 int64）
     * @param articleId   文章 ID
     * @param title       文章标题；<b>空白时</b>走 Go 的无标题文案分支
     * @return true = RPC 正常返回；false = 失败（已记日志，调用方<b>不应</b>因此报错）
     */
    public boolean sendArticlePublished(long recipientId, String authorId, String articleId, String title) {
        // Go: content := "你的文章已经完成审核并成功入库。"
        //      if strings.TrimSpace(title) != "" { content = fmt.Sprintf("《%s》已经完成审核并成功入库。", strings.TrimSpace(title)) }
        // ⚠️ 两处都用 TrimSpace(title)：标题只有空白字符时也走无标题文案。
        String content = "你的文章已经完成审核并成功入库。";
        if (title != null && !title.trim().isEmpty()) {
            content = "《" + title.trim() + "》已经完成审核并成功入库。";
        }

        SendNotificationReq.Builder req = SendNotificationReq.newBuilder()
                // Go: RecipientIds: []int64{uid}
                .addRecipientIds(recipientId)
                // Go: Broadcast: false
                .setBroadcast(false)
                // Go: SenderId: 0 —— 系统消息，无发送人
                .setSenderId(0)
                // Go: SenderRole: messagepb.SenderRole_SYSTEM
                //    生成物里的 Java 常量名是 SenderRole.SYSTEM（proto 值名 SYSTEM，不是 SENDER_ROLE_SYSTEM）
                .setSenderRole(SenderRole.SYSTEM)
                // Go: Kind: messagepb.NotificationKind_ARTICLE_PUBLISHED
                .setKind(NotificationKind.ARTICLE_PUBLISHED)
                // Go: Title: "文章发布成功"
                .setTitle("文章发布成功")
                .setContent(content)
                // Go: Extra: map[string]string{"article_id":…, "author_id":…}
                .putExtra("article_id", articleId)
                // ⚠️ 必须是原串（Go :207），非 String.valueOf(recipientId)
                .putExtra("author_id", authorId);

        try {
            messageRpc().sendNotification(req.build());
            return true;
        } catch (StatusRuntimeException e) {
            // Go: logger.LogBusinessErr(..., errmsg.ErrorServerCommon, ...) —— 吞掉，不影响发布结果
            LOG.warn("send article published notification failed: article={} recipient={} err={}",
                    articleId, recipientId, e.getMessage());
            return false;
        }
    }

    /**
     * 🔴 <b>出向调用必须带 2s 客户端封顶</b>（2026-10-31 · G3 域侧自修 · Jason 拍板「修」）。
     *
     * <p><b>Go 出处</b>：{@code service/article/rpc/internal/mqs/article_sync_result_consumer.go:197}
     * 的 {@code l.svcCtx.MessageRpc.SendNotification(ctx, …)}
     * ⇒ ⇒ Go 侧 {@code zrpc.MustNewClient(c.MessageRpc)} 的客户端<b>无条件</b>装
     * {@code TimeoutInterceptor}（{@code zrpc/internal/client.go:113-117} ＋
     * {@code zrpc/internal/config.go:15} 的 {@code default=true}）
     * ⇒ ⇒ 每次调用都有 <b>2000ms</b> 上限（{@code zrpc/config.go:31} 的 {@code json:",default=2000"}）；
     * ⚠️ 本域 {@code service/article/rpc/etc/article.yaml} 的 {@code MessageRpc} 段
     * <b>没写 Timeout</b> ⇒ 取的就是这个 default ⇒ ⇒ <b>不是</b>我拍脑袋定的数。
     *
     * <p><b>修前的可观察分叉</b>：本类原先<b>直接用注入的 stub 发起调用</b> ⇒ <b>零期限</b>
     * ⇒ ⇒ 下游 {@code message-rpc} 挂死时 Go 2s 就返回、RAG <b>一路等到底</b>
     * ⇒ ⇒ 而本调用点<b>吞掉异常</b>（只记日志、返回 false）⇒ ⇒ 失败<b>时机</b>的差异不会向上冒泡
     * ⇒ ⇒ 表现为「消费变慢」而非「报错」⇒ ⇒ 更难被发现；
     * ⇒ ⇒ 更糟的是本调用点在 <b>Kafka consumer 线程</b>上
     * （调用点 {@code service/article/article/rpc/mq/ArticleSyncResultConsumer.java:213}，
     * 该类 {@code :80} 是 {@code @KafkaListener}）⇒ ⇒ 无限期阻塞会把<b>消费线程占死</b>
     * ⇒ ⇒ 该 partition 不再推进 ⇒ ⇒ 「文章发布成功」通知这条链路<b>整条停摆</b>。
     *
     * <p><b>🔴 为什么是「两段式」而不是只套 2s</b>：
     * <ul>
     *   <li><b>第一段 {@code GoStyleRequestDeadline.applyTo(stub)}</b>：把<b>入向</b> deadline 的
     *       剩余预算挂到出向 stub 上（父 deadline 参与 min）；
     *       ⚠️ <b>本出口跑在 Kafka consumer 线程上</b> ⇒ 共享层 {@code RpcInterceptor.onHalfClose}
     *       绑的 ThreadLocal 载体<b>在本线程不可见</b> ⇒ ⇒ 第一段在本出口<b>原样返回 stub</b>
     *       （不挂 deadline）⇒ ⇒ 净效果 = <b>恰好 2s 封顶</b>
     *       ⇒ ⇒ 与 Go 侧<b>同形</b>（Go 的 {@code article_sync_result_consumer.go:197} 传的
     *       也是 <b>Kafka 消费 ctx（无 deadline）</b> ⇒ 同样只有 2s 封顶）；</li>
     *   <li><b>第二段 {@code GoStyleClientCallTimeout.capByClientTimeout(…)}</b>：算
     *       {@code min(请求剩余, 2s)} ⇒ ⇒ <b>这一半是本域必须自己给的</b>
     *       （共享层拦截器层的 {@code GoStyleProtectionProperties.OutboundDeadline#capMillis}
     *       <b>默认 0 = 不启用</b>，全仓 {@code service/<域>/<组件>/src/main/resources/*.yml}
     *       的 {@code outbound-deadline} <b>零配置</b>）⇒ ⇒ 域侧漏了就是真漂移（RAG 比 Go 松）。</li>
     * </ul>
     *
     * <p>⚠️ <b>不要把返回值缓存到字段上</b>：{@code withDeadlineAfter} 每次返回新实例。
     */
    private MessageServiceGrpc.MessageServiceBlockingStub messageRpc() {
        return GoStyleClientCallTimeout.capByClientTimeout(
                GoStyleRequestDeadline.applyTo(stub));
    }
}
