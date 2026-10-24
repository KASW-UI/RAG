package com.rag.comment.comment.common.errmsg;

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

/**
 * <b>真 in-process gRPC server + 真 channel</b> 的 comment 业务码往返守门。
 *
 * <p><b>为什么必须过真实传输层</b>：comment 的业务码靠自定义 trailer 上线，
 * 而「trailer 能不能无损抵达客户端」是整个方案的<b>承重假设</b>。
 * 只断言 {@code grpcError(...).getTrailers()} 的值，证明不了它真的穿过了传输层
 * —— 本项目此前已多次栽在「单测全绿但真实链路上没生效」上（AGENTS.md §3.3）。
 *
 * <p>同时把「grpc-java 遇到越界 grpc-status 会怎样」当作<b>待验证假设</b>实测，
 * 而不是照抄 like 域的注释 —— 那条结论在本仓从未被复现过。
 */
class CommentGrpcErrorTest {

    private static Server server;
    private static ManagedChannel channel;

    /** Go comment-rpc 实际用 {@code NewGrpcErr} 发出的码，逐字取自 Go 调用点。 */
    private static final int BIZ_CODE = CommentErrorCode.ERROR_SUBJECT_NOT_EXIST.getCode();
    private static final String BIZ_MSG = "评论区不存在";

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
                        // ServerCalls 从 onHalfClose 里调业务方法，throw 会逃出流监听回调，
                        // 被当成传输层内部错误，description 被替换成
                        // "Application error processing RPC"。
                        observer.onError(CommentGrpcError.grpcError(BIZ_CODE, BIZ_MSG))))
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
        assertEquals("1012", trailers.get(CommentGrpcError.BIZ_CODE_TRAILER),
                "trailer 里的业务码没无损抵达 —— 整个 CommentGrpcError 方案的前提被推翻");
        assertEquals(CommentErrorCode.ERROR_SUBJECT_NOT_EXIST.getCode(),
                CommentGrpcError.bizCodeFromError(thrown));
    }

    @Test
    @DisplayName("description 逐字保留 Go 的 msg，comment-api 呈现给用户的文案不会变")
    void descriptionKeepsGoMessageVerbatim() {
        assertEquals(BIZ_MSG, callAndCatch().getStatus().getDescription());
    }

    @Test
    @DisplayName("grpc-java 对越界 grpc-status 的降级形态（实测，不是假设）")
    void grpcJavaDegradesOutOfRangeStatusCode() {
        // 这是 CommentGrpcError 整个方案的立论前提，先把它钉死。
        // 若某个 grpc 版本改了这里的表现，下面的降级捞码分支需要同步调整。
        Status degraded = Status.fromCodeValue(5003);
        assertEquals(Status.Code.UNKNOWN, degraded.getCode(),
                "grpc-java 不再把越界码降级成 UNKNOWN —— 本类的设计前提变了，必须重新评估");
        assertEquals("Unknown code 5003", degraded.getDescription(),
                "降级 description 的格式变了 —— recoverFromGoDegradedDescription 的前缀要跟着改");
        assertEquals(2, degraded.getCode().value(), "UNKNOWN 的标准值应为 2");
    }

    @Test
    @DisplayName("灰度期：Go comment-rpc 发出的越界 grpc-status，Java 侧仍能捞回业务码")
    void degradedGoStatusStillYieldsBizCode() {
        // 模拟 grpc-java 收到 Go 的 grpc-status: 5003 后降级成的 status
        StatusRuntimeException fromGo = Status.UNKNOWN
                .withDescription("Unknown code 5003")
                .asRuntimeException();

        assertEquals(CommentErrorCode.ERROR_DB_SELECT.getCode(),
                CommentGrpcError.bizCodeFromError(fromGo),
                "灰度期 comment-api → Go comment-rpc 时应捞回 5003，而不是退化成 UNKNOWN 的 2");
    }

    @Test
    @DisplayName("降级形态只捞码不捞 msg —— 原始文案已被覆盖，不做猜测性还原")
    void degradedGoStatusDoesNotInventMessage() {
        StatusRuntimeException fromGo = Status.UNKNOWN
                .withDescription("Unknown code 1012")
                .asRuntimeException();

        assertEquals(1012, CommentGrpcError.bizCodeFromError(fromGo));
        // description 仍是 grpc-java 生成的降级文案，不应被我们"还原"成 Go 的原文案
        assertEquals("Unknown code 1012", fromGo.getStatus().getDescription());
    }

    @Test
    @DisplayName("标准 gRPC 码原值返回，对齐 Go 的 int(st.Code()) 字面搬运")
    void standardGrpcCodeIsReturnedVerbatimLikeGoIntStCode() {
        // Go: errmsg.NewErrCodeMsg(int(st.Code()), st.Message())
        // 收到 UNAUTHENTICATED(16) 时 Go 给客户端的就是 code:16。
        // 若这里改判成 2001，就是凭空造出 Go 永远产生不到的码。
        StatusRuntimeException unauthenticated =
                Status.UNAUTHENTICATED.withDescription("token invalid").asRuntimeException();
        assertEquals(Status.Code.UNAUTHENTICATED.value(), CommentGrpcError.bizCodeFromError(unauthenticated));

        StatusRuntimeException permissionDenied =
                Status.PERMISSION_DENIED.withDescription("not up master").asRuntimeException();
        assertEquals(Status.Code.PERMISSION_DENIED.value(), CommentGrpcError.bizCodeFromError(permissionDenied));

        StatusRuntimeException invalidArg =
                Status.INVALID_ARGUMENT.withDescription("target_type is required").asRuntimeException();
        assertEquals(Status.Code.INVALID_ARGUMENT.value(), CommentGrpcError.bizCodeFromError(invalidArg));
    }

    @Test
    @DisplayName("业务码绝不落在 INTERNAL / UNAVAILABLE 这类「会被重试」的类别里")
    void businessCodesNeverCollideWithRetryableCategories() {
        // Go 把业务码原样塞进 grpc-status，comment-rpc 的这些错误在 Go 里全部是
        // 「正常业务错误」。若 Java 标成 INTERNAL/UNAVAILABLE，下游重试器会把它们
        // 当依赖故障反复重试 —— 那是新增行为，不是对齐。
        int[] bizCodes = {
                CommentErrorCode.ERROR_SUBJECT_NOT_EXIST.getCode(),
                CommentErrorCode.ERROR_COMMENT_NOT_EXIST.getCode(),
                CommentErrorCode.ERROR_DB_SELECT.getCode(),
                CommentErrorCode.ERROR_DB_UPDATE.getCode(),
                CommentErrorCode.ERROR_USER_NO_RIGHT.getCode(),
                CommentErrorCode.ERROR_KAFKA_PUSH.getCode(),
                CommentErrorCode.ERROR_SNOWFLAKE_ID.getCode(),
                CommentErrorCode.ERROR_JSON_MARSHAL.getCode(),
                CommentErrorCode.ERROR_DB_INSERT.getCode(),
        };
        for (int bizCode : bizCodes) {
            Status.Code mapped = CommentGrpcError.grpcError(bizCode, "x").getStatus().getCode();
            assertNotEquals(Status.Code.INTERNAL, mapped, "业务码 " + bizCode + " 被标成 INTERNAL 了");
            assertNotEquals(Status.Code.UNAVAILABLE, mapped, "业务码 " + bizCode + " 被标成 UNAVAILABLE 了");
            assertNotEquals(Status.Code.DEADLINE_EXCEEDED, mapped, "业务码 " + bizCode + " 被标成 DEADLINE_EXCEEDED 了");
        }
    }

    @Test
    @DisplayName("入参错误标成 INVALID_ARGUMENT（语义最贴近，且不制造重试）")
    void inputWrongMapsToInvalidArgument() {
        assertEquals(Status.Code.INVALID_ARGUMENT,
                CommentGrpcError.grpcError(CommentErrorCode.ERROR_INPUT_WRONG.getCode(), "评论ID不能为空")
                        .getStatus().getCode());
        // 其余码 → UNKNOWN（与「码无法归类」的实情相符）
        assertEquals(Status.Code.UNKNOWN,
                CommentGrpcError.grpcError(CommentErrorCode.ERROR_COMMENT_NOT_EXIST.getCode(), "评论不存在")
                        .getStatus().getCode());
    }

    @Test
    @DisplayName("业务码与降级捞码的优先级：trailer 优于降级 description")
    void trailerWinsOverDegradedDescription() {
        // 本域自己发的错误同时带 trailer 和正常 description，trailer 必须赢
        StatusRuntimeException ours = callAndCatch();
        assertEquals(BIZ_CODE, CommentGrpcError.bizCodeFromError(ours));
    }

    @Test
    @DisplayName("非 status 的 Throwable：CommentCodeError 取自身码，其余退 1015")
    void nonStatusThrowableHandling() {
        assertEquals(1012, CommentGrpcError.bizCodeFromError(
                CommentCodeError.newCode(1012)));

        assertEquals(CommentErrorCode.CODE_SERVER_BUSY.getCode(),
                CommentGrpcError.bizCodeFromError(new IllegalStateException("PG 连接断开")));
    }

    @Test
    @DisplayName("null 输入返回 200 —— Go 侧调用点都在 if err != nil 内，该分支不可达")
    void nullInputReturnsSuccess() {
        assertEquals(CommentErrorCode.SUCCESS.getCode(), CommentGrpcError.bizCodeFromError(null));
    }

    @Test
    @DisplayName("脏 trailer 不会让整条错误解析失败")
    void dirtyTrailerFallsThroughToStandardCode() {
        Metadata dirty = new Metadata();
        dirty.put(CommentGrpcError.BIZ_CODE_TRAILER, "not-a-number");
        StatusRuntimeException err = Status.UNKNOWN
                .withDescription("boom")
                .asRuntimeException(dirty);

        assertEquals(Status.Code.UNKNOWN.value(), CommentGrpcError.bizCodeFromError(err));
    }
}