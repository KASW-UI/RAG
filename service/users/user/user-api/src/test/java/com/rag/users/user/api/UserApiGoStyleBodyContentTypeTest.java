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
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static com.rag.users.user.api.support.GoStyleHostPortPinning.pinContainerPort;

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
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379).withCreateContainerCmdModifier(cmd -> pinContainerPort(cmd, 6379));

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

        assertGoPlainTextUtf8(asPlainText.getResponse());
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
        assertGoPlainTextUtf8(r.getResponse());
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

    /**
     * 🔴 <b>守 Go {@code http.Error} 的第 ① 与 ④ 件事（DRIFT-USERAPI-006）</b>。
     *
     * <p><b>为什么断言「语义」而不是字节相等</b>：
     * Go 写的是 {@code text/plain; charset=utf-8}（分号后有空格、charset 小写）；
     * Spring 的 {@code MediaType.toString()} 输出 {@code text/plain;charset=UTF-8}
     * （无空格、大写）。二者<b>逐字不同</b>，但按 RFC 9110：
     * 参数分隔符后的 OWS 不显著、charset 取值<b>大小写不敏感</b>
     * ⇒ **同一个媒体类型、同一个字符集**，属「表述差异」而非语义漂移。
     * 强行把 Spring 拼成带空格的小写字面量，只能靠绕过 {@code MediaType} 直接塞裸 header，
     * 换来的是「字节相同」而丢掉类型安全 —— 不划算，故登记不修。
     *
     * <p><b>真正要守的是 charset 必须存在</b>：它<b>不是</b>表述问题。
     * 没有它，{@code StringHttpMessageConverter} 会回落到 {@code DEFAULT_CHARSET = ISO_8859_1}，
     * body 按 ISO-8859-1 写出 ⇒ 畸形 JSON 里带中文时客户端收到乱码，Go 不会。
     * ⚠️ 本类其余用例的文案恰好全是 ASCII，<b>看不出这个差别</b> ——
     * 所以「charset 在不在」必须单独断言，不能靠别的用例顺带覆盖。
     */
    private static void assertGoPlainTextUtf8(MockHttpServletResponse result) {
        String raw = result.getContentType();
        MediaType ct = MediaType.parseMediaType(raw);
        assertEquals("text/plain", ct.getType() + "/" + ct.getSubtype(),
                "Go 的 http.Error 写的是 Content-Type: text/plain; charset=utf-8。实际 = " + raw);
        assertEquals(StandardCharsets.UTF_8, ct.getCharset(),
                "⚠️ charset 必须显式存在：缺了它 StringHttpMessageConverter 会按 ISO-8859-1 "
                        + "写 body，畸形 JSON 带中文时输出乱码而 Go 不乱。实际 = " + raw);
        assertEquals("nosniff", result.getHeader("X-Content-Type-Options"),
                "Go 的 http.Error 同时写 X-Content-Type-Options: nosniff");
    }

    /**
     * 🔴 <b>守 DRIFT-USERAPI-006 ②「body 字节编码」那条差异的<b>前提</b></b>。
     *
     * <p><b>本轮实测推翻了旧台账的一条断言</b>：旧台账写
     * 「{@code HttpMessageNotReadableException.getMessage()} <b>可能包含非 ASCII</b>
     * （例如畸形 JSON 里带中文）⇒ 那种输入下 RAG 会输出乱码而 Go 不会」。
     * <b>实测不成立</b>：Jackson 2.21 的 {@code StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION}
     * <b>默认关闭</b>，异常文案里原始输入被替换成 {@code Source: REDACTED}
     * ⇒ <b>非 ASCII 进不了 body</b>。
     *
     * <p>⇒ 于是「ISO-8859-1 vs UTF-8」这条 body 编码差异<b>当前【不可观察】</b>：
     * body 全是 ASCII，两种编码逐字节相同。
     * <b>可观察的只有 header</b> —— 这正是本类另一条 {@code assertGoPlainTextUtf8} 守的东西。
     *
     * <p><b>本用例的作用是钉住这个前提</b>：哪天有人显式打开
     * {@code INCLUDE_SOURCE_IN_LOCATION}（或换成 Jackson 3 的相关开关），
     * 本用例会红 ⇒ 届时 body 编码差异<b>从潜伏变成可观察</b>，
     * 必须回头核对 charset 是否仍然钉着 UTF-8。
     *
     * <p>⚠️ 断言直接比<b>原始字节</b>而不是 {@code getContentAsString()} ——
     * 后者会用响应自报的 charset 解码，<b>把编码错配自己吞掉</b>。
     */
    @Test
    @DisplayName("DRIFT-006：畸形 body 的错误文案不含原始 JSON ⇒ 非 ASCII 路径当前不可达（前提守门）")
    void unreadableBodyMessageDoesNotLeakRawSource() throws Exception {
        String malformed = "{\"username\":\"中文密码\"";
        byte[] content = malformed.getBytes(StandardCharsets.UTF_8);

        byte[] raw = mockMvc.perform(post(LOGIN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().isBadRequest())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        String decodedAsUtf8 = new String(raw, StandardCharsets.UTF_8);
        assertFalse(decodedAsUtf8.contains("中文"),
                "⚠️ 前提变了：原始 JSON 进了错误文案 ⇒ body 里出现非 ASCII ⇒ "
                        + "「body 编码差异不可观察」不再成立，body 编码差异变成【可观察】，"
                        + "必须重新核对 charset 是否仍钉着 UTF-8。实际 body = " + decodedAsUtf8);
        assertFalse(decodedAsUtf8.contains("�"),
                "body 里出现替换字符：声明了 charset 却用别的编码写。实际 bytes = " + Arrays.toString(raw));
        // 顺带把「ASCII 下两种编码等价」这件事写实：body 全 ASCII ⇒ UTF-8 解码必须无损
        assertTrue(decodedAsUtf8.contains("JSON parse error"),
                "body 应当是 Spring 的解析错误文案。实际 body = " + decodedAsUtf8);
    }
}