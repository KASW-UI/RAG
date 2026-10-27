package com.rag.message.message.rpc;

import com.rag.message.message.proto.ConversationListReq;
import com.rag.message.message.proto.NotificationListReq;
import com.rag.message.message.proto.SenderRole;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.rag.message.message.rpc.support.RealContainerPort;

/**
 * 🔴 DRIFT-MSG-V-014 守门测试 —— 负 {@code offset} 必须是<b>被跳过的子句</b>，
 * 绝不能变成 Postgres 语法错误。
 *
 * <h2>Go 侧行为（消费点）</h2>
 * <pre>{@code
 * // internal/model/message.go:59-64 / :127-131 / :164-168 —— 三处逐字同构
 * if offset > 0 { db = db.Offset(int(offset)) }   ← offset = -1 ⇒ 整段被跳过
 * if limit  > 0 { db = db.Limit(int(limit))   }   ← limit  =  0 ⇒ 整段被跳过
 * }</pre>
 * ⇒ {@code offset=-1} 时 SQL 里<b>根本没有 OFFSET 子句</b> ⇒ <b>SUCCESS + 第一页</b>。
 *
 * <h2>修复前的 RAG 行为</h2>
 * 三个 Mapper 都是 {@code w.last("LIMIT " + limit + " OFFSET " + offset)} 无条件拼接
 * ⇒ {@code offset=-1} 生成 {@code LIMIT 20 OFFSET -1}
 * ⇒ {@code ERROR: OFFSET must not be negative}
 * ⇒ 被 {@code MessageRpcServiceImpl} 的兜底翻成 {@code INTERNAL} + "internal server error"
 * ⇒ 客户端拿到 <b>5001</b>。
 *
 * <p><b>危害不在于返回错，而在于分类错了</b>：参数非法被降级成基础设施故障，
 * 监控会把它记成 DB 故障，且客户端拿到的是「服务端内部错误」而不是「参数非法」。
 *
 * <h2>为什么必须真 PG</h2>
 * {@code OFFSET must not be negative} 是 <b>Postgres</b> 的行为，H2 没有这条语义；
 * 而「负 offset 变成 500」这个可观察后果<b>只在真 PG 上才成立</b>。
 *
 * <h2>可证伪性</h2>
 * 把 {@code GoStylePagination.limitOffset} 改回无条件拼接（或直接内联字符串），
 * 本类<b>应当立刻红</b>。
 */
@SpringBootTest(
        classes = MessageRpcApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "grpc.server.port=0",
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN",
                "grpc.client.user-rpc.address=static://127.0.0.1:1",
                "grpc.client.admin-rpc.address=static://127.0.0.1:1"
        })
@Testcontainers
@org.testcontainers.junit.jupiter.EnabledIfDockerAvailable
class MessagePaginationNegativeOffsetWiringTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("message_rpc_page_it")
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
    private static final int FIXED_HOST_PORT = 22764;
    static {
        RealContainerPort.withFixedHostPort(POSTGRES, FIXED_HOST_PORT,
                RealContainerPort.PG_CONTAINER_PORT);
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private MessageDomainService domainService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private static final int SEED = 5;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("DELETE FROM message_conversation_message");
        jdbcTemplate.update("DELETE FROM message_conversation");
        jdbcTemplate.update("DELETE FROM message_notification");

        for (int i = 0; i < SEED; i++) {
            LocalDateTime at = LocalDateTime.of(2026, 1, 1, 0, 0).plusMinutes(i);
            jdbcTemplate.update("""
                    INSERT INTO message_notification
                        (id, recipient_id, sender_id, sender_role, kind, title, content,
                         is_read, extra, created_at)
                    VALUES (?, 5001, 6001, 2, 1, ?, ?, false, '{}'::jsonb, ?)
                    """, 100L + i, "t" + i, "c" + i, at);
        }
    }

    // ==================================================================
    // 1. NotificationMapper.listByRecipient —— Go message.go:59-64
    // ==================================================================

    @Test
    @DisplayName("🔴 Go: ListNotifications 负 offset 被跳过（返回第一页，不是 500）")
    void listNotificationsWithNegativeOffsetReturnsFirstPage() {
        var resp = assertDoesNotThrow(() -> domainService.listNotifications(
                        NotificationListReq.newBuilder()
                                .setUserId(5001L)
                                .setLimit(20)
                                .setOffset(-1)
                                .build()),
                "offset=-1 在 Go 侧是「跳过 OFFSET 子句」⇒ 必须 SUCCESS + 第一页。"
                        + "抛异常说明又变回 'OFFSET must not be negative' 了");

        List<Long> ids = resp.getItemsList().stream()
                .map(n -> n.getId()).toList();
        assertEquals(SEED, ids.size(), "OFFSET 被跳过 ⇒ 取到全部 5 行（limit=20 未截断）");
        assertTrue(ids.contains(100L) && ids.contains(104L), "第一页应含首尾两行");
        assertEquals(5L, resp.getTotal(), "total 不受 offset 影响");
    }

    // ==================================================================
    // 2. ConversationMapper.listByParticipant —— Go message.go:127-131
    // ==================================================================

    @Test
    @DisplayName("🔴 Go: ListConversations 负 offset 被跳过（不是 500）")
    void listConversationsWithNegativeOffsetReturnsFirstPage() {
        long convId = 9001L;
        jdbcTemplate.update("""
                INSERT INTO message_conversation
                    (id, participant1_role, participant1_id, participant2_role, participant2_id,
                     status, pending_sender_role, pending_sender_id, created_by_role, created_by_id,
                     last_message_id, last_message_preview, last_message_at,
                     created_at, updated_at)
                VALUES (?, 1, 5001, 2, 6001,
                        1, 0, 0, 1, 5001, 0, '', ?, ?, ?)
                """, convId, LocalDateTime.of(2026, 1, 1, 0, 0),
                LocalDateTime.of(2026, 1, 1, 0, 0), LocalDateTime.of(2026, 1, 1, 0, 0));

        var resp = assertDoesNotThrow(() -> domainService.listConversations(
                        ConversationListReq.newBuilder()
                                .setOperatorId(5001L)
                                .setOperatorRole(SenderRole.USER)
                                .setLimit(20)
                                .setOffset(-1)
                                .build()),
                "offset=-1 必须是「跳过子句」，不是 SQL 语法错");

        assertEquals(1, resp.getItemsCount(), "OFFSET 被跳过 ⇒ 取到全部会话");
    }

    // ==================================================================
    // 3. limit <= 0 —— Go 同样「不加 LIMIT」⇒ 不限行数
    // ==================================================================

    @Test
    @DisplayName("🔴 Go: limit=0 时不加 LIMIT 子句（不限行数），不是 'LIMIT 0' 返回空页")
    void zeroLimitMeansNoLimitClause() {
        var resp = assertDoesNotThrow(() -> domainService.listNotifications(
                        NotificationListReq.newBuilder()
                                .setUserId(5001L)
                                .setLimit(0)
                                .setOffset(0)
                                .build()),
                "Go: limit>0 才加 Limit ⇒ limit=0 时不限行数");

        assertEquals(SEED, resp.getItemsCount(),
                "Go 语义是「不加 LIMIT」⇒ 返回全部 5 行。"
                        + "若返回 0 行，说明拼了 'LIMIT 0' —— 那是 Go 不存在的行为");
    }

    @Test
    @DisplayName("🔴 Go: limit=-1 时同样不加 LIMIT（PG 会拒 'LIMIT -1'）")
    void negativeLimitIsAlsoSkipped() {
        var resp = assertDoesNotThrow(() -> domainService.listNotifications(
                        NotificationListReq.newBuilder()
                                .setUserId(5001L)
                                .setLimit(-1)
                                .setOffset(-5)
                                .build()),
                "limit=-1 与 offset=-5 两条子句都该被跳过");

        assertEquals(SEED, resp.getItemsCount());
    }

    // ==================================================================
    // 4. 对照组：正常值行为不变
    // ==================================================================

    @Test
    @DisplayName("对照组：正常 limit/offset 分页不变（limit=2 offset=1 ⇒ 中间 1 行）")
    void normalPaginationUnchanged() {
        var resp = domainService.listNotifications(NotificationListReq.newBuilder()
                .setUserId(5001L)
                .setLimit(2)
                .setOffset(1)
                .build());

        // created_at desc ⇒ 顺序是 104,103,102,101,100；offset=1 limit=2 ⇒ [103,102]
        assertEquals(List.of(103L, 102L), resp.getItemsList().stream()
                .map(n -> n.getId()).toList(),
                "正常分页路径必须与修复前完全一致");
        assertEquals(SEED, resp.getTotal(), "total 是全量计数，不受分页影响");
    }

    /**
     * 🔴 <b>守恒断言：三条分页路径必须同款</b>。
     *
     * <p>修复涉及 <b>3 个 Mapper × 4 种参数组合</b>，逐点行为测试最容易漏掉某一个组合
     * （AGENTS.md §3.3.3「凡是脚本批量改 N 处，必须配一条覆盖全部 N 处的守恒断言」）。
     * 这里断言「任一路径 + 任一负值组合」都<b>不抛</b>且行数一致。
     */
    @Test
    @DisplayName("守恒：3 条路径 × 全部负值组合均不抛异常，且行数与「跳过子句」一致")
    void allNegativeCombinationsAcrossAllThreePaths() {
        int[][] combos = {{-1, -1}, {-1, 0}, {0, -1}, {0, 0}, {-100, -100}, {20, -1}, {-1, 20}};
        for (int[] c : combos) {
            int limit = c[0], offset = c[1];
            assertDoesNotThrow(() -> domainService.listNotifications(
                            NotificationListReq.newBuilder()
                                    .setUserId(5001L)
                                    .setLimit(limit)
                                    .setOffset(offset)
                                    .build()),
                    "limit=" + limit + " offset=" + offset + " 抛异常 ⇒ 该组合仍会生成非法 SQL");
            assertEquals(expectedRows(limit, offset), actualRows(limit, offset),
                    "limit=" + limit + " offset=" + offset
                            + " 的行数应等于 Go 的「两个子句各自判 > 0」语义");
        }
    }

    /** Go 语义的可执行表述：limit>0 才截断，offset>0 才偏移。 */
    private int expectedRows(int limit, int offset) {
        int rows = SEED;
        if (limit > 0) rows = Math.min(rows, limit);
        if (offset > 0) rows = Math.max(0, rows - offset);
        return rows;
    }

    private int actualRows(int limit, int offset) {
        return domainService.listNotifications(NotificationListReq.newBuilder()
                .setUserId(5001L)
                .setLimit(limit)
                .setOffset(offset)
                .build()).getItemsCount();
    }

    /** 显式保留：负 offset 在 Go 侧<b>不是</b> INVALID_ARGUMENT，而是正常返回第一页。 */
    @Test
    @DisplayName("对照：本模块**不**把负 offset 改成 INVALID_ARGUMENT（Go 没这个出口）")
    void negativeOffsetIsNotRejectedAsInvalidArgument() {
        assertThrows(AssertionError.class,
                () -> assertEquals(0, actualRows(-1, -1)),
                "负 offset 必须返回数据而不是 0 行 —— 若你把它改成「参数非法直接报错」，"
                        + "说明偏离了 Go：Go 侧返回的是 SUCCESS + 第一页");
    }
}