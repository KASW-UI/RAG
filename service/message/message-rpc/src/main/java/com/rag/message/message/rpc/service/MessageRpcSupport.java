package com.rag.message.message.rpc.service;

import com.rag.message.message.common.errmsg.MessageErrorCode;
import com.rag.message.message.common.errmsg.MessageErrorMessages;
import com.rag.message.message.proto.BaseResp;
import com.rag.message.message.proto.ChatMessageItem;
import com.rag.message.message.proto.ConversationItem;
import com.rag.message.message.proto.ConversationStatus;
import com.rag.message.message.proto.NotificationItem;
import com.rag.message.message.proto.NotificationKind;
import com.rag.message.message.proto.SenderRole;
import com.rag.message.message.rpc.domain.ConversationEntity;
import com.rag.message.message.rpc.domain.ConversationMessageEntity;
import com.rag.message.message.rpc.domain.NotificationEntity;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 跨 RPC 共享业务工具 —— 对应镜像 Go 端 {@code service/message/rpc/internal/logic/helper.go}（MessageHelpers）。
 *
 * <p>承担：
 * <ul>
 *   <li>响应工厂：successBaseResp / toNotificationItem / toMessageItem / buildConversationItem</li>
 *   <li>校验 / 归一化：validRole / normalizeLimit / trimContent / runeLen</li>
 *   <li>参与者规范化：canonicalParticipants / isParticipant / peerOf / canSend</li>
 *   <li>错误构造：grpcRuntimeError</li>
 *   <li>工具：notificationKindLabel / dedupeRecipients / newNotificationRecord / trimPreview</li>
 * </ul>
 *
 * <p>用 {@code @Component} 让 Spring 注入到 DomainService；包成类是为 Java 没有 package-level function。
 * <br>ServiceContext 中"依赖外部 stub 的两个方法"（loadProfile / collectBroadcastRecipients）已迁到
 * {@link com.rag.message.message.rpc.client.UserRpcClient} 与
 * {@link com.rag.message.message.rpc.client.AdminRpcClient}，避免本工具类持外部依赖。
 *
 * <p>RAG 端"1 DomainService 9 方法"模式：所有 Logic 调用都走 MessageRpcSupport + 注入的
 * Mapper / Client / Metrics / Logger，**不引入 ServiceContext 中间层**。
 */
@Component
public class MessageRpcSupport {

    /**
     * 预览截断上限（Go {@code message.go:236} 的字面量 {@code 80}，单位是 <b>rune / 码点</b>）。
     * 曾被误写成 255（抄了 {@code last_message_preview} 的列宽），见 {@link #trimPreview} 的 javadoc。
     */
    private static final int TRIM_PREVIEW_MAX_RUNES = 80;

    // =============================================================================
    // 响应工厂 —— successBaseResp / toNotificationItem / toMessageItem / buildConversationItem
    // =============================================================================

    /** 对应 Go {@code successBaseResp()}：code=200 / msg="OK"。 */
    public static BaseResp successBaseResp() {
        return BaseResp.newBuilder()
                .setCode(MessageErrorCode.SUCCESS.getCode())
                .setMsg(MessageErrorMessages.getMsg(MessageErrorCode.SUCCESS.getCode()))
                .build();
    }

    /**
     * 🔴 <b>DRIFT-MSG-V-007（2026-10-22 修）：无时区墙钟 → epoch 秒，必须按 <b>JVM 本地时区</b>解释</b>
     *
     * <p><b>Go 侧怎么做的</b>：
     * <pre>
     * GORM/PG 的时间列是 timestamptz（gorm postgres driver postgres.go:251-255 把
     * schema.Time 映射成 "timestamptz"），DSN 显式钉 TimeZone=Asia/Shanghai（db.go:22）。
     * 读出来是 time.Time（一个绝对瞬时）⇒ .Unix() 直接就是真实 epoch 秒，**与时区无关**。
     * </pre>
     *
     * <p><b>RAG 侧的差异</b>：DDL 用的是 {@code TIMESTAMP}（无时区），实体是 {@link LocalDateTime}。
     * pgjdbc 对无时区列<b>不做任何换算</b>，写进去什么墙钟就读出什么墙钟
     * ⇒ 这是一个<b>无时区墙钟</b>，本身不含 epoch 信息。
     * 修复前用的是 {@code toEpochSecond(ZoneOffset.UTC)} —— 把墙钟<b>当成 UTC</b>。
     *
     * <p><b>后果（部署时区 = Asia/Shanghai，已实测）</b>：
     * <pre>
     * JVM default zone       = Asia/Shanghai
     * 写入的墙钟              = 2026-10-22 19:08:17
     * 真实瞬时                = 2026-10-22T11:08:17Z
     * Go  .Unix()            = 1792667297
     * 修复前 toEpochSecond(UTC)= 1792696097   差 +8 小时 ⇒ <b>RAG 比 Go 晚 8 小时</b>
     * 修法 atZone(systemDefault)= 1792667297   差  0 小时
     * </pre>
     * ⇒ 客户端看到的所有 {@code created_at} / {@code latest_message_at} / {@code updated_at}
     * <b>都比真源晚 8 小时</b>。
     *
     * <p><b>为什么用 {@link ZoneId#systemDefault()} 而不是硬编码 {@code Asia/Shanghai}</b>：
     * 写侧用的就是 {@code LocalDateTime.now()}（= JVM 本地墙钟），读侧也用本地时区解释，
     * <b>写读自洽 ⇒ 无论 JVM 跑在哪个时区，epoch 都正确</b>。
     * 硬编码 Asia/Shanghai 反而在「JVM 改成 UTC」的部署里会错。
     * 这也是本次决策的结论：<b>不要求部署时区固定，只要求写读用同一个时区解释</b>。
     *
     * <p><b>剩下的稳健性缺口（不在本次修复范围）</b>：
     * 列类型仍是 {@code TIMESTAMP} 而非 {@code TIMESTAMPTZ}。
     * 在「一个 DB 只由一个时区的 JVM 写」的前提下，上面的自洽成立；
     * 但一旦出现多时区写入（迁移期新旧实例并存）或换时区回读历史数据，就会重新错位。
     * 要根治需 {@code V4} 迁移把列改成 {@code TIMESTAMPTZ}（连带修读侧），见 AGENTS.md §6.7.10。
     */
    private static long toEpochSecond(LocalDateTime wallClock) {
        return wallClock.atZone(ZoneId.systemDefault()).toEpochSecond();
    }

    /** 对应 Go {@code toNotificationItem(item model.Notification) *pb.NotificationItem}：
     * Notification entity → wire DTO 转换。{@code Extra} 走 {@link Map} 1:1 输出。
     *
     * <p>{@code CreatedAt} 经 {@link #toEpochSecond} 转秒级时间戳，
     * 与 Go 端 {@code CreatedAt.Unix()} 1:1（见 DRIFT-MSG-V-007 的时区论证）。
     */
    public static NotificationItem toNotificationItem(NotificationEntity item) {
        if (item == null) {
            return null;
        }
        return NotificationItem.newBuilder()
                .setId(item.getId())
                .setRecipientId(item.getRecipientId())
                .setSenderId(item.getSenderId())
                .setSenderRole(SenderRole.forNumber(item.getSenderRole()))
                .setKind(NotificationKind.forNumber(item.getKind()))
                .setTitle(item.getTitle())
                .setContent(item.getContent())
                .setIsRead(item.isRead())
                .setCreatedAt(item.getCreatedAt() != null ? toEpochSecond(item.getCreatedAt()) : 0L)
                .putAllExtra(safeMap(item.getExtra()))
                .build();
    }

    /** 对应 Go {@code toMessageItem(item model.ConversationMessage) *pb.ChatMessageItem}。 */
    public static ChatMessageItem toMessageItem(ConversationMessageEntity item) {
        if (item == null) {
            return null;
        }
        return ChatMessageItem.newBuilder()
                .setId(item.getId())
                .setConversationId(item.getConversationId())
                .setSenderId(item.getSenderId())
                .setSenderRole(SenderRole.forNumber(item.getSenderRole()))
                .setRecipientId(item.getRecipientId())
                .setRecipientRole(SenderRole.forNumber(item.getRecipientRole()))
                .setContent(item.getContent())
                .setIsRead(item.isRead())
                .setCreatedAt(item.getCreatedAt() != null ? toEpochSecond(item.getCreatedAt()) : 0L)
                .build();
    }

    private static Map<String, String> safeMap(Map<String, String> in) {
        if (in == null || in.isEmpty()) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(in);
    }

    // =============================================================================
    // 校验 / 归一化 —— validRole / normalizeLimit / trimContent / runeLen
    // =============================================================================

    /** 对应 Go {@code validRole(role pb.SenderRole) bool}：只有 USER / ADMIN 合法。 */
    public static boolean validRole(SenderRole role) {
        return role == SenderRole.USER || role == SenderRole.ADMIN;
    }

    /** role int → SenderRole；非法 int 一律返回 UNSPECIFIED。 */
    public static SenderRole senderRoleFromInt(int role) {
        return SenderRole.forNumber(role);
    }

    /**
     * 对应 Go {@code normalizeLimit(limit, defaultLimit, maxLimit int32) int32}：
     * ≤0 视为 default；maxLimit &gt; 0 时把超出的部分截到 maxLimit（&lt;=0 不截断）。
     */
    public static int normalizeLimit(int limit, int defaultLimit, int maxLimit) {
        if (limit <= 0) {
            limit = defaultLimit;
        }
        if (maxLimit > 0 && limit > maxLimit) {
            limit = maxLimit;
        }
        return limit;
    }

    /** 对应 Go {@code trimContent(content string) string}：{@code strings.TrimSpace} 等价。 */
    public static String trimContent(String content) {
        return content == null ? "" : trimGoSpace(content);
    }

    /**
     * <b>⚠️ DRIFT-MSG-V-008（2026-10-22 修）</b>：复刻 Go {@code strings.TrimSpace} 的空白集合。
     *
     * <p><b>为什么不能用 {@link String#trim()} 或 {@link String#strip()}</b>——
     * 修复前本文件用的是 {@code String.trim()}，两者<b>都不等价</b>，且是<b>双向不等价</b>：
     * <table border="1">
     *   <caption>三种 trim 的差异方向</caption>
     *   <tr><th>字符</th><th>Go TrimSpace</th><th>Java trim()</th><th>Java strip()</th></tr>
     *   <tr><td>{@code U+00A0} NBSP</td><td>✔ 空白</td><td>✘ <b>漏</b></td><td>✘ <b>漏</b>（non-breaking）</td></tr>
     *   <tr><td>{@code U+3000} 全角空格</td><td>✔ 空白</td><td>✘ 漏</td><td>✔</td></tr>
     *   <tr><td>{@code U+2028} / {@code U+2029}</td><td>✔ 空白</td><td>✘ 漏</td><td>✔</td></tr>
     *   <tr><td>{@code U+0085} NEL</td><td>✔ 空白</td><td>✘ 漏</td><td>✘ 漏</td></tr>
     *   <tr><td>{@code U+202F} / {@code U+2007}</td><td>✔ 空白</td><td>✘ 漏</td><td>✘ <b>漏</b>（no-break 类）</td></tr>
     *   <tr><td>{@code U+001C}–{@code U+001F}</td><td>✘ <b>非空白</b></td><td>✔ <b>多删</b></td><td>✔ <b>多删</b></td></tr>
     * </table>
     * ⇒ {@code strip()} 也不是答案：它<b>少</b> 5 个码点、<b>多</b> 4 个码点。
     *
     * <p><b>可观察影响（这就是本条被登记为语义漂移而不是"表达层差异"的原因）</b>：
     * <pre>
     * 输入 content = "\u00A0\u00A0"（两个不换行空格）
     *   Go  : TrimSpace → "" → content == ""  → INVALID_ARGUMENT + 5003 invalid parameter
     *   修复前: trim()     → 原样非空          → 继续入库，写进一条只含 NBSP 的消息
     * </pre>
     * 它同时改变<b>输入解释</b>（空串判定）与<b>持久化结果</b>，按 AGENTS.md §3.2
     * 必须作为行为差异处理，不能按"表达层"豁免。
     *
     * <p><b>集合的权威来源（不是记忆，是实测）</b>：
     * Go {@code unicode.IsSpace}（{@code src/unicode/graphic.go:128}）对 {@code r <= 0xFF}
     * 特判 8 个码点，其余走 {@code White_Space} 表（{@code src/unicode/tables.go:8570}）。
     * 实测枚举 {@code r ∈ [0, 0x3200]} 得 <b>恰好 25 个</b>，本方法的集合与之一一对应。
     */
    private static String trimGoSpace(String s) {
        int start = 0;
        int end = s.length();
        // 按 code point 前进：代理对是 1 个 code point 占 2 个 char，
        // 按 char 前进会把代理对劈开（虽然代理对不在空白集合里，但边界推进必须正确）。
        start = skipGoSpaceForward(s, 0);
        end = skipGoSpaceBackward(s, s.length());
        return (start >= end) ? "" : s.substring(start, end);
    }

    private static int skipGoSpaceForward(String s, int index) {
        int i = index;
        while (i < s.length()) {
            int cp = s.codePointAt(i);
            if (!isGoSpace(cp)) {
                break;
            }
            i += Character.charCount(cp);
        }
        return i;
    }

    private static int skipGoSpaceBackward(String s, int index) {
        int i = index;
        while (i > 0) {
            int cp = s.codePointBefore(i);
            if (!isGoSpace(cp)) {
                break;
            }
            i -= Character.charCount(cp);
        }
        return i;
    }

    /**
     * Go {@code unicode.IsSpace} 的逐码点等价判定。
     *
     * <p>25 个码点分两段，与 Go 的实现结构一一对应：
     * {@code r <= 0xFF} 时特判 8 个，其余走 White_Space 表。
     */
    private static boolean isGoSpace(int cp) {
        if (cp <= 0xFF) {
            return cp == '\t' || cp == '\n' || cp == 0x0B || cp == '\f' || cp == '\r'
                    || cp == ' ' || cp == 0x85 || cp == 0xA0;
        }
        // Go: isExcludingLatin(White_Space, r) —— 表里 0x00A0–0x1680 这一段
        // 对 Latin-1 已在上一个分支处理完，这里从 0x1680 起算。
        return cp == 0x1680
                || (cp >= 0x2000 && cp <= 0x200A)
                || cp == 0x2028 || cp == 0x2029
                || cp == 0x202F || cp == 0x205F
                || cp == 0x3000;
    }

    /**
     * 对应 Go {@code runeLen(content string) int}：codepoint 数。
     * SendChatMessage 的"首条消息 ≤ 50 字符"用此方法度量。
     */
    public static int runeLen(String content) {
        if (content == null || content.isEmpty()) {
            return 0;
        }
        return content.codePointCount(0, content.length());
    }

    // =============================================================================
    // 参与者规范化 —— canonicalParticipants / isParticipant / peerOf / canSend
    // =============================================================================

    /**
     * 对应 Go {@code canonicalParticipants(role1, id1, role2, id2)}：
     * 先按 role asc，role 相同则按 id asc。让 uk_conversation_pair 唯一索引能找到同一对会话。
     */
    public static CanonicalPair canonicalParticipants(SenderRole role1, long id1,
                                                      SenderRole role2, long id2) {
        int r1 = role1.getNumber();
        int r2 = role2.getNumber();
        if (r1 < r2) {
            return new CanonicalPair(role1, id1, role2, id2);
        }
        if (r1 > r2) {
            return new CanonicalPair(role2, id2, role1, id1);
        }
        if (id1 <= id2) {
            return new CanonicalPair(role1, id1, role2, id2);
        }
        return new CanonicalPair(role2, id2, role1, id1);
    }

    /** 对应 Go {@code isParticipant(conv, role, id) bool}。 */
    public static boolean isParticipant(ConversationEntity conv, SenderRole role, long id) {
        if (conv == null) {
            return false;
        }
        int roleNum = role.getNumber();
        return (conv.getParticipant1Role() == roleNum && conv.getParticipant1Id() == id)
                || (conv.getParticipant2Role() == roleNum && conv.getParticipant2Id() == id);
    }

    /**
     * 对应 Go {@code peerOf(conv, role, id) (SenderRole, int64, bool)}。
     * 找不到 → (UNSPECIFIED, 0, false)。
     */
    public static PeerResult peerOf(ConversationEntity conv, SenderRole role, long id) {
        if (conv == null) {
            return new PeerResult(SenderRole.SENDER_ROLE_UNSPECIFIED, 0L, false);
        }
        int roleNum = role.getNumber();
        if (conv.getParticipant1Role() == roleNum && conv.getParticipant1Id() == id) {
            return new PeerResult(SenderRole.forNumber(conv.getParticipant2Role()),
                    conv.getParticipant2Id(), true);
        }
        if (conv.getParticipant2Role() == roleNum && conv.getParticipant2Id() == id) {
            return new PeerResult(SenderRole.forNumber(conv.getParticipant1Role()),
                    conv.getParticipant1Id(), true);
        }
        return new PeerResult(SenderRole.SENDER_ROLE_UNSPECIFIED, 0L, false);
    }

    /**
     * 对应 Go {@code canSend(conv, role, id) bool}：
     * OPEN 全员可发；PENDING 只有不是 pending_sender 的一方可发。
     */
    public static boolean canSend(ConversationEntity conv, SenderRole role, long id) {
        if (conv == null) {
            return false;
        }
        ConversationStatus status = ConversationStatus.forNumber(conv.getStatus());
        if (status == ConversationStatus.OPEN) {
            return true;
        }
        if (status != ConversationStatus.PENDING) {
            return false;
        }
        return !(conv.getPendingSenderRole() == role.getNumber() && conv.getPendingSenderId() == id);
    }

    // =============================================================================
    // ConversationItem 构造
    // =============================================================================

    /**
     * 对应 Go {@code buildConversationItem(conv, operatorRole, operatorID, unreadCount, peerName, peerEmail)}：
     * 调用方把 unreadCount / peerName / peerEmail 准备好（避免本工具类持外部 stub 依赖）。
     */
    public static ConversationItem buildConversationItem(ConversationEntity conv,
                                                          SenderRole operatorRole,
                                                          long operatorId,
                                                          long unreadCount,
                                                          String peerName,
                                                          String peerEmail) {
        PeerResult peer = peerOf(conv, operatorRole, operatorId);
        if (!peer.found()) {
            throw grpcRuntimeError(Status.Code.PERMISSION_DENIED,
                    MessageErrorCode.ERROR_FORBIDDEN.getCode());
        }
        return ConversationItem.newBuilder()
                .setConversationId(conv.getId())
                .setPeerId(peer.id())
                .setPeerRole(peer.role())
                .setPeerName(peerName == null ? "" : peerName)
                .setPeerEmail(peerEmail == null ? "" : peerEmail)
                .setStatus(ConversationStatus.forNumber(conv.getStatus()))
                .setUnreadCount(unreadCount)
                .setLatestMessage(conv.getLastMessagePreview() == null ? "" : conv.getLastMessagePreview())
                .setLatestMessageAt(conv.getLastMessageAt() != null
                        ? toEpochSecond(conv.getLastMessageAt()) : 0L)
                .setUpdatedAt(conv.getUpdatedAt() != null
                        ? toEpochSecond(conv.getUpdatedAt()) : 0L)
                .setCanSend(canSend(conv, operatorRole, operatorId))
                .build();
    }

    // =============================================================================
    // 错误构造 —— grpcRuntimeError
    // =============================================================================

    /**
     * 对应 Go {@code errWithBizCode(grpcCode codes.Code, bizCode int) error}：
     * 用 bizCode 查表得到 msg，塞进 gRPC status description，构造 {@link StatusRuntimeException}。
     */
    public static StatusRuntimeException grpcRuntimeError(Status.Code grpcCode, int bizCode) {
        return new StatusRuntimeException(
                Status.fromCode(grpcCode)
                        .withDescription(MessageErrorMessages.getMsg(bizCode)));
    }

    // =============================================================================
    // 工具 —— notificationKindLabel / dedupeRecipients / trimPreview / newNotificationRecord
    // =============================================================================

    /** 对应 Go {@code notificationKindLabel(kind pb.NotificationKind) string}。 */
    public static String notificationKindLabel(NotificationKind kind) {
        if (kind == null) {
            return "notification_kind_unspecified";
        }
        return kind.name().toLowerCase(Locale.ROOT);
    }

    /**
     * 对应 Go {@code dedupeRecipients(ids []int64) []int64}：
     * 去除 ≤0 与重复，保留首次出现顺序。
     */
    public static List<Long> dedupeRecipients(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashMap<Long, Boolean> seen = new LinkedHashMap<>();
        List<Long> out = new ArrayList<>(ids.size());
        for (Long id : ids) {
            if (id == null || id <= 0L) {
                continue;
            }
            if (seen.containsKey(id)) {
                continue;
            }
            seen.put(id, Boolean.TRUE);
            out.add(id);
        }
        return out;
    }

    /**
     * 对应 Go 端 {@code MessageModel.TrimPreview}（{@code rpc/internal/model/message.go:234-240}）。
     *
     * <p><b>Go 出处逐字</b>：
     * <pre>
     * func (m *MessageModel) TrimPreview(content string) string {
     *     content = strings.TrimSpace(content)
     *     runes := []rune(content)
     *     if len(runes) <= 80 { return content }
     *     return string(runes[:80])
     * }
     * </pre>
     *
     * <p><b>修复前本方法用的是 255，且按 char（UTF-16 code unit）切</b>，两处都错：
     * <ol>
     *   <li><b>255 是抄错</b>：注释写「与列定义 varchar(255) 对齐」——那是<b>列宽</b>，
     *       不是 Go 的截断规则。Go 的规则是 80。<b>§3.2 的典型反例</b>：
     *       有出处（javadoc 写了依据）但依据指错了地方，review 时无从判断。
     *       取证：{@code message.go:236} 字面量 {@code 80}。</li>
     *   <li><b>char ≠ rune</b>：Java 的 {@code String.length()} 与 {@code substring} 按 UTF-16
     *       code unit 计数，中文/emoji 一个字符算 1（代理对算 2）。Go 的 {@code []rune} 按码点。
     *       直接切会在代理对中间断开，产生<b>半个代理对</b>（非法字符，序列化即乱码）。</li>
     * </ol>
     *
     * <p><b>语义等价论证</b>：{@code codePointCount} ↔ {@code len([]rune(s))}；
     * {@code offsetByCodePoints(0, 80)} ↔ {@code runes[:80]}；
     * 空白集合见 {@link #trimGoSpace}（DRIFT-MSG-V-008）——
     * <b>修复前本方法用的是 {@code String.trim()} 且被注释定性为「有意保留的表达层差异」，
     * 那个定性是错的</b>：它同时改变了首条 50 字判定与预览结果。
     */
    public static String trimPreview(String content) {
        if (content == null) {
            return "";
        }
        String trimmed = trimGoSpace(content);
        // 按码点计数，与 Go 的 []rune 对齐（不能用 length()：UTF-16 code unit ≠ rune）
        int runeCount = trimmed.codePointCount(0, trimmed.length());
        if (runeCount <= TRIM_PREVIEW_MAX_RUNES) {
            return trimmed;
        }
        // 按码点切边界，保证不会落在代理对中间
        int endIndex = trimmed.offsetByCodePoints(0, TRIM_PREVIEW_MAX_RUNES);
        return trimmed.substring(0, endIndex);
    }

    /**
     * 对应 Go {@code newNotificationRecord(recipientID, senderID, senderRole, kind, title, content, extra, now)}：
     * 构造一条 {@link NotificationEntity}，ID 由调用方提供的 snowflakeId 分配。
     */
    public static NotificationEntity newNotificationRecord(
            long recipientId, long senderId, SenderRole senderRole,
            NotificationKind kind, String title, String content,
            Map<String, String> extra, java.time.LocalDateTime now, long snowflakeId) {
        Map<String, String> safeExtra = (extra == null || extra.isEmpty())
                ? Collections.emptyMap() : extra;
        NotificationEntity n = new NotificationEntity();
        n.setId(snowflakeId);
        n.setRecipientId(recipientId);
        n.setSenderId(senderId);
        n.setSenderRole(senderRole.getNumber());
        n.setKind(kind.getNumber());
        n.setTitle(title);
        n.setContent(content);
        n.setRead(false);
        n.setExtra(safeExtra);
        n.setCreatedAt(now);
        n.setUpdatedAt(now);
        return n;
    }

    // =============================================================================
    // Result records
    // =============================================================================

    /** canonicalParticipants 返回值。 */
    public record CanonicalPair(SenderRole role1, long id1, SenderRole role2, long id2) {}

    /** peerOf 返回值。 */
    public record PeerResult(SenderRole role, long id, boolean found) {}
}