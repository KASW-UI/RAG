package com.rag.user.user.rpc.internal.logic;

import com.rag.user.user.proto.AvatarHistoryItem;
import com.rag.user.user.rpc.internal.model.UserAvatarHistory;

import java.time.format.DateTimeFormatter;

/**
 * 对应 Go: internal/logic/avatarhelpers.go
 *
 * Go 端的 avatarhelpers.go 提供两个纯函数:
 *   1. avatarHistoryItemFromModel - 把 model.UserAvatarHistory 转成 pb.AvatarHistoryItem,
 *      其中 CreateTime 从 time.Time 格式化成 RFC3339 string
 *   2. normalizedAvatarURL - strings.TrimSpace(value)
 *
 * Java 端,proto 已经用 java_multiple_files=true 把 AvatarHistoryItem 生成出来,
 * 这里只需要写转换函数 + 一个 URL 清洗函数。
 *
 * 注意:AvatarHistoryItem 的 create_time 在 proto 里是 string 字段,
 * Go 端用 RFC3339 (例如 2026-10-03T01:23:45+08:00),
 * Java 端为了对齐同样的序列化格式,用 DateTimeFormatter.ISO_OFFSET_DATE_TIME。
 */
public final class AvatarHelpers {

    private AvatarHelpers() {}

    /**
     * 对应 Go: avatarHistoryItemFromModel(item *model.UserAvatarHistory) *pb.AvatarHistoryItem
     * 入参 item == nil 时返回 null(Go 端同样 nil-safe)。
     */
    public static AvatarHistoryItem toProto(UserAvatarHistory item) {
        if (item == null) {
            return null;
        }

        String createTime = "";
        if (item.getCreateTime() != null) {
            createTime = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
                    item.getCreateTime().atZone(java.time.ZoneId.systemDefault())
            );
        }

        return AvatarHistoryItem.newBuilder()
                .setId(item.getHistoryId())
                .setAvatarUrl(item.getAvatarUrl())
                .setContentType(item.getContentType())
                .setSizeBytes(item.getSizeBytes() == null ? 0L : item.getSizeBytes())
                .setIsCurrent(item.getIsCurrent() != null && item.getIsCurrent())
                .setCreateTime(createTime)
                .build();
    }

    /**
     * 对应 Go: normalizedAvatarURL(value string) string —— strings.TrimSpace
     */
    public static String normalizeAvatarUrl(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }
}