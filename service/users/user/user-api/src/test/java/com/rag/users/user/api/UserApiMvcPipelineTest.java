package com.rag.users.user.api;

import com.rag.users.common.jwt.JwtUtil;
import com.rag.users.user.api.client.UserRpcClient;
import com.rag.users.user.proto.UserServiceGrpc;
import com.rag.users.user.proto.UserServiceGrpc.UserServiceBlockingStub;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * user-api 真实 MVC 管线集成测试（AGENTS.md 的 C-2 覆盖缺口）。
 *
 * <p><b>为什么 user-api 侧也必须有这个类</b>：已有的
 * {@code AuthenticationHttpStatusTest} 断言「鉴权失败 → HTTP 401」，
 * 但它用 {@code MockMvcBuilders.standaloneSetup} + 内联 {@code @RestController} 探针，
 * <b>既不加载</b>真实的 {@code UserController} / {@code WebMvcConfig} / {@code JwtAuthInterceptor}，
 * 也<b>不挂</b> {@code HttpMiddleware} filter。因此 §6.1 第 10 项（HTTP 状态码语义变更）
 * 与 §3.4.2 N-1（鉴权失败必须 401）在 user-api 侧<b>从未在真实运行时被验证过</b>。
 *
 * <p><b>这条缺口是实测踩出来的，不是推演的</b>：DRIFT-FRAMEWORK-001
 * （{@code HttpMiddleware$ObservabilityResponseWriter.setStatus} 不 propagate 到 underlying，
 * 把全部 401 / 5003 静默降级成 200）正是在 admin-api 侧首次挂真 filter 时暴露的。
 * 修完 admin-api 后若不给 user-api 补这张网，<b>下一次谁动 HttpMiddleware 就能把 user-api
 * 打回 200 而 CI 全绿</b> —— 因为这个 bug 的宿主是共享层，而共享层自己当时也没有断言。
 *
 * <p><b>唯一被替换的是 gRPC 边界</b>（{@link UserRpcClient}）。
 * 被测的语义全部在 api 进程内：路由 → 拦截器 → Service → advice → 序列化，
 * 外加最外层的 {@code HttpMiddleware} filter（这一层才是本测试与既有 standalone 测试的关键差别）。
 *
 * <p><b>为什么必须用真 Redis</b>：黑名单查询走 {@code StringRedisTemplate.hasKey}。
 * mock Redis 只能测到「代码调用了 hasKey」，测不到真连接、真 key、真命中。
 *
 * <p>Go 出处（鉴权两段，与 {@code JwtAuthInterceptor} 的 javadoc 同源）：
 * <ul>
 *   <li>{@code rest.WithJwt} → {@code rest/handler/authhandler.go:106 unauthorized()} → <b>401</b></li>
 *   <li>{@code user/api/internal/middleware/checkblacklistmiddleware.go:38} → <b>401</b></li>
 * </ul>
 */
@SpringBootTest(
        classes = UserApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                // 拒连最快；Nacos 连不上只会打日志，不影响本测试
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                // UserRpcClient 整体被 @MockitoBean 替换，这个 channel bean 不会被用到；
                // 给个不会真连、也不走 Nacos name resolver 的地址
                "grpc.client.user-rpc.address=static://127.0.0.1:65535",
                "grpc.client.user-rpc.negotiation-type=plaintext",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN"
        })
@AutoConfigureMockMvc
@Testcontainers
class UserApiMvcPipelineTest {

    /** 与 user-api 的 application.yml 保持同一份密钥，否则签出来的 token 验签必失败。 */
    private static final String SECRET = "ae0536f9-6450-4606-8e13-5a19ed505da0";
    private static final long EXPIRE_SEC = 2592000L;
    private static final long CLAIM_UID = 31337L;

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void redis(DynamicPropertyRegistry r) {
        r.add("spring.data.redis.host", REDIS::getHost);
        r.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        r.add("spring.data.redis.password", () -> "");
    }

    @Autowired
    private MockMvc mockMvc;
    /**
     * spy 而非纯 mock：DRIFT-009/010 的语义正是「黑名单<b>有没有被查、用哪个 key 查的</b>」，
     * 纯 mock 证明不了「一次都没调用」这个顺序断言。spy 保留真容器 Redis 的连接与真 key 命中。
     */
    @MockitoSpyBean
    private StringRedisTemplate redisTemplate;

    @MockitoBean
    private UserRpcClient userRpcClient;
    private UserServiceBlockingStub stub;

    @BeforeEach
    void setUp() {
        stub = Mockito.mock(UserServiceBlockingStub.class);
        Mockito.when(userRpcClient.userRpc()).thenReturn(stub);
        // 每个用例从干净的黑名单开始
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
        // flushAll 本身会记进 spy 的交互记录，清掉，只留「被测请求」的交互
        Mockito.clearInvocations(redisTemplate);
    }

    private static String validToken() {
        return JwtUtil.getToken(SECRET, System.currentTimeMillis() / 1000L, EXPIRE_SEC, CLAIM_UID);
    }

    // ==================================================================
    // DRIFT-FRAMEWORK-001 / §6.1 第 10 项 / N-1：
    // 401 必须穿过 HttpMiddleware filter 后仍然是 401
    // ==================================================================

    /**
     * 本类的<b>核心断言</b>：缺 Authorization → <b>HTTP 401</b>。
     *
     * <p>修复前本用例拿到的是 <b>HTTP 200 + {@code {"code":1006,...}}</b>：
     * {@code @ExceptionHandler} 返回了 {@code ResponseEntity.status(UNAUTHORIZED)}，
     * 但 {@code HttpMiddleware$ObservabilityResponseWriter.setStatus} 只缓存不 propagate，
     * 等 {@code flushTo} 想补写时底层已被 {@code flushBuffer()} 提交，
     * {@code dst.setStatus(401)} 被 Servlet 的 {@code !isCommitted()} 守卫吞掉。
     */
    @Test
    @DisplayName("C-2/DRIFT-FRAMEWORK-001：缺 Authorization → 真 MVC 管线 + HttpMiddleware 下仍是 401")
    void missingAuthorizationReturns401ThroughRealPipeline() throws Exception {
        mockMvc.perform(post("/user/v1/get")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1006))
                .andExpect(jsonPath("$.msg").value("TOKEN已过期"))
                // N-1 已签字：401 的 body 统一成完整 envelope（Go 自身两处 body 不一致）
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(stub, never()).getUser(any());
    }

    /** 签名无效 → 401，且<b>确实查过</b>黑名单（先查黑名单后验签，Go 的 CheckBlacklist 在外层）。 */
    @Test
    @DisplayName("C-2：无效签名 → 401，且已查过黑名单")
    void invalidSignatureReturns401AfterBlacklistMiss() throws Exception {
        mockMvc.perform(post("/user/v1/get")
                        .header("Authorization", "Bearer not-a-valid-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        verify(stub, never()).getUser(any());
    }

    /**
     * 黑名单命中 → 401，且 key 前缀与 Go 一致（{@code user:jwt_blacklist:<token>}）。
     *
     * <p>Go 出处：{@code checkblacklistmiddleware.go} 的
     * {@code fmt.Sprintf("user:jwt_blacklist:%s", token)}。
     */
    @Test
    @DisplayName("C-2：token 在黑名单 → 401，且 key 格式 user:jwt_blacklist:<token> 命中真 Redis")
    void blacklistedTokenReturns401() throws Exception {
        redisTemplate.opsForValue().set("user:jwt_blacklist:valid-looking-token", "revoked");

        mockMvc.perform(post("/user/v1/get")
                        .header("Authorization", "Bearer valid-looking-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        assertEquals("revoked", redisTemplate.opsForValue().get("user:jwt_blacklist:valid-looking-token"),
                "黑名单 key 必须与 Go 的 fmt 格式逐字一致，否则注销后查不到");
        verify(stub, never()).getUser(any());
    }

    /** public 路径完全绕过鉴权（Go 出处 routes.go 第一段 AddRoutes 不带 WithJwt）。 */
    @Test
    @DisplayName("C-2：public 路径 /user/v1/login 不做任何鉴权检查")
    void publicPathBypassesAuth() throws Exception {
        when(stub.login(any())).thenReturn(
                com.rag.users.user.proto.LoginResp.newBuilder().setUid(CLAIM_UID).build());

        mockMvc.perform(post("/user/v1/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"pw\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(stub).login(any());
    }

    /** 合法 token → 穿过 HttpMiddleware + 拦截器后到达 Controller。 */
    @Test
    @DisplayName("C-2：合法 token 穿过真管线到达 Controller")
    void validTokenReachesController() throws Exception {
        // IdentityResolver.resolveUser 有 4 个前置条件，缺一个都走不到 Controller：
        // found=true / user 非默认实例 / user.uid == claim.uid / hasStatus() && status==0
        when(stub.getUser(any())).thenReturn(
                com.rag.users.user.proto.GetUserResp.newBuilder()
                        .setFound(true)
                        .setUser(com.rag.users.user.proto.UserInfo.newBuilder()
                                .setUid(CLAIM_UID).setUsername("alice").setStatus(0L).build())
                        .build());

        String body = mockMvc.perform(post("/user/v1/get")
                        .header("Authorization", "Bearer " + validToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();

        assertTrue(body.contains("\"username\":\"alice\""), body);
        verify(stub).getUser(any());
    }

    // ==================================================================
    // 业务层失败仍是 HTTP 200（Go 的 httpx.OkJson 语义）
    // ==================================================================

    /**
     * 与 {@code AuthenticationHttpStatusTest} 的第 2 条配对：<b>同一个业务码 1006</b>，
     * 异常<b>类型</b>决定 HTTP 状态码。业务层的 1006 必须保持 200。
     *
     * <p>这条断言是 {@code AuthenticationException} 这个子类型存在的全部理由 ——
     * 若哪天有人把 {@code handleAuthentication} 删掉、改回按 code 分流，本条会先红。
     */
    @Test
    @DisplayName("C-2：业务层的 1006（NOT_FOUND）仍是 HTTP 200 —— 状态码由类型而非业务码决定")
    void businessLayer1006Stays200() throws Exception {
        when(stub.getUser(any())).thenThrow(new StatusRuntimeException(Status.NOT_FOUND));

        mockMvc.perform(post("/user/v1/get")
                        .header("Authorization", "Bearer " + validToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1003));
    }

    /**
     * 非 2xx 的 HTTP 状态码在<b>正常响应</b>侧同样要能穿过 HttpMiddleware
     * —— {@code GlobalExceptionHandler.handleUnreadableBody} 等框架级分支走的就是这条。
     * 这条与 401 的路径不同（不抛 AuthenticationException），能覆盖到 advice 的另一侧。
     */
    @Test
    @DisplayName("C-2：框架级 400（body 不可读）穿过真管线仍是 400，不被降级成 200")
    void framework400SurvivesRealPipeline() throws Exception {
        mockMvc.perform(post("/user/v1/get")
                        .header("Authorization", "Bearer " + validToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ 这不是合法 JSON"))
                .andExpect(status().isBadRequest());
    }

    /**
     * A 类 P2 回归（user 侧）：<b>空 body 的 POST 不得返回 HTTP 400</b>。
     *
     * <p>Go 出处（三段实测源码，非推断；<b>与 admin 侧分别取证</b>，未套用 admin 的结论 ——
     * AGENTS.md §3.4.3 N-3 明确警告过跨模块套用的问题）：
     * <ol>
     *   <li>{@code internal/handler/user/*.go} 9 个 handler 一律 {@code var req types.XxxReq} +
     *       {@code httpx.Parse(r, &req)}</li>
     *   <li>go-zero v1.10.2 {@code requests.go:153 withJsonBody} = {@code r.ContentLength > 0 && ...}
     *       ⇒ 空 body 走 {@code UnmarshalJsonMap(nil, v)}，对 struct 是 no-op、<b>不报错</b></li>
     *   <li>user {@code types.go} 8 个 Req、<b>零个</b> {@code validate:} tag、<b>零个</b>
     *       {@code Validate()}，且全服务未调 {@code httpx.SetValidator} ⇒ 校验环节不生效</li>
     * </ol>
     *
     * <p>修复前 register / login / update / avatar-select / avatar 共 5 个端点是
     * {@code required=true} ⇒ {@code HttpMessageNotReadableException} ⇒ HTTP 400 + 空 body。
     */
    @Test
    @DisplayName("A-P2：空 body 的 public 端点 /login 不得 400 —— Go 零值 req 直达 rpc，落 CodeServerBusy")
    void emptyBodyOnLoginDoesNotReturn400() throws Exception {
        // Go 出处 loginlogic.go 的 switch：Internal → ErrorServerCommon，default → CodeServerBusy。
        // 这里给一个不在 switch 里的 code，验证走的是 Go 的 default 分支。
        when(stub.login(any())).thenThrow(new StatusRuntimeException(Status.UNKNOWN));

        mockMvc.perform(post("/user/v1/login")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1015));

        // 零值 username/password 必须真的透传到 rpc，而不是被本地预校验拦下
        org.mockito.ArgumentCaptor<com.rag.users.user.proto.LoginReq> captor =
                org.mockito.ArgumentCaptor.forClass(com.rag.users.user.proto.LoginReq.class);
        verify(stub).login(captor.capture());
        assertEquals("", captor.getValue().getUsername(), "零值 LoginReq 应以空用户名抵达 rpc");
        assertEquals("", captor.getValue().getPassword(), "零值 LoginReq 应以空密码抵达 rpc");
    }

    /** 同上，protected 端点：证明 required=false 不依赖拦截器顺序，且空 body 能穿过鉴权进 Controller。 */
    @Test
    @DisplayName("A-P2：空 body 的 protected 端点 /update 不得 400")
    void emptyBodyOnUpdateDoesNotReturn400() throws Exception {
        when(stub.updateUser(any())).thenThrow(new StatusRuntimeException(Status.NOT_FOUND));

        mockMvc.perform(post("/user/v1/update")
                        .header("Authorization", "Bearer " + validToken())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1003));
    }

    // ==================================================================
    // DRIFT-009 / DRIFT-010：鉴权两段的顺序与 token 提取（issue #71186893）
    //
    // Go 出处（三段）：
    //   rest/engine.go:101-113 bindRoute 先 appendAuthHandler(JWT) 再 ThenFunc(route.Handler)；
    //   rest/chain/chain.go 的 Then = m0(m1(...mN(h)))，索引 0 最外层先执行；
    //   rest/server.go:222-233 WithMiddleware 在构造期把黑名单包进 route.Handler，
    //   它是 ThenFunc 的参数，比 chain 任何元素都内层。
    //   ⇒ 真实顺序：Authorize(JWT) → CheckBlacklist → handler
    //
    // token 提取：golang-jwt/jwt/v4@v4.5.2/request/oauth2.go:7-12
    //   前缀大小写不敏感（ToUpper 比较），不匹配时【原样返回整串】
    // ==================================================================

    /**
     * DRIFT-009：验签先于黑名单。签名无效时黑名单<b>一次都不查</b>，
     * 与 Go 一致（Authorize 先失败，CheckBlacklistMiddleware 根本不会被调用）。
     */
    @Test
    @DisplayName("DRIFT-009：验签先于黑名单 —— 无效签名时一次都不查 Redis")
    void signatureVerifiedBeforeBlacklistLookup() throws Exception {
        redisTemplate.opsForValue().set("user:jwt_blacklist:not-a-valid-jwt", "banned");

        mockMvc.perform(post("/user/v1/get")
                        .header("Authorization", "Bearer not-a-valid-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        verify(redisTemplate, never()).hasKey(Mockito.anyString());
        verify(stub, never()).getUser(any());
    }

    /**
     * DRIFT-010（安全）：<b>注销绕过</b>。
     *
     * <p>Go：{@code "XXBearer eyJ..."} 的验签 token 是<b>整串</b>（oauth2.go 前缀不匹配即原样返回）
     * ⇒ 必然验签失败 → 401。
     *
     * <p>修复前的 RAG 用 {@code startsWith("Bearer ")}（大小写敏感）直接判 401 —— 结果同样是 401，
     * 但那不是 Go 的机制；真正的漏洞形态在 admin 侧（那里用 split 取 parts[1] 会<b>放行</b>）。
     * 本用例在 user 侧锁住「非规范前缀不得放行」，两个模块共用 BearerTokenExtractor 后行为一致。
     */
    @Test
    @DisplayName("DRIFT-010：XXBearer 前缀不得绕过黑名单")
    void nonBearerPrefixedTokenCannotBypassBlacklist() throws Exception {
        String token = validToken();
        redisTemplate.opsForValue().set("user:jwt_blacklist:" + token, "banned");

        mockMvc.perform(post("/user/v1/get")
                        .header("Authorization", "XXBearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        verify(stub, never()).getUser(any());
    }

    /**
     * DRIFT-010：{@code Bearer } 前缀<b>大小写不敏感</b>（Go：{@code strings.ToUpper(tok[0:7]) == "BEARER "}）。
     * 修复前本模块用大小写敏感的 {@code startsWith("Bearer ")}，小写写法会被误拒为 401。
     */
    @Test
    @DisplayName("DRIFT-010：小写 bearer 前缀应被接受（Go 用 ToUpper 比较）")
    void bearerPrefixIsCaseInsensitive() throws Exception {
        when(stub.getUser(any())).thenReturn(com.rag.users.user.proto.GetUserResp.newBuilder()
                .setFound(true)
                .setUser(com.rag.users.user.proto.UserInfo.newBuilder()
                        .setUid(CLAIM_UID).setUsername("u").setStatus(0L))
                .build());

        mockMvc.perform(post("/user/v1/get")
                        .header("Authorization", "bearer " + validToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(stub).getUser(any());
        // 黑名单用的是大小写【敏感】的 TrimPrefix ⇒ 查未剥前缀的整串（Go 自身如此）
        verify(redisTemplate).hasKey("user:jwt_blacklist:bearer " + validToken());
    }

    /** 规范写法 + 有效签名 + 在黑名单 → 401，且查的是 Go 的 key 格式。 */
    @Test
    @DisplayName("DRIFT-009：有效签名 + 已拉黑 → 401，且用 Go 的 key")
    void validTokenInBlacklistRejected() throws Exception {
        String token = validToken();
        redisTemplate.opsForValue().set("user:jwt_blacklist:" + token, "banned");

        mockMvc.perform(post("/user/v1/get")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        verify(redisTemplate).hasKey("user:jwt_blacklist:" + token);
        verify(stub, never()).getUser(any());
    }

    /**
     * DRIFT-006：畸形 JSON ⇒ Go 的 {@code http.Error(w, msg, 400)} 形状。
     *
     * <p>Go 出处：{@code service/user} 全树未注册 {@code httpx.SetErrorHandler}
     * ⇒ {@code httpx.Parse} 失败走 {@code doHandleError} 的 {@code handler == nil} 分支
     * ⇒ {@code text/plain; charset=utf-8} + 裸文本 + 尾随换行，<b>不是</b> envelope。
     */
    @Test
    @DisplayName("DRIFT-006：畸形 JSON → 400 + text/plain + 裸文本（Go 的 http.Error 形状）")
    void malformedJsonReturnsPlainTextBadRequest() throws Exception {
        mockMvc.perform(post("/user/v1/get")
                        .header("Authorization", "Bearer " + validToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"a\":}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));

        // 关键：body 不能是 envelope —— Go 的这条路径根本没有 {code,msg,data}
        String body = mockMvc.perform(post("/user/v1/get")
                        .header("Authorization", "Bearer " + validToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"a\":}"))
                .andReturn().getResponse().getContentAsString();
        assertFalse(body.trim().startsWith("{") && body.contains("\"code\""),
                "400 响应体不得是 envelope，实际 = " + body);
        assertTrue(body.endsWith("\n"), "Go 的 fmt.Fprintln 会补尾随换行，实际 = " + body);
    }
}
