package com.rag.users.user.rpc.service;

import com.rag.users.user.proto.AvatarHistoryItem;
import com.rag.users.user.rpc.domain.UserAvatarHistoryEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 头像域服务 —— 头像字段归一化 + proto 转换工具。
 *
 * 对应 Go: service/user/user/rpc/internal/logic/avatarhelpers.go 的 4 个工具函数：
 *   func avatarHistoryItemFromModel(item *model.UserAvatarHistory) *pb.AvatarHistoryItem
 *   func normalizedAvatarURL(value string) string
 *   func normalizeContentType(value string) string  (= model.NormalizeAvatarContentType)
 *   func normalizedHistoryId(value string) string   (= strings.TrimSpace)
 *
 * Go 端 create_time 用 time.RFC3339 字符串发给 wire（proto 字段类型也是 string）；
 * Java 侧保持 String 透传（不做 Instant.parse）——wire 一致。
 *
 * 框架职责:
 *   - Spring @Service —— 跨多个头像用例共享的工具方法聚合点
 *   - 取代了原 internal.logic.AvatarHelpers 静态工具类
 *   - selectAvatar / uploadAvatar / getAvatarHistory 三个用例共用
 */
@Service
public class AvatarDomainService {

    /**
     * 对应 Go avatarHistoryItemFromModel：
     *   - Id          ← item.HistoryID
     *   - AvatarUrl   ← item.AvatarURL
     *   - ContentType ← item.ContentType
     *   - SizeBytes   ← item.SizeBytes
     *   - IsCurrent   ← item.IsCurrent
     *   - CreateTime  ← item.CreateTime.Format(time.RFC3339)（非空时）
     */
    public AvatarHistoryItem fromModel(UserAvatarHistoryEntity h) {
        if (h == null) return null;
        AvatarHistoryItem.Builder b = AvatarHistoryItem.newBuilder()
                .setId(h.getHistoryId() == null ? "" : h.getHistoryId())
                .setAvatarUrl(h.getAvatarUrl() == null ? "" : h.getAvatarUrl())
                .setContentType(h.getContentType() == null ? "" : h.getContentType())
                .setSizeBytes(h.getSizeBytes() == null ? 0L : h.getSizeBytes())
                .setIsCurrent(Boolean.TRUE.equals(h.getIsCurrent()));
        LocalDateTime ct = h.getCreateTime();
        if (ct != null) {
            // ISO_OFFSET_DATE_TIME 输出形如 "2026-09-23T10:15:30Z"，与 Go 的 time.RFC3339 一致
            b.setCreateTime(ct.atOffset(ZoneOffset.UTC).toInstant().toString());
        }
        return b.build();
    }

    /** 对应 Go 端 strings.TrimSpace(value) */
    public String normalizeAvatarUrl(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * 对应 Go 端 model.NormalizeAvatarContentType：
     *   trimmed = strings.TrimSpace(value)
     *   if trimmed == "" { return "image/png" }   // D-012
     *   return trimmed
     */
    public String normalizeContentType(String value) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.isEmpty() ? "image/png" : trimmed;
    }

    /** 对应 Go 端 strings.TrimSpace */
    public String normalizeHistoryId(String value) {
        return value == null ? "" : value.trim();
    }
}