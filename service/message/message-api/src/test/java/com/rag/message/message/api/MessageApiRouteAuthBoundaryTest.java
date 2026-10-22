package com.rag.message.message.api;

import com.rag.message.message.api.client.MessageRpcClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 「<b>路由匹配先于鉴权</b>」这条规则在 message-api 侧的守门。
 *
 * <h2>为什么 MockMvc 在这里够用</h2>
 *
 * <p>本类只断言<b>状态码</b>与 {@code Allow} 头的<b>存在性</b>，不断言 body 字节、
 * 不断言 {@code Content-Type} 规范化后的取值 —— 那两层由
 * {@code LikeApiRouterErrorRealTomcatTest}（真 Tomcat，读原始响应）在<b>共享实现</b>
 * {@code com.rag.common.http.GoStyleRouterErrorResponses} 上锁死。
 * 两个模块共用同一份实现，所以<b>字节级只需守一次</b>；本类守的是「拦截器那条守卫」，
 * 而那是<b>每个模块各写一遍</b>的代码，必须各守一次。
 *
 * <h2>Go 侧依据</h2>
 * <pre>
 *   rest/engine.go:104-118        bindRoute 只把 chain（native + auth + 用户中间件）
 *                                 挂到 router.Handle(method, path, handle) 上
 *                                 ⇒ chain 只对**匹配上的路由**生效
 *   rest/router/patrouter.go:73   路径无匹配 → handleNotFound → 404，**不进 chain**
 *   rest/router/patrouter.go:79   方法不匹配 → Allow + 405，**不进 chain**
 * </pre>
 * ⇒ Go 侧未知路径**恒 404，与有没有 token 无关**。
 *
 * <p>⚠️ <b>Spring 侧默认做不到</b>：Boot 的静态资源处理器匹配 {@code /**}，
 * 未注册路径也拿到了 handler ⇒ {@code mappedHandler != null} ⇒ 拦截器照跑
 * ⇒ 404 被鉴权遮蔽成 401（2026-10-22 真 Tomcat 实测）。
 * 守卫是 {@code preHandle} 里的 {@code handler instanceof HandlerMethod}。
 */
@SpringBootTest(
        classes = MessageApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "grpc.client.message-rpc.address=static://127.0.0.1:65535",
                "grpc.client.message-rpc.negotiation-type=plaintext",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN"
        })
@AutoConfigureMockMvc
class MessageApiRouteAuthBoundaryTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MessageRpcClient rpcClient;

    @Test
    @DisplayName("未知路径 + **无 token** → 404（不是 401）：路由匹配先于鉴权")
    void unknownPathIs404WithoutToken() throws Exception {
        // ⚠️ 刻意不带 Authorization。修复前这条返回 401 ——
        // 因为 Boot 的静态资源处理器匹配 /**，未注册路径也拿到了 handler，鉴权拦截器先跑。
        mockMvc.perform(post("/message/v1/nope"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("反向对照：已注册路径 + 无 token → 仍是 401（守卫没有把鉴权整体关掉）")
    void knownPathStillRequiresToken() throws Exception {
        mockMvc.perform(post("/message/v1/conversations/list"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("方法不匹配 → 405 且带 Allow 头（body 为空，与 404 有 body 形状相反）")
    void methodMismatchIs405WithAllowHeader() throws Exception {
        // /message/v1/conversations/list 只注册了 POST
        mockMvc.perform(get("/message/v1/conversations/list"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "POST"))
                // ⚠️ Go patrouter.go:79-82 只有 WriteHeader ⇒ body 必须为空
                .andExpect(content().string(""));
    }
}