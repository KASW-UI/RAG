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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static com.rag.users.admin.api.support.GoStyleHostPortPinning.pinContainerPort;

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
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379).withCreateContainerCmdModifier(cmd -> pinContainerPort(cmd, 6379));

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
                        // ⚠️ DRIFT-016（2026-10-21）：body 必须【完整】。Go 的 UpdateSelfReq
                        // username/password/email 三个标量的 tag 都是 `,omitempty`，
                        // 而 go-zero 只认 `,optional` ⇒ 三者全必填
                        // （实测 probe-go-required-fields.py：drop password ⇒
                        //   400 field "password" is not set）。
                        // 本用例此前发 `{"username":"alice"}`，那在 Go 侧根本到不了 business 层。
                        .content("{\"username\":\"alice\",\"password\":\"pw123456\","
                                + "\"email\":\"alice@example.com\"}"))
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
                        // 同上：body 必须完整，否则被 DRIFT-016 的必填校验拦在 400
                        .content("{\"username\":\"alice\",\"password\":\"pw123456\","
                                + "\"email\":\"alice@example.com\"}"))
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
                        // ⚠️ DRIFT-016（2026-10-21）：keyword 的 Go tag 是 `form:"keyword,omitempty"`，
                        // `,omitempty` ≠ `,optional` ⇒ 必填（实测 drop keyword ⇒ 400）。
                        // 本用例此前发 `{}`，在 Go 侧同样到不了 business 层。
                        // page / page_size 带 default= ⇒ 不必填，所以这里只补 keyword。
                        .content("{\"keyword\":\"nobody\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();

        assertThatJsonContains(body, "\"list\":null");
        assertThatJsonContains(body, "\"total\":0");
    }

    /**
     * <b>⚠️ 2026-10-21 反转（DRIFT-016）· 本用例此前断言的是错误行为。</b>
     *
     * <p>原用例名「A-U0/防回退：uid 缺失端到端走 1017」，原 javadoc 写
     * 「Go 侧 uid 缺失走 form 绑定零值路径 ⇒ {@code 1003}；RAG 保留 {@code 1017}」，
     * 并据此把它当作「已签字的防回退测试」。<b>那个理由已被实测推翻</b>：
     * {@code tools/difftest/probe-go-required-fields.py} 打真实 Go 进程
     * （2026-10-21，admin-api:18889）三态：
     * <pre>
     *   完全不传 uid → HTTP 400  field "uid" is not set
     *   uid=abc      → HTTP 400  strconv.ParseInt: parsing "abc": invalid syntax
     *   uid=0        → HTTP 200  {"code":1003,"msg":"用户不存在"}
     * </pre>
     * Go 出处：{@code httpx.Parse} → {@code ParseForm} 无条件执行 →
     * {@code core/mapping/unmarshaler.go:966-969} 的
     * {@code default: if !opts.optional() { return newInitError(fullName) }}。
     * 「缺失」<b>不是</b>零值路径 —— 它在进 business 层之前就被 400 拦掉了。
     *
     * <p>用户 2026-10-21 对 DRIFT-016 拍板「向 Go 对齐」，故本断言改为 400 + text/plain。
     * <b>N-3（uid=0 → 1017）仍是有意偏离</b>，由 {@link #uidZeroIs1017NotGoZeroValuePath} 锁住。
     */
    @Test
    @DisplayName("DRIFT-016：uid 缺失端到端 400 + text/plain（Go 返 400，不是 1017）")
    void missingUidIs400TextPlainEndToEnd() throws Exception {
        String body = mockMvc.perform(post("/admin/v1/getuser")
                        .header("Authorization", "Bearer " + validToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn().getResponse().getContentAsString();

        // Go 出处：unmarshaler.go:1199 newInitError → fmt.Errorf("field %q is not set", name)
        assertEquals("field \"uid\" is not set\n", body);

        verify(stub, never()).getUser(any());
    }

    /**
     * <b>⚠️ 2026-10-21 实证推翻了 N-3 留档（DRIFT-016 审计期间发现）。</b>
     *
     * <p>§3.4.3 的 N-3 写「RAG 在 {@code AdminApiSupport.parseUid} <b>提前拦截 uid=0</b>
     * → 1017」。<b>代码里没有这个拦截</b>：{@code AdminApiSupport#parseUid} 只拒
     * null / 空串 / 非数字 / 溢出（见 {@code AdminUidParseTest} 的 5 条），
     * 全仓 grep {@code uid == 0} 在 admin-api 侧<b>零命中</b>。
     *
     * <p>因此 uid=0 的真实路径是：{@code parseUid("0")} → 0L → 透传给 admin-rpc
     * → 查无此人 → {@code 1003 用户不存在} ——
     * <b>与 Go 逐字相同</b>（实测 Go：{@code uid=0 → 200 {"code":1003}}）。
     *
     * <p>换句话说：N-3 记的是一个<b>从未存在过的偏离</b>，观测行为反而一直是对的。
     * 这与 §3.4.4 DRIFT-009 同型——<b>留档不能因为「有签字、有防回退测试」就免于复核</b>。
     */
    @Test
    @DisplayName("N-3 留档证伪：uid=0 实际透传到 rpc → 1003（与 Go 相同，不是留档所写的 1017）")
    void uidZeroIsPassedThroughAndMatchesGo() throws Exception {
        when(stub.getUser(any())).thenThrow(new StatusRuntimeException(Status.NOT_FOUND));

        mockMvc.perform(post("/admin/v1/getuser")
                        .header("Authorization", "Bearer " + validToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"uid\":\"0\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1003))
                .andExpect(jsonPath("$.msg").value("用户不存在"));

        // 关键证据：uid=0 确实【被透传】到了 rpc —— 若有 1017 拦截，这里应该是 never()
        verify(stub).getUser(argThat(r -> r.getUid() == 0L));
    }

    /**
     * <b>⚠️ 2026-10-21 反转（DRIFT-016）· 本用例此前断言「空 body 的 POST 不得 400」。</b>
     *
     * <p>原 javadoc 的三段取证里第 2 段写「空 body 走 {@code UnmarshalJsonMap(nil, v)}，
     * 对 struct 是 no-op、<b>不报错</b>」。<b>第 2 段的后半句是错的</b>：
     * {@code core/mapping/jsonunmarshaler.go:19-21} 收的是<b>有类型的 nil map</b>，
     * 在 {@code unmarshaler.go:988} 的 type switch 里命中 {@code case map[string]any}
     * （不会落到 default 的 errUnsupportedType），照常遍历全部字段 ⇒ 标量必填照样报。
     *
     * <p><b>实测坐实</b>（probe-go-required-fields.py）：真空 body（Content-Length: 0）
     * 与 {@code {}} 都返回 {@code 400 field "xxx" is not set}。
     *
     * <p>这与 AGENTS.md §3.4.4 DRIFT-009 同型：<b>引用了真实文件、但把行为读反了</b> ——
     * 而且当时正是这个错误结论让 22 个端点全部设成 {@code required = false} + 零值归一化。
     * <b>教训</b>：不能因为「已有测试锁住」就默认它对（与 DRIFT-009 末尾的评审纪律同源）。
     */
    @Test
    @DisplayName("DRIFT-016：空 body 的 protected 端点必须 400 + text/plain（与 Go 逐字一致）")
    void emptyBodyOnProtectedEndpointIs400TextPlain() throws Exception {
        String body = mockMvc.perform(post("/admin/v1/getuser")
                        .header("Authorization", "Bearer " + validToken())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andReturn().getResponse().getContentAsString();

        assertEquals("field \"uid\" is not set\n", body);
        verify(stub, never()).getUser(any());
    }

    /**
     * 同上，但走 <b>public 端点</b>（无 JWT 拦截器）—— 证明必填校验<b>不依赖鉴权链</b>，
     * 是在 Controller 参数绑定之后、进入 business 层之前发生的。
     *
     * <p>此前本用例期望「零值 username/password 直达 rpc → 1002」，
     * 那是<b>基于错误的第 2 段取证</b>得出的；Go 实测是
     * {@code user.login 真空 body → 400 field "username" is not set}。
     */
    @Test
    @DisplayName("DRIFT-016：空 body 的 public 端点 /login 也必须 400（必填校验不依赖鉴权链）")
    void emptyBodyOnPublicEndpointIs400TextPlain() throws Exception {
        String body = mockMvc.perform(post("/admin/v1/login")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andReturn().getResponse().getContentAsString();

        assertEquals("field \"username\" is not set\n", body);
        verify(stub, never()).login(any());
    }

    // ==================================================================
    // DRIFT-016 · go-zero 必填字段校验（用户 2026-10-21 拍板「向 Go 对齐」）
    //
    // 规则（Go 出处三段，见 GoStyleRequiredFields 的 javadoc）：
    //   必填 ⟺ tag 不含 ,optional AND tag 不含 ,default= AND 字段是标量
    //   Map / Slice / Array 永不计必填
    //
    // 本块的用例分两类，**都必须有**：
    //   正向：该 400 的确 400
    //   反向：该放行的确放行（map 字段、default= 字段、空串）
    // 只写正向的话，一个「全部字段一律必填」的粗暴实现也能全绿。
    // ==================================================================

    @Test
    @DisplayName("DRIFT-016 反向：extra_info 是 map 字段，缺席【不得】400")
    void mapFieldIsNeverRequired() throws Exception {
        when(stub.login(any())).thenThrow(new StatusRuntimeException(Status.UNAUTHENTICATED));

        // 实测：user.register drop extra_info → 200；extra_info={} → 200
        mockMvc.perform(post("/admin/v1/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"a\",\"password\":\"pw123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1002));

        verify(stub).login(any());
    }

    @Test
    @DisplayName("DRIFT-016 反向：page / page_size 带 default=，缺席【不得】400")
    void defaultValuedFieldsAreNotRequired() throws Exception {
        when(stub.getUserList(any())).thenReturn(
                com.rag.users.admin.proto.GetUserListResp.newBuilder().setTotal(0).build());

        // 实测：缺 page / page_size → 200（unmarshaler.go:918 getDefault() 先命中并返回）
        mockMvc.perform(post("/admin/v1/getuserlist")
                        .header("Authorization", "Bearer " + validToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keyword\":\"alice\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(stub).getUserList(any());
    }

    /**
     * 空串在 Go 的 <b>json 通道</b>里是合法值 —— 实测 {@code user.register email=""} ⇒ 200 注册成功。
     * 必填校验只判 {@code null}，不判空串。
     *
     * <p><b>这条与 Go 的 form 通道相反</b>（{@code keyword=} 会被 {@code httpx.GetFormValues}
     * 丢弃空值而退化成缺席 ⇒ 400），但 RAG 全部走 json body，
     * 所以按 json 通道语义统一处理，详见 {@code GetUserListReq} 的 javadoc。
     */
    @Test
    @DisplayName("DRIFT-016 反向：email=\"\" 空串【不得】被当缺失（Go json 通道接受空串）")
    void emptyStringIsNotTreatedAsMissing() throws Exception {
        when(stub.updateSelf(any())).thenThrow(new StatusRuntimeException(Status.NOT_FOUND));

        mockMvc.perform(post("/admin/v1/update")
                        .header("Authorization", "Bearer " + validToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"\",\"email\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1003));

        verify(stub).updateSelf(any());
    }

    /** 显式 JSON null 在 Go 侧是 400 {@code field "xxx" mustn't be nil}；
     *  Java 侧 null 与「缺失」塌缩成同一个判据，返回 {@code is not set}。
     *  <b>状态码与 Content-Type 一致，只有错误文本不同</b>，与 DRIFT-006 的对齐边界同款。 */
    @Test
    @DisplayName("DRIFT-016：显式 null 同样 400（错误文本与缺失塌缩，是已知的表述差异）")
    void explicitNullIsRejected() throws Exception {
        String body = mockMvc.perform(post("/admin/v1/getuser")
                        .header("Authorization", "Bearer " + validToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"uid\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andReturn().getResponse().getContentAsString();

        assertEquals("field \"uid\" is not set\n", body);
        verify(stub, never()).getUser(any());
    }

    /**
     * <b>⚠️ 2026-10-21 实证推翻了 N-6 留档（DRIFT-016 审计期间发现）。</b>
     *
     * <p>§3.4.3 的 N-6 写「uid=abc 经过 {@code parseUid} 拦截 → <b>200 + 1017 envelope</b>」。
     * <b>代码里到不了 {@code parseUid}</b>：{@code GetUserReq.uid} 的类型是 {@link Long}，
     * admin-api <b>没有</b>自定义 ObjectMapper（grep 零命中），所以 Jackson 默认
     * 在反序列化阶段就抛 {@code InvalidFormatException} → Spring 包成
     * {@code HttpMessageNotReadableException} → {@code handleUnreadableBody} →
     * {@code 400 + text/plain}。
     *
     * <p>而 Go 侧 {@code uid=abc} 同样是 {@code HTTP 400 + text/plain}
     * （实测：{@code strconv.ParseInt: parsing "abc": invalid syntax}），
     * 只是<b>文本内容</b>来自不同框架 —— 与 DRIFT-006 已确立的「形状对齐、文本不对齐」边界一致。
     *
     * <p>本用例的<b>真实价值</b>是双重的：既锁住 400 + text/plain 这个形状，
     * 又作为「必填校验只判存在性、不判合法性」的负向对照 ——
     * 若把校验写成值校验（判 {@code abc} 非法），本条会先在别处红掉。
     */
    @Test
    @DisplayName("N-6 留档证伪 + DRIFT-016 负向对照：uid=\"abc\" 是 400（Jackson 拒），且必填校验只判存在性")
    void nonNumericUidIsRejectedByJacksonNotByRequiredCheck() throws Exception {
        mockMvc.perform(post("/admin/v1/getuser")
                        .header("Authorization", "Bearer " + validToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"uid\":\"abc\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN));

        // 两条独立证据：
        //  1) rpc 一次都没被调用 —— 错误发生在到达 business 层之前
        //  2) 错误文本不是我们自己的 "field \"uid\" is not set" —— 说明它来自 Jackson
        String body = mockMvc.perform(post("/admin/v1/getuser")
                        .header("Authorization", "Bearer " + validToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"uid\":\"abc\"}"))
                .andReturn().getResponse().getContentAsString();
        assertFalse(body.contains("is not set"), "错误文本不应来自必填校验，实际：" + body);

        verify(stub, never()).getUser(any());
    }

    // ==================================================================
    // helper
    // ==================================================================

    private static void assertThatJsonContains(String body, String fragment) {
        assertTrue(body.contains(fragment),
                "响应体应包含 " + fragment + "，实际：" + body);
    }

}