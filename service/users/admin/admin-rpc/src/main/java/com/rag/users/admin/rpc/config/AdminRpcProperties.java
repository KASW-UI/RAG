package com.rag.users.admin.rpc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * admin-rpc 模块配置聚合点 —— Spring Boot @ConfigurationProperties。
 *
 * 对应 Go 端 service/user/admin/rpc/etc/admin.yaml 的 admin-rpc.* 段；
 * Go 端是 conf.MustLoad(...) 强转到 Config struct。
 *
 * <p>框架职责：
 * <ul>
 *   <li>{@code @ConfigurationProperties} 把 application.yaml 中 {@code admin-rpc.*} 段自动绑定到字段</li>
 *   <li>{@code @NestedConfigurationProperty} 用于嵌套对象（adminAuth / system 段）</li>
 *   <li>adminAuth.accessSecret / accessExpire 给后续 admin JWT 接入预留</li>
 *   <li>system.defaultPassword 给 AdminSeederBootstrap 启动期 super admin seed 用</li>
 * </ul>
 *
 * <p>L1 外部契约冻结（admin-rpc 启动期）：
 * <ul>
 *   <li>{@link AdminAuth#accessSecret} = Go 端 AdminAuth.AccessSecret = "yU-hCm)2*?NQmWG"</li>
 *   <li>{@link AdminAuth#accessExpire} = Go 端 AdminAuth.AccessExpire = 2592000（30 天，与 user-rpc UserAuth 对齐）</li>
 *   <li>{@link System#defaultPassword} = Go 端 System.DefaultPassword = "123456"（admin 启动种子的密码）</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "admin-rpc")
public class AdminRpcProperties {

    @NestedConfigurationProperty
    private AdminAuth adminAuth = new AdminAuth();

    @NestedConfigurationProperty
    private System system = new System();

    public AdminAuth getAdminAuth() { return adminAuth; }
    public void setAdminAuth(AdminAuth adminAuth) { this.adminAuth = adminAuth; }
    public System getSystem() { return system; }
    public void setSystem(System system) { this.system = system; }

    /**
     * 对应 Go 端 AdminAuth：
     *   AccessSecret = "yU-hCm)2*?NQmWG"
     *   AccessExpire = 2592000   // 30 天，与 user-rpc UserAuth 对齐
     */
    public static class AdminAuth {
        private String accessSecret;
        private long accessExpire;

        public String getAccessSecret() { return accessSecret; }
        public void setAccessSecret(String accessSecret) { this.accessSecret = accessSecret; }
        public long getAccessExpire() { return accessExpire; }
        public void setAccessExpire(long accessExpire) { this.accessExpire = accessExpire; }
    }

    /**
     * 对应 Go 端 System：DefaultPassword 用于 AdminSeederBootstrap bootstrap 时给默认账号加密。
     * Go 端 etc/admin.yaml: System.DefaultPassword = "123456"。
     */
    public static class System {
        private String defaultPassword;

        public String getDefaultPassword() { return defaultPassword; }
        public void setDefaultPassword(String defaultPassword) { this.defaultPassword = defaultPassword; }
    }
}
