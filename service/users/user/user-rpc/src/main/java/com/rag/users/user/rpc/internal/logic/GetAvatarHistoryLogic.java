package com.rag.users.user.rpc.internal.logic;

import com.rag.users.user.proto.AvatarHistoryItem;
import com.rag.users.user.proto.GetAvatarHistoryReq;
import com.rag.users.user.proto.GetAvatarHistoryResp;
import com.rag.users.user.rpc.internal.model.UserExceptions.NotFoundException;
import com.rag.users.user.rpc.internal.model.UserAvatarHistory;
import com.rag.users.user.rpc.internal.svc.ServiceContext;
import io.grpc.Status;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 对应 Go 端 internal/logic/getavatarhistorylogic.go。
 *
 * Works：
 *   W1 FindOneByUid(uid) → 用户必须存在
 *   W2 DataModel.listAvatarHistory(uid, limit) → ORDER BY create_time DESC
 *
 * 关键 Decision：
 *   D-010：limit <= 0 || limit > 30 静默 clamp 到 30，不抛异常
 *         （DataModel.listAvatarHistory 已经做了）
 *
 * 没有 metrics / OTel span（D-016）。
 * 注：Go 端 mojibake 中文 message 已被替换为正常 UTF-8。
 */
@Service
public class GetAvatarHistoryLogic {

    private final ServiceContext svcCtx;

    public GetAvatarHistoryLogic(ServiceContext svcCtx) {
        this.svcCtx = svcCtx;
    }

    public GetAvatarHistoryResp getAvatarHistory(GetAvatarHistoryReq req) {
        // DRIFT-018: timeout -> DEADLINE_EXCEEDED
        svcCtx.checkDeadline();
        // W1
        try {
            svcCtx.getUserModel().findOneByUid(req.getUid());
        } catch (NotFoundException e) {
            throw Status.NOT_FOUND
                    .withDescription("用户不存在")
                    .asRuntimeException();
        } catch (Exception e) {
            throw Status.INTERNAL
                    .withDescription("DB查询失败")
                    .asRuntimeException();
        }

        // W2（D-010：limit clamp 在 DataModel.listAvatarHistory 内部完成）
        List<UserAvatarHistory> histories;
        try {
            histories = svcCtx.getUserModel().listAvatarHistory(req.getUid(), req.getLimit());
        } catch (Exception e) {
            throw Status.INTERNAL
                    .withDescription("DB查询失败")
                    .asRuntimeException();
        }

        List<AvatarHistoryItem> items = new ArrayList<>(histories.size());
        for (UserAvatarHistory h : histories) {
            items.add(AvatarHelpers.fromModel(h));
        }

        return GetAvatarHistoryResp.newBuilder()
                .addAllList(items)
                .build();
    }
}