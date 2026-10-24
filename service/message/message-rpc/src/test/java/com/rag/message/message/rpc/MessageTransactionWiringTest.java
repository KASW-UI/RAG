package com.rag.message.message.rpc;

import com.rag.message.message.proto.SendChatMessageReq;
import com.rag.message.message.proto.SendChatMessageResp;
import com.rag.message.message.proto.SenderRole;
import com.rag.message.message.rpc.domain.ConversationEntity;
import com.rag.message.message.rpc.domain.ConversationMessageEntity;
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

import static org.junit.jupiter.api.Assertions.*;

/**
 * §6.6.1 的事务证据 —— 真 PostgreSQL 上验证 DRIFT-MSG-001 与 DRIFT-MSG-002 两个 P0。
 *
 * <p><b>为什么必须是真 PG</b>：本测试要验证的是「唯一约束 / NOT NULL 冲突时整事务回滚」。
 * 这依赖 PG 的事务与约束语义；H2 语义不同，替换等于把要验证的东西换没了。
 * Flyway 的 3 份迁移脚本也只面向 PG。
 *
 * <p><b>两个 P0 的形态（修复前）</b>：
 * <ol>
 *   <li><b>DRIFT-MSG-001</b>：6 个 {@code @Transactional} 方法挂在 {@code MessageDomainService}
 *       本类上，被同类 {@code doXxx(...)} 通过 {@code this.} 调用 ⇒ 不经代理 ⇒
 *       <b>事务静默失效</b>，每条语句独立 auto-commit，异常不回滚；</li>
 *   <li><b>DRIFT-MSG-002</b>：{@code conversationId} 是 primitive {@code long}，
 *       构造 {@code messageItem} 时漏赋值 ⇒ 所有消息写成 {@code conversation_id = 0}
 *       ⇒ 查不出历史消息、unread_count 恒 0、标记已读更新 0 行。</li>
 * </ol>
 * 两者都是<b>纯 Mockito 单测按定义测不到</b>的：mock 数据层不执行 SQL、不装配 Spring 上下文，
 * 这正是本模块此前 9 条测试全绿而这两个 P0 同时存在的根因（AGENTS.md §6.6.4）。
 *
 * <p><b>与同款测试的关系</b>：{@code AdminRpcTransactionWiringTest}（admin-rpc 4 步事务）
 * 与 {@code AvatarTransactionWiringTest}（user-rpc）是同一范式的既有实例，
 * 本类是 message 域的第三例。
 */
@SpringBootTest(
        classes = MessageRpcApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "grpc.server.port=0",
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN",
                // ⚠️ 必须把两个 @GrpcClient 的 discovery:// 覆盖成 static://，否则上下文起不来：
                //   devh 的 GrpcClientBeanPostProcessor 在 **Bean 初始化期就建 channel**（不是懒建），
                //   而测试环境没有 Nacos ⇒
                //     IllegalArgumentException: Incorrectly formatted target uri;
                //     expected: 'discovery:[//]/<service-name>'; but was 'discovery://admin-rpc'
                // 指向一个必然连不上的端口即可 —— devh 的连接本身是懒的，而本类验证的
                // sendChatMessage / 事务路径**根本不调 user-rpc / admin-rpc**。
                // 同款覆盖见 run.sh 启动 RAG message-rpc 时的 --grpc.client.*.address=static://… 参数。
                "grpc.client.user-rpc.address=static://127.0.0.1:1",
                "grpc.client.admin-rpc.address=static://127.0.0.1:1"
        })
@Testcontainers
@org.testcontainers.junit.jupiter.EnabledIfDockerAvailable
class MessageTransactionWiringTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("message_rpc_tx_it")
                    .withUsername("rag")
                    .withPassword("rag");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private MessageAtomicService atomicService;
    @Autowired private MessageDomainService domainService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanSlate() {
        jdbcTemplate.update("DELETE FROM message_conversation_message");
        jdbcTemplate.update("DELETE FROM message_conversation");
        jdbcTemplate.update("DELETE FROM message_notification");
    }

    // ==================================================================
    // DRIFT-MSG-002：conversation_id 必须被写回，且两端一致
    // ==================================================================

    /**
     * <b>本类最关键的一条</b>：走完整 DomainService 链路发一条消息，
     * 断言 <b>响应里</b>与 <b>数据库行里</b>的 {@code conversation_id} 都等于真实会话 id。
     *
     * <p>修复前 {@code doSendChatMessage} 构造 {@code messageItem} 时漏了
     * {@code setConversationId}，实体字段是 primitive {@code long} 默认 0 ⇒
     * 响应 {@code message.conversation_id} = 0、DB 行 {@code conversation_id} = 0。
     *
     * <p>顺带覆盖「新建会话 + 首条消息是一个原子事务」：这条消息的
     * {@code conversation_id} 只能来自 {@code MessageAtomicService} 在事务内写回。
     */
    @Test
    @DisplayName("DRIFT-MSG-002：新建会话后 message.conversation_id 必须等于真实会话 id（响应 + DB 行）")
    void conversationIdMustBePropagatedToResponseAndRow() {
        SendChatMessageResp resp = domainService.sendChatMessage(
                SendChatMessageReq.newBuilder()
                        .setSenderId(1L).setSenderRole(SenderRole.USER)
                        .setRecipientId(2L).setRecipientRole(SenderRole.USER)
                        .setContent("hello")
                        .build());

        long convId = resp.getConversationId();
        assertNotEquals(0L, convId,
                "响应里的 conversation_id 不能是 0 —— DRIFT-MSG-002 回归");

        assertEquals(convId, resp.getMessage().getConversationId(),
                "响应内 message.conversation_id 必须与外层 conversation_id 一致；" +
                        "不等说明实体的 conversationId 没被写回（修复前恒为 0）");

        Long dbConvId = jdbcTemplate.queryForObject(
                "SELECT conversation_id FROM message_conversation_message WHERE id = ?",
                Long.class, resp.getMessage().getId());
        assertEquals(convId, dbConvId,
                "DB 行的 conversation_id 必须等于真实会话 id；为 0 说明 insertMsg 绑到了未赋值的字段");
    }

    /**
     * DRIFT-MSG-002 的<b>下游</b>防线：会话内的消息<b>必须查得到</b>。
     *
     * <p>修复前所有消息的 {@code conversation_id} 都是 0，
     * 而 {@code GetConversationMessages} 按 conversation_id 过滤 ⇒ 永远返回空列表。
     * 这里直接查表验证可被定位，是最贴近调用方真实路径的判据。
     */
    @Test
    @DisplayName("DRIFT-MSG-002 下游：新建会话里的首条消息必须能按 conversation_id 查到")
    void firstMessageMustBeFindableByConversationId() {
        SendChatMessageResp resp = domainService.sendChatMessage(
                SendChatMessageReq.newBuilder()
                        .setSenderId(1L).setSenderRole(SenderRole.USER)
                        .setRecipientId(2L).setRecipientRole(SenderRole.USER)
                        .setContent("findable")
                        .build());

        long n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM message_conversation_message WHERE conversation_id = ?",
                Long.class, resp.getConversationId());
        assertEquals(1L, n,
                "该会话下必须恰好查到 1 条消息；为 0 说明 conversation_id 写错，历史消息查不出来");
    }

    /**
     * <b>变异测试逼出来的一条</b>：上面两条只走「会话不存在 → 新建」这条
     * {@code createConversationWithMessageTx} 路径，<b>完全没覆盖 {@code createOrSendMessageTx}
     * （已有会话追加消息）那条路径</b>。
     *
     * <p>实测（2026-10-22）：把 {@code createOrSendMessageTx} 里的
     * {@code msg.setConversationId(conv.getId())} 删掉后重跑，<b>上面 7 条用例仍然全绿</b> ——
     * 也就是说这条分支的 {@code conversation_id} 缺陷<b>本来漏网</b>。
     * 本条就是补上的那道网。
     *
     * <p><b>怎么走到 createOrSendMessageTx 分支</b>（Go 出处 sendchatmessagelogic.go:204-228）：
     * <ol>
     *   <li>先由 uid 发一条 ⇒ 会话被建成 PENDING，{@code pending_sender = uid}；</li>
     *   <li>再由 <b>对端</b> peer 发一条 ⇒ findByPair 命中已有会话，
     *       且发送方<b>不是</b> pending_sender ⇒ 不会撞 {@code FailedPrecondition(5204)}，
     *       状态机把 PENDING 转 OPEN，走 {@code createOrSendMessageTx}。</li>
     * </ol>
     *
     * <p>注意方向不能反：若由 uid 自己再发一条，Go 会以
     * {@code codes.FailedPrecondition + ErrorChatPending} 拒掉，永远走不到追加分支。
     */
    @Test
    @DisplayName("DRIFT-MSG-002 补漏：已有会话追加消息（createOrSendMessageTx 路径）conversation_id 也必须正确")
    void appendedMessageMustAlsoCarryConversationId() {
        SendChatMessageResp first = domainService.sendChatMessage(
                SendChatMessageReq.newBuilder()
                        .setSenderId(11L).setSenderRole(SenderRole.USER)
                        .setRecipientId(22L).setRecipientRole(SenderRole.USER)
                        .setContent("first")
                        .build());
        long convId = first.getConversationId();
        assertNotEquals(0L, convId);

        // 对端回复 ⇒ 走 createOrSendMessageTx
        SendChatMessageResp second = domainService.sendChatMessage(
                SendChatMessageReq.newBuilder()
                        .setSenderId(22L).setSenderRole(SenderRole.USER)
                        .setRecipientId(11L).setRecipientRole(SenderRole.USER)
                        .setContent("second")
                        .build());

        assertEquals(convId, second.getConversationId(),
                "对端回复必须落回同一个会话 id");

        assertEquals(convId, second.getMessage().getConversationId(),
                "追加消息的 message.conversation_id 必须等于会话 id；" +
                        "为 0 说明 createOrSendMessageTx 漏了 setConversationId —— " +
                        "该分支原先无任何测试覆盖（2026-10-22 变异测试发现）");

        Long dbConvId = jdbcTemplate.queryForObject(
                "SELECT conversation_id FROM message_conversation_message WHERE id = ?",
                Long.class, second.getMessage().getId());
        assertEquals(convId, dbConvId,
                "追加消息的 DB 行 conversation_id 必须等于会话 id");

        // 该会话下现在应当有 2 条消息，且都能按 conversation_id 查到
        long n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM message_conversation_message WHERE conversation_id = ?",
                Long.class, convId);
        assertEquals(2L, n,
                "同一会话下必须能查到 2 条消息；少一条说明追加的那条 conversation_id 写错了");
    }

    // ==================================================================
    // DRIFT-MSG-001：事务必须真回滚
    // ==================================================================

    /**
     * <b>DRIFT-MSG-001 的正面证据</b>：conversation 插入成功、message 插入失败
     * （{@code content} 传 null 撞 NOT NULL）⇒ <b>conversation 行必须被回滚掉</b>。
     *
     * <p>修复前两个方法是同类 self-invocation、{@code @Transactional} 静默失效，
     * 于是 conversation 行会<b>残留</b>（孤儿会话），下一次同对用户发消息就会命中
     * {@code findByPair} 找到一个「有会话但没消息」的脏状态。
     */
    @Test
    @DisplayName("DRIFT-MSG-001：message 插入失败 ⇒ 整事务回滚，conversation 行不得残留")
    void rollbackConversationWhenMessageInsertFails() {
        ConversationEntity conv = newConversation(900_000_000_001L, 1L, 2L);
        ConversationMessageEntity msg = newMessage(conv.getId(), 1L, 2L);
        msg.setContent(null); // ← 撞 content TEXT NOT NULL

        assertThrows(RuntimeException.class,
                () -> atomicService.createConversationWithMessageTx(conv, msg),
                "message 插入撞 NOT NULL 必须抛异常");

        Long n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM message_conversation WHERE id = ?",
                Long.class, conv.getId());
        assertEquals(0L, n,
                "conversation 行必须被回滚；残留即为 DRIFT-MSG-001 回归（事务静默失效 ⇒ auto-commit）");

        Long m = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM message_conversation_message", Long.class);
        assertEquals(0L, m, "失败的 message 不得留下任何行");
    }

    /**
     * DRIFT-MSG-001 的另一档：已有会话内 append 消息失败 ⇒
     * <b>会话的 last_message_* 不得被改动</b>。
     *
     * <p>Go 出处（{@code internal/model/message.go:196-231}）：事务里是
     * 「先 UPDATE 会话 → 再 INSERT 消息」，任一步失败整体回滚。
     * 修复前 {@code @Transactional} 失效，会话已被 UPDATE 提交 ⇒ 出现
     * 「会话声称最新消息是 X、但 X 根本不存在」的悬空状态。
     */
    @Test
    @DisplayName("DRIFT-MSG-001：append 消息失败 ⇒ 会话 last_message_* 必须保持原值")
    void rollbackConversationUpdateWhenMessageInsertFails() {
        // 先造一个干净会话
        ConversationEntity seed = newConversation(900_000_000_002L, 3L, 4L);
        seed.setLastMessageId(111L);
        seed.setLastMessagePreview("original");
        seed.setLastMessageAt(OffsetDateTime.of(2026, 10, 1, 0, 0, 0, 0, ZoneOffset.UTC));
        atomicService.createConversationWithMessageTx(seed, newMessage(seed.getId(), 3L, 4L));

        // 尝试 append 一条会失败的消息
        ConversationMessageEntity bad = newMessage(seed.getId(), 3L, 4L);
        bad.setContent(null);

        assertThrows(RuntimeException.class,
                () -> atomicService.createOrSendMessageTx(seed, bad,
                        2, 0, 0L, "should-not-commit"),
                "message 插入撞 NOT NULL 必须抛异常");

        Long lastMessageId = jdbcTemplate.queryForObject(
                "SELECT last_message_id FROM message_conversation WHERE id = ?",
                Long.class, seed.getId());
        String preview = jdbcTemplate.queryForObject(
                "SELECT last_message_preview FROM message_conversation WHERE id = ?",
                String.class, seed.getId());

        assertEquals(111L, lastMessageId,
                "会话的 last_message_id 必须回滚到原值；变了说明 UPDATE 提交了而 INSERT 失败（事务失效）");
        assertEquals("original", preview,
                "会话的 last_message_preview 必须回滚到原值；「should-not-commit」出现即事务失效");
    }

    // ==================================================================
    // 反向断言：防「什么都不做」式假阳性
    // ==================================================================

    /**
     * happy path：同样两个方法，<b>不制造失败时必须真的写进去</b>。
     *
     * <p>没有这一条，上面两条「回滚」用例可能因为「压根没执行 SQL」而假绿。
     */
    @Test
    @DisplayName("反向断言：happy path ⇒ conversation 与 message 都真的写入了")
    void happyPathActuallyPersistsBothRows() {
        ConversationEntity conv = newConversation(900_000_000_003L, 5L, 6L);
        ConversationMessageEntity msg = newMessage(conv.getId(), 5L, 6L);
        msg.setContent("persisted");

        atomicService.createConversationWithMessageTx(conv, msg);

        assertEquals(1L, count("message_conversation", conv.getId()), "happy path 下会话行必须存在");
        assertEquals(1L, count("message_conversation_message", msg.getId()), "happy path 下消息行必须存在");
        assertEquals(conv.getId(), msg.getConversationId(),
                "事务内必须把 conversation.id 写回 message.conversationId");
    }

    // ==================================================================
    // 结构性防回归：@Transactional 不得再回到 MessageDomainService 本类上
    // ==================================================================

    /**
     * <b>DRIFT-MSG-001 的结构性防回归</b>（§3.3：凡「批量改 N 处」必须配守恒断言）。
     *
     * <p>若有人把多步事务方法从 {@link MessageAtomicService} 搬回
     * {@link MessageDomainService} 本类、并由 {@code doXxx(...)} 同类调用，
     * 本断言立刻变红 —— 语义测试需要真 PG + 容器，而容器在很多 CI 上是跳过的
     * （{@code @EnabledIfDockerAvailable}），<b>结构断言是唯一不依赖 Docker 的那道网</b>。
     *
     * <p>同款范式见 {@code AdminUpdateSelfAndCreateAdminRegressionTest:169}。
     */
    @Test
    @DisplayName("DRIFT-MSG-001 防回归：MessageDomainService 上不得再有 @Transactional 方法（会 self-invocation 失效）")
    void domainServiceMustNotDeclareTransactionalMethods() {
        var declared = java.util.Arrays.stream(MessageDomainService.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(org.springframework.transaction.annotation.Transactional.class))
                .map(java.lang.reflect.Method::getName)
                .collect(java.util.stream.Collectors.toList());

        assertTrue(declared.isEmpty(),
                "MessageDomainService 上不得声明 @Transactional 方法：它们只能被同类 doXxx(...) 直接调用，" +
                        "不经 Spring 代理 ⇒ 事务静默失效。当前命中：" + declared +
                        "（多步写事务必须放在独立 Bean MessageAtomicService 上）");
    }

    @Test
    @DisplayName("DRIFT-MSG-001 防回归：6 个多步写事务方法必须留在独立 Bean MessageAtomicService 上")
    void atomicServiceMustOwnTheTransactionalMethods() {
        var expected = java.util.Set.of(
                "createConversationWithMessageTx", "createOrSendMessageTx", "insertNotificationsTx",
                "markConversationReadTx", "markNotificationReadTx", "markAllNotificationsReadTx");

        var actual = java.util.Arrays.stream(MessageAtomicService.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(org.springframework.transaction.annotation.Transactional.class))
                .map(java.lang.reflect.Method::getName)
                .collect(java.util.stream.Collectors.toSet());

        assertEquals(expected, actual,
                "MessageAtomicService 必须持有且仅持有这 6 个 @Transactional 方法");
    }

    // ==================================================================
    // helpers
    // ==================================================================

    private long count(String table, long id) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE id = ?", Long.class, id);
    }

    /** 构造一个字段齐全的 ConversationEntity（insertConv 会绑全部 15 列）。 */
    private ConversationEntity newConversation(long id, long p1Id, long p2Id) {
        ConversationEntity c = new ConversationEntity();
        c.setId(id);
        c.setParticipant1Role(1);      // SenderRole.USER
        c.setParticipant1Id(p1Id);
        c.setParticipant2Role(1);
        c.setParticipant2Id(p2Id);
        c.setStatus(1);                 // ConversationStatus.PENDING
        c.setPendingSenderRole(1);
        c.setPendingSenderId(p1Id);
        c.setCreatedByRole(1);
        c.setCreatedById(p1Id);
        c.setLastMessageId(0L);
        c.setLastMessagePreview("");
        OffsetDateTime now = OffsetDateTime.of(2026, 10, 22, 10, 0, 0, 0, ZoneOffset.UTC);
        c.setLastMessageAt(now);
        c.setCreatedAt(now);
        c.setUpdatedAt(now);
        return c;
    }

    private ConversationMessageEntity newMessage(long conversationId, long senderId, long recipientId) {
        ConversationMessageEntity m = new ConversationMessageEntity();
        m.setId(System.nanoTime());
        m.setConversationId(conversationId);
        m.setSenderId(senderId);
        m.setSenderRole(1);
        m.setRecipientId(recipientId);
        m.setRecipientRole(1);
        m.setContent("content");
        m.setRead(false);
        m.setCreatedAt(OffsetDateTime.of(2026, 10, 22, 10, 0, 0, 0, ZoneOffset.UTC));
        return m;
    }
}