package com.rag.like.like.common.errmsg;

import io.grpc.CallOptions;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.ServerCalls;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>真 in-process gRPC server + 真 channel</b> 的 like 业务码往返守门。
 *
 * <p><b>为什么必须过真实传输层</b>：like 的业务码靠自定义 trailer 上线，
 * 而「trailer 能不能无损抵达客户端」是整个方案的<b>承重假设</b>。
 * 只断言 {@code grpcError(...).getTrailers()} 的值，证明不了它真的穿过了传输层
 * ——本项目此前已多次栽在「单测全绿但真实链路上没生效」上（AGENTS.md §3.3）。
 */
class LikeGrpcErrorTest {

    private static Server server;
    private static ManagedChannel channel;

    /** Go like-rpc 实际用 {@code NewGrpcErr} 发出的 3 个码，逐字取自 Go 调用点。 */
    private static final int BIZ_CODE = LikeErrorCode.ERROR_DB_SELECT.getCode();
    private static final String BIZ_MSG = "DB查询失败";

    private static final MethodDescriptor.Marshaller<String> STRING_MARSHALLER =
            new MethodDescriptor.Marshaller<>() {
                @Override
                public InputStream stream(String value) {
                    return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
                }

                @Override
                public String parse(InputStream stream) {
                    try (InputStream in = stream) {
                        return new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
                }
            };

    private static final MethodDescriptor<String, String> METHOD =
            MethodDescriptor.<String, String>newBuilder()
                    .setType(MethodDescriptor.MethodType.UNARY)
                    .setFullMethodName("probe.Ping/Ping")
                    .setRequestMarshaller(STRING_MARSHALLER)
                    .setResponseMarshaller(STRING_MARSHALLER)
                    .build();

    @BeforeAll
    static void startServer() throws IOException {
        String name = InProcessServerBuilder.generateName();
        ServerServiceDefinition service = ServerServiceDefinition.builder("probe.Ping")
                .addMethod(METHOD, ServerCalls.asyncUnaryCall((request, observer) ->
                        // ⚠️ 必须 observer.onError(...) 而不是 throw：
                        // ServerCalls 从 onHalfClose(ServerCalls.java:182) 里调业务方法，
                        // throw 会逃出流监听回调，被当成传输层内部错误，
                        // description 被替换成 "Application error processing RPC"。
                        observer.onError(LikeGrpcError.grpcError(BIZ_CODE, BIZ_MSG))))
                .build();
        server = InProcessServerBuilder.forName(name).directExecutor()
                .addService(service).build().start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
    }

    @AfterAll
    static void shutdown() throws InterruptedException {
        if (channel != null) {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
        if (server != null) {
            server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static StatusRuntimeException callAndCatch() {
        return assertThrows(StatusRuntimeException.class,
                () -> ClientCalls.blockingUnaryCall(channel, METHOD, CallOptions.DEFAULT, "ping"));
    }

    @Test
    @DisplayName("业务码经自定义 trailer 无损往返 —— 整个方案的承重假设")
    void bizCodeTrailerRoundTripsLosslessly() {
        StatusRuntimeException thrown = callAndCatch();

        Metadata trailers = Status.trailersFromThrowable(thrown);
        assertNotNull(trailers, "客户端没收到 trailer");
        assertEquals("5003", trailers.get(LikeGrpcError.BIZ_CODE_TRAILER),
                "trailer 里的业务码没无损抵达 —— 整个 LikeGrpcError 方案的前提被推翻");
        assertEquals(LikeErrorCode.ERROR_DB_SELECT.getCode(),
                LikeGrpcError.bizCodeFromError(thrown));
    }

    @Test
    @DisplayName("description 逐字保留 Go 的 msg，like-api 呈现给用户的文案不会变")
    void descriptionKeepsGoMessageVerbatim() {
        StatusRuntimeException thrown = callAndCatch();
        // Go 侧 st.Message() 拿到的就是 "DB查询失败"；Java 侧 getDescription() 必须一致，
        // 否则 envelope.msg 会漂移 —— 注意查表得到的是 "查询数据库失败"，那是不对的。
        assertEquals(BIZ_MSG, thrown.getStatus().getDescription());
        assertNotEquals(LikeErrorMessages.getMsg(BIZ_CODE), thrown.getStatus().getDescription(),
                "若这里相等，说明误把查表文案当成了 RPC 原文");
    }

    @Test
    @DisplayName("⚠️ 业务码绝不能映射到熔断失败名单里的标准码")
    void businessCodesAreNeverMappedToBreakerFailureCodes() {
        // go-zero codes.Acceptable 的失败名单。Go like-rpc 发的 codes.Code(5003)/codes.Code(2001)
        // 都不在其中 ⇒ 业务错误一律记为 Accept。若 Java 侧映到名单内的码，
        // 每次业务错误都会被计成依赖故障、熔断误开、打掉正常流量（新增行为，不是对齐）。
        var breakerFailureCodes = new io.grpc.Status.Code[]{
                Status.Code.DEADLINE_EXCEEDED, Status.Code.INTERNAL, Status.Code.UNAVAILABLE,
                Status.Code.DATA_LOSS, Status.Code.UNIMPLEMENTED, Status.Code.RESOURCE_EXHAUSTED};

        for (int bizCode : new int[]{
                LikeErrorCode.ERROR_INPUT_WRONG.getCode(), LikeErrorCode.ERROR_DB_SELECT.getCode(),
                LikeErrorCode.ERROR_REDIS_UPDATE.getCode(), LikeErrorCode.ERROR_SERVER_COMMON.getCode(),
                LikeErrorCode.ERROR_KAFKA_PUSH.getCode(), LikeErrorCode.ERROR_TOKEN_RUNTIME.getCode()}) {

            // 借 grpcError 反推实际用到的标准码（发一次即可，无需反射私有方法）
            var status = LikeGrpcError.grpcError(bizCode, "x").getStatus();
            for (var failure : breakerFailureCodes) {
                assertNotEquals(failure, status.getCode(),
                        "业务码 " + bizCode + " 被映成了熔断失败码 " + failure);
            }
        }
    }

    @Test
    @DisplayName("入参错映成 INVALID_ARGUMENT，其余映成 UNKNOWN（刻意不用 INTERNAL）")
    void grpcCodeSelectionIsExplicit() {
        assertEquals(Status.Code.INVALID_ARGUMENT,
                LikeGrpcError.grpcError(LikeErrorCode.ERROR_INPUT_WRONG.getCode(), "x").getStatus().getCode());
        assertEquals(Status.Code.UNKNOWN,
                LikeGrpcError.grpcError(LikeErrorCode.ERROR_DB_SELECT.getCode(), "x").getStatus().getCode());
        // 显式钉住：INTERNAL 是熔断失败码，绝不能出现在业务错误的映射上
        assertTrue(Status.Code.values().length == 17, "gRPC 合法码只有 0..16");
        assertNotEquals(Status.Code.INTERNAL,
                LikeGrpcError.grpcError(LikeErrorCode.ERROR_REDIS_UPDATE.getCode(), "x").getStatus().getCode());
    }

    @Test
    @DisplayName("⚠️ 钉住 grpc-java 无法承载越界码 —— 这是本方案存在的原因，不是功能断言")
    void grpcJavaCannotCarryGoLikeRawBusinessCodes() {
        // grpc-java Status 内部只存 Code 枚举（Status.java:438），合法值 0..16；
        // Status.fromCodeValue(Status.java:305-311) 对越界值降级为 UNKNOWN 并覆盖 description。
        // 若哪天 grpc-java 支持了任意码值，本断言会红 —— 那是好事，
        // 提示可以把业务码搬回 grpc-status，本类即可简化。
        for (int bizCode : new int[]{2001, 5003, 5012}) {
            Status parsed = Status.fromCodeValue(bizCode);
            assertNotEquals(bizCode, parsed.getCode().value());
            assertEquals(Status.Code.UNKNOWN, parsed.getCode());
            assertEquals("Unknown code " + bizCode, parsed.getDescription());
        }
    }

    @Test
    @DisplayName("灰度期：Go like-rpc 的降级形态只能捞回码，msg 不可复原")
    void degradedGoStatusStillYieldsUnknownCode() {
        // 这就是 Java 客户端收到 Go like-rpc 时的真实形态（构造方式即 grpc-java 的解码路径）。
        StatusRuntimeException fromGoLikeRpc = Status.fromCodeValue(5003).asRuntimeException();
        assertEquals(5003, LikeGrpcError.bizCodeFromError(fromGoLikeRpc),
                "灰度期至少要保住机器可读的业务码");
        // msg 已在上游被覆盖，只能按捞回的码查表补 —— 不做猜测性还原。
        assertEquals(LikeErrorMessages.getMsg(5003), LikeErrorMessages.getMsg(5003));
        assertNotNull(LikeErrorMessages.getMsg(LikeGrpcError.bizCodeFromError(fromGoLikeRpc)));
    }

    @Test
    @DisplayName("非 gRPC 异常与裸状态回落 1015，CodeError 原样取码")
    void fallbacksAreExplicit() {
        assertEquals(LikeErrorCode.CODE_SERVER_BUSY.getCode(),
                LikeGrpcError.bizCodeFromError(new IllegalStateException("连接池耗尽")));
        assertEquals(LikeErrorCode.CODE_SERVER_BUSY.getCode(),
                LikeGrpcError.bizCodeFromError(Status.INTERNAL.asRuntimeException()));
        assertEquals(LikeErrorCode.ERROR_INPUT_WRONG.getCode(),
                LikeGrpcError.bizCodeFromError(Status.INVALID_ARGUMENT.asRuntimeException()));
        assertEquals(LikeErrorCode.ERROR_USER_NO_LOGIN.getCode(),
                LikeGrpcError.bizCodeFromError(Status.UNAUTHENTICATED.asRuntimeException()));
        assertEquals(LikeErrorCode.ERROR_USER_NO_RIGHT.getCode(),
                LikeGrpcError.bizCodeFromError(Status.PERMISSION_DENIED.asRuntimeException()));
        assertEquals(LikeErrorCode.ERROR_INPUT_WRONG.getCode(),
                LikeGrpcError.bizCodeFromError(new CodeError(2001, "输入有误")),
                "进程内的 CodeError 应当原样取回自身业务码，不走任何兜底");
        // null 是防御性分支：Go 侧对应调用点都在 if err != nil 内，实际不可达
        assertEquals(LikeErrorCode.SUCCESS.getCode(), LikeGrpcError.bizCodeFromError(null));
    }
}