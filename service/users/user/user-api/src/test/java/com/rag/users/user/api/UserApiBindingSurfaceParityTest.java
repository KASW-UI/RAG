package com.rag.users.user.api;

import com.rag.users.user.api.client.UserRpcClient;
import com.rag.users.user.proto.UserServiceGrpc.UserServiceBlockingStub;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

/**
 * 🔴 <b>锁死「user-api 的绑定面只有 JSON body」这一事实</b> ——
 * 用来防一件很具体的事：<b>下一个人照着旧台账去「补齐 query/form/header 绑定面」</b>。
 *
 * <h2>为什么需要这个类</h2>
 * <p>旧台账 {@code DRIFT-USERAPI-004} 断言「Go 接受 path/query/form/HTTP header，
 * RAG 只接受 JSON body ⇒ 客户端契约面被收窄」，并把它列为待修项。
 * <b>该断言是错的</b>，而且它是<b>纯静态源码阅读</b>得出的、从未跑过活体。
 *
 * <p><b>错在哪</b>：旧台账读了 go-zero 的 {@code parseKeyAndOptions}
 * （{@code core/mapping/utils.go}）—— 它确实在「没有对应 tag」时回落到 <b>Go 字段名</b>，
 * 旧台账据此推断 form/header 通道会按字段名绑定。
 * <b>但漏掉了紧挨着的那道门</b>：
 * <pre>
 *   core/mapping/unmarshaler.go:570-573
 *     func usingDifferentKeys(key string, field reflect.StructField) bool {
 *         if len(field.Tag) &gt; 0 {
 *             if _, ok := field.Tag.Lookup(key); !ok {
 *                 return true        // ← 该字段没有 &lt;key&gt; tag ⇒ **整个字段被跳过**
 *             }
 *         }
 *         return false
 *     }
 *   ⇒ processField 首行就 return nil
 * </pre>
 * 也就是说：<b>字段必须带对应通道的 tag，那个通道才会看它一眼。</b>
 * 没有 tag ⇒ 不是「按字段名回落」，而是「<b>完全不看</b>」。
 *
 * <p>而 user-api 的 Req 结构体<b>只有 json tag</b>
 * （{@code service/user/user/api/internal/types.go}，{@code json:"username"} 等）⇒
 * path / form / header 三个通道对它们<b>全部惰性</b>；
 * 随后的 json 通道又按 json tag 强制必填 ⇒
 * <b>⇒ Go 的 user-api 只认「Content-Type 含 application/json + JSON body」这一种输入。</b>
 *
 * <h2>Go 侧活体实证</h2>
 * <p>用真源自己的 {@code types.LoginReq} + go-zero 的 {@code httpx.Parse} 直接探
 * （{@code go run -overlay} 虚拟注入探针，真源磁盘零写入）：
 * <pre>
 *   1) JSON body (CT=application/json)        ⇒ Username="j" Password="p"
 *   2) 空 body + query ?username=q&password=p  ⇒ ERR field "username" is not set
 *   3) 空 body + urlencoded form body          ⇒ ERR field "username" is not set
 *   4) header Username/Password                ⇒ ERR field "username" is not set
 *   5) query + JSON body（username 冲突）      ⇒ Username="fromJson"   ← JSON 赢
 *   6) JSON body 缺 password                   ⇒ ERR field "password" is not set
 *   7) JSON body 但 Content-Type 缺失           ⇒ ERR field "username" is not set
 *   8) JSON body 但 Content-Type=text/plain     ⇒ ERR field "username" is not set
 * </pre>
 * 第 3/4 条<b>连带 {@code form:}/{@code header:} tag 的对照结构体也绑不上</b>，
 * 只有 json tag 写成 {@code ,optional} 且<b>同时</b>带 form/header tag 时该通道才生效
 * ⇒ 证明机制本身可用，是<b>user-api 的结构体没带那些 tag</b>，不是「go-zero 的 form 通道坏了」。
 *
 * <h2>本类守什么</h2>
 * <ul>
 *   <li>query / header / urlencoded form <b>都不参与绑定</b> ⇒ 一律 400 + 与 Go 逐字相同的文案</li>
 *   <li>Content-Type 不含 {@code application/json} ⇒ body 被<b>丢弃</b>（不是被「宽松解析」）</li>
 *   <li>query 与 JSON 同时给同一字段 ⇒ <b>JSON 赢</b>（Go 的通道顺序 path→form→header→json，
 *       json 在<b>最后</b>）</li>
 *   <li>Content-Type 含 {@code application/json} ⇒ <b>确实绑上了</b>（正向对照）</li>
 * </ul>
 * ⚠️ <b>不要在 RAG 侧「顺手加上」query/form/header 绑定</b> ——
 * 那样才是真的引入漂移：Go 不绑，RAG 绑 ⇒ 同一请求两侧行为分叉。
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
class UserApiBindingSurfaceParityTest {

    /** public 路径；Go 的 LoginReq 两个标量必填（types.go:32-35，只有 json tag）。 */
    private static final String LOGIN_PATH = "/user/v1/login";

    /** Go 的 newInitError 文案 + fmt.Fprintln 的尾换行（与 Go 逐字相同）。 */
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

    // ==================================================================
    // 1. 非 JSON 通道一律**不参与绑定**
    // ==================================================================

    /**
     * Go 侧对照：{@code httpx.Parse} 对 {@code ?username=q&password=p} 返回
     * {@code field "username" is not set}（探针第 2 条）。
     *
     * <p>⚠️ <b>这条红了就说明 RAG 侧「多绑了」query</b> ⇒ 那是引入漂移，不是修复。
     */
    @Test
    @DisplayName("query 参数**不参与绑定**（Go 亦然）⇒ 400 + 与 Go 逐字相同的文案")
    void queryParamsAreNotBound() throws Exception {
        var r = mockMvc.perform(post(LOGIN_PATH)
                        .param("username", "q")
                        .param("password", "p"))
                .andReturn();

        assertEquals(400, r.getResponse().getStatus());
        assertEquals(USERNAME_MISSING, r.getResponse().getContentAsString());
        verify(stub, never()).login(any());
    }

    /**
     * Go 侧对照：探针第 4 条 —— header 通道对 user-api 的结构体<b>完全惰性</b>。
     *
     * <p>⚠️ Go 的 header 通道还多一道规范化（{@code textproto.CanonicalMIMEHeaderKey}），
     * 但对 user-api <b>无关</b>：字段没有 {@code header:} tag ⇒ 整个字段被跳过。
     */
    @Test
    @DisplayName("HTTP header **不参与绑定**（Go 亦然）⇒ 400 + 与 Go 逐字相同的文案")
    void httpHeadersAreNotBound() throws Exception {
        var r = mockMvc.perform(post(LOGIN_PATH)
                        .header("username", "h")
                        .header("password", "hp"))
                .andReturn();

        assertEquals(400, r.getResponse().getStatus());
        assertEquals(USERNAME_MISSING, r.getResponse().getContentAsString());
        verify(stub, never()).login(any());
    }

    /**
     * Go 侧对照：探针第 3 条 —— urlencoded body 也进不了 form 通道。
     *
     * <p>⚠️ 注意 Go 的 form 通道**看的是 {@code r.Form}**，
     * 而 {@code r.Form} 是 query ∪ urlencoded body（{@code r.ParseForm()} 的合并语义）。
     * 但那 irrelevant：user-api 的字段没有 {@code form:} tag ⇒ 整个通道跳过。
     */
    @Test
    @DisplayName("urlencoded form body **不参与绑定**（Go 亦然）⇒ 400 + 与 Go 逐字相同的文案")
    void urlEncodedFormBodyIsNotBound() throws Exception {
        var r = mockMvc.perform(post(LOGIN_PATH)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("username=f&password=fp"))
                .andReturn();

        assertEquals(400, r.getResponse().getStatus());
        assertEquals(USERNAME_MISSING, r.getResponse().getContentAsString());
        verify(stub, never()).login(any());
    }

    // ==================================================================
    // 2. Content-Type 判据：不是 application/json ⇒ body 被丢弃
    // ==================================================================

    /**
     * Go 侧对照：探针第 7/8 条 —— body 非空但 Content-Type 不含 {@code application/json}
     * ⇒ {@code withJsonBody == false} ⇒ <b>body 被丢弃</b> ⇒ 400 必填缺失。
     *
     * <p>⚠️ <b>不是 415</b>：Go 从不返回 415（它根本没有「媒体类型不支持」这个概念）。
     * RAG 侧若出现 415 ⇒ 说明 Spring 的 {@code HttpMediaTypeNotSupportedException}
     * 抢在了 Go 风格判据前面 ⇒ 是漂移。
     */
    @Test
    @DisplayName("Content-Type 不是 application/json ⇒ body 被丢弃 ⇒ 400（**不是 415**）")
    void nonJsonContentTypeDiscardsBodyAndYields400() throws Exception {
        var r = mockMvc.perform(post(LOGIN_PATH)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("{\"username\":\"u\",\"password\":\"p\"}"))
                .andReturn();

        assertEquals(400, r.getResponse().getStatus(),
                "Go 侧是 400（body 被丢弃 ⇒ 必填缺失），不是 415");
        assertEquals(USERNAME_MISSING, r.getResponse().getContentAsString());
        verify(stub, never()).login(any());
    }

    // ==================================================================
    // 3. 通道优先级：query 与 JSON 冲突时 **JSON 赢**
    // ==================================================================

    /**
     * Go 侧对照：探针第 5 条 ⇒ {@code Username="fromJson"}。
     *
     * <p><b>机制</b>：go-zero {@code rest/httpx/requests.go:50-68} 的通道顺序是
     * {@code ParsePath → ParseForm → ParseHeaders → ParseJsonBody}，
     * <b>json 在最后</b> ⇒ 后写的赢。
     *
     * <p>⚠️ 对 RAG 的意义：query 与 JSON 同时给同一字段时，<b>必须以 JSON 为准</b>。
     * 现在 RAG 根本不读 query，天然满足；这条把它钉住，
     * 防止将来有人「补 query 绑定」时把优先级做反。
     */
    @Test
    @DisplayName("query 与 JSON 同时给 username ⇒ **JSON 赢**（Go 的通道顺序：json 在最后）")
    void jsonBodyWinsOverQueryParam() throws Exception {
        when(stub.login(any())).thenReturn(
                com.rag.users.user.proto.LoginResp.newBuilder().setUid(31337L).build());

        var r = mockMvc.perform(post(LOGIN_PATH)
                        .param("username", "fromQuery")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"fromJson\",\"password\":\"p\"}"))
                .andReturn();

        assertEquals(200, r.getResponse().getStatus(),
                "JSON body 字段齐全 ⇒ 应绑上并进入下游，不该是 400");

        ArgumentCaptor<com.rag.users.user.proto.LoginReq> captor =
                ArgumentCaptor.forClass(com.rag.users.user.proto.LoginReq.class);
        verify(stub).login(captor.capture());
        assertEquals("fromJson", captor.getValue().getUsername(),
                "⚠️ 必须以 JSON 为准（Go 的通道顺序 path→form→header→json，json 在最后）。"
                        + "若取到 fromQuery，说明有人把 query 绑定加进来且优先级做反了");
    }
}
