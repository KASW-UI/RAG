package com.rag.users.user.rpc.internal.logic;

import com.rag.common.snowflake.Snowflake;
import com.rag.users.user.proto.UploadAvatarReq;
import com.rag.users.user.proto.UploadAvatarResp;
import com.rag.users.user.rpc.internal.model.UserExceptions.NotFoundException;
import com.rag.users.user.rpc.internal.model.UserExceptions.AvatarHistoryNotFoundException;
import com.rag.users.user.rpc.internal.model.UserAvatarHistory;
import com.rag.users.user.rpc.internal.svc.ServiceContext;
import io.grpc.Status;
import org.springframework.stereotype.Service;

/**
 * 对应 Go 端 internal/logic/uploadavatarlogic.go。
 *
 * Works（W1~W4）：
 *   W1 校验 avatar_url trim 后非空
 *   W2 Snowflake.nextId() → history_id（字符串化的 long）—— D-013
 *   W3 DataModel.createAvatarHistory(uid, historyId, avatarURL, normalizedCT, sizeBytes) —— 4 步原子事务（D-005）
 *   W4 构造 resp
 *
 * 关键 Decision：
 *   D-005：4 步事务原子（user 查找 / 旧 current 标 false / 插新 history / 写 user.extra_info["avatar"]）
 *   D-012：content_type 空 → "image/png"（AvatarHelpers.normalizeContentType）
 *   D-013：history_id 服务端 snowflake 生成，不接受请求传入
 *   D-014：必须双写 user_avatar_history + users.extra_info["avatar"]
 *
 * 注：Go 端此 RPC 的中文 error message 是 mojibake（GBK→UTF-8 转换错，源文件里是乱码字面值）；
 *     Java 侧用正常 UTF-8 中文，不影响 wire 协议（错误码仍是 codes.NotFound / codes.Internal）。
 *
 * 没有 metrics / OTel span（D-016）。
 */
@Service
public class UploadAvatarLogic {

    private final ServiceContext svcCtx;

    public UploadAvatarLogic(ServiceContext svcCtx) {
        this.svcCtx = svcCtx;
    }

    public UploadAvatarResp uploadAvatar(UploadAvatarReq req) {
        // DRIFT-018: timeout -> DEADLINE_EXCEEDED
        svcCtx.checkDeadline();
        // W1
        String avatarUrl = AvatarHelpers.normalizedAvatarURL(req.getAvatarUrl());
        if (avatarUrl.isEmpty()) {
            throw Status.INVALID_ARGUMENT
                    .withDescription("avatar_url is required")
                    .asRuntimeException();
        }

        // W2（Snowflake 是静态工具）
        String historyId;
        try {
            historyId = String.valueOf(Snowflake.nextId());
        } catch (Exception e) {
            throw Status.INTERNAL
                    .withDescription("history_id生成失败")
                    .asRuntimeException();
        }

        // W3（D-005 / D-012 / D-013 / D-014）
        String contentType = AvatarHelpers.normalizeContentType(req.getContentType());
        UserAvatarHistory history;
        try {
            history = svcCtx.getUserModel().createAvatarHistory(
                    req.getUid(), historyId, avatarUrl, contentType, req.getSizeBytes());
        } catch (NotFoundException e) {
            throw Status.NOT_FOUND
                    .withDescription("用户不存在")
                    .asRuntimeException();
        } catch (AvatarHistoryNotFoundException e) {
            // 实际不会抛（createAvatarHistory 不会查 history），但保留以防实现变化
            throw Status.NOT_FOUND
                    .withDescription("头像历史不存在")
                    .asRuntimeException();
        } catch (Exception e) {
            throw Status.INTERNAL
                    .withDescription("DB插入失败")
                    .asRuntimeException();
        }

        // W4
        return UploadAvatarResp.newBuilder()
                .setAvatarUrl(avatarUrl)
                .setHistory(AvatarHelpers.fromModel(history))
                .build();
    }
}