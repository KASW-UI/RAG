package com.rag.like.like.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * like-api 模块配置聚合点 —— Spring Boot {@code @ConfigurationProperties}。
 *
 * <p>对应 Go: {@code service/like/api/etc/likecenter.yaml} 的 {@code UserAuth} 段
 * （Go 侧由 {@code conf.MustLoad} 强转进 {@code internal/config/config.go} 的
 * {@code Config} struct：{@code rest.RestConf} + {@code UserAuth} + {@code LikeRpc}）。
 *
 * <p><b>L1 外部契约冻结（默认值即 Go yaml 的字面量）</b>：
 * <table border="1">
 *   <caption>默认值对照</caption>
 *   <tr><th>字段</th><th>Go 值</th></tr>
 *   <tr><td>{@code userAuth.accessSecret}</td><td>{@code ae0536f9-6450-4606-8e13-5a19ed505da0}</td></tr>
 *   <tr><td>{@code userAuth.accessExpire}</td><td>{@code 31536000}（365 天）</td></tr>
 * </table>
 *
 * <p><b>本模块只验签、不签发</b>：Go 侧 like api 没有 login / register 端点，
 * {@code accessSecret} 的唯一用途是 {@code rest.WithJwt} 的验签密钥。
 * 签发方是 user-api —— <b>两边密钥必须一致</b>，否则验签必失败，
 * 表现为 <b>HTTP 401 + 空 body</b>（不是业务码）。排查时先看这里。
 *
 * <p>{@code accessExpire} 在本模块<b>不被读取</b>：Go 侧同样如此 ——
 * {@code api/internal/config/config.go} 的 {@code AccessExpire} 没有任何 logic 读它
 * （签发在 user-api）。保留字段是为了让 yaml 与 Go 端 1:1 可对照。
 *
 * <p><b>为什么没有 LikeRpc 字段</b>：Go 侧 {@code config.Config} 有
 * {@code LikeRpc zrpc.RpcClientConf}，但那只是 {@code zrpc.MustNewClient} 的入参；
 * Java 侧 {@code @GrpcClient("like-rpc")} + Nacos discovery 已经接管寻址，
 * 再放一份 yaml 字段就是<b>没有消费者的死配置</b>（Go 侧那份有消费者，两边情况不同）。
 * 寻址地址写在与 Go {@code Etcd.Key: like.rpc} 对应的
 * {@code grpc.client.like-rpc.address=discovery:///like-rpc}。
 */
@ConfigurationProperties(prefix = "like-api")
public class LikeApiProperties {

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