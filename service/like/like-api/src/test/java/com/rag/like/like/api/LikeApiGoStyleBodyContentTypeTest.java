package com.rag.like.like.api;

import com.rag.like.like.api.client.LikeRpcClient;
import com.rag.like.like.proto.LikeServiceGrpc.LikeServiceBlockingStub;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * **非 JSON Content-Type 的请求体判据**（DRIFT-LIKEAPI-006）—— 对齐 Go 的 {@code withJsonBody}。
 *
 * <h2>⚠️ 本类要证明<b>不是</b>「415 消失了」，而是「body 被丢弃了」</h2>
 *
 * <p>Go {@code rest/httpx/requests.go:153-155}：
 * <pre>
 *   func withJsonBody(r *http.Request) bool {
 *       return r.ContentLength &gt; 0 &amp;&amp; strings.Contains(r.Header.Get("Content-Type"), "application/json")
 *   }
 * // 不满足时：ParseJsonBody → mapping.UnmarshalJsonMap(nil, v)   ← **完全不看 body**
 * </pre>
 * ⇒ 最关键的一条是 {@link #nonJsonBodyIsIgnoredEvenWhenItIsValidJson()}：
 * 一个<b>字段齐全、完全合法</b>的 JSON body，只要 Content-Type 不是 JSON，
 * Go 就会把它<b>整个丢掉</b> ⇒ 必填字段缺失 ⇒ <b>200 + 5001</b>。
 * <p>若有人把实现写成「放宽 content type 后照常解析 body」，那条测试立刻红
 * —— 那会把 Go 明确拒绝的请求变成成功，<b>比 415 更糟</b>（它看起来像"修好了"）。
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
class LikeApiGoStyleBodyContentTypeTest {

    private static final String SECRET = "ae0536f9-6450-4606-8e13-5a19ed505da0";
    private static final long EXPIRE_SEC = 3600L;
    private static final long CLAIM_UID = 31337L;
    private static final String COUNT_PATH = "/like/v1/getlikecount";
    private static final String TOTAL_PATH = "/like/v1/getusertotallike";

    /** 字段齐全、完全合法的 JSON —— 用它证明「body 被丢弃」而不是「body 被拒绝」。 */
    private static final String VALID_JSON =
            "{\"target_type\":\"article\",\"target_ids\":[\"1\",\"2\"]}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LikeRpcClient likeRpcClient;

    private static String token() {
        return com.rag.users.common.jwt.JwtUtil.getToken(
                SECRET, System.currentTimeMillis() / 1000L, EXPIRE_SEC, CLAIM_UID);
    }

    @Test
    @DisplayName("非 JSON Content-Type + **合法完整** body ⇒ 200 + 5001（body 被丢弃，不是被解析）")
    void nonJsonBodyIsIgnoredEvenWhenItIsValidJson() throws Exception {
        LikeServiceBlockingStub stub = mock(LikeServiceBlockingStub.class);
        when(likeRpcClient.likeRpc()).thenReturn(stub);

        mockMvc.perform(post(COUNT_PATH)
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.TEXT_PLAIN)          // 不是 application/json
                        .content(VALID_JSON))                        // 但是完全合法、字段齐全
                .andExpect(status().isOk())                          // Go 的 SetErrorHandler 恒 200
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(5001));

        verify(stub, never()).getLikeCount(any());
    }

    @Test
    @DisplayName("零必填 DTO + 非 JSON Content-Type ⇒ **成功**（Go 不拒绝，RAG 也不该拒绝）")
    void zeroRequiredFieldDtoSucceedsWithNonJsonContentType() throws Exception {
        // GetUserTotalLikeReq 是空结构体 ⇒ Go 的 UnmarshalJsonMap(nil, v) 不会失败。
        // ⚠️ 这条正是原签字理由「两侧都是拒绝」不完整的那个反例。
        LikeServiceBlockingStub stub = mock(LikeServiceBlockingStub.class);
        when(likeRpcClient.likeRpc()).thenReturn(stub);
        when(stub.getUserTotalLike(any())).thenReturn(
                com.rag.like.like.proto.GetUserTotalLikeResp.getDefaultInstance());

        mockMvc.perform(post(TOTAL_PATH)
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("{\"anything\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<com.rag.like.like.proto.GetUserTotalLikeReq> captor =
                ArgumentCaptor.forClass(com.rag.like.like.proto.GetUserTotalLikeReq.class);
        verify(stub).getUserTotalLike(captor.capture());
        assertNotNull(captor.getValue(), "必须真的调到下游：Go 在零必填 DTO 上是成功的");
    }

    @Test
    @DisplayName("非 JSON + **无 token** ⇒ 仍是 401（鉴权先于 body 判据，与 Go chain 顺序一致）")
    void authStillRunsBeforeBodyDecision() throws Exception {
        mockMvc.perform(post(COUNT_PATH)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(VALID_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("正常 JSON Content-Type 路径**不受影响**（回归网：这条最容易被上面三条改坏）")
    void normalJsonPathIsUnchanged() throws Exception {
        LikeServiceBlockingStub stub = mock(LikeServiceBlockingStub.class);
        when(likeRpcClient.likeRpc()).thenReturn(stub);
        when(stub.getLikeCount(any())).thenReturn(
                com.rag.like.like.proto.GetLikeCountResp.getDefaultInstance());

        mockMvc.perform(post(COUNT_PATH)
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(stub).getLikeCount(any());          // body 真的被解析了 ⇒ 调到了下游
    }

    @Test
    @DisplayName("application/*+json 在 Go 侧**同样不算** JSON（字面子串匹配，不是媒体类型兼容判定）")
    void vendorJsonSuffixIsNotApplicationJson() throws Exception {
        // Go 用 strings.Contains(ct, "application/json")；
        // "application/vnd.api+json" **不包含**该子串 ⇒ body 被丢弃 ⇒ 5001。
        // ⚠️ Spring 默认把 application/*+json 当 JSON 正常解析 —— 这是同一条判据的另一处漂移。
        LikeServiceBlockingStub stub = mock(LikeServiceBlockingStub.class);
        when(likeRpcClient.likeRpc()).thenReturn(stub);

        mockMvc.perform(post(COUNT_PATH)
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.parseMediaType("application/vnd.api+json"))
                        .content(VALID_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5001));

        verify(stub, never()).getLikeCount(any());
    }

    @Test
    @DisplayName("无 Content-Type + 合法 body ⇒ 同样丢弃（Go: Header.Get 返回空串，contains 为假）")
    void absentContentTypeAlsoDiscardsBody() throws Exception {
        LikeServiceBlockingStub stub = mock(LikeServiceBlockingStub.class);
        when(likeRpcClient.likeRpc()).thenReturn(stub);

        mockMvc.perform(post(COUNT_PATH)
                        .header("Authorization", "Bearer " + token())
                        .content(VALID_JSON))          // 刻意不设 Content-Type
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5001));

        verify(stub, never()).getLikeCount(any());
    }

    @Test
    @DisplayName("健壮性：判据函数本身的边界（可直接看出哪条判据在起作用）")
    void goReadsBodyBoundaries() {
        // Go: r.ContentLength > 0 && strings.Contains(ct, "application/json")
        assertEquals(false,
                com.rag.common.http.GoStyleJsonBodyConverter.goReadsBody("application/json", 0L),
                "Content-Length 为 0 ⇒ 不读（与 MaxBytes 的 chunked 同源：Go 用 -1 表示未知）");
        assertEquals(false,
                com.rag.common.http.GoStyleJsonBodyConverter.goReadsBody("application/json", -1L),
                "chunked（-1）⇒ 不读。Go 的 r.ContentLength 对 chunked 就是 -1");
        assertEquals(false,
                com.rag.common.http.GoStyleJsonBodyConverter.goReadsBody(null, 10L),
                "缺 Content-Type ⇒ 不读");
        assertEquals(false,
                com.rag.common.http.GoStyleJsonBodyConverter.goReadsBody("text/plain", 10L),
                "非 JSON ⇒ 不读");
        assertEquals(false,
                com.rag.common.http.GoStyleJsonBodyConverter.goReadsBody("application/vnd.api+json", 10L),
                "⚠️ 字面子串匹配：+json 后缀**不含** application/json ⇒ 不读");
        assertEquals(true,
                com.rag.common.http.GoStyleJsonBodyConverter.goReadsBody("application/json", 10L),
                "正常 JSON + 有体 ⇒ 读");
        assertEquals(true,
                com.rag.common.http.GoStyleJsonBodyConverter.goReadsBody("application/json; charset=utf-8", 10L),
                "带 charset 参数时依然命中子串 ⇒ 读（Go 也是 contains，不是精确相等）");
    }
}