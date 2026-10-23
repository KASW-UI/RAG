package com.rag.users.user.api;

import com.rag.users.common.jwt.JwtUtil;
import com.rag.users.user.api.client.UserRpcClient;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 🔴 守门测试 —— <b>claim 语义</b>（DRIFT-USERAPI-001 / DRIFT-USERAPI-002）。
 *
 * <h2>这两条为什么是 SEMANTIC MISMATCH（而不是表达层差异）</h2>
 * <p>Go 侧<b>并存两套</b>规则，RAG 曾把它们合并成一套：
 * <table border="1">
 *   <caption>Go 出处（消费点）</caption>
 *   <tr><th>场景</th><th>Go</th><th>RAG（修复前）</th></tr>
 *   <tr>
 *     <td>签名合法、userId claim <b>缺失 / 是字符串</b></td>
 *     <td>{@code rest/handler/authhandler.go:54-81}：只有 {@code ParseToken} 失败与
 *         {@code !tok.Valid} 两条会 401；之后把 claim 塞进 ctx 就 {@code next.ServeHTTP}
 *         ⇒ 请求<b>到达 handler</b> ⇒ {@code identity/resolve.go:70-73} type assertion 失败
 *         ⇒ {@code ErrInvalidClaim} ⇒ {@code httpx.OkJson} ⇒ <b>HTTP 200 + 1006</b></td>
 *     <td>{@code JwtAuthInterceptor} 在鉴权层抛 {@code AuthenticationException} ⇒ <b>HTTP 401</b></td>
 *   </tr>
 *   <tr>
 *     <td>签名合法、<b>userId = 0</b>，打 delete / update</td>
 *     <td>{@code deleteuserlogic.go:37-47} / {@code updateuserlogic.go:36-46}：
 *         只有 type assertion 失败才 1006，<b>没有 uid&lt;=0 判定</b>
 *         ⇒ uid=0 <b>透传给 rpc</b> ⇒ rpc NOT_FOUND ⇒ <b>1003</b></td>
 *     <td>{@code IdentityResolver.claimedUID} 统一拒 uid&lt;=0 ⇒ <b>1006</b>，且 <b>0 次</b> rpc 调用</td>
 *   </tr>
 * </table>
 *
 * <h2>双侧活体对拍实测（2026-10-23 21:12，真进程 + 真 PG + 真 Redis）</h2>
 * <pre>
 *   claim 缺失（无 userId）：Go 200 {"code":1006,…}   RAG 401 {"code":1006,…}   ← 修复前
 *   claim 是字符串        ：Go 200 {"code":1006,…}   RAG 401 {"code":1006,…}   ← 修复前
 *   userId=0 → delete    ：Go 200 {"code":1003,"msg":"用户不存在",…}
 *                         RAG 200 {"code":1006,…}                              ← 修复前
 *   userId=0 → update    ：Go 200 {"code":1003,"msg":"用户不存在",…}
 *                         RAG 200 {"code":1006,…}                              ← 修复前
 *   userId=0 → get       ：Go 200 {"code":1006,…}   RAG 200 {"code":1006,…}   ← 本就一致
 * </pre>
 *
 * <h2>本类的证据强度（诚实边界）</h2>
 * <p><b>user-api 自身不访问任何数据库</b>（§4 矩阵第 57 行：本组件无 DB 访问），
 * 所以「真 PG 守门」在本组件<b>不适用</b>；本类给的是：
 * <ul>
 *   <li><b>真 Spring MVC 管线</b>（真 {@code HttpMiddleware} filter + 真
 *       {@code JwtAuthInterceptor} + 真 advice），不是 {@code standaloneSetup}；</li>
 *   <li><b>真 Redis</b>（Testcontainers）—— 黑名单那条路径必须真连；</li>
 *   <li><b>唯一被替换的是 gRPC 边界</b>（{@code UserRpcClient}）——
 *       与 {@code UserApiMvcPipelineTest} 同款取舍。</li>
 * </ul>
 * ⇒ <b>本类是回归守门，不是一致性证据</b>；一致性证据是上面的<b>双侧活体对拍</b>。
 *
 * <h2>可证伪性</h2>
 * <ul>
 *   <li>把拦截器的 claim 分支改回抛 {@code AuthenticationException} → 用例 1 / 2 立刻红（变 401）；</li>
 *   <li>把 delete / update 改回用 {@code claimedUID} → 用例 3 / 4 立刻红（rpc 不再被调用）；</li>
 *   <li>把 get 改用 {@code claimedUIDAllowNonPositive} → 用例 5 立刻红（get 会放行 uid=0）。</li>
 * </ul>
 */
@SpringBootTest(
        classes = UserApiApplication.class,
        properties = {
                "grpc.client.user-rpc.address=static://127.0.0.1:1",
                "spring.cloud.nacos.discovery.enabled=false",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN"
        })
@AutoConfigureMockMvc
@Testcontainers
class UserApiClaimSemanticsParityTest {

    /** 与 user-api 的 application.yml 保持同一份密钥，否则签出来的 token 验签必失败。 */
    private static final String SECRET = "ae0536f9-6450-4606-8e13-5a19ed505da0";
    private static final long EXPIRE_SEC = 2592000L;

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
    @MockitoSpyBean
    private StringRedisTemplate redisTemplate;
    @MockitoBean
    private UserRpcClient userRpcClient;

    private com.rag.users.user.proto.UserServiceGrpc.UserServiceBlockingStub stub;

    @BeforeEach
    void setUp() {
        stub = Mockito.mock(com.rag.users.user.proto.UserServiceGrpc.UserServiceBlockingStub.class);
        Mockito.when(userRpcClient.userRpc()).thenReturn(stub);
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
        Mockito.clearInvocations(redisTemplate);
    }

    // ==================================================================
    // DRIFT-USERAPI-001：畸形 claim 必须是 200 + 1006，不是 401
    // ==================================================================

    @Test
    @DisplayName("Go: userId claim 缺失 → HTTP 200 + 1006（不是 401）")
    void missingUserIdClaim_isRejectedByBusinessLayer_notByAuthLayer() throws Exception {
        mockMvc.perform(post("/user/v1/get")
                        .header("Authorization", "Bearer " + tokenWithoutUserIdClaim())
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1006));

        Mockito.verify(stub, Mockito.never()).getUser(Mockito.any());
    }

    @Test
    @DisplayName("Go: userId claim 是字符串 → HTTP 200 + 1006（不是 401）")
    void stringUserIdClaim_isRejectedByBusinessLayer_notByAuthLayer() throws Exception {
        mockMvc.perform(post("/user/v1/get")
                        .header("Authorization", "Bearer " + tokenWithStringUserIdClaim())
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1006));

        Mockito.verify(stub, Mockito.never()).getUser(Mockito.any());
    }

    // ==================================================================
    // DRIFT-USERAPI-002：delete / update 的 uid<=0 必须**透传**给 rpc
    // ==================================================================

    @Test
    @DisplayName("Go: userId=0 打 delete → uid=0 必须透传给 rpc，最终 1003（不是 1006）")
    void deleteWithZeroUid_passesThroughToRpc() throws Exception {
        Mockito.when(stub.deleteUser(Mockito.any()))
                .thenThrow(new StatusRuntimeException(Status.NOT_FOUND));

        mockMvc.perform(post("/user/v1/delete")
                        .header("Authorization", "Bearer " + tokenWithUid(0L))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1003));

        ArgumentCaptor<com.rag.users.user.proto.DeleteUserReq> captor =
                ArgumentCaptor.forClass(com.rag.users.user.proto.DeleteUserReq.class);
        Mockito.verify(stub).deleteUser(captor.capture());
        assertEquals(0L, captor.getValue().getUid(),
                "Go 的 deleteuserlogic.go:37-47 没有 uid<=0 判定 ⇒ uid=0 必须原样进 rpc");
    }

    @Test
    @DisplayName("Go: userId=0 打 update → uid=0 必须透传给 rpc，最终 1003（不是 1006）")
    void updateWithZeroUid_passesThroughToRpc() throws Exception {
        Mockito.when(stub.updateUser(Mockito.any()))
                .thenThrow(new StatusRuntimeException(Status.NOT_FOUND));

        mockMvc.perform(post("/user/v1/update")
                        .header("Authorization", "Bearer " + tokenWithUid(0L))
                        .contentType("application/json")
                        .content("{\"email\":\"someone@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1003));

        ArgumentCaptor<com.rag.users.user.proto.UpdateUserReq> captor =
                ArgumentCaptor.forClass(com.rag.users.user.proto.UpdateUserReq.class);
        Mockito.verify(stub).updateUser(captor.capture());
        assertEquals(0L, captor.getValue().getUid(),
                "Go 的 updateuserlogic.go:36-46 没有 uid<=0 判定 ⇒ uid=0 必须原样进 rpc");
    }

    // ==================================================================
    // 对照组：Go 侧**确实**拒绝 uid<=0 的端点，必须仍然拒绝
    // ==================================================================

    @Test
    @DisplayName("Go（对照组）: userId=0 打 get → 1006 且 rpc 一次都不该被调用")
    void getWithZeroUid_isRejectedAndNeverReachesRpc() throws Exception {
        mockMvc.perform(post("/user/v1/get")
                        .header("Authorization", "Bearer " + tokenWithUid(0L))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1006));

        Mockito.verify(stub, Mockito.never()).getUser(Mockito.any());
    }

    @Test
    @DisplayName("Go（对照组）: userId=0 打 avatar/history → 1006 且 rpc 一次都不该被调用")
    void avatarHistoryWithZeroUid_isRejectedAndNeverReachesRpc() throws Exception {
        mockMvc.perform(post("/user/v1/avatar/history")
                        .header("Authorization", "Bearer " + tokenWithUid(0L))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1006));

        Mockito.verify(stub, Mockito.never()).getAvatarHistory(Mockito.any());
    }

    // ==================================================================
    // 对照组：真正的鉴权失败仍然必须是 401（N-1 不受影响）
    // ==================================================================

    @Test
    @DisplayName("Go（对照组）: 签名无效 → 仍然 401（N-1 的 body 形状不变）")
    void invalidSignature_stillReturns401() throws Exception {
        mockMvc.perform(post("/user/v1/get")
                        .header("Authorization", "Bearer not-a-jwt")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1006));
    }

    // ==================================================================
    // helpers
    // ==================================================================

    private static SecretKey key() {
        return Keys.hmacShaKeyFor(JwtUtil.padToHmacKeySize(SECRET));
    }

    /** 签名合法、userId claim **缺失**。 */
    private static String tokenWithoutUserIdClaim() {
        long now = System.currentTimeMillis() / 1000L;
        return Jwts.builder()
                .claim("exp", new Date((now + EXPIRE_SEC) * 1000L))
                .claim("iat", new Date(now * 1000L))
                .signWith(key())
                .compact();
    }

    /** 签名合法、userId claim 是**字符串**。 */
    private static String tokenWithStringUserIdClaim() {
        long now = System.currentTimeMillis() / 1000L;
        return Jwts.builder()
                .claim("userId", "12345")
                .claim("exp", new Date((now + EXPIRE_SEC) * 1000L))
                .claim("iat", new Date(now * 1000L))
                .signWith(key())
                .compact();
    }

    /** 签名合法、userId = 指定值（可为 0 / 负数）。 */
    private static String tokenWithUid(long uid) {
        long now = System.currentTimeMillis() / 1000L;
        return Jwts.builder()
                .claim("userId", uid)
                .claim("exp", new Date((now + EXPIRE_SEC) * 1000L))
                .claim("iat", new Date(now * 1000L))
                .signWith(key())
                .compact();
    }
}
