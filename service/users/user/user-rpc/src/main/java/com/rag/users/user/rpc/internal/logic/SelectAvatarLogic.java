package com.rag.users.user.rpc.internal.logic;

import com.rag.users.user.proto.SelectAvatarReq;
import com.rag.users.user.proto.SelectAvatarResp;
import com.rag.users.user.rpc.internal.model.UserExceptions.NotFoundException;
import com.rag.users.user.rpc.internal.model.UserExceptions.AvatarHistoryNotFoundException;
import com.rag.users.user.rpc.internal.model.UserAvatarHistory;
import com.rag.users.user.rpc.internal.svc.ServiceContext;
import io.grpc.Status;
import org.springframework.stereotype.Service;

/**
 * 对应 Go 端 internal/logic/selectavatarlogic.go。
 *
 * Works（W1~W3）：
 *   W1 校验 history_id trim 后非空
 *   W2 DataModel.selectAvatarHistory(uid, historyID) —— 5 步原子事务（D-005）
 *   W3 构造 resp
 *
 * 关键 Decision：
 *   D-005：5 步原子（user 查找 / history 查找带 uid 隔离 / 旧 current 标 false /
 *        目标 history 标 true / 写 user.extra_info["avatar"]）
 *   D-014：必须双写
 *   D-015：history 查询条件必须带 uid（DataModel.selectAvatarHistory 内部已实现 WHERE uid=? AND history_id=?）
 *
 * 没有 metrics / OTel span（D-016）。
 */
@Service
public class SelectAvatarLogic {

    private final ServiceContext svcCtx;

    public SelectAvatarLogic(ServiceContext svcCtx) {
        this.svcCtx = svcCtx;
    }

    public SelectAvatarResp selectAvatar(SelectAvatarReq req) {
        // DRIFT-018: timeout -> DEADLINE_EXCEEDED
        svcCtx.checkDeadline();
        // W1
        String historyId = AvatarHelpers.normalizedHistoryId(req.getHistoryId());
        if (historyId.isEmpty()) {
            throw Status.INVALID_ARGUMENT
                    .withDescription("history_id is required")
                    .asRuntimeException();
        }

        // W2（D-005 / D-015）
        UserAvatarHistory history;
        try {
            history = svcCtx.getUserModel().selectAvatarHistory(req.getUid(), historyId);
        } catch (NotFoundException e) {
            throw Status.NOT_FOUND
                    .withDescription("用户不存在")
                    .asRuntimeException();
        } catch (AvatarHistoryNotFoundException e) {
            throw Status.NOT_FOUND
                    .withDescription("头像历史不存在")
                    .asRuntimeException();
        } catch (Exception e) {
            throw Status.INTERNAL
                    .withDescription("DB更新失败")
                    .asRuntimeException();
        }

        // W3
        return SelectAvatarResp.newBuilder()
                .setAvatarUrl(history.getAvatarUrl() == null ? "" : history.getAvatarUrl())
                .setHistory(AvatarHelpers.fromModel(history))
                .build();
    }
}