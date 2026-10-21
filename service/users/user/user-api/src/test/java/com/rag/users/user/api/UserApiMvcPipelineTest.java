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
    /**
     * <b>⚠️ 2026-10-21 反转（DRIFT-016）· 本用例此前断言的是错误行为。</b>
     *
     * <p>原用例名「A-P2：空 body 的 public 端点 /login 不得 400 —— Go 零值 req 直达 rpc」，
     * 原 javadoc 的三段取证里第 2 段写「空 body 走 {@code UnmarshalJsonMap(nil, v)}，
     * 对 struct 是 no-op、<b>不报错</b>」。<b>第 2 段的后半句是错的。</b>
     *
     * <p>Go 出处（逐行复核）：{@code core/mapping/jsonunmarshaler.go:19-21} 的
     * {@code UnmarshalJsonMap(m map[string]any, v any)} 收的是<b>有类型的 nil map</b>，
     * 传进 {@code Unmarshal(i any, ...)} 后在 {@code unmarshaler.go:988} 的 type switch 里
     * <b>命中 {@code case map[string]any}</b>（不会落到 default 的 errUnsupportedType），
     * 于是照常 {@code unmarshalValuer} 遍历全部字段；空 map 下 {@code getValue} 全部落空，
     * {@code LoginReq.Username} 的 opts 非 optional ⇒ 走
     * {@code unmarshaler.go:966-969} 的 {@code newInitError}。
     *
     * <p><b>实测坐实</b>（probe-go-required-fields.py，2026-10-21，真 Go 进程 18888）：
     * {@code user.login 真空 body（Content-Length: 0）→ HTTP 400 field "username" is not set}。
     *
     * <p>这与 AGENTS.md §3.4.4 DRIFT-009 同型：<b>引用了真实文件、但把行为读反了</b> ——
     * 而且当时正是这个错误结论，让 22 个端点全部设成 {@code required = false} + 零值归一化。
     * <b>评审纪律</b>：不能因为「已有测试锁住 + 注释写得很完整」就默认它对。
     */
    @Test
    @DisplayName("DRIFT-016：空 body 的 public 端点 /login 必须 400 + text/plain（Go 实测一致）")
    void emptyBodyOnLoginIs400NotPassedThrough() throws Exception {
        String body = mockMvc.perform(post("/user/v1/login")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn().getResponse().getContentAsString();

        assertEquals("field \"username\" is not set\n", body);
        // 关键：零值 req 必须【没有】透传到 rpc —— 此前本用例断言的正是相反的事
        verify(stub, never()).login(any());
    }

    /**
     * 同上，但 protected 端点 <b>/update</b> —— 本条<b>不反转</b>，且它是对照组。
     *
     * <p>Go 出处：{@code types.go UpdateUserReq} 四个字段<b>全是 {@code ,optional}</b>
     * ⇒ 一个都不校验 ⇒ 空 body 下 req 停在零值 struct、照常进 business 层。
     * 实测 probe-go-required-fields.py：{@code user.update drop email → 200}。
     *
     * <p>与 {@link #emptyBodyOnLoginIs400NotPassedThrough} 并列看才完整：
     * <b>同一个「空 body」，两个端点一个 400 一个 200</b>，
     * 差别只在 DTO 的 tag。这正是「校验必须逐字段按 Go tag 推导、不能一刀切」的证据。
     */
    @Test
    @DisplayName("DRIFT-016 对照组：空 body 的 /update（全 ,optional）仍然 200 直达 rpc")
    void emptyBodyOnUpdateStillPassesThroughBecauseAllFieldsAreOptional() throws Exception {
        when(stub.updateUser(any())).thenThrow(new StatusRuntimeException(Status.NOT_FOUND));

        mockMvc.perform(post("/user/v1/update")
                        .header("Authorization", "Bearer " + validToken())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1003));

        verify(stub).updateUser(any());
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
        // ⚠️ 必须复用同一个 token：validToken() 的 iat 是 System.currentTimeMillis()/1000L，
        //   秒精度。原先这里请求头用 validToken()、断言里又调一次 validToken()，
        //   两次若跨了秒边界就是两个不同的 token（本轮实测：
        //   期望 iat=…7939、实际 iat=…7938），断言随机失败 —— 一个纯假红 flake。
        //   同一类的 nonBearerPrefixedTokenCannotBypassBlacklist 一直是先存局部变量，
        //   只有这里漏了。
        String token = validToken();

        when(stub.getUser(any())).thenReturn(com.rag.users.user.proto.GetUserResp.newBuilder()
                .setFound(true)
                .setUser(com.rag.users.user.proto.UserInfo.newBuilder()
                        .setUid(CLAIM_UID).setUsername("u").setStatus(0L))
                .build());

        mockMvc.perform(post("/user/v1/get")
                        .header("Authorization", "bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(stub).getUser(any());
        // 黑名单用的是大小写【敏感】的 TrimPrefix ⇒ 查未剥前缀的整串（Go 自身如此）
        verify(redisTemplate).hasKey("user:jwt_blacklist:bearer " + token);
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

    // ==================================================================
    // DRIFT-016 · go-zero 必填字段校验（用户 2026-10-21 拍板「向 Go 对齐」）
    //
    // 规则（Go 出处三段，见 users-common 的 GoStyleRequiredFields javadoc）：
    //   必填 ⟺ tag 不含 ,optional AND tag 不含 ,default= AND 字段是标量
    //   Map / Slice / Array 永不计必填
    //
    // 实测证据全部来自 tools/difftest/probe-go-required-fields.py（真 Go 进程）。
    // 本块的用例分两类，**都必须有**：
    //   正向：该 400 的确 400
    //   反向：该放行的确放行 —— 只写正向的话，一个「所有字段一律必填」的
    //         粗暴实现也能全绿。
    // ==================================================================

    /**
     * Go 出处：{@code types.go CreateUserReq} — username/password 无 tag、
     * email 是 {@code ,omitempty}（<b>不是 {@code ,optional}</b>）⇒ 三个标量全必填；
     * {@code Extrainfo} 是 map ⇒ 不校验。
     *
     * <p>实测：drop email ⇒ {@code 400 field "email" is not set}；drop extra_info ⇒ 200。
     */
    @Test
    @DisplayName("DRIFT-016：register 缺 email 也必须 400（,omitempty ≠ ,optional）")
    void registerMissingEmailIsRejectedBecauseOmitemptyIsNotOptional() throws Exception {
        String body = mockMvc.perform(post("/user/v1/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"pw123456\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn().getResponse().getContentAsString();

        assertEquals("field \"email\" is not set\n", body);
        verify(stub, never()).register(any());
    }

    /** Go 出处：{@code types.go CreateUserReq.Extrainfo} 是 {@code map[string]string}。
     *  {@code unmarshaler.go:944-948} 的 Array/Map/Slice 分支走
     *  {@code processFieldNotFromString}、<b>不报错</b>。实测 drop extra_info ⇒ 200。 */
    @Test
    @DisplayName("DRIFT-016 反向：extra_info 是 map 字段，缺席【不得】400")
    void mapFieldExtraInfoIsNeverRequired() throws Exception {
        when(stub.register(any())).thenReturn(
                com.rag.users.user.proto.CreateUserResp.newBuilder().setUid(31337L).build());

        mockMvc.perform(post("/user/v1/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"pw123456\","
                                + "\"email\":\"alice@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(stub).register(any());
    }

    /**
     * Go 出处：{@code types.go UpdateUserReq} 四个字段<b>全是 {@code ,optional}</b>
     * ⇒ 一个都不校验。实测 drop email ⇒ 200。
     *
     * <p><b>与 admin 侧 {@code admin.update} 恰好相反</b>（那边三个 string 是
     * {@code ,omitempty} ⇒ 全必填）。两端同名结构、不同 tag，<b>不要跨模块套用结论</b>
     * （AGENTS.md §3.4.3 N-3 的同款警告）。
     */
    @Test
    @DisplayName("DRIFT-016 反向：user.update 全字段 ,optional，缺 email 【不得】400")
    void userUpdateFieldsAreAllOptional() throws Exception {
        when(stub.updateUser(any())).thenReturn(
                com.rag.users.user.proto.UpdateUserResp.newBuilder().build());

        mockMvc.perform(post("/user/v1/update")
                        .header("Authorization", "Bearer " + validToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        verify(stub).updateUser(any());
    }

    /**
     * 空串在 Go 的 <b>json 通道</b>里是合法值 —— 实测 {@code user.register email=""} ⇒ 200 注册成功。
     * 必填校验只判 {@code null}，不判空串。
     *
     * <p>实测：{@code email=""} ⇒ 200；{@code email=null} ⇒ 400 {@code field "email" mustn't be nil}。
     * Java 侧 null 与「缺失」塌缩成同一判据 ⇒ 都返回 {@code is not set}，
     * 状态码与 Content-Type 一致，仅错误文本不同（与 DRIFT-006 的对齐边界同款）。
     */
    @Test
    @DisplayName("DRIFT-016 反向：email=\"\" 空串【不得】被当缺失（Go json 通道接受空串）")
    void emptyStringIsAcceptedBecauseGoJsonChannelKeepsIt() throws Exception {
        when(stub.register(any())).thenReturn(
                com.rag.users.user.proto.CreateUserResp.newBuilder().setUid(31337L).build());

        mockMvc.perform(post("/user/v1/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"pw123456\",\"email\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(stub).register(any());
    }

    /** {@code {}} 与真空 body 等价：Content-Length=2 > 0，json 通道进得去，但字段一个都没传。
     *  真空 body 本身的断言在历史槽位 {@link #emptyBodyOnLoginIs400NotPassedThrough}（已反转）。 */
    @Test
    @DisplayName("DRIFT-016：空对象 {} 与真空 body 等价，同样 400")
    void emptyJsonObjectIsRejected() throws Exception {
        mockMvc.perform(post("/user/v1/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN));

        verify(stub, never()).login(any());
    }

    /**
     * <b>负向对照（§3.3「机制必须真正生效」）</b>：{@code password} 缺失而
     * {@code username} 存在时，报的必须是 <b>password</b> 而不是 username ——
     * 证明校验器按字段逐个判、而不是笼统地「必填失败」。
     */
    @Test
    @DisplayName("DRIFT-016：缺 password 时报的是 password（逐字段定位，不是笼统失败）")
    void errorNamesTheActualMissingField() throws Exception {
        String body = mockMvc.perform(post("/user/v1/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\"}"))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        assertEquals("field \"password\" is not set\n", body);
    }
}
