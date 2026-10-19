package com.rag.users.user.api.advice;

import com.rag.common.exception.BusinessException;
import com.rag.common.response.Response;
import com.rag.users.common.errmsg.ErrorCode;
import com.rag.users.user.api.service.IdentityResolver;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器 —— @RestControllerAdvice + @ExceptionHandler。
 *
 * 对应 Go: service/user/user/api/internal/handler/*.go 每个 handler 末尾的 err != nil 分支 + filter 的 401 短路。
 *
 * <p>框架职责：
 * <ul>
 *   <li>把业务异常 / gRPC 异常 / IdentityResolver sentinel 异常 / 兜底 RuntimeException 统一映射成
 *       {@code Response{code, msg, data=null}}</li>
 *   <li>HTTP 状态码：401（token 失效）/ 500（业务失败 / 系统错）</li>
 *   <li>取代了 Go 端 LogicResult 抛业务码模式 + handler 层 try/catch + filter 短路的三段式</li>
 * </ul>
 *
 * <p>L1 wire 契约冻结：响应体 1:1 = Go 端 envelope {@code {code, msg, data=null}}；
 * 业务码到 ErrorCode 的映射与 Go 端 switch 完全一致。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ============== 1. 业务异常（Service 主动抛） ==============

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Response> handleBusiness(BusinessException e) {
        int code = e.getCode();
        String msg = e.getMessage();
        log.error("BusinessException code={} msg={}", code, msg);
        HttpStatus status = decideStatus(code);
        return ResponseEntity.status(status).body(new Response(code, msg, null));
    }

    // ============== 2. gRPC 异常（Service 未捕获透传） ==============

    @ExceptionHandler(StatusRuntimeException.class)
    public ResponseEntity<Response> handleRPC(StatusRuntimeException e) {
        Status.Code code = e.getStatus().getCode();
        int businessCode = mapRPCStatusToCode(code);
        String msg = ErrorCode.getMsg(businessCode);
        log.error("gRPC StatusRuntimeException code={} mapped to businessCode={} desc={}", code, businessCode, e.getStatus().getDescription(), e);
        return ResponseEntity.status(HttpStatus.OK).body(new Response(businessCode, msg, null));
    }

    /**
     * gRPC code → 业务码映射（与 Go 端每个 Logic 的 switch 1:1 对齐）。
     * 每个 case 的具体业务码根据不同 Service 不一样，所以此处只给 default + 通用映射，
     * 业务码精确映射放在 Service 内部抛 BusinessException。
     */
    private int mapRPCStatusToCode(Status.Code code) {
        if (code == Status.Code.INTERNAL) return ErrorCode.ERROR_SERVER_COMMON.getCode();   // 5001
        if (code == Status.Code.NOT_FOUND) return ErrorCode.ERROR_USER_NOT_EXIST.getCode(); // 1003
        if (code == Status.Code.ALREADY_EXISTS) return ErrorCode.ERROR_USER_EXIST.getCode(); // 1001
        if (code == Status.Code.INVALID_ARGUMENT) return ErrorCode.ERROR.getCode();          // 500
        return ErrorCode.CODE_SERVER_BUSY.getCode();                                          // 1015
    }

    // ============== 3. IdentityResolver 5 个 sentinel exception（兜底） ==============

    @ExceptionHandler(IdentityResolver.ErrInvalidClaimException.class)
    public ResponseEntity<Response> handleErrInvalidClaim(IdentityResolver.ErrInvalidClaimException e) {
        log.error("ErrInvalidClaim : {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new Response(ErrorCode.ERROR_TOKEN_RUNTIME.getCode(), ErrorCode.ERROR_TOKEN_RUNTIME.getMsg(), null));
    }

    @ExceptionHandler(IdentityResolver.ErrUserNotFoundException.class)
    public ResponseEntity<Response> handleErrUserNotFound(IdentityResolver.ErrUserNotFoundException e) {
        log.error("ErrUserNotFound : {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.OK)
                .body(new Response(ErrorCode.ERROR_USER_NOT_EXIST.getCode(), ErrorCode.ERROR_USER_NOT_EXIST.getMsg(), null));
    }

    @ExceptionHandler(IdentityResolver.ErrUserInactiveException.class)
    public ResponseEntity<Response> handleErrUserInactive(IdentityResolver.ErrUserInactiveException e) {
        log.error("ErrUserInactive : {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.OK)
                .body(new Response(ErrorCode.ERROR_USER_BANNED.getCode(), ErrorCode.ERROR_USER_BANNED.getMsg(), null));
    }

    @ExceptionHandler(IdentityResolver.ErrStatusUnavailableException.class)
    public ResponseEntity<Response> handleErrStatusUnavailable(IdentityResolver.ErrStatusUnavailableException e) {
        log.error("ErrStatusUnavailable : {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.OK)
                .body(new Response(ErrorCode.CODE_SERVER_BUSY.getCode(), ErrorCode.CODE_SERVER_BUSY.getMsg(), null));
    }

    @ExceptionHandler(IdentityResolver.ErrIdentityMismatchException.class)
    public ResponseEntity<Response> handleErrIdentityMismatch(IdentityResolver.ErrIdentityMismatchException e) {
        log.error("ErrIdentityMismatch : {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.OK)
                .body(new Response(ErrorCode.ERROR_SERVER_COMMON.getCode(), ErrorCode.ERROR_SERVER_COMMON.getMsg(), null));
    }

    // ============== 4. 兜底：所有未捕获异常 ==============

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Response> handleAny(Exception e) {
        log.error("unhandled exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new Response(ErrorCode.ERROR_SERVER_COMMON.getCode(), ErrorCode.ERROR_SERVER_COMMON.getMsg(), null));
    }

    // ============== 业务码 → HTTP 状态码 ==============

    /**
     * 业务码 → HTTP 状态码。
     * 1004 (Token不存在) / 1006 (Token已过期) / 1009 (未登录) → 401；
     * 其它 → 200（业务码本身就告诉客户端结果）。
     */
    private HttpStatus decideStatus(int code) {
        if (code == ErrorCode.ERROR_TOKEN_NOT_EXIST.getCode()   // 1004
                || code == ErrorCode.ERROR_TOKEN_RUNTIME.getCode()  // 1006
                || code == ErrorCode.ERROR_USER_NO_LOGIN.getCode()) { // 1009
            return HttpStatus.UNAUTHORIZED;
        }
        return HttpStatus.OK;
    }
}
