package com.rag.favorite.favorite.api.config;

import jakarta.annotation.PostConstruct;
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

    /**
     * 🔴 **启动期强制：验签密钥不得为空**（2026-10-29 · G3 阶段 1 · P0 #8）。
     *
     * <h3>为什么这一条不能省（Spring 的坑）</h3>
     * <p>yaml 里写的是 {@code ${FAVORITE_JWT_SECRET}}（**无默认值**）⇒ 分两种情况：
     * <ol>
     *   <li><b>变量没设</b> ⇒ Spring 的 placeholder <b>不可解析</b> ⇒ 绑定期直接炸
     *       ⇒ 已经是 fail-closed ✔；</li>
     *   <li><b>变量设了但值是空串</b> ⇒ Spring 的
     *       {@code PropertyPlaceholderHelper} 会把<b>空串当合法值</b>
     *       （<b>不走默认值、也不报错</b>）⇒ ⇒ 属性绑成 {@code ""}
     *       ⇒ ⇒ <b>应用带着空签名密钥正常起来</b> ⇒ ⇒ 🔴 <b>token 可伪造</b>。</li>
     * </ol>
     * ⇒ ⇒ 所以「去掉 yaml 默认值」只堵住第 ① 条 ⇒ ⇒ **第 ② 条必须由代码堵** ⇒ 即本方法。
     * <p>⚠️ <b>空串 ⇒ 启动失败</b>（而不是「启动成功但验签永远失败」）⇒ 因为
     * 「用空密钥签发的 token」是<b>可被任何人算出来的</b>（HMAC 空密钥）⇒ ⇒ 属于**静默的安全降级**。
     *
     * <h3>为什么用 {@code @PostConstruct} 而不是 {@code @Validated + @NotBlank}</h3>
     * <p>后者要引入 {@code spring-boot-starter-validation}（<b>新增依赖</b>）⇒ ⇒ 本模块
     * <b>零新增依赖</b>更稳；且这里的判据是「<b>不得为空</b>」而不是「长度/格式」⇒ 显式抛异常更直白。
     *
     * <h3>Go 出处</h3>
     * <p>Go 侧 {@code service/favorite/api/etc/favorite.yaml:11} 写的是<b>字面量</b>
     * {@code ae0536f9-6450-4606-8e13-5a19ed505da0} ⇒ ⇒ <b>Go 侧同样有「公开默认密钥」这个问题</b>
     * ⇒ ⇒ 本条属 <b>RAG 侧更严</b>的一侧（AGENTS.md §0 的共性：「RAG 做了更严/更安全的那一侧」
     * ⇒ 对拍看到它不同**先确认是不是签字项**）⇒ ⇒ **登记为有意偏离**，不是「RAG 抄错了 Go」。
     */
    @PostConstruct
    void verifyAccessSecret() {
        String secret = userAuth == null ? null : userAuth.getAccessSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "favorite-api.user-auth.access-secret 不能为空："
                            + "请设置环境变量 FAVORITE_JWT_SECRET（与签发方 user-api 一致）"
                            + "—— 本模块只验签、不签发，空密钥会让任何人都能伪造 JWT");
        }
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
