package com.rag.article.article.rpc.bootstrap;

import com.rag.article.article.rpc.service.ArticleAtomicService;
import com.rag.common.spring.SnowflakeBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 雪花 ID 生成器就绪确认 —— 对应 Go 端 {@code internal/svc/service_context.go:58}
 * 的 {@code snowflake.Init()}。
 *
 * <h2>⚠️ 与 {@code SnowflakeBean} 的分工（不要重复初始化）</h2>
 * {@code com.rag.common.spring.SnowflakeBean} 在<b>容器 refresh 期</b>解析 workerId 并交给
 * {@code Snowflake} 的唯一静态状态（见该类 javadoc：它<b>不持有</b>生成状态）。
 * <br>Go 侧 {@code snowflake.Init()} 是<b>无参</b>的，workerId 来自进程内默认值或环境变量。
 * <br>⇒ Java 侧<b>不需要</b>再调一次 {@code Init()}：那会走 {@code setWorkerId} 的
 * first-wins 分支，把环境变量里解析好的 workerId 覆盖成默认值。
 *
 * <p>本类因此只做<b>就绪确认与可见性</b>：确认 workerId 已解析成功，
 * 并把它打进启动日志。
 * <p><b>⚠️ 为什么需要确认</b>：若 {@code SNOWFLAKE_NODE_ID} 配置非法（例如 &gt; 1023），
 * {@code setWorkerId} 会拒绝或抛错。Go 侧 {@code snowflake.Init()} 在这种情况下
 * 同样会失败（{@code MustSetup} 之后直接 panic）。本类把这种失败变成<b>启动即可见</b>，
 * 而不是等到第一篇文章创建时才发现 ID 重复。
 *
 * <p><b>不阻止启动</b>：与桶 bootstrap 同理，ID 只在写入路径用到；
 * 且 {@code Snowflake} 自身在 {@code nextId()} 时还有 workerId 兜底。
 * 这里只<b>大声告警</b>。若要 fail-fast，把 {@code LOG.error} 换成抛异常即可。
 */
@Component
public class SnowflakeBootstrap {

    private static final Logger LOG = LoggerFactory.getLogger(SnowflakeBootstrap.class);

    private final SnowflakeBean snowflakeBean;
    private final ArticleAtomicService atomicService;

    public SnowflakeBootstrap(SnowflakeBean snowflakeBean, ArticleAtomicService atomicService) {
        this.snowflakeBean = snowflakeBean;
        // 仅用于确认事务 Bean 已就绪（见下方说明），不参与任何逻辑
        this.atomicService = atomicService;
    }

    /**
     * 启动后确认 ID 生成器就绪。
     *
     * <p>⚠️ <b>顺带验证事务 Bean 的代理已生成</b>：
     * {@link ArticleAtomicService} 上的 {@code @Transactional} 依赖 AOP 代理。
     * 若某个装配问题让代理没生成，本类<b>仍然能构造</b>（Spring 会注入目标对象），
     * 但运行期事务会静默失效。所以这里显式断言它是<b>代理对象</b>，
     * 把 AGENTS.md §3.3 那类"注解在 ≠ 按预期生效"的问题在启动时就暴露。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void verifySnowflakeReady() {
        long workerId = snowflakeBean.getWorkerId();
        LOG.info("SnowflakeBootstrap: workerId={} (article ids and sync event ids use it)", workerId);

        boolean proxied = org.springframework.aop.support.AopUtils.isAopProxy(atomicService);
        if (proxied) {
            LOG.info("SnowflakeBootstrap: ArticleAtomicService is an AOP proxy ⇒ @Transactional is active");
        } else {
            // ⚠️ 没有代理 ⇒ 三处多步事务（retract+update / retract+delete / 审核入队 / 发布落地）
            //    全部退化为 auto-commit，异常不回滚，且不报任何错。
            LOG.error("""
                    SnowflakeBootstrap: ArticleAtomicService is NOT an AOP proxy ⇒ @Transactional is INACTIVE.
                      The following operations would run without a transaction:
                        - update: 已发布文章降级（retract + 域事件 + 同步事件 + 更新 article）
                        - delete: retract + 同步事件 + 软删
                        - review consumer: 行锁 + 幂等复查 + 写同步 outbox
                        - sync result: revision + 指针 + 域事件 + 状态推进
                      典型原因：把事务方法挪到了被 self-invocation 调用的内部类里。
                    """);
        }
    }
}