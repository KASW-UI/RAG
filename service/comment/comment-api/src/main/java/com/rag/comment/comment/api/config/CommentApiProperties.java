package com.rag.comment.comment.api.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.util.regex.Pattern;

/**
 * comment-api 配置聚合点 —— Spring Boot {@code @ConfigurationProperties}。
 *
 * <p>对应 Go：{@code service/comment/api/internal/config/config.go} 的 {@code Config} struct，
 * 数据来自 {@code etc/commentservice.yaml}（Go 侧是 {@code conf.MustLoad} 强转）。
 *
 * <p><b>L1 外部契约冻结（默认值即 Go yaml 的字面量）</b>：
 * <table border="1">
 *   <caption>默认值对照</caption>
 *   <tr><th>字段</th><th>Go 值</th><th>出处</th></tr>
 *   <tr><td>{@code userAuth.accessSecret}</td>
 *       <td><b>36 字节 UUID</b>（Go 侧 yaml 的字面量 —— ⚠️ <b>值不贴在本仓</b>：
 *       它就是那把「公开的验签密钥」）</td>
 *       <td>{@code commentservice.yaml:6}</td></tr>
 *   <tr><td>{@code userAuth.accessExpire}</td>
 *       <td>{@code 31536000}（365 天）</td>
 *       <td>{@code commentservice.yaml:7}</td></tr>
 *   <tr><td>{@code commentRpc.target}</td>
 *       <td>{@code 127.0.0.1:9001}</td>
 *       <td>{@code commentservice.yaml:22}</td></tr>
 * </table>
 *
 * <p><b>本模块只验签、不签发</b>：Go 侧 comment api 没有 login / register 端点，
 * {@code AccessSecret} 的唯一用途是 {@code rest.WithJwt(...)} 的验签密钥
 * （{@code routes.go:54}）。签发方在 user-api。
 * 该密钥必须与签发方一致，否则验签必失败 ——
 * 失败表现为 <b>HTTP 401 + 空 body</b>（不是业务码），排查时先看这里。
 *
 * <p><b>{@code accessExpire} 在本模块不被读取</b>：Go 侧同样如此 ——
 * {@code internal/config/config.go} 的 {@code AccessExpire} 没有任何 logic 读它。
 * 保留字段是为了让配置与 Go 端 1:1 可对照，不做「顺手清理」。
 *
 * <p><b>与 Go 的一处表达层差异（已知、可配置）</b>：Go 的
 * {@code CommentRpc.Target: 127.0.0.1:9001} 是<b>直连</b>，
 * 而本模块默认走 Nacos 自动发现 {@code discovery:///comment-rpc}（与 RAG 其余 api 一致）。
 * 两者都是「找到 comment-rpc 的地址」，属实现机制；需要直连时把
 * {@code grpc.client.comment-rpc.address} 覆盖成显式 host:port 即可，无需改代码。
 */
@ConfigurationProperties(prefix = "comment-api")
public class CommentApiProperties {

    @NestedConfigurationProperty
    private UserAuth userAuth = new UserAuth();

    @NestedConfigurationProperty
    private CommentRpc commentRpc = new CommentRpc();

    public UserAuth getUserAuth() {
        return userAuth;
    }

    public void setUserAuth(UserAuth userAuth) {
        this.userAuth = userAuth;
    }

    /**
     * 🔴 **启动期强制：验签密钥不得为空、也不得是未解析的占位符**
     * （2026-10-29 · G3 阶段 1 · P0 #8；2026-10-29 本轮补「未解析占位符」判据）。
     *
     * <h3>为什么「去掉 yaml 默认值」<b>只堵住了一半</b></h3>
     * <p>yaml 里写的是 {@code ${USER_JWT_SECRET}}（**无默认值**）⇒ 但<b>实测</b>（真
     * {@code SpringApplication} + 真 ConfigData 流程 ⇒ 见
     * {@code CommentApiPropertiesSecretGuardTest#prodLikeStartupWithoutEnvVarFails}）
     * 有<b>两条</b>都能悄悄起来：
     * <ol>
     *   <li><b>变量没设</b> ⇒ Spring 的 {@code @ConfigurationProperties} 绑定器
     *       <b>不报错</b>，而是把<b>占位符原文</b>（形如 {@code "${USER_JWT_SECRET}"}）
     *       <b>当成值绑上去</b> ⇒ ⇒ 应用<b>照常启动</b>
     *       ⇒ ⇒ 而这个「密钥」是<b>仓里公开可知的一串字符</b>
     *       ⇒ ⇒ 🔴 <b>比空密钥更危险</b>（HMAC 密钥公开 ⇒ 谁都能伪造 token）；</li>
     *   <li><b>变量设了但值是空串</b> ⇒ Spring 的 {@code PropertyPlaceholderHelper}
     *       会把<b>空串当合法值</b>（<b>不走默认值、也不报错</b>）⇒ ⇒ 属性绑成 {@code ""}
     *       ⇒ ⇒ 应用带着<b>空签名密钥</b>正常起来 ⇒ ⇒ 🔴 <b>token 可伪造</b>。</li>
     * </ol>
     * ⇒ ⇒ 所以「去掉 yaml 默认值」<b>只堵住了一半</b> ⇒ ⇒ <b>另一半必须由代码堵</b> ⇒ 即本方法。
     *
     * <h3>为什么空串 / 占位符 ⇒ 启动失败（而不是「启动成功但验签永远失败」）</h3>
     * <p>「用空密钥签发的 token」是<b>可被任何人算出来的</b>（HMAC 空密钥），
     * 而「用占位符原文当密钥」是<b>公开可知</b>的 ⇒ ⇒ 两者都属于<b>静默的安全降级</b>
     * ⇒ ⇒ 宁可起不来。
     *
     * <h3>判据</h3>
     * <p><b>null / 空串 / 全空白 / 未解析的占位符字面量</b> ⇒ 不可用 ⇒ 抛
     * （实现见 {@link #requireSecret(String, String, String)} 与 {@link #UNRESOLVED_PLACEHOLDER}）。
     *
     * <h3>Go 出处与语义等价论证</h3>
     * <p>Go 侧 {@code service/comment/api/etc/commentservice.yaml:6} 写的是<b>字面量</b>
     * ⇒ ⇒ <b>Go 侧同样有「公开默认密钥」这个问题</b> ⇒ ⇒ 本条属 <b>RAG 侧更严</b>的一侧
     * ⇒ ⇒ 登记为有意偏离（不是「RAG 抄错了 Go」）。
     * <p>语义等价论证：<b>变量一旦被提供</b>，本方法<b>不改动任何值</b> ⇒ 验签密钥与 Go 侧
     * <b>逐字节一致</b> ⇒ ⇒ 验签行为零变化（Go 侧消费点 =
     * {@code service/comment/api/internal/handler/routes.go:54} 的
     * {@code rest.WithJwt(serverCtx.Config.UserAuth.AccessSecret)}）。
     *
     * <h3>为什么用 {@code @PostConstruct} 而不是 {@code @Validated + @NotBlank}</h3>
     * <p>后者要引入 {@code spring-boot-starter-validation}（<b>新增依赖</b>）⇒ ⇒ 本模块
     * <b>零新增依赖</b>更稳；且这里的判据是「<b>不得为空、也不得是未解析的占位符</b>」
     * 而不是「长度/格式」⇒ 显式抛异常更直白。
     */
    @PostConstruct
    void verifyAccessSecret() {
        String secret = userAuth == null ? null : userAuth.getAccessSecret();
        requireSecret(secret, "comment-api.user-auth.access-secret", "USER_JWT_SECRET");
    }

    /**
     * 判据本体：<b>null / 空串 / 全空白 / 未解析的占位符字面量</b> ⇒ 不可用 ⇒ 抛。
     *
     * @param secret       绑到的密钥值（可能是 {@code null}）
     * @param propertyPath 出问题的配置路径（写进报错 ⇒ 运维能直接定位）
     * @param envVar       对应的环境变量名（写进报错）
     */
    private static void requireSecret(String secret, String propertyPath, String envVar) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    propertyPath + " 不能为空："
                            + "请设置环境变量 " + envVar + "（与签发方 user-api 一致）"
                            + "—— 本模块只验签、不签发，空密钥会让任何人都能伪造 JWT");
        }
        if (UNRESOLVED_PLACEHOLDER.matcher(secret).matches()) {
            throw new IllegalStateException(
                    propertyPath + " 未配置（绑到了未解析的占位符字面量 " + secret + "）："
                            + "请设置环境变量 " + envVar + "（与签发方 user-api 一致）"
                            + "—— 本模块只验签、不签发，占位符原文是公开可知的「密钥」⇒ 任何人都能伪造 JWT");
        }
    }

    /**
     * 「整串<b>就是</b>一个未解析的 {@code ${...}} 占位符」⇒ 说明环境变量<b>根本没设</b>。
     *
     * <p>⚠️ <b>为什么必须专门识别它（2026-10-29 实测）</b>：Spring 的
     * {@code @ConfigurationProperties} 绑定器遇到<b>解析不出来</b>的占位符时，
     * <b>不会抛异常</b>，而是把<b>占位符原文</b>当成值绑上去
     * ⇒ ⇒ 若只判「空 / 空白」，这一条就<b>漏过去了</b>
     * ⇒ ⇒ 「yaml 不给默认值」**不等于**「缺变量会启动失败」⇒ ⇒ 必须在这里显式堵。
     */
    private static final Pattern UNRESOLVED_PLACEHOLDER = Pattern.compile("^\\$\\{[^}]*\\}$");

    public CommentRpc getCommentRpc() {
        return commentRpc;
    }

    public void setCommentRpc(CommentRpc commentRpc) {
        this.commentRpc = commentRpc;
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

    /** 对应 Go {@code CommentRpc}（{@code zrpc.RpcClientConf}）段。 */
    public static class CommentRpc {
        private String target;

        public String getTarget() {
            return target;
        }

        public void setTarget(String target) {
            this.target = target;
        }
    }
}