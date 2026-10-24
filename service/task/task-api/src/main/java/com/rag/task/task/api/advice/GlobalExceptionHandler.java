package com.rag.task.task.api.advice;

import com.rag.common.http.GoStyleRouterErrorResponses;
import com.rag.common.protection.GoStyleCircuitOpenException;
import com.rag.task.task.api.dto.GetTaskReq;
import io.grpc.Status;
import io.grpc.StatusException;
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
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 本域<b>唯一</b>的错误响应出口 —— 对应 go-zero v1.10.2 {@code rest/httpx/responses.go:142-173}（{@code doHandleError}）
 * 在 {@code errorHandler == nil} 时的分支。
 *
 * <h2>🔴 前提：task-api <b>没有注册 error handler</b>，本类的形状由此唯一确定</h2>
 * <pre>
 *   service/task/api/task.go（全文 37 行）—— 没有 httpx.SetErrorHandler
 *   service/like/api/likecenter.go:48      httpx.SetErrorHandler(...)      ← 有
 *   service/hot/api/hotcenter.go:53        httpx.SetErrorHandler(...)      ← 有
 *   service/message/api/messagecenter.go:53 httpx.SetErrorHandler(...)     ← 有
 *   service/article/api/article.go（42 行）  —— 没有                            ← 与本域同类
 * </pre>
 * 后果直接落在 {@code doHandleError}：
 * <pre>
 *   handler == nil
 *     ⇒ if errcode.IsGrpcError(err) { http.Error(w, err.Error(), errcode.CodeFromGrpcError(err)) }
 *     ⇒ else                        { http.Error(w, err.Error(), http.StatusBadRequest) }
 * </pre>
 * 而 Go 的 {@code http.Error}（net/http/server.go）只做四件事：
 * <pre>
 *   h.Set("Content-Type", "text/plain; charset=utf-8")
 *   h.Set("X-Content-Type-Options", "nosniff")
 *   w.WriteHeader(code)
 *   fmt.Fprintln(w, error)        // ← 尾随一个 "\n"
 * </pre>
 * ⇒ <b>本模块所有失败都是「真实 HTTP 状态码 + 裸 text/plain 文案」，不是 200 + 信封。</b>
 * 照抄 like / message / hot 三个域的 {@code GlobalExceptionHandler}（都返回
 * {@code 200 + {code,msg,data}}）到本模块就是<b>整条错误路径的漂移</b>。
 *
 * <h3>五种响应形状，各自来源不同，不要互相「统一」</h3>
 * <table border="1">
 *   <caption>本模块的错误形状</caption>
 *   <tr><th>形状</th><th>来源</th><th>方法</th></tr>
 *   <tr><td>状态码 + text/plain</td><td>handler 内部错误（本类主体）</td><td>{@link #plainText}</td></tr>
 *   <tr><td>404 + text/plain 有 body</td><td>go-zero router</td><td>{@link GoStyleRouterErrorResponses}</td></tr>
 *   <tr><td>405 + 裸状态码无 body</td><td>go-zero router</td><td>{@link GoStyleRouterErrorResponses}</td></tr>
 *   <tr><td>413 + 裸状态码无 body</td><td>go-zero MaxBytesHandler</td><td>{@code com.rag.common.protection}</td></tr>
 *   <tr><td>499 + "Request Timeout"</td><td>observability 超时分支</td><td>{@code com.rag.common.observability}</td></tr>
 * </table>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Go {@code http.Error} 的 Content-Type。<b>分号后有空格</b>，不要用 {@code MediaType.TEXT_PLAIN}。 */
    private static final String TEXT_PLAIN_UTF8 = "text/plain; charset=utf-8";

    // ============== 1. gRPC 状态码 → HTTP 状态码 ==============

    /**
     * go-zero v1.10.2 {@code rest/httpx/responses.go:150-153} 的 gRPC 分支 ——
     * {@code http.Error(w, err.Error(), errcode.CodeFromGrpcError(err))}。
     *
     * <p>⚠️ <b>不是</b> user-api 的 {@code mapRPCStatusToCode}（那是 5 分支的<b>业务码</b>映射，
     * 产出 {@code {code:1003}} 信封）。本域要的是 go-zero {@code rest/internal/errcode/grpc.go:12-42}
     * 的 <b>15 分支 HTTP 状态码</b>映射，两者形状与来源都不同。
     *
     * <p>⚠️ Java 侧用「是否 {@link StatusRuntimeException} / {@link StatusException}」判别，
     * 对应 Go 的 {@code errcode.IsGrpcError}（判 {@code GRPCStatus()} 方法存在）。
     */
    @ExceptionHandler({StatusRuntimeException.class, StatusException.class})
    public ResponseEntity<byte[]> handleGrpcStatus(Exception e) {
        Status status = (e instanceof StatusRuntimeException sre)
                ? sre.getStatus()
                : ((StatusException) e).getStatus();
        HttpStatus http = httpStatusOf(status.getCode());
        LOG.warn("task-rpc call failed: grpcCode={} http={} desc={}",
                status.getCode(), http.value(), status.getDescription());
        return plainText(http, goStatusText(status));
    }

    /**
     * Go {@code errcode.CodeFromGrpcError}（{@code rest/internal/errcode/grpc.go:12-42}）
     * 的逐分支复刻。<b>分支与返回值都是 1:1</b>，顺序也照抄，便于逐行比对。
     */
    private static HttpStatus httpStatusOf(Status.Code code) {
        switch (code) {
            case OK:
                return HttpStatus.OK;                                       // :15-16
            case INVALID_ARGUMENT:
            case FAILED_PRECONDITION:
            case OUT_OF_RANGE:
                return HttpStatus.BAD_REQUEST;                             // :17-18
            case UNAUTHENTICATED:
                return HttpStatus.UNAUTHORIZED;                            // :19-20
            case PERMISSION_DENIED:
                return HttpStatus.FORBIDDEN;                               // :21-22
            case NOT_FOUND:
                return HttpStatus.NOT_FOUND;                               // :23-24
            case CANCELLED:
                return HttpStatus.REQUEST_TIMEOUT;                         // :25-26
            case ALREADY_EXISTS:
            case ABORTED:
                return HttpStatus.CONFLICT;                                // :27-28
            case RESOURCE_EXHAUSTED:
                return HttpStatus.TOO_MANY_REQUESTS;                       // :29-30
            case INTERNAL:
            case DATA_LOSS:
            case UNKNOWN:
                return HttpStatus.INTERNAL_SERVER_ERROR;                   // :31-32
            case UNIMPLEMENTED:
                return HttpStatus.NOT_IMPLEMENTED;                         // :33-34
            case UNAVAILABLE:
                return HttpStatus.SERVICE_UNAVAILABLE;                     // :35-36
            case DEADLINE_EXCEEDED:
                return HttpStatus.GATEWAY_TIMEOUT;                         // :37-38
            default:
                // :40-41 的兜底。Status.Code 只有上面 17 个值，走不到；保留是为了
                // 将来 grpc-java 新增码时不至于漏掉映射（那时 Go 侧同样是 500）。
                return HttpStatus.INTERNAL_SERVER_ERROR;
        }
    }

    /**
     * 把 grpc-java 的 status 还原成 <b>grpc-go 的 {@code err.Error()} 文本</b>。
     *
     * <p>grpc-go（{@code status/status.go}）：
     * <pre>
     *   func (e *Error) Error() string {
     *       return fmt.Sprintf("rpc error: code = %s desc = %s", codes.Code(e.s.Code()), e.s.Message())
     *   }
     * </pre>
     * grpc-java 的 {@code StatusRuntimeException} <b>没有</b>覆写 {@code getMessage()}，
     * 拿到的是 {@code io.grpc.Status: Status{code=..., description=..., cause=...}} ——
     * <b>格式与 Go 完全不同</b>。body 是客户端可见字节，必须自己拼。
     *
     * <p>码名用 grpc-go 的 CamelCase（{@code codes.Code.String()}），不是 grpc-java 的
     * {@code SCREAMING_SNAKE}（{@code Status.Code.name()}）。
     */
    private static String goStatusText(Status status) {
        return "rpc error: code = " + goCodeName(status.getCode())
                + " desc = " + (status.getDescription() == null ? "" : status.getDescription());
    }

    /** grpc-go {@code codes.Code.String()} 的 CamelCase 名 → grpc-java {@link Status.Code}。 */
    private static String goCodeName(Status.Code code) {
        switch (code) {
            case OK:                  return "OK";
            case CANCELLED:           return "Canceled";
            case UNKNOWN:             return "Unknown";
            case INVALID_ARGUMENT:    return "InvalidArgument";
            case DEADLINE_EXCEEDED:   return "DeadlineExceeded";
            case NOT_FOUND:           return "NotFound";
            case ALREADY_EXISTS:      return "AlreadyExists";
            case PERMISSION_DENIED:   return "PermissionDenied";
            case RESOURCE_EXHAUSTED:  return "ResourceExhausted";
            case FAILED_PRECONDITION: return "FailedPrecondition";
            case ABORTED:             return "Aborted";
            case OUT_OF_RANGE:        return "OutOfRange";
            case UNIMPLEMENTED:       return "Unimplemented";
            case INTERNAL:            return "Internal";
            case UNAVAILABLE:         return "Unavailable";
            case DATA_LOSS:           return "DataLoss";
            case UNAUTHENTICATED:     return "Unauthenticated";
            default:                  return code.name();
        }
    }

    // ============== 2. gRPC client 熔断打开 ==============

    /**
     * Go：熔断打开时 {@code breaker.DoWithAcceptableCtx} 返回
     * {@code breaker.ErrServiceUnavailable = errors.New("circuit breaker is open")}
     * —— 一个<b>普通 error</b>，{@code IsGrpcError} 为 <b>false</b> ⇒
     * {@code http.Error(w, "circuit breaker is open", 400)}。
     *
     * <p>⚠️ <b>不能落进 {@link #handleAny} 的兜底就完事</b>——本域的兜底恰好也是 400，
     * 看着能蒙对，但那样就<b>丢掉了 Go 的确切文案</b>。这里单列，锁住
     * {@code GoStyleCircuitOpenException#getMessage()} 就是 Go 的
     * {@code breaker.go:19} 那个常量。
     */
    @ExceptionHandler(GoStyleCircuitOpenException.class)
    public ResponseEntity<byte[]> handleCircuitOpen(GoStyleCircuitOpenException e) {
        LOG.warn("task-rpc client circuit breaker is open: {}", e.getMessage());
        return plainText(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    // ============== 3. 请求体解析失败 ==============

    /**
     * Go {@code gettaskhandler.go:21-24}：{@code decoder.Decode(&req)} 出错 ⇒
     * {@code httpx.ErrorCtx} ⇒ <b>400</b> + 该错误的 {@code Error()}。
     *
     * <p>覆盖两类：
     * <ul>
     *   <li><b>空 body</b> ⇒ Go 得到 {@code io.EOF}（文案 "EOF"）；
     *       Spring 侧是 {@code HttpRequestBodyRequiredException}
     *       （{@code HttpMessageNotReadableException} 的子类）⇒ 形状一致、文案不同。</li>
     *   <li><b>{@code id} 既不是字符串也不是数字</b> ⇒ Go 得到
     *       {@code "id must be a string or integer"}（{@code types.go:35}）。
     *       这里<b>沿 cause 链</b>把 {@link GetTaskReq.BadIdTypeException} 捞出来，
     *       保住 Go 的<b>确切文案</b>——直接用 {@code e.getMessage()} 拿到的是
     *       Jackson 的 "JSON parse error: ..." 包装文案。</li>
     * </ul>
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<byte[]> handleUnreadableBody(HttpMessageNotReadableException e) {
        String message = findGoText(e, GetTaskReq.BadIdTypeException.class);
        if (message == null) {
            LOG.warn("unreadable request body: {}", e.getMessage());
            message = String.valueOf(e.getMessage());
        }
        return plainText(HttpStatus.BAD_REQUEST, message);
    }

    /**
     * 媒体类型不被支持 —— <b>正常情况下不可达</b>。
     *
     * <p>{@code config/TaskApiJsonBodyConfig} 的转换器认领 {@code MediaType.ALL}（读侧），
     * 所以 Go 能接受的任何 Content-Type 都不会触发 415。本方法只是<b>兜底</b>：
     * 万一该转换器被摘掉，落到 Spring 默认的 415 会是一条<b>新增</b>的对外契约；
     * 映成 400 text/plain 则仍落在 Go「解析失败 ⇒ 400」的范围内。
     * <p>⚠️ 它<b>不能</b>证明转换器装上了 —— 那由
     * {@code TaskApiContentTypeAgnosticBodyTest} 端点级断言负责（AGENTS.md §3.3：
     * 「断言 Bean 存在 ≠ 断言机制生效」）。
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<byte[]> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e) {
        LOG.warn("media type not supported (converter should have claimed it): {}", e.getMessage());
        return plainText(HttpStatus.BAD_REQUEST, String.valueOf(e.getMessage()));
    }

    // ============== 4. 路由层 ==============

    /**
     * 404 —— go-zero {@code router/patrouter.go:73} → {@code http.NotFound(w, r)}。
     * <b>有</b> body（{@code "404 page not found\n"}）+ text/plain + nosniff。
     */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<byte[]> handleNotFound(Exception e) {
        LOG.debug("no handler for {} : {}", e.getMessage(), e.toString());
        return GoStyleRouterErrorResponses.notFound(e.getMessage());
    }

    /**
     * 405 —— go-zero {@code router/patrouter.go:79-82}：<b>裸状态码</b>，
     * 只有 {@code Allow} 头，无 body、无 Content-Type。
     *
     * <p>本模块只有一条路由且只有 POST ⇒ {@code Allow} 恒为 {@code POST}，
     * 不涉及 Go 侧「遍历 map 得来的顺序不确定」问题（见共享实现类注释）。
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Void> handleMethodNotAllowed(HttpRequestMethodNotSupportedException e) {
        LOG.debug("method not supported: {}", e.getMessage());
        return GoStyleRouterErrorResponses.methodNotAllowed(
                e.getSupportedHttpMethods() == null
                        ? List.of()
                        : e.getSupportedHttpMethods().stream().map(Object::toString).toList());
    }

    // ============== 5. 兜底 ==============

    /**
     * go-zero v1.10.2 {@code rest/httpx/responses.go:154-155}：<b>任何</b>非 gRPC 错误都是
     * {@code http.Error(w, err.Error(), http.StatusBadRequest)} ⇒ <b>400</b>。
     *
     * <p>⚠️ <b>这里是 400，不是 500</b>。这是本域最容易照抄错的一处：别的域的兜底写
     * {@code 500} 或「200 + 5001 业务码」，因为那些域注册了 error handler；
     * 本域没注册，Go 的兜底<b>字面量</b>就是 {@code http.StatusBadRequest}。
     * 把它改成 500 会让「task-api 服务端内部错误」多出一个 Go 从未返回过的状态码。
     *
     * <p>覆盖：{@code GetTaskReq#userIdOrThrow} 的 {@code "user_id invalid"}、
     * {@code TaskGetService} 的 nil 响应兜底，以及任何意料之外的异常。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<byte[]> handleAny(Exception e) {
        LOG.warn("unhandled exception -> 400 (go-zero v1.10.2 rest/httpx/responses.go:155): {}", e.toString());
        return plainText(HttpStatus.BAD_REQUEST, String.valueOf(e.getMessage()));
    }

    // ============== 工具 ==============

    /**
     * Go {@code http.Error} 的逐字等价物。
     *
     * @param status  HTTP 状态码
     * @param message {@code err.Error()}；<b>尾随的 {@code "\n"} 由本方法补</b>
     *                （Go 用 {@code fmt.Fprintln}），调用方不要自己加
     */
    private static ResponseEntity<byte[]> plainText(HttpStatus status, String message) {
        byte[] body = ((message == null ? "" : message) + "\n").getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.status(status)
                .header(HttpHeaders.CONTENT_TYPE, TEXT_PLAIN_UTF8)
                .header("X-Content-Type-Options", "nosniff")
                .body(body);
    }

    /**
     * 在异常链里找第一个 {@code type} 实例并返回它的 message；找不到返回 {@code null}。
     *
     * <p>用于把 Jackson 包装后的异常还原成 Go 的<b>原始文案</b>（见 {@link #handleUnreadableBody}）。
     */
    private static <T extends Throwable> String findGoText(Throwable e, Class<T> type) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (type.isInstance(t)) {
                return t.getMessage();
            }
            if (t.getCause() == t) {
                break; // 自引用，防止极端实现导致的死循环
            }
        }
        return null;
    }
}