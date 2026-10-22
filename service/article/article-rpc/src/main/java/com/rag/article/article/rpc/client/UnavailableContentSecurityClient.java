package com.rag.article.article.rpc.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 内容安全审计的<b>默认实现</b> —— 在 {@code security-rpc} 尚未落地到本仓时，
 * <b>让审核链路 fail-loud 而不是 fail-open</b>。
 *
 * <h2>为什么必须有这个类（而不是干脆不注册 Bean）</h2>
 * {@link ContentSecurityPort} 是审核消费者的<b>唯一</b> security 出口。
 * 若 security 模块未接入且没有兜底实现：
 * <ul>
 *   <li><b>方案 A：没有 Bean</b> ⇒ {@code ArticleReviewConsumer} 构造失败 ⇒
 *       整个 Spring 上下文起不来（{@code UnsatisfiedDependencyException}）。
 *       这让「article-rpc 不依赖 security」变成「article-rpc 起不来」——
 *       部署耦合被放大，且错误信息完全指不到真正的原因。</li>
 *   <li><b>方案 B（本类）</b> ⇒ 上下文正常启动，审核消费者在遇到待审文章时
 *       <b>明确抛出</b>「content security client not initialized」，
 *       Kafka <b>不确认</b>、消息重投。文章停在 REVIEWING，不会被误判为通过。</li>
 * </ul>
 *
 * <h2>🔴 这个类体现的是一条必须守住的语义：fail-loud，绝不 fail-open</h2>
 * Go 侧 {@code auditArticle} 的第一行就是：
 * <pre>
 * if l.svcCtx.SecurityRpc == nil { return fmt.Errorf("content security client not initialized") }
 * </pre>
 * ⇒ <b>Go 自己就要求 security 不可用时审核必须失败</b>，
 * 而不是"跳过审核直接放行"。本类逐字复刻这条：
 * <b>任何一次调用都抛出同样的错误</b>，让审核文章卡在 REVIEWING 而不是被放行。
 *
 * <p>⚠️ <b>严禁改成"返回 isAd=false 就算通过"</b>：
 * 那会让所有文章绕过广告检测直接进入发布流程，
 * 并且在 {@code article_status_total} 上完全看不出来（因为审核"成功"了）。
 * 这是本组件里后果最严重的可能改动。
 *
 * <h2>接入真实 security-rpc 后的移除方式</h2>
 * {@code security-proto} 模块落地后，新增一个 {@code @Component} 实现
 * {@link ContentSecurityPort}；本类<b>自动退场</b>，审核消费者的代码<b>一行都不用改</b>。
 *
 * <h2>⚠️ 为什么注册在自动配置里而不是直接 {@code @Component}</h2>
 * {@code @ConditionalOnMissingBean} <b>只在自动配置类上才有确定语义</b>。
 * 它判断的是"处理这个配置类<b>之后</b>容器里有没有该类型的 Bean"，
 * 而自动配置类是在用户 Bean（{@code @Component}）之后求值的。
 * 若把它写在被组件扫描的普通类上，判断时机与扫描顺序不确定 ⇒
 * 条件可能误判为"存在 Bean"而让本类<b>不被注册</b>，
 * 于是 {@code ArticleReviewConsumer} 拿到 {@code null} 端口 ⇒ NPE。
 * 这与 AGENTS.md §3.3「不得凭注解名称断定机制已启用」是同款陷阱：
 * 注解在不等于按预期生效。
 * ⇒ 本类本体是<b>普通类</b>，由 {@link ContentSecurityAutoConfiguration} 条件注册。
 */
public class UnavailableContentSecurityClient implements ContentSecurityPort {

    private static final Logger LOG = LoggerFactory.getLogger(UnavailableContentSecurityClient.class);

    /**
     * 对应 Go {@code article_consumer.go:154-156} 的
     * {@code fmt.Errorf("content security client not initialized")} —— <b>逐字一致</b>。
     */
    private static final String NOT_INITIALIZED = "content security client not initialized";

    @Override
    public TextAuditResult sanitizeText(String text) {
        LOG.error("{} — article review cannot proceed; article stays REVIEWING and the kafka "
                + "message will be redelivered", NOT_INITIALIZED);
        // 抛出而非返回：见类级说明的 fail-loud 论证
        throw new SecurityAuditException(NOT_INITIALIZED);
    }

    @Override
    public ImageAuditResult detectImageAd(String imageDataUri) {
        LOG.error("{} — article review cannot proceed; article stays REVIEWING and the kafka "
                + "message will be redelivered", NOT_INITIALIZED);
        throw new SecurityAuditException(NOT_INITIALIZED);
    }
}