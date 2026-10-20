package com.rag.users.user.api.service;

import com.rag.users.user.api.dto.AvatarHistoryItem;
import com.rag.users.user.api.dto.UserInfo;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * user-api 共用 helper —— 跨 9 个 Service / Interceptor / Controller 的工具方法聚合点。
 *
 * 框架职责:
 *   - Spring @Component —— 跨用例共享的工具方法聚合点
 *   - 取代了原 internal.svc.ServiceContext 中的 helper
 *
 * HUMAN_ONLY.MD 提到的方法：
 *   - extractUserId：from request attribute "userId"（由 JwtAuthInterceptor 写入）
 *   - resolveUserId：Identity check
 *
 * 额外提供：
 *   - fromProto(AvatarHistoryItem proto) → DTO 转换
 *   - fromProto(UserInfo proto) → DTO 转换
 *   - normalizeAvatarUrl / normalizeContentType：与 user-rpc AvatarDomainService 同款
 *
 * <p><b>设计注</b>：本类用全限定名引用 proto message（{@code com.rag.users.user.proto.UserInfo} /
 * {@code com.rag.users.user.proto.AvatarHistoryItem}），因为与 dto 包同名（{@code com.rag.users.user.api.dto.UserInfo} /
 * {@code com.rag.users.user.api.dto.AvatarHistoryItem}），不能用 import alias 解决。
 */
@Component
public class UserApiSupport {

    /** 与 user-rpc AvatarDomainService.normalizeAvatarUrl 同款（TrimSpace）。 */
    public String normalizeAvatarUrl(String value) {
        return value == null ? "" : value.trim();
    }

    /** 与 user-rpc AvatarDomainService.normalizeContentType 同款（D-012：空 → "image/png"）。 */
    public String normalizeContentType(String value) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.isEmpty() ? "image/png" : trimmed;
    }

    /** 与 user-rpc AvatarDomainService.normalizeHistoryId 同款（TrimSpace）。 */
    public String normalizeHistoryId(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * 从 request attribute "userId" 取 uid（由 JwtAuthInterceptor 写入）。
     * 失败抛 IdentityResolver.ErrInvalidClaimException（与 Go 端 identity.ClaimedUID 1:1）。
     */
    public long extractUserId(HttpServletRequest request) {
        return IdentityResolver.claimedUID(request);
    }

    /**
     * proto → DTO：AvatarHistoryItem（6 字段）。
     * proto3 map 与 nullable 处理：
     *   - id (string) → 空字符串 fallback（与 Go 端 nil string 对齐）
     *   - sizeBytes (int64) → long（不会是 0）
     *   - isCurrent (bool) → boolean
     *   - createTime (string，proto 已是 RFC3339 字符串) → 直接透传
     */
    public AvatarHistoryItem fromProto(com.rag.users.user.proto.AvatarHistoryItem proto) {
        if (proto == null) return null;
        AvatarHistoryItem dto = new AvatarHistoryItem();
        dto.setId(proto.getId() == null ? "" : proto.getId());
        dto.setAvatarUrl(proto.getAvatarUrl() == null ? "" : proto.getAvatarUrl());
        dto.setContentType(proto.getContentType() == null ? "" : proto.getContentType());
        dto.setSizeBytes(proto.getSizeBytes());
        dto.setCurrent(proto.getIsCurrent());
        dto.setCreateTime(proto.getCreateTime() == null ? "" : proto.getCreateTime());
        return dto;
    }

    /**
     * proto → DTO：UserInfo（4 字段，不含 score）。
     * D-011：HTTP 层不暴露 score 字段；Go 端 UserInfo 包含但只填 4 字段。
     */
    public UserInfo fromProto(com.rag.users.user.proto.UserInfo proto) {
        if (proto == null) return null;
        UserInfo dto = new UserInfo();
        dto.setUid(proto.getUid());
        dto.setUsername(proto.getUsername());
        if (!proto.getEmail().isEmpty()) dto.setEmail(proto.getEmail());
        if (proto.getExtraInfoCount() > 0) {
            Map<String, String> map = new HashMap<>(proto.getExtraInfoMap());
            dto.setExtraInfo(map);
        }
        return dto;
    }
}
