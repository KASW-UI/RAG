package com.rag.users.admin.rpc;

import com.rag.users.admin.rpc.domain.UserEntity;
import com.rag.users.admin.rpc.service.AdminDomainService;
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

import static org.junit.jupiter.api.Assertions.*;
import static com.rag.users.admin.rpc.support.GoStyleHostPortPinning.pinContainerPort;

/**
 * E-3 根因 **R4** 的回归网 —— admin-rpc {@code getUserList} 的分页边界，在<b>真 PG</b> 上。
 *
 * <p><b>为什么必须是真 PG</b>：R4 的内容就是「负 OFFSET / 负 LIMIT 会不会真的打到 PG」。
 * 纯 Mockito 的 mapper 测试<b>根本不生成 SQL</b>，无论 {@code goStylePagination} 返回什么
 * 字符串都测不到 PG 的 {@code 22023: OFFSET must not be negative}。
 * 只有真 PG 能证明「这条 SQL 被执行了、且没有报错」。
 *
 * <p><b>Go 的权威判据（两处来源，互相印证）</b>：
 * <ol>
 *   <li><b>GORM 源码</b>：gorm.io/gorm@v1.31.1 {@code clause/limit.go} 的 {@code Limit.Build}
 *       —— {@code LIMIT} 仅在 {@code *Limit >= 0} 时生成，
 *       {@code OFFSET} 仅在 {@code Offset > 0} 时生成；</li>
 *   <li><b>真进程实测</b>：Go admin-api（18889）curl 直打
 *       {@code page=0} / {@code page=-1} / {@code page_size=-5} /
 *       {@code page=9223372036854775807} —— <b>四种全部 200 + code 200</b>。</li>
 * </ol>
 *
 * <p><b>修之前的形态</b>：{@code listWrapper.last("LIMIT " + pageSize + " OFFSET " + offset)}
 * 原样下推 ⇒ PG 报 22023 ⇒ {@code Status.INTERNAL} ⇒ api 层映射成 <b>5001</b>，
 * 而 Go 是 200。且当时的 javadoc 写的是「Go 无任何保护，page=0 会让 PG 抛错」
 * —— 那句话<b>引用了真实的 model/admin.go，但把 GORM 的行为读反了</b>，
 * 与 §3.4.4 DRIFT-009 同型。
 *
 * <p><b>无 Docker 时自动跳过</b>。
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
class AdminRpcPaginationGormSemanticsTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("admin_rpc_page_it")
                    .withUsername("rag")
                    .withPassword("rag").withCreateContainerCmdModifier(cmd -> pinContainerPort(cmd, 5432));

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        // ⚠️ 刻意**不**设 spring.cloud.nacos.discovery.enabled=false：
        // 关掉它会让 NacosServiceManager Bean 不存在，而 NacosRegistration bootstrap
        // 仍然要注入它 ⇒ 上下文起不来。既有 AdminRpcTransactionWiringTest 同样只把
        // server-addr 指到死端口，让 Nacos 连不上但 Bean 仍在。
    }

    @Autowired
    private AdminDomainService adminDomainService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("delete from users_user");
        for (int i = 1; i <= 5; i++) {
            jdbcTemplate.update(
                    "insert into users_user (uid, username, email, status, create_time, update_time)"
                            + " values (?,?,?,0, now(), now())",
                    1000L + i, "page_user_" + i, "page_user_" + i + "@x.com");
        }
    }

    // ==================================================================
    // 纯函数层：goStylePagination 的规则本身
    // ==================================================================

    @Test
    @DisplayName("R4 规则：LIMIT 仅在 pageSize>=0 时生成，OFFSET 仅在 offset>0 时生成")
    void paginationStringMirrorsGormBuild() {
        // GORM clause/limit.go 的四条分支，逐条对齐
        assertEquals("LIMIT 20", AdminDomainService.goStylePagination(20, 0),
                "offset=0 → GORM 不生成 OFFSET");
        assertEquals("LIMIT 20 OFFSET 20", AdminDomainService.goStylePagination(20, 20));
        assertEquals("OFFSET 5", AdminDomainService.goStylePagination(-5, 5),
                "pageSize<0 → GORM 不生成 LIMIT，但 OFFSET 仍生成（且前面没有空格）");
        assertEquals("", AdminDomainService.goStylePagination(-5, -5),
                "两个都被抑制 → 空片段，绝不能拼出 'LIMIT -5 OFFSET -5'");
        assertEquals("LIMIT 0", AdminDomainService.goStylePagination(0, 0),
                "pageSize=0 是 >= 0 ⇒ GORM 仍生成 LIMIT 0");
    }

    // ==================================================================
    // 真 PG 层：这些 SQL 必须真的跑得通
    // ==================================================================

    @Test
    @DisplayName("R4：page=0 → 不报错，返回首页（Go 是 200；修复前 RAG 是 5001）")
    void pageZeroSuppressesOffset() {
        AdminDomainService.UserListResult r = adminDomainService.getUserList(0L, 2L, "page_user");
        assertEquals(5L, r.total());
        assertEquals(2, r.users().size(), "offset=-20 被抑制 ⇒ 不带 OFFSET，等价于首页");
    }

    @Test
    @DisplayName("R4：page=-1 → 不报错，返回首页")
    void negativePageSuppressesOffset() {
        AdminDomainService.UserListResult r = adminDomainService.getUserList(-1L, 2L, "page_user");
        assertEquals(2, r.users().size(), "offset=-40 被抑制");
    }

    @Test
    @DisplayName("R4：page_size=-5 → LIMIT 与 OFFSET 双双被抑制，返回全量（Go 是全量，不是 400）")
    void negativePageSizeSuppressesBoth() {
        AdminDomainService.UserListResult r = adminDomainService.getUserList(1L, -5L, "page_user");
        assertEquals(5L, r.total());
        assertEquals(5, r.users().size(), "GORM 对负 LIMIT 是静默忽略 ⇒ 全量返回");
    }

    @Test
    @DisplayName("R4：page=int64max → int64 回绕成负 offset，被抑制后仍返回 200")
    void int64MaxPageWrapsToNegativeOffset() {
        // Go: (2^63-2)*20 在 int64 下回绕成 -64 → 负 → 不生成 OFFSET
        // ⚠️ 这一条同时锁住「必须用 long 算」——用 int 算 page 会先在窄化处溢出成别的值
        AdminDomainService.UserListResult r =
                adminDomainService.getUserList(Long.MAX_VALUE, 2L, "page_user");
        assertEquals(2, r.users().size());
    }

    @Test
    @DisplayName("R4 对照：正常分页不受影响（第 2 页应与第 1 页内容不同）")
    void normalPaginationStillWorks() {
        AdminDomainService.UserListResult p1 = adminDomainService.getUserList(1L, 2L, "page_user");
        AdminDomainService.UserListResult p2 = adminDomainService.getUserList(2L, 2L, "page_user");
        assertEquals(2, p1.users().size());
        assertEquals(2, p2.users().size());
        assertNotEquals(p1.users().get(0).getId(), p2.users().get(0).getId(),
                "第 2 页的第 1 行不该等于第 1 页的第 1 行 —— 说明 OFFSET 没生效");
    }

    @Test
    @DisplayName("R4 对照：修复前的形态必须真的会被 PG 拒掉（证明这条用例不是恒绿）")
    void theOldSqlWouldIndeedFailOnRealPg() {
        // 变异测试：手工执行修复前的拼法，PG 必须报 22023。
        // 没有这条，上面几条就可能因为「压根没查库」而假绿。
        var ex = assertThrows(Exception.class, () -> jdbcTemplate.queryForList(
                "select uid from users_user order by id desc LIMIT -5 OFFSET -10"));
        assertTrue(String.valueOf(ex.getMessage()).contains("OFFSET must not be negative")
                        || String.valueOf(ex.getCause()).contains("OFFSET must not be negative"),
                "PG 没有以 22023 拒掉负 OFFSET，测试前提不成立：" + ex.getMessage());
    }
}