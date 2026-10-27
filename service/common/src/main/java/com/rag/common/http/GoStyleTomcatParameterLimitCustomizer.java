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
 * <h2>🔴🔴 2026-10-27：抬上限<b>对所有域无条件生效</b>（不再随 {@code mode} 分叉）</h2>
 * <p>本类旧版按「{@code mode == OFF ⇒ 不动容器上限}」分叉 ⇒ ⇒ 造成「OFF 域停在 yaml 的 2048、
 * 其余域 10001」的<b>运行期分叉</b> ⇒ ⇒ 根因是<b>把两件独立的事耦合在同一个 {@code mode} 上</b>：
 * <ul>
 *   <li><b>轴 ①「装不装 form 值闸门」</b>（决定<b>拒绝形状</b>）⇒ 归 {@code mode}
 *       ⇒ OFF = 域自捕获 / 上限不可达 ⇒ 共享层<b>不装</b>闸门
 *       ⇒ 由 {@link GoStyleFormValueLimitInterceptor} 决定；</li>
 *   <li><b>轴 ②「抬不抬容器参数上限」</b>（决定<b>判定权归容器还是归应用层</b>）
 *       ⇒ <b>与 {@code mode} 无关</b> ⇒ 一律抬到 {@link #MAX_PARAMETER_COUNT}（10001）。</li>
 * </ul>
 * ⇒ ⇒ 本类现在<b>无条件</b>抬上限；{@code mode} 只在构造期被校验（非法值 ⇒ 启动失败）。
 * <p>⚠️ <b>为什么轴 ② 必须与 {@code mode} 解耦</b>：Go 的 {@code net/url} 段数上限是 <b>10000</b>
 * ⇒ Tomcat 若停在 2048，它会比 Go <b>更早</b>地拒（400 + HTML 错误页）⇒ ⇒
 * OFF 的<b>两种</b>情形都会被这条误伤：
 * <ul>
 *   <li><b>域自捕获</b>（favorite）：域内<b>已经</b>装好 Go 口径的应用层判据
 *       （数<b>非空值</b>）⇒ ⇒ 判定权<b>应该</b>交还应用层 ⇒ ⇒ 容器上限<b>应该</b>抬到 10001
 *       ⇒ ⇒ 否则「Tomcat 总参数值数 vs Go 非空值数」的<b>差 1</b>永远闭不上；</li>
 *   <li><b>上限不可达</b>（task：Go 侧 {@code r.Form} 从不求值）⇒ ⇒ 容器上限<b>也</b>该抬到 10001
 *       （否则 Tomcat 比 Go 更早拒）⇒ ⇒ 而该域的上限<b>不可达</b> ⇒ ⇒ 抬上限<b>无任何可观察变化</b>。</li>
 * </ul>
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
 * <br>⇒ 但<b>运行期值</b>由本类决定：<b>一律</b>抬到 {@link #MAX_PARAMETER_COUNT}（10001）——
 * <b>与 {@code mode} 无关</b>（<b>无条件</b>；详见下面「两轴解耦」一节）。
 * 🔴 **订正（2026-10-27 · G2 反馈）**：本行原先写「{@code mode ≠ OFF} ⇒ 抬到 10001；
 * {@code mode == OFF} ⇒ <b>不动</b> ⇒ 运行期 = yaml 的 2048」—— **这句是过期描述**，
 * 它与本类 javadoc 下方的「两轴解耦」结论（以及 {@link #limitCustomizer()} 的实际实现：
 * <b>无任何 mode 判断</b>）<b>自相矛盾</b> ⇒ ⇒ **读者落到这两行会读到错误的当前行为**。
 * ⇒ ⇒ 保留上一句的 yaml 层事实（10 份 yaml 都写 2048）⇒ ⇒ **运行期一律 10001**。
 *
 * <h3>🔴 「OFF 域的运行期上限 = 2048」这个分叉<b>已消除</b>（2026-10-27 第十三轮）</h3>
 * <p>本类旧版 javadoc 写「10 份 yaml 保持一致 ＋ 共享层统一覆盖 ⇒ <b>无分叉、favorite 零改动</b>」
 * ⇒ ⇒ <b>这句是错的</b>（它把「yaml 层一致」当成了「运行期一致」）：
 * <ul>
 *   <li>旧版实测：<b>9 个声明了 {@code mode} 的域</b>运行期 = <b>10001</b>；</li>
 *   <li>旧版实测：<b>favorite</b>（未声明 ⇒ 缺省 {@code off} ⇒ {@link GoStyleFormLimitMode.Mode#OFF}）
 *       运行期 = <b>2048</b> ⇒ ⇒ <b>它是唯一停在 2048 的域</b>；</li>
 *   <li>⇒ ⇒ <b>分叉存在</b>，且它<b>不是</b>无害的：favorite 域的 form 上限<b>边界口径</b>
 *       与 Go 不同（<b>Tomcat 数「总参数值个数（含空值）」，Go 数「非空值个数」</b>）
 *       ⇒ ⇒ 总「值」数 2049~10000 且非空值 ≤2048 时 <b>Go 放行 / RAG 拒</b>
 *       ⇒ ⇒ 归 <b>DRIFT-HOTAPI-030</b>（跨域，收口在 global）+ <b>DRIFT-FAV-API-001</b>（域内）。</li>
 * </ul>
 * <p>⇒ ⇒ <b>根因</b>：本类把<b>两件独立的事</b>耦合在了同一个 {@code mode} 上 ——
 * ①「<b>装不装</b> form 值<b>闸门</b>（决定拒绝<b>形状</b>）」与
 * ②「<b>抬不抬</b>容器<b>参数上限</b>（决定谁来当<b>判定权</b>）」。
 * ⇒ ⇒ {@link GoStyleFormLimitMode.Mode#OFF} 的<b>两种语义</b>对②的答案<b>相同</b>：
 * <ul>
 *   <li><b>域自捕获</b>（favorite）：域内<b>已经</b>装好了 Go 语义的判据
 *       ⇒ ⇒ 判定权<b>应该</b>交还给应用层 ⇒ ⇒ 容器上限<b>应该</b>抬到 10001；</li>
 *   <li><b>上限不可达</b>（Go 侧 {@code r.Form} 从不求值）：Go 对 query 参数个数<b>没有</b> 2048 上限
 *       ⇒ ⇒ 容器上限<b>也</b>应该抬到 10001（否则 Tomcat 会比 Go <b>更早</b>拒）。</li>
 * </ul>
 * <p>⇒ ⇒ <b>本轮（第十三轮）落码</b>：轴 ② 与 {@code mode} <b>解耦</b> ⇒ 本类<b>无条件</b>抬上限
 * ⇒ ⇒ 「OFF 域停在 2048」这个分叉<b>消除</b> ⇒ ⇒ favorite 的「差 1」窗口<b>闭合</b>
 * ⇒ ⇒ ⚠️ <b>唯一可观察变化落在 favorite 那一格</b>（总「值」数 2049~10000 且非空值 ≤2048
 * ⇒ ⇒ Go 放行 / RAG 由「拒」变「放行」）⇒ ⇒ 那是<b>跨域可观察行为变更</b>
 * ⇒ ⇒ favorite 域的 {@code FavoriteApiRealTomcatGateTest} 需<b>同步更新</b>（域侧动作）
 * ⇒ ⇒ task 域<b>无可观察变化</b>（上限不可达 ⇒ 见本类 javadoc 的「两轴解耦」一节）
 * ⇒ ⇒ 台账 ⇒ {@code docs/DRIFT/global/vs-common.md} §4.10.25。
 * <br>⚠️ <b>别只看 yaml 判断行为</b>（AGENTS.md §3.3.1）⇒ 真值以<b>运行期 connector</b> 为准
 * ⇒ ⇒ 守门测试必须断言<b>运行期</b>的值。
 *
 * <h3>🔴 {@code mode} 的判据仍然只管「装不装闸门」，不管「抬不抬上限」</h3>
 * <p>本类<b>不再</b>按 {@code mode} 分支 ⇒ ⇒ 「闸门的开不开」与「容器上限动不动」<b>已解耦</b>：
 * <ul>
 *   <li><b>闸门</b>（拒绝形状）⇒ 由 {@link GoStyleFormValueLimitInterceptor} 按 {@code mode} 决定
 *       ⇒ {@code mode} 的判据仍是「<b>语义可达性</b>」——「Go 侧该域是否真的解析 form/query」
 *       （见 {@link GoStyleFormLimitMode#SEMANTIC_REACHABILITY} 的逐域取证表）
 *       ⇒ ⇒ <b>不是</b>「该域在不在 global 的写权范围内」——
 *       后者是一条与 Go 行为<b>无关</b>的理由 ⇒ ⇒ 用它当判据会让「下一个裸 decoder 域」照抄邻居；</li>
 *   <li><b>容器上限</b>（判定权归谁）⇒ <b>无条件</b>抬到 {@link #MAX_PARAMETER_COUNT}
 *       ⇒ <b>与 {@code mode} 无关</b>。</li>
 * </ul>
 * <p>⇒ favorite 之所以停在 {@code OFF}，是因为它<b>语义上就该是 OFF</b>
 * （7 个 handler <b>各自捕获</b> {@code httpx.Parse} 的错 ⇒ 拒绝形状由域自己定
 * ⇒ ⇒ <b>共享层不能装闸门</b>）⇒ ⇒ <b>不是</b>「刻意保留的偏离」，也不该被下一个 agent 销账
 * ⇒ ⇒ 它与 Go <b>逐字同形</b>（真 HTTP 对拍：{@code tools/api-envelope-probe/favorite}）
 * ⇒ ⇒ 而「<b>不装闸门</b>」<b>不等于</b>「不管容器上限」⇒ ⇒ 两者已解耦。
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

    public GoStyleTomcatParameterLimitCustomizer(
            @Value("${" + GoStyleFormLimitMode.PROPERTY + ":off}") String mode) {
        // 🔴 非法值在构造期就炸（与拦截器同一把尺子 ⇒ 两边不会对同一个属性得出不同解读）
        // ⚠️ 本类**不再按 mode 分支**（抬上限已无条件化）⇒ 这里只做**校验**，不保存结果：
        //    保留这条校验是为了「非法 mode 值 ⇒ 进程起不来」这条纪律不因两轴解耦而丢
        //    ⇒ 而「装不装闸门」的真判定在 GoStyleFormValueLimitInterceptor（它按 mode 分支）。
        GoStyleFormLimitMode.parse(mode);
    }

    @Override
    public void customize(WebServerFactory factory) {
        // 🔴 抬上限**对所有域无条件生效**（mode 不再参与分叉）：
        //    mode 只决定「装不装应用层闸门」（= 拒绝形状），不决定「判定权归容器还是归应用层」。
        //    ⇒ OFF 域（favorite = 域自捕获 / task = 上限不可达）同样抬到 10001：
        //       · favorite：域内已有 Go 口径的应用层判据（数**非空值**）
        //         ⇒ 抬上限后「Tomcat 总参数值数 vs Go 非空值数」的**差 1**自动闭合；
        //       · task：Go 侧 r.Form 从不求值 ⇒ 上限不可达 ⇒ 抬上限**无任何可观察变化**。
        // ⚠️ 跨域可观察行为变更 ⇒ favorite 域的 FavoriteApiRealTomcatGateTest 需同步更新（域侧动作）。
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
