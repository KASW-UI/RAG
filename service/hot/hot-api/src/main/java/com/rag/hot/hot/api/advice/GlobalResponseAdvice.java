package com.rag.hot.hot.api.advice;

import com.rag.common.response.Response;
import com.rag.hot.hot.common.errmsg.HotErrorCode;
import com.rag.hot.hot.common.errmsg.HotErrorMessages;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 全局响应包装 —— 对应 Go {@code hotcenter.go:45-51} 的 {@code httpx.SetOkHandler}：
 *
 * <pre>
 *   httpx.SetOkHandler(func(ctx context.Context, v interface{}) interface{} {
 *       return &amp;response.Response{
 *           Code: hotcommon.Success,                       // 200
 *           Msg:  hotcommon.GetErrMsg(hotcommon.Success), // "OK"
 *           Data: v,
 *       }
 *   })
 * </pre>
 *
 * <h3>L1 wire 契约</h3>
 * 成功路径恒为
 * {@code HTTP 200 + {"code":200,"msg":"OK","data":<controller 返回值>}}，且<b>只包一层</b>。
 * 失败路径由 {@link GlobalExceptionHandler} 直接产出完整信封，不经过本类。
 *
 * <h3>两条必须判空放行的分支（少一条就产生假绿 / 假红）</h3>
 * <ol>
 *   <li><b>{@code body == null}</b>：{@code @ExceptionHandler} 返回的无 body 响应
 *       （本模块的 404 / 405 / 保护层裸状态码）若被包成
 *       {@code {"code":200,"msg":"OK","data":null}}，
 *       等于<b>凭空造出一个业务码</b>，把「路由不存在」伪装成「成功但无数据」。
 *       Spring 的 {@code HttpEntityMethodProcessor} 会查 advice chain，
 *       连 {@code @ExceptionHandler} 的 {@code ResponseEntity} 也在链上
 *       （AGENTS.md §3.3 的 DRIFT-FRAMEWORK-001 就是这个形态）。</li>
 *   <li><b>{@code body instanceof Response}</b>：错误路径的信封已成型，
 *       再包一层会变成 {@code {"code":200,"msg":"OK","data":{"code":5001,...}}} ——
 *       <b>顶层 code 恒 200</b>，所有按 {@code code != 200} 判失败的客户端全部失效。</li>
 * </ol>
 *
 * <h3>非 JSON 响应透传</h3>
 * Go 的 envelope 只由 {@code httpx.OkJsonCtx} 产出（固定 {@code Content-Type: application/json}），
 * 框架级错误走别的分支。判定条件因此是「这次写出的到底是不是 JSON」，
 * 而不是「body 是不是 String」—— 否则保护层的裸 503 / 超时的 text/plain 会被卷进来。
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
        int success = HotErrorCode.SUCCESS.getCode();
        return new Response(success, HotErrorMessages.getMsg(success), body);
    }
}