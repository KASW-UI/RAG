package com.rag.users.admin.rpc.bootstrap;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rag.common.snowflake.Snowflake;
import com.rag.users.admin.rpc.config.AdminRpcProperties;
import com.rag.users.admin.rpc.domain.AdminEntity;
import com.rag.users.admin.rpc.exception.AdminExceptions;
import com.rag.users.admin.rpc.mapper.AdminMapper;
import com.rag.users.admin.rpc.service.AdminDomainService;
import com.rag.users.common.cryptx.PasswordUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 启动期 bootstrap：创建默认 admin 账号（admin/admin 或 admin/<System.DefaultPassword>）。
 *
 * <p>对应 Go 端 internal/svc/servicecontext.go 的 ensureDefaultAdmin 业务方法：
 * <pre>
 *   func (s *ServiceContext) ensureDefaultAdmin(ctx context.Context) error {
 *       _, err := s.AdminModel.FindOneAdminByUsername(ctx, defaultAdminUsername)
 *       if err == nil { return nil }                                       // 已存在 → no-op
 *       if err != model.ErrorNotFound { return err }
 *       // ... 加密密码 + snowflake uid + InsertOneAdmin + 唯一冲突兜底
 *   }
 * </pre>
 *
 * <p>Java 侧用 {@code @EventListener(ApplicationReadyEvent)} 自动触发：
 * <ul>
 *   <li>触发时机：Spring 容器就绪后、gRPC server 接受请求前（与 Go 端 main 同步调 ensureDefaultAdmin 时机接近）</li>
 *   <li>失败语义：抛异常 → SpringApplication 启动失败（对齐 Go 端 logx.Must 行为）</li>
 * </ul>
 *
 * <p>1:1 复刻 Go 端语义：先尝试查默认账号；已存在 → no-op；不存在 → 加密密码 + 生成 uid + 插入；
 *                    插入时若唯一冲突（多实例同时启动）→ 重读一次，存在就 OK，不存在就把原异常抛出去。
 */
@Component
public class AdminSeederBootstrap {

    private static final Logger log = LoggerFactory.getLogger(AdminSeederBootstrap.class);

    private final AdminMapper adminMapper;
    private final AdminRpcProperties properties;

    public AdminSeederBootstrap(AdminMapper adminMapper, AdminRpcProperties properties) {
        this.adminMapper = adminMapper;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureDefaultAdmin() {
        // 先尝试查默认账号；已存在 → no-op
        AdminEntity existing = adminMapper.selectOne(
                new LambdaQueryWrapper<AdminEntity>().eq(AdminEntity::getUsername, AdminDomainService.DEFAULT_ADMIN_USERNAME)
        );
        if (existing != null) {
            log.info("default admin account already exists, skip bootstrap");
            return;
        }

        // 不存在 → 创建
        String passwordRaw = properties.getSystem().getDefaultPassword();
        if (passwordRaw == null || passwordRaw.isEmpty()) {
            passwordRaw = AdminDomainService.DEFAULT_ADMIN_PASSWORD_FALLBACK;
        }
        String passwordHash = PasswordUtil.encrypt(passwordRaw);

        long uid;
        try {
            uid = Snowflake.nextId();
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
