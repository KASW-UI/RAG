package com.rag.users.user.api.advice;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/**
 * 响应包裹契约的<b>定性</b>测试（characterization test）。
 *
 * <p><b>要回答的问题</b>：{@code GlobalResponseAdvice} 的 {@code supports()} 无条件返回 true，
 * {@code beforeBodyWrite} 无条件包一层；而 {@code GlobalExceptionHandler} 的所有
 * {@code @ExceptionHandler} 返回的是 {@code ResponseEntity<Response>}。
 * {@code GlobalResponseAdvice} 里那句注释「如果 Controller 已经返回 Response，beforeBodyWrite
 * 不会再被调用」是否为真？若为假，则每个错误响应都会被二次包裹成
 * {@code {"code":200,"msg":"OK","data":{"code":1003,...}}}，
 * 所有按 {@code code != 200} 判断失败的客户端会全部失效。
 *
 * <p>历史背景：本次行为一致性审计中，两侧全部测试都 mock 掉了数据层且没有任何
 * {@code @WebMvcTest} / {@code MockMvc}，因此 Web 层行为从未被断言过 —— 这正是该问题
 * 能长期潜伏的原因。本测试把 Web 层纳入回归网。
 *
 * <p>用 {@code standaloneSetup} 而非 {@code @WebMvcTest}：本测试只关心
 * {@code ResponseBodyAdvice} 与 {@code @ExceptionHandler} 返回 {@code ResponseEntity}
 * 之间的相互作用，与 Redis / Nacos / gRPC 等基础设施无关，standalone 能以最小依赖
 * 忠实复现 {@code RequestMappingHandlerAdapter} 对 advice 的装配
 * （{@code setControllerAdvice} 同时收集 {@code ControllerAdvice} 与 {@code ResponseBodyAdvice}）。
 */
class ResponseEnvelopeAdviceTest {

    /** 探针控制器：只为触发两条路径——正常返回与抛业务异常。 */
    @RestController
    static class ProbeController {

        @GetMapping("/probe/ok")
        Map<String, Object> ok() {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("uid", 42L);
            body.put("username", "jason");
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
    @DisplayName("正常路径：响应被包成 {code:200, msg:OK, data:<控制器返回值>}")
    void successPath_isWrappedOnce() throws Exception {
        MvcResult r = mvc().perform(get("/probe/ok").accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andReturn();

        String json = r.getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertTrue(
                json.contains("\"code\":200")
                        && json.contains("\"msg\":\"OK\"")
                        && json.contains("\"username\":\"jason\""),
                "成功路径应只包一层 envelope，实际 = " + json);
    }

    @Test
    @DisplayName("错误路径：@ExceptionHandler 返回的 ResponseEntity<Response> 不得被二次包裹")
    void errorPath_isNotDoubleWrapped() throws Exception {
        MvcResult r = mvc().perform(get("/probe/boom").accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andReturn();

        String json = r.getResponse().getContentAsString();

        // 关键断言：错误响应顶层的 code 必须是业务码 1003，而不是 envelope 的 200。
        // 若实际是 {"code":200,"msg":"OK","data":{"code":1003,...}}，说明 advice 二次包裹了。
        org.junit.jupiter.api.Assertions.assertTrue(
                json.contains("\"code\":1003"),
                "错误响应顶层 code 必须是业务码 1003（未被二次包裹），实际 = " + json);

        org.junit.jupiter.api.Assertions.assertFalse(
                json.contains("\"data\":{\"code\""),
                "错误响应不应出现 data 内再嵌一层 envelope，实际 = " + json);
    }
}