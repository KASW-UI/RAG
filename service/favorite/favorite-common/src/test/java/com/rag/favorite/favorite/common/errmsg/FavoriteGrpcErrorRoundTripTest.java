package com.rag.favorite.favorite.common.errmsg;

import io.grpc.CallOptions;
import io.grpc.ManagedChannel;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * <b>真 in-process gRPC server + 真 channel</b> 的 favorite 业务码往返守门。
 *
 * <p><b>为什么必须过真实传输层</b>：favorite 的业务码<b>不</b>放在 {@code grpc-status} 里
 * （那是 Go 端越界码的老问题，like 域因此才需要自造 trailer），
 * 而是<b>藏在 description 文案里</b>（见 {@link FavoriteGrpcError}）。
 * 「description 能不能逐字节穿过传输层、并被 {@code bizCodeFromError} 还原成业务码」
 * 是整个组件的<b>承重假设</b>。
 * 只断言 {@code grpcError(...)} 对象的值，证明不了它真的穿过了传输层
 * ——本项目此前已多次栽在「单测全绿但真实链路上没生效」上（AGENTS.md §3.3）。
 *
 * <p><b>Go 侧对照</b>：真源 {@code Sea-RideTheWind_golang/service/favorite/common/errmsg.go:104-137}，
 * 端到端行为由 Go 测试 {@code rpc/internal/server/favorite_fact_rpc_test.go:54-55} 钉死
 * （断言 wire 上是 {@code codes.FailedPrecondition} 且 {@code BizCodeFromError(err) == 5304}）。
 * 本测试是它在 Java 侧的对等物。
 */
class FavoriteGrpcErrorRoundTripTest {

    private static final String SERVICE = "probe.Ping";
    private static Server server;
    private static ManagedChannel channel;

    /**
     * Go favorite-rpc <b>真实发出</b>的「业务码 → gRPC 标准码」组合，逐条抄自 Go 调用点。
     *
     * <p>⚠️ 这张表同时是「本域没有越界 grpc-status」的证据：全部 7 个值都是 grpc-java 认得的
     * 标准码（0-16），所以 Java 侧可以直接透传，不需要 like 域那套 trailer 兜底。
     * 它也是「禁止为躲开熔断失败码而改写 grpc 码」的依据 ——
     * {@code INTERNAL} / {@code UNAVAILABLE} 在 Go 侧就是真实发出的，
     * 改写它们会让 favorite 的 DB / 下游故障不再计入熔断统计，那是新增行为而非对齐。
     */
    private static final Map<Integer, Status.Code> GO_CALL_SITES = new LinkedHashMap<>();

    static {
        // rpc/internal/logic/delete_favorite_logic.go:61 — 收藏记录不存在
        GO_CALL_SITES.put(FavoriteErrorCode.ERROR_FAVORITE_NOT_FOUND.getCode(), Status.Code.NOT_FOUND);
        // rpc/internal/logic/delete_favorite_logic.go:71 — DB 查询失败
        GO_CALL_SITES.put(FavoriteErrorCode.ERROR_DB_SELECT.getCode(), Status.Code.INTERNAL);
        // rpc/internal/logic/delete_favorite_logic.go:74 — 收藏归属不符
        GO_CALL_SITES.put(FavoriteErrorCode.ERROR_FORBIDDEN.getCode(), Status.Code.PERMISSION_DENIED);
        // rpc/internal/logic/delete_favorite_logic.go:86 — 事实链路迁移未完成，禁止撤回
        GO_CALL_SITES.put(FavoriteErrorCode.ERROR_FAVORITE_HISTORY_BLOCKED.getCode(),
                Status.Code.FAILED_PRECONDITION);
        // rpc/internal/logic/delete_favorite_folder_logic.go:90 — DB 更新失败
        GO_CALL_SITES.put(FavoriteErrorCode.ERROR_DB_UPDATE.getCode(), Status.Code.INTERNAL);
        // rpc/internal/logic/create_favorite_folder_logic.go:52 — 入参解析失败
        GO_CALL_SITES.put(FavoriteErrorCode.ERROR_INVALID_PARAM.getCode(), Status.Code.INVALID_ARGUMENT);
        // rpc/internal/logic/create_favorite_folder_logic.go:61 — 收藏夹名为空
        GO_CALL_SITES.put(FavoriteErrorCode.ERROR_FAVORITE_FOLDER_NAME_EMPTY.getCode(),
                Status.Code.INVALID_ARGUMENT);
        // rpc/internal/logic/create_favorite_folder_logic.go:94 — 雪花 ID 生成失败
        GO_CALL_SITES.put(FavoriteErrorCode.ERROR_GENERATE_ID.getCode(), Status.Code.INTERNAL);
        // rpc/internal/logic/get_favorites_by_folder_logic.go:61 — 收藏夹不存在
        GO_CALL_SITES.put(FavoriteErrorCode.ERROR_FAVORITE_FOLDER_NOT_FOUND.getCode(), Status.Code.NOT_FOUND);
        // rpc/internal/logic/update_favorite_folder_name_logic.go:98 — 收藏夹重名
        GO_CALL_SITES.put(FavoriteErrorCode.ERROR_FAVORITE_FOLDER_NAME_EXISTS.getCode(),
                Status.Code.ALREADY_EXISTS);
        // rpc/internal/logic/create_favorite_logic.go:123 — 重复收藏
        GO_CALL_SITES.put(FavoriteErrorCode.ERROR_FAVORITE_ALREADY_EXIST.getCode(),
                Status.Code.ALREADY_EXISTS);
        // rpc/internal/logic/helper.go:115 — 下游文章快照不可用
        GO_CALL_SITES.put(FavoriteErrorCode.ERROR_SERVER_COMMON.getCode(), Status.Code.UNAVAILABLE);
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
                        // ServerCalls 从 onHalfClose(ServerCalls.java:182) 里调业务方法，
                        // throw 会逃出流监听回调，被当成传输层内部错误，
                        // description 被替换成 "Application error processing RPC" —— 文案即业务码，全丢。
                        {
                            int bizCode = Integer.parseInt(request);
                            Status.Code grpcCode = java.util.Objects.requireNonNull(
                                    GO_CALL_SITES.get(bizCode),
                                    "GO_CALL_SITES 里没有登记业务码 " + bizCode
                                            + "：Go favorite-rpc 从未用该码发过 gRPC 错误，"
                                            + "凭空断言即为制造 Go 侧不存在的行为");
                            observer.onError(FavoriteGrpcError.grpcError(grpcCode, bizCode));
                        }))
                .addMethod(RAW_METHOD, ServerCalls.asyncUnaryCall((request, observer) -> {
                    int split = request.indexOf('|');
                    Status.Code grpcCode = Status.Code.valueOf(request.substring(0, split));
                    observer.onError(Status.fromCode(grpcCode)
                            .withDescription(request.substring(split + 1))
                            .asException());
                }))
                .addMethod(THROW_METHOD, ServerCalls.asyncUnaryCall((request, observer) -> {
                    // 故意让异常逃出业务方法（而不是走 observer.onError）：
                    // 见下面的 descriptionLosesBizCodeWhenServerThrows。
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
    @DisplayName("Go 真实发出的全部 12 个调用点：业务码经真传输层无损往返 —— 整个方案的承重假设")
    void everyGoCallSiteRecoversItsBizCodeOverRealTransport() {
        assertEquals(12, GO_CALL_SITES.size(), "本表应覆盖 Go favorite-rpc 的 12 个代表性调用点");

        for (Map.Entry<Integer, Status.Code> callSite : GO_CALL_SITES.entrySet()) {
            int bizCode = callSite.getKey();
            StatusRuntimeException thrown = emit(bizCode);

            // ① wire 上的 gRPC 码必须原样透传
            assertEquals(callSite.getValue(), thrown.getStatus().getCode(),
                    "grpc-status 漂移：bizCode=" + bizCode + " 的标准码没透传"
                            + "（Go 调用点用的是 " + callSite.getValue() + "）");
            // ② description 必须逐字节等于 Go 的文案（这正是业务码的载体）
            assertEquals(FavoriteErrorMessages.getMsg(bizCode), thrown.getStatus().getDescription(),
                    "description 漂移：bizCode=" + bizCode + " 的文案没逐字穿过传输层");
            // ③ 业务码必须能还原回来
            assertEquals(bizCode, FavoriteGrpcError.bizCodeFromError(thrown),
                    "业务码 " + bizCode + " 没能跨传输还原 —— description 反查这条链路被破坏了");
        }
    }

    @Test
    @DisplayName("Go 测试同款场景：wire 上是 FAILED_PRECONDITION、业务码还原为 5304（收藏暂不可撤回）")
    void failedPreconditionRecoversHistoryBlockedViaMessageLookup() {
        // Go: rpc/internal/server/favorite_fact_rpc_test.go:54-55
        //   status.Code(err) == codes.FailedPrecondition
        //   favoritecommon.BizCodeFromError(err) == favoritecommon.ErrorFavoriteHistoryBlocked (5304)
        // 关键点：FAILED_PRECONDITION 不在 Go BizCodeFromError 的 switch 白名单里（errmsg.go:122-136），
        // 还原 5304 只能靠 description 文案反查 —— 这条断言锁死的就是那个唯一途径。
        StatusRuntimeException thrown = emit(FavoriteErrorCode.ERROR_FAVORITE_HISTORY_BLOCKED.getCode());

        assertEquals(Status.Code.FAILED_PRECONDITION, thrown.getStatus().getCode());
        assertEquals("收藏暂不可撤回", thrown.getStatus().getDescription());
        assertEquals(FavoriteErrorCode.ERROR_FAVORITE_HISTORY_BLOCKED.getCode(),
                FavoriteGrpcError.bizCodeFromError(thrown));
    }

    @Test
    @DisplayName("中文文案逐字节穿过传输层，不出现编码损坏")
    void nonAsciiDescriptionSurvivesTransportVerbatim() {
        // 只用 GO_CALL_SITES 里已登记真实 Go 调用点的码：Go favorite-rpc 从未用 gRPC 发出 5004
        // （ErrorUnauthorized 只在 api 侧作为 int 返回值出现，见
        //  api/internal/logic/favorite/create_favorite_folder_logic.go:39），
        // 因此「5004 走 UNAUTHENTICATED 上线」在 Go 侧不存在，不应在这里凭空断言。
        assertEquals("收藏夹名称不能为空",
                emit(FavoriteErrorCode.ERROR_FAVORITE_FOLDER_NAME_EMPTY.getCode())
                        .getStatus().getDescription());
        assertEquals("收藏暂不可撤回",
                emit(FavoriteErrorCode.ERROR_FAVORITE_HISTORY_BLOCKED.getCode())
                        .getStatus().getDescription());
        assertEquals("数据库查询失败",
                emit(FavoriteErrorCode.ERROR_DB_SELECT.getCode()).getStatus().getDescription());
    }

    // ==================== 2. grpc 码透传（禁止像 like 那样改写） ====================

    @Test
    @DisplayName("INTERNAL / UNAVAILABLE 原样上线——不得为躲开熔断失败名单被改写成 UNKNOWN")
    void breakerRelevantGrpcCodesPassThroughVerbatim() {
        // Go 侧确实发出 INTERNAL（19 处）与 UNAVAILABLE（4 处），即 Go 确实把这些计入依赖失败统计。
        // 若按 like 域的做法把它们改写成 UNKNOWN / INVALID_ARGUMENT，favorite 的 DB 故障与
        // 下游不可用就不再计入熔断 —— 那是新增行为，不是对齐。
        StatusRuntimeException internal =
                emit(FavoriteErrorCode.ERROR_DB_SELECT.getCode());
        assertEquals(Status.Code.INTERNAL, internal.getStatus().getCode(),
                "5007 必须仍以 INTERNAL 上线（Go: delete_favorite_logic.go:71）");

        StatusRuntimeException unavailable =
                emit(FavoriteErrorCode.ERROR_SERVER_COMMON.getCode());
        assertEquals(Status.Code.UNAVAILABLE, unavailable.getStatus().getCode(),
                "5001 必须仍以 UNAVAILABLE 上线（Go: helper.go:115）");

        // 同时确认业务码仍能还原（证明透传没有牺牲本组件的核心职责）。
        assertEquals(FavoriteErrorCode.ERROR_DB_SELECT.getCode(),
                FavoriteGrpcError.bizCodeFromError(internal));
        assertEquals(FavoriteErrorCode.ERROR_SERVER_COMMON.getCode(),
                FavoriteGrpcError.bizCodeFromError(unavailable));
    }

    // ==================== 3. onError vs throw：文案即业务码 ====================

    @Test
    @DisplayName("业务方法内异常逃出（未走 onError）会吞掉 description 并让业务码丢失 —— 反证必须走 onError")
    void descriptionLosesBizCodeWhenServerThrows() {
        // 这是本组件最容易被后人「顺手优化」掉的坑：把 observer.onError(...) 改成让异常逃出
        // 流监听回调后，传输层会把 description 兜底成 "Application error processing RPC"，
        // 而 description 就是业务码的载体 ⇒ 业务码彻底还原不出来。
        //
        // ⚠️ 以下三个值都是【grpc-java 1.63.0 实测值】，不是按文档推断的：
        //   description → "Application error processing RPC"
        //   code        → UNKNOWN（**不是** INTERNAL）
        // 三者任一变化都会改变降级语义，故按实测锁定并在变化时报警。
        StatusRuntimeException thrown = call(THROW_METHOD, "whatever");

        assertEquals("Application error processing RPC", thrown.getStatus().getDescription(),
                "description 应被传输层兜底文案覆写 —— 若此断言变了，说明 grpc-java 的"
                        + "兜底行为变了，需重新评估本组件「文案即业务码载体」的前提");
        assertEquals(Status.Code.UNKNOWN, thrown.getStatus().getCode(),
                "实测：逃出异常被兜成 UNKNOWN（不是 INTERNAL）");

        // 兜底文案不在反查表里 ⇒ 只能走 switch；UNKNOWN 不在 Go switch 白名单 ⇒ 落 default 1015。
        // 与真实业务码 5304 完全不符：客户端会看到「服务繁忙」而不是「收藏暂不可撤回」。
        assertEquals(FavoriteErrorCode.CODE_SERVER_BUSY.getCode(),
                FavoriteGrpcError.bizCodeFromError(thrown),
                "文案被覆写后只剩 UNKNOWN → Go switch 的 default → 1015，业务码丢失");
        assertNotEquals(FavoriteErrorCode.ERROR_FAVORITE_HISTORY_BLOCKED.getCode(),
                FavoriteGrpcError.bizCodeFromError(thrown),
                "逃出异常时业务码必然丢失（这正是必须走 onError 的原因）");
    }

    // ==================== 4. 文案反查未命中时的标准码兜底（Go switch） ====================

    @Test
    @DisplayName("文案不在反查表时按 Go switch 的 6 个白名单兜底")
    void unknownDescriptionFallsBackToWhitelistedGrpcCodes() {
        // Go errmsg.go:122-136 的 switch 白名单恰好 6 个，逐条对齐。
        assertEquals(FavoriteErrorCode.ERROR_INVALID_PARAM.getCode(),
                FavoriteGrpcError.bizCodeFromError(call(RAW_METHOD, "INVALID_ARGUMENT|某个不在表里的文案")),
                "Go: codes.InvalidArgument → ErrorInvalidParam(5003)");
        assertEquals(FavoriteErrorCode.ERROR_UNAUTHORIZED.getCode(),
                FavoriteGrpcError.bizCodeFromError(call(RAW_METHOD, "UNAUTHENTICATED|某个不在表里的文案")),
                "Go: codes.Unauthenticated → ErrorUnauthorized(5004)");
        assertEquals(FavoriteErrorCode.ERROR_FORBIDDEN.getCode(),
                FavoriteGrpcError.bizCodeFromError(call(RAW_METHOD, "PERMISSION_DENIED|某个不在表里的文案")),
                "Go: codes.PermissionDenied → ErrorForbidden(5005)");
        assertEquals(FavoriteErrorCode.ERROR_ALREADY_EXIST.getCode(),
                FavoriteGrpcError.bizCodeFromError(call(RAW_METHOD, "ALREADY_EXISTS|某个不在表里的文案")),
                "Go: codes.AlreadyExists → ErrorAlreadyExist(5006)");
        assertEquals(FavoriteErrorCode.ERROR_NOT_FOUND.getCode(),
                FavoriteGrpcError.bizCodeFromError(call(RAW_METHOD, "NOT_FOUND|某个不在表里的文案")),
                "Go: codes.NotFound → ErrorNotFound(5002)");
        assertEquals(FavoriteErrorCode.ERROR_SERVER_COMMON.getCode(),
                FavoriteGrpcError.bizCodeFromError(call(RAW_METHOD, "INTERNAL|某个不在表里的文案")),
                "Go: codes.Internal → ErrorServerCommon(5001)");
    }

    @Test
    @DisplayName("白名单外的标准码一律落 1015——含本域常用的 FAILED_PRECONDITION 与 UNAVAILABLE")
    void nonWhitelistedGrpcCodesFallBackToServerBusy() {
        // Go errmsg.go:135-136 的 default → CodeServerBusy。
        // ⚠️ FAILED_PRECONDITION 在 Go 侧【没有】映射到 5304：文案丢失时它落 default（1015）。
        //    不得因为「语义上该映射」就加进白名单 —— 那会让降级场景的行为与 Go 分叉。
        for (String code : new String[]{"FAILED_PRECONDITION", "UNAVAILABLE", "DEADLINE_EXCEEDED",
                "RESOURCE_EXHAUSTED", "DATA_LOSS", "UNIMPLEMENTED", "ABORTED", "CANCELLED",
                "OUT_OF_RANGE", "UNKNOWN", "OK"}) {
            assertEquals(FavoriteErrorCode.CODE_SERVER_BUSY.getCode(),
                    FavoriteGrpcError.bizCodeFromError(call(RAW_METHOD, code + "|文案已丢失")),
                    "grpc 码 " + code + " 不在 Go switch 白名单里，必须落 1015");
        }
    }

    @Test
    @DisplayName("\"OK\" / \"FAIL\" 不参与反查（Go 显式排除 Success / Error）")
    void okAndFailAreExcludedFromReverseLookup() {
        // Go errmsg.go:81-83：if code == Success || code == Error { continue }
        // 若不排除，一条恰好描述为 "FAIL" 的错误就会被误还原成 500。
        assertEquals(FavoriteErrorCode.ERROR_SERVER_COMMON.getCode(),
                FavoriteGrpcError.bizCodeFromError(call(RAW_METHOD, "INTERNAL|FAIL")),
                "description=\"FAIL\" 不应被反查成 500，而应走 switch 得 5001");

        // "OK" 同理：查不到反查项 → 落 switch。
        assertEquals(FavoriteErrorCode.CODE_SERVER_BUSY.getCode(),
                FavoriteGrpcError.bizCodeFromError(call(RAW_METHOD, "UNAVAILABLE|OK")),
                "description=\"OK\" 不应被反查成 200，而应走 default 得 1015");
    }

    // ==================== 5. 非 status 错误 / null（本地语义，无需传输层） ====================

    @Test
    @DisplayName("非 gRPC status 的 Throwable 一律落 1015（Go status.FromError 的 ok=false 分支）")
    void nonStatusThrowableFallsBackToServerBusy() {
        // Go status.FromError 对普通 error 返回 ok=false ⇒ return CodeServerBusy（errmsg.go:113-116）。
        // favorite-rpc 大量把裸 DB error 传进 BizCodeFromError，是热路径，例如
        // rpc/internal/logic/delete_favorite_logic.go:70 的 logger.LogBusinessErr(..., dbErr, ...)。
        assertEquals(FavoriteErrorCode.CODE_SERVER_BUSY.getCode(),
                FavoriteGrpcError.bizCodeFromError(new RuntimeException("db error")),
                "普通异常必须落 1015");

        assertEquals(FavoriteErrorCode.CODE_SERVER_BUSY.getCode(),
                FavoriteGrpcError.bizCodeFromError(new java.io.IOException("io")),
                "受检异常同样落 1015");

        // ⚠️ 包了一层的 StatusException 必须仍落 1015：Go 的 status.FromError 只看顶层，
        // 不沿 cause 链回溯（grpc-java 的 Status.fromThrowable 会回溯），两侧必须一致。
        RuntimeException wrapped = new RuntimeException("wrapper",
                FavoriteGrpcError.grpcError(Status.Code.FAILED_PRECONDITION,
                        FavoriteErrorCode.ERROR_FAVORITE_HISTORY_BLOCKED.getCode()));
        assertEquals(FavoriteErrorCode.CODE_SERVER_BUSY.getCode(),
                FavoriteGrpcError.bizCodeFromError(wrapped),
                "包了一层的 status 必须落 1015（Go 单层判定），不能沿 cause 链还原成 5304");
    }

    @Test
    @DisplayName("null 输入返回 200（Go 的防御性分支，两侧一致）")
    void nullErrorYieldsSuccess() {
        // Go errmsg.go:109-111：if err == nil { return Success }
        assertEquals(FavoriteErrorCode.SUCCESS.getCode(),
                FavoriteGrpcError.bizCodeFromError(null));
    }
}