package com.rag.like.like.api;

import com.rag.like.like.api.client.LikeRpcClient;
import com.rag.like.like.api.service.GoStyleTargetIds;
import com.rag.like.like.proto.GetLikeCountReq;
import com.rag.like.like.proto.GetLikeCountResp;
import com.rag.like.like.proto.LikeServiceGrpc.LikeServiceBlockingStub;
import com.rag.users.common.jwt.JwtUtil;
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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 2026-10-23 Go 对齐修复轮（like-api）的<b>守门测试</b>。
 *
 * <p>⚠️ 本类存在的理由与 like-rpc 侧同款：本轮修的漂移<b>没有一条</b>被既有 62 条测试覆盖，
 * 改回原状时那 62 条<b>依然全绿</b>。
 *
 * <h2>为什么 014 必须走真实 MVC 管线</h2>
 * <p>尾随 token 的判定发生在 <b>Spring MVC 实际使用的那个</b> Jackson 3 mapper 上
 * （经 {@code JsonMapperBuilderCustomizer} 注入）。
 * 任何「自己 {@code new} 一个 mapper 再造断言」的写法都是<b>假绿</b> ——
 * 它只能证明「Jackson 有这个开关」，证明不了「本服务真的关掉了它」。
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
class LikeApiGoAlignmentTest {

    private static final String SECRET = "ae0536f9-6450-4606-8e13-5a19ed505da0";
    private static final long EXPIRE_SEC = 3600L;

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

    private static String bearer() {
        long now = System.currentTimeMillis() / 1000L;
        return "Bearer " + JwtUtil.getToken(SECRET, now, EXPIRE_SEC, 1001L);
    }

    private void stubGetLikeCount() {
        when(stub.getLikeCount(any())).thenReturn(
                GetLikeCountResp.newBuilder().putAllCounts(Map.of()).build());
    }

    // ==================== DRIFT-LIKEAPI-014：尾随 token ====================

    @Test
    @DisplayName("🔴 JSON 后的尾随内容必须被接受并只取第一个值（Go core/jsonx/json.go:74-77 只解第一个）")
    void trailingContentAfterFirstJsonValueIsAccepted() throws Exception {
        stubGetLikeCount();

        // Go B21：{…} xx ⇒ OK tt="a"（解析成功，用第一个对象，会真的发出 RPC）
        mockMvc.perform(post("/like/v1/getlikecount")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target_type\":\"article\",\"target_ids\":[\"1\"]} xx"))
                .andExpect(status().isOk());

        ArgumentCaptor<GetLikeCountReq> captor = ArgumentCaptor.forClass(GetLikeCountReq.class);
        verify(stub).getLikeCount(captor.capture());
        assertEquals("article", captor.getValue().getTargetType(),
                "必须取**第一个** JSON 对象的值 —— 说明尾随 token 未被拒绝");
        assertEquals(List.of("1"), captor.getValue().getTargetIdsList());
    }

    @Test
    @DisplayName("🔴 连续两个 JSON 对象也只取第一个（Go B22 同款）")
    void secondJsonObjectIsIgnored() throws Exception {
        stubGetLikeCount();

        mockMvc.perform(post("/like/v1/getlikecount")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target_type\":\"article\",\"target_ids\":[\"1\"]}"
                                + "{\"target_type\":\"video\",\"target_ids\":[\"2\"]}"))
                .andExpect(status().isOk());

        ArgumentCaptor<GetLikeCountReq> captor = ArgumentCaptor.forClass(GetLikeCountReq.class);
        verify(stub).getLikeCount(captor.capture());
        assertEquals("article", captor.getValue().getTargetType(),
                "必须取第一个对象；第二个对象必须被忽略（Go 只解第一个 JSON 值）");
    }

    // ==================== DRIFT-LIKEAPI-013：target_ids 的 null 元素 ====================

    @Test
    @DisplayName("🔴 target_ids 全是 null ⇒ 发出**空**列表的 RPC（Go slice 保持 nil），不能崩在 addAllTargetIds")
    void allNullTargetIdsBecomeEmptyList() throws Exception {
        stubGetLikeCount();

        // 改前：NPE ⇒ 被兜成 200+5001，**下游根本没被调用**
        mockMvc.perform(post("/like/v1/getlikecount")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target_type\":\"article\",\"target_ids\":[null]}"))
                .andExpect(status().isOk());

        ArgumentCaptor<GetLikeCountReq> captor = ArgumentCaptor.forClass(GetLikeCountReq.class);
        verify(stub).getLikeCount(captor.capture());
        assertTrue(captor.getValue().getTargetIdsList().isEmpty(),
                "Go 侧 valid 标志从未置位 ⇒ slice 保持 nil ⇒ 发出空列表");
    }

    @Test
    @DisplayName("🔴 target_ids 部分 null ⇒ 该位变空串且**长度不变**（Go 的 continue 不是过滤）")
    void partialNullTargetIdsKeepLengthAndFillEmptyString() throws Exception {
        stubGetLikeCount();

        mockMvc.perform(post("/like/v1/getlikecount")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target_type\":\"article\",\"target_ids\":[\"x\",null,\"y\"]}"))
                .andExpect(status().isOk());

        ArgumentCaptor<GetLikeCountReq> captor = ArgumentCaptor.forClass(GetLikeCountReq.class);
        verify(stub).getLikeCount(captor.capture());
        assertEquals(List.of("x", "", "y"), captor.getValue().getTargetIdsList(),
                "长度必须是 3：过滤掉 null 会让分页的 limit/offset 全部错位");
    }

    // ==================== 013 的纯函数侧（不依赖容器） ====================

    @Test
    @DisplayName("GoStyleTargetIds：缺席 / 空 / 全 null / 部分 null 四种形态逐条对齐 Go")
    void normalizeCoversAllFourShapes() {
        assertEquals(List.of(), GoStyleTargetIds.normalize(null), "缺席 ⇒ 空");
        assertEquals(List.of(), GoStyleTargetIds.normalize(List.of()), "空数组 ⇒ 空");

        List<String> allNull = java.util.Arrays.asList(null, null);
        assertEquals(List.of(), GoStyleTargetIds.normalize(allNull), "全 null ⇒ 空（Go slice 保持 nil）");

        List<String> mixed = java.util.Arrays.asList("a", null);
        assertEquals(List.of("a", ""), GoStyleTargetIds.normalize(mixed), "部分 null ⇒ 空串占位，长度不变");

        // ⚠️ 单元素 null 列表不能用 Arrays.asList(null) 构造：
        //    那个 null 会被当成 varargs **数组本身** ⇒ Arrays$ArrayList 里 requireNonNull NPE。
        //    Collections.singletonList 允许 null 元素，才是这里要的语义。
        assertTrue(GoStyleTargetIds.normalize(java.util.Collections.singletonList(null)).isEmpty(),
                "单元素 null ⇒ 等价于空列表（Go slice 保持 nil）");
    }

    // ==================== DRIFT-LIKEAPI-012：target_ids 里的 JSON 数字 ====================

    /**
     * Go 侧的完整因果链（均已读到消费点，见 {@code GoStyleRawStringListDeserializer} 的类注释）：
     * {@code jsonx/json.go:74-77 UseNumber()} ⇒ 数字是 {@code json.Number}（实现 {@code fmt.Stringer}）
     * ⇒ {@code unmarshaler.go:228 case fmt.Stringer} ⇒ {@code utils.go case reflect.String: return str, nil}（原样）。
     */
    @Test
    @DisplayName("🔴 target_ids 里放 JSON 数字必须被接受并原样字符串化（Go B05/B06/B28）")
    void jsonNumbersInTargetIdsAreAcceptedAsTheirLiteralText() throws Exception {
        // 三个用例与 Go 活体探针逐条对应
        assertIdsForLiteral(1, "1");
        assertIdsForLiteral(2, "2");
        assertIdsForLiteral(5, "1.5");
        assertIdsForLiteral(28, "99999999999999999999");
    }

    private void assertIdsForLiteral(int seq, String literal) throws Exception {
        stubGetLikeCount();
        org.mockito.Mockito.clearInvocations(stub);

        mockMvc.perform(post("/like/v1/getlikecount")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target_type\":\"article\",\"target_ids\":[" + literal + "]}"))
                .andExpect(status().isOk());

        ArgumentCaptor<GetLikeCountReq> captor = ArgumentCaptor.forClass(GetLikeCountReq.class);
        verify(stub).getLikeCount(captor.capture());
        assertEquals(List.of(literal), captor.getValue().getTargetIdsList(),
                "JSON 字面量 " + literal + " 必须按**原字面量**字符串化 —— "
                        + "走数值再拼回字符串会把 1e3 变成 1000.0、1.50 变成 1.5，与 Go 的 json.Number 不同");
        // 序号仅用于让失败用例可定位
        assertTrue(seq > 0);
    }

    @Test
    @DisplayName("🔴 科学计数法也必须保真（Go json.Number 原样，Jackson 默认会变成 1000.0）")
    void scientificNotationKeepsLiteralForm() throws Exception {
        stubGetLikeCount();
        mockMvc.perform(post("/like/v1/getlikecount")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target_type\":\"article\",\"target_ids\":[1e3]}"))
                .andExpect(status().isOk());

        ArgumentCaptor<GetLikeCountReq> captor = ArgumentCaptor.forClass(GetLikeCountReq.class);
        verify(stub).getLikeCount(captor.capture());
        assertEquals(List.of("1e3"), captor.getValue().getTargetIdsList(),
                "Go 侧是 json.Number(\"1e3\").String() = \"1e3\"；"
                        + "⚠️ BigDecimal 也不行 —— new BigDecimal(\"1e3\").toString() 是 \"1E+3\"");
    }

    @Test
    @DisplayName("🔴 target_ids 里放 bool 必须被拒（Go unmarshaler.go:255-261 ⇒ errTypeMismatch）")
    void booleanElementIsRejected() throws Exception {
        mockMvc.perform(post("/like/v1/getlikecount")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target_type\":\"article\",\"target_ids\":[true]}"))
                .andExpect(status().isOk());

        // 关键：下游 RPC **没有被调用**（Go 侧同样在解析阶段就失败）
        verify(stub, org.mockito.Mockito.never()).getLikeCount(any());
    }

    // ==================== DRIFT-LIKEAPI-016：weight 越出 f64 值域 ====================

    @Test
    @DisplayName("🔴 weight:1e400 必须解析失败且**不发 RPC**（Go json.Number.Float64() ⇒ ErrRange）")
    void weightOutOfF64RangeIsRejectedAndNoRpcIsSent() throws Exception {
        mockMvc.perform(post("/like/v1/likeaction")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target_type\":\"article\",\"target_id\":\"1\","
                                + "\"action_type\":1,\"author_id\":1,\"weight\":1e400}"))
                .andExpect(status().isOk());

        // Go: unmarshaler.go:684-687 → v.Float64() 返回 (+Inf, ErrRange) ⇒ return err ⇒ 解析失败
        verify(stub, org.mockito.Mockito.never()).likeAction(any());
    }

    @Test
    @DisplayName("🔴 weight:-1e400 同样被拒（负向溢出同路径）")
    void negativeWeightOutOfRangeIsRejected() throws Exception {
        mockMvc.perform(post("/like/v1/likeaction")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target_type\":\"article\",\"target_id\":\"1\","
                                + "\"action_type\":1,\"author_id\":1,\"weight\":-1e400}"))
                .andExpect(status().isOk());
        verify(stub, org.mockito.Mockito.never()).likeAction(any());
    }

    @Test
    @DisplayName("⚠️ weight:1e39 必须被**接受**（f64 合法；weight 是 float64，不触发 f32 的 OverflowFloat）")
    void largeButValidWeightIsAccepted() throws Exception {
        when(stub.likeAction(any())).thenReturn(
                com.rag.like.like.proto.LikeActionResp.newBuilder().setSuccess(true).build());

        mockMvc.perform(post("/like/v1/likeaction")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target_type\":\"article\",\"target_id\":\"1\","
                                + "\"action_type\":1,\"author_id\":1,\"weight\":1e39}"))
                .andExpect(status().isOk());

        // 这条是**防过度收紧**：若把 f32 的溢出规则也搬过来，1e39 会被误拒
        verify(stub).likeAction(any());
    }

    @Test
    @DisplayName("🔴 weight 传字符串必须被拒（String → 浮点字段 type mismatch）")
    void weightAsStringIsRejected() throws Exception {
        mockMvc.perform(post("/like/v1/likeaction")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target_type\":\"article\",\"target_id\":\"1\","
                                + "\"action_type\":1,\"author_id\":1,\"weight\":\"1.5\"}"))
                .andExpect(status().isOk());
        verify(stub, org.mockito.Mockito.never()).likeAction(any());
    }

    // ==================== 018：只改注释，不加断言 ====================

    /*
     * ⚠️ 这里**刻意不给** `observability.http-timeout` 加运行期断言。
     *
     * DRIFT-LIKEAPI-018 是**纯文档缺陷**（`LikeApiApplication` 的类 javadoc 曾宣称
     * 「刻意不配 http-timeout」，而 `application.yml:27-29` 早就配了 3s）。
     * 修复方式是**改注释**，不需要新测试。
     *
     * ⚠️ 而「3s 超时真的会在运行期产出 503」这件事，台账 §9 U-03 已明确记为
     * **未验证**（需要「真 Tomcat + 慢下游」才能实测，本轮不具备）。
     * ⇒ 在此写一条 `assertEquals("3s", …)` 之类的恒真断言，就是**主动制造一条假绿测试**
     *   来给未来的人「已经验过了」的错觉 —— 这正是 AGENTS.md §3.3.3 点名的反模式。
     * ⇒ 等真能做端到端慢下游对拍时，再补一条真断言。
     */
}