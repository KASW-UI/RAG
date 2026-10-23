package com.rag.users.user.rpc.service;

import com.rag.users.user.proto.AvatarHistoryItem;
import com.rag.users.user.rpc.domain.UserAvatarHistoryEntity;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneId;
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
     * <p>🔴 <b>2026-10-23（DRIFT-USERRPC-V-004 可观察半边）：偏移量必须落在"进程本地时区"，不得叠 {@code atOffset(UTC)}</b>。
     * <p>Go 出处（渲染点）：{@code service/user/user/rpc/internal/logic/avatarhelpers.go:16-19}
     * <pre>
     *   createTime := ""
     *   if !item.CreateTime.IsZero() {
     *       createTime = item.CreateTime.Format(time.RFC3339)
     *   }
     * </pre>
     * Go 的 {@code time.Time} 自带 location，{@code Format} 打印的就是<b>该 location 的偏移量</b>
     * （非 UTC 时形如 {@code 2026-10-23T21:01:48+08:00}）。
     * <p>⚠️ <b>原实现 {@code ct.atOffset(ZoneOffset.UTC)} 是错的</b>：它把读到的时刻强行标成 UTC，
     * 于是在 JVM 默认时区非 UTC 时<b>渲染出的是另一个时刻的墙钟</b>（实测差 8 小时）
     * 且偏移标签恒为 {@code Z}。实测（双侧活体对拍，2026-10-23 21:01）：
     * <pre>
     *   Go  : "2026-10-23T21:01:48+08:00"
     *   RAG : "2026-10-23T21:01:48Z"      ← 修复前
     * </pre>
     * <p>⚠️ <b>偏移量由"进程本地时区"决定，不是 PG 会话时区</b>（2026-10-23 实测更正旧台账）：
     * 把 Go 进程 TZ 改成 UTC（DSN 的 {@code TimeZone=Asia/Shanghai} 原样不动），
     * Go 立刻渲染成 {@code …Z} ⇒ 决定者是 Go 的 {@code time.Local}
     * （源码：pgx v5.10.0 {@code pgtype/timestamptz.go:270-276}，{@code ScanLocation} 默认 nil）。
     * <p>⚠️ <b>但 pgjdbc 恒给 UTC</b>（实测，pgjdbc 42.7.13 独立 JDBC 探针）：
     * <pre>
     *   session TZ=UTC          → getObject(OffsetDateTime) = 2026-01-01T08:00Z  offset=Z
     *   session TZ=Asia/Shanghai → getObject(OffsetDateTime) = 2026-01-01T08:00Z  offset=Z
     * </pre>
     * ⇒ 所以 RAG 侧<b>不能</b>直接用驱动给的偏移量，必须显式换算到 {@link ZoneId#systemDefault()}
     * 再格式化 —— 那才是 Go 的 {@code time.Local} 语义。
     * ⇒ 也因此<b>不要</b>在 JDBC URL 上钉 {@code TimeZone}：那会让 RAG 依赖配置、
     * Go 依赖进程环境，两者在"配置 ≠ 环境"时分叉。
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
        OffsetDateTime ct = h.getCreateTime();
        if (ct != null) {
            // 🔴 渲染必须落在**进程本地时区**（Go 的 time.Local），而不是 pgjdbc 给的 UTC。
            //
            // ⚠️ 实测（pgjdbc 42.7.13，2026-10-23，独立 JDBC 探针）：
            //     session TZ=UTC          → getObject(OffsetDateTime) = 2026-01-01T08:00Z  offset=Z
            //     session TZ=Asia/Shanghai → getObject(OffsetDateTime) = 2026-01-01T08:00Z  offset=Z
            //   ⇒ pgjdbc 把 timestamptz 取成 OffsetDateTime 时**恒用 UTC**，
            //     与会话时区、与 JVM 默认时区都无关（MyBatis 的 OffsetDateTimeTypeHandler
            //     走的就是 rs.getObject(i, OffsetDateTime.class)）。
            //   ⇒ 而 Go 侧（pgx v5.10.0 pgtype/timestamptz.go:270-276，ScanLocation 默认 nil）
            //     落回 **time.Local** ⇒ Format(RFC3339) 打印的是**进程本地时区**的偏移量。
            //   ⇒ 两侧要同形，必须由 RAG 侧显式换算到 JVM 本地时区后再格式化。
            //
            // ⚠️ atZoneSameInstant（不是 withOffsetSameInstant(UTC)）：
            //   前者换的是**时区**（同一时刻、不同的墙上表示），
            //   后者会把时刻标成 UTC —— 那正是本条修复前的错误（实测差 8 小时）。
            OffsetDateTime local = ct.atZoneSameInstant(ZoneId.systemDefault()).toOffsetDateTime();
            // XXX 布局在偏移为 0 时输出 "Z"、否则输出 "+08:00"，与 Go 的 "Z07:00" 逐字符同构。
            b.setCreateTime(RFC3339.format(local));
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