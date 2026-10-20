package com.rag.users.user.rpc.service;

import com.rag.users.user.proto.AvatarHistoryItem;
import com.rag.users.user.rpc.domain.UserAvatarHistoryEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

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
     * 对齐 Go {@code time.RFC3339} 的渲染格式。
     *
     * <p><b>为什么不能用 {@code Instant.toString()}</b>（DRIFT-006 修复）：
     * Go 的 {@code Format(time.RFC3339)} 里布局是 {@code "2006-01-02T15:04:05Z07:00"}，
     * **不含 {@code .999} 段**，而 {@code Time.Format} 只输出布局指定的字段
     * ⇒ <b>纳秒被整段丢弃</b>，输出恒为秒精度。
     * 而 {@code Instant.toString()} 在纳秒非零时<b>必带</b>小数部分，
     * Postgres {@code TIMESTAMP} 是微秒精度、{@code CURRENT_TIMESTAMP} 几乎总是非零
     * ⇒ 同一份数据两边渲染出的字符串必然不同：
     * <pre>
     *   Go    : "2026-09-23T10:15:30Z"
     *   原 Java: "2026-09-23T10:15:30.123456Z"
     * </pre>
     * 该字符串会一路透传到 HTTP body 的 {@code create_time}，是纯客户端可见的差异。
     *
     * <p><b>时区残留问题（UNRESOLVED，见验证报告 U-2）</b>：Go 的 DSN 固定
     * {@code TimeZone=Asia/Shanghai}，RAG 的 JDBC URL 未固定；且本方法把读到的墙钟值
     * 一律标成 UTC。这里只对齐「格式」这一可证部分，偏移量标签仍待真实 PG 栈验证后再定。
     */
    private static final DateTimeFormatter RFC3339 =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    /**
     * 对应 Go avatarHistoryItemFromModel：
     *   - Id          ← item.HistoryID
     *   - AvatarUrl   ← item.AvatarURL
     *   - ContentType ← item.ContentType
     *   - SizeBytes   ← item.SizeBytes
     *   - IsCurrent   ← item.IsCurrent
     *   - CreateTime  ← item.CreateTime.Format(time.RFC3339)（非零时）
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
            // 零偏移渲染成 "…Z"，与 Go 的 RFC3339 在 UTC 下逐字符一致
            b.setCreateTime(RFC3339.format(ct.atOffset(ZoneOffset.UTC)));
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