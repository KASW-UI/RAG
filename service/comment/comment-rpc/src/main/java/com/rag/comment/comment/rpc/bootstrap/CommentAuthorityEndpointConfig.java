package com.rag.comment.comment.rpc.bootstrap;

import com.rag.comment.comment.rpc.config.CommentRpcProperties;
import com.rag.comment.comment.rpc.service.CommentAuthorityLookupService;
import com.rag.comment.comment.rpc.service.CommentDomainFactService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 权威读端点装配 —— 对应 Go 端
 * {@code service/comment/rpc/cmd/fact-authority/main.go:50-95} 的 {@code run(logger)}：
 * 注册 {@code communityfact.NewAuthorityHandler("rtw.comment-rpc", token, lookup, logger)}。
 *
 * <h2>⚠️ 默认关闭：打开它等于<b>新增一个对外 HTTP 端口</b>（wire 面变更）</h2>
 * <p>Go 侧该端点跑在<b>独立进程 + 独立监听端口</b>上（{@code COMMENT_FACT_AUTHORITY_LISTEN}）。
 * 合并进 comment-rpc 的单 JVM 后要提供它，必须引入 servlet 容器 ——
 * 而 comment-rpc 是<b>纯 gRPC 进程</b>（刻意没引 {@code spring-boot-starter-web}）。
 *
 * <p>本仓库三个先例都<b>没有</b>这么做：
 * <ul>
 *   <li>{@code message-rpc} / {@code article-rpc} / {@code like-rpc} 都没有为这件事加 web starter；</li>
 *   <li>{@code like-rpc} 的同款文件 {@code LikeAuthorityEndpointConfig.java:3} 自记为
 *       「监听地址与端口 UNKNOWN，需给 like-rpc 新增 HTTP connector（wire 面变更，待拍板）」。</li>
 * </ul>
 *
 * <p>⇒ 本类默认 <b>不装配</b>（{@code comment.authority.enabled=false}）。
 * 若要启用，需要人工同时完成三件事，缺一件都会起不来或暴露面失控：
 * <ol>
 *   <li>在 {@code comment-rpc/pom.xml} 增加 {@code spring-boot-starter-web}
 *       —— 当前 pom <b>没有</b>它，这也正是本类不能无条件注册 {@code ServletRegistrationBean}
 *       的原因（{@code AuthorityHandlerFactory.register} 依赖 {@code jakarta.servlet.http.HttpServlet}）；</li>
 *   <li>显式设置监听端口（management.server.port 或 server.port），不要让它抢 9001；</li>
 *   <li>确认 {@code COMMENT_FACT_AUTHORITY_TOKEN} ≥ 32 字节并完成对外暴露面的评审。</li>
 * </ol>
 *
 * <p><b>本类只负责「装配什么」，不负责「暴露在哪」</b> —— 端口是部署契约，留在配置里。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "comment.authority", name = "enabled", havingValue = "true")
public class CommentAuthorityEndpointConfig {

    private static final Logger LOG = LoggerFactory.getLogger(CommentAuthorityEndpointConfig.class);

    /** Go {@code cmd/fact-authority/main.go:71} 的 {@code communityfact.NewAuthorityHandler("rtw.comment-rpc", …)}。 */
    private static final String PRODUCER = CommentDomainFactService.PRODUCER;

    private final CommentRpcProperties props;

    public CommentAuthorityEndpointConfig(CommentRpcProperties props) {
        this.props = props;
    }

    /**
     * 对应 Go 的 {@code communityfact.NewAuthorityHandler(producer, token, lookup, logger)}。
     *
     * <p>路径逐字沿用 {@code AuthorityHandlerFactory.AUTHORITY_PATH}
     * （{@code /internal/v1/community/facts}，与 Go 的 {@code mux.HandleFunc} 路由一致）。
     */
    @Bean
    public com.rag.common.spring.AuthorityHandlerFactory authorityHandlerFactoryMarker() {
        return new com.rag.common.spring.AuthorityHandlerFactory();
    }

    /**
     * 注册权威读 Servlet。
     *
     * @throws IllegalStateException token 长度不足 —— 与 Go
     *         {@code cmd/fact-authority/main.go:54} 的 {@code len(token) &lt; 32} 校验同向。
     */
    @Bean
    public org.springframework.boot.web.servlet.ServletRegistrationBean<
            jakarta.servlet.http.HttpServlet> communityFactAuthorityServlet(
            CommentAuthorityLookupService lookupService) {
        String token = props.getAuthority().getToken();
        if (token == null || token.length() < 32) {
            throw new IllegalStateException(
                    "community fact authority is not configured: token 必须 ≥32 字节"
                            + "（对应 Go cmd/fact-authority/main.go:54）");
        }
        LOG.warn("""
                CommentAuthorityEndpointConfig: 已启用权威读端点 {}。
                  ⚠️ 这会让 comment-rpc 从「纯 gRPC 进程」变成「同时对外提供 HTTP」——
                     请确认监听端口已显式设置、且对外暴露面已评审。
                """, com.rag.common.spring.AuthorityHandlerFactory.AUTHORITY_PATH);
        return com.rag.common.spring.AuthorityHandlerFactory.register(
                PRODUCER, token, lookupService);
    }
}