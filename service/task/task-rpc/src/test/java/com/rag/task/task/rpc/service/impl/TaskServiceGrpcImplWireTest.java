package com.rag.task.task.rpc.service.impl;

import com.rag.task.task.proto.GetTaskReq;
import com.rag.task.task.proto.GetTaskResp;
import com.rag.task.task.proto.TaskServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * {@code TaskService.GetTask} 的 <b>wire 层</b>测试 —— 用真实 gRPC channel 收发，
 * 断言的是「客户端真正收到的 code 与 description」。
 *
 * <h2>为什么不能只在领域层测</h2>
 * <p>Go 侧唯一对外的错误契约就是 gRPC code + description
 * （{@code status.Error(codes.InvalidArgument, "user_id invalid")}），
 * 调用方是 Go 的 {@code task-api}（{@code gettasklogic.go:39} 把 error 原样上抛 HTTP 层）。
 * 而 grpc-java 有个必须记住的坑：<b>异常若逃出 listener 回调</b>，
 * 会被当成传输层内部错误，description 被替换成 {@code "Application error processing RPC"}，
 * 业务码与原文当场全丢。所以「领域层抛对了」<b>不等于</b>「客户端收到了」——
 * 必须真的走一遍 channel（本仓 like-rpc 的 {@code LikeServiceGrpcImplWireTest} 同款做法）。
 */
class TaskServiceGrpcImplWireTest {

    private ManagedChannel channel;
    private io.grpc.Server server;
    private TaskServiceGrpc.TaskServiceBlockingStub stub;
    private com.rag.task.task.rpc.service.TaskDomainService domain;

    @BeforeEach
    void setUp() throws Exception {
        String serverName = InProcessServerBuilder.generateName();

        domain = Mockito.mock(com.rag.task.task.rpc.service.TaskDomainService.class);
        TaskServiceGrpc.TaskServiceImplBase service = new TaskServiceGrpcImpl(domain);

        InProcessServerBuilder serverBuilder = InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .addService(service);
        server = serverBuilder.build().start();

        channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
        stub = TaskServiceGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void tearDown() {
        channel.shutdownNow();
        server.shutdownNow();
    }

    @Test
    @DisplayName("🔴 uid<=0 ⇒ 客户端收到 INVALID_ARGUMENT + 原文 user_id invalid")
    void invalidArgumentReachesClientVerbatim() {
        when(domain.getTask(any())).thenThrow(
                Status.INVALID_ARGUMENT.withDescription("user_id invalid").asRuntimeException());

        StatusRuntimeException ex = assertThrows(StatusRuntimeException.class,
                () -> stub.getTask(GetTaskReq.newBuilder().setUserId(0L).build()));

        assertEquals(Status.Code.INVALID_ARGUMENT, ex.getStatus().getCode(),
                "★ 必须是 INVALID_ARGUMENT；若被兜底分支改写成 UNKNOWN，"
                        + "调用方 task-api 拿到的错误语义就变了");
        assertEquals("user_id invalid", ex.getStatus().getDescription(),
                "★ description 必须逐字保留（不能被 'Application error processing RPC' 覆盖）");
    }

    @Test
    @DisplayName("🔴 裸异常 ⇒ 客户端收到 UNKNOWN + 原始 message（Go: return nil, err）")
    void rawErrorBecomesUnknown() {
        when(domain.getTask(any())).thenThrow(new RuntimeException("pg down"));

        StatusRuntimeException ex = assertThrows(StatusRuntimeException.class,
                () -> stub.getTask(GetTaskReq.newBuilder().setUserId(1L).build()));

        assertEquals(Status.Code.UNKNOWN, ex.getStatus().getCode(),
                "★ grpc-go 对非 status error 一律转 Unknown");
        assertEquals("pg down", ex.getStatus().getDescription());
    }

    @Test
    @DisplayName("正常响应 ⇒ 字段逐个落到线上（wire 层字段名与 proto 契约一致）")
    void successResponseIsWireCompatible() {
        GetTaskResp resp = GetTaskResp.newBuilder()
                .addTask(com.rag.task.task.proto.Task.newBuilder()
                        .setName("点赞任务")
                        .setDesc("给文章点赞5次")
                        .setTaskId(1902L)
                        .setCompletionProgress(4L)
                        .setRequiredProgress(5L)
                        .build())
                .build();
        when(domain.getTask(any())).thenReturn(resp);

        GetTaskResp got = stub.getTask(GetTaskReq.newBuilder().setUserId(7L).build());

        assertEquals(1, got.getTaskCount());
        var t = got.getTask(0);
        assertEquals("点赞任务", t.getName());
        assertEquals("给文章点赞5次", t.getDesc());
        assertEquals(1902L, t.getTaskId());
        assertEquals(4L, t.getCompletionProgress());
        assertEquals(5L, t.getRequiredProgress());
    }

    @Test
    @DisplayName("service name 与 method 名必须是 task.TaskService/GetTask（与 Go proto 一致）")
    void serviceContractMatchesProto() {
        assertEquals("task.TaskService", TaskServiceGrpc.SERVICE_NAME);
        assertEquals("GetTask", TaskServiceGrpc.getGetTaskMethod().getBareMethodName());
    }
}