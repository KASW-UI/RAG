package com.rag.users.admin.api.advice;

import com.rag.common.response.Response;
import com.rag.users.common.errmsg.ErrorCode;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 全局响应包装 —— 与 user-api 端 GlobalResponseAdvice 同款。
 *
 * <p>把所有 Controller 返回值包成 {@code Response(code=200, msg="OK", data=body)}。
 *
 * <p><b>与 user-api 同款的幂等保护</b>：{@code GlobalExceptionHandler} 的
 * {@code @ExceptionHandler} 返回 {@code ResponseEntity<Response>}，而 Spring 的
 * {@code HttpEntityMethodProcessor} 继承 {@code writeWithMessageConverters}
 * 并会查询 advice chain，所以 {@code ResponseBodyAdvice} 对 {@code ResponseEntity} 生效。
 * 若无条件再包一层，错误响应顶层 code 会恒为 200，客户端的失败分支全部失效。
 * 详见 user-api 端 {@code ResponseEnvelopeAdviceTest} 的实测记录。
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
        // 已经是 envelope 的不再包；null body 也不能包（否则会把框架级错误状态码的
        // 空响应体篡改成 {"code":200,...}）。
        if (body == null || body instanceof Response) {
            return body;
        }
        return new Response(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMsg(), body);
    }
}
