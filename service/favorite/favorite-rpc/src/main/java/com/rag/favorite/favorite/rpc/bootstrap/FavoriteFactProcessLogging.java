package com.rag.favorite.favorite.rpc.bootstrap;

import com.rag.common.communityfact.ProcessLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 🔴 四个 Go 进程的日志身份隔离 —— 对应 Go 侧四个 {@code main} 各自的
 * {@code slog.New(slog.NewJSONHandler(os.Stderr, nil)).With("service", "…")}。
 *
 * <h2>为什么要单点处理</h2>
 *
 * <p>Go 侧四个进程各自有一条独立的 slog 身份：
 * <pre>
 * P1 gRPC            etc/favorite.yaml  Log.ServiceName: favorite-rpc
 * P2 fact-dispatch   cmd/fact-dispatch/main.go:25   service = rtw-favorite-fact-dispatch
 * P3 fact-authority  cmd/fact-authority/main.go:22  service = rtw-favorite-fact-authority
 * P4 legacy-preflight cmd/fact-legacy-preflight/main.go:23  service = rtw-favorite-legacy-preflight
 * </pre>
 * RAG 侧合并成单 JVM 后，如果只留一个 SLF4J 树，
 * 就<b>无法区分</b>「这条 dispatch 日志来自哪个进程」——
 * 而 fact-dispatch 的 blocked / retry 日志恰恰需要单独归因
 * （{@code favorite.delivery.blocked} 这类结构化事件在运维上是独立告警项）。
 *
 * <p>{@link ProcessLogger} 的 logger 名由 {@code service.environment.component} 拼，
 * 同 JVM 可建多份独立 appender（见其类注释「logger 名唯一化」）。
 *
 * <h2>⚠️ 只在对应功能<b>启用</b>时创建</h2>
 * <p>{@code ProcessMetadata.validate()} 要求 {@code version} 是 40 位小写 hex
 * （Git commit SHA）。拿不到时用「全 0」占位并降级为普通 SLF4J 名称，
 * 而不是让 {@code newProcessLogger} 抛异常拖垮整个容器。
 */
@Component
public class FavoriteFactProcessLogging {

    private static final Logger LOG = LoggerFactory.getLogger(FavoriteFactProcessLogging.class);

    /** Go 侧四个进程的 service 身份（逐字照抄 cmd/* 里的字面量）。 */
    public static final String SERVICE_DISPATCH = "rtw-favorite-fact-dispatch";
    public static final String SERVICE_AUTHORITY = "rtw-favorite-fact-authority";
    public static final String SERVICE_PREFLIGHT = "rtw-favorite-legacy-preflight";
    public static final String SERVICE_RPC = "favorite-rpc";

    /**
     * {@code ProcessMetadata.validate()} 要求 40 位小写 hex；
     * 拿不到 commit SHA 时用全 0 占位（validate 只做格式检查，不校验是否真实存在）。
     */
    private static final String UNKNOWN_VERSION = "0000000000000000000000000000000000000000";

    private final String instanceId;
    private final String environment;
    private final String version;

    public FavoriteFactProcessLogging(
            @Value("${spring.application.name:favorite-rpc}") String service,
            @Value("${HOSTNAME:unknown}") String hostname,
            org.springframework.core.env.Environment environment) {
        this.instanceId = hostname;
        this.environment = resolveEnvironment(environment);
        this.version = resolveVersion();
        LOG.info("FavoriteFactProcessLogging: 4 个进程身份将使用 version={} environment={} instance_id={} service={}",
                version, environment, instanceId, service);
    }

    /**
     * 取（或按需创建）某个进程角色的 {@link ProcessLogger}。
     *
     * <p>⚠️ {@link ProcessLogger#newProcessLogger} 需要一个 {@link java.io.OutputStream}；
     * 本实现传 {@code System.out}，实际 appender 由 logback 配置接管
     * （与 like 域的同名职责同款做法）。版本缺失时降级为普通 SLF4J logger，
     * 保证「拿不到 commit SHA」不会让 favorite-rpc 起不来。
     */
    public Logger loggerFor(String processService, String component) {
        try {
            ProcessLogger.ProcessMetadata meta = new ProcessLogger.ProcessMetadata(
                    processService, environment, version, instanceId, component);
            return ProcessLogger.newProcessLogger(System.out, meta);
        } catch (RuntimeException e) {
            LOG.warn("ProcessLogger 不可用（version={} environment={}），降级为普通 SLF4J logger: {}",
                    version, environment, e.getMessage());
            return LoggerFactory.getLogger("communityfact.process." + processService + "." + component);
        }
    }

    /**
     * 环境名：Go 侧 {@code ProcessMetadata.environment} 取值域是
     * {@code local / test / staging / production}。
     * 优先从 Spring profile 推断（与 {@link FavoriteSubjectRefGate} 的 dev/test 映射一致）。
     */
    private static String resolveEnvironment(org.springframework.core.env.Environment environment) {
        for (String profile : environment.getActiveProfiles()) {
            if ("prod".equals(profile) || "production".equals(profile)) {
                return "production";
            }
            if ("staging".equals(profile)) {
                return "staging";
            }
            if ("test".equals(profile)) {
                return "test";
            }
        }
        return "local";
    }

    /**
     * Git commit SHA：优先读 {@code GIT_COMMIT} 环境变量，
     * 拿不到用全 0 占位（validate 只校验格式）。
     */
    private static String resolveVersion() {
        String fromEnv = System.getenv("GIT_COMMIT");
        if (fromEnv != null && fromEnv.matches("[0-9a-f]{40}")) {
            return fromEnv;
        }
        return UNKNOWN_VERSION;
    }
}