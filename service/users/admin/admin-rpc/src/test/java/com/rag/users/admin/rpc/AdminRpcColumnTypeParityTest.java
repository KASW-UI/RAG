package com.rag.users.admin.rpc;

import com.rag.users.admin.rpc.domain.AdminInviteEntity;
import com.rag.users.admin.rpc.mapper.AdminInviteMapper;
import com.rag.users.admin.rpc.service.AdminDomainService;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
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

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 🔴 DRIFT-ARPC-004 ①② 守门测试 —— admin 域 DDL 列类型与 Go 真源同构。
 *
 * <h2>本类补的是哪一层证据</h2>
 *
 * <p>2026-10-23 修复了两处 DDL 漂移（用户 22:0x 拍板「DDL 与实体一起改」）：
 * <ul>
 *   <li><b>① {@code admin_admin.extra_info}</b>：{@code JSONB → TEXT}
 *       （Go 的 {@code serializer:json} 按 String 建列，见 {@code data_model.go:11}）</li>
 *   <li><b>② 6 个时间列</b>：{@code timestamp → timestamptz}
 *       （Go 的 {@code time.Time} 无 type tag ⇒ {@code postgres.go:251-256} 生成 timestamptz）</li>
 * </ul>
 *
 * <h2>为什么必须真 PG（不能 mock，也不能只查 DDL 脚本文本）</h2>
 *
 * <p>要证伪的是三件只有真进程能证的事：
 * <ol>
 *   <li><b>列类型真的变了</b> —— 脚本改了不等于库里变了；断言打在
 *       {@code information_schema.columns} 上，是库的真实状态；</li>
 *   <li><b>text 列真的收下 jsonb 会拒收的 payload</b> —— 这条走完整业务链
 *       （{@code updateSelf} → MP 生成 SQL → PG），不是单测一个 handler；</li>
 *   <li><b>跨时区的 invite 有效期判定仍然正确</b> —— 这是 ② 里<b>唯一可观察</b>的那一半。</li>
 * </ol>
 * 纯 Mockito 测数据层天然测不到以上任何一条（§3.3）。
 *
 * <h2>⚠️ 本类各条的「可证伪性」与<b>已知盲区</b>（不夸大证据强度）</h2>
 *
 * <table border="1">
 *   <tr><th>用例</th><th>能抓住什么回退</th><th>盲区</th></tr>
 *   <tr><td>{@code #adminColumnsMatchGoDdlTypes}</td>
 *       <td>DDL 改回 {@code jsonb} / {@code timestamp}</td>
 *       <td>无（查的是库的真实 schema）</td></tr>
 *   <tr><td>{@code #extraInfoAcceptsPayloadJsonbWouldReject}</td>
 *       <td>实体改回 {@code JsonbTypeHandler}、或 {@code updateSelf} 重新 CAST 成 jsonb</td>
 *       <td>无</td></tr>
 *   <tr><td>{@code #inviteExpiryJudgedOnAbsoluteInstantNotWallClock}</td>
 *       <td>列退回 {@code timestamp}（偏移量被 PG 丢弃 ⇒ 绝对时刻错位）</td>
 *       <td><b>实体类型退回 {@code LocalDateTime} 会编译不过</b>（编译期守卫，不是运行期）</td></tr>
 * </table>
 *
 * <p>⇒ 三条<b>叠加</b>覆盖：列类型由第 1 条确定性守住，
 * 跨时区语义由第 3 条在 JVM 时区 ≠ 写入时区时守住（用 {@link #pickForeignZone()} 保证偏移量非零），
 * 边界 payload 由第 2 条守住。
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
class AdminRpcColumnTypeParityTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("admin_rpc_column_parity_it")
                    .withUsername("rag")
                    .withPassword("rag");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private AdminDomainService domain;
    @Autowired private AdminInviteMapper adminInviteMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private javax.sql.DataSource dataSource;

    private static final long ADMIN_UID = 8801L;

    // ==================================================================
    // 1. 列类型：库里的真实 schema，不是脚本文本
    // ==================================================================

    @Test
    @DisplayName("✅ admin_admin.extra_info 是 text、6 个时间列是 timestamptz（对齐 Go AutoMigrate）")
    void adminColumnsMatchGoDdlTypes() {
        assertEquals("text", columnType("admin_admin", "extra_info"),
                "Go 的 data_model.go:11 用 serializer:json ⇒ GORM 按 String 建列，即 text。"
                        + "若这里是 jsonb，PG 会拒收 encoding/json 能产出的合法内容（见下一条用例）。");
        assertEquals("text", columnType("admin_admin", "username"),
                "GORM 给 Go 的 string 字段建的是 text/无上限，RAG 侧已统一放宽为 TEXT（E-3/R3 自愈）");

        for (String col : new String[]{"create_time", "update_time"}) {
            assertEquals("timestamp with time zone", columnType("admin_admin", col),
                    "Go 的 data_model.go:12-13 是 time.Time 无 type tag ⇒ GORM 生成 timestamptz");
        }
        for (String col : new String[]{"expires_at", "used_at", "create_time", "update_time"}) {
            assertEquals("timestamp with time zone", columnType("admin_invite", col),
                    "Go 的 data_model.go:24,26,27,28 是 time.Time 无 type tag ⇒ GORM 生成 timestamptz");
        }
    }

    // ==================================================================
    // 1b. 脚本重放的幂等性（**本轮自己引入的隐患，必须自证**）
    // ==================================================================

    /**
     * 非 UTC 会话时区下重放 users 域 DDL，{@code timestamptz} 时刻**不得漂移**。
     *
     * <p><b>为什么这条必须存在</b>：{@code SchemaInitializer.applyAll}
     * <b>每次启动都原样重放</b>这四份脚本（无版本账本，靠重放做升级）。
     * 而 {@code 03} / {@code 04} 里的 ALTER 用了
     * {@code USING create_time AT TIME ZONE 'UTC'} ——
     * 该表达式对<b>已经是 timestamptz</b> 的列**并不恒等</b>：
     * <pre>
     *   timestamptz --AT TIME ZONE 'UTC'--> timestamp(UTC 墙钟)
     *                --按会话 TimeZone 铸回--> timestamptz(可能被平移)
     * </pre>
     * 会话时区非 UTC 时，<b>每启动一次整列时刻就偏移一次，且永不收敛</b>。
     * 两份脚本里写的 {@code SET TIME ZONE 'UTC'} … {@code RESET TIME ZONE}
     * 就是为了让两条路径都恒等 —— <b>而这个前提此前没有任何测试守着</b>
     * （台账本轮 §5 第 6 项自认「未验证」）。
     *
     * <p><b>本用例的可证伪性</b>：把 {@code 03/04} 里的
     * {@code SET TIME ZONE 'UTC'} 那一行删掉 ⇒ 本条立刻红（时刻偏移一个时区差）。
     * 用例自带「会话时区已被钉成 Asia/Shanghai」的前提断言，不依赖跑测试的机器时区。
     *
     * <p><b>为什么不能只查 DDL 脚本文本</b>：脚本文本里有那行 SET 不代表它被执行 ——
     * 语句切分、事务、连接池都可能改变实际效果，必须让脚本<b>真跑一遍</b>。
     */
    @Test
    @DisplayName("✅ 非 UTC 会话时区下重放 DDL，时刻不漂移（SET TIME ZONE 范式的守门）")
    void ddlReplayDoesNotShiftInstantsUnderNonUtcSession() throws Exception {
        seedAdmin(ADMIN_UID, "replay-admin");
        // 用带偏移量的字面量写入，避免 Timestamp.from() 自己就掺进 JVM 默认时区
        jdbcTemplate.update(
                "update admin_admin set create_time = '2026-01-02T03:04:05+00:00'::timestamptz where uid = ?",
                ADMIN_UID);
        long before = epochSecondOfAdminCreateTime(ADMIN_UID);

        // 会话时区钉成非 UTC —— 这正是会让 `AT TIME ZONE 'UTC'` 平移的条件
        try (java.sql.Connection conn = dataSource.getConnection();
             java.sql.Statement st = conn.createStatement()) {
            st.execute("SET TIME ZONE 'Asia/Shanghai'");
            assertEquals("Asia/Shanghai", currentSessionTimeZone(conn),
                    "前提断言：会话时区必须真的是 Asia/Shanghai，否则本用例测不到任何东西");
            com.rag.users.common.ddl.SchemaInitializer.applyAll(conn, "replay-probe");
        }

        long after = epochSecondOfAdminCreateTime(ADMIN_UID);
        assertEquals(before, after,
                "SchemaInitializer 每次启动都重放脚本。若 03/04 的 ALTER 少了 `SET TIME ZONE 'UTC'`，"
                        + "`timestamptz AT TIME ZONE 'UTC'` 会在非 UTC 会话下把整列平移一个时区差，"
                        + "且每次启动累积一次、永不收敛。before=" + before + " after=" + after);
    }

    /** {@code extract(epoch from timestamptz)} 给的是真实 epoch 秒，与会话时区无关。 */
    private long epochSecondOfAdminCreateTime(long uid) {
        Long v = jdbcTemplate.queryForObject(
                "select extract(epoch from create_time)::bigint from admin_admin where uid = ?",
                Long.class, uid);
        assertNotNull(v, "行必须还在（重放不应删数据）");
        return v;
    }

    private static String currentSessionTimeZone(java.sql.Connection conn) throws Exception {
        try (java.sql.Statement st = conn.createStatement();
             java.sql.ResultSet rs = st.executeQuery("show timezone")) {
            rs.next();
            return rs.getString(1);
        }
    }

    // ==================================================================
    // 2. extra_info 的 jsonb 边界（DRIFT-ARPC-004① 的核心行为）
    // ==================================================================

    @Test
    @DisplayName("✅ extra_info 收下 jsonb 会拒收的 \\u0000 payload（Go 落库成功的那一类）")
    void extraInfoAcceptsPayloadJsonbWouldReject() {
        seedAdmin(ADMIN_UID, "col-parity-admin");

        // 自证：这个 payload 对 jsonb 确实是敌意的 —— 否则本用例会变成空断言。
        // 若哪天 Jackson 不再把 U+0000 转义成 \u0000，下面这行会抛异常 ⇒ 测试会失败提醒人重判。
        assertThrows(Exception.class,
                () -> jdbcTemplate.queryForObject("select cast(? as jsonb)", String.class, "{\"k\":\"\\u0000\"}"),
                "前提断言：这段字面量必须能被 jsonb 拒收（22P02 unsupported Unicode escape sequence），"
                        + "否则本用例测不到任何东西");

        // 真业务链：updateSelf 原本写死 `extra_info = CAST({0} AS jsonb)`，jsonb 列会直接拒收。
        Map<String, String> payload = Map.of("k", "\u0000");
        domain.updateSelf(ADMIN_UID, null, null, null, payload);

        String stored = jdbcTemplate.queryForObject(
                "select extra_info from admin_admin where uid = ?", String.class, ADMIN_UID);
        assertNotNull(stored, "行必须还在：修复前这里会因 22P02 整条 UPDATE 失败");
        assertTrue(stored.contains("\\u0000"),
                "库里应存着 Jackson 转义后的 6 个 ASCII 字符 \\u0000（不是裸 NUL 字节）。实际 = " + stored);

        // 读回路径同样要过一遍（JsonTextTypeHandler 的 getNullableResult）。
        //
        // ⚠️ 断言的是**整体替换**，不是合并 —— 本条第一次写成「两轮写入后 Map 里两个 key 都在」，
        //    跑出来红了。复核 Go 侧后发现**替换才是对的**，是断言写错了：
        //      updateselflogic.go  →  if in.ExtraInfo != nil { toUpdate.ExtraInfo = in.ExtraInfo }
        //      updateuserlogic.go:84-86 → 同款
        //    GORM 的 Updates 赋值给 map 字段时是**整列覆盖**。
        // ⇒ 这条断言现在顺带锁住「extra_info 是替换而非合并」这个语义
        //    （§3.3.3：先改/删错误断言，再改实现 —— 这次是断言单方面写错）。
        var reread = domain.updateSelf(ADMIN_UID, null, null, null, Map.of("k2", "v2"));
        assertEquals(Map.of("k2", "v2"), reread.getExtraInfo(),
                "Go 侧是整列覆盖（toUpdate.ExtraInfo = in.ExtraInfo ⇒ GORM Updates 替换），"
                        + "不是与旧值合并；本条断言同时锁住这个语义");
        String afterReplace = jdbcTemplate.queryForObject(
                "select extra_info from admin_admin where uid = ?", String.class, ADMIN_UID);
        assertFalse(afterReplace.contains("\\u0000"),
                "整列覆盖后旧 key 必须消失。实际 = " + afterReplace);
    }

    // ==================================================================
    // 3. 跨时区的 invite 有效期判定（② 里唯一可观察的那一半）
    // ==================================================================

    @Test
    @DisplayName("✅ invite 有效期按绝对时刻判定：跨时区写入的未过期邀请码不会被误判为已过期")
    void inviteExpiryJudgedOnAbsoluteInstantNotWallClock() {
        seedAdmin(ADMIN_UID, "invite-zone-admin");
        ZoneId foreign = pickForeignZone();

        // 模拟「实例 A（foreign 时区）建邀请码」：绝对时刻是 10 分钟后，但墙钟与 JVM 时区差一整个偏移。
        OffsetDateTime expiresIn10Min = OffsetDateTime.now(foreign).plusMinutes(10);
        seedInvite("ADM-FOREIGN-ZONE-0001", expiresIn10Min);

        // 模拟「实例 B（JVM 默认时区）消费」：走真实业务链，走真实 PG。
        long uid = domain.createAdmin("zone-boss", "pwd123456", null, Map.of(), "ADM-FOREIGN-ZONE-0001");
        assertTrue(uid > 0, "一个绝对时刻上仍未过期的邀请码必须被接受 —— "
                + "若列是 timestamp，PG 会丢掉偏移量，按墙钟比就会跨时区误判为已过期（这正是修复前的行为）");

        // 反向：确实已过期的必须仍被拒（证明这条判定没有因为修复而放松）
        seedInvite("ADM-ALREADY-EXPIRED-01", OffsetDateTime.now(foreign).minusMinutes(1));
        StatusRuntimeException e = assertThrows(StatusRuntimeException.class,
                () -> domain.createAdmin("zone-boss-2", "pwd123456", null, Map.of(), "ADM-ALREADY-EXPIRED-01"),
                "已过期的邀请码必须仍被拒绝");
        assertEquals(Status.Code.PERMISSION_DENIED, e.getStatus().getCode(),
                "Go 出处 createadminlogic.go:59/67 —— 邀请码不存在与已过期都是 PermissionDenied");
    }

    // ==================================================================
    // 辅助
    // ==================================================================

    /**
     * 挑一个<b>与 JVM 默认时区偏移量不同</b>的时区。
     *
     * <p>必要性：若写入时区恰好等于 JVM 时区，即使列退化成 {@code timestamp}
     * （PG 丢偏移）也观察不到错位，用例会假绿。
     * ⇒ 这里保证偏移量<b>非零</b>，让「列类型回退」在<b>任何机器上</b>都能被抓住。
     */
    private static ZoneId pickForeignZone() {
        ZoneId jvm = ZoneId.systemDefault();
        boolean jvmIsUtc = jvm.getRules().getOffset(Instant.now()).equals(ZoneOffset.UTC);
        return jvmIsUtc ? ZoneId.of("Asia/Shanghai") : ZoneOffset.UTC;
    }

    /** 断言打在库的真实 schema 上，而不是 DDL 脚本文本上。 */
    private String columnType(String table, String column) {
        return jdbcTemplate.queryForObject(
                "select data_type from information_schema.columns "
                        + "where table_schema = 'public' and table_name = ? and column_name = ?",
                String.class, table, column);
    }

    private void seedAdmin(long uid, String username) {
        jdbcTemplate.update(
                "insert into admin_admin (uid, username, password, email, extra_info) "
                        + "values (?, ?, 'hash', null, null) on conflict (uid) do nothing",
                uid, username);
    }

    private void seedInvite(String code, OffsetDateTime expiresAt) {
        adminInviteMapper.insert(invite(code, expiresAt));
    }

    private AdminInviteEntity invite(String code, OffsetDateTime expiresAt) {
        AdminInviteEntity e = new AdminInviteEntity();
        e.setCode(code);
        e.setInviterUid(ADMIN_UID);
        e.setExpiresAt(expiresAt);
        return e;
    }
}