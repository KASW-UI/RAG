package com.rag.common.http;

/**
 * 🔴 本域的 <b>form 值上限闸门</b>用哪种拒绝形状 —— 对齐 Go 侧「该 api 有没有注册
 * {@code httpx.SetErrorHandler}」这一事实。
 *
 * <h2>Go 出处（决定三种模式的唯一依据）</h2>
 * <pre>
 * go-zero@v1.10.2/rest/httpx/responses.go:141-172  doHandleError
 *   if handler == nil {
 *       ...
 *       http.Error(w, err.Error(), http.StatusBadRequest)      // ← 无 SetErrorHandler
 *       return
 *   }
 *   code, body := handler(err)                                   // ← 有 SetErrorHandler
 *   ...
 *   writeJson(w, code, body)                                    //   ⇒ 业务信封
 * </pre>
 * <p>而 {@code handler} 来自 {@code httpx.SetErrorHandler} —— <b>逐域注册</b>（Go 侧逐域取证）：
 * <table border="1">
 *   <caption>Go 侧 {@code service/…/api/…center.go} / {@code commentservice.go}</caption>
 *   <tr><th>模式</th><th>域</th><th>wire 形状</th></tr>
 *   <tr><td>{@link #RAW}</td><td>article / user / task / favorite<br>（<b>没注册</b>）</td>
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
 * <h2>⚠️ 为什么默认是 {@link #OFF} 而不是 {@link #RAW}</h2>
 * <p>{@code favorite} 域<b>不在 global 的写权范围内</b> ⇒ 共享层对它必须是<b>零影响</b>：
 * <ul>
 *   <li>默认 {@code OFF} ⇒ favorite 既不改容器上限、也不装闸门 ⇒ <b>行为逐字节不变</b>；</li>
 *   <li>若默认 {@code RAW} ⇒ favorite 的 2049 场景会从「5003」变成「400 原文」
 *       ⇒ 那是对<b>别人的域</b>的未授权改动 ⇒ ❌。</li>
 * </ul>
 * ⇒ ⇒ 其它 9 个域<b>必须显式声明</b>本域的模式 ⇒ 漏声明就是漏保护 ⇒
 * 由 {@code tools/difftest/check-tomcat-limits.py} 静态断言（§3.3：配置要对 + 真值要验）。
 */
public final class GoStyleFormLimitMode {

    /**
     * 模式属性的 {@code @Value} key —— 值域 {@code off} | {@code raw} | {@code envelope}。
     */
    public static final String PROPERTY = "rag.http.go-form-limit.mode";

    /** 本域的闸门形状。 */
    public enum Mode {
        /** 不装闸门、也不改容器上限（{@code favorite} 走这条 ⇒ 行为逐字节不变）。 */
        OFF,
        /** 拒绝成 Go 的 {@code http.Error} 形状：<b>400 + text/plain + Go 原文</b>。 */
        RAW,
        /** 拒绝成业务信封：<b>抛异常</b> ⇒ 由各域自己的兜底 handler 渲染 200 + {code:5001,…}。 */
        ENVELOPE
    }

    private GoStyleFormLimitMode() {
    }

    /**
     * 把属性值解析成 {@link Mode}。
     *
     * <p>🔴 <b>非法值一律抛异常，不静默兜底</b>（§3.3 同款：不报错的失效）。
     * <p>⚠️ 静默兜底的后果是「以为装上了、其实没装」或「以为走信封、其实走了 400 原文」
     * ⇒ 两者都是<b>不可见的行为漂移</b> ⇒ 宁可启动失败。
     *
     * @param raw 属性原值（{@code null} / 空串 ⇒ {@link Mode#OFF}）
     * @throws IllegalArgumentException 值不在 {@code off|raw|envelope} 内
     */
    public static Mode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Mode.OFF;
        }
        String v = raw.trim().toLowerCase(java.util.Locale.ROOT);
        switch (v) {
            case "off":
                return Mode.OFF;
            case "raw":
                return Mode.RAW;
            case "envelope":
                return Mode.ENVELOPE;
            default:
                throw new IllegalArgumentException(
                        "非法的 " + PROPERTY + " 值：" + raw + "（只允许 off | raw | envelope）");
        }
    }
}
