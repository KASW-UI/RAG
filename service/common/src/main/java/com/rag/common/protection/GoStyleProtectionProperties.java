package com.rag.common.protection;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Go「请求保护层」四道中间件的配置聚合点。
 *
 * <p><b>为什么 Go yaml 里一个字都没写，这里却要显式写默认值</b>：
 * Go 的 {@code service/message/api/etc/messagecenter.yaml} <b>完全没有</b>提到
 * {@code MaxBytes} / {@code MaxConns} / {@code CpuThreshold} / {@code Breaker}，
 * 但它们<b>确实是开启的</b> —— 依据是 go-zero v1.10.2 的两处默认值声明：
 * <ul>
 *   <li>{@code rest/config.go:52-56}：{@code MaxConns int json:",default=10000"}、
 *       {@code MaxBytes int64 json:",default=1048576"}、
 *       {@code CpuThreshold int64 json:",default=900,range=[0:1000)"}</li>
 *   <li>{@code rest/config.go:12-22} {@code MiddlewaresConf}：九个开关
 *       <b>全部</b> {@code json:",default=true"}</li>
 * </ul>
 * ⇒ <b>逐字段照 yaml 对是永远看不见这几层的</b>，它们只存在于 go-zero 的 tag 里。
 * （这正是 §3.1「不要拿另一个域的实测结论当对照基准」的另一种形态：yaml 不是行为的全集。）
 *
 * <p><b>四层各自的 Go 出处与可观察行为</b>：
 * <table border="1">
 *   <caption>保护层对照</caption>
 *   <tr><th>层</th><th>Go 实现</th><th>拒绝时</th><th>本类字段</th></tr>
 *   <tr>
 *     <td>并发连接上限</td>
 *     <td>{@code rest/handler/maxconnshandler.go:12-36}</td>
 *     <td><b>503 空 body</b></td>
 *     <td>{@link #maxConns}</td>
 *   </tr>
 *   <tr>
 *     <td>请求体大小</td>
 *     <td>{@code rest/handler/maxbyteshandler.go:10-25}</td>
 *     <td><b>413 空 body</b></td>
 *     <td>{@link #maxBytes}</td>
 *   </tr>
 *   <tr>
 *     <td>CPU 自适应降载</td>
 *     <td>{@code rest/handler/sheddinghandler.go:20-52} + {@code core/load/adaptiveshedder.go}</td>
 *     <td><b>503 空 body</b></td>
 *     <td>{@link CpuShedding}</td>
 *   </tr>
 *   <tr>
 *     <td>熔断</td>
 *     <td>{@code rest/handler/breakerhandler.go:19-44} + {@code core/breaker/googlebreaker.go:52-79}</td>
 *     <td><b>503 空 body</b></td>
 *     <td>{@link #breaker}</td>
 *   </tr>
 * </table>
 *
 * <p><b>四层的 body 都是空的</b>，且<b>不走 envelope</b>：Go 侧这四处都是裸
 * {@code w.WriteHeader(code)}，没有 {@code httpx.Error}，因此既没有
 * {@code {code:...}} 也没有 {@code Content-Type: application/json}。
 * 这与各业务模块「所有业务错误恒 HTTP 200 + envelope」的约定<b>刻意不同</b> ——
 * 保护层在到达业务逻辑之前就拒绝了，没有 envelope 可包。
 *
 * <h2>为什么这套实现住在 {@code service/common}</h2>
 *
 * <p>Go 侧这四层是 <b>go-zero {@code rest.Server} 的内建行为</b>，不是某个业务模块的代码 ——
 * 任何 {@code rest.MustNewServer} 起的进程都自带。因此 RAG 侧也只有一处实现才对：
 * 复制到每个 api 模块，最终必然出现「message 有 MaxBytes、like 没有」这类静默不一致。
 *
 * <h2>为什么四个 Filter 带 {@code @ConditionalOnWebApplication(SERVLET)}</h2>
 *
 * <p><b>诚实边界：这四个条件在当前仓库里不是「补现行 bug」，而是「防止下一个模块踩坑」。</b>
 * 实测（{@code mvn dependency:list}）：{@code service/message/message-rpc} 的依赖树里
 * <b>没有</b> {@code jakarta.servlet-api}、也没有 {@code tomcat-embed-core}
 * （只有 {@code tomcat-embed-el}，它不含 servlet API）；
 * 而四个 rpc 进程（{@code message-rpc} / {@code like-rpc} / {@code admin-rpc} / {@code user-rpc}）
 * 又<b>已经刻意不扫 {@code com.rag.common}</b> —— 原因正是同包下
 * {@code com.rag.common.spring.HttpMiddlewareFilter} 是 servlet Filter，
 * 全包扫描会把 web 类拖进纯 gRPC 进程。所以今天两者没有相交。
 * <p>但「安全」此刻依赖的是「每个 rpc 进程都记得不扫 {@code com.rag.common}」这条<b>口头约定</b>，
 * 而 {@code com.rag.common} 恰恰是唯一必须被所有进程共享的包。把 HTTP 保护层放进共享包时，
 * 用一个条件注解把「这是 HTTP 层」这件事编码进去，比在每个 rpc 进程的注释里重复警告更可靠
 * —— <b>约定会随新人流失，条件不会</b>。
 *
 * <h2>配置前缀为什么是 {@code rag.protection}</h2>
 *
 * <p>本类原先是 {@code message-api.protection}，因为它只服务 message-api。
 * 下沉到共享包后带业务域前缀是错的：一个「共用的限流器」不该归某一个业务域所有。
 * ⇒ 前缀改为 {@code rag.protection}。
 * ⚠️ <b>这是本次下沉唯一一处「部署可见」的变更</b>：各服务 yaml 里
 * {@code message-api.protection.*} 必须改写成 {@code rag.protection.*}。
 * 未配置时全部字段回落到下方默认值，而<b>这些默认值就是 go-zero 的 tag 默认值</b>，
 * 所以「漏改」不会变成「保护层静默关闭」，只会变成「阈值回到 Go 默认」——
 * 这是刻意选的失效方向（fail-safe，而非 fail-open）。
 */
@ConfigurationProperties(prefix = "rag.protection")
public class GoStyleProtectionProperties {

    /**
     * Go {@code rest/config.go:52}：{@code MaxConns int json:",default=10000"}。
     * {@code <= 0} 时 Go 的 {@code MaxConnsHandler} 直接返回 no-op（不限流）。
     */
    private int maxConns = 10000;

    /**
     * Go {@code rest/config.go:53}：{@code MaxBytes int64 json:",default=1048576"}（1 MiB）。
     * {@code <= 0} 时 {@code MaxBytesHandler} 返回 no-op。
     */
    private long maxBytes = 1048576L;

    @NestedConfigurationProperty
    private CpuShedding cpuShedding = new CpuShedding();

    @NestedConfigurationProperty
    private Breaker breaker = new Breaker();

    public int getMaxConns() {
        return maxConns;
    }

    public void setMaxConns(int maxConns) {
        this.maxConns = maxConns;
    }

    public long getMaxBytes() {
        return maxBytes;
    }

    public void setMaxBytes(long maxBytes) {
        this.maxBytes = maxBytes;
    }

    public CpuShedding getCpuShedding() {
        return cpuShedding;
    }

    public void setCpuShedding(CpuShedding cpuShedding) {
        this.cpuShedding = cpuShedding;
    }

    public Breaker getBreaker() {
        return breaker;
    }

    public void setBreaker(Breaker breaker) {
        this.breaker = breaker;
    }

    /**
     * 对应 Go {@code rest/config.go:56} 的 {@code CpuThreshold}（千分比）+ {@code core/load/adaptiveshedder.go:22}。
     *
     * <p><b>取值范围 {@code [0:1000)}</b> 与 Go 的 tag 一致（{@code range=[0:1000)}）；
     * RAG 侧不做 tag 级校验，越界时 {@link #maxCpuPermille()} 会夹到 {@code [0,1000)}，
     * 因为「一个 1200 的阈值」在 Go 侧是启动期配置错误、在 Java 侧若不夹会变成永不降载的静默失效。
     */
    public static class CpuShedding {
        /** {@code rest/config.go:22} {@code Middlewares.Shedding bool json:",default=true"}。 */
        private boolean enabled = true;

        /** {@code rest/config.go:56} {@code CpuThreshold int64 json:",default=900,range=[0:1000)"}。 */
        private long cpuThreshold = 900L;

        /**
         * CPU 口径。Go 侧 {@code core/load/adaptiveshedder.go:41-43} 的
         * {@code systemOverloadChecker} 读的是<b>整机</b> CPU（Linux 下 {@code /proc/stat}）。
         * <ul>
         *   <li>{@code SYSTEM}（默认，= Go）：{@code getSystemCpuLoad()}，整机口径。
         *       在容器里若宿主很忙但本进程很闲，RAG 与 Go 会<b>同样</b>降载 —— 这是刻意对齐。</li>
         *   <li>{@code PROCESS}：{@code getProcessCpuLoad()}，只看本 JVM。
         *       偏离 Go，但适合「多副本各自限流」的部署口径。</li>
         * </ul>
         */
        private CpuSource cpuSource = CpuSource.SYSTEM;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getCpuThreshold() {
            return cpuThreshold;
        }

        public void setCpuThreshold(long cpuThreshold) {
            this.cpuThreshold = cpuThreshold;
        }

        public CpuSource getCpuSource() {
            return cpuSource;
        }

        public void setCpuSource(CpuSource cpuSource) {
            this.cpuSource = cpuSource;
        }
    }

    /** {@code rest/config.go:15} {@code Middlewares.Breaker bool json:",default=true"}。 */
    public static class Breaker {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /** CPU 采样口径，见 {@link CpuShedding#getCpuSource()}。 */
    public enum CpuSource {
        /** 整机 CPU，等价 Go {@code /proc/stat} 口径。 */
        SYSTEM,
        /** 仅本 JVM 进程 CPU。 */
        PROCESS
    }

    /**
     * 夹到 Go 的 tag 声明域 {@code [0,1000)}。
     *
     * <p>Go 的 {@code range=[0:1000)} 会在<b>配置加载期</b>报错并拒绝启动；
     * Java 侧没有等价的 tag 校验，若照抄一个越界值，最可能的结果不是启动失败，
     * 而是「阈值永远达不到 ⇒ 永不降载」的<b>静默失效</b>（§3.3 同款形态）。
     */
    public long maxCpuPermille() {
        long t = cpuShedding.getCpuThreshold();
        if (t < 0) {
            return 0;
        }
        return Math.min(t, 999);
    }
}