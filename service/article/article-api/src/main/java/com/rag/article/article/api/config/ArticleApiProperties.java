package com.rag.article.article.api.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * article-api 的配置 —— 对应 Go {@code service/article/api/internal/config/config.go} 的 {@code Config}。
 *
 * <pre>
 * Go:
 *   type Config struct {
 *       rest.RestConf                       // Host / Port / Timeout / MaxBytes / Middlewares / Auth?…
 *       Auth struct {
 *           AccessSecret string
 *           AccessExpire int64
 *       }
 *       ArticleRpcConf  zrpc.RpcClientConf
 *       SecurityRpcConf zrpc.RpcClientConf
 *       UserRpcConf     zrpc.RpcClientConf
 *   }
 * </pre>
 *
 * <h2>三类字段的去向（不要在本类里堆在一起）</h2>
 * <table border="1">
 *   <caption>Go Config 各段的 Java 落点</caption>
 *   <tr><th>Go 段</th><th>Java 落点</th><th>理由</th></tr>
 *   <tr>
 *     <td>{@code Auth.*}</td>
 *     <td><b>本类</b></td>
 *     <td>业务相关，只被 {@code JwtAuthInterceptor} 读</td>
 *   </tr>
 *   <tr>
 *     <td>{@code RestConf} 的 Host/Port/Timeout/MaxBytes/Middlewares</td>
 *     <td><b>{@code application.yml}</b> 的 {@code server.*} / {@code observability.*} / {@code rag.protection.*}</td>
 *     <td>这些是框架级旋钮，Spring Boot 有自己的键名；
 *         保护层四道闸门由 {@code com.rag.common.protection} 统一承接</td>
 *   </tr>
 *   <tr>
 *     <td>{@code ArticleRpcConf} / {@code UserRpcConf}</td>
 *     <td><b>{@code grpc.client.*}</b></td>
 *     <td>devh starter 直接消费；寻址从 etcd flat key 换成 Nacos service name</td>
 *   </tr>
 *   <tr>
 *     <td>{@code SecurityRpcConf}</td>
 *     <td><b>无</b></td>
 *     <td>见下方「SecurityRpc 为什么没有对应实现」</td>
 *   </tr>
 * </table>
 *
 * <h2>⚠️ {@code SecurityRpc} 在 Go 侧<b>配了但没有任何消费者</b></h2>
 *
 * <p>全仓实测（{@code grep -rn "SecurityRpc\|ImageSecurity" article/api/}）只命中三处，
 * <b>全部是「定义它」</b>：
 * <pre>
 *   etc/article-api.yaml:14                 SecurityRpcConf:            ← 配置
 *   internal/config/config.go:18            SecurityRpcConf zrpc.RpcClientConf  ← 字段
 *   internal/svc/service_context.go:17,25   SecurityRpc: imagesecurityservice.NewImageSecurityService(...)  ← 装配
 * </pre>
 * <b>没有任何 logic / handler 读 {@code svcCtx.SecurityRpc}</b>。
 * ⇒ 它的行为<b>对外不可观测</b>（连「启动时能否建连」都只取决于 zrpc 是否解析成功）。
 *
 * <p><b>因此本端刻意不建这个 client。</b>建了就等于凭空新增一个
 * 「启动时必须能解析 security-rpc」的硬依赖 —— 一个 Go 侧不存在的启动前置条件。
 * 若日后 Go 真的在某个 logic 里用上它，再补不迟（届时要连带确认它是否影响响应）。
 *
 * <h2>⚠️ {@code AccessExpire} 在本模块<b>不被读取</b></h2>
 * Go 侧同样如此：{@code config.go} 的 {@code AccessExpire} 没有任何 logic 读它
 * （签发在 user-api / admin-api）。保留字段是为了让 yaml 与 Go 端 1:1 可对照，
 * 不做「顺手清理」—— 那会让两边的 yaml 无法逐行对照。
 */
@ConfigurationProperties(prefix = "article-api")
public class ArticleApiProperties {

    @NestedConfigurationProperty
    private Auth auth = new Auth();

    public Auth getAuth() {
        return auth;
    }

    public void setAuth(Auth auth) {
        this.auth = auth;
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
        String secret = auth == null ? null : auth.getAccessSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "article-api.auth.access-secret 不能为空：请设置环境变量 USER_JWT_SECRET（与签发方 user-api 一致）"
                            + "—— 本模块只验签、不签发，空密钥会让任何人都能伪造 JWT");
        }
    }

    /** 对应 Go {@code Config.Auth} 段。 */
    public static class Auth {
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