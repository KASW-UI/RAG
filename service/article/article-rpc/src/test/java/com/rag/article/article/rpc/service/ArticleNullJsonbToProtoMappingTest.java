package com.rag.article.article.rpc.service;

import com.rag.article.article.proto.Article;
import com.rag.article.article.rpc.domain.ArticleEntity;
import com.rag.article.article.rpc.mapper.ArticleMapper;
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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 🔴 <b>「Go 创建的行」被 RAG 读</b> —— null 的 jsonb 列 ⇒ 映射成 proto ⇒ wire 上的表现。
 *
 * <h2>为什么这条场景真实存在</h2>
 *
 * <p>Go 侧创建文章时：
 * <pre>{@code
 * // service/article/rpc/internal/logic/create_article_logic.go:94
 * SecondaryTags: model.StringArray(in.SecondaryTags),
 * }</pre>
 * ⇒ 请求不带二级标签时，这里是<b>直接类型转换</b>（不是 {@code append(nil, ...)}）
 * ⇒ 结果是 <b>nil slice</b>
 * ⇒ {@code StringArray.Value()} = {@code json.Marshal(nil)} = {@code "null"}
 * ⇒ <b>落库就是 jsonb {@code 'null'}</b>。
 *
 * <p>⇒ 灰度期两侧写<b>同一张表</b>时，Go 建的行在 RAG 侧读到的是
 * {@code secondary_tags = 'null'::jsonb} ⇒ RAG 的 {@code JsonbTypeHandler.readList}
 * 把它读成 <b>null</b>（{@code "null".equals(json)} ⇒ null）。
 *
 * <h2>Go 侧同一行的 wire 表现（对照基准）</h2>
 * <pre>{@code
 * // internal/logic/get_article_logic.go:112
 * SecondaryTags: article.SecondaryTags,           // nil ⇒ proto3 repeated ⇒ 空
 * // internal/logic/get_article_logic.go:191-201
 * func cloneStringMap(source map[string]string) map[string]string {
 *     if len(source) == 0 { return nil }             // 空 map ⇒ nil ⇒ proto3 map ⇒ 空
 *     ...
 * }
 * }</pre>
 * ⇒ Go 侧：nil / 空 ⇒ <b>proto3 的 {@code repeated} / {@code map} 没有 presence</b>
 * ⇒ <b>wire 上就是「字段不存在」</b>。
 *
 * <h2>本类要证明的事</h2>
 * <ol>
 *   <li>RAG 侧读 jsonb {@code 'null'} 的行 ⇒ {@code secondaryTags == null}；</li>
 *   <li>映射成 proto 时<b>不抛 NPE</b>（{@code protobuf 的 addAll(null) 会 NPE}）；</li>
 *   <li>映射结果的 <b>wire 字节</b>与「proto3 里没有这两个字段」<b>逐字节相同</b>
 *       ⇒ 与 Go 侧<b>完全一致</b>。</li>
 * </ol>
 *
 * <h2>为什么第 3 条要用「字节」而不是「集合相等」</h2>
 * <p>「空集合」相等只能证明<b>Java 层</b>一样；proto3 的 {@code repeated} / {@code map}
 * <b>没有 presence</b> ⇒ 「空集合」与「null」在 wire 上<b>本来就无法区分</b>。
 * ⇒ 要证明「与 Go 一样」，必须比<b>字节</b>。
 *
 * <h2>可证伪性</h2>
 * <p>把 {@code ArticleDomainService.toAuthorProtoArticle} 里的两个 null 守卫删掉
 * （{@code if (a.getSecondaryTags() != null)} / {@code if (a.getExtInfo() != null && !...isEmpty())}）
 * ⇒ 本类第 2 条应当立刻红（NPE）。
 *
 * @see JsonbTypeHandler#readList(String)
 * @see ArticleDomainService#toAuthorProtoArticle
 */
@SpringBootTest(
        classes = com.rag.article.article.rpc.ArticleRpcApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN",
                "grpc.client.security-rpc.address=static://127.0.0.1:1",
                "grpc.client.message-rpc.address=static://127.0.0.1:1",
                "spring.kafka.bootstrap-servers=127.0.0.1:1",
                "article-rpc.kq-pusher-conf.brokers=127.0.0.1:1",
                "article-rpc.kq-consumer-conf.brokers=127.0.0.1:1",
                "article-rpc.article-sync-pusher-conf.brokers=127.0.0.1:1",
                "article-rpc.article-sync-result-consumer-conf.brokers=127.0.0.1:1"
        })
@Testcontainers
@org.testcontainers.junit.jupiter.EnabledIfDockerAvailable
class ArticleNullJsonbToProtoMappingTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("article_rpc_nulljsonb_it")
                    .withUsername("rag")
                    .withPassword("rag")
                    .withCreateContainerCmdModifier(RealContainerHostPort.bindPg(RealContainerHostPort.NULL_JSONB_TO_PROTO_MAPPING));

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
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clean() {
        jdbcTemplate.update("TRUNCATE TABLE article CASCADE");
    }

    // ==================================================================
    // ① Go 创建的行（secondary_tags = jsonb 'null'）⇒ 读回 null
    // ==================================================================

    @Test
    @DisplayName("🔴 Go 创建的行（secondary_tags = jsonb 'null'）在 RAG 侧读回 **null**")
    void goCreatedRowWithJsonNullTagsReadsBackAsNull() {
        // ⚠️ 这正是 Go 侧 create_article_logic.go:94 的落库形态：
        //    model.StringArray(nil) ⇒ json.Marshal(nil) ⇒ "null" ⇒ jsonb 'null'
        jdbcTemplate.update("""
                INSERT INTO article (id, title, brief, content, secondary_tags, ext_info,
                                     status, view_count, created_at, updated_at)
                VALUES ('go-null-1', 't1', 'b1', 'c1', 'null'::jsonb, '{}'::jsonb, 0, 0, now(), now())
                """);

        ArticleEntity a = articleMapper.selectById("go-null-1");
        assertNull(a.getSecondaryTags(),
                "jsonb 'null' 必须被读成 null（readList 的 \"null\".equals(json) 分支）");
        // ext_info 是 '{}'（非 null）⇒ 读成**空 map**（不是 null）
        assertEquals(java.util.Map.of(), a.getExtInfo(),
                "'{}' 读成空 map（不是 null）—— 这是 Go create 路径写 ext_info 的形态（populate 过的 map）");
    }

    // ==================================================================
    // ② 映射成 proto：null ⇒ 不抛 NPE，且字段为空
    // ==================================================================

    @Test
    @DisplayName("🔴 实体的 secondaryTags / extInfo 为 null ⇒ 映射成 proto **不抛 NPE**，且两字段为空")
    void nullEntityFieldsMapToEmptyProtoFieldsWithoutThrowing() {
        ArticleEntity a = new ArticleEntity();
        a.setId("go-null-2");
        a.setTitle("t2");
        a.setBrief("b2");
        a.setContent("c2");
        a.setSecondaryTags(null);
        a.setExtInfo(null);

        // ⚠️ protobuf 的 addAll(null) / putAll(null) 会 NPE ⇒ 这里必须有 null 守卫
        Article mapped = assertDoesNotThrow(() -> ArticleDomainService.toAuthorProtoArticle(a, "md"),
                "实体的 jsonb 字段为 null 时不得抛 NPE"
                        + "（Go 侧 nil ⇒ proto3 repeated/map 为空，不抛）");

        assertTrue(mapped.getSecondaryTagsList().isEmpty(),
                "null 的 secondaryTags ⇒ proto 里必须是空（Go 侧 nil ⇒ 同样为空）");
        assertTrue(mapped.getExtInfoMap().isEmpty(),
                "null 的 extInfo ⇒ proto 里必须是空");
    }

    // ==================================================================
    // ③ wire 字节级比对（proto3 repeated/map 无 presence ⇒ 空 = 字段不存在）
    // ==================================================================

    @Test
    @DisplayName("🔴 wire **字节**与「proto3 里没有这两个字段」逐字节相同 ⇒ 与 Go 完全一致")
    void wireBytesAreIdenticalToProtoWithoutThoseFields() {
        jdbcTemplate.update("""
                INSERT INTO article (id, title, brief, content, secondary_tags, ext_info,
                                     status, view_count, created_at, updated_at)
                VALUES ('go-null-3', 't3', 'b3', 'c3', 'null'::jsonb, '{}'::jsonb, 0, 5, now(), now())
                """);

        ArticleEntity a = articleMapper.selectById("go-null-3");
        assertNull(a.getSecondaryTags());

        Article mapped = ArticleDomainService.toAuthorProtoArticle(a, "md");

        // 期望值：同样的标量字段，但**不带** secondary_tags / ext_info
        Article expected = Article.newBuilder()
                .setId(a.getId())
                .setTitle(a.getTitle())
                .setBrief(a.getBrief())
                .setMarkdownContent("md")
                .setCoverImageUrl("")
                .setManualTypeTag("")
                .setAuthorId("")
                .setCreateTime(a.getCreatedAt() == null ? 0L : a.getCreatedAt().toInstant().toEpochMilli())
                .setUpdateTime(a.getUpdatedAt() == null ? 0L : a.getUpdatedAt().toInstant().toEpochMilli())
                .setStatus(com.rag.article.article.proto.ArticleStatus.forNumber(a.getStatus()))
                .setViewCount(a.getViewCount())
                .setLikeCount(a.getLikeCount())
                .setCommentCount(a.getCommentCount())
                .setShareCount(a.getShareCount())
                .build();

        assertArrayEquals(expected.toByteArray(), mapped.toByteArray(),
                "🔴 wire 字节必须与「proto3 里没有 secondary_tags / ext_info」完全相同"
                        + " ⇒ 与 Go 侧（nil / 空 ⇒ 字段不存在）**逐字节一致** ⇒ 该差异在 wire 上不可观察");
    }
}
