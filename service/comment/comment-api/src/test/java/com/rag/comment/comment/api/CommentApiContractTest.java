package com.rag.comment.comment.api;

import com.rag.comment.comment.api.client.CommentRpcClient;
import com.rag.comment.comment.proto.CommentServiceGrpc.CommentServiceBlockingStub;
import com.rag.users.common.jwt.JwtUtil;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * comment-api L1 外部契约测试 —— 锁住 Go {@code service/comment/api} 的 wire 形状。
 *
 * <p><b>唯一被替换的是 gRPC 边界</b>（{@link CommentRpcClient}）：
 * 被测语义全在 api 进程内：路由 → 拦截器 → 解析层 → Service → advice → 序列化。
 *
 * <p><b>为什么必须是真实 MVC 管线而不是 standaloneSetup</b>：本类要锁的四个性质
 * 全部只有在真实管线里才成立：
 * <ul>
 *   <li><b>200 + 信封</b>：取决于 {@code ResponseBodyAdvice} 真的挂在 MVC 链上；</li>
 *   <li><b>401 空 body</b>：取决于 advice 对 {@code null} 放行这条分支；</li>
 *   <li><b>标量类型不匹配 → 5001</b>：取决于
 *       {@code CommentGoStyleCoercion} 的 customizer 真的挂到了 MVC 用的
 *       Jackson 3 mapper 上 —— 自己 new 一个 mapper 造断言是假绿；</li>
 *   <li><b>未知路径 404</b>：取决于拦截器对非 {@code HandlerMethod} 放行。</li>
 * </ul>
 */
@SpringBootTest(
        classes = CommentApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "grpc.client.comment-rpc.address=static://127.0.0.1:65535",
                "grpc.client.comment-rpc.negotiation-type=plaintext",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN"
        })
@AutoConfigureMockMvc
class CommentApiContractTest {

    /** 与 application.yml 的 {@code comment-api.user-auth.access-secret} 同一份。 */
    private static final String USER_SECRET = "ae0536f9-6450-4606-8e13-5a19ed505da0";
    private static final long EXPIRE_SEC = 3600L;
    private static final long CLAIM_UID = 31337L;

    /** {@code ERROR_TOKEN_RUNTIME} = 1006（Go {@code errmsg.go:56}）。 */
    private static final int ERROR_TOKEN_RUNTIME = 1006;
    /** {@code ERROR_SERVER_COMMON} = 5001（Go {@code errmsg.go:41}）。 */
    private static final int ERROR_SERVER_COMMON = 5001;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CommentRpcClient rpcClient;

    private CommentServiceBlockingStub stub;

    @BeforeEach
    void setUp() {
        stub = Mockito.mock(CommentServiceBlockingStub.class);
        Mockito.when(rpcClient.commentRpc()).thenReturn(stub);
    }

    private static String userToken() {
        return JwtUtil.getToken(USER_SECRET, System.currentTimeMillis() / 1000L, EXPIRE_SEC, CLAIM_UID);
    }

    /** 造一个<b>不带 userId claim</b>的合法签名 token（验签通过，但身份缺失）。 */
    private static String tokenWithoutUserId() {
        long now = System.currentTimeMillis() / 1000L;
        return Jwts.builder()
                .issuedAt(new Date(now * 1000L))
                .expiration(new Date((now + EXPIRE_SEC) * 1000L))
                .signWith(Keys.hmacShaKeyFor(USER_SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    // ==================================================================
    // 1. 鉴权：401 + 空 body（Go authhandler.go:106 unauthorized() 逐字还原）
    // ==================================================================

    @Test
    @DisplayName("无 Authorization 头 → HTTP 401 且 body 完全为空")
    void missingAuthorizationIs401WithEmptyBody() throws Exception {
        mockMvc.perform(post("/comment/v1/comment/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                // Go 侧只 WriteHeader(401)，不写任何字节
                .andExpect(content().string(""));
        verify(stub, never()).createComment(any());
    }

    @Test
    @DisplayName("签名错的 token → 401 空 body；且不触碰 comment-rpc")
    void badSignatureIs401WithEmptyBody() throws Exception {
        String foreign = JwtUtil.getToken("some-other-secret-entirely",
                System.currentTimeMillis() / 1000L, EXPIRE_SEC, CLAIM_UID);
        mockMvc.perform(post("/comment/v1/comment/create")
                        .header("Authorization", "Bearer " + foreign)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(""));
        verify(stub, never()).createComment(any());
    }

    // ==================================================================
    // 2. 路由表逐条对齐 Go routes.go
    // ==================================================================

    @Test
    @DisplayName("7 条端点全部要求 JWT —— Go 只有一段 AddRoutes，无 public 路径")
    void everyEndpointRequiresJwt() throws Exception {
        String[] paths = {
                "/comment/v1/comment/create", "/comment/v1/comment/delete",
                "/comment/v1/comment/like", "/comment/v1/comment/list",
                "/comment/v1/comment/manage", "/comment/v1/comment/report",
                "/comment/v1/subject/state",
        };
        for (String path : paths) {
            mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    @DisplayName("/comment/get 不存在（Go 的 path 是 /comment/list，不是按方法名推的）")
    void commentGetIsNotRegistered() throws Exception {
        mockMvc.perform(post("/comment/v1/comment/get")
                        .header("Authorization", "Bearer " + userToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("未注册路径 → 404，不被鉴权遮蔽成 401（Go：路由匹配先于 auth）")
    void unknownPathIs404EvenWithoutToken() throws Exception {
        mockMvc.perform(post("/comment/v1/nope")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET 方法不被支持 → 405（Go 侧 7 条全是 POST）")
    void getOnPostOnlyEndpointIs405() throws Exception {
        mockMvc.perform(get("/comment/v1/comment/create")
                        .header("Authorization", "Bearer " + userToken()))
                .andExpect(status().isMethodNotAllowed());
    }

    // ==================================================================
    // 3. 解析层：必填缺失 / 类型不匹配 → 200 + 5001
    // ==================================================================

    @Test
    @DisplayName("必填字段缺席 → HTTP 200 + {code:5001}（不是 400）")
    void missingRequiredFieldIs200With5001() throws Exception {
        // CreateCommentReq 六个字段全部必填（tag 均无 ,optional）
        mockMvc.perform(post("/comment/v1/comment/create")
                        .header("Authorization", "Bearer " + userToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target_type\":\"article\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ERROR_SERVER_COMMON))
                .andExpect(jsonPath("$.msg").value("系统内部错误"))
                .andExpect(jsonPath("$.data").doesNotExist());
        verify(stub, never()).createComment(any());
    }

    @Test
    @DisplayName("JSON 字符串进整数字段 → 5001（Go processFieldPrimitive 判 typeKind != valueKind）")
    void stringIntoInt64FieldIsRejected() throws Exception {
        mockMvc.perform(post("/comment/v1/comment/create")
                        .header("Authorization", "Bearer " + userToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"target_type":"article","target_id":"BV1","root_id":"123",
                                 "parent_id":0,"content":"hi","meta":""}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ERROR_SERVER_COMMON));
        verify(stub, never()).createComment(any());
    }

    @Test
    @DisplayName("非法 JSON → 200 + 5001（httpx.Parse 失败已被 SetErrorHandler 接住）")
    void malformedJsonIs200With5001() throws Exception {
        mockMvc.perform(post("/comment/v1/comment/create")
                        .header("Authorization", "Bearer " + userToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ERROR_SERVER_COMMON));
    }

    // ==================================================================
    // 4. 身份：claim 缺失 → 200 + 1006（不是 401）
    // ==================================================================

    @Test
    @DisplayName("token 无 userId claim → 200 + {code:1006}，绝不 401（extractUserID 失败分支）")
    void missingUserIdClaimIs200With1006() throws Exception {
        mockMvc.perform(post("/comment/v1/comment/create")
                        .header("Authorization", "Bearer " + tokenWithoutUserId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"target_type":"article","target_id":"BV1","root_id":0,
                                 "parent_id":0,"content":"hi","meta":""}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ERROR_TOKEN_RUNTIME))
                .andExpect(jsonPath("$.msg").value("TOKEN已过期"));
        verify(stub, never()).createComment(any());
    }
}