package com.rag.hot.hot.api.advice;

import com.rag.common.exception.BusinessException;
import com.rag.common.http.GoStyleRouterErrorResponses;
import com.rag.common.response.Response;
import com.rag.hot.hot.common.errmsg.HotErrorCode;
import com.rag.hot.hot.common.errmsg.HotErrorMessages;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/**
 * 全局异常处理器 —— 对应 Go {@code hotcenter.go:53-66} 的 {@code httpx.SetErrorHandler}：
 *
 * <pre>
 *   httpx.SetErrorHandler(func(err error) (int, interface{}) {
 *       code := hotcommon.ErrorServerCommon          // 5001
 *       msg  := hotcommon.GetErrMsg(code)           // "internal server error"
 *       var codeErr *hotcommon.CodeError
 *       if errors.As(err, &amp;codeErr) {             // errors.As：可穿透包装
 *           code = codeErr.Code
 *           msg  = codeErr.Msg
 *       }
 *       return http.StatusOK, &amp;response.Response{Code: code, Msg: msg, Data: nil}
 *   })
 * </pre>
 *
 * <h3>⇒ 本服务的响应矩阵</h3>
 * <table border="1">
 *   <caption>hot-api 的三类响应形状</caption>
 *   <tr><th>场景</th><th>HTTP</th><th>body</th></tr>
 *   <tr><td>成功</td><td><b>200</b></td><td>{@code {"code":200,"msg":"OK","data":{...}}}</td></tr>
 *   <tr>
 *     <td>业务失败（hot-rpc 报错 / nil 响应、query 解析失败、框架层异常）</td>
 *     <td><b>200</b></td>
 *     <td>{@code {"code":5001,"msg":"internal server error","data":null}}</td>
 *   </tr>
 *   <tr>
 *     <td>404 / 405（路由层，不进任何 handler）</td>
 *     <td>404 / 405</td>
 *     <td><b>裸响应，无信封</b>（Go router 直出）</td>
 *   </tr>
 * </table>
 *
 * <h3>⚠️ 错误码必须是 hot 域自己的表，不能复用 users / article</h3>
 * hot 的 5002/5003/5004 与 {@code users-common} / {@code article-common} 的同号码
 * <b>语义冲突且语言不同</b>（hot 是英文）。本模块只用 {@link HotErrorCode} + {@link HotErrorMessages}。
 *
 * <h3>⚠️ 为什么 {@code decideStatus} 恒为 200，而不像 user-api 那样按 code 决定</h3>
 * Go 侧 {@code SetErrorHandler} 的返回值<b>字面量就是</b> {@code http.StatusOK}，
 * 不存在「code → 状态码」的映射。照抄 user-api 的 {@code 401/403/500} 判定
 * 会让「业务码 5001」变成「HTTP 500」，客户端按 {@code code} 判失败的老逻辑仍然工作，
 * 但按 HTTP 状态码判的一律崩 —— 那是新增的偏离。
 *
 * <h3>⚠️ 为什么 404 / 405 <b>不</b>包信封</h3>
 * Go 的 {@code router/patrouter.go:59-83} 在「路径无匹配」「方法不匹配」时
 * <b>根本不进任何路由的 chain</b>（不经过 handler、不经过 auth、不经过保护层），
 * 直出 {@code http.NotFound} / {@code Allow + 405}。
 * 复刻由 {@link GoStyleRouterErrorResponses} 承担（字节形状与
 * {@code com.rag.common} 的其它域共用同一份实现，不在本模块重复）。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 业务异常 —— Go 侧对应 {@code *hotcommon.CodeError} 被 {@code errors.As} 命中的分支。
     * <b>HTTP 恒 200</b>，{@code data} 恒 {@code null}。
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Response> onBusinessException(BusinessException e) {
        return envelope(e.getCode(), e.getMessage());
    }

    /**
     * query / path 标量转换失败 —— Go 侧对应 {@code httpx.Parse} 返回错误
     * （{@code getHotArticlesHandler.go:18-21} → {@code httpx.ErrorCtx}）。
     *
     * <p>Go 的解析错误不是 {@code *CodeError} ⇒ {@code SetErrorHandler} 取默认 5001
     * ⇒ <b>HTTP 200 + {code:5001}</b>，而不是 400。
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Response> onTypeMismatch(MethodArgumentTypeMismatchException e) {
        LOG.debug("hot-api query parse failed: {}", e.getMessage());
        return serverCommon();
    }

    /**
     * 未捕获异常的兜底 —— Go 侧 go-zero 的 {@code Recover} 中间件
     * （{@code rest/config.go:23 Middlewares.Recover default=true}）
     * 捕获后同样交给 {@code SetErrorHandler}，产出 200 + 5001。
     *
     * <p>包括从 service 冒出来的 {@link StatusRuntimeException}：
     * 本组件的两条 fatal 已在 service 内转成 {@link BusinessException}，
     * 能冒到这里的是意料之外的 gRPC 异常，出口仍是 5001。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Response> onAnyException(Exception e) {
        LOG.warn("hot-api unhandled exception: {}", e.toString());
        return serverCommon();
    }

    /**
     * 404 —— Go {@code router/patrouter.go:73} → {@code http.NotFound}：
     * {@code 404 page not found\n} + {@code Content-Type: text/plain; charset=utf-8}
     * + {@code X-Content-Type-Options: nosniff}。<b>无信封</b>。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<byte[]> onNotFound(NoResourceFoundException e) {
        return GoStyleRouterErrorResponses.notFound(e.getResourcePath());
    }

    /**
     * 405 —— Go {@code router/patrouter.go:79-82}：裸状态码 + {@code Allow}，<b>无 body</b>。
     *
     * <p>本组件唯一路由是 {@code GET /hot/v1/articles}，所以除该路径外的其它方法都是 405。
     * {@code Allow} 的值顺序在 Go 侧是遍历 map 得来的（不确定），这里只有一个值，不涉及该问题。
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Void> onMethodNotAllowed(HttpRequestMethodNotSupportedException e) {
        return GoStyleRouterErrorResponses.methodNotAllowed(List.of("GET"));
    }

    /** Go {@code SetErrorHandler} 的默认分支：{@code code=5001} + {@code Data=nil}。 */
    private ResponseEntity<Response> serverCommon() {
        int code = HotErrorCode.ERROR_SERVER_COMMON.getCode();
        return envelope(code, HotErrorMessages.getMsg(code));
    }

    /** 业务码分支：{@code code}/{@code msg} 取自异常，{@code data} 恒 null。 */
    private ResponseEntity<Response> envelope(int code, String msg) {
        return ResponseEntity.status(HttpStatus.OK).body(new Response(code, msg, null));
    }
}