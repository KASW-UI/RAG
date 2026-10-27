package com.rag.users.user.rpc.bootstrap;

import com.rag.users.user.rpc.UserRpcApplication;
import com.rag.users.user.rpc.domain.UserAvatarHistoryEntity;
import com.rag.users.user.rpc.domain.UserEntity;
import com.rag.users.user.rpc.mapper.UserAvatarHistoryMapper;
import com.rag.users.user.rpc.mapper.UserMapper;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.rag.users.user.rpc.support.GoStyleHostPortPinning.pinContainerPort;

/**
 * <p><b>关闭 {@code DRIFT-USERRPC-V-006}</b>：两张表的 {@code id} 取值来源
 * （Go {@code bigserial} 序列 vs RAG {@code IdType.ASSIGN_ID} 雪花）此前一直是「静态推断 + 未实测」。
 * 本类用<b>真 PG</b>把它一次钉死，并同时回答台账追问的那个问题：
 * <b>{@code @Options(useGeneratedKeys=true)} 与 {@code IdType.ASSIGN_ID} 到底谁胜出。</b>
 *
 * <h3>Go 侧事实（消费点，非声明点）</h3>
 * <ul>
 *   <li>声明点 {@code service/user/user/rpc/internal/model/data_model.go:6,23}：
 *       {@code Id uint64 \`gorm:"primaryKey"\`} —— <b>无 autoIncrement 标签</b>。
 *       GORM 的约定是「整型单列主键 ⇒ 视作自增」，零值主键<b>不入 INSERT</b>，
 *       交给 DDL 上的 {@code BIGSERIAL} 序列填。</li>
 *   <li>消费点 {@code service/user/user/rpc/internal/logic/registerlogic.go:73} 只赋 {@code Uid}，
 *       <b>从不赋 {@code Id}</b> ⇒ 恒为零值 ⇒ 恒由序列填。</li>
 *   <li>消费点 {@code service/user/user/rpc/internal/model/user.go:86-94} 构造
 *       {@code UserAvatarHistory{HistoryID, Uid, AvatarURL, ContentType, SizeBytes, IsCurrent}}
 *       —— <b>同样不含 {@code Id}</b>。</li>
 * </ul>
 *
 * <h3>RAG 侧事实</h3>
 * {@code UserEntity.id} 与 {@code UserAvatarHistoryEntity.id} 都是
 * {@code @TableId(type = IdType.ASSIGN_ID)} ⇒ MyBatis-Plus 会<b>生成雪花并显式写进 INSERT</b>。
 *
 * <h3>为什么这条<b>不影响 wire</b>（决定它是 OBSERVABLE 还是 SEMANTIC 的关键）</h3>
 * <ul>
 *   <li>{@code users_user.id}：proto 的 {@code UserInfo} 只有 {@code uid}，<b>没有 id</b>
 *       （{@code user-proto/src/main/proto/users.proto:9-15}）。</li>
 *   <li>{@code users_user_avatar_history.id}：proto 的 {@code AvatarHistoryItem.id} 虽然叫 id，
 *       但 Go 在 {@code service/user/user/rpc/internal/logic/avatarhelpers.go:22} 填的是
 *       <b>{@code item.HistoryID}</b>（雪花<b>字符串</b>），<b>不是 DB 主键</b>。</li>
 * </ul>
 * ⇒ 两个 {@code id} 列都<b>永远不进入任何 proto 字段</b>，所以取值来源不同**不可观察**。
 * 本类把这个结论从「推断」升级为「真 PG 实证」。
 *
 * <p><b>⚠️ 为什么必须 {@code grpc.server.port=0}</b>：{@code application.yaml:81} 把 gRPC 端口
 * 钉死在 {@code 49210}，而本仓库有多条并行 worktree 会话 + 外部 difftest 容器，
 * 实测该端口被 <b>Docker（别的会话的栈）</b>占着 ⇒
 * {@code Failed to bind to address 0.0.0.0/0.0.0.0:49210 / BindException} ⇒
 * 整个 {@code ApplicationContext} 起不来（4 条用例全部变成
 * {@code ApplicationContext failure threshold exceeded}，<b>一条真实断言都没跑到</b>）。
 * 本类只关心 DDL 与主键来源，<b>不需要真的监听端口</b> ⇒ 用临时端口（0 由内核分配）。
 */
@Testcontainers
@SpringBootTest(classes = UserRpcApplication.class,
        properties = "grpc.server.port=0")
class UserRpcPrimaryKeySourceParityTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("user_rpc_pk_it")
                    .withUsername("rag")
                    .withPassword("rag").withCreateContainerCmdModifier(cmd -> pinContainerPort(cmd, 5432));

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private UserAvatarHistoryMapper avatarHistoryMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * <b>用例 1</b>：{@code users_user.id} 由 MyBatis-Plus 的雪花填充，<b>不是</b>序列。
     *
     * <p>判据用「量级」而不是「等于某个具体值」——雪花是 19 位、序列是 1~N，
     * 两者不可能混淆；而 <b>序列的 {@code last_value} 不动</b> 是更硬的第二判据。
     */
    @Test
    @DisplayName("Go: Id 零值不入 INSERT ⇒ 由 bigserial 填；RAG: @TableId(ASSIGN_ID) ⇒ 雪花填。实测谁是赢家")
    void usersUserIdComesFromSnowflakeNotSequence() {
        UserEntity u = new UserEntity();
        u.setUid(9000000000001L);
        u.setUsername("pk_probe_user");
        u.setPassword("x");
        userMapper.insert(u);

        Long idInDb = jdbcTemplate.queryForObject(
                "SELECT id FROM users_user WHERE uid = ?", Long.class, u.getUid());
        assertNotNull(idInDb);

        // 雪花（IdType.ASSIGN_ID）是 19 位量级；bigserial 序列在测试库里只会是 1,2,3…
        assertTrue(idInDb > 1_000_000_000_000_000_000L,
                () -> "users_user.id=" + idInDb + " 不像雪花，ASSIGN_ID 可能没生效");

        // 更硬的判据：序列根本没被消耗
        Long seqValue = jdbcTemplate.queryForObject(
                "SELECT last_value FROM users_user_id_seq", Long.class);
        assertTrue(seqValue == 1L,
                () -> "bigserial 序列已被消耗（last_value=" + seqValue
                        + "）⇒ 说明 id 是序列给的，不是雪花");
    }

    /**
     * <b>用例 2</b>：{@code users_user_avatar_history.id} 同上。
     *
     * <p>这张表的 {@code id} 是 {@code users_user_avatar_history.id_seq} 管的，
     * 名称与 users_user 的不同，必须分开断言。
     */
    @Test
    @DisplayName("头像历史表同理：id 来自雪花，序列未被消耗")
    void avatarHistoryIdComesFromSnowflakeNotSequence() {
        UserAvatarHistoryEntity h = new UserAvatarHistoryEntity();
        h.setHistoryId("pk-probe-history-1");
        h.setUid(9000000000002L);
        h.setAvatarUrl("http://probe/a.png");
        h.setContentType("image/png");
        h.setSizeBytes(128L);
        h.setIsCurrent(true);
        avatarHistoryMapper.insert(h);

        Long idInDb = jdbcTemplate.queryForObject(
                "SELECT id FROM users_user_avatar_history WHERE history_id = ?",
                Long.class, h.getHistoryId());
        assertNotNull(idInDb);

        assertTrue(idInDb > 1_000_000_000_000_000_000L,
                () -> "users_user_avatar_history.id=" + idInDb + " 不像雪花");

        Long seqValue = jdbcTemplate.queryForObject(
                "SELECT last_value FROM users_user_avatar_history_id_seq", Long.class);
        assertTrue(seqValue == 1L,
                () -> "头像表的 bigserial 序列已被消耗（last_value=" + seqValue + "）");
    }

    /**
     * <b>用例 3（守恒式）</b>：{@code id} 列<b>不在任何 proto 字段里</b>。
     *
     * <p>这是本条判为「不可观察」的根据，**不能只靠注释**。
     * 做法：断言 {@code users.proto} 全文里，{@code UserInfo} 与 {@code AvatarHistoryItem}
     * 两个 message 都不含 {@code int64 id}。
     */
    @Test
    @DisplayName("守恒断言：两个进 wire 的 message 都不含 int64 id —— 这是 V-006 不可观察的根据")
    void wireMessagesDoNotExposePrimaryKey() throws Exception {
        String src = java.nio.file.Files.readString(locateProto());

        String userInfo = extractMessage(src, "UserInfo");
        assertTrue(userInfo != null && !userInfo.matches("(?s).*\\bint64\\s+id\\s*=.*"),
                () -> "UserInfo 出现了 int64 id —— V-006 的「不可观察」结论失效，必须重判");

        String item = extractMessage(src, "AvatarHistoryItem");
        assertTrue(item != null, "找不到 AvatarHistoryItem message");
        // AvatarHistoryItem 有一个 string id，但它在 Go 侧被 HistoryID 填充，不是 DB 主键
        assertTrue(item.contains("string id = 1"),
                () -> "AvatarHistoryItem.id 的类型/字段号变了，需重新核对 avatarhelpers.go:22");
        assertFalse(item.contains("int64 id"),
                () -> "AvatarHistoryItem 出现了 int64 id —— 需重新核对是否直接映射了 DB 主键");
    }

    /**
     * 定位 {@code users.proto}。
     *
     * <p>不能直接用相对路径 {@code src/main/proto/users.proto}：用 {@code mvn -pl} 跑时
     * surefire 的工作目录是<b>仓库根</b>而不是模块目录，实测报 {@code NoSuchFileException}。
     * ⇒ 从当前目录逐级向上找，找不到就退到用户目录硬找，<b>宁可失败也不静默跳过断言</b>。
     */
    private static java.nio.file.Path locateProto() {
        java.nio.file.Path dir = java.nio.file.Path.of("").toAbsolutePath();
        while (dir != null) {
            java.nio.file.Path p = dir.resolve(
                    "service/users/user/user-proto/src/main/proto/users.proto");
            if (java.nio.file.Files.exists(p)) return p;
            dir = dir.getParent();
        }
        java.nio.file.Path p = java.nio.file.Path.of(System.getProperty("user.dir"))
                .resolve("service/users/user/user-proto/src/main/proto/users.proto");
        assertTrue(java.nio.file.Files.exists(p), () -> "找不到 users.proto，最后试过：" + p);
        return p;
    }

    /** 取出 {@code message <name> { ... }} 的原文（含嵌套大括号）。 */
    private static String extractMessage(String src, String name) {
        int i = src.indexOf("message " + name + " ");
        if (i < 0) return null;
        int start = src.indexOf('{', i);
        int depth = 0;
        for (int j = start; j < src.length(); j++) {
            char c = src.charAt(j);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return src.substring(i, j + 1);
            }
        }
        return null;
    }

    /** 两条用例都要用到：断言表已建好（否则上面的查询会以「表不存在」失败，掩盖真正的结论）。 */
    @Test
    @DisplayName("前提：users 域两张表的 DDL 真建出来了，且 id 列是 BIGSERIAL")
    void schemaIsReal() {
        String dataType = jdbcTemplate.queryForObject(
                "SELECT data_type FROM information_schema.columns "
                        + "WHERE table_name='users_user' AND column_name='id'", String.class);
        assertEquals("bigint", dataType);

        Long seqCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pg_class WHERE relname IN "
                        + "('users_user_id_seq','users_user_avatar_history_id_seq')", Long.class);
        assertTrue(seqCount != null && seqCount == 2L,
                () -> "两条序列都在（Go 侧正是靠这两条序列取 id），实际 count=" + seqCount);
    }
}