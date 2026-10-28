package com.rag.message.message.rpc;

import com.rag.message.message.rpc.domain.ConversationMessageEntity;
import com.rag.message.message.rpc.mapper.ConversationMessageMapper;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.rag.message.message.rpc.support.RealContainerPort;
import com.rag.message.message.rpc.support.GrpcTestPort;

/**
 * 🔴 DRIFT-MSG-V-017 守门测试 —— MyBatis-Plus 的 lambda 列名解析必须<b>真的能跑</b>。
 *
 * <h2>修的是什么</h2>
 * {@code ConversationMessageEntity.isRead} 字段名与 getter 名不一致
 * （字段 {@code isRead} / getter {@code isRead()} / setter {@code setRead()}）
 * ⇒ {@code TableInfo.propertyMap} 的 key 是 {@code isRead}，
 * 而 {@code E::isRead} 解析出的属性名也对得上……但
 * {@code LambdaUtils} 取的是<b>方法引用反查出的属性</b>，两条路径给出不同结果
 * ⇒ 运行期抛 {@code MybatisPlusException: can not find lambda cache for this property [read]}。
 *
 * <p><b>修法照抄同模块的 {@link com.rag.message.message.rpc.domain.NotificationEntity}</b>：
 * 字段改成 {@code read} + {@code @TableField("is_read")}，
 * getter/setter 签名保持 {@code isRead()} / {@code setRead(boolean)} 不变。
 *
 * <h2>⚠️ 这条缺陷<b>今天没有可观察差异</b>（潜伏缺陷）</h2>
 * {@code markAllReadByRecipient} <b>生产路径不走它</b>（走的是手写
 * {@code @Update} 的 {@code markReadByConversationAndRecipient}）。
 * 修它<b>不是为了改变今天的行为</b>，是为了拆掉「一旦有人接线就抛异常」那颗雷。
 *
 * <h2>为什么这类测试必须有</h2>
 * 「字段名与 getter 不一致」是<b>纯静态</b>缺陷：不执行那段 lambda 就永远不会暴露。
 * 而 {@code markAllReadByRecipient} 无调用方 ⇒ 没有业务测试会碰到它 ⇒
 * 缺陷在代码里躺了多久没人知道。<b>本类就是把它「接上线」的唯一地方</b>：
 * 它主动调用那个死方法，让 lambda 解析在真 PG 上真的跑一遍。
 *
 * <h2>可证伪性</h2>
 * 把实体字段改回 {@code isRead} 再跑本类，<b>应当立刻红</b>
 * （抛 {@code can not find lambda cache for this property [read]}）。
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
class MessageIsReadLambdaWiringTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("message_rpc_isread_it")
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
    private static final int FIXED_HOST_PORT = 22761;
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

    @Autowired private ConversationMessageMapper messageMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    private static final long CONV = 7701L;

    @BeforeEach
    void seed() {
        jdbcTemplate.execute("DELETE FROM message_conversation_message");
        // 3 条未读 + 1 条已读：UPDATE 的 WHERE 必须只命中未读那 3 条
        for (int i = 0; i < 3; i++) {
            insert(100L + i, false);
        }
        insert(199L, true);
    }

    private void insert(long id, boolean isRead) {
        jdbcTemplate.update("""
                INSERT INTO message_conversation_message
                    (id, conversation_id, sender_id, sender_role, recipient_id, recipient_role,
                     content, is_read, created_at)
                VALUES (?, ?, 6001, 2, 5001, 1, ?, ?, ?)
                """, id, CONV, "c" + id, isRead, OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC));
    }

    // ==================================================================

    /**
     * 🔴 核心用例：<b>真的调用那个死方法</b>，让 MP 的 lambda 在真 PG 上跑一遍。
     *
     * <p>修复前这一行会抛
     * {@code MybatisPlusException: can not find lambda cache for this property [read]}，
     * 且是<b>运行期</b>抛（不编译失败）。
     */
    @Test
    @DisplayName("🔴 markAllReadByRecipient 的 lambda 能解析（DRIFT-MSG-V-017）")
    void markAllReadByRecipientLambdaResolves() {
        int rows = messageMapper.markAllReadByRecipient(5001L, 1, OffsetDateTime.of(2026, 2, 1, 0, 0, 0, 0, ZoneOffset.UTC));

        assertEquals(3, rows, "只有 3 条未读应被置为已读（种子里的第 4 条本来就是已读，不该再计入）");
        assertEquals(4L, countRead(), "UPDATE 真落地了 3 条 ⇒ 加上原本就已读的那条共 4 条");
    }

    /**
     * 🔴 <b>反向</b>：lambda 解析出来的列名必须是 {@code is_read}，
     * 而不是实体属性名 {@code read}。
     *
     * <p>这条比上一条更强：它断言的是<b>列名本身</b>。
     * 若有人把 {@code @TableField("is_read")} 删掉让 MP 走驼峰推断，
     * 上一条可能仍然绿（因为列恰好对得上），而这条会红。
     */
    @Test
    @DisplayName("🔴 lambda 解析出的列名是 is_read（不是属性名 read）")
    void lambdaResolvesToSnakeCaseColumn() {
        messageMapper.markAllReadByRecipient(5001L, 1, OffsetDateTime.of(2026, 2, 1, 0, 0, 0, 0, ZoneOffset.UTC));

        Boolean viaColumn = jdbcTemplate.queryForObject(
                "SELECT bool_and(is_read) FROM message_conversation_message WHERE recipient_id = 5001",
                Boolean.class);
        assertTrue(Boolean.TRUE.equals(viaColumn),
                "若 lambda 把属性名 read 当列名，PG 会报 column \"read\" does not exist；"
                        + "能跑通说明 @TableField(\"is_read\") 生效");

        // 属性名与列名不同这件事本身就是本缺陷的成因，显式钉住
        assertEquals("read", propertyNameOfReadField(),
                "实体字段名必须是 read（= NotificationEntity 的形态）");
        assertEquals("isRead", methodRefName(),
                "而 getter 仍是 isRead() —— MP 靠它反查属性名，所以两者必须能对上");
    }

    /** 读路径也要能映射（同一字段被 SELECT 回来时同样走 typeHandler/属性映射）。 */
    @Test
    @DisplayName("对照：读路径 getIsRead() 能正确还原（不是恒 false）")
    void readPathMapsIsRead() {
        messageMapper.markAllReadByRecipient(5001L, 1, OffsetDateTime.of(2026, 2, 1, 0, 0, 0, 0, ZoneOffset.UTC));

        List<ConversationMessageEntity> rows = messageMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ConversationMessageEntity>()
                        .eq(ConversationMessageEntity::getConversationId, CONV));
        assertEquals(4, rows.size());
        assertTrue(rows.stream().allMatch(ConversationMessageEntity::isRead),
                "4 条都应读回 is_read=true（其中 1 条本来就是）—— "
                        + "若这里恒为 false，说明读路径的属性映射也被字段改名影响了");
    }

    // ==================================================================

    private long countRead() {
        Long n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM message_conversation_message WHERE recipient_id = 5001 AND is_read",
                Long.class);
        return n == null ? 0 : n;
    }

    private String propertyNameOfReadField() {
        try {
            return ConversationMessageEntity.class.getDeclaredField("read").getName();
        } catch (NoSuchFieldException e) {
            return "<<no such field: read>>";
        }
    }

    private String methodRefName() {
        try {
            return ConversationMessageEntity.class
                    .getMethod("isRead").getName();
        } catch (NoSuchMethodException e) {
            return "<<no such method>>";
        }
    }
}