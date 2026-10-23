package com.rag.message.message.common.errmsg;

import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link MessageGrpcError} 与 Go {@code BizCodeFromError} / {@code GRPCError} 的对照测试。
 *
 * <p><b>Go 出处（消费点）</b>：{@code Sea-RideTheWind_golang/service/message/common/errmsg.go}
 * <pre>
 *   :75-86   bizCodeByMsg 反查表（排除 200/500，first-write-wins）
 *   :88-93   GetErrMsg
 *   :95-97   GRPCError(grpcCode, bizCode) = status.Error(grpcCode, GetErrMsg(bizCode))
 *   :99-132  BizCodeFromError
 * </pre>
 *
 * <h3>本类存在的三条理由（按重要性）</h3>
 * <ol>
 *   <li><b>本模块此前一条测试都没有</b>，而 message-api 的 javadoc 却声称「已有 focused tests」
 *       —— 一句实测为假的断言（AGENTS.md §3.3.2）。</li>
 *   <li><b>解包语义</b>：Go 侧 {@code status.FromError} 内部有 {@code errors.As}，
 *       会<b>沿 cause 链</b>找 status；RAG 原实现只做 {@code instanceof}，
 *       ⇒ 任何被包一层的 status 错误（gRPC-Java 异步 stub 抛 {@code CompletionException}、
 *       Spring {@code @Async}）都会落到 {@code 1015}，而 Go 会按 code class 正常映射。
 *       这条是 {@code DRIFT-MSG-COMMON-001}。</li>
 *   <li><b>判定顺序</b>：Go 是「先 CodeError，后 status」；RAG 原实现写反了。
 *       今天两类互斥所以不可观察，但顺序写反 = 依赖巧合，迟早出事。</li>
 * </ol>
 */
class MessageGrpcErrorTest {

    // ==================================================================
    // 1. Go 侧 null / 兜底分支
    // ==================================================================

    @Test
    @DisplayName("null error → 200（Go: if err == nil { return Success }）")
    void nullErrorIsSuccess() {
        assertEquals(200, MessageGrpcError.bizCodeFromError(null));
    }

    @Test
    @DisplayName("非 status 的普通异常 → 1015（Go: !ok ⇒ CodeServerBusy）")
    void plainThrowableFallsBackToServerBusy() {
        assertEquals(1015, MessageGrpcError.bizCodeFromError(new IllegalStateException("boom")));
    }

    @Test
    @DisplayName("cause 链上完全没有 status 时 statusMessage 为 null（Go: ok=false）")
    void plainThrowableHasNoStatusMessage() {
        assertNull(MessageGrpcError.statusMessage(new IllegalStateException("boom")));
        assertNull(MessageGrpcError.statusMessage(new RuntimeException("outer",
                new IllegalArgumentException("inner"))));
    }

    // ==================================================================
    // 2. CodeError 分支（Go: errmsg.go:104-106，**先于** status 判定）
    // ==================================================================

    @Test
    @DisplayName("CodeError 原样返回自己的 code（不看表、不看 gRPC code）")
    void codeErrorReturnsItsOwnCode() {
        assertEquals(5202, MessageGrpcError.bizCodeFromError(
                MessageGrpcError.newCode(MessageErrorCode.ERROR_CONVERSATION_MISS.getCode())));
        assertEquals(5202, MessageGrpcError.bizCodeFromError(
                MessageGrpcError.newCodeMsg(5202, "自定义文案也会被丢掉")));
    }

    @Test
    @DisplayName("newCode 走表、newCodeMsg 不走表（Go: NewErrCode vs NewErrCodeMsg）")
    void factoriesMatchGo() {
        assertEquals("conversation not found", MessageGrpcError.newCode(5202).getMsg());
        assertEquals("随便什么", MessageGrpcError.newCodeMsg(5202, "随便什么").getMsg());
    }

    /**
     * Go 的 {@code CodeError.Error()} 是 {@code "ErrCode:%d, Errmsg:%s"}。
     * RAG 把它放在 {@code RuntimeException#getMessage()} 上 ⇒ 日志 grep 与 Go 一致。
     */
    @Test
    @DisplayName("CodeError 的 message 字符串格式与 Go 的 Error() 一致")
    void codeErrorMessageFormatMatchesGo() {
        assertEquals("ErrCode:5202, Errmsg:conversation not found",
                MessageGrpcError.newCode(5202).getMessage());
    }

    // ==================================================================
    // 3. status 分支：description 反查 vs code class 兜底
    // ==================================================================

    @Test
    @DisplayName("description 命中反查表 → 直接得到业务码（Go: bizCodeByMsg[st.Message()]）")
    void statusDescriptionHitsReverseTable() {
        // message-rpc 用 GRPCError(codes.X, bizCode) 把业务码藏进 description
        Throwable err = new StatusRuntimeException(
                Status.PERMISSION_DENIED.withDescription("forbidden"));
        assertEquals(5005, MessageGrpcError.bizCodeFromError(err));
        assertEquals("forbidden", MessageGrpcError.statusMessage(err));
    }

    @Test
    @DisplayName("description 不在表里 → 按 gRPC code class 兜底（Go 的 switch 六条）")
    void statusCodeClassFallback() {
        assertEquals(5003, MessageGrpcError.bizCodeFromError(
                new StatusRuntimeException(Status.INVALID_ARGUMENT.withDescription("weird"))));
        assertEquals(5004, MessageGrpcError.bizCodeFromError(
                new StatusRuntimeException(Status.UNAUTHENTICATED)));
        assertEquals(5002, MessageGrpcError.bizCodeFromError(
                new StatusRuntimeException(Status.NOT_FOUND.withDescription("weird"))));
        assertEquals(5006, MessageGrpcError.bizCodeFromError(
                new StatusRuntimeException(Status.ALREADY_EXISTS)));
        assertEquals(5001, MessageGrpcError.bizCodeFromError(
                new StatusRuntimeException(Status.INTERNAL)));
    }

    @Test
    @DisplayName("switch 未列出的 code → 1015（Go: default ⇒ CodeServerBusy）")
    void unlistedGrpcCodeFallsBackToServerBusy() {
        assertEquals(1015, MessageGrpcError.bizCodeFromError(
                new StatusRuntimeException(Status.RESOURCE_EXHAUSTED)));
        assertEquals(1015, MessageGrpcError.bizCodeFromError(
                new StatusRuntimeException(Status.UNAVAILABLE)));
        assertEquals(1015, MessageGrpcError.bizCodeFromError(
                new StatusRuntimeException(Status.UNKNOWN)));
    }

    /** checked 的 StatusException 与 unchecked 的 StatusRuntimeException 两端都要兜住。 */
    @Test
    @DisplayName("StatusException 与 StatusRuntimeException 走同一条路径")
    void bothStatusExceptionKindsAreRecognized() {
        Status s = Status.NOT_FOUND.withDescription("notification not found");
        assertEquals(5203, MessageGrpcError.bizCodeFromError(s.asException()));
        assertEquals(5203, MessageGrpcError.bizCodeFromError(new StatusRuntimeException(s)));
    }

    // ==================================================================
    // 4. 🔴 DRIFT-MSG-COMMON-001：沿 cause 链解包
    // ==================================================================

    /**
     * Go 出处：grpc-go v1.81.1 {@code status.FromError} 的第二段
     * {@code if errors.As(err, &gs) { … }} ⇒ <b>解包</b>。
     * <p>修复前 RAG 只做 {@code instanceof}，下面四种包装全部落到 1015。
     *
     * <p>🔴 <b>注意期望值为什么是 5002 而不是 5203</b>（本轮实测差点写错，值得记住）：
     * 被包一层后 Go 会执行 {@code p.Message = err.Error()}，description 被
     * <b>外层文本</b>覆盖 ⇒ 「notification not found」不再是 status 的 message
     * ⇒ 反查<b>落空</b> ⇒ 退到 {@code codes.NotFound → ErrorNotFound(5002)}。
     * <b>同一个错误，直接抛与包一层，业务码不同</b> —— 这不是 RAG 的自由度，是 Go 的实际行为。
     */
    @Test
    @DisplayName("🔴 被包一层的 status 错误仍被识别（反查落空 ⇒ 按 code class 得 5002，不是 1015）")
    void wrappedStatusErrorIsStillMapped() {
        StatusRuntimeException inner = new StatusRuntimeException(
                Status.NOT_FOUND.withDescription("notification not found"));

        // 对照：直接抛 ⇒ description 就是表里那条 ⇒ 反查命中 5203
        assertEquals(5203, MessageGrpcError.bizCodeFromError(inner));

        // 以下四种包装修复前全部落到 1015；现在按 code class 映射到 5002
        assertEquals(5002, MessageGrpcError.bizCodeFromError(new CompletionException(inner)));
        assertEquals(5002, MessageGrpcError.bizCodeFromError(new ExecutionException(inner)));
        // Spring @Async / AOP / 自定义装饰器的通用包装
        assertEquals(5002, MessageGrpcError.bizCodeFromError(
                new RuntimeException("调用 message-rpc 失败", inner)));
        // 两层
        assertEquals(5002, MessageGrpcError.bizCodeFromError(
                new CompletionException(new RuntimeException("两层", inner))));
    }

    /**
     * grpc-go 在<b>解包</b>路径上还会做一句 {@code p.Message = err.Error()} ——
     * 用<b>外层</b>文本覆盖 description。
     * <p>行为后果：按 description 反查<b>查不中</b> ⇒ 最终按 code class 兜底。
     * 本用例把这个副作用钉死：直接命中时反查能中，解包时反查<b>不能</b>中。
     */
    @Test
    @DisplayName("解包时 description 被外层文本覆盖 ⇒ 反查不中、按 code class 兜底（Go: p.Message = err.Error()）")
    void wrappedStatusUsesOuterMessageForReverseLookup() {
        StatusRuntimeException direct = new StatusRuntimeException(
                Status.INTERNAL.withDescription("internal server error"));
        // 直接命中：description 正是表里那条 ⇒ 5001
        assertEquals(5001, MessageGrpcError.bizCodeFromError(direct));
        assertEquals("internal server error", MessageGrpcError.statusMessage(direct));

        // 解包：Go 会把 message 换成外层文本 ⇒ 反查不中 ⇒ 退回 code class（INTERNAL → 5001）
        Throwable wrapped = new RuntimeException("调 rpc 失败: internal server error", direct);
        assertEquals(5001, MessageGrpcError.bizCodeFromError(wrapped));
        assertEquals("调 rpc 失败: internal server error", MessageGrpcError.statusMessage(wrapped),
                "解包路径的 message 必须是**外层**文本（Go: p.Message = err.Error()）");
    }

    /**
     * 🔴 反向钉死：解包 + 外层文本<b>恰好</b>等于表里某条 msg 时，
     * Go 是<b>能</b>反查中的（覆盖后的 message 就是那条 msg）。
     * 若实现漏掉 {@code p.Message = err.Error()} 这一步，就会落到 code class 兜底，
     * 两者可能给出不同码 —— 本用例用「外层文本 = 表内 msg」把这条路径单独摘出来。
     */
    @Test
    @DisplayName("外层文本恰好等于表内 msg 时反查命中（钉死 p.Message = err.Error() 这一步）")
    void wrappedOuterMessageCanStillHitReverseTable() {
        // NOT_FOUND 兜底是 5002 "record not found"；这里让外层文本正好是 "record not found"，
        // 但 gRPC code 选 INTERNAL（兜底会得到 5001）⇒ 只有反查命中才可能得到 5002。
        StatusRuntimeException inner = new StatusRuntimeException(
                Status.INTERNAL.withDescription("某段与表无关的 description"));
        Throwable wrapped = new RuntimeException("record not found", inner);
        assertEquals(5002, MessageGrpcError.bizCodeFromError(wrapped),
                "外层 message 覆盖后应命中 bizCodeByMsg ⇒ 5002；若得 5002 以外说明没做覆盖");
    }

    // ==================================================================
    // 5. GRPCError 构造方向
    // ==================================================================

    @Test
    @DisplayName("grpcError 把业务码查表后放进 description（Go: GRPCError）")
    void grpcErrorPutsTableMsgIntoDescription() {
        StatusException e = MessageGrpcError.grpcError(Status.Code.NOT_FOUND,
                MessageErrorCode.ERROR_NOTIFICATION_MISS.getCode());
        assertEquals(Status.Code.NOT_FOUND, e.getStatus().getCode());
        assertEquals("notification not found", e.getStatus().getDescription());
    }

    /** 构造与解析应当互为逆运算（Go 侧同样是靠 description 往返的）。 */
    @Test
    @DisplayName("grpcError → bizCodeFromError 往返还原出原业务码")
    void grpcErrorRoundTripsBackToBizCode() {
        for (MessageErrorCode c : new MessageErrorCode[]{
                MessageErrorCode.ERROR_CONVERSATION_MISS,
                MessageErrorCode.ERROR_USER_NOT_FOUND,
                MessageErrorCode.ERROR_SERVER_COMMON}) {
            Throwable e = MessageGrpcError.grpcError(Status.Code.INTERNAL, c.getCode());
            assertEquals(c.getCode(), MessageGrpcError.bizCodeFromError(e),
                    "往返失败：" + c.name());
        }
    }

    /** cause 自引用不能让解包循环变成死循环。 */
    @Test
    @DisplayName("cause 自引用时不死循环（按 1015 兜底）")
    void selfReferencingCauseDoesNotHang() {
        RuntimeException e = new RuntimeException("self");
        // 人工构造自引用：initCause 不允许自引用自身，这里用一个中间层
        RuntimeException mid = new RuntimeException("mid", e);
        assertEquals(1015, MessageGrpcError.bizCodeFromError(mid));
    }
}