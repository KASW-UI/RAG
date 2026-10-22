package com.rag.message.message.api.service;

import com.rag.common.exception.BusinessException;
import com.rag.message.message.api.client.MessageRpcClient;
import com.rag.message.message.api.dto.ConversationListResp;
import com.rag.message.message.api.dto.ConversationMessagesReq;
import com.rag.message.message.api.dto.ConversationMessagesResp;
import com.rag.message.message.api.dto.EmptyReq;
import com.rag.message.message.api.dto.MarkConversationReadReq;
import com.rag.message.message.api.dto.PageReq;
import com.rag.message.message.api.metrics.MessageApiMetrics;
import com.rag.message.message.proto.ConversationListReq;
import com.rag.message.message.proto.ConversationMessageListReq;
import com.rag.message.message.proto.SenderRole;
import io.grpc.StatusRuntimeException;
import jakarta.servlet.http.HttpServletRequest;

import java.util.function.Supplier;

/**
 * 13 个 Service 的公共骨架 —— Go 端 13 个 logic 文件里那段<b>完全相同</b>样板
 * （{@code started := time.Now()} + {@code defer ObserveRequest} + {@code UserIDFromContext}
 * + {@code ObserveReject}）的收敛表达。
 *
 * <p><b>为什么不逐个 logic 抄一遍</b>：Go 侧的重复是 goctl 的代码生成产物，
 * 不是设计意图；13 份逐字复制会让「埋点时序」这种行为相关语义出现 13 个可漂移点
 * （AGENTS.md §3 的 11 处回归正是这类重复的产物）。
 * 收敛到 {@link #invoke} 后，语义只有<b>一份</b>定义。
 *
 * <h3>埋点时序（与 Go 逐条对齐）</h3>
 * <pre>
 * Go:
 *   started := time.Now()
 *   const route = "/message/v1/conversations/list"
 *   defer func() { metrics.ObserveRequest(route, started, err) }()   // 成功失败都记
 *   uid, err := shared.UserIDFromContext(l.ctx)
 *   if err != nil { metrics.ObserveReject(route, "user_id_missing"); return nil, err }
 *
 * Java（Template Method）:
 *   invoke(route, () -&gt; { uid = userId(request); metrics.observeReject(...); ... })
 *   finally 里 metrics.observeRequest(route, failed, elapsed)
 * </pre>
 *
 * <p><b>关键点</b>：{@code request_total} 的 {@code result} 标签取自
 * 「logic 是否以错误收场」，与 Go 判的是<b>同一个东西</b>（Go 判命名返回值 {@code err != nil}）。
 * 因此返回业务失败码也算 {@code fail}，成功才是 {@code success}。
 * {@code try/finally} 保证抛异常时也会记账 —— 与 Go 的 {@code defer} 等价。
 *
 * <p><b>3 个 user/admin 共用的操作</b>（{@code listConversations} /
 * {@code getConversationMessages} / {@code markConversationRead}）在 Go 侧是
 * 「user logic 与 admin logic 各一份、只差一个枚举常量」。这里参数化成 role，
 * 避免同一段 RPC + 映射出现两份。
 */
abstract class AbstractMessageApiService {

    /** Go: {@code ObserveReject(route, "user_id_missing")} —— 8 个 user 端点。 */
    static final String REASON_USER_ID_MISSING = "user_id_missing";
    /** Go: {@code ObserveReject(route, "admin_id_missing")} —— 5 个 admin 端点。 */
    static final String REASON_ADMIN_ID_MISSING = "admin_id_missing";
    /** Go: {@code ObserveReject(route, "target_missing")} —— 仅 SendAdminNotification。 */
    static final String REASON_TARGET_MISSING = "target_missing";

    private final MessageRpcClient rpcClient;
    private final MessageApiSupport support;
    private final MessageApiMetrics metrics;

    AbstractMessageApiService(MessageRpcClient rpcClient,
                              MessageApiSupport support,
                              MessageApiMetrics metrics) {
        this.rpcClient = rpcClient;
        this.support = support;
        this.metrics = metrics;
    }

    MessageApiSupport support() {
        return support;
    }

    MessageRpcClient rpc() {
        return rpcClient;
    }

    MessageApiMetrics metrics() {
        return metrics;
    }

    /**
     * 埋点 + 异常传播的模板方法 —— 对应 Go 每个 logic 头部/尾部的样板。
     *
     * <p>异常<b>不在</b>这里捕获：让 gRPC / 业务异常继续往上抛到
     * {@code GlobalExceptionHandler}，那里负责把 code/msg 映射成 Go 的 envelope。
     * 这里只负责 finally 里的 {@code observeRequest}。
     */
    <T> T invoke(String route, Supplier<T> body) {
        long startedAt = System.nanoTime();
        boolean failed = true;
        try {
            T result = body.get();
            failed = false;
            return result;
        } finally {
            metrics.observeRequest(route, failed, System.nanoTime() - startedAt);
        }
    }

    /**
     * 取身份 + 失败时记 reject —— 对应 Go 13 个 logic 里<b>逐字相同</b>的这段：
     * <pre>
     *   uid, err := shared.UserIDFromContext(l.ctx)
     *   if err != nil {
     *       metrics.ObserveReject(route, "user_id_missing")   // admin 端点是 "admin_id_missing"
     *       return nil, err
     *   }
     * </pre>
     *
     * <p><b>顺序不可颠倒</b>：身份解析永远在参数校验之前。
     * Go 的 {@code SendAdminNotification} 也是先 {@code UserIDFromContext}
     * 再判 {@code target_id <= 0} —— 所以「无 token 的请求打 admin 端点」
     * 得到的是 5004（unauthorized）而不是 5003（invalid parameter）。
     */
    long requireIdentity(String route, String rejectReason, HttpServletRequest request) {
        try {
            return support.userId(request);
        } catch (BusinessException e) {
            metrics.observeReject(route, rejectReason);
            throw e;
        }
    }

    /**
     * 会话列表 —— 对应 Go {@code ListConversationsLogic} / {@code ListAdminConversationsLogic}。
     *
     * <p>两个 Go logic 的 body <b>逐字相同</b>，只差
     * {@code OperatorRole: pb.SenderRole_USER} vs {@code pb.SenderRole_ADMIN}。
     * 这里把 role 参数化，保留各自 Service 里的 route 常量与 reject reason 不变。
     */
    ConversationListResp listConversations(String route, String rejectReason,
                                            long uid, int operatorRole, PageReq req) {
        ConversationListReq.Builder builder = ConversationListReq.newBuilder()
                .setOperatorId(uid)
                .setOperatorRoleValue(operatorRole)
                .setOffset(req.offset())
                .setLimit(req.limit());
        try {
            return support.toConversationListResp(rpcClient.messageRpc().listConversations(builder.build()));
        } catch (StatusRuntimeException e) {
            throw support.rpcError(e);
        }
    }

    /**
     * 会话消息列表 —— 对应 Go {@code GetConversationMessagesLogic} /
     * {@code GetAdminConversationMessagesLogic}（同样只差 role）。
     */
    ConversationMessagesResp getConversationMessages(String route, String rejectReason,
                                                    long uid, int operatorRole,
                                                    ConversationMessagesReq req) {
        ConversationMessageListReq.Builder builder = ConversationMessageListReq.newBuilder()
                .setOperatorId(uid)
                .setOperatorRoleValue(operatorRole)
                .setConversationId(req.conversationId())
                .setOffset(req.offset())
                .setLimit(req.limit());
        try {
            return support.toConversationMessagesResp(
                    rpcClient.messageRpc().getConversationMessages(builder.build()));
        } catch (StatusRuntimeException e) {
            throw support.rpcError(e);
        }
    }

    /**
     * 标记会话已读 —— 对应 Go {@code MarkConversationReadLogic} /
     * {@code MarkAdminConversationReadLogic}。
     *
     * <p><b>返回 {@code {}}</b>：Go 返回 {@code &types.EmptyReq{}, nil}，
     * 而不是 nil —— 见 {@link EmptyReq} 的类注释。
     */
    EmptyReq markConversationRead(String route, String rejectReason,
                                  long uid, int operatorRole, MarkConversationReadReq req) {
        com.rag.message.message.proto.MarkConversationReadReq.Builder builder =
                com.rag.message.message.proto.MarkConversationReadReq.newBuilder()
                .setOperatorId(uid)
                .setOperatorRoleValue(operatorRole)
                .setConversationId(req.conversationId());
        try {
            rpcClient.messageRpc().markConversationRead(builder.build());
        } catch (StatusRuntimeException e) {
            throw support.rpcError(e);
        }
        return new EmptyReq();
    }

    /**
     * 便捷取值：{@code SenderRole} 的原始数值。
     *
     * <p>Go 侧写的是枚举常量（{@code pb.SenderRole_USER}），Java protobuf 的枚举对象
     * 需要 {@code getNumber()} 才等价；用 {@code setOperatorRoleValue(int)} 还能顺带
     * 支持 Go 的 proto3 开放枚举（未知值不会被 Java 的 {@code forNumber} 变成 null）。
     */
    static int roleValue(SenderRole role) {
        return role.getNumber();
    }
}