package com.rag.users.admin.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * admin-api 模块配置聚合点 —— Spring Boot @ConfigurationProperties。
 *
 * 对应 Go 端 service/user/admin/api/etc/admincenter.yaml 的 admin-api.* 段。
 *
 * <p>L1 外部契约冻结：
 * <ul>
 *   <li>{@link AdminAuth#accessSecret} 默认值 = Go 端 AdminAuth.AccessSecret = "yU-hCm)2*?NQmWG"</li>
 *   <li>{@link AdminAuth#accessExpire} 默认值 = Go 端 AdminAuth.AccessExpire = 2592000（30 天）</li>
 * </ul>
 *
 * <p>关键：JWT secret 必须与 admin-rpc 进程共享同一份密钥 —— 否则跨进程 JWT 校验会失败。
 * secret 来自 application.yml 的 {@code admin-api.admin-auth.access-secret}（env: {@code ADMIN_JWT_SECRET}）。
 */
@ConfigurationProperties(prefix = "admin-api")
public class AdminApiProperties {

    @NestedConfigurationProperty
    private AdminAuth adminAuth = new AdminAuth();

    public AdminAuth getAdminAuth() { return adminAuth; }
    public void setAdminAuth(AdminAuth adminAuth) { this.adminAuth = adminAuth; }

    public static class AdminAuth {
        private String accessSecret;
        private long accessExpire;

        public String getAccessSecret() { return accessSecret; }
        public void setAccessSecret(String accessSecret) { this.accessSecret = accessSecret; }
        public long getAccessExpire() { return accessExpire; }
        public void setAccessExpire(long accessExpire) { this.accessExpire = accessExpire; }
    }
}
