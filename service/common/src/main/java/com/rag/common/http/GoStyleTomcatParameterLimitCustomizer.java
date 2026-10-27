package com.rag.common.http;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.tomcat.ConfigurableTomcatWebServerFactory;
import org.springframework.boot.tomcat.TomcatConnectorCustomizer;
import org.springframework.boot.web.server.WebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 🔴 把 Tomcat 的<b>参数个数上限</b>从「模块 yaml 里各自配的值」抬到 <b>10001</b>，
 * 让 {@link GoStyleFormValues} / {@link GoStyleFormValueLimitInterceptor} 有机会<b>先按 Go 的语义</b>判定。
 *
 * <h2>为什么需要它（Go 出处，逐行）</h2>
 * <pre>
 * go-zero@v1.10.2/rest/httpx/util.go:15
 *   maxFormParamCount = 2048              // ← Go 的 form 值上限（数【非空值】）
 *
 * Go 1.25 net/url/url.go:1001-1022（parseQuery）
 *   defaultMaxParams = 10000
 *   if !urlParamsWithinMax(strings.Count(query, "&amp;") + 1) {
 *       return errors.New("number of URL query parameters exceeded limit")
 *   }
 *   // ⇒ Go 的 query 段数上限 = 10000（数【段】，不是 distinct key）
 * </pre>
 *
 * <h2>Tomcat 侧的事实</h2>
 * <pre>
 * tomcat-embed-core-11.0.24
 *   catalina/connector/Connector.java:251        maxParameterCount 默认 1000
 *   catalina/connector/Connector.java:538        setMaxParameterCount(int)
 *   catalina/connector/Request.java:148/480     每请求从 connector 取值
 *   tomcat/util/http/Parameters.java:79          javadoc：「or -1 for no limit」
 *   tomcat/util/http/Parameters.java:247         if (limit &gt; -1 &amp;&amp; parameterCount &gt;= limit) → 拒
 *   tomcat/util/http/Parameters.java             processParameters → addParameter(name, value)
 *                                                ⇒ 【每个参数都计数，【含空值】】（与 Go 不同）
 * </pre>
 *
 * <h2>🔴 为什么必须是 10001（而不是 2048，也不是 -1）</h2>
 * <ul>
 *   <li><b>不是 2048</b>（现状）：Tomcat 数「参数总数」，Go 数「非空值」⇒
 *       请求带空值参数时（{@code ?a&a&a…}）<b>总数 ≥ 2048 就被 Tomcat 拒</b>
 *       ⇒ 而 Go 只数非空值 ⇒ Go 放行 ⇒ <b>两侧行为不一致</b>。
 *       且 Tomcat 拒的是 <b>400 + HTML</b>，Go 拒的是 <b>400 + text/plain 原文</b>。</li>
 *   <li><b>不是 -1（不限）</b>：那会让 Tomcat <b>无界</b>解析 query 参数
 *       ⇒ 资源暴露面比 Go 大得多（Go 自己有 10000 段上限）⇒ <b>不该为了对齐而放宽 DoS 闸门</b>。</li>
 *   <li><b>= 10001</b>：= Go 的段数上限 10000 <b>+1</b> ⇒
 *       ① <b>≤ 10001 个参数</b> ⇒ Tomcat 放行 ⇒ 由 {@link GoStyleFormValues} / {@link GoStyleFormValueLimitInterceptor} 按 Go 的语义判
 *       （非空值 &gt; 2048 ⇒ 400 + Go 原文 body）<br>
 *       ② <b>10001 段</b> ⇒ Go 的 net/url 会拒（&gt; 10000）⇒ 我们的 filter 也拒 ⇒
 *       <b>两边都拿到 Go 的原文 body</b>（比现状更接近 Go）<br>
 *       ③ <b>&gt; 10001 个参数</b> ⇒ Tomcat 拒（400 + HTML）⇒ Go 也拒（400）⇒ <b>状态码一致</b>
 *       ⇒ 与现状相比，HTML 错误页的窗口从「[2049, ∞)」<b>缩到</b>「[10002, ∞)」⇒ <b>严格变小</b>。</li>
 * </ul>
 *
 * <h2>🔴 为什么 {@code @Order} 必须是「大于 Boot 的 0」</h2>
 * <p>Boot 4.0.8 的 {@code org.springframework.boot.tomcat.autoconfigure.TomcatWebServerFactoryCustomizer}
 * 会把 <b>模块 yaml 里的 {@code server.tomcat.max-parameter-count}</b> 设到 connector 上，
 * 而它的 {@code getOrder()} <b>返回 0</b>（实测：{@code javap -c}）。
 * <br>⇒ {@code WebServerFactoryCustomizer} 之间的应用顺序 = {@code @Order} 升序
 * ⇒ <b>本类必须排在它后面</b>，否则会被 Boot 用 yaml 的值盖回去 ⇒
 * ⇒ 本类取 {@link Ordered#LOWEST_PRECEDENCE}（{@code Integer.MAX_VALUE}）⇒ 必然最后 ⇒ <b>必然生效</b>。
 *
 * <h2>⚠️ yaml 里的 {@code server.tomcat.max-parameter-count} 与<b>运行期值</b>的关系</h2>
 * <p>10 个 api 模块的 {@code application.yml} 里都写着 {@code max-parameter-count: 2048}
 * （那是 hot 域 2026-10-25 第十三轮为了对齐 Go 统一加的）
 * ⇒ <b>yaml 层全仓一致</b>（守门 {@code tools/difftest/check-tomcat-limits.py} 的
 * {@code EXPECTED_MAX_PARAM_COUNT = 2048} 校的就是这一层）。
 * <br>⇒ 但<b>运行期值</b>由本类决定：{@code mode ≠ OFF} ⇒ 抬到 {@link #MAX_PARAMETER_COUNT}（10001）；
 * {@code mode == OFF} ⇒ <b>不动</b> ⇒ 运行期 = yaml 的 2048。
 *
 * <h3>🔴 因此「OFF 域的运行期上限 = 2048」是<b>真实存在的分叉</b>（2026-10-27 订正）</h3>
 * <p>本类旧版 javadoc 写「10 份 yaml 保持一致 ＋ 共享层统一覆盖 ⇒ <b>无分叉、favorite 零改动</b>」
 * ⇒ ⇒ <b>这句是错的</b>（它把「yaml 层一致」当成了「运行期一致」）：
 * <ul>
 *   <li>实测：<b>9 个声明了 {@code mode} 的域</b>运行期 = <b>10001</b>；</li>
 *   <li>实测：<b>favorite</b>（未声明 ⇒ 缺省 {@code off} ⇒ {@link GoStyleFormLimitMode.Mode#OFF}）
 *       运行期 = <b>2048</b> ⇒ ⇒ <b>它是唯一停在 2048 的域</b>；</li>
 *   <li>⇒ ⇒ <b>分叉存在</b>，且它<b>不是</b>无害的：favorite 域的 form 上限<b>边界口径</b>
 *       与 Go 不同（<b>Tomcat 数「总参数值个数（含空值）」，Go 数「非空值个数」</b>）
 *       ⇒ ⇒ 总「值」数 2049~10000 且非空值 ≤2048 时 <b>Go 放行 / RAG 拒</b>
 *       ⇒ ⇒ 归 <b>DRIFT-HOTAPI-030</b>（跨域，收口在 global）+ <b>DRIFT-FAV-API-001</b>（域内）。</li>
 * </ul>
 * <p>⇒ ⇒ <b>根因</b>：本类把<b>两件独立的事</b>耦合在了同一个 {@code mode} 上 ——
 * ①「<b>装不装</b> form 值<b>闸门</b>（决定拒绝<b>形状</b>）」与
 * ②「<b>抬不抬</b>容器<b>参数上限</b>（决定谁来当<b>判定权</b>）」。
 * ⇒ ⇒ {@link GoStyleFormLimitMode.Mode#OFF} 的<b>两种语义</b>对②的答案<b>不同</b>：
 * <ul>
 *   <li><b>域自捕获</b>（favorite）：域内<b>已经</b>装好了 Go 语义的判据
 *       ⇒ ⇒ 判定权<b>应该</b>交还给应用层 ⇒ ⇒ 容器上限<b>应该</b>抬到 10001；</li>
 *   <li><b>上限不可达</b>（Go 侧 {@code r.Form} 从不求值）：Go 对 query 参数个数<b>没有</b> 2048 上限
 *       ⇒ ⇒ 容器上限<b>也</b>应该抬到 10001（否则 Tomcat 会比 Go <b>更早</b>拒）。</li>
 * </ul>
 * <p>⚠️ <b>但本轮不改行为</b>：②的改法会让 <b>OFF 域的运行期容器上限从 2048 变 10001</b>
 * ⇒ ⇒ 那是<b>跨域可观察行为</b>变更（favorite 域的
 * {@code FavoriteApiRealTomcatGateTest} 会随之改）⇒ ⇒ 按「共享层缺陷一律转派 G4，
 * 但<b>跨域行为变更必须让域侧参与</b>」⇒ ⇒ <b>本轮只订正事实 + 登记 + 转派</b>
 * ⇒ ⇒ 详见 {@code docs/DRIFT/global/vs-common.md} §4.10.15。
 * <br>⚠️ <b>别只看 yaml 判断行为</b>（AGENTS.md §3.3.1）⇒ 真值以<b>运行期 connector</b> 为准
 * ⇒ ⇒ 守门测试必须断言<b>运行期</b>的值。
 *
 * <h3>🔴 「{@code OFF} ⇒ 不动容器上限」的判据是<b>语义可达性</b>，不是「写权范围」</h3>
 * <p>本类在 {@link GoStyleFormLimitMode.Mode#OFF} 时<b>不动</b>容器上限
 * ⇒ ⇒ 闸门的「开不开」与容器上限的「动不动」<b>始终同源</b>（同一个 {@code mode} 属性）。
 * <p>⇒ 而这个 {@code mode} 的<b>判据</b>是「<b>Go 侧该域是否真的解析 form/query</b>」
 * （见 {@link GoStyleFormLimitMode#SEMANTIC_REACHABILITY} 的逐域取证表）
 * ⇒ ⇒ <b>不是</b>「该域在不在 global 的写权范围内」——
 * 后者是一条与 Go 行为<b>无关</b>的理由 ⇒ ⇒ 用它当判据会让「下一个裸 decoder 域」照抄邻居。
 * <p>⇒ favorite 之所以停在 {@code OFF}，是因为它<b>语义上就该是 OFF</b>
 * （7 个 handler <b>各自捕获</b> {@code httpx.Parse} 的错 ⇒ 拒绝形状由域自己定
 * ⇒ ⇒ <b>共享层不能装闸门</b>）⇒ ⇒ <b>不是</b>「刻意保留的偏离」，也不该被下一个 agent 销账
 * ⇒ ⇒ 它与 Go <b>逐字同形</b>（真 HTTP 对拍：{@code tools/api-envelope-probe/favorite}）。
 *
 * <h2>Go 出处（本类的取值依据）</h2>
 * <p>Go 侧<b>没有</b>「容器层参数上限」这个概念（{@code net/http} 不限参数个数）⇒
 * 本类的 10001 <b>不是</b>复刻 Go 的某个常量，而是<b>为了把「判定权」交还给应用层</b>
 * 而设的一个边界：它必须 ≥ Go 的段数上限 + 1（这样 filter 才有机会先按 Go 的语义拒），
 * 同时必须<b>有限</b>（不能比 Go 的 DoS 闸门更松）。
 */
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = "org.springframework.boot.tomcat.ConfigurableTomcatWebServerFactory")
@Order(Ordered.LOWEST_PRECEDENCE)
public class GoStyleTomcatParameterLimitCustomizer implements WebServerFactoryCustomizer<WebServerFactory> {

    /**
     * 容器层的参数个数上限 —— <b>10001</b>。
     *
     * <p>= Go 的 {@code net/url} 段数上限（{@code defaultMaxParams = 10000}）<b>+1</b>。
     * <p>⚠️ <b>不是 Go 的常量</b>（Go 没有容器层上限）⇒ 它是「让应用层拿到判定权」所需的<b>最小</b>边界。
     */
    public static final int MAX_PARAMETER_COUNT = 10001;

    private final GoStyleFormLimitMode.Mode mode;

    public GoStyleTomcatParameterLimitCustomizer(
            @Value("${" + GoStyleFormLimitMode.PROPERTY + ":off}") String mode) {
        // 🔴 非法值在构造期就炸（与拦截器同一把尺子 ⇒ 两边不会对同一个属性得出不同解读）
        this.mode = GoStyleFormLimitMode.parse(mode);
    }

    @Override
    public void customize(WebServerFactory factory) {
        // 🔴 OFF ⇒ **不动**容器上限 ⇒ 本域的容器行为与修之前逐字节一致
        //    （判据 = Go 侧该域是否真的解析 form/query，见 GoStyleFormLimitMode#SEMANTIC_REACHABILITY；
        //      favorite 停在 OFF 是**语义正确**的（域自捕获 ⇒ 共享层不能装闸门），不是「刻意保留的偏离」）。
        // ⚠️ 已知后果：OFF 域的运行期上限因此停在 yaml 的 2048（其余域是 10001）⇒ 分叉存在
        //    ⇒ favorite 域的 form 上限「边界口径」与 Go 不同 ⇒ 归 DRIFT-HOTAPI-030 / DRIFT-FAV-API-001
        //    ⇒ 本轮只登记 + 转派（改它 = 跨域可观察行为变更 ⇒ 必须域侧参与）。
        if (mode == GoStyleFormLimitMode.Mode.OFF) {
            return;
        }
        // ⚠️ 用 WebServerFactory（而不是 ConfigurableTomcatWebServerFactory）做泛型参数：
        // Boot 的 WebServerFactoryCustomizerBeanPostProcessor 会把**所有** WebServerFactoryCustomizer
        // 都套到当前容器上 ⇒ 若泛型写窄了，换个容器就会在桥接方法里抛 ClassCastException。
        if (factory instanceof ConfigurableTomcatWebServerFactory tomcat) {
            tomcat.addConnectorCustomizers(limitCustomizer());
        }
    }

    /**
     * 产出那个「把上限设成 {@link #MAX_PARAMETER_COUNT}」的 connector customizer。
     *
     * <p>🔴 <b>为什么抽成静态方法</b>：守门测试必须能走<b>完全相同</b>的这一段逻辑
     * （而不是自己再写一遍 {@code connector.setMaxParameterCount(...)}）
     * ⇒ 否则「测试里设的值」与「生产里设的值」可能漂移 ⇒ 测试就假绿了。
     *
     * <p>⚠️ <b>为什么签名里不出现 {@code org.apache.catalina.connector.Connector}</b>：
     * 那样会把 {@code tomcat-embed-core} 变成 service/common 的**编译期**依赖
     * ⇒ 而 Tomcat 的类只在 web 宿主里才有 ⇒ 用 {@code TomcatConnectorCustomizer}
     * 这个<b>Boot 提供的</b>函数式接口，源码里就不必出现 Tomcat 的类型 ⇒
     * 生产类在**没有 Tomcat 的 rpc 模块里也能被加载**（虽然 {@code @ConditionalOnClass} 会挡掉它）。
     */
    public static TomcatConnectorCustomizer limitCustomizer() {
        return connector -> connector.setMaxParameterCount(MAX_PARAMETER_COUNT);
    }
}
