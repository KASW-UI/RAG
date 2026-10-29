package com.rag.users.user.rpc.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * user-rpc 模块配置聚合点 —— Spring Boot @ConfigurationProperties。
 *
 * 对应 Go: service/user/user/rpc/etc/user.yaml 的 user-rpc.user-auth 段；
 *   Go 端是 conf.MustLoad(...) 强转到 Config struct。
 *
 * 框架职责:
 *   - @ConfigurationProperties 把 application.yaml 中 user-rpc.* 段自动绑定到这个类的字段
 *   - 取代了原 internal.config.Config
 *   - @NestedConfigurationProperty 用于嵌套对象（userAuth 段）
 *   - MyBatis-Plus 不需要在这里声明 mapper 路径，它通过 @MapperScan 自动发现
 *
 * UserAuth 段对应 Go 端 UserAuth.AccessSecret / UserAuth.AccessExpire（JWT 配置）：
 *   - accessSecret: 用于 JWT HS256 签名（user-api / admin-api 登录成功后签发）
 *   - accessExpire: token 有效期（秒）。D-007：LogoutLogic Redis Setex TTL 必须用此值，不能写死 24h
 */
@ConfigurationProperties(prefix = "user-rpc")
public class UserRpcProperties {

    private String name = "user-rpc";

    @NestedConfigurationProperty
    private UserAuth userAuth = new UserAuth();

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public UserAuth getUserAuth() { return userAuth; }
    public void setUserAuth(UserAuth userAuth) { this.userAuth = userAuth; }

    /**
     * 🔴 <b>启动期强制：验签密钥不得为空、不得是未解析的占位符</b>（2026-10-29 · G1 阶段 1 · P0 #8）。
     *
     * <p>yaml 里写的是 {@code ${USER_JWT_SECRET}}（<b>无默认值</b>）⇒ <b>两条路都要堵</b>：
     * <ol>
     *   <li><b>变量设了但值是空串</b> ⇒ Spring 的 {@code PropertyPlaceholderHelper}
     *       会把<b>空串当合法值</b>（<b>不走默认值、也不报错</b>）⇒ ⇒ 属性绑成 {@code ""}
     *       ⇒ ⇒ 应用带着<b>空签名密钥</b>正常起来 ⇒ ⇒ 🔴 <b>token 可伪造</b>。</li>
     *   <li><b>变量没设</b> ⇒ ⚠️ <b>绑上的不是 {@code null}，而是占位符字面量</b>
     *       （实测口径见下）⇒ ⇒ 只判「null/空串」<b>堵不住这条路</b> ⇒ ⇒ 必须显式拒绝「仍是占位符」的值。</li>
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
     * <p>⚠️ <b>Go 侧对照（§3.1）</b>：Go {@code service/user/user/rpc/etc/user.yaml:34} 写的是<b>字面量</b>
     * （公开在仓里）⇒ ⇒ 本条属 <b>RAG 侧更严</b>的一侧 ⇒ ⇒ 登记为有意偏离（不是「RAG 抄错了 Go」）。
     * <p>（补充事实：Go 侧 user-rpc <b>只消费 AccessExpire</b>（{@code internal/logic/logoutlogic.go:36}），
     * <b>不消费 AccessSecret</b> ⇒ ⇒ 本条是<b>纯 fail-closed 收紧</b>，不改变任何已存在的运行时取值。）
     */
    @PostConstruct
    void verifyAccessSecret() {
        String secret = userAuth == null ? null : userAuth.getAccessSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "user-rpc.user-auth.access-secret 不能为空：请设置环境变量 USER_JWT_SECRET（与签发方 user-api 一致）"
                            + "—— 空密钥会让任何人都能伪造 user JWT");
        }
        if (secret.contains("${")) {
            throw new IllegalStateException(
                    "user-rpc.user-auth.access-secret 仍是未解析的占位符（" + secret + "）：请设置环境变量 USER_JWT_SECRET"
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
