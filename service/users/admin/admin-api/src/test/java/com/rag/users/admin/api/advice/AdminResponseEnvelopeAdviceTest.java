package com.rag.users.admin.api.advice;

import com.rag.common.exception.BusinessException;
import com.rag.users.common.errmsg.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/**
 * admin-api 的响应包裹契约测试 —— 与 user-api 端
 * {@code ResponseEnvelopeAdviceTest} 同款，覆盖的是同一个被实测确认过的陷阱。
 *
 * <p>背景：{@code GlobalResponseAdvice.supports()} 无条件返回 true，而
 * {@code GlobalExceptionHandler} 的所有 {@code @ExceptionHandler} 返回
 * {@code ResponseEntity<Response>}。Spring 的 {@code HttpEntityMethodProcessor}
 * 继承 {@code writeWithMessageConverters} 并会查询 advice chain，
 * 因此错误响应会被二次包裹成
 * {@code {"code":200,"msg":"OK","data":{"code":1003,...}}}。
 * 该行为已在 user-api 端实测确认，admin-api 端结构完全相同，一并锁死。
 */
class AdminResponseEnvelopeAdviceTest {

    @RestController
    static class ProbeController {

        @GetMapping("/probe/ok")
        Map<String, Object> ok() {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("uid", 7L);
            return body;
        }

        @GetMapping("/probe/boom")
        Map<String, Object> boom() {
            throw new BusinessException(
                    ErrorCode.ERROR_USER_NOT_EXIST.getCode(),
                    ErrorCode.ERROR_USER_NOT_EXIST.getMsg());
        }
    }

    private MockMvc mvc() {
        return standaloneSetup(new ProbeController())
                .setControllerAdvice(new GlobalResponseAdvice(), new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("正常路径：{code:200, msg:OK, data:<返回值>}")
    void successPath_isWrappedOnce() throws Exception {
        MvcResult r = mvc().perform(get("/probe/ok").accept(MediaType.APPLICATION_JSON)).andReturn();
        String json = r.getResponse().getContentAsString();
        assertTrue(json.contains("\"code\":200") && json.contains("\"uid\":7"),
                "成功路径应只包一层 envelope，实际 = " + json);
    }

    @Test
    @DisplayName("错误路径：顶层 code 必须是业务码，不能被包成 200")
    void errorPath_isNotDoubleWrapped() throws Exception {
        MvcResult r = mvc().perform(get("/probe/boom").accept(MediaType.APPLICATION_JSON)).andReturn();
        String json = r.getResponse().getContentAsString();
        assertTrue(json.contains("\"code\":1003"),
                "错误响应顶层 code 必须是 1003，实际 = " + json);
        assertFalse(json.contains("\"data\":{\"code\""),
                "错误响应不应出现 data 内再嵌 envelope，实际 = " + json);
    }

    @Test
    @DisplayName("业务失败一律 HTTP 200（与参考侧 envelope 语义 1:1），不用 HTTP 状态码表达业务成败")
    void businessError_usesHttp200() throws Exception {
        MvcResult r = mvc().perform(get("/probe/boom").accept(MediaType.APPLICATION_JSON)).andReturn();
        assertTrue(r.getResponse().getStatus() == 200,
                "业务码 1003 应走 HTTP 200，实际 = " + r.getResponse().getStatus());
    }
}