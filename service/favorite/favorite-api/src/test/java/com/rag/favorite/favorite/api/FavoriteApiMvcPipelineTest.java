package com.rag.favorite.favorite.api;

import com.rag.favorite.favorite.api.client.FavoriteRpcClient;
import com.rag.favorite.favorite.api.support.RealContainerHostPort;
import com.rag.favorite.favorite.proto.FavoriteServiceGrpc;
import com.rag.users.common.jwt.JwtUtil;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
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

import javax.crypto.SecretKey;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * favorite-api 真实 MVC 管线集成测试。
 *
 * <p><b>与 admin-api 的 {@code AdminApiMvcPipelineTest} 同款做法</b>：
 * {@code @SpringBootTest} 加载真实的 {@code FavoriteApiApplication}，
 * MockMvc 由 Spring MVC 真实装配 —— 路由 / 拦截器 / advice / 序列化全部是生产代码。
 * <b>唯一被替换的是 gRPC 边界</b>（{@link FavoriteRpcClient}）。
 *
 * <p><b>为什么必须用真 Redis</b>：本组件最关键的两条语义是
 * 「黑名单<b>有没有被查</b>」与「查的<b>是不是 Go 那个 key</b>」。
 * 纯 mock Redis 只能证明代码调用了 {@code hasKey}，证明不了真连接、真 key、真命中，
 * 更证明不了「一次都没调用」这个顺序断言（Go 侧 {@code Authorize} 失败时
 * 黑名单<b>从不</b>被查询）。故这里用 {@code @MockitoSpyBean} 把真容器 Redis 包成 spy。
 *
 * <p><b>被测的语义</b>：
 * <ol>
 *   <li>鉴权链顺序（先验签、后黑名单）与 key 形态；</li>
 *   <li>Redis 故障 fail-open；</li>
 *   <li>envelope 形状（成功 200/OK、失败 200 + 业务码）；</li>
 *   <li>解析失败 → <b>200 + 5003</b>（本域与 users/admin 域相反）；</li>
 *   <li>gRPC 错误 → 按 description 反查还原业务码；</li>
 *   <li>下游 success=false → 5001；</li>
 *   <li>claim 缺失 / 非法 → <b>200 + 5004</b>（不是 401）；</li>
 *   <li>未注册路径 → 404（<b>不是</b> 401）。</li>
 * </ol>
 */
@SpringBootTest(
        classes = FavoriteApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                // 拒连最快；Nacos 连不上只会打日志，不影响本测试
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                // FavoriteRpcClient 整体被 @MockitoBean 替换，这个 channel bean 不会被用到；
                // 给个不会真连的地址，避免 devh 的 channel 解析去碰 Nacos
                "grpc.client.favorite-rpc.address=static://127.0.0.1:65535",
                "grpc.client.favorite-rpc.negotiation-type=plaintext",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN"
        })
@AutoConfigureMockMvc
@Testcontainers
class FavoriteApiMvcPipelineTest {

    /** 与 favorite-api/application.yml 的默认密钥一致（Go 端
     *     {@code service/favorite/api/etc/favorite.yaml:11}，读取点
     *     {@code service/favorite/api/internal/handler/routes.go:57}）。 */
    private static final String SECRET = "ae0536f9-6450-4606-8e13-5a19ed505da0";
    private static final long EXPIRE_SEC = 2592000L;
    private static final long CLAIM_UID = 4242L;

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379)
            .withCreateContainerCmdModifier(RealContainerHostPort.bindRedis(RealContainerHostPort.FAVORITE_API_MVC_PIPELINE_REDIS));

    @DynamicPropertySource
    static void redis(DynamicPropertyRegistry r) {
        r.add("spring.data.redis.host", REDIS::getHost);
        r.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        r.add("spring.data.redis.password", () -> "");
    }

    @Autowired
    private MockMvc mockMvc;

    /**
     * 用 spy 而非纯 mock：黑名单语义正是「有没有被查」「用哪个 key 查的」，
     * spy 保留真 Redis 连接与真 key 命中，同时让
     * {@code verify(hasKey)} 断言「一次都没查」成为可能。
     */
    @MockitoSpyBean
    private StringRedisTemplate redisTemplate;

    @MockitoBean
    private FavoriteRpcClient favoriteRpcClient;

    private FavoriteServiceGrpc.FavoriteServiceBlockingStub stub;

    @BeforeEach
    void setUp() {
        stub = Mockito.mock(FavoriteServiceGrpc.FavoriteServiceBlockingStub.class);
        when(favoriteRpcClient.favoriteRpc()).thenReturn(stub);
        // 每个用例从干净的黑名单开始
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
        // flushAll 本身会记进 spy 的交互记录，清掉，只留「被测请求」的交互
        Mockito.clearInvocations(redisTemplate);
    }

    private static String validToken() {
        return JwtUtil.getToken(SECRET, System.currentTimeMillis() / 1000L, EXPIRE_SEC, CLAIM_UID);
    }

    /** 签一个【没有 userId claim】的 token（Go 侧 Authorize 不校验 claim 取值，
     *     {@code go-zero@v1.10.2/rest/handler/authhandler.go:45}）。 */
    private static String tokenWithoutUserId() {
        return tokenWithUserIdClaim(null);
    }

    /**
     * 签一个 userId claim 形态可变的 token —— 对应 Go {@code extractUserID} 的多条分支：
     * <pre>
     *   case json.Number: return v.Int64()     // go-zero 的 jwt.WithJSONNumber ⇒ 数字 claim 走这条
     *   case string:     return strconv.ParseInt(v, 10, 64)
     *   default:         return 0, fmt.Errorf("ctx userId has unexpected type %T", raw)
     * </pre>
     */
    private static String tokenWithUserIdClaim(Object userIdClaim) {
        SecretKey key = Keys.hmacShaKeyFor(JwtUtil.padToHmacKeySize(SECRET));
        long now = System.currentTimeMillis();
        var builder = Jwts.builder()
                .issuedAt(new Date(now))
                .expiration(new Date(now + EXPIRE_SEC * 1000L));
        if (userIdClaim != null) {
            builder.claim("userId", userIdClaim);
        }
        return builder.signWith(key).compact();
    }

    // ==================================================================
    // 鉴权链：先验签、后黑名单
    // ==================================================================

    @Test
    @DisplayName("缺 Authorization 头 → HTTP 401")
    void missingAuthorizationHeaderIsUnauthorized() throws Exception {
        mockMvc.perform(post("/favorite/v1/folder/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("验签先于黑名单 —— 无效签名 + 已拉黑 → 401 且【一次都没查 Redis】")
    void signatureVerifiedBeforeBlacklistLookup() throws Exception {
        // 预置黑名单：若黑名单真被查过，这里会命中并走黑名单分支
        redisTemplate.opsForValue().set("user:jwt_blacklist:not-a-valid-jwt", "banned");

        mockMvc.perform(post("/favorite/v1/folder/create")
                        .header("Authorization", "Bearer not-a-valid-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\"}"))
                .andExpect(status().isUnauthorized());

        // Go 出处：engine.go bindRoute 把 Authorize 排在 route.Handler（黑名单）之前
        verify(redisTemplate, never()).hasKey(anyString());
        verify(stub, never()).createFavoriteFolder(any());
    }

    @Test
    @DisplayName("有效签名 + 在黑名单 → 401，且查的正是 Go 的 key（user:jwt_blacklist:<token>）")
    void validTokenInBlacklistRejectedWithGoKey() throws Exception {
        String token = validToken();
        redisTemplate.opsForValue().set("user:jwt_blacklist:" + token, "banned");

        mockMvc.perform(post("/favorite/v1/folder/create")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\"}"))
                .andExpect(status().isUnauthorized());

        // Go 出处 checkblacklist_middleware.go:36 fmt.Sprintf("user:jwt_blacklist:%s", token)
        verify(redisTemplate).hasKey("user:jwt_blacklist:" + token);
        verify(stub, never()).createFavoriteFolder(any());
    }

    @Test
    @DisplayName("Redis 故障 → fail-open 放行（Go：err != nil ⇒ 条件为 false ⇒ 放行）")
    void redisFailureFailsOpen() throws Exception {
        doThrow(new IllegalStateException("redis down")).when(redisTemplate).hasKey(anyString());

        when(stub.createFavoriteFolder(any())).thenReturn(
                com.rag.favorite.favorite.proto.CreateFavoriteFolderResp.newBuilder()
                        .setFolderId(11L).build());

        mockMvc.perform(post("/favorite/v1/folder/create")
                        .header("Authorization", "Bearer " + validToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("未注册路径 → 404（不是 401）：Go 的 router 只把 chain 挂在匹配上的路由上")
    void unregisteredPathReturns404Not401() throws Exception {
        mockMvc.perform(get("/favorite/v1/nope")
                        .header("Authorization", "Bearer " + validToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("未注册路径【无 token】→ 仍是 404（不是 401）：Go 的 404 分支根本不进鉴权 chain")
    void unregisteredPathWithoutTokenIsStill404() throws Exception {
        // Go 出处：rest/engine.go:104-118 的 bindRoute 只把 chain 挂到 router.Handle(...) 上，
        // 而 patrouter.go:73,79 的 404 / 405 分支进不了这条链 ⇒ 鉴权对未匹配路径不生效。
        mockMvc.perform(get("/favorite/v1/nope"))
                .andExpect(status().isNotFound());
        verify(redisTemplate, never()).hasKey(anyString());
    }

    @Test
    @DisplayName("405 → 裸状态码 + Allow 头只含【该路径实际注册的那个 method】")
    void methodNotAllowedCarriesGoAllowHeader() throws Exception {
        // Go 侧每个路径只注册了 1 个 method（routes.go:22-53）：
        // /favorite/v1/folder/create 只接受 POST ⇒ Allow 头只有 "POST"。
        // ⚠️ 若实现里写死一份全集（POST/DELETE/GET），客户端会看到 Go 侧不存在的假信息。
        mockMvc.perform(get("/favorite/v1/folder/create"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "POST"))
                .andExpect(content().string(""));
    }

    // ==================================================================
    // envelope 与业务语义
    // ==================================================================

    @Test
    @DisplayName("成功路径 → HTTP 200 + {code:200, msg:\"OK\", data:{folderId:\"字符串\"}}")
    void createFolderReturnsGoEnvelope() throws Exception {
        when(stub.createFavoriteFolder(any())).thenReturn(
                com.rag.favorite.favorite.proto.CreateFavoriteFolderResp.newBuilder()
                        .setFolderId(123L).build());

        mockMvc.perform(post("/favorite/v1/folder/create")
                        .header("Authorization", "Bearer " + validToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.msg").value("OK"))
                // ⚠️ 出参是字符串（Go 的 json:"folderId,string"）
                .andExpect(jsonPath("$.data.folderId").value("123"));
    }

    @Test
    @DisplayName("解析失败 → HTTP 200 + {code:5003, msg:\"参数错误\", data:null}（本域与 users/admin 域相反）")
    void parseFailureReturns200WithInvalidParam() throws Exception {
        mockMvc.perform(post("/favorite/v1/folder/create")
                        .header("Authorization", "Bearer " + validToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5003))
                .andExpect(jsonPath("$.msg").value("参数错误"))
                // ⚠️ strict=true：Go 的 response.Response 的 Data 字段【没有 omitempty】
                // ⇒ 失败响应里 data 必须【存在】且为 null，不能整个消失。
                .andExpect(content().json("{\"code\":5003,\"msg\":\"参数错误\",\"data\":null}", true));
        verify(stub, never()).createFavoriteFolder(any());
    }

    @Test
    @DisplayName("Content-Type 不是 JSON → 同样 200 + 5003（Go：withJsonBody=false ⇒ 字段全部落空）")
    void nonJsonContentTypeReturns200WithInvalidParam() throws Exception {
        mockMvc.perform(post("/favorite/v1/folder/create")
                        .header("Authorization", "Bearer " + validToken())
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("{\"name\":\"x\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5003));
    }

    @Test
    @DisplayName("gRPC 错误 → 按 description 反查还原业务码（5304 收藏暂不可撤回）")
    void rpcFailureMapsBizCodeByDescription() throws Exception {
        // Go：favorite-rpc 用 GRPCError(codes.FailedPrecondition, 5304) 发出，（service/favorite/rpc/internal/logic/delete_favorite_logic.go:86）
        // wire 上只有 grpc-status + description 文案 ⇒ api 侧只能靠文案反查。
        when(stub.deleteFavorite(any())).thenThrow(
                Status.FAILED_PRECONDITION.withDescription("收藏暂不可撤回").asRuntimeException());

        mockMvc.perform(delete("/favorite/v1/item/delete")
                        .header("Authorization", "Bearer " + validToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"favoriteId\":\"1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5304))
                .andExpect(jsonPath("$.msg").value("收藏暂不可撤回"));
    }

    @Test
    @DisplayName("下游 success=false → 5001 系统内部错误（Go：nil || !Success ⇒ 5001）")
    void rpcUnsuccessfulResponseBecomesServerCommon() throws Exception {
        when(stub.deleteFavorite(any())).thenReturn(
                com.rag.favorite.favorite.proto.DeleteFavoriteResp.newBuilder()
                        .setSuccess(false).build());

        mockMvc.perform(delete("/favorite/v1/item/delete")
                        .header("Authorization", "Bearer " + validToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"favoriteId\":\"1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5001))
                .andExpect(jsonPath("$.msg").value("系统内部错误"));
    }

    @Test
    @DisplayName("下游返回 nil 响应 → 5001（Go：rpcResp == nil ⇒ 5001）")
    void rpcNullResponseBecomesServerCommon() throws Exception {
        when(stub.getFavoriteFolders(any())).thenReturn(null);

        mockMvc.perform(get("/favorite/v1/folder/list")
                        .header("Authorization", "Bearer " + validToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5001));
    }

    @Test
    @DisplayName("claim 缺失 → HTTP 200 + {code:5004}（【不是】401：Go 的 Authorize 不校验 claim 取值）")
    void missingUserIdClaimBecomesUnauthorizedBusinessCode() throws Exception {
        mockMvc.perform(get("/favorite/v1/folder/list")
                        .header("Authorization", "Bearer " + tokenWithoutUserId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5004))
                .andExpect(jsonPath("$.msg").value("未登录或登录已失效"));
        verify(stub, never()).getFavoriteFolders(any());
    }

    @Test
    @DisplayName("claim 是字符串 \"4242\" → 走 Go 的 case string 分支，正常放行")
    void stringUserIdClaimIsAccepted() throws Exception {
        when(stub.getFavoriteFolders(any())).thenReturn(
                com.rag.favorite.favorite.proto.GetFavoriteFoldersResp.newBuilder().build());

        mockMvc.perform(get("/favorite/v1/folder/list")
                        .header("Authorization", "Bearer " + tokenWithUserIdClaim("4242")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        org.mockito.ArgumentCaptor<com.rag.favorite.favorite.proto.GetFavoriteFoldersReq> captor =
                org.mockito.ArgumentCaptor.forClass(
                        com.rag.favorite.favorite.proto.GetFavoriteFoldersReq.class);
        verify(stub).getFavoriteFolders(captor.capture());
        assertEquals(4242L, captor.getValue().getUserId());
    }

    @Test
    @DisplayName("claim 是字符串 \"abc\" → Go 的 strconv.ParseInt 失败 → 200 + 5004")
    void nonNumericStringUserIdClaimBecomes5004() throws Exception {
        mockMvc.perform(get("/favorite/v1/folder/list")
                        .header("Authorization", "Bearer " + tokenWithUserIdClaim("abc")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5004));
        verify(stub, never()).getFavoriteFolders(any());
    }

    @Test
    @DisplayName("claim 是小数 1.5 → Go 的 json.Number(\"1.5\").Int64() 失败 → 200 + 5004（【不是】截断成 1）")
    void fractionalUserIdClaimBecomes5004() throws Exception {
        mockMvc.perform(get("/favorite/v1/folder/list")
                        .header("Authorization", "Bearer " + tokenWithUserIdClaim(1.5)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5004));
        verify(stub, never()).getFavoriteFolders(any());
    }

    @Test
    @DisplayName("POST /item/create 不传 title/cover → 200，且下游收到【空串】（Go 零值；protobuf 的 setTitle(null) 会 NPE）")
    void createItemWithoutOptionalFieldsSendsEmptyStrings() throws Exception {
        // ⚠️ 这条用例是「protobuf setXxx(null) → NPE」缺陷的守门测试。
        // Go 侧 title/cover 是 ,optional 字段，缺席时保持零值 ""（types.go:18-19 + unmarshaler.go:856-862），
        // 正常传给 rpc；而 protobuf 生成的 setTitle(String) 内部是 Objects.requireNonNull ⇒
        // 直接把 DTO 的 null 透进去会让一个最普通的请求炸成 500。
        when(stub.createFavorite(any())).thenReturn(
                com.rag.favorite.favorite.proto.CreateFavoriteResp.newBuilder()
                        .setFavoriteId(77L).build());

        mockMvc.perform(post("/favorite/v1/item/create")
                        .header("Authorization", "Bearer " + validToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"folderId\":\"1\",\"targetId\":\"a\",\"targetType\":\"article\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.favoriteId").value("77"));

        org.mockito.ArgumentCaptor<com.rag.favorite.favorite.proto.CreateFavoriteReq> captor =
                org.mockito.ArgumentCaptor.forClass(
                        com.rag.favorite.favorite.proto.CreateFavoriteReq.class);
        verify(stub).createFavorite(captor.capture());
        assertEquals("", captor.getValue().getTitle());
        assertEquals("", captor.getValue().getCover());
        assertEquals("a", captor.getValue().getTargetId());
        assertEquals("article", captor.getValue().getTargetType());
        assertEquals(1L, captor.getValue().getFolderId());
    }

    /**
     * <b>整张「拒绝矩阵」在真实 MVC 管线上的断言</b> —— 与 Go 的 {@code httpx.Parse}
     * （{@code go-zero@v1.10.2/rest/httpx/requests.go:50}）逐条对齐。
     *
     * <p><b>为什么放在 MVC 层而不是 DTO 层</b>：DTO 层测的是「Jackson 能不能反序列化出来」，
     * 而 MVC 层测的是「<b>整条管线</b>把这次请求判成参数错误」。
     * 两者会分叉的地方恰恰是坑：
     * <ul>
     *   <li>空 body：Spring 的 {@code @RequestBody(required=false)} 让 req 变 null，
     *       由必填判定接手（Go 是「字段落空」）；</li>
     *   <li>Content-Type 不是 JSON：Spring 默认 415，Go 是 5003；</li>
     *   <li>JSON 语法错误 / 顶层不是对象：Spring 抛
     *       {@code HttpMessageNotReadableException}，Go 是 mapping 报错。</li>
     * </ul>
     * 这三处都必须落到 <b>HTTP 200 + 5003</b>，少改一处就是一处静默漂移。
     *
     * <p><b>矩阵取自 Go 真源探针</b>（go-zero v1.10.2，对 types.go 逐字同构的 struct 直接调
     * {@code httpx.Parse}，2026-10-23），不是从源码推断。
     */
    @Test
    @DisplayName("拒绝矩阵：Go 判解析失败的 9 种形态 → 一律 HTTP 200 + 5003")
    void rejectedInputsAllReturn200WithInvalidParam() throws Exception {
        // {说明, 请求体, Content-Type}
        String[][] cases = {
                {"字段缺席 {}", "{}", MediaType.APPLICATION_JSON_VALUE},
                {"显式 null", "{\"name\":null}", MediaType.APPLICATION_JSON_VALUE},
                {"数字进 String 字段", "{\"name\":123}", MediaType.APPLICATION_JSON_VALUE},
                {"布尔进 String 字段", "{\"name\":true}", MediaType.APPLICATION_JSON_VALUE},
                {"数组进 String 字段", "{\"name\":[\"a\"]}", MediaType.APPLICATION_JSON_VALUE},
                {"空 body", "", MediaType.APPLICATION_JSON_VALUE},
                {"JSON 语法错误", "{", MediaType.APPLICATION_JSON_VALUE},
                {"顶层是数组", "[1]", MediaType.APPLICATION_JSON_VALUE},
                {"顶层是标量", "123", MediaType.APPLICATION_JSON_VALUE},
                {"顶层是 null", "null", MediaType.APPLICATION_JSON_VALUE},
                {"Content-Type 不是 JSON", "{\"name\":\"x\"}", MediaType.TEXT_PLAIN_VALUE},
        };

        for (String[] c : cases) {
            mockMvc.perform(post("/favorite/v1/folder/create")
                            .header("Authorization", "Bearer " + validToken())
                            .contentType(c[2])
                            .content(c[1]))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(5003))
                    .andExpect(jsonPath("$.msg").value("参数错误"));
        }

        // 解析被拒 ⇒ 一次都不该打到下游
        verify(stub, never()).createFavoriteFolder(any());
    }

    @Test
    @DisplayName("接受矩阵：合法请求确实打到下游（Go 侧这几类是 ACCEPT）")
    void acceptedInputsReachDownstream() throws Exception {
        when(stub.createFavoriteFolder(any())).thenReturn(
                com.rag.favorite.favorite.proto.CreateFavoriteFolderResp.newBuilder()
                        .setFolderId(1L).build());

        // 多余字段被忽略（Go 的 mapping 只取已声明的字段）
        mockMvc.perform(post("/favorite/v1/folder/create")
                        .header("Authorization", "Bearer " + validToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\",\"extra\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 空串是合法值（Go 的 json 通道不判空串）
        mockMvc.perform(post("/favorite/v1/folder/create")
                        .header("Authorization", "Bearer " + validToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(stub, times(2)).createFavoriteFolder(any());
    }

    @Test
    @DisplayName("claim 形态矩阵：bool / null / 1e2 / 超 int64 → 一律 5004（Go 的 extractUserID 各条失败分支）")
    void rejectedUserIdClaimShapesAllBecome5004() throws Exception {
        // Go 出处：api/internal/logic/favorite/helper.go:14-34 的 extractUserID
        //   case json.Number: return v.Int64()   ← "1e2" / "1.5" / 超界都在这里失败
        //   case string:     return strconv.ParseInt(v, 10, 64)
        //   default:         return 0, fmt.Errorf("ctx userId has unexpected type %T", raw)
        //   raw == nil                                 ← claim 缺席或显式 null
        // ⚠️ 这些失败**不是 401**：Go 的 Authorize 只验签名，claim 取值由业务层判定，
        //    所以出口是 HTTP 200 + 5004。
        Object[] badClaims = {
                Boolean.TRUE,            // default 分支：unexpected type
                null,                    // 显式 null ⇒ 与缺席同路径
                1e2,                     // json.Number("1e2").Int64() 失败
                new java.math.BigInteger("99999999999999999999"),  // strconv.ParseInt 越界
        };
        for (Object claim : badClaims) {
            mockMvc.perform(get("/favorite/v1/folder/list")
                            .header("Authorization", "Bearer " + tokenWithUserIdClaim(claim)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(5004))
                    .andExpect(jsonPath("$.msg").value("未登录或登录已失效"));
        }
        verify(stub, never()).getFavoriteFolders(any());
    }

    @Test
    @DisplayName("claim 是负数 -1 → 正常放行并原样透传给下游（Go 的 int64 分支不校验正负）")
    void negativeUserIdClaimIsPassedThrough() throws Exception {
        when(stub.getFavoriteFolders(any())).thenReturn(
                com.rag.favorite.favorite.proto.GetFavoriteFoldersResp.newBuilder().build());

        mockMvc.perform(get("/favorite/v1/folder/list")
                        .header("Authorization", "Bearer " + tokenWithUserIdClaim(-1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        org.mockito.ArgumentCaptor<com.rag.favorite.favorite.proto.GetFavoriteFoldersReq> captor =
                org.mockito.ArgumentCaptor.forClass(
                        com.rag.favorite.favorite.proto.GetFavoriteFoldersReq.class);
        verify(stub).getFavoriteFolders(captor.capture());
        assertEquals(-1L, captor.getValue().getUserId());
    }

    @Test
    @DisplayName("Accept: text/plain / application/xml → 仍是 200 + JSON envelope（Go 不做内容协商）")
    void acceptHeaderDoesNotChangeTheResponseShape() throws Exception {
        // Go 出处：rest/httpx/responses.go:182 `w.Header().Set(ContentType, header.ContentTypeJson)`
        //          —— 无条件写 application/json，【完全无视请求的 Accept 头】。
        // ⚠️ 不关掉内容协商时，Spring 会因找不到能写 text/plain 的转换器而抛 406
        //    → 落兜底 → HTTP 500，而 Go 是 200。这是可观察的 L1 差异。
        when(stub.getFavoriteFolders(any())).thenReturn(
                com.rag.favorite.favorite.proto.GetFavoriteFoldersResp.newBuilder().build());

        for (String accept : new String[]{"text/plain", "application/xml", "*/*", "text/html"}) {
            mockMvc.perform(get("/favorite/v1/folder/list")
                            .header("Authorization", "Bearer " + validToken())
                            .header("Accept", accept))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.msg").value("OK"))
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
        }
    }

    @Test
    @DisplayName("GET /item/list：query 里带无值的额外参数 → 仍按 folderId 正常处理")
    void listItemsIgnoresValuelessExtraQueryParam() throws Exception {
        // Go：r.Form = {"folderId": ["123"], "456": [""]} ⇒ form 通道只取 "folderId" ⇒ 123（go-zero@v1.10.2/rest/httpx/requests.go:85）
        when(stub.getFavoritesByFolder(any())).thenReturn(
                com.rag.favorite.favorite.proto.GetFavoritesByFolderResp.newBuilder().build());

        mockMvc.perform(get("/favorite/v1/item/list")
                        .param("folderId", "123")
                        .param("456", "")
                        .header("Authorization", "Bearer " + validToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        org.mockito.ArgumentCaptor<com.rag.favorite.favorite.proto.GetFavoritesByFolderReq> captor =
                org.mockito.ArgumentCaptor.forClass(
                        com.rag.favorite.favorite.proto.GetFavoritesByFolderReq.class);
        verify(stub).getFavoritesByFolder(captor.capture());
        assertEquals(123L, captor.getValue().getFolderId());
    }

    @Test
    @DisplayName("空收藏夹列表 → data.folders 是 []（不是 null）：Go 侧无条件 make")
    void emptyFolderListIsEmptyArray() throws Exception {
        when(stub.getFavoriteFolders(any())).thenReturn(
                com.rag.favorite.favorite.proto.GetFavoriteFoldersResp.newBuilder().build());

        mockMvc.perform(get("/favorite/v1/folder/list")
                        .header("Authorization", "Bearer " + validToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.folders").isArray())
                .andExpect(jsonPath("$.data.folders").isEmpty());
    }

    @Test
    @DisplayName("GET /item/list 走 query 通道：folderId 透传到下游")
    void listItemsReadsQueryParam() throws Exception {
        when(stub.getFavoritesByFolder(any())).thenReturn(
                com.rag.favorite.favorite.proto.GetFavoritesByFolderResp.newBuilder().build());

        mockMvc.perform(get("/favorite/v1/item/list")
                        .param("folderId", "5")
                        .header("Authorization", "Bearer " + validToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.favorites").isEmpty());

        org.mockito.ArgumentCaptor<com.rag.favorite.favorite.proto.GetFavoritesByFolderReq> captor =
                org.mockito.ArgumentCaptor.forClass(
                        com.rag.favorite.favorite.proto.GetFavoritesByFolderReq.class);
        verify(stub).getFavoritesByFolder(captor.capture());
        assertEquals(5L, captor.getValue().getFolderId());
        assertEquals(CLAIM_UID, captor.getValue().getUserId());
    }

    @Test
    @DisplayName("GET /item/list 缺 folderId → 200 + 5003（Go：field \"folderId\" is not set）")
    void listItemsWithoutFolderIdReturnsInvalidParam() throws Exception {
        mockMvc.perform(get("/favorite/v1/item/list")
                        .header("Authorization", "Bearer " + validToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5003));
        verify(stub, never()).getFavoritesByFolder(any());
    }

    @Test
    @DisplayName("DELETE /item/delete 带 JSON body：favoriteId 透传到下游")
    void deleteItemReadsJsonBody() throws Exception {
        when(stub.deleteFavorite(any())).thenReturn(
                com.rag.favorite.favorite.proto.DeleteFavoriteResp.newBuilder()
                        .setSuccess(true).build());

        mockMvc.perform(delete("/favorite/v1/item/delete")
                        .header("Authorization", "Bearer " + validToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"favoriteId\":\"9\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.success").value(true));

        org.mockito.ArgumentCaptor<com.rag.favorite.favorite.proto.DeleteFavoriteReq> captor =
                org.mockito.ArgumentCaptor.forClass(
                        com.rag.favorite.favorite.proto.DeleteFavoriteReq.class);
        verify(stub).deleteFavorite(captor.capture());
        assertEquals(9L, captor.getValue().getFavoriteId());
    }

    // ================================================================
    // 以下用例锁住【双栈活体对拍】抓出的 3 个 L1 差异。
    // 每条断言的「Go 侧是什么」都来自 go-zero v1.10.2 真源探针
    // （逐字 types.go 结构体 + httpx.Parse），不是推断。
    // ================================================================

    /** {@code ?folderId=123&folderId=456} —— Go 取首值 123，Spring 会拼成 "123,456" 而炸。 */
    @Test
    @DisplayName("GET /item/list：重复 folderId → 取【首值】（Go unmarshaler.go:835-846 的 fromArray index 0）")
    void duplicatedFolderIdTakesFirstValue() throws Exception {
        when(stub.getFavoritesByFolder(any())).thenReturn(
                com.rag.favorite.favorite.proto.GetFavoritesByFolderResp.newBuilder().build());

        mockMvc.perform(get("/favorite/v1/item/list")
                        .param("folderId", "123", "456")
                        .header("Authorization", "Bearer " + validToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        org.mockito.ArgumentCaptor<com.rag.favorite.favorite.proto.GetFavoritesByFolderReq> captor =
                org.mockito.ArgumentCaptor.forClass(
                        com.rag.favorite.favorite.proto.GetFavoritesByFolderReq.class);
        verify(stub).getFavoritesByFolder(captor.capture());
        assertEquals(123L, captor.getValue().getFolderId());
    }

    /**
     * 「先滤空值、再取首值」的顺序不能反 —— 这是本轮最容易写错的一处。
     * 若先取下标 0 再判空，{@code ?folderId=&folderId=456} 会退化成「字段缺席」⇒ 5003。
     */
    @Test
    @DisplayName("GET /item/list：?folderId=&folderId=456 → 456（空值先被滤掉，Go go-zero@v1.10.2/rest/httpx/util.go:37-43）")
    void leadingEmptyFolderIdIsFilteredBeforeTakingFirst() throws Exception {
        when(stub.getFavoritesByFolder(any())).thenReturn(
                com.rag.favorite.favorite.proto.GetFavoritesByFolderResp.newBuilder().build());

        mockMvc.perform(get("/favorite/v1/item/list")
                        .param("folderId", "", "456")
                        .header("Authorization", "Bearer " + validToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        org.mockito.ArgumentCaptor<com.rag.favorite.favorite.proto.GetFavoritesByFolderReq> captor =
                org.mockito.ArgumentCaptor.forClass(
                        com.rag.favorite.favorite.proto.GetFavoritesByFolderReq.class);
        verify(stub).getFavoritesByFolder(captor.capture());
        assertEquals(456L, captor.getValue().getFolderId());
    }

    @Test
    @DisplayName("GET /item/list：?folderId=&folderId= → 字段缺席 ⇒ 5003（滤完为空 ⇒ key 不入 params）")
    void allEmptyFolderIdMeansAbsent() throws Exception {
        mockMvc.perform(get("/favorite/v1/item/list")
                        .param("folderId", "", "")
                        .header("Authorization", "Bearer " + validToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5003));

        verify(stub, never()).getFavoritesByFolder(any());
    }

    /** Go 的 GetFormValues 里 n++ 是跨所有 key 累计的，超过 2048 个非空值直接报错 ⇒ 5003
     *     （{@code go-zero@v1.10.2/rest/httpx/util.go:15,22,48}）。 */
    @Test
    @DisplayName("GET /item/list：非空 query 值超 2048 个 → 5003（Go util.go:15 maxFormParamCount）")
    void tooManyFormValuesIsRejected() throws Exception {
        StringBuilder query = new StringBuilder("/favorite/v1/item/list?folderId=7");
        for (int i = 0; i < 2048; i++) {
            query.append("&pad").append(i).append("=v");
        }
        mockMvc.perform(get(query.toString())
                        .header("Authorization", "Bearer " + validToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5003));

        verify(stub, never()).getFavoritesByFolder(any());
    }

    /**
     * {@code /folder/list} 的请求体是空 struct，但 Go 仍会解析并校验它
     * （httpx.Parse 无条件跑 ParseJsonBody）⇒ 只有「根是 JSON object」才放行。
     */
    @Test
    @DisplayName("GET /folder/list：body 是 JSON object（{} 或含未知字段）→ 放行 200")
    void folderListAcceptsJsonObjectBody() throws Exception {
        when(stub.getFavoriteFolders(any())).thenReturn(
                com.rag.favorite.favorite.proto.GetFavoriteFoldersResp.newBuilder().build());

        for (String body : new String[]{"{}", "{\"a\":1}"}) {
            mockMvc.perform(get("/favorite/v1/folder/list")
                            .header("Authorization", "Bearer " + validToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }
    }

    /**
     * 真源探针实测：{@code null} / 标量 / 数组 / 语法错误在 Go 侧
     * （{@code go-zero@v1.10.2/core/mapping/utils.go:38}），
     * 分别落 {@code unsupported type on setting field value}、{@code type mismatch}、
     * {@code unexpected EOF}，<b>全都是 5003</b>。
     */
    @Test
    @DisplayName("GET /folder/list：body 是 null / 标量 / 数组 / 语法错 → 200 + 5003")
    void folderListRejectsNonObjectAndMalformedBody() throws Exception {
        for (String body : new String[]{"null", "123", "1.5", "\"str\"", "true", "[]", "[1,2]", "{"}) {
            mockMvc.perform(get("/favorite/v1/folder/list")
                            .header("Authorization", "Bearer " + validToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(5003))
                    .andExpect(jsonPath("$.msg").value("参数错误"));
        }
        verify(stub, never()).getFavoriteFolders(any());
    }

    /**
     * {@code withJsonBody = ContentLength&gt;0 && strings.Contains(CT, "application/json")}。
     * Contains 是<b>大小写敏感的子串匹配</b>，不是 MIME 解析 ⇒ 这两种 Content-Type 都判 false，
     * Go 压根不读 body ⇒ 放行 200。
     */
    @Test
    @DisplayName("GET /folder/list：Content-Type 非 application/json 子串（含大小写变体）→ body 被忽略，200")
    void folderListIgnoresBodyWhenContentTypeDoesNotMatch() throws Exception {
        when(stub.getFavoriteFolders(any())).thenReturn(
                com.rag.favorite.favorite.proto.GetFavoriteFoldersResp.newBuilder().build());

        for (String ct : new String[]{"text/plain", "Application/JSON", "application/vnd.api+json"}) {
            mockMvc.perform(get("/favorite/v1/folder/list")
                            .header("Authorization", "Bearer " + validToken())
                            .contentType(ct)
                            .content("{"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }
    }

    /**
     * Go 出处 {@code rest/internal/header/headers.go:17}：{@code ContentTypeJson = "application/json; charset=utf-8"}，
     * 且 {@code go-zero@v1.10.2/rest/httpx/responses.go:182} 对每个业务响应无条件写入。Spring 默认发的是不带 charset 的版本。
     */
    @Test
    @DisplayName("响应 Content-Type 是 application/json; charset=utf-8（Go header.ContentTypeJson）")
    void responseContentTypeMatchesGoConstant() throws Exception {
        when(stub.getFavoriteFolders(any())).thenReturn(
                com.rag.favorite.favorite.proto.GetFavoriteFoldersResp.newBuilder().build());

        mockMvc.perform(get("/favorite/v1/folder/list")
                        .header("Authorization", "Bearer " + validToken()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/json;charset=utf-8"));

        // 错误分支同样要带 —— Go 的 ErrorJsonCtx 走的是同一个 doWriteJson。
        mockMvc.perform(get("/favorite/v1/item/list").header("Authorization", "Bearer " + validToken()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/json;charset=utf-8"));
    }
}
