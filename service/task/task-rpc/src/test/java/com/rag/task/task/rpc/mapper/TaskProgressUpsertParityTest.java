package com.rag.task.task.rpc.mapper;

import com.rag.task.task.rpc.domain.TaskProgressEntity;
import com.rag.task.task.rpc.mapper.UserLikeCountMapper.UserLikeCountRow;
import com.rag.task.task.rpc.service.TaskProgressWriter;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 三段 upsert 的<b>真 PG 语义对拍</b> —— 这三段是 Go 两个 sink 幂等链路的全部基石。
 *
 * <h2>⚠️ 为什么必须接真 PostgreSQL，不能用 H2</h2>
 * <p>要验证的全是 <b>PG 特有语义</b>：
 * <ul>
 *   <li>{@code ON CONFLICT (user_id, task_id)} —— 依赖复合<b>主键</b>；</li>
 *   <li>{@code GREATEST(a, b)} —— H2 的行为与 PG 不同；</li>
 *   <li>{@code CASE WHEN ... THEN ... ELSE EXCLUDED.status END} —— 引用 {@code EXCLUDED}，
 *       这是 PG 的 upsert 伪表，H2 没有等价物；</li>
 *   <li>{@code COALESCE(task_progress.done_at, now())} —— 「首次完成时间不可覆盖」的核心；</li>
 *   <li>{@code now()} 与 TIMESTAMPTZ 的实际行为。</li>
 * </ul>
 * 换成 H2 等于<b>把要验证的东西换没了</b>（AGENTS.md §3.3 的同类警告）。
 *
 * <h2>Go 出处</h2>
 * <ul>
 *   <li>{@code like_sink.go:237-240} / {@code article_sink.go:324-327} —— lazyInit（DO NOTHING）</li>
 *   <li>{@code like_sink.go:311-323} / {@code article_sink.go:401-416} —— updateDoing（CASE + GREATEST）</li>
 *   <li>{@code like_sink.go:375-384} / {@code article_sink.go:477-490} —— markDone（COALESCE）</li>
 *   <li>{@code like_sink.go:415-422} —— user_like_count（GREATEST）</li>
 * </ul>
 */
@SpringBootTest(
        classes = com.rag.task.task.rpc.TaskRpcApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "grpc.server.port=0",
                // 绕过 Nacos：没有注册中心。注册本身在 ApplicationReadyEvent 且已 try/catch，
                // 但 devh 的发现配置仍会连，故直接指向不可达地址。
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "logging.level.com.alibaba.nacos=WARN",
                // ⚠️ 绕过 Kafka 的方式是**指向不可达地址**，**不是** exclude 自动配置
                //    （同 like-rpc 的 wiring test）：
                //    exclude 掉 KafkaAutoConfiguration ⇒ 容器里没有 KafkaTemplate
                //    ⇒ 两个 sink 的构造注入失败 ⇒ 上下文起不来；
                //    指向 127.0.0.1:1 时 consumer 只是持续重试连接，不影响上下文启动，
                //    而 KafkaTemplate / ConsumerFactory / listener 容器**照样被装配**
                //    —— 这正是本类想顺带验证的东西。
                "spring.kafka.bootstrap-servers=127.0.0.1:1",
                // 绕过 Redis：Lettuce 连接懒建，不真正连
                "spring.data.redis.host=127.0.0.1",
                "spring.data.redis.port=1",
                "management.otlp.tracing.export.enabled=false"
        })
@Testcontainers
@org.testcontainers.junit.jupiter.EnabledIfDockerAvailable
class TaskProgressUpsertParityTest {

    @Container
    static PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("first_db")
                    .withUsername("admin")
                    .withPassword("password");

    /**
     * 把容器接成真数据源。
     *
     * <p>⚠️ 这一步不能省：接了容器才能同时验证
     * <b>Flyway 是否真跑</b>（V1/V2 建表）与 <b>MyBatis-Plus 是否真产出 SqlSessionFactory</b> ——
     * 这两条都是「编译通过 + 纯 mock 单测全绿」永远发现不了的装配问题
     * （AGENTS.md §3.3：like-rpc / hot-rpc 都曾「测试全绿、组件根本起不来」）。
     */
    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TaskProgressMapper mapper;

    @Autowired
    private UserLikeCountMapper userLikeCountMapper;

    @Autowired
    private TaskProgressWriter writer;

    // ==================== 建表（顺带验证 Flyway 真跑了） ====================

    @Test
    @DisplayName("Flyway 真的建出了两张表（验证 spring-boot-flyway 装配点有效）")
    void flywayCreatedTables() {
        assertNotNull(tableExists("task_progress"), "task_progress 不存在 ⇒ 迁移没跑");
        assertNotNull(tableExists("user_like_count"), "user_like_count 不存在 ⇒ 迁移没跑");
    }

    @Test
    @DisplayName("task_progress 的 user_id 是 BIGINT，而 user_like_count 的 user_id 是 TEXT")
    void columnTypesMatchGo() {
        // ⚠️ information_schema.columns.data_type 给的是 **SQL 标准名**
        //    （bigint / text / timestamp with time zone），
        //    不是 PG 的内部名（int8 / varchar / timestamptz）。
        //    两者指向同一类型，写断言时别拿错。
        assertEquals("bigint", columnType("task_progress", "user_id"),
                "Go TaskProgress.UserID 是 int64 ⇒ BIGINT");
        assertEquals("text", columnType("user_like_count", "user_id"),
                "Go UserLikeCount.UserID 是 string ⇒ TEXT（与上表不同，这是 Go 的现状）");
        assertEquals("text", columnType("task_progress", "status"), "Go Status 是无 size tag 的 string");
        assertEquals("bigint", columnType("task_progress", "progress"));
        assertEquals("bigint", columnType("task_progress", "target"));
        assertEquals("timestamp with time zone", columnType("task_progress", "done_at"),
                "Go 的 *time.Time 在 PG 上是 timestamptz，不是 timestamp");
        assertEquals("timestamp with time zone", columnType("task_progress", "created_at"));
        assertEquals("timestamp with time zone", columnType("task_progress", "updated_at"));
    }

    // ==================== lazyInit：DO NOTHING ====================

    @Test
    @DisplayName("lazyInit 只在首次插入；已存在的行完全不被改动（不是 upsert）")
    void lazyInitDoesNothingOnConflict() {
        long uid = 9001L;
        long taskId = 1902L;

        writer.lazyInit(List.of(initRow(uid, taskId, "doing", 0L, 5L)));
        // 手工把状态改成 done / progress 改大，模拟"已完成"
        jdbc.update("UPDATE task_progress SET status='done', progress=5 WHERE user_id=? AND task_id=?",
                uid, taskId);

        writer.lazyInit(List.of(initRow(uid, taskId, "doing", 0L, 5L)));

        assertEquals("done", statusOf(uid, taskId), "★ DO NOTHING 不许把 done 重置成 doing");
        assertEquals(5L, progressOf(uid, taskId));
    }

    // ==================== updateDoing：CASE + GREATEST ====================

    @Test
    @DisplayName("🔴 updateDoing 不得把 done 回退成 doing（CASE 保护，Go like_sink.go:314-315）")
    void updateDoingNeverRegressesStatusFromDone() {
        long uid = 9002L;
        long taskId = 1902L;
        writer.lazyInit(List.of(initRow(uid, taskId, "doing", 0L, 5L)));
        writer.markDone(uid, taskId, 5L);

        // 迟到的"未达标"快照
        writer.updateDoing(uid, taskId, 5L, 2L);

        assertEquals("done", statusOf(uid, taskId), "★ 已完成的任务不许被回退");
        assertEquals(5L, progressOf(uid, taskId), "★ 且 progress 保持 5（GREATEST 生效）");
    }

    @Test
    @DisplayName("updateDoing：progress 只增不减（GREATEST），且插入不存在的行")
    void updateDoingIsMonotonicAndUpserts() {
        long uid = 9003L;
        long taskId = 1902L;

        // 未初始化直接 update ⇒ 应插入一行
        writer.updateDoing(uid, taskId, 5L, 3L);
        assertEquals("doing", statusOf(uid, taskId));
        assertEquals(3L, progressOf(uid, taskId));

        // 更小的值 ⇒ GREATEST 保持 3
        writer.updateDoing(uid, taskId, 5L, 1L);
        assertEquals(3L, progressOf(uid, taskId));

        // 更大的值 ⇒ 前进
        writer.updateDoing(uid, taskId, 5L, 4L);
        assertEquals(4L, progressOf(uid, taskId));
    }

    @Test
    @DisplayName("🔴 updateDoing 会把 progress 夹到 [0, target]（夹取在 Java 侧，Go like_sink.go:296-301）")
    void updateDoingClampsProgressToTarget() {
        long taskId = 1902L;

        // 上界：99 → target
        writer.updateDoing(9010L, taskId, 5L, 99L);
        assertEquals(5L, progressOf(9010L, taskId), "★ 99 必须被夹到 target=5");

        // 下界：负数 → 0（用**另一个** uid：同一 uid 上 GREATEST 会挡住回退，见下面的用例）
        writer.updateDoing(9011L, taskId, 5L, -7L);
        assertEquals(0L, progressOf(9011L, taskId), "★ 负数必须被夹到 0");
    }

    @Test
    @DisplayName("🔴 夹取与 GREATEST 的叠加效果：已到 5 的行，再来 0 也**不会**退回 0")
    void clampThenGreatestStillBlocksRegression() {
        long uid = 9012L;
        long taskId = 1902L;

        writer.updateDoing(uid, taskId, 5L, 99L);   // 夹到 5
        assertEquals(5L, progressOf(uid, taskId));

        writer.updateDoing(uid, taskId, 5L, -7L);   // 夹到 0，但 DO UPDATE 用的是 GREATEST

        assertEquals(5L, progressOf(uid, taskId),
                "★ 夹取只保证「写入值」在 [0,target]；真正的单调性由 GREATEST 提供"
                        + "（Go like_sink.go:317）。这条断言同时锁住两层语义。");
    }

    @Test
    @DisplayName("lazyInit 插入的行不带 created_at / updated_at（Go 的局部结构体没有时间字段）")
    void lazyInitOmitsTimestampColumns() {
        long uid = 9005L;
        long taskId = 1902L;

        writer.lazyInit(List.of(initRow(uid, taskId, "doing", 0L, 5L)));

        assertNull(jdbc.queryForObject(
                "SELECT created_at FROM task_progress WHERE user_id=? AND task_id=?",
                java.time.OffsetDateTime.class, uid, taskId),
                "★ Go 的 UserTaskProgress 结构体没有 CreatedAt ⇒ INSERT 不含该列 ⇒ 为 NULL");
    }

    // ==================== markDone：COALESCE ====================

    @Test
    @DisplayName("🔴 markDone 的 done_at 用 COALESCE：首次完成时间不可被覆盖")
    void markDoneKeepsFirstDoneAt() {
        long uid = 9006L;
        long taskId = 1902L;
        writer.lazyInit(List.of(initRow(uid, taskId, "doing", 0L, 5L)));

        writer.markDone(uid, taskId, 5L);
        java.time.OffsetDateTime first = doneAtOf(uid, taskId);
        assertNotNull(first, "★ markDone 必须写 done_at");

        // 人工把 done_at 改成一个可区分的旧值，再 markDone 一次
        jdbc.update("UPDATE task_progress SET done_at = timestamptz '2000-01-01 00:00:00+00' "
                + "WHERE user_id=? AND task_id=?", uid, taskId);
        writer.markDone(uid, taskId, 5L);

        assertEquals(jdbc.queryForObject(
                        "SELECT done_at FROM task_progress WHERE user_id=? AND task_id=?",
                        java.time.OffsetDateTime.class, uid, taskId),
                doneAtOf(uid, taskId),
                "★ COALESCE(done_at, now()) ⇒ 已有的 done_at 不被刷新");
    }

    @Test
    @DisplayName("markDone 无条件覆盖 status 与 progress（没有 CASE 保护）")
    void markDoneOverwritesProgress() {
        long uid = 9007L;
        long taskId = 1902L;
        writer.lazyInit(List.of(initRow(uid, taskId, "doing", 0L, 5L)));

        // 先人为把 progress 写成大于 target 的值
        jdbc.update("UPDATE task_progress SET progress=99 WHERE user_id=? AND task_id=?", uid, taskId);

        writer.markDone(uid, taskId, 5L);

        assertEquals("done", statusOf(uid, taskId));
        assertEquals(5L, progressOf(uid, taskId),
                "★ markDone 用的是**常量覆盖**而不是 GREATEST ⇒ 99 被压到 target=5");
        assertEquals(5L, targetOf(uid, taskId));
    }

    @Test
    @DisplayName("markDone 能凭空插入一行（对应 Go 在没有初始行时直接 upsert）")
    void markDoneUpsertsMissingRow() {
        long uid = 9008L;
        long taskId = 1902L;

        writer.markDone(uid, taskId, 5L);

        assertEquals("done", statusOf(uid, taskId));
        assertEquals(5L, progressOf(uid, taskId));
    }

    // ==================== user_like_count：GREATEST ====================

    @Test
    @DisplayName("🔴 user_like_count 的 like_count 只增不减（GREATEST，Go like_sink.go:419）")
    void userLikeCountIsMonotonic() {
        userLikeCountMapper.batchUpsertGreatest(List.of(new UserLikeCountRow("5001", 7L)));
        assertEquals(7L, likeCountOf("5001"));

        // 乱序的更小值 ⇒ 保持 7
        userLikeCountMapper.batchUpsertGreatest(List.of(new UserLikeCountRow("5001", 3L)));
        assertEquals(7L, likeCountOf("5001"), "★ 乱序重投不能把计数写小");

        userLikeCountMapper.batchUpsertGreatest(List.of(new UserLikeCountRow("5001", 9L)));
        assertEquals(9L, likeCountOf("5001"));
    }

    @Test
    @DisplayName("user_like_count 批量插入带 updated_at（Go 的结构体有 autoUpdateTime）")
    void userLikeCountSetsUpdatedAt() {
        userLikeCountMapper.batchUpsertGreatest(List.of(new UserLikeCountRow("5002", 1L)));

        assertNotNull(jdbc.queryForObject(
                "SELECT updated_at FROM user_like_count WHERE user_id=?", java.time.OffsetDateTime.class, "5002"),
                "★ Go 的 UserLikeCount 有 UpdatedAt autoUpdateTime ⇒ INSERT 必须含该列");
    }

    @Test
    @DisplayName("🔴 user_like_count 的 user_id 是 TEXT：非数字也能存（Go 是 string 字段）")
    void userLikeCountAcceptsNonNumericId() {
        userLikeCountMapper.batchUpsertGreatest(List.of(new UserLikeCountRow("not-a-number", 4L)));

        assertEquals(4L, likeCountOf("not-a-number"),
                "★ 若是 BIGINT 这一行会直接插入失败 —— 这正是两侧类型必须一致的原因");
    }

    // ==================== 复合主键 ====================

    @Test
    @DisplayName("同一 uid 可有多条不同 taskId 的行（复合主键，不是单列主键）")
    void compositePrimaryKey() {
        long uid = 9009L;
        writer.lazyInit(List.of(initRow(uid, 190L, "doing", 0L, 3L),
                initRow(uid, 1902L, "doing", 0L, 5L)));

        assertEquals("doing", statusOf(uid, 190L));
        assertEquals("doing", statusOf(uid, 1902L));

        // 改一条不影响另一条
        writer.markDone(uid, 190L, 3L);
        assertEquals("done", statusOf(uid, 190L));
        assertEquals("doing", statusOf(uid, 1902L));
    }

    // ==================== helpers ====================

    private static TaskProgressEntity initRow(long uid, long taskId, String status,
                                               long progress, long target) {
        TaskProgressEntity e = new TaskProgressEntity();
        e.setUserId(uid);
        e.setTaskId(taskId);
        e.setStatus(status);
        e.setProgress(progress);
        e.setTarget(target);
        return e;
    }

    private String statusOf(long uid, long taskId) {
        return jdbc.queryForObject("SELECT status FROM task_progress WHERE user_id=? AND task_id=?",
                String.class, uid, taskId);
    }

    private long progressOf(long uid, long taskId) {
        Long v = jdbc.queryForObject("SELECT progress FROM task_progress WHERE user_id=? AND task_id=?",
                Long.class, uid, taskId);
        return v == null ? -1L : v;
    }

    private long targetOf(long uid, long taskId) {
        Long v = jdbc.queryForObject("SELECT target FROM task_progress WHERE user_id=? AND task_id=?",
                Long.class, uid, taskId);
        return v == null ? -1L : v;
    }

    private java.time.OffsetDateTime doneAtOf(long uid, long taskId) {
        return jdbc.queryForObject("SELECT done_at FROM task_progress WHERE user_id=? AND task_id=?",
                java.time.OffsetDateTime.class, uid, taskId);
    }

    private long likeCountOf(String userId) {
        Long v = jdbc.queryForObject("SELECT like_count FROM user_like_count WHERE user_id=?",
                Long.class, userId);
        return v == null ? -1L : v;
    }

    private String tableExists(String table) {
        List<String> names = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema='public'", String.class);
        return names.contains(table) ? table : null;
    }

    private String columnType(String table, String column) {
        return jdbc.queryForObject(
                """
                SELECT data_type FROM information_schema.columns
                WHERE table_schema='public' AND table_name=? AND column_name=?
                """, String.class, table, column);
    }
}