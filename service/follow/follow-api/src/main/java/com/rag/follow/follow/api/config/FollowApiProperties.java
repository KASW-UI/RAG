package com.rag.follow.follow.api.config;

import jakarta.annotation.PostConstruct;
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
 * <p>🔴 <b>上表是「Go 值」，不是「RAG 侧的默认值」</b>（2026-10-29 · G1 阶段 1 · P0 #8）：
 * RAG 侧 {@code application.yml} 的 {@code access-secret}
 * <b>已去掉明文默认值</b>（修前是「环境变量名 + 冒号 + 明文默认值」的占位符写法，
 * 默认值即上表那个<b>写在仓里的公开密钥</b>）⇒ ⇒ <b>缺变量 ⇒ 启动失败</b>
 * ⇒ ⇒ 部署方必须用环境变量提供 ⇒ ⇒ 上表那行只在<b>环境变量被提供时</b>才是本模块的取值
 * ⇒ ⇒ 详见 {@link #verifyAccessSecret()}。
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

    /**
     * 🔴 **启动期强制：验签密钥不得为空**（2026-10-29 · G1 阶段 1 · P0 #8）。
     *
     * <h3>为什么这一条不能省（Spring 的坑）</h3>
     * <p>yaml 里写的是 {@code ${USER_JWT_SECRET}}（**无默认值**）⇒ 但**实测**（2026-10-29，
     * 真 {@code SpringApplication} + 真 ConfigData 流程，见
     * {@code FollowApiPropertiesSecretGuardTest#realYamlWithoutEnvVarFailsTheContext}）
     * 有<b>两条</b>都能悄悄起来：
     * <ol>
     *   <li><b>变量没设</b> ⇒ Spring 的 {@code @ConfigurationProperties} 绑定器
     *       <b>不报错</b>，而是把<b>占位符原文</b> {@code "${USER_JWT_SECRET}"}
     *       <b>当成值绑上去</b> ⇒ ⇒ 应用<b>照常启动</b>，且这个「密钥」是<b>公开可知的字面量</b>
     *       ⇒ ⇒ 🔴 比空密钥更危险（HMAC 密钥 = 仓里公开的一串字符 ⇒ 谁都能伪造 token）；</li>
     *   <li><b>变量设了但值是空串</b> ⇒ Spring 的
     *       {@code PropertyPlaceholderHelper} 会把<b>空串当合法值</b>
     *       （<b>不走默认值、也不报错</b>）⇒ ⇒ 属性绑成 {@code ""}
     *       ⇒ ⇒ <b>应用带着空签名密钥正常起来</b> ⇒ ⇒ 🔴 <b>token 可伪造</b>。</li>
     * </ol>
     * ⇒ ⇒ 所以「去掉 yaml 默认值」<b>只堵住了一半</b> ⇒ ⇒ <b>另一半必须由代码堵</b> ⇒ 即本方法。
     * <p>⚠️ <b>空串 ⇒ 启动失败</b>（而不是「启动成功但验签永远失败」）⇒ 因为
     * 「用空密钥签发的 token」是<b>可被任何人算出来的</b>（HMAC 空密钥）⇒ ⇒ 属于<b>静默的安全降级</b>。
     *
     * <h3>为什么用 {@code @PostConstruct} 而不是 {@code @Validated + @NotBlank}</h3>
     * <p>后者要引入 {@code spring-boot-starter-validation}（<b>新增依赖</b>）⇒ ⇒ 本模块
     * <b>零新增依赖</b>更稳；且这里的判据是「<b>不得为空、也不得是未解析的占位符</b>」
     * 而不是「长度/格式」⇒ 显式抛异常更直白。
     *
     * <h3>Go 出处与语义等价论证</h3>
     * <p>Go 侧 {@code service/follow/api/etc/followcenter.yaml:11} 写的是<b>字面量</b>
     * （36 字节 UUID）⇒ ⇒ <b>Go 侧同样有「公开默认密钥」这个问题</b>
     * ⇒ ⇒ 本条属 <b>RAG 侧更严</b>的一侧（AGENTS.md §0 的共性：「RAG 做了更严/更安全的那一侧」
     * ⇒ 对拍看到它不同<b>先确认是不是签字项</b>）⇒ ⇒ <b>登记为有意偏离</b>，不是「RAG 抄错了 Go」。
     * <p>语义等价论证：<b>变量一旦被提供</b>，本方法不改动任何值 ⇒ 验签密钥与 Go 侧<b>逐字节一致</b>
     * ⇒ ⇒ 验签行为零变化（Go 侧消费点 = {@code service/follow/api/internal/handler/routes.go:62}
     * 的 {@code rest.WithJwt(serverCtx.Config.UserAuth.AccessSecret)}）。
     */
    @PostConstruct
    void verifyAccessSecret() {
        String secret = userAuth == null ? null : userAuth.getAccessSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "follow-api.user-auth.access-secret 不能为空："
                            + "请设置环境变量 USER_JWT_SECRET（与签发方 user-api 一致）"
                            + "—— 本模块只验签、不签发，空密钥会让任何人都能伪造 JWT");
        }
        if (UNRESOLVED_PLACEHOLDER.matcher(secret).matches()) {
            throw new IllegalStateException(
                    "follow-api.user-auth.access-secret 未配置（绑到了未解析的占位符字面量 " + secret + "）："
                            + "请设置环境变量 USER_JWT_SECRET（与签发方 user-api 一致）"
                            + "—— 本模块只验签、不签发，占位符原文是公开可知的「密钥」⇒ 任何人都能伪造 JWT");
        }
    }

    /**
     * 「整串<b>就是</b>一个未解析的 {@code ${...}} 占位符」⇒ 说明环境变量<b>根本没设</b>。
     *
     * <p>⚠️ <b>为什么必须专门识别它（2026-10-29 实测）</b>：Spring 的
     * {@code @ConfigurationProperties} 绑定器遇到<b>解析不出来</b>的占位符时，
     * <b>不会抛异常</b>，而是把<b>占位符原文</b>当成值绑上去
     * （实测：真 {@code SpringApplication} 启动 ⇒ {@code accessSecret} = {@code "${USER_JWT_SECRET}"}，
     * <b>应用照常启动</b>）⇒ ⇒ 若只判「空 / 空白」，这一条就<b>漏过去了</b>
     * ⇒ ⇒ 「yaml 不给默认值」不等于「缺变量会启动失败」⇒ ⇒ 必须在这里显式堵。
     */
    private static final java.util.regex.Pattern UNRESOLVED_PLACEHOLDER =
            java.util.regex.Pattern.compile("^\\$\\{[^}]*\\}$");

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