package com.rag.users.admin.rpc.bootstrap;

import com.rag.users.common.ddl.SchemaInitializer;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * admin-rpc 启动期建表：把 users 域全部 DDL 幂等重放一遍（2026-10-21 起取代 Flyway）。
 *
 * <p><b>为什么 admin-rpc 也要建 user 域的表</b>：它<b>也读写 {@code users_user}</b>
 * （{@code BanUser} / {@code DeleteUser} / {@code ResetUserPassword} / {@code UpdateUser}），
 * 这是它自己那份 {@code V3__create_user.sql} 迁移注释里写明的设计意图。
 * DDL 全部是 {@code CREATE ... IF NOT EXISTS}，所以两个进程先后或同时启动都安全 ——
 * 这正是 Go 侧 GORM {@code AutoMigrate} 的无状态语义。
 * 详见 {@link SchemaInitializer} 的 javadoc 与 AGENTS.md §6.2.5 DRIFT-NEW-015。
 *
 * <p><b>Go 出处</b>：{@code service/user/admin/rpc/internal/model/db.go:11-18} ——
 * {@code db.AutoMigrate(&Admin{}, &AdminInvite{}, &User{})}，
 * 其中 {@code &User{}} 就是「admin-rpc 也管 users_user」的直接证据；
 * 失败走 {@code log.Fatalln("数据表迁移失败")}。
 *
 * <p><b>⚠️ 时序约束：本类必须在 {@link AdminSeederBootstrap} 之前跑完。</b>
 * {@code AdminSeederBootstrap} 是 {@code @PostConstruct} 且注入 {@code AdminMapper}，
 * 一上来就 {@code SELECT ... FROM admin_admin}。
 * DDL 若排在它后面，启动就会挂在
 * {@code PSQLException: relation "admin_admin" does not exist}。
 * 约束由 {@link AdminSeederBootstrap} 上的 {@code @DependsOn("adminSchemaBootstrap")} 保证
 * —— 而<b>不是</b>靠 bean 定义的扫描顺序（那只是巧合，不可依赖）。
 *
 * <p>原先这个时序由 Flyway 兜住：MyBatis-Plus 注册了
 * {@code MybatisDependsOnDatabaseInitializationDetector}，会让所有 mapper 依赖
 * {@code flywayInitializer}，而 {@code AdminSeederBootstrap} 注入 mapper ⇒ 传递依赖。
 * 去掉 Flyway 后那条隐式链随之消失，所以必须补显式 {@code @DependsOn}。
 */
@Component
public class AdminSchemaBootstrap {

    private static final Logger log = LoggerFactory.getLogger(AdminSchemaBootstrap.class);

    private final DataSource dataSource;

    public AdminSchemaBootstrap(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @PostConstruct
    public void initSchema() {
        log.info("admin-rpc schema init starting");
        try (Connection connection = dataSource.getConnection()) {
            SchemaInitializer.applyAll(connection, "admin-rpc");
        } catch (Exception e) {
            // 不吞：建表失败必须让整个进程起不来。
            // 特别地，如果 admin_admin 没建出来，AdminSeederBootstrap 会在
            // @PostConstruct 里报 relation "admin_admin" does not exist，
            // 那条报错完全指不到真正的原因（DDL 为什么没跑），所以这里必须先拦一道。
            throw new IllegalStateException("admin-rpc 建表失败，进程拒绝启动", e);
        }
        log.info("admin-rpc schema init done");
    }
}