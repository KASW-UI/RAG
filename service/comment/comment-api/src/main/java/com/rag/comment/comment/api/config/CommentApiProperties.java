package com.rag.comment.comment.api.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

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
 *       <td>{@code ae0536f9-6450-4606-8e13-5a19ed505da0}</td>
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
     * 🔴 **启动期强制：验签密钥不得为空**（2026-10-29 · G3 阶段 1 · P0 #8）。
     *
     * <p>yaml 里写的是 {@code ${USER_JWT_SECRET}}（**无默认值**）⇒ 分两种情况：
     * <ol>
     *   <li><b>变量没设</b> ⇒ Spring 的 placeholder <b>不可解析</b> ⇒ 绑定期直接炸 ⇒ 已 fail-closed；</li>
     *   <li><b>变量设了但值是空串</b> ⇒ Spring 的 {@code PropertyPlaceholderHelper}
     *       会把<b>空串当合法值</b>（<b>不走默认值、也不报错</b>）⇒ ⇒ 属性绑成 {@code ""}
     *       ⇒ ⇒ 应用带着<b>空签名密钥</b>正常起来 ⇒ ⇒ 🔴 <b>token 可伪造</b>。</li>
     * </ol>
     * ⇒ ⇒ 「去掉 yaml 默认值」只堵住第 ① 条 ⇒ ⇒ **第 ② 条必须由代码堵** ⇒ 即本方法。
     *
     * <p>⚠️ **空串 ⇒ 启动失败**（而不是「启动成功但验签永远失败」）⇒ 因为「用空密钥签发的 token」
     * 是<b>可被任何人算出来的</b>（HMAC 空密钥）⇒ ⇒ 属于<b>静默的安全降级</b>。
     *
     * <p>⚠️ Go 侧写的是<b>字面量</b>（公开在仓里）⇒ ⇒ 本条属 <b>RAG 侧更严</b>的一侧
     * ⇒ ⇒ 登记为有意偏离（不是「RAG 抄错了 Go」）。
     */
    @PostConstruct
    void verifyAccessSecret() {
        String secret = userAuth == null ? null : userAuth.getAccessSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "comment-api.user-auth.access-secret 不能为空：请设置环境变量 USER_JWT_SECRET（与签发方 user-api 一致）"
                            + "—— 本模块只验签、不签发，空密钥会让任何人都能伪造 JWT");
        }
    }

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