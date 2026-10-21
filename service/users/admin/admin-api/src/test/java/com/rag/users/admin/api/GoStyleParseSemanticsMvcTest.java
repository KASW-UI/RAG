package com.rag.users.admin.api;

import com.rag.users.admin.api.client.AdminRpcClient;
import com.rag.users.common.jwt.JwtUtil;
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
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * E-3 根因 **R1 + R2** 的回归网 —— admin-api 侧，走<b>真实 MVC 管线</b>。
 *
 * <p><b>为什么必须是真 MVC（而不是 standaloneSetup）</b>：
 * R1 的修复挂在 {@code WebMvcConfig#extendMessageConverters} 上 ——
 * 那是<b>框架装配期</b>对 message converter 里那个 mapper 的改写。
 * {@code MockMvcBuilders.standaloneSetup} 自己造一套 converter，
 * <b>根本不加载 WebMvcConfig</b>，于是：
 * <ul>
 *   <li>修复前它会全绿（看不到任何问题）；</li>
 *   <li>修复后它也会全绿（看不到修复生效）。</li>
 * </ul>
 * 这正是 §3.3「断言 Bean 存在 ≠ 断言机制生效」的另一个实例 ——
 * {@code ObservabilityWiringTest} 那次假绿是「Bean 在但 starter 不用它」，
 * 这里是「converter 在但装配代码没跑」。
 *
 * <p><b>Redis 为什么可以纯 mock</b>：本类只测<b>请求解析层</b>，
 * 不测鉴权顺序（那是 {@link AdminApiMvcPipelineTest} 的职责，用的是真容器 Redis）。
 * 这里只需要 hasKey 返回 false 让请求穿过拦截器到达 handler。
 *
 * <p><b>gRPC 边界被替换</b>：断言的是「解析层有没有把请求打进去」，
 * 所以 stub 一律返回 {@code NOT_FOUND}（1003）——
 * 「进了 handler」⇒ 200+envelope1003；「解析层拒了」⇒ 400+text/plain。
 * 这两者无法混淆，因此不需要真的数据库。
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
class GoStyleParseSemanticsMvcTest {

    private static final String SECRET = "yU-hCm)2*?NQmWG";
    private static final long EXPIRE_SEC = 2592000L;
    private static final long CLAIM_UID = 4242L;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        // 纯 mock Redis：本类不测鉴权，只要 hasKey=false 放行即可
        r.add("spring.data.redis.host", () -> "127.0.0.1");
        r.add("spring.data.redis.port", () -> 6399);
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StringRedisTemplate redisTemplate;

    @MockitoBean
    private AdminRpcClient adminRpcClient;

    @Autowired
    private org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer
            goStyleCoercionCustomizer;

    @BeforeEach
    void setUp() {
        when(redisTemplate.hasKey(any(String.class))).thenReturn(false);
        var stub = Mockito.mock(com.rag.users.admin.proto.AdminServiceGrpc.AdminServiceBlockingStub.class);
        when(adminRpcClient.adminRpc()).thenReturn(stub);
        when(stub.banUser(any())).thenThrow(new StatusRuntimeException(Status.NOT_FOUND));
        when(stub.createAdmin(any())).thenThrow(new StatusRuntimeException(Status.NOT_FOUND));
        when(stub.getUserList(any())).thenReturn(com.rag.users.admin.proto.GetUserListResp.newBuilder().build());
        // login：Go 侧「用户不存在」走 LoginResp.status=2，不抛 gRPC code；
        // 这里用 NOT_FOUND 表达同一件事，api 层会映射成 1003。
        when(stub.login(any())).thenThrow(new StatusRuntimeException(Status.NOT_FOUND));
    }

    private static String validToken() {
        return JwtUtil.getToken(SECRET, System.currentTimeMillis() / 1000L, EXPIRE_SEC, CLAIM_UID);
    }

    // ==================================================================
    // 装配断言：R1 的规则必须真的落在 MVC 那个 mapper 上
    //
    // 这条与下面所有行为用例是**互补**的，不是重复：
    // 行为用例证明「端点拒了」，这条证明「拒它的机制挂在哪个对象上」——
    // 万一将来有人把 extendMessageConverters 删掉又碰巧有别的机制兜住，
    // 行为用例仍会绿，而这条会立刻红。
    // ==================================================================

    /** 反序列化目标：模拟 {@code BanUserReq.uid}（Long 字段）。 */
    public static class LongBox {
        public Long v;
    }

    /** 反序列化目标：模拟 {@code LoginReq.username}（String 字段）。 */
    public static class StringBox {
        public String v;
    }

    @Test
    @DisplayName("R1 装配：customizer 造出来的 Jackson 3 mapper 确实拒浮点→整数、拒数字→字符串")
    void coercionRulesAreReallyInTheCustomizer() {
        // 用 customizer 现造一个 mapper，**不依赖容器里那个**。
        // 于是这条用例回答「规则本身对不对」，而下面端点级用例回答
        // 「规则有没有被装配到生产 mapper 上」。两者互补，缺一不可：
        //   · 只测端点 → 规则写错时红了，但不知道是规则错还是没装配
        //   · 只测 customizer → 规则对但压根没被 Boot 用上，端点仍全绿（第一版就死在这）
        var builder = tools.jackson.databind.json.JsonMapper.builder();
        goStyleCoercionCustomizer.customize(builder);
        var mapper = builder.build();

        // 1.5 → Long 必须失败（修复前 Jackson 静默截断成 1）
        assertThrows(Exception.class, () -> mapper.readValue("{\"v\":1.5}", LongBox.class),
                "1.5 进整数居然成功了 —— ACCEPT_FLOAT_AS_INT 没被关掉");
        // 1.0 → Long 也必须失败（Go 是 strconv.ParseInt(\"1.0\") 失败）
        assertThrows(Exception.class, () -> mapper.readValue("{\"v\":1.0}", LongBox.class),
                "1.0 进整数居然成功了 —— 规则被写成了「只看小数部分」，与 Go 不符");
        // 合法整数必须仍能进
        assertDoesNotThrow(() -> mapper.readValue("{\"v\":1}", LongBox.class));
        // 字符串→Long 必须仍能进（Go 的 ,string 通道是字符串化后 ParseInt）
        assertDoesNotThrow(() -> mapper.readValue("{\"v\":\"1\"}", LongBox.class));

        // 99999 → String 必须失败（Go 是 type mismatch）
        assertThrows(Exception.class, () -> mapper.readValue("{\"v\":99999}", StringBox.class),
                "数字进 String 居然成功了 —— Textual 的 coercion 没设成 Fail");
        assertThrows(Exception.class, () -> mapper.readValue("{\"v\":true}", StringBox.class),
                "布尔进 String 居然成功了");
        // 真字符串必须仍能进
        assertDoesNotThrow(() -> mapper.readValue("{\"v\":\"x\"}", StringBox.class));
    }

    // ==================================================================
    // R1 · 数字 / 布尔不许进 String 字段
    //
    // Go 实测（18889 真进程，curl 直打）：
    //   {"username":1}    → 400  type mismatch for field "username", expect "string", actual "number"
    //   {"username":1.5}  → 400  type mismatch for field "username", expect "string", actual "number"
    //   {"username":true} → 400  type mismatch for field "username"
    // 修复前 RAG 三种都返 200 + 业务码（Jackson 静默强转），是 E-3 矩阵里 R1 的 64 条。
    // ==================================================================

    @Test
    @DisplayName("R1：username 传 JSON 数字 → 400（Go 是 type mismatch，修复前 RAG 静默强转成 \"99999\"）")
    void numberIntoStringFieldIsRejected() throws Exception {
        mockMvc.perform(post("/admin/v1/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":99999,\"password\":\"pw\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("R1：username 传 JSON 浮点 → 400（Go 同样 type mismatch）")
    void floatIntoStringFieldIsRejected() throws Exception {
        mockMvc.perform(post("/admin/v1/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":1.5,\"password\":\"pw\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("R1：username 传 JSON 布尔 → 400（Go 同样 type mismatch）")
    void booleanIntoStringFieldIsRejected() throws Exception {
        mockMvc.perform(post("/admin/v1/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":true,\"password\":\"pw\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("R1 对照：username 传真字符串仍要能进 handler（证明不是把整个端点堵死了）")
    void stringIntoStringFieldStillAccepted() throws Exception {
        // 进了 handler ⇒ 返回业务 envelope(1003 用户不存在)，而不是 400
        mockMvc.perform(post("/admin/v1/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"nobody\",\"password\":\"pw\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("1003")));
    }

    // ==================================================================
    // R1 · 浮点不许进整数字段 —— 连 1.0 也必须拒
    //
    // Go 实测：
    //   {"uid":1}   → 200（接受）
    //   {"uid":"1"} → 200（接受！先字符串化再 strconv.ParseInt）
    //   {"uid":1.0} → 400  strconv.ParseInt: parsing "1.0": invalid syntax
    //   {"uid":1.5} → 400  strconv.ParseInt: parsing "1.5": invalid syntax
    //
    // ⚠️ "1.0 也拒" 这条最容易写错 —— 直觉会写成「小数部分非零才拒」，
    //    而 Go 的字符串化保留 ".0"，ParseInt 直接失败。
    // ==================================================================

    @Test
    @DisplayName("R1：uid 传 1.5 → 400（Jackson 修复前会【截断成 1】并真的执行封禁）")
    void fractionalUidIsRejected() throws Exception {
        mockMvc.perform(post("/admin/v1/banuser").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + validToken())
                        .content("{\"uid\":1.5}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("R1：uid 传 1.0 → 400（Go 是 parsing \"1.0\" 失败；不是「整数部分完整就放过」）")
    void integralFloatUidIsAlsoRejected() throws Exception {
        mockMvc.perform(post("/admin/v1/banuser").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + validToken())
                        .content("{\"uid\":1.0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("R1 对照：uid 传整数 1 仍要能进 handler")
    void integerUidStillAccepted() throws Exception {
        mockMvc.perform(post("/admin/v1/banuser").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + validToken())
                        .content("{\"uid\":1}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("R1 对照：uid 传字符串 \"1\" 仍要能进 handler（Go 的 ,string 通道是字符串化后 ParseInt，不是「只收字符串」）")
    void stringUidStillAccepted() throws Exception {
        // 上一轮实测差点把它写成 Fail —— 那会把「两边都接受」的输入改成「只有一边接受」，
        // 等于把 R1 修成新漂移。这条断言就是为了钉住这个方向。
        mockMvc.perform(post("/admin/v1/banuser").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + validToken())
                        .content("{\"uid\":\"1\"}"))
                .andExpect(status().isOk());
    }

    // ==================================================================
    // R1 的**反向**用例：Go 走 form(query) 通道的字段不吃「标量→String 一律拒」
    //
    // Go 出处：types.go 的 GetUserListReq 三个字段 tag 全是 `form:`，
    // 值来自 query string —— 永远是字符串，所以 keyword=99999 在 Go 侧就是 "99999"，合法。
    // 而 RAG 因 A-03 偏离走 JSON body，若沿用 json 通道的规则就会把 {"keyword":99999}
    // 拒成 400 ⇒ 把 R1 修成一条新漂移。
    //
    // ⚠️ 本组第一版把「非标量」也 String.valueOf 了，于是 {"keyword":{"a":1}} 被
    // 变成 "{a=1}" 然后去 LIKE 匹配 —— 静默的错误结果，被 E-3 矩阵抓到。
    // ==================================================================

    @Test
    @DisplayName("R1 反向：keyword 传 JSON 数字/布尔仍要能进 handler（Go 的 form 通道值本就是字符串）")
    void formChannelKeywordAcceptsScalars() throws Exception {
        mockMvc.perform(post("/admin/v1/getuserlist").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + validToken())
                        .content("{\"page\":1,\"page_size\":5,\"keyword\":99999}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/admin/v1/getuserlist").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + validToken())
                        .content("{\"page\":1,\"page_size\":5,\"keyword\":true}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("R1 反向的边界：keyword 传 JSON 对象必须拒（Go 是 type mismatch，不能静默 String.valueOf）")
    void formChannelKeywordRejectsNonScalar() throws Exception {
        mockMvc.perform(post("/admin/v1/getuserlist").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + validToken())
                        .content("{\"page\":1,\"page_size\":5,\"keyword\":{\"a\":1}}"))
                .andExpect(status().isBadRequest());
    }

    // ==================================================================
    // R2 · 显式 null ≠ 字段缺席
    //
    // Go 出处：unmarshaler.go:856-862 processNamedFieldWithValue
    //     if mapValue == nil { if optional {return nil}
    //                          return fmt.Errorf("field %q mustn't be nil", key) }
    // 而「缺席」走 processNamedFieldWithoutValue 的 Map 分支 → 不报错。
    // 实测（18888）：{"extra_info":null} → 400 field "extra_info" mustn't be nil；缺该字段 → 200。
    //
    // 修复前 RAG 四个 DTO 的 javadoc 都写着「Extrainfo 是 map ⇒ 不校验」，
    // 两种输入都放行 ⇒ E-3 矩阵里 R2 的 4 条。
    // ==================================================================

    @Test
    @DisplayName("R2：extra_info 显式 null → 400 field \"extra_info\" mustn't be nil")
    void explicitNullExtraInfoIsRejected() throws Exception {
        mockMvc.perform(post("/admin/v1/create").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"u\",\"password\":\"p\",\"email\":\"e@x.com\","
                                + "\"invite_code\":\"X\",\"extra_info\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("mustn't be nil")));
    }

    @Test
    @DisplayName("R2 对照：extra_info 缺席 → 放行进 handler（Go 是 200，不是 400）")
    void omittedExtraInfoStillAccepted() throws Exception {
        // 只看 extraInfo==null 就判错的话，这条会变成 400 —— 那就是「把缺席也杀了」
        mockMvc.perform(post("/admin/v1/create").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"u\",\"password\":\"p\",\"email\":\"e@x.com\","
                                + "\"invite_code\":\"X\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("R2 对照：extra_info 是真对象 → 放行进 handler")
    void presentExtraInfoStillAccepted() throws Exception {
        mockMvc.perform(post("/admin/v1/create").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"u\",\"password\":\"p\",\"email\":\"e@x.com\","
                                + "\"invite_code\":\"X\",\"extra_info\":{\"city\":\"北京\"}}"))
                .andExpect(status().isOk());
    }
}