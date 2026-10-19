package com.rag.users.user.rpc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * user-rpc 模块配置聚合点 —— Spring Boot @ConfigurationProperties。
 *
 * 对应 Go: service/user/user/rpc/etc/user.yaml 的 user-rpc.user-auth 段；
 *   Go 端是 conf.MustLoad(...) 强转到 Config struct。
 *
 * 框架职责:
 *   - @ConfigurationProperties 把 application.yaml 中 user-rpc.* 段自动绑定到这个类的字段
 *   - 取代了原 internal.config.Config
 *   - @NestedConfigurationProperty 用于嵌套对象（userAuth 段）
 *   - MyBatis-Plus 不需要在这里声明 mapper 路径，它通过 @MapperScan 自动发现
 *
 * UserAuth 段对应 Go 端 UserAuth.AccessSecret / UserAuth.AccessExpire（JWT 配置）：
 *   - accessSecret: 用于 JWT HS256 签名（user-api / admin-api 登录成功后签发）
 *   - accessExpire: token 有效期（秒）。D-007：LogoutLogic Redis Setex TTL 必须用此值，不能写死 24h
 */
@ConfigurationProperties(prefix = "user-rpc")
public class UserRpcProperties {

    private String name = "user-rpc";

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