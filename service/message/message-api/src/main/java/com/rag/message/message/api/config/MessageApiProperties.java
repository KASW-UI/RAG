package com.rag.message.message.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * message-api 模块配置聚合点 —— Spring Boot {@code @ConfigurationProperties}。
 *
 * <p>对应 Go: {@code service/message/api/etc/messagecenter.yaml} 的
 * {@code UserAuth} / {@code AdminAuth} 两段（Go 侧是 {@code conf.MustLoad} 强转进
 * {@code internal/config/config.go} 的 {@code Config} struct）。
 *
 * <p><b>L1 外部契约冻结（默认值即 Go yaml 的字面量）</b>：
 * <table border="1">
 *   <caption>默认值对照</caption>
 *   <tr><th>字段</th><th>Go 值</th></tr>
 *   <tr><td>{@code userAuth.accessSecret}</td><td>{@code ae0536f9-6450-4606-8e13-5a19ed505da0}</td></tr>
 *   <tr><td>{@code userAuth.accessExpire}</td><td>{@code 31536000}（365 天，<b>注意与 user-api 的 30 天不同</b>）</td></tr>
 *   <tr><td>{@code adminAuth.accessSecret}</td><td>{@code yU-hCm)2*?NQmWG}</td></tr>
 *   <tr><td>{@code adminAuth.accessExpire}</td><td>{@code 2592000}（30 天）</td></tr>
 * </table>
 *
 * <p><b>本模块只验签、不签发</b>：Go 侧 message api 没有 login / register 端点，
 * 两个 accessSecret 的用途是 {@code rest.WithJwt} 的验签密钥。
 * 签发方分别在 user-api（user 段）与 admin-api（admin 段）。
 * 两段密钥必须与签发方一致，否则验签必失败 —— 该失败表现为
 * <b>HTTP 401 + 空 body</b>（不是业务码），排查时先看这里。
 *
 * <p>{@code accessExpire} 在本模块<b>不被读取</b>：Go 侧同样如此 ——
 * {@code api/internal/config/config.go} 的 {@code AccessExpire} 没有任何 logic 读它
 * （签发在别的服务）。保留字段是为了让 yaml 与 Go 端 1:1 可对照，不做「顺手清理」。
 */
@ConfigurationProperties(prefix = "message-api")
public class MessageApiProperties {

    @NestedConfigurationProperty
    private UserAuth userAuth = new UserAuth();

    @NestedConfigurationProperty
    private AdminAuth adminAuth = new AdminAuth();

    public UserAuth getUserAuth() {
        return userAuth;
    }

    public void setUserAuth(UserAuth userAuth) {
        this.userAuth = userAuth;
    }

    public AdminAuth getAdminAuth() {
        return adminAuth;
    }

    public void setAdminAuth(AdminAuth adminAuth) {
        this.adminAuth = adminAuth;
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

    /** 对应 Go {@code AdminAuth} 段。 */
    public static class AdminAuth {
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