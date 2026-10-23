package com.rag.comment.comment.api;

import com.rag.comment.comment.api.client.CommentRpcClient;
import com.rag.comment.comment.proto.CommentServiceGrpc.CommentServiceBlockingStub;
import com.rag.comment.comment.proto.DeleteCommentResp;
import com.rag.comment.comment.proto.GetCommentResp;
import com.rag.comment.comment.proto.ManageCommentReq;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * comment-api L2/L3 业务语义测试 —— 锁住 Go 7 个 logic 各自的分支与映射。
 *
 * <p>本类专门覆盖那些「不读 Go 源码就会自然写成错」的点：
 * <ol>
 *   <li>{@code owner_id} 恒 0（Go 丢弃了 proto 里的该字段）</li>
 *   <li>{@code children} / {@code comment} 恒为 {@code []} 而非 {@code null}</li>
 *   <li>{@code DeleteComment.success} 是<b>字面量 true</b>，不取 rpc 值</li>
 *   <li>6 个端点的下游错误 → <b>gRPC 标准码原值</b>（5），而
 *       {@code GetComment} 的下游错误 → <b>5001</b>（两个端点契约不同）</li>
 *   <li>枚举字段按 open enum 原样透传（{@code action_type=99} 不被改写成 0）</li>
 *   <li>GetComment 的五处入参兜底</li>
 * </ol>
 */
@SpringBootTest(
        classes = CommentApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "grpc.client.comment-rpc.address=static://127.0.0.1:65535",
                "grpc.client.comment-rpc.negotiation-type=plaintext",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN"
        })
@AutoConfigureMockMvc
class CommentApiBehaviorTest {

    private static final String USER_SECRET = "ae0536f9-6450-4606-8e13-5a19ed505da0";
    private static final long EXPIRE_SEC = 3600L;
    private static final long CLAIM_UID = 31337L;

    /** {@code ERROR_SERVER_COMMON} = 5001。 */
    private static final int ERROR_SERVER_COMMON = 5001;

    private static final String CREATE_BODY = """
            {"target_type":"article","target_id":"BV1","root_id":0,
             "parent_id":0,"content":"hi","meta":""}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CommentRpcClient rpcClient;

    private CommentServiceBlockingStub stub;

    @BeforeEach
    void setUp() {
        stub = Mockito.mock(CommentServiceBlockingStub.class);
        Mockito.when(rpcClient.commentRpc()).thenReturn(stub);
    }

    private static String userToken() {
        return JwtUtil.getToken(USER_SECRET, System.currentTimeMillis() / 1000L, EXPIRE_SEC, CLAIM_UID);
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder postJson(
            String path, String body) throws Exception {
        return post(path)
                .header("Authorization", "Bearer " + userToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    // ==================================================================
    // 1. 成功信封：{code:200, msg:"OK", data:{...}}
    // ==================================================================

    @Test
    @DisplayName("成功路径恒为 HTTP 200 + {code:200,msg:\"OK\",data:...}")
    void successEnvelopeIs200WithOkCode() throws Exception {
        when(stub.createComment(any()))
                .thenReturn(com.rag.comment.comment.proto.CreateCommentResp.newBuilder()
                        .setId(42L).setCreatedAt("2026-10-23T00:00:00Z").setSubjectCount(7L)
                        .build());

        mockMvc.perform(postJson("/comment/v1/comment/create", CREATE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.msg").value("OK"))
                .andExpect(jsonPath("$.data.id").value(42))
                .andExpect(jsonPath("$.data.subject_count").value(7));
    }

    // ==================================================================
    // 2. owner_id 恒 0 + children/comment 恒为 []
    // ==================================================================

    @Test
    @DisplayName("owner_id 恒为 0 —— 即使 rpc 返回 999（Go 丢弃了 proto 的 owner_id）")
    void ownerIdIsAlwaysZero() throws Exception {
        when(stub.getComment(any()))
                .thenReturn(GetCommentResp.newBuilder()
                        .setSubject(com.rag.comment.comment.proto.SubjectInfo.newBuilder()
                                .setTargetType("article")
                                .setTargetId("BV1")
                                .setTotalCount(11L)
                                .setRootCount(5L)
                                .setOwnerId(999L)          // ← Go 侧这个值会被丢弃
                                .build())
                        .build());

        mockMvc.perform(postJson("/comment/v1/comment/list", """
                {"target_type":"article","target_id":"BV1","sort_type":0,
                 "root_id":0,"page":1,"page_size":20}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.subject.total_count").value(11))
                // ⚠️ Go getcommentlogic.go:91 是字面量 OwnerId: 0
                .andExpect(jsonPath("$.data.subject.owner_id").value(0));
    }

    @Test
    @DisplayName("空结果时 comment 恒为 []，且每层 children 恒为 []（绝不是 null）")
    void emptyCollectionsAreNeverNull() throws Exception {
        when(stub.getComment(any()))
                .thenReturn(GetCommentResp.newBuilder()
                        .addComment(com.rag.comment.comment.proto.CommentItem.newBuilder()
                                .setId(1L).setContent("root").build())
                        .build());

        mockMvc.perform(postJson("/comment/v1/comment/list", """
                {"target_type":"article","target_id":"BV1","sort_type":0,
                 "root_id":0,"page":1,"page_size":20}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.comment").isArray())
                .andExpect(jsonPath("$.data.comment.length()").value(1))
                // 叶子节点没有子评论，仍输出 []
                .andExpect(jsonPath("$.data.comment[0].children").isArray())
                .andExpect(jsonPath("$.data.comment[0].children.length()").value(0));
    }

    @Test
    @DisplayName("rpc 未返回 subject 时输出全零对象，不是 null、也不被省略")
    void absentSubjectIsZeroObjectNotNull() throws Exception {
        when(stub.getComment(any())).thenReturn(GetCommentResp.newBuilder().build());

        mockMvc.perform(postJson("/comment/v1/comment/list", """
                {"target_type":"article","target_id":"BV1","sort_type":0,
                 "root_id":0,"page":1,"page_size":20}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subject").exists())
                .andExpect(jsonPath("$.data.subject.owner_id").value(0))
                .andExpect(jsonPath("$.data.comment.length()").value(0));
    }

    // ==================================================================
    // 3. 两个端点下游错误契约不同（Go 侧真实存在的差异）
    // ==================================================================

    @Test
    @DisplayName("CreateComment 下游 NotFound → {code:5}（gRPC 标准码原值，不是 1013/5001）")
    void createMapsGrpcCodeVerbatim() throws Exception {
        when(stub.createComment(any()))
                .thenThrow(Status.NOT_FOUND.withDescription("comment not exist").asRuntimeException());

        mockMvc.perform(postJson("/comment/v1/comment/create", CREATE_BODY))
                .andExpect(status().isOk())
                // int(st.Code()) = codes.NotFound = 5
                .andExpect(jsonPath("$.code").value(5))
                .andExpect(jsonPath("$.msg").value("comment not exist"));
    }

    @Test
    @DisplayName("GetComment 下游 NotFound → {code:5001}（Go 裸 return err，被兜底）")
    void getCommentFallsBackTo5001() throws Exception {
        when(stub.getComment(any()))
                .thenThrow(Status.NOT_FOUND.withDescription("subject not exist").asRuntimeException());

        mockMvc.perform(postJson("/comment/v1/comment/list", """
                {"target_type":"article","target_id":"BV1","sort_type":0,
                 "root_id":0,"page":1,"page_size":20}"""))
                .andExpect(status().isOk())
                // ⚠️ 与上一条形成对照：同一个 gRPC code，两个端点的业务码不同。
                //    照抄「统一模板」把这里改成 5 就会破坏 Go 行为。
                .andExpect(jsonPath("$.code").value(ERROR_SERVER_COMMON))
                .andExpect(jsonPath("$.msg").value("系统内部错误"));
    }

    @Test
    @DisplayName("GetComment 入参校验失败 → 5001（不是 InvalidArgument=3，也拿不到报错文案）")
    void blankTargetTypeFallsBackTo5001() throws Exception {
        mockMvc.perform(postJson("/comment/v1/comment/list", """
                {"target_type":"   ","target_id":"BV1","sort_type":0,
                 "root_id":0,"page":1,"page_size":20}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ERROR_SERVER_COMMON))
                .andExpect(jsonPath("$.msg").value("系统内部错误"));
    }

    // ==================================================================
    // 4. DeleteComment.success 是字面量 true
    // ==================================================================

    @Test
    @DisplayName("DeleteComment 恒返回 success=true，即使 rpc 说 false（Go 是字面量）")
    void deleteSuccessIsAlwaysTrue() throws Exception {
        when(stub.deleteComment(any()))
                .thenReturn(DeleteCommentResp.newBuilder()
                        .setSuccess(false)          // ← Go 不读这个值
                        .setSubjectTotalCount(3L)
                        .build());

        mockMvc.perform(postJson("/comment/v1/comment/delete", """
                {"comment_id":9,"target_type":"article","target_id":"BV1"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.success").value(true))
                .andExpect(jsonPath("$.data.subject_total_count").value(3));
    }

    // ==================================================================
    // 5. open enum 原样透传
    // ==================================================================

    @Test
    @DisplayName("action_type=99 原样透传给 rpc（用 forNumber 会被静默改写成 0）")
    void unknownEnumValueIsPassedThrough() throws Exception {
        when(stub.manageComment(any()))
                .thenReturn(com.rag.comment.comment.proto.ManageCommentResp.newBuilder()
                        .setSuccess(true).build());

        mockMvc.perform(postJson("/comment/v1/comment/manage", """
                {"comment_id":9,"target_type":"article","target_id":"BV1","action_type":99}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<ManageCommentReq> captor = ArgumentCaptor.forClass(ManageCommentReq.class);
        verify(stub).manageComment(captor.capture());
        assertEquals(99, captor.getValue().getActionTypeValue(),
                "未知枚举值必须原样透传，不能退化成 0(UNKNOWN)");
    }

    // ==================================================================
    // 6. GetComment 的五处入参兜底
    // ==================================================================

    @Test
    @DisplayName("入参兜底：page=0→1、pageSize=100→50、sortType=5→0、rootId=-1→0")
    void paginationFallbacksAreApplied() throws Exception {
        when(stub.getComment(any())).thenReturn(GetCommentResp.newBuilder().build());

        mockMvc.perform(postJson("/comment/v1/comment/list", """
                {"target_type":"article","target_id":"BV1","sort_type":5,
                 "root_id":-1,"page":0,"page_size":100}"""))
                .andExpect(status().isOk());

        ArgumentCaptor<com.rag.comment.comment.proto.GetCommentReq> captor =
                ArgumentCaptor.forClass(com.rag.comment.comment.proto.GetCommentReq.class);
        verify(stub).getComment(captor.capture());
        var sent = captor.getValue();
        assertEquals(1L, sent.getPage(), "page<=0 兜底成 1");
        assertEquals(50L, sent.getPageSize(), "pageSize>50 截断到 50");
        assertEquals(0L, sent.getSortType(), "sortType ∉ {0,1} 归 0");
        assertEquals(0L, sent.getRootId(), "rootId<0 归 0");
    }

    @Test
    @DisplayName("校验只判空、不回写 trim 结果（带空格的 target_type 原样下发）")
    void targetTypeIsNotTrimmedBeforeRpc() throws Exception {
        when(stub.getComment(any())).thenReturn(GetCommentResp.newBuilder().build());

        mockMvc.perform(postJson("/comment/v1/comment/list", """
                {"target_type":" article ","target_id":"BV1","sort_type":1,
                 "root_id":0,"page":2,"page_size":10}"""))
                .andExpect(status().isOk());

        ArgumentCaptor<com.rag.comment.comment.proto.GetCommentReq> captor =
                ArgumentCaptor.forClass(com.rag.comment.comment.proto.GetCommentReq.class);
        verify(stub).getComment(captor.capture());
        assertEquals(" article ", captor.getValue().getTargetType(),
                "Go 只用 TrimSpace 判空，没有 trim 后的赋值 ⇒ 原值下发");
    }

    // ==================================================================
    // 7. user_id 来自 token，不来自 body
    // ==================================================================

    @Test
    @DisplayName("user_id 取自 token claim 并下发 rpc；body 无法指定操作人")
    void userIdComesFromTokenNotBody() throws Exception {
        when(stub.deleteComment(any()))
                .thenReturn(DeleteCommentResp.newBuilder().setSubjectTotalCount(1L).build());

        // body 里故意塞一个 user_id 字段：Go 的 types.go 没有该字段，
        // Jackson 默认忽略未知属性，不应影响真实身份。
        mockMvc.perform(postJson("/comment/v1/comment/delete", """
                {"comment_id":9,"target_type":"article","target_id":"BV1","user_id":666}"""))
                .andExpect(status().isOk());

        ArgumentCaptor<com.rag.comment.comment.proto.DeleteCommentReq> captor =
                ArgumentCaptor.forClass(com.rag.comment.comment.proto.DeleteCommentReq.class);
        verify(stub).deleteComment(captor.capture());
        assertEquals(CLAIM_UID, captor.getValue().getUserId(),
                "操作人必须是 token 里的身份，不能被 body 里的同名字段覆盖");
    }
}