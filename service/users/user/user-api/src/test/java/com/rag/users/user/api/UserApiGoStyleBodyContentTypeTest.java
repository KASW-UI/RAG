package com.rag.users.user.api;

import com.rag.users.common.jwt.JwtUtil;
import com.rag.users.user.api.client.UserRpcClient;
import com.rag.users.user.proto.UserServiceGrpc.UserServiceBlockingStub;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * <b>user-api 自动继承了共享的请求体判据，却从未被任何测试覆盖过。</b>
 *
 * <p>与 {@code AdminApiGoStyleBodyContentTypeTest} 同构 —— 那一份是因为 admin-api 在
 * {@code GoStyleJsonBodyConverter} 下沉当天<b>真的回归过 4 条</b>却没被任何本模块测试抓到
 * 而补的。user-api 是同一个宿主（共享层）的<b>另一个消费者</b>，风险完全相同：
 * <b>共享组件的爆炸半径只能用全量跑发现，隔离跑看不到邻居受害。</b>
 *
 * <h2>user 域的表现形态</h2>
 * <pre>
 *   Go rest/httpx/requests.go:153-155
 *       withJsonBody = ContentLength &gt; 0 &amp;&amp; strings.Contains(ct, "application/json")
 *       不满足 ⇒ mapping.UnmarshalJsonMap(nil, v)   ← **完全不看 body**
 *
 *   Go rest/httpx/responses.go:142-168  doHandleError
 *       handler == nil ⇒ http.Error(w, err.Error(), 400)
 *   证据：service/user 全树 grep SetErrorHandler <b>零命中</b>
 *       （user/api 的 main.go 只调 server.Use(HTTPMiddleware)，从未 SetErrorHandler）
 *
 *   ⇒ user-api：HTTP 400 + text/plain + nosniff + 尾换行
 * </pre>
 *
 * <h2>⚠️ 与 Go 真源不一致但<b>已签字</b>的两处（不要在本类里"顺手修回来"）</h2>
 * <ul>
 *   <li><b>路径前缀</b>：Go 是 {@code /usercenter/v1}（routes.go 的 {@code rest.WithPrefix}），
 *       RAG 是 {@code /user/v1}。</li>
 *   <li><b>HTTP 方法</b>：Go 的 {@code /user/get}、{@code /user/delete}、
 *       {@code /user/avatar/history} 是 <b>GET</b>，RAG 全部是 <b>POST</b>。</li>
 * </ul>
 * <p>本类只测<b>请求体判据</b>，与上面两项无关 ⇒ 一律用 RAG 侧的冻结路径。
 */
@SpringBootTest(
        classes = UserApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "grpc.client.user-rpc.address=static://127.0.0.1:65535",
                "grpc.client.user-rpc.negotiation-type=plaintext",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN"
        })
@AutoConfigureMockMvc
@Testcontainers
class UserApiGoStyleBodyContentTypeTest {

    /** 与 user-api 的 application.yml 保持同一份密钥，否则签出来的 token 验签必失败。 */
    private static final String SECRET = "ae0536f9-6450-4606-8e13-5a19ed505da0";
    private static final long EXPIRE_SEC = 2592000L;
    private static final long CLAIM_UID = 31337L;

    /** public 路径（Go routes.go 第一段 AddRoutes 不带 WithJwt）：LoginReq 两个标量必填。 */
    private static final String LOGIN_PATH = "/user/v1/login";

    /** protected 路径：GetUserReq 在 Go {@code types.go} 里是 {@code struct{}}（零字段）。 */
    private static final String GET_PATH = "/user/v1/get";

    /** 字段齐全、完全合法的 JSON —— 用它证明「body 被丢弃」而不是「body 被拒绝」。 */
    private static final String VALID_LOGIN_JSON = "{\"username\":\"probe\",\"password\":\"pw123456\"}";

    /**
     * Go 的 {@code newInitError} 文案 + {@code fmt.Fprintln} 的尾换行。
     * ⚠️ {@code username} 排在 {@code password} 之前（GoStyleRequired 按声明序调用）。
     */
    private static final String USERNAME_MISSING = "field \"username\" is not set\n";

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
    @MockitoBean
    private UserRpcClient userRpcClient;
    private UserServiceBlockingStub stub;

    @BeforeEach
    void setUp() {
        stub = Mockito.mock(UserServiceBlockingStub.class);
        Mockito.when(userRpcClient.userRpc()).thenReturn(stub);
    }

    private static String validToken() {
        return JwtUtil.getToken(SECRET, System.currentTimeMillis() / 1000L, EXPIRE_SEC, CLAIM_UID);
    }

    // ==================================================================
    // 1. 核心断言：判据不满足 ⇒ body 被**丢弃**（不是被「宽松解析」）
    // ==================================================================

    /**
     * ⭐ 本类最锐利的一条。
     *
     * <p>发一个<b>字段齐全、完全合法</b>的 JSON body，但 Content-Type 不是 {@code application/json}
     * ⇒ Go 判 {@code withJsonBody == false} ⇒ <b>整个 body 被丢弃</b> ⇒ 必填字段缺失 ⇒ 400。
     *
     * <p><b>对照组</b>：{@code application/json} + {@code {}}。
     * 两者在 Go 侧进的是<b>同一个函数</b>（{@code mapping.UnmarshalJsonMap}，只是 map 一个是 nil、
     * 一个是空）⇒ <b>响应必须逐字节相同</b>。
     */
    @Test
    @DisplayName("非 JSON + **合法完整** body ⇒ 与 application/json+{} **逐字节相同**（body 被丢弃，不是被解析）")
    void nonJsonWithValidBodyIsIdenticalToEmptyJsonObject() throws Exception {
        var asJsonEmptyObject = mockMvc.perform(post(LOGIN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn();

        var asPlainText = mockMvc.perform(post(LOGIN_PATH)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(VALID_LOGIN_JSON))          // ← 完全合法、字段齐全
                .andReturn();

        assertEquals(400, asPlainText.getResponse().getStatus(),
                "user 域未注册 SetErrorHandler ⇒ Go 侧是 400，不是 like/message 的 200+5001");

        assertEquals(asJsonEmptyObject.getResponse().getContentAsString(),
                asPlainText.getResponse().getContentAsString(),
                "两者在 Go 侧进的是同一个函数（UnmarshalJsonMap），响应必须逐字节相同");

        assertEquals(USERNAME_MISSING, asPlainText.getResponse().getContentAsString(),
                "⚠️ 这一条同时锁住两个已发生过的事故：\n"
                        + "  1. 尾换行（Go 的 fmt.Fprintln）与文案「is not set」（Go 的 newInitError）必须逐字对齐；\n"
                        + "  2. **不能出现 JSON 转义**：若 body 是 \"field \\\"username\\\" is not set\\n\"，"
                        + "说明响应被共享转换器按 JSON 字符串写出（2026-10-22 的 canWrite 回归）。\n"
                        + "     那种回归的状态码与 Content-Type 都对，只看响应头会以为没问题。");

        assertEquals("text/plain", asPlainText.getResponse().getContentType(),
                "Go 的 http.Error 写的是 Content-Type: text/plain; charset=utf-8");
        assertEquals("nosniff", asPlainText.getResponse().getHeader("X-Content-Type-Options"),
                "Go 的 http.Error 同时写 X-Content-Type-Options: nosniff");

        // 最强判据：根本没调到下游。body 若被解析，login 一定会被调用。
        verify(stub, never()).login(any());
    }

    // ==================================================================
    // 2. 零字段 DTO：Go **不**在成功路径上丢弃
    // ==================================================================

    /**
     * {@code GetUserReq} 在 Go {@code types.go} 里是 {@code struct{}}（零字段）
     * ⇒ {@code UnmarshalJsonMap(nil, v)} 不报错 ⇒ 请求应当<b>成功</b>。
     *
     * <p>⚠️ 这正是当初签字理由「两侧都是拒绝」<b>不完整</b>的那个反例：
     * 非 JSON 的 Content-Type 对<b>零必填</b> DTO 毫无影响。
     */
    @Test
    @DisplayName("零字段 DTO + 非 JSON Content-Type ⇒ **成功**（Go 不在成功路径上丢弃 body）")
    void zeroFieldDtoSucceedsWithNonJsonContentType() throws Exception {
        // IdentityResolver.resolveUser 有 4 个前置条件（见 UserApiMvcPipelineTest 的注释）：
        // found=true / user 非默认实例 / user.uid == claim.uid / hasStatus() && status==0
        when(stub.getUser(any())).thenReturn(
                com.rag.users.user.proto.GetUserResp.newBuilder()
                        .setFound(true)
                        .setUser(com.rag.users.user.proto.UserInfo.newBuilder()
                                .setUid(CLAIM_UID).setUsername("alice").setStatus(0L).build())
                        .build());

        mockMvc.perform(post(GET_PATH)
                        .header("Authorization", "Bearer " + validToken())
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("{\"anything\":1}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(200));

        verify(stub).getUser(any());
    }

    // ==================================================================
    // 3. 判据的另外两个边界（Go: Header.Get / strings.Contains 都是字面量）
    // ==================================================================

    /**
     * Go 用的是 {@code strings.Contains(ct, "application/json")} —— <b>字面子串</b>，
     * 不是 Spring 的媒体类型兼容判定。{@code application/vnd.api+json} <b>不包含</b>该子串
     * ⇒ body 被丢弃 ⇒ 400；而 Spring 默认会把它当 JSON 正常解析 ⇒ 那就是 200。
     */
    @Test
    @DisplayName("判据边界：application/*+json 在 Go 侧**也不算** JSON（字面子串，不是兼容判定）")
    void vendorJsonSuffixIsNotApplicationJson() throws Exception {
        var asJsonEmptyObject = mockMvc.perform(post(LOGIN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn();

        var asVendorJson = mockMvc.perform(post(LOGIN_PATH)
                        .contentType(MediaType.parseMediaType("application/vnd.api+json"))
                        .content(VALID_LOGIN_JSON))
                .andReturn();

        assertEquals(USERNAME_MISSING, asVendorJson.getResponse().getContentAsString(),
                "⚠️ Spring 默认把 application/*+json 当 JSON 正常解析（会成功），"
                        + "Go 用字面子串匹配 ⇒ 丢弃 body ⇒ 400");
        assertEquals(asJsonEmptyObject.getResponse().getStatus(),
                asVendorJson.getResponse().getStatus());

        verify(stub, never()).login(any());
    }

    /**
     * Go：{@code r.Header.Get("Content-Type")} 在头缺失时返回<b>空串</b>，
     * {@code strings.Contains("", "application/json")} 为假 ⇒ 丢弃 body。
     */
    @Test
    @DisplayName("判据边界：**完全不带 Content-Type** ⇒ 同样丢弃（Go 的 Header.Get 返回空串）")
    void absentContentTypeAlsoDiscardsBody() throws Exception {
        var r = mockMvc.perform(post(LOGIN_PATH)
                        .content(VALID_LOGIN_JSON))          // 刻意不设 Content-Type
                .andReturn();

        assertEquals(400, r.getResponse().getStatus());
        assertEquals(USERNAME_MISSING, r.getResponse().getContentAsString());
        assertEquals("text/plain", r.getResponse().getContentType());
        assertEquals("nosniff", r.getResponse().getHeader("X-Content-Type-Options"));

        verify(stub, never()).login(any());
    }

    // ==================================================================
    // 4. 回归网：正常 JSON 路径 + 鉴权优先
    // ==================================================================

    /**
     * 最容易被上面几条改坏的一条：判据一旦写宽，正常 JSON 路径必须<b>不受影响</b>。
     */
    @Test
    @DisplayName("正常 JSON Content-Type 路径**不受影响**（回归网：body 真的被解析 ⇒ 调到了下游）")
    void normalJsonPathIsUnchanged() throws Exception {
        // user-rpc 的 LoginResp 是 {uid, status}；token 由 api 层自己签发，不是 rpc 返回的。
        when(stub.login(any())).thenReturn(
                com.rag.users.user.proto.LoginResp.newBuilder()
                        .setUid(CLAIM_UID)
                        .build());

        mockMvc.perform(post(LOGIN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_LOGIN_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(stub).login(any());
    }

    /**
     * 鉴权链在 body 解析<b>之外</b>（Go：{@code engine.go:105-112} 先装 MaxBytes 等原生中间件、
     * 再 {@code appendAuthHandler}；但 body 解析发生在<b>进入 handler 之后</b>）。
     * ⇒ 无 token 的请求先 401，永远走不到 body 判据。
     */
    @Test
    @DisplayName("非 JSON + **无 token** ⇒ 401（鉴权先于 body 判据）")
    void authRunsBeforeBodyDecision() throws Exception {
        mockMvc.perform(post(GET_PATH)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(VALID_LOGIN_JSON))
                .andExpect(status().isUnauthorized());

        verify(stub, never()).getUser(any());
    }
}