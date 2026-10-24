package com.rag.common.exception;

/**
 * RAG 通用业务异常 —— Service 层显式抛，Controller 层 GlobalExceptionHandler 统一捕获并包成 envelope。
 *
 * <p>对应 Go 端 logic 的 {@code return LogicResult.fail(errmsg.XXX)} 双返回模型：
 * <pre>
 *   Go: return nil, errmsg.ERROR_USER_EXIST
 *   Java: throw new BusinessException(1001, "用户名已存在")
 * </pre>
 *
 * <p>框架职责:
 * <ul>
 *   <li>{@code code} 直接给业务码（来自 {@code com.rag.users.common.errmsg.ErrorCode} 的 int 值），
 *       {@code GlobalExceptionHandler} 把异常包成 {@code Response(code, msg, data=null)}</li>
 *   <li>HTTP 状态码由 {@code GlobalExceptionHandler} 按 code 决定（401 / 403 / 500 / 200）</li>
 *   <li>不带 trace / 不带 metrics（metrics 由 GlobalExceptionHandler 统一处理）</li>
 * </ul>
 *
 * <p><b>设计注</b>：本类只接 {@code int} 业务码 + {@code String} 提示语，<b>不</b>耦合
 * {@code com.rag.users.common.errmsg.ErrorCode} 枚举 —— 因为 {@code common} 模块在 maven
 * 模块树里是下层，被 {@code users-common} 反向依赖；这里持引用会形成循环依赖。
 * 调用方传 {@code ErrorCode.ERROR_USER_EXIST.getCode()} + {@code ErrorCode.ERROR_USER_EXIST.getMsg()}
 * 即可。
 */
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(int code, String msg) {
        super(msg);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
