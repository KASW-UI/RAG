package com.rag.comment.comment.api.advice;

import com.rag.comment.comment.common.errmsg.CommentErrorCode;
import com.rag.comment.comment.common.errmsg.CommentErrorMessages;
import com.rag.common.exception.AuthenticationException;
import com.rag.common.exception.BusinessException;
import com.rag.common.http.GoStyleRouterErrorResponses;
import com.rag.common.logger.FatalError;
import com.rag.common.protection.GoStyleCircuitOpenException;
import com.rag.common.response.Response;
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
 * 全局异常处理器 —— 对应 Go {@code service/comment/api/commentservice.go:47-60}
 * 里注册的 {@code httpx.SetErrorHandler}。
 *
 * <p><b>Go 出处（逐字）</b>：
 * <pre>
 * httpx.SetErrorHandler(func(err error) (int, interface{}) {
 *     code := errmsg.ErrorServerCommon            // 5001
 *     msg  := errmsg.GetErrMsg(code)              // "系统内部错误"
 *     var e *errmsg.CodeError
 *     if errors.As(err, &e) {                      // errors.As：可穿透包装
 *         code = e.Code
 *         msg  = e.Msg
 *     }
 *     return http.StatusOK, &response.Response{Code: code, Msg: msg, Data: nil}
 * })
 * </pre>
 *
 * <p><b>⚠️ 本服务把所有错误都压成 HTTP 200</b>（除鉴权 401 / 路由 404、405 / 媒体类型 415）。
 * 与 Go 侧 user-api / admin-api <b>不同</b>：那两个从未调用 {@code httpx.SetErrorHandler}，
 * 于是 {@code httpx.ErrorCtx} 落到 go-zero 默认分支
 * {@code http.Error(w, err.Error(), 400)} ⇒ <b>HTTP 400 + text/plain</b>；
 * comment api <b>注册了</b>，默认分支被整个替换掉 ⇒
 * <b>包括 {@code httpx.Parse} 的解析失败在内，一切都是 HTTP 200 + envelope。</b>
 * 照抄 400 text/plain 会直接构成行为回归。
 *
 * <p><b>鉴权失败是唯一的 body 级例外，且 body 为空</b>：Go
 * {@code rest/handler/authhandler.go:106 unauthorized()} 只调
 * {@code writer.WriteHeader(http.StatusUnauthorized)}，不写任何字节。
 * 这里逐字还原成 <b>401 + 空 body</b>。
 *
 * <p><b>本服务没有 1015 这条出口</b>（与 message-api 的关键差异）：
 * message 域的 {@code shared.RPCError} 会调 {@code BizCodeFromError}，
 * 把熔断错误映射成 {@code CodeServerBusy}=1015；
 * 而 comment 的 7 个 logic <b>没有</b> {@code RPCError} 这层包装，
 * 直接用 {@code status.FromError(err)} 的 {@code ok} 判定（逐条核对 6 处 logic）⇒
 * 熔断抛出的 {@code breaker.ErrServiceUnavailable} 是<b>普通 error</b>
 * （不实现 {@code GRPCStatus()}），{@code ok=false} ⇒ 落进兜底分支 ⇒ <b>5001</b>。
 */
@RestControllerAdvice
public class CommentGlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(CommentGlobalExceptionHandler.class);

    // ============== 1. 鉴权失败：401 + 空 body ==============

    /**
     * Go {@code unauthorized()} 的逐字等价物：<b>401，空 body</b>。
     *
     * <p>返回 {@code ResponseEntity.status(401).build()} 而不是带 envelope 的 body ——
     * 后者会把 Go 从未输出过的字节写给客户端。空 body 不会被
     * {@link CommentGlobalResponseAdvice} 包成 envelope（它对 {@code null} 直接放行）。
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
     * <p>对应 Go 侧全部抛出点：
     * <ul>
     *   <li>{@code extractUserID} 失败 → {@code NewErrCode(ErrorTokenRuntime)} → <b>1006</b>
     *       （6 处 logic，见 {@code CommentApiSupport#userId}）</li>
     *   <li>6 个 logic 的 gRPC 出口 → {@code NewErrCodeMsg(int(st.Code()), st.Message())}
     *       ⇒ code 是 <b>gRPC 标准码数值</b>（见 {@code CommentApiSupport#rpcError}）</li>
     * </ul>
     *
     * <p><b>HTTP 恒为 200</b>：Go 的 error handler 返回 {@code http.StatusOK}。
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Response> handleBusiness(BusinessException e) {
        log.error("BusinessException code={} msg={}", e.getCode(), e.getMessage());
        return okEnvelope(e.getCode(), e.getMessage());
    }

    // ============== 3. 未被 Service 转换的 gRPC / 基础设施异常 ==============

    /**
     * gRPC status 异常 —— 折叠成 <b>5001</b>。
     *
     * <p><b>为什么不按 gRPC code 反查</b>：正常路径下 6 个 logic 都会先经
     * {@code CommentApiSupport#rpcError} 把 {@link StatusRuntimeException} 转成
     * {@link BusinessException}；本 handler 是最后一道网。
     * 唯一会直接落到这里的是 {@code GetCommentService} ——
     * Go {@code getcommentlogic.go:72-75} 是<b>裸 {@code return nil, err}</b>，
     * 刻意<b>没有</b>做 {@code NewErrCodeMsg} 转换
     * （与其余 6 个 logic 不同，这是 Go 侧真实存在的差异）。
     * 该 error 不是 {@code *CodeError} ⇒ Go 的 error handler 也把它兜底成 5001。
     *
     * <p>⇒ 两边一致：<b>GetComment 的下游失败对客户端表现为 200 + {code:5001}</b>，
     * 拿不到下游的 gRPC code。
     */
    @ExceptionHandler({StatusRuntimeException.class, StatusException.class})
    public ResponseEntity<Response> handleGrpcStatus(Exception e) {
        Status status = e instanceof StatusRuntimeException sre
                ? sre.getStatus()
                : ((StatusException) e).getStatus();
        log.error("unconverted gRPC status code={} desc={}", status.getCode(),
                status.getDescription(), e);
        return okEnvelope(serverCommon(), serverCommonMsg());
    }

    /**
     * gRPC client 熔断打开 → <b>5001</b>（<b>不是</b> message-api 的 1015）。
     *
     * <p><b>推导链</b>：Go 侧 {@code zrpc/internal/config.go} 的
     * {@code Breaker bool json:",default=true"} 默认开启，comment api 的 yaml 未覆盖
     * ⇒ 熔断是开的。但 comment 的 logic <b>不调</b> {@code BizCodeFromError}，
     * 熔断错误 {@code breaker.ErrServiceUnavailable} 走
     * {@code status.FromError} 时 {@code ok=false}（普通 error 不实现 {@code GRPCStatus()}）
     * ⇒ 落进 {@code NewErrCode(ErrorServerCommon)} ⇒ <b>5001</b>。
     *
     * <p>单列出来是为了让这条语义<b>可测</b>；映射结果与兜底分支相同，但成因不同，
     * 混在 {@link #handleAny} 里会让人误以为「熔断没被单独想过」。
     */
    @ExceptionHandler(GoStyleCircuitOpenException.class)
    public ResponseEntity<Response> handleCircuitOpen(GoStyleCircuitOpenException e) {
        log.error("gRPC client circuit breaker is open", e);
        return okEnvelope(serverCommon(), serverCommonMsg());
    }

    // ============== 4. 解析层失败：一律 5001 + 200 ==============

    /**
     * {@code httpx.Parse} 失败 —— 非法 JSON / 标量类型不匹配 / body 畸形。
     *
     * <p>Go 出处：{@code rest/httpx/requests.go} 的 {@code ParseJsonBody}
     * → {@code mapping.UnmarshalJsonBody} 返回 error → handler 调 {@code httpx.ErrorCtx}
     * → {@code SetErrorHandler} 已注册 ⇒ <b>200 + 5001</b>。
     *
     * <p>comment 域的高频触发形态：{@code {"root_id":"123"}} ——
     * Go 侧 {@code processFieldPrimitive} 判 {@code typeKind != valueKind}
     * 报 {@code type mismatch for field "root_id"}。RAG 侧由
     * {@code CommentGoStyleCoercion} 让 Jackson 在同一位置失败。
     *
     * <p><b>错误文本刻意不对齐</b>：Go 的文本来自 go-zero mapping 层，
     * Java 侧来自 Jackson；两边都<b>不会</b>把它写进 body
     * （body 恒为 {@code msg:"系统内部错误"}），所以文本差异在本服务<b>不可观测</b>。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Response> handleUnreadableBody(HttpMessageNotReadableException e) {
        log.warn("unreadable request body : {}", e.getMessage());
        return okEnvelope(serverCommon(), serverCommonMsg());
    }

    /**
     * go-zero 必填字段缺失 —— 同上，折叠成 200 + 5001。
     *
     * <p>Go 出处：{@code core/mapping/unmarshaler.go:968} 的
     * {@code return newInitError(fullName)}（标量字段缺席）或
     * {@code unmarshaler.go:857-861} 的 {@code field %q mustn't be nil}（显式 null）。
     *
     * <p><b>本域命中率很高</b>：{@code types.go} 里 6 个请求 DTO 的 tag
     * <b>全部没有 {@code ,optional}</b> ⇒ 28 个字段<b>逐个</b>都是必填
     * （判据见 {@code GoStyleRequiredFields}）。「缺席」与「显式 null」在 Go 里是两种文案，
     * 但在本服务都落到 5001，所以<b>可观测行为一致</b>，无需区分。
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
     * <p><b>⚠️ 2026-10-24 双侧活体实测后订正：这里原本写的「Java 侧由 Spring 的消息转换器选择直接判 415」
     * 是<b>错的</b>，据此登记的 OBSERVABLE DIFFERENCE（D1）也已撤销。</b>
     *
     * <p>实测结论（Go 真源 + RAG 同时在跑，6 组 Content-Type × body 形态逐条对拍）：
     * <b>非 {@code application/json} 的 Content-Type ⇒ 两侧都是 HTTP 200 + {@code {code:5001}} ⇒ 一致。</b>
     *
     * <ul>
     *   <li>Go 侧：go-zero 没有「媒体类型协商」，{@code withJsonBody} 只判 Content-Type 是否<b>包含</b>
     *       {@code application/json}；不含时走 {@code UnmarshalJsonMap(nil, v)} —— 对本模块<b>全必填</b>的 DTO
     *       仍会报必填错 ⇒ 200 + 5001。</li>
     *   <li>RAG 侧：<b>不走本分支</b>。实测日志显示它落在
     *       {@code HttpMessageNotReadableException("Required request body is missing")}
     *       （见 {@link #handleUnreadableBody}）⇒ 同样 200 + 5001。
     *       判别实验：{@code application/json} + 1 字节 body ⇒ Spring 报 <b>JSON parse error</b>（body 被读了）；
     *       {@code text/plain} + 1 字节 body ⇒ 报 <b>body missing</b>（body 读不出字节）⇒ 判别点是 Content-Type。</li>
     * </ul>
     *
     * <p>⚠️ <b>机制未完全查实</b>（Spring 7 + Tomcat 11 组合下，非 JSON Content-Type 时 servlet input stream 读不出字节）：
     * 但<b>可观察行为已定</b> ⇒ 台账以活体为准（{@code docs/DRIFT/comment/api.md} §3 D1）。
     *
     * <p>本分支保留为<b>防御性兜底</b>：在上述实测形态下不可达，但保留 Spring 默认语义比主动吞掉它更安全。
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
     * {@link NoResourceFoundException}，它会落进 {@link #handleAny}，
     * 于是未知路径被改写成 <b>HTTP 200 + {code:5001}</b> —— 而 Go 的 router
     * 对未注册路径返回 <b>404</b>。「客户端调错了地址」不该被伪装成「服务端处理失败」。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<byte[]> handleNoResourceFound(NoResourceFoundException e) {
        log.warn("no handler for {} : {}", e.getResourcePath(), e.getMessage());
        return GoStyleRouterErrorResponses.notFound(e.getResourcePath());
    }

    /** HTTP 方法不支持 → 405（Go 的 router 同样不匹配 ⇒ 405）。 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("method not supported : {}", e.getMessage());
        return GoStyleRouterErrorResponses.methodNotAllowed(
                e.getSupportedHttpMethods() == null ? java.util.List.of()
                        : e.getSupportedHttpMethods().stream().map(Object::toString).toList());
    }

    // ============== 6. 致命错误 / 兜底 ==============

    /**
     * {@link FatalError} 原样抛出，绝不转 envelope。
     *
     * <p>Go 侧 {@code service/common/logger/logger.go} 在 {@code fatal_error} 之后
     * 无条件 {@code os.Exit(1)}，「fatal 后必须停止接流量」是硬契约；
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
     * 刻意<b>不</b>用 500：那会让 comment-api 成为本仓唯一一个
     * 「服务端内部错误会返回 5xx」的服务，破坏 Go 侧「除 401 外恒为 200」的对外契约。
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
        return CommentErrorCode.ERROR_SERVER_COMMON.getCode();
    }

    private static String serverCommonMsg() {
        return CommentErrorMessages.getMsg(serverCommon());
    }
}