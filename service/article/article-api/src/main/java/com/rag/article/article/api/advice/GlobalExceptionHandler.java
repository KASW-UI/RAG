package com.rag.article.article.api.advice;

import com.rag.article.article.api.http.GoStyleBadRequestException;
import com.rag.article.article.api.service.ArticleCreateService;
import com.rag.article.article.api.http.GoStylePlainTextErrors;
import com.rag.common.exception.AuthenticationException;
import com.rag.common.exception.BusinessException;
import com.rag.common.http.GoStyleRouterErrorResponses;
import com.rag.common.logger.FatalError;
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
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理 —— <b>本模块与 message-api 最不一样的一层</b>。
 *
 * <h2>article-api <b>没有</b> {@code httpx.SetErrorHandler}</h2>
 *
 * <p>这是必须先钉死的<b>决定性差异</b>（{@code article.go} 全文 42 行，一个字都没有）：
 * <pre>
 *   message/api/messagecenter.go:53   httpx.SetErrorHandler(func(err) (int, any) {
 *       return http.StatusOK, &response.Response{Code: code, …} })     ← 有 ⇒ 任何 err 都是 200 + 信封
 *   like/api/likecenter.go:48          httpx.SetErrorHandler(...)                    ← 有
 *   hot/api/hotcenter.go:53            httpx.SetErrorHandler(...)                    ← 有
 *   article/api/article.go             （无）                                        ← 无 ⇒ 400 + text/plain
 * </pre>
 *
 * <p>后果落在 {@code httpx/responses.go:142-159}：
 * <pre>
 *   handler == nil  ⇒  http.Error(w, err.Error(), http.StatusBadRequest)
 *                       ⇒ 400 + text/plain; charset=utf-8 + X-Content-Type-Options: nosniff
 *                          + err.Error() + "\n"
 * </pre>
 *
 * ⇒ <b>article-api 的「解析失败」与「上传时下游失败」都是 400 text/plain，不是 200 信封。</b>
 * ⚠️ message-api 的同名处理器把 {@code HttpMessageNotReadableException} 映成
 * {@code 200 + {code:5001}} —— 那个映射对 message 正确，
 * <b>逐行照搬到 article 就是 L1 漂移</b>。
 * 这是 AGENTS.md §3.1「不要拿另一个域的实测结论当对照基准」最直接的一次发作。
 *
 * <h2>本处理器的<b>分流规则</b>（一句话：有没有信封装）</h2>
 * <table border="1">
 *   <caption>异常 → wire 形状</caption>
 *   <tr><th>异常</th><th>HTTP</th><th>body</th><th>Go 出处</th></tr>
 *   <tr>
 *     <td>{@code AuthenticationException}</td><td><b>401</b></td><td><b>空</b></td>
 *     <td>{@code authhandler.go:121} 只 WriteHeader(401)</td>
 *   </tr>
 *   <tr>
 *     <td>{@code BusinessException}</td><td><b>200</b></td><td>信封 {@code {code,msg,data:null}}</td>
 *     <td>各 handler 显式构造的 {@code response.Response}</td>
 *   </tr>
 *   <tr>
 *     <td>{@code GoStyleBadRequestException}</td><td><b>400</b></td><td>裸文案 + {@code "\n"}</td>
 *     <td>{@code httpx.ErrorCtx}（上传的 FormFile / RPC 错误）</td>
 *   </tr>
 *   <tr>
 *     <td>Spring 解析异常族</td><td><b>400</b></td><td>裸文案 + {@code "\n"}</td>
 *     <td>{@code httpx.Parse} 的各条失败分支</td>
 *   </tr>
 *   <tr>
 *     <td>gRPC status（未被 service 转换）</td><td><b>500</b></td><td><b>空</b></td>
 *     <td>{@code recoverhandler.go:17} 裸 WriteHeader(500)</td>
 *   </tr>
 *   <tr>
 *     <td>{@code 兜底 Exception}</td><td><b>500</b></td><td><b>空</b></td>
 *     <td>同上（panic 被 recover）</td>
 *   </tr>
 * </table>
 *
 * <h2>⚠️ <b>刻意没有</b> {@code @ExceptionHandler(HttpMediaTypeNotSupportedException.class)}</h2>
 * <p>message-api 有一条映成 415。article-api <b>不能有</b>，理由是 Go 侧
 * {@code httpx.ParseJsonBody}（{@code httpx/requests.go:117-124}）：
 * <pre>
 *   if withJsonBody(r) { … UnmarshalJsonReader … }
 *   return mapping.UnmarshalJsonMap(nil, v)        ← Content-Type 不是 json 时**丢 body 当空对象继续**
 * </pre>
 * ⇒ {@code Content-Type: text/plain} 的 {@code POST /v1/article} 在 Go 侧
 * <b>依然成功</b>（只是字段全是零值）。本模块靠
 * {@code com.rag.common.http.GoStyleJsonBodyConverter}（认领全部媒体类型、
 * 非 JSON 时返回 {@code null}）复现这一行为，因此 415 <b>根本不会发生</b>。
 * 写一条永不触发的 415 handler 等于给后人留一个「415 是本模块行为」的错觉。
 *
 * <h2>⚠️ {@code GoRequiredFieldException} 走 <b>400 text/plain</b>，不是 200 + 5001</h2>
 * <p>message-api 映成 200 + 5001（因为 Go message 注册了 error handler）；
 * article 侧必须 400。这条由 {@code ArticleApiMvcPipelineTest} 的
 * 「必填字段缺席 / 显式 null → 400 + text/plain + nosniff」用例钉死。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ============== 1. 鉴权失败：401 + 空 body ==============

    /**
     * Go {@code rest/handler/authhandler.go:106-122}：
     * <pre>writer.WriteHeader(http.StatusUnauthorized)     // 只写状态码</pre>
     *
     * <p>⚠️ 返回 {@code ResponseEntity<Void>.build()}（<b>不是</b> {@code .body(...)}）：
     * body 非空会让 Spring 选出一个 {@code MediaType}，响应头就比 Go 多一项
     * （DRIFT-024 同款教训：多出来的 {@code Content-Type} / {@code nosniff}）。
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Void> handleAuthentication(AuthenticationException e) {
        log.warn("AuthenticationException code={} msg={}", e.getCode(), e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    // ============== 2. 业务异常：200 + envelope ==============

    /**
     * Go 各 handler 的 {@code httpx.OkJson(w, &response.Response{Code: code, Msg: errmsg.GetErrMsg(code), Data: resp})}。
     *
     * <p>⚠️ <b>HTTP 恒 200</b>，业务码只在 body 的 {@code code} 里 —— 连
     * 「请先登录」（1005）、「文章不存在」（1002）、「无权限」（1006）都是 200。
     * 这是 L1 契约：客户端<b>不能</b>按 HTTP 状态码分流。
     *
     * <p>{@code msg} 取 {@code e.getMessage()}：service 侧统一用
     * {@code ErrorCode.X.getMsg()} 构造，故与 {@code errmsg.GetErrMsg(code)} 等价。
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Response> handleBusiness(BusinessException e) {
        log.error("BusinessException code={} msg={}", e.getCode(), e.getMessage());
        return envelope(e.getCode(), e.getMessage());
    }

    /**
     * 「失败但<b>带 data</b>」的信封 —— 仅 {@code ArticleCreateService.CreateFailure} 一条路径。
     *
     * <p>Go {@code create_article_logic.go:60-69} 对 {@code FailedPrecondition} /
     * {@code DeadlineExceeded} 返回 <b>带 article_id 的 resp</b>，同时给 1004 / 1003：
     * <pre>
     *   {"code":1003,"msg":"文章处理中，请稍后查看","data":{"article_id":"1792…"}}
     * </pre>
     * ⇒ 客户端会收到「HTTP 200 + code≠200 + data 非空」。这是<b>有意</b>的：
     * 文章已落库、只是发布流水线没跑完，客户端需要那个 id 才能继续轮询/跳转。
     *
     * <p>⚠️ <b>不能</b>把它并进 {@link #handleBusiness}：那个方法产出 {@code data = null}
     * （{@code com.rag.common.exception.BusinessException} 不带 payload）。
     * 合并会把「失败也带 data」这个 L2 语义抹掉。
     */
    @ExceptionHandler(ArticleCreateService.CreateFailure.class)
    public ResponseEntity<Response> handleCreateFailure(ArticleCreateService.CreateFailure e) {
        log.error("CreateFailure code={} msg={} articleId={}",
                e.getCode(), e.getMessage(), e.getPayload().getArticleId());
        return envelopeWithData(e.getCode(), e.getMessage(), e.getPayload());
    }

    // ============== 3. 「http.Error 形状」的错误：400 + text/plain ==============

    /**
     * Go {@code httpx.ErrorCtx} 在<b>没有</b> error handler 时的落点。
     *
     * <p>两个真实来源：
     * <ol>
     *   <li>{@code UploadImageHandler} 里 {@code r.FormFile("image")} 的错误；</li>
     *   <li>{@code UploadImageHandler} 里 {@code UploadFile} RPC 的错误
     *       （{@code upload_image_handler.go:25-26}：{@code if err != nil { httpx.ErrorCtx(...) } else { OkJsonCtx(...) }}）。</li>
     * </ol>
     * 两条在 Go 里是<b>同一个</b> 400 —— 因为 {@code UploadImageLogic.UploadImage}
     * 对读文件失败与 RPC 失败<b>都</b>返回裸 {@code err}，由 handler 统一交给
     * {@code ErrorCtx}。见 {@code ArticleUploadService}。
     */
    @ExceptionHandler(GoStyleBadRequestException.class)
    public ResponseEntity<byte[]> handleGoStyleBadRequest(GoStyleBadRequestException e) {
        log.warn("go-style bad request : {}", e.getMessage());
        return GoStylePlainTextErrors.badRequest(e.getMessage());
    }

    /**
     * Spring 的解析异常族 → Go {@code httpx.Parse} 的各条失败分支。
     *
     * <p><b>为什么是 400 而不是 200 信封</b>：Go 侧这四种失败都由 handler 的
     * {@code if err := httpx.Parse(r, &req); err != nil { httpx.ErrorCtx(r.Context(), w, err); return }}
     * 承接（6 个 handler 都是这同一段），article-api 无 error handler ⇒ 400 text/plain。
     *
     * <ul>
     *   <li>{@link HttpMessageNotReadableException}：畸形 JSON / 类型不符
     *       （Go: {@code unmarshal field %q, %w} 或 {@code type mismatch for field %q}）；</li>
     *   <li>{@link MethodArgumentTypeMismatchException}：{@code path}/{@code form} 标量转换失败
     *       （Go: {@code processNamedFieldWithValueFromString} 的 type mismatch，
     *       本模块走 {@code GoFormValues} 的 bool / int32 分支）；</li>
     *   <li>{@link MissingServletRequestParameterException} / {@link MissingServletRequestPartException}：</li>
     *   <li>{@link BindException}：多参数绑定失败。</li>
     * </ul>
     *
     * <p>⚠️ {@code MaxUploadSizeExceededException} 归到 400 而不是 413：
     * Go 侧 Content-Length 超限时是 {@code MaxBytesHandler} 的<b>裸 413</b>
     * （由 {@code GoStyleMaxBytesFilter} 承担，已在到达这里之前返回）；
     * 而 <b>无 Content-Length</b>（chunked）时 Go 只看 ContentLength
     * （{@code maxbyteshandler.go:19}）⇒ <b>放行</b>，随后
     * {@code r.FormFile} 因 reader 超限失败 ⇒ {@code httpx.ErrorCtx} ⇒ <b>400</b>。
     * 本类的 multipart 上限是后者那条路径的等价物，故映射 400。
     */
    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class,
            BindException.class,
            MaxUploadSizeExceededException.class,
            // 🔴 DRIFT-003（已修）：**父类** MultipartException 必须列，否则
            //    「Content-Type 不是 multipart/form-data」这种请求会落到 handleAny ⇒ 裸 500。
            //    Go 侧此时是 r.FormFile 失败 ⇒ httpx.ErrorCtx ⇒ **400 text/plain**。
            //    ⚠️ 上游只列了子类 MaxUploadSizeExceededException，漏了父类 ——
            //    除了状态码错（500 vs 400），500 还会被 GoStyleBreakerFilter 记为
            //    **熔断失败**（code < 500 ⇒ Accept），一个客户端笔误就能打熔断器。
            org.springframework.web.multipart.MultipartException.class
    })
    public ResponseEntity<byte[]> handleParseFailure(Exception e) {
        log.warn("request parse failed : {}", e.getMessage());
        return GoStylePlainTextErrors.badRequest(rootMessage(e));
    }

    /**
     * Go {@code core/mapping/unmarshaler.go} 的必填失败（{@code is not set} / {@code mustn't be nil}）。
     *
     * <p>⚠️ 走 400 text/plain 而<b>不是</b> 200 + 5001：article-api 没有 error handler。
     * 两条 message 形态不同但状态码相同，见 {@code CreateArticleReq#assertGoRequiredFields}。
     */
    @ExceptionHandler(GoRequiredFieldException.class)
    public ResponseEntity<byte[]> handleGoRequiredField(GoRequiredFieldException e) {
        log.warn("go-style required field missing : field={} explicitNull={}", e.getField(), e.isExplicitNull());
        return GoStylePlainTextErrors.badRequest(e.getMessage());
    }

    // ============== 4. 框架级错误：保留共享实现的字节形状 ==============

    /**
     * Go {@code rest/router/patrouter.go:73-79}：未注册路径 → 404。
     *
     * <p>body / Content-Type / nosniff 全部由共享实现
     * {@link GoStyleRouterErrorResponses} 给出（{@code "404 page not found\n"}）。
     *
     * <p>⚠️ <b>这条路径不经过鉴权</b>：{@code JwtAuthInterceptor} 的
     * {@code handler instanceof HandlerMethod} 守卫会让未注册路径放行。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<byte[]> handleNoResourceFound(NoResourceFoundException e) {
        log.warn("no handler for {} : {}", e.getResourcePath(), e.getMessage());
        return GoStyleRouterErrorResponses.notFound(e.getResourcePath());
    }

    /**
     * 🔴 DRIFT-001（已修）：<b>方法不匹配 ⇒ HTTP 404，不是 405</b>。
     *
     * <p><b>Go 侧证据链（三段，必须连起来读）</b>：
     * <pre>
     *   rest/router/patrouter.go:71-82
     *       allows, ok := pr.methodsAllowed(r.Method, reqPath)
     *       if !ok { pr.handleNotFound(w, r); return }        // 路径未注册 → 404
     *       if pr.notAllowed != nil {
     *           pr.notAllowed.ServeHTTP(w, r)                 // ← 走这里
     *       } else {
     *           w.Header().Set(allowHeader, allows)           // ← 这段**没有执行**
     *           w.WriteHeader(http.StatusMethodNotAllowed)    // ← 405 也**没有执行**
     *       }
     *
     *   rest/server.go:148-153   func WithCors(origin ...string) RunOption {
     *           server.router.SetNotAllowedHandler(cors.NotAllowedHandler(nil, origin...))
     *                                                        ↑ 把 notAllowed 换掉了
     *   rest/internal/cors/handlers.go:44-48
     *           if r.Method == http.MethodOptions { gw.WriteHeader(http.StatusNoContent) }
     *           else { gw.WriteHeader(http.StatusNotFound) }   // ← 404
     * </pre>
     *
     * <p>⇒ 因为 article-api <b>是四个 api 域里唯一用了 {@code rest.WithCors()} 的</b>
     * （实测 {@code article.go:33} 有，另三域的 {@code MustNewServer} 都是单参），
     * 它的 {@code notAllowed} 被 CORS handler 接管 ⇒ 方法不匹配输出
     * <b>404 + 空 body + 无 Content-Type + 无 {@code Allow}</b>。
     *
     * <h3>⚠️ 为什么<b>不</b>用共享的 {@link GoStyleRouterErrorResponses#methodNotAllowed}</h3>
     * <p>那个共享实现给的是 <b>405 + {@code Allow}</b>，它对 message / like / hot
     * <b>是正确的</b>（那三个域没开 {@code WithCors}，patrouter 的 else 分支真的会执行）。
     * ⇒ 改共享类会<b>同时改坏三个已验证域</b>。本方法<b>刻意与它分叉</b>，
     * 这正是 AGENTS.md §3.1「不要拿另一个域的实测结论当对照基准」——
     * 上游第一次实现就是照抄了 message-api 的 405，验证时被实测抓出。
     *
     * <h3>⚠️ Go 侧还有一处不对称，本实现<b>没有</b>照搬</h3>
     * <p>{@code patrouter.go:104-113} 的 {@code methodsAllowed} 遍历
     * {@code map[string]*search.Tree}，<b>顺序随机</b>。也就是说 Go 侧如果真的走 else 分支，
     * {@code Allow} 头的<b>值顺序</b>本身就是不确定的。
     * 现在 article-api 走的是 CORS handler（不输出 {@code Allow}），
     * 这个不确定性<b>恰好不可见</b>；本实现因此不引入「稳定的 Allow 顺序」这种
     * Go 侧根本不存在的确定性。
     *
     * <p>⚠️ <b>与「路径未注册」的 404 是两支不同形状</b>：
     * <ul>
     *   <li>路径未注册 → {@link GoStyleRouterErrorResponses#notFound}
     *       ⇒ 404 + {@code "404 page not found\n"} + text/plain + nosniff；</li>
     *   <li>路径命中、方法不对 → <b>本方法</b> ⇒ 404 + <b>空 body</b> + 无 Content-Type。</li>
     * </ul>
     * 两者状态码相同但 body 形状不同，客户端可据此区分（Go 侧亦然）。
     * CORS 头两者都有（{@code cors.NotAllowedHandler} 先调 {@code checkAndSetHeaders}）。
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("method not supported on article-api (Go 侧此路径为 404，见 DRIFT-001) : {}", e.getMessage());
        // ResponseEntity<Void> + 无 body ⇒ 容器不补 Content-Type，与 Go 的裸 WriteHeader(404) 等价
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    // ============== 5. 致命错误 / 兜底：Go RecoverHandler 的裸 500 ==============

    @ExceptionHandler(FatalError.class)
    public void handleFatal(FatalError e) {
        throw e;
    }

    /**
     * 未被任何 service 转换的 gRPC 错误 —— 对应 Go 的 <b>panic → recover</b>。
     *
     * <p>Go {@code rest/handler/recoverhandler.go:14-19}：
     * <pre>defer func(){ if r:=recover(); r!=nil { …; w.WriteHeader(500) } }()</pre>
     * ⇒ <b>裸 500，无 body</b>。不是 200 信封、不是 500 信封。
     *
     * <p>正常情况下<b>不该</b>走到这里：7 个 logic 的每个 RPC 调用点都有
     * {@code if err != nil { status.FromError → switch → 业务码 }}。
     * 只有「service 漏了转换」才会到这里，所以它同时是一道<b>兜底告警</b>。
     */
    @ExceptionHandler({StatusRuntimeException.class, StatusException.class})
    public ResponseEntity<Void> handleUnconvertedRpcStatus(Exception e) {
        Status.Code code = e instanceof StatusRuntimeException sre
                ? sre.getStatus().getCode()
                : ((StatusException) e).getStatus().getCode();
        String description = e instanceof StatusRuntimeException sre
                ? sre.getStatus().getDescription()
                : ((StatusException) e).getStatus().getDescription();
        log.error("UNCONVERTED gRPC status code={} desc={} —— "
                + "某个 logic 漏了 status→业务码 转换（Go 侧对应 panic→500）", code, description, e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    /**
     * 兜底 —— 同样对应 Go 的 {@code RecoverHandler}（<b>裸 500</b>）。
     *
     * <p>⚠️ <b>刻意不产出信封</b>：Go 侧 panic 之后 {@code w.WriteHeader(500)} 且<b>不写 body</b>。
     * 产出 {@code {"code":5001}} 会凭空造出一个 article 域里<b>语义不同</b>的码
     * （5001 在本域 = 「系统内部错误」的<b>业务</b>失败，HTTP 200）。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Void> handleAny(Exception e) {
        if (e instanceof FatalError fatal) {
            throw fatal;
        }
        log.error("unhandled exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    // ============== 工具 ==============

    private static ResponseEntity<Response> envelope(int code, String msg) {
        return ResponseEntity.status(HttpStatus.OK).body(new Response(code, msg, null));
    }

    private static ResponseEntity<Response> envelopeWithData(int code, String msg, Object data) {
        return ResponseEntity.status(HttpStatus.OK).body(new Response(code, msg, data));
    }

    /**
     * 取最内层异常的信息作为 {@code err.Error()}。
     *
     * <p>Go 的 {@code httpx.ErrorCtx} 写的是 <b>原始 err</b> 的 {@code Error()}；
     * Spring 这边拿到的是包装异常（{@code HttpMessageNotReadableException}
     * 把 Jackson 的 {@code JsonMappingException} 包在 cause 里），
     * 直接取 {@code getMessage()} 会把整个嵌套链写进 body。
     * 取最内层更接近 Go 的形状（Go 侧 {@code UnmarshalJsonReader} 返回的也是最内层那个 error）。
     */
    private static String rootMessage(Throwable e) {
        Throwable cur = e;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur.getMessage() == null ? cur.getClass().getSimpleName() : cur.getMessage();
    }
}