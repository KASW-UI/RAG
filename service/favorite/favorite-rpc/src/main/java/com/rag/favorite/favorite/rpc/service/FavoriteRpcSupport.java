package com.rag.favorite.favorite.rpc.service;

import com.rag.favorite.favorite.proto.FavoriteFolder;
import com.rag.favorite.favorite.proto.FavoriteItem;
import com.rag.favorite.favorite.rpc.domain.FavoriteFolderEntity;
import com.rag.favorite.favorite.rpc.domain.FavoriteItemEntity;
import com.rag.common.logger.WithArticleID;
import com.rag.common.logger.WithUserID;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Locale;

/**
 * 跨 7 个 logic 的共享片段 —— 对应 Go {@code service/favorite/rpc/internal/logic/helper.go}
 * 的 6 类非依赖型职责（:48-90）。
 *
 * <p><b>为什么这些进 Support 而不是一个 "Helper"</b>：Go 把它们散在一个 helper 里，
 * 但它们分属三种不同的复用面 ——
 * <ul>
 *   <li><b>纯函数</b>（normalize / toProto / isUniqueViolation）：无状态、可单测、
 *       被 7 个 logic 共用 ⇒ 静态方法即可，不需要 Spring Bean。</li>
 *   <li><b>日志选项</b>（userLogOption / articleLogOption）：与 common/logger 的
 *       {@link WithUserID} / {@link WithArticleID} 逐字对齐。</li>
 *   <li><b>跨域依赖调用</b>（ensureUserExists / resolveArticleSnapshot）：
 *       需要注入 client ⇒ 拆成 {@link FavoriteUserGuard} 与
 *       {@link FavoriteArticleSnapshotResolver} 两个 Bean（见那两处说明）。</li>
 * </ul>
 */
public final class FavoriteRpcSupport {

    private FavoriteRpcSupport() {
        // 静态工具，禁止实例化
    }

    // ==================== JSON（favorite_authority.go:60-70 strictFavoriteJSON） ====================

    /**
     * 🔴 与 Go {@code strictFavoriteJSON}（favorite_authority.go:60-70）逐条同构的 mapper。
     *
     * <p>Go 的实现：
     * <pre>
     * decoder := json.NewDecoder(bytes.NewReader(raw))
     * decoder.DisallowUnknownFields()
     * if err := decoder.Decode(value); err != nil { return err }
     * if err := decoder.Decode(new(any)); err != io.EOF { return errors.New("trailing JSON") }
     * </pre>
     *
     * <p><b>两条规则一条都不能少</b>：
     * <ol>
     *   <li>{@link DeserializationFeature#FAIL_ON_UNKNOWN_PROPERTIES} ——
     *       信封里多一个字段就必须判为「冻结信封损坏」（⇒ status=3 blocked，
     *       要求人工迁移），而不是"宽容地忽略"。</li>
     *   <li>拒绝尾随 JSON —— {@code {}{} } 或 {@code {} 尾随字节} 都是损坏。</li>
     * </ol>
     *
     * <p>⚠️ <b>不能直接用 {@code com.rag.common.communityfact.Wire.MAPPER}</b>：
     * 那个字段是 {@code package-private}（Wire.java:115），
     * favorite-rpc 拿不到 ⇒ 必须自持一份。
     *
     * <h4>⚠️ 与 Go 的转义差异（已知、且被 JCS 归一化掉）</h4>
     * <p>Go 的 {@code json.Marshal} 默认做 <b>HTML 转义</b>（{@code < > &} → {@code \u003c \u003e \u0026}）
     * 并转义 U+2028/U+2029；Jackson 2 默认<b>不</b>转义这些。
     * ⇒ 本域写入的 payload <b>原始字节</b>与 Go 不同。
     * <p><b>为什么不影响正确性</b>：所有跨进程比对都经过 JCS 规范化
     * （{@code Wire.canonicalHash} ↔ Go {@code favoriteJCSHash}），
     * 而 RFC 8785 对这些字符的规范形式<b>唯一</b> ⇒ 两侧 hash 必然相同。
     * <p>⚠️ <b>但不要因此去改本 mapper 加 HTML 转义</b>：那会让
     * 「写入时转义」与「投递/权威读时用同一份字节」之间多一次不必要的转换，
     * 且 Jackson 没有与 Go 逐字等价的 HTML 转义开关（要用自定义
     * {@code CharacterEscapes} 覆盖）。当前形态是正确的最小实现。
     */
    public static final com.fasterxml.jackson.databind.ObjectMapper STRICT_JSON =
            new com.fasterxml.jackson.databind.ObjectMapper()
                    .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    /**
     * 对应 Go {@code strictFavoriteJSON} 的「拒绝尾随 JSON」那一步。
     *
     * <p>Jackson 的 {@code readValue} 默认允许尾随内容 ⇒ 必须显式再读一次，
     * 有内容就判失败。
     */
    public static void rejectTrailingJson(com.fasterxml.jackson.core.JsonParser parser) {
        try {
            if (parser.nextToken() != null) {
                throw new IllegalArgumentException("trailing JSON");
            }
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("trailing JSON", e);
        }
    }

    // ==================== 归一化（helper.go:56-62） ====================

    /**
     * 对应 Go {@code normalizeFolderName}（helper.go:56-58）：
     * {@code return strings.TrimSpace(name)}。
     *
     * <p>⚠️ <b>只 TrimSpace，不做其它处理</b>。Go 不小写、不压缩内部空白、
     * 不做 Unicode 归一化 —— 加任何一条都会改变唯一键 {@code uk_user_folder_name}
     * 的判定范围。
     */
    public static String normalizeFolderName(String name) {
        return name == null ? "" : name.trim();
    }

    /**
     * 🔴 对应 Go {@code normalizeTargetType}（helper.go:60-62）：
     * {@code return strings.ToLower(strings.TrimSpace(targetType))}。
     *
     * <p><b>小写化是 L2 业务语义，不是格式化偏好</b>：
     * 唯一键 {@code uk_folder_target(folder_id, target_id, target_type)}
     * 建在<b>规范化之后</b>的值上 ⇒ {@code "Article"} 与 {@code "article"}
     * 指向同一条收藏，第二次收藏返回 {@code AlreadyExists/5302}。
     *
     * <h3>🔴 必须用「简单大小写映射」，不能用 {@code String.toLowerCase}</h3>
     * <p>Go 的 {@code strings.ToLower} 走 {@code unicode.ToLower}，
     * 对每个 code point 做<b>一对一</b>映射；而 Java 的
     * {@code String.toLowerCase} 做<b>完整</b>映射，会把某些字符展开成多个 code point。
     * <p><b>实测差异（本仓有 Go 侧对照）</b>：
     * <pre>
     * 输入 "VİDEO"（U+0130 是拉丁大写 I 带点）
     *   Go   strings.ToLower → "video"
     *   Java toLowerCase(Locale.ROOT) → "vi̇deo"（i + U+0307 组合尖音符）
     * </pre>
     * ⇒ 两侧会造出<b>不同的 {@code target_type}</b>，唯一键的判定范围随之不同：
     * 同一条收藏在 Go 侧是「重复」、在 Java 侧可能是「两条不同的收藏」。
     * <p>修法：逐 code point 调 {@link Character#toLowerCase(int)}（该重载就是简单映射，
     * 与 Go 的 {@code unicode.ToLower} 语义一致）。
     *
     * <p>⚠️ <b>Locale 仍要显式指定 ROOT</b>：虽然简单映射与 locale 无关，
     * 但留着显式参数能防止后人"简化"回无参重载（无参版走默认 locale，
     * 在土耳其语环境下 {@code "I"} 会变 {@code "ı"}）。
     */
    public static String normalizeTargetType(String targetType) {
        if (targetType == null) {
            return "";
        }
        String trimmed = targetType.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        // 快速路径：全是 ASCII 时与 Go 完全等价，且省掉 codePointAt 的开销
        boolean ascii = true;
        for (int i = 0; i < trimmed.length(); i++) {
            if (trimmed.charAt(i) > 0x7F) {
                ascii = false;
                break;
            }
        }
        if (ascii) {
            return trimmed.toLowerCase(Locale.ROOT);
        }
        StringBuilder lower = new StringBuilder(trimmed.length());
        for (int i = 0; i < trimmed.length(); ) {
            int codePoint = trimmed.codePointAt(i);
            // Character.toLowerCase(int) = 简单映射（Unicode 官方 toLowercase 差值表），
            // 与 Go 的 unicode.ToLower 同构
            lower.appendCodePoint(Character.toLowerCase(codePoint));
            i += Character.charCount(codePoint);
        }
        return lower.toString();
    }

    // ==================== model → proto（helper.go:64-85） ====================

    /**
     * 对应 Go {@code toProtoFolder}（helper.go:64-72）。
     *
     * <p>🔴 {@code CreatedAt / UpdatedAt} 是 <b>Unix 毫秒</b>（{@code UnixMilli()}），
     * <b>不是秒</b>。proto 里两个字段都是 {@code int64}，写错单位是肉眼看不出的 L1 破坏。
     */
    public static FavoriteFolder toProtoFolder(FavoriteFolderEntity folder) {
        return FavoriteFolder.newBuilder()
                .setFolderId(folder.getFolderId())
                .setUserId(folder.getUserId())
                .setName(folder.getName())
                .setCreatedAt(unixMillis(folder.getCreateTime()))
                .setUpdatedAt(unixMillis(folder.getUpdateTime()))
                .build();
    }

    /**
     * 对应 Go {@code toProtoItem}（helper.go:74-85）。
     *
     * <p>⚠️ <b>没有 {@code updated_at}</b>：proto 的 {@code FavoriteItem} 只有
     * {@code created_at}（favorite.proto:22），因为表里也只有 {@code create_time}。
     */
    public static FavoriteItem toProtoItem(FavoriteItemEntity item) {
        return FavoriteItem.newBuilder()
                .setFavoriteId(item.getFavoriteId())
                .setFolderId(item.getFolderId())
                .setUserId(item.getUserId())
                .setTargetId(item.getTargetId())
                .setTargetType(item.getTargetType())
                .setTitle(nullToEmpty(item.getTitle()))
                .setCover(nullToEmpty(item.getCover()))
                .setCreatedAt(unixMillis(item.getCreateTime()))
                .build();
    }

    /**
     * {@code time.Time.UnixMilli()} 的等价物（{@code time/time.go:1437}）。
     *
     * <p>⚠️ 列是 {@code timestamptz}，读出来带偏移 ⇒ {@code toInstant()} 得到绝对时刻。
     * 若误用 {@code OffsetDateTime.toLocalDateTime()} 或直接取 epoch 字段，
     * 就会把「数据库时区」变成「JVM 默认时区」⇒ 整体平移。
     *
     * <p>Go 的 {@code UnixMilli()} 对零值 {@code time.Time} 返回一个巨大的负数，
     * 但那不会发生 —— 业务写入必带 autoCreateTime。{@code null} 在这里
     * 只可能来自人工插入的坏数据，按 0 处理（proto3 的 int64 默认值）。
     */
    private static long unixMillis(OffsetDateTime time) {
        return time == null ? 0L : time.toInstant().toEpochMilli();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    // ==================== 唯一冲突识别（helper.go:87-90） ====================

    /**
     * 🔴 对应 Go {@code isUniqueViolation}（helper.go:87-90）：
     * <pre>
     * var pgErr *pgconn.PgError
     * return errors.As(err, &amp;pgErr) &amp;&amp; pgErr.Code == "23505"
     * </pre>
     *
     * <p><b>只认 PG SQLSTATE {@code 23505}（unique_violation）</b>。
     *
     * <p>⚠️ <b>不要用 {@code SQLIntegrityConstraintViolationException} 兜底</b>：
     * 它还包含 {@code 23503}（外键）、{@code 23502}（not null）、{@code 23514}（check），
     * 本域另外三个 CHECK 约束（{@code aggregate_version IN (1,2)}、
     * {@code target_revision} 作用域、legacy 标记的 5 个 CHECK）失败时也抛这个异常
     * ⇒ 把它们误判成「业务重复」会把 {@code Internal/5008} 变成 {@code AlreadyExists/5302}。
     *
     * <p>⚠️ <b>必须沿 cause 链找</b>：MyBatis 会把 PG 的 {@code PSQLException}
     * 包在 {@code org.springframework.dao.DuplicateKeyException} 里，
     * 直接 {@code instanceof} 顶层异常会漏。
     */
    public static boolean isUniqueViolation(Throwable err) {
        Throwable current = err;
        while (current != null) {
            if (current instanceof SQLException sqlException
                    && PG_UNIQUE_VIOLATION.equals(sqlException.getSQLState())) {
                return true;
            }
            Throwable next = current.getCause();
            if (next == current) {
                break;
            }
            current = next;
        }
        return false;
    }

    /** PG 的 unique_violation SQLSTATE（Go {@code pgconn.PgError.Code}）。 */
    private static final String PG_UNIQUE_VIOLATION = "23505";

    // ==================== 日志选项（helper.go:48-54） ====================

    /** 对应 Go {@code userLogOption}（helper.go:48-50）：{@code logger.WithUserID(strconv.FormatInt(userID, 10))}。 */
    public static WithUserID userLogOption(long userId) {
        return WithUserID.of(String.valueOf(userId));
    }

    /** 对应 Go {@code articleLogOption}（helper.go:52-54）：{@code logger.WithArticleID(strings.TrimSpace(articleID))}。 */
    public static WithArticleID articleLogOption(String articleId) {
        return WithArticleID.of(articleId == null ? "" : articleId.trim());
    }
}