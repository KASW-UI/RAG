package com.rag.comment.comment.api.advice;

import com.rag.comment.comment.common.errmsg.CommentErrorCode;
import com.rag.comment.comment.common.errmsg.CommentErrorMessages;
import com.rag.common.response.Response;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 全局响应包装 —— 对应 Go {@code service/comment/api/commentservice.go:39-45}
 * 里注册的 {@code httpx.SetOkHandler}。
 *
 * <p><b>Go 出处（逐字）</b>：
 * <pre>
 * httpx.SetOkHandler(func(ctx context.Context, v interface{}) interface{} {
 *     return &response.Response{
 *         Code: errmsg.Success,                 // 200
 *         Msg:  errmsg.GetErrMsg(errmsg.Success), // "OK"
 *         Data: v,
 *     }
 * })
 * </pre>
 *
 * <p><b>L1 wire 契约</b>：成功路径恒为
 * {@code HTTP 200 + {"code":200,"msg":"OK","data":<controller 返回值>}}，
 * 且<b>只包一层</b>。失败路径由 {@link CommentGlobalExceptionHandler} 直接产出完整 envelope，
 * 不经过本类。
 *
 * <p><b>两条必须判空放行的分支（少一条就会产生假绿 / 假红）</b>：
 * <ol>
 *   <li><b>{@code body == null}</b>：{@code @ExceptionHandler} 返回的无 body 响应
 *       （本模块的 <b>401 空 body</b>、415、405）若被包成
 *       {@code {"code":200,"msg":"OK","data":null}}，就等于凭空造出一个业务码，
 *       把「鉴权失败」伪装成「成功但无数据」。</li>
 *   <li><b>{@code body instanceof Response}</b>：错误路径的 envelope 已成型，
 *       再包一层会变成 {@code {"code":200,"msg":"OK","data":{"code":...,...}}} ——
 *       顶层 code 恒 200，所有按 {@code code != 200} 判失败的客户端全部失效。</li>
 * </ol>
 *
 * <p><b>非 JSON 响应透传</b>：Go 的 envelope 只由 {@code httpx.OkJson} 产出
 * （固定写 {@code Content-Type: application/json}）；框架级错误走别的分支。
 * 判定条件因此是「这次写出的到底是不是 JSON」。
 */
@RestControllerAdvice
public class CommentGlobalResponseAdvice implements ResponseBodyAdvice<Object> {

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
        int success = CommentErrorCode.SUCCESS.getCode();
        return new Response(success, CommentErrorMessages.getMsg(success), body);
    }
}