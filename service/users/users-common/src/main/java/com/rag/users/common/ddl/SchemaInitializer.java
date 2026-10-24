package com.rag.users.common.ddl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * users 域 DDL 的幂等执行器（2026-10-21 起取代 Flyway）。
 *
 * <p><b>为什么不用 Flyway</b>：{@code flyway_schema_history} 是 <b>schema 级</b>账本，
 * 一个 schema 只能有一本账。而 {@code user-rpc} 与 {@code admin-rpc} 各自的迁移里
 * {@code V1/V2} <b>版本号相同、内容不同</b>，两个进程又<b>必须连同一个库</b>
 * （admin-rpc 也要读写 {@code users_user}）。于是后启动的那个要么报
 * {@code version (3) is newer than the latest available migration (2)} 并静默跳过自己的迁移，
 * 要么 checksum mismatch 直接启动失败。
 * 「加 {@code validate-on-migrate=false} + {@code out-of-order=true} 糊过去」这条路也已实测证伪，
 * 见 {@code tools/difftest/exp-drift015-flyway-flags.sh}：admin-rpc 自己的 V1/V2 会被静默跳过，
 * {@code admin_admin} / {@code admin_invite} 永远建不出来。
 *
 * <p><b>更接近 Go</b>：Go 侧用 GORM {@code AutoMigrate}，无状态、幂等、谁先启动都行。
 * 本类把 Java 侧拉回同一条路上 —— DDL 全部写成 {@code CREATE ... IF NOT EXISTS}，
 * 每次启动原样重放。没有版本号、没有账本、没有任何跨进程状态。
 *
 * <p><b>已知代价（第一次 ALTER 之前可以无视，之后必须重新引入版本化机制）</b>：
 * 脚本得同时承担「建表」与「升级」两件事，只能到处加
 * {@code ADD COLUMN IF NOT EXISTS}；也没有任何地方记录「这张表现在该长什么样」。
 * 项目仍在重构期、无已部署环境（{@code docker-compose.yaml} 里连 admin-rpc 都没有），
 * 升级路径的价值要等到第一次 {@code ALTER} 才兑现 —— 而那时只有一张库要改。
 *
 * <p><b>Go 出处</b>：
 * <ul>
 *   <li>{@code service/user/user/rpc/internal/model/db.go:33} —
 *       {@code db.AutoMigrate(&User{}, &UserAvatarHistory{})}</li>
 *   <li>{@code service/user/admin/rpc/internal/model/db.go:15} —
 *       {@code db.AutoMigrate(&Admin{}, &AdminInvite{}, &User{})}</li>
 * </ul>
 * 两侧都用 GORM 生成的幂等 DDL（{@code CREATE TABLE IF NOT EXISTS}），
 * 所以「每个 rpc 进程启动时把全套 users 域 DDL 重放一遍」就是 Go 的等价物。
 *
 * <p><b>⚠️ 不是 {@code @Component}</b>：user-rpc / admin-rpc 都是朴素
 * {@code @SpringBootApplication}，只扫自己的包，而且两边都在注释里
 * <b>明确禁止</b>把 {@code com.rag.common} 加进 {@code scanBasePackages}
 * （admin-rpc 是纯 gRPC 进程、刻意不引 servlet 依赖，全包扫描会拖进
 * {@code jakarta.servlet.Filter} 直接启动失败）。
 * 所以这里是静态工具，由各模块自己的 {@code bootstrap} 类显式调用。
 */
public final class SchemaInitializer {

    private static final Logger log = LoggerFactory.getLogger(SchemaInitializer.class);

    /**
     * users 域全部 DDL，按文件名顺序执行。
     *
     * <p>数字前缀只保证<b>确定性顺序</b>，不表示版本。这四份之间没有外键依赖，
     * 顺序其实不影响正确性，但固定下来能让日志可读、行为可复现。
     */
    private static final String[] SCRIPTS = {
            "db/01_create_user.sql",
            "db/02_create_user_avatar_history.sql",
            "db/03_create_admin.sql",
            "db/04_create_admin_invite.sql",
    };

    /**
     * PG advisory lock 的键（bigint）。
     *
     * <p><b>为什么需要它</b>：PostgreSQL 的 {@code CREATE TABLE IF NOT EXISTS}
     * <b>在并发下并不安全</b>。两个进程同时对同一个空库执行同一条 DDL 时，
     * 后到者可能撞上唯一约束 {@code pg_type_typname_nsp_index} 而失败：
     * <pre>
     *   ERROR: duplicate key value violates unique constraint "pg_type_typname_nsp_index"
     * </pre>
     * 这不是假设 —— 是 2026-10-21 双端对拍实测撞出来的：
     * user-rpc 与 admin-rpc 同时启动、各自建 {@code users_user}，直接炸掉。
     * 单元测试测不到（每次只起一个服务），只有同时起两套栈才会发生。
     *
     * <p>Flyway 原本用 schema history 上的行锁天然挡住了这一层，
     * 去掉 Flyway 之后必须自己补上。PG 的 advisory lock 是这里最合适的工具：
     * 不需要额外表、不需要事务、会话级自动释放（连接断开即释放）。
     *
     * <p>键值是<b>常量</b>，两个模块必须用同一个 —— 所以它定义在本类里，
     * 而不是在各自的 bootstrap 里各自取一个。
     * 改动时注意：换键等于让并发建表的保护失效。
     */
    private static final long ADVISORY_LOCK_KEY = 8_675_309L;

    private SchemaInitializer() {
    }

    /**
     * 用给定连接把全部 DDL 原样重放一遍。
     *
     * <p>连接由调用方持有 —— 通常是 Spring 注入的 {@link DataSource}。
     * 这里刻意<b>不</b>自己开连接，避免每份脚本各拿一条、也便于调用方
     * 把整段纳入自己的事务/生命周期。
     *
     * @param serviceName 日志里用来区分是哪个进程在执行
     * @param connection  已建立的 JDBC 连接，调用方负责关闭
     * @throws SQLException 任一 DDL 失败即抛出 —— <b>必须让启动失败</b>，
     *                      绝不能吞掉。对齐 Go 侧 {@code logx.Must} / {@code log.Fatalln}
     *                      在 AutoMigrate 失败时的行为
     */
    public static void applyAll(Connection connection, String serviceName) throws SQLException {
        // 先拿会话级 advisory lock：把两个 rpc 并发建表这件事串行化。
        // 拿不到就一直等（PG 的 pg_advisory_lock 是阻塞语义），等到即可，
        // 因为另一个进程执行完就会释放 —— 不会死锁，也不需要超时。
        boolean locked = false;
        try (Statement st = connection.createStatement()) {
            st.execute("select pg_advisory_lock(" + ADVISORY_LOCK_KEY + ")");
            locked = true;
            log.info("schema init [{}]: acquired advisory lock {}", serviceName, ADVISORY_LOCK_KEY);
            for (String script : SCRIPTS) {
                log.info("schema init [{}]: applying {}", serviceName, script);
                // continueOnError=false（ScriptUtils 默认）：任一条语句失败即抛 SQLException。
                ScriptUtils.executeSqlScript(connection, new ClassPathResource(script));
            }
        } finally {
            if (locked) {
                // 会话级锁在连接断开时也会自动释放，这里显式释放只是为了尽快放开，
                // 避免另一个进程干等。失败不抛：解锁失败不影响 DDL 本身的结论。
                try (Statement st = connection.createStatement()) {
                    st.execute("select pg_advisory_unlock(" + ADVISORY_LOCK_KEY + ")");
                } catch (SQLException e) {
                    log.warn("schema init [{}]: advisory unlock failed (连接断开也会自动释放)",
                            serviceName, e);
                }
            }
        }
        log.info("schema init [{}]: all {} scripts applied", serviceName, SCRIPTS.length);
    }
}