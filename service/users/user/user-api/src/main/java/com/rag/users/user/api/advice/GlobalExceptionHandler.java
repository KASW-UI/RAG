package com.rag.users.user.api.advice;

import com.rag.common.exception.AuthenticationException;
import com.rag.common.exception.BusinessException;
import com.rag.common.logger.FatalError;
import com.rag.common.response.Response;
import com.rag.users.common.errmsg.ErrorCode;
import com.rag.users.user.api.service.IdentityResolver;
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
 * 全局异常处理器 —— @RestControllerAdvice + @ExceptionHandler。
 *
 * 对应 Go: service/user/user/api/internal/handler/*.go 每个 handler 末尾的 err != nil 分支 + filter 的 401 短路。
 *
 * <p>框架职责：
 * <ul>
 *   <li>把业务异常 / gRPC 异常 / IdentityResolver sentinel 异常 / 兜底 RuntimeException 统一映射成
 *       {@code Response{code, msg, data=null}}</li>
 *   <li>HTTP 状态码：<b>鉴权层失败 = 401</b>（{@link AuthenticationException}，由拦截器抛出）；
 *       其余业务失败一律 200（业务码由 body 表达）；仅框架级错误保留 Spring 默认语义
 *       （400 / 405 / 415），未捕获异常 500</li>
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

    /**
     * <b>鉴权层失败 → HTTP 401</b>（DRIFT-004 修复）。
     *
     * <p>Spring 按「最具体」挑 handler：{@code AuthenticationException extends BusinessException}，
     * 因此本方法优先于上面的 {@link #handleBusiness}，只有拦截器抛出的鉴权失败会走这里。
     *
     * <p>Go 出处（401 已由 Go 侧源码与 identity_contract.md 双重坐实）：
     * <ul>
     *   <li>{@code rest.WithJwt} → {@code rest/handler/authhandler.go:106 unauthorized()}
     *       → {@code writer.WriteHeader(http.StatusUnauthorized)}</li>
     *   <li>{@code checkblacklistmiddleware.go:38} → {@code w.WriteHeader(http.StatusUnauthorized)}</li>
     * </ul>
     * Go 两处 body 形状不同（JWT 失败空 body / 黑名单 {@code {code:401,msg}}），
     * 本端统一成完整 envelope，属表达层差异 —— 已按 §3.4 留档。
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Response> handleAuthentication(AuthenticationException e) {
        log.warn("AuthenticationException code={} msg={}", e.getCode(), e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new Response(e.getCode(), e.getMessage(), null));
    }

    // ============== 2. gRPC 异常（Service 未捕获透传） ==============

    @ExceptionHandler(StatusRuntimeException.class)
    public ResponseEntity<Response> handleRPC(StatusRuntimeException e) {
        Status.Code code = e.getStatus().getCode();
        int businessCode = mapRPCStatusToCode(code);
        String msg = ErrorCode.getMsg(businessCode);
        log.error("gRPC StatusRuntimeException code={} mapped to businessCode={} desc={}",
                code,
                businessCode,
                e.getStatus().getDescription(),
                e);
        return ResponseEntity.status(HttpStatus.OK).body(new Response(businessCode, msg, null));
    }

    /**
     * gRPC code → 业务码映射（与 Go 端每个 Logic 的 switch 1:1 对齐）。
     * 每个 case 的具体业务码根据不同 Service 不一样，所以此处只给 default + 通用映射，
     * 业务码精确映射放在 Service 内部抛 BusinessException。
     */
    private int mapRPCStatusToCode(Status.Code code) {
        if (code == Status.Code.INTERNAL)
            return ErrorCode.ERROR_SERVER_COMMON.getCode();   // 5001
        if (code == Status.Code.NOT_FOUND)
            return ErrorCode.ERROR_USER_NOT_EXIST.getCode(); // 1003
        if (code == Status.Code.ALREADY_EXISTS)
            return ErrorCode.ERROR_USER_EXIST.getCode(); // 1001
        if (code == Status.Code.INVALID_ARGUMENT)
            return ErrorCode.ERROR.getCode();          // 500
        return ErrorCode.CODE_SERVER_BUSY.getCode();                                          // 1015
    }

    // ============== 3. IdentityResolver 5 个 sentinel exception（兜底） ==============

    @ExceptionHandler(IdentityResolver.ErrInvalidClaimException.class)
    public ResponseEntity<Response> handleErrInvalidClaim(IdentityResolver.ErrInvalidClaimException e) {
        log.error("ErrInvalidClaim : {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.OK)
                .body(new Response(ErrorCode.ERROR_TOKEN_RUNTIME.getCode(), ErrorCode.ERROR_TOKEN_RUNTIME.getMsg(), null));
    }

    @ExceptionHandler(IdentityResolver.ErrUserNotFoundException.class)
    public ResponseEntity<Response> handleErrUserNotFound(IdentityResolver.ErrUserNotFoundException e) {
        log.error("ErrUserNotFound : {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.OK)
                .body(new Response(ErrorCode.ERROR_USER_NOT_EXIST.getCode(),
                        ErrorCode.ERROR_USER_NOT_EXIST.getMsg(),
                        null));
    }

    @ExceptionHandler(IdentityResolver.ErrUserInactiveException.class)
    public ResponseEntity<Response> handleErrUserInactive(IdentityResolver.ErrUserInactiveException e) {
        log.error("ErrUserInactive : {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.OK)
                .body(new Response(ErrorCode.ERROR_USER_BANNED.getCode(),
                        ErrorCode.ERROR_USER_BANNED.getMsg(),
                        null));
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

    // ============== 4. 框架级错误：交回 Spring 默认语义（参考侧无任何 @ExceptionHandler）==============

    /**
     * 请求体不可读 / 缺失 → 400。
     *
     * <p>参考侧 user-api <b>没有任何</b> {@code @ExceptionHandler}，这类错误由 Spring 默认解析成
     * 400；若被下面的兜底 {@code Exception} 处理器吞掉就会变成 500，把客户端的请求错误
     * 误报成服务端故障（并污染错误看板）。目标侧把全部端点改成 {@code @RequestBody} 后，
     * "忘记带 body" 是最常见的调用错误，这个状态码尤其重要。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Void> handleUnreadableBody(HttpMessageNotReadableException e) {
        log.warn("unreadable request body : {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }

    /** HTTP 方法不支持 → 405。 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("method not supported : {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).build();
    }

    /** Content-Type 不支持 → 415。 */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Void> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e) {
        log.warn("media type not supported : {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).build();
    }

    // ============== 5. 致命错误：绝不转 envelope（H3 修复）==============

    /**
     * H3 修复：{@link FatalError} 必须原样抛出，<b>不得</b>被转成 200 业务 envelope。
     *
     * <p>背景：Go 版 {@code logger.go:296} 在打出 fatal_error 后无条件 {@code os.Exit(1)}，
     * 「fatal 后必须停止接流量」是硬契约。Java 版改为抛专用异常（不调 System.exit，
     * 以免绕过 Spring graceful shutdown / {@code @PreDestroy}），
     * 但如果这里不显式处理，{@link #handleAny} 会把它当成普通业务异常：
     * 打一条 "unhandled exception"、返回 {@code ERROR_SERVER_COMMON} + HTTP 200 ——
     * 请求看起来「失败了」，而<b>进程带着致命状态继续正常服务</b>，正是 Go 侧绝不会发生的情况。
     *
     * <p>本方法只做一件事：把异常抛回去，让它穿透 DispatcherServlet 冒泡到容器错误页，
     * 从而对调用方表现为「这个请求没被正常处理完」，而不是「一切正常、只是业务码不对」。
     *
     * <p><b>刻意不做 {@code System.exit(1)}</b>：终止决策权留给调用 {@code logFatal} 的位置
     * （通常是启动期 bootstrap，那时 Spring 容器未就绪，异常自然冒泡出 main → JVM 非零退出）。
     */
    @ExceptionHandler(FatalError.class)
    public void handleFatal(FatalError e) {
        // 故意不返回 ResponseEntity：让异常继续向上抛
        throw e;
    }

    // ============== 6. 兜底：所有未捕获异常 ===============

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Response> handleAny(Exception e) {
        // FatalError 已在上面被专门处理；这里若仍收到，说明有 advice 未注册 FatalError 分支，
        // 此时也必须放行而不是包成 envelope。
        if (e instanceof FatalError fatal) {
            throw fatal;
        }
        log.error("unhandled exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new Response(ErrorCode.ERROR_SERVER_COMMON.getCode(), ErrorCode.ERROR_SERVER_COMMON.getMsg(), null));
    }

    // ============== 业务码 → HTTP 状态码 ==============

    /**
     * 业务码 → HTTP 状态码：<b>一律 200</b>。
     *
     * <p>与 Go 端 {@code httpx.OkJson} 1:1：Go 侧所有 Logic 结果都经 envelope 输出，
     * HTTP 状态码恒为 200，业务成败完全由 body 里的 {@code code} 表达。
     *
     * <p><b>⚠️ 更正本方法此前那段错误论证（DRIFT-004）</b>。原文写的是：
     * <blockquote>「只有两个 Servlet Filter 的鉴权短路才写 401，而那属于鉴权层语义、不经过本处理器」</blockquote>
     * 在本工程里<b>不存在</b> Servlet Filter 鉴权短路 —— 鉴权是
     * {@code JwtAuthInterceptor}（{@code HandlerInterceptor}），它<b>抛异常</b>，
     * 必然经过本处理器。于是鉴权失败被降级成 HTTP 200。
     *
     * <p>现在鉴权层改抛 {@link AuthenticationException}，由
     * {@link #handleAuthentication} 单独产出 401，<b>不再依赖本方法按业务码分流</b> ——
     * 1006 在鉴权层与 Service 层都可能出现，按码分流会把两层重新搅在一起。
     *
     * <p>本方法因此可以安全地保持「无条件 200」。
     */
    private HttpStatus decideStatus(int code) {
        return HttpStatus.OK;
    }
}
