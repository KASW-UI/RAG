package com.rag.users.admin.api.advice;

import com.rag.common.exception.AuthenticationException;
import com.rag.common.exception.BusinessException;
import com.rag.common.logger.FatalError;
import com.rag.common.response.Response;
import com.rag.users.common.errmsg.ErrorCode;
import com.rag.users.common.http.GoRequiredFieldException;
import com.rag.users.admin.api.service.IdentityResolver;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
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
    public ResponseEntity<Response> handleAuthentication(
            AuthenticationException e, jakarta.servlet.http.HttpServletResponse response) {
        log.warn("admin AuthenticationException code={} msg={}", e.getCode(), e.getMessage());
        // 显式钉 401 — 防 gap：若某日 HttpMiddleware status 转发再次失效，这里确保状态码提交
        // （response 实际就是 HttpMiddleware 的 ORW wrapper，传到此处时仍可能未被 commit）。
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
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
    public ResponseEntity<String> handleUnreadableBody(HttpMessageNotReadableException e) {
        log.warn("unreadable request body : {}", e.getMessage());
        return goStylePlainTextBadRequest(e.getMessage());
    }

    /**
     * go-zero 必填字段缺失 → 400 + text/plain（DRIFT-016）。
     *
     * <p>Go 出处：{@code handler.Entries()} 拿到 {@code httpx.Parse} 的错误后调
     * {@code httpx.ErrorCtx}；admin 服务的 yaml <b>从未</b>注册
     * {@code httpx.SetErrorHandler}（service/user 全树 grep 零命中），
     * 所以一定落到 {@code rest/httpx/responses.go:doHandleError} 的
     * {@code handler == nil} 分支 → {@code http.Error(w, err.Error(), 400)}。
     *
     * <p>这条同时把 §3.4.3 N-6 的「缺失 uid → 1017 + envelope」改成与 Go 一致的
     * {@code 400 + text/plain}（用户 2026-10-21 对 DRIFT-016 的拍板）。
     * <b>N-6 仍保留的部分</b>：uid <b>存在但非法</b>（{@code uid=abc}）与
     * <b>uid=0</b> 仍走 {@code AdminApiSupport.parseUid} → 1017，分别对应
     * Go 的 {@code strconv.ParseInt} 400 与 {@code 1003 用户不存在}。
     *
     * <p><b>为什么刻意不并进 {@link #handleBusiness}</b>：{@code BusinessException}
     * 会被渲染成 {@code HTTP 200 + envelope}，而这里是 {@code HTTP 400 + text/plain}。
     * AGENTS.md §3.4.2 N-1 的结论是<b>层次由类型承载，不靠业务码推断</b>。
     */
    @ExceptionHandler(GoRequiredFieldException.class)
    public ResponseEntity<String> handleGoRequiredField(GoRequiredFieldException e) {
        log.warn("go-style required field missing : field={}", e.getField());
        return goStylePlainTextBadRequest(e.getMessage());
    }

    /**
     * Go {@code http.Error(w, msg, 400)} 的逐字等价实现（DRIFT-006）。
     *
     * <p>Go 出处：go-zero v1.10.2 {@code rest/httpx/responses.go:doHandleError} 的
     * {@code handler == nil} 分支。{@code service/user} 全树<b>从未</b>调用
     * {@code httpx.SetErrorHandler}（grep 零命中），故 {@code httpx.Parse} 失败一定落到
     * {@code http.Error(w, err.Error(), http.StatusBadRequest)}。
     *
     * <p>Go {@code net/http.Error} 的四件事必须都做：
     * <ol>
     *   <li>{@code Content-Type: text/plain; charset=utf-8}</li>
     *   <li>{@code X-Content-Type-Options: nosniff}</li>
     *   <li>写入 HTTP 状态码</li>
     *   <li>{@code fmt.Fprintln(w, error)} —— <b>带一个尾随换行</b></li>
     * </ol>
     *
     * <p><b>对齐边界（刻意）</b>：body 的<b>形状</b>（text/plain + 非空文本 + 尾换行）对齐；
     * 文本<b>内容</b>不对齐、也不试图对齐 —— Go 侧是 go-zero 的解析错误文本，
     * Java 侧是 Spring 的 {@code HttpMessageNotReadableException.getMessage()}，
     * 两者来自不同框架，强行凑字面只会制造一处假的「1:1」。
     *
     * <p>配套改动见 {@code GlobalResponseAdvice#beforeBodyWrite}：非 JSON 响应不再被包 envelope。
     */
    private static ResponseEntity<String> goStylePlainTextBadRequest(String message) {
        HttpHeaders headers = new HttpHeaders();
        // 🔴 2026-10-23 双侧活体对拍（真实 Tomcat，admin-api 58889 vs Go admin-api 18889）实测：
        //   Go  : Content-Type: text/plain; charset=utf-8
        //   RAG : Content-Type: text/plain            ← 少了 charset 参数
        // Go 出处：net/http.Error 的实现（Go 1.x）—— `h.Set("Content-Type", "text/plain; charset=utf-8")`
        //   是**逐字**写死的字符串，不是由框架推导出来的。
        //
        // ⚠️ 为什么不用 MediaType.TEXT_PLAIN：Spring 会把它渲染成 `text/plain`（无 charset），
        //   而 `new MediaType("text","plain",UTF_8)` 渲染成 `text/plain;charset=UTF-8`
        //   （**无空格 + 大写 UTF-8**）—— 与 Go 的 `text/plain; charset=utf-8` 仍不是逐字节相同。
        //   ⇒ 直接写原始字符串，才是「与 Go 一致」而不是「与 Go 大致一致」。
        //
        // ⚠️ 严重度 LOW：400 的 body 全是 ASCII（`field "x" is not set` / Jackson 的解析文本），
        //   任何 charset 解释都得到同一串字节 ⇒ **无字节级歧义**，只是响应头与 Go 不逐字相同。
        //   ⚠️ 现有守门脚本 `tools/difftest/check-plaintext400-headers.sh` 只断言
        //   「Content-Type 含 text/plain」，**抓不到 charset 的差异** ⇒ 本条需要独立守门。
        headers.set(HttpHeaders.CONTENT_TYPE, "text/plain; charset=utf-8");
        headers.set("X-Content-Type-Options", "nosniff");
        String body = (message == null || message.isEmpty() ? "bad request" : message) + "\n";
        return new ResponseEntity<>(body, headers, HttpStatus.BAD_REQUEST);
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
