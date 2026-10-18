package com.rag.users.admin.api.internal.logic;

import com.rag.users.common.errmsg.ErrorCode;

/**
 * 对应 Go 端 Logic 的双返回值 {@code (*Resp, int)} tuple。
 *
 * <p>Go 端每个 Logic 方法签名形如：
 * <pre>
 *   func (l *LoginLogic) Login(req *types.LoginReq) (resp *types.LoginResp, code int)
 * </pre>
 *
 * <p>Java 没有内建 tuple；用这个不可变 record 承载 {@code (data, businessCode)} 对。
 * 调用方读 {@link #code()} 决定 envelope.code，读 {@link #data()} 决定 envelope.data。
 *
 * <p>L1 契约冻结：{@link #code()} 是 {@link ErrorCode} 枚举的 int 值，与 Go 端同款业务码
 * （5001 / 1001 / 1002 / 1003 / 1004 / 1006 / 1011 / 1015 / 500 / 200）。
 */
public final class LogicResult<T> {

    private final T data;
    private final int code;

    private LogicResult(T data, int code) {
        this.data = data;
        this.code = code;
    }

    public static <T> LogicResult<T> of(T data, ErrorCode code) {
        return new LogicResult<>(data, code.getCode());
    }

    public static <T> LogicResult<T> of(T data, int code) {
        return new LogicResult<>(data, code);
    }

    /** 业务码 = {@link ErrorCode#SUCCESS} 时使用。 */
    public static <T> LogicResult<T> ok(T data) {
        return new LogicResult<>(data, ErrorCode.SUCCESS.getCode());
    }

    /** 业务失败，data = null。 */
    public static <T> LogicResult<T> fail(ErrorCode code) {
        return new LogicResult<>(null, code.getCode());
    }

    public T data() { return data; }
    public int code() { return code; }
}