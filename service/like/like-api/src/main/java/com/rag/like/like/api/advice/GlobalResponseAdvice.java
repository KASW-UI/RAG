package com.rag.like.like.api.advice;

import com.rag.common.response.Response;
import com.rag.like.like.common.errmsg.LikeErrorCode;
import com.rag.like.like.common.errmsg.LikeErrorMessages;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 全局响应包装 —— 对应 Go 端 {@code likecenter.go:41-47} 注册的
 * {@code httpx.SetOkHandler}：
 * <pre>
 * httpx.SetOkHandler(func(ctx context.Context, v interface{}) interface{} {
 *     return &amp;response.Response{
 *         Code: errmsg.Success,                 // 200
 *         Msg:  errmsg.GetErrMsg(errmsg.Success),   // "OK"
 *         Data: v,
 *     }
 * })
 * </pre>
 *
 * <p><b>L1 wire 契约</b>：成功路径恒为
 * {@code HTTP 200 + {"code":200,"msg":"OK","data":<controller 返回值>}}，
 * 且<b>只包一层</b>。失败路径由 {@link GlobalExceptionHandler} 直接产出完整 envelope，
 * 不经过本类。
 *
 * <p><b>两条必须判空放行的分支（少一条就会产生假绿 / 假红）</b>：
 * <ol>
 *   <li><b>{@code body == null}</b>：{@code @ExceptionHandler} 返回的无 body 响应
 *       （本模块的 401 / 404 / 405 / 415）若被包成
 *       {@code {"code":200,"msg":"OK","data":null}}，
 *       等于<b>凭空造出一个业务码</b>，把「鉴权失败」伪装成「成功但无数据」。
 *       Spring 的 {@code HttpEntityMethodProcessor} 会查 advice chain，
 *       连 {@code @ExceptionHandler} 的 {@code ResponseEntity} 也在链上
 *       （AGENTS.md §3.3 的 DRIFT-FRAMEWORK-001 就是这个形态）。</li>
 *   <li><b>{@code body instanceof Response}</b>：错误路径的 envelope 已成型，
 *       再包一层会变成
 *       {@code {"code":200,"msg":"OK","data":{"code":5001,...}}} ——
 *       <b>顶层 code 恒 200</b>，所有按 {@code code != 200} 判失败的客户端全部失效。</li>
 * </ol>
 *
 * <p><b>非 JSON 响应透传</b>：Go 的 envelope 只由 {@code httpx.OkJsonCtx} 产出
 * （固定写 {@code Content-Type: application/json}）；框架级错误走别的分支。
 * 判定条件因此是「这次写出的到底是不是 JSON」，而不是「body 是不是 String」。
 */
@RestControllerAdvice
public class GlobalResponseAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (body == null || body instanceof Response) {
            return body;
        }
        if (selectedContentType != null
                && !MediaType.APPLICATION_JSON.isCompatibleWith(selectedContentType)) {
            return body;
        }
        int success = LikeErrorCode.SUCCESS.getCode();
        return new Response(success, LikeErrorMessages.getMsg(success), body);
    }
}