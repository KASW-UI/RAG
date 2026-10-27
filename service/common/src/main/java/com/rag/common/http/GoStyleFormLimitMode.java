package com.rag.common.http;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 🔴 本域的 <b>form 值上限闸门</b>用哪种拒绝形状 —— 对齐 Go 侧「该 api 有没有注册
 * {@code httpx.SetErrorHandler}」这一事实。
 *
 * <h2>🟢 闸门「开 / 关」的<b>唯一判据</b>：<b>语义可达性</b>（G2 反馈单 FB-02）</h2>
 * <p><b>Go 侧该域的 handler 是否真的解析 form / query</b>：
 * <ul>
 *   <li><b>解析了</b>（handler 调 {@code httpx.Parse}）
 *       ⇒ ⇒ {@code httpx.Parse} → {@code ParseForm} → {@code GetFormValues}（{@code httpx/util.go:15-48}）
 *       ⇒ ⇒ {@code r.ParseForm()} 会触发 Go 的 {@code net/url} <b>10000 段</b>上限
 *       ⇒ ⇒ {@code GetFormValues} 会数<b>非空值</b>，超过 {@code maxFormParamCount = 2048} 即报错
 *       ⇒ ⇒ <b>两道上限都可达</b> ⇒ 闸门<b>必须开</b>；</li>
 *   <li><b>没解析</b>（handler 直接 {@code json.NewDecoder(r.Body)}）
 *       ⇒ ⇒ {@code r.Form} <b>从不被求值</b> ⇒ 两道上限<b>都不可达</b>
 *       ⇒ ⇒ <b>闸门必须关</b>（{@link Mode#OFF}）⇒ RAG 与 Go 同为「不因 query 而拒」。</li>
 * </ul>
 *
 * <h3>🔴 为什么这条判据必须是「语义可达性」，而<b>不能</b>是「写权范围」</h3>
 * <p>旧版本类的 javadoc 把「{@code off} 的依据」写成「<b>favorite 域不在 global 的写权范围内</b>」
 * ⇒ ⇒ 那是一条<b>与 Go 行为无关</b>的理由 ⇒ ⇒ 后果：
 * <ul>
 *   <li>① 判据无法回答「下一个域该开还是该关」⇒ ⇒ 下一个<b>裸 decoder</b> 域会照抄别人的声明；</li>
 *   <li>② 漏声明的域会<b>静默落到缺省 {@code off}</b>
 *       ⇒ ⇒ 而缺省 {@code off} 只对「Go 侧不解析」的域才是对的
 *       ⇒ ⇒ 对「Go 侧解析」的域，缺省 {@code off} <b>就是一条漂移</b>。</li>
 * </ul>
 * ⇒ ⇒ 现把判据改成「语义可达性」，并把 <b>10 个域的逐域取证落成代码</b>
 * （{@link #SEMANTIC_REACHABILITY}）⇒ ⇒ 判据<b>只有一份</b>，且<b>可被机器核对</b>。
 *
 * <h2>⚠️ 两条轴必须分开看（这是本类最容易混淆的地方）</h2>
 * <table border="1">
 *   <caption>「开不开」与「拒成什么样」是<b>两条独立的轴</b></caption>
 *   <tr><th>轴</th><th>判据</th><th>取值</th></tr>
 *   <tr><td><b>① 开 / 关</b></td><td>Go 侧该域是否真的解析 form/query（<b>语义可达性</b>）</td>
 *       <td>{@link Mode#OFF} ⇔ 不可达；{@link Mode#RAW} / {@link Mode#ENVELOPE} ⇔ 可达</td></tr>
 *   <tr><td><b>② 拒成什么形状</b></td><td>Go 侧该 api 有没有注册 {@code httpx.SetErrorHandler}</td>
 *       <td>{@link Mode#RAW} ⇔ 没注册（400 + text/plain + Go 原文）；<br>
 *           {@link Mode#ENVELOPE} ⇔ 注册了（200 + 业务信封）</td></tr>
 * </table>
 * <p>⚠️ <b>轴 ② 的逐域事实（Go 侧 {@code service/…/api/…center.go} / {@code commentservice.go}）</b>：
 * <table border="1">
 *   <tr><th>模式</th><th>域</th><th>wire 形状</th></tr>
 *   <tr><td>{@link #RAW}</td><td>article / user / admin / favorite<br>（<b>没注册</b>）</td>
 *       <td><b>HTTP 400</b> + {@code text/plain; charset=utf-8} + {@code nosniff}<br>
 *           + body = Go 原文 + {@code "\n"}</td></tr>
 *   <tr><td>{@link #ENVELOPE}</td><td>hot / follow / like / message / comment<br>（<b>注册了</b>）</td>
 *       <td><b>HTTP 200</b> + {@code {"code":5001,"msg":"&lt;该域文案&gt;","data":null}}</td></tr>
 * </table>
 *
 * <h2>⚠️ 为什么 5001 的文案<b>不能</b>写在共享层</h2>
 * <p>各域 5001 的文案<b>不一样</b>（Go 侧逐域取证）：
 * <ul>
 *   <li>hot / follow / message = {@code "internal server error"}；</li>
 *   <li>like / comment = {@code "系统内部错误"}。</li>
 * </ul>
 * ⇒ ⇒ 共享层<b>只抛异常</b>，由<b>各域自己的兜底 handler</b>去查自己的表渲染 ⇒
 * <b>零文案复制、零 per-domain 硬编码</b>。
 * <p>（实测：9 个 api 模块<b>全部</b>有 {@code @ExceptionHandler(Exception.class)} 兜底，
 * 且都渲染成 {@code 200 + {code:5001, 该域查表文案, data:null}}。）
 *
 * <h2>🔴 「缺省」为什么是 {@link #OFF}</h2>
 * <p>{@link #OFF} 是<b>缺省值</b> ⇒ 域没声明时<b>不装闸门</b>。
 * <p>⚠️ 但缺省只对「Go 侧<b>不</b>解析」的域才是<b>正确</b>的 ⇒ ⇒ 对「Go 侧解析」的域，
 * 缺省 {@code off} <b>就是漂移</b> ⇒ ⇒ 这正是本类要有一张<b>逐域表</b>的原因
 * （漏声明 ⇒ 没人提醒 ⇒ 静默漂移）。
 * <p>⇒ 缺省值本身仍取 {@link #OFF}：它是「<b>未取证</b>」时的<b>安全侧</b>（不装闸门 ⇒ 不误拒）
 * ⇒ ⇒ 而「误拒」比「漏拒」更危险（会挡掉 Go 能受理的请求）⇒ ⇒ 这个取舍是有意的。
 */
public final class GoStyleFormLimitMode {

    /**
     * 模式属性的 {@code @Value} key —— 值域 {@code off} | {@code raw} | {@code envelope}。
     *
     * <h2>🔴 YAML 里写 {@code off} <b>必须加引号</b>：{@code mode: "off"}</h2>
     *
     * <p>⚠️ <b>YAML 1.1 会把未加引号的 {@code off} 解析成布尔 {@code false}</b>
     * （SnakeYAML 沿用 YAML 1.1 的布尔字面量表：{@code y|Y|yes|Yes|YES|n|N|no|No|NO|
     * true|True|TRUE|false|False|FALSE|on|On|ON|off|Off|OFF}）
     * ⇒ ⇒ 域侧按文档写 {@code mode: off} 时，{@code @Value} 实际拿到的是字符串 {@code "false"}
     * ⇒ ⇒ 而 {@code "false"} <b>不在</b> {@link #parse} 的原合法值域里 ⇒ ⇒
     * <b>构造期就抛 {@link IllegalArgumentException} ⇒ 进程起不来</b>
     * （G2 反馈单 FB-05 的 L4 实测：报错信息里的值是 {@code false} 而不是 {@code off}）。
     * <p>⇒ ⇒ <b>两种正确写法</b>：① {@code mode: "off"}（<b>加引号</b>，推荐）② {@code mode: false}
     * ⇒ ⇒ 本类<b>同时接受这两种</b>（见 {@link #parse}）⇒ ⇒ 域侧<b>按文档写也能用</b>。
     *
     * <p>⚠️ <b>为什么不是「把 {@code off} 从合法值里删掉」</b>：{@code off} 是本类的
     * <b>文档化合法取值</b>（{@link #OFF} 的语义）⇒ 删掉它等于「文档与实现不符」⇒
     * ⇒ 正确方向是<b>让实现接受文档里那个值的实际编码形式</b>，而不是改文档迁就实现。
     */
    public static final String PROPERTY = "rag.http.go-form-limit.mode";

    /** 本域的闸门形状。 */
    public enum Mode {
        /** 不装闸门、也不改容器上限（Go 侧<b>不</b>解析 form/query 的域走这条）。 */
        OFF,
        /** 拒绝成 Go 的 {@code http.Error} 形状：<b>400 + text/plain + Go 原文</b>。 */
        RAW,
        /** 拒绝成业务信封：<b>抛异常</b> ⇒ 由各域自己的兜底 handler 渲染 200 + {code:5001,…}。 */
        ENVELOPE
    }

    /**
     * 某个 api 域在 Go 侧的 <b>form/query 语义可达性</b> —— 闸门「开不开」的<b>唯一判据</b>。
     *
     * @param module            该域的模块路径（相对仓库根，{@code service/…}）
     * @param goSideParsesForm  Go 侧该域的 handler 是否<b>真的</b>调 {@code httpx.Parse}（⇒ 解析 form/query）
     * @param semanticMode      由上一行推出的<b>语义</b>模式（闸门「应当」是什么）
     * @param goEvidence        Go 侧证据（文件:行号 / 命中数）
     */
    public record DomainReachability(
            String module,
            boolean goSideParsesForm,
            Mode semanticMode,
            String goEvidence) {

        /** 语义模式与「是否解析」是否自洽（{@code OFF} ⇔ 不可达）。 */
        public boolean selfConsistent() {
            return (semanticMode == Mode.OFF) == !goSideParsesForm;
        }
    }

    /**
     * 🔴 <b>10 个 api 域的语义可达性逐域取证表</b>（闸门「开不开」的<b>唯一判据源</b>）。
     *
     * <p>⚠️ <b>这张表是「目标态」，不是「现状快照」</b>：
     * <ul>
     *   <li>域自己的 yaml 里声明的 {@code mode} 是<b>现状</b>；</li>
     *   <li>本表的 {@code semanticMode} 是<b>应当</b>（由 Go 侧事实推出）；</li>
     *   <li>两者不一致 ⇒ <b>就是一条待修的漂移</b>（由各域自己改 yaml 修，共享层只记账）。</li>
     * </ul>
     * ⇒ ⇒ 这就是「下一个裸 decoder 域不会重复踩」的机制：
     * <b>新增域时照着这张表填，而不是照抄邻居</b>。
     */
    public static final List<DomainReachability> SEMANTIC_REACHABILITY = List.of(
            new DomainReachability("service/article/article-api", true, Mode.RAW,
                    "article/api/internal/handler/**：httpx.Parse 命中 6 处；"
                            + "未注册 httpx.SetErrorHandler ⇒ 400 + text/plain + Go 原文"),
            new DomainReachability("service/comment/comment-api", true, Mode.ENVELOPE,
                    "comment/api/internal/handler/**：调 httpx.Parse；"
                            + "comment/api/commentservice.go:47 注册了 httpx.SetErrorHandler ⇒ 业务信封"),
            new DomainReachability("service/favorite/favorite-api", true, Mode.RAW,
                    "favorite/api/internal/handler/favorite/**：httpx.Parse 命中 7 处；"
                            + "未注册 httpx.SetErrorHandler ⇒ 语义答案是 raw"),
            new DomainReachability("service/follow/follow-api", true, Mode.ENVELOPE,
                    "follow/api/internal/handler/**：调 httpx.Parse；"
                            + "follow/api/followcenter.go:53 注册了 httpx.SetErrorHandler ⇒ 业务信封"),
            new DomainReachability("service/hot/hot-api", true, Mode.ENVELOPE,
                    "hot/api/internal/handler/**：调 httpx.Parse；"
                            + "hot/api/hotcenter.go:53 注册了 httpx.SetErrorHandler ⇒ 业务信封"),
            new DomainReachability("service/like/like-api", true, Mode.ENVELOPE,
                    "like/api/internal/handler/**：调 httpx.Parse；"
                            + "like/api/likecenter.go:48 注册了 httpx.SetErrorHandler ⇒ 业务信封"),
            new DomainReachability("service/message/message-api", true, Mode.ENVELOPE,
                    "message/api/internal/handler/**：调 httpx.Parse；"
                            + "message/api/messagecenter.go:53 注册了 httpx.SetErrorHandler ⇒ 业务信封"),
            new DomainReachability("service/task/task-api", false, Mode.OFF,
                    "task/api/**：httpx.Parse / ParseForm / GetFormValues 命中数 = 0"
                            + "（gettaskhandler.go:19 用 json.NewDecoder(r.Body) + UseNumber，"
                            + "只 import 了 httpx 但只用 httpx.ErrorCtx）"
                            + " ⇒ r.Form 从不被求值 ⇒ 两道上限不可达 ⇒ 语义答案是 off"),
            new DomainReachability("service/users/user/user-api", true, Mode.RAW,
                    "user/api/internal/handler/user/**：httpx.Parse 命中 8 处；"
                            + "未注册 httpx.SetErrorHandler ⇒ 400 + text/plain + Go 原文"),
            new DomainReachability("service/users/admin/admin-api", true, Mode.RAW,
                    "admin/api/internal/handler/admin/**：httpx.Parse 命中 13 处；"
                            + "未注册 httpx.SetErrorHandler ⇒ 400 + text/plain + Go 原文"));

    /**
     * 🔴 <b>已登记的偏离</b>：某域的实际模式与本表的语义答案<b>不一致</b>，且<b>刻意不改</b>。
     *
     * <p>⚠️ 这里只登记<b>刻意保留</b>的偏离；<b>待修</b>的偏离（各域自己改 yaml）不在这里。
     *
     * @param module    模块路径
     * @param actualMode 该域<b>实际</b>生效的模式（= 缺省值）
     * @param reason    为什么<b>刻意</b>不改
     * @param observable 可观察差异（写清楚，别只写「不一致」）
     */
    public record RegisteredDeviation(String module, Mode actualMode, String reason, String observable) {
    }

    /**
     * 🔴 刻意保留的偏离（<b>只登记，不改</b>）。
     *
     * <p>⚠️ favorite 域<b>不在 global 的写权范围内</b>（AGENTS.md §5.3c）⇒ 共享层对它必须<b>零影响</b>
     * ⇒ ⇒ 它的 yaml 里**不得**出现 {@code rag.http.go-form-limit.mode}
     * ⇒ ⇒ 它<b>缺省</b> {@link Mode#OFF}。
     * <p>⚠️ 而按语义判据，favorite 的 Go 侧<b>真的</b>解析 form（7 处 {@code httpx.Parse}）⇒ 语义答案是 {@link Mode#RAW}
     * ⇒ ⇒ <b>这是一条已知、刻意保留的偏离</b>，<b>不是</b>「已对齐」：
     * <ul>
     *   <li>Go：{@code >2048} 个<b>非空</b> query 值 ⇒ {@code httpx.GetFormValues} 报
     *       「too many form values」⇒ {@code http.Error} ⇒ <b>400 + text/plain + Go 原文</b>；</li>
     *   <li>RAG（OFF）：闸门不装；容器层仍按 yaml 的 {@code max-parameter-count: 2048} 拒
     *       ⇒ <b>400 + Tomcat 的 HTML 错误页</b>，且 Tomcat 数的是<b>参数总数（含空值）</b>。</li>
     * </ul>
     * ⇒ ⇒ <b>状态码相同（400），响应体形状不同</b> ⇒ LOW 级形状漂移。
     * <p>⚠️ <b>下一个 agent 不得把它当成「已对齐」销账，也不得擅自去改 favorite</b>。
     */
    public static final List<RegisteredDeviation> REGISTERED_DEVIATIONS = List.of(
            new RegisteredDeviation("service/favorite/favorite-api", Mode.OFF,
                    "favorite 域不在 global 的写权范围内（AGENTS.md §5.3c）⇒ 共享层对它必须零影响；"
                            + "它的 yaml 不得出现该属性 ⇒ 缺省 off",
                    "Go：>2048 个非空 query 值 ⇒ 400 + text/plain + Go 原文；"
                            + "RAG(OFF)：400 + Tomcat HTML 错误页（且计的是参数总数、含空值）"
                            + " ⇒ 状态码一致、响应体形状不同（LOW）"));

    private GoStyleFormLimitMode() {
    }

    /**
     * 把属性值解析成 {@link Mode}。
     *
     * <p>🔴 <b>非法值一律抛异常，不静默兜底</b>（§3.3 同款：不报错的失效）。
     * <p>⚠️ 静默兜底的后果是「以为装上了、其实没装」或「以为走信封、其实走了 400 原文」
     * ⇒ 两者都是<b>不可见的行为漂移</b> ⇒ 宁可启动失败。
     *
     * <h2>🟢 唯一的别名：{@code "false"}（YAML 1.1 里 {@code off} 的布尔形态）</h2>
     *
     * <p>{@code "false"} <b>不是</b>「非法值兜底」，而是<b>精确匹配</b>：
     * YAML 1.1（SnakeYAML）把未加引号的 {@code off}（以及 {@code no} / {@code n}）
     * 解析成布尔 {@code false} ⇒ ⇒ {@code "false"} 是
     * <b>文档化合法取值 {@code off} 的「唯一实际编码形式」</b>
     * ⇒ ⇒ 接受它 = 让实现<b>对齐文档</b>，而不是放宽合法性。
     *
     * <p>⚠️ <b>为什么不放宽成「任意非法值都当 OFF」</b>：那会<b>重新引入</b>
     * 「静默兜底 = 以为装上了其实没装」（本方法 javadoc 明确反对的那种失效）
     * ⇒ ⇒ 例：把 {@code mode: raw} 误写成 {@code mode: OFF1} ⇒ 若兜底成 OFF
     * ⇒ ⇒ 该域会<b>静默失去闸门</b>（且「多记 413」方向的错误更难察觉）⇒ ⇒ 仍然必须炸。
     *
     * @param raw 属性原值（{@code null} / 空串 ⇒ {@link Mode#OFF}）
     * @throws IllegalArgumentException 值不在 {@code off|false|raw|envelope} 内
     */
    public static Mode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Mode.OFF;
        }
        String v = raw.trim().toLowerCase(java.util.Locale.ROOT);
        switch (v) {
            case "off":
                return Mode.OFF;
            case "false":
                // YAML 1.1：未加引号的 off / no / n ⇒ 布尔 false（G2 反馈单 FB-05 的 L4 实测）
                // ⇒ 域侧按文档写 `mode: off` 时拿到的就是它 ⇒ 必须与 "off" 同义
                return Mode.OFF;
            case "raw":
                return Mode.RAW;
            case "envelope":
                return Mode.ENVELOPE;
            default:
                throw new IllegalArgumentException(
                        "非法的 " + PROPERTY + " 值：" + raw + "（只允许 off | false | raw | envelope；"
                                + "注意 YAML 里写 off 必须加引号：mode: \"off\"，否则会被 YAML 1.1 解析成布尔 false）");
        }
    }

    /** 某个模块路径的语义可达性记录（找不到 ⇒ {@code null}）。 */
    public static DomainReachability reachabilityOf(String module) {
        for (DomainReachability r : SEMANTIC_REACHABILITY) {
            if (r.module().equals(module)) {
                return r;
            }
        }
        return null;
    }

    /** 某个模块路径的<b>语义模式</b>（找不到 ⇒ {@code null} ⇒ 调用方必须自己处理「未知域」）。 */
    public static Mode semanticModeOf(String module) {
        DomainReachability r = reachabilityOf(module);
        return r == null ? null : r.semanticMode();
    }

    /** 某个模块路径是否登记了「刻意保留的偏离」。 */
    public static boolean hasRegisteredDeviation(String module) {
        for (RegisteredDeviation d : REGISTERED_DEVIATIONS) {
            if (d.module().equals(module)) {
                return true;
            }
        }
        return false;
    }

    /** 模块路径 → 语义可达性（便于按域查；{@link List} 保持声明顺序）。 */
    public static Map<String, DomainReachability> reachabilityIndex() {
        Map<String, DomainReachability> index = new LinkedHashMap<>();
        for (DomainReachability r : SEMANTIC_REACHABILITY) {
            index.put(r.module(), r);
        }
        return Map.copyOf(index);
    }
}
