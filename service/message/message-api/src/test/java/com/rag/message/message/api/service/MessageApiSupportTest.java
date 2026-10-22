package com.rag.message.message.api.service;

import com.rag.common.exception.BusinessException;
import com.rag.message.message.api.interceptor.AbstractMessageJwtAuthInterceptor;
import com.rag.message.message.common.errmsg.MessageErrorCode;
import com.rag.message.message.proto.NotificationKind;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.math.BigDecimal;
import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link MessageApiSupport} 的纯单测 —— 对应 Go {@code api/internal/logic/shared/helper.go}
 * 中<b>无法用 HTTP 请求构造</b>的那几条规则。
 *
 * <p><b>为什么 MVC 管线测试不够</b>：claim 的原始类型由 jjwt 的反序列化决定，
 * {@link MessageApiMvcPipelineTest} 能覆盖「没有 claim」与「字符串 claim」两种，
 * 但<b>浮点 / 超长 BigInteger</b> 这两类造不出一个能通过验签的 token，
 * 只能直接构造 request attribute 来验。
 *
 * <p>这一类是<b>越权风险</b>而不只是格式问题：Go 的
 * {@code json.Number.Int64()} 对 {@code 1.5} / {@code 1e2} 一律失败返回 5004；
 * 若 Java 侧直接 {@code ((Number) claim).longValue()}，这些输入会被<b>静默截断</b>成
 * 合法 uid（1），从而用错误的身份通过鉴权。
 */
class MessageApiSupportTest {

    private final MessageApiSupport support = new MessageApiSupport();

    private static MockHttpServletRequest requestWithClaim(Object claim) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(AbstractMessageJwtAuthInterceptor.CLAIM_ATTRIBUTE, claim);
        return request;
    }

    // ============== UserIDFromContext ==============

    @Test
    @DisplayName("整数 claim（Integer / Long / Short / Byte / BigInteger）都接受")
    void integralClaimsAreAccepted() {
        assertEquals(7L, support.userId(requestWithClaim(7)));
        assertEquals(7L, support.userId(requestWithClaim(7L)));
        assertEquals(7L, support.userId(requestWithClaim((short) 7)));
        assertEquals(7L, support.userId(requestWithClaim((byte) 7)));
        assertEquals(7L, support.userId(requestWithClaim(BigInteger.valueOf(7))));
    }

    @Test
    @DisplayName("claim 缺席 ⇒ 5004（Go 的 !ok 分支），不是 401 也不是 0")
    void missingClaimIs5004() {
        MockHttpServletRequest bare = new MockHttpServletRequest();
        BusinessException e = assertThrows(BusinessException.class, () -> support.userId(bare));
        assertEquals(MessageErrorCode.ERROR_UNAUTHORIZED.getCode(), e.getCode());
        assertEquals("unauthorized", e.getMessage());
    }

    @Test
    @DisplayName("null claim ⇒ 5004")
    void nullClaimIs5004() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> support.userId(requestWithClaim(null)));
        assertEquals(5004, e.getCode());
    }

    @Test
    @DisplayName("字符串 / 布尔 claim ⇒ 5004（Go 的类型断言目标是 json.Number）")
    void nonNumericClaimIs5004() {
        assertEquals(5004, assertThrows(BusinessException.class,
                () -> support.userId(requestWithClaim("7"))).getCode());
        assertEquals(5004, assertThrows(BusinessException.class,
                () -> support.userId(requestWithClaim(true))).getCode());
    }

    @Test
    @DisplayName("浮点 claim ⇒ 5004，绝不能被静默截断成整数（越权风险点）")
    void fractionalClaimIsRejectedNotTruncated() {
        // Go: json.Number("1.5").Int64() → strconv.ParseInt 失败 → 5004
        BusinessException e = assertThrows(BusinessException.class,
                () -> support.userId(requestWithClaim(1.5d)));
        assertEquals(5004, e.getCode());
        // 科学计数法：Go 的 json.Number 是 "1e2" 字面量，ParseInt 同样失败
        assertEquals(5004, assertThrows(BusinessException.class,
                () -> support.userId(requestWithClaim(100.0d))).getCode());
        assertEquals(5004, assertThrows(BusinessException.class,
                () -> support.userId(requestWithClaim(new BigDecimal("1.0")))).getCode());
    }

    @Test
    @DisplayName("超出 long 范围的 BigInteger ⇒ 5004（Go ParseInt 同样溢出失败）")
    void overflowingClaimIs5004() {
        BigInteger tooBig = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE);
        assertEquals(5004, assertThrows(BusinessException.class,
                () -> support.userId(requestWithClaim(tooBig))).getCode());
    }

    @Test
    @DisplayName("uid = 0 与负数 <b>照常透传</b>（Go 不在 api 层拒，由 rpc 判 5101/5102）")
    void zeroAndNegativeUidArePassedThrough() {
        assertEquals(0L, support.userId(requestWithClaim(0)));
        assertEquals(-1L, support.userId(requestWithClaim(-1L)));
    }

    // ============== RPCError ==============

    @Test
    @DisplayName("gRPC status：code 按 description 反查，msg 用 description 原文")
    void rpcErrorUsesDescriptionForBothCodeAndMsg() {
        BusinessException e = support.rpcError(new StatusRuntimeException(
                Status.PERMISSION_DENIED.withDescription("conversation not found")));
        assertEquals(MessageErrorCode.ERROR_CONVERSATION_MISS.getCode(), e.getCode());   // 5202
        assertEquals("conversation not found", e.getMessage());
    }

    @Test
    @DisplayName("description 反查不到 ⇒ 退回按 gRPC code class 兜底")
    void rpcErrorFallsBackToGrpcCodeClass() {
        BusinessException e = support.rpcError(new StatusRuntimeException(
                Status.UNAVAILABLE.withDescription("upstream connect error")));
        // description 不在业务码表里 → UNAVAILABLE 不在 switch 里 → CODE_SERVER_BUSY
        assertEquals(MessageErrorCode.CODE_SERVER_BUSY.getCode(), e.getCode());            // 1015
        // msg 仍取 description 原文（Go: st.Message() != "" 分支先于查表）
        assertEquals("upstream connect error", e.getMessage());
    }

    @Test
    @DisplayName("description 为空 ⇒ msg 退回按 code 查表（Go 的 NewErrCode 分支）")
    void rpcErrorFallsBackToMsgTableWhenDescriptionEmpty() {
        BusinessException e = support.rpcError(new StatusRuntimeException(
                Status.INVALID_ARGUMENT));
        assertEquals(MessageErrorCode.ERROR_INVALID_PARAM.getCode(), e.getCode());         // 5003
        assertEquals("invalid parameter", e.getMessage());
    }

    @Test
    @DisplayName("checked 的 StatusException 与 unchecked 的 StatusRuntimeException 都要能吃")
    void rpcErrorAcceptsBothStatusFlavours() {
        StatusException checked = Status.NOT_FOUND
                .withDescription("notification not found").asException();
        BusinessException e = support.rpcError(checked);
        assertEquals(MessageErrorCode.ERROR_NOTIFICATION_MISS.getCode(), e.getCode());     // 5203
        assertEquals("notification not found", e.getMessage());
    }

    @Test
    @DisplayName("非 status 异常 ⇒ 1015（Go BizCodeFromError 的最后一行）")
    void nonStatusErrorIs1015() {
        BusinessException e = support.rpcError(new IllegalStateException("boom"));
        assertEquals(MessageErrorCode.CODE_SERVER_BUSY.getCode(), e.getCode());
        assertEquals("service busy", e.getMessage());
    }

    // ============== AdminNotificationKind ==============

    @Test
    @DisplayName("kind > 0 原值优先（客户端可指定任意通知类型）")
    void explicitKindWins() {
        assertEquals(NotificationKind.ARTICLE_COMMENT.getNumber(),
                support.adminNotificationKind(false, NotificationKind.ARTICLE_COMMENT.getNumber()));
        assertEquals(NotificationKind.ARTICLE_LIKED.getNumber(),
                support.adminNotificationKind(true, NotificationKind.ARTICLE_LIKED.getNumber()));
    }

    @Test
    @DisplayName("kind = 0 ⇒ 按 broadcast 推断 ADMIN_BROADCAST / ADMIN_NOTICE")
    void zeroKindIsInferredFromBroadcast() {
        assertEquals(NotificationKind.ADMIN_BROADCAST.getNumber(), support.adminNotificationKind(true, 0));
        assertEquals(NotificationKind.ADMIN_NOTICE.getNumber(), support.adminNotificationKind(false, 0));
    }

    @Test
    @DisplayName("kind < 0 也走推断分支（Go 判的是 raw > 0，不是 raw != 0）")
    void negativeKindAlsoFallsBackToInference() {
        assertEquals(NotificationKind.ADMIN_NOTICE.getNumber(), support.adminNotificationKind(false, -1));
        assertEquals(NotificationKind.ADMIN_BROADCAST.getNumber(), support.adminNotificationKind(true, -1));
    }

    @Test
    @DisplayName("未定义的 kind（如 99）原值透传 —— proto3 开放枚举，Go 允许")
    void unknownKindIsPassedThroughVerbatim() {
        assertEquals(99, support.adminNotificationKind(false, 99));
        // 对照：Java 的 NotificationKind.forNumber(99) 会返回 null，所以必须走 int 通道
        assertEquals(null, NotificationKind.forNumber(99));
    }

    // ============== recipientIDs ==============

    @Test
    @DisplayName("广播 ⇒ 空 recipient 列表；定向 ⇒ 单元素")
    void recipientIdsMatchGoSemantics() {
        assertTrue(support.recipientIds(true, 9L).isEmpty());
        assertEquals(1, support.recipientIds(false, 9L).size());
        assertEquals(9L, support.recipientIds(false, 9L).get(0));
    }

    @Test
    @DisplayName("recipientIds 返回的列表可以被 protobuf 直接消费")
    void recipientIdsFeedProtoBuilderDirectly() {
        var req = com.rag.message.message.proto.SendNotificationReq.newBuilder()
                .addAllRecipientIds(support.recipientIds(false, 42L))
                .build();
        assertEquals(1, req.getRecipientIdsCount());
        assertEquals(42L, req.getRecipientIds(0));
        assertNotNull(req);
    }
}