package com.rag.article.article.rpc;

import com.rag.article.article.rpc.domain.ArticleEntity;
import com.rag.article.article.rpc.domain.ArticleRevisionEntity;
import com.rag.article.article.rpc.mapper.ArticleMapper;
import com.rag.article.article.rpc.mapper.ArticlePublicMapper;
import com.rag.article.article.rpc.mapper.ArticleRevisionMapper;
import com.rag.article.article.rpc.support.RealContainerHostPort;
import com.rag.article.article.rpc.support.RealGrpcPort;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 🔴 jsonb {@code @ResultMap} 守门测试 —— 证明 4 处<b>曾经静默返回 null</b>的自定义
 * {@code @Select} 现在真的把 jsonb 列映射进实体。
 *
 * <h2>为什么这类缺陷能一路绿灯</h2>
 *
 * <p>{@code secondary_tags}（{@code List<String>}）与 {@code ext_info}（{@code Map<String,String>}）
 * 在 MyBatis 的 TypeHandler<b>全局注册表里没有条目</b>。自定义 {@code @Select} 不带
 * {@code @ResultMap} 时走注解自动映射，它按「Java 类型 + JDBC 类型」去那张表里查 ——
 * 查不到就<b>静默跳过该属性</b>：<b>不抛异常、不打警告、SQL 全部成功</b>，
 * 实体字段恒为 {@code null}。
 *
 * <p>而 {@code @TableName(autoResultMap = true)} <b>救不了这里</b>：
 * 那张自动结果映射只挂在 MyBatis-Plus <b>自己生成</b>的方法上，
 * 一旦用 {@code @Select} <b>覆盖</b>同名方法，走的就是注解自动映射那条路。
 *
 * <p><b>业务后果不是「读不到」，是「静默早退」</b>：{@code extInfo} 整块为 null ⇒
 * {@code acceptSyncResult} 的 eventId / versionMs 判据<b>永远不成立</b> ⇒
 * 发布流程每次都在这一步 return，文章永远停在 REVIEWING。
 *
 * <h2>为什么必须真 PG（不能 mock、不能 H2）</h2>
 *
 * <p>要验证的是「真实驱动把 jsonb 取出来之后 TypeHandler 能不能接住」。
 * mock 数据层根本不执行 SQL；H2 没有 {@code JSONB} 类型也没有 {@code @>} 算子，
 * 换成它等于<b>把要验证的东西换没了</b>。
 *
 * <h2>本类的可证伪性</h2>
 *
 * <p>把 {@code ArticleMapper.selectAuthorView}（或 {@code ArticleRevisionMapper} 任意一个）
 * 上的 {@code @ResultMap} 删掉再跑本类，<b>应当立刻红</b>。
 * 若删掉仍然全绿，说明断言没真的落在字段上，本类即刻失去价值。
 * 这条纪律来自 AGENTS.md §3.3.3「绿测试可能是错误答案的守卫，比没有测试更危险」。
 *
 * @see JsonbTypeHandler 对 Go {@code StringArray} / {@code JSONMap} 的复刻说明
 */
@SpringBootTest(
        classes = ArticleRpcApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN",
                // ⚠️ devh 的 @GrpcClient 在 Bean 初始化期就建 channel（不是懒建），
                //    测试环境没有 Nacos ⇒ discovery:// 解析失败 ⇒ 上下文起不来。
                //    本类只验 mapper，**不调这两个 client**。
                "grpc.client.security-rpc.address=static://127.0.0.1:1",
                "grpc.client.message-rpc.address=static://127.0.0.1:1",
                // listener 容器启动时会尝试连 broker；本类不验证消费路径，
                // 用不可达地址让它惰性重试即可（不阻塞上下文启动）。
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
class ArticleJsonbResultMapWiringTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("article_rpc_jsonb_it")
                    .withUsername("rag")
                    .withPassword("rag")
                    .withCreateContainerCmdModifier(RealContainerHostPort.bindPg(RealContainerHostPort.JSONB_RESULT_MAP_WIRING));

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) {
        // 🔴 2026-10-28（G3）：gRPC server 端口走**本域专用段**（global 段表预分配给 article 域的 22400–22479）
        //    ⇒ 不用 `grpc.server.port=0`（0 ⇒ OS 从**临时端口段** 49152–65535 里挑
        //    ⇒ 而那是 **Docker 随机发布容器端口用的同一段** ⇒ 别的会话的容器先占住
        //    ⇒ `0.0.0.0:P` ⇒ 本类起 server 时 **EADDRINUSE** ⇒ **整个测试类启动失败**）
        //    ⇒ ⚠️ 这里**扩展已有的** @DynamicPropertySource 方法（不新增方法）
        //    ⇒ ⇒ `@DynamicPropertySource` 的**值**不进 Spring 的 context cache key
        //    ⇒ ⇒ 方法集合不变 ⇒ **上下文共享关系与迁移前逐字相同**（零行为变化）。
        r.add("grpc.server.port", RealGrpcPort::pick);
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private ArticleMapper articleMapper;
    @Autowired private ArticleRevisionMapper revisionMapper;
    @Autowired private ArticlePublicMapper publicMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    private static final String ARTICLE_ID = "jsonb-a1";
    private static final String REVISION_ID = "jsonb-a1:r1";
    /**
     * legacy 行：<b>没有</b> article_publication 行 + {@code status=2(PUBLISHED)}。
     * 它专门用来覆盖可见谓词的第二支 —— javadoc 明写「不要清理掉这一支，
     * 删掉它会让全部历史 PUBLISHED 文章从公开列表消失」（Go 测试
     * {@code article_public_test.go} 的 "legacy" 行专测这一点）。
     */
    private static final String LEGACY_ARTICLE_ID = "jsonb-legacy";

    /**
     * 🔴 静态容器在整个测试类生命周期内复用同一份 PG，<b>不清就是跨用例串数据</b>。
     *
     * <p>用 TRUNCATE 而非 DELETE：{@code article_revision} 上有 BEFORE DELETE 触发器
     * （{@code article_revision_immutable}），DELETE 会被它挡下来。
     */
    @BeforeEach
    void seedNonEmptyJsonb() {
        jdbcTemplate.execute("TRUNCATE article, article_sync_outbox, article_revision, "
                + "article_publication, article_domain_outbox RESTART IDENTITY CASCADE");

        // 刻意用**非空** jsonb：null / 空数组 / 空对象在“没映射上”和“真值是空”时读起来一样，
        // 只有非空值才能把「静默 null」与「真的读到空」区分开。
        jdbcTemplate.update("""
                INSERT INTO article (id, title, brief, content, cover_image_url, manual_type_tag,
                                     secondary_tags, author_id, status, view_count, like_count,
                                     comment_count, share_count, ext_info, created_at, updated_at)
                VALUES (?, 'title', 'brief', 'content.md', '', 'tag',
                        '["alpha","beta"]'::jsonb, '1001', 3, 0, 0, 0, 0,
                        '{"reco_sync_state":"pending","last_sync_event_id":"evt-1"}'::jsonb,
                        now(), now())
                """, ARTICLE_ID);

        jdbcTemplate.update("""
                INSERT INTO article_revision
                    (article_id, revision, revision_id, author_id, source_object, content_sha256,
                     title, brief, cover_image_url, manual_type_tag, secondary_tags,
                     markdown, published_at, sync_event_id)
                VALUES (?, 1, ?, '1001', 'community', 'sha', 'title', 'brief', '', 'tag',
                        '["gamma","delta"]'::jsonb, '# md', now(), 'sync-1')
                """, ARTICLE_ID, REVISION_ID);

        // selectRevisionsForPublished 的 join 条件要求 publication 指向这个 revision 且已发布
        jdbcTemplate.update("""
                INSERT INTO article_publication (article_id, current_revision, pointer_version,
                                                 state, last_event_id)
                VALUES (?, ?, 1, 'published', 'evt-1')
                """, ARTICLE_ID, REVISION_ID);

        // legacy 行：PUBLISHED(2) 且**从无** publication 行 ⇒ 走谓词第二支
        // (p.article_id IS NULL AND a.status = 2)，标签过滤落在 a.secondary_tags 上。
        jdbcTemplate.update("""
                INSERT INTO article (id, title, brief, content, cover_image_url, manual_type_tag,
                                     secondary_tags, author_id, status, view_count, like_count,
                                     comment_count, share_count, ext_info, created_at, updated_at)
                VALUES (?, 'legacy-title', 'brief', 'content.md', '', 'tag',
                        '["legacy-a"]'::jsonb, '1001', 2, 0, 0, 0, 0, '{}'::jsonb, now(), now())
                """, LEGACY_ARTICLE_ID);
    }

    // ==================================================================
    // 对照组：本来就声明了 resultMap 的方法 —— 确立「本测试能看见 jsonb」的基准
    // ==================================================================

    @Test
    @DisplayName("对照组 selectById：已声明 resultMap 的方法能读出 jsonb（非空值）")
    void selectByIdReadsJsonb() {
        ArticleEntity a = articleMapper.selectById(ARTICLE_ID);

        assertNotNull(a, "前置校验：文章应能查到");
        assertEquals(List.of("alpha", "beta"), a.getSecondaryTags(),
                "对照组失效 ⇒ 本类其余断言都失去意义（先怀疑装配，再怀疑被测代码）");
        assertNotNull(a.getExtInfo(), "ext_info 同理");
        assertEquals("pending", a.getExtInfo().get("reco_sync_state"));
    }

    @Test
    @DisplayName("对照组 selectByIdUnscoped / selectByIdForUpdate：jsonb 同样读得出")
    void unscopedAndForUpdateReadJsonb() {
        assertEquals(List.of("alpha", "beta"),
                articleMapper.selectByIdUnscoped(ARTICLE_ID).getSecondaryTags());
        assertEquals(List.of("alpha", "beta"),
                articleMapper.selectByIdForUpdate(ARTICLE_ID).getSecondaryTags());
    }

    // ==================================================================
    // 本轮修复点 1：ArticleMapper.selectAuthorView
    // ==================================================================

    @Test
    @DisplayName("修复点 selectAuthorView：作者视角列表不再把 secondary_tags 读成 null")
    void selectAuthorViewReadsJsonb() {
        List<ArticleEntity> rows = articleMapper.selectAuthorView(
                "tag", null, "1001", null, false, 10, 0);

        // 作者视角不受 publication/legacy 谓词约束，只看 deleted_at + 三个可选判据
        // ⇒ 两行种子（已发布 + legacy）都应出现
        assertEquals(2, rows.size(), "前置校验：种子数据应命中两行");
        ArticleEntity publishedRow = rows.stream()
                .filter(a -> ARTICLE_ID.equals(a.getId())).findFirst().orElseThrow();
        // 🔴 这条断言在修复前拿到的是 null
        assertEquals(List.of("alpha", "beta"), publishedRow.getSecondaryTags(),
                "selectAuthorView 丢了 @ResultMap ⇒ secondaryTags 恒为 null（静默早退的源头）");
        assertEquals("evt-1", publishedRow.getExtInfo().get("last_sync_event_id"));
    }

    // ==================================================================
    // 本轮修复点 2/3：ArticleRevisionMapper 的两个读方法
    // ==================================================================

    @Test
    @DisplayName("修复点 selectByRevisionId：单条 revision 的 secondary_tags 不再是 null")
    void selectByRevisionIdReadsJsonb() {
        ArticleRevisionEntity r = revisionMapper.selectByRevisionId(ARTICLE_ID, REVISION_ID);

        assertNotNull(r, "前置校验：revision 应能查到");
        // 🔴 修复前为 null
        assertEquals(List.of("gamma", "delta"), r.getSecondaryTags(),
                "selectByRevisionId 是 articleRevisionEntityResultMap 的**声明处**，"
                        + "丢了 @Results 则本方法与所有引用它的方法一起失效");
    }

    @Test
    @DisplayName("修复点 selectByArticleId：批量读 revision 的 secondary_tags 不再是 null")
    void selectByArticleIdReadsJsonb() {
        List<ArticleRevisionEntity> rows = revisionMapper.selectByArticleId(ARTICLE_ID);

        assertEquals(1, rows.size());
        assertEquals(List.of("gamma", "delta"), rows.get(0).getSecondaryTags(),
                "批量路径与单条路径必须同样能读出 jsonb —— 回滚断言要靠它数行");
    }

    // ==================================================================
    // 本轮修复点 4：ArticlePublicMapper.selectRevisionsForPublished（跨 Mapper 引用）
    // ==================================================================

    @Test
    @DisplayName("修复点 selectRevisionsForPublished：跨 Mapper 引用 resultMap 生效")
    void selectRevisionsForPublishedReadsJsonb() {
        List<ArticleRevisionEntity> rows =
                publicMapper.selectRevisionsForPublished(List.of(ARTICLE_ID));

        assertEquals(1, rows.size());
        // 🔴 修复前为 null：这条路径喂给公开列表的二级标签会整列空掉
        assertEquals(List.of("gamma", "delta"), rows.get(0).getSecondaryTags(),
                "跨 Mapper 引用 FQN 形式（...ArticleRevisionMapper.articleRevisionEntityResultMap）"
                        + "若拼错，MyBatis 启动即失败；若漏掉，则静默 null");
    }

    @Test
    @DisplayName("对照组 selectPublicById / selectPublicPage：jsonb 读得出")
    void publicReadsJsonb() {
        assertEquals(List.of("alpha", "beta"),
                publicMapper.selectPublicById(ARTICLE_ID).getSecondaryTags());
        List<ArticleEntity> page = publicMapper.selectPublicPage(null, null, null, null,
                false, 10, 0);
        // 两行：已发布 + legacy（谓词两个分支各一行）
        assertEquals(2, page.size(),
                "可见谓词两个分支各应命中一行：published 走 revision，legacy 走 article");
        assertEquals(2L, publicMapper.countPublic(null, null, null), "count 与 page 同口径");
    }

    // ==================================================================
    // 本轮新发现：ArticlePublicMapper 的 secondaryTag 参数绑定（生产可达的 100% 报错）
    // ==================================================================

    /**
     * 🔴 <b>本用例是被一次测试失败「撞」出来的，不是审出来的</b>：
     * 写上面那个对照组时顺手调了一次 {@code selectPublicPage}，直接抛
     * {@code BindingException: Parameter 'secondaryTag' not found}。
     *
     * <p><b>根因</b>：{@code @Param} 声明的是 {@code secondaryTagJson}
     * （为了避开 SQL 里的引号转义，见 {@link ArticleMapper#selectAuthorView} 的 javadoc），
     * 而 {@code <if test="secondaryTag != null …">} 判的却是另一个名字
     * ⇒ MyBatis 解析 script 时<b>找不到该参数</b>。
     *
     * <p><b>影响面</b>：{@code ArticlePublicDomainService:185/187} 同时调用
     * {@code countPublic} 与 {@code selectPublicPage} ⇒ <b>公开文章列表接口 100% 报错</b>。
     * 与 {@code selectAuthorView} 曾有的缺陷是<b>同一个</b>（那次修了，这次漏了）。
     *
     * <p><b>为什么以前的测试拿不到</b>：既有 101 条测试<b>没有一条</b>让 MyBatis 真的解析过
     * 这段 {@code <script>} —— 走 mock 的根本不建 SqlSource。
     * 这正是 AGENTS.md §3.3.3「必须配一条能真正触发该机制的测试」的直接反例：
     * {@code <script>} 里的参数名<b>是运行期才解析的</b>，静态检查与 mock 都测不到。
     *
     * <p><b>Go 侧对照</b>：{@code internal/model/article_public.go:76-84} ——
     * {@code if opt.SecondaryTag != "" { … AND r.secondary_tags @> ?::jsonb … }}，
     * 条件与占位符用的是<b>同一个变量</b>，不存在「判据名 ≠ 参数名」的第二种可能。
     */
    @Test
    @DisplayName("🔴 修复点 selectPublicPage/countPublic：标签过滤覆盖 published / legacy 两个分支")
    void publicSecondaryTagFilterDoesNotThrowBindingException() {
        // —— 分支 1：已发布（有 publication 行）——
        // 标签匹配的是 **revision** 的 tags（"读者看到的分类是批准时的值"，
        // Go internal/model/article_public.go:67-85）。种子 revision 的 tags 是 ["gamma","delta"]。
        List<ArticleEntity> byGamma = publicMapper.selectPublicPage(
                null, "[\"gamma\"]", null, null, false, 10, 0);
        assertEquals(1, byGamma.size(), "revision 含 gamma ⇒ 只命中已发布那一行（legacy 行没有 revision）");
        assertEquals(ARTICLE_ID, byGamma.get(0).getId());

        // —— 分支 2：legacy（无 publication 行 + status=2）——
        // 标签匹配落在 a.secondary_tags 上。种子 legacy 行是 ["legacy-a"]。
        List<ArticleEntity> byLegacyA = publicMapper.selectPublicPage(
                null, "[\"legacy-a\"]", null, null, false, 10, 0);
        assertEquals(1, byLegacyA.size(), "article 含 legacy-a ⇒ 只命中 legacy 那一行");
        assertEquals(LEGACY_ARTICLE_ID, byLegacyA.get(0).getId());

        // 反向：alpha 只在 article 上、gamma 只在 revision 上 —— 两支各走各的列，不可互串
        assertEquals(0, publicMapper.selectPublicPage(null, "[\"alpha\"]", null, null, false, 10, 0).size(),
                "alpha 不在 revision 上 ⇒ 已发布行不命中；legacy 行是 [\"legacy-a\"] 也不含 alpha");
        assertEquals(1, publicMapper.selectPublicPage(null, "[\"gamma\"]", null, null, false, 10, 0).size(),
                "gamma 不在 article 上 ⇒ legacy 行不命中；只有已发布行命中");

        // count 与 page 必须同口径 —— 否则分页器的 total 会和实际页内容对不上
        assertEquals(1L, publicMapper.countPublic(null, "[\"gamma\"]", null));
        assertEquals(1L, publicMapper.countPublic(null, "[\"legacy-a\"]", null));
        assertEquals(0L, publicMapper.countPublic(null, "[\"alpha\"]", null));

        // null 判据：最常见的调用形态（proto3 默认空串 → 归一为 null）也必须不报错
        assertEquals(2, publicMapper.selectPublicPage(null, null, null, null, false, 10, 0).size(),
                "secondaryTagJson=null 是最常见形态；判据名写错时它同样会抛 BindingException");

        // manualTypeTag 判据（与 secondaryTagJson 共用同一段拼接，同样曾被 <where> 语法错盖住）
        assertEquals(2, publicMapper.selectPublicPage("tag", null, null, null, false, 10, 0).size(),
                "两行的 manual_type_tag 都是 'tag' ⇒ 都命中");
        assertEquals(0, publicMapper.selectPublicPage("no-such-tag", null, null, null, false, 10, 0).size(),
                "不存在的 manualTypeTag ⇒ 0 行");

        // 三个判据可组合（Go 是链式 .Where()，由 GORM 以 AND 组合）
        assertEquals(1, publicMapper.selectPublicPage("tag", "[\"gamma\"]", "1001",
                null, false, 10, 0).size(), "三条件同时给：只有已发布那一行同时满足");
        assertEquals(0, publicMapper.selectPublicPage("tag", "[\"gamma\"]", "9999",
                null, false, 10, 0).size(), "加上不匹配的 authorId ⇒ 0 行");
    }
}