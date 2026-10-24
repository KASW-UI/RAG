package com.rag.users.user.api.advice;

import com.rag.common.exception.AuthenticationException;
import com.rag.common.exception.BusinessException;
import com.rag.users.common.errmsg.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/**
 * <b>鉴权层 HTTP 状态码契约测试</b>（DRIFT-004 回归）。
 *
 * <p><b>被锁住的语义</b>：同一个业务码 1006，<b>异常类型</b>决定 HTTP 状态码：
 * <table border="1">
 *   <caption>Go 出处</caption>
 *   <tr><th>路径</th><th>Go</th><th>RAG</th></tr>
 *   <tr>
 *     <td>鉴权层失败<br>（缺 Bearer / 签名错 / 过期 / 黑名单）</td>
 *     <td>{@code rest.WithJwt} → {@code authhandler.go:106 unauthorized()} → <b>401</b>；
 *         {@code checkblacklistmiddleware.go:38} → <b>401</b></td>
 *     <td>{@link AuthenticationException} → <b>401</b></td>
 *   </tr>
 *   <tr>
 *     <td>业务层失败</td>
 *     <td>Logic 返回业务码 → {@code httpx.OkJson} 恒 <b>200</b></td>
 *     <td>{@link BusinessException} → <b>200</b></td>
 *   </tr>
 * </table>
 *
 * <p><b>为什么必须有这条测试</b>：修复前 user-api / admin-api 的 {@code decideStatus(code)}
 * 无条件返回 {@code HttpStatus.OK}，鉴权失败被降级成 <b>HTTP 200</b>。而那段代码的注释
 * 论证「401 只由鉴权层的 Servlet Filter 写出，不经过本处理器」—— 本工程<b>根本没有</b>
 * 这样的 Filter，鉴权是 {@code HandlerInterceptor} 抛异常、<b>必然</b>经过本处理器。
 * 也就是说：一个引用了不存在的机制的论证，把一个 wire 级缺陷"论证"成了设计。
 *
 * <p>另一条同样重要的性质：<b>状态码不能由业务码推断</b>。1006 在鉴权层与业务层都会出现，
 * 所以修复采用「加一个 {@code AuthenticationException} 子类型」而不是「在 decideStatus 里
 * 判断 code == 1006 → 401」。本测试的第 3 条正是这个不变量的守卫。
 */
class AuthenticationHttpStatusTest {

    @RestController
    static class ProbeController {

        /** 模拟拦截器抛出的鉴权失败 */
        @GetMapping("/probe/auth")
        Object auth() {
            throw new AuthenticationException(
                    ErrorCode.ERROR_TOKEN_RUNTIME.getCode(),
                    ErrorCode.ERROR_TOKEN_RUNTIME.getMsg());
        }

        /** 模拟 Service 层抛出的业务失败（同一个业务码 1006） */
        @GetMapping("/probe/business")
        Object business() {
            throw new BusinessException(
                    ErrorCode.ERROR_TOKEN_RUNTIME.getCode(),
                    ErrorCode.ERROR_TOKEN_RUNTIME.getMsg());
        }

        /** 模拟 logout 无 jwt_token → 1004。Go 侧这条走 envelope，HTTP 恒 200 */
        @GetMapping("/probe/token-missing")
        Object tokenMissing() {
            throw new BusinessException(
                    ErrorCode.ERROR_TOKEN_NOT_EXIST.getCode(),
                    ErrorCode.ERROR_TOKEN_NOT_EXIST.getMsg());
        }
    }

    private MockMvc mvc() {
        return standaloneSetup(new ProbeController())
                .setControllerAdvice(new GlobalResponseAdvice(), new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("DRIFT-004：鉴权层失败必须是 HTTP 401，且 body 仍是完整 envelope")
    void authenticationFailure_returns401() throws Exception {
        MvcResult r = mvc().perform(get("/probe/auth").accept(MediaType.APPLICATION_JSON))
                .andReturn();

        assertStatus(r, 401);

        String json = r.getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertTrue(
                json.contains("\"code\":1006")
                        && json.contains("\"msg\":\"TOKEN已过期\"")
                        && json.contains("\"data\":null"),
                "鉴权失败响应体应为 {code,msg,data} 完整 envelope，实际 = " + json);
    }

    @Test
    @DisplayName("DRIFT-004：同一业务码 1006 在业务层仍是 HTTP 200 —— 状态码由类型而非业务码决定")
    void businessFailureWithSameCode_returns200() throws Exception {
        MvcResult r = mvc().perform(get("/probe/business").accept(MediaType.APPLICATION_JSON))
                .andReturn();

        assertStatus(r, 200);   // Service 层的 1006 必须保持 200 —— 对齐 Go 的 httpx.OkJson

        String json = r.getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertTrue(json.contains("\"code\":1006"), json);
    }

    @Test
    @DisplayName("logout 无 token（1004）走 envelope + 200：Go 侧该分支在鉴权层之后，业务码由 body 表达")
    void tokenMissing_returns200() throws Exception {
        MvcResult r = mvc().perform(get("/probe/token-missing").accept(MediaType.APPLICATION_JSON))
                .andReturn();

        assertStatus(r, 200);
        org.junit.jupiter.api.Assertions.assertTrue(
                r.getResponse().getContentAsString().contains("\"code\":1004"), 
                r.getResponse().getContentAsString());
    }

    private static void assertStatus(MvcResult r, int expectedStatus) {
        org.junit.jupiter.api.Assertions.assertEquals(
                expectedStatus,
                r.getResponse().getStatus(),
                "实际状态码 = " + r.getResponse().getStatus() + "，body = " + safeBody(r));
    }

    private static String safeBody(MvcResult r) {
        try {
            return r.getResponse().getContentAsString();
        } catch (Exception e) {
            return "<unreadable>";
        }
    }
}
