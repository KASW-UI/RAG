package com.rag.favorite.favorite.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * favorite-api 模块配置聚合点 —— Spring Boot {@code @ConfigurationProperties}。
 *
 * <p>对应 Go: {@code service/favorite/api/etc/favorite.yaml} 的 {@code UserAuth} 段
 * （Go 侧由 {@code conf.MustLoad} 强转进 {@code internal/config/config.go} 的
 * {@code Config} struct：{@code rest.RestConf} + {@code UserAuth} + {@code FavoriteRpc} +
 * {@code BizRedis}）。
 *
 * <p><b>L1 外部契约冻结（默认值即 Go yaml 的字面量）</b>：
 * <table border="1">
 *   <caption>默认值对照</caption>
 *   <tr><th>字段</th><th>Go 值</th><th>出处</th></tr>
 *   <tr><td>{@code userAuth.accessSecret}</td>
 *       <td>{@code ae0536f9-6450-4606-8e13-5a19ed505da0}</td>
 *       <td>{@code favorite.yaml:11}</td></tr>
 *   <tr><td>{@code userAuth.accessExpire}</td>
 *       <td>{@code 2592000}（30 天）</td>
 *       <td>{@code favorite.yaml:12}</td></tr>
 * </table>
 *
 * <p><b>本模块只验签、不签发</b>：Go 侧 favorite api 没有 login / register 端点，
 * {@code accessSecret} 的唯一用途是 {@code rest.WithJwt} 的验签密钥
 * （{@code api/internal/handler/routes.go:57}）。
 * 签发方是 user-api —— <b>两边密钥必须一致</b>，否则验签必失败，
 * 表现为 <b>HTTP 401</b>。
 *
 * <p>{@code accessExpire} 在本模块<b>不被读取</b>：Go 侧同样如此 ——
 * {@code api/internal/config/config.go:13} 的 {@code AccessExpire} 没有任何 logic 读它
 * （签发在 user-api）。保留字段是为了让 yaml 与 Go 端 1:1 可对照。
 *
 * <p><b>为什么没有 FavoriteRpc / BizRedis 字段</b>：
 * <ul>
 *   <li>{@code FavoriteRpc zrpc.RpcClientConf} 在 Go 侧只是 {@code zrpc.MustNewClient} 的入参；
 *       Java 侧 {@code @GrpcClient("favorite-rpc")} + Nacos discovery 已经接管寻址，
 *       再放一份 yaml 字段就是<b>没有消费者的死配置</b>。</li>
 *   <li>{@code BizRedis} 在 Java 侧由 {@code spring.data.redis.*} 承载（Spring Boot 标准配置），
 *       再造一份自定义属性就是第二套 Redis 配置源。</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "favorite-api")
public class FavoriteApiProperties {

    @NestedConfigurationProperty
    private UserAuth userAuth = new UserAuth();

    public UserAuth getUserAuth() {
        return userAuth;
    }

    public void setUserAuth(UserAuth userAuth) {
        this.userAuth = userAuth;
    }

    /** 对应 Go {@code UserAuth} 段（{@code favorite.yaml:10-12}）。 */
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
