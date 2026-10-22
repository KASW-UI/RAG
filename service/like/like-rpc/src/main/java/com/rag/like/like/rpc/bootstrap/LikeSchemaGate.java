package com.rag.like.like.rpc.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 启动后校验 5 张 like_* 表确实存在 —— 对应 Go 侧 {@code model/db.go} 的
 * {@code db.AutoMigrate(&LikeRecord{}, &LikeConsumeInbox{}, &LikeOutboxEvent{},
 * &LikeDomainFactOutbox{}, &LikeFactStream{})}。
 *
 * <h2>⚠️ 为什么要有这道闸</h2>
 *
 * <p>Go 用 {@code AutoMigrate}，建表失败（{@code log.Fatalln}）直接退出 —— 表与进程同生共死。
 * Java 侧改用 Flyway，于是引入了 Go 没有的新失败模式：
 * <b>Flyway 引擎在、但自动配置没加载</b> ⇒ 启动日志里<b>连一行 Flyway 都没有</b>
 * （不是报错，是根本没被加载，Boot 4 拆模块后极易发生）⇒
 * 5 张表永不创建 ⇒ 进程看起来完全正常 ⇒ <b>第一次真正执行 SQL 时才炸</b>：
 * <pre>
 *   org.postgresql.util.PSQLException: ERROR: relation "like_record" does not exist
 * </pre>
 *
 * <p>message-rpc 就是这么踩的（AGENTS.md DRIFT-MSG-006）。本类把「迁移到底跑没跑」
 * 变成<b>启动时可见</b>的一条 ERROR 日志，而不是等到线上第一次点赞。
 *
 * <p><b>不抛异常、不阻止启动</b>：与 Go 的差异说明如下——
 * Go 迁移失败会退出进程，而 Java 单 JVM 里还跑着 Kafka 消费与 gRPC。
 * 这里只<b>大声告警</b>，让运维能立刻看到；真的缺表时首条 SQL 自然会失败。
 * （若要改成 fail-fast，只需把 {@code LOG.error} 换成抛异常。）
 */
@Component
public class LikeSchemaGate {

    private static final Logger LOG = LoggerFactory.getLogger(LikeSchemaGate.class);

    /** 本域 5 张表，与 {@code V1..V5} 迁移脚本一一对应。 */
    private static final List<String> REQUIRED_TABLES = List.of(
            "like_record",
            "like_consume_inbox",
            "like_outbox_event",
            "like_domain_fact_outbox",
            "like_fact_stream");

    private final JdbcTemplate jdbcTemplate;

    public LikeSchemaGate(JdbcTemplate jdbcTemplate) {
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
                LOG.info("LikeSchemaGate: all {} like tables present", REQUIRED_TABLES.size());
                return;
            }
            LOG.error("""
                    LikeSchemaGate: 以下 like 表不存在 ⇒ Flyway 迁移未生效，运行时 SQL 会失败。
                      缺失表: {}
                      检查: spring.flyway.enabled / locations=classpath:db/migration /
                            pom 是否引了 org.springframework.boot:spring-boot-flyway（Boot 4 必需）
                    """, missing);
        } catch (Exception e) {
            LOG.error("LikeSchemaGate: 表存在性校验失败（不阻止启动）: {}", e.getMessage(), e);
        }
    }
}