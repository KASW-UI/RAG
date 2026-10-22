package com.rag.message.message.rpc.service;

import com.rag.message.message.proto.ChatMessageItem;
import com.rag.message.message.proto.ConversationItem;
import com.rag.message.message.proto.NotificationItem;
import com.rag.message.message.rpc.domain.ConversationEntity;
import com.rag.message.message.rpc.domain.ConversationMessageEntity;
import com.rag.message.message.rpc.domain.NotificationEntity;
import com.rag.message.message.proto.SenderRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * DRIFT-MSG-V-007 的防回退用例 —— 无时区墙钟 → epoch 秒的转换。
 *
 * <p><b>缺陷形态</b>：修复前用 {@code toEpochSecond(ZoneOffset.UTC)}，
 * 即把 {@code LocalDateTime} 墙钟<b>当成 UTC</b>解释。
 * 而写侧 {@code LocalDateTime.now()} 是 JVM 本地墙钟 ⇒ <b>写读不自洽</b>。
 *
 * <p><b>为什么这些用例要自己 setDefault 时区</b>：
 * 若跟着宿主机时区跑，在 UTC 机器上修复前后的结果<b>完全一样</b>（差 0），
 * 用例就是假绿；在中国时区机器上又永远只能验一种情况。
 * ⇒ 每条用例都显式设定默认时区并在 finally 还原，
 * 使判据<b>与宿主机时区无关</b>。
 *
 * <p><b>Go 侧基准</b>：GORM 把时间列建成 {@code timestamptz}
 * （gorm postgres driver {@code postgres.go:251-255}），DSN 钉 {@code TimeZone=Asia/Shanghai}
 * （{@code db.go:22}），读出 {@code time.Time} ⇒ {@code .Unix()} 是真实瞬时，<b>与时区无关</b>。
 */
class MessageRpcSupportTimeZoneTest {

    /** 一条固定的墙钟，写入 DB 的就是这个字符串本身（`TIMESTAMP` 无时区列不做换算）。 */
    private static final LocalDateTime WALL_CLOCK = LocalDateTime.of(2026, 10, 23, 2, 0, 0);

    /**
     * 部署时区（用户 2026-10-22 确认）：<b>Asia/Shanghai</b>。
     * 此刻修复前的实现会把 epoch 算成「晚 8 小时」。
     */
    @Test
    @DisplayName("DRIFT-MSG-V-007：Asia/Shanghai 下通知 created_at 必须等于真实瞬时（不是晚 8 小时）")
    void notificationCreatedAtMustBeTheTrueInstantInShanghai() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
            long expected = WALL_CLOCK.atZone(ZoneId.of("Asia/Shanghai")).toInstant().getEpochSecond();
            long wrong = WALL_CLOCK.toEpochSecond(ZoneOffset.UTC);

            assertNotEquals(expected, wrong,
                    "前置条件：在 Asia/Shanghai 下，「按本地时区解释」与「按 UTC 解释」必须不同，"
                            + "否则本用例测不出任何东西");
            assertEquals(expected + 8 * 3600, wrong,
                    "前置条件：修复前的错误值应恰好比正确值晚 8 小时");

            NotificationItem out = MessageRpcSupport.toNotificationItem(
                    notificationWithCreatedAt(WALL_CLOCK));

            assertEquals(expected, out.getCreatedAt(),
                    "DRIFT-MSG-V-007：修复前是 " + wrong + "（晚 8 小时），"
                            + "Go 的 .Unix() 是 " + expected);
        } finally {
            TimeZone.setDefault(original);
        }
    }

    /**
     * <b>「写读自洽」是判据，不是「必须是 +08:00」</b>。
     * JVM 若改成 UTC，写侧 {@code LocalDateTime.now()} 也就变成 UTC 墙钟 ⇒ 结果依然正确。
     * 这条锁的是<b>不能硬编码时区</b>：有人把 {@code Asia/Shanghai} 写死在本机时区是 UTC 的部署上就会错。
     */
    @Test
    @DisplayName("DRIFT-MSG-V-007：JVM 换到 UTC 后结果同样自洽（禁止硬编码 Asia/Shanghai）")
    void mustStaySelfConsistentUnderADifferentJvmZone() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            long expected = WALL_CLOCK.atZone(ZoneId.of("UTC")).toInstant().getEpochSecond();

            NotificationItem out = MessageRpcSupport.toNotificationItem(
                    notificationWithCreatedAt(WALL_CLOCK));

            assertEquals(expected, out.getCreatedAt(),
                    "写侧用 LocalDateTime.now()（本机墙钟），读侧也必须用本机时区解释。"
                            + "硬编码 Asia/Shanghai 会让这条在 UTC 部署上失败");
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    @DisplayName("DRIFT-MSG-V-007：会话消息 created_at 走同一套转换")
    void messageCreatedAtMustUseTheSameConversion() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
            long expected = WALL_CLOCK.atZone(ZoneId.of("Asia/Shanghai")).toInstant().getEpochSecond();

            ConversationMessageEntity m = new ConversationMessageEntity();
            m.setId(1L);
            // content 必须给：protobuf builder 对 null 字符串直接 NPE。
            // 生产路径上 content 恒非空（写入前 trimContent + 空串判 5003；DB 列 NOT NULL），
            // 所以这里补上不是"为了让测试过"，而是复刻真实入参。
            m.setContent("hello");
            m.setCreatedAt(WALL_CLOCK);

            ChatMessageItem out = MessageRpcSupport.toMessageItem(m);
            assertEquals(expected, out.getCreatedAt(),
                    "会话消息与通知走同一套规则 —— 两处曾各自写死 UTC 转换");
        } finally {
            TimeZone.setDefault(original);
        }
    }

    /** 会话上的两个时间字段：{@code latest_message_at} 与 {@code updated_at}。 */
    @Test
    @DisplayName("DRIFT-MSG-V-007：会话 latest_message_at / updated_at 同样不得当成 UTC")
    void conversationTimestampsMustUseTheSameConversion() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
            long expected = WALL_CLOCK.atZone(ZoneId.of("Asia/Shanghai")).toInstant().getEpochSecond();

            ConversationEntity conv = new ConversationEntity();
            conv.setId(1L);
            conv.setParticipant1Role(SenderRole.USER.getNumber());
            conv.setParticipant1Id(11L);
            conv.setParticipant2Role(SenderRole.USER.getNumber());
            conv.setParticipant2Id(22L);
            conv.setLastMessageAt(WALL_CLOCK);
            conv.setUpdatedAt(WALL_CLOCK);

            ConversationItem out = MessageRpcSupport.buildConversationItem(
                    conv, SenderRole.USER, 11L, 0L, "peer", "peer@example.com");

            assertEquals(expected, out.getLatestMessageAt());
            assertEquals(expected, out.getUpdatedAt());
        } finally {
            TimeZone.setDefault(original);
        }
    }

    /** null 时间戳仍回 0（与 proto3 默认值一致），不抛 NPE —— 这条与时区无关，防止修坏边界。 */
    @Test
    @DisplayName("DRIFT-MSG-V-007：null 时间戳回 0，不抛 NPE")
    void nullTimestampsMustStillMapToZero() {
        NotificationItem out = MessageRpcSupport.toNotificationItem(
                notificationWithCreatedAt(null));
        assertEquals(0L, out.getCreatedAt(),
                "proto3 的 int64 默认值是 0；修复时区逻辑不得顺手改变 null 的处理");
    }

    /** 边界对照：epoch 必须是<b>绝对瞬时</b>，与墙上显示的时分无关。 */
    @Test
    @DisplayName("DRIFT-MSG-V-007：epoch 指向绝对瞬时（还原回墙钟等于写入值）")
    void epochMustDenoteAbsoluteInstant() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
            NotificationItem out = MessageRpcSupport.toNotificationItem(
                    notificationWithCreatedAt(WALL_CLOCK));

            // 可验证的等价关系：epoch 还原回本地墙钟 == 写入时的墙钟
            LocalDateTime roundTrip = Instant.ofEpochSecond(out.getCreatedAt())
                    .atZone(ZoneId.of("Asia/Shanghai"))
                    .toLocalDateTime();

            assertEquals(WALL_CLOCK, roundTrip,
                    "把 epoch 还原回本地墙钟必须等于写入时的墙钟 —— 这是端到端可验证的等价关系");
        } finally {
            TimeZone.setDefault(original);
        }
    }

    private static NotificationEntity notificationWithCreatedAt(LocalDateTime createdAt) {
        NotificationEntity n = new NotificationEntity();
        n.setId(1L);
        n.setRecipientId(2L);
        n.setSenderId(3L);
        n.setSenderRole(SenderRole.ADMIN.getNumber());
        n.setKind(1);
        n.setTitle("t");
        n.setContent("c");
        n.setRead(false);
        n.setExtra(new HashMap<>());
        n.setCreatedAt(createdAt);
        n.setUpdatedAt(createdAt);
        return n;
    }
}