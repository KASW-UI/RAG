package com.rag.article.article.rpc.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 启动期校验 article 域的五张表与<b>不可变触发器</b>确实存在 —— 对应 Go 侧
 * {@code internal/model/db.go:61-71} 的触发器门禁：
 * <pre>
 * SELECT EXISTS(SELECT 1 FROM pg_trigger
 *   WHERE tgname='article_revision_immutable' AND tgrelid=to_regclass('article_revision'))
 * if !revisionSchemaReady {
 *     return nil, fmt.Errorf("community revision migration 002_community_revision.sql is required
 *                             before article RPC startup")
 * }
 * </pre>
 *
 * <h2>🔴 为什么这道闸<b>必须 fail-fast</b>（与 like-rpc 的 SchemaGate 不同）</h2>
 * like 域缺表只是"第一次点赞会失败"；而 article 域缺
 * {@code article_revision_immutable} 触发器意味着
 * <b>「已发布文章的字节不可变」这条保证被静默取消</b> ——
 * 进程照常启动、审核照常发布，但任何一次误 UPDATE 都能改写已发布正文，
 * <b>且不会有任何报错</b>。
 * <p>Go 侧对此 {@code return err} ⇒ 上层 {@code panic} ⇒ <b>进程不启动</b>。
 * 这是刻意的 fail-fast：宁可起不来，也不要在失去不可变保证的情况下运行。
 * ⇒ 本类<b>照搬 fail-fast</b>，这是它与 {@code LikeSchemaGate}「只告警不阻止」的<b>本质区别</b>，
 * 不是风格差异。
 *
 * <p><b>与 Flyway 的分工</b>：本类不是 Flyway 的替代品，而是它的<b>第二道防线</b>。
 * Boot 4 拆模块后，缺 {@code spring-boot-flyway} 时<b>启动日志里连一行 Flyway 都没有</b>
 * （不是报错，是根本没加载）⇒ 迁移永不执行 ⇒ 表与触发器都不存在
 * ⇒ 进程"看起来完全正常"⇒ 第一次真正执行 SQL 才炸。
 * message-rpc 就这么踩过（AGENTS.md DRIFT-MSG-006）。
 * 本类把「迁移到底跑没跑」变成<b>启动时的一条异常</b>。
 *
 * <h2>校验时机：{@link ApplicationReadyEvent}</h2>
 * 必须在上下文 refresh 之后（此时 Flyway 若生效已经跑完），
 * 又要早于任何业务流量。用 {@code ApplicationReadyEvent} 与 like-rpc 一致。
 */
@Component
public class ArticleSchemaGate {

    private static final Logger LOG = LoggerFactory.getLogger(ArticleSchemaGate.class);

    /** 本域 5 张表，与 {@code V8}/{@code V9}/{@code V10} 迁移脚本一一对应。 */
    private static final List<String> REQUIRED_TABLES = List.of(
            "article",
            "article_sync_outbox",
            "article_revision",
            "article_publication",
            "article_domain_outbox");

    /** Go {@code db.go:63-65} 的触发器名，不可改。 */
    private static final String IMMUTABILITY_TRIGGER = "article_revision_immutable";

    private final JdbcTemplate jdbcTemplate;

    public ArticleSchemaGate(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 校验表与触发器。
     *
     * @throws IllegalStateException 表缺失或触发器缺失 —— <b>阻止启动</b>，与 Go 的 panic 等价
     */
    @EventListener(ApplicationReadyEvent.class)
    public void verifySchema() {
        List<String> present;
        try {
            present = jdbcTemplate.queryForList(
                    "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
                    String.class);
        } catch (Exception e) {
            // ⚠️ 连 information_schema 都查不到 ⇒ 数据源本身不可用。
            //    Go 侧这等价于 NewArticleRepo 里 gorm.Open 失败 ⇒ panic。
            throw new IllegalStateException(
                    "article-rpc schema gate could not reach PostgreSQL; refusing to start", e);
        }

        List<String> missing = REQUIRED_TABLES.stream().filter(t -> !present.contains(t)).toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "article-rpc schema gate: missing tables " + missing
                            + " ⇒ Flyway migrations did not run. Check spring.flyway.enabled, "
                            + "locations=classpath:db/migration, and that the pom includes "
                            + "org.springframework.boot:spring-boot-flyway (required since Boot 4).");
        }

        Boolean triggerReady;
        try {
            // ⚠️ 与 Go db.go:62-67 逐字同一条查询（含 to_regclass 的存在性判断）
            triggerReady = jdbcTemplate.queryForObject("""
                    SELECT EXISTS(
                        SELECT 1 FROM pg_trigger
                        WHERE tgname = ?
                          AND tgrelid = to_regclass('article_revision')
                    )
                    """, Boolean.class, IMMUTABILITY_TRIGGER);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "article-rpc schema gate could not verify the article_revision immutability trigger", e);
        }

        if (!Boolean.TRUE.equals(triggerReady)) {
            // ⚠️ 与 Go 的错误文案对齐（Go: db.go:70）
            throw new IllegalStateException(
                    "community revision migration 002_community_revision.sql is required before "
                            + "article RPC startup (trigger " + IMMUTABILITY_TRIGGER + " not found)");
        }

        LOG.info("ArticleSchemaGate: all {} article tables and the {} immutability trigger are present",
                REQUIRED_TABLES.size(), IMMUTABILITY_TRIGGER);
    }
}