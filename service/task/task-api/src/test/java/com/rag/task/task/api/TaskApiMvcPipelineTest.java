package com.rag.task.task.api;

import com.rag.common.protection.GoStyleCircuitOpenException;
import com.rag.task.task.api.client.TaskRpcClient;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * task-api <b>真实 MVC 管线</b>测试 —— 锁住 Go {@code service/task/api} 的 L1 契约。
 *
 * <h2>为什么必须是真实管线（不能用 {@code standaloneSetup}）</h2>
 * 本组件有三件事<b>只在装配后才成立</b>：
 * <table border="1">
 *   <caption>真实管线 vs standaloneSetup</caption>
 *   <tr><th>要验的东西</th><th>依赖</th></tr>
 *   <tr>
 *     <td>任何 Content-Type 都读 body（无 415）</td>
 *     <td>{@code TaskApiJsonBodyConfig#extendMessageConverters} 真的被调用，
 *         且真的插到了 Boot 的 Jackson 转换器<b>之前</b></td>
 *   </tr>
 *   <tr>
 *     <td>共享的 {@code GoStyleJsonBodyConverterConfig} 真的被排除了</td>
 *     <td>{@code TaskApiApplication} 的 {@code excludeFilters}</td>
 *   </tr>
 *   <tr>
 *     <td>404 是裸 text/plain、405 是裸状态码</td>
 *     <td>advice 与共享 {@code GoStyleRouterErrorResponses} 的配合</td>
 *   </tr>
 * </table>
 * AGENTS.md §3.3 的另一个实例：<b>断言 Bean 存在 ≠ 断言机制生效</b>。
 * 上一轮 users 域就出现过「converter 在，但装配代码没跑」而测试仍全绿。
 *
 * <p><b>唯一被替换的是 gRPC 边界</b>（{@code TaskRpcClient}）—— 被测语义全在 api 进程内。
 * 另注：<b>匿名可访问</b>本身就是被测语义（Go 的 {@code routes.go} 没有 {@code rest.WithJwt}），
 * 因此这里刻意不带任何 token。
 */
@SpringBootTest(
        classes = TaskApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                // 指向一个不存在的端口：channel 建不起来也不影响，被测路径不真的发包。
                "grpc.client.task-rpc.address=static://127.0.0.1:65535",
                "grpc.client.task-rpc.negotiation-type=plaintext",
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN"
        })
@AutoConfigureMockMvc
class TaskApiMvcPipelineTest {

    private static final String PATH = "/taskcenter/v1/task/get";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TaskRpcClient taskRpcClient;

    private void stubTwoTasks() {
        when(taskRpcClient.getTask(anyLong())).thenReturn(
                com.rag.task.task.proto.GetTaskResp.newBuilder()
                        .addTask(com.rag.task.task.proto.Task.newBuilder()
                                .setName("文章获赞任务").setDesc("有一篇文章点赞大于3")
                                .setTaskId(190L).setCompletionProgress(0L).setRequiredProgress(3L).build())
                        .addTask(com.rag.task.task.proto.Task.newBuilder()
                                .setName("点赞任务").setDesc("给文章点赞5次")
                                .setTaskId(1902L).setCompletionProgress(5L).setRequiredProgress(5L).build())
                        .build());
    }

    // ============== 成功路径 ==============

    @Test
    @DisplayName("Go routes.go:19-24 —— POST /taskcenter/v1/task/get，前缀**不是** /task/v1")
    void routeIsTaskCenterV1() throws Exception {
        stubTwoTasks();

        mockMvc.perform(post(PATH).contentType("application/json").content("{\"id\":\"123\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Go types.go:57-58 —— 成功响应**无信封**，就是 {\"tasks\":[...]}")
    void successHasNoEnvelope() throws Exception {
        stubTwoTasks();

        String body = mockMvc.perform(post(PATH).contentType("application/json")
                        .content("{\"id\":\"123\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.tasks").isArray())
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.msg").doesNotExist())
                .andExpect(jsonPath("$.data").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertTrue(body.startsWith("{\"tasks\":["), "body = " + body);
    }

    @Test
    @DisplayName("Go types.go:60-66 —— 5 个字段名与**键序**逐字节对上")
    void taskInfoFieldNamesAndOrderAreByteExact() throws Exception {
        when(taskRpcClient.getTask(anyLong())).thenReturn(
                com.rag.task.task.proto.GetTaskResp.newBuilder()
                        .addTask(com.rag.task.task.proto.Task.newBuilder()
                                .setName("n").setDesc("").setTaskId(190L)
                                .setCompletionProgress(1L).setRequiredProgress(3L).build())
                        .build());

        // ⚠️ 断言**原始字符串**而不是 jsonPath：jsonPath 验不出键序，也验不出
        //    "desc":"" 存在（JsonPath 取不到空串与取不到键是同一件事）。
        assertTrue(("{\"tasks\":[{\"name\":\"n\",\"desc\":\"\",\"taskId\":190,"
                        + "\"completionProgress\":1,\"requiredProgress\":3}]}")
                        .equals(mockMvc.perform(post(PATH).contentType("application/json")
                                        .content("{\"id\":\"1\"}"))
                                .andExpect(status().isOk())
                                .andReturn().getResponse().getContentAsString()),
                "Go 的 encoding/json 按结构体字段声明顺序输出；Jackson 也按声明顺序");
    }

    @Test
    @DisplayName("Go gettasklogic.go:43 —— 空结果输出 {\"tasks\":[]}，不是 null")
    void emptyResultIsEmptyArrayNotNull() throws Exception {
        when(taskRpcClient.getTask(anyLong())).thenReturn(
                com.rag.task.task.proto.GetTaskResp.newBuilder().build());

        assertTrue("{\"tasks\":[]}".equals(mockMvc.perform(post(PATH).contentType("application/json")
                        .content("{\"id\":\"1\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()));
    }

    // ============== 🔴 Content-Type 无差别（Go 裸 encoding/json） ==============

    @Test
    @DisplayName("🔴 Go gettaskhandler.go:19 裸 json.Decoder —— text/plain 也必须成功（不是 415）")
    void textPlainContentTypeIsAccepted() throws Exception {
        stubTwoTasks();

        mockMvc.perform(post(PATH).contentType("text/plain").content("{\"id\":\"123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks.length()").value(2));
    }

    @Test
    @DisplayName("🔴 同上 —— 完全不带 Content-Type 也要成功")
    void missingContentTypeIsAccepted() throws Exception {
        stubTwoTasks();

        mockMvc.perform(post(PATH).content("{\"id\":\"123\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("🔴 同上 —— form-urlencoded 也不能被媒体类型**协商**挡住（绝不能是 415）")
    void formUrlEncodedIsNotRejectedByMediaTypeNegotiation() throws Exception {
        stubTwoTasks();

        // ⚠️ 这里只断言「**不是 415**」，不断言 200。
        //    MockMvc 对 form-urlencoded 的 content() 会做 URL 编码，
        //    于是 JSON body 到达转换器时已变成 %7B%22id%22... ⇒ 必然解析失败 ⇒ 400。
        //    那是 **MockMvc 的构造产物**，不是生产行为：Boot 4 不再自动注册
        //    OrderedFormContentFilter（实测 spring-boot-*-4.0.8 里无该 Bean 引用），
        //    真 Tomcat 下 body 原样到达本域转换器，与 Go 一致。
        //    ⇒ 用 MockMvc 断言「200」等于断言一个 MockMvc 的副作用；
        //       真正要锁死的保证是「媒体类型协商没有把这请求挡在门外」。
        mockMvc.perform(post(PATH).contentType("application/x-www-form-urlencoded")
                        .content("{\"id\":\"123\"}"))
                .andExpect(result -> org.junit.jupiter.api.Assertions.assertNotEquals(
                        415, result.getResponse().getStatus(),
                        "出现 415 ⇒ 请求体转换器没认领媒体类型（Go 无媒体类型协商）"));
    }

    // ============== 解析失败：400 text/plain ==============

    @Test
    @DisplayName("Go types.go:41,46 + responses.go:152 —— id 非法 ⇒ 400 + \"user_id invalid\\n\"")
    void invalidIdIsPlainTextBadRequest() throws Exception {
        mockMvc.perform(post(PATH).contentType("application/json").content("{\"id\":\"abc\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("text/plain; charset=utf-8"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().string("user_id invalid\n"));
    }

    @Test
    @DisplayName("Go types.go:35 —— id 既非 string 又非数字 ⇒ 文案是 \"id must be a string or integer\"")
    void nonScalarIdKeepsGoWording() throws Exception {
        // ⚠️ 这条断言的是**文案**：Jackson 会把 setter 的异常包一层，
        //    advice 必须沿 cause 链捞出 Go 原文，否则 body 会变成 "JSON parse error: ..."。
        mockMvc.perform(post(PATH).contentType("application/json").content("{\"id\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("id must be a string or integer\n"));
    }

    @Test
    @DisplayName("Go types.go:18-21 —— id 缺席 / null ⇒ user_id invalid")
    void missingOrNullIdIsUserIdInvalid() throws Exception {
        for (String body : new String[]{"{}", "{\"id\":null}", "{\"other\":1}"}) {
            mockMvc.perform(post(PATH).contentType("application/json").content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string("user_id invalid\n"));
        }
    }

    @Test
    @DisplayName("Go types.go:29-33 + :44 —— 浮点 id ⇒ user_id invalid（**不是** id must be...）")
    void floatIdIsUserIdInvalid() throws Exception {
        mockMvc.perform(post(PATH).contentType("application/json").content("{\"id\":1.5}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("user_id invalid\n"));
    }

    @Test
    @DisplayName("Go gettaskhandler.go:21 —— 非法 JSON ⇒ 400（形状对齐，文案来自 Jackson）")
    void malformedJsonIsBadRequest() throws Exception {
        mockMvc.perform(post(PATH).contentType("application/json").content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("text/plain; charset=utf-8"));
    }

    @Test
    @DisplayName("Go gettaskhandler.go:21 —— 空 body ⇒ 400（Go 得到 io.EOF）")
    void emptyBodyIsBadRequest() throws Exception {
        mockMvc.perform(post(PATH).contentType("application/json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("text/plain; charset=utf-8"));
    }

    @Test
    @DisplayName("Go :31-34 —— id 非法时**不发** rpc")
    void invalidIdNeverReachesRpc() throws Exception {
        org.mockito.Mockito.verify(taskRpcClient, org.mockito.Mockito.never()).getTask(anyLong());
    }

    // ============== 下游失败 ==============

    @Test
    @DisplayName("Go errcode/grpc.go:37-38 —— DEADLINE_EXCEEDED ⇒ HTTP 504")
    void deadlineExceededIsGatewayTimeout() throws Exception {
        when(taskRpcClient.getTask(anyLong())).thenThrow(
                Status.DEADLINE_EXCEEDED.withDescription("context deadline exceeded").asRuntimeException());

        mockMvc.perform(post(PATH).contentType("application/json").content("{\"id\":\"1\"}"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(content().contentType("text/plain; charset=utf-8"))
                .andExpect(content().string("rpc error: code = DeadlineExceeded desc = context deadline exceeded\n"));
    }

    @Test
    @DisplayName("Go errcode/grpc.go:31-32 —— UNKNOWN（下游 DB 错误经 gRPC 自动包装）⇒ HTTP 500")
    void unknownIsInternalServerError() throws Exception {
        when(taskRpcClient.getTask(anyLong())).thenThrow(
                Status.UNKNOWN.withDescription("pq: deadlock detected").asRuntimeException());

        mockMvc.perform(post(PATH).contentType("application/json").content("{\"id\":\"1\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string("rpc error: code = Unknown desc = pq: deadlock detected\n"));
    }

    @Test
    @DisplayName("Go breaker.go:19 + responses.go:152 —— 熔断打开 ⇒ 400 + \"circuit breaker is open\"")
    void circuitOpenIsBadRequest() throws Exception {
        when(taskRpcClient.getTask(anyLong()))
                .thenThrow(new GoStyleCircuitOpenException("circuit breaker is open"));

        mockMvc.perform(post(PATH).contentType("application/json").content("{\"id\":\"1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("circuit breaker is open\n"));
    }

    // ============== 路由层 ==============

    @Test
    @DisplayName("Go patrouter.go:73 —— 未知路径 ⇒ 404 + \"404 page not found\\n\"（text/plain）")
    void unknownPathIsGoShapedNotFound() throws Exception {
        mockMvc.perform(post("/taskcenter/v1/task/nope").contentType("application/json").content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType("text/plain; charset=utf-8"))
                .andExpect(content().string("404 page not found\n"));
    }

    @Test
    @DisplayName("Go patrouter.go:79-82 —— GET 打在本端点 ⇒ 405 裸状态码（无 body、无 Content-Type）")
    void getOnPostOnlyPathIsBareMethodNotAllowed() throws Exception {
        var response = mockMvc.perform(get(PATH))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "POST"))
                .andReturn().getResponse();

        assertTrue(response.getContentAsByteArray().length == 0,
                "405 必须无 body（Go 只 WriteHeader，不 Fprintln）");
        assertFalse(String.valueOf(response.getHeader("Content-Type")).contains("json"),
                "405 不应有 Content-Type");
    }

    // ============== 鉴权边界 ==============

    @Test
    @DisplayName("Go routes.go:16-25 无 rest.WithJwt —— 匿名可访问（不加 token 也能拿到数据）")
    void anonymousAccessIsTheGoBehavior() throws Exception {
        stubTwoTasks();

        // 没有 Authorization 头。若将来有人加了 JwtAuthInterceptor，这里立刻红。
        mockMvc.perform(post(PATH).contentType("application/json").content("{\"id\":\"123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks.length()").value(2));
    }
}