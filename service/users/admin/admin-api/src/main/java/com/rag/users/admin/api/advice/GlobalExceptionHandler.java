package com.rag.users.admin.api.advice;

import com.rag.common.exception.BusinessException;
import com.rag.common.response.Response;
import com.rag.users.common.errmsg.ErrorCode;
import com.rag.users.admin.api.service.IdentityResolver;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器 —— 与 user-api 端 GlobalExceptionHandler 同款结构。
 *
 * <p>7 个 @ExceptionHandler：
 * <ul>
 *   <li>BusinessException → 业务码 + HTTP 200（与参考侧 envelope 语义 1:1）</li>
 *   <li>StatusRuntimeException → gRPC code 转 ErrorCode 业务码 + HTTP 200</li>
 *   <li>IdentityResolver 3 个 sentinel → 业务码 + HTTP 200</li>
 *   <li>框架级错误 → 交回 Spring 默认 400 / 405 / 415</li>
 *   <li>Exception → 5001 Internal</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Response> handleBusiness(BusinessException e) {
        int code = e.getCode();
        String msg = e.getMessage();
        log.error("BusinessException code={} msg={}", code, msg);
        return ResponseEntity.status(decideStatus(code)).body(new Response(code, msg, null));
    }

    @ExceptionHandler(StatusRuntimeException.class)
    public ResponseEntity<Response> handleRPC(StatusRuntimeException e) {
        Status.Code code = e.getStatus().getCode();
        int businessCode = mapRPCStatusToCode(code);
        String msg = ErrorCode.getMsg(businessCode);
        log.error("gRPC StatusRuntimeException code={} mapped to businessCode={} desc={}",
                code, businessCode, e.getStatus().getDescription(), e);
        return ResponseEntity.status(HttpStatus.OK).body(new Response(businessCode, msg, null));
    }

    /**
     * gRPC code → 业务码映射（与 Go 端每个 Logic 的 switch 1:1 对齐）。
     */
    private int mapRPCStatusToCode(Status.Code code) {
        if (code == Status.Code.INTERNAL) return ErrorCode.ERROR_SERVER_COMMON.getCode();   // 5001
        if (code == Status.Code.NOT_FOUND) return ErrorCode.ERROR_USER_NOT_EXIST.getCode(); // 1003
        if (code == Status.Code.ALREADY_EXISTS) return ErrorCode.ERROR_USER_EXIST.getCode(); // 1001
        if (code == Status.Code.INVALID_ARGUMENT) return ErrorCode.ERROR.getCode();          // 500
        if (code == Status.Code.PERMISSION_DENIED) return ErrorCode.ERROR_ADMIN_INVITE_CODE_WRONG.getCode(); // 1016
        if (code == Status.Code.UNAUTHENTICATED) return ErrorCode.ERROR_TOKEN_RUNTIME.getCode(); // 1006
        return ErrorCode.CODE_SERVER_BUSY.getCode();                                          // 1015
    }

    @ExceptionHandler(IdentityResolver.ErrInvalidClaimException.class)
    public ResponseEntity<Response> handleErrInvalidClaim(IdentityResolver.ErrInvalidClaimException e) {
        log.error("ErrInvalidClaim : {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.OK)
                .body(new Response(ErrorCode.ERROR_TOKEN_RUNTIME.getCode(), ErrorCode.ERROR_TOKEN_RUNTIME.getMsg(), null));
    }

    @ExceptionHandler(IdentityResolver.ErrAdminNotFoundException.class)
    public ResponseEntity<Response> handleErrAdminNotFound(IdentityResolver.ErrAdminNotFoundException e) {
        log.error("ErrAdminNotFound : {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.OK)
                .body(new Response(ErrorCode.ERROR_USER_NOT_EXIST.getCode(), ErrorCode.ERROR_USER_NOT_EXIST.getMsg(), null));
    }

    @ExceptionHandler(IdentityResolver.ErrIdentityMismatchException.class)
    public ResponseEntity<Response> handleErrIdentityMismatch(IdentityResolver.ErrIdentityMismatchException e) {
        log.error("ErrIdentityMismatch : {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.OK)
                .body(new Response(ErrorCode.ERROR_SERVER_COMMON.getCode(), ErrorCode.ERROR_SERVER_COMMON.getMsg(), null));
    }

    /**
     * 框架级错误 → 交回 Spring 默认语义。
     *
     * <p>参考侧 admin-api 同样没有针对框架异常的 {@code @ExceptionHandler}，
     * 请求体缺失 / 方法不对 / Content-Type 不对分别落在 400 / 405 / 415。
     * 若被下面的兜底 {@code Exception} 吞掉会变成 500，把客户端调用错误误报成服务端故障。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Void> handleUnreadableBody(HttpMessageNotReadableException e) {
        log.warn("unreadable request body : {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("method not supported : {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).build();
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Void> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e) {
        log.warn("media type not supported : {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).build();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Response> handleAny(Exception e) {
        log.error("unhandled exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new Response(ErrorCode.ERROR_SERVER_COMMON.getCode(), ErrorCode.ERROR_SERVER_COMMON.getMsg(), null));
    }

    /**
     * 业务码 → HTTP 状态码：<b>一律 200</b>。
     *
     * <p>与参考实现 1:1：参考侧所有 Logic 结果都经 {@code LogicResult} 走
     * {@code envelope(...)}，HTTP 恒为 200，业务成败由 body 的 {@code code} 表达；
     * 401 只由鉴权层的 Servlet Filter 写出，不经过本处理器。
     */
    private HttpStatus decideStatus(int code) {
        return HttpStatus.OK;
    }
}
