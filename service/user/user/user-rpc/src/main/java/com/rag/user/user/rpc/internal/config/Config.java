package com.rag.user.user.rpc.internal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 对应 Go: internal/config/config.go
 *
 * Go 端用 struct 嵌 zrpc.RpcServerConf + 自定义 Postgres / BizRedis / UserAuth,框架用 conf.MustLoad 反序列化 yaml。
 * Java 端用 @ConfigurationProperties 把 application.yaml 里的字段绑进这个对象。
 *
 * 字段层级按 application.yaml 里写的命名一一对应:
 *   user-auth.access-secret  → UserAuth.accessSecret
 *   user-auth.access-expire  → UserAuth.accessExpire
 *
 * 数据库 / Redis 字段没放在这里 —— 它们由 spring-boot-starter-data-jpa / data-redis
 * 自动接管(spring.datasource.*、spring.data.redis.*),Java 自己不用再镜像一份,这是 Spring Boot 收编的体现。
 */
@ConfigurationProperties(prefix = "user-auth")
public class Config {

    /**
     * 对应 Go: UserAuth.AccessSecret(etcd.user.yaml)
     * 用途:JWT 签名密钥,登录成功后由 api 层用 secret 签 token;这里 rpc 服务只校验/读取 token,
     * 但仍然需要 secret 用于可能的解析场景(本服务暂不签发,只在配置中保留 1:1)。
     */
    private String accessSecret;

    /**
     * 对应 Go: UserAuth.AccessExpire = 2592000(30 天,秒)
     * 用途:登出时往 Redis 写 JWT 黑名单,TTL 必须 >= AccessExpire,否则 token 已过期但黑名单还占内存。
     * LogoutLogic 直接读这个值,和 Go 端语义一致。
     */
    private long accessExpire;

    public String getAccessSecret() {
        return accessSecret;
    }

    public void setAccessSecret(String accessSecret) {
        this.accessSecret = accessSecret;
    }

    public long getAccessExpire() {
        return accessExpire;
    }

    public void setAccessExpire(long accessExpire) {
        this.accessExpire = accessExpire;
    }
}