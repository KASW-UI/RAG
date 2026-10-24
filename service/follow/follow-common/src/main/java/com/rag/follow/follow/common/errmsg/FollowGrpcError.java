package com.rag.follow.follow.common.errmsg;

import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * follow 域 gRPC 错误双向转换 —— 1:1 对应 Go 端
 * {@code Sea-RideTheWind_golang/service/follow/common/errmsg.go:76-133}：
 * {@code bizCodeByMsg} / {@code GRPCError} / {@code BizCodeFromError}。
 *
 * <h2>本域的 wire 形态：业务码藏在 description 文案里</h2>
 *
 * <p>Go 侧发出错误时<b>不</b>把业务码写进 {@code grpc-status}，而是：
 * <pre>
 * func GRPCError(grpcCode codes.Code, bizCode int) error {
 *     return status.Error(grpcCode, GetErrMsg(bizCode))   // errmsg.go:97-99
 * }
 * </pre>
 * 即 wire 上只有「<b>标准</b> gRPC 码 + 业务文案」，业务码靠文案反查还原。
 *
 * <p><b>⚠️ 文案反查不是可选优化，而是本域唯一的还原途径</b>：
 * follow-rpc 实际发出的 4 种 gRPC 码<b>全部落在</b> {@link #mapStatusCodeToBizCode} 的
 * switch 白名单内（{@code Internal}×多、{@code NotFound}、{@code InvalidArgument}、
 * {@code PermissionDenied}），但白名单只能还原出<b>粗粒度</b>的码：
 * <table border="1">
 *   <caption>Go 真实调用点：switch 兜底会给出错误的码</caption>
 *   <tr><th>调用点</th><th>gRPC 码</th><th>业务码</th><th>仅靠 switch 兜底得到</th></tr>
 *   <tr><td>{@code helper.go:29,39}</td><td>INVALID_ARGUMENT</td><td>5003</td><td>5003 ✅</td></tr>
 *   <tr><td>{@code helper.go:32}</td><td>INVALID_ARGUMENT</td><td>5201 自关系</td><td>5003 ❌</td></tr>
 *   <tr><td>{@code followlogic.go:55}</td><td>PERMISSION_DENIED</td><td>5202 已拉黑</td><td>5005 ❌</td></tr>
 *   <tr><td>{@code helper.go:86,91}</td><td>NOT_FOUND</td><td>5101 用户不存在</td><td>5002 ❌</td></tr>
 *   <tr><td>{@code helper.go:88}</td><td>INTERNAL</td><td>5001</td><td>5001 ✅</td></tr>
 *   <tr><td>各 logic 的 DB 分支</td><td>INTERNAL</td><td>5007/5008</td><td>5001 ❌</td></tr>
 * </table>
 * 标 ❌ 的 5 类场景里，{@link #BIZ_CODE_BY_MSG} 是<b>唯一</b>能把 5201/5202/5101/5007/5008
 * 还原出来的途径。删掉这张表或跳过文案反查，api 层返回给客户端的业务码会全部退化。
 *
 * <p><b>本域不需要自定义 trailer</b>：follow-rpc 实际发出的 4 种 gRPC 码
 * （{@code Internal} / {@code NotFound} / {@code InvalidArgument} / {@code PermissionDenied}）
 * <b>全部是 grpc-java 认识的 17 个标准码之一</b>，不像 like 域那样把越界的
 * {@code codes.Code(5003)} 塞进 {@code grpc-status} 而被 grpc-java 降级成
 * {@code UNKNOWN + "Unknown code 5003"}。而 description 文案 grpc-java 是<b>逐字节保真</b>的
 * ⇒ 加一个 Go 侧不存在的 wire 元素属于新增行为，会让两侧 wire 形态不再对称。
 *
 * <h2>框架职责划分</h2>
 * <ul>
 *   <li><b>Spring 不参与</b>：本组件是 gRPC 错误契约，与 HTTP 无关；
 *       Spring 的 {@code ResponseStatusException} / {@code ProblemDetail} 都是 HTTP 错误模型，
 *       不承载 gRPC 码，套用反而会把业务码降级成 HTTP status（破坏 L1）。</li>
 *   <li><b>grpc-java 承担传输</b>：{@link Status} / {@link StatusException} 由
 *       {@code io.grpc:grpc-stub}（传递依赖 {@code grpc-api}）提供，
 *       server runtime 负责把它写上 wire。</li>
 *   <li><b>业务码 ↔ 文案的映射表由本类持有</b>：grpc-java 不认识 follow 的 16 个业务码，
 *       也没有任何「按 description 反查业务码」的机制 ⇒ 这部分必须自定义。</li>
 * </ul>
 */
public final class FollowGrpcError {

    private FollowGrpcError() {
        // 工具类，禁止 new
    }

    /**
     * 对应 Go {@code status.Error(grpcCode, GetErrMsg(bizCode))}：
     * 以给定的 <b>标准</b> gRPC 码构造 status，description 放业务码查表得到的文案。
     *
     * <p><b>⚠️ 调用方必须交给 {@code responseObserver.onError(...)}，不能 {@code throw}</b>：
     * grpc-java 在 {@code UnaryServerCallListener.onHalfClose} 里调用业务方法，
     * 在那里 {@code throw} 会让异常逃出流监听回调，被当成<b>传输层内部错误</b>，
     * description 被替换成 {@code "Application error processing RPC"} —— <b>文案当场全丢</b>，
     * 而文案正是本域业务码的载体（丢了就再也还原不出业务码，见类注释的表格）。
     * {@code onError(Throwable)} 不要求 checked，所以返回 {@link StatusException} 不带来
     * {@code throws} 负担（protoc 生成的方法签名也不声明 throws）。
     *
     * <p>用法：
     * <pre>{@code
     * responseObserver.onError(FollowGrpcError.grpcError(
     *         Status.Code.INTERNAL, FollowErrorCode.ERROR_DB_SELECT.getCode()));
     * }</pre>
     *
     * <p><b>不设默认超时/重试</b>：Go 侧 {@code GRPCError} 也不带，
     * 超时与熔断由 go-zero 的 client/server interceptor 承担（{@code timeoutinterceptor.go}、
     * {@code breakerinterceptor.go}），对应的 Java 侧配置属于 follow-api / follow-rpc 的
     * {@code FollowRpcClient} / grpc 拦截器，不在本组件职责内。
     */
    public static StatusException grpcError(Status.Code grpcCode, int bizCode) {
        return Status.fromCode(grpcCode)
                .withDescription(FollowErrorMessages.getMsg(bizCode))
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
     * }()                                                    // errmsg.go:76-87
     * </pre>
     *
     * <p>对应行为：
     * <ul>
     *   <li>排除 {@link FollowErrorCode#SUCCESS} / {@link FollowErrorCode#ERROR} ——
     *       它们的文案（{@code "OK"} / {@code "FAIL"}）过于泛化，纳入反查会误命中；</li>
     *   <li>first-write-wins 去重（Go 用 {@code if _, exists := lookup[msg]; !exists}），
     *       这里用 {@link Map#putIfAbsent} 表达同一语义。⚠️ Go 遍历 map 顺序随机，
     *       所以「一旦出现重复文案，反查结果就随机」；当前 16 条文案两两互不相同，
     *       该分支实际不可达，由 {@code FollowErrorMessagesContractTest#messagesArePairwiseDistinct} 钉死。</li>
     * </ul>
     */
    private static final Map<String, Integer> BIZ_CODE_BY_MSG;

    static {
        Map<String, Integer> reverse = new HashMap<>();
        for (Map.Entry<Integer, String> entry : FollowErrorMessages.MSG_BY_CODE.entrySet()) {
            int code = entry.getKey();
            if (code == FollowErrorCode.SUCCESS.getCode()
                    || code == FollowErrorCode.ERROR.getCode()) {
                continue;
            }
            reverse.putIfAbsent(entry.getValue(), code);
        }
        BIZ_CODE_BY_MSG = Collections.unmodifiableMap(reverse);
    }

    /** 对应 Go 未导出的 {@code func bizCodeFromMessage(msg string) (int, bool)}（未导出）。 */
    private static Integer bizCodeFromMessage(String msg) {
        return BIZ_CODE_BY_MSG.get(msg);
    }

    /**
     * 对应 Go {@code func BizCodeFromError(err error) int}（{@code errmsg.go:101-133}）：
     * 从任意 error 里还原 follow 业务码。
     *
     * <p><b>判定顺序即语义（顺序不可调换）</b>：
     * <ol>
     *   <li>{@code err == null} → {@link FollowErrorCode#SUCCESS}（200）；</li>
     *   <li><b>不是 gRPC status</b> → 先看顶层是不是 {@link FollowCodeError}，
     *       是则取它的 {@code code}；否则 {@link FollowErrorCode#CODE_SERVER_BUSY}（1015）。
     *       ⚠️ Go 这里用的是<b>直接类型断言</b> {@code err.(*CodeError)}，
     *       <b>不</b>沿 cause 链 —— 见 {@link #resolveStatus} 的说明，两处行为刻意不对称；</li>
     *   <li><b>是 gRPC status</b> → 先按 description <b>反查文案</b>，命中即返回；</li>
     *   <li>反查未命中 → 按标准 gRPC 码类别兜底映射，见 {@link #mapStatusCodeToBizCode}；</li>
     *   <li>兜底也未命中（{@code default}）→ {@link FollowErrorCode#CODE_SERVER_BUSY}（1015）。</li>
     * </ol>
     */
    public static int bizCodeFromError(Throwable err) {
        if (err == null) {
            return FollowErrorCode.SUCCESS.getCode();
        }

        ResolvedStatus resolved = resolveStatus(err);
        if (resolved == null) {
            // Go: status.FromError 返回 ok=false
            // ⚠️ 直接类型断言，只判顶层，不走 getCause() 链
            if (err instanceof FollowCodeError followCodeError) {
                return followCodeError.getCode();
            }
            return FollowErrorCode.CODE_SERVER_BUSY.getCode();
        }

        Integer fromMsg = bizCodeFromMessage(resolved.message());
        if (fromMsg != null) {
            return fromMsg;
        }

        return mapStatusCodeToBizCode(resolved.status().getCode());
    }

    /**
     * 对应 Go {@code BizCodeFromError} 末尾的 {@code switch st.Code()}（{@code errmsg.go:118-132}）。
     *
     * <p>白名单<b>恰好 6 个</b>：{@code InvalidArgument} / {@code Unauthenticated} /
     * {@code PermissionDenied} / {@code AlreadyExists} / {@code NotFound} / {@code Internal}，
     * 其余（含 {@code Unavailable}、{@code DeadlineExceeded}、{@code Canceled}）一律走
     * {@code default} → 1015。
     *
     * ⚠️ <b>不要为了「显得合理」往白名单里加码</b>：Go 侧没有这些映射，
     * 加上会让降级场景从 1015 变成别的码 —— 那是新增行为，不是对齐。
     * 反之也不要把白名单<b>缩小</b>：这 6 个是 Go 端写死的。
     */
    private static int mapStatusCodeToBizCode(Status.Code grpcCode) {
        if (grpcCode == null) {
            return FollowErrorCode.CODE_SERVER_BUSY.getCode();
        }
        switch (grpcCode) {
            case INVALID_ARGUMENT:
                return FollowErrorCode.ERROR_INVALID_PARAM.getCode();
            case UNAUTHENTICATED:
                return FollowErrorCode.ERROR_UNAUTHORIZED.getCode();
            case PERMISSION_DENIED:
                return FollowErrorCode.ERROR_FORBIDDEN.getCode();
            case ALREADY_EXISTS:
                return FollowErrorCode.ERROR_ALREADY_EXIST.getCode();
            case NOT_FOUND:
                return FollowErrorCode.ERROR_NOT_FOUND.getCode();
            case INTERNAL:
                return FollowErrorCode.ERROR_SERVER_COMMON.getCode();
            default:
                return FollowErrorCode.CODE_SERVER_BUSY.getCode();
        }
    }

    /**
     * 对应 grpc-go v1.81.1（{@code status.FromError}，本仓 {@code go.mod:25}）的<b>两段式</b>判定。
     *
     * <p>Go 侧原实现的关键结构是<b>两级</b>的，且第二级会<b>改写</b> message：
     * <pre>
     * func FromError(err error) (s *Status, ok bool) {
     *     if err == nil { return nil, true }
     *     type grpcstatus interface{ GRPCStatus() *Status }
     *     if gs, ok := err.(grpcstatus); ok {            // ① 顶层
     *         grpcStatus := gs.GRPCStatus()
     *         ...
     *         return grpcStatus, true                   //    message 原样
     *     }
     *     var gs grpcstatus
     *     if errors.As(err, &amp;gs) {                    // ② 沿 cause 链
     *         grpcStatus := gs.GRPCStatus()
     *         ...
     *         p := grpcStatus.Proto()
     *         p.Message = err.Error()                   // ⚠️ message 被【外层】err.Error() 覆盖
     *         return status.FromProto(p), true
     *     }
     *     return New(codes.Unknown, err.Error()), false
     * }
     * </pre>
     *
     * <p><b>⚠️ 为什么不能直接用 grpc-java 的 {@code Status.fromThrowable}</b>：
     * 它会沿 cause 链找到 status 并<b>原样保留 description</b>，而 Go 在这条路径上把
     * description 覆盖成了外层 error 的 {@code Error()} 文本。
     * 两者对「被业务代码包了一层的 status error」会给出<b>不同的业务码</b>：
     * Java 会靠原文案反查命中 5201/5101/5007…，Go 则反查落空、退到 switch 兜底。
     * ⇒ 必须按 Go 的两段式手写，不能借用 {@code Status.fromThrowable}。
     *
     * <p><b>本域实际路径不受影响（已核实）</b>：follow-api 唯一把 RPC 错误喂进来的地方是
     * {@code codeFromRPCError(rpcErr)}（{@code api/internal/logic/follow/helper.go:39-41}，
     * 8 个 logic 各调一次），而 {@code rpcErr} 是 protoc 生成的 stub 直接返回的
     * {@code *status.Error}。go-zero 的 4 个 client interceptor
     * （{@code timeoutinterceptor} / {@code breakerinterceptor} / {@code tracinginterceptor} /
     * {@code durationinterceptor}）全部 {@code return err} 原样透传，
     * <b>没有一个</b>做 {@code fmt.Errorf("%w")} 包装；
     * 4 个 server interceptor 同理。⇒ 真实运行里恒走第①级。
     * 这里仍完整实现第②级，是为了在 follow-api 将来引入包装时<b>行为不发生漂移</b>。
     *
     * <p><b>残留的跨语言差异（无法消除，且不影响现有路径）</b>：
     * Go 的 {@code err.Error()} 与 Java 的 {@link Throwable#getMessage()} 在<b>包装场景</b>下
     * 文本格式不同（Go 会拼上被包装 error 的 {@code "rpc error: …"}），
     * 因此第②级的文案几乎必然反查落空、落到 switch —— 这与 Go 的行为<b>方向一致</b>。
     * 真正能命中的只有「外层 message 恰好逐字等于某条业务文案」这种构造场景，
     * 在 follow 域不存在。
     */
    private static ResolvedStatus resolveStatus(Throwable err) {
        // ① 顶层直接命中：message 原样保留（Go: err.(grpcstatus) 分支）
        Status top = statusOf(err);
        if (top != null) {
            return new ResolvedStatus(top, top.getDescription());
        }

        // ② 沿 cause 链命中：message 被【外层】err.Error() 覆盖（Go: errors.As 分支）
        Throwable nested = err.getCause();
        // 上限防御：Throwable 允许构造出 cause 环，Go 的 errors.As 有自己的终止条件
        for (int depth = 0; nested != null && depth < 64; depth++) {
            Status found = statusOf(nested);
            if (found != null) {
                return new ResolvedStatus(found, err.getMessage());
            }
            Throwable next = nested.getCause();
            if (next == nested) {
                break;
            }
            nested = next;
        }
        return null;
    }

    /** 对应 Go {@code type grpcstatus interface{ GRPCStatus() *Status }} 的 {@code instanceof} 判定。 */
    private static Status statusOf(Throwable err) {
        if (err instanceof StatusException statusException) {
            return statusException.getStatus();
        }
        if (err instanceof StatusRuntimeException statusRuntimeException) {
            return statusRuntimeException.getStatus();
        }
        return null;
    }

    /**
     * 解析结果：{@code status} 用于取 gRPC 码，{@code message} 用于文案反查。
     *
     * <p>两者必须分开：第②级里 Go 的 message 已被外层 {@code err.Error()} 覆盖，
     * 与 {@code status.getDescription()} 不再是同一个值。
     */
    private record ResolvedStatus(Status status, String message) {
    }
}