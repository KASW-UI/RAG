package com.rag.users.user.rpc;

import com.rag.users.common.ddl.SchemaInitializer;
import com.rag.users.user.proto.UploadAvatarReq;
import com.rag.users.user.proto.UploadAvatarResp;
import com.rag.users.user.rpc.domain.UserAvatarHistoryEntity;
import com.rag.users.user.rpc.domain.UserEntity;
import com.rag.users.user.rpc.mapper.UserAvatarHistoryMapper;
import com.rag.users.user.rpc.mapper.UserMapper;
import com.rag.users.user.rpc.service.AvatarDomainService;
import com.rag.users.user.rpc.service.UserDomainService;
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

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.rag.users.user.rpc.support.GoStyleHostPortPinning.pinContainerPort;

/**
 * 🔴 守门测试 —— users 域<b>唯一 wire 可见</b>的时间列：{@code users_user_avatar_history}。
 *
 * <h2>本类守两条漂移</h2>
 * <ol>
 *   <li><b>DRIFT-USERRPC-V-004（可观察半边）</b> —— 时间列必须是 {@code timestamptz}，
 *       实体必须带偏移，渲染必须保留偏移量。<br>
 *       旧实现是 {@code TIMESTAMP} + {@code LocalDateTime} + {@code atOffset(UTC)}，
 *       实测双侧活体对拍（2026-10-23 21:01，真进程 + 真 PG）：
 *       <pre>
 *         Go  : "2026-10-23T21:01:48+08:00"
 *         RAG : "2026-10-23T21:01:48Z"      ← 修复前（时刻本身差 8 小时）
 *       </pre></li>
 *   <li><b>DRIFT-USERRPC-V-009</b> —— 上传头像的响应里 {@code create_time} 必须是<b>非空</b>的
 *       RFC3339 字符串。修复前实体不被回填 ⇒ wire 上是空串。<br>
 *       <pre>
 *         Go  : "2026-10-23T21:01:48+08:00"
 *         RAG : ""                              ← 修复前
 *       </pre></li>
 * </ol>
 *
 * <h2>Go 出处（声明点 + 消费点 + 渲染点）</h2>
 * <ul>
 *   <li><b>声明点</b>：{@code service/user/user/rpc/internal/model/data_model.go:30-31}
 *       —— {@code CreateTime/UpdateTime time.Time `gorm:"column:…;auto…Time"}`，<b>无 type tag</b>；</li>
 *   <li><b>消费点（列类型）</b>：{@code gorm.io/driver/postgres@v1.6.0/postgres.go:251-256}
 *       <pre>
 *       case schema.Time:
 *           if field.Precision &gt; 0 { return fmt.Sprintf("timestamptz(%d)", field.Precision) }
 *           return "timestamptz"
 *       </pre></li>
 *   <li><b>消费点（写入 + 回填）</b>：{@code service/user/user/rpc/internal/model/user.go:86-95}
 *       <pre>
 *       history = UserAvatarHistory{ …, IsCurrent: true }
 *       if err := tx.Create(&history).Error; err != nil { return err }
 *       </pre>
 *       GORM 的 {@code autoCreateTime/autoUpdateTime} 在 Create 之前把两个字段写成
 *       <b>应用侧时钟</b>（{@code gorm.io/gorm/callbacks/create.go} 的
 *       {@code ConvertToCreateValues} 用 {@code stmt.DB.NowFunc()}），并随 INSERT 一起写库；
 *       传的是指针 ⇒ <b>同一个结构体被回填</b>。</li>
 *   <li><b>渲染点</b>：{@code service/user/user/rpc/internal/logic/avatarhelpers.go:16-19}
 *       <pre>
 *       createTime := ""
 *       if !item.CreateTime.IsZero() { createTime = item.CreateTime.Format(time.RFC3339) }
 *       </pre>
 *       Go 的 {@code time.Time} 自带 location ⇒ {@code Format} 打印该 location 的偏移量。</li>
 * </ul>
 *
 * <h2>偏移量由谁决定（2026-10-23 实测更正旧台账的归因）</h2>
 * <p><b>进程本地时区</b>，<b>不是</b> PG 会话时区，也不是 DSN 里的 {@code TimeZone=}：
 * <pre>
 *   Go 进程 TZ=Asia/Shanghai（DSN 仍钉 TimeZone=Asia/Shanghai） → "2026-10-23T21:01:48+08:00"
 *   Go 进程 TZ=UTC        （DSN 仍钉 TimeZone=Asia/Shanghai） → "2026-10-23T13:02:19Z"
 * </pre>
 * 源码依据：{@code pgx v5.10.0 pgtype/timestamptz.go:270-276}
 * —— {@code tim := time.Unix(…); if plan.location != nil { tim = tim.In(plan.location) }}，
 * 而 {@code TimestamptzCodec.ScanLocation} 默认 nil（:133-137）⇒ 落回 {@code time.Local}；
 * pgx 走二进制解码，PG 会话时区不参与。
 * ⇒ RAG 侧用 {@link OffsetDateTime} 承接 pgjdbc 的解码（pgjdbc 同样解到 JVM 默认时区），
 * 渲染时<b>保留该偏移量</b>即与 Go 同形；<b>不要</b>在 JDBC URL 上钉 {@code TimeZone}。
 *
 * <h2>为什么必须真 PG</h2>
 * <p>要证的是「DDL 真被执行了」＋「{@code @Insert} 真把应用侧时钟写进了库」＋
 * 「回填的实体与库里的值是同一时刻」——这三件都是<b>读 SQL 文件证不出来</b>的。
 * 纯 Mockito 的 mapper 单测只证明「代码调用了 insertHistory」。
 *
 * <h2>可证伪性</h2>
 * <ul>
 *   <li>把 DDL 的 {@code TIMESTAMPTZ} 改回 {@code TIMESTAMP} → 用例 1 / 3 立刻红；</li>
 *   <li>把实体改回 {@code LocalDateTime} → 用例 2 立刻红；</li>
 *   <li>把渲染改回 {@code ct.atOffset(ZoneOffset.UTC)} → 用例 6 / 7 立刻红；</li>
 *   <li>把 {@code insertHistory} 的 {@code #{createTime}} 改回 {@code CURRENT_TIMESTAMP}
 *       且不回填实体 → 用例 4 / 5 立刻红；</li>
 *   <li>删掉 DDL 里的 {@code SET TIME ZONE 'UTC'} → 用例 8 在非 UTC 会话时区下立刻红。</li>
 * </ul>
 *
 * <p>⚠️ <b>Ryuk 已在本模块 pom 的 surefire 配置里关掉</b>
 * （{@code TESTCONTAINERS_RYUK_DISABLED=true}；Ryuk 镜像需从 Docker Hub 拉，
 * 本机走加速器拉会 EOF 失败且发生在 {@code @Container} 之前）。
 * ⚠️ <b>本类带 {@code @EnabledIfDockerAvailable}</b> ⇒ 无 Docker 时会被<b>整类跳过</b>；
 * 「跑绿」不等于「真 PG 已验」——必须另行核对 surefire 报告里的 {@code skipped="0"}。
 */
@SpringBootTest(
        classes = UserRpcApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "grpc.server.port=0",
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN"
        })
@Testcontainers
@org.testcontainers.junit.jupiter.EnabledIfDockerAvailable
class UserRpcAvatarHistoryTimestamptzParityTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("user_rpc_avatar_tstz_it")
                    .withUsername("rag")
                    .withPassword("rag").withCreateContainerCmdModifier(cmd -> pinContainerPort(cmd, 5432));

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DataSource dataSource;
    @Autowired private UserMapper userMapper;
    @Autowired private UserAvatarHistoryMapper avatarHistoryMapper;
    @Autowired private AvatarDomainService avatarDomainService;
    @Autowired private UserDomainService userDomainService;

    private static final DateTimeFormatter RFC3339 =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
    private static final long SEED_UID = 8801L;
    private static final String NON_UTC_TZ = "Asia/Shanghai";
    /** 迁移后应当等于的绝对时刻：无时区墙钟 {@code 2020-01-01 08:00:00} 按 UTC 解释。 */
    private static final Instant SEED_INSTANT = Instant.parse("2020-01-01T08:00:00Z");

    @BeforeEach
    void resetTables() {
        jdbcTemplate.execute("TRUNCATE users_user_avatar_history RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE users_user RESTART IDENTITY CASCADE");
        jdbcTemplate.update("""
                INSERT INTO users_user (uid, username, password, email, status, score, extra_info)
                VALUES (?, 'avatar-tstz-user', 'h', 'avatar-tstz@mail.test', 0, 0, '{}')
                """, SEED_UID);
    }

    // ==================================================================
    // 1. 守恒断言：本表**恰好**只有 create_time / update_time 两个时间列，且都是 timestamptz
    // ==================================================================

    @Test
    @DisplayName("Go: users_user_avatar_history 的全部时间列都是 timestamptz（守恒：恰好 2 列，无漏网）")
    void allTimeColumnsAreTimestamptz() {
        Map<String, String> timeColumns = timeColumnTypes();
        assertEquals(
                Map.of("create_time", "timestamp with time zone",
                        "update_time", "timestamp with time zone"),
                timeColumns,
                "users_user_avatar_history 的时间列必须全部是 timestamptz"
                        + "（Go 消费点 postgres.go:251-256）；实际=" + timeColumns);
    }

    // ==================================================================
    // 2. 实体侧：字段类型必须带偏移（LocalDateTime 根本无法表达 Go 的偏移量）
    // ==================================================================

    @Test
    @DisplayName("Go: UserAvatarHistoryEntity 的两个时间字段声明为 OffsetDateTime（带偏移）")
    void entityTimeFieldsAreOffsetDateTime() throws NoSuchFieldException {
        // 为什么断言「声明类型」而不是「往返值的具体偏移量」：pgjdbc 把 timestamptz 取成
        // OffsetDateTime 时会归一到 JVM 默认时区，断言具体偏移量会随跑测机器的 TZ 变 ⇒ 假红。
        // 声明类型才是稳定且可证伪的那一半。
        assertEquals(OffsetDateTime.class, fieldType("createTime"));
        assertEquals(OffsetDateTime.class, fieldType("updateTime"));
    }

    // ==================================================================
    // 3. DRIFT-USERRPC-V-009：上传响应的 create_time 必须非空
    // ==================================================================

    @Test
    @DisplayName("Go: UploadAvatar 响应的 history.create_time 必须是非空的 RFC3339 字符串")
    void uploadAvatarResponseCarriesNonEmptyCreateTime() {
        UploadAvatarResp resp = userDomainService.uploadAvatar(UploadAvatarReq.newBuilder()
                .setUid(SEED_UID)
                .setAvatarUrl("https://cdn/tstz/a1.png")
                .setContentType("image/png")
                .setSizeBytes(1234L)
                .build());

        String createTime = resp.getHistory().getCreateTime();
        assertNotNull(createTime, "create_time 不得为 null");
        assertTrue(!createTime.isEmpty(),
                "create_time 不得是空串 —— Go 的 GORM autoCreateTime 会把时刻回填进结构体，"
                        + "响应里必然带真实时刻；修复前本值为 \"\"（DRIFT-USERRPC-V-009）");
        assertTrue(createTime.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(Z|[+-]\\d{2}:\\d{2})"),
                "create_time 必须是 Go time.RFC3339 的形态（秒精度 + 偏移量标签），实际=" + createTime);
        // 秒精度：Go 的 RFC3339 布局不含 .999 段 ⇒ 纳秒整段丢弃
        assertTrue(!createTime.contains("."), "必须是秒精度，实际=" + createTime);
    }

    // ==================================================================
    // 4. 回填的实体与库里的值必须是**同一时刻**（Go 也是如此）
    // ==================================================================

    @Test
    @DisplayName("Go: 上传后返回的实体 create_time 与库里的值是同一绝对时刻")
    void returnedEntityCarriesTheStoredInstant() {
        UserAvatarHistoryEntity returned = avatarHistoryMapper.createAvatarHistory(
                userMapper, SEED_UID, "H-tstz-1", "https://cdn/tstz/a1.png", "image/png", 1234L);

        assertNotNull(returned.getCreateTime(),
                "实体必须被回填 create_time（Go: GORM autoCreateTime 回填指针指向的结构体）");
        assertNotNull(returned.getUpdateTime(), "实体必须被回填 update_time");

        OffsetDateTime stored = jdbcTemplate.queryForObject(
                "SELECT create_time FROM users_user_avatar_history WHERE history_id = ?",
                OffsetDateTime.class, "H-tstz-1");
        assertEquals(stored.toInstant(), returned.getCreateTime().toInstant(),
                "响应里的时刻必须与库里存的是同一绝对时刻（Go 侧两者同源：应用侧时钟写库 + 同一结构体回填）");
    }

    // ==================================================================
    // 5. 🔴 上传响应里的 create_time 必须与「事后重读」的逐字相同
    // ==================================================================

    @Test
    @DisplayName("Go: 上传响应的 create_time 必须与事后重读同一行的 create_time 逐字相同")
    void uploadResponseCreateTimeEqualsLaterRead() {
        UserAvatarHistoryEntity returned = avatarHistoryMapper.createAvatarHistory(
                userMapper, SEED_UID, "H-tstz-2", "https://cdn/tstz/a2.png", "image/png", 1234L);
        String fromInsert = avatarDomainService.fromModel(returned).getCreateTime();

        UserAvatarHistoryEntity reread = avatarHistoryMapper.selectByUidAndHistoryIdRaw(
                SEED_UID, "H-tstz-2");
        String fromRead = avatarDomainService.fromModel(reread).getCreateTime();

        assertEquals(fromRead, fromInsert,
                "上传响应与事后重读必须渲染出同一个字符串 —— 修复前插入侧是空串、读侧是 '…Z'，"
                        + "两者恒不相等");
    }

    // ==================================================================
    // 6. 🔴 渲染必须**保留实体自带的偏移量**，不得叠 atOffset(UTC)
    // ==================================================================

    @Test
    @DisplayName("Go: create_time 的偏移量标签必须来自**进程本地时区**，不得被强制成 UTC")
    void renderingKeepsTheEntityOffset() {
        // 机器无关 + 可证伪的做法：**强制**进程时区，而不是依赖跑测机器的 TZ。
        // （Go 侧的 time.Local 就是进程 TZ；把 Go 进程 TZ 改成 UTC，Go 立刻渲染成 '…Z'
        //   —— 双侧活体对拍实测，2026-10-23 21:02。）
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
            // 实体的偏移量故意给成 UTC：渲染必须按**本地时区**表达同一个时刻，
            // 而不是把驱动给的偏移量原样带出（pgjdbc 恒给 UTC）。
            UserAvatarHistoryEntity h = new UserAvatarHistoryEntity();
            h.setHistoryId("H-tstz-3");
            h.setCreateTime(OffsetDateTime.of(2026, 9, 23, 10, 15, 30, 0, ZoneOffset.UTC));

            assertEquals("2026-09-23T18:15:30+08:00", avatarDomainService.fromModel(h).getCreateTime(),
                    "渲染必须落在进程本地时区（Go 的 time.Local 语义）；"
                            + "叠 atOffset(UTC) 会渲染成 '…Z' —— 那是 DRIFT-USERRPC-V-004 的可观察半边");

            // 对照：本地时区是 UTC 时，Go 侧同样渲染成 Z（实测：Go 进程 TZ=UTC → '…Z'）
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            assertEquals("2026-09-23T10:15:30Z", avatarDomainService.fromModel(h).getCreateTime(),
                    "本地时区为 UTC 时必须渲染成 Z（与 Go 进程 TZ=UTC 的实测一致）");
        } finally {
            TimeZone.setDefault(original);
        }
    }

    // ==================================================================
    // 7. 🔴 渲染的偏移量必须等于 JVM 默认时区的偏移量（与 Go 的 time.Local 同构）
    // ==================================================================

    @Test
    @DisplayName("Go: 渲染出的偏移量标签必须等于 JVM 默认时区的偏移量（对齐 Go 的 time.Local）")
    void renderingUsesTheProcessLocalZoneOffset() {
        OffsetDateTime now = OffsetDateTime.now();
        UserAvatarHistoryEntity h = new UserAvatarHistoryEntity();
        h.setHistoryId("H-tstz-4");
        h.setCreateTime(now);

        String rendered = avatarDomainService.fromModel(h).getCreateTime();
        String expected = RFC3339.format(now);

        assertEquals(offsetPart(expected), offsetPart(rendered),
                "偏移量标签必须与 JVM 默认时区一致（Go 侧由 time.Local 决定，实测："
                        + "Go 进程 TZ=UTC 时 Go 渲染 '…Z'，与 DSN 的 TimeZone= 无关）。"
                        + "渲染值=" + rendered + " 期望值=" + expected);
    }

    // ==================================================================
    // 8. 重放守门：非 UTC 会话下重复重放，本表的时刻不得漂移
    // ==================================================================

    @Test
    @DisplayName("重放守门：本表在非 UTC 会话下重复重放，绝对时刻不得累积平移")
    void replayUnderNonUtcSessionTimeZone_doesNotShiftStoredInstants() throws Exception {
        downgradeToLegacyTimestamp();
        seedLegacyRow();

        applySchema(NON_UTC_TZ);
        assertEquals(SEED_INSTANT, instantOf("create_time"), "首次迁移后的基准");

        for (int i = 0; i < 3; i++) {
            applySchema(NON_UTC_TZ);
            assertEquals(SEED_INSTANT, instantOf("create_time"),
                    "第 " + (i + 1) + " 次重放后绝对时刻被平移了。"
                            + "根因：裸 ALTER 的 `timestamptz AT TIME ZONE 'UTC'` 不恒等，"
                            + "回铸时会按会话 TimeZone 再解释一次 —— DDL 必须先 `SET TIME ZONE 'UTC'` 再转。");
        }
    }

    // ==================================================================
    // 9. SET/RESET 必须把会话时区还原（连接要归还连接池，不能带 UTC 出去）
    // ==================================================================

    @Test
    @DisplayName("DDL 不得把会话 TimeZone 带出脚本：applyAll 后应回到库默认值")
    void schemaReplayRestoresSessionTimeZone() throws Exception {
        String serverDefault = jdbcTemplate.queryForObject("SHOW TimeZone", String.class);
        try (Connection conn = dataSource.getConnection()) {
            try (Statement st = conn.createStatement()) {
                st.execute("SET TIME ZONE '" + NON_UTC_TZ + "'");
            }
            SchemaInitializer.applyAll(conn, "UserRpcAvatarHistoryTimestamptzParityTest");
            try (Statement st = conn.createStatement();
                 var rs = st.executeQuery("SHOW TimeZone")) {
                rs.next();
                assertEquals(serverDefault, rs.getString(1),
                        "脚本里的 `SET TIME ZONE 'UTC'` 没有被 `RESET TIME ZONE` 还原 —— "
                                + "这条连接会带着 UTC 回到连接池，影响后续所有业务查询。");
            }
        }
    }

    // ==================================================================
    // helpers
    // ==================================================================

    /** 取 RFC3339 字符串的偏移量标签部分（"Z" 或 "+08:00"）。 */
    private static String offsetPart(String rfc3339) {
        return rfc3339.length() <= 19 ? "Z" : rfc3339.substring(19);
    }

    private static Class<?> fieldType(String name) throws NoSuchFieldException {
        Field f = UserAvatarHistoryEntity.class.getDeclaredField(name);
        return f.getType();
    }

    /** 守恒用：把本表所有时间列的 data_type 收成一张表，漏一列就红。 */
    private Map<String, String> timeColumnTypes() {
        Map<String, String> out = new LinkedHashMap<>();
        jdbcTemplate.queryForList("""
                SELECT column_name, data_type
                FROM information_schema.columns
                WHERE table_schema = current_schema()
                  AND table_name = 'users_user_avatar_history'
                  AND data_type LIKE 'timestamp%'
                ORDER BY column_name
                """).forEach(r -> out.put((String) r.get("column_name"), (String) r.get("data_type")));
        return out;
    }

    private Instant instantOf(String column) {
        OffsetDateTime v = jdbcTemplate.queryForObject(
                "SELECT " + column + " FROM users_user_avatar_history WHERE history_id = ?",
                OffsetDateTime.class, "H-legacy");
        return v.toInstant();
    }

    /** 把两列退回「迁移前」的 TIMESTAMP 形态，模拟老库。 */
    private void downgradeToLegacyTimestamp() {
        jdbcTemplate.execute("ALTER TABLE users_user_avatar_history ALTER COLUMN create_time TYPE TIMESTAMP");
        jdbcTemplate.execute("ALTER TABLE users_user_avatar_history ALTER COLUMN update_time TYPE TIMESTAMP");
    }

    private void seedLegacyRow() {
        jdbcTemplate.update("""
                INSERT INTO users_user_avatar_history
                    (history_id, uid, avatar_url, content_type, size_bytes, is_current,
                     create_time, update_time)
                VALUES ('H-legacy', ?, 'https://cdn/legacy.png', 'image/png', 1, true,
                        TIMESTAMP '2020-01-01 08:00:00', TIMESTAMP '2020-01-01 08:00:00')
                """, SEED_UID);
    }

    /** 在指定会话时区下走**真实的** SchemaInitializer（不是把 SQL 抄一遍）。 */
    private void applySchema(String sessionTimeZone) throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            try (Statement st = conn.createStatement()) {
                st.execute("SET TIME ZONE '" + sessionTimeZone + "'");
            }
            SchemaInitializer.applyAll(conn, "UserRpcAvatarHistoryTimestamptzParityTest");
        }
    }
}
