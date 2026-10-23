package com.rag.favorite.favorite.api.advice;

import com.rag.common.response.Response;
import com.rag.favorite.favorite.common.errmsg.FavoriteErrorCode;
import com.rag.favorite.favorite.common.errmsg.FavoriteErrorMessages;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 全局响应包装 —— 把所有 Controller 返回值包成
 * {@code Response(code=200, msg="OK", data=body)}。
 *
 * <p><b>Go 出处</b>：{@code service/favorite/api/internal/handler/favorite/*.go} 的 7 个 handler
 * 统一走 {@code httpx.OkJsonCtx(r.Context(), w, &response.Response{Code: code, Msg: GetErrMsg(code), Data: resp})}，
 * 其中 {@code response.Response} 是
 * {@code service/common/response/response.go:3-7} 的
 * {@code {Code int `json:"code"`; Msg string `json:"msg"`; Data interface{} `json:"data"`}}。
 * <p>⇒ <b>三个字段恒定出现</b>（没有 {@code omitempty}）：成功时
 * {@code {"code":200,"msg":"OK","data":{...}}}，失败时
 * {@code {"code":5004,"msg":"未登录或登录已失效","data":null}}。
 *
 * <p><b>幂等保护</b>：{@link com.rag.favorite.favorite.api.advice.GlobalExceptionHandler} 的
 * {@code @ExceptionHandler} 返回 {@code ResponseEntity<Response>}，而 Spring 的
 * {@code HttpEntityMethodProcessor} 继承 {@code writeWithMessageConverters}
 * 并会查询 advice chain，所以 {@code ResponseBodyAdvice} 对 {@code ResponseEntity} 生效。
 * 若无条件再包一层，错误响应顶层 code 会恒为 200，客户端的失败分支全部失效。
 *
 * <p><b>⚠️ 非 JSON 响应必须原样透传</b>：Go 的 {@code httpx.OkJson} 固定写
 * {@code Content-Type: application/json}，而框架级错误（404 / 405）走的是
 * {@code http.Error} / 裸状态码 —— 那些不是 envelope。
 * 判定条件是「这次写出的到底是不是 JSON」，而不是「body 是不是字符串」，
 * 否则 404 / 405 这类同样走 text/plain 的分支会被漏判。
 */
@RestControllerAdvice
public class GlobalResponseAdvice implements ResponseBodyAdvice<Object> {

    /**
     * Go 的 JSON Content-Type —— <b>带 charset</b>，与 Spring 默认写出的不带 charset 有差。
     *
     * <p><b>Go 出处</b>：{@code rest/internal/header/headers.go:17}
     * <pre>
     * ContentTypeJson = "application/json; charset=utf-8"
     * </pre>
     * 且 {@code rest/httpx/responses.go:182} 的 {@code doWriteJson} 对<b>每一个</b>业务响应
     * <b>无条件</b> {@code w.Header().Set(ContentType, header.ContentTypeJson)}。
     *
     * <p>⚠️ Spring 的 Jackson 转换器默认发的是 {@code application/json}（不带 charset），
     * 对多数客户端无影响，但它是<b>可观察的 L1 头部差异</b>，且修起来没有成本。
     * 这里在写出前把响应头钉死成 Go 的取值。
     *
     * <p>⚠️ <b>为什么能钉死</b>：{@code AbstractHttpMessageConverter.addDefaultHeaders} 只在
     * {@code headers.getContentType() == null} 时才填默认值 ⇒ 这里先写入，
     * 转换器不会覆盖。{@code AbstractMessageConverterMethodProcessor} 里的
     * {@code isContentTypePreset} 是在 advice <b>之前</b>算的，所以转换器仍按自己选定的
     * media type 序列化（仍是 JSON），只是最终响应头用我们钉的值。
     */
    private static final MediaType GO_JSON_CONTENT_TYPE =
            MediaType.parseMediaType("application/json; charset=utf-8");

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        // 【DRIFT-006】非 JSON 响应必须原样透传，不能包 envelope，也不能改它的 Content-Type。
        // 404 / 405 在 Go 侧走的是 http.Error / 裸状态码，本就不是 JSON。
        if (selectedContentType != null
                && !MediaType.APPLICATION_JSON.isCompatibleWith(selectedContentType)) {
            return body;
        }
        // Go 无条件写 application/json; charset=utf-8（对成功与错误响应一视同仁）。
        response.getHeaders().setContentType(GO_JSON_CONTENT_TYPE);
        // 已经是 envelope 的不再包；null body 也不能包（否则会把框架级错误状态码的
        // 空响应体篡改成 {"code":200,...}）。
        if (body == null || body instanceof Response) {
            return body;
        }
        int code = FavoriteErrorCode.SUCCESS.getCode();
        return new Response(code, FavoriteErrorMessages.getMsg(code), body);
    }
}
