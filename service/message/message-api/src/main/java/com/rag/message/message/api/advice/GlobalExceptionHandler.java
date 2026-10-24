package com.rag.message.message.api.advice;

import com.rag.common.exception.AuthenticationException;
import com.rag.common.http.GoStyleRouterErrorResponses;
import com.rag.common.exception.BusinessException;
import com.rag.common.logger.FatalError;
import com.rag.common.response.Response;
import com.rag.message.message.common.errmsg.MessageErrorCode;
import com.rag.message.message.common.errmsg.MessageErrorMessages;
import com.rag.common.protection.GoStyleCircuitOpenException;
import com.rag.users.common.http.GoRequiredFieldException;
import io.grpc.Status;
import io.grpc.StatusException;
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
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理器 —— 对应 Go 端 {@code messagecenter.go} 里注册的 {@code httpx.SetErrorHandler}。
 *
 * <p><b>Go 出处（逐字）</b>：
 * <pre>
 * httpx.SetErrorHandler(func(err error) (int, interface{}) {
 *     code := messagecommon.ErrorServerCommon            // 5001
 *     msg  := messagecommon.GetErrMsg(code)              // "internal server error"
 *     var codeErr *messagecommon.CodeError
 *     if errors.As(err, &codeErr) {                      // errors.As：可穿透包装
 *         code = codeErr.Code
 *         msg  = codeErr.Msg
 *     }
 *     return http.StatusOK, &response.Response{Code: code, Msg: msg, Data: nil}
 * })
 * </pre>
 *
 * <p><b>⚠️ 与 user-api / admin-api 的关键差异：本服务把所有错误都压成 HTTP 200。</b>
 * <p>Go 侧 {@code service/user/**} 的两个 api <b>从未</b>调用 {@code httpx.SetErrorHandler}
 * （全树 grep 零命中），于是 {@code httpx.ErrorCtx} 落到 go-zero 默认分支
 * {@code http.Error(w, err.Error(), 400)} → <b>HTTP 400 + text/plain</b>；
 * 而 <b>message api 注册了</b>，默认分支被整个替换掉 ⇒
 * <b>包括 {@code httpx.Parse} 的解析失败在内，一切都是 HTTP 200 + envelope。</b>
 *
 * <p>具体差异（照抄 user-api 的 400 text/plain 会直接构成行为回归）：
 * <table border="1">
 *   <caption>解析失败的两条截然不同的出口</caption>
 *   <tr><th>场景</th><th>Go message api（真源）</th><th>Go user api / RAG user-api</th></tr>
 *   <tr>
 *     <td>body 非法 JSON</td>
 *     <td rowspan="3"><b>200</b> + {@code {code:5001,msg:"internal server error",data:null}}</b></td>
 *     <td rowspan="3"><b>400</b> + text/plain</td>
 *   </tr>
 *   <tr><td>必填字段缺失</td></tr>
 *   <tr><td>标量类型不匹配</td></tr>
 * </table>
 *
 * <p>换句话说，message api 对客户端的契约是
 * <b>「除鉴权失败外，HTTP 状态码恒为 200，成败全在 body 的 code 里」</b>。
 * 本类的每一个 handler 都在维持这条契约。
 *
 * <p><b>鉴权失败是唯一的例外，且 body 为空</b>：Go
 * {@code rest/handler/authhandler.go:106 unauthorized()} 只调
 * {@code writer.WriteHeader(http.StatusUnauthorized)}，不写任何字节。
 * 与 N-1（users 域把两种 401 body 收敛成完整 envelope）不同，
 * message 域只有<b>一条</b>鉴权路径，Go 自身不存在不一致，因此没有需要收敛的分歧，
 * 这里逐字还原成 <b>401 + 空 body</b>。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ============== 1. 鉴权失败：401 + 空 body ==============

    /**
     * Go {@code unauthorized()} 的逐字等价物：<b>401，空 body</b>。
     *
     * <p>返回 {@code ResponseEntity.status(401).build()} 而不是带 envelope 的 body ——
     * 后者会把 Go 从未输出过的字节写给客户端。空 body 不会被
     * {@link GlobalResponseAdvice} 包成 envelope（它对 {@code null} 直接放行）。
     *
     * <p>锁定用例：{@code MessageApiAuthFlowTest}（走真实 MVC 管线断言 body 为空）。
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Void> handleAuthentication(AuthenticationException e) {
        log.warn("AuthenticationException code={} msg={}", e.getCode(), e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    // ============== 2. 业务异常：200 + envelope ==============

    /**
     * Go {@code errors.As(err, &codeErr)} 分支 —— {@code CodeError} 直接决定 code + msg。
     *
     * <p>对应 Go 侧三类抛出点：
     * <ul>
     *   <li>{@code shared.UserIDFromContext} 失败 → {@code NewErrCode(ErrorUnauthorized)} → 5004</li>
     *   <li>{@code SendAdminNotification} 的 target 校验 →
     *       {@code shared.RPCError(NewErrCode(ErrorInvalidParam))} → 5003</li>
     *   <li>{@code shared.RPCError(err)} → {@code NewErrCode / NewErrCodeMsg}（见
     *       {@link #handleRpcStatus}）</li>
     * </ul>
     *
     * <p><b>HTTP 恒为 200</b>：Go 的 error handler 返回 {@code http.StatusOK}。
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Response> handleBusiness(BusinessException e) {
        log.error("BusinessException code={} msg={}", e.getCode(), e.getMessage());
        return okEnvelope(e.getCode(), e.getMessage());
    }

    // ============== 3. gRPC 异常（Service 未捕获时的兜底） ==============

    /**
     * 正常路径下各 Service 都会先调 {@code MessageApiSupport.rpcError(...)} 把
     * {@link StatusRuntimeException} 转成 {@link BusinessException}，本 handler 只是最后一道网。
     *
     * <p>映射规则与 Go 的 {@code httpx.SetErrorHandler} 兜底分支完全一致：
     * 非 {@code CodeError} ⇒ {@code ErrorServerCommon}（5001）+ {@code "internal server error"}。
     * 因此这里<b>不</b>调用 {@code MessageApiSupport.rpcError} ——
     * 那会把 code 变成「按 gRPC code 反查」的结果，与 Go 的兜底语义不同。
     */
    @ExceptionHandler({StatusRuntimeException.class, StatusException.class})
    public ResponseEntity<Response> handleRpcStatus(Exception e) {
        Status.Code code = e instanceof StatusRuntimeException sre
                ? sre.getStatus().getCode()
                : ((StatusException) e).getStatus().getCode();
        log.error("unconverted gRPC status code={} desc={}", code,
                e instanceof StatusRuntimeException sre
                        ? sre.getStatus().getDescription()
                        : ((StatusException) e).getStatus().getDescription(), e);
        return okEnvelope(serverCommon(), serverCommonMsg());
    }

    // ============== 4. 解析层失败：一律 5001 + 200 ==============

    /**
     * {@code httpx.Parse} 失败 —— 非法 JSON / 类型不匹配 / body 畸形。
     *
     * <p>Go 出处：{@code rest/httpx/requests.go} 的 {@code ParseJsonBody} →
     * {@code mapping.UnmarshalJsonReader} 返回 error → handler 调 {@code httpx.ErrorCtx}
     * → {@code SetErrorHandler} 已注册 ⇒ <b>200 + 5001</b>。
     *
     * <p><b>错误文本刻意不对齐</b>：Go 的文本来自 go-zero 的 mapping 层
     * （{@code field "x" is not set} / {@code type mismatch ...}），
     * Java 侧来自 Jackson。两边都<b>不会</b>把它写进 body（body 恒为
     * {@code msg:"internal server error"}），所以文本差异在本服务里<b>不可观测</b> ——
     * 这也是本服务与 users 域（文本会进 text/plain body）的一个实质差别。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Response> handleUnreadableBody(HttpMessageNotReadableException e) {
        log.warn("unreadable request body : {}", e.getMessage());
        return okEnvelope(serverCommon(), serverCommonMsg());
    }

    /**
     * go-zero 必填字段缺失 —— 同上，折叠成 200 + 5001。
     *
     * <p>Go 出处：{@code core/mapping/unmarshaler.go:1199 newInitError}
     * → {@code ParseJsonBody} 返回 error → 同上路径。
     *
     * <p>注意本模块的必填判定把「缺席」与「显式 null」两种错误文本<b>都</b>映射到 5001，
     * 因为两者在本服务都不可观测（文本不进 body）。
     */
    @ExceptionHandler(GoRequiredFieldException.class)
    public ResponseEntity<Response> handleGoRequiredField(GoRequiredFieldException e) {
        log.warn("go-style required field missing : field={} explicitNull={}",
                e.getField(), e.isExplicitNull());
        return okEnvelope(serverCommon(), serverCommonMsg());
    }

    // ============== 5. 框架级错误：保留 Spring 语义 ==============

    /**
     * Content-Type 不支持 → 415。
     *
     * <p><b>已知偏离（OBSERVABLE DIFFERENCE）</b>：Go 侧的 go-zero 没有「媒体类型协商」这回事，
     * {@code withJsonBody} 只判 Content-Type 是否<b>包含</b> {@code application/json}；
     * 不含时它<b>不报错</b>，而是走 {@code UnmarshalJsonMap(nil, v)}（对有必填字段的 DTO
     * 仍然会报必填错 ⇒ 200 + 5001；对无必填字段的 DTO 则按零值继续）。
     * Java 侧由 Spring 的消息转换器选择直接判 415。
     *
     * <p>取舍：本服务在 users 域既有结论之上选择保留 Spring 默认（不为了「更接近 Go」
     * 去放宽媒体类型协商），差异只出现在<b>客户端 Content-Type 写错</b>时，
     * 两侧都是拒绝、都不产生业务副作用。改这条要动 message-api 全部 13 个端点的
     * {@code consumes}，性价比不成立。
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Void> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e) {
        log.warn("media type not supported : {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).build();
    }

    /**
     * 路径不存在 → 404。
     *
     * <p><b>这条是必需的，不是可选的兜底。</b>Spring 6+ 对未匹配路径抛
     * {@link NoResourceFoundException}，它会落进下面的 {@code handleAny(Exception)}，
     * 于是未知路径被改写成 <b>HTTP 200 + {code:5001}</b> —— 而 Go 的 router 对未注册路径
     * 返回 <b>404</b>。这是本模块<b>引入过的一处真实回归</b>：
     * 由 {@code MessageApiMvcPipelineTest#unknownPathIs404} 当场抓出。
     *
     * <p>道理与「层次由类型承载」一致：路由未命中与业务失败是两种不同的结果，
     * 不能共用同一个 fallback 出口，否则「客户端调错了地址」会被伪装成
     * 「服务端处理失败」。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<byte[]> handleNoResourceFound(NoResourceFoundException e) {
        log.warn("no handler for {} : {}", e.getResourcePath(), e.getMessage());
        // body / Content-Type / nosniff 全部由共享实现给出，逐字节证据见该类 javadoc
        return GoStyleRouterErrorResponses.notFound(e.getResourcePath());
    }

    /** HTTP 方法不支持 → 405（Go 的 router 同样不匹配 ⇒ 405）。 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("method not supported : {}", e.getMessage());
        // ⚠️ Go 侧是**裸状态码**（patrouter.go:79-82 只 WriteHeader，无 body、无 Content-Type），
        // 多出来的只有 Allow 头。Allow 的取值顺序刻意不排序，见共享实现类 javadoc。
        return GoStyleRouterErrorResponses.methodNotAllowed(
                e.getSupportedHttpMethods() == null ? java.util.List.of()
                        : e.getSupportedHttpMethods().stream().map(Object::toString).toList());
    }

    // ============== 6. 致命错误 / 兜底 ==============

    /**
     * {@link FatalError} 原样抛出，绝不转 envelope。
     *
     * <p>与 user-api 同款理由：Go 侧 {@code logger.go} 在 fatal_error 之后无条件
     * {@code os.Exit(1)}，「fatal 后必须停止接流量」是硬契约；转成 200 + 5001
     * 会让请求看起来「只是业务码不对」而进程继续正常服务。
     */
    @ExceptionHandler(FatalError.class)
    public void handleFatal(FatalError e) {
        throw e;
    }

    /**
     * 兜底 —— 对应 Go error handler 的 default 分支：<b>200 + 5001</b>。
     *
     * <p>Go 侧 {@code SetErrorHandler} 对任何非 {@code CodeError} 都返回 5001，
     * 所以「Spring 抛出的任何意外异常」在 Go 里也只会得到 5001 —— 这里保持一致。
     * 刻意<b>不</b>用 500：那会让 message-api 成为本仓唯一一个「服务端内部错误会返回 5xx」
     * 的服务，破坏 Go 侧「除 401 外恒为 200」的对外契约。
     */
    /**
     * gRPC client 熔断打开 → <b>1015 + "service busy"</b>。
     *
     * <p><b>为什么必须单列，不能落进 {@link #handleAny} 的兜底分支</b>：
     * Go 侧每个 logic 的错误出口是 {@code if err != nil { return nil, shared.RPCError(err) }}，
     * 而 Go 的 {@code err != nil} 是<b>捕获一切</b>，所以熔断抛出的
     * {@code breaker.ErrServiceUnavailable}（普通 error）也会被
     * {@code RPCError → BizCodeFromError} 转成 {@code CodeServerBusy=1015}。
     *
     * <p>RAG 的对应写法是 {@code catch (StatusRuntimeException e) { throw support.rpcError(e); }}
     * —— <b>只捕 gRPC status</b>。而 {@link com.rag.common.protection.GoStyleCircuitOpenException}
     * 刻意不是 status（否则 msg 会变成 {@code "circuit breaker is open"} 而非 Go 的
     * {@code "service busy"}），于是它<b>绕过</b> service 的 catch，
     * 最终落到 {@link #handleAny} 被兜底成 <b>5001</b> ⇒ 客户端看到的业务码与 Go 不同。
     *
     * <p>⚠️ <b>没有把 13 个 service 的 catch 改成 {@code Exception}</b>：
     * 那个 try 里除了 stub 调用，还包着 {@code support.toXxxResp(...)} 的响应转换；
     * 一旦它抛 {@code BusinessException}，宽 catch 会把它重新包成 1015 ——
     * 那是<b>新引入的错</b>。所以在这里按异常类型精确处理，是三项里副作用最小且可证明的。
     *
     * <p>锁定用例：{@code GoStyleGrpcClientBreakerE2ETest}（断言 HTTP 码从 5001 变成 1015）。
     */
    @ExceptionHandler(GoStyleCircuitOpenException.class)
    public ResponseEntity<Response> handleCircuitOpen(GoStyleCircuitOpenException e) {
        log.error("gRPC client circuit breaker is open", e);
        return okEnvelope(serverBusy(), serverBusyMsg());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Response> handleAny(Exception e) {
        if (e instanceof FatalError fatal) {
            throw fatal;
        }
        log.error("unhandled exception", e);
        return okEnvelope(serverCommon(), serverCommonMsg());
    }

    // ============== 工具 ==============

    private static ResponseEntity<Response> okEnvelope(int code, String msg) {
        return ResponseEntity.status(HttpStatus.OK).body(new Response(code, msg, null));
    }

    private static int serverCommon() {
        return MessageErrorCode.ERROR_SERVER_COMMON.getCode();
    }

    private static String serverCommonMsg() {
        return MessageErrorMessages.getMsg(serverCommon());
    }

    /** Go {@code errmsg.go:31} {@code CodeServerBusy = 1015}。 */
    private static int serverBusy() {
        return MessageErrorCode.CODE_SERVER_BUSY.getCode();
    }

    /**
     * msg 一律<b>查表</b>（Go {@code errmsg.go:55}：{@code CodeServerBusy: "service busy"}），
     * 刻意不用 {@code e.getMessage()} —— Go 侧的文案同样来自码表，不是异常文本。
     */
    private static String serverBusyMsg() {
        return MessageErrorMessages.getMsg(serverBusy());
    }
}