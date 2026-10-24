package com.rag.like.like.api.advice;

import com.rag.common.exception.AuthenticationException;
import com.rag.common.http.GoStyleRouterErrorResponses;
import com.rag.common.logger.FatalError;
import com.rag.common.response.Response;
import com.rag.like.like.common.errmsg.CodeError;
import com.rag.like.like.common.errmsg.LikeErrorCode;
import com.rag.like.like.common.errmsg.LikeErrorMessages;
import com.rag.users.common.http.GoRequiredFieldException;
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
 * 全局异常处理器 —— 对应 Go 端 {@code likecenter.go:48-61} 注册的
 * {@code httpx.SetErrorHandler}。
 *
 * <p><b>Go 出处（逐字）</b>：
 * <pre>
 * httpx.SetErrorHandler(func(err error) (int, interface{}) {
 *     code := errmsg.ErrorServerCommon            // 5001
 *     msg  := errmsg.GetErrMsg(code)             // "系统内部错误"
 *     var e *errmsg.CodeError
 *     if errors.As(err, &amp;e) {                   // errors.As：可穿透包装
 *         code = e.Code
 *         msg  = e.Msg
 *     }
 *     return http.StatusOK, &amp;response.Response{Code: code, Msg: msg, Data: nil}
 * })
 * </pre>
 *
 * <h3>⇒ 本服务的对客户端契约</h3>
 * <table border="1">
 *   <caption>like-api 的响应矩阵</caption>
 *   <tr><th>场景</th><th>HTTP</th><th>body</th></tr>
 *   <tr><td>成功</td><td><b>200</b></td><td>{@code {"code":200,"msg":"OK","data":{...}}}</td></tr>
 *   <tr><td>业务失败（含 RPC 错误、1006 身份解析失败）</td><td><b>200</b></td><td>{@code {"code":<业务码>,"msg":"...","data":null}}</td></tr>
 *   <tr><td>解析失败（非法 JSON / 类型不匹配 / 必填缺失）</td><td><b>200</b></td><td>{@code {"code":5001,"msg":"系统内部错误","data":null}}</td></tr>
 *   <tr><td><b>鉴权失败</b>（缺 token / 签名错 / 过期）</td><td><b>401</b></td><td><b>空</b></td></tr>
 *   <tr><td>路径不存在</td><td>404</td><td>空</td></tr>
 *   <tr><td>方法不支持</td><td>405</td><td>空</td></tr>
 *   <tr><td>Content-Type 不支持</td><td>415</td><td>空</td></tr>
 * </table>
 *
 * <p><b>关键点：除了鉴权失败与框架级错误，HTTP 状态码恒为 200，成败全在 body 的 code 里。</b>
 * 原因在 Go 侧：{@code likecenter.go} <b>注册了</b> {@code httpx.SetErrorHandler}，
 * 该注册会<b>整体替换</b> go-zero 的默认处理器
 * （{@code httpx/responses.go:142-172} 的 {@code doHandleError}：
 * {@code if handler == nil {...} else { code, body := handler(err) }}）。
 * 没有它时才会落到默认的 {@code http.Error(w, err.Error(), 400)}。
 *
 * <p><b>⚠️ 鉴权失败是唯一的「非 200」业务出口，且 body 为空</b>：
 * Go {@code rest/handler/authhandler.go:106-122} 的 {@code unauthorized()}
 * 只调 {@code writer.WriteHeader(http.StatusUnauthorized)}，<b>不写任何字节</b>。
 * 它的位置在 chain 里<b>先于</b> {@code httpx.SetErrorHandler} 生效的路径 ——
 * authhandler 直接短路返回，根本不经过 error handler。
 * ⇒ Java 侧 {@link #handleAuthentication} 必须返回 <b>无 body</b> 的
 * {@code ResponseEntity.status(401).build()}。
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
     * <p>锁定用例：{@code LikeApiAuthFlowTest}（走真实 MVC 管线断言 body 为空）。
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Void> handleAuthentication(AuthenticationException e) {
        log.warn("AuthenticationException code={} msg={}", e.getCode(), e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    // ============== 2. 业务异常：200 + envelope ==============

    /**
     * Go {@code errors.As(err, &amp;codeErr)} 分支 —— {@code *CodeError} 的 Code/Msg
     * 直接决定 envelope 的 code 与 msg。
     *
     * <p>本服务两条业务抛出路径都落到这里：
     * <ul>
     *   <li>{@code LikeApiSupport#userId} 失败 ⇒ 1006（{@code ErrorTokenRuntime}）</li>
     *   <li>{@code LikeApiSupport#rpcError} 失败 ⇒ gRPC 业务码，或兜底 5001</li>
     * </ul>
     *
     * <p><b>HTTP 恒为 200</b>：Go 的 error handler 返回 {@code http.StatusOK}。
     */
    @ExceptionHandler(CodeError.class)
    public ResponseEntity<Response> handleCodeError(CodeError e) {
        log.error("CodeError code={} msg={}", e.getCode(), e.getMsg());
        return okEnvelope(e.getCode(), e.getMsg());
    }

    // ============== 3. gRPC 异常兜底 ==============

    /**
     * 正常路径下各 Service 都会先经 {@code LikeApiSupport#rpcError(e)} 把
     * {@link StatusRuntimeException} 转成 {@link CodeError}，本 handler 只是最后一道网。
     *
     * <p>映射与 Go 的兜底一致：<b>5001 + 查表 msg</b>。
     * 这里<b>刻意不</b>调 {@code rpcError} —— 它会把 code 变成
     * 「按 gRPC code 反查」的结果，与 Go 兜底分支的 5001 不同。
     */
    @ExceptionHandler({StatusRuntimeException.class, StatusException.class})
    public ResponseEntity<Response> handleRpcStatus(Exception e) {
        log.error("unconverted gRPC status error", e);
        return okEnvelope(serverCommon(), serverCommonMsg());
    }

    // ============== 4. 解析层失败：一律 5001 + 200 ==============

    /**
     * {@code httpx.Parse} 失败 —— 非法 JSON / 类型不匹配 / body 畸形。
     *
     * <p>Go 出处：{@code rest/httpx/requests.go} 的 {@code ParseJsonBody}
     * → {@code mapping.UnmarshalJsonReader} 返回 error
     * → handler 调 {@code httpx.ErrorCtx} → 已注册 error handler ⇒ <b>200 + 5001</b>。
     *
     * <p><b>错误文本刻意不对齐</b>：Go 的文本来自 go-zero 的 mapping 层，
     * Java 侧来自 Jackson。两侧都<b>不会</b>把它写进 body
     * （msg 恒为查表的"系统内部错误"），所以文本差异在本服务<b>不可观测</b>。
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
     * （{@code field %q is not set}）与 {@code :856-862} 的显式 null
     * （{@code field %q mustn't be nil}）—— 两者都经同一条 Parse 失败路径，
     * 在本服务<b>不可观测</b>（文本不进 body），故统一映射到 5001。
     */
    @ExceptionHandler(GoRequiredFieldException.class)
    public ResponseEntity<Response> handleGoRequiredField(GoRequiredFieldException e) {
        log.warn("go-style required field missing : field={} explicitNull={}",
                e.getField(), e.isExplicitNull());
        return okEnvelope(serverCommon(), serverCommonMsg());
    }

    /**
     * go-zero {@code options=} 取值域校验失败 —— 同样折叠成 200 + 5001。
     *
     * <p>本服务唯一会走到这里的是 {@code LikeActionReq.action_type} 的
     * {@code options=1|2|3|4}（见该 DTO）。Go 出处：
     * {@code core/mapping/unmarshaler.go} 的 {@code validateValueInOptions}，
     * 发生在解析期 ⇒ 同样落到 {@code httpx.ErrorCtx} ⇒ 5001。
     *
     * <p><b>单列一个 handler 而不是并进 {@link #handleAny}</b>：
     * 并进去也能得到 5001，但日志级别会从 {@code warn} 降到 {@code error}，
     * 且与「真正的未预期异常」混在一起无法区分 —— 客户端发错 {@code action_type}
     * 是常态，不该刷 error 日志。
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Response> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("go-style field validation failed : {}", e.getMessage());
        return okEnvelope(serverCommon(), serverCommonMsg());
    }

    // ============== 5. 框架级错误：保留 Spring 语义 ==============

    /**
     * Content-Type 不支持 → 415。
     *
     * <p><b>已知偏离（OBSERVABLE DIFFERENCE）</b>：Go 侧没有「媒体类型协商」，
     * {@code withJsonBody} 只判 Content-Type 是否<b>包含</b> {@code application/json}，
     * 不含时它<b>不报错</b>而是走表单/零值路径，最终多半仍是 200 + 5001。
     * Java 侧由 Spring 的消息转换器选择直接判 415。
     *
     * <p>取舍：保留 Spring 默认。改这条要动全部 6 个端点的 {@code consumes}，
     * 而差异只在「客户端 Content-Type 写错」时出现，两侧都是拒绝、都无业务副作用。
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
     * {@link NoResourceFoundException}，若不单列就会落进 {@link #handleAny}，
     * 于是未知路径被改写成 <b>HTTP 200 + {code:5001}</b> —— 而 Go 的 router
     * 对未注册路径返回 <b>404</b>。那是凭空制造出来的回归。
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

    // ============== 6. 最终兜底 ==============

    /** 未预期异常 → 5001 + 200，与 Go 的兜底分支一致。 */
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
        return LikeErrorCode.ERROR_SERVER_COMMON.getCode();
    }

    private static String serverCommonMsg() {
        return LikeErrorMessages.getMsg(serverCommon());
    }
}