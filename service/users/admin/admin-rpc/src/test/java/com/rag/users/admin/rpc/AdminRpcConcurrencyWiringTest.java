package com.rag.users.admin.rpc;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static com.rag.users.admin.rpc.support.GoStyleHostPortPinning.pinContainerPort;

/**
 * §6.2.6 E-5 真 PG 上的并发路径证据。
 *
 * <p><b>E-5 的本意</b>(AGENTS.md §6.2.6):
 * 「同用户名并发注册、并发改/删/封禁,全部未测。Go 侧靠 GORM 事务 + 行锁,
 *  RAG 侧靠 {@code @Transactional} + 可能的 {@code FOR UPDATE}」—— 这里的
 * 「可能的」三字是 AGENTS.md 留过的不确定性。本类把它坐实:
 *
 * <p><b>对策</b>:两个并行场景。
 * <ol>
 *   <li><b>同 invite code 并发消费</b>:W1 的悲观锁 {@code SELECT ... FOR UPDATE}
 *       是这个测试的关键。N 个线程同时进 W1,
 *       <b>应当</b>看到 1 个 OK + N-1 个 InviteUnavailableException。
 *       若 W1 没有锁,理论上 N 个都能读到「未使用」,全部走完 W4 都认为自己是合法消费者,
 *       最终 invite.used_by_uid 指向最后一个 commit 的人 —— 邀请码一次性约束失效。</li>
 *   <li><b>同 username 并发注册</b>:不依赖悲观锁,依赖 username 列的 UNIQUE 约束兜底。
 *       N 个线程同时走过 W2 的「username 冲突检测」(都不会找到对方),全部进 W3 INSERT,
 *       最后一个被 UNIQUE 撞飞。所有 N 个调用应当见到:要么成功、要么 UsernameTaken /
 *       UniqueViolation —— 最终恰好 1 行。</li>
 * </ol>
 *
 * <p><b>Go 一致性</b>:对比实现 (Go 真源) 在 admin.rpc 同样有 invite 一次性约束,
 * 并发场景下行为应当相同。本类不直接对比 Go / RAG 的全量错误码分布 —— 那是
 * §6.2.6 E-2 端到端对拍的范围;这里只验「RAG 单侧的并发约束不丢」(单测足以
 * 锁住「邀请码一次性」「用户名唯一」这两条业务不变量)。
 *
 * <p><b>无 Docker 时自动跳过</b>(沿用 {@code @EnabledIfDockerAvailable},与
 * {@link AdminRpcTransactionWiringTest} 同款)。
 */
@SpringBootTest(
        classes = AdminRpcApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "grpc.server.port=0",
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN"
        })
@Testcontainers
@org.testcontainers.junit.jupiter.EnabledIfDockerAvailable
class AdminRpcConcurrencyWiringTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("admin_rpc_conc_it")
                    .withUsername("rag")
                    .withPassword("rag").withCreateContainerCmdModifier(cmd -> pinContainerPort(cmd, 5432));

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private AdminCreateAtomicService createAtomicService;
    @Autowired private AdminInviteMapper inviteMapper;
    @Autowired private AdminMapper adminMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanSlate() {
        jdbcTemplate.update("DELETE FROM admin_invite");
        jdbcTemplate.update("DELETE FROM admin_admin");
    }

    // ==================================================================
    // E-5 / 场景 1:同一 invite code 并发消费 ⇒ 恰好 1 个成功
    // ==================================================================

    /**
     * <b>E-5 验收点 1</b>:同一 invite code 被 N 个并发线程抢,
     * 由于 W1 的 {@code SELECT ... FOR UPDATE} 悲观锁,实际只会有一个线程
     * 看到「未使用」+ 「未过期」,其余线程或遇到 invite 已被标 used(后到锁场景),
     * 或其事务在 W1 锁等待后看到 invite 已被别人消费 ⇒ 抛 InviteUnavailable。
     *
     * <p><b>反例</b>:若 RAG 不锁,所有 N 线程都能进入 W4,最终 invite.used_by_uid
     * 指向最后 commit 的那个 = 邀请码一次性约束失效。本用例就此失效动作的失败点
     * 钉死在 PG。
     */
    @Test
    @DisplayName("E-5:N 线程并发消费同 invite code ⇒ 恰好 1 个 OK + N-1 InviteUnavailable")
    void parallelInviteCode_usesAreSerializedToOneSuccess() throws Exception {
        final int N = 8;
        final String code = "ADM-CONC-1111";
        seedInvite(code, LocalDateTime.now().plusHours(1), 1L);

        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(N);

        AtomicInteger success = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        AtomicInteger other = new AtomicInteger();

        List<Future<String>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < N; i++) {
            final int idx = i;
            final long uid = 800_000_000_000L + idx;
            Callable<String> task = () -> {
                try {
                    // 都先在 startGate 等一齐跑
                    startGate.await();
                    Long got = createAtomicService.createAdminAtomic(
                            "u-" + idx,
                            code,
                            "hash",
                            null,
                            new HashMap<>(),
                            uid);
                    success.incrementAndGet();
                    return "OK:" + got;
                } catch (InviteUnavailableException e) {
                    rejected.incrementAndGet();
                    return "REJ:" + e.getMessage();
                } catch (Throwable t) {
                    other.incrementAndGet();
                    return "OTH:" + t.getClass().getSimpleName() + ":" + t.getMessage();
                }
            };
            futures.add(pool.submit(task));
        }

        long t0 = System.nanoTime();
        startGate.countDown();
        for (Future<String> f : futures) f.get(30, TimeUnit.SECONDS);
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
        pool.shutdown();

        assertEquals(1, success.get(),
                "同 invite code 并发消费:成功次数必须 = 1,实际=" + success.get());
        assertEquals(N - 1, rejected.get(),
                "同 invite code 并发消费:被拒次数必须 = N-1,实际=" + rejected.get());
        assertEquals(0, other.get(),
                "其他异常数必须 = 0:实际=" + other.get());

        // 物理证据:invite 行 used_by_uid 必须唯一指向一个值,used_at 必须非空
        AdminInviteEntity invite = inviteMapper.selectOne(
                new LambdaQueryWrapper<AdminInviteEntity>().eq(AdminInviteEntity::getCode, code));
        assertNotNull(invite);
        assertNotNull(invite.getUsedAt(), "W4 必须写回 used_at");
        assertNotNull(invite.getUsedByUid(), "W4 必须写回 used_by_uid");

        // admin 表里有且只有 1 个新建用户(已邀请的那个);其余 7 个线程没机会插
        Long createdCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_admin WHERE username LIKE 'u-%'", Long.class);
        assertEquals(1L, createdCount.longValue(),
                "admin 表里应当只有 1 行 username LIKE 'u-%',实际=" + createdCount);
        // sanity:那个 u-X 的 uid 就是 invite.used_by_uid
        AdminEntity winner = adminMapper.selectOne(
                new LambdaQueryWrapper<AdminEntity>().eq(AdminEntity::getUid, invite.getUsedByUid()));
        assertNotNull(winner);
        // 谁抢到锁是 OS 调度的事,**断言只验证"入参 username 是 u-0..u-7 之一 + invite.used_by_uid
        // 与 winner.uid 一致"**。谁赢跟测试无关,这是 E-5 的本意:验证约束本身,
        // 而不是线程调度。
        assertTrue(winner.getUsername().matches("u-[0-9]+"),
                "winner.username 必须是 u-{N} 形态,实际=" + winner.getUsername());

        System.out.printf("[E-5 invite] N=%d success=%d rejected=%d elapsed=%dms%n",
                N, success.get(), rejected.get(), elapsedMs);
    }

    // ==================================================================
    // E-5 / 场景 2:同 username 并发注册 ⇒ 唯一约束兜底,最终 1 行
    // ==================================================================

    /**
     * <b>E-5 验收点 2</b>:不依赖悲观锁,依赖 {@code admin_admin.username} 的 UNIQUE 约束。
     * N 个线程给同一 username 抢注,理论上:
     * <ul>
     *   <li>第一个 INSERT 进入 ⇒ OK
     *   <li>其余 INSERT 撞 UNIQUE ⇒ 抛(被 {@link AdminCreateAtomicService}
     *       原样抛上来,不要看 caller 的 try/catch 是 ALREADY_EXISTS — 这里只验
     *       写路径能撑住并发,不管最终报错是 UsernameTaken 还是底层 SQLException)
     * </ul>
     * 最终 admin 表里 username=xxx 的行应当<b>恰好 1 行**。
     */
    @Test
    @DisplayName("E-5:N 线程并发注册同 username ⇒ 唯一约束兜底,最终恰好 1 行")
    void parallelSameUsername_insertsExactlyOneRow() throws Exception {
        final int N = 8;
        final String u = "race-user";

        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(N);

        // 每个线程需要独占的一个 invite(不然 invite 一次性约束会先于 username 唯一性失效,
        // 测不出 username 这条路径)
        for (int i = 0; i < N; i++) {
            seedInvite("ADM-RACE-" + i, LocalDateTime.now().plusHours(1), 999L);
        }

        AtomicInteger success = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();

        List<Future<String>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < N; i++) {
            final int idx = i;
            final long uid = 700_000_000_000L + idx;
            Callable<String> task = () -> {
                try {
                    startGate.await();
                    createAtomicService.createAdminAtomic(
                            u,
                            "ADM-RACE-" + idx,
                            "x",
                            null,
                            null,
                            uid);
                    success.incrementAndGet();
                    return "OK";
                } catch (Throwable t) {
                    failed.incrementAndGet();
                    return "FAIL:" + t.getClass().getSimpleName();
                }
            };
            futures.add(pool.submit(task));
        }

        startGate.countDown();
        for (Future<String> f : futures) f.get(30, TimeUnit.SECONDS);
        pool.shutdown();

        assertEquals(1, success.get(), "恰好 1 个 INSERT 成功");
        assertEquals(N - 1, failed.get(), "其余 N-1 个必须撞 unique 或 username 已被占");

        Long adminCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_admin WHERE username = ?", Long.class, u);
        assertEquals(1L, adminCount,
                "admin 表里 username=" + u + " 必须恰好 1 行,实际=" + adminCount);
    }

    /**
     * 微小细节:两个 N 个线程并发,W1 锁会序列化,8 个线程应当会有 1 个完整走完 4 步,
     * 其余 7 个在 W1 抢锁后看到 used_in != null 抛出。这条的边界在 CommitStrategy 与
     * PG 锁释放协议上,留一行注释提醒后人:W1 的锁<b>只在事务生命周期内</b>持有,
     * 长事务下其它线程会饥饿,这与 Go GORM {@code .Clauses(clause.Locking{Strength: "FOR UPDATE"})}
     * 实现路径上的差异不在本类的考察范围内(本类只验「锁工作或不工作」,不论
     * 锁开销语义)。
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
