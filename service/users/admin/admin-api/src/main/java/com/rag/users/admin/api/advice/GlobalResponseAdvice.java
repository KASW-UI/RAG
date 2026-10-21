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
        // 【DRIFT-006】非 JSON 响应必须原样透传，不能包 envelope。
        //
        // Go 出处：go-zero rest/httpx/responses.go 的 doHandleError 在 handler == nil 时走
        //   http.Error(w, err.Error(), http.StatusBadRequest) —— 产出的是
        //   Content-Type: text/plain; charset=utf-8 + 裸文本，**没有 {code,msg,data} 结构**。
        //   envelope 只由 httpx.OkJson 产出，而它固定写 Content-Type: application/json
        //   （同文件 doWriteJson）。
        //
        // 因此判定条件是「这次写出的到底是不是 JSON」，而不是「body 是不是字符串」——
        // 否则 405/415 这类同样走 text/plain 的分支会被漏判。
        if (selectedContentType != null
                && !MediaType.APPLICATION_JSON.isCompatibleWith(selectedContentType)) {
            return body;
        }
        return new Response(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMsg(), body);
    }
}
