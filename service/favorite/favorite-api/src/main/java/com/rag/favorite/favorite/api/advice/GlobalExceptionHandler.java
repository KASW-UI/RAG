package com.rag.favorite.favorite.api.advice;

import com.rag.common.exception.AuthenticationException;
import com.rag.common.exception.BusinessException;
import com.rag.common.http.GoStyleRouterErrorResponses;
import com.rag.common.logger.FatalError;
import com.rag.common.response.Response;
import com.rag.favorite.favorite.common.errmsg.FavoriteErrorCode;
import com.rag.favorite.favorite.common.errmsg.FavoriteErrorMessages;
import com.rag.favorite.favorite.common.errmsg.FavoriteGrpcError;
import com.rag.users.common.http.GoRequiredFieldException;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/**
 * 全局异常处理器 —— 把 Go 端「logic 返回业务码 + handler 写 envelope」的模型
 * 映射到 Spring 的异常体系。
 *
 * <h3>⚠️ 本域与 users/admin 域<b>最不一样</b>的一条：解析失败也是 <b>HTTP 200 + 业务码</b></h3>
 * <p>Go 出处（7 个 handler 完全同构，以 create_favorite_folder_handler.go:18-25 为例）：
 * <pre>
 *   var req types.CreateFavoriteFolderReq
 *   if err := httpx.Parse(r, &amp;req); err != nil {
 *       httpx.OkJsonCtx(r.Context(), w, &amp;response.Response{
 *           Code: favoritecommon.ErrorInvalidParam,   // 5003
 *           Msg:  favoritecommon.GetErrMsg(favoritecommon.ErrorInvalidParam),
 *           Data: nil,
 *       })
 *       return
 *   }
 * </pre>
 * <p>⇒ <b>解析失败走的是 {@code httpx.OkJsonCtx} ⇒ HTTP 200</b>，
 * body 是 {@code {"code":5003,"msg":"参数错误","data":null}}。
 * <p>⚠️ <b>这与 users/admin 域相反</b>：那边走 {@code httpx.ErrorCtx} ⇒
 * {@code http.Error(w, err.Error(), 400)} ⇒ 400 + text/plain
 * （见 {@code admin-api} 的 {@code GlobalExceptionHandler#handleGoRequiredField}）。
 * <p>所以本类的 {@link #handleGoRequiredField} / {@link #handleUnreadableBody} /
 * {@link #handleMediaTypeNotSupported} <b>刻意</b>返回 200 + envelope，
 * <b>不是</b>复用 admin-api 的 400 写法。<b>抄错这一处就是一处静默的 L1 漂移。</b>
 *
 * <h3>另一条本域特有：非 JSON 的 Content-Type 也走 5003</h3>
 * <p>Go: {@code rest/httpx/requests.go:153-155} 的
 * {@code withJsonBody(r) = r.ContentLength > 0 &amp;&amp; strings.Contains(Content-Type, "application/json")}。
 * Content-Type 不是 JSON ⇒ 走 {@code ParseJsonBody} 的
 * {@code mapping.UnmarshalJsonMap(nil, v)} 分支 ⇒ 必填字段全部落空 ⇒
 * {@code field "xxx" is not set} ⇒ 同样落到 5003。
 * <p>⇒ Spring 默认的 415 在本域必须改写成 200 + 5003。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * go-zero 解析失败在 favorite 域的<b>唯一</b>业务码。
     * Go: {@code favoritecommon.ErrorInvalidParam = 5003}（{@code service/favorite/common/errmsg.go:16}）。
     */
    private static final int ERROR_INVALID_PARAM = FavoriteErrorCode.ERROR_INVALID_PARAM.getCode();

    /**
     * 业务异常 → HTTP 200 + envelope。
     *
     * <p>Go 出处：所有 logic 的 {@code return nil, code} 形态，handler 侧
     * {@code httpx.OkJsonCtx(w, &response.Response{Code: code, Msg: GetErrMsg(code), Data: resp})}
     * （{@code service/favorite/api/internal/handler/favorite/create_favorite_folder_handler.go:29-33}）。
     * <b>HTTP 恒为 200</b>，业务成败由 body 的 {@code code} 表达。
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Response> handleBusiness(BusinessException e) {
        int code = e.getCode();
        String msg = e.getMessage();
        log.error("BusinessException code={} msg={}", code, msg);
        return ResponseEntity.status(HttpStatus.OK).body(new Response(code, msg, null));
    }

    /**
     * <b>鉴权层失败 → HTTP 401</b>。
     *
     * <p>Go 出处两处，<b>都是 401</b>：
     * <ul>
     *   <li>{@code rest.WithJwt} → {@code rest/handler/authhandler.go:106-122 unauthorized()}
     *       → {@code WriteHeader(401)}，<b>body 为空</b>；</li>
     *   <li>{@code api/internal/middleware/checkblacklist_middleware.go:39-45}
     *       → 401 + {@code {"code":5004,"msg":"未登录或登录已失效"}}
     *       （手写 {@code fmt.Sprintf}，<b>没有 {@code data} 字段</b>）。</li>
     * </ul>
     * <p>本端统一成完整 envelope（{@code {code,msg,data}}）—— 与 users/admin/comment 域同款。
     * <p>⚠️ <b>这是本域唯一一处「两侧 body 形状不同」的地方</b>：
     * Go 的验签失败是<b>空 body</b>、黑名单命中是<b>无 data 字段</b>的两字段 JSON，
     * RAG 侧两者都是三字段 envelope。<b>HTTP 状态码 401 与 code 5004 是对齐的</b>，
     * 差的只是 body 的字段集合 ⇒ 属表达层差异，<b>待签字</b>，登记在
     * {@code docs/Intentional_Deviations/favorite/api.md}（DEV-FAV-API-001）。
     * <p>⚠️ <b>不要</b>把「§3.4 已登记」当成依据：§3.4 是<b>proto 复制</b>那一节，
     * 与本条无关（此前本类的注释误引了它）。
     *
     * <p>Spring 挑最具体的 handler，故本方法优先于 {@link #handleBusiness}。
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Response> handleAuthentication(
            AuthenticationException e, jakarta.servlet.http.HttpServletResponse response) {
        log.warn("favorite AuthenticationException code={} msg={}", e.getCode(), e.getMessage());
        // 显式钉 401 — 防 gap：确保状态码在 advice 层提交
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new Response(e.getCode(), e.getMessage(), null));
    }

    /**
     * gRPC 调用失败 → <b>HTTP 200</b> + 由 gRPC 错误还原出的业务码。
     *
     * <p>Go 出处：每个 logic 的 {@code if rpcErr != nil { code = codeFromRPCError(rpcErr); return nil, code }}
     * —— {@code codeFromRPCError} 即 {@code favoritecommon.BizCodeFromError}
     * （{@code errmsg.go:108-137}），<b>7 个 logic 的这段完全同构</b>。
     * <p>⇒ 本域的还原规则（先按 description 反查文案、再按标准 gRPC 码兜底）由
     * {@link FavoriteGrpcError#bizCodeFromError} 承担，本方法只负责把它变成 envelope。
     */
    @ExceptionHandler(StatusRuntimeException.class)
    public ResponseEntity<Response> handleRPC(StatusRuntimeException e) {
        int code = FavoriteGrpcError.bizCodeFromError(e);
        String msg = FavoriteErrorMessages.getMsg(code);
        log.error("gRPC StatusRuntimeException status={} mapped to bizCode={} desc={}",
                e.getStatus().getCode(), code, e.getStatus().getDescription(), e);
        return ResponseEntity.status(HttpStatus.OK).body(new Response(code, msg, null));
    }

    /**
     * go-zero 必填字段缺失 → <b>HTTP 200 + 5003</b>（本域特有，见类注释）。
     *
     * <p>Go: {@code httpx.Parse} 返回 {@code field "xxx" is not set} /
     * {@code field "xxx" mustn't be nil} ⇒ handler 落 5003 + 200。
     */
    @ExceptionHandler(GoRequiredFieldException.class)
    public ResponseEntity<Response> handleGoRequiredField(GoRequiredFieldException e) {
        log.warn("go-style required field missing : field={}", e.getField());
        return invalidParam();
    }

    /**
     * {@code ,string} int64 字段解析失败 → <b>HTTP 200 + 5003</b>。
     *
     * <p>Go: {@code strconv.ParseInt} 失败（{@code "abc"} / {@code "1.5"} / 越界）
     * ⇒ {@code httpx.Parse} 报错 ⇒ 5003 + 200。
     *
     * <p>⚠️ 注意：本域的 JSON 通道里，这类错误通常被 Jackson 包成
     * {@link HttpMessageNotReadableException}（见 {@link #handleUnreadableBody}）；
     * 本方法覆盖的是<b>不走 Jackson</b> 的路径 —— 即 GET 的 form 通道
     * （{@code GET /item/list?folderId=abc}）。
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Response> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("go-style scalar parse failed : {}", e.getMessage());
        return invalidParam();
    }

    /**
     * 请求体无法解析 → <b>HTTP 200 + 5003</b>（本域特有，见类注释）。
     *
     * <p>覆盖 Go 侧的三类 body 失败：JSON 语法错误、字段类型不匹配
     * （{@code type mismatch for field "name", expect "string", actual "number"}）、
     * 以及 {@code ,string} 字段的 ParseInt 失败。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Response> handleUnreadableBody(HttpMessageNotReadableException e) {
        log.warn("unreadable request body : {}", e.getMessage());
        return invalidParam();
    }

    /**
     * Content-Type 不是 JSON → <b>HTTP 200 + 5003</b>（本域特有，见类注释）。
     *
     * <p>Go: {@code withJsonBody} 为 false ⇒ {@code UnmarshalJsonMap(nil, v)}
     * ⇒ 必填字段落空 ⇒ {@code field "xxx" is not set} ⇒ 5003 + 200。
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Response> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e) {
        log.warn("media type not supported : {}", e.getMessage());
        return invalidParam();
    }

    /**
     * 路径未注册 → 404 + text/plain（Go router 的形状）。
     *
     * <p>Go: {@code rest/router/patrouter.go:73,93-97} → {@code http.NotFound(w, r)}
     * → {@code http.Error(w, "404 page not found", 404)}。
     * <p>⚠️ 404 <b>不经过鉴权</b>：Go 的 chain 只挂在匹配上的路由上（见
     * {@code FavoriteJwtAuthInterceptor} 里 {@code handler instanceof HandlerMethod} 的注释）。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<byte[]> handleNoResourceFound(NoResourceFoundException e) {
        log.warn("no resource found : {}", e.getResourcePath());
        return GoStyleRouterErrorResponses.notFound(e.getResourcePath());
    }

    /**
     * 方法不匹配 → 405 + 裸状态码（Go router 的形状）。
     *
     * <p>Go: {@code rest/router/patrouter.go:79-82} → {@code w.WriteHeader(405)}，
     * <b>无 body、无 Content-Type</b>，只带一个 {@code Allow} 头。
     *
     * <p><b>⚠️ Allow 的取值必须按路径取，不能写死一份全集</b>：Go 侧<b>每个路径只注册了 1 个
     * method</b>（{@code routes.go:22-53}：folder/create=POST、folder/delete=DELETE、
     * folder/list=GET、folder/update=POST、item/create=POST、item/delete=DELETE、item/list=GET），
     * 所以 Go 发出的 {@code Allow} <b>永远只有一个值</b>。
     * 写死全集会让客户端看到「这个路径支持 3 个 method」这种 Go 侧不存在的假信息。
     * <p>这里用 Spring 提供的 {@code e.getSupportedHttpMethods()} —— 它来自
     * {@code RequestMappingHandlerMapping.handleNoMatch}，正是「该路径实际注册了哪些 method」。
     * <p>⚠️ 共享实现 {@code GoStyleRouterErrorResponses#methodNotAllowed} <b>刻意不排序</b>：
     * Go 的 {@code methodsAllowed} 遍历 Go map，顺序随机；本域每个路径只有 1 个 method，
     * 该差异不可观测。
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("method not supported : {}", e.getMessage());
        List<String> allowed = e.getSupportedHttpMethods() == null
                ? List.of()
                : e.getSupportedHttpMethods().stream().map(HttpMethod::name).toList();
        return GoStyleRouterErrorResponses.methodNotAllowed(allowed);
    }

    /**
     * H3 修复：{@link FatalError} 必须原样抛出，<b>不得</b>被转成 200 业务 envelope。
     *
     * <p>Go 版 {@code logger.go:296} 在打出 fatal_error 后无条件 {@code os.Exit(1)}，
     * 「fatal 后必须停止接流量」是硬契约。
     */
    @ExceptionHandler(FatalError.class)
    public void handleFatal(FatalError e) {
        // 故意不返回 ResponseEntity：让异常继续向上抛
        throw e;
    }

    /**
     * 兜底：未预期的异常 → HTTP 500 + 5001。
     *
     * <p>Go 侧的等价物是 go-zero 的 {@code RecoverHandler}（把 panic 转成 500，
     * {@code go-zero@v1.10.2/rest/handler/recoverhandler.go:12}），
     * 但 Go 的 logic 不会 panic —— 本分支对应的是「RAG 侧的实现缺陷」，
     * 不属于迁移需要保留的语义。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Response> handleAny(Exception e) {
        if (e instanceof FatalError fatal) {
            throw fatal;
        }
        log.error("unhandled exception", e);
        int code = FavoriteErrorCode.ERROR_SERVER_COMMON.getCode();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new Response(code, FavoriteErrorMessages.getMsg(code), null));
    }

    /**
     * 解析失败的统一出口：<b>HTTP 200 + {@code {code:5003, msg:"参数错误", data:null}}</b>。
     *
     * <p>Go: {@code httpx.OkJsonCtx(w, &response.Response{Code: ErrorInvalidParam,
     * Msg: GetErrMsg(ErrorInvalidParam), Data: nil})}
     * （{@code service/favorite/api/internal/handler/favorite/create_favorite_folder_handler.go:19-23}）。
     */
    private static ResponseEntity<Response> invalidParam() {
        return ResponseEntity.status(HttpStatus.OK)
                .body(new Response(ERROR_INVALID_PARAM,
                        FavoriteErrorMessages.getMsg(ERROR_INVALID_PARAM), null));
    }
}
