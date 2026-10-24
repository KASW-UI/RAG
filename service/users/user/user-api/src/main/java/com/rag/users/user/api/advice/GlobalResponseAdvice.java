package com.rag.users.user.api.advice;

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
 * 全局响应包装 —— @RestControllerAdvice + ResponseBodyAdvice。
 *
 * 对应 Go 端每个 handler.go 末尾手动 {@code httpx.OkJson(w, &response.Response{...})}。
 *
 * <p>框架职责：
 * <ul>
 *   <li>Spring ResponseBodyAdvice 在 Controller 返回值写入 response body 之前拦截</li>
 *   <li>自动把任意 Controller 返回值包成 {@code {code, msg, data}}（统一响应格式）</li>
 *   <li>取代了 Go 端 LogicResult + handler 层手动包 Response 的两层套娃</li>
 *   <li>与 {@link GlobalExceptionHandler} 配合：异常路径走业务码 + HTTP 状态码，正常路径走 {@code data} 字段</li>
 * </ul>
 *
 * <p>L1 wire 契约冻结：成功路径 {@code {code: 200, msg: "OK", data: <controller 返回值>}}；
 * 失败路径 {@code {code: <业务码>, msg: <提示语>, data: null}}（由
 * {@link GlobalExceptionHandler} 直接产出）。两者都<b>只包一层</b>。
 *
 * <p><b>为什么必须在 beforeBodyWrite 里判空并放行 {@link Response}</b>：
 * {@code GlobalExceptionHandler} 的所有 {@code @ExceptionHandler} 返回
 * {@code ResponseEntity<Response>}。Spring 的 {@code HttpEntityMethodProcessor}
 * <b>并没有覆写</b> {@code writeWithMessageConverters}，而是继承
 * {@code AbstractMessageConverterMethodProcessor} 的实现；后者会查询 advice chain，
 * 因此 {@code ResponseBodyAdvice} 对 {@code ResponseEntity} 同样生效。
 * 若这里无条件再包一层，错误响应会变成
 * {@code {"code":200,"msg":"OK","data":{"code":1003,...}}}——
 * 顶层 code 恒为 200，所有按 {@code code != 200} 判定失败的客户端会全部失效。
 * 该行为已由 {@code ResponseEnvelopeAdviceTest} 实测确认并锁定为回归用例。
 */
@RestControllerAdvice
public class GlobalResponseAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        // 拦截所有 Controller 返回值；Controller 自己抛异常由 GlobalExceptionHandler 处理
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        // 已经是 envelope 的（@ExceptionHandler 返回的 ResponseEntity 的 body）不再包；
        // null body（如框架级错误直接返回无 body 的 ResponseEntity）也不能包，
        // 否则会把 400/405/415 的空响应体篡改成 {"code":200,...}。
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
