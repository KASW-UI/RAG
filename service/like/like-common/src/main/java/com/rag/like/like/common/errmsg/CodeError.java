package com.rag.like.like.common.errmsg;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * like 域业务错误载体 —— 对应 Go 端 {@code service/like/common/errmsg/errmsg.go:10-17}：
 *
 * <pre>
 * type CodeError struct {
 *     Code int    `json:"code"`
 *     Msg  string `json:"msg"`
 * }
 * func (e *CodeError) Error() string {
 *     return fmt.Sprintf("ErrCode:%d, Errmsg:%s", e.Code, e.Msg)
 * }
 * </pre>
 *
 * <p><b>为什么是 {@link RuntimeException} 而不是 checked exception</b>：
 * Go 的 {@code error} 在语言层面没有 checked/unchecked 之分，Service 层返回 error、
 * 上层判定。Java 侧对等物是 unchecked 异常 —— 业务判定处显式构造并抛出，
 * 由 {@link LikeGrpcError}（RPC 出口）或 like-api 的 GlobalExceptionHandler（HTTP 出口）统一转换。
 * 用 checked 会把每个调用点都拖进 {@code throws}，而 Go 侧根本没有这个成本。
 *
 * <p><b>Jackson 注解不是装饰，是行为对齐</b>：Go 的 {@code encoding/json} 只序列化导出字段，
 * {@code Message()} 是方法不是字段，<b>不会</b>出现在 JSON 里。Java {@code Throwable} 继承的
 * {@code getMessage()} / {@code getCause()} / {@code getStackTrace()} 默认都是可见 getter，
 * 会额外吐出 Go 侧根本不存在的 {@code message} / {@code cause} / {@code stackTrace} 字段。
 * 这里用 {@link JsonAutoDetect} 把继承来的 getter 全部关掉，只留 {@code code} / {@code msg}。
 */
@JsonAutoDetect(getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public class CodeError extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 对应 Go 字段 {@code Code int `json:"code"`}。 */
    private final int code;

    /** 对应 Go 字段 {@code Msg string `json:"msg"`}。 */
    private final String msg;

    public CodeError(int code, String msg) {
        // getMessage() 必须与 Go 的 Error() 逐字相同，运维 grep 日志依赖这个格式
        super(String.format("ErrCode:%d, Errmsg:%s", code, msg));
        this.code = code;
        this.msg = msg;
    }

    /** 对应 Go {@code func NewErrCode(code int) error}：msg 走查表。 */
    public static CodeError newCode(int code) {
        return new CodeError(code, LikeErrorMessages.getMsg(code));
    }

    /** 对应 Go {@code func NewErrCodeMsg(code int, msg string) error}：msg 用传入值。 */
    public static CodeError newCodeMsg(int code, String msg) {
        return new CodeError(code, msg);
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