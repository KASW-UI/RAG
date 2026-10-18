package com.rag.users.admin.api.internal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 对应 Go 端 internal/config/config.go：
 * <pre>
 * type Config struct {
 *     rest.RestConf                                       // Host=0.0.0.0, Port=8889
 *     AdminAuth struct { AccessSecret string; AccessExpire int64 }
 *     AdminRpc  zrpc.RpcClientConf                        // Etcd.Hosts, Key=admin.rpc
 *     BizRedis  redis.RedisConf                           // Host=redis:6379, Type=node
 * }
 * </pre>
 *
 * <p>L4 实现机制差异：
 * <ul>
 *   <li>{@code rest.RestConf}（Go）→ Spring Boot 的 {@code server.port/address}，由 WebAutoConfiguration 直接读，<b>不进 Config.java</b></li>
 *   <li>{@code BizRedis}（Go）→ Spring Data Redis 的 {@code spring.data.redis.*}，由 RedisAutoConfiguration 直接读，<b>不进 Config.java</b></li>
 *   <li>{@code AdminAuth}（Go）→ 本类的 {@link AdminAuth}（保留字段名 + 类型语义）</li>
 *   <li>{@code AdminRpc.Etcd.Hosts + Key}（Go）→ <b>不进本类</b>：gRPC client 由 devh grpc-client-spring-boot-starter 通过
 *       {@code grpc.client.admin-rpc.address=discovery:///admin-rpc}（application.yml §3.5）装配；
 *       Nacos discovery 由根 pom + spring-cloud-starter-alibaba-nacos-discovery 承担服务发现</li>
 * </ul>
 *
 * <p>L1 外部契约冻结：
 * <ul>
 *   <li>{@link AdminAuth#accessSecret} 默认值 = Go 端 {@code AdminAuth.AccessSecret = "yU-hCm)2*?NQmWG"}</li>
 *   <li>{@link AdminAuth#accessExpire} 默认值 = Go 端 {@code AdminAuth.AccessExpire = 2592000}（秒，30 天）</li>
 * </ul>
 *
 * <p><b>关键：JWT secret 必须与 admin-rpc 进程共享同一份密钥</b> —— 否则跨进程 JWT 校验会失败。
 * 本类的 secret 来自 application.yml 的 {@code admin-api.admin-auth.access-secret}（env: {@code ADMIN_JWT_SECRET}）。
 */
@ConfigurationProperties(prefix = "admin-api")
public class Config {

    private AdminAuth adminAuth = new AdminAuth();

    public AdminAuth getAdminAuth() { return adminAuth; }
    public void setAdminAuth(AdminAuth adminAuth) { this.adminAuth = adminAuth; }

    /**
     * 对应 Go 端 {@code AdminAuth} 嵌套 struct。
     *
     * <p>{@link #accessSecret} 字段：JWT 签名密钥。Go 端固定字符串
     * {@code "yU-hCm)2*?NQmWG"}；Java 侧直接当字符串使用（{@code secretKey.getBytes(UTF_8)}
     * 作为 HMAC-SHA256 key）。<b>必须</b>与 admin-rpc 进程共享同一份密钥，否则跨进程 JWT 校验会失败。
     *
     * <p>{@link #accessExpire} 字段：JWT 有效期，单位秒。Go 端写死 2592000（30 天）；
     * admin-rpc 进程的 LogoutLogic 用这个值决定 Redis 黑名单 TTL，所以两端必须一致。
     */
    public static class AdminAuth {
        private String accessSecret;
        private long accessExpire;

        public String getAccessSecret() { return accessSecret; }
        public void setAccessSecret(String accessSecret) { this.accessSecret = accessSecret; }

        public long getAccessExpire() { return accessExpire; }
        public void setAccessExpire(long accessExpire) { this.accessExpire = accessExpire; }
    }
}