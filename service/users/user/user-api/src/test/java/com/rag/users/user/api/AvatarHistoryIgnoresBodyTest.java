package com.rag.users.user.api;

import com.rag.users.user.api.advice.GlobalExceptionHandler;
import com.rag.users.user.api.advice.GlobalResponseAdvice;
import com.rag.users.user.api.controller.UserController;
import com.rag.users.user.api.dto.GetAvatarHistoryResp;
import com.rag.users.user.api.service.AvatarHistoryService;
import com.rag.users.user.api.service.AvatarSelectService;
import com.rag.users.user.api.service.AvatarUploadService;
import com.rag.users.user.api.service.UserDeleteService;
import com.rag.users.user.api.service.UserGetService;
import com.rag.users.user.api.service.UserLoginService;
import com.rag.users.user.api.service.UserLogoutService;
import com.rag.users.user.api.service.UserRegisterService;
import com.rag.users.user.api.service.UserUpdateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * <p><b>DRIFT-USERAPI-005 的守门网（绑定语义层 · 不依赖 Redis / Docker）</b>：
 * {@code POST /user/v1/avatar/history} <b>必须完全忽略 request body</b>。
 *
 * <h3>Go 出处（全文 26 行，逐行核对）</h3>
 * {@code service/user/user/api/internal/handler/user/getavatarhistoryhandler.go:16-25}
 * <pre>
 *   func GetavatarhistoryHandler(svcCtx *svc.ServiceContext) http.HandlerFunc {
 *       return func(w http.ResponseWriter, r *http.Request) {
 *           l := user.NewGetavatarhistorylogic(r.Context(), svcCtx)
 *           resp, code := l.Getavatarhistory()      // ← 无 var req、无 httpx.Parse
 *           httpx.OkJson(w, &amp;response.Response{Code: code, Msg: errmsg.GetErrMsg(code), Data: resp})
 *       }
 *   }
 * </pre>
 * ⇒ 请求体<b>从头到尾没被读过</b>。参数全部来自别处：
 * {@code getavatarhistorylogic.go:32} uid 取自 JWT、{@code :40} limit <b>硬编码 30</b>。
 *
 * <h3>为什么用 {@code standaloneSetup} 而不是 {@code @SpringBootTest}</h3>
 * 这条漂移<b>只关乎「Spring 会不会去解析 body」</b>，与鉴权/黑名单无关。
 * <ul>
 *   <li><b>稳定性</b>：本机 Docker Desktop 只分到 <b>7.65 GiB</b>，并行会话的
 *       Kafka ×3 / Nacos ×2 / Neo4j / Grafana 已吃掉大半，
 *       {@code redis:7-alpine} 本轮连撞 3 次起不来 ⇒ 让「绑定语义」的护网
 *       依赖一个容器，等于<b>把基础设施抖动当成代码信号</b>。</li>
 *   <li><b>够用</b>：本条只需要 <b>真 DispatcherServlet 的参数解析 + 真 advice</b>，
 *       这两样 {@code standaloneSetup} 都提供了（Controller 与 advice 均为真实实现，
 *       只有 Service 被 mock）。</li>
 * </ul>
 * ⚠️ <b>本类不覆盖</b>真 Tomcat 下 {@code Content-Type} 的 charset 行为
 * —— 那是 {@code UNRESOLVED-001}，需要 {@code check-plaintext400-headers.sh} 那个真容���脚本。
 * 两件事不要混。
 *
 * <p>端到端层另有一条 {@code UserApiClaimSemanticsParityTest#avatarHistory_malformedJsonBody_isStill200}
 * （真 Redis + 真 MVC 全上下文）。两层并存：绑定层稳定、端到端层完整。
 */
class AvatarHistoryIgnoresBodyTest {

    private MockMvc mockMvc;
    private AvatarHistoryService avatarHistoryService;

    @BeforeEach
    void setUp() {
        avatarHistoryService = mock(AvatarHistoryService.class);
        when(avatarHistoryService.getAvatarHistory(any())).thenReturn(new GetAvatarHistoryResp());

        UserController controller = new UserController(
                mock(UserRegisterService.class),
                mock(UserLoginService.class),
                mock(UserLogoutService.class),
                mock(UserGetService.class),
                mock(UserUpdateService.class),
                mock(UserDeleteService.class),
                mock(AvatarSelectService.class),
                mock(AvatarUploadService.class),
                avatarHistoryService);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(), new GlobalResponseAdvice())
                .build();
    }

    @Test
    @DisplayName("Go: avatar/history 不 Parse body ⇒ 畸形 JSON 仍 200，不得变 400")
    void malformedJsonBody_isIgnored() throws Exception {
        mockMvc.perform(post("/user/v1/avatar/history")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"a\":}"))                 // 故意畸形
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(avatarHistoryService).getAvatarHistory(any());
    }

    @Test
    @DisplayName("非 JSON Content-Type + 无关 body ⇒ 同样 200（Go 连 Content-Type 都不看）")
    void nonJsonContentType_isIgnored() throws Exception {
        mockMvc.perform(post("/user/v1/avatar/history")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("这不是 JSON，也不是表单"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("合法 JSON 里带未知字段 ⇒ 仍 200 且业务照跑（body 不参与任何判定）")
    void wellFormedBodyWithUnknownFields_isIgnored() throws Exception {
        mockMvc.perform(post("/user/v1/avatar/history")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"uid\":999,\"limit\":1,\"whatever\":[1,2,3]}"))
                .andExpect(status().isOk());

        verify(avatarHistoryService).getAvatarHistory(any());
    }

    /**
     * <b>对照组</b>：同样畸形 JSON 打<b>会</b> Parse body 的端点（login），必须是 400。
     *
     * <p>作用：防止「为了让 avatar/history 通过，把 body 解析整体关掉」
     * —— 那会把一条漂移修成九条。
     */
    @Test
    @DisplayName("对照组：login 同样畸形 body 必须 400（Go 侧 login 确实 Parse）")
    void malformedJsonBody_still400OnEndpointsThatParse() throws Exception {
        mockMvc.perform(post("/user/v1/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"a\":}"))
                .andExpect(status().isBadRequest());
    }
}