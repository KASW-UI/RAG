package com.rag.users.user.api.internal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 对应 Go 端 internal/config/config.go：
 * <pre>
 * type Config struct {
 *     rest.RestConf                                       // Host=0.0.0.0, Port=8888
 *     UserAuth struct { AccessSecret string; AccessExpire int64 }
 *     UserRpc  zrpc.RpcClientConf                         // Etcd.Hosts, Key=user.rpc
 *     BizRedis redis.RedisConf                            // Host=redis:6379, Type=node
 * }
 * </pre>
 *
 * <p>L4 实现机制差异：
 * <ul>
 *   <li>{@code rest.RestConf}（Go）→ Spring Boot 的 {@code server.port/address}，由 WebAutoConfiguration 直接读，<b>不进 Config.java</b></li>
 *   <li>{@code BizRedis}（Go）→ Spring Data Redis 的 {@code spring.data.redis.*}，由 RedisAutoConfiguration 直接读，<b>不进 Config.java</b></li>
 *   <li>{@code UserAuth}（Go）→ 本类的 {@link UserAuth}（保留字段名 + 类型语义）</li>
 *   <li>{@code UserRpc.Etcd.Hosts + Key}（Go）→ 本类的 {@link UserRpc}（去掉 Etcd discovery，改用 {@code host:port} 直连；Nacos discovery 由根 pom + spring-cloud-starter-alibaba-nacos-discovery 承担服务发现，本类只保留连接目标）</li>
 * </ul>
 *
 * <p>L1 外部契约冻结：
 * <ul>
 *   <li>{@link UserAuth#accessSecret} = Go 端 {@code UserAuth.AccessSecret = "ae0536f9-6450-4606-8e13-5a19ed505da0"}</li>
 *   <li>{@link UserAuth#accessExpire} = Go 端 {@code UserAuth.AccessExpire = 2592000}（秒，30 天）</li>
 * </ul>
 *
 * <p>L4 注：Spring Boot 的 {@code server.port} 与 {@code spring.data.redis.host:port} 不归本类管，
 * 但对应的语义仍冻结（端口 49900 / Redis 49380）。
 */
@ConfigurationProperties(prefix = "user-api")
public class Config {

    private UserAuth userAuth = new UserAuth();

    public UserAuth getUserAuth() { return userAuth; }
    public void setUserAuth(UserAuth userAuth) { this.userAuth = userAuth; }

    /**
     * 对应 Go 端 {@code UserAuth} 嵌套 struct。
     *
     * <p>{@link #accessSecret} 字段：JWT 签名密钥。Go 端固定 UUID 字符串，
     * Java 侧直接当字符串使用（{@code secretKey.getBytes(UTF_8)} 作为 HMAC-SHA256 key）。
     * 必须与 user-rpc 进程共享同一份密钥，否则跨进程 JWT 校验会失败。
     *
     * <p>{@link #accessExpire} 字段：JWT 有效期，单位秒。Go 端写死 2592000（30 天）；
     * user-rpc 进程的 LogoutLogic 用这个值决定 Redis 黑名单 TTL，所以两端必须一致。
     */
    public static class UserAuth {
        private String accessSecret;
        private long accessExpire;

        public String getAccessSecret() { return accessSecret; }
        public void setAccessSecret(String accessSecret) { this.accessSecret = accessSecret; }

        public long getAccessExpire() { return accessExpire; }
        public void setAccessExpire(long accessExpire) { this.accessExpire = accessExpire; }
    }

    }