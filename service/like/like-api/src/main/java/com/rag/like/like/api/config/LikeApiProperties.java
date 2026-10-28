package com.rag.like.like.api.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.time.Duration;

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
 *   <tr><td>{@code userAuth.accessSecret}</td><td>{@code ae0536f9-6450-4606-8e13-5a19ed505da0}
 *       —— 🔴 <b>2026-10-29 起 RAG 侧不再自带这个默认值</b>（P0 #8）⇒ 改由环境变量
 *       {@code USER_JWT_SECRET} 提供 ⇒ 缺变量 / 空串 ⇒ <b>启动失败</b>
 *       ⇒ 见 {@link #verifyAccessSecret()} ⇒ <b>「RAG 更严」的有意偏离</b></td></tr>
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
 * <p><b>为什么 {@code LikeRpc} 段<b>只有</b> timeout、<b>没有</b>寻址字段</b>：
 * Go 侧 {@code config.Config} 有 {@code LikeRpc zrpc.RpcClientConf}，它有<b>两类</b>字段：
 * <ul>
 *   <li><b>寻址类</b>（{@code Etcd.Hosts} / {@code Key} / {@code Endpoints} / {@code Target}）
 *       —— Java 侧由 {@code @GrpcClient("like-rpc")} + Nacos discovery 接管，
 *       写在与 Go {@code Etcd.Key: like.rpc} 对应的
 *       {@code grpc.client.like-rpc.address=discovery:///like-rpc}。
 *       <b>再放一份 yaml 字段就是没有消费者的死配置</b>（Go 侧那份有消费者，两边情况不同）。</li>
 *   <li><b>语义旋钮类</b>（{@code Timeout}）—— ⚠️ <b>这一类必须有 Java 等价物</b>：
 *       Go 的 {@code Timeout int64 json:",default=2000"} 是<b>单次 RPC 调用的独立上界</b>
 *       （{@code zrpc/client.go:70-71} → {@code clientinterceptors/timeoutinterceptor.go:25}
 *       的 {@code context.WithTimeout(ctx, 2s)}），它与 HTTP 总预算（3s）是<b>两个独立旋钮</b>。
 *       没有它 ⇒ Java 侧会把 Go 判超时（2s~3s 窗口）的调用<b>放过去</b>（DRIFT-LIKEAPI-017）。
 *       ⇒ 见 {@link LikeRpc#getTimeout()}。</li>
 * </ul>
 *
 * <p>⚠️ <b>默认值取自 Go 的 struct tag，不是从 yaml 抄的</b>：
 * Go 的 {@code likecenter.yaml} <b>没有</b> {@code LikeRpc.Timeout} 这一行，
 * 2000ms 来自 {@code zrpc/config.go:31} 的 {@code json:",default=2000"} ——
 * 写不写都是 2000（AGENTS.md §3.3.1 同型：<b>「yaml 里没这个字段」不等于「值是 0」</b>）。
 * ⇒ Java 侧同样<b>不写进 application.yml</b>，由字段默认值承担，与 Go 端 1:1。
 */
@ConfigurationProperties(prefix = "like-api")
public class LikeApiProperties {

    @NestedConfigurationProperty
    private UserAuth userAuth = new UserAuth();

    @NestedConfigurationProperty
    private LikeRpc likeRpc = new LikeRpc();

    /**
     * 🔴 <b>验签密钥的 fail-closed 校验</b>（2026-10-29 · R1·T-1.3 · P0 #8）。
     *
     * <h3>为什么「去掉 yaml 默认值」还不够</h3>
     * <p>主配置 {@code application.yml} 现在写的是 {@code like-api.user-auth.access-secret: ${USER_JWT_SECRET}}
     * ——<b>没有默认值</b> ⇒ 变量<b>完全没设</b>时 Spring 的 placeholder 不可解析 ⇒ 绑定期直接炸
     * ⇒ 启动失败（这一条由 Spring 自己兜住）。
     * <p>🔴 <b>但「变量存在、值是空串」这条路径 Spring 不会兜</b>：
     * Spring 的 {@code PropertyPlaceholderHelper} 对「属性存在但值为空串」
     * 解析成<b>空串</b>（<b>不走默认值、也不报错</b>）⇒ 属性绑成 {@code ""}
     * ⇒ ⇒ 应用带着<b>空签名密钥</b>正常起来 ⇒ ⇒ 🔴 <b>token 可伪造</b>
     * （用空密钥算 HMAC，谁都能算）。
     * <p>⇒ ⇒ <b>空串必须由代码堵</b> ⇒ 即本方法。
     *
     * <h3>为什么空串 ⇒ 启动失败（而不是「启动成功但验签永远失败」）</h3>
     * <p>因为「用空密钥签发的 token」是<b>可被任何人算出来的</b>（HMAC 空密钥）
     * ⇒ ⇒ 属于<b>静默的安全降级</b> ⇒ 宁可起不来。
     *
     * <p>⚠️ Go 侧写的是<b>字面量</b>（真源公开在仓里）⇒ ⇒ 本条属
     * <b>「RAG 更严」</b>的一侧 ⇒ 登记为有意偏离（不是「RAG 抄错了 Go」）。
     */
    @PostConstruct
    void verifyAccessSecret() {
        String secret = userAuth == null ? null : userAuth.getAccessSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "like-api.user-auth.access-secret 不能为空：请设置环境变量 USER_JWT_SECRET"
                            + "（必须与签发方 user-api 完全一致）"
                            + "—— 本模块只验签、不签发，空密钥会让任何人都能伪造 JWT");
        }
    }

    public UserAuth getUserAuth() {
        return userAuth;
    }

    public void setUserAuth(UserAuth userAuth) {
        this.userAuth = userAuth;
    }

    public LikeRpc getLikeRpc() {
        return likeRpc;
    }

    public void setLikeRpc(LikeRpc likeRpc) {
        this.likeRpc = likeRpc;
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

    /**
     * 对应 Go {@code config.Config} 的 {@code LikeRpc zrpc.RpcClientConf} 段 ——
     * 但<b>只保留语义旋钮，不保留寻址字段</b>（理由见类注释）。
     */
    public static class LikeRpc {

        /**
         * <b>单次 RPC 调用的独立超时上界</b>。
         *
         * <p>Go 出处：{@code zrpc/config.go:31}
         * {@code Timeout int64 `json:",default=2000"`}
         * → {@code zrpc/client.go:70-71}
         * {@code if c.Timeout > 0 { opts = append(opts, WithTimeout(2000ms)) }}
         * → {@code zrpc/internal/clientinterceptors/timeoutinterceptor.go:25}
         * {@code ctx, cancel := context.WithTimeout(ctx, t)}。
         *
         * <p>⚠️ <b>它与 HTTP 总预算（{@code observability.http-timeout} = 3s）是两个独立旋钮</b>：
         * Go 侧 {@code context.WithTimeout(ctx, 2s)} 在「父 ctx 已有更早 deadline」时
         * <b>取更早者</b>（Go 的 {@code WithDeadline} 语义）⇒ 实际有效值 = {@code min(2s, 3s − 已耗时)}。
         *
         * <p>⚠️ <b>默认值 2000ms 来自 struct tag，不是 yaml</b>：
         * Go 的 {@code likecenter.yaml} 没写 {@code LikeRpc.Timeout} ⇒ 仍为 2000。
         * Java 侧同理<b>不写进 application.yml</b>，由这个字段默认值承担。
         */
        private Duration timeout = Duration.ofMillis(2000);

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }
    }
}