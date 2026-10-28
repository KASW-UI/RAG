package com.rag.message.message.rpc;

import com.rag.common.spring.LoggerFactoryBean;
import com.rag.message.message.proto.MarkAllNotificationsReq;
import com.rag.message.message.proto.MarkConversationReadReq;
import com.rag.message.message.proto.MarkNotificationReq;
import com.rag.message.message.proto.MessageServiceGrpc;
import com.rag.message.message.proto.SenderRole;
import com.rag.message.message.rpc.client.AdminRpcClient;
import com.rag.message.message.rpc.client.UserRpcClient;
import com.rag.message.message.rpc.config.MessageProperties;
import com.rag.message.message.rpc.domain.ConversationEntity;
import com.rag.message.message.rpc.mapper.ConversationMapper;
import com.rag.message.message.rpc.mapper.ConversationMessageMapper;
import com.rag.message.message.rpc.mapper.NotificationMapper;
import com.rag.message.message.rpc.metrics.MessageMetrics;
import com.rag.message.message.rpc.service.MessageAtomicService;
import com.rag.message.message.rpc.service.MessageDomainService;
import com.rag.message.message.rpc.service.impl.MessageRpcServiceImpl;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * <b>阶段 1 补的守门：DB 失败必须以 UNKNOWN 出 wire —— 走<b>真实域服务</b>那条生产路径。</b>
 *
 * <h2>为什么已有守门不够（这是「跑绿 ≠ 对」的实例）</h2>
 *
 * <p>{@code MessageRpcUnknownStatusWiringTest}（DRIFT-MSG-V-024，2026-10-24）已经断言
 * 「DB 异常 ⇒ 客户端看到 UNKNOWN（不是 INTERNAL）」，且它<b>一直是绿的</b>。
 * ⇒ 但它把 {@code MessageDomainService} <b>整个 mock 掉</b>（{@code :74-75}
 * {@code when(domain.markNotificationRead(any())).thenThrow(thrownByDomain)}），
 * 断言的其实是「<b>假设</b>域服务原样上抛 ⇒ 兜底逻辑对」，
 * <b>而不是</b>「生产路径上域服务真的原样上抛」。
 *
 * <p>而 2026-10-28 阶段 1 复核发现：{@code MessageDomainService} 的 3 处 DB 失败路径
 * （markConversationRead / markNotificationRead / markAllNotificationsRead）
 * 把原始异常改写成 {@code INTERNAL + ERROR_DB_UPDATE(5008)} ⇒
 * <b>生产路径永远走不到那条 UNKNOWN 兜底</b> ⇒ 那条绿测试<b>守着一个不会发生的场景</b>。
 *
 * <h2>本类与已有守门的差别</h2>
 *
 * <p>本类<b>不 mock 域服务</b>：真的 {@link MessageDomainService} + 真的
 * {@link MessageRpcServiceImpl} + <b>真 in-process gRPC 往返</b> ⇒ 断言的是
 * <b>客户端在 wire 上真正看到的 status code</b>。
 *
 * <h2>Go 出处与语义等价论证</h2>
 *
 * <p>【Go 出处（消费点）】三处 DB 失败都是<b>裸 error</b>：
 * <ul>
 *   <li>{@code markconversationreadlogic.go:59-62} —— {@code metrics.ObserveDBError("conversation","mark_read"); return nil, err}</li>
 *   <li>{@code marknotificationreadlogic.go:50-51} —— {@code metrics.ObserveDBError("notification","mark_read"); return nil, err}</li>
 *   <li>{@code markallnotificationsreadlogic.go:42-44} —— {@code metrics.ObserveDBError("notification","mark_all_read"); return nil, err}</li>
 * </ul>
 * ⇒ grpc-go v1.81.1 的 {@code processUnaryRPC} 走 {@code status.FromContextError(appErr)}
 * ⇒ <b>codes.Unknown</b>（+ 原始错误文本）。
 *
 * <p>【为什么 UNKNOWN 重要 —— 熔断判据】go-zero v1.10.2
 * {@code zrpc/internal/codes/accept.go:9-17} 与本仓共享层
 * {@code GoStyleGrpcClientBreakerInterceptor.isAcceptable} 是<b>同一份名单</b>：
 * {@code {DEADLINE_EXCEEDED, INTERNAL, UNAVAILABLE, DATA_LOSS, UNIMPLEMENTED, RESOURCE_EXHAUSTED}}
 * 判「失败」，其余（含 {@code UNKNOWN}）判「成功」。
 * ⇒ <b>同一个 DB 故障：Go 记成功（不开路）；RAG 若给 INTERNAL 则记失败（开路）</b>
 * ⇒ 两侧开路决策分岔 ⇒ 这正是 P0 第 4 条「失败路径明确」要防的那种偏差。
 *
 * <p>【Go 的 5008 是「声明但从不用」】Go {@code service/message/common/errmsg.go:39}
 * 定义了 {@code ErrorDbUpdate = 5008}，但<b>全仓没有任何 logic 路径引用它</b>
 * （{@code 5007 ErrorDbSelect} 同款）⇒ Go 的 DB 失败从来不是 5008。
 */
@DisplayName("阶段 1：DB 失败 ⇒ 客户端看到 UNKNOWN（走真实域服务这条生产路径）")
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class MessageDomainServiceDbFailureStatusTest {

    private static final String SERVER_NAME = "db-failure-message-rpc";

    /** Go 侧 DB 失败的原始错误文本（GORM/pq 的真实形态）。 */
    private static final String GO_RAW_DB_ERROR =
            "ERROR: relation \"message_notification\" does not exist (SQLSTATE 42P01)";

    private Server server;
    private io.grpc.ManagedChannel channel;
    private MessageAtomicService atomicService;

    @BeforeEach
    void setUp() throws Exception {
        // ① 原子服务：三个 mark 方法都抛**裸** DB 异常（模拟真 PG 故障）
        atomicService = mock(MessageAtomicService.class);
        RuntimeException raw = new IllegalStateException(GO_RAW_DB_ERROR);
        when(atomicService.markNotificationReadTx(anyLong(), anyLong(), any())).thenThrow(raw);
        org.mockito.Mockito.doThrow(raw).when(atomicService)
                .markConversationReadTx(anyLong(), anyLong(), anyInt(), any());
        org.mockito.Mockito.doThrow(raw).when(atomicService)
                .markAllNotificationsReadTx(anyLong(), anyInt(), anyInt(), any());

        // ② 真实域服务（只 mock 它的协作者）
        NotificationMapper notificationMapper = mock(NotificationMapper.class);
        ConversationMapper conversationMapper = mock(ConversationMapper.class);
        ConversationMessageMapper messageMapper = mock(ConversationMessageMapper.class);

        ConversationEntity conv = new ConversationEntity();
        conv.setId(1L);
        conv.setParticipant1Role(SenderRole.USER.getNumber());
        conv.setParticipant1Id(7L);
        conv.setParticipant2Role(SenderRole.ADMIN.getNumber());
        conv.setParticipant2Id(9L);
        when(conversationMapper.findByIdOpt(anyLong())).thenReturn(Optional.of(conv));

        MessageDomainService domain = new MessageDomainService(
                notificationMapper,
                conversationMapper,
                messageMapper,
                mock(UserRpcClient.class),
                mock(AdminRpcClient.class),
                new MessageProperties(),
                new MessageMetrics(new SimpleMeterRegistry()),
                mock(LoggerFactoryBean.class),
                atomicService);

        // ③ 真实 gRPC 服务实现 + 真 in-process server/channel
        MessageRpcServiceImpl impl = new MessageRpcServiceImpl(domain);
        server = InProcessServerBuilder.forName(SERVER_NAME).directExecutor()
                .addService(new MessageServiceGrpc.MessageServiceImplBase() {
                    @Override
                    public void markNotificationRead(MarkNotificationReq request,
                                                     StreamObserver<com.rag.message.message.proto.BaseResp> observer) {
                        impl.markNotificationRead(request, observer);
                    }

                    @Override
                    public void markConversationRead(MarkConversationReadReq request,
                                                     StreamObserver<com.rag.message.message.proto.BaseResp> observer) {
                        impl.markConversationRead(request, observer);
                    }

                    @Override
                    public void markAllNotificationsRead(MarkAllNotificationsReq request,
                                                         StreamObserver<com.rag.message.message.proto.BaseResp> observer) {
                        impl.markAllNotificationsRead(request, observer);
                    }
                })
                .build()
                .start();
        channel = InProcessChannelBuilder.forName(SERVER_NAME).directExecutor().build();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (channel != null) {
            channel.shutdownNow();
            channel.awaitTermination(5, TimeUnit.SECONDS);
        }
        if (server != null) {
            server.shutdownNow();
        }
    }

    @Test
    @DisplayName("🔴 markNotificationRead 的 DB 失败 ⇒ UNKNOWN（不是 INTERNAL，也不是 5008）")
    void markNotificationReadDbFailureMustSurfaceAsUnknown() {
        MessageServiceGrpc.MessageServiceBlockingStub stub =
                MessageServiceGrpc.newBlockingStub(channel).withDeadlineAfter(10, TimeUnit.SECONDS);

        StatusRuntimeException e = assertThrows(StatusRuntimeException.class,
                () -> stub.markNotificationRead(MarkNotificationReq.newBuilder()
                        .setNotificationId(1L).setUserId(7L).build()));

        assertEquals(Status.Code.UNKNOWN, e.getStatus().getCode(),
                "Go: marknotificationreadlogic.go:50-51 原样返回 err ⇒ grpc-go status.FromContextError ⇒ codes.Unknown。"
                        + "RAG 若给 INTERNAL ⇒ INTERNAL 落在 Go 那 6 码失败名单里 ⇒ RAG 的熔断器会比 Go 灵敏得多。"
                        + "实际=" + e.getStatus().getCode());
    }

    @Test
    @DisplayName("🔴 markConversationRead 的 DB 失败 ⇒ UNKNOWN")
    void markConversationReadDbFailureMustSurfaceAsUnknown() {
        MessageServiceGrpc.MessageServiceBlockingStub stub =
                MessageServiceGrpc.newBlockingStub(channel).withDeadlineAfter(10, TimeUnit.SECONDS);

        StatusRuntimeException e = assertThrows(StatusRuntimeException.class,
                () -> stub.markConversationRead(MarkConversationReadReq.newBuilder()
                        .setConversationId(1L).setOperatorId(7L).setOperatorRole(SenderRole.USER).build()));

        assertEquals(Status.Code.UNKNOWN, e.getStatus().getCode(),
                "Go: markconversationreadlogic.go:59-62 原样返回 err ⇒ codes.Unknown。实际=" + e.getStatus().getCode());
    }

    @Test
    @DisplayName("🔴 markAllNotificationsRead 的 DB 失败 ⇒ UNKNOWN")
    void markAllNotificationsReadDbFailureMustSurfaceAsUnknown() {
        MessageServiceGrpc.MessageServiceBlockingStub stub =
                MessageServiceGrpc.newBlockingStub(channel).withDeadlineAfter(10, TimeUnit.SECONDS);

        StatusRuntimeException e = assertThrows(StatusRuntimeException.class,
                () -> stub.markAllNotificationsRead(MarkAllNotificationsReq.newBuilder()
                        .setUserId(7L).build()));

        assertEquals(Status.Code.UNKNOWN, e.getStatus().getCode(),
                "Go: markallnotificationsreadlogic.go:42-44 原样返回 err ⇒ codes.Unknown。实际=" + e.getStatus().getCode());
    }

    @Test
    @DisplayName("⚠️ DB 失败的原始错误文本不得进 wire（DRIFT-MSG-V-003 已签字只回码表文案）")
    void rawDbErrorTextMustNotLeakIntoTheStatus() {
        MessageServiceGrpc.MessageServiceBlockingStub stub =
                MessageServiceGrpc.newBlockingStub(channel).withDeadlineAfter(10, TimeUnit.SECONDS);

        StatusRuntimeException e = assertThrows(StatusRuntimeException.class,
                () -> stub.markNotificationRead(MarkNotificationReq.newBuilder()
                        .setNotificationId(1L).setUserId(7L).build()));

        String description = e.getStatus().getDescription();
        assertNull(description,
                "description 不得携带原始错误文本（Go 的 api 会把它原文塞进 envelope ⇒ 信息泄露）。实际=" + description);
    }
}
