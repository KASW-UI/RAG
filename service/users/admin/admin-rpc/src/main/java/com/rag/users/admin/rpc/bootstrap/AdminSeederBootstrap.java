package com.rag.users.admin.rpc.bootstrap;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rag.common.spring.SnowflakeBean;
import com.rag.users.admin.rpc.domain.AdminEntity;
import com.rag.users.admin.rpc.exception.AdminExceptions;
import com.rag.users.admin.rpc.mapper.AdminMapper;
import com.rag.users.admin.rpc.service.AdminDomainService;
import com.rag.users.common.cryptx.PasswordUtil;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.stereotype.Component;

/**
 * 启动期 bootstrap：创建默认 admin 账号（用户名 / 密码均硬编码 {@code admin}）。
 *
 * <p>对应 Go 端 internal/svc/servicecontext.go 的 ensureDefaultAdmin 业务方法：
 * <pre>
 *   const (
 *       defaultAdminUsername = "admin"
 *       defaultAdminPassword = "admin"      // ← 注意：不是 Config.System.DefaultPassword
 *   )
 *
 *   func NewServiceContext(c config.Config) *ServiceContext {
 *       ...
 *       logx.Must(svcCtx.ensureDefaultAdmin(context.Background()))
 *       return svcCtx
 *   }
 * </pre>
 *
 * <p><b>DRIFT-001（已修）· 密码来源</b>：Go 端 seeder 用的是<b>包内常量</b>
 * {@code defaultAdminPassword = "admin"}，<b>从不读</b> {@code Config.System.DefaultPassword}
 * （后者是 {@code admin.yaml} 里的 {@code "123456"}，只被
 * {@code ResetUserPasswordLogic} 用于重置<i>普通用户</i>密码）。
 * <p>本类此前误接了 {@code admin-rpc.system.default-password}，导致全新环境下
 * 默认管理员密码从 {@code admin} 变成 {@code 123456}。
 * 这正是 §26 反事实检验会命中的漂移：一个不看 Go 源码、只读配置语义的自然实现者，
 * 必然会把这个现成的配置项接上——而它管的是另一条业务路径。
 * <p>修法：seeder 完全不依赖 {@code AdminRpcProperties}，密码取
 * {@link AdminDomainService#DEFAULT_ADMIN_PASSWORD}。
 *
 * <p><b>DRIFT-010（已修）· 触发时机</b>：Go 端 {@code NewServiceContext(c)} 在
 * {@code zrpc.MustNewServer} <b>之前</b>执行 seeder，即 gRPC server 开始收流量之前。
 * 本类此前用 {@code @EventListener(ApplicationReadyEvent)}，而该事件在
 * {@code SmartLifecycle} 启动 gRPC server <b>之后</b>才发布 —— 冷启动窗口内
 * admin-api 若已连通，登录会拿到 {@code 1003 用户不存在} 而不是连不上服务。
 * <p>修法：改用 {@link PostConstruct}。它在 {@code finishBeanFactoryInitialization}
 * 阶段执行，早于 {@code finishRefresh} 里的 {@code SmartLifecycle.start()}，
 * 且依赖的 {@code AdminMapper} 由 {@code @MapperScan} 注册的 bean definition 已就绪。
 *
 * <p><b>关于 {@link DependsOnDatabaseInitialization}：显式声明，但当前是冗余的。</b>
 * <p>把 seeder 提前到 {@code @PostConstruct} 后确实会引入一个时序问题：它跑在
 * {@code finishBeanFactoryInitialization}，而 Flyway 迁移跑在 {@code flywayInitializer}
 * bean 创建时（{@code afterPropertiesSet → migrate()}）。两者都是普通 bean，
 * 单看注册顺序（组件扫描早于自动配置）似乎会先跑 seeder。
 * <p><b>但实测证明不会出问题——框架已经保证了顺序</b>：MyBatis-Plus 自带
 * {@code com.baomidou.mybatisplus.autoconfigure.MybatisDependsOnDatabaseInitializationDetector}
 * （继承 {@code AbstractBeansOfTypeDependsOnDatabaseInitializationDetector}，
 * 监听类型为 {@code org.mybatis.spring.SqlSessionTemplate}），Spring Boot 的
 * {@code DatabaseInitializationDependencyConfigurer} 会自动给匹配的 bean 打上
 * 「依赖数据库初始化」标记。因此链路是：
 * <pre>
 *   AdminSeederBootstrap
 *     └─ AdminMapper (MapperFactoryBean)
 *          └─ SqlSessionTemplate   ← 被检测器自动加上「依赖数据库初始化」
 *               └─ flywayInitializer 先跑 migrate()
 * </pre>
 * <p><b>实证</b>：把本类上的 {@code @DependsOnDatabaseInitialization} 注释掉后重跑
 * {@code AdminRpcContainerWiringTest}，7 条仍全部通过 —— 该约束并非由本注解提供。
 * <p><b>那为什么还留着</b>：上面的保证是<b>传递性</b>的，依赖「MyBatis-Plus 提供 detector」
 * +「Boot 的 configurer 收集 detector」两个前提。任一前提变化（换 ORM、Boot 大版本升级、
 * detector 被裁掉）都会让 seeder 静默失去排序保护。本注解把约束钉在本类身上，不依赖那条传递链。
 * <p>要回到「纯靠传递链」的状态，删掉本注解即可 —— 实测行为不变。
 *
 * <p><b>失败语义</b>：抛异常 → Spring 容器启动失败、进程退出，等价于 Go 端
 * {@code logx.Must(...)} 的 fatal 行为。
 *
 * <p>1:1 复刻 Go 端语义：先尝试查默认账号；已存在 → no-op；不存在 → 加密密码 + 生成 uid + 插入；
 *                    插入时若唯一冲突（多实例同时启动）→ 重读一次，存在就 OK，不存在就把原异常抛出去。
 */
@Component
@DependsOnDatabaseInitialization
public class AdminSeederBootstrap {

    private static final Logger log = LoggerFactory.getLogger(AdminSeederBootstrap.class);

    private final AdminMapper adminMapper;
    private final SnowflakeBean snowflakeBean;

    public AdminSeederBootstrap(AdminMapper adminMapper, SnowflakeBean snowflakeBean) {
        this.adminMapper = adminMapper;
        // 注入 SnowflakeBean（而非直接用静态 Snowflake.nextId()）是为了强制 Spring 的
        // bean 创建顺序：SnowflakeBean 的构造器调 Snowflake.setWorkerId(configuredId)，
        // 而 @PostConstruct 阶段的 seeder 若先跑，会用「静态兜底 workerId」生成 uid，
        // 随后 SnowflakeBean 才把 workerId 换成配置值 —— 同一进程内先后两个不同 workerId
        // 产 ID，存在跨 workerId 撞号风险。
        // 注入后 Spring 保证 SnowflakeBean 先构造完，seeder 再执行。
        this.snowflakeBean = snowflakeBean;
    }

    @PostConstruct
    public void ensureDefaultAdmin() {
        // 先尝试查默认账号；已存在 → no-op
        AdminEntity existing = adminMapper.selectOne(
                new LambdaQueryWrapper<AdminEntity>().eq(AdminEntity::getUsername, AdminDomainService.DEFAULT_ADMIN_USERNAME)
        );
        if (existing != null) {
            log.info("default admin account already exists, skip bootstrap");
            return;
        }

        // 不存在 → 创建。密码来自硬编码常量，不读 system.default-password（Go 出处见类注释 DRIFT-001）。
        String passwordHash = PasswordUtil.encrypt(AdminDomainService.DEFAULT_ADMIN_PASSWORD);

        long uid;
        try {
            uid = snowflakeBean.nextId();
        } catch (Exception e) {
            log.error("ensureDefaultAdmin failed: snowflake uid generation error", e);
            throw new IllegalStateException("snowflake uid generation failed", e);
        }

        AdminEntity admin = new AdminEntity();
        admin.setUid(uid);
        admin.setUsername(AdminDomainService.DEFAULT_ADMIN_USERNAME);
        admin.setPassword(passwordHash);

        try {
            adminMapper.insert(admin);
        } catch (Exception e) {
            if (AdminExceptions.isUniqueViolation(e)) {
                // 多实例并发启动场景：另一个实例已经创建。重读一次确认。
                try {
                    AdminEntity relookup = adminMapper.selectOne(
                            new LambdaQueryWrapper<AdminEntity>().eq(AdminEntity::getUsername, AdminDomainService.DEFAULT_ADMIN_USERNAME)
                    );
                    if (relookup != null) {
                        log.info("default admin account bootstrapped by another instance");
                        return;
                    }
                    // 重读也没找到 → 把原 unique violation 抛上去
                } catch (Exception lookupErr) {
                    log.error("ensureDefaultAdmin relookup failed", lookupErr);
                }
            }
            log.error("ensureDefaultAdmin failed: insert admin error", e);
            throw new IllegalStateException("ensureDefaultAdmin insert failed", e);
        }

        log.info("default admin account bootstrapped, uid={}", uid);
    }
}
