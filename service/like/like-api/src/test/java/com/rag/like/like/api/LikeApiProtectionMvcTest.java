package com.rag.like.like.api;

import com.rag.like.like.api.client.LikeRpcClient;
import com.rag.users.common.jwt.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * like-api 的<b>请求保护层</b>守门：证明这四层不是「代码写在 common 里」，
 * 而是**真的挂进了本模块的请求管线**。
 *
 * <h2>为什么 like-api 也需要这一份（而不是「message-api 有就够了」）</h2>
 *
 * <p>2026-10-22 保护层下沉到 {@code com.rag.common.protection}。like-api 的
 * {@code scanBasePackages} <b>本来就含 {@code com.rag.common}</b>
 * ⇒ 四个 Filter 与 gRPC client breaker 是<b>自动</b>接上的，
 * <b>不需要本模块写任何装配代码</b>。
 * <p>正因为「零装配代码」，<b>没有任何一条编译期或启动期的信号</b>告诉使用者
 * 「这层在你身上生效了」。不写这个测试，结果就是一条<b>从未被验证过就生效的行为变更</b> ——
 * 要么没人发现它没生效（= 保护层形同虚设），要么哪天它悄悄改了行为也没人知道。
 * 这正是 AGENTS.md §3.3 的形态。
 *
 * <h2>「自动生效」本身也要被断言</h2>
 *
 * <p>{@link #allFourFiltersAreRegistered()} 断言四个 Filter 都在上下文里。
 * 它是本类其它几条的<b>前提</b>：没有它，「超限返回 413」这类断言可能是被
 * 某个别的东西短路出来的。顺序上先跑它不是巧合。
 *
 * <h2>Go 侧依据</h2>
 * <pre>
 *   rest/engine.go:105-112      先 buildChainWithNativeMiddlewares（含 MaxBytes，:164），
 *                               再 appendAuthHandler；chain.Append 是「加在最后」
 *                               （rest/chain/chain.go:38-43）
 *                               ⇒ MaxBytes 在鉴权之外
 *   rest/handler/maxbyteshandler.go:10-25  超限 → 裸 w.WriteHeader(413)，无 body
 *   rest/handler/maxconnshandler.go:12-36   闸门满 → 裸 w.WriteHeader(503)，无 body
 * </pre>
 */
@SpringBootTest(
        classes = LikeApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "grpc.client.like-rpc.address=static://127.0.0.1:65535",
                "grpc.client.like-rpc.negotiation-type=plaintext",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN",
                // 压到极小值，让「超过阈值」在测试里可达；生产默认仍是 Go 的 1048576
                "rag.protection.max-bytes=1024",
                "rag.protection.max-conns=1"
        })
@AutoConfigureMockMvc
class LikeApiProtectionMvcTest {

    private static final String SECRET = "ae0536f9-6450-4606-8e13-5a19ed505da0";
    private static final long EXPIRE_SEC = 3600L;
    private static final long CLAIM_UID = 31337L;
    private static final String COUNT_PATH = "/like/v1/getlikecount";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext ctx;

    @MockitoBean
    private LikeRpcClient likeRpcClient;

    private static String token() {
        return JwtUtil.getToken(SECRET, System.currentTimeMillis() / 1000L, EXPIRE_SEC, CLAIM_UID);
    }

    /** 造一个超过 {@code rag.protection.max-bytes=1024}、但仍是合法 JSON 的体。 */
    private static String oversizedJson() {
        StringBuilder sb = new StringBuilder("{\"target_ids\":[\"");
        while (sb.length() < 4096) {
            sb.append('a');
        }
        return sb.append("\"]}").toString();
    }

    @Test
    @DisplayName("四个保护 Filter 都真的注册进本模块上下文（自动装配的前提）")
    void allFourFiltersAreRegistered() {
        // 刻意用断言 Bean 存在，而不是断言「请求返回 413」——
        // 后者无法区分「Filter 生效」与「被别的组件短路」。
        assertNotNull(ctx.getBean("goStyleMaxBytesFilter"));
        assertNotNull(ctx.getBean("goStyleMaxConnsFilter"));
        assertNotNull(ctx.getBean("goStyleCpuSheddingFilter"));
        assertNotNull(ctx.getBean("goStyleBreakerFilter"));
    }

    @Test
    @DisplayName("超限请求体 → 413 空 body，且**压过鉴权**（Go 把 MaxBytes 挂在 Authorize 之外）")
    void oversizedBodyIsRejectedBeforeAuth() throws Exception {
        mockMvc.perform(post(COUNT_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(oversizedJson()))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(content().string(""));
        // ⚠️ 刻意**不带** Authorization：拿到 413 而不是 401，就是「MaxBytes 在鉴权之外」的唯一证据。
        // 若返回 401，说明<b>本层没生效</b>（而不是「顺序被改坏」）。
        // ⚠️ 已实测（2026-10-22）：把本类的 @Order 改成 LOWEST_PRECEDENCE，本条<b>依然绿</b> ——
        // 因为所有 Filter 都包在 DispatcherServlet 外，而 HandlerInterceptor 跑在它内部，
        // 两者先后由容器结构决定、与 order 数值无关。详见 GoStyleMaxBytesFilter 的类 javadoc。
        // 能真正让本条变红的变异是<b>摘掉本层的 @Component</b>（已实测：413 → 401）。
    }

    @Test
    @DisplayName("未超限 + 无 token → 仍是 401（证明上一条不是被无条件 413 短路）")
    void withinLimitStillReachesAuth() throws Exception {
        mockMvc.perform(post(COUNT_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target_ids\":[\"1\"]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("未超限 + 带合法 token → 走到业务逻辑（envelope 200，不是保护层拒绝）")
    void normalRequestReachesBusinessLogic() throws Exception {
        mockMvc.perform(post(COUNT_PATH)
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target_ids\":[\"1\"]}"))
                // like-rpc 是 mock，默认返回 null → rpcError 走 5001 分支。
                // 关键断言是 **200 + envelope**：保护层一旦误拒，这里会是裸 413/503。
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }
}