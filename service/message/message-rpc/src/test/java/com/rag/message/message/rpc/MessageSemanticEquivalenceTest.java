package com.rag.message.message.rpc;

import com.rag.message.message.proto.ConversationListReq;
import com.rag.message.message.proto.ConversationListResp;
import com.rag.message.message.proto.ConversationMessageListReq;
import com.rag.message.message.proto.ConversationMessageListResp;
import com.rag.message.message.proto.MarkConversationReadReq;
import com.rag.message.message.proto.NotificationListReq;
import com.rag.message.message.proto.NotificationListResp;
import com.rag.message.message.proto.SenderRole;
import com.rag.message.message.rpc.domain.NotificationEntity;
import com.rag.message.message.rpc.service.MessageAtomicService;
import com.rag.message.message.rpc.service.MessageDomainService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import com.rag.message.message.rpc.support.RealContainerPort;
import com.rag.message.message.rpc.support.GrpcTestPort;

/**
 * AGENTS.md §6.7 第二轮全域验证的防回退用例 —— 锁住 DRIFT-MSG-V-001 / -002 / -004 / -005 / -006。
 *
 * <p><b>为什么这一类用例此前完全不存在，是本轮最值得记的一件事</b>：
 * message-rpc 原有 21 条测试、message-api 59 条测试，加上 {@code tools/semantic-probe} 的
 * 23 例双侧对拍，<b>没有一条能抓到 §6.7 表里的任何一项</b>。三个结构性原因：
 * <ol>
 *   <li><b>没有断言分页接口的 {@code total}</b> —— 测「列表返回了正确的 2 条」不需要
 *       {@code total} 参与断言，于是 {@code total} 成了无人区（DRIFT-MSG-V-001）；</li>
 *   <li><b>没有一条用例造过 &gt;5461 个收件人</b> —— 规模悬崖在正常规模的用例里不可见
 *       （DRIFT-MSG-V-002）；</li>
 *   <li><b>没有一条用例构造过排序并列</b> —— {@code created_at} / {@code last_message_at}
 *       不并列时，两种排序键给出<b>相同</b>结果（DRIFT-MSG-V-004 / -005）。</li>
 * </ol>
 * ⇒ 这三条已经写进 §6.7.5 纪律 A，并沉淀为下面每个 {@code @DisplayName} 的判据说明。
 *
 * <p><b>为什么必须是真 PG</b>：本类验证的是<b>排序与计数</b>在真实 SQL 上的语义。
 * H2 的默认 collation 与 {@code NULLS FIRST/LAST} 行为都不同，
 * 替换等于把要验证的东西换没了（同 {@code MessageTransactionWiringTest} 的理由）。
 *
 * <p><b>本类已做变异验证</b>（AGENTS.md §6.6.5 纪律：先证明缺陷存在时会红，再相信它的绿）：
 * 把 {@code countByRecipient} 的 {@code unreadOnly} 去掉 / 把 {@code orderByAsc(getId)}
 * 改回来 / 去掉 {@code orderByDesc(getUpdatedAt)} / 去掉 {@code insertNotificationsTx} 的切片
 * / 把 {@code touchUpdatedAt} 加回去，<b>对应 5 条用例各自变红</b>。
 */
@SpringBootTest(
        classes = MessageRpcApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "grpc.server.port=0",
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN",
                // 同 MessageTransactionWiringTest：devh 在 Bean 初始化期就建 channel，
                // 测试环境没有 Nacos ⇒ discovery:// 必然失败，覆盖成指向死端口的 static://。
                "grpc.client.user-rpc.address=static://127.0.0.1:1",
                "grpc.client.admin-rpc.address=static://127.0.0.1:1"
        })
@Testcontainers
@org.testcontainers.junit.jupiter.EnabledIfDockerAvailable
class MessageSemanticEquivalenceTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("message_rpc_equiv_it")
                    .withUsername("rag")
                    .withPassword("rag");
/**
     * 本类的真容器**固定宿主口**（message 域段 22760–22789 内 · 全局段表 §4.10.19.3 / §4.10.39.2 分配）。
     *
     * <p>🔴 为什么不用 Testcontainers 的默认随机口：随机宿主口落在 **OS 临时段 49152–65535**
     * （= Docker published 段）⇒ ⇒ 与 {@code tools/difftest/ddl-parity.sh} 的固定口 {@code 55560–55569}
     * 同属一个池子 ⇒ ⇒ 全局段表把该段标为**禁占**。
     *
     * <p>🔴 为什么端口写死在本类里、而不是轮询分配：Testcontainers 的 surefire **每个 fork 一个独立 JVM**
     * ⇒ ⇒ 静态计数器在每个 fork 里都从头开始 ⇒ ⇒ 两个并发 fork 会拿到同一个端口 ⇒ ⇒ 撞车。
     */
    private static final int FIXED_HOST_PORT = 22760;
    static {
        RealContainerPort.withFixedHostPort(POSTGRES, FIXED_HOST_PORT,
                RealContainerPort.PG_CONTAINER_PORT);
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) {
        r.add("grpc.server.port", GrpcTestPort::freePort);
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private MessageDomainService domainService;
    @Autowired private MessageAtomicService atomicService;
    // 只注入 service 层 + JdbcTemplate：setup 一律走生产写路径（insertNotificationsTx）
    // 或裸 SQL，刻意不注入 Mapper —— 「用一个从未被生产调用的方法做 setup」会让
    // 用例断言的东西和真实路径脱节。
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanSlate() {
        jdbcTemplate.update("DELETE FROM message_conversation_message");
        jdbcTemplate.update("DELETE FROM message_conversation");
        jdbcTemplate.update("DELETE FROM message_notification");
    }

    // ==================================================================
    // DRIFT-MSG-V-001：listNotifications 的 total 必须跟随 unread_only
    // ==================================================================

    /**
     * <b>§6.7.5 纪律 A 的原型用例</b>：{@code total} 与 {@code items} <b>必须成对断言</b>。
     *
     * <pre>
     * Go  message.go:39-48
     *      query := Where("recipient_id = ?")
     *      if unreadOnly { query = query.Where("is_read = ?", false) }   ← 先加过滤
     *      total = query.Count()                                         ← 复用同一 query
     *
     * 修复前 RAG
     *      items  = listByRecipient(..., unreadOnly, ...)   ← 有 is_read
     *      total  = selectCount(QueryWrapper.eq("recipient_id", uid))  ← 无 is_read
     * </pre>
     *
     * <p>修复前本用例在 {@code unreadOnly=true} 分支会得到 {@code total=5 / items.size=3}。
     */
    @Test
    @DisplayName("DRIFT-MSG-V-001：unread_only=true 时 total 与 items 必须同为未读数")
    void listNotificationsTotalMustRespectUnreadOnly() {
        long uid = 9001L;
        // 前 3 条**未读**、后 2 条**已读** ⇒ 共 5 条、其中未读 3 条
        // 走生产的 insertNotificationsTx 而不是 BaseMapper.insert：后者不带 extra 的
        // JsonbMapTypeHandler，撞 jsonb 列会报 "column extra is of type jsonb but
        // expression is of type character varying" —— 它不是生产路径，不要拿它做 setup。
        List<NotificationEntity> seed = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            seed.add(notification(1000L + i, uid, /* read = */ i >= 3));
        }
        atomicService.insertNotificationsTx(seed);

        NotificationListResp all = domainService.listNotifications(
                NotificationListReq.newBuilder().setUserId(uid).setLimit(50).build());
        assertEquals(5, all.getItemsCount(), "unread_only 未开：items 应返回全部 5 条");
        assertEquals(5, all.getTotal(), "unread_only 未开：total 应为全部 5 条");
        assertEquals(3, all.getUnreadCount(), "unread_count 与过滤无关，恒为未读数");

        NotificationListResp unread = domainService.listNotifications(
                NotificationListReq.newBuilder().setUserId(uid).setUnreadOnly(true).setLimit(50).build());
        assertEquals(3, unread.getItemsCount(), "unread_only=true：items 应只返回 3 条未读");
        assertEquals(3, unread.getTotal(),
                "DRIFT-MSG-V-001：unread_only=true 时 total 必须与 items 同为 3。"
                        + "Go 的 total 复用带 is_read=false 的 query —— 见 message.go:40-48");
        assertEquals(unread.getItemsCount(), (int) unread.getTotal(),
                "纪律 A：total 与 items 必须成对一致，否则前端分页会多翻空页");
    }

    // ==================================================================
    // DRIFT-MSG-V-002：批量插入必须切片，否则 >5461 收件人整批失败
    // ==================================================================

    /**
     * <b>规模悬崖用例</b>。
     *
     * <p>{@code insertBatch} 是 12 列/行的一条 {@code <foreach>} 多行 INSERT；
     * Postgres 单条 SQL 的绑定参数上限是 65535 ⇒ {@code floor(65535/12) = 5461}。
     * 修复前这里会发一条 6000×12 = 72000 个参数的 SQL ⇒ <b>整批失败、事务回滚、
     * 一条通知都发不出去</b>。Go 侧 {@code CreateInBatches(items, 200)} 无此上限。
     *
     * <p>⚠️ <b>为什么必须真的造出 6000 条</b>：用 5 条、50 条都测不出来 ——
     * 分片与不分片的差别<b>只在大批量时显现</b>。这是 §3.3「小样本让规模缺陷隐形」的又一例。
     */
    @Test
    @DisplayName("DRIFT-MSG-V-002：6000 条通知（>5461 参数阈值）必须全部落库")
    void notificationBatchInsertMustSurviveTheParameterLimit() {
        final int recipients = 6000;
        List<NotificationEntity> batch = new ArrayList<>(recipients);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        for (int i = 0; i < recipients; i++) {
            NotificationEntity n = notification(2000L + i, 777L, false);
            n.setCreatedAt(now);
            n.setUpdatedAt(now);
            batch.add(n);
        }

        atomicService.insertNotificationsTx(batch);

        Integer persisted = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM message_notification", Integer.class);
        assertEquals(recipients, persisted,
                "DRIFT-MSG-V-002：6000 条必须全部落库。修复前是一条 6000 行的 foreach INSERT，"
                        + "绑定参数 6000×12=72000 超过 Postgres 的 65535 上限 ⇒ 整批失败");

        // 分片不是"少插几条"的另一种失败方式：必须一条不少、id 齐全
        Integer distinct = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT id) FROM message_notification", Integer.class);
        assertEquals(recipients, distinct, "分片后 id 必须唯一且齐全，不能重复或丢失");
    }

    /**
     * <b>分片不能削弱原子性</b>：Go 的 {@code CreateInBatches(items, 200)} 整批在事务里，
     * 修复后的 Java 也必须整批在一个事务里 —— 任一片失败 ⇒ 全部回滚。
     *
     * <p>做法：传一个 id 与首片冲突的批次（首片插入成功、第二片主键冲突）
     * ⇒ 期望<b>一条都不留</b>，而不是留下前 200 条。
     */
    @Test
    @DisplayName("DRIFT-MSG-V-002：分片后仍是单一事务，中途失败必须全部回滚")
    void batchInsertMustStayAtomicAcrossChunks() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<NotificationEntity> batch = new ArrayList<>();
        for (int i = 0; i < 250; i++) {   // > INSERT_BATCH_SIZE(200) ⇒ 必被切成 2 片
            NotificationEntity n = notification(3000L + i, 888L, false);
            n.setCreatedAt(now);
            n.setUpdatedAt(now);
            batch.add(n);
        }
        // 把第 2 片里的一条 id 改成与第 1 片某条相同 ⇒ 插入到第二片时主键冲突
        batch.get(200).setId(batch.get(0).getId());

        assertThrowsAny(() -> atomicService.insertNotificationsTx(batch));

        Integer persisted = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM message_notification", Integer.class);
        assertEquals(0, persisted,
                "第 2 片冲突时第 1 片也必须回滚 —— 整批在同一个 @Transactional 里，"
                        + "与 Go CreateInBatches 的原子性一致");
    }

    // ==================================================================
    // DRIFT-MSG-V-004：会话消息按 created_at 排序，不是 id
    // ==================================================================

    /**
     * <b>并列敏感用例</b>：构造「id 顺序与 created_at 顺序相反」的两行。
     *
     * <p>不并列时两种排序键结果相同 ⇒ 这类漂移在常规数据上<b>永远测不出来</b>。
     * 雪花 id 单调这一点会让人误以为「按 id 排 ≈ 按 created_at 排」，
     * 但 id 的毫秒内次序由 (机器号, 序列号) 决定，与 created_at 的并列次序不必一致。
     */
    @Test
    @DisplayName("DRIFT-MSG-V-004：会话消息必须按 created_at ASC 排序（构造与 id 相反的次序）")
    void conversationMessagesMustBeOrderedByCreatedAtNotId() {
        long convId = 7001L;
        insertConversation(convId, 1L, 11L, OffsetDateTime.now(ZoneOffset.UTC));

        OffsetDateTime t1 = OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime t2 = t1.plusHours(1);
        // id 小的那行 created_at 更大 ⇒ 按 id 得 [id1, id2]，按 created_at 得 [id2, id1]
        insertMessage(9001L, convId, t2, 11L, SenderRole.USER.getNumber());
        insertMessage(9002L, convId, t1, 11L, SenderRole.USER.getNumber());

        ConversationMessageListResp resp = domainService.getConversationMessages(
                ConversationMessageListReq.newBuilder()
                        .setOperatorId(11L)
                        .setOperatorRoleValue(SenderRole.USER.getNumber())
                        .setConversationId(convId)
                        .setLimit(10)
                        .build());

        assertEquals(2, resp.getItemsCount());
        assertEquals(9002L, resp.getItems(0).getId(),
                "DRIFT-MSG-V-004：第一条必须是 created_at 更早的那行（id=9002），"
                        + "修复前按 id 排序会返回 9001 —— 见 message.go:163 的 Order(\"created_at asc\")");
        assertEquals(9001L, resp.getItems(1).getId());
    }

    // ==================================================================
    // DRIFT-MSG-V-005：会话列表的次级排序键 updated_at DESC
    // ==================================================================

    /**
     * <b>并列敏感用例</b>：两个会话 {@code last_message_at} 完全相同，
     * 只有 {@code updated_at} 不同 ⇒ 次级排序键缺失时顺序不确定。
     *
     * <p>批量发消息极易产生 {@code last_message_at} 并列，所以这不是罕见路径。
     */
    @Test
    @DisplayName("DRIFT-MSG-V-005：last_message_at 并列时必须按 updated_at DESC 决定顺序")
    void conversationListMustUseUpdatedAtAsTiebreaker() {
        long uid = 12L;
        OffsetDateTime sameLastMessageAt = OffsetDateTime.of(2026, 2, 2, 12, 0, 0, 0, ZoneOffset.UTC);
        // 两个会话的 last_message_at 完全相同，只有 updated_at 不同。
        // ⚠️ 参与者组合必须不同：uk_conversation_pair 是 (p1_role,p1_id,p2_role,p2_id) 唯一，
        //    同一对参与者只能有一个会话。让 operator 分别落在 p1 / p2 两侧即可绕开。
        // conv A 的 updated_at 更早 ⇒ 按 (last_message_at desc, updated_at desc) 应排在后面
        insertConversationAsP2(7101L, 99L, uid, sameLastMessageAt,
                sameLastMessageAt.minusDays(2));
        insertConversationAsP1(7102L, uid, 98L, sameLastMessageAt,
                sameLastMessageAt.plusDays(1));

        ConversationListResp resp = domainService.listConversations(
                ConversationListReq.newBuilder()
                        .setOperatorId(uid)
                        .setOperatorRoleValue(SenderRole.USER.getNumber())
                        .setLimit(10)
                        .build());

        assertEquals(2, resp.getItemsCount());
        assertEquals(7102L, resp.getItems(0).getConversationId(),
                "DRIFT-MSG-V-005：updated_at 更新的会话应排在前 —— Go 的次级排序键是 "
                        + "Order(\"updated_at desc\")（message.go:126），修复前 RAG 只有主键");
    }

    // ==================================================================
    // DRIFT-MSG-V-006：markConversationRead 不得改写 conversation.updated_at
    // ==================================================================

    /**
     * <b>副作用集用例</b>：Go 的 {@code MarkConversationRead} 只 UPDATE 消息表，
     * 不触碰 conversation 表。修复前 RAG 额外调了 {@code touchUpdatedAt}，
     * 于是「标记已读」这个写操作会改写会话的 {@code updated_at}。
     */
    @Test
    @DisplayName("DRIFT-MSG-V-006：标记会话已读不得改写 conversation.updated_at")
    void markConversationReadMustNotTouchConversationUpdatedAt() {
        long convId = 7201L;
        long uid = 13L;
        OffsetDateTime originalUpdatedAt = OffsetDateTime.of(2026, 3, 3, 8, 0, 0, 0, ZoneOffset.UTC);
        insertConversation(convId, 1L, uid, OffsetDateTime.now(ZoneOffset.UTC), originalUpdatedAt);

        OffsetDateTime createdAt = OffsetDateTime.of(2026, 3, 3, 7, 0, 0, 0, ZoneOffset.UTC);
        // recipient 必须就是 operator（uid/USER）—— MarkConversationRead 的 WHERE 带
        // recipient_role + recipient_id，对不上就更新 0 行，本用例会因"什么都没做"而假绿。
        insertMessage(9101L, convId, createdAt, uid, SenderRole.USER.getNumber());

        OffsetDateTime before = jdbcTemplate.queryForObject(
                "SELECT updated_at FROM message_conversation WHERE id = ?",
                OffsetDateTime.class, convId);
        assertEquals(originalUpdatedAt, before);

        domainService.markConversationRead(
                MarkConversationReadReq.newBuilder()
                        .setOperatorId(uid)
                        .setOperatorRoleValue(SenderRole.USER.getNumber())
                        .setConversationId(convId)
                        .build());

        OffsetDateTime after = jdbcTemplate.queryForObject(
                "SELECT updated_at FROM message_conversation WHERE id = ?",
                OffsetDateTime.class, convId);
        assertEquals(originalUpdatedAt, after,
                "DRIFT-MSG-V-006：Go 的 MarkConversationRead 只 UPDATE 消息表（message.go:176-184）。"
                        + "修复前 RAG 额外 touch 了 conversation.updated_at —— "
                        + "一个「读类写操作」改了会话元数据，两端副作用集不同");

        // 正向确认：消息本身确实被标成已读（避免本用例因"什么都没做"而假绿）
        Boolean read = jdbcTemplate.queryForObject(
                "SELECT is_read FROM message_conversation_message WHERE id = ?",
                Boolean.class, 9101L);
        assertEquals(Boolean.TRUE, read, "消息必须真的被标为已读，否则本用例是假绿");
    }

    // ==================================================================
    // DRIFT-MSG-V-007：timestamp（无时区）列对墙钟必须无损
    // ==================================================================

    /**
     * <b>DB 往返用例</b>：单测证明了「转换函数算得对」，
     * 这条证明**它依赖的前提也成立** —— 即 {@code TIMESTAMP}（无时区）列对
     * {@code OffsetDateTime} 墙钟<b>不做任何换算、原样存取</b>。
     *
     * <p>整个 DRIFT-MSG-V-007 的修法（读侧用 {@code atZone(systemDefault())}）就建立在这个前提上：
     * 若某天有人把列改成 {@code TIMESTAMPTZ}、或换掉 JDBC 驱动，
     * 这个前提就不成立了，读侧也必须跟着改 —— <b>而没有任何单测会发现</b>。
     *
     * <p>断言用「写入一个非当前时间的固定墙钟」，避免用 {@code OffsetDateTime.now(ZoneOffset.UTC)}
     * 时把「写对读错」和「读写都对」混在一起看不出来。
     */
    @Test
    @DisplayName("DRIFT-MSG-V-007：写入的墙钟经 TIMESTAMP 列往返后必须逐秒相等")
    void notificationTimestampsMustRoundTripLosslessly() {
        long uid = 9500L;
        OffsetDateTime wallClock = OffsetDateTime.of(2026, 10, 23, 2, 0, 0, 0, ZoneOffset.UTC);

        NotificationEntity n = notification(1900L, uid, false);
        n.setCreatedAt(wallClock);
        n.setUpdatedAt(wallClock);
        atomicService.insertNotificationsTx(List.of(n));

        // 直接从库里读，绕开 mapper，确认列本身没做换算
        OffsetDateTime rawFromDb = jdbcTemplate.queryForObject(
                "SELECT created_at FROM message_notification WHERE id = ?",
                OffsetDateTime.class, 1900L);
        assertEquals(wallClock, rawFromDb,
                "TIMESTAMP（无时区）列必须原样存取墙钟。"
                        + "若这一步发生换算，读侧的 atZone(systemDefault) 就不再是正确修法");

        // 再走完整的 service 层，断言 proto 里的 epoch
        java.util.TimeZone original = java.util.TimeZone.getDefault();
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Shanghai"));
            NotificationListResp resp = domainService.listNotifications(
                    NotificationListReq.newBuilder().setUserId(uid).setLimit(10).build());

            assertEquals(1, resp.getItemsCount());
            // 🔴 V-020：列已是 TIMESTAMPTZ、实体是 OffsetDateTime ⇒ 值自带 offset，
            //    「按本地时区解释」这一步**已被删除**。这里刻意在 JVM=Asia/Shanghai 下
            //    断言一个**用 +08:00 构造**的瞬时 —— 若实现偷偷把 offset 丢掉、按 JVM
            //    时区重新解释，结果会差 8 小时而本用例转红。
            long expected = wallClock.toEpochSecond();
            assertEquals(expected, resp.getItems(0).getCreatedAt(),
                    "端到端：带 offset 的瞬时 → TIMESTAMPTZ 列 → proto epoch，必须原样往返");
        } finally {
            java.util.TimeZone.setDefault(original);
        }
    }

    // ==================================================================
    // helpers
    // ==================================================================

    private NotificationEntity notification(long id, long recipientId, boolean read) {
        NotificationEntity n = new NotificationEntity();
        n.setId(id);
        n.setRecipientId(recipientId);
        n.setSenderId(1L);
        n.setSenderRole(SenderRole.ADMIN.getNumber());
        n.setKind(1);
        n.setTitle("t");
        n.setContent("c");
        n.setRead(read);
        n.setExtra(new HashMap<>());
        // created_at / updated_at 在 DDL 里是 NOT NULL，且 insertBatch 是手写 @Insert
        // —— @TableField(fill = INSERT) 的自动填充**只作用于 MP 生成的 SQL**，
        // 手写 SQL 不填。生产的 MessageRpcSupport.newNotificationRecord 总是显式赋值，
        // 所以这里也必须显式给，否则撞 NOT NULL。
        n.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        n.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return n;
    }

    private void insertConversation(long id, long p1Id, long p2Id, OffsetDateTime lastMessageAt) {
        insertConversation(id, p1Id, p2Id, lastMessageAt, lastMessageAt);
    }

    private void insertConversation(long id, long p1Id, long p2Id,
                                    OffsetDateTime lastMessageAt, OffsetDateTime updatedAt) {
        insertConversationAsP1(id, p1Id, p2Id, lastMessageAt, updatedAt);
    }

    /** operator 落在 participant1 侧。 */
    private void insertConversationAsP1(long id, long p1Id, long p2Id,
                                        OffsetDateTime lastMessageAt, OffsetDateTime updatedAt) {
        insertConversationRow(id, p1Id, p2Id, lastMessageAt, updatedAt);
    }

    /** operator 落在 participant2 侧（用于绕开 uk_conversation_pair 的唯一约束）。 */
    private void insertConversationAsP2(long id, long peerId, long operatorId,
                                        OffsetDateTime lastMessageAt, OffsetDateTime updatedAt) {
        insertConversationRow(id, operatorId, peerId, lastMessageAt, updatedAt);
    }

    private void insertConversationRow(long id, long p1Id, long p2Id,
                                       OffsetDateTime lastMessageAt, OffsetDateTime updatedAt) {
        OffsetDateTime createdAt = lastMessageAt;
        jdbcTemplate.update("""
                INSERT INTO message_conversation
                  (id, participant1_role, participant1_id, participant2_role, participant2_id,
                   status, pending_sender_role, pending_sender_id,
                   created_by_role, created_by_id,
                   last_message_id, last_message_preview, last_message_at,
                   created_at, updated_at)
                VALUES (?, 1, ?, 1, ?, 2, 0, 0, 1, ?, 0, '', ?, ?, ?)
                """,
                id, p1Id, p2Id, p1Id, lastMessageAt, createdAt, updatedAt);
    }

    private void insertMessage(long id, long convId, OffsetDateTime createdAt,
                               long recipientId, int recipientRole) {
        jdbcTemplate.update("""
                INSERT INTO message_conversation_message
                  (id, conversation_id, sender_id, sender_role, recipient_id, recipient_role,
                   content, is_read, read_at, created_at)
                VALUES (?, ?, 1, 1, ?, ?, 'hello', FALSE, NULL, ?)
                """,
                id, convId, recipientId, recipientRole, createdAt);
    }

    private static void assertThrowsAny(Runnable r) {
        try {
            r.run();
        } catch (RuntimeException expected) {
            assertNotNull(expected);
            return;
        }
        throw new AssertionError("预期抛异常（主键冲突），但正常返回了");
    }
}