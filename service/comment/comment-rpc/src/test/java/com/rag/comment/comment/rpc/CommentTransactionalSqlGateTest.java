package com.rag.comment.comment.rpc;

import com.rag.comment.comment.rpc.mapper.CommentIndexMapper;
import com.rag.comment.comment.rpc.mapper.SubjectMapper;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 🔴 真 PostgreSQL + 真 MyBatis 上的<b>动态 SQL</b> 守门测试。
 *
 * <h2>为什么单独开一个类</h2>
 * <p>本域此前的测试分两类：① 纯 Java 逻辑断言（不碰 SQL）② 裸 JDBC 的 DDL 断言
 * （<b>不经过 MyBatis 的动态拼接</b>）。
 * ⇒ <b>没有任何测试执行过 mapper 里那些 {@code <if>/<set>} 拼出来的 SQL</b>。
 *
 * <p>🔴 <b>代价已经付过了</b>：{@code SubjectMapper#decrementCounts} 的 {@code <if>}
 * 被拼在 WHERE 子句之后 ⇒ 删根评论时是<b>语法错误</b>
 * ⇒ {@code DeleteCommentTx} 整个事务回滚 ⇒ 对外报 5002「DB更新失败」。
 * 该缺陷是<b>双侧活体对拍</b>（2026-10-24）抓出来的，不是单测抓出来的。
 *
 * <p>⇒ 本类把**每一个**带 {@code <if>} / {@code <set>} 的 mapper 方法都跑一遍真 PG，
 * 让「动态拼接出来的 SQL 是不是合法 SQL」不再靠运气。
 */
@Testcontainers
class CommentTransactionalSqlGateTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("first_db")
            .withUsername("admin")
            .withPassword("password");

    private static final String LEDGER = "flyway_schema_history_comment_sql_gate";

    private static JdbcTemplate jdbc;
    private static SubjectMapper subjectMapper;
    private static CommentIndexMapper indexMapper;

    @BeforeAll
    static void setUp() {
        String url = PG.getJdbcUrl() + "?TimeZone=Asia/Shanghai";
        Flyway flyway = Flyway.configure()
                .dataSource(url, PG.getUsername(), PG.getPassword())
                .locations("classpath:db/migration")
                .schemas("public")
                .table(LEDGER)
                .baselineOnMigrate(true)
                .load();
        flyway.migrate();

        DataSource dataSource = new DriverManagerDataSource(url, PG.getUsername(), PG.getPassword());
        jdbc = new JdbcTemplate(dataSource);

        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment("sql-gate",
                new SpringManagedTransactionFactory(), dataSource));
        configuration.addMapper(SubjectMapper.class);
        configuration.addMapper(CommentIndexMapper.class);
        SqlSessionFactory factory = new SqlSessionFactoryBuilder().build(configuration);
        SqlSessionTemplate template = new SqlSessionTemplate(factory);
        subjectMapper = template.getMapper(SubjectMapper.class);
        indexMapper = template.getMapper(CommentIndexMapper.class);
    }

    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM subject");
        jdbc.update("DELETE FROM comment_index");
    }

    // ====================================================================================
    // ① SubjectMapper.decrementCounts —— 修复前的「<if> 拼在 WHERE 之后」缺陷
    // ====================================================================================

    @Test
    @DisplayName("🔴 decrementCounts(rootCountDecrement=true)：根评论删除 ⇒ total/root 计数都减 1")
    void decrementCountsDecrementsRootCountForRootComment() {
        seedSubject("article", "a-root", 3, 2);

        // 修复前这里会抛 PG 的 syntax error（"，" 位置）
        assertThatCode(() -> subjectMapper.decrementCounts("article", "a-root", true))
                .doesNotThrowAnyException();

        assertCounts("a-root", 2L, 1L);
    }

    @Test
    @DisplayName("🔴 decrementCounts(rootCountDecrement=false)：非根评论删除 ⇒ 只减 total")
    void decrementCountsLeavesRootCountForReply() {
        seedSubject("article", "a-reply", 3, 2);

        assertThatCode(() -> subjectMapper.decrementCounts("article", "a-reply", false))
                .doesNotThrowAnyException();

        assertCounts("a-reply", 2L, 2L);
    }

    // ====================================================================================
    // ② SubjectMapper.upsertCommentSubject —— ON CONFLICT 分支的 <if>
    // ====================================================================================

    @Test
    @DisplayName("🔴 upsertCommentSubject(rootCount=1)：首次插入 total/root 都是 1")
    void upsertSubjectCreatesRootCounts() {
        assertThatCode(() -> subjectMapper.upsertCommentSubject("article", "a-up-1", 1, 5L))
                .doesNotThrowAnyException();

        assertCounts("a-up-1", 1L, 1L);
    }

    @Test
    @DisplayName("🔴 upsertCommentSubject(rootCount=0)：首次插入只有 total=1，root 保持 0")
    void upsertSubjectCreatesReplyWithoutRootCount() {
        assertThatCode(() -> subjectMapper.upsertCommentSubject("article", "a-up-2", 0, 5L))
                .doesNotThrowAnyException();

        assertCounts("a-up-2", 1L, 0L);
    }

    @Test
    @DisplayName("🔴 upsertCommentSubject(rootCount=1)：冲突分支只加 root_count（rootCount=0 时不加）")
    void upsertSubjectOnConflictAddsRootCountOnlyForRoot() {
        seedSubject("article", "a-up-3", 1, 1);
        subjectMapper.upsertCommentSubject("article", "a-up-3", 1, 5L);
        assertCounts("a-up-3", 2L, 2L);

        subjectMapper.upsertCommentSubject("article", "a-up-3", 0, 5L);
        assertCounts("a-up-3", 3L, 2L);
    }

    // ====================================================================================
    // ③ CommentIndexMapper 的动态 SET
    // ====================================================================================

    @Test
    @DisplayName("🔴 incrementParentReplyCount(markAuthorReplied=true)：置 attribute 第 1 位")
    void incrementParentReplyCountSetsAuthorRepliedBit() {
        jdbc.update("INSERT INTO comment_index (id, target_type, target_id, user_id, root_id, parent_id) "
                + "VALUES (7101, 'article', 'a1', 7, 0, 0)");
        jdbc.update("INSERT INTO comment_index (id, target_type, target_id, user_id, root_id, parent_id) "
                + "VALUES (7102, 'article', 'a1', 7, 7101, 7101)");

        assertThatCode(() -> indexMapper.incrementParentReplyCount(7101, true)).doesNotThrowAnyException();

        assertThat(jdbc.queryForObject("SELECT attribute FROM comment_index WHERE id=7101", Long.class))
                .isEqualTo(2L);
        assertThat(jdbc.queryForObject("SELECT reply_count FROM comment_index WHERE id=7101", Long.class))
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("🔴 incrementParentReplyCount(markAuthorReplied=false)：不动 attribute")
    void incrementParentReplyCountWithoutAuthorRepliedBit() {
        jdbc.update("INSERT INTO comment_index (id, target_type, target_id, user_id, root_id, parent_id) "
                + "VALUES (7103, 'article', 'a1', 7, 0, 0)");

        assertThatCode(() -> indexMapper.incrementParentReplyCount(7103, false)).doesNotThrowAnyException();

        assertThat(jdbc.queryForObject("SELECT attribute FROM comment_index WHERE id=7103", Long.class))
                .isZero();
    }

    @Test
    @DisplayName("🔴 applyInteractionDeltas：点赞 +1 / 置位 / 取消赞清位（<set> 动态拼接）")
    void applyInteractionDeltasAppliesLikeAndAttributeBits() {
        jdbc.update("INSERT INTO comment_index (id, target_type, target_id, user_id, root_id, parent_id) "
                + "VALUES (7104, 'article', 'a1', 7, 0, 0)");

        // 点赞：like_count +1 且（作者）置 attribute 第 0 位
        assertThatCode(() -> indexMapper.applyInteractionDeltas(7104, 1L, 0L, true, false))
                .doesNotThrowAnyException();
        assertThat(jdbc.queryForObject("SELECT like_count FROM comment_index WHERE id=7104", Long.class))
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT attribute FROM comment_index WHERE id=7104", Long.class))
                .isEqualTo(1L);

        // 取消赞：like_count -1 且清 attribute 第 0 位（bitwise NOT，不是减法）
        assertThatCode(() -> indexMapper.applyInteractionDeltas(7104, -1L, 0L, false, true))
                .doesNotThrowAnyException();
        assertThat(jdbc.queryForObject("SELECT like_count FROM comment_index WHERE id=7104", Long.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT attribute FROM comment_index WHERE id=7104", Long.class))
                .isZero();
    }

    @Test
    @DisplayName("🔴 applyInteractionDeltas：差量为 0 时不写对应列（Go 的 len(updateCols)>0 语义）")
    void applyInteractionDeltasSkipsZeroDeltas() {
        jdbc.update("INSERT INTO comment_index (id, target_type, target_id, user_id, root_id, parent_id, "
                + "like_count, dislike_count) VALUES (7105, 'article', 'a1', 7, 0, 0, 5, 3)");

        assertThatCode(() -> indexMapper.applyInteractionDeltas(7105, 0L, 0L, false, false))
                .doesNotThrowAnyException();

        assertThat(jdbc.queryForObject("SELECT like_count FROM comment_index WHERE id=7105", Long.class))
                .isEqualTo(5L);
        assertThat(jdbc.queryForObject("SELECT dislike_count FROM comment_index WHERE id=7105", Long.class))
                .isEqualTo(3L);
    }

    @Test
    @DisplayName("🔴 markDeleted：软删除置 state=2（不是物理删除），updated_at 前进")
    void markDeletedSoftDeletes() {
        jdbc.update("INSERT INTO comment_index (id, target_type, target_id, user_id, root_id, parent_id, "
                + "created_at) VALUES (7106, 'article', 'a1', 7, 0, 0, now() - interval '1 hour')");

        assertThatCode(() -> indexMapper.markDeleted(7106)).doesNotThrowAnyException();

        assertThat(jdbc.queryForObject("SELECT state FROM comment_index WHERE id=7106", Integer.class))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM comment_index WHERE id=7106", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT updated_at > created_at FROM comment_index WHERE id=7106", Boolean.class)).isTrue();
    }

    // ====================================================================================

    private static void seedSubject(String targetType, String targetId, long total, long root) {
        jdbc.update("INSERT INTO subject (target_type, target_id, total_count, root_count, state, attribute, owner_id) "
                + "VALUES (?, ?, ?, ?, 0, 0, 2002)", targetType, targetId, total, root);
    }

    /** 断言 subject 行的 [total_count, root_count]。 */
    private static void assertCounts(String targetId, long total, long root) {
        java.util.Map<String, Object> row = jdbc.queryForMap(
                "SELECT total_count, root_count FROM subject WHERE target_id = ?", targetId);
        assertThat(((Number) row.get("total_count")).longValue()).isEqualTo(total);
        assertThat(((Number) row.get("root_count")).longValue()).isEqualTo(root);
    }
}
