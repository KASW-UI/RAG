package com.rag.users.user.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * user-api 模块配置聚合点 —— Spring Boot @ConfigurationProperties。
 *
 * 对应 Go: service/user/user/api/etc/usercenter.yaml 的 user-api.user-auth 段；
 *   Go 端是 conf.MustLoad(...) 强转到 Config struct。
 *
 * 框架职责:
 *   - @ConfigurationProperties 把 application.yaml 中 user-api.* 段自动绑定到这个类的字段
 *   - @NestedConfigurationProperty 用于嵌套对象（userAuth 段）
 *   - userAuth.accessSecret / accessExpire 同时被 JWT 签发（user-api LoginLogic）和下
 *     游 user-rpc LogoutLogic 的 Redis TTL 使用（D-007）
 */
@ConfigurationProperties(prefix = "user-api")
public class UserApiProperties {

    private String name = "user-api";

    @NestedConfigurationProperty
    private UserAuth userAuth = new UserAuth();

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public UserAuth getUserAuth() { return userAuth; }
    public void setUserAuth(UserAuth userAuth) { this.userAuth = userAuth; }

    public static class UserAuth {
        private String accessSecret;
        private long accessExpire;

        public String getAccessSecret() { return accessSecret; }
        public void setAccessSecret(String accessSecret) { this.accessSecret = accessSecret; }
        public long getAccessExpire() { return accessExpire; }
        public void setAccessExpire(long accessExpire) { this.accessExpire = accessExpire; }
    }
}