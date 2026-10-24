package com.rag.hot.hot.api.exception;

import com.rag.common.exception.BusinessException;

/**
 * Go {@code *hotcommon.CodeError} 的等价物 —— <b>只为对齐日志的 {@code error_reason} 字段</b>。
 *
 * <h2>Go 出处</h2>
 * <ul>
 *   <li>{@code service/hot/common/errmsg.go:10-13} —— 类型定义：
 *       {@code type CodeError struct { Code int; Msg string }}；</li>
 *   <li>{@code service/hot/common/errmsg.go:15-17} —— {@code func (e *CodeError) Error() string
 *       { return fmt.Sprintf("ErrCode:%d, Errmsg:%s", e.Code, e.Msg) }}；</li>
 *   <li>{@code service/hot/common/errmsg.go:19-24} —— {@code NewErrCode(code)} ⇒
 *       {@code Code=code}、{@code Msg=GetErrMsg(code)}；</li>
 *   <li>构造点：{@code service/hot/api/internal/logic/hot/getHotArticlesLogic.go:68,72-74}。</li>
 * </ul>
 *
 * <h2>为什么 {@code getMessage()} 被覆写成 Go 的 {@code Error()} 整串</h2>
 * <p>Go 侧的 <b>日志</b>取的是 {@code err.Error()}（{@code service/common/logger/logger.go:120}），
 * 所以「hot-rpc 返回空响应」这一条 business_error 的 {@code error_reason} 字段是：
 * <pre>
 *   ErrCode:5001, Errmsg:internal server error
 * </pre>
 * 而 RAG 侧日志取的是 {@code err.getMessage()}（{@code service/common/logger/Logger.java:243}），
 * {@link BusinessException} 的 {@code getMessage()} 就是纯文案 {@code "internal server error"}
 * ⇒ <b>少了一段前缀</b>，字段值与 Go 不一致。
 *
 * <h2>⚠️ 为什么「HTTP 信封」不能跟着变</h2>
 * <p>Go 的信封是<b>逐字段复制</b>（{@code hotcenter.go:53-66}）：
 * <pre>
 *   code := hotcommon.ErrorServerCommon
 *   msg  := hotcommon.GetErrMsg(code)
 *   var codeErr *hotcommon.CodeError
 *   if errors.As(err, &amp;codeErr) { code = codeErr.Code; msg = codeErr.Msg }   // ← 取字段，不是 Error() 整串
 * </pre>
 * ⇒ 信封的 {@code msg} 必须是<b>纯文案</b> {@code "internal server error"}，
 * <b>不能</b>是 {@code "ErrCode:5001, Errmsg:internal server error"}。
 * ⇒ 故本类把两个面<b>分开</b>：
 * <ul>
 *   <li>{@link #getBizMsg()} ⇒ 纯文案，<b>给 HTTP 信封</b>；</li>
 *   <li>{@link #getMessage()} ⇒ Go 的 {@code Error()} 整串，<b>给日志</b>。</li>
 * </ul>
 * ⚠️ {@code GlobalExceptionHandler} 必须用 {@link #getBizMsg()} 取信封文案；
 * 用 {@code getMessage()} 会把 {@code "ErrCode:5001, Errmsg:..."} 写进响应体 ⇒ <b>制造新漂移</b>。
 *
 * <h2>为什么放在 hot-api 而不是 hot-common</h2>
 * <ul>
 *   <li>Go 的 {@code CodeError} 在 {@code service/hot/common/}，但 Go 没有「跨域共享的错误基类」；
 *       RAG 的对应基类 {@link BusinessException} 在<b>跨域</b>的 {@code service/common}。</li>
 *   <li>{@code hot-common} 的 pom <b>不依赖</b> {@code service/common}（Go 侧 {@code errmsg.go} 也是零依赖的纯值库）
 *       ⇒ 在 hot-common 里 {@code extends BusinessException} 会引入一条 Go 侧没有的依赖。</li>
 *   <li>Go 侧 hot-<b>rpc</b> 全域不使用 {@code CodeError}（只有 api 侧用）
 *       ⇒ 该类型不需要跨组件共享 ⇒ 落在<b>用它的那个组件</b>里不制造新耦合。</li>
 * </ul>
 */
public class HotCodeError extends BusinessException {

    /** 业务文案（Go 的 {@code CodeError.Msg}）—— 给 HTTP 信封用。 */
    private final String bizMsg;

    /**
     * @param code 业务码（Go 的 {@code CodeError.Code}）
     * @param msg  业务文案（Go 的 {@code CodeError.Msg}），通常是 {@code GetErrMsg(code)}
     */
    public HotCodeError(int code, String msg) {
        super(code, msg);
        this.bizMsg = msg;
    }

    /**
     * Go {@code CodeError.Msg} —— <b>纯文案</b>，供 HTTP 信封使用。
     *
     * <p>⚠️ <b>不要</b>用 {@link #getMessage()} 填信封：那个方法是 Go {@code Error()} 整串，
     * 会把 {@code "ErrCode:5001, Errmsg:internal server error"} 写进响应体。
     */
    public String getBizMsg() {
        return bizMsg;
    }

    /**
     * 覆写 {@code Throwable.getMessage()} ⇒ 返回 Go {@code (*CodeError).Error()} 的逐字节形态。
     *
     * <p>这是本类存在的<b>唯一</b>理由：让日志的 {@code error_reason} 字段与 Go 一致。
     * ⚠️ 该串<b>只</b>进日志；HTTP 信封走 {@link #getBizMsg()}。
     */
    @Override
    public String getMessage() {
        return "ErrCode:" + getCode() + ", Errmsg:" + bizMsg;
    }
}
