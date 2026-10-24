package com.rag.follow.follow.common.errmsg;

import io.grpc.CallOptions;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
import io.grpc.StatusException;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>真 in-process gRPC server + 真 channel</b> 的 follow 业务码往返与 {@code BizCodeFromError}
 * 语义守门。真源 {@code Sea-RideTheWind_golang/service/follow/common/errmsg.go:76-133}。
 *
 * <p><b>为什么必须过真实传输层</b>：follow 的业务码<b>不</b>放在 {@code grpc-status} 里，
 * 而是<b>藏在 description 文案里</b>（见 {@link FollowGrpcError} 类注释的对照表）。
 * 「description 能不能逐字节穿过传输层、并被 {@code bizCodeFromError} 还原成业务码」
 * 是整个组件的<b>承重假设</b>：本域实际发出的 4 种 gRPC 码<b>全部落在</b> switch 白名单内，
 * 若文案反查失效，5201/5202/5101/5007/5008 会全部退化成 5003/5005/5002/5001 ——
 * 客户端拿到的是<b>错误但合法</b>的业务码，最难被发现。
 */
class FollowGrpcErrorContractTest {

    private static final String SERVICE = "probe.Ping";
    private static Server server;
    private static ManagedChannel channel;

    /**
     * Go follow-rpc <b>真实发出</b>的「业务码 → gRPC 标准码」组合，逐条抄自 Go 调用点。
     *
     * <p>⚠️ 这张表同时是「本域没有越界 grpc-status」的证据：全部 4 个 gRPC 码都是 grpc-java
     * 认得的标准码（0-16），所以 Java 侧可以直接透传，不需要 like 域那套 trailer 兜底。
     */
    private static final Map<Integer, Status.Code> GO_CALL_SITES = new LinkedHashMap<>();

    static {
        // rpc/internal/logic/helper.go:29,39 — 入参校验失败
        GO_CALL_SITES.put(FollowErrorCode.ERROR_INVALID_PARAM.getCode(), Status.Code.INVALID_ARGUMENT);
        // rpc/internal/logic/helper.go:32 — 自己关注自己
        GO_CALL_SITES.put(FollowErrorCode.ERROR_SELF_RELATION.getCode(), Status.Code.INVALID_ARGUMENT);
        // rpc/internal/logic/followlogic.go:55 — 已存在拉黑关系
        GO_CALL_SITES.put(FollowErrorCode.ERROR_RELATION_BLOCKED.getCode(), Status.Code.PERMISSION_DENIED);
        // rpc/internal/logic/helper.go:86,91 — 目标用户不存在
        GO_CALL_SITES.put(FollowErrorCode.ERROR_USER_NOT_FOUND.getCode(), Status.Code.NOT_FOUND);
        // rpc/internal/logic/helper.go:88 — 用户 RPC 非 NOTFound 的失败
        GO_CALL_SITES.put(FollowErrorCode.ERROR_SERVER_COMMON.getCode(), Status.Code.INTERNAL);
        // rpc/internal/logic/followlogic.go:52 及各 logic 的读路径 DB 分支
        GO_CALL_SITES.put(FollowErrorCode.ERROR_DB_SELECT.getCode(), Status.Code.INTERNAL);
        // rpc/internal/logic/followlogic.go:63 / unfollowlogic.go:46 / blocklogic.go:51
        GO_CALL_SITES.put(FollowErrorCode.ERROR_DB_UPDATE.getCode(), Status.Code.INTERNAL);
    }

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

    private static MethodDescriptor<String, String> method(String name) {
        return MethodDescriptor.<String, String>newBuilder()
                .setType(MethodDescriptor.MethodType.UNARY)
                .setFullMethodName(MethodDescriptor.generateFullMethodName(SERVICE, name))
                .setRequestMarshaller(STRING_MARSHALLER)
                .setResponseMarshaller(STRING_MARSHALLER)
                .build();
    }

    /** 请求体是业务码字符串；server 按 Go 真实调用点表选出 gRPC 码后走 {@code onError}。 */
    private static final MethodDescriptor<String, String> EMIT_METHOD = method("Emit");
    /** 请求体格式 {@code <grpcCodeName>|<description>}，用于构造文案不在反查表里的错误。 */
    private static final MethodDescriptor<String, String> RAW_METHOD = method("Raw");
    /** 故意用 {@code throw} 而不是 {@code onError}，用来证明 description 会被传输层吞掉。 */
    private static final MethodDescriptor<String, String> THROW_METHOD = method("Throw");

    @BeforeAll
    static void startServer() throws IOException {
        String name = InProcessServerBuilder.generateName();
        ServerServiceDefinition service = ServerServiceDefinition.builder(SERVICE)
                .addMethod(EMIT_METHOD, ServerCalls.asyncUnaryCall((request, observer) ->
                        // ⚠️ 必须 observer.onError(...) 而不是 throw：
                        // ServerCalls 从 onHalfClose 里调业务方法，throw 会逃出流监听回调，
                        // 被当成传输层内部错误，description 被替换成
                        // "Application error processing RPC" —— 文案即业务码，全丢。
                        {
                            int bizCode = Integer.parseInt(request);
                            Status.Code grpcCode = Objects.requireNonNull(
                                    GO_CALL_SITES.get(bizCode),
                                    "GO_CALL_SITES 里没有登记业务码 " + bizCode
                                            + "：Go follow-rpc 从未用该码发过 gRPC 错误，"
                                            + "凭空断言即为制造 Go 侧不存在的行为");
                            observer.onError(FollowGrpcError.grpcError(grpcCode, bizCode));
                        }))
                .addMethod(RAW_METHOD, ServerCalls.asyncUnaryCall((request, observer) -> {
                    int split = request.indexOf('|');
                    Status.Code grpcCode = Status.Code.valueOf(request.substring(0, split));
                    observer.onError(Status.fromCode(grpcCode)
                            .withDescription(request.substring(split + 1))
                            .asException());
                }))
                .addMethod(THROW_METHOD, ServerCalls.asyncUnaryCall((request, observer) -> {
                    // 故意让异常逃出业务方法（而不是走 observer.onError）。
                    // 用不带 status 的裸 RuntimeException，保证 grpc-java 一定走
                    // 「传输层兜底」分支，而不是沿 cause 链把 status 捞回来。
                    throw new IllegalStateException("业务方法直接抛出，未走 onError");
                }))
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

    private static StatusRuntimeException call(MethodDescriptor<String, String> md, String request) {
        return assertThrows(StatusRuntimeException.class,
                () -> ClientCalls.blockingUnaryCall(channel, md, CallOptions.DEFAULT, request));
    }

    private static StatusRuntimeException emit(int bizCode) {
        return call(EMIT_METHOD, String.valueOf(bizCode));
    }

    // ==================== 1. 业务码经真传输层往返 ====================

    @Test
    @DisplayName("Go 真实发出的全部 7 组调用点：业务码经真传输层无损往返 —— 整个方案的承重假设")
    void everyGoCallSiteRecoversItsBizCodeOverRealTransport() {
        assertEquals(7, GO_CALL_SITES.size(), "本表应覆盖 Go follow-rpc 的 7 组代表性调用点");

        for (Map.Entry<Integer, Status.Code> callSite : GO_CALL_SITES.entrySet()) {
            int bizCode = callSite.getKey();
            StatusRuntimeException thrown = emit(bizCode);

            // ① wire 上的 gRPC 码必须原样透传
            assertEquals(callSite.getValue(), thrown.getStatus().getCode(),
                    "grpc-status 漂移：bizCode=" + bizCode + " 的标准码没透传"
                            + "（Go 调用点用的是 " + callSite.getValue() + "）");
            // ② description 必须逐字节等于 Go 的文案（这正是业务码的载体）
            assertEquals(FollowErrorMessages.getMsg(bizCode), thrown.getStatus().getDescription(),
                    "description 漂移：bizCode=" + bizCode + " 的文案没逐字穿过传输层");
            // ③ 业务码必须能还原回来
            assertEquals(bizCode, FollowGrpcError.bizCodeFromError(thrown),
                    "业务码 " + bizCode + " 没能跨传输还原 —— description 反查这条链路被破坏了");
        }
    }

    @Test
    @DisplayName("文案反查相对 switch 兜底的增量：5 组调用点只能靠 description 才能还原出正确业务码")
    void messageLookupIsLoadBearingNotRedundant() {
        // 这 2 组 switch 兜底恰好也能得出正确码（文案反查在这里是冗余的）
        assertEquals(5003, FollowGrpcError.bizCodeFromError(emit(5003)));
        assertEquals(5001, FollowGrpcError.bizCodeFromError(emit(5001)));

        // ⚠️ 以下 5 组只可能靠文案反查还原 —— 若跳过反查直走 switch，会得到这些「错误但合法」的码
        assertEquals(5201, FollowGrpcError.bizCodeFromError(emit(5201)), "自关系");
        assertEquals(5202, FollowGrpcError.bizCodeFromError(emit(5202)), "已拉黑");
        assertEquals(5101, FollowGrpcError.bizCodeFromError(emit(5101)), "用户不存在");
        assertEquals(5007, FollowGrpcError.bizCodeFromError(emit(5007)), "DB 读失败");
        assertEquals(5008, FollowGrpcError.bizCodeFromError(emit(5008)), "DB 写失败");

        // 反证：这些业务码的 gRPC 标准码若单独看，会落到 switch 兜底的另一个码上
        assertEquals(5003, FollowErrorCode.ERROR_INVALID_PARAM.getCode());
        assertNotEquals(FollowErrorCode.ERROR_SELF_RELATION.getCode(),
                FollowErrorCode.ERROR_INVALID_PARAM.getCode(),
                "5201 与 5003 必须不同，否则上面对照表的结论不成立");
        assertNotEquals(FollowErrorCode.ERROR_DB_SELECT.getCode(),
                FollowErrorCode.ERROR_SERVER_COMMON.getCode(),
                "5007 与 5001 必须不同，否则上面对照表的结论不成立");
    }

    @Test
    @DisplayName("英文文案逐字节穿过传输层，不出现编码损坏")
    void asciiDescriptionSurvivesTransportVerbatim() {
        for (int bizCode : GO_CALL_SITES.keySet()) {
            String description = emit(bizCode).getStatus().getDescription();
            assertTrue(description.chars().allMatch(c -> c < 128),
                    "bizCode=" + bizCode + " 的 description 含非 ASCII 字符，与 Go 整表英文形态不符");
        }
    }

    @Test
    @DisplayName("INTERNAL / PERMISSION_DENIED 原样上线——不得为躲开熔断失败名单被改写成 UNKNOWN")
    void breakerRelevantGrpcCodesPassThroughVerbatim() {
        // Go 侧确实把 Internal / PermissionDenied 计入 go-zero 熔断的失败统计，
        // 改写会让 follow 的 DB / 权限故障不再计入熔断 —— 那是新增行为而非对齐。
        assertEquals(Status.Code.INTERNAL, emit(5007).getStatus().getCode());
        assertEquals(Status.Code.INTERNAL, emit(5008).getStatus().getCode());
        assertEquals(Status.Code.PERMISSION_DENIED, emit(5202).getStatus().getCode());
        assertEquals(Status.Code.INVALID_ARGUMENT, emit(5201).getStatus().getCode());
    }

    @Test
    @DisplayName("业务方法内异常逃出（未走 onError）会吞掉 description 并让业务码丢失 —— 反证必须走 onError")
    void descriptionLosesBizCodeWhenServerThrows() {
        StatusRuntimeException thrown = call(THROW_METHOD, "x");
        assertEquals(Status.Code.UNKNOWN, thrown.getStatus().getCode());
        assertNotEquals("database query failed", thrown.getStatus().getDescription());
        // 文案没了 ⇒ 反查落空 ⇒ 退到 switch ⇒ UNKNOWN 不在白名单 ⇒ default ⇒ 1015
        assertEquals(1015, FollowGrpcError.bizCodeFromError(thrown));
    }

    // ==================== 2. BizCodeFromError 的分支语义 ====================

    @Test
    @DisplayName("文案不在反查表时按 Go switch 的 6 个白名单兜底")
    void unknownDescriptionFallsBackToWhitelistedGrpcCodes() {
        // Go: errmsg.go:118-132 的 switch 恰好 6 个 case
        assertEquals(5003, FollowGrpcError.bizCodeFromError(call(RAW_METHOD, "INVALID_ARGUMENT|some new reason")));
        assertEquals(5004, FollowGrpcError.bizCodeFromError(call(RAW_METHOD, "UNAUTHENTICATED|some new reason")));
        assertEquals(5005, FollowGrpcError.bizCodeFromError(call(RAW_METHOD, "PERMISSION_DENIED|some new reason")));
        assertEquals(5006, FollowGrpcError.bizCodeFromError(call(RAW_METHOD, "ALREADY_EXISTS|some new reason")));
        assertEquals(5002, FollowGrpcError.bizCodeFromError(call(RAW_METHOD, "NOT_FOUND|some new reason")));
        assertEquals(5001, FollowGrpcError.bizCodeFromError(call(RAW_METHOD, "INTERNAL|some new reason")));
    }

    @Test
    @DisplayName("白名单外的标准码一律落 1015——含 DEADLINE_EXCEEDED / UNAVAILABLE / CANCELLED / UNKNOWN")
    void nonWhitelistedGrpcCodesFallBackToServerBusy() {
        // Go switch 的 default 分支；不得为「显得合理」往白名单里加码 —— 那是新增行为
        assertEquals(1015, FollowGrpcError.bizCodeFromError(call(RAW_METHOD, "DEADLINE_EXCEEDED|some new reason")));
        assertEquals(1015, FollowGrpcError.bizCodeFromError(call(RAW_METHOD, "UNAVAILABLE|some new reason")));
        assertEquals(1015, FollowGrpcError.bizCodeFromError(call(RAW_METHOD, "CANCELLED|some new reason")));
        assertEquals(1015, FollowGrpcError.bizCodeFromError(call(RAW_METHOD, "UNKNOWN|some new reason")));
        assertEquals(1015, FollowGrpcError.bizCodeFromError(call(RAW_METHOD, "RESOURCE_EXHAUSTED|some new reason")));
    }

    @Test
    @DisplayName("\"OK\" / \"FAIL\" 不参与反查（Go errmsg.go:79-81 显式排除 Success / Error）")
    void okAndFailAreExcludedFromReverseLookup() {
        // 若纳入反查，description 为 "OK" 的错误会被误判成 200。
        // 正确行为：反查未命中 ⇒ 退到 switch ⇒ INTERNAL ⇒ 5001。
        assertEquals(5001, FollowGrpcError.bizCodeFromError(call(RAW_METHOD, "INTERNAL|OK")));
        assertEquals(5001, FollowGrpcError.bizCodeFromError(call(RAW_METHOD, "INTERNAL|FAIL")));

        // 再用一条非白名单码钉死"确实被排除"：若 "OK" 进了反查表，这里会得到 200 而非 1015。
        assertEquals(1015, FollowGrpcError.bizCodeFromError(call(RAW_METHOD, "UNAVAILABLE|OK")));
        assertEquals(1015, FollowGrpcError.bizCodeFromError(call(RAW_METHOD, "UNAVAILABLE|FAIL")));
    }

    @Test
    @DisplayName("非 gRPC status 的 Throwable 一律落 1015（Go status.FromError 的 ok=false 分支）")
    void nonStatusThrowableFallsBackToServerBusy() {
        assertEquals(1015, FollowGrpcError.bizCodeFromError(new IllegalStateException("db down")));
        assertEquals(1015, FollowGrpcError.bizCodeFromError(new RuntimeException()));
    }

    @Test
    @DisplayName("顶层是 FollowCodeError 时取其 code —— Go 的 err.(*CodeError) 直接类型断言分支")
    void topLevelCodeErrorYieldsItsOwnCode() {
        // Go: errmsg.go:107-110
        assertEquals(5004, FollowGrpcError.bizCodeFromError(
                FollowCodeError.newCode(FollowErrorCode.ERROR_UNAUTHORIZED.getCode())));
        assertEquals(5005, FollowGrpcError.bizCodeFromError(
                FollowCodeError.newCode(FollowErrorCode.ERROR_FORBIDDEN.getCode())));
        assertEquals(9999, FollowGrpcError.bizCodeFromError(
                FollowCodeError.newCode(9999)),
                "newCode(未知码) 的 msg 回落 \"FAIL\"，但 code 原样取 9999");
    }

    @Test
    @DisplayName("⚠️ CodeError 分支只判顶层、不沿 cause 链 —— Go 用的是直接类型断言，不是 errors.As")
    void codeErrorBranchDoesNotWalkCauseChain() {
        // Go: err.(*CodeError) 只认顶层。包一层的 CodeError 在 Go 侧落 1015。
        RuntimeException wrapper = new RuntimeException("logic layer wrapped",
                FollowCodeError.newCode(FollowErrorCode.ERROR_UNAUTHORIZED.getCode()));
        assertEquals(1015, FollowGrpcError.bizCodeFromError(wrapper),
                "包了一层的 CodeError 必须落 1015；改成沿 cause 链查找即为语义漂移");

        // ⚠️ 这与 api 层取码的行为【恰好相反】：followcenter.go:57 用的是 errors.As，会沿链查找。
        // 两处不对称是 Go 源码的既有事实，Java 侧必须同样不对称。
    }

    @Test
    @DisplayName("⚠️ status 被包一层时：Go 的 FromError 用【外层】err.Error() 覆盖 message，反查必然落空")
    void wrappedStatusUsesOuterMessageSoLookupMisses() {
        // grpc-go v1.81.1 status.FromError 的 errors.As 分支：
        //     p := grpcStatus.Proto(); p.Message = err.Error(); return status.FromProto(p), true
        // ⇒ description 被外层错误文本覆盖，文案反查落空 → 退到 switch。
        StatusRuntimeException inner = emit(5007);           // INTERNAL + "database query failed"
        RuntimeException wrapped = new RuntimeException("follow-api 调用 follow-rpc 失败", inner);

        assertEquals(5001, FollowGrpcError.bizCodeFromError(wrapped),
                "被包一层的 status 必须退到 switch 兜底（INTERNAL→5001），不能还原出 5007 —— "
                        + "grpc-java 的 Status.fromThrowable 会保留原 description 而 Go 不会，"
                        + "故本类必须自行实现 Go 的两段式判定");
    }

    @Test
    @DisplayName("status 在 cause 链更深层时同样走外层 message（Go errors.As 命中后一律用外层 Error()）")
    void deeplyNestedStatusAlsoUsesOuterMessage() {
        StatusRuntimeException inner = emit(5201);           // INVALID_ARGUMENT + "self relation is not allowed"
        RuntimeException middle = new RuntimeException("中间层包装", inner);
        RuntimeException outer = new RuntimeException("最外层包装", middle);

        assertEquals(5003, FollowGrpcError.bizCodeFromError(outer),
                "深层嵌套同样退到 switch（INVALID_ARGUMENT→5003），不得还原 5201");
    }

    @Test
    @DisplayName("null 输入返回 200（Go 的防御性分支，两侧一致）")
    void nullErrorYieldsSuccess() {
        assertEquals(200, FollowGrpcError.bizCodeFromError(null));
    }

    @Test
    @DisplayName("无 description 的 status（description 为 null）不得抛 NPE，退到 switch 兜底")
    void statusWithoutDescriptionDoesNotThrow() {
        // Go: st.Message() 对无 description 的 status 返回空串 ⇒ 反查未命中 ⇒ switch
        StatusRuntimeException bare = Status.INTERNAL.withDescription(null).asRuntimeException();
        assertEquals(5001, FollowGrpcError.bizCodeFromError(bare));

        StatusRuntimeException bareUnknown = Status.UNKNOWN.asRuntimeException();
        assertEquals(1015, FollowGrpcError.bizCodeFromError(bareUnknown));
    }

    @Test
    @DisplayName("grpcError 构造出的 description 逐字等于 Go 的 GetErrMsg(bizCode)")
    void grpcErrorDescriptionMatchesGoLookup() {
        for (int bizCode : GO_CALL_SITES.keySet()) {
            assertEquals(FollowErrorMessages.getMsg(bizCode),
                    FollowGrpcError.grpcError(Status.Code.INTERNAL, bizCode).getStatus().getDescription());
        }
        // 未知码 → description 回落 "FAIL"（Go: status.Error(grpcCode, GetErrMsg(9999))）
        assertEquals("FAIL",
                FollowGrpcError.grpcError(Status.Code.INTERNAL, 9999).getStatus().getDescription());
    }

    @Test
    @DisplayName("grpcError 返回 StatusException —— onError(Throwable) 可直接接收，不给调用点加 throws 负担")
    void grpcErrorReturnsStatusException() {
        // protoc 生成的 Java 方法签名不声明 throws ⇒ onError(Throwable) 不能带 checked 异常。
        // grpcError 的返回值类型必须是 Throwable 而非 Exception，否则调用点被迫声明 throws。
        assertInstanceOf(StatusException.class,
                FollowGrpcError.grpcError(Status.Code.INTERNAL, 5001));
    }

    @Test
    @DisplayName("bizCodeFromError 对 server 侧 StatusException 同样生效（Go 两侧都走 FromError）")
    void serverSideStatusExceptionIsRecognised() {
        StatusException serverSide = FollowGrpcError.grpcError(
                Status.Code.NOT_FOUND, FollowErrorCode.ERROR_USER_NOT_FOUND.getCode());
        assertEquals(5101, FollowGrpcError.bizCodeFromError(serverSide),
                "server 侧构造的 StatusException 也必须能反查回 5101");
    }
}