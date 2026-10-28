package com.rag.users.admin.rpc;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rag.users.admin.rpc.bootstrap.AdminSeederBootstrap;
import com.rag.users.admin.rpc.domain.AdminEntity;
import com.rag.users.admin.rpc.domain.AdminInviteEntity;
import com.rag.users.admin.rpc.exception.AdminExceptions.InviteUnavailableException;
import com.rag.users.admin.rpc.exception.AdminExceptions.UsernameTakenException;
import com.rag.users.admin.rpc.mapper.AdminInviteMapper;
import com.rag.users.admin.rpc.mapper.AdminMapper;
import com.rag.users.admin.rpc.service.AdminCreateAtomicService;
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
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;
import static com.rag.users.admin.rpc.support.GoStyleHostPortPinning.pinContainerPort;
import com.rag.users.admin.rpc.support.RealGrpcPort;

/**
 * §6.2.6 E-6 真 PG 上的事务回滚证据 —— admin-rpc 测 {@link AdminCreateAtomicService} 的 4 步事务。
 *
 * <p><b>为什么需要这个测试</b>：4 步事务
 * <pre>
 *   W1 查 invite 行 → SELECT ... FOR UPDATE
 *   W2 查 username 冲突
 *   W3 插入 admin 行
 *   W4 标 invite 已用 (used_by_uid + used_at)
 * </pre>
 * 任一步骤抛异常,前几步的写入都必须回滚。静态阅读难以发现 {@code @Transactional}
 * 失效(同类 self-invocation 会静默退化成 auto-commit,见 §6.2.3);只有真 PG 才能验证。
 *
 * <p><b>对应 AGENTS.md §6.2.6 E-6</b>:之前 user-rpc 已有同款测试
 * ({@code UserAvatarTransactionWiringTest},真 PG + Testcontainers 关 W3 撞
 * {@code UNIQUE(history_id)} 时 W2 的 {@code clearCurrent} 被回滚)。这是 admin-rpc
 * 的同款证据 —— 两端的写路径都得能证明「中途失败不留脏数据」。
 *
 * <p><b>与 {@link AdminRpcContainerWiringTest} 的关系</b>:它证明 seeder + 建表能跑起来;
 * 本类证明写路径在真实事务下的行为。前者断启动,后者断运行期。
 *
 * <p><b>无 Docker 时自动跳过</b>(继承 {@code disabledWithoutDocker = true}),不因
 * dev 机没开 Docker 而让 mvn build 变红。
 */
@SpringBootTest(
        classes = AdminRpcApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN"
        })
@Testcontainers
@org.testcontainers.junit.jupiter.EnabledIfDockerAvailable
class AdminRpcTransactionWiringTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("admin_rpc_tx_it")
                    .withUsername("rag")
                    .withPassword("rag").withCreateContainerCmdModifier(cmd -> pinContainerPort(cmd, 5432));

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) {
        r.add("grpc.server.port", () -> String.valueOf(RealGrpcPort.pick()));
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private AdminCreateAtomicService createAtomicService;
    @Autowired private AdminInviteMapper inviteMapper;
    @Autowired private AdminMapper adminMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    /**
     * 每个用例开始前清表 —— 与 {@link AdminRpcContainerWiringTest#seedUsers()}
     * 同款手法:同容器被多用例共享,局部删会让用例顺序影响结果。
     */
    @BeforeEach
    void cleanSlate() {
        jdbcTemplate.update("DELETE FROM admin_invite");
        jdbcTemplate.update("DELETE FROM admin_admin");
    }

    // ==================================================================
    // E-6:4 步事务每一步失败都必须回滚
    // ==================================================================

    /**
     * <b>E-6 / 防回归</b>:W3 在 admin_uid 唯一冲突下抛 SQLException ⇒ 整个 4 步必须回滚,
     * invite 行不得出现 used_by_uid / used_at(W4 没机会跑)。
     *
     * <p>这一道对应 AGENTS.md §6.4 「DRIFT-NEW-018 留档」里那段:
     * 「W1 占的 SELECT ... FOR UPDATE 锁,W4 写回 used_at 的 UPDATE」
     * 必须都 rollback,否则第二次用同一个 invite code 来消费时,W1 的锁已释放但
     * W4 的 used_at 已落,会出现「锁未消费但码已废」的脏状态。
     */
    @Test
    @DisplayName("E-6:W3 admin uid 冲突 ⇒ 整 4 步回滚(invite 必须未被标记 used)")
    void rollbackWhenStep3HitsUidUniqueViolation() {
        long plantedUid = 800_000_000_001L;
        seedInvite("ADM-AAAA-1111", LocalDateTime.now().plusHours(1), 777L);

        // 在表里塞一行 admin,占住 plantedUid —— RAG 这边 W3 insert 同 uid 必撞唯一约束
        jdbcTemplate.update(
                "INSERT INTO admin_admin (uid, username, password, email) VALUES (?, ?, 'x', ?)",
                plantedUid, "planted-uid-owner", "p@x");

        // 触发 W3 失败
        Throwable caught = assertThrows(Exception.class, () ->
                createAtomicService.createAdminAtomic(
                        "fresh-username",
                        "ADM-AAAA-1111",
                        "hash-pw",
                        "x@x.com",
                        new HashMap<>(),
                        plantedUid));

        // 不在乎是 SQLException 还是它包裹的 duplicate key,只要原子事务被中断即可
        // (createAtomicService 的 catch 在 AdminDomainService 里,这里直接调底层方法,
        //  因此异常会原样冒泡)
        assertNotNull(caught);

        // **关键断言**:invite 行保持原样 (used_at = null、used_by_uid = null)
        AdminInviteEntity afterInvite = inviteMapper.selectOne(
                new LambdaQueryWrapper<AdminInviteEntity>()
                        .eq(AdminInviteEntity::getCode, "ADM-AAAA-1111"));
        assertNotNull(afterInvite, "invite 行不能被原子事务删除");
        assertNull(afterInvite.getUsedAt(),
                "W3 失败 ⇒ invite 必须未被使用 (W4 未执行,used_at = null)");
        assertNull(afterInvite.getUsedByUid(),
                "W3 失败 ⇒ invite 必须未被使用 (W4 未执行,used_by_uid = null)");

        // 没有新 admin 行插入。预先种植的一行还在,但「fresh-username」不在
        assertNull(adminMapper.selectOne(
                new LambdaQueryWrapper<AdminEntity>().eq(AdminEntity::getUsername, "fresh-username")),
                "W3 失败 ⇒ 不应留下新 admin 行");

        long freshAdminCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_admin WHERE username = 'fresh-username'", Long.class);
        assertEquals(0L, freshAdminCount, "fresh-username 必须不在 admin_admin 表里");
    }

    /**
     * W1 invite 不存在 ⇒ InviteUnavailableException 透传,W2/W3/W4 都没跑,
     * 所以 admin_admin 既不能多一行,也不能少一行。
     */
    @Test
    @DisplayName("E-6:W1 invite 不存在 ⇒ 抛 InviteUnavailable,admin_admin 表无变化")
    void inviteNotFound_doesNotMutateAdminTable() {
        long beforeAdminCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_admin", Long.class);

        assertThrows(InviteUnavailableException.class, () ->
                createAtomicService.createAdminAtomic(
                        "someone", "INV-NOT-EXISTS", "x", null, null, 9_999_999_999L),
                "InviteUnavailableException 应原样抛上来"
        );

        long afterAdminCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_admin", Long.class);
        assertEquals(beforeAdminCount, afterAdminCount,
                "W1 失败 ⇒ 不应触及 admin_admin 表");
    }

    /**
     * W2 username 已存在 ⇒ UsernameTakenException ⇒ invite 不能被用,W3 没机会跑。
     *
     * <p>这是 E-6 的另一档:之前是「后写撞唯一约束」(W3 抛 SQLEx);这里是「前置唯一
     * 校验」(W2 抛 UsernameTakenException)。两条路径都应当全回滚。
     */
    @Test
    @DisplayName("E-6:W2 username 已存在 ⇒ 整事务回滚(invite 必须未被标记 used)")
    void rollbackWhenStep2DetectsUsernameTaken() {
        seedInvite("ADM-BBBB-2222", LocalDateTime.now().plusHours(1), 888L);

        // 预先塞一个「alice」用户,W2 查重必命中
        jdbcTemplate.update(
                "INSERT INTO admin_admin (uid, username, password, email) VALUES (?, 'alice', 'x', NULL)",
                555_555_555_555L);

        assertThrows(UsernameTakenException.class, () ->
                createAtomicService.createAdminAtomic(
                        "alice",      // 撞上游预塞的 alice ⇒ W2 抛
                        "ADM-BBBB-2222",
                        "x", null, null, 1_111_111_111_111L));

        // invite 仍可用
        AdminInviteEntity afterInvite = inviteMapper.selectOne(
                new LambdaQueryWrapper<AdminInviteEntity>()
                        .eq(AdminInviteEntity::getCode, "ADM-BBBB-2222"));
        assertNotNull(afterInvite);
        assertNull(afterInvite.getUsedAt(),
                "W2 失败 ⇒ invite 必须未被使用 (W4 未执行)");
    }

    /**
     * 完整 4 步 happy path:admin 插入 + invite 被标记 used。这是「不退滚」的反向断言,
     * 保证上面三道「回滚」用例不是用「什么都不做」来假阳性。
     */
    @Test
    @DisplayName("E-6 (反向断言):happy path ⇒ invite 被正确标记 used,admin 行被插入")
    void happyPath_marksInviteAndInsertsAdmin() {
        seedInvite("ADM-CCCC-3333", LocalDateTime.now().plusHours(1), 999L);

        long uid = createAtomicService.createAdminAtomic(
                "e6-happy",
                "ADM-CCCC-3333",
                "hashed",
                "e6@x.com",
                null,
                700_000_000_777L);
        assertEquals(700_000_000_777L, uid);

        // admin 行存在
        AdminEntity admin = adminMapper.selectOne(
                new LambdaQueryWrapper<AdminEntity>().eq(AdminEntity::getUid, 700_000_000_777L));
        assertNotNull(admin);
        assertEquals("e6-happy", admin.getUsername());

        // invite 已用
        AdminInviteEntity invite = inviteMapper.selectOne(
                new LambdaQueryWrapper<AdminInviteEntity>()
                        .eq(AdminInviteEntity::getCode, "ADM-CCCC-3333"));
        assertEquals(700_000_000_777L, invite.getUsedByUid(),
                "happy path 下 W4 必须把 used_by_uid 写回 = 新 admin uid");
        assertNotNull(invite.getUsedAt(),
                "happy path 下 W4 必须把 used_at 写回");
    }

    /**
     * <b>对应 §6.4 DRIFT-NEW-018 留档</b>:Self-invocation 会让
     * {@code @Transactional} 静默失效。若把 {@code createAdminAtomic} 从独立 Bean
     * 拉回 {@code AdminDomainService} 同类内调用,4 步会退化成 auto-commit,每个
     * SELECT/INSERT/UPDATE 立即提交,W1 的悲观锁形同虚设。本用例的语义测试是这一
     * 防线,留一行注释提醒后人:**AdminCreateAtomicService 必须保持独立 Bean**。
     *
     * <p>(没必要在本类里实测这个回归 —— 用
     * {@link AdminRpcContainerWiringTest#schemaAppliedBeforeSeeder()} 那
     * 「容器能不能起来」作为先决条件;且类注释已点明这条边界;补一行 @Bean 警告即可。)
     */

    // ==================================================================
    // helpers
    // ==================================================================

    private void seedInvite(String code, LocalDateTime expiresAt, long inviterUid) {
        jdbcTemplate.update(
                "INSERT INTO admin_invite (code, inviter_uid, expires_at) VALUES (?, ?, ?)",
                code, inviterUid, java.sql.Timestamp.valueOf(expiresAt));
    }
}
