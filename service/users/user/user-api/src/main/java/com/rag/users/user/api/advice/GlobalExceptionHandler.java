package com.rag.users.user.api.advice;

import com.rag.common.exception.AuthenticationException;
import com.rag.common.exception.BusinessException;
import com.rag.common.logger.FatalError;
import com.rag.common.protection.GoStyleCircuitOpenException;
import com.rag.common.response.Response;
import com.rag.users.common.errmsg.ErrorCode;
import com.rag.users.common.http.GoRequiredFieldException;
import com.rag.users.user.api.service.IdentityResolver;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.nio.charset.StandardCharsets;

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
     * <b>gRPC client 熔断打开 → 1015 + HTTP 200</b>。
     *
     * <p><b>为什么必须单列，不能落进 {@link #handleAny} 的兜底分支</b>：
     * Go 侧每个 logic 的错误出口都是「捕获一切」的 {@code err != nil}，例如
     * {@code loginlogic.go:46-56}：
     * <pre>
     *   rpcResp, err := l.svcCtx.UserRpc.Login(l.ctx, rpcReq)
     *   if err != nil {
     *       st, _ := status.FromError(err)
     *       switch st.Code() {
     *       case codes.Internal: return nil, errmsg.ErrorServerCommon   // 5001
     *       default:             return nil, errmsg.CodeServerBusy      // 1015
     *       }
     *   }
     * </pre>
     * 而 Go 的客户端熔断（{@code zrpc/internal/clientinterceptors/breakerinterceptor.go}）
     * 在熔断打开时返回的是 {@code breaker.ErrServiceUnavailable} ——
     * <b>一个普通 Go error，不是 gRPC status</b>。
     * {@code status.FromError} 对非 status 错误返回 {@code codes.Unknown}
     * ⇒ 落 {@code default} ⇒ <b>1015</b>，
     * 再经 handler 的 {@code httpx.OkJson} 输出 ⇒ <b>HTTP 恒 200 + envelope</b>。
     *
     * <p>⚠️ <b>本域 9 个 logic 全部如此</b>：逐个核对
     * {@code selectavatar / getuser / logout / uploadavatar / deleteuser / updateuser /
     * getavatarhistory / register / login} 的 switch，<b>没有一个显式列 {@code codes.Unknown}</b>
     * ⇒ 熔断错误在 Go 侧<b>一律</b>落 default ⇒ 一律 1015。
     *
     * <p>RAG 侧的对应写法是 {@code catch (StatusRuntimeException e)} ——
     * <b>只捕 gRPC status</b>。而 {@link GoStyleCircuitOpenException} 刻意不是 status
     * （否则 msg 会变成 {@code "circuit breaker is open"} 而非 Go 的 {@code "服务繁忙"}），
     * 于是它<b>绕过</b> service 的 catch、也绕过 {@link #handleRPC}，
     * 最终落到 {@link #handleAny} ⇒ <b>HTTP 500 + 业务码 5001</b>。
     *
     * <p>⚠️ <b>这条漂移还有二阶后果</b>：HTTP 500 会被<b>入口</b>侧的
     * {@code GoStyleBreakerFilter} 判为失败（{@code cw.Code < 500} 才算成功），
     * 于是「客户端熔断打开」会反过来把<b>入口</b>熔断也打开，
     * 对外表现为 <b>503 空 body</b> —— 与 Go 的「200 + 1015」在 wire 上<b>完全不同</b>。
     * ⇒ 修好本条，两层熔断的联动也随之消失。
     *
     * <p>锁定用例：{@code GoStyleGrpcClientBreakerE2ETest}（断言 HTTP 200 + 业务码 1015）。
     */
    @ExceptionHandler(GoStyleCircuitOpenException.class)
    public ResponseEntity<Response> handleCircuitOpen(GoStyleCircuitOpenException e) {
        log.error("gRPC client circuit breaker is open", e);
        return ResponseEntity.status(HttpStatus.OK)
                .body(new Response(ErrorCode.CODE_SERVER_BUSY.getCode(),
                        ErrorCode.CODE_SERVER_BUSY.getMsg(), null));
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
    public ResponseEntity<String> handleUnreadableBody(HttpMessageNotReadableException e) {
        log.warn("unreadable request body : {}", e.getMessage());
        return goStylePlainTextBadRequest(e.getMessage());
    }

    /**
     * go-zero 必填字段缺失 → 400 + text/plain（DRIFT-016）。
     *
     * <p>Go 出处：{@code handler.Entries()} 拿到 {@code httpx.Parse} 的错误后调
     * {@code httpx.ErrorCtx}；两个 api 服务的 yaml 都<b>从未</b>注册
     * {@code httpx.SetErrorHandler}（service/user 全树 grep 零命中），
     * 所以一定落到 {@code rest/httpx/responses.go:doHandleError} 的
     * {@code handler == nil} 分支 → {@code http.Error(w, err.Error(), 400)}。
     *
     * <p>错误文本是 Go 的逐字复刻：{@code unmarshaler.go:1199} 的
     * {@code newInitError} 产出 {@code fmt.Errorf("field %q is not set", name)}。
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
     *
     * <p>🔴 <b>2026-10-23 修正（DRIFT-USERAPI-006）</b>：上面第 ① 条
     * 「{@code Content-Type: text/plain; charset=utf-8}」<b>此前只是注释里的一句话，
     * 代码写的是 {@code MediaType.TEXT_PLAIN}—— 它的字面量就是 {@code "text/plain"}，
     * <b>没有 charset</b>。即：<b>注释声称做的事，代码一件都没做</b>（§3.2 无出处 idiomatization）。
     *
     * <p>连带第二处：{@code ResponseEntity<String>} 的 body 由 {@code StringHttpMessageConverter}
     * 写出，它在没有 charset 时回落到 {@code DEFAULT_CHARSET = ISO_8859_1}
     * （spring-web 7.0.9 字节码核实）⇒ <b>body 按 ISO-8859-1 编码</b>，
     * 而 Go 的 {@code fmt.Fprintln} 写的是 UTF-8 字节。
     * 当前文案恰好全是 ASCII 所以看不出来，但 {@code handleUnreadableBody}
     * 会把 {@code HttpMessageNotReadableException.getMessage()}（含畸形 JSON 原文，
     * 可能带中文）直接写进 body ⇒ 那种输入下 RAG 输出乱码、Go 不乱码。
     *
     * <p><b>修法</b>：给 Content-Type 显式钉上 charset。
     * {@code StringHttpMessageConverter} 见到 {@code text/plain;charset=UTF-8}
     * 就按 UTF-8 写 body，①②同时解决。
     */
    private static ResponseEntity<String> goStylePlainTextBadRequest(String message) {
        HttpHeaders headers = new HttpHeaders();
        // 显式带 charset：既对齐 Go 的 header 字面量，也让 StringHttpMessageConverter 改用 UTF-8
        headers.setContentType(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8));
        headers.set("X-Content-Type-Options", "nosniff");
        String body = (message == null || message.isEmpty() ? "bad request" : message) + "\n";
        return new ResponseEntity<>(body, headers, HttpStatus.BAD_REQUEST);
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
