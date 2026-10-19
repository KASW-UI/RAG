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
 * <p>L1 wire 契约冻结：成功路径 {@code {code: 200, msg: "OK", data: <controller 返回值>}}。
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
        // body 已经是被 Controller 返回的 DTO；包成 envelope
        // （如果 Controller 已经返回 Response，beforeBodyWrite 不会再被调用；
        //   此处不重复包装，因为 Spring 会按返回值类型 chain 多个 advice，
        //   真正的 Response 包法在 GlobalExceptionHandler.handle 方法里完成）
        return new Response(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMsg(), body);
    }
}
