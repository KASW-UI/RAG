package com.rag.follow.follow.api.advice;

import com.rag.common.exception.AuthenticationException;
import com.rag.common.exception.BusinessException;
import com.rag.common.http.GoStyleRouterErrorResponses;
import com.rag.common.logger.FatalError;
import com.rag.common.protection.GoStyleCircuitOpenException;
import com.rag.common.response.Response;
import com.rag.follow.follow.common.errmsg.FollowErrorCode;
import com.rag.follow.follow.common.errmsg.FollowErrorMessages;
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
 * 全局异常处理器 —— 对应 Go 端 {@code followcenter.go:53-66} 里注册的 {@code httpx.SetErrorHandler}。
 *
 * <p><b>Go 出处（逐字）</b>：
 * <pre>
 * httpx.SetErrorHandler(func(err error) (int, interface{}) {
 *     code := followcommon.ErrorServerCommon        // 5001
 *     msg  := followcommon.GetErrMsg(code)         // "internal server error"
 *     var codeErr *followcommon.CodeError
 *     if errors.As(err, &amp;codeErr) {              // errors.As：可穿透包装
 *         code = codeErr.Code
 *         msg  = codeErr.Msg
 *     }
 *     return http.StatusOK, &amp;response.Response{Code: code, Msg: msg, Data: nil}
 * })
 * </pre>
 *
 * <p><b>⚠️ 与 users 域 api 的关键差异：本服务把所有业务错误压成 HTTP 200。</b>
 * <p>Go 侧 {@code service/user/**} 的两个 api <b>从未</b>调用 {@code httpx.SetErrorHandler}
 * （全树 grep 零命中），于是 {@code httpx.ErrorCtx} 落到 go-zero 默认分支
 * {@code http.Error(w, err.Error(), 400)} → <b>HTTP 400 + text/plain</b>；
 * 而 <b>follow api 注册了</b>，默认分支被整个替换掉 ⇒
 * <b>包括 {@code httpx.Parse} 的解析失败在内，一切都是 HTTP 200 + envelope。</b>
 *
 * <p>具体差异（照抄 user-api 的 400 text/plain 会直接构成行为回归）：
 * <table border="1">
 *   <caption>解析失败的两条截然不同的出口</caption>
 *   <tr><th>场景</th><th>Go follow api（真源）</th><th>Go user api / RAG user-api</th></tr>
 *   <tr>
 *     <td>body 非法 JSON</td>
 *     <td rowspan="3"><b>200</b> + {@code {code:5001,msg:"internal server error",data:null}}</b></td>
 *     <td rowspan="3"><b>400</b> + text/plain</td>
 *   </tr>
 *   <tr><td>必填字段缺失（{@code target_id}）</td></tr>
 *   <tr><td>标量类型不匹配（{@code offset:"10"}）</td></tr>
 * </table>
 *
 * <p><b>鉴权失败是唯一的例外，且 body 为空</b>：Go
 * {@code rest/handler/authhandler.go:106 unauthorized()} 只调
 * {@code writer.WriteHeader(http.StatusUnauthorized)}，不写任何字节。
 * <p>⚠️ <b>但黑名单那条 401 不是空 body</b>：它写
 * {@code {"code":5004,"msg":"unauthorized"}}（{@code checkblacklistmiddleware.go:42-48}），
 * 且<b>不经过本类</b>（由拦截器直接写响应）。⇒ 本域共有<b>三种</b>错误响应形状：
 * <ol>
 *   <li>业务失败 → 200 + {@code {code,msg,data}}；</li>
 *   <li>验签失败 → 401 + 空 body；</li>
 *   <li>黑名单命中 → 401 + {@code {code,msg}}（无 {@code data}）。</li>
 * </ol>
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
     * <p>锁定用例：{@code FollowApiAuthFlowTest}（走真实 MVC 管线断言 body 为空）。
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
     * <p>对应 Go 侧四类抛出点：
     * <ul>
     *   <li>{@code extractUserID} 失败 → {@code NewErrCode(ErrorUnauthorized)} → <b>5004</b>（8 个端点）</li>
     *   <li>归属校验失败 → {@code NewErrCode(ErrorForbidden)} → <b>5005</b>（{@code block/list} + {@code recommendations}）</li>
     *   <li>{@code codeFromRPCError(rpcErr)} → {@code BizCodeFromError}（见 {@code FollowGrpcError}）</li>
     *   <li>{@code NewErrCodeMsg(rpcResp.Code, rpcResp.Msg)} → <b>原样透传 rpc 的 code + msg</b></li>
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
     * 正常路径下各 Service 都会先调 {@code FollowApiSupport.rpcError(...)} 把
     * {@link StatusRuntimeException} 转成 {@link BusinessException}，本 handler 只是最后一道网。
     *
     * <p>映射规则与 Go 的 {@code httpx.SetErrorHandler} 兜底分支完全一致：
     * 非 {@code CodeError} ⇒ {@code ErrorServerCommon}（5001）+ {@code "internal server error"}。
     * 因此这里<b>不</b>调用 {@code FollowApiSupport.rpcError} ——
     * 那会把 code 变成「按 gRPC code 反查」的结果（可能 1015/5003/...），
     * 与 Go 的兜底语义不同。
     */
    @ExceptionHandler({StatusRuntimeException.class, StatusException.class})
    public ResponseEntity<Response> handleRpcStatus(Exception e) {
        Status.Code code = e instanceof StatusRuntimeException sre
                ? sre.getStatus().getCode()
                : ((StatusException) e).getStatus().getCode();
        String desc = e instanceof StatusRuntimeException sre
                ? sre.getStatus().getDescription()
                : ((StatusException) e).getStatus().getDescription();
        log.error("unconverted gRPC status code={} desc={}", code, desc, e);
        return okEnvelope(serverCommon(), serverCommonMsg());
    }

    // ============== 4. 解析层失败：一律 5001 + 200 ==============

    /**
     * {@code httpx.Parse} 失败 —— 非法 JSON / 类型不匹配 / body 畸形。
     *
     * <p>Go 出处：{@code rest/httpx/requests.go:66-68} 的 {@code ParseJsonBody}
     * → {@code mapping.UnmarshalJsonReader} 返回 error → handler 调 {@code httpx.ErrorCtx}
     * → {@code SetErrorHandler} 已注册 ⇒ <b>200 + 5001</b>。
     *
     * <p>本域三个真实触发点：
     * <ul>
     *   <li>{@code {"offset":"10"}} —— 无 {@code ,string} 的整数字段收到 JSON 字符串
     *       （Go: {@code type mismatch for field "offset"}），
     *       由 {@code FollowGoStyleCoercion} 让 Jackson 也拒绝；</li>
     *   <li>{@code target_id} 缺席 / 显式 null —— 由 {@code GoRequiredFieldException} 分支处理；</li>
     *   <li>{@code target_id: 1.5} —— {@link com.rag.users.common.http.GoStyleStringInt64} 抛
     *       {@code IllegalArgumentException}，被 Spring 包成 {@code HttpMessageNotReadableException}。</li>
     * </ul>
     *
     * <p><b>错误文本刻意不对齐</b>：Go 的文本来自 go-zero 的 mapping 层
     * （{@code field "x" is not set} / {@code type mismatch ...}），
     * Java 侧来自 Jackson。两边都<b>不会</b>把它写进 body（body 恒为
     * {@code msg:"internal server error"}），所以文本差异在本服务里<b>不可观测</b>。
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
     * <p>本域唯一的必填字段是 {@code RelationActionReq.target_id}（4 个动作端点）。
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
     * <p>取舍与 message-api 一致：保留 Spring 默认，不为了「更接近 Go」去放宽媒体类型协商。
     * 差异只出现在<b>客户端 Content-Type 写错</b>时，两侧都是拒绝、都不产生业务副作用。
     * ⚠️ 但 {@code com.rag.common.http.GoStyleJsonBodyConverter}（扫到即生效）已把
     * 「Go 是否读这个 body」的判据收进本模块的 MVC 管线，
     * 所以 {@code text/plain} + 合法 JSON 在本服务<b>不会</b>被 415 挡掉，
     * 而是按 Go 的规则<b>丢弃 body</b> ⇒ 4 个动作端点因此得到 200 + 5001（target_id 缺失），
     * 4 个 list 端点则按零值继续。这正是 Go 的行为。
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
     * {@link NoResourceFoundException}，它会落进 {@link #handleAny} 的兜底，
     * 于是未知路径被改写成 <b>HTTP 200 + {code:5001}</b> —— 而 Go 的 router 对未注册路径
     * 返回 <b>404</b>。
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
        // ⚠️ Go 侧是**裸状态码**（patrouter.go:79-82 只 WriteHeader，无 body、无 Content-Type）。
        return GoStyleRouterErrorResponses.methodNotAllowed(
                e.getSupportedHttpMethods() == null ? java.util.List.of()
                        : e.getSupportedHttpMethods().stream().map(Object::toString).toList());
    }

    // ============== 6. 熔断 / 致命错误 / 兜底 ==============

    /**
     * gRPC client 熔断打开 → <b>1015 + "service busy"</b>。
     *
     * <p><b>为什么必须单列，不能落进 {@link #handleAny} 的兜底分支</b>：
     * Go 侧每个 logic 的错误出口是 {@code if rpcErr != nil { return nil, codeFromRPCError(rpcErr) }}，
     * 而 Go 的 {@code rpcErr != nil} 是<b>捕获一切</b>，所以熔断抛出的
     * {@code breaker.ErrServiceUnavailable}（普通 error）也会被
     * {@code BizCodeFromError} 转成 {@code CodeServerBusy=1015}。
     *
     * <p>RAG 的对应写法是 {@code catch (StatusRuntimeException e) { throw support.rpcError(e); }}
     * —— <b>只捕 gRPC status</b>。而 {@link GoStyleCircuitOpenException} 刻意不是 status
     * （否则 msg 会变成 {@code "circuit breaker is open"} 而非 Go 的 {@code "service busy"}），
     * 于是它<b>绕过</b> service 的 catch，最终落到 {@link #handleAny} 被兜底成 <b>5001</b>
     * ⇒ 客户端看到的业务码与 Go 不同。故在此单列。
     */
    @ExceptionHandler(GoStyleCircuitOpenException.class)
    public ResponseEntity<Response> handleCircuitOpen(GoStyleCircuitOpenException e) {
        log.error("gRPC client circuit breaker is open", e);
        return okEnvelope(serverBusy(), serverBusyMsg());
    }

    /**
     * {@link FatalError} 原样抛出，绝不转 envelope。
     *
     * <p>与其它域同款理由：Go 侧 {@code logger.go} 在 fatal_error 之后无条件
     * {@code os.Exit(1)}，「fatal 后必须停止接流量」是硬契约；
     * 转成 200 + 5001 会让请求看起来「只是业务码不对」而进程继续正常服务。
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
     * 刻意<b>不</b>用 500：那会让 follow-api 成为本仓唯一一个「服务端内部错误会返回 5xx」
     * 的服务，破坏 Go 侧「除 401 外恒为 200」的对外契约。
     */
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
        return FollowErrorCode.ERROR_SERVER_COMMON.getCode();
    }

    private static String serverCommonMsg() {
        return FollowErrorMessages.getMsg(serverCommon());
    }

    /** Go {@code errmsg.go:37} {@code CodeServerBusy = 1015}。 */
    private static int serverBusy() {
        return FollowErrorCode.CODE_SERVER_BUSY.getCode();
    }

    /**
     * msg 一律<b>查表</b>（Go {@code errmsg.go:59}：{@code CodeServerBusy: "service busy"}），
     * 刻意不用 {@code e.getMessage()} —— Go 侧的文案同样来自码表，不是异常文本。
     */
    private static String serverBusyMsg() {
        return FollowErrorMessages.getMsg(serverBusy());
    }
}