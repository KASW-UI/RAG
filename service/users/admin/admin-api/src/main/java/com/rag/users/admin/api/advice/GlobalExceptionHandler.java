package com.rag.users.admin.api.advice;

import com.rag.common.exception.AuthenticationException;
import com.rag.common.exception.BusinessException;
import com.rag.common.logger.FatalError;
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

    /**
     * <b>鉴权层失败 → HTTP 401</b>（DRIFT-004 修复，与 user-api 同款）。
     *
     * <p>Spring 挑最具体的 handler，故本方法优先于 {@link #handleBusiness}。
     *
     * <p>Go 出处（admin 侧与 user 侧同构）：
     * {@code rest.WithJwt} → {@code rest/handler/authhandler.go:106 unauthorized()}
     * → {@code WriteHeader(http.StatusUnauthorized)}；
     * {@code admin/api/internal/middleware/checkblacklistmiddleware.go:38} 同。
     * 两处都是 401；body 形状 Go 自身不一致，本端统一成完整 envelope（已按 §3.4 留档）。
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Response> handleAuthentication(AuthenticationException e) {
        log.warn("admin AuthenticationException code={} msg={}", e.getCode(), e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new Response(e.getCode(), e.getMessage(), null));
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

    /**
     * H3 修复：{@link FatalError} 必须原样抛出，<b>不得</b>被转成 200 业务 envelope。
     *
     * <p>Go 版 {@code logger.go:296} 在打出 fatal_error 后无条件 {@code os.Exit(1)}，
     * 「fatal 后必须停止接流量」是硬契约。Java 版改为抛专用异常（不调 System.exit，
     * 以免绕过 Spring graceful shutdown / {@code @PreDestroy}），
     * 但若不在此显式处理，{@link #handleAny} 会把它当普通业务异常包成 envelope，
     * 进程带着致命状态继续服务 —— Go 侧绝不会发生。
     *
     * <p>刻意不做 {@code System.exit(1)}：终止决策权留给调用 {@code logFatal} 的位置。
     */
    @ExceptionHandler(FatalError.class)
    public void handleFatal(FatalError e) {
        // 故意不返回 ResponseEntity：让异常继续向上抛
        throw e;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Response> handleAny(Exception e) {
        // FatalError 已被专门处理；此处兜底再放行一次，防止 advice 未注册该分支时静默吞掉
        if (e instanceof FatalError fatal) {
            throw fatal;
        }
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
