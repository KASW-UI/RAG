package com.rag.users.admin.api.service;

import com.rag.users.admin.api.dto.AdminInfo;
import io.grpc.Status;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * admin-api 共用 helper —— 跨 13 个 Service / Interceptor / Controller 的工具方法聚合点。
 *
 * 对应 user-api 端 UserApiSupport 同款位置与职责。
 *
 * <p>框架职责：
 * <ul>
 *   <li>{@code extractAdminId}：from request attribute "userId"（由 AdminJwtAuthInterceptor 写入）</li>
 *   <li>{@code parseUid}：uid 字符串解析（6 个 Service 共用同一份实现）</li>
 *   <li>{@code codeFromRPCError}：把 gRPC Status 映射成 ErrorCode 业务码</li>
 *   <li>{@code fromProto(AdminInfo proto)} → DTO 转换</li>
 *   <li>{@code fromProto(UserInfo proto)} → DTO 转换（admin.proto.UserInfo，与 dto UserInfo 同名用全限定名）</li>
 * </ul>
 */
@Component
public class AdminApiSupport {

    /**
     * uid 字段从 String 解析（JSON 序列化为字符串避免精度丢失）。
     *
     * <p>解析失败 / 为空 → 直接抛 {@code BusinessException(1017 请求参数错误)}，
     * 与参考侧 {@code parseLongOrNull} → {@code LogicResult.fail(ErrorCode.ERROR_REQUEST_PARAM)}
     * 的语义 1:1。
     *
     * <p>曾经返回 0L 而不报错：那会把"客户端传了非法 uid"静默降级成"查询 uid=0 的用户"，
     * 客户端拿到的是 {@code 1003 用户不存在} 而不是 {@code 1017 请求参数错误}，
     * 坏请求被误分类成业务不存在，且掩盖了调用方的参数拼装 bug。
     */
    public static long parseUid(String uidStr) {
        if (uidStr == null || uidStr.isEmpty()) {
            throw new com.rag.common.exception.BusinessException(
                    com.rag.users.common.errmsg.ErrorCode.ERROR_REQUEST_PARAM.getCode(),
                    com.rag.users.common.errmsg.ErrorCode.ERROR_REQUEST_PARAM.getMsg());
        }
        try {
            return Long.parseLong(uidStr.trim());
        } catch (NumberFormatException e) {
            throw new com.rag.common.exception.BusinessException(
                    com.rag.users.common.errmsg.ErrorCode.ERROR_REQUEST_PARAM.getCode(),
                    com.rag.users.common.errmsg.ErrorCode.ERROR_REQUEST_PARAM.getMsg());
        }
    }

    /**
     * 从 request attribute "userId" 取 uid（由 AdminJwtAuthInterceptor 写入）。
     * 失败抛 IdentityResolver.ErrInvalidClaimException（与 Go 端 admin ClaimedUID 1:1）。
     */
    public long extractAdminId(HttpServletRequest request) {
        return IdentityResolver.claimedUID(request);
    }

    /**
     * 把 gRPC Status.Code 映射成 ErrorCode 业务码。
     * 业务码精确映射在 Service 内部抛 BusinessException 完成；这里是 fallback。
     */
    public int codeFromRPCError(Status.Code code, int fallbackBusinessCode) {
        if (code == Status.Code.INTERNAL) {
            return 5001;   // ErrorServerCommon
        }
        if (code == Status.Code.NOT_FOUND) {
            return 1003;   // ErrorUserNotExist
        }
        if (code == Status.Code.ALREADY_EXISTS) {
            return 1001;   // ErrorUserExist
        }
        if (code == Status.Code.INVALID_ARGUMENT) {
            return 500;    // Error
        }
        if (code == Status.Code.PERMISSION_DENIED) {
            return 1016;   // ErrorAdminInviteCodeWrong
        }
        if (code == Status.Code.UNAUTHENTICATED) {
            return 1006;   // ErrorTokenRuntime
        }
        return fallbackBusinessCode;  // 默认 → CodeServerBusy (1015)
    }

    /**
     * proto AdminInfo → DTO AdminInfo。
     */
    public AdminInfo fromProto(com.rag.users.admin.proto.AdminInfo proto) {
        if (proto == null) return null;
        AdminInfo dto = new AdminInfo();
        dto.setUid(proto.getUid());
        dto.setUsername(proto.getUsername() == null ? "" : proto.getUsername());
        if (!proto.getEmail().isEmpty()) dto.setEmail(proto.getEmail());
        if (proto.getExtraInfoCount() > 0) {
            Map<String, String> map = new HashMap<>(proto.getExtraInfoMap());
            dto.setExtraInfo(map);
        }
        return dto;
    }

    /**
     * proto UserInfo → DTO UserInfo。
     * 注：admin.proto UserInfo 多一个 score 字段，但 admin-api DTO 不暴露 score（与 admin-rpc 端 1:1 保留 Go 行为）。
     */
    public com.rag.users.admin.api.dto.UserInfo fromProto(com.rag.users.admin.proto.UserInfo proto) {
        if (proto == null) return null;
        com.rag.users.admin.api.dto.UserInfo dto = new com.rag.users.admin.api.dto.UserInfo();
        dto.setUid(proto.getUid());
        dto.setStatus(proto.getStatus());
        dto.setUsername(proto.getUsername() == null ? "" : proto.getUsername());
        if (!proto.getEmail().isEmpty()) dto.setEmail(proto.getEmail());
        if (proto.getExtraInfoCount() > 0) {
            Map<String, String> map = new HashMap<>(proto.getExtraInfoMap());
            dto.setExtraInfo(map);
        }
        return dto;
    }
}
