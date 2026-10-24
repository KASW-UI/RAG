package com.rag.message.message.api.service;

import com.rag.common.exception.BusinessException;
import com.rag.message.message.api.client.MessageRpcClient;
import com.rag.message.message.api.dto.AdminSendNotificationReq;
import com.rag.message.message.api.dto.AdminSendNotificationResp;
import com.rag.message.message.api.metrics.MessageApiMetrics;
import com.rag.message.message.common.errmsg.MessageErrorCode;
import com.rag.message.message.common.errmsg.MessageErrorMessages;
import com.rag.message.message.proto.SendNotificationReq;
import com.rag.message.message.proto.SenderRole;
import io.grpc.StatusRuntimeException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 管理员发通知 —— 对应 Go
 * {@code logic/messageadmin/sendadminnotificationlogic.go} 的 {@code SendAdminNotificationLogic}。
 *
 * <p><b>Go body 逐行对照</b>（这是本模块<b>唯一在 api 层做业务判定</b>的端点）：
 * <pre>
 *   uid, err := shared.UserIDFromContext(l.ctx)                      ① 身份
 *   if err != nil { ObserveReject(route, "admin_id_missing"); return nil, err }
 *
 *   if !req.Broadcast &amp;&amp; req.TargetId &lt;= 0 {                    ② 参数
 *       metrics.ObserveReject(route, "target_missing")
 *       return nil, shared.RPCError(messagecommon.NewErrCode(messagecommon.ErrorInvalidParam))
 *   }
 *
 *   rpcResp, err := l.svcCtx.MessageRpc.SendNotification(l.ctx, &amp;pb.SendNotificationReq{
 *       RecipientIds: recipientIDs(req),      // broadcast → nil；否则 [TargetId]
 *       Broadcast:    req.Broadcast,
 *       SenderId:     uid,
 *       SenderRole:   pb.SenderRole_ADMIN,
 *       Kind:         shared.AdminNotificationKind(req.Broadcast, req.Kind),
 *       Title:        req.Title,
 *       Content:      req.Content,
 *       Extra:        req.Extra,
 *   })
 *   if err != nil { return nil, shared.RPCError(err) }
 *   return &amp;types.AdminSendNotificationResp{Affected: rpcResp.Affected}, nil
 * </pre>
 *
 * <h3>三条容易做错的顺序/边界</h3>
 * <ol>
 *   <li><b>身份（①）先于参数（②）</b>。反过来的话，「无 token + 无 target_id」会得到
 *       5003 而 Go 给的是 5004。这是两个不同 reject reason 存在的原因。</li>
 *   <li><b>{@code TargetId <= 0} 而非 {@code == 0}</b>：Go 侧是
 *       {@code req.TargetId <= 0}，负数同样被拒。{@code target_id} 是 optional，
 *       缺席时零值 0 ⇒ 也落进这条分支 —— 所以「定向发送但没给 target_id」= 5003。</li>
 *   <li><b>{@code shared.RPCError(NewErrCode(5003))} 的 msg 是查表得到的
 *       {@code "invalid parameter"}</b>，不是 gRPC 的 description ——
 *       因为入参不是 status 异常，Go 的 {@code status.FromError} 返回 {@code ok=false}，
 *       走 {@code NewErrCode(code)} 分支。这里显式用
 *       {@code MessageErrorMessages.getMsg(5003)} 复刻。</li>
 * </ol>
 *
 * <p><b>广播是唯一的批量副作用路径</b>：message-rpc 按
 * {@code message.broadcast.page-size=200} <b>分页拉取</b>用户列表，
 * 再把收件人<b>整批</b>交给 rpc 端插入 —— 插入侧由
 * {@code MessageAtomicService#insertNotificationsTx} 按 {@code INSERT_BATCH_SIZE=200}
 * 切片，但<b>全部切片在同一个事务里</b>。
 * api 层<b>不</b>循环、不分批、不做补偿 —— Go 侧也没有，
 * 「部分成功」这种语义在 Go 侧不存在。
 *
 * <p>⚠️ <b>2026-10-22 修正本段的一处事实错误（原注释写的是「分批插库」）</b>：
 * {@code page-size} 只用于 {@code admin.GetUserList} 的 <b>RPC 拉取分页</b>，
 * 与插库分批<b>毫无关系</b>；修复前插库是一条 foreach 多行 INSERT，
 * 收件人 ≥ 5462（Postgres 单条 SQL 绑定参数上限 65535 ÷ 12 列）时整批失败
 * ⇒ 广播在用户量过千的场景下<b>一条都发不出去</b>，而注释让人以为规模问题已处理。
 * 已由 DRIFT-MSG-V-002 修复（切片 + 同事务）。见 AGENTS.md §6.7.2。
 */
@Service
public class MessageAdminSendNotificationService extends AbstractMessageApiService {

    /** Go: {@code const route = "/message/v1/admin/notifications/send"}。 */
    static final String ROUTE = "/message/v1/admin/notifications/send";

    public MessageAdminSendNotificationService(MessageRpcClient rpcClient,
                                               MessageApiSupport support,
                                               MessageApiMetrics metrics) {
        super(rpcClient, support, metrics);
    }

    public AdminSendNotificationResp sendAdminNotification(AdminSendNotificationReq req,
                                                           HttpServletRequest httpRequest) {
        return invoke(ROUTE, () -> {
            // ① 身份 —— 必须先于参数校验（Go 侧顺序，见类注释）
            long uid = requireIdentity(ROUTE, REASON_ADMIN_ID_MISSING, httpRequest);

            // ② 参数 —— Go: !req.Broadcast && req.TargetId <= 0
            if (!req.broadcast() && (req.targetId() == null || req.targetId() <= 0)) {
                metrics().observeReject(ROUTE, REASON_TARGET_MISSING);
                throw new BusinessException(
                        MessageErrorCode.ERROR_INVALID_PARAM.getCode(),
                        MessageErrorMessages.getMsg(MessageErrorCode.ERROR_INVALID_PARAM.getCode()));
            }

            SendNotificationReq.Builder builder = SendNotificationReq.newBuilder()
                    .setBroadcast(req.broadcast())
                    .setSenderId(uid)
                    .setSenderRoleValue(roleValue(SenderRole.ADMIN))
                    // setKindValue(int) 而非 setKind(enum)：Go 的 proto3 是开放枚举，
                    // 客户端可以下发未定义值；Java 的 forNumber 会把未知值变成 null。
                    .setKindValue(support().adminNotificationKind(req.broadcast(), req.kind()))
                    .setTitle(req.title() == null ? "" : req.title())
                    .setContent(req.content() == null ? "" : req.content())
                    .addAllRecipientIds(support().recipientIds(req.broadcast(), req.targetId()));

            // Go: Extra: req.Extra —— 缺席与空 map 都传 nil/空，rpc 端统一按空 extra 处理。
            // 这里只在非 null 时写入，避免把「缺席」变成「空 map」以外的第三种形态。
            Map<String, String> extra = req.extra();
            if (extra != null) {
                builder.putAllExtra(extra);
            }

            try {
                return support().toAdminSendNotificationResp(
                        rpc().messageRpc().sendNotification(builder.build()));
            } catch (StatusRuntimeException e) {
                throw support().rpcError(e);
            }
        });
    }
}