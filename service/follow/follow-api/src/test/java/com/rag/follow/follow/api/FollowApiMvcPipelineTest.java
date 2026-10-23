package com.rag.follow.follow.api;

import com.rag.follow.follow.api.client.FollowRpcClient;
import com.rag.follow.follow.api.metrics.FollowApiMetrics;
import com.rag.follow.follow.proto.BaseResp;
import com.rag.follow.follow.proto.FollowServiceGrpc.FollowServiceBlockingStub;
import com.rag.follow.follow.proto.ListReq;
import com.rag.follow.follow.proto.RecommendResp;
import com.rag.follow.follow.proto.RelationReq;
import com.rag.follow.follow.proto.UserListResp;
import com.rag.users.common.jwt.JwtUtil;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * follow-api 真实 MVC 管线测试 —— 锁住 Go {@code service/follow/api} 的 L1 / L2 契约。
 *
 * <p><b>为什么必须是「真实管线」而不是 standaloneSetup</b>：本服务最关键的性质
 * 全部只有真实管线才测得到：
 * <table border="1">
 *   <caption>standaloneSetup 测不到的几件事</caption>
 *   <tr><th>性质</th><th>为什么</th></tr>
 *   <tr>
 *     <td>{@code offset} 收到 JSON 字符串被拒</td>
 *     <td>取决于 Spring MVC 实际使用的 Jackson 3 mapper 上是否真的挂了
 *         {@code FollowGoStyleCoercion} 的 customizer。单独 new 一个 mapper 造断言是假绿
 *         （{@code GoStyleCoercion} 的 javadoc 记录过第一版就是这么骗过自己的）</td>
 *   </tr>
 *   <tr>
 *     <td>401 + <b>空 body</b>（验签失败）</td>
 *     <td>取决于 {@code HttpMiddlewareFilter} 与 {@code ResponseBodyAdvice} 的相互作用；
 *         AGENTS.md 的 DRIFT-FRAMEWORK-001 就是在这层丢的</td>
 *   </tr>
 *   <tr>
 *     <td>黑名单 401 的<b>两字段 body</b>不被包成三字段</td>
 *     <td>取决于 advice 链是否被绕过</td>
 *   </tr>
 *   <tr>
 *     <td>未注册路径 404 / 方法不符 405（不被鉴权遮蔽）</td>
 *     <td>取决于 {@code WebMvcConfig#addInterceptors} 与两个拦截器的 HandlerMethod 判据</td>
 *   </tr>
 * </table>
 *
 * <p><b>唯一被替换的是两个外部边界</b>（{@link FollowRpcClient} 与
 * {@link StringRedisTemplate}）—— 被测语义全在 api 进程内：
 * 路由 → 拦截器 → Service → advice → 序列化。
 */
@SpringBootTest(
        classes = FollowApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "grpc.client.follow-rpc.address=static://127.0.0.1:65535",
                "grpc.client.follow-rpc.negotiation-type=plaintext",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN"
        })
@AutoConfigureMockMvc
class FollowApiMvcPipelineTest {

    /** 与 application.yml 的 {@code follow-api.user-auth.access-secret} 同一份。 */
    private static final String USER_SECRET = "ae0536f9-6450-4606-8e13-5a19ed505da0";
    private static final long EXPIRE_SEC = 3600L;
    private static final long CLAIM_UID = 31337L;
    private static final long OTHER_UID = 4242L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FollowApiMetrics metrics;

    @MockitoBean
    private FollowRpcClient rpcClient;

    @MockitoBean
    private StringRedisTemplate redisTemplate;

    private FollowServiceBlockingStub stub;

    @BeforeEach
    void setUp() {
        stub = Mockito.mock(FollowServiceBlockingStub.class);
        Mockito.when(rpcClient.followRpc()).thenReturn(stub);
        // 默认：token 不在黑名单里。Redis 异常路径另有用例单独覆盖。
        Mockito.when(redisTemplate.hasKey(Mockito.anyString())).thenReturn(Boolean.FALSE);
    }

    private static String userToken() {
        return JwtUtil.getToken(USER_SECRET, System.currentTimeMillis() / 1000L, EXPIRE_SEC, CLAIM_UID);
    }

    /** 构造一个 {@code userId} claim 为<b>字符串</b>的 token（follow 域允许，Go helper.go:23）。 */
    private static String tokenWithStringUserIdClaim(String claimValue) {
        return Jwts.builder()
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + 3600_000L))
                .claim("userId", claimValue)
                .signWith(Keys.hmacShaKeyFor(JwtUtil.padToHmacKeySize(USER_SECRET)))
                .compact();
    }

    /** 构造一个<b>不带</b> userId claim 的 token。 */
    private static String tokenWithoutUserIdClaim() {
        return Jwts.builder()
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + 3600_000L))
                .claim("scope", "user")
                .signWith(Keys.hmacShaKeyFor(JwtUtil.padToHmacKeySize(USER_SECRET)))
                .compact();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static BaseResp okBase() {
        return BaseResp.newBuilder().setCode(200).setMsg("OK").build();
    }

    // ==================================================================
    // 1. 鉴权：三种 401/未鉴权形态
    // ==================================================================

    @Test
    @DisplayName("无 Authorization → HTTP 401 且 body 为空（Go authhandler.go:106 unauthorized()）")
    void missingAuthorizationIs401WithEmptyBody() throws Exception {
        mockMvc.perform(post("/follow/v1/follow/list")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(""));
    }

    @Test
    @DisplayName("签名错（用别的 secret 签）→ HTTP 401 且 body 为空")
    void wrongSignatureIs401WithEmptyBody() throws Exception {
        String foreignToken = JwtUtil.getToken("00000000-0000-0000-0000-000000000000",
                System.currentTimeMillis() / 1000L, EXPIRE_SEC, CLAIM_UID);
        mockMvc.perform(post("/follow/v1/follow/list")
                        .header("Authorization", bearer(foreignToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(""));
    }

    @Test
    @DisplayName("命中黑名单 → 401 + Go 的两字段 body + 逐字节 Content-Type（checkblacklistmiddleware.go:42-48）")
    void blacklistedTokenHasGoExactBody() throws Exception {
        Mockito.when(redisTemplate.hasKey(Mockito.anyString())).thenReturn(Boolean.TRUE);

        String token = userToken();
        mockMvc.perform(post("/follow/v1/follow/list")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                // ⚠️ 逐字节断言：Go 是 `{"code":5004,"msg":"unauthorized"}`，**没有 data 字段**。
                //    若被 GlobalResponseAdvice 包一层就变成三字段，这条断言当场红。
                .andExpect(content().string("{\"code\":5004,\"msg\":\"unauthorized\"}"))
                // ⚠️ Go 字面量里分号后**没有空格**
                .andExpect(content().contentType("application/json;charset=utf-8"));

        // 黑名单 key 必须是 user:jwt_blacklist:<token>（写错前缀 ⇒ 永远不命中，且零报错）
        verify(redisTemplate).hasKey("user:jwt_blacklist:" + token);
        // 命中即短路：handler 不得被执行
        verify(stub, never()).getFollowList(any());
    }

    @Test
    @DisplayName("Redis 故障 → fail-open（Go: err != nil ⇒ 放行），不返回 401")
    void redisFailureFailsOpen() throws Exception {
        Mockito.when(redisTemplate.hasKey(Mockito.anyString()))
                .thenThrow(new RuntimeException("redis down"));
        when(stub.getFollowList(any())).thenReturn(okUserList(1L, 2L));

        mockMvc.perform(post("/follow/v1/follow/list")
                        .header("Authorization", bearer(userToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("token 无 userId claim → HTTP 200 + {code:5004}（不是 401，Go 归为业务未鉴权）")
    void missingUserIdClaimIs200With5004() throws Exception {
        mockMvc.perform(post("/follow/v1/follow/list")
                        .header("Authorization", bearer(tokenWithoutUserIdClaim()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5004))
                .andExpect(jsonPath("$.msg").value("unauthorized"));

        verify(stub, never()).getFollowList(any());
    }

    @Test
    @DisplayName("⚠️ 字符串 userId claim 被接受（Go helper.go:23 case string），与 message 域不同")
    void stringUserIdClaimIsAccepted() throws Exception {
        when(stub.getFollowList(any())).thenReturn(okUserList(7L));

        mockMvc.perform(post("/follow/v1/follow/list")
                        .header("Authorization", bearer(tokenWithStringUserIdClaim("777")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<ListReq> captor = ArgumentCaptor.forClass(ListReq.class);
        verify(stub).getFollowList(captor.capture());
        assertEquals(777L, captor.getValue().getUserId());
    }

    @Test
    @DisplayName("浮点 userId claim → 200 + {code:5004}（不截断成合法 uid）")
    void floatingUserIdClaimIs5004() throws Exception {
        mockMvc.perform(post("/follow/v1/follow/list")
                        .header("Authorization", bearer(tokenWithStringUserIdClaim("1.5")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5004));
    }

    // ==================================================================
    // 2. 请求解析：,string 双向接受 + 必填 + 类型不匹配
    // ==================================================================

    @Test
    @DisplayName("动作端点成功 → 200 {code:200,msg:OK,data:{success:true}} 且透传 claim 的 userId")
    void followSuccessEnvelope() throws Exception {
        when(stub.follow(any())).thenReturn(okBase());

        mockMvc.perform(post("/follow/v1/follow")
                        .header("Authorization", bearer(userToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target_id\":\"999\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.msg").value("OK"))
                .andExpect(jsonPath("$.data.success").value(true));

        ArgumentCaptor<RelationReq> captor = ArgumentCaptor.forClass(RelationReq.class);
        verify(stub).follow(captor.capture());
        assertEquals(CLAIM_UID, captor.getValue().getUserId());
        assertEquals(999L, captor.getValue().getTargetId());
    }

    @Test
    @DisplayName("target_id 缺席 → 200 + {code:5001}（Go: field \"target_id\" is not set ⇒ 折叠成 5001）")
    void missingTargetIdIs5001() throws Exception {
        mockMvc.perform(post("/follow/v1/follow")
                        .header("Authorization", bearer(userToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5001))
                .andExpect(jsonPath("$.msg").value("internal server error"));

        verify(stub, never()).follow(any());
    }

    @Test
    @DisplayName("⚠️ target_id 写成 JSON 数字也被接受（Go 的 json.Number Kind() 是 string ⇒ ,string 双向放行）")
    void targetIdAsJsonNumberIsAccepted() throws Exception {
        when(stub.block(any())).thenReturn(okBase());

        mockMvc.perform(post("/follow/v1/block")
                        .header("Authorization", bearer(userToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target_id\":123}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<RelationReq> captor = ArgumentCaptor.forClass(RelationReq.class);
        verify(stub).block(captor.capture());
        assertEquals(123L, captor.getValue().getTargetId());
    }

    @Test
    @DisplayName("target_id 是浮点 → 200 + {code:5001}（Go: strconv.ParseInt(\"1.5\") 失败）")
    void floatingTargetIdIs5001() throws Exception {
        mockMvc.perform(post("/follow/v1/unblock")
                        .header("Authorization", bearer(userToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target_id\":1.5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5001));
    }

    @Test
    @DisplayName("⚠️ offset 收到 JSON 字符串 → 200 + {code:5001}（证明 FollowGoStyleCoercion 真的挂上了）")
    void stringOffsetIsRejectedByGoStyleCoercion() throws Exception {
        // ⚠️ 这条是「注解存在 ≠ 机制生效」的反向用例：Jackson 3 默认会安静接受 "10" → 10，
        //    只有 customizer 真的被 Boot 应用到 MVC 的 mapper 上，本断言才会红。
        mockMvc.perform(post("/follow/v1/follow/list")
                        .header("Authorization", bearer(userToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"offset\":\"10\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5001));

        verify(stub, never()).getFollowList(any());
    }

    @Test
    @DisplayName("limit 收到 JSON 字符串同样被拒；数字与浮点按 Go 规则判别")
    void stringLimitIsRejectedToo() throws Exception {
        mockMvc.perform(post("/follow/v1/follower/list")
                        .header("Authorization", bearer(userToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"limit\":\"5\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5001));
    }

    @Test
    @DisplayName("user_id 写成 JSON 字符串被接受（FollowListReq.user_id 带 ,string）")
    void listUserIdAsStringIsAccepted() throws Exception {
        when(stub.getFollowList(any())).thenReturn(okUserList(3L));

        mockMvc.perform(post("/follow/v1/follow/list")
                        .header("Authorization", bearer(userToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"user_id\":\"55\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<ListReq> captor = ArgumentCaptor.forClass(ListReq.class);
        verify(stub).getFollowList(captor.capture());
        assertEquals(55L, captor.getValue().getUserId());
    }

    @Test
    @DisplayName("空 body 的 list 端点按零值继续（Go: UnmarshalJsonMap(nil, v)，三字段全 optional）")
    void emptyBodyOnListEndpointUsesZeroValues() throws Exception {
        when(stub.getFollowerList(any())).thenReturn(okUserList());

        mockMvc.perform(post("/follow/v1/follower/list")
                        .header("Authorization", bearer(userToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<ListReq> captor = ArgumentCaptor.forClass(ListReq.class);
        verify(stub).getFollowerList(captor.capture());
        assertEquals(CLAIM_UID, captor.getValue().getUserId());
        assertEquals(0, captor.getValue().getOffset());
        assertEquals(0, captor.getValue().getLimit());
    }

    @Test
    @DisplayName("空 body 的动作端点 → 200 + {code:5001}（target_id 必填）")
    void emptyBodyOnActionEndpointIs5001() throws Exception {
        mockMvc.perform(post("/follow/v1/unfollow")
                        .header("Authorization", bearer(userToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5001));
    }

    @Test
    @DisplayName("畸形 JSON → 200 + {code:5001}（httpx.Parse 失败经 SetErrorHandler 折叠，绝不是 400）")
    void malformedJsonIs200With5001() throws Exception {
        mockMvc.perform(post("/follow/v1/follow")
                        .header("Authorization", bearer(userToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5001));
    }

    // ==================================================================
    // 2b. withJsonBody 判据：Content-Type 不含 application/json ⇒ Go **丢弃整个 body**
    // ==================================================================

    @Test
    @DisplayName("⚠️ text/plain + 合法 JSON 的动作端点 → 200 + {code:5001}：Go 丢弃 body，不是解析它")
    void nonJsonContentTypeDiscardsBodyOnActionEndpoint() throws Exception {
        // Go 出处：rest/httpx/requests.go 的 withJsonBody
        //   return r.ContentLength > 0 && strings.Contains(r.Header.Get("Content-Type"), "application/json")
        // 判据为假时走的是 UnmarshalJsonMap(nil, v)（**完全不看 body**），而不是「勉强当 JSON 读」。
        // ⇒ target_id 视为缺席 ⇒ 必填校验失败 ⇒ 200 + {code:5001}。
        //
        // ⚠️ 这条同时证明 com.rag.common 的 GoStyleJsonBodyConverter **真的进了本模块的 MVC 管线**：
        //    若它没生效，Spring 要么 415、要么照常解析出 target_id=999 而成功 —— 两者都会让本断言红。
        mockMvc.perform(post("/follow/v1/follow")
                        .header("Authorization", bearer(userToken()))
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("{\"target_id\":\"999\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5001));

        verify(stub, never()).follow(any());
    }

    @Test
    @DisplayName("⚠️ 同一条判据下，list 端点按**零值**继续（Go: 无必填字段 ⇒ UnmarshalJsonMap(nil,v) 通过）")
    void nonJsonContentTypeGivesZeroValuesOnListEndpoint() throws Exception {
        when(stub.getFollowList(any())).thenReturn(okUserList());

        mockMvc.perform(post("/follow/v1/follow/list")
                        .header("Authorization", bearer(userToken()))
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("{\"user_id\":\"" + OTHER_UID + "\",\"limit\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // body 被丢弃 ⇒ user_id / limit 全部取零值 ⇒ 查的是当前登录用户的第 0 页
        ArgumentCaptor<ListReq> captor = ArgumentCaptor.forClass(ListReq.class);
        verify(stub).getFollowList(captor.capture());
        assertEquals(CLAIM_UID, captor.getValue().getUserId());
        assertEquals(0, captor.getValue().getLimit());
    }

    // ==================================================================
    // 3. 归属校验的「不对称」—— 本域最容易被"顺手统一"掉的业务语义
    // ==================================================================

    @Test
    @DisplayName("block/list 查别人 → 200 + {code:5005} 且 **RPC 完全没被调用**")
    void blockListRejectsOtherUser() throws Exception {
        mockMvc.perform(post("/follow/v1/block/list")
                        .header("Authorization", bearer(userToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"user_id\":\"" + OTHER_UID + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5005))
                .andExpect(jsonPath("$.msg").value("forbidden"));

        // ⚠️ 授权必须在<b>发 RPC 之前</b>生效：否则就是拿 A 的 token 触发了 B 的数据查询
        verify(stub, never()).getBlockList(any());
    }

    @Test
    @DisplayName("recommendations 查别人 → 200 + {code:5005} 且 RPC 未被调用")
    void recommendationsRejectsOtherUser() throws Exception {
        mockMvc.perform(post("/follow/v1/recommendations")
                        .header("Authorization", bearer(userToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"user_id\":\"" + OTHER_UID + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5005));

        verify(stub, never()).getRecommendations(any());
    }

    @Test
    @DisplayName("⚠️ follow/list 查别人 → 允许（Go getfollowlistlogic.go 没有 Forbidden 分支）")
    void followListAllowsOtherUser() throws Exception {
        when(stub.getFollowList(any())).thenReturn(okUserList(8L));

        mockMvc.perform(post("/follow/v1/follow/list")
                        .header("Authorization", bearer(userToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"user_id\":\"" + OTHER_UID + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<ListReq> captor = ArgumentCaptor.forClass(ListReq.class);
        verify(stub).getFollowList(captor.capture());
        assertEquals(OTHER_UID, captor.getValue().getUserId());
    }

    @Test
    @DisplayName("⚠️ follower/list 查别人 → 允许（与 follow/list 同属公开的两个端点）")
    void followerListAllowsOtherUser() throws Exception {
        when(stub.getFollowerList(any())).thenReturn(okUserList(8L));

        mockMvc.perform(post("/follow/v1/follower/list")
                        .header("Authorization", bearer(userToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"user_id\":\"" + OTHER_UID + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<ListReq> captor = ArgumentCaptor.forClass(ListReq.class);
        verify(stub).getFollowerList(captor.capture());
        assertEquals(OTHER_UID, captor.getValue().getUserId());
    }

    @Test
    @DisplayName("查自己（显式传自己的 id）→ 归属校验放行")
    void blockListAllowsSelf() throws Exception {
        when(stub.getBlockList(any())).thenReturn(okUserList());

        mockMvc.perform(post("/follow/v1/block/list")
                        .header("Authorization", bearer(userToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"user_id\":\"" + CLAIM_UID + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ==================================================================
    // 4. RPC 回包 / RPC 错误的码值映射
    // ==================================================================

    @Test
    @DisplayName("rpc 回包 code != 200 → 原样透传 code + msg（Go 的 NewErrCodeMsg）")
    void rpcBusinessCodeIsPropagatedVerbatim() throws Exception {
        when(stub.follow(any())).thenReturn(BaseResp.newBuilder()
                .setCode(5201).setMsg("self relation is not allowed").build());

        mockMvc.perform(post("/follow/v1/follow")
                        .header("Authorization", bearer(userToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target_id\":\"999\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5201))
                .andExpect(jsonPath("$.msg").value("self relation is not allowed"));
    }

    @Test
    @DisplayName("⚠️ rpc 错误：description 文本优先于 gRPC code（Internal + \"user not found\" ⇒ 5101）")
    void rpcErrorMessageTakesPrecedenceOverStatusCode() throws Exception {
        // Go BizCodeFromError：先查 bizCodeByMsg[st.Message()]，查不到才按 status code 映射。
        // follow-rpc 用 GRPCError(codes.NotFound, ErrorUserNotFound) ⇒ Internal 也可能带业务文案。
        when(stub.getFollowList(any())).thenThrow(
                Status.INTERNAL.withDescription("user not found").asRuntimeException());

        mockMvc.perform(post("/follow/v1/follow/list")
                        .header("Authorization", bearer(userToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5101))
                .andExpect(jsonPath("$.msg").value("user not found"));
    }

    @Test
    @DisplayName("rpc 错误：无可识别文案时按 gRPC code 映射，Unavailable ⇒ 1015 service busy")
    void rpcUnavailableBecomes1015() throws Exception {
        when(stub.getRecommendations(any())).thenThrow(
                Status.UNAVAILABLE.withDescription("transport is closing").asRuntimeException());

        mockMvc.perform(post("/follow/v1/recommendations")
                        .header("Authorization", bearer(userToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1015))
                .andExpect(jsonPath("$.msg").value("service busy"));
    }

    @Test
    @DisplayName("rpc 错误：PermissionDenied 无文案 ⇒ 5005 forbidden")
    void rpcPermissionDeniedBecomes5005() throws Exception {
        when(stub.block(any())).thenThrow(
                Status.PERMISSION_DENIED.asRuntimeException());

        mockMvc.perform(post("/follow/v1/block")
                        .header("Authorization", bearer(userToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target_id\":\"5\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5005))
                .andExpect(jsonPath("$.msg").value("forbidden"));
    }

    // ==================================================================
    // 5. 出参 wire 形态（含 ,string 的不对称）
    // ==================================================================

    @Test
    @DisplayName("⚠️ user_ids 出参是 JSON 数字（Go tag 无 ,string），空结果输出 null 而不是 []")
    void userIdsAreSerializedAsNumbers() throws Exception {
        when(stub.getFollowList(any())).thenReturn(okUserList(11L, 22L));

        String body = mockMvc.perform(post("/follow/v1/follow/list")
                        .header("Authorization", bearer(userToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrue(body.contains("\"user_ids\":[11,22]"),
                "user_ids 必须是 JSON 数字数组，实际 body = " + body);
        // ⚠️ 空列表必须是 null（不是 []）。
        //   Go 侧的 rpcResp.UserIds 是 protobuf 反序列化后的切片：proto3 的 repeated
        //   为空时字段不上 wire ⇒ 反序列化得 nil ⇒ encoding/json 输出 "user_ids":null。
        //   2026-10-24 的 Go↔RAG 双侧活体对拍实测：Go 输出 "user_ids":null。
        //   ⚠️ 本断言原先写的是 [] —— 那是一条**锁死错误答案**的断言（AGENTS.md §3.3）：
        //   它只看 rpc 进程内的非 nil 空切片，漏了中间那次 protobuf 编解码。
        when(stub.getFollowList(any())).thenReturn(okUserList());
        String emptyBody = mockMvc.perform(post("/follow/v1/follow/list")
                        .header("Authorization", bearer(userToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(emptyBody.contains("\"user_ids\":null"),
                "空 user_ids 必须是 null（与 Go 的 nil 切片一致），实际 body = " + emptyBody);
    }

    @Test
    @DisplayName("⚠️ recommend 的 target_id 出参是 JSON 字符串（Go tag 带 ,string），与 user_ids 相反")
    void recommendTargetIdIsSerializedAsString() throws Exception {
        when(stub.getRecommendations(any())).thenReturn(RecommendResp.newBuilder()
                .setCode(200).setMsg("OK")
                .addUsers(RecommendResp.RecommendUser.newBuilder()
                        .setTargetId(123L).setMutualScore(7))
                .build());

        String body = mockMvc.perform(post("/follow/v1/recommendations")
                        .header("Authorization", bearer(userToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrue(body.contains("\"target_id\":\"123\""),
                "target_id 必须带引号（Go ,string），实际 body = " + body);
        assertTrue(body.contains("\"mutual_score\":7"),
                "mutual_score 是普通 int32，实际 body = " + body);
    }

    @Test
    @DisplayName("recommend 空结果 → \"users\":[]（Go 的 make(...,0,n) 保证非 nil）")
    void emptyRecommendationsIsEmptyArray() throws Exception {
        when(stub.getRecommendations(any())).thenReturn(
                RecommendResp.newBuilder().setCode(200).setMsg("OK").build());

        String body = mockMvc.perform(post("/follow/v1/recommendations")
                        .header("Authorization", bearer(userToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrue(body.contains("\"users\":[]"),
                "空推荐必须是 [] 而不是 null，实际 body = " + body);
    }

    // ==================================================================
    // 6. 路由层：404 / 405 不被鉴权遮蔽
    // ==================================================================

    @Test
    @DisplayName("未注册路径 → 404（Go 的 router 在 auth 之前就 404）")
    void unknownPathIs404() throws Exception {
        mockMvc.perform(post("/follow/v1/nope"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET 打 POST 端点 → 405（Go 的路由方法不匹配）")
    void wrongMethodIs405() throws Exception {
        mockMvc.perform(get("/follow/v1/follow"))
                .andExpect(status().isMethodNotAllowed());
    }

    // ==================================================================
    // 7. Metrics：两个 reject reason + request 计数
    // ==================================================================

    @Test
    @DisplayName("埋点：user_id_missing 与 forbidden_scope 两条 reject 各自被记录")
    void rejectMetricsAreRecorded() throws Exception {
        // user_id_missing
        mockMvc.perform(post("/follow/v1/follow/list")
                        .header("Authorization", bearer(tokenWithoutUserIdClaim()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
        Double missing = metrics.rejectCount("/follow/v1/follow/list", "user_id_missing");
        assertTrue(missing != null && missing >= 1.0D,
                "user_id_missing 埋点缺失，实际 = " + missing);

        // forbidden_scope
        mockMvc.perform(post("/follow/v1/block/list")
                        .header("Authorization", bearer(userToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"user_id\":\"" + OTHER_UID + "\"}"))
                .andExpect(status().isOk());
        Double forbidden = metrics.rejectCount("/follow/v1/block/list", "forbidden_scope");
        assertTrue(forbidden != null && forbidden >= 1.0D,
                "forbidden_scope 埋点缺失，实际 = " + forbidden);
    }

    @Test
    @DisplayName("埋点：service 内的业务失败记 request_total{result=fail}（Go 判的是 logic 返回的 err 非空）")
    void businessFailureCountsAsFail() throws Exception {
        // ⚠️ 失败必须发生在 **service 内部**（rpc 回包 code != 200）才能命中这条埋点：
        //    Go 的埋点在 logic 方法里，而 httpx.Parse 失败时 handler 压根不进 logic
        //    （unfollowhandler.go:18-21 先 Parse、失败即 return）⇒ 解析失败**不记** request_total。
        //    这条断言用 rpc 业务码失败，正是为了落在 logic 内部。
        when(stub.unfollow(any())).thenReturn(BaseResp.newBuilder()
                .setCode(5203).setMsg("follow relation not found").build());

        mockMvc.perform(post("/follow/v1/unfollow")
                        .header("Authorization", bearer(userToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target_id\":\"5\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5203));

        Double fail = metrics.requestCount("/follow/v1/unfollow", "fail");
        assertTrue(fail != null && fail >= 1.0D,
                "service 内业务失败应计入 result=fail，实际 = " + fail);
    }

    @Test
    @DisplayName("埋点：解析失败（target_id 缺失）**不记** request_total —— Go 的 logic 根本没被调用")
    void parseFailureRecordsNoRequestMetric() throws Exception {
        // ⚠️ 这是一条「反直觉但必须保留」的语义：Go 的 metrics.ObserveRequest 在 logic 方法的
        //    defer 里，而 httpx.Parse 在 handler 里、失败即 return ⇒ 解析失败没有任何埋点。
        //    若 Java 侧把埋点上移到 Controller，就会比 Go 多出一批计数（压测下可观测）。
        //
        // ⚠️ 用「前后差值」而不是绝对值：MeterRegistry 在同一 Spring 上下文里跨用例共享，
        //    同一个 route 的绝对计数会被其它用例累加，写绝对值就成了顺序相关的假红。
        String route = "/follow/v1/unfollow";
        Double failBefore = metrics.requestCount(route, "fail");
        Double successBefore = metrics.requestCount(route, "success");

        mockMvc.perform(post("/follow/v1/unfollow")
                        .header("Authorization", bearer(userToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5001));

        assertEquals(failBefore, metrics.requestCount(route, "fail"),
                "解析失败不得计入 request_total{result=fail}（Go 侧 logic 未被调用）");
        assertEquals(successBefore, metrics.requestCount(route, "success"),
                "解析失败不得计入 request_total{result=success}（Go 侧 logic 未被调用）");
    }

    // ================== helpers ==================

    private static UserListResp okUserList(Long... ids) {
        UserListResp.Builder builder = UserListResp.newBuilder().setCode(200).setMsg("OK");
        for (Long id : ids) {
            builder.addUserIds(id);
        }
        return builder.build();
    }
}