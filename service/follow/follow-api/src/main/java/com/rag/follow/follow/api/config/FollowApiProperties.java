package com.rag.follow.follow.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * follow-api 模块配置聚合点 —— Spring Boot {@code @ConfigurationProperties}。
 *
 * <p>对应 Go: {@code service/follow/api/etc/followcenter.yaml} 的 {@code UserAuth} 段
 * （Go 侧是 {@code conf.MustLoad} 强转进 {@code internal/config/config.go} 的 {@code Config} struct）。
 *
 * <p><b>L1 外部契约冻结（默认值即 Go yaml 的字面量）</b>：
 * <table border="1">
 *   <caption>默认值对照</caption>
 *   <tr><th>字段</th><th>Go 值</th></tr>
 *   <tr><td>{@code userAuth.accessSecret}</td><td>{@code ae0536f9-6450-4606-8e13-5a19ed505da0}</td></tr>
 *   <tr><td>{@code userAuth.accessExpire}</td><td>{@code 31536000}（365 天）</td></tr>
 * </table>
 *
 * <p><b>⚠️ 这个 secret 与 user-api 的 user 段是同一个值</b>：
 * Go 侧 {@code followcenter.yaml:11} 与 {@code user/api/etc/*.yaml} 都写
 * {@code ae0536f9-6450-4606-8e13-5a19ed505da0} ⇒ 用户拿 user-api 签发的 token
 * 可以直接调 follow 域，<b>跨服务共用一把钥匙是 Go 的既定设计</b>，不是复用错误。
 * 改成不同的值会让所有已签发 token 在 follow 域验签失败（401 空 body）。
 *
 * <p><b>本模块只验签、不签发</b>：Go 侧 follow/api 没有 login / register 端点，
 * {@code accessSecret} 的用途是 {@code rest.WithJwt} 的验签密钥，签发方是 user-api。
 *
 * <p><b>⚠️ BizRedis 不在这里</b>：Go 的 {@code Config.BizRedis} 在本服务只被
 * {@code servicecontext.go:23} 用来建一个 Redis 连接、交给黑名单中间件；
 * 它<b>不参与任何业务数据读写</b>（follow/api 层零缓存）。⇒ 落到
 * {@code spring.data.redis.*}（Spring Boot 的标准连接配置）而不是自定义 properties，
 * 避免为一个「只有一个消费者、且消费者只要 hasKey」的连接单造一层配置对象。
 *
 * <p>{@code accessExpire} 在本模块<b>不被读取</b>：Go 侧同样如此 ——
 * {@code api/internal/config/config.go} 的 {@code AccessExpire} 没有任何 logic 读它。
 * 保留字段是为了让 yaml 与 Go 端 1:1 可对照，不做「顺手清理」。
 */
@ConfigurationProperties(prefix = "follow-api")
public class FollowApiProperties {

    @NestedConfigurationProperty
    private UserAuth userAuth = new UserAuth();

    public UserAuth getUserAuth() {
        return userAuth;
    }

    public void setUserAuth(UserAuth userAuth) {
        this.userAuth = userAuth;
    }

    /** 对应 Go {@code UserAuth} 段。 */
    public static class UserAuth {
        private String accessSecret;
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
}