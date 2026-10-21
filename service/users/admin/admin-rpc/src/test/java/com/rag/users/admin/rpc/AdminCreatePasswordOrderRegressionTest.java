package com.rag.users.admin.rpc;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rag.users.admin.rpc.domain.AdminEntity;
import com.rag.users.admin.rpc.domain.AdminInviteEntity;
import com.rag.users.admin.rpc.exception.AdminExceptions.InviteUnavailableException;
import com.rag.users.admin.rpc.exception.AdminExceptions.UsernameTakenException;
import com.rag.users.admin.rpc.mapper.AdminInviteMapper;
import com.rag.users.admin.rpc.mapper.AdminMapper;
import com.rag.users.admin.rpc.service.AdminCreateAtomicService;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R5 防回归测试 —— {@code admin.create} 里 bcrypt 哈希与前置业务检查的<b>先后顺序</b>。
 *
 * <p><b>根因</b>：修复前 {@code AdminDomainService.createAdmin} 在<b>事务外</b>先做
 * {@code PasswordUtil.encrypt}。bcrypt 有 72 字节上限，两端都会拒；分歧<b>纯粹来自步骤顺序</b>：
 * <pre>
 *   Go  （createadminlogic.go 事务闭包内）
 *       FindAdminInviteByCodeForUpdate → not found/过期 → PermissionDenied(1016)
 *       FindOneAdminByUsernameTx        → 命中         → AlreadyExists(1001)
 *       cryptx.PasswordEncrypt          → &gt;72 字节    → err(→ Internal 1015)
 *   RAG （修复前）
 *       PasswordUtil.encrypt            → &gt;72 字节    → INTERNAL(5001)   ★ 抢跑
 *       事务：查 invite / 查 username / insert
 * </pre>
 * 于是「超长密码 + 本该被前置检查拦下的输入」这一交集上，RAG 报 5001 而 Go 报 1016 / 1001。
 *
 * <p><b>实测证据</b>（{@code tools/difftest/r5-order-probe.py}，全新 cluster，两端实测）：
 * <pre>
 *   A 有效邀请码 + 超长密码 + 新用户名        Go=5001 / 修复前 RAG=5001  （本就一致）
 *   B 有效邀请码 + 超长密码 + 用户名已存在     Go=1001 / 修复前 RAG=5001  ★
 *   C 无效邀请码 + 超长密码                   Go=1016 / 修复前 RAG=5001  ★
 * </pre>
 *
 * <p><b>为什么必须是真 PG 而不是 Mockito</b>（AGENTS.md §3.3）：
 * 本类断言的是「bcrypt 抛错时，事务走到了哪一步」——
 * 需要真实 SQL 命中真实 invite 行与真实 username 唯一约束，才谈得上顺序。
 * mock 数据层的单测会把 {@code createAdminAtomic} 整个替换掉，
 * 无论里面怎么调 PasswordUtil 都是绿的。
 *
 * <p><b>无 Docker 时自动跳过</b>（{@code disabledWithoutDocker = true}）。
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
class AdminCreatePasswordOrderRegressionTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("admin_rpc_r5_it")
                    .withUsername("rag")
                    .withPassword("rag");

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

    /** bcrypt 的 72 字节上限 —— 超出会让 PasswordUtil.encrypt 抛 IllegalArgumentException。 */
    private static final String OVERSIZED_PASSWORD = "P".repeat(5000);

    @BeforeEach
    void cleanSlate() {
        jdbcTemplate.update("DELETE FROM admin_invite");
        jdbcTemplate.update("DELETE FROM admin_admin");
    }

    private void seedInvite(String code, LocalDateTime expiresAt) {
        // inviter_uid 是 NOT NULL —— 建邀请码时必须带上操作人
        jdbcTemplate.update(
                "INSERT INTO admin_invite (code, inviter_uid, expires_at) VALUES (?, ?, ?)",
                code, 1L, expiresAt);
    }

    private void seedAdmin(String username) {
        jdbcTemplate.update(
                "INSERT INTO admin_admin (uid, username, password, email) VALUES (?, ?, 'x', ?)",
                900_000_000_001L, username, username + "@x.com");
    }

    private Long createWithPlaintext(String username, String inviteCode, String password) {
        return createAtomicService.createAdminAtomic(
                username, inviteCode, password, null, new HashMap<>(), 900_000_000_999L, true);
    }

    // ==================================================================
    // B 支：有效邀请码 + 超长密码 + 用户名已存在
    //     Go 顺序 username 查重在 bcrypt 之前 ⇒ 应抛 UsernameTakenException(→1001)
    // ==================================================================

    @Test
    @DisplayName("R5-B:有效邀请码+超长密码+用户名已存在 ⇒ 抛 UsernameTakenException（对齐 Go 的 1001）")
    void oversizedPasswordWithExistingUsernameYieldsUsernameTaken() {
        seedInvite("ADM-R5-B", LocalDateTime.now().plusHours(1));
        seedAdmin("already-taken");

        Throwable caught = assertThrows(UsernameTakenException.class, () ->
                createWithPlaintext("already-taken", "ADM-R5-B", OVERSIZED_PASSWORD),
                "Go 侧 PasswordEncrypt 排在 FindOneAdminByUsernameTx 之后 ⇒ 查重先命中，"
                        + "不应看到 bcrypt 的 72 字节上限");

        assertTrue(caught.getMessage().contains("already-taken"), caught.getMessage());
    }

    // ==================================================================
    // C 支：无效邀请码 + 超长密码
    //     Go 顺序 invite 校验在 bcrypt 之前 ⇒ 应抛 InviteUnavailableException(→1016)
    // ==================================================================

    @Test
    @DisplayName("R5-C:无效邀请码+超长密码 ⇒ 抛 InviteUnavailableException（对齐 Go 的 1016）")
    void oversizedPasswordWithUnknownInviteYieldsInviteUnavailable() {
        Throwable caught = assertThrows(InviteUnavailableException.class, () ->
                createWithPlaintext("brand-new-user", "ADM-R5-NOT-EXIST", OVERSIZED_PASSWORD),
                "Go 侧 FindAdminInviteByCodeForUpdate 排在 PasswordEncrypt 之前 ⇒ "
                        + "应先报邀请码错误，不应看到 bcrypt 的 72 字节上限");

        assertTrue(caught.getMessage().contains("ADM-R5-NOT-EXIST"), caught.getMessage());
    }

    @Test
    @DisplayName("R5-C2:已用过的邀请码+超长密码 ⇒ 抛 InviteUnavailableException（对齐 Go 的 1016）")
    void oversizedPasswordWithUsedInviteYieldsInviteUnavailable() {
        seedInvite("ADM-R5-USED", LocalDateTime.now().plusHours(1));
        jdbcTemplate.update("UPDATE admin_invite SET used_at = now() WHERE code = 'ADM-R5-USED'");

        assertThrows(InviteUnavailableException.class, () ->
                        createWithPlaintext("brand-new-user-2", "ADM-R5-USED", OVERSIZED_PASSWORD),
                "邀请码已用 ⇒ 应先报邀请码错误（Go 同一顺序）");
    }

    @Test
    @DisplayName("R5-C3:已过期邀请码+超长密码 ⇒ 抛 InviteUnavailableException（对齐 Go 的 1016）")
    void oversizedPasswordWithExpiredInviteYieldsInviteUnavailable() {
        seedInvite("ADM-R5-EXPIRED", LocalDateTime.now().minusHours(1));

        assertThrows(InviteUnavailableException.class, () ->
                        createWithPlaintext("brand-new-user-3", "ADM-R5-EXPIRED", OVERSIZED_PASSWORD),
                "邀请码过期 ⇒ 应先报邀请码错误（Go 同一顺序）");
    }

    // ==================================================================
    // A 支：有效邀请码 + 超长密码 + 新用户名
    //     前置检查都不命中 ⇒ 两端都应落到 bcrypt 的 72 字节上限（Go=5001，RAG=5001）
    //     这条是**反向守卫**：防止「无脑把 bcrypt 挪到最后」把 A 支也改坏
    // ==================================================================

    @Test
    @DisplayName("R5-A:有效邀请码+超长密码+新用户名 ⇒ 落到 bcrypt 上限（Go 同样是 5001，不是一致性回退）")
    void oversizedPasswordWithFreshUsernameFallsThroughToBcryptLimit() {
        seedInvite("ADM-R5-A", LocalDateTime.now().plusHours(1));

        Throwable caught = assertThrows(IllegalArgumentException.class, () ->
                        createWithPlaintext("fresh-r5-user", "ADM-R5-A", OVERSIZED_PASSWORD),
                "前置检查都通过时，应当由 bcrypt 的 72 字节上限收口 —— "
                        + "Go 侧同样是这一步报错，最终都是 Internal/5001");

        assertTrue(caught.getMessage().contains("72"), caught.getMessage());
    }

    // ==================================================================
    // 正常路径不许被 R5 修复带坏
    // ==================================================================

    @Test
    @DisplayName("R5-正常路径:有效邀请码+普通密码 ⇒ 成功建号且密码列存的是 bcrypt hash（不是明文）")
    void normalPathStillHashesPassword() {
        seedInvite("ADM-R5-OK", LocalDateTime.now().plusHours(1));

        long uid = createWithPlaintext("normal-r5-user", "ADM-R5-OK", "pw123456");
        assertEquals(900_000_000_999L, uid);

        AdminEntity admin = adminMapper.selectOne(
                new LambdaQueryWrapper<AdminEntity>().eq(AdminEntity::getUid, uid));
        assertNotNull(admin, "admin 行应存在");
        assertEquals("normal-r5-user", admin.getUsername());

        // 关键：传的是明文，存进去的必须是 bcrypt hash
        assertNotEquals("pw123456", admin.getPassword(), "密码列不得存明文");
        assertTrue(admin.getPassword().startsWith("$2a$"),
                "应落 bcrypt hash，实际=" + admin.getPassword());

        // 邀请码必须被标记已用
        AdminInviteEntity invite = inviteMapper.selectOne(
                new LambdaQueryWrapper<AdminInviteEntity>().eq(AdminInviteEntity::getCode, "ADM-R5-OK"));
        assertNotNull(invite.getUsedAt(), "invite 必须被标记 used");
        assertEquals(uid, invite.getUsedByUid());
    }

    /**
     * 反向守卫：旧的 6 参签名（传<b>已哈希</b>密码）必须仍然<b>不</b>再哈希一次。
     *
     * <p>它锁住的是「两个重载共存」这个结构本身：
     * {@link AdminCreateAtomicService} 现在有 6 参（hash 直插）与 7 参（明文，在 W2 后哈希）两条路，
     * 既有测试全走 6 参。若哪天有人把 6 参也改成无条件哈希，这些断言会先红。
     */
    @Test
    @DisplayName("R5-重载:6 参签名传已哈希密码 ⇒ 原样落库（不得二次哈希）")
    void preHashedOverloadStoresValueVerbatim() {
        seedInvite("ADM-R5-OVERLOAD", LocalDateTime.now().plusHours(1));

        String alreadyHashed = "$2a$10$abcdefghijklmnopqrstuv";
        long uid = createAtomicService.createAdminAtomic(
                "overload-r5-user", "ADM-R5-OVERLOAD", alreadyHashed,
                null, new HashMap<>(), 900_000_000_998L);

        AdminEntity admin = adminMapper.selectOne(
                new LambdaQueryWrapper<AdminEntity>().eq(AdminEntity::getUid, uid));
        assertNotNull(admin);
        assertEquals(alreadyHashed, admin.getPassword(),
                "6 参签名约定收到的就是已哈希密码，必须原样落库");
    }

    // ==================================================================
    // 端到端：AdminDomainService.createAdmin 抛出的 gRPC Status 也必须是 1001/1016
    //     （上面几条只到 sentinel 异常层；这一条锁到 AdminServiceGrpcImpl 消费后的 Status）
    // ==================================================================

    @Test
    @DisplayName("R5-Status:R5-B 场景在 DomainService 层 ⇒ gRPC 码是 ALREADY_EXISTS(1001)，不是 INTERNAL(5001)")
    void domainServiceMapsUsernameTakenBeforeBcrypt() {
        seedInvite("ADM-R5-DS", LocalDateTime.now().plusHours(1));
        seedAdmin("ds-taken-user");

        StatusRuntimeException caught = assertThrows(StatusRuntimeException.class,
                () -> domainService().createAdmin(
                        "ds-taken-user", OVERSIZED_PASSWORD, null,
                        Map.of(), "ADM-R5-DS"));

        assertEquals(Status.Code.ALREADY_EXISTS, caught.getStatus().getCode(),
                "Go 侧此处是 codes.AlreadyExists（errmsg 1001）；"
                        + "若变成 INTERNAL 说明 bcrypt 又抢跑到 username 查重之前了");
    }

    @Test
    @DisplayName("R5-Status:R5-C 场景在 DomainService 层 ⇒ gRPC 码是 PERMISSION_DENIED(1016)，不是 INTERNAL(5001)")
    void domainServiceMapsInviteWrongBeforeBcrypt() {
        StatusRuntimeException caught = assertThrows(StatusRuntimeException.class,
                () -> domainService().createAdmin(
                        "ds-fresh-user", OVERSIZED_PASSWORD, null,
                        Map.of(), "ADM-R5-NOPE"));

        assertEquals(Status.Code.PERMISSION_DENIED, caught.getStatus().getCode(),
                "Go 侧此处是 codes.PermissionDenied（errmsg 1016）；"
                        + "若变成 INTERNAL 说明 bcrypt 又抢跑到邀请码校验之前了");
    }

    @Autowired
    private com.rag.users.admin.rpc.service.AdminDomainService adminDomainService;

    private com.rag.users.admin.rpc.service.AdminDomainService domainService() {
        return adminDomainService;
    }
}