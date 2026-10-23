package com.rag.users.user.api;

import com.rag.users.user.api.advice.GlobalExceptionHandler;
import com.rag.users.user.api.advice.GlobalResponseAdvice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * <p><b>DRIFT-USERAPI-007 的守门网</b>：兜底异常（Go 的 panic）必须是
 * <b>HTTP 500 + 完整 envelope</b>，不是空 body。
 *
 * <h3>Go 出处（全文 23 行，逐行核对）</h3>
 * {@code go-zero@v1.10.2/rest/handler/recoverhandler.go:12-23}
 * <pre>
 *   func RecoverHandler(next http.Handler) http.Handler {
 *       return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
 *           defer func() {
 *               if result := recover(); result != nil {
 *                   internal.Error(r, fmt.Sprintf("%v\n%s", result, debug.Stack()))
 *                   w.WriteHeader(http.StatusInternalServerError)   // ← 只写状态码
 *               }                                                // ← 没有任何 w.Write(...)
 *           }()
 *           next.ServeHTTP(w, r)
 *       })
 *   }
 * </pre>
 * ⇒ Go 返回 <b>HTTP 500 + 空 body</b>，连 {@code Content-Type} 都不是 handler 设的。
 *
 * <h3>RAG 侧（本类锁定的是「已签字保留」的行为）</h3>
 * {@code GlobalExceptionHandler#handleAny} ⇒ <b>HTTP 500 + {@code {"code":5001,"msg":"系统内部错误","data":null}}</b>。
 *
 * <p><b>⚠️ 这是「有意偏离」，不是漏对齐</b> —— 完整论证见
 * {@code docs/DRIFT/users/user/api.md} 第四轮 §2。要点：空 body 的 500 对客户端
 * 比「带业务码的 500」<b>更难消费</b>（按 body 结构统一解析的客户端会在这里踩空，
 * 恰好就是原台账「Must Preserve」栏写的那句话）。本类的作用是
 * <b>把这个已签字的行为钉死，防止它在无人察觉时漂走</b>。
 *
 * <p><b>零容器依赖</b>：用 {@code standaloneSetup} + 真 advice，4 秒内跑完
 * （理由同 {@code AvatarHistoryIgnoresBodyTest}，见该类 javadoc）。
 */
class PanicFallbackResponseShapeTest {

    /** 真实业务里 panic/未捕获异常从各 Service 冒泡；这里用一个只会抛的端点把路径打通。 */
    @RestController
    static class ExplodingController {
        @GetMapping("/boom")
        public Object boom() {
            throw new IllegalStateException("simulated unrecoverable failure");
        }

        /** 对照组：一个正常端点，证明 advice 没把一切都变成 500。 */
        @GetMapping("/fine")
        public Object fine() {
            return new com.rag.users.user.api.dto.GetAvatarHistoryResp();
        }
    }

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ExplodingController())
                .setControllerAdvice(new GlobalExceptionHandler(), new GlobalResponseAdvice())
                .build();
    }

    @Test
    @DisplayName("未捕获异常 → HTTP 500 + 完整 envelope（已签字偏离：Go 侧是空 body）")
    void uncaughtException_is500WithFullEnvelope() throws Exception {
        mockMvc.perform(get("/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(5001))
                .andExpect(jsonPath("$.msg").value("系统内部错误"));
    }

    /**
     * <b>对照组</b>：正常端点不得被兜底逻辑污染。
     *
     * <p>没有这条，就可能有人为了让 500 的形状「更接近 Go」而动 advice，
     * 结果把 200 路径也一起改坏 —— 这正是 {@code DRIFT-006} 修 400 时踩过的坑形态。
     */
    @Test
    @DisplayName("对照组：正常端点仍是 200 且走统一 envelope")
    void normalEndpoint_isUnaffected() throws Exception {
        mockMvc.perform(get("/fine"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}