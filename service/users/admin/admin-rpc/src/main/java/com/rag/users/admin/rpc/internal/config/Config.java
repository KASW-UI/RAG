package com.rag.users.admin.rpc.internal.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 对应 Go 端 internal/config/config.go：
 *   type Config struct {
 *       zrpc.RpcServerConf                       // ListenOn=0.0.0.0:8081
 *       DataSource string                        // 已迁到 spring.datasource.url
 *       System     struct { DefaultPassword string }
 *       BizRedis   redis.RedisConf               // 已迁到 spring.data.redis.*
 *       AdminAuth  struct { AccessSecret string; AccessExpire int64 }
 *   }
 *
 * 只承载"业务真正消费"的字段。
 * - 监听端口由 grpc-server-spring-boot-starter 自动读 grpc.server.port (8081)
 * - DB / Redis 连接信息由 Spring Boot 自动读 spring.datasource.* / spring.data.redis.*
 * - Nacos 自动读 spring.cloud.nacos.discovery.*
 *
 * AdminAuth: 保留字段供后续 admin JWT 接入时直接复用（Go 端 AdminAuth.AccessSecret/AccessExpire 同样定义）。
 * System.DefaultPassword: ensureDefaultAdmin bootstrap 用。
 *
 * 框架对照：
 *   Go: conf.MustLoad(*configFile, &c)
 *   Java: @ConfigurationProperties("admin-rpc") 自动绑定 application.yml 的 admin-rpc.* 段
 */
@Setter
@Getter
@ConfigurationProperties(prefix = "admin-rpc")
public class Config {

    private AdminAuth adminAuth = new AdminAuth();
    private System system = new System();

    /**
     * 对应 Go 端 AdminAuth：
     *   AccessSecret = "yU-hCm)2*?NQmWG"
     *   AccessExpire = 2592000   // 30 天，与 user-rpc UserAuth 对齐
     */
    @Setter
    @Getter
    public static class AdminAuth {
        private String accessSecret;
        private long accessExpire;
    }

    /**
     * 对应 Go 端 System：DefaultPassword 用于 ensureDefaultAdmin bootstrap 时给默认账号加密。
     * Go 端 etc/admin.yaml: System.DefaultPassword = "123456"。
     */
    @Setter
    @Getter
    public static class System {
        private String defaultPassword;
    }
}