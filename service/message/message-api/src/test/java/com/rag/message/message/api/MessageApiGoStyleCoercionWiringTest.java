package com.rag.message.message.api;

import com.rag.message.message.api.client.MessageRpcClient;
import com.rag.message.message.proto.NotificationListReq;
import com.rag.message.message.proto.NotificationListResp;
import com.rag.message.message.proto.SendNotificationReq;
import com.rag.message.message.proto.SendNotificationResp;
import com.rag.users.common.jwt.JwtUtil;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * <b>装配层守门</b>：证明 {@code MessageGoStyleCoercion} 的规则真的挂在
 * <b>Spring MVC 实际使用的那一个 mapper</b> 上（关闭 message-api 台账 UNRESOLVED-002）。
 *
 * <h3>为什么需要这一类，而 {@code tools/semantic-probe} 不够</h3>
 * <p>双侧探针证明的是「规则本身是什么」（它自己 {@code new} 一个 builder + applyTo），
 * <b>不证明「MVC 用的是这条规则」</b>。两者之间的挂载点只有一个：
 * {@code WebMvcConfig#goStyleCoercionCustomizer} 注册的 {@code JsonMapperBuilderCustomizer}。
 * 若那个 Bean 被摘掉、或者 Spring 换了另一个 mapper，探针<b>照样全绿</b>，
 * 而端点行为已经变了。
 *
 * <p>⇒ 本类全部走 {@code @SpringBootTest + MockMvc} 的<b>真实 MVC 管线</b>，
 * 每条断言都是端点行为；这正是 AGENTS.md §3.3「不得凭注解名称断定机制已启用」
 * 与 §3.3.3「纯 mock 单测不算」的直接落地。
 *
 * <h3>本类锁住的三条（都是 2026-10-23 才修的）</h3>
 * <ol>
 *   <li><b>DRIFT-MSGAPI-008</b>：{@code ,optional} 标量字段的显式 {@code null} = 零值
 *       （靠关掉 {@code FAIL_ON_NULL_FOR_PRIMITIVES}）；</li>
 *   <li><b>DRIFT-MSGAPI-006</b>：{@code extra} 的<b>元素</b>收 JSON 数字要放行且字符串化；</li>
 *   <li><b>DRIFT-MSGAPI-007</b>：{@code extra} 的<b>元素</b>显式 {@code null} 要拒绝。</li>
 * </ol>
 *
 * <h3>🔴 反向守门同样重要</h3>
 * <p>{@link #requiredFieldExplicitNullIsStillRejected()} 专门盯住「关掉
 * {@code FAIL_ON_NULL_FOR_PRIMITIVES} 有没有把关到不该关的地方」——
 * 必填字段收显式 null <b>必须</b>仍然 5001。若将来有人把必填判定也一并放松，
 * 本用例会变红（§3.3 强制三条之③的实践形态：<b>先有守门，再谈修</b>）。
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:noop;DB_CLOSE_DELAY=-1",
        "management.tracing.enabled=false",
        "management.otlp.tracing.export.enabled=false",
        "logging.level.com.alibaba.nacos=WARN"
})
@AutoConfigureMockMvc
class MessageApiGoStyleCoercionWiringTest {

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

    private com.rag.message.message.proto.MessageServiceGrpc.MessageServiceBlockingStub stub;

    @BeforeEach
    void setUp() {
        stub = Mockito.mock(com.rag.message.message.proto.MessageServiceGrpc.MessageServiceBlockingStub.class);
        Mockito.when(rpcClient.messageRpc()).thenReturn(stub);
    }

    private static String userToken() {
        return JwtUtil.getToken(USER_SECRET, System.currentTimeMillis() / 1000L, EXPIRE_SEC, CLAIM_UID);
    }

    private static String adminToken() {
        return JwtUtil.getToken(ADMIN_SECRET, System.currentTimeMillis() / 1000L, EXPIRE_SEC, CLAIM_UID);
    }

    // ==================================================================
    // 1. DRIFT-MSGAPI-008：,optional 标量字段的显式 null = 零值
    // ==================================================================

    /**
     * Go 出处（消费点）：{@code core/mapping/unmarshaler.go:854-863}
     * {@code processNamedFieldWithValue}：
     * <pre>
     *   if mapValue == nil {
     *       if opts.optional() { return nil }        ← 接受，值保持零值
     *       return fmt.Errorf("field %q mustn't be nil", key)
     *   }
     * </pre>
     * 探针用例 C1/C2/C3 是双侧对拍；本用例证明<b>同一规则在 MVC 真实管线里也生效</b>。
     */
    @Test
    @DisplayName("装配层：,optional 标量字段收显式 null → 放行且落零值（offset/limit/unread_only）")
    void optionalScalarsAcceptExplicitNullAsZeroValue() throws Exception {
        when(stub.listNotifications(any())).thenReturn(NotificationListResp.newBuilder().build());

        mockMvc.perform(post("/message/v1/notifications/list")
                        .header("Authorization", "Bearer " + userToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"offset\":null,\"limit\":null,\"unread_only\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<NotificationListReq> captor = ArgumentCaptor.forClass(NotificationListReq.class);
        verify(stub).listNotifications(captor.capture());
        assertEquals(0, captor.getValue().getOffset());
        assertEquals(0, captor.getValue().getLimit());
        assertFalse(captor.getValue().getUnreadOnly());
    }

    /**
     * {@code kind} 是 admin 侧的 {@code ,optional} int32。
     * <p>业务面：Go 侧 {@code kind:null} ⇒ 落 0 ⇒ {@code adminNotificationKind} 按
     * {@code broadcast} 推断（0 = 「按 broadcast 推断」）。RAG 侧一旦把 null 拒成 5001，
     * 一条 Go 上合法的请求就被拒了。
     */
    @Test
    @DisplayName("装配层：admin kind 收显式 null → 放行且落 0（按 broadcast 推断）")
    void adminKindAcceptsExplicitNullAsZero() throws Exception {
        when(stub.sendNotification(any()))
                .thenReturn(SendNotificationResp.newBuilder().setAffected(1).build());

        mockMvc.perform(post("/message/v1/admin/notifications/send")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":null,\"title\":\"t\",\"content\":\"c\",\"target_id\":\"9\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(stub, times(1)).sendNotification(any());
    }

    // ==================================================================
    // 2. DRIFT-MSGAPI-006：extra 元素收 JSON 数字 → 放行 + 字符串化
    // ==================================================================

    /**
     * Go 出处（消费点）：{@code core/mapping/unmarshaler.go} 的 {@code generateMap}
     * {@code default} 分支里 {@code case json.Number → setValueFromString(…, v.String())}。
     *
     * <p>⚠️ <b>字面量保留</b>是本用例真正要钉的东西：Go 用的是 {@code json.Number.String()}，
     * 也就是<b>原始字面量</b>，所以 {@code 1.0} 落 {@code "1.0"}、{@code 1e2} 落 {@code "1e2"}，
     * <b>不是</b> {@code "1"}。若有人改用数值格式化，这三条会一起变红。
     */
    @Test
    @DisplayName("装配层：extra 元素收 JSON 数字 → 放行且保留字面量（1 / 1.0 / 1e2）")
    void mapValueAcceptsJsonNumberPreservingLiteral() throws Exception {
        when(stub.sendNotification(any()))
                .thenReturn(SendNotificationResp.newBuilder().setAffected(1).build());

        String[][] cases = {
                {"{\"title\":\"t\",\"content\":\"c\",\"target_id\":\"9\",\"extra\":{\"k\":1}}", "1"},
                {"{\"title\":\"t\",\"content\":\"c\",\"target_id\":\"9\",\"extra\":{\"k\":1.0}}", "1.0"},
                {"{\"title\":\"t\",\"content\":\"c\",\"target_id\":\"9\",\"extra\":{\"k\":1e2}}", "1e2"},
        };

        for (String[] c : cases) {
            mockMvc.perform(post("/message/v1/admin/notifications/send")
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(c[0]))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        ArgumentCaptor<SendNotificationReq> captor = ArgumentCaptor.forClass(SendNotificationReq.class);
        verify(stub, times(3)).sendNotification(captor.capture());
        for (int i = 0; i < cases.length; i++) {
            assertEquals(cases[i][1], captor.getAllValues().get(i).getExtraMap().get("k"),
                    "第 " + (i + 1) + " 条的 extra.k 字面量应与 Go 侧 json.Number.String() 一致");
        }
    }

    // ==================================================================
    // 3. DRIFT-MSGAPI-007：extra 元素显式 null → 拒绝，且零次下游调用
    // ==================================================================

    /**
     * Go 出处：{@code generateMap} 的 {@code default} 分支，
     * {@code dereffedElemKind}（String）≠ {@code reflect.ValueOf(nil).Kind()}（Invalid）
     * ⇒ {@code errTypeMismatch}。
     *
     * <p>🔴 <b>必须同时断言「零次下游调用」</b>：RAG 的 {@code extra} 会原样进
     * {@code SendNotificationReq.Extra} 并落库，Go 侧读回来时这个 key 根本不存在。
     * 若只是状态码对、下游已经被调用，落库内容两侧就已经不一致了。
     */
    @Test
    @DisplayName("装配层：extra 元素显式 null → 5001 且零次下游调用（Go type mismatch）")
    void mapValueExplicitNullIsRejectedWithoutCallingRpc() throws Exception {
        mockMvc.perform(post("/message/v1/admin/notifications/send")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\",\"content\":\"c\",\"target_id\":\"9\",\"extra\":{\"k\":null}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5001));

        verify(stub, never()).sendNotification(any());
    }

    // ==================================================================
    // 4. 🔴 反向守门：放松 FAIL_ON_NULL_FOR_PRIMITIVES 不得波及必填字段
    // ==================================================================

    /**
     * 关掉 {@code FAIL_ON_NULL_FOR_PRIMITIVES} 只应放开 <b>{@code ,optional} 原始类型字段</b>；
     * 必填字段收显式 null 必须仍然 5001。
     *
     * <p>本用例存在的理由：这条 feature 是<b>正交开关</b>，不是按字段生效的规则 ——
     * 一旦将来有人把必填判定也挪到 Jackson 层、或图省事把 {@code GoStyleRequired} 放松，
     * 最先暴露的就是这里。§3.3.3「先改/删错误断言，再改实现」的实践形态：
     * <b>反向断言必须在修之前就存在</b>，否则修完就没有基线可对照。
     */
    @Test
    @DisplayName("反向守门：必填字段 title 收显式 null → 仍然 5001（放松可选字段不得越界）")
    void requiredFieldExplicitNullIsStillRejected() throws Exception {
        mockMvc.perform(post("/message/v1/admin/notifications/send")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":null,\"content\":\"c\",\"target_id\":\"9\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5001));

        verify(stub, never()).sendNotification(any());
    }

    /**
     * 字段级 {@code extra: null} 与元素级 {@code extra:{"k":null}} <b>方向相反</b>，
     * 本用例把「字段级仍放行」这条钉死 —— 修 007 时最容易误伤的就是它
     * （两者名字几乎一样，实测结论完全相反：Go 对前者 ACCEPTED、对后者 REJECTED）。
     */
    @Test
    @DisplayName("反向守门：字段级 extra:null 仍放行（与元素级 null 方向相反，勿一起改）")
    void fieldLevelExtraNullIsStillAccepted() throws Exception {
        when(stub.sendNotification(any()))
                .thenReturn(SendNotificationResp.newBuilder().setAffected(1).build());

        mockMvc.perform(post("/message/v1/admin/notifications/send")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\",\"content\":\"c\",\"target_id\":\"9\",\"extra\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(stub, times(1)).sendNotification(any());
    }
}