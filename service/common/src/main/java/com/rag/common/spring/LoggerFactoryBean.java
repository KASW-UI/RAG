package com.rag.common.spring;

import com.rag.common.logger.LogOption;
import com.rag.common.logger.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * <p><b>Go 出处</b>：{@code service/common/logger/logger.go:63-70}
 * （{@code func Init(svcName string)} ⇒ 服务名的<b>唯一</b>来源，且<b>空名 panic</b>）
 * ＋ {@code service/common/logger/logger.go:98}（{@code func (l *Logger) logBusinessErr(...)}`）。
 * Spring 入口：将服务名从 {@code spring.application.name} 注入，提供实例方法
 * {@link #logInfo} / {@link #logBusinessErr} / {@link #logFatal}。
 *
 * <p><b>本类现在只是 {@link Logger} 的薄适配器（2026-10-21 重构）</b>。
 *
 * <p><b>为什么重构</b>：此前 {@code service/common} 里存在<b>两份逐条同构</b>的日志实现 ——
 * {@code com.rag.common.logger.Logger}（静态门面 + 包私有 {@code doLogXxx}）与本类
 * （公开实例方法）。经 AST 级比对，两者的 {@code doLogBusinessErr} / {@code doLogInfo} /
 * {@code doLogFatal} 方法体<b>只差三处</b>：方法可见性、方法名自调用、以及本类没有的
 * serviceName 判空守卫（Bean 的 serviceName 由构造器保证非空）。
 * 除此之外，本类还各自维护了一份
 * {@code FILTER_PREFIXES} / {@code IMAGE_BASE64_TRUNCATE} / {@code getFuncName} /
 * {@code getCallChain} / {@code isFiltered} / {@code extractTraceId} / {@code fileBaseName}。
 *
 * <p>两份实现并存的真实代价不是「重复」本身，而是<b>改一处忘一处</b>：
 * {@code call_chain} 过滤修过一次（AGENTS.md §6.2 已修项），
 * 如果当时只改了一份，静态入口与 Bean 入口的 {@code call_chain} 就会静默不一致。
 * 现在实现只剩一份，不存在这种可能。
 *
 * <p><b>委托顺带修正了 Bean 路径 call_chain 的一个 off-by-one</b>：
 * 委托前 Bean 路径的栈是 {@code getCallChain → Bean.logInfo → 业务调用方}，
 * 而 {@code getCallChain(3)} 会跳过 3 帧 ⇒ 链首落在<b>业务调用方的上一层</b>，
 * 少一帧。委托后栈变成 {@code getCallChain → Logger.doLogInfo → Bean.logInfo → 业务调用方}，
 * 跳 3 帧正好落在业务调用方，与静态入口一致。
 *
 * <p><b>两套 public API 全部保留</b>，调用方无需改动：
 * <pre>{@code
 *   // 静态入口（继续可用）
 *   Logger.logBusinessErr(ctx, 40101, err, WithUserID.of("u1"));
 *
 *   // Spring Bean 入口（user-rpc 的 UserDomainService 等在用）
 *   @Autowired LoggerFactoryBean logger;
 *   logger.logBusinessErr(ctx, 40101, err, WithUserID.of("u1"));
 * }</pre>
 *
 * <p><b>serviceName 仍是各自独立的</b>（决策 3，不因本次重构改变）：
 * Bean 的 serviceName 由 Spring 构造注入；{@code Logger.GLOBAL.serviceName} 由业务通过
 * {@code Logger.init(...)} 设置。两者互不干扰 —— 委托只复用<b>算法</b>，不共享 serviceName 状态。
 *
 * <p><b>字段语义</b>与 Go 版 {@code logger.LogBusinessErr} 1:1 对齐：
 * service / event_id / trace_id / error_code / file_line / call_path / call_chain / error_reason；
 * 可选 user_id / article_id（空字符串字段消失而非空串）。
 */
@Component
public class LoggerFactoryBean {

    /**
     * Spring 注入的 serviceName；与 {@code Logger.GLOBAL.serviceName} 完全独立。
     *
     * <p>同时用于 {@link #getServiceName()} 与构造委托实例。
     */
    private final String serviceName;

    /**
     * 算法委托实例。serviceName 在构造时固定，与静态 {@code Logger.GLOBAL} 无关。
     *
     * <p>{@code Logger} 的 {@code doLogBusinessErr} / {@code doLogInfo} / {@code doLogFatal}
     * 原为包私有，2026-10-21 起改为 public 以支持跨包委托。
     */
    private final Logger delegate;

    /**
     * Spring 构造注入 serviceName；不允许为空（与 {@code Logger.init} 行为对齐）。
     *
     * @param serviceName 从 {@code spring.application.name} 注入的服务名；为空时抛 IllegalStateException
     */
    public LoggerFactoryBean(
            @Value("${spring.application.name:unknown-service}") String serviceName
    ) {
        if (serviceName == null || serviceName.isEmpty()) {
            throw new IllegalStateException("spring.application.name must not be empty");
        }
        this.serviceName = serviceName;
        this.delegate = new Logger(serviceName);
    }

    public String getServiceName() {
        return serviceName;
    }

    /**
     * 业务错误日志，字段集合与 {@code Logger.logBusinessErr} 一致
     * （service / event_id / trace_id / error_code / file_line / call_path / call_chain /
     * error_reason + 可选 user_id / article_id）。
     */
    public void logBusinessErr(io.opentelemetry.context.Context otelCtx,
                               int code,
                               Throwable err,
                               LogOption... opts) {
        delegate.doLogBusinessErr(otelCtx, code, err, opts);
    }

    /**
     * 信息日志。{@link FatalError} 与 {@code Logger.logInfo} 行为完全一致。
     */
    public void logInfo(io.opentelemetry.context.Context otelCtx,
                        String msg,
                        LogOption... opts) {
        delegate.doLogInfo(otelCtx, msg, opts);
    }

    /**
     * 致命日志：打出 {@code fatal_error} 后抛 {@link com.rag.common.logger.FatalError}，
     * <b>不</b>调 {@code System.exit}（见 AGENTS.md §3.4.1 A-1 已签字偏离）。
     */
    public void logFatal(io.opentelemetry.context.Context otelCtx,
                         Throwable err,
                         LogOption... opts) {
        delegate.doLogFatal(otelCtx, err, opts);
    }
}