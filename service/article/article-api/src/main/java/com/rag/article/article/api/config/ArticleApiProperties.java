package com.rag.article.article.api.config;

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