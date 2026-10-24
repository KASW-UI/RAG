package com.rag.article.article.rpc;

import com.rag.article.article.rpc.domain.ArticleEntity;
import com.rag.article.article.rpc.mapper.ArticleMapper;
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

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 🔴 MyBatis-Plus {@code updateStrategy = NOT_NULL} 的写入语义守门 ——
 * 与 Go 侧 GORM {@code Save}（{@code Selects="*"}）逐条对拍。
 *
 * <h2>这条为什么值得单独守</h2>
 *
 * <p>台账里那条「未展开」的相邻差异原本写成：
 * <blockquote>
 * Go 的 {@code Save} 走 {@code finisher_api.go} 的 {@code Selects = append(Selects, "*")}
 * ——「update 时使用<b>全部</b>字段，含零值字段」；
 * MP {@code updateById} 默认 {@code updateStrategy = NOT_NULL}，<b>只写非 null 字段</b>。
 * ⇒ 一个「把某列显式置为零值」的更新，两侧结果不同。
 * </blockquote>
 *
 * <p>⚠️ <b>这个推理是错的</b>，而且错在关键处：
 * MP 的 {@code NOT_NULL} 跳过的只是 <b>null</b>，
 * <b>零值（{@code ""} / {@code 0} / {@code false}）照样会被写</b>
 * ⇒ 两侧在零值上<b>本来就一致</b>。
 *
 * <h2>Go 侧探针（gorm v1.31.1 + 真 PG，模块 probe/save）</h2>
 * <pre>
 * 探针 A：只改 title 后 Save（实体从库里读出）
 *   ⇒ secondary_tags / ext_info 被**原样写回**（不写 null）
 * 探针 B：把 SecondaryTags / ExtInfo 显式置 nil 后 Save
 *   ⇒ 写成 **jsonb 'null'**（**不是** SQL NULL）；读回是 nil
 * 探针 C：把 Brief 置空串、ViewCount 置 0 后 Save（零值）
 *   ⇒ 写成 **''/0**（不是 NULL，也不是「跳过」）
 * 探针 D：库里的 jsonb 列本来就是 SQL NULL，再 Save 一次
 *   ⇒ SQL NULL 被**升级成 jsonb 'null'**；但**读回的值不变**（都是 nil）
 * </pre>
 *
 * <h2>本类逐条对拍这四个探针结论</h2>
 * <ol>
 *   <li>零值<b>照样写</b>（对拍探针 C）⇒ 台账那句「只写非 null ⇒ 零值被跳过」被推翻；</li>
 *   <li>没碰的 jsonb 列<b>原样写回</b>（对拍探针 A）；</li>
 *   <li>null 的 jsonb 字段<b>被跳过</b>（Go 侧会写 jsonb 'null'）⇒ <b>存储表示不同</b>；</li>
 *   <li>但<b>读回的值与 Go 完全一致</b>（对拍探针 D）⇒ <b>该差异不可观察</b>。</li>
 * </ol>
 *
 * <h2>为什么「不可观察」不是「不存在」</h2>
 * <p>第 3 条与第 4 条合起来才是完整结论：
 * <b>存储表示</b>（SQL NULL vs jsonb {@code 'null'}）确实不同，
 * 但两侧<b>读回都是 null</b>
 * （Go 的 {@code JSONMap.Scan} / {@code StringArray.Scan} 与 RAG 的
 * {@code JsonbTypeHandler.readMap/readList} 都把 SQL NULL、空白、JSON {@code "null"}
 * <b>一律读成 null</b>）⇒ 过滤条件 {@code secondary_tags @> CAST(...)}
 * 也不受影响（SQL NULL ⇒ NULL、jsonb 'null' ⇒ false，<b>两者都把行排除掉</b>）
 * ⇒ <b>API 层面不可观察</b>。
 *
 * <h2>本类的可证伪性</h2>
 * <p>把实体上的 {@code @TableField(updateStrategy = ...)} 改成 {@code IGNORED}
 * 或 {@code ALWAYS}，第 1 / 3 条应立刻红。
 * 若仍然全绿，说明断言没真的落在列上，本类即刻失去价值。
 * （AGENTS.md §3.3.3「绿测试可能是错误答案的守卫，比没有测试更危险」。）
 *
 * @see JsonbTypeHandler#readList(String)
 * @see JsonbTypeHandler#readMap(String)
 */
@SpringBootTest(
        classes = ArticleRpcApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "grpc.server.port=0",
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN",
                "grpc.client.security-rpc.address=static://127.0.0.1:1",
                "grpc.client.message-rpc.address=static://127.0.0.1:1",
                "spring.kafka.bootstrap-servers=127.0.0.1:1",
                // ⚠️ 四个通道的 brokers 键没有消费点（客户端是全局单例）⇒ 必须与全局同值，
                //    否则 ArticleKafkaChannelValidator 会在启动时因分叉而让上下文起不来。
                "article-rpc.kq-pusher-conf.brokers=127.0.0.1:1",
                "article-rpc.kq-consumer-conf.brokers=127.0.0.1:1",
                "article-rpc.article-sync-pusher-conf.brokers=127.0.0.1:1",
                "article-rpc.article-sync-result-consumer-conf.brokers=127.0.0.1:1"
        })
@Testcontainers
@org.testcontainers.junit.jupiter.EnabledIfDockerAvailable
class ArticleNotNullUpdateStrategyWiringTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("article_rpc_notnull_it")
                    .withUsername("rag")
                    .withPassword("rag");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private ArticleMapper articleMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clean() {
        jdbcTemplate.update("TRUNCATE TABLE article CASCADE");
    }

    // ==================================================================
    // ① 零值照样写（对拍 Go 探针 C）—— 推翻台账那句「零值被跳过」
    // ==================================================================

    @Test
    @DisplayName("🔴 零值（空串 / 0）**照样被写**（NOT_NULL 只跳过 null，不跳过零值）—— 与 Go 探针 C 一致")
    void zeroValuesAreWrittenJustLikeGoSave() {
        seed("nn-p1", "'brief-p1'", 7, "'[\"old\"]'::jsonb", "'{\"k\":\"v\"}'::jsonb");

        ArticleEntity a = articleMapper.selectById("nn-p1");
        assertNotNull(a, "种子行读不到");
        assertEquals("brief-p1", a.getBrief());
        assertEquals(7, a.getViewCount());

        // 把两个字段置成**零值**（不是 null）
        a.setBrief("");
        a.setViewCount(0);
        articleMapper.updateById(a);

        // ⭐ Go 探针 C：Save 把零值写成 ''/0 —— 不是 NULL，也不是「跳过」
        assertEquals("", jdbcTemplate.queryForObject(
                "SELECT brief FROM article WHERE id = 'nn-p1'", String.class),
                "brief 的零值必须被写下去（Go 侧 Save 写的是 ''）"
                        + "；若仍是旧值，说明 MP 把零值也跳过了 ⇒ 与 Go 不一致");
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT view_count FROM article WHERE id = 'nn-p1'", Integer.class),
                "view_count 的零值必须被写下去（Go 侧 Save 写的是 0）");

        // 读回也必须一致
        ArticleEntity after = articleMapper.selectById("nn-p1");
        assertEquals("", after.getBrief());
        assertEquals(0, after.getViewCount());
    }

    // ==================================================================
    // ② 没碰的 jsonb 列原样写回（对拍 Go 探针 A）
    // ==================================================================

    @Test
    @DisplayName("🔴 没碰过的 jsonb 列被**原样写回**（不被写成 null）—— 与 Go 探针 A 一致")
    void untouchedJsonbColumnsAreWrittenBackUnchanged() {
        seed("nn-p2", "'brief-p2'", 3, "'[\"old\"]'::jsonb", "'{\"k\":\"v\"}'::jsonb");

        ArticleEntity a = articleMapper.selectById("nn-p2");
        assertNotNull(a);
        a.setTitle("title-A");
        articleMapper.updateById(a);

        assertEquals("[\"old\"]", jdbcTemplate.queryForObject(
                "SELECT secondary_tags::text FROM article WHERE id = 'nn-p2'", String.class),
                "secondary_tags 必须原样写回（Go 探针 A 同结论）");
        assertEquals("{\"k\": \"v\"}", jdbcTemplate.queryForObject(
                "SELECT ext_info::text FROM article WHERE id = 'nn-p2'", String.class),
                "ext_info 必须原样写回");

        ArticleEntity after = articleMapper.selectById("nn-p2");
        assertEquals(List.of("old"), after.getSecondaryTags());
        assertEquals(Map.of("k", "v"), after.getExtInfo());
    }

    // ==================================================================
    // ③ null 的 jsonb 字段被跳过（Go 侧会写 jsonb 'null'）⇒ 存储表示不同
    // ==================================================================

    @Test
    @DisplayName("🔴 实体的 jsonb 字段为 null ⇒ 该列被**跳过**（Go 侧会写 jsonb 'null' ⇒ 存储表示不同）")
    void nullJsonbFieldsAreSkippedInsteadOfWrittenAsJsonNull() {
        seed("nn-p3", "'brief-p3'", 1, "'[\"old\"]'::jsonb", "'{\"k\":\"v\"}'::jsonb");

        ArticleEntity a = articleMapper.selectById("nn-p3");
        assertNotNull(a);
        assertEquals(List.of("old"), a.getSecondaryTags());

        a.setSecondaryTags(null);
        a.setExtInfo(null);
        a.setTitle("title-p3");
        articleMapper.updateById(a);

        // ⭐ 与 Go 探针 B 的**存储表示**差异：Go 写 jsonb 'null'，RAG 跳过 ⇒ 列保持原值
        assertEquals("[\"old\"]", jdbcTemplate.queryForObject(
                "SELECT secondary_tags::text FROM article WHERE id = 'nn-p3'", String.class),
                "RAG 侧 null ⇒ 该列被跳过（保持原值）；Go 侧会写成 jsonb 'null'"
                        + " ⇒ 这是**存储表示**层面的差异（第 ④ 条会证明它不可观察）");
        assertEquals("{\"k\": \"v\"}", jdbcTemplate.queryForObject(
                "SELECT ext_info::text FROM article WHERE id = 'nn-p3'", String.class),
                "ext_info 同理");
    }

    // ==================================================================
    // ④ 读回值与 Go 完全一致 ⇒ 该差异**不可观察**
    // ==================================================================

    @Test
    @DisplayName("🔴 库里的 jsonb 列本就是 SQL NULL 时：RAG 保持 SQL NULL，但**读回与 Go 完全一致** ⇒ 不可观察")
    void sqlNullJsonbColumnsReadBackIdenticallyToGo() {
        // 显式种成 SQL NULL（不是 jsonb 'null'）
        jdbcTemplate.update("""
                INSERT INTO article (id, title, brief, content, secondary_tags, ext_info,
                                     status, view_count, created_at, updated_at)
                VALUES ('nn-p4', 't4', 'brief-p4', 'content-p4', NULL, NULL, 0, 0, now(), now())
                """);

        ArticleEntity a = articleMapper.selectById("nn-p4");
        assertNotNull(a);
        assertNull(a.getSecondaryTags(), "SQL NULL 必须被读成 null");
        assertNull(a.getExtInfo(), "SQL NULL 必须被读成 null");

        a.setTitle("title-p4");
        articleMapper.updateById(a);

        // 存储表示：RAG 保持 SQL NULL（Go 探针 D 会把它升级成 jsonb 'null'）
        assertTrue(jdbcTemplate.queryForObject(
                "SELECT secondary_tags IS NULL FROM article WHERE id = 'nn-p4'", Boolean.class),
                "RAG 侧 null ⇒ 列被跳过 ⇒ 仍是 SQL NULL（Go 探针 D 是 jsonb 'null'）");
        assertTrue(jdbcTemplate.queryForObject(
                "SELECT ext_info IS NULL FROM article WHERE id = 'nn-p4'", Boolean.class),
                "ext_info 同理");

        // ⭐ 但**读回的值**与 Go 探针 D 完全一致（都是 nil）⇒ API 层面不可观察
        ArticleEntity after = articleMapper.selectById("nn-p4");
        assertNull(after.getSecondaryTags(),
                "⭐ 读回必须与 Go 一致（Go 侧读回也是 nil）⇒ 这条存储差异**不可观察**");
        assertNull(after.getExtInfo(),
                "⭐ 读回必须与 Go 一致 ⇒ 这条存储差异**不可观察**");
    }

    /**
     * 🔴 <b>过滤面也不受影响</b> —— 补齐「不可观察」的另一半。
     *
     * <p>{@code secondary_tags @> CAST('["x"]' AS jsonb)}：
     * <ul>
     *   <li>列是 <b>SQL NULL</b> ⇒ 表达式为 NULL ⇒ <b>行不匹配</b>；</li>
     *   <li>列是 <b>jsonb 'null'</b> ⇒ {@code 'null' @> '["x"]'} = false ⇒ <b>行不匹配</b>。</li>
     * </ul>
     * ⇒ 两种存储表示在<b>过滤</b>上结果相同 ⇒ 「不可观察」的结论对列表接口同样成立。
     */
    @Test
    @DisplayName("🔴 过滤面同样不可观察：SQL NULL 与 jsonb 'null' 都把行排除掉")
    void filterBehaviourIsIdenticalForSqlNullAndJsonNull() {
        // 一行：secondary_tags 是 SQL NULL
        jdbcTemplate.update("""
                INSERT INTO article (id, title, brief, content, secondary_tags, ext_info,
                                     status, view_count, created_at, updated_at)
                VALUES ('nn-p5', 't5', 'b5', 'c5', NULL, '{}'::jsonb, 0, 0, now(), now())
                """);
        // 另一行：secondary_tags 是 jsonb 'null'（Go 探针 D 存下来的那种）
        jdbcTemplate.update("""
                INSERT INTO article (id, title, brief, content, secondary_tags, ext_info,
                                     status, view_count, created_at, updated_at)
                VALUES ('nn-p6', 't6', 'b6', 'c6', 'null'::jsonb, '{}'::jsonb, 0, 0, now(), now())
                """);

        Integer sqlNullHits = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM article WHERE id = 'nn-p5' "
                        + "AND secondary_tags @> CAST('[\"x\"]' AS jsonb)", Integer.class);
        Integer jsonNullHits = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM article WHERE id = 'nn-p6' "
                        + "AND secondary_tags @> CAST('[\"x\"]' AS jsonb)", Integer.class);
        assertEquals(0, sqlNullHits,
                "SQL NULL 行的过滤结果必须是「不匹配」（表达式为 NULL）");
        assertEquals(0, jsonNullHits,
                "jsonb 'null' 行的过滤结果必须是「不匹配」（'null' @> '[\"x\"]' = false）"
                        + " ⇒ 两种存储表示在过滤面上**结果相同** ⇒ 差异不可观察");
    }

    // ==================================================================
    // 工具
    // ==================================================================

    /**
     * 种一行。
     *
     * <p>⚠️ jsonb 列的值必须<b>内联</b>（{@code '["old"]'::jsonb}）——
     * 用占位符传进去会变成 varchar ⇒ PG 报
     * {@code column "secondary_tags" is of type jsonb but expression is of type character varying}。
     */
    private void seed(String id, String brief, int viewCount, String tags, String ext) {
        jdbcTemplate.update("""
                INSERT INTO article (id, title, brief, content, secondary_tags, ext_info,
                                     status, view_count, created_at, updated_at)
                VALUES ('%s', 'title-%s', %s, 'content-%s', %s, %s, 0, %d, now(), now())
                """.formatted(id, id, brief, id, tags, ext, viewCount));
    }
}
