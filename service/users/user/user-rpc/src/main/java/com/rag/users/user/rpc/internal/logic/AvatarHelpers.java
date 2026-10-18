package com.rag.users.user.rpc.internal.logic;

import com.rag.users.user.proto.AvatarHistoryItem;
import com.rag.users.user.rpc.internal.model.UserAvatarHistory;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * AvatarHistoryItem 转换 + 字段归一化。
 * 对应 Go 端 internal/logic/avatarhelpers.go 的两个工具函数：
 *   func avatarHistoryItemFromModel(item *model.UserAvatarHistory) *pb.AvatarHistoryItem
 *   func normalizedAvatarURL(value string) string
 *
 * Go 端 create_time 用 time.RFC3339 字符串发给 wire（proto 字段类型也是 string）。
 * Java 侧保持 String 透传（不做 Instant.parse）——wire 一致。
 */
public final class AvatarHelpers {

    private AvatarHelpers() {}

    /**
     * 对应 Go avatarHistoryItemFromModel：
     *   - Id          ← item.HistoryID
     *   - AvatarUrl   ← item.AvatarURL
     *   - ContentType ← item.ContentType
     *   - SizeBytes   ← item.SizeBytes
     *   - IsCurrent   ← item.IsCurrent
     *   - CreateTime  ← item.CreateTime.Format(time.RFC3339)（非空时）
     */
    public static AvatarHistoryItem fromModel(UserAvatarHistory h) {
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
    public static String normalizedAvatarURL(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * 对应 Go 端 model.NormalizeAvatarContentType：
     *   trimmed = strings.TrimSpace(value)
     *   if trimmed == "" { return "image/png" }   // D-012
     *   return trimmed
     */
    public static String normalizeContentType(String value) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.isEmpty() ? "image/png" : trimmed;
    }

    /** 对应 Go 端 strings.TrimSpace */
    public static String normalizedHistoryId(String value) {
        return value == null ? "" : value.trim();
    }
}