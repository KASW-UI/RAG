package com.rag.message.message.rpc.service.impl;

import com.rag.message.message.proto.BaseResp;
import com.rag.message.message.proto.ConversationListReq;
import com.rag.message.message.proto.ConversationListResp;
import com.rag.message.message.proto.ConversationMessageListReq;
import com.rag.message.message.proto.ConversationMessageListResp;
import com.rag.message.message.proto.UnreadSummaryReq;
import com.rag.message.message.proto.UnreadSummaryResp;
import com.rag.message.message.proto.NotificationListReq;
import com.rag.message.message.proto.NotificationListResp;
import com.rag.message.message.proto.MarkAllNotificationsReq;
import com.rag.message.message.proto.MarkConversationReadReq;
import com.rag.message.message.proto.MarkNotificationReq;
import com.rag.message.message.proto.MessageServiceGrpc;
import com.rag.message.message.proto.SendChatMessageReq;
import com.rag.message.message.proto.SendChatMessageResp;
import com.rag.message.message.proto.SendNotificationReq;
import com.rag.message.message.proto.SendNotificationResp;
import com.rag.message.message.rpc.service.MessageDomainService;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

/**
 * MessageService gRPC 实现 —— 9 个 RPC 方法的 thin wrapper。
 *
 * <p>对应 Go: service/message/rpc/internal/server/messageserviceserver.go
 *   （go-zero 自动生成的 MessageServiceServer + svc 注入的 Logic）
 *
 * <p>对应 Java 版：
 * <ul>
 *   <li>{@link MessageServiceGrpc.MessageServiceImplBase} 由 protobuf-maven-plugin 生成，
 *       包含 9 个 RPC method abstract 方法</li>
 *   <li>本类继承它，对每个 method override 实现：把请求委派给 {@link MessageDomainService}</li>
 *   <li>{@link GrpcService} 让 devh grpc-server-spring-boot-starter 自动注册到 gRPC server</li>
 *   <li>每个 method 通过 {@link #invoke} 包装 Supplier，把 Logic 抛出的
 *       {@link StatusRuntimeException} 直接转发，其它异常兜底为 INTERNAL + ErrorServerCommon</li>
 * </ul>
 *
 * <p>DI 风格：{@link MessageDomainService} 通过 Spring DI 注入；Logic 是单例 Bean，
 * 方法本身 stateless，**不需要每次请求 new**（Go 端用 {@code logic.NewXxxLogic(ctx, s.svcCtx)}）。
 *
 * <p>9 个 RPC method：
 * <ul>
 *   <li>通知域：{@code sendNotification} / {@code listNotifications} /
 *       {@code markNotificationRead} / {@code markAllNotificationsRead} /
 *       {@code getUnreadSummary}</li>
 *   <li>会话域：{@code listConversations} / {@code getConversationMessages} /
 *       {@code sendChatMessage} / {@code markConversationRead}</li>
 * </ul>
 *
 * <p>框架职责：grpc-java 框架机制约束（一个 service 一个实现类）—— 非主动合并；
 * 9 个方法体从镜像 9 Logic 翻译后合并到 {@link MessageDomainService}，这里只做 thin wrapper。
 */
@GrpcService
public class MessageRpcServiceImpl extends MessageServiceGrpc.MessageServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(MessageRpcServiceImpl.class);

    private final MessageDomainService domain;

    public MessageRpcServiceImpl(MessageDomainService domain) {
        this.domain = domain;
    }

    @Override
    public void sendChatMessage(SendChatMessageReq request, StreamObserver<SendChatMessageResp> responseObserver) {
        invoke(() -> domain.sendChatMessage(request), responseObserver);
    }

    @Override
    public void sendNotification(SendNotificationReq request, StreamObserver<SendNotificationResp> responseObserver) {
        invoke(() -> domain.sendNotification(request), responseObserver);
    }

    @Override
    public void listConversations(ConversationListReq request, StreamObserver<ConversationListResp> responseObserver) {
        invoke(() -> domain.listConversations(request), responseObserver);
    }

    @Override
    public void getConversationMessages(ConversationMessageListReq request, StreamObserver<ConversationMessageListResp> responseObserver) {
        invoke(() -> domain.getConversationMessages(request), responseObserver);
    }

    @Override
    public void markConversationRead(MarkConversationReadReq request, StreamObserver<BaseResp> responseObserver) {
        invoke(() -> domain.markConversationRead(request), responseObserver);
    }

    @Override
    public void listNotifications(NotificationListReq request, StreamObserver<NotificationListResp> responseObserver) {
        invoke(() -> domain.listNotifications(request), responseObserver);
    }

    @Override
    public void markNotificationRead(MarkNotificationReq request, StreamObserver<BaseResp> responseObserver) {
        invoke(() -> domain.markNotificationRead(request), responseObserver);
    }

    @Override
    public void markAllNotificationsRead(MarkAllNotificationsReq request, StreamObserver<BaseResp> responseObserver) {
        invoke(() -> domain.markAllNotificationsRead(request), responseObserver);
    }

    @Override
    public void getUnreadSummary(UnreadSummaryReq request, StreamObserver<UnreadSummaryResp> responseObserver) {
        invoke(() -> domain.getUnreadSummary(request), responseObserver);
    }

    /**
     * 对应镜像 follow-rpc / user-rpc 的 invoke 模板：把 DomainService 调用包成 try/catch 转发给 StreamObserver。
     *
     * <p>关键：DomainService 已经把业务异常用 {@link com.rag.message.message.rpc.service.MessageRpcSupport#grpcRuntimeError}
     * 包成 {@link StatusRuntimeException}，这里直接 catch 并 {@code responseObserver.onError(...)}；
     * 其它 RuntimeException 兜底为 <b>UNKNOWN</b>（2026-10-24 由 INTERNAL 改来，见下方长注释）。
     */
    private static <Resp> void invoke(Supplier<Resp> handler, StreamObserver<Resp> observer) {
        try {
            Resp resp = handler.get();
            observer.onNext(resp);
            observer.onCompleted();
        } catch (StatusRuntimeException sre) {
            // DomainService 已经把业务错误包成 gRPC status error，直接转发
            observer.onError(sre);
        } catch (RuntimeException e) {
            log.error("message-rpc handler failed", e);
            // ⚠️⚠️ **兜底码必须是 UNKNOWN，不是 INTERNAL**（DRIFT-MSG-V-024，2026-10-24 修）
            //
            // 【Go 行为（消费点，不是声明点）】
            //   ① message-rpc 的业务代码在 DB / 未知异常路径上**原样返回普通 error**
            //      （service/message/rpc/internal/logic/**：**共 19 处** ObserveDBError 之后
            //       `return nil, err`，不包 status）⇒ grpc-go v1.81.1 的
            //       `processUnaryRPC` 走 `status.FromContextError(appErr)` ⇒
            //       **codes.Unknown + 原始错误文本**。
            //   ② go-zero 的熔断判据 `codes.Acceptable(err)` 只对
            //      {DEADLINE_EXCEEDED, INTERNAL, UNAVAILABLE, DATA_LOSS, UNIMPLEMENTED,
            //       RESOURCE_EXHAUSTED} 这 6 个码判「失败」
            //      （zrpc/internal/codes/accept.go:9-17）
            //      ⇒ **UNKNOWN 不在名单里 ⇒ Go 的 DB 故障不触发熔断**。
            //
            // 【改之前的 RAG 行为】本方法把 RuntimeException 统一转成
            //   `Status.INTERNAL + "internal server error"` ⇒
            //   ① 客户端看到的 status 码与 Go 不同（INTERNAL vs UNKNOWN）；
            //   ② **INTERNAL 在那 6 码名单里 ⇒ RAG 的熔断器会把 DB 故障记成失败**
            //      ⇒ 同一个 DB 故障，两侧的开路决策分岔（RAG 灵敏得多）。
            //
            // 【为什么 description 留空（null），而不是抄 Go 的原始错误文本】
            //   Go 的 description 是**原始错误文本**（如 `pq: relation "x" does not exist`），
            //   而 api 侧的 `shared.RPCError` 会把 description **原文**塞进 HTTP envelope 的 msg
            //   ⇒ Go 侧客户端能直接看到 SQL 错误文本（**信息泄露**）。
            //   本仓 `DRIFT-MSG-V-003` 已就这一点**签字保留 RAG 侧**：
            //   envelope 只回码表文案，不回显原始错误文本。
            //   ⇒ 这里若把原始文本抄过来，等于在**签字之外**又引入一次泄露。
            //   ⇒ description 留空 ⇒ api 侧 `bizCodeFromError` 走
            //      「反查未命中 ⇒ 按 status code class 兜底」这一支
            //      （Go 侧对非 status 错误走的是**同一支**：`status.FromError` 返回 ok=false
            //        ⇒ 直接 `CodeServerBusy`）⇒ **业务码 1015 与 Go 一致**，
            //      且 envelope 的 msg 由码表给出（"service busy"），**不泄露原始文本**。
            observer.onError(new StatusRuntimeException(Status.UNKNOWN.withCause(e)));
        }
    }
}