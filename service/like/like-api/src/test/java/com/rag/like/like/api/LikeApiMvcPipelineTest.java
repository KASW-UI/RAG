package com.rag.like.like.api;

import com.rag.like.like.api.client.LikeRpcClient;
import com.rag.like.like.proto.ActionType;
import com.rag.like.like.proto.LikeServiceGrpc.LikeServiceBlockingStub;
import com.rag.users.common.jwt.JwtUtil;
import io.grpc.Metadata;
import io.grpc.Status;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * like-api <b>真实 MVC 管线</b>测试 —— 锁住 Go {@code service/like/api} 的 L1 / L2 契约。
 *
 * <p><b>为什么必须是真实管线而不是 standaloneSetup</b>：本服务最容易漂移的三件事
 * <b>全都只在真实管线里才测得到</b>：
 * <table border="1">
 *   <caption>standaloneSetup 测不到的三件事</caption>
 *   <tr><th>性质</th><th>为什么</th></tr>
 *   <tr>
 *     <td>{@code options=1|2|3|4} / {@code ,string} / 「String 不进 int 字段」</td>
 *     <td>取决于 Spring MVC <b>实际使用的那个</b> Jackson 3 mapper 上是否真挂了
 *         {@code LikeGoStyleCoercion} 的 customizer。自己 new 一个 mapper 造断言是假绿</td>
 *   </tr>
 *   <tr>
 *     <td>401 + <b>空 body</b></td>
 *     <td>取决于拦截器短路与 {@code ResponseBodyAdvice} 的相互作用</td>
 *   </tr>
 *   <tr>
 *     <td>envelope 只包一层</td>
 *     <td>取决于 {@code GlobalResponseAdvice} 对 {@code null} / {@code Response} 的放行分支</td>
 *   </tr>
 * </table>
 *
 * <p><b>唯一被替换的是 gRPC 边界</b>（{@link LikeRpcClient}）—— 被测语义全在 api 进程内：
 * 路由 → 鉴权拦截器 → 必填校验 → Service → advice → 序列化。
 */
@SpringBootTest(
        classes = LikeApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "grpc.client.like-rpc.address=static://127.0.0.1:65535",
                "grpc.client.like-rpc.negotiation-type=plaintext",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN"
        })
@AutoConfigureMockMvc
class LikeApiMvcPipelineTest {

    /** 与 application.yml 的 {@code like-api.user-auth.access-secret} 同一份。 */
    private static final String SECRET = "ae0536f9-6450-4606-8e13-5a19ed505da0";
    private static final long EXPIRE_SEC = 3600L;
    /** 取当前时间而不是写死常量：写死的 iat 会让 token 在「运行时刻」早已过期。 */
    private static long now() {
        return System.currentTimeMillis() / 1000L;
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LikeRpcClient likeRpcClient;

    private LikeServiceBlockingStub stub;

    @BeforeEach
    void setUp() {
        stub = mock(LikeServiceBlockingStub.class);
        when(likeRpcClient.likeRpc()).thenReturn(stub);
    }

    // ==================== helpers ====================

    private static String token() {
        return tokenWithLongUserId(1001L);
    }

    private static String tokenWithLongUserId(long userId) {
        return JwtUtil.getToken(SECRET, now(), EXPIRE_SEC, userId);
    }

    /** 手工签一个 {@code userId} claim 为字符串/非法值的 token（JwtUtil 只能写 long）。 */
    private static String tokenWithRawUserIdClaim(Object raw) {
        return Jwts.builder()
                .issuedAt(new Date(now() * 1000L))
                .expiration(new Date((now() + EXPIRE_SEC) * 1000L))
                .claim("userId", raw)
                .signWith(Keys.hmacShaKeyFor(JwtUtil.padToHmacKeySize(SECRET)))
                .compact();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    // ==================== 1. 鉴权：401 + 空 body ====================

    @Test
    @DisplayName("缺 Authorization 头 → HTTP 401 且 body 为空（Go authhandler.unauthorized 的逐字还原）")
    void missingAuthorizationIs401WithEmptyBody() throws Exception {
        mockMvc.perform(post("/like/v1/getlikecount")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target_type\":\"article\",\"target_ids\":[\"1\"]}"))
                .andExpect(status().isUnauthorized())
                // 关键：Go 侧 unauthorized() 只 WriteHeader(401)，不写任何字节
                .andExpect(content().string(""));
    }

    @Test
    @DisplayName("签名错的 token → 401 + 空 body（6 个端点全覆盖，Go 是 6/6 全量 WithJwt）")
    void badSignatureIs401WithEmptyBody() throws Exception {
        String bad = JwtUtil.getToken("wrong-secret-value-0000000000", now(), EXPIRE_SEC, 1L);
        for (String path : new String[]{"/like/v1/getlikecount", "/like/v1/getlikestate",
                "/like/v1/gettargetlikerlist", "/like/v1/getuserlikelist",
                "/like/v1/getusertotallike", "/like/v1/likeaction"}) {
            mockMvc.perform(post(path)
                            .header("Authorization", bearer(bad))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().string(""));
        }
    }

    @Test
    @DisplayName("过期 token → 401 + 空 body")
    void expiredTokenIs401() throws Exception {
        String expired = JwtUtil.getToken(SECRET, now() - 7200L, 60L, 1L);
        mockMvc.perform(post("/like/v1/getlikecount")
                        .header("Authorization", bearer(expired))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target_type\":\"article\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(""));
    }

    // ==================== 2. envelope ====================

    @Test
    @DisplayName("成功 → HTTP 200 + {code:200,msg:\"OK\",data:...}（httpx.SetOkHandler 的等价物）")
    void successIsWrappedInOkEnvelope() throws Exception {
        when(stub.getLikeCount(any())).thenReturn(
                com.rag.like.like.proto.GetLikeCountResp.newBuilder()
                        .putCounts("1", com.rag.like.like.proto.LikeCountItem.newBuilder()
                                .setLikeCount(10).setDislikeCount(2).build())
                        .build());

        mockMvc.perform(post("/like/v1/getlikecount")
                        .header("Authorization", bearer(token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target_type\":\"article\",\"target_ids\":[\"1\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.msg").value("OK"))
                .andExpect(jsonPath("$.data.counts['1'].like_count").value(10))
                .andExpect(jsonPath("$.data.counts['1'].dislike_count").value(2));
    }

    @Test
    @DisplayName("空结果 → counts 是 {} 而不是 null（Go 的 make(map,...) 保证非 nil）")
    void emptyCountsIsObjectNotNull() throws Exception {
        when(stub.getLikeCount(any())).thenReturn(
                com.rag.like.like.proto.GetLikeCountResp.getDefaultInstance());

        mockMvc.perform(post("/like/v1/getlikecount")
                        .header("Authorization", bearer(token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target_type\":\"article\",\"target_ids\":[\"1\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.counts").isMap())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"counts\":{}")));
    }

    @Test
    @DisplayName("⚠️ is_end 的 JSON key 必须叫 is_end，不能多出一个 end（Jackson 会剥 is 前缀）")
    void isEndKeepsGoWireKey() throws Exception {
        when(stub.getTargetLikerList(any())).thenReturn(
                com.rag.like.like.proto.GetTargetLikerListResp.newBuilder()
                        .setIsEnd(true)
                        .setNextCursor(99L)
                        .build());

        mockMvc.perform(post("/like/v1/gettargetlikerlist")
                        .header("Authorization", bearer(token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target_type\":\"article\",\"target_id\":\"1\","
                                + "\"cursor\":0,\"page_size\":20}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.is_end").value(true))
                .andExpect(jsonPath("$.data.next_cursor").value(99))
                // 空 list 必须是 [] 而不是 null
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"list\":[]")))
                // 关键回归锁：不能同时出现 "end"
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("\"end\":"))));
    }

    // ==================== 3. 身份解析：1006（不是 401！） ====================

    @Test
    @DisplayName("⚠️ userId claim 不是数字 → HTTP 200 + code 1006（不是 401！）")
    void nonNumericUserIdClaimYields1006Not401() throws Exception {
        mockMvc.perform(post("/like/v1/getusertotallike")
                        .header("Authorization", bearer(tokenWithRawUserIdClaim("abc")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1006))
                .andExpect(jsonPath("$.msg").value("TOKEN已过期"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("⚠️ userId claim 是小数 → 也是 1006（Go 的 strconv.ParseInt(\"1.5\") 失败）")
    void fractionalUserIdClaimYields1006() throws Exception {
        mockMvc.perform(post("/like/v1/likeaction")
                        .header("Authorization", bearer(tokenWithRawUserIdClaim(1.5)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target_type\":\"article\",\"target_id\":\"1\","
                                + "\"action_type\":1,\"author_id\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1006));
    }

    @Test
    @DisplayName("⚠️ 公开读端点不解析 uid：claim 非法也照样成功（Go 的鉴权不对称）")
    void publicReadEndpointsDoNotExtractUid() throws Exception {
        when(stub.getLikeCount(any())).thenReturn(
                com.rag.like.like.proto.GetLikeCountResp.getDefaultInstance());
        when(stub.getTargetLikerList(any())).thenReturn(
                com.rag.like.like.proto.GetTargetLikerListResp.getDefaultInstance());

        // getlikecount 在 Go 侧全文没有 ctx.Value("userId")
        mockMvc.perform(post("/like/v1/getlikecount")
                        .header("Authorization", bearer(tokenWithRawUserIdClaim("not-a-number")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target_type\":\"article\",\"target_ids\":[\"1\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(post("/like/v1/gettargetlikerlist")
                        .header("Authorization", bearer(tokenWithRawUserIdClaim("not-a-number")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target_type\":\"article\",\"target_id\":\"1\","
                                + "\"cursor\":0,\"page_size\":20}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("uid 必须真的传进 rpc（证明取自 JWT 而非请求体）")
    void userIdComesFromJwtClaim() throws Exception {
        when(stub.getUserTotalLike(any())).thenReturn(
                com.rag.like.like.proto.GetUserTotalLikeResp.newBuilder()
                        .setTotalLikeCount(42L).build());

        mockMvc.perform(post("/like/v1/getusertotallike")
                        .header("Authorization", bearer(tokenWithLongUserId(7788L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total_like_count").value(42));

        ArgumentCaptor<com.rag.like.like.proto.GetUserTotalLikeReq> captor =
                ArgumentCaptor.forClass(com.rag.like.like.proto.GetUserTotalLikeReq.class);
        verify(stub).getUserTotalLike(captor.capture());
        assertEquals(7788L, captor.getValue().getUserId());
    }

    // ==================== 4. 必填/解析失败 → 200 + 5001 ====================

    @Test
    @DisplayName("必填标量缺失 → 200 + 5001（Go: field \"target_type\" is not set → httpx.ErrorCtx）")
    void missingRequiredScalarIs5001() throws Exception {
        mockMvc.perform(post("/like/v1/getlikecount")
                        .header("Authorization", bearer(token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5001))
                .andExpect(jsonPath("$.msg").value("系统内部错误"));
    }

    @Test
    @DisplayName("⚠️ cursor / page_size 也是必填（Go: 4 个标量字段全必填，客户端必须显式发 0）")
    void cursorAndPageSizeAreAlsoRequired() throws Exception {
        // ⚠️ 必须**先**stub：否则「校验放行 → 调用未 stub 的 stub → Mockito 返回 null → NPE → 5001」，
        //   本用例即使删掉 cursor 的必填校验也照样绿 —— 那就是为错误答案发通行证的测试。
        //   stub 之后「校验放行」的唯一可观察结果变成 code 200，负例才具备鉴别力。
        when(stub.getTargetLikerList(any())).thenReturn(
                com.rag.like.like.proto.GetTargetLikerListResp.getDefaultInstance());

        mockMvc.perform(post("/like/v1/gettargetlikerlist")
                        .header("Authorization", bearer(token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        // 只缺 cursor：target_type/target_id/page_size 全部齐备，
                        // 这样 5001 只能由 cursor 的必填校验产生。
                        // ⚠️ 不能用「两个字段一起缺」的写法 —— 那样即使删掉 cursor 的校验
                        //   测试仍然会因 page_size 缺失而红。
                        .content("{\"target_type\":\"article\",\"target_id\":\"1\","
                                + "\"page_size\":20}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5001));

        // 只缺 page_size：cursor 齐备，隔离 page_size
        mockMvc.perform(post("/like/v1/gettargetlikerlist")
                        .header("Authorization", bearer(token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target_type\":\"article\",\"target_id\":\"1\","
                                + "\"cursor\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5001));

        // ⚠️ **真正有鉴别力的断言**：Go 在必填校验失败时**根本不会调用 rpc**。
        // 只断言 code==5001 是不够的 —— 缺少必填校验时，null cursor 会在
        // protobuf 的 setCursor(Long) 自动拆箱处抛 NPE，兜底后**同样是 5001**，
        // 于是用例照样绿，却已经不再锁住「必填校验」这件事本身（实测踩过）。
        verify(stub, never()).getTargetLikerList(any());

        // 四个字段齐备 → 成功（证明上面两条 5001 确实来自必填校验，而不是别的副作用）
        mockMvc.perform(post("/like/v1/gettargetlikerlist")
                        .header("Authorization", bearer(token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        // 显式 0 是合法的 —— 「必填」= 键必须在，不是值不能为 0
                        .content("{\"target_type\":\"article\",\"target_id\":\"1\","
                                + "\"cursor\":0,\"page_size\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("⚠️ target_ids 缺席 = 解析失败，且**不得**发出 rpc（DRIFT-LIKEAPI-002 的回归锁）")
    void missingTargetIdsIsParseFailureAndSkipsRpc() throws Exception {
        // ⚠️ 本用例的断言在 2026-10-22 **反向过**：原断言是「缺席不算缺字段 → code 200」，
        //   @DisplayName 还写着「Go: Slice 永不计必填」—— 那个 Go 结论已被独立复核推翻。
        //   Go 侧控制流：core/mapping/unmarshaler.go:944-950 的 Slice 分支调的是
        //   processFieldNotFromString(..., value: emptyMap, ...) → 落到 default: →
        //   processFieldPrimitive，typeKind(Slice) != valueKind(Map) → newTypeMismatchError。
        //   活体探针实测：缺失 → `type mismatch for field "target_ids"`；null → `mustn't be nil`。
        // 这里刻意**先 stub**：让「校验放行」的可观察结果变成 code 200，
        // 负例才有鉴别力（否则未 stub 的 mock 返回 null → NPE → 同样 5001，是假绿）。
        when(stub.getLikeCount(any())).thenReturn(
                com.rag.like.like.proto.GetLikeCountResp.getDefaultInstance());

        // 缺 target_ids
        mockMvc.perform(post("/like/v1/getlikecount")
                        .header("Authorization", bearer(token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target_type\":\"article\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5001));

        // 显式 null 同样被拒（Go: field "target_ids" mustn't be nil）
        mockMvc.perform(post("/like/v1/getlikecount")
                        .header("Authorization", bearer(token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target_type\":\"article\",\"target_ids\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5001));

        // 关键鉴别断言：Go 在解析期就拦下，**根本不会调用 rpc**
        verify(stub, never()).getLikeCount(any());

        // 字段齐备才放行，且空数组是合法的（必填 = 键必须在，不是数组不能为空）
        mockMvc.perform(post("/like/v1/getlikecount")
                        .header("Authorization", bearer(token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target_type\":\"article\",\"target_ids\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("getlikestate 的 target_ids 同样必填")
    void getLikeStateTargetIdsAlsoRequired() throws Exception {
        when(stub.getLikeState(any())).thenReturn(
                com.rag.like.like.proto.GetLikeStateResp.getDefaultInstance());

        mockMvc.perform(post("/like/v1/getlikestate")
                        .header("Authorization", bearer(token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target_type\":\"article\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5001));

        verify(stub, never()).getLikeState(any());
    }

    @Test
    @DisplayName("⚠️ JSON 字符串进无 ,string 的整数字段 → 5001（Go: type mismatch）")
    void jsonStringIntoIntFieldIs5001() throws Exception {
        mockMvc.perform(post("/like/v1/gettargetlikerlist")
                        .header("Authorization", bearer(token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target_type\":\"article\",\"target_id\":\"1\","
                                + "\"cursor\":\"10\",\"page_size\":20}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5001));
        verify(stub, never()).getTargetLikerList(any());
    }

    @Test
    @DisplayName("⚠️ JSON 数字进 String 字段 → 5001（Go: type mismatch for field \"target_type\"）")
    void jsonNumberIntoStringFieldIs5001() throws Exception {
        mockMvc.perform(post("/like/v1/getlikecount")
                        .header("Authorization", bearer(token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target_type\":123,\"target_ids\":[\"1\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5001));
    }

    @Test
    @DisplayName("非法 JSON → 200 + 5001")
    void malformedJsonIs5001() throws Exception {
        mockMvc.perform(post("/like/v1/getlikecount")
                        .header("Authorization", bearer(token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5001));
    }

    // ==================== 5. options / ,string ====================

    @Test
    @DisplayName("⚠️ action_type 超出 options=1|2|3|4 → 5001，且不得调用 rpc")
    void actionTypeOutsideOptionsIs5001() throws Exception {
        for (String body : new String[]{
                "{\"target_type\":\"a\",\"target_id\":\"1\",\"action_type\":5,\"author_id\":1}",
                "{\"target_type\":\"a\",\"target_id\":\"1\",\"action_type\":0,\"author_id\":1}",
                "{\"target_type\":\"a\",\"target_id\":\"1\",\"action_type\":1.0,\"author_id\":1}"}) {
            mockMvc.perform(post("/like/v1/likeaction")
                            .header("Authorization", bearer(token()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(5001));
        }
        verify(stub, never()).likeAction(any());
    }

    @Test
    @DisplayName("⚠️ action_type 是 JSON 字符串 → 5001（无 ,string，Go 判 type mismatch）")
    void actionTypeAsJsonStringIs5001() throws Exception {
        mockMvc.perform(post("/like/v1/likeaction")
                        .header("Authorization", bearer(token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target_type\":\"a\",\"target_id\":\"1\","
                                + "\"action_type\":\"1\",\"author_id\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5001));
    }

    @Test
    @DisplayName("⚠️ ,string 的 author_id 字符串与数字两种形态都接受（Go 的入参方向宽松）")
    void stringTaggedAuthorIdAcceptsBothForms() throws Exception {
        when(stub.likeAction(any())).thenReturn(
                com.rag.like.like.proto.LikeActionResp.newBuilder()
                        .setMessage("Success").setLikeCount(3).build());

        for (String authorId : new String[]{"\"123\"", "123", "\"+123\""}) {
            mockMvc.perform(post("/like/v1/likeaction")
                            .header("Authorization", bearer(token()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"target_type\":\"a\",\"target_id\":\"1\","
                                    + "\"action_type\":1,\"author_id\":" + authorId + "}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        ArgumentCaptor<com.rag.like.like.proto.LikeActionReq> captor =
                ArgumentCaptor.forClass(com.rag.like.like.proto.LikeActionReq.class);
        verify(stub, org.mockito.Mockito.times(3)).likeAction(captor.capture());
        captor.getAllValues().forEach(r -> {
            assertEquals(123L, r.getAuthorId(), "字符串形态与数字形态都必须解析成同一个 author_id");
            assertEquals(ActionType.ACTION_LIKE, r.getActionType());
        });
    }

    @Test
    @DisplayName("⚠️ ,string 的 author_id 传浮点 → 5001（Go: ParseInt(\"1.5\") 失败）")
    void stringTaggedAuthorIdRejectsFloat() throws Exception {
        mockMvc.perform(post("/like/v1/likeaction")
                        .header("Authorization", bearer(token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target_type\":\"a\",\"target_id\":\"1\","
                                + "\"action_type\":1,\"author_id\":1.5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5001));
    }

    @Test
    @DisplayName(",optional 字段缺席或显式 null 都合法（Go: opts.optional() 直接 return nil）")
    void optionalFieldsAcceptAbsentAndExplicitNull() throws Exception {
        when(stub.likeAction(any())).thenReturn(
                com.rag.like.like.proto.LikeActionResp.newBuilder().setMessage("ok").build());

        mockMvc.perform(post("/like/v1/likeaction")
                        .header("Authorization", bearer(token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target_type\":\"a\",\"target_id\":\"1\","
                                + "\"action_type\":1,\"author_id\":1,"
                                + "\"weight\":null,\"client_ip\":null,\"promotion_id\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 缺席也一样
        mockMvc.perform(post("/like/v1/likeaction")
                        .header("Authorization", bearer(token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target_type\":\"a\",\"target_id\":\"1\","
                                + "\"action_type\":1,\"author_id\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ==================== 6. LikeActionResp 的三处「像 bug 但是行为」 ====================

    @Test
    @DisplayName("⚠️ success 恒为 true（Go 硬编码，不读 proto 的 success），message 与计数透传")
    void likeActionSuccessIsAlwaysTrue() throws Exception {
        // 故意让 proto 返回 success=false，Go 也会忽略并硬编码 true
        when(stub.likeAction(any())).thenReturn(
                com.rag.like.like.proto.LikeActionResp.newBuilder()
                        .setSuccess(false)
                        .setMessage("服务繁忙")
                        .setLikeCount(7).setDislikeCount(1)
                        .build());

        mockMvc.perform(post("/like/v1/likeaction")
                        .header("Authorization", bearer(token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target_type\":\"a\",\"target_id\":\"1\","
                                + "\"action_type\":1,\"author_id\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.success").value(true))
                .andExpect(jsonPath("$.data.message").value("服务繁忙"))
                .andExpect(jsonPath("$.data.like_count").value(7))
                .andExpect(jsonPath("$.data.dislike_count").value(1));
    }

    @Test
    @DisplayName("⚠️ weight 由 double 窄化成 proto 的 float（Go: float32(req.Weight)）")
    void weightIsNarrowedToFloat32() throws Exception {
        when(stub.likeAction(any())).thenReturn(
                com.rag.like.like.proto.LikeActionResp.newBuilder().setMessage("ok").build());

        // 0.1 在 float32 里是 0.10000000149011612 —— 证明确实发生了窄化
        mockMvc.perform(post("/like/v1/likeaction")
                        .header("Authorization", bearer(token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target_type\":\"a\",\"target_id\":\"1\","
                                + "\"action_type\":1,\"author_id\":1,\"weight\":0.1}"))
                .andExpect(status().isOk());

        ArgumentCaptor<com.rag.like.like.proto.LikeActionReq> captor =
                ArgumentCaptor.forClass(com.rag.like.like.proto.LikeActionReq.class);
        verify(stub).likeAction(captor.capture());
        assertEquals((float) 0.1, captor.getValue().getWeight());
    }

    // ==================== 7. gRPC 错误映射（Go 的 ok 标志） ====================

    @Test
    @DisplayName("⚠️ gRPC 业务错误 → code 取 trailer 里的业务码，msg 用 description 原文不查表")
    void grpcBusinessErrorMapsToTrailerCodeAndVerbatimMsg() throws Exception {
        Metadata trailers = new Metadata();
        trailers.put(com.rag.like.like.common.errmsg.LikeGrpcError.BIZ_CODE_TRAILER, "5003");
        when(stub.getLikeCount(any())).thenThrow(
                Status.fromCode(Status.Code.UNKNOWN)
                        .withDescription("DB查询失败")
                        .asRuntimeException(trailers));

        mockMvc.perform(post("/like/v1/getlikecount")
                        .header("Authorization", bearer(token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target_type\":\"article\",\"target_ids\":[\"1\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5003))
                // 关键是 "DB查询失败"（Go 原文），不是码表里的 "查询数据库失败"
                .andExpect(jsonPath("$.msg").value("DB查询失败"));
    }

    @Test
    @DisplayName("⚠️ 非 gRPC 异常 → 兜底 5001（Go 的 ok==false 分支，不是 1015）")
    void nonStatusErrorFallsBackTo5001() throws Exception {
        when(stub.getLikeCount(any())).thenThrow(new IllegalStateException("连接池耗尽"));

        mockMvc.perform(post("/like/v1/getlikecount")
                        .header("Authorization", bearer(token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target_type\":\"article\",\"target_ids\":[\"1\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5001))
                .andExpect(jsonPath("$.msg").value("系统内部错误"));
    }

    @Test
    @DisplayName("status 错误无 trailer 时按标准 gRPC code 反查业务码")
    void statusWithoutTrailerFallsBackToGrpcCodeMapping() throws Exception {
        when(stub.getLikeState(any())).thenThrow(
                Status.INVALID_ARGUMENT.withDescription("参数异常").asRuntimeException());

        mockMvc.perform(post("/like/v1/getlikestate")
                        .header("Authorization", bearer(token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target_type\":\"article\",\"target_ids\":[\"1\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2001))
                .andExpect(jsonPath("$.msg").value("参数异常"));
    }

    // ==================== 8. 路由层 ====================

    @Test
    @DisplayName("未知路径 → 404（不能被兜底成 200 + 5001）")
    void unknownPathIs404() throws Exception {
        mockMvc.perform(post("/like/v1/nope")
                        .header("Authorization", bearer(token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET 方法不支持 → 405（Go 侧 6 个端点全是 POST）")
    void getMethodIs405() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/like/v1/getlikecount")
                        .header("Authorization", bearer(token())))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("零字段 DTO：空 body 也能通过（Go 的 getusertotallike 零必填）")
    void emptyBodyWorksForZeroFieldDto() throws Exception {
        when(stub.getUserTotalLike(any())).thenReturn(
                com.rag.like.like.proto.GetUserTotalLikeResp.newBuilder().setTotalLikeCount(5L).build());

        mockMvc.perform(post("/like/v1/getusertotallike")
                        .header("Authorization", bearer(token()))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total_like_count").value(5));
    }
}