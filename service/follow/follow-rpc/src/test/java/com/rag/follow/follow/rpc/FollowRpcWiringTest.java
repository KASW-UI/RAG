package com.rag.follow.follow.rpc;

import com.rag.follow.follow.rpc.domain.FollowRelationEntity;
import com.rag.follow.follow.rpc.mapper.BlockRelationMapper;
import com.rag.follow.follow.rpc.mapper.FollowRelationMapper;
import com.rag.follow.follow.rpc.mapper.GoStyleLimitOffset;
import com.rag.follow.follow.rpc.service.FollowBlockTransactionService;
import com.rag.follow.follow.rpc.service.FollowDomainService;
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
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §6.3 / §3.3 的真 PG 证据 —— 在<b>真实 PostgreSQL 16</b> 上验证
 * follow-rpc 的装配、DDL 对齐与事务原子性。
 *
 * <h2>为什么必须是真 PG（而不是 H2 / 纯 Mockito）</h2>
 * <p>本类要验证的东西<b>按定义</b>就是纯 mock 测不到的：
 * <ul>
 *   <li><b>上下文能不能真起来</b>：{@code @MapperScan} 缺失、MyBatis-Plus 在 Boot 4 下的
 *       自动配置排序悬空、{@code spring-boot-flyway} 缺失、{@code nacosRegistration} Bean 撞名 ——
 *       这四类问题<b>全都不会让编译失败</b>，其中三类连启动日志都没有异常。
 *       （AGENTS.md §0 已记录：hot-rpc / like-rpc 曾「测试全绿而根本起不来」。）</li>
 *   <li><b>DDL 是否真的对齐 GORM AutoMigrate 产物</b>：{@code timestamptz} 与 {@code timestamp}
 *       在同一行数据上会读出不同的墙上时间；{@code BIGSERIAL} 与 {@code BIGINT} 决定主键谁来分配。</li>
 *   <li><b>ON CONFLICT 的幂等语义</b>：它依赖唯一索引真实存在。</li>
 *   <li><b>事务回滚</b>：依赖 PG 的事务与约束语义。</li>
 * </ul>
 *
 * <h2>本类共 5 组证据</h2>
 * <ol>
 *   <li><b>装配</b>：上下文启动 + Mapper Bean 存在 + Flyway 建表 + 账本表名隔离；</li>
 *   <li><b>DDL 对齐</b>：逐列核对 GORM 产物；</li>
 *   <li><b>Mapper 语义</b>：幂等 upsert / 双向删除与计数 / 排序 + 条件 LIMIT-OFFSET；</li>
 *   <li><b>事务原子性</b>：用<b>数据库自然约束冲突</b>让第二条语句失败，验证第一条被回滚；</li>
 *   <li><b>防回归断言</b>：{@code @Transactional} 的归属（self-invocation 会静默失效）。</li>
 * </ol>
 */
@SpringBootTest(
        classes = FollowRpcApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "grpc.server.port=0",
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN",
                // ⚠️ 必须把 @GrpcClient 的 discovery:// 覆盖成 static://，否则上下文起不来：
                //   devh 的 GrpcClientBeanPostProcessor 在 **Bean 初始化期就建 channel**（不是懒建），
                //   而测试环境没有 Nacos ⇒ IllegalArgumentException: Incorrectly formatted target uri。
                //   指向一个必然连不上的端口即可 —— devh 的连接本身是懒的，
                //   而本类验证的是装配 / DDL / 事务路径，**不调 user-rpc**。
                "grpc.client.user-rpc.address=static://127.0.0.1:1"
        })
@Testcontainers
@org.testcontainers.junit.jupiter.EnabledIfDockerAvailable
class FollowRpcWiringTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("follow_rpc_it")
                    .withUsername("rag")
                    .withPassword("rag");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private FollowRelationMapper followRelationMapper;
    @Autowired private BlockRelationMapper blockRelationMapper;
    @Autowired private FollowBlockTransactionService blockTransaction;
    @Autowired private FollowDomainService domainService;
    @Autowired private JdbcTemplate jdbcTemplate;

    /**
     * 外键守卫表：让「删除 follow_relation 的某一行」被数据库<b>自然拒绝</b>。
     *
     * <p><b>为什么需要它</b>：{@code createBlockAndCleanup} 的第二条语句是 DELETE，
     * 而 DELETE 几乎不会自然失败 ⇒ 无法用「制造数据库错误」的方式验证回滚。
     * 这张表引用 {@code follow_relation(user_id, target_id)} 且 {@code ON DELETE RESTRICT}
     * ⇒ 只要存在守卫行，DELETE 就必然违反外键约束。
     *
     * <p>它让「第二条语句失败」成为一件<b>由数据库判定</b>的事，
     * 而不是测试代码自己抛异常 —— 后者与「事务到底有没有生效」无关。
     */
    private static final String GUARD_DDL = """
            CREATE TABLE IF NOT EXISTS test_follow_guard (
                id BIGSERIAL PRIMARY KEY,
                guard_user   BIGINT NOT NULL,
                guard_target BIGINT NOT NULL,
                CONSTRAINT fk_test_follow_guard
                    FOREIGN KEY (guard_user, guard_target)
                    REFERENCES follow_relation (user_id, target_id)
                    ON DELETE RESTRICT
            )
            """;

    @BeforeEach
    void cleanSlate() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS test_follow_guard");
        jdbcTemplate.update("DELETE FROM follow_relation");
        jdbcTemplate.update("DELETE FROM block_relation");
    }

    // ==================================================================
    // 1. 装配：上下文必须真的能起来
    // ==================================================================

    @Test
    @DisplayName("装配：Spring 上下文能启动，且两个 Mapper Bean 都注册成功")
    void contextLoadsWithBothMapperBeans() {
        assertNotNull(followRelationMapper, "@MapperScan 缺失 ⇒ Mapper Bean 注册不上");
        assertNotNull(blockRelationMapper);
        assertNotNull(domainService);
        assertNotNull(blockTransaction);
    }

    @Test
    @DisplayName("装配：两张业务表被 Flyway 建出来（spring-boot-flyway 缺失时连日志都没有）")
    void flywayCreatedBothTables() {
        Integer n = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN ('follow_relation', 'block_relation')
                """, Integer.class);
        assertEquals(2, n, "follow_relation / block_relation 必须都由迁移建出来");
    }

    @Test
    @DisplayName("装配：Flyway 账本表必须是 flyway_schema_history_follow（AGENTS.md §6.3 问题 4）")
    void flywayLedgerTableIsIsolated() {
        Integer own = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name = 'flyway_schema_history_follow'
                """, Integer.class);
        Integer default_ = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name = 'flyway_schema_history'
                """, Integer.class);
        assertEquals(1, own, "本模块的账本表名必须是 flyway_schema_history_follow");
        assertEquals(0, default_,
                "不得落回默认账本名：like-rpc / message-rpc / article-rpc 连同一个 schema，"
                        + "共用默认账本会让后启动者把自己的迁移判为「已应用」而静默跳过");
    }

    // ==================================================================
    // 2. DDL 对齐 GORM AutoMigrate 产物
    // ==================================================================

    @Test
    @DisplayName("DDL：create_time / update_time 必须是 timestamptz（GORM 对 Go time.Time 的映射）")
    void timestampColumnsAreTimestampz() {
        for (String table : List.of("follow_relation", "block_relation")) {
            for (String col : List.of("create_time", "update_time")) {
                String type = jdbcTemplate.queryForObject("""
                        SELECT data_type FROM information_schema.columns
                        WHERE table_schema = 'public' AND table_name = ? AND column_name = ?
                        """, String.class, table, col);
                assertEquals("timestamp with time zone", type,
                        table + "." + col + " 必须是 timestamptz。"
                                + "用 TIMESTAMP 会让 Java 侧读出的墙上时间与 Go 差一个时区偏移，"
                                + "而 create_time 还是列表的排序键");
            }
        }
    }

    @Test
    @DisplayName("DDL：主键必须是 bigserial（由 PG 序列分配，与 GORM autoIncrement 一致）")
    void primaryKeyIsBigserial() {
        String defaultValue = jdbcTemplate.queryForObject("""
                SELECT column_default FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'follow_relation' AND column_name = 'id'
                """, String.class);
        assertNotNull(defaultValue, "id 必须有 DEFAULT（序列）");
        assertTrue(defaultValue.toLowerCase().contains("nextval"),
                "id 的 DEFAULT 必须是 nextval(...)；否则插入时会写 id=0 —— " + defaultValue);
    }

    @Test
    @DisplayName("DDL：user_id / target_id 必须是 NOT NULL")
    void relationColumnsAreNotNull() {
        for (String table : List.of("follow_relation", "block_relation")) {
            for (String col : List.of("user_id", "target_id")) {
                String isNullable = jdbcTemplate.queryForObject("""
                        SELECT is_nullable FROM information_schema.columns
                        WHERE table_schema = 'public' AND table_name = ? AND column_name = ?
                        """, String.class, table, col);
                assertEquals("NO", isNullable, table + "." + col + " 必须 NOT NULL（Go 侧 not null tag）");
            }
        }
    }

    @Test
    @DisplayName("DDL：复合唯一索引 uk_follow_pair / uk_block_pair 必须存在（ON CONFLICT 的冲突目标）")
    void compositeUniqueIndexesExist() {
        for (String table : List.of("follow_relation", "block_relation")) {
            List<String> uniqueCols = jdbcTemplate.queryForList("""
                    SELECT a.attname FROM pg_index i
                    JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = ANY(i.indkey)
                    WHERE i.indrelid = ?::regclass AND i.indisunique
                    """, String.class, table);
            assertTrue(uniqueCols.containsAll(List.of("user_id", "target_id")),
                    table + " 必须有 (user_id, target_id) 复合唯一索引，实际：" + uniqueCols);
        }

        Integer followUniq = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM pg_indexes
                WHERE schemaname='public' AND tablename='follow_relation' AND indexname='uk_follow_pair'
                """, Integer.class);
        Integer blockUniq = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM pg_indexes
                WHERE schemaname='public' AND tablename='block_relation' AND indexname='uk_block_pair'
                """, Integer.class);
        assertEquals(1, followUniq, "唯一索引名必须逐字为 uk_follow_pair（Go: uniqueIndex:uk_follow_pair）");
        assertEquals(1, blockUniq, "唯一索引名必须逐字为 uk_block_pair（Go: uniqueIndex:uk_block_pair）");
    }

    // ==================================================================
    // 3. Mapper 语义
    // ==================================================================

    @Test
    @DisplayName("Mapper：insertIgnoreConflict 重复插入不报错、不产生第二行（Go 的 ON CONFLICT DO NOTHING）")
    void insertIsIdempotentViaOnConflict() {
        OffsetDateTime t = OffsetDateTime.of(2026, 10, 1, 0, 0, 0, 0, ZoneOffset.UTC);

        int first = followRelationMapper.insertIgnoreConflict(1L, 2L, t, t);
        int second = followRelationMapper.insertIgnoreConflict(1L, 2L, t.plusDays(1), t.plusDays(1));

        assertEquals(1, first, "首次插入必须真的写入 1 行");
        assertEquals(0, second, "冲突时受影响行数为 0，且**不抛异常**（Go 侧 Error==nil）");
        assertEquals(1L, countFollow(1L, 2L));
    }

    @Test
    @DisplayName("Mapper：create_time / update_time 由应用写入，不是 DB 默认值（对应 GORM struct tag）")
    void timestampsComeFromApplication() {
        OffsetDateTime t = OffsetDateTime.of(2026, 10, 1, 12, 30, 0, 0, ZoneOffset.UTC);
        followRelationMapper.insertIgnoreConflict(1L, 2L, t, t);

        OffsetDateTime createTime = jdbcTemplate.queryForObject(
                "SELECT create_time FROM follow_relation WHERE user_id=1 AND target_id=2", OffsetDateTime.class);
        assertNotNull(createTime);
        assertEquals(t.toInstant(), createTime.toInstant(),
                "时间戳必须逐瞬相等 ⇒ 来源是应用进程而非 CURRENT_TIMESTAMP");
    }

    @Test
    @DisplayName("Mapper：deleteFollowBothDirections 必须同时清掉两个方向")
    void deleteFollowBothDirectionsIsSymmetric() {
        OffsetDateTime t = OffsetDateTime.now();
        followRelationMapper.insertIgnoreConflict(1L, 2L, t, t);
        followRelationMapper.insertIgnoreConflict(2L, 1L, t, t);
        followRelationMapper.insertIgnoreConflict(3L, 4L, t, t);   // 无关行，不该被动

        int affected = followRelationMapper.deleteFollowBothDirections(1L, 2L);

        assertEquals(2, affected, "双向各删一行");
        assertEquals(0L, countFollow(1L, 2L));
        assertEquals(0L, countFollow(2L, 1L));
        assertEquals(1L, countFollow(3L, 4L), "无关行必须保留");
    }

    @Test
    @DisplayName("Mapper：countAnyBlock 必须双向命中（后三个占位符是 (targetId, userId)）")
    void countAnyBlockIsSymmetric() {
        OffsetDateTime t = OffsetDateTime.now();

        assertEquals(0L, blockRelationMapper.countAnyBlock(1L, 2L));

        // 只有 2→1 一个方向存在时，(1,2) 查询也必须命中
        blockRelationMapper.insertIgnoreConflict(2L, 1L, t, t);
        assertEquals(1L, blockRelationMapper.countAnyBlock(1L, 2L),
                "2 拉黑了 1 ⇒ 1 也不能关注 2（若占位符顺序写反，这条会返回 0）");

        blockRelationMapper.insertIgnoreConflict(1L, 2L, t, t);
        assertEquals(2L, blockRelationMapper.countAnyBlock(1L, 2L), "双向都拉黑 ⇒ 2 行");
    }

    @Test
    @DisplayName("Mapper：三个列表必须按 create_time DESC 排序（Go 的 Order(\"create_time desc\")）")
    void listsAreOrderedByCreateTimeDesc() {
        OffsetDateTime base = OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        // 故意**乱序**插入：先插最新的，再插最旧的
        followRelationMapper.insertIgnoreConflict(1L, 200L, base.plusDays(10), base.plusDays(10));
        followRelationMapper.insertIgnoreConflict(1L, 100L, base, base);
        followRelationMapper.insertIgnoreConflict(1L, 300L, base.plusDays(5), base.plusDays(5));

        assertThatDesc(List.of(200L, 300L, 100L),
                followRelationMapper.listFollowTargets(1L, GoStyleLimitOffset.limitOffset(0, 0)));
    }

    @Test
    @DisplayName("Mapper：⚠️ limit/offset<=0 时不得生成 LIMIT/OFFSET 片段（否则 PG 拒绝负 OFFSET）")
    void limitOffsetHelperSkipsNonPositiveSegments() {
        assertEquals("", GoStyleLimitOffset.limitOffset(0, 0));
        assertEquals("", GoStyleLimitOffset.limitOffset(-5, -5));
        assertEquals("LIMIT 20", GoStyleLimitOffset.limitOffset(20, 0));
        assertEquals("LIMIT 20 OFFSET 40", GoStyleLimitOffset.limitOffset(20, 40));
        assertEquals("OFFSET 40", GoStyleLimitOffset.limitOffset(0, 40),
                "limit<=0 时只输出 OFFSET —— 与 Go 的两段独立条件一致");
    }

    @Test
    @DisplayName("Mapper：分页窗口 offset/limit 逐条生效，且 listFollowerUsers 过滤列是 target_id")
    void paginationAndFollowerDirectionAreCorrect() {
        OffsetDateTime base = OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        // 1 关注 2/3/4/5（按时间倒序应为 5,4,3,2）
        followRelationMapper.insertIgnoreConflict(1L, 2L, base, base);
        followRelationMapper.insertIgnoreConflict(1L, 3L, base.plusDays(1), base.plusDays(1));
        followRelationMapper.insertIgnoreConflict(1L, 4L, base.plusDays(2), base.plusDays(2));
        followRelationMapper.insertIgnoreConflict(1L, 5L, base.plusDays(3), base.plusDays(3));
        // 9 关注 1
        followRelationMapper.insertIgnoreConflict(9L, 1L, base, base);

        assertEquals(List.of(5L, 4L), followRelationMapper.listFollowTargets(1L,
                GoStyleLimitOffset.limitOffset(2, 0)), "limit=2 ⇒ 最新的两条");
        assertEquals(List.of(3L, 2L), followRelationMapper.listFollowTargets(1L,
                GoStyleLimitOffset.limitOffset(2, 2)), "offset=2 ⇒ 跳过前两条");
        assertEquals(List.of(9L), followRelationMapper.listFollowerUsers(1L,
                GoStyleLimitOffset.limitOffset(0, 0)),
                "listFollowerUsers 过滤 target_id=1、投影 user_id ⇒ 只有 9");
    }

    // ==================================================================
    // 4. 事务原子性 —— 唯一一笔多语句事务
    // ==================================================================

    @Test
    @DisplayName("事务：正向路径 —— 建 block + 双向清 follow 必须都写进去（反向断言，防假绿）")
    void happyPathWritesBothStatements() {
        OffsetDateTime t = OffsetDateTime.now();
        followRelationMapper.insertIgnoreConflict(1L, 2L, t, t);
        followRelationMapper.insertIgnoreConflict(2L, 1L, t, t);

        blockTransaction.createBlockAndCleanup(1L, 2L);

        assertEquals(1L, blockRelationMapper.countAnyBlock(1L, 2L), "block 行必须真的写入");
        assertEquals(0L, countFollow(1L, 2L), "1→2 必须被清理");
        assertEquals(0L, countFollow(2L, 1L), "2→1 必须被清理");
    }

    @Test
    @DisplayName("事务：★ 第二条语句失败 ⇒ 第一条 INSERT 必须被回滚（否则拉黑半吊子落库）")
    void secondStatementFailureRollsBackTheInsert() {
        OffsetDateTime t = OffsetDateTime.now();
        followRelationMapper.insertIgnoreConflict(1L, 2L, t, t);
        followRelationMapper.insertIgnoreConflict(2L, 1L, t, t);

        // 让 DELETE follow_relation 必然违反外键约束
        jdbcTemplate.execute(GUARD_DDL);
        jdbcTemplate.update("INSERT INTO test_follow_guard (guard_user, guard_target) VALUES (1, 2)");

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> blockTransaction.createBlockAndCleanup(1L, 2L),
                "第二条 DELETE 撞外键必须抛异常");

        assertEquals(0L, blockRelationMapper.countAnyBlock(1L, 2L),
                "★ block 行必须被回滚。它若残留，说明 @Transactional 静默失效（auto-commit）—— "
                        + "那样用户会处在一个「拉黑已建、关注没清」的中间态，而 Follow 的第一道闸门"
                        + "正是 ExistsAnyBlock。异常类型：" + thrown.getClass().getName());

        assertEquals(1L, countFollow(1L, 2L), "follow 行本来就该在（DELETE 被拒绝）");
        assertEquals(1L, countFollow(2L, 1L));
    }

    @Test
    @DisplayName("事务：★ 重复拉黑仍成功，且第二次照样执行 follow 清理（Go 不在「冲突就不执行」分支里）")
    void repeatedBlockStillCleansUpFollow() {
        OffsetDateTime t = OffsetDateTime.now();
        blockRelationMapper.insertIgnoreConflict(1L, 2L, t, t);
        followRelationMapper.insertIgnoreConflict(1L, 2L, t, t);
        followRelationMapper.insertIgnoreConflict(2L, 1L, t, t);

        blockTransaction.createBlockAndCleanup(1L, 2L);

        assertEquals(1L, blockRelationMapper.countAnyBlock(1L, 2L), "block 幂等，不新增第二行");
        assertEquals(0L, countFollow(1L, 2L), "★ 关键：INSERT 冲突(0 行)后 DELETE 仍必须执行");
        assertEquals(0L, countFollow(2L, 1L));
    }

    // ==================================================================
    // 5. 防回归断言（AGENTS.md §3.3：self-invocation 会让事务静默失效）
    // ==================================================================

    @Test
    @DisplayName("防回归：FollowDomainService 上不得声明 @Transactional（会被同类调用 ⇒ 事务静默失效）")
    void domainServiceMustNotDeclareTransactional() {
        List<String> declared = Arrays.stream(FollowDomainService.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(org.springframework.transaction.annotation.Transactional.class))
                .map(java.lang.reflect.Method::getName)
                .collect(Collectors.toList());

        assertTrue(declared.isEmpty(),
                "FollowDomainService 上不得声明 @Transactional 方法：多步写事务必须放在独立 Bean "
                        + "FollowBlockTransactionService 上，否则 self-invocation 不经代理、事务静默失效。"
                        + "当前命中：" + declared);
    }

    @Test
    @DisplayName("防回归：唯一的多步写事务方法必须挂在独立 Bean FollowBlockTransactionService 上")
    void atomicServiceMustOwnTheTransactionalMethod() {
        Set<String> expected = Set.of("createBlockAndCleanup");
        Set<String> actual = Arrays.stream(FollowBlockTransactionService.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(org.springframework.transaction.annotation.Transactional.class))
                .map(java.lang.reflect.Method::getName)
                .collect(Collectors.toSet());

        assertEquals(expected, actual,
                "FollowBlockTransactionService 必须持有且仅持有 createBlockAndCleanup 这一个 @Transactional 方法");
    }

    @Test
    @DisplayName("防回归：MybatisPlusOrderingBridgeAutoConfiguration 必须在 AutoConfiguration.imports 里注册")
    void mybatisOrderingBridgeMustBeRegistered() throws Exception {
        String imports = new String(getClass().getResourceAsStream(
                "/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")
                .readAllBytes());
        assertTrue(imports.contains(
                        "com.rag.follow.follow.rpc.config.MybatisPlusOrderingBridgeAutoConfiguration"),
                "排序桥未注册 ⇒ Boot 4 下 MyBatis-Plus 早于 DataSource 求值 ⇒ 整个 MP 自动配置不生效 ⇒ "
                        + "mapper 报 sqlSessionFactory 缺失。contextLoadsWithBothMapperBeans 是它的运行时防线。");
    }

    // ==================================================================
    // helpers
    // ==================================================================

    private long countFollow(long userId, long targetId) {
        Long n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM follow_relation WHERE user_id=? AND target_id=?",
                Long.class, userId, targetId);
        return n == null ? 0L : n;
    }

    private static void assertThatDesc(List<Long> expected, List<Long> actual) {
        assertEquals(expected, actual);
    }
}