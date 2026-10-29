package com.rag.users.user.api.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * user-api 模块配置聚合点 —— Spring Boot @ConfigurationProperties。
 *
 * 对应 Go: service/user/user/api/etc/usercenter.yaml 的 user-api.user-auth 段；
 *   Go 端是 conf.MustLoad(...) 强转到 Config struct。
 *
 * 框架职责:
 *   - @ConfigurationProperties 把 application.yaml 中 user-api.* 段自动绑定到这个类的字段
 *   - @NestedConfigurationProperty 用于嵌套对象（userAuth 段）
 *   - userAuth.accessSecret / accessExpire 同时被 JWT 签发（user-api LoginLogic）和下
 *     游 user-rpc LogoutLogic 的 Redis TTL 使用（D-007）
 */
@ConfigurationProperties(prefix = "user-api")
public class UserApiProperties {

    private String name = "user-api";

    @NestedConfigurationProperty
    private UserAuth userAuth = new UserAuth();

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public UserAuth getUserAuth() { return userAuth; }
    public void setUserAuth(UserAuth userAuth) { this.userAuth = userAuth; }

    /**
     * 🔴 <b>启动期强制：验签/签名密钥不得为空、不得是未解析的占位符</b>（2026-10-29 · G1 阶段 1 · P0 #8）。
     *
     * <p>yaml 里写的是 {@code ${USER_JWT_SECRET}}（<b>无默认值</b>）⇒ <b>两条路都要堵</b>：
     * <ol>
     *   <li><b>变量设了但值是空串</b> ⇒ Spring 的 {@code PropertyPlaceholderHelper}
     *       会把<b>空串当合法值</b>（<b>不走默认值、也不报错</b>）⇒ ⇒ 属性绑成 {@code ""}
     *       ⇒ ⇒ 应用带着<b>空签名密钥</b>正常起来 ⇒ ⇒ 🔴 <b>token 可伪造</b>。</li>
     *   <li><b>变量没设</b> ⇒ ⚠️ <b>绑上的不是 {@code null}，而是占位符字面量</b>
     *       （实测口径见下）⇒ ⇒ 只判「null/空串」**堵不住这条路** ⇒ ⇒ 必须显式拒绝「仍是占位符」的值。</li>
     * </ol>
     *
     * <p>🔴 <b>第 ② 条的框架实测口径（AGENTS.md §3.3.1：不靠记忆，读消费点）</b>
     * <ul>
     *   <li>{@code SpringConfigurationPropertySource#getConfigurationProperty} 取值走
     *       {@code getPropertySourceProperty} → {@code propertySource.getProperty(name)}
     *       ⇒ 拿到的是<b>原始标量</b>（此处 = {@code "${USER_JWT_SECRET}"}），<b>不在此处解析占位符</b>；</li>
     *   <li>随后的占位符解析由 {@code PropertySourcesPlaceholdersResolver} 承担，
     *       其 {@code PropertyPlaceholderHelper(..., ignoreUnresolvablePlaceholders = true)}
     *       ⇒ ⇒ <b>解析不出来时占位符原样留下</b> ⇒ ⇒ 最终绑进去的值是字面量 {@code "${USER_JWT_SECRET}"}。</li>
     * </ul>
     * ⇒ ⇒ 「那个字面量」是<b>公开在仓里的常量</b> ⇒ 用它当 HMAC 密钥 = 等于把密钥公开 ⇒
     * ⇒ ⇒ <b>必须判掉</b>（否则「缺变量 ⇒ 启动失败」这条承诺是假的）。
     *
     * <p>⚠️ <b>空串 / 占位符 ⇒ 启动失败</b>（而不是「启动成功但验签永远失败」）⇒ 因为这两种值
     * <b>都可被任何人算出来</b>（空 HMAC 密钥 / 公开字面量）⇒ ⇒ 属于<b>静默的安全降级</b>。
     *
     * <p>⚠️ <b>Go 侧对照（§3.1）**：Go {@code service/user/user/api/etc/usercenter.yaml:11}
     * 写的是<b>字面量</b>（公开在仓里）⇒ ⇒ 本条属 <b>RAG 侧更严</b>的一侧
     * ⇒ ⇒ 登记为有意偏离（不是「RAG 抄错了 Go」）。
     */
    @PostConstruct
    void verifyAccessSecret() {
        String secret = userAuth == null ? null : userAuth.getAccessSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "user-api.user-auth.access-secret 不能为空：请设置环境变量 USER_JWT_SECRET（与 user-rpc 共享同一把签发密钥）"
                            + "—— 本模块既签发又验签，空密钥会让任何人都能伪造 JWT");
        }
        if (secret.contains("${")) {
            throw new IllegalStateException(
                    "user-api.user-auth.access-secret 仍是未解析的占位符（" + secret + "）：请设置环境变量 USER_JWT_SECRET"
                            + "—— 占位符字面量是公开在仓里的常量，拿它当密钥 = 密钥公开");
        }
    }

    public static class UserAuth {
        private String accessSecret;
        private long accessExpire;

        public String getAccessSecret() { return accessSecret; }
        public void setAccessSecret(String accessSecret) { this.accessSecret = accessSecret; }
        public long getAccessExpire() { return accessExpire; }
        public void setAccessExpire(long accessExpire) { this.accessExpire = accessExpire; }
    }
}