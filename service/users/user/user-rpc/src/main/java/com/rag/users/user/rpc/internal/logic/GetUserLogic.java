package com.rag.users.user.rpc.internal.logic;

import com.rag.users.common.errmsg.ErrorCode;
import com.rag.users.user.proto.GetUserReq;
import com.rag.users.user.proto.GetUserResp;
import com.rag.users.user.proto.UserInfo;
import com.rag.users.user.rpc.internal.model.UserExceptions.NotFoundException;
import com.rag.users.user.rpc.internal.model.User;
import com.rag.users.user.rpc.internal.svc.ServiceContext;
import io.grpc.Status;
import io.opentelemetry.context.Context;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 对应 Go 端 internal/logic/getuserlogic.go。
 *
 * 入口：GetUserReq{Uid}
 * 出口：
 *   - 成功 → GetUserResp{User:{Uid, Username, Email, ExtraInfo, Status}, Found=true}
 *   - NotFound → StatusRuntimeException(NOT_FOUND, "用户不存在")
 *   - DB 异常 → StatusRuntimeException(INTERNAL, "DB查询失败")
 *
 * 关键 Decision：
 *   D-003：Go 端同时返 Found=false + NotFound error，但 gRPC wire 上只发 error
 *         （resp.Found=false 是 dead code，identity resolver 走 error 分支）
 *         → Java 端只抛 StatusRuntimeException，不构造半成品 resp
 *   D-011：成功时 Status 字段必须填（proto3 optional int64，非 nil）
 *         → Java 端 setStatus(user.getStatus()) —— user.getStatus() 返回 Long，非 null
 *   DRIFT-OBS-LOGIN 修复：补 Go 端的 logger.LogBusinessErr/LogInfo 调用（业务事件日志对齐）
 *
 * 没有 metrics / OTel span（Go 端 getuserlogic.go 也没埋点，D-016 1:1 保留）。
 */
@Service
public class GetUserLogic {

    private final ServiceContext svcCtx;

    public GetUserLogic(ServiceContext svcCtx) {
        this.svcCtx = svcCtx;
        // DRIFT-OBS-LOGIN 修复：补 Go 端的 logger 调用，1:1 复刻
        //   logger.LogBusinessErr(l.ctx, errmsg.ErrorUserNotExist, err)
        //   logger.LogBusinessErr(l.ctx, errmsg.ErrorDbSelect, err)
        //   logger.LogInfo(l.ctx, fmt.Sprintf("search user success,uid : %d", in.Uid))
        // Logger 通过 svcCtx.getLogger() 取（构造器签名不变，对应其它 Logic 模式）
    }

    public GetUserResp getUser(GetUserReq req) {
        // DRIFT-018: timeout -> DEADLINE_EXCEEDED
        svcCtx.checkDeadline();
        // 没有 span（Go 端 getuserlogic.go 也没起 span，D-016 1:1 保留）
        Context otelCtx = Context.current();
        User user;
        try {
            user = svcCtx.getUserModel().findOneByUid(req.getUid());
        } catch (NotFoundException e) {
            // 对应 Go 端 logger.LogBusinessErr(l.ctx, errmsg.ErrorUserNotExist, err) —— ErrorUserNotExist=1003
            svcCtx.getLogger().logBusinessErr(otelCtx, ErrorCode.ERROR_USER_NOT_EXIST.getCode(), e);
            throw Status.NOT_FOUND
                    .withDescription("用户不存在")
                    .asRuntimeException();
        } catch (Exception e) {
            // 对应 Go 端 logger.LogBusinessErr(l.ctx, errmsg.ErrorDbSelect, err) —— ErrorDbSelect=5003
            svcCtx.getLogger().logBusinessErr(otelCtx, ErrorCode.ERROR_DB_SELECT.getCode(), e);
            throw Status.INTERNAL
                    .withDescription("DB查询失败")
                    .asRuntimeException();
        }

        UserInfo.Builder b = UserInfo.newBuilder()
                .setUid(user.getUid())
                .setUsername(user.getUsername() == null ? "" : user.getUsername())
                .setEmail(user.getEmail() == null ? "" : user.getEmail())
                .setStatus(user.getStatus()); // D-011: Long（非 null）保证 proto optional 字段被设置
        Map<String, String> extra = user.getExtraInfo();
        if (extra != null && !extra.isEmpty()) {
            b.putAllExtraInfo(extra);
        } else {
            b.putAllExtraInfo(new HashMap<>());
        }

        // 对应 Go 端 logger.LogInfo(l.ctx, fmt.Sprintf("search user success,uid : %d", in.Uid))
        svcCtx.getLogger().logInfo(otelCtx, "search user success,uid : " + req.getUid());
        return GetUserResp.newBuilder()
                .setUser(b.build())
                .setFound(true)
                .build();
    }
}