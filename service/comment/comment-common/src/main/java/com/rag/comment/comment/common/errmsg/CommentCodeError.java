package com.rag.comment.comment.common.errmsg;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * comment 域业务错误载体 —— 对应 Go 端 {@code service/comment/common/errmsg/errmsg.go:10-31}：
 *
 * <pre>
 * type CodeError struct {
 *     Code int    `json:"code"`
 *     Msg  string `json:"msg"`
 * }
 * func (e *CodeError) Error() string {
 *     return fmt.Sprintf("ErrCode:%d, Errmsg:%s", e.Code, e.Msg)
 * }
 * func NewErrCode(code int) error            { return &amp;CodeError{code, GetErrMsg(code)} }
 * func NewErrCodeMsg(code int, msg string) error { return &amp;CodeError{code, msg} }
 * </pre>
 *
 * <p><b>为什么是 {@link RuntimeException} 而不是 checked exception</b>：
 * Go 的 {@code error} 在语言层面没有 checked/unchecked 之分，logic 层 {@code return nil, err}、
 * 上层判定。Java 侧对等物是 unchecked 异常 —— 业务判定处显式构造并抛出，
 * 由 {@link CommentGrpcError}（RPC 出口）或 comment-api 的 GlobalExceptionHandler（HTTP 出口）统一转换。
 * 用 checked 会把每个调用点都拖进 {@code throws}，而 Go 侧根本没有这个成本。
 *
 * <p><b>⚠️ api 层必须沿 cause 链回溯，不能只 catch 最外层</b>：
 * Go 的 HTTP 出口 {@code api/commentservice.go:49-58} 做的是
 * <pre>
 * var e *errmsg.CodeError
 * if errors.As(err, &amp;e) { code = e.Code; msg = e.Msg }
 * </pre>
 * {@code errors.As} <b>会沿 {@code Unwrap()} 链一路回溯</b>，所以被业务代码
 * {@code fmt.Errorf("...: %w", err)} 包过一层的 {@code *CodeError} 仍能被还原成原始 code/msg；
 * 匹配不到才回退 {@code ErrorServerCommon}(5001) + "系统内部错误"。
 * Java 侧对应的就是 {@link #findIn(Throwable)} —— <b>不要写成单纯的
 * {@code catch (CommentCodeError e)}</b>，那会把「包过一层的业务错误」误降级成 5001，
 * 让客户端拿到 5001 而不是真实业务码（可观察行为变了）。
 *
 * <p><b>Jackson 注解不是装饰，是行为对齐</b>：Go 的 {@code encoding/json} 只序列化导出字段，
 * {@code Error()} 是方法不是字段，<b>不会</b>出现在 JSON 里。Java {@code Throwable} 继承的
 * {@code getMessage()} / {@code getCause()} / {@code getStackTrace()} 默认都是可见 getter，
 * 会额外吐出 Go 侧根本不存在的 {@code message} / {@code cause} / {@code stackTrace} 字段。
 * 这里用 {@link JsonAutoDetect} 把继承来的 getter 全部关掉，只留 {@code code} / {@code msg}。
 */
@JsonAutoDetect(getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public class CommentCodeError extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 对应 Go 字段 {@code Code int `json:"code"`}。 */
    private final int code;

    /** 对应 Go 字段 {@code Msg string `json:"msg"`}。 */
    private final String msg;

    public CommentCodeError(int code, String msg) {
        // getMessage() 必须与 Go 的 Error() 逐字相同，运维 grep 日志依赖这个格式
        super(String.format("ErrCode:%d, Errmsg:%s", code, msg));
        this.code = code;
        this.msg = msg;
    }

    /** 对应 Go {@code func NewErrCode(code int) error}：msg 走查表。 */
    public static CommentCodeError newCode(int code) {
        return new CommentCodeError(code, CommentErrorMessages.getMsg(code));
    }

    /** 对应 Go {@code func NewErrCodeMsg(code int, msg string) error}：msg 用传入值，不查表。 */
    public static CommentCodeError newCodeMsg(int code, String msg) {
        return new CommentCodeError(code, msg);
    }

    /**
     * 对应 Go {@code errors.As(err, &amp;e)}：沿 {@code getCause()} 链回溯找第一个
     * {@link CommentCodeError}，找不到返回 {@code null}（调用方据此回退 5001）。
     *
     * <p><b>循环安全的说明</b>：{@code Throwable.initCause} 禁止自引用，
     * 正常构造的 cause 链必然终止，故不设深度上限也不会死循环；
     * 手工 {@code override getCause()} 造环属病态构造，不在防御范围。
     */
    public static CommentCodeError findIn(Throwable err) {
        for (Throwable cur = err; cur != null; cur = cur.getCause()) {
            if (cur instanceof CommentCodeError codeError) {
                return codeError;
            }
        }
        return null;
    }

    @JsonProperty("code")
    public int getCode() {
        return code;
    }

    @JsonProperty("msg")
    public String getMsg() {
        return msg;
    }
}