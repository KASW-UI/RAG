package com.rag.favorite.favorite.common.errmsg;

import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * favorite 域 gRPC 错误双向转换 —— 1:1 对应 Go 端
 * {@code Sea-RideTheWind_golang/service/favorite/common/errmsg.go:78-137}：
 * {@code bizCodeByMsg} / {@code bizCodeFromMessage} / {@code GRPCError} / {@code BizCodeFromError}。
 *
 * <h2>本域的 wire 形态：业务码藏在 description 文案里</h2>
 *
 * <p>Go 侧发出错误时<b>不</b>把业务码写进 {@code grpc-status}，而是：
 * <pre>
 * func GRPCError(grpcCode codes.Code, bizCode int) error {
 *     return status.Error(grpcCode, GetErrMsg(bizCode))   // errmsg.go:104-106
 * }
 * </pre>
 * 即 wire 上只有「<b>标准</b> gRPC 码 + 业务文案」，业务码靠文案反查还原。
 * 这不是随手写法，是 favorite 域全部 56 个 {@code GRPCError} 调用点的统一约定。
 *
 * <h3>⚠️ 与 like 域的关键差异：like 有 trailer，本域不需要</h3>
 *
 * <p>like 域的 Go 端把业务码直接塞进 {@code grpc-status}（{@code codes.Code(5003)} 这种越界值），
 * 而 grpc-java 只认 17 个标准码（0-16），越界码会被降级成
 * {@code UNKNOWN + "Unknown code 5003"}，业务码与文案双双丢失 ⇒ like 侧必须自造 trailer 兜底
 * （见 {@code RAG/service/like/like-common/.../LikeGrpcError.java}）。
 *
 * <p><b>本域不存在这个问题</b>：favorite 的 Go 端只发出 7 种标准 gRPC 码
 * （实测分布：{@code Internal}×19、{@code InvalidArgument}×10、{@code NotFound}×9、
 * {@code PermissionDenied}×6、{@code AlreadyExists}×6、{@code Unavailable}×4、
 * {@code FailedPrecondition}×2），<b>一个越界码都没有</b>；
 * 且业务码的载体是 description 文案，而 grpc-java 对 description 是<b>逐字节保真</b>的
 * （不像越界 {@code grpc-status} 会被覆写）。
 * ⇒ 因此本类<b>不引入自定义 trailer</b>：加一个 Go 侧不存在的 wire 元素属于新增行为，
 * 而且会让 Go / Java 两侧的 wire 形态不再对称。
 *
 * <h3>⚠️ 与 like 域的第二个关键差异：禁止为「躲开熔断失败码」而改写 grpc 码</h3>
 *
 * <p>like 域刻意把业务错误映射成 {@code INVALID_ARGUMENT} / {@code UNKNOWN}，
 * 目的是避开 go-zero 熔断的失败名单。但<b>本域的 Go 端本来就大量发 {@code Internal} 与
 * {@code Unavailable}</b>（例如 {@code rpc/internal/logic/delete_favorite_logic.go:72} 的
 * {@code GRPCError(codes.Internal, ErrorDbSelect)}、
 * {@code service/favorite/rpc/internal/logic/helper.go:115} 的
 * {@code GRPCError(codes.Unavailable, ErrorServerCommon)}），
 * 即 Go 侧<b>确实</b>把这些计入依赖失败统计。
 * ⇒ {@link #grpcError} <b>原样透传</b>调用方给的 gRPC 码；
 * 若在此处把它们改写成 {@code UNKNOWN}，就会让 favorite 的 DB 故障不再计入熔断，
 * 那<b>是新增行为，不是对齐</b>。
 *
 * <h2>框架职责划分</h2>
 * <ul>
 *   <li><b>Spring 不参与</b>：本组件是 gRPC 错误契约，与 HTTP 无关；
 *       Spring 的 {@code ResponseStatusException} / {@code ProblemDetail} 都是 HTTP 错误模型，
 *       不承载 gRPC 码，套用反而会把业务码降级成 HTTP status（破坏 L1）。</li>
 *   <li><b>grpc-java 承担传输</b>：{@link Status} / {@link StatusException} 由
 *       {@code io.grpc:grpc-stub}（传递依赖 {@code grpc-api}）提供，server runtime 负责把它写上 wire。</li>
 *   <li><b>业务码 ↔ 文案的映射表由本类持有</b>：grpc-java 不认识 favorite 的 24 个业务码，
 *       也没有任何「按 description 反查业务码」的机制 ⇒ 这部分必须自定义。</li>
 *   <li><b>本类不定义业务异常类型</b>：Go favorite 域<b>没有</b> {@code CodeError} 结构体
 *       （unlike like / message 域），所以 Java 侧也不该凭空造一个
 *       —— 否则 {@code bizCodeFromError} 会多出一条 Go 侧不存在的分支。</li>
 * </ul>
 */
public final class FavoriteGrpcError {

    private FavoriteGrpcError() {
        // 工具类，禁止 new
    }

    /**
     * 对应 Go {@code status.Error(codes.Code(code), msg)}：
     * 以给定的<b>标准</b> gRPC 码构造 status，description 放业务码查表得到的文案。
     *
     * <p><b>⚠️ 调用方必须交给 {@code responseObserver.onError(...)}，不能 {@code throw}</b>：
     * grpc-java 在 {@code UnaryServerCallListener.onHalfClose} 里调用业务方法，
     * 在那里 {@code throw} 会让异常逃出流监听回调，被当成<b>传输层内部错误</b>，
     * description 被替换成 {@code "Application error processing RPC"} —— <b>文案当场全丢</b>，
     * 而文案正是本域业务码的载体（丢了就再也还原不出业务码）。
     * {@code onError(Throwable)} 不要求 checked，所以返回 {@link StatusException} 不带来
     * {@code throws} 负担（protoc 生成的方法签名也不声明 throws）。
     *
     * <p>用法：
     * <pre>{@code
     * responseObserver.onError(FavoriteGrpcError.grpcError(
     *         Status.Code.INTERNAL, FavoriteErrorCode.ERROR_DB_SELECT.getCode()));
     * }</pre>
     */
    public static StatusException grpcError(Status.Code grpcCode, int bizCode) {
        return Status.fromCode(grpcCode)
                .withDescription(FavoriteErrorMessages.getMsg(bizCode))
                .asException();
    }

    /**
     * msg → code 反查表，对应 Go 的
     * <pre>
     * var bizCodeByMsg = func() map[string]int {
     *     lookup := make(map[string]int, len(codeMsg))
     *     for code, msg := range codeMsg {
     *         if code == Success || code == Error { continue }
     *         if _, exists := lookup[msg]; !exists { lookup[msg] = code }
     *     }
     *     return lookup
     * }()                                                    // errmsg.go:78-89
     * </pre>
     *
     * <p>对应行为：
     * <ul>
     *   <li>排除 {@link FavoriteErrorCode#SUCCESS} / {@link FavoriteErrorCode#ERROR} ——
     *       它们的文案（{@code "OK"} / {@code "FAIL"}）过于泛化，纳入反查会误命中；</li>
     *   <li>first-write-wins 去重（Go 用 {@code if _, exists := lookup[msg]; !exists}），
     *       这里用 {@link Map#putIfAbsent} 表达同一语义。⚠️ Go 遍历 map 顺序随机，
     *       所以「一旦出现重复文案，反查结果就随机」；当前 24 条文案两两互不相同，
     *       该分支实际不可达，由 {@code FavoriteErrorMessagesContractTest#messagesArePairwiseDistinct} 钉死。</li>
     * </ul>
     *
     * <p><b>这张表是本组件的核心而非附属</b>：本域唯一能让「非标准码」往返的办法就是文案反查。
     * 最硬的证据是 {@code ErrorFavoriteHistoryBlocked = 5304} ——
     * {@code FAILED_PRECONDITION} 不在 Go {@code BizCodeFromError} 的 switch 白名单里，
     * 还原 5304 <b>只能</b>靠这张表（Go 端测试
     * {@code rpc/internal/server/favorite_fact_rpc_test.go:54-55} 就是这么断言的）。
     */
    private static final Map<String, Integer> BIZ_CODE_BY_MSG;

    static {
        Map<String, Integer> reverse = new HashMap<>();
        for (Map.Entry<Integer, String> entry : FavoriteErrorMessages.MSG_BY_CODE.entrySet()) {
            int code = entry.getKey();
            if (code == FavoriteErrorCode.SUCCESS.getCode()
                    || code == FavoriteErrorCode.ERROR.getCode()) {
                continue;
            }
            reverse.putIfAbsent(entry.getValue(), code);
        }
        BIZ_CODE_BY_MSG = Collections.unmodifiableMap(reverse);
    }

    /**
     * 对应 Go {@code func bizCodeFromMessage(msg string) (int, bool)}（未导出）。
     *
     * <p>本类内私有：Go 侧也没有导出它，唯一调用点是 {@link #bizCodeFromError}。
     */
    private static Integer bizCodeFromMessage(String msg) {
        return BIZ_CODE_BY_MSG.get(msg);
    }

    /**
     * 对应 Go {@code func BizCodeFromError(err error) int}（{@code errmsg.go:108-137}）：
     * 从任意 error 里还原 favorite 业务码。
     *
     * <p><b>判定顺序即语义（顺序不可调换）</b>：
     * <ol>
     *   <li>{@code err == null} → {@link FavoriteErrorCode#SUCCESS}（200）——防御性取值，
     *       Go 侧对应调用点都在 {@code if err != nil} 之内，该分支实际不可达；</li>
     *   <li><b>不是 gRPC status</b> → {@link FavoriteErrorCode#CODE_SERVER_BUSY}（1015）。
     *       Go 侧 {@code status.FromError(err)} 对普通 error（如 GORM 的 {@code dbErr}）返回
     *       {@code ok=false}；favorite-rpc 大量把裸 DB error 传进来（如
     *       {@code rpc/internal/logic/delete_favorite_logic.go:70}），这条分支是热路径；</li>
     *   <li><b>是 gRPC status</b> → 先按 description <b>反查文案</b>，命中即返回
     *       （这是还原 5201/5202/5203/5301-5304/5101-5105 等非标准业务码的唯一途径）；</li>
     *   <li>反查未命中 → 按标准 gRPC 码类别兜底映射，见 {@link #mapStatusCodeToBizCode}；</li>
     *   <li>兜底也未命中（{@code default}）→ {@link FavoriteErrorCode#CODE_SERVER_BUSY}（1015）。</li>
     * </ol>
     */
    public static int bizCodeFromError(Throwable err) {
        if (err == null) {
            return FavoriteErrorCode.SUCCESS.getCode();
        }

        Status status = extractStatus(err);
        if (status == null) {
            // Go: status.FromError 返回 ok=false ⇒ return CodeServerBusy
            return FavoriteErrorCode.CODE_SERVER_BUSY.getCode();
        }

        Integer fromMsg = bizCodeFromMessage(status.getDescription());
        if (fromMsg != null) {
            return fromMsg;
        }

        return mapStatusCodeToBizCode(status.getCode());
    }

    /**
     * 对应 Go {@code BizCodeFromError} 末尾的 {@code switch st.Code()}（{@code errmsg.go:122-136}）。
     *
     * <p>白名单<b>恰好 6 个</b>：{@code InvalidArgument} / {@code Unauthenticated} /
     * {@code PermissionDenied} / {@code AlreadyExists} / {@code NotFound} / {@code Internal}，
     * 其余（含本域常用的 {@code FailedPrecondition} 与 {@code Unavailable}）一律走
     * {@code default} → 1015。
     *
     * <p>⚠️ <b>不要因为「{@code FailedPrecondition} 看着该映射」就加进白名单</b>：
     * Go 侧没有这条映射，加上会让本域最常见的「收藏暂不可撤回」在<b>文案丢失</b>时
     * 从 1015 变成 5304 —— 那是新增行为。文案丢失的降级场景应保持 Go 的 1015。
     */
    private static int mapStatusCodeToBizCode(Status.Code grpcCode) {
        if (grpcCode == null) {
            return FavoriteErrorCode.CODE_SERVER_BUSY.getCode();
        }
        switch (grpcCode) {
            case INVALID_ARGUMENT:
                return FavoriteErrorCode.ERROR_INVALID_PARAM.getCode();
            case UNAUTHENTICATED:
                return FavoriteErrorCode.ERROR_UNAUTHORIZED.getCode();
            case PERMISSION_DENIED:
                return FavoriteErrorCode.ERROR_FORBIDDEN.getCode();
            case ALREADY_EXISTS:
                return FavoriteErrorCode.ERROR_ALREADY_EXIST.getCode();
            case NOT_FOUND:
                return FavoriteErrorCode.ERROR_NOT_FOUND.getCode();
            case INTERNAL:
                return FavoriteErrorCode.ERROR_SERVER_COMMON.getCode();
            default:
                return FavoriteErrorCode.CODE_SERVER_BUSY.getCode();
        }
    }

    /**
     * 同时识别 server 端 {@link StatusException} 与 client 端 {@link StatusRuntimeException}。
     *
     * <p><b>⚠️ 这里刻意<b>不</b>沿 cause 链向上找</b>：Go 的 {@code status.FromError(err)}
     * 只检查<b>顶层</b> error 是否实现 {@code GRPCStatus()}，不遍历 cause 链；
     * 而 grpc-java 的 {@code Status.fromThrowable} 会沿 cause 链回溯。
     * 若直接用 {@code Status.fromThrowable}，一个被业务代码包了一层的
     * {@code StatusException} 在 Java 侧会还原出业务码、而在 Go 侧会落 1015 ——
     * 两侧分叉。故此处按 Go 的单层语义手写判定。
     */
    private static Status extractStatus(Throwable err) {
        if (err instanceof StatusException statusException) {
            return statusException.getStatus();
        }
        if (err instanceof StatusRuntimeException statusRuntimeException) {
            return statusRuntimeException.getStatus();
        }
        return null;
    }
}