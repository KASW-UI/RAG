package com.rag.task.task.api.service;

import com.rag.task.task.api.client.TaskRpcClient;
import com.rag.task.task.api.dto.GetTaskReq;
import com.rag.task.task.api.dto.GetTaskResp;
import com.rag.task.task.api.dto.TaskInfo;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link TaskGetService} —— 对应 Go {@code api/internal/logic/task/gettasklogic.go:30-56}。
 *
 * <h2>本类验证的语义（不是风格）</h2>
 * <ul>
 *   <li><b>投影逐字段 1:1</b>：5 个字段不做任何换算 / 封顶 / 过滤；</li>
 *   <li><b>空列表仍是数组</b>：Go 的 {@code make([]types.TaskInfo, 0, n)}（{@code :43}）
 *       产出 {@code "tasks":[]} 而不是 {@code null}；</li>
 *   <li><b>uid 非法时不发 RPC</b>：Go 在 {@code :31-34} 就 return，根本没走到 {@code :36}；</li>
 *   <li><b>gRPC 错误原样透传</b>：Go 的 {@code :39-41} 是 {@code return nil, err}，
 *       本类<b>不</b>包成别的异常类型 —— 包了就断掉
 *       {@code errcode.CodeFromGrpcError} 的状态码映射链。</li>
 * </ul>
 *
 * <p>gRPC 边界用 mock：被测语义全在 api 进程内（Go 侧该 logic 也只用 task-rpc 的返回值）。
 */
class TaskGetServiceTest {

    private final TaskRpcClient taskRpcClient = mock(TaskRpcClient.class);
    private final TaskGetService service = new TaskGetService(taskRpcClient);

    private static GetTaskReq req(String id) {
        GetTaskReq r = new GetTaskReq();
        r.setId(id);
        return r;
    }

    @Test
    @DisplayName("Go :44-52 —— proto 5 个字段逐个透传，不做任何换算")
    void projectionIsFieldByField() {
        when(taskRpcClient.getTask(anyLong())).thenReturn(
                com.rag.task.task.proto.GetTaskResp.newBuilder()
                        .addTask(com.rag.task.task.proto.Task.newBuilder()
                                .setName("文章获赞任务")
                                .setDesc("有一篇文章点赞大于3")
                                .setTaskId(190L)
                                .setCompletionProgress(2L)
                                .setRequiredProgress(3L)
                                .build())
                        .build());

        GetTaskResp resp = service.getTask(req("123"));

        assertEquals(1, resp.getTasks().size());
        TaskInfo t = resp.getTasks().get(0);
        assertEquals("文章获赞任务", t.getName());
        assertEquals("有一篇文章点赞大于3", t.getDesc());
        assertEquals(190L, t.getTaskId());
        assertEquals(2L, t.getCompletionProgress());
        assertEquals(3L, t.getRequiredProgress());
    }

    @Test
    @DisplayName("Go :43 —— rpc 返回空列表时输出空数组，绝不是 null")
    void emptyListStaysEmptyArray() {
        when(taskRpcClient.getTask(anyLong())).thenReturn(
                com.rag.task.task.proto.GetTaskResp.newBuilder().build());

        GetTaskResp resp = service.getTask(req("123"));

        assertNotNull(resp.getTasks(), "\"tasks\" 不能为 null（Go make(..., 0, n)）");
        assertTrue(resp.getTasks().isEmpty());
    }

    @Test
    @DisplayName("Go :31-34 —— uid 非法时返回 user_id invalid，且**不调用** rpc")
    void invalidUidShortCircuitsBeforeRpc() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.getTask(req("0")));
        assertEquals(GetTaskReq.USER_ID_INVALID, e.getMessage());

        verify(taskRpcClient, never()).getTask(anyLong());
    }

    @Test
    @DisplayName("Go :39-41 —— gRPC 错误**原样透传**，不被包装（包装会断掉状态码映射链）")
    void grpcErrorPropagatesUnwrapped() {
        StatusRuntimeException thrown = Status.DEADLINE_EXCEEDED
                .withDescription("context deadline exceeded")
                .asRuntimeException();
        when(taskRpcClient.getTask(anyLong())).thenThrow(thrown);

        StatusRuntimeException caught = assertThrows(StatusRuntimeException.class,
                () -> service.getTask(req("123")));

        assertSame(thrown, caught, "必须是同一个异常实例（Go 是 return nil, err，不是重新构造）");
    }

    @Test
    @DisplayName("Go :44 —— rpc 返回 nil 时 Go 会在 range 处 panic 并被 Recover 兜成 400")
    void nilRpcResponseIsRejectedNotSilentlyEmptied() {
        when(taskRpcClient.getTask(anyLong())).thenReturn(null);

        // ⚠️ 这里刻意**不**当成「空任务列表」返回：Go 侧对 nil *GetTaskResp 取字段会 panic，
        //   panic 被 go-zero Recover 接住后同样走 httpx.ErrorCtx ⇒ 400。
        //   悄悄返回空数组会把一个失败变成 200，那是凭空新增的「成功」。
        assertThrows(IllegalStateException.class, () -> service.getTask(req("123")));
    }

    @Test
    @DisplayName("Go :36 —— uid 以 proto int64 原样传给下游")
    void uidIsForwardedAsInt64() {
        when(taskRpcClient.getTask(anyLong())).thenReturn(
                com.rag.task.task.proto.GetTaskResp.newBuilder().build());

        service.getTask(req("9007199254740993")); // > 2^53，验证没走 double

        verify(taskRpcClient).getTask(9007199254740993L);
    }

    @Test
    @DisplayName("Go :44-51 —— rpc 的多条 task 按原顺序输出（不做排序/去重）")
    void orderIsPreservedAndDuplicatesKept() {
        when(taskRpcClient.getTask(anyLong())).thenReturn(
                com.rag.task.task.proto.GetTaskResp.newBuilder()
                        .addTask(com.rag.task.task.proto.Task.newBuilder().setTaskId(1902L).setName("b").build())
                        .addTask(com.rag.task.task.proto.Task.newBuilder().setTaskId(190L).setName("a").build())
                        .addTask(com.rag.task.task.proto.Task.newBuilder().setTaskId(190L).setName("a2").build())
                        .build());

        List<TaskInfo> tasks = service.getTask(req("1")).getTasks();

        assertEquals(List.of("b", "a", "a2"), tasks.stream().map(TaskInfo::getName).toList());
    }
}