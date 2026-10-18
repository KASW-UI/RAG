package com.rag.users.user.rpc.internal.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 对应 Go 端 internal/config/config.go：
 *   type Config struct {
 *       zrpc.RpcServerConf                       // 监听地址、timeout 等
 *       Postgres struct { Host, Port, User, Password, DBName, Mode }
 *       BizRedis  redis.RedisConf                // redis 地址
 *       UserAuth struct { AccessSecret, AccessExpire }
 *   }
 * <p>
 * Java 侧用 @ConfigurationProperties 把 application.yaml 的 user-rpc.* 段绑进来。
 * 当前只实装 UserAuth（其它 RPC 暂时只用到 AccessExpire；后续 application.yaml 配齐后再补 Postgres/BizRedis）。
 * <p>
 * gRPC 服务端监听端口由 grpc-spring-boot-starter 直接读 spring.grpc.server.port，不在这里。
 */
@Setter
@Getter
@ConfigurationProperties(prefix = "user-rpc")
public class Config {

    private UserAuth userAuth = new UserAuth();

    @Setter
    @Getter
    public static class UserAuth {
        /** Go: UserAuth.AccessSecret = "ae0536f9-6450-4606-8e13-5a19ed505da0" */
        private String accessSecret;

        /** Go: UserAuth.AccessExpire = 2592000（秒，30 天）。Logout 的 Redis TTL 用这个值。 */
        private long accessExpire;

    }
}