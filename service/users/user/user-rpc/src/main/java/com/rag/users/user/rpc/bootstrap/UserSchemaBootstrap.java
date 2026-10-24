package com.rag.users.user.rpc.bootstrap;

import com.rag.users.common.ddl.SchemaInitializer;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * user-rpc 启动期建表：把 users 域全部 DDL 幂等重放一遍（2026-10-21 起取代 Flyway）。
 *
 * <p><b>为什么需要它</b>：Flyway 的 {@code flyway_schema_history} 是 schema 级账本，
 * 一个 schema 只能有一本账，而 user-rpc 与 admin-rpc 的迁移版本号撞车、内容不同，
 * 两个进程又必须连同一个库（admin-rpc 也读写 {@code users_user}）。
 * 详见 {@link SchemaInitializer} 的 javadoc 与 AGENTS.md §6.2.5 DRIFT-NEW-015。
 *
 * <p><b>Go 出处</b>：{@code service/user/user/rpc/internal/model/db.go:29-36} ——
 * {@code gorm.Open} 之后紧跟 {@code db.AutoMigrate(&User{}, &UserAvatarHistory{})}，
 * 失败走 {@code log.Fatalln("数据表迁移失败")} 让进程退出。
 * 本类等价物：{@code @PostConstruct} 里重放 DDL，失败抛
 * {@link IllegalStateException} 让 {@code SpringApplication} 启动失败。
 *
 * <p><b>为什么放 {@code bootstrap/} 而不是让某个 mapper 顺带建表</b>：
 * 本模块沿用 {@code SnowflakeBootstrap} / {@code AdminSeederBootstrap} 的既有约定 ——
 * 启动期副作用集中在 {@code bootstrap} 包，失败一律 fail-fast。
 *
 * <p><b>user-rpc 侧没有顺序约束</b>（与 admin-rpc 不同）：
 * 本模块唯一的启动期 DB 访问方不存在，{@code SnowflakeBootstrap} 不碰数据库。
 * 所以 DDL 什么时候跑完都不会踩到「表不存在」。
 * admin-rpc 才有硬约束 —— 它的 {@code AdminSeederBootstrap} 是 {@code @PostConstruct}
 * 且注入 {@code AdminMapper}，必须靠 {@code @DependsOn} 排在 DDL 之后。
 */
@Component
public class UserSchemaBootstrap {

    private static final Logger log = LoggerFactory.getLogger(UserSchemaBootstrap.class);

    private final DataSource dataSource;

    public UserSchemaBootstrap(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @PostConstruct
    public void initSchema() {
        log.info("user-rpc schema init starting");
        try (Connection connection = dataSource.getConnection()) {
            SchemaInitializer.applyAll(connection, "user-rpc");
        } catch (Exception e) {
            // 不吞：建表失败必须让整个进程起不来，否则进程会「Started」然后在第一次
            // SQL 时才炸，报错长得像业务 bug。对齐 Go 的 log.Fatalln。
            throw new IllegalStateException("user-rpc 建表失败，进程拒绝启动", e);
        }
        log.info("user-rpc schema init done");
    }
}