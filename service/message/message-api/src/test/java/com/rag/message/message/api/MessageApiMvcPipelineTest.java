package com.rag.message.message.api;

import com.rag.message.message.api.client.MessageRpcClient;
import com.rag.message.message.proto.ConversationListReq;
import com.rag.message.message.proto.ConversationListResp;
import com.rag.message.message.proto.ConversationMessageListReq;
import com.rag.message.message.proto.ConversationMessageListResp;
import com.rag.message.message.proto.ConversationStatus;
import com.rag.message.message.proto.MarkAllNotificationsReq;
import com.rag.message.message.proto.MarkConversationReadReq;
import com.rag.message.message.proto.MarkNotificationReq;
import com.rag.message.message.proto.MessageServiceGrpc.MessageServiceBlockingStub;
import com.rag.message.message.proto.NotificationListReq;
import com.rag.message.message.proto.NotificationListResp;
import com.rag.message.message.proto.SendChatMessageReq;
import com.rag.message.message.proto.SendChatMessageResp;
import com.rag.message.message.proto.SendNotificationReq;
import com.rag.message.message.proto.SendNotificationResp;
import com.rag.message.message.proto.SenderRole;
import com.rag.message.message.proto.UnreadSummaryReq;
import com.rag.message.message.proto.UnreadSummaryResp;
import com.rag.users.common.jwt.JwtUtil;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * message-api 真实 MVC 管线测试 —— 锁住 Go {@code service/message/api} 的 L1/L2 契约。
 *
 * <p><b>为什么必须是「真实管线」而不是 standaloneSetup</b>：本服务最关键的三个性质
 * 全部只有真实管线才测得到：
 * <table border="1">
 *   <caption>standaloneSetup 测不到的三件事</caption>
 *   <tr><th>性质</th><th>为什么</th></tr>
 *   <tr>
 *     <td>{@code ,string} 的 int64 序列化</td>
 *     <td>取决于 Spring MVC 实际使用的 Jackson 3 mapper 上是否真的挂了
 *         {@code GoStyleCoercion} 的 customizer。单独 new 一个 mapper 造断言是假绿
 *         （GoStyleCoercion 的 javadoc 记录过第一版就是这么骗过自己的）</td>
 *   </tr>
 *   <tr>
 *     <td>401 + <b>空 body</b></td>
 *     <td>取决于 {@code HttpMiddlewareFilter} 与 {@code ResponseBodyAdvice} 的相互作用；
 *         AGENTS.md 的 DRIFT-FRAMEWORK-001 就是在这层丢的</td>
 *   </tr>
 *   <tr>
 *     <td>两个拦截器的路径互斥</td>
 *     <td>取决于 {@code WebMvcConfig#addInterceptors} 的 exclude 配置</td>
 *   </tr>
 * </table>
 *
 * <p><b>唯一被替换的是 gRPC 边界</b>（{@link MessageRpcClient}）——
 * 被测语义全在 api 进程内：路由 → 拦截器 → Service → advice → 序列化。
 */
@SpringBootTest(
        classes = MessageApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "grpc.client.message-rpc.address=static://127.0.0.1:65535",
                "grpc.client.message-rpc.negotiation-type=plaintext",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN"
        })
@AutoConfigureMockMvc
class MessageApiMvcPipelineTest {

    /** 与 application.yml 的 {@code message-api.user-auth.access-secret} 同一份。 */
    private static final String USER_SECRET = "ae0536f9-6450-4606-8e13-5a19ed505da0";
    /** 与 application.yml 的 {@code message-api.admin-auth.access-secret} 同一份（15 字节，需补齐）。 */
    private static final String ADMIN_SECRET = "yU-hCm)2*?NQmWG";
    private static final long EXPIRE_SEC = 3600L;
    private static final long CLAIM_UID = 31337L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MessageRpcClient rpcClient;

    private MessageServiceBlockingStub stub;

    @BeforeEach
    void setUp() {
        stub = Mockito.mock(MessageServiceBlockingStub.class);
        Mockito.when(rpcClient.messageRpc()).thenReturn(stub);
    }

    private static String userToken() {
        return JwtUtil.getToken(USER_SECRET, System.currentTimeMillis() / 1000L, EXPIRE_SEC, CLAIM_UID);
    }

    private static String adminToken() {
        return JwtUtil.getToken(ADMIN_SECRET, System.currentTimeMillis() / 1000L, EXPIRE_SEC, CLAIM_UID);
    }

    // ==================================================================
    // 1. 鉴权：401 + 空 body（Go unauthorized() 逐字还原）
    // ==================================================================

    @Test
    @DisplayName("鉴权失败 → HTTP 401 且 body 为空（Go authhandler.go:106 unauthorized()）")
    void missingAuthorizationIs401WithEmptyBody() throws Exception {
        mockMvc.perform(post("/message/v1/conversations/list")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                // Go 侧只 WriteHeader(401)，不写任何字节 —— 断言 body 完全为空
                .andExpect(content().string(""));
        // 短路：不该打到下游
        verify(stub, never()).listConversations(any());
    }

    @Test
    @DisplayName("签名错的 token → 401 空 body；且不触碰 message-rpc")
    void badSignatureIs401WithEmptyBody() throws Exception {
        String foreign = JwtUtil.getToken("some-other-secret-entirely", System.currentTimeMillis() / 1000L,
                EXPIRE_SEC, CLAIM_UID);
        mockMvc.perform(post("/message/v1/conversations/list")
                        .header("Authorization", "Bearer " + foreign)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(""));
        verify(stub, never()).listConversations(any());
    }

    @Test
    @DisplayName("两把密钥互不通用：user token 打 admin 端点 → 401（Go 两段 AddRoutes 各带一把 key）")
    void userTokenCannotReachAdminRoute() throws Exception {
        mockMvc.perform(post("/message/v1/admin/conversations/list")
                        .header("Authorization", "Bearer " + userToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(""));
        verify(stub, never()).listConversations(any());
    }

    @Test
    @DisplayName("admin token 打 user 端点同样 401")
    void adminTokenCannotReachUserRoute() throws Exception {
        mockMvc.perform(post("/message/v1/conversations/list")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(""));
    }

    // ==================================================================
    // 2. envelope：只有 200 与 401 两种状态码（Go SetErrorHandler 的定义）
    // ==================================================================

    @Test
    @DisplayName("成功路径 → 200 + {code:200,msg:\"OK\",data:{...}}（Go httpx.SetOkHandler）")
    void successIs200WithEnvelope() throws Exception {
        when(stub.getUnreadSummary(any())).thenReturn(UnreadSummaryResp.newBuilder()
                .setNotificationUnread(3).setConversationUnread(5).setTotalUnread(8).build());

        mockMvc.perform(get("/message/v1/unread").header("Authorization", "Bearer " + userToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.msg").value("OK"))
                // Go tag 无 ,string ⇒ 三个字段是 JSON 数字（不是字符串）
                .andExpect(jsonPath("$.data.notification_unread").value(3))
                .andExpect(jsonPath("$.data.conversation_unread").value(5))
                .andExpect(jsonPath("$.data.total_unread").value(8));
    }

    @Test
    @DisplayName("业务失败 → 仍是 HTTP 200，业务码在 body 里（Go SetErrorHandler 返回 http.StatusOK）")
    void businessFailureIsStill200() throws Exception {
        when(stub.markConversationRead(any()))
                .thenThrow(new StatusRuntimeException(Status.PERMISSION_DENIED
                        .withDescription("waiting for the other side to reply")));

        mockMvc.perform(post("/message/v1/conversations/read")
                        .header("Authorization", "Bearer " + userToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversation_id\":\"1\"}"))
                .andExpect(status().isOk())
                // Go: BizCodeFromError 先按 description 反查 → ERROR_CHAT_PENDING = 5204
                .andExpect(jsonPath("$.code").value(5204))
                // Go: shared.RPCError 命中 status.Message() != "" 分支 → msg 用 description 原文
                .andExpect(jsonPath("$.msg").value("waiting for the other side to reply"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    // ==================================================================
    // 3. 解析层失败：200 + 5001（message api 与 user api 的关键差异）
    // ==================================================================

    @Test
    @DisplayName("必填字段缺失 → 200 + {code:5001}，不是 400 text/plain（Go 注册了 SetErrorHandler）")
    void missingRequiredFieldIs200With5001() throws Exception {
        mockMvc.perform(post("/message/v1/conversations/messages")
                        .header("Authorization", "Bearer " + userToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5001))
                .andExpect(jsonPath("$.msg").value("internal server error"));
        verify(stub, never()).getConversationMessages(any());
    }

    @Test
    @DisplayName("空 body 同样 5001（Go withJsonBody=false 时走 UnmarshalJsonMap(nil) 仍校验必填）")
    void emptyBodyIs200With5001() throws Exception {
        mockMvc.perform(post("/message/v1/notifications/read")
                        .header("Authorization", "Bearer " + userToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5001));
    }

    @Test
    @DisplayName("非法 JSON → 200 + 5001")
    void malformedJsonIs200With5001() throws Exception {
        mockMvc.perform(post("/message/v1/conversations/list")
                        .header("Authorization", "Bearer " + userToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5001));
    }

    /**
     * {@code extra} 的 null / 缺席<b>都放行</b>（2026-10-22 修正，DRIFT-MSGAPI-003）。
     *
     * <p>⚠️ <b>本用例替换了原 {@code extraPresenceSemanticsArePreserved}</b>。旧版断言
     * {@code extra:null → 5001}，依据是 users-common {@code GoStyleRequired.explicitNull} 的注释 ——
     * 而那条实测针对的是 users 域 {@code CreateUserReq.Extrainfo}，tag 是
     * {@code json:"extra_info,omitempty"}；<b>{@code omitempty} 不是 {@code optional}</b>
     * （go-zero {@code core/mapping/utils.go:376} 的 parseOption 只认 {@code optional}），
     * 所以它才走 non-optional 分支报错。
     *
     * <p>本 DTO 的 tag 是 {@code json:"extra,optional"} ⇒ Go <b>接受</b> null。
     * 真源探针实测：{@code {"title":"t","content":"c","extra":null}} → ACCEPTED。
     * 同一文件里 Go 的 {@code UpdateUserReq.Extrainfo}（也是 {@code ,optional}）同样接受 null，
     * 两条实测互相印证。
     *
     * <p><b>教训</b>：把「某个 struct 的实测结论」当成「整类字段的通用规则」，
     * 是 §3.1 点名的对照陷阱；本条与 {@code int64AsJsonNumberIsRejected} 是同一类的两个实例。
     */
    @Test
    @DisplayName("extra 缺席与 extra:null 都放行（Go tag 是 ,optional 而非 ,omitempty）")
    void extraNullAndAbsentAreBothAccepted() throws Exception {
        when(stub.sendNotification(any()))
                .thenReturn(SendNotificationResp.newBuilder().setAffected(1).build());

        for (String body : new String[]{
                "{\"title\":\"t\",\"content\":\"c\",\"target_id\":\"9\"}",
                "{\"title\":\"t\",\"content\":\"c\",\"target_id\":\"9\",\"extra\":null}"}) {
            mockMvc.perform(post("/message/v1/admin/notifications/send")
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }
        verify(stub, times(2)).sendNotification(any());
    }

    // ==================================================================
    // 4. ,string 语义：JSON 字符串与数字【都】接受（DRIFT-001 修复后）
    // ==================================================================

    /**
     * <b>DRIFT-MSGAPI-001 修复后的正确断言</b>（2026-10-22）。
     *
     * <p>⚠️ <b>本用例替换了原 {@code int64AsJsonNumberIsRejected}</b>。旧版断言
     * 「JSON 数字 → 5001」，并把 RAG 的这个行为当成「与 Go 一致」——
     * <b>方向反了</b>，而且那条绿测试会主动阻止正确修复。
     *
     * <p><b>Go 侧实测（v1.10.2 真源探针，{@code core/mapping} 直接调用）</b>：
     * <pre>
     *   {"conversation_id":123}   → parsed = 123，<b>无错误</b>
     *   {"conversation_id":"123"} → parsed = 123，无错误
     * </pre>
     * 根因：{@code encoding/json} 里 {@code type Number string}，
     * {@code reflect.TypeOf(json.Number("123")).Kind()} 返回 {@code string}，
     * 于是 {@code unmarshaler.go:889} 的 {@code if valueKind != reflect.String} 对数字放行，
     * 再由 {@code fillPrimitive}（{@code :1115-1125}）的 {@code case json.Number} 分支
     * 走 {@code strconv.ParseInt} 成功。
     *
     * <p><b>反事实检验</b>：一个不看 Go 源码的 Spring 开发者，用 {@code Long} 字段
     * 或 {@code String} setter 都<b>不会</b>自然得到「只收字符串」——
     * Jackson 默认两种都收。所以这是一处真实的高危漂移，不是实现风格差异。
     */
    @Test
    @DisplayName("DRIFT-001：conversation_id 以 JSON 数字传入 → 与字符串【一样】放行")
    void int64AsJsonNumberIsAlsoAccepted() throws Exception {
        when(stub.getConversationMessages(any()))
                .thenReturn(ConversationMessageListResp.newBuilder().build());

        mockMvc.perform(post("/message/v1/conversations/messages")
                        .header("Authorization", "Bearer " + userToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversation_id\":123}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<ConversationMessageListReq> captor =
                ArgumentCaptor.forClass(ConversationMessageListReq.class);
        verify(stub).getConversationMessages(captor.capture());
        assertEquals(123L, captor.getValue().getConversationId());
    }

    /**
     * 浮点仍然拒绝 —— 但注意<b>「数值上是整数」也拒</b>。
     *
     * <p>Go 侧 {@code UseNumber} 保留<b>原样字面量</b>：{@code 1.0} → {@code "1.0"}、
     * {@code 1e2} → {@code "1e2"}，{@code strconv.ParseInt} 对它们一律失败。
     * 探针实测：{@code {"conversation_id":1.5} → strconv.ParseInt: parsing "1.5"}、
     * {@code {"conversation_id":1.0} → strconv.ParseInt: parsing "1.0"}。
     */
    @Test
    @DisplayName(",string int64 收浮点 → 5001，且 1.0 也拒（Go 字面量 ParseInt 失败）")
    void int64AsFloatIsRejected() throws Exception {
        for (String body : new String[]{
                "{\"conversation_id\":1.5}", "{\"conversation_id\":1.0}"}) {
            mockMvc.perform(post("/message/v1/conversations/messages")
                            .header("Authorization", "Bearer " + userToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(5001));
        }
        verify(stub, never()).getConversationMessages(any());
    }

    /** 布尔拒绝：Go 报 {@code the value in map is not string, but bool}（探针实测）。 */
    @Test
    @DisplayName(",string int64 收布尔 → 5001（Go: not string, but bool）")
    void int64AsBooleanIsRejected() throws Exception {
        mockMvc.perform(post("/message/v1/conversations/messages")
                        .header("Authorization", "Bearer " + userToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversation_id\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5001));
        verify(stub, never()).getConversationMessages(any());
    }

    /**
     * <b>DRIFT-MSGAPI-002</b>：无 {@code ,string} 的整数字段<b>拒绝</b> JSON 字符串。
     *
     * <p>Go 出处：{@code offset} 是 {@code int32 json:"offset,optional"}，tag 里没有 {@code string}
     * ⇒ {@code FromString == false}（{@code core/mapping/utils.go:376} 只在出现
     * {@code stringOption} 时置 true）⇒ 走 {@code processFieldPrimitive}
     * （{@code unmarshaler.go:643-662}）的 {@code typeKind != valueKind} 分支 ⇒ 解析失败。
     * 探针实测：{@code {"offset":"10"} → "type mismatch for field \"offset\""}，
     * 且解析失败后 {@code offset} 仍为 0。
     *
     * <p>修复前 Jackson 默认强转，RAG 会<b>接受</b>并真的把 offset=10 传下去。
     */
    @Test
    @DisplayName("DRIFT-002：offset / unread_only 收 JSON 字符串 → 5001（Go type mismatch）")
    void plainScalarFieldsRejectJsonString() throws Exception {
        when(stub.listConversations(any())).thenReturn(ConversationListResp.newBuilder().build());

        for (String body : new String[]{
                "{\"offset\":\"10\"}", "{\"limit\":\"5\"}", "{\"unread_only\":\"true\"}"}) {
            mockMvc.perform(post("/message/v1/conversations/list")
                            .header("Authorization", "Bearer " + userToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(5001));
        }
        verify(stub, never()).listConversations(any());
    }

    /** 补强的反面：JSON 数字仍然正常接收（别把规则写过头）。 */
    @Test
    @DisplayName("DRIFT-002 反向：offset 传 JSON 数字 → 正常放行且值生效")
    void plainScalarFieldsStillAcceptJsonNumber() throws Exception {
        when(stub.listConversations(any())).thenReturn(ConversationListResp.newBuilder().build());

        mockMvc.perform(post("/message/v1/conversations/list")
                        .header("Authorization", "Bearer " + userToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"offset\":10,\"limit\":5,\"unread_only\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<ConversationListReq> captor = ArgumentCaptor.forClass(ConversationListReq.class);
        verify(stub).listConversations(captor.capture());
        assertEquals(10, captor.getValue().getOffset());
        assertEquals(5, captor.getValue().getLimit());
    }

    /** admin 侧的 bool / int32 同样受补强规则约束（broadcast / kind）。 */
    @Test
    @DisplayName("DRIFT-002：admin 侧 broadcast / kind 收 JSON 字符串 → 5001")
    void adminPlainScalarsRejectJsonString() throws Exception {
        for (String body : new String[]{
                "{\"broadcast\":\"true\",\"title\":\"t\",\"content\":\"c\"}",
                "{\"kind\":\"1\",\"title\":\"t\",\"content\":\"c\"}"}) {
            mockMvc.perform(post("/message/v1/admin/notifications/send")
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(5001));
        }
        verify(stub, never()).sendNotification(any());
    }

    /**
     * 🔴 <b>DRIFT-MSGAPI-005（2026-10-23 修）：JSON <b>数字</b>进布尔字段必须被拒。</b>
     *
     * <p><b>Go 出处</b>：Go 的 {@code encoding/json} 对 number → bool 是<b>报错</b>：
     * <pre>
     * json: cannot unmarshal number into Go struct field ... of type bool
     * </pre>
     * 而 Jackson 3 默认<b>接受</b>并静默转换（{@code 1 ⇒ true}、{@code 0 ⇒ false}）。
     *
     * <p><b>为什么这条是投放事故级</b>：{@code AdminSendNotificationReq.broadcast}
     * （Go: {@code Broadcast bool \`json:"broadcast,optional"\`}）决定「定向通知」还是「<b>全量广播</b>」。
     * 一条本意为定向的请求写成 {@code {"broadcast":1}} ⇒
     * <b>修复前</b>：Java 侧静默变成 {@code true} ⇒ <b>单播变全站广播</b>，而 Go 侧会直接拒绝这条请求。
     * <b>修复后</b>：解析失败 ⇒ 5001 ⇒ 零次下游调用。
     *
     * <p>⚠️ 与上面那条 {@code adminPlainScalarsRejectJsonString} 是<b>不同的输入形态</b>：
     * 那条测的是 JSON <b>字符串</b>（{@code "true"}），这条测的是 JSON <b>数字</b>（{@code 1}）。
     * 两条分别对应 {@code CoercionInputShape.String} 与 {@code CoercionInputShape.Integer}
     * 两条独立的 coercion 配置，缺一不可。
     */
    @Test
    @DisplayName("🔴 DRIFT-005：broadcast 收 JSON 数字 → 5001 且零次下游调用（防单播变全量广播）")
    void adminBroadcastRejectsJsonNumber() throws Exception {
        for (String body : new String[]{
                "{\"broadcast\":1,\"title\":\"t\",\"content\":\"c\"}",
                "{\"broadcast\":0,\"title\":\"t\",\"content\":\"c\"}"}) {
            mockMvc.perform(post("/message/v1/admin/notifications/send")
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(5001));
        }
        // 🔴 关键：不只是返回码要对，**下游必须一次都没被调用** ——
        //    否则「误发全量」这个后果已经发生了，状态码再对也只是事后补救。
        verify(stub, never()).sendNotification(any());
    }

    /**
     * {@code ,string} 字段<b>不受</b>补强规则影响：数字与字符串都放行。
     *
     * <p>这条是对 {@link com.rag.message.message.api.config.MessageGoStyleCoercion}
     * 的守门 —— 若有人把 {@code LogicalType.Integer/String=Fail} 误改成
     * {@code CoercionInputShape.Integer=Fail}（即回到只收字符串），
     * 本用例会立刻变红。
     */
    @Test
    @DisplayName("补强规则不得误伤 ,string 字段：字符串与数字并存放行")
    void stringTaggedFieldIsUnaffectedByPlainScalarRules() throws Exception {
        when(stub.markNotificationRead(any())).thenReturn(
                com.rag.message.message.proto.BaseResp.newBuilder().build());

        for (String body : new String[]{
                "{\"notification_id\":\"77\"}", "{\"notification_id\":77}"}) {
            mockMvc.perform(post("/message/v1/notifications/read")
                            .header("Authorization", "Bearer " + userToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        ArgumentCaptor<MarkNotificationReq> captor = ArgumentCaptor.forClass(MarkNotificationReq.class);
        verify(stub, times(2)).markNotificationRead(captor.capture());
        for (MarkNotificationReq req : captor.getAllValues()) {
            assertEquals(77L, req.getNotificationId());
        }
    }

    @Test
    @DisplayName("conversation_id 以 JSON 字符串传入 → 放行，且以 int64 传给 rpc")
    void int64AsJsonStringIsAccepted() throws Exception {
        when(stub.getConversationMessages(any()))
                .thenReturn(ConversationMessageListResp.newBuilder().build());

        mockMvc.perform(post("/message/v1/conversations/messages")
                        .header("Authorization", "Bearer " + userToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversation_id\":\"123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<ConversationMessageListReq> captor =
                ArgumentCaptor.forClass(ConversationMessageListReq.class);
        verify(stub).getConversationMessages(captor.capture());
        assertEquals(123L, captor.getValue().getConversationId());
        assertEquals(CLAIM_UID, captor.getValue().getOperatorId());
        // Go: OperatorRole: pb.SenderRole_USER
        assertEquals(SenderRole.USER.getNumber(), captor.getValue().getOperatorRoleValue());
    }

    @Test
    @DisplayName("content 传数字 → 5001（Go type mismatch，GoStyleCoercion 规则 1）")
    void numberIntoStringFieldIsRejected() throws Exception {
        mockMvc.perform(post("/message/v1/conversations/send")
                        .header("Authorization", "Bearer " + userToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":123}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5001));
    }

    // ==================================================================
    // 5. 身份解析：claim 不合格 → 200 + 5004（不是 401）
    // ==================================================================

    @Test
    @DisplayName("无 userId claim 的有效 token → 200 + {code:5004}（Go UserIDFromContext 的 CodeError）")
    void tokenWithoutUserIdClaimIs5004() throws Exception {
        String noUid = io.jsonwebtoken.Jwts.builder()
                .issuedAt(new java.util.Date())
                .expiration(new java.util.Date(System.currentTimeMillis() + 3_600_000L))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(JwtUtil.padToHmacKeySize(USER_SECRET)))
                .compact();

        mockMvc.perform(get("/message/v1/unread").header("Authorization", "Bearer " + noUid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5004))
                .andExpect(jsonPath("$.msg").value("unauthorized"));
        verify(stub, never()).getUnreadSummary(any());
    }

    @Test
    @DisplayName("userId 是字符串 claim → 5004（Go 的类型断言目标是 json.Number）")
    void stringUserIdClaimIs5004() throws Exception {
        String strUid = io.jsonwebtoken.Jwts.builder()
                .claim("userId", "not-a-number")
                .issuedAt(new java.util.Date())
                .expiration(new java.util.Date(System.currentTimeMillis() + 3_600_000L))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(JwtUtil.padToHmacKeySize(USER_SECRET)))
                .compact();

        mockMvc.perform(get("/message/v1/unread").header("Authorization", "Bearer " + strUid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5004));
    }

    // ==================================================================
    // 6. 路由与 HTTP 方法：13 条逐条对齐 Go routes.go
    // ==================================================================

    @Test
    @DisplayName("GET /message/v1/unread 是 13 个端点里唯一的 GET；其余 12 条用 POST 注册")
    void unreadIsGetAndOthersArePost() throws Exception {
        when(stub.getUnreadSummary(any())).thenReturn(UnreadSummaryResp.newBuilder().build());
        when(stub.listConversations(any())).thenReturn(ConversationListResp.newBuilder().build());

        // GET 通
        mockMvc.perform(get("/message/v1/unread").header("Authorization", "Bearer " + userToken()))
                .andExpect(status().isOk());
        // 对同一个路径发 POST → 405（Go router 对方法不匹配也是 405）
        mockMvc.perform(post("/message/v1/unread").header("Authorization", "Bearer " + userToken()))
                .andExpect(status().isMethodNotAllowed());
        // 反向：POST-only 的端点用 GET → 405
        mockMvc.perform(get("/message/v1/conversations/list").header("Authorization", "Bearer " + userToken()))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("13 条路由全部注册成功：鉴权通过后不再 404/405")
    void allThirteenRoutesExist() throws Exception {
        when(stub.listConversations(any())).thenReturn(ConversationListResp.newBuilder().build());
        when(stub.getConversationMessages(any())).thenReturn(ConversationMessageListResp.newBuilder().build());
        when(stub.markConversationRead(any())).thenReturn(
                com.rag.message.message.proto.BaseResp.newBuilder().build());
        when(stub.sendChatMessage(any())).thenReturn(SendChatMessageResp.newBuilder().build());
        when(stub.listNotifications(any())).thenReturn(NotificationListResp.newBuilder().build());
        when(stub.markNotificationRead(any())).thenReturn(
                com.rag.message.message.proto.BaseResp.newBuilder().build());
        when(stub.markAllNotificationsRead(any())).thenReturn(
                com.rag.message.message.proto.BaseResp.newBuilder().build());
        when(stub.getUnreadSummary(any())).thenReturn(UnreadSummaryResp.newBuilder().build());
        when(stub.sendNotification(any())).thenReturn(SendNotificationResp.newBuilder().build());

        String ua = "Bearer " + userToken();
        String aa = "Bearer " + adminToken();
        String json = MediaType.APPLICATION_JSON_VALUE;

        // 8 个 user 端点 —— 每条都给<b>合法 body</b>，确保真正走到 rpc 而不是被 5001 拦住。
        // （第一版这里全用 {}，结果有必填字段的端点被解析层挡在 Service 之前，
        //   verify 计数对不上 —— 那是测试写错了，不是路由没注册。）
        mockMvc.perform(post("/message/v1/conversations/list")
                .header("Authorization", ua).contentType(json).content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(post("/message/v1/conversations/messages")
                .header("Authorization", ua).contentType(json).content("{\"conversation_id\":\"1\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(post("/message/v1/conversations/read")
                .header("Authorization", ua).contentType(json).content("{\"conversation_id\":\"1\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(post("/message/v1/conversations/send")
                .header("Authorization", ua).contentType(json).content("{\"content\":\"hi\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(post("/message/v1/notifications/list")
                .header("Authorization", ua).contentType(json).content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(post("/message/v1/notifications/read")
                .header("Authorization", ua).contentType(json).content("{\"notification_id\":\"1\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(post("/message/v1/notifications/readall")
                .header("Authorization", ua).contentType(json).content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(get("/message/v1/unread").header("Authorization", ua))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));

        // 5 个 admin 端点
        mockMvc.perform(post("/message/v1/admin/conversations/list")
                .header("Authorization", aa).contentType(json).content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(post("/message/v1/admin/conversations/messages")
                .header("Authorization", aa).contentType(json).content("{\"conversation_id\":\"1\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(post("/message/v1/admin/conversations/read")
                .header("Authorization", aa).contentType(json).content("{\"conversation_id\":\"1\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(post("/message/v1/admin/conversations/send")
                .header("Authorization", aa).contentType(json)
                .content("{\"recipient_id\":\"1\",\"content\":\"hi\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(post("/message/v1/admin/notifications/send")
                .header("Authorization", aa).contentType(json)
                .content("{\"target_id\":\"1\",\"title\":\"t\",\"content\":\"c\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));

        // 13 条路由逐条命中：8 个 user + 5 个 admin，各落到自己的 rpc 方法
        verify(stub, times(2)).listConversations(any());            // user 1 + admin 1
        verify(stub, times(2)).getConversationMessages(any());        // user 1 + admin 1
        verify(stub, times(2)).markConversationRead(any());           // user 1 + admin 1
        verify(stub, times(2)).sendChatMessage(any());                // user 1 + admin 1
        verify(stub, times(1)).listNotifications(any());
        verify(stub, times(1)).markNotificationRead(any());
        verify(stub, times(1)).markAllNotificationsRead(any());
        verify(stub, times(1)).getUnreadSummary(any());
        verify(stub, times(1)).sendNotification(any());
    }

    // ==================================================================
    // 7. 空响应体是 {} 而不是 null（Go return &types.EmptyReq{}, nil）
    // ==================================================================

    @Test
    @DisplayName("markConversationRead 返回 data:{} 而不是 data:null")
    void emptyRespIsObjectNotNull() throws Exception {
        when(stub.markConversationRead(any())).thenReturn(
                com.rag.message.message.proto.BaseResp.newBuilder().build());

        mockMvc.perform(post("/message/v1/conversations/read")
                        .header("Authorization", "Bearer " + userToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversation_id\":\"1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isMap())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    // ==================================================================
    // 8. 响应序列化：,string vs 数字 的逐字段断言
    // ==================================================================

    @Test
    @DisplayName("会话列表：conversation_id/peer_id 是字符串，三个计数字段是数字，items 为 []")
    void conversationListSerializationMatchesGoTags() throws Exception {
        when(stub.listConversations(any())).thenReturn(ConversationListResp.newBuilder()
                .addItems(com.rag.message.message.proto.ConversationItem.newBuilder()
                        .setConversationId(9001L).setPeerId(77L)
                        .setPeerRoleValue(SenderRole.USER.getNumber())
                        .setPeerName("alice").setPeerEmail("a@b.c")
                        .setStatusValue(ConversationStatus.OPEN.getNumber())
                        .setUnreadCount(4).setLatestMessage("hi")
                        .setLatestMessageAt(1700000000L).setUpdatedAt(1700000001L)
                        .setCanSend(true).build())
                .setTotal(1).setUnreadCount(4).build());

        mockMvc.perform(post("/message/v1/conversations/list")
                        .header("Authorization", "Bearer " + userToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                // ,string ⇒ 带引号的字符串
                .andExpect(jsonPath("$.data.items[0].conversation_id").value("9001"))
                .andExpect(jsonPath("$.data.items[0].peer_id").value("77"))
                // 无 ,string ⇒ JSON 数字
                .andExpect(jsonPath("$.data.items[0].unread_count").value(4))
                .andExpect(jsonPath("$.data.items[0].latest_message_at").value(1700000000L))
                .andExpect(jsonPath("$.data.items[0].updated_at").value(1700000001L))
                .andExpect(jsonPath("$.data.unread_count").value(4))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    @DisplayName("通知列表：id 三兄弟是字符串、created_at 是数字、key 是 is_read、items 为 []")
    void notificationListSerializationMatchesGoTags() throws Exception {
        when(stub.listNotifications(any())).thenReturn(NotificationListResp.newBuilder()
                .addItems(com.rag.message.message.proto.NotificationItem.newBuilder()
                        .setId(555L).setRecipientId(CLAIM_UID).setSenderId(9L)
                        .setSenderRoleValue(SenderRole.ADMIN.getNumber())
                        .setKindValue(com.rag.message.message.proto.NotificationKind.ADMIN_BROADCAST.getNumber())
                        .setTitle("t").setContent("c").setIsRead(true)
                        .setCreatedAt(1700000002L).build())
                .setTotal(1).setUnreadCount(0).build());

        mockMvc.perform(post("/message/v1/notifications/list")
                        .header("Authorization", "Bearer " + userToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value("555"))
                .andExpect(jsonPath("$.data.items[0].recipient_id").value(String.valueOf(CLAIM_UID)))
                .andExpect(jsonPath("$.data.items[0].sender_id").value("9"))
                .andExpect(jsonPath("$.data.items[0].created_at").value(1700000002L))
                // JSON key 是 is_read，不是 read
                .andExpect(jsonPath("$.data.items[0].is_read").value(true))
                // extra 缺席 ⇒ Go 的 nil map ⇒ "extra":null
                .andExpect(jsonPath("$.data.items[0].extra").doesNotExist());
    }

    @Test
    @DisplayName("空列表 ⇒ items 是 [] 而不是 null（Go make([]T,0,len)）")
    void emptyItemsIsEmptyArrayNotNull() throws Exception {
        when(stub.listConversations(any())).thenReturn(ConversationListResp.newBuilder().build());

        mockMvc.perform(post("/message/v1/conversations/list")
                        .header("Authorization", "Bearer " + userToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    @Test
    @DisplayName("sendChat：message 永远是对象（proto 缺 message 时用全零值占位）")
    void sendChatMessageIsNeverNull() throws Exception {
        when(stub.sendChatMessage(any())).thenReturn(SendChatMessageResp.newBuilder()
                .setConversationId(42L)
                .setStatusValue(ConversationStatus.OPEN.getNumber())
                .setCanSend(true)
                .build());   // 故意不 setMessage()

        mockMvc.perform(post("/message/v1/conversations/send")
                        .header("Authorization", "Bearer " + userToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"hello\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.conversation_id").value("42"))
                .andExpect(jsonPath("$.data.message").isMap())
                .andExpect(jsonPath("$.data.message.id").value("0"))
                .andExpect(jsonPath("$.data.message.content").value(""))
                .andExpect(jsonPath("$.data.message.is_read").value(false));
    }

    @Test
    @DisplayName("admin 发通知：affected 是字符串；定向发送的 recipient_ids 只有 1 个")
    void adminSendNotificationAffectedIsString() throws Exception {
        when(stub.sendNotification(any())).thenReturn(SendNotificationResp.newBuilder().setAffected(3).build());

        mockMvc.perform(post("/message/v1/admin/notifications/send")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target_id\":\"9\",\"title\":\"t\",\"content\":\"c\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.affected").value("3"));

        ArgumentCaptor<SendNotificationReq> captor = ArgumentCaptor.forClass(SendNotificationReq.class);
        verify(stub).sendNotification(captor.capture());
        assertEquals(1, captor.getValue().getRecipientIdsCount());
        assertEquals(9L, captor.getValue().getRecipientIds(0));
        // kind 缺席 + 非广播 ⇒ ADMIN_NOTICE(2)（Go shared.AdminNotificationKind）
        assertEquals(com.rag.message.message.proto.NotificationKind.ADMIN_NOTICE.getNumber(),
                captor.getValue().getKindValue());
        // sender 恒为 ADMIN（Go: SenderRole: pb.SenderRole_ADMIN）
        assertEquals(SenderRole.ADMIN.getNumber(), captor.getValue().getSenderRoleValue());
    }

    @Test
    @DisplayName("广播 ⇒ recipient_ids 为空 + kind 推断为 ADMIN_BROADCAST(1)")
    void broadcastInfersAdminBroadcastKind() throws Exception {
        when(stub.sendNotification(any())).thenReturn(SendNotificationResp.newBuilder().setAffected(100).build());

        mockMvc.perform(post("/message/v1/admin/notifications/send")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"broadcast\":true,\"title\":\"t\",\"content\":\"c\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<SendNotificationReq> captor = ArgumentCaptor.forClass(SendNotificationReq.class);
        verify(stub).sendNotification(captor.capture());
        assertEquals(0, captor.getValue().getRecipientIdsCount());
        assertTrue(captor.getValue().getBroadcast());
        assertEquals(com.rag.message.message.proto.NotificationKind.ADMIN_BROADCAST.getNumber(),
                captor.getValue().getKindValue());
    }

    @Test
    @DisplayName("定向发送但 target_id <= 0 → 200 + {code:5003}（Go 的 invalid parameter 检查）")
    void targetedSendWithoutTargetIs5003() throws Exception {
        mockMvc.perform(post("/message/v1/admin/notifications/send")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\",\"content\":\"c\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5003))
                .andExpect(jsonPath("$.msg").value("invalid parameter"));
        verify(stub, never()).sendNotification(any());
    }

    @Test
    @DisplayName("身份缺失先于参数非法：无有效 token 时打 admin 发通知 → 401（不是 5003）")
    void authenticationPrecedesParameterValidation() throws Exception {
        mockMvc.perform(post("/message/v1/admin/notifications/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\",\"content\":\"c\"}"))
                .andExpect(status().isUnauthorized());
        verify(stub, never()).sendNotification(any());
    }

    // ==================================================================
    // 9. rpc 归属条件必须透传（api 层不得自己查库）
    // ==================================================================

    @Test
    @DisplayName("markNotificationRead 把 JWT uid 作为归属条件下发，不在 api 层预查")
    void notificationOwnershipIsDelegatedToRpc() throws Exception {
        when(stub.markNotificationRead(any())).thenReturn(
                com.rag.message.message.proto.BaseResp.newBuilder().build());

        mockMvc.perform(post("/message/v1/notifications/read")
                        .header("Authorization", "Bearer " + userToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notification_id\":\"77\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<MarkNotificationReq> captor = ArgumentCaptor.forClass(MarkNotificationReq.class);
        verify(stub).markNotificationRead(captor.capture());
        assertEquals(CLAIM_UID, captor.getValue().getUserId());
        assertEquals(77L, captor.getValue().getNotificationId());
    }

    @Test
    @DisplayName("listNotifications 用 UserId 而非 OperatorId（proto 形状与 listConversations 不同）")
    void listNotificationsUsesUserIdField() throws Exception {
        when(stub.listNotifications(any())).thenReturn(NotificationListResp.newBuilder().build());

        mockMvc.perform(post("/message/v1/notifications/list")
                        .header("Authorization", "Bearer " + userToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"offset\":10,\"limit\":5,\"unread_only\":true}"))
                .andExpect(status().isOk());

        ArgumentCaptor<NotificationListReq> captor = ArgumentCaptor.forClass(NotificationListReq.class);
        verify(stub).listNotifications(captor.capture());
        assertEquals(CLAIM_UID, captor.getValue().getUserId());
        assertEquals(10, captor.getValue().getOffset());
        assertEquals(5, captor.getValue().getLimit());
        assertTrue(captor.getValue().getUnreadOnly());
    }

    @Test
    @DisplayName("markAllNotificationsRead / unread 都只下发 JWT uid")
    void identityOnlyEndpointsSendUid() throws Exception {
        when(stub.markAllNotificationsRead(any())).thenReturn(
                com.rag.message.message.proto.BaseResp.newBuilder().build());
        when(stub.getUnreadSummary(any())).thenReturn(UnreadSummaryResp.newBuilder().build());

        mockMvc.perform(post("/message/v1/notifications/readall")
                        .header("Authorization", "Bearer " + userToken())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/message/v1/unread").header("Authorization", "Bearer " + userToken()))
                .andExpect(status().isOk());

        ArgumentCaptor<MarkAllNotificationsReq> m = ArgumentCaptor.forClass(MarkAllNotificationsReq.class);
        verify(stub).markAllNotificationsRead(m.capture());
        assertEquals(CLAIM_UID, m.getValue().getUserId());

        ArgumentCaptor<UnreadSummaryReq> u = ArgumentCaptor.forClass(UnreadSummaryReq.class);
        verify(stub).getUnreadSummary(u.capture());
        assertEquals(CLAIM_UID, u.getValue().getUserId());
    }

    @Test
    @DisplayName("admin 发私信：sender=ADMIN、recipient 恒为 USER（Go 硬编码）")
    void adminSendChatUsesAdminUserRoles() throws Exception {
        when(stub.sendChatMessage(any())).thenReturn(SendChatMessageResp.newBuilder().build());

        mockMvc.perform(post("/message/v1/admin/conversations/send")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipient_id\":\"88\",\"content\":\"hi\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<SendChatMessageReq> captor = ArgumentCaptor.forClass(SendChatMessageReq.class);
        verify(stub).sendChatMessage(captor.capture());
        assertEquals(SenderRole.ADMIN.getNumber(), captor.getValue().getSenderRoleValue());
        assertEquals(SenderRole.USER.getNumber(), captor.getValue().getRecipientRoleValue());
        assertEquals(88L, captor.getValue().getRecipientId());
        assertEquals("hi", captor.getValue().getContent());
    }

    @Test
    @DisplayName("admin 列表：operator_role=ADMIN（user 侧断言已在 getConversationMessages 里覆盖 USER）")
    void adminListUsesAdminRole() throws Exception {
        when(stub.listConversations(any())).thenReturn(ConversationListResp.newBuilder().build());

        mockMvc.perform(post("/message/v1/admin/conversations/list")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());

        ArgumentCaptor<ConversationListReq> captor = ArgumentCaptor.forClass(ConversationListReq.class);
        verify(stub).listConversations(captor.capture());
        assertEquals(SenderRole.ADMIN.getNumber(), captor.getValue().getOperatorRoleValue());
        assertEquals(CLAIM_UID, captor.getValue().getOperatorId());
    }

    @Test
    @DisplayName("未知路径仍是 404（不因为挂了 catch-all advice 就返回 200 envelope）")
    void unknownPathIs404() throws Exception {
        mockMvc.perform(post("/message/v1/nope").header("Authorization", "Bearer " + userToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("admin 会话已读：operator_role=ADMIN + conversation_id 透传")
    void adminMarkConversationRead() throws Exception {
        when(stub.markConversationRead(any())).thenReturn(
                com.rag.message.message.proto.BaseResp.newBuilder().build());

        mockMvc.perform(post("/message/v1/admin/conversations/read")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversation_id\":\"5\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<MarkConversationReadReq> captor =
                ArgumentCaptor.forClass(MarkConversationReadReq.class);
        verify(stub).markConversationRead(captor.capture());
        assertEquals(SenderRole.ADMIN.getNumber(), captor.getValue().getOperatorRoleValue());
        assertEquals(5L, captor.getValue().getConversationId());
    }

    @Test
    @DisplayName("admin 消息列表：operator_role=ADMIN")
    void adminGetMessagesUsesAdminRole() throws Exception {
        when(stub.getConversationMessages(any())).thenReturn(ConversationMessageListResp.newBuilder()
                .setStatusValue(ConversationStatus.PENDING.getNumber())
                .setCanSend(false)
                .setPendingSenderId(123L)
                .setPendingSenderRoleValue(SenderRole.ADMIN.getNumber())
                .build());

        mockMvc.perform(post("/message/v1/admin/conversations/messages")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversation_id\":\"6\"}"))
                .andExpect(status().isOk())
                // pending_sender_id 是 ,string；pending_sender_role 是数字
                .andExpect(jsonPath("$.data.pending_sender_id").value("123"))
                .andExpect(jsonPath("$.data.pending_sender_role").value(SenderRole.ADMIN.getNumber()))
                .andExpect(jsonPath("$.data.can_send").value(false));

        ArgumentCaptor<ConversationMessageListReq> captor =
                ArgumentCaptor.forClass(ConversationMessageListReq.class);
        verify(stub).getConversationMessages(captor.capture());
        assertEquals(SenderRole.ADMIN.getNumber(), captor.getValue().getOperatorRoleValue());
    }

    @Test
    @DisplayName("user 发私信：sender/recipient 双方都是 USER（Go 写死）")
    void userSendChatUsesUserRoles() throws Exception {
        when(stub.sendChatMessage(any())).thenReturn(SendChatMessageResp.newBuilder().build());

        mockMvc.perform(post("/message/v1/conversations/send")
                        .header("Authorization", "Bearer " + userToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipient_id\":\"12\",\"content\":\"yo\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<SendChatMessageReq> captor = ArgumentCaptor.forClass(SendChatMessageReq.class);
        verify(stub).sendChatMessage(captor.capture());
        assertEquals(SenderRole.USER.getNumber(), captor.getValue().getSenderRoleValue());
        assertEquals(SenderRole.USER.getNumber(), captor.getValue().getRecipientRoleValue());
        assertEquals(12L, captor.getValue().getRecipientId());
    }
}