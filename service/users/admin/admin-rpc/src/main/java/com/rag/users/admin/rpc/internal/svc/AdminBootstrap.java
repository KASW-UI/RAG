package com.rag.users.admin.rpc.internal.svc;

import com.rag.common.snowflake.Snowflake;
import com.rag.users.admin.rpc.internal.config.Config;
import com.rag.users.admin.rpc.internal.model.Admin;
import com.rag.users.admin.rpc.internal.model.AdminExceptions;
import com.rag.users.admin.rpc.internal.model.AdminExceptions.NotFoundException;
import com.rag.users.admin.rpc.internal.model.DataModel;
import com.rag.users.common.cryptx.PasswordUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 启动期 bootstrap：创建默认 admin 账号（admin/admin 或 admin/<System.DefaultPassword>）。
 *
 * 对应 Go 端 internal/svc/servicecontext.go 的 ensureDefaultAdmin 业务方法：
 *   func (s *ServiceContext) ensureDefaultAdmin(ctx context.Context) error {
 *       _, err := s.AdminModel.FindOneAdminByUsername(ctx, defaultAdminUsername)
 *       if err == nil { return nil }                                       // 已存在 → no-op
 *       if err != model.ErrorNotFound { return err }
 *       // ... 加密密码 + snowflake uid + InsertOneAdmin + 唯一冲突兜底
 *   }
 *
 * Java 侧用 @EventListener(ApplicationReadyEvent) 自动触发：
 *   - 触发时机：Spring 容器就绪后、web/gRPC server 接受请求前（与 Go 端 main 同步调 ensureDefaultAdmin 时机接近）
 *   - 失败语义：抛异常 → SpringApplication 启动失败（对齐 Go 端 logx.Must 行为）
 *
 * 对应 Go 端默认账号：
 *   defaultAdminUsername = "admin"
 *   defaultAdminPassword = "admin"        // hardcoded,Go 端
 *   System.DefaultPassword = "123456"     // 来自 etc/admin.yaml,优先使用
 *
 * 1:1 复刻 Go 端语义：先尝试查默认账号；已存在 → no-op；不存在 → 加密密码 + 生成 uid + 插入；
 *                    插入时若唯一冲突（多实例同时启动）→ 重读一次，存在就 OK，不存在就把原异常抛出去。
 */
@Component
public class AdminBootstrap {

    private static final Logger LOG = LoggerFactory.getLogger(AdminBootstrap.class);

    private static final String DEFAULT_ADMIN_USERNAME = "admin";
    /** Go 端 hardcoded "admin"；这里用 null 判断 fallback */
    private static final String DEFAULT_ADMIN_PASSWORD_FALLBACK = "admin";

    private final DataModel dataModel;
    private final Config config;

    public AdminBootstrap(DataModel dataModel, Config config) {
        this.dataModel = dataModel;
        this.config = config;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureDefaultAdmin() {
        try {
            dataModel.findOneAdminByUsername(DEFAULT_ADMIN_USERNAME);
            // 已存在 → no-op
            return;
        } catch (NotFoundException e) {
            // 不存在 → 创建
        }

        String passwordRaw = config.getSystem().getDefaultPassword();
        if (passwordRaw == null || passwordRaw.isEmpty()) {
            passwordRaw = DEFAULT_ADMIN_PASSWORD_FALLBACK;
        }
        String passwordHash = PasswordUtil.encrypt(passwordRaw);

        long uid;
        try {
            uid = Snowflake.nextId();
        } catch (Exception e) {
            LOG.error("ensureDefaultAdmin failed: snowflake uid generation error", e);
            throw new IllegalStateException("snowflake uid generation failed", e);
        }

        Admin admin = new Admin();
        admin.setUid(uid);
        admin.setUsername(DEFAULT_ADMIN_USERNAME);
        admin.setPassword(passwordHash);

        try {
            dataModel.insertAdmin(admin);
        } catch (Exception e) {
            if (AdminExceptions.isUniqueViolation(e)) {
                // 多实例并发启动场景：另一个实例已经创建。重读一次确认。
                try {
                    dataModel.findOneAdminByUsername(DEFAULT_ADMIN_USERNAME);
                    LOG.info("default admin account bootstrapped by another instance");
                    return;
                } catch (NotFoundException lookupErr) {
                    // 重读也没找到 → 把原 unique violation 抛上去
                }
            }
            LOG.error("ensureDefaultAdmin failed: insert admin error", e);
            throw new IllegalStateException("ensureDefaultAdmin insert failed", e);
        }

        LOG.info("default admin account bootstrapped");
    }
}