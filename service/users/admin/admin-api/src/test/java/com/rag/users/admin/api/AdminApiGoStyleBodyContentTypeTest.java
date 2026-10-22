package com.rag.users.admin.api;

import com.rag.users.admin.api.client.AdminRpcClient;
import com.rag.users.admin.proto.AdminServiceGrpc.AdminServiceBlockingStub;
import com.rag.users.common.jwt.JwtUtil;
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
import org.springframework.test.web.servlet.MvcResult;
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
 * <b>admin-api 自动继承了共享的请求体判据，却从未被任何测试覆盖过。</b>
 *
 * <h2>为什么这是一处必须补的缺口</h2>
 * <p>{@code GoStyleJsonBodyConverter} 住在 {@code service/common}，本模块
 * {@code scanBasePackages} 含 {@code com.rag.common} ⇒ 它<b>自动注册</b>。
 * 但「注册了」与「行为是对的」是两件事：like-api / message-api 各自有 7 / 4 条对拍用例，
 * <b>admin-api 一条都没有</b>。2026-10-22 那次下沉实打实在本模块造成过 4 条回归
 * （{@code canWrite} 两个重载漏覆写导致 400 的 body 被 JSON 转义），
 * 却<b>没有任何一条本模块的测试抓到</b> —— 因为它们全都没发过非 JSON 的 Content-Type。
 *
 * <h2>⚠️ admin 域的表现形态与 like/message <b>不同</b>，共享的是<b>判据</b>不是<b>表现</b></h2>
 * <pre>
 *   Go rest/httpx/requests.go:153-155
 *       withJsonBody = ContentLength &gt; 0 &amp;&amp; strings.Contains(ct, "application/json")
 *       不满足 ⇒ mapping.UnmarshalJsonMap(nil, v)   ← **完全不看 body**
 *
 *   Go rest/httpx/responses.go:142-168  doHandleError
 *       handler == nil ⇒ http.Error(w, err.Error(), 400)
 *   证据：service/user 全树 grep SetErrorHandler <b>零命中</b>
 *       （admin/user 两个 api 都从未注册过）
 *
 *   ⇒ admin/user：HTTP 400 + text/plain + nosniff + 尾换行
 *   ⇒ like/message：HTTP 200 + {"code":5001}   ← 因为它们注册了 SetErrorHandler
 * </pre>
 *
 * <h2>本类用<b>public</b> 的 {@code /admin/v1/login} 做判据测试，这是刻意选的</h2>
 * <p>{@code LoginReq} 两个字段都是 go-zero <b>标量必填</b>（types.go 里无 tag ⇒ 非 {@code ,optional}），
 * 所以「body 被丢弃」与「body 为空」必然撞出<b>同一条错误文本</b>。
 * 而它<b>不需要 token</b>（Go routes.go 第一段 AddRoutes 不带 {@code WithJwt}）⇒
 * 判据测试完全不碰 Redis，把「body 解析」与「鉴权」两层隔离开。
 *
 * <h2>⚠️ 为什么不用 {@code /admin/v1/getuser} 做这条断言</h2>
 * <p>Go {@code types.go} 的 {@code GetUserReq.Uid} 标的是 <b>{@code form:"uid,string"}</b> 而不是 {@code json}。
 * 没有 {@code json} tag 时 go-zero 回退到<b>字段名</b>，那条「key 到底叫什么、是否必填」的推导链
 * 在本次范围外且未实测 ⇒ 拿它当判据的支点，等于把一个<b>未验证的前提</b>写进断言
 * （AGENTS.md §3.3.2）。{@code LoginReq} 的两个字段 tag 逐字清楚，零歧义。
 */
@SpringBootTest(
        classes = AdminApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "grpc.client.admin-rpc.address=static://127.0.0.1:65535",
                "grpc.client.admin-rpc.negotiation-type=plaintext",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN"
        })
@AutoConfigureMockMvc
@Testcontainers
class AdminApiGoStyleBodyContentTypeTest {

    /** 与 admin-api 的 application.yml 同一份密钥，否则验签必失败。 */
    private static final String SECRET = "yU-hCm)2*?NQmWG";
    private static final long EXPIRE_SEC = 2592000L;
    private static final long CLAIM_UID = 4242L;

    /** public 路径（Go routes.go 第一段 AddRoutes，不带 WithJwt）：LoginReq 两个标量必填。 */
    private static final String LOGIN_PATH = "/admin/v1/login";

    /** protected 路径：GetSelfReq 在 Go 里是 <b>零字段 struct</b>。 */
    private static final String GET_SELF_PATH = "/admin/v1/getself";

    /** 字段齐全、完全合法的 JSON —— 用它证明「body 被丢弃」而不是「body 被拒绝」。 */
    private static final String VALID_LOGIN_JSON = "{\"username\":\"probe\",\"password\":\"pw123456\"}";

    /**
     * Go 的 {@code newInitError} 文案 + {@code fmt.Fprintln} 的尾换行。
     * ⚠️ {@code username} 排在 {@code password} 之前（GoStyleRequired 按声明序调用），
     * 所以第一个撞出来的是 username。
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
    private AdminRpcClient adminRpcClient;
    private AdminServiceBlockingStub stub;

    @BeforeEach
    void setUp() {
        stub = Mockito.mock(AdminServiceBlockingStub.class);
        Mockito.when(adminRpcClient.adminRpc()).thenReturn(stub);
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
     *
     * <p>若有人把实现改成「放宽 content type 后照常解析 body」，本条立刻红 ——
     * 那会把 Go 明确拒绝的请求变成<b>成功</b>，比原来的 415 更糟（它看起来像"修好了"）。
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
                "admin 域未注册 SetErrorHandler ⇒ Go 侧是 400，不是 like/message 的 200+5001");

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
     * {@code GetSelfReq} 在 Go {@code types.go} 里是 {@code struct{}}（零字段）
     * ⇒ {@code UnmarshalJsonMap(nil, v)} 不报错 ⇒ 请求应当<b>成功</b>。
     *
     * <p>⚠️ 这正是当初签字理由「两侧都是拒绝」<b>不完整</b>的那个反例：
     * 非 JSON 的 Content-Type 对<b>零必填</b> DTO 毫无影响。
     */
    @Test
    @DisplayName("零字段 DTO + 非 JSON Content-Type ⇒ **成功**（Go 不在成功路径上丢弃 body）")
    void zeroFieldDtoSucceedsWithNonJsonContentType() throws Exception {
        when(stub.getSelf(any())).thenReturn(
                com.rag.users.admin.proto.GetSelfResp.newBuilder()
                        .setAdmin(com.rag.users.admin.proto.AdminInfo.newBuilder()
                                .setUid(CLAIM_UID).setUsername("admin").setEmail("").build())
                        .build());

        mockMvc.perform(post(GET_SELF_PATH)
                        .header("Authorization", "Bearer " + validToken())
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("{\"anything\":1}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(200));

        verify(stub).getSelf(any());
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
        mockMvc.perform(post(LOGIN_PATH)
                        .content(VALID_LOGIN_JSON))          // 刻意不设 Content-Type
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));

        assertEquals(USERNAME_MISSING,
                mockMvc.perform(post(LOGIN_PATH).content(VALID_LOGIN_JSON))
                        .andReturn().getResponse().getContentAsString());

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
        when(stub.login(any())).thenReturn(
                com.rag.users.admin.proto.LoginResp.newBuilder()
                        .setUid(CLAIM_UID).build());

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
        mockMvc.perform(post(GET_SELF_PATH)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(VALID_LOGIN_JSON))
                .andExpect(status().isUnauthorized());

        verify(stub, never()).getSelf(any());
    }
}