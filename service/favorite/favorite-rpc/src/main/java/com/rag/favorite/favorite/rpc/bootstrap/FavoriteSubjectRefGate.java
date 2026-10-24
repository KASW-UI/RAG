package com.rag.favorite.favorite.rpc.bootstrap;

import com.rag.favorite.favorite.rpc.config.FavoriteRpcProperties;
import com.rag.common.logger.FatalError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 🔴 启动期硬门禁 —— 对应 Go {@code service/favorite/rpc/favorite.go:31-35}：
 * <pre>
 * if c.SubjectRefV2Facts &amp;&amp; c.Mode != service.DevMode &amp;&amp; c.Mode != service.TestMode {
 *     logx.Error("SubjectRefV2Facts is refused outside dev/test")
 *     os.Exit(2)
 * }
 * </pre>
 *
 * <h2>🔴 为什么这是<b>硬</b>门禁而不是一条告警</h2>
 *
 * <p>Go 把它放在 <b>{@code logx.MustSetup}（:36）之前</b> ⇒ 进程在<b>打开数据库之前</b>就退出。
 * 这是有意的：v2 的 {@code subject_ref} 会改变<b>已冻结事实的形状</b>
 * （v2 的 {@code subject_ref} 没有 {@code tenant_id}，见
 * {@code FavoriteFactEnvelopeFactory} 的 {@code subjectRefV2}）。
 * 一个 v2 进程与 v1 进程对<b>同一个收藏</b>写出的 assert/retract 形状不同，
 * 下游权威读会因 {@code strictFavoriteJSON} 的未知字段规则而<b>永久 404</b>。
 *
 * <p>⇒ 宁可启动失败，也不能让它在生产里"跑起来再说"。
 *
 * <h2>Go 的 {@code Mode} ↔ Java 的 profile</h2>
 *
 * <p>⚠️ Go 的 {@code c.Mode} 来自 go-zero 的 {@code zrpc.RpcServerConf} 内嵌
 * {@code service.ServiceConf}，<b>取值域不在本仓库</b>
 * （docs/FileTree/favorite/rpc.md 的 UNKNOWN U3）。
 * 本实现按 Spring 惯例映射：<b>激活了 {@code dev} 或 {@code test} profile 即视为
 * 开发/测试模式</b>。
 * ⚠️ 这是映射而非逐字复刻；Go 的三值（DevMode/TestMode/其它）在本仓库无法完全取证。
 */
@Component
public class FavoriteSubjectRefGate implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(FavoriteSubjectRefGate.class);

    /** 视为「Go 的 DevMode / TestMode」的 Spring profile 名
     *     （{@code go-zero@v1.10.2/core/service/serviceconf.go:16,18}）。 */
    private static final java.util.List<String> DEV_TEST_PROFILES = java.util.List.of("dev", "test");

    private final FavoriteRpcProperties properties;
    private final Environment environment;

    public FavoriteSubjectRefGate(FavoriteRpcProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean devOrTest = DEV_TEST_PROFILES.stream()
                .anyMatch(profile -> java.util.Arrays.asList(environment.getActiveProfiles()).contains(profile));

        // 对应 Go service/favorite/rpc/favorite.go:52-54 —— 启动日志带 subject_ref_version
        // （Go 的 favoriteSubjectVersion 产出 "v1" / "v2"）
        String version = properties.isSubjectRefV2Facts() ? "v2" : "v1";
        LOG.info("favorite-rpc starting: subject_ref_version={} profiles={} fact.dispatch.enabled={} "
                        + "fact.authority.enabled={} fact.legacy-preflight.enabled={}",
                version, java.util.Arrays.toString(environment.getActiveProfiles()),
                properties.getFact().getDispatch().isEnabled(),
                properties.getFact().getAuthority().isEnabled(),
                properties.getFact().getLegacyPreflight().isEnabled());

        // Go: service/favorite/rpc/favorite.go:31-35 —— 门禁本体
        if (properties.isSubjectRefV2Facts() && !devOrTest) {
            // ⚠️ 抛 FatalError 而不是 System.exit(2)：
            //    FatalError 的契约（common/logger/FatalError.java）就是
            //    「打出 fatal 日志 + 抛出让异常冒泡出 main，JVM 非零退出」，
            //    等价于 Go 的 os.Exit(2)，同时不绕过 Spring 的 graceful shutdown。
            //    Spring Boot 的 main 未捕获该异常 ⇒ 容器启动失败 ⇒ 进程退出。
            throw new FatalError(
                    "SubjectRefV2Facts is refused outside dev/test "
                            + "(favorite.subject-ref-v2-facts=true, profiles="
                            + java.util.Arrays.toString(environment.getActiveProfiles()) + ")",
                    null);
        }
    }
}