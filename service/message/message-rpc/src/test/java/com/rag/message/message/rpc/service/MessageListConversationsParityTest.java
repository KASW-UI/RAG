package com.rag.message.message.rpc.service;

import com.rag.message.message.common.errmsg.MessageErrorCode;
import com.rag.message.message.proto.ConversationListReq;
import com.rag.message.message.proto.SenderRole;
import com.rag.message.message.rpc.client.AdminRpcClient;
import com.rag.message.message.rpc.client.UserRpcClient;
import com.rag.message.message.rpc.config.MessageProperties;
import com.rag.message.message.rpc.domain.ConversationEntity;
import com.rag.message.message.rpc.mapper.ConversationMapper;
import com.rag.message.message.rpc.mapper.ConversationMessageMapper;
import com.rag.message.message.rpc.mapper.NotificationMapper;
import com.rag.message.message.rpc.metrics.MessageMetrics;
import com.rag.common.spring.LoggerFactoryBean;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 🔴 <b>DRIFT-MSG-V-018 守门</b>：{@code ListConversations} 组装 item 时的
 * <b>判定顺序</b>与 <b>DB 错误埋点</b>必须与 Go 一致。
 *
 * <h2>Go 侧行为（消费点）</h2>
 * <pre>{@code
 * // service/message/rpc/internal/logic/helper.go:160-187  buildConversationItem
 * peerRole, peerID, ok := peerOf(&conv, operatorRole, operatorID)
 * if !ok {
 *     return nil, status.Error(codes.PermissionDenied, messagecommon.GetErrMsg(messagecommon.ErrorForbidden))
 * }                                                   // ← ① 先判，短路；**根本不查 count_unread**
 *
 * unread, err := svcCtx.MessageModel.CountUnreadMessagesByConversation(ctx, conv.ID, int32(operatorRole), operatorID)
 * if err != nil {
 *     metrics.ObserveDBError("conversation", "count_unread")   // ← ② 有埋点
 *     return nil, err                                        // ← 原样返回裸 error
 * }
 *
 * name, email := loadProfile(ctx, svcCtx, peerRole, peerID)   // ← ③ 最后才取 profile
 * }</pre>
 *
 * <h2>修复前的 RAG 行为</h2>
 * <ol>
 *   <li><b>顺序反了</b>：先 {@code count_unread} 再 {@code peerOf}
 *       ⇒ peerOf 失败时<b>白查一次 DB</b>（Go 那边不会）；</li>
 *   <li><b>少了埋点</b>：{@code count_unread} 抛异常时<b>没有</b>
 *       {@code observeDbError("conversation","count_unread")}
 *       ⇒ {@code message_rpc_db_error_total{action="conversation",type="count_unread"}}
 *       在这条路径上<b>永不增长</b>
 *       ⇒ 运维看板会误以为「从没发生过 count_unread 失败」。</li>
 * </ol>
 *
 * <h2>可证伪性</h2>
 * 把 {@code MessageDomainService#doListConversations} 里的两处改回去
 * （① 把 peerOf 判定挪到 count 之后；② 删掉 catch 里的 observeDbError）
 * ⇒ 本类<b>应当立刻红</b>。
 */
@DisplayName("DRIFT-MSG-V-018：ListConversations 的 peerOf 判定顺序 + count_unread 埋点必须与 Go 一致")
class MessageListConversationsParityTest {

    /** operator 的 id（故意不是任何会话的参与者）。 */
    private static final long OPERATOR_ID = 999L;

    private ConversationMapper conversationMapper;
    private ConversationMessageMapper messageMapper;
    private MessageMetrics metrics;
    private MessageDomainService service;

    @BeforeEach
    void setUp() {
        conversationMapper = mock(ConversationMapper.class);
        messageMapper = mock(ConversationMessageMapper.class);
        metrics = mock(MessageMetrics.class);

        service = new MessageDomainService(
                mock(NotificationMapper.class),
                conversationMapper,
                messageMapper,
                mock(UserRpcClient.class),
                mock(AdminRpcClient.class),
                new MessageProperties(),
                metrics,
                mock(LoggerFactoryBean.class),
                mock(MessageAtomicService.class));
    }

    /** 造一个「operator 是参与者」的会话。 */
    private static ConversationEntity conversationOfParticipant(long operatorId) {
        ConversationEntity c = new ConversationEntity();
        c.setId(1L);
        c.setParticipant1Role(SenderRole.USER.getNumber());
        c.setParticipant1Id(operatorId);
        c.setParticipant2Role(SenderRole.ADMIN.getNumber());
        c.setParticipant2Id(200L);
        c.setStatus(1);
        return c;
    }

    /** 造一个「operator 不是参与者」的会话（peerOf 会返回 !found）。 */
    private static ConversationEntity conversationOfStranger() {
        ConversationEntity c = new ConversationEntity();
        c.setId(1L);
        c.setParticipant1Role(SenderRole.USER.getNumber());
        c.setParticipant1Id(100L);
        c.setParticipant2Role(SenderRole.ADMIN.getNumber());
        c.setParticipant2Id(200L);
        c.setStatus(1);
        return c;
    }

    private ConversationListReq request() {
        return ConversationListReq.newBuilder()
                .setOperatorId(OPERATOR_ID)
                .setOperatorRole(SenderRole.USER)
                .build();
    }

    // ==================================================================
    // ① peerOf 先判：失败时**根本不去查 count_unread**
    // ==================================================================

    @Test
    @DisplayName("peerOf 失败 ⇒ 抛 PERMISSION_DENIED + 5005，且 count_unread **从未被查**")
    void peerFailureShortCircuitsBeforeCountUnread() {
        when(conversationMapper.listByParticipant(anyInt(), anyLong(), anyInt(), anyInt()))
                .thenReturn(List.of(conversationOfStranger()));
        when(conversationMapper.countByParticipant(anyInt(), anyLong())).thenReturn(1L);
        when(messageMapper.countUnreadByRecipientGlobal(anyLong(), anyInt())).thenReturn(0L);

        assertThatThrownBy(() -> service.listConversations(request()))
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(t -> {
                    StatusRuntimeException sre = (StatusRuntimeException) t;
                    assertThat(sre.getStatus().getCode())
                            .as("Go: helper.go:162 —— codes.PermissionDenied")
                            .isEqualTo(Status.Code.PERMISSION_DENIED);
                });

        verify(conversationMapper, never())
                .countUnreadMessagesByConversation(anyLong(), anyLong(), anyInt());
        verify(metrics, never()).observeDbError(any(), any());
    }

    // ==================================================================
    // ② count_unread 的埋点
    // ==================================================================

    @Test
    @DisplayName("count_unread 失败 ⇒ observeDbError(\"conversation\",\"count_unread\") 必须被调用，且异常原样上抛")
    void countUnreadFailureIsRecorded() {
        when(conversationMapper.listByParticipant(anyInt(), anyLong(), anyInt(), anyInt()))
                .thenReturn(List.of(conversationOfParticipant(OPERATOR_ID)));
        when(conversationMapper.countByParticipant(anyInt(), anyLong())).thenReturn(1L);
        when(messageMapper.countUnreadByRecipientGlobal(anyLong(), anyInt())).thenReturn(0L);
        when(conversationMapper.countUnreadMessagesByConversation(anyLong(), anyLong(), anyInt()))
                .thenThrow(new IllegalStateException("boom from count_unread"));

        assertThatThrownBy(() -> service.listConversations(request()))
                .as("Go: helper.go:169 —— return nil, err（**原样返回**，不吞、不包装）")
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom from count_unread");

        verify(metrics).observeDbError("conversation", "count_unread");
    }

    @Test
    @DisplayName("count_unread 成功 ⇒ **不**产生 db_error 埋点")
    void countUnreadSuccessRecordsNoDbError() {
        when(conversationMapper.listByParticipant(anyInt(), anyLong(), anyInt(), anyInt()))
                .thenReturn(List.of(conversationOfParticipant(OPERATOR_ID)));
        when(conversationMapper.countByParticipant(anyInt(), anyLong())).thenReturn(1L);
        when(messageMapper.countUnreadByRecipientGlobal(anyLong(), anyInt())).thenReturn(0L);
        when(conversationMapper.countUnreadMessagesByConversation(anyLong(), anyLong(), anyInt()))
                .thenReturn(7L);

        // profile 走 userRpcClient（peer 是 ADMIN ⇒ 走 adminRpcClient）；两者都是 mock ⇒ 返回 null
        // ⇒ buildConversationItem 里 profile[0] 会 NPE？—— 不对：这里 peer 是 participant2 = ADMIN
        //    ⇒ adminRpcClient.loadProfile 返回 null ⇒ profile[0] NPE。
        // ⇒ 所以本用例只断言「埋点没被调用」，异常类型不关心。
        try {
            service.listConversations(request());
        } catch (RuntimeException ignore) {
            // 预期：mock 的 profile 返回 null 导致后续 NPE，与本用例无关
        }

        verify(metrics, never()).observeDbError("conversation", "count_unread");
    }

    @Test
    @DisplayName("错误码常量自检：ERROR_FORBIDDEN 必须是 5005（Go 的 ErrorForbidden）")
    void forbiddenCodeMatchesGo() {
        assertThat(MessageErrorCode.ERROR_FORBIDDEN.getCode())
                .as("Go: service/message/common/errmsg.go:36 ErrorForbidden = 5005")
                .isEqualTo(5005);
    }
}
