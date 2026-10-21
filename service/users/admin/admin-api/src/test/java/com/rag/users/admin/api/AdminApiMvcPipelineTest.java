package com.rag.users.admin.api;

import com.rag.users.admin.api.client.AdminRpcClient;
import com.rag.users.admin.proto.AdminServiceGrpc;
import com.rag.users.common.jwt.JwtUtil;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.Assertions;
import org.mockito.Mockito;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * admin-api 真实 MVC 管线集成测试（issue #71186443）。
 *
 * <p>对应 AGENTS.md §6.2.3 的 **A-U0（admin-api 侧）**。
 * 此前 admin-api 的唯一 Web 测试 {@code AdminResponseEnvelopeAdviceTest} 用的是
 * {@code MockMvcBuilders.standaloneSetup} + 内联 {@code @RestController}，
 * <b>不加载</b>真实的 {@code AdminController} / {@code WebMvcConfig} /
 * {@code AdminJwtAuthInterceptor} ⇒ DRIFT-009 的鉴权链改动从未走过真实管线。
 * 本类把它补上：{@code @SpringBootTest} 加载真实 {@code AdminApiApplication}，
 * MockMvc 由 Spring MVC 真实装配，拦截器 / advice / 路由全部是生产代码。
 *
 * <p><b>为什么必须用真 Redis</b>：DRIFT-009 / DRIFT-010 的语义正是
 * 「黑名单<b>有没有被查、用哪个 key 查的</b>」，以及「黑名单阶段有没有被绕过」。
 * 用纯 mock Redis 只能测到「代码调用了 hasKey」，测不到真连接、真 key、真命中，
 * 更测不到「一次都没调用」这个顺序断言。故这里用 {@code @MockitoSpyBean}
 * 把真容器 Redis 包成 spy —— 既保留真连接，又让交互次数可断言。
 *
 * <p><b>2026-10-21 修正（issue #71186893）</b>：本类此前把「黑名单先于验签」锁成了规格，
 * 而 Go 的真实顺序是 {@code Authorize(JWT) → CheckBlacklist}（三段取证见下方测试块）。
 * 同时新发现的 DRIFT-010 是一个<b>注销绕过</b>（{@code "XXBearer " + 已拉黑 token} 可通过鉴权），
 * 由 {@link #nonBearerPrefixedTokenCannotBypassBlacklist} 锁住。
 *
 * <p><b>唯一被替换的是 gRPC 边界</b>（{@link AdminRpcClient}）。
 * 被测的语义全部在 api 进程内：路由 → 拦截器 → Service → advice → 序列化。
 */
@SpringBootTest(
        classes = AdminApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                // 拒连最快；Nacos 连不上只会打日志，不影响本测试
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                // AdminRpcClient 整体被 @MockitoBean 替换，这个 channel bean 不会被用到；
                // 给个不会真连的地址，避免 devh 的 channel 解析去碰 Nacos
                "grpc.client.admin-rpc.address=static://127.0.0.1:65535",
                "grpc.client.admin-rpc.negotiation-type=plaintext",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN"
        })
@AutoConfigureMockMvc
@Testcontainers
class AdminApiMvcPipelineTest {

    /** 与 admin-rpc 的 application.yml 保持同一份密钥，否则签出来的 token 验签必失败。 */
    private static final String SECRET = "yU-hCm)2*?NQmWG";
    private static final long EXPIRE_SEC = 2592000L;
    private static final long CLAIM_UID = 4242L;

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
     * 用 spy 而非纯 mock：DRIFT-009/010 的语义正是「黑名单<b>有没有被查</b>」，
     * mock 只能证明代码调用了 hasKey、证明不了它没调用；spy 保留真 Redis 连接与真 key 命中，
     * 同时让 {@code verify(hasKey)} 断言「一次都没查」成为可能。
     */
    @MockitoSpyBean
    private StringRedisTemplate redisTemplate;

    @MockitoBean
    private AdminRpcClient adminRpcClient;
    private AdminServiceGrpc.AdminServiceBlockingStub stub;

    @BeforeEach
    void setUp() {
        stub = Mockito.mock(AdminServiceGrpc.AdminServiceBlockingStub.class);
        Mockito.when(adminRpcClient.adminRpc()).thenReturn(stub);
        // 每个用例从干净的黑名单开始
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
        // flushAll 本身会记进 spy 的交互记录，清掉，只留「被测请求」的交互
        Mockito.clearInvocations(redisTemplate);
    }

    private static String validToken() {
        return JwtUtil.getToken(SECRET, System.currentTimeMillis() / 1000L, EXPIRE_SEC, CLAIM_UID);
    }

    // ==================================================================
    // 鉴权链顺序与 token 提取：与 Go 逐条对齐（issue #71186893 / DRIFT-009 / DRIFT-010）
    //
    // Go 出处（三段，见 AdminJwtAuthInterceptor#preHandle 的完整论证）：
    //   rest/engine.go:101-113 bindRoute → appendAuthHandler(JWT) 先于 ng.middlewares，
    //   最后 chn.ThenFunc(route.Handler)；rest/chain/chain.go Then = m0(m1(...mN(h)))
    //   索引 0 最外层先执行；rest/server.go:222-233 WithMiddleware 在构造期把黑名单
    //   包进 route.Handler —— 它是 ThenFunc 的参数，比 chain 任何元素都内层。
    //   ⇒ 真实顺序：Authorize(JWT) → CheckBlacklist → handler
    //
    // 2026-10-21 之前本块把顺序锁成了相反方向（原 DisplayName「黑名单先于验签」），
    // 现在改回 Go 的方向。
    // ==================================================================

    /**
     * DRIFT-009 的核心场景：<b>签名无效但已在黑名单中</b>的 token。
     *
     * <p>Go：{@code Authorize} 先失败 → 401 + <b>空 body</b>；{@code CheckBlacklistMiddleware}
     * 根本不会被调用 ⇒ <b>Redis 一次都不查</b>。
     *
     * <p>修复前的 RAG 会先打一次 {@code hasKey} 再 401 —— 即为畸形 token 凭空产生 Redis 读。
     * 本用例用 spy 断言 {@code hasKey} 从未被调用，这是顺序本身的直接证据，
     * 比「同样返回 401」这种表面断言强得多。
     */
    @Test
    @DisplayName("DRIFT-009：验签先于黑名单 —— 无效签名 + 已拉黑 → 401 且【一次都没查 Redis】")
    void signatureVerifiedBeforeBlacklistLookup() throws Exception {
        // 预置黑名单：若黑名单真被查过，这里会命中并走黑名单分支
        redisTemplate.opsForValue().set("admin:jwt_blacklist:not-a-valid-jwt", "banned");

        mockMvc.perform(post("/admin/v1/getself")
                        .header("Authorization", "Bearer not-a-valid-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        // Go 出处：engine.go bindRoute 把 Authorize 排在 route.Handler（黑名单）之前
        verify(redisTemplate, never()).hasKey(anyString());
        verify(stub, never()).getSelf(any());
    }

    /**
     * DRIFT-010（安全）：<b>注销绕过</b>。
     *
     * <p>Go 出处 golang-jwt/jwt/v4@v4.5.2/request/oauth2.go:7-12：
     * <pre>
     * if len(tok) &gt; 6 &amp;&amp; strings.ToUpper(tok[0:7]) == "BEARER " { return tok[7:], nil }
     * return tok, nil        ← 前缀不匹配时【原样返回整串】
     * </pre>
     * 即 {@code "XXBearer eyJ..."} 的验签 token 是<b>整串</b> → 必然验签失败 → 401。
     *
     * <p>修复前的 RAG 用 {@code split(" ", 2).parts[1]} 取到 {@code "eyJ..."} → 验签通过；
     * 再用整串 TrimPrefix 查黑名单（key 变成 {@code admin:jwt_blacklist:XXBearer eyJ...}）必然查不到
     * ⇒ <b>已注销的管理员 token 仍可继续调用 admin-api</b>。Go 会拒绝。
     */
    @Test
    @DisplayName("DRIFT-010 安全：XXBearer 前缀不能绕过黑名单（Go 验整串必失败）")
    void nonBearerPrefixedTokenCannotBypassBlacklist() throws Exception {
        String token = validToken();
        // 正常写法下该 token 已被注销拉黑
        redisTemplate.opsForValue().set("admin:jwt_blacklist:" + token, "banned");

        // 攻击写法：加一个不是 "Bearer " 的前缀
        mockMvc.perform(post("/admin/v1/getself")
                        .header("Authorization", "XXBearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        // 关键：请求根本没走到 Service —— 即没有被放行
        verify(stub, never()).getSelf(any());
    }

    /** 规范写法 + 有效签名 + 在黑名单 → 401，且黑名单用的是 Go 的 key 格式。 */
    @Test
    @DisplayName("DRIFT-009：有效签名 + 已拉黑 → 401，且查的正是 Go 的 key")
    void validTokenInBlacklistRejected() throws Exception {
        String token = validToken();
        redisTemplate.opsForValue().set("admin:jwt_blacklist:" + token, "banned");

        mockMvc.perform(post("/admin/v1/getself")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        // Go 出处 checkblacklistmiddleware.go:34 fmt.Sprintf("admin:jwt_blacklist:%s", token)
        verify(redisTemplate).hasKey("admin:jwt_blacklist:" + token);
        verify(stub, never()).getSelf(any());
    }

    /**
     * DRIFT-010：{@code Bearer } 前缀在 Go 侧是<b>大小写不敏感</b>的
     * （{@code strings.ToUpper(tok[0:7]) == "BEARER "}）。
     * 有效 token + 小写 {@code bearer } + 未拉黑 ⇒ Go 验签通过并进入下游。
     *
     * <p>同时锁住 Go 自身的不一致：小写写法下黑名单那段用的是大小写<b>敏感</b>的 TrimPrefix，
     * 查的是 {@code admin:jwt_blacklist:bearer <token>}（整串，含前缀）。
     */
    @Test
    @DisplayName("DRIFT-010：Bearer 前缀大小写不敏感（Go ToUpper 比较）")
    void bearerPrefixIsCaseInsensitive() throws Exception {
        String token = validToken();

        mockMvc.perform(post("/admin/v1/getself")
                        .header("Authorization", "bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"));

        // 验签用的是剥掉前缀后的 token ⇒ 请求必须真的到达 Service
        verify(stub).getSelf(any());
        // 黑名单查的是【未剥前缀的整串】—— Go 自身 TrimPrefix 大小写敏感的必然结果
        verify(redisTemplate).hasKey("admin:jwt_blacklist:bearer " + token);
    }

    /** 未拉黑 + 无效签名 → 401，且验签阶段就结束、从没查过黑名单。 */
    @Test
    @DisplayName("DRIFT-009：无效签名 → 401，且未进入黑名单阶段")
    void invalidSignatureRejectedBeforeBlacklistStage() throws Exception {
        mockMvc.perform(post("/admin/v1/getself")
                        .header("Authorization", "Bearer not-a-valid-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        verify(redisTemplate, never()).hasKey(anyString());
        verify(stub, never()).getSelf(any());
    }

    /** 缺 Authorization 头 → 401，且从不查 Redis（Go 在 header 缺失时直接 next）。 */
    @Test
    @DisplayName("DRIFT-009：缺 Authorization → 401 且不查黑名单")
    void missingHeaderSkipsBlacklistLookup() throws Exception {
        mockMvc.perform(post("/admin/v1/getself")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        verify(redisTemplate, never()).hasKey(anyString());
        verify(stub, never()).getSelf(any());
    }

    /**
     * Go 出处 oauth2.go:8 {@code len(tok) > 6} —— {@code "Bearer "} 正好 7 字符，
     * 满足 {@code > 6} 被剥成空串 → 空 token → 验签失败 401。
     * {@code "Bearer"}（6 字符）不满足 {@code > 6}，原样当 token，同样 401。
     */
    @Test
    @DisplayName("DRIFT-010：Bearer 后为空 → 401（Go 剥出空串后验签失败）")
    void emptyBearerTokenRejected() throws Exception {
        mockMvc.perform(post("/admin/v1/getself")
                        .header("Authorization", "Bearer ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        verify(stub, never()).getSelf(any());
    }

    /**
     * DRIFT-010 回退的防回退用例：{@code uid <= 0} <b>不在拦截器层</b>拒绝。
     * Go 出处 rest/handler/authhandler.go：Authorize 只验签名 + exp，
     * claim 取值校验在 identity 层（resolve.go:75-77）。
     * 即：签一个 {@code userId = 0} 的有效 token，拦截器应<b>放行</b>，
     * 由 {@code IdentityResolver.claimedUID} 在业务层判成 1006（HTTP 200 + 业务码）。
     */
    @Test
    @DisplayName("DRIFT-010：uid=0 不在拦截器层拒绝（放行后由 identity 层判 1006）")
    void uidZeroIsNotRejectedByInterceptor() throws Exception {
        String zeroUidToken = JwtUtil.getToken(SECRET, System.currentTimeMillis() / 1000L, EXPIRE_SEC, 0L);

        mockMvc.perform(post("/admin/v1/getself")
                        .header("Authorization", "Bearer " + zeroUidToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                // 拦截器放行 ⇒ 不是 401；identity 层判 uid<=0 ⇒ HTTP 200 + 业务码 1006
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1006));

        // 拦截器确实放行了：走到了黑名单阶段（Go 的 CheckBlacklist 同位置）
        verify(redisTemplate).hasKey("admin:jwt_blacklist:" + zeroUidToken);
        // 但 RPC 未被调用 —— claimedUID 在调 RPC 之前就拒了
        verify(stub, never()).getSelf(any());
    }

    /** public 路径完全绕过拦截器：Go 出处 routes.go 第一段 AddRoutes 不带 WithJwt/中间件。 */
    @Test
    @DisplayName("A-U0/DRIFT-009：public 路径 /admin/v1/login 不做任何鉴权")
    void publicPathBypassesInterceptor() throws Exception {
        when(stub.login(any())).thenReturn(
                com.rag.users.admin.proto.LoginResp.newBuilder().setUid(CLAIM_UID).build());

        mockMvc.perform(post("/admin/v1/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").isNotEmpty());

        verify(stub).login(any());
    }

    /** 合法 token → 穿过拦截器进 Controller。 */
    @Test
    @DisplayName("A-U0/DRIFT-009：合法 token 穿过拦截器到达 Controller")
    void validTokenReachesController() throws Exception {
        when(stub.getSelf(any())).thenReturn(
                com.rag.users.admin.proto.GetSelfResp.newBuilder()
                        .setAdmin(com.rag.users.admin.proto.AdminInfo.newBuilder()
                                .setUid(CLAIM_UID).setUsername("admin").setEmail("").build())
                        .build());

        mockMvc.perform(post("/admin/v1/getself")
                        .header("Authorization", "Bearer " + validToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.admin.username").value("admin"));

        // A-02 的端到端实证：Go 的 AdminInfo.Email 无 omitempty ⇒ 空邮箱也必须出现
        assertThatJsonContains(mockMvc.perform(post("/admin/v1/getself")
                        .header("Authorization", "Bearer " + validToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn().getResponse().getContentAsString(), "\"email\":\"\"");
    }

    // ==================================================================
    // 端到端穿过真实 advice 链：DRIFT-002 / DRIFT-003
    // ==================================================================

    /**
     * DRIFT-002 的<b>端到端</b>实证：Go 出处 api/internal/logic/admin/getuserlogic.go:59
     * 在 api 层写死 {@code Found: true}。这里穿过真实的 Controller → Service →
     * GlobalResponseAdvice → Jackson，直击客户端实际看到的 HTTP body。
     */
    @Test
    @DisplayName("A-U0/DRIFT-002：getuser 端到端 found=true（穿过真实 Controller + advice）")
    void getUserFoundIsTrueEndToEnd() throws Exception {
        when(stub.getUser(any())).thenReturn(
                com.rag.users.admin.proto.GetUserResp.newBuilder()
                        .setUser(com.rag.users.admin.proto.UserInfo.newBuilder()
                                .setUid(100L).setUsername("bob").setStatus(1).build())
                        .build());   // 故意不 setFound

        String body = mockMvc.perform(post("/admin/v1/getuser")
                        .header("Authorization", "Bearer " + validToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"uid\":\"100\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();

        assertThatJsonContains(body, "\"found\":true");
        assertThatJsonContains(body, "\"status\":1");
    }

    /** DRIFT-003 的端到端实证：Go 出处 updatelogic.go:61-62 `NotFound → 1003`。 */
    @Test
    @DisplayName("A-U0/DRIFT-003：update 端到端 NotFound → 1003（真实 advice 链）")
    void updateSelfNotFoundIs1003EndToEnd() throws Exception {
        when(stub.updateSelf(any())).thenThrow(new StatusRuntimeException(Status.NOT_FOUND));

        mockMvc.perform(post("/admin/v1/update")
                        .header("Authorization", "Bearer " + validToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1003))
                .andExpect(jsonPath("$.msg").value("用户不存在"));
    }

    /** DRIFT-004 的端到端实证：Go 出处 updatelogic.go:63-64 `Internal → 5003`。 */
    @Test
    @DisplayName("A-U0/DRIFT-004：update 端到端 Internal → 5003（不是 5001）")
    void updateSelfInternalIs5003EndToEnd() throws Exception {
        when(stub.updateSelf(any())).thenThrow(new StatusRuntimeException(Status.INTERNAL));

        mockMvc.perform(post("/admin/v1/update")
                        .header("Authorization", "Bearer " + validToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5003));
    }

    /** A-01 的端到端实证：Go 出处 getuserlistlogic.go:51-62，nil slice + `json:"list"` 无 omitempty。 */
    @Test
    @DisplayName("A-U0/A-01：getuserlist 空结果端到端输出 \"list\": null（不是 []，也不是字段消失）")
    void emptyUserListIsNullEndToEnd() throws Exception {
        when(stub.getUserList(any())).thenReturn(
                com.rag.users.admin.proto.GetUserListResp.newBuilder().setTotal(0).build());

        String body = mockMvc.perform(post("/admin/v1/getuserlist")
                        .header("Authorization", "Bearer " + validToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();

        assertThatJsonContains(body, "\"list\":null");
        assertThatJsonContains(body, "\"total\":0");
    }

    /**
     * DRIFT-005 已签字：Go 侧 uid 缺失走 form 绑定零值路径 ⇒ {@code 1003}；
     * RAG 保留 {@code 1017}。本测试锁住现状，防止被无声改回。
     * <p>Go 出处补证（本次已读 go-zero v1.10.2 源码原文）：
     * {@code service/user/admin/api/admincenter.go} <b>没有</b>调 {@code httpx.SetErrorHandler}
     * ⇒ {@code buildErrorHandler} 返回 nil ⇒ {@code doHandleError} 走
     * {@code http.Error(w, err.Error(), http.StatusBadRequest)}。
     * 所以 Go 两种非法输入分别是：<b>缺失</b> → uid=0 → {@code 1003}；
     * <b>格式非法</b> → HTTP {@code 400} + {@code text/plain} 纯文本。
     */
    @Test
    @DisplayName("A-U0/防回退：uid 缺失端到端走 1017（DRIFT-005 已签字）")
    void missingUidIs1017EndToEnd() throws Exception {
        mockMvc.perform(post("/admin/v1/getuser")
                        .header("Authorization", "Bearer " + validToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1017));

        verify(stub, never()).getUser(any());
    }

    /**
     * A 类 P2 回归：<b>空 body 的 POST 不得返回 HTTP 400</b>。
     *
     * <p>Go 出处（三段实测源码，非推断）：
     * <ol>
     *   <li>{@code internal/handler/admin/*.go} 13 个 handler 一律 {@code httpx.Parse(r, &req)}，
     *       {@code req} 是包级 {@code var req types.XxxReq}</li>
     *   <li>go-zero v1.10.2 {@code rest/httpx/requests.go:153 withJsonBody} =
     *       {@code r.ContentLength > 0 && ...} ⇒ 空 body 走
     *       {@code UnmarshalJsonMap(nil, v)}，对 struct 是 no-op、<b>不报错</b></li>
     *   <li>admin {@code types.go} 13 个 Req <b>零个</b> {@code Validate()}、<b>零个</b>
     *       {@code validate:} tag，且未调 {@code httpx.SetValidator} ⇒ 校验环节整体不生效</li>
     * </ol>
     * 结论：Go 对空 body 一律 <b>HTTP 200 + 业务码</b>。
     *
     * <p>修复前 RAG 13 个端点全是 {@code required=true} ⇒ 抛
     * {@code HttpMessageNotReadableException} ⇒ {@code handleUnreadableBody} ⇒ HTTP 400 + 空 body，
     * 与 Go 不一致且客户端拿不到任何可读原因。
     *
     * <p>这里用 {@code /getuser}（protected）而不是 public 端点：public 端点不挂拦截器，
     * 测不出「鉴权 + 空 body」叠加时的顺序。
     */
    @Test
    @DisplayName("A-P2：空 body 的 protected 端点不得 400 —— 走业务码 1017（Go 空 body 恒 200）")
    void emptyBodyDoesNotReturn400() throws Exception {
        mockMvc.perform(post("/admin/v1/getuser")
                        .header("Authorization", "Bearer " + validToken())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1017));

        verify(stub, never()).getUser(any());
    }

    /** 同上，但 public 端点（无 JWT 拦截器）—— 证明 required=false 不依赖拦截器顺序。 */
    @Test
    @DisplayName("A-P2：空 body 的 public 端点 /login 也不得 400")
    void emptyBodyOnPublicEndpointDoesNotReturn400() throws Exception {
        // 零值 username/password 直达 rpc；Go 出处 loginlogic.go:54
        // `codes.Unauthenticated → errmsg.ErrorLoginWrong`，而 ErrorLoginWrong = 1002
        // （service/*/common/errmsg/errmsg.go:54；不是 1008）
        when(stub.login(any())).thenThrow(new StatusRuntimeException(Status.UNAUTHENTICATED));

        mockMvc.perform(post("/admin/v1/login")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1002));

        verify(stub).login(any());
    }

    // ==================================================================
    // helper
    // ==================================================================

    private static void assertThatJsonContains(String body, String fragment) {
        assertTrue(body.contains(fragment),
                "响应体应包含 " + fragment + "，实际：" + body);
    }

}