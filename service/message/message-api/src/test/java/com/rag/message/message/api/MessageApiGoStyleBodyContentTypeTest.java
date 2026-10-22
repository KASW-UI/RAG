package com.rag.message.message.api;

import com.rag.message.message.api.client.MessageRpcClient;
import com.rag.users.common.jwt.JwtUtil;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * **非 JSON Content-Type 的请求体判据**（message 域，与 like 域同源）。
 *
 * <h2>⚠️ 本类关闭的是<b>原签字理由不完整</b>的那条漂移</h2>
 *
 * <p>message-api 当初对「Content-Type 非 JSON ⇒ 415」给出的签字理由是
 * 「<b>两侧都是拒绝</b>、都无业务副作用」。该理由已被推翻：
 * Go {@code rest/httpx/requests.go:153-155} 的 {@code withJsonBody} 不满足时走
 * {@code mapping.UnmarshalJsonMap(nil, v)} —— <b>不是拒绝，是丢弃 body 后按空对象继续</b>。
 * ⇒ 对 {@code PageReq} / {@code EmptyReq} 这类<b>零必填</b> DTO，Go 会<b>成功</b>。
 *
 * <p>实现落在共享的 {@code com.rag.common.http.GoStyleJsonBodyConverter}；
 * 本类只守 message 域的<b>装配与端点行为</b>，判据本身的边界由 like 域那份 + 单元断言覆盖。
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
class MessageApiGoStyleBodyContentTypeTest {

    private static final String USER_SECRET = "ae0536f9-6450-4606-8e13-5a19ed505da0";
    private static final long EXPIRE_SEC = 3600L;
    private static final long CLAIM_UID = 31337L;

    private static final String LIST_PATH = "/message/v1/conversations/list";      // PageReq，零必填
    private static final String READ_PATH = "/message/v1/conversations/read";      // MarkConversationReadReq，必填

    /**
     * PageReq 的字段全是 {@code ,optional}，且<b>没有 {@code ,string}</b>
     * （Go types.go:101-105）⇒ 必须是 JSON **数字**，字符串会 type mismatch。
     * ⚠️ 这一点最初被我写成 {@code "offset":"10"}，测试红了 —— 查证后确认
     * <b>是夹具写错、两侧行为一致</b>（都返回 5001），不是实现有问题。
     */
    private static final String VALID_PAGE_JSON = "{\"offset\":10,\"limit\":20}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MessageRpcClient rpcClient;

    private static String token() {
        return JwtUtil.getToken(USER_SECRET, System.currentTimeMillis() / 1000L, EXPIRE_SEC, CLAIM_UID);
    }

    @Test
    @DisplayName("零必填 DTO + 非 JSON Content-Type ⇒ **成功**（推翻「两侧都是拒绝」的原签字）")
    void zeroRequiredDtoSucceedsWithNonJsonContentType() throws Exception {
        var stub = mock(com.rag.message.message.proto.MessageServiceGrpc.MessageServiceBlockingStub.class);
        when(rpcClient.messageRpc()).thenReturn(stub);
        when(stub.listConversations(any())).thenReturn(
                com.rag.message.message.proto.ConversationListResp.getDefaultInstance());

        mockMvc.perform(post(LIST_PATH)
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(VALID_PAGE_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<com.rag.message.message.proto.ConversationListReq> captor =
                ArgumentCaptor.forClass(com.rag.message.message.proto.ConversationListReq.class);
        verify(stub).listConversations(captor.capture());
        assertNotNull(captor.getValue());
        assertEquals(0, captor.getValue().getOffset(),
                "⭐ 关键证据：offset 必须回落到 **0**。"
                        + "若它是 10，说明 body 被解析了 —— 而 Go 侧会丢弃它。"
                        + "这正是 Go 的 mapping.UnmarshalJsonMap(nil, v) 的等价形态。");
    }

    @Test
    @DisplayName("必填 DTO + 非 JSON + **合法完整** body ⇒ 200 + 5001（body 被丢弃，不是被拒绝）")
    void requiredFieldDtoFailsBecauseBodyIsDiscarded() throws Exception {
        var stub = mock(com.rag.message.message.proto.MessageServiceGrpc.MessageServiceBlockingStub.class);
        when(rpcClient.messageRpc()).thenReturn(stub);

        mockMvc.perform(post(READ_PATH)
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("{\"conversation_id\":\"123\"}"))     // 完全合法
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5001));

        verify(stub, never()).markConversationRead(any());
    }

    @Test
    @DisplayName("非 JSON + **无 token** ⇒ 仍是 401（鉴权先于 body 判据）")
    void authStillRunsFirst() throws Exception {
        mockMvc.perform(post(LIST_PATH)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(VALID_PAGE_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("正常 JSON Content-Type 路径**不受影响**（回归网）")
    void normalJsonPathIsUnchanged() throws Exception {
        var stub = mock(com.rag.message.message.proto.MessageServiceGrpc.MessageServiceBlockingStub.class);
        when(rpcClient.messageRpc()).thenReturn(stub);
        when(stub.listConversations(any())).thenReturn(
                com.rag.message.message.proto.ConversationListResp.getDefaultInstance());

        mockMvc.perform(post(LIST_PATH)
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAGE_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<com.rag.message.message.proto.ConversationListReq> captor =
                ArgumentCaptor.forClass(com.rag.message.message.proto.ConversationListReq.class);
        verify(stub).listConversations(captor.capture());
        assertEquals(10, captor.getValue().getOffset(),
                "正常 JSON 路径必须真的解析 body（offset=10 回落到 proto 字段）");
    }
}