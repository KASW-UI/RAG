package com.rag.comment.comment.rpc.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 启动后校验 7 张 comment 表确实存在 —— 对应 Go 侧 {@code model/db.go:10-19} 的
 * {@code db.AutoMigrate(&Subject{}, &CommentContent{}, &CommentIndex{}, &CommentLike{},
 * &CommentDomainFactOutbox{}, &CommentFactStream{})} 及其失败即 {@code log.Fatalln} 的行为。
 *
 * <h2>⚠️ 为什么要有这道闸</h2>
 *
 * <p>Go 用 {@code AutoMigrate}，建表失败直接退出 —— 表与进程同生共死。
 * Java 侧改用 Flyway，于是引入了 Go 没有的新失败模式：
 * <b>Flyway 引擎在、但自动配置没加载</b> ⇒ 启动日志里<b>连一行 Flyway 都没有</b>
 * （不是报错，是根本没加载，Boot 4 拆模块后极易发生）⇒
 * 7 张表永不创建 ⇒ 进程看起来完全正常 ⇒ <b>第一次真正执行 SQL 时才炸</b>：
 * <pre>
 *   org.postgresql.util.PSQLException: ERROR: relation "subject" does not exist
 * </pre>
 *
 * <p>message-rpc 就是这么踩的（AGENTS.md DRIFT-MSG-006），like-rpc 补了这道闸
 * （{@code LikeSchemaGate.java:17-34}）。本域沿用。
 *
 * <p><b>不抛异常、不阻止启动</b>：与 Go 的差异说明如下 ——
 * Go 迁移失败会退出进程，而 Java 单 JVM 里还跑着 Kafka 消费与 gRPC。
 * 这里只<b>大声告警</b>让运维立刻看到；真的缺表时首条 SQL 自然会失败。
 * （若要改成 fail-fast，只需把 {@code LOG.error} 换成抛异常。）
 *
 * <p>⚠️ 表清单里<b>包含 report_record</b>，而它<b>不在</b> Go 的 AutoMigrate 列表中
 * （Go 侧这张表从不被建出，详见 {@code V5__create_comment_report_record.sql} 的偏离说明）。
 * 这里检查它是为了把「RAG 侧补建」这件事在启动日志里显式化。
 */
@Component
public class CommentSchemaGate {

    private static final Logger LOG = LoggerFactory.getLogger(CommentSchemaGate.class);

    /** 本域 7 张表，与 {@code V1..V8} 迁移脚本一一对应。 */
    private static final List<String> REQUIRED_TABLES = List.of(
            "subject",
            "comment_index",
            "comment_content",
            "comment_like",
            "report_record",
            "comment_domain_fact_outbox",
            "comment_fact_stream");

    private final JdbcTemplate jdbcTemplate;

    public CommentSchemaGate(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void verifySchema() {
        try {
            List<String> present = jdbcTemplate.queryForList(
                    "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
                    String.class);

            List<String> missing = REQUIRED_TABLES.stream().filter(t -> !present.contains(t)).toList();
            if (missing.isEmpty()) {
                LOG.info("CommentSchemaGate: all {} comment tables present", REQUIRED_TABLES.size());
                return;
            }
            LOG.error("""
                    CommentSchemaGate: 以下 comment 表不存在 ⇒ Flyway 迁移未生效，运行时 SQL 会失败。
                      缺失表: {}
                      检查: spring.flyway.enabled / locations=classpath:db/migration /
                            spring.flyway.table=flyway_schema_history_comment /
                            pom 是否引了 org.springframework.boot:spring-boot-flyway（Boot 4 必需）
                    """, missing);
        } catch (Exception e) {
            LOG.error("CommentSchemaGate: 表存在性校验失败（不阻止启动）: {}", e.getMessage(), e);
        }
    }
}