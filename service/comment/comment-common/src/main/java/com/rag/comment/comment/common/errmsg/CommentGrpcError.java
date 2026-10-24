package com.rag.comment.comment.common.errmsg;

import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;

/**
 * comment 域 gRPC 错误双向转换 —— 对应 Go 端
 * {@code service/comment/common/errmsg/errmsg.go:33-35}：
 *
 * <pre>
 * func NewGrpcErr(code int, msg string) error {
 *     return status.Error(codes.Code(code), msg)
 * }
 * </pre>
 *
 * <h2>Go 端到底发生了什么（不要照抄这行就以为等价）</h2>
 *
 * <p>{@code codes.Code(code)} 是<b>把 comment 业务码（2001 / 5003 / 1012 / 1013 …）直接强转成
 * gRPC 状态码</b>。grpc-go 的 {@code codes.Code} 是 {@code int32} 的命名类型，<b>不做范围校验</b>，
 * 服务端写 trailer 时执行 {@code strconv.Itoa(int(s.Code()))}，
 * 所以线上真实字节是 {@code grpc-status: 5003}。
 * 而消费端 Go comment-api（{@code createcommentlogic.go:52-55} 等 6 处）做的是：
 * <pre>
 * st, ok := status.FromError(err)
 * if ok { return nil, errmsg.NewErrCodeMsg(int(st.Code()), st.Message()) }
 * </pre>
 * 即<b>把 gRPC 状态码原样当成 HTTP envelope 的业务码</b>。
 * ⇒ 「业务码出现在 grpc-status 字段里」是 comment 域的跨语言 wire 契约，不是随手写的。
 *
 * <h2>⚠️ 平台强制偏离：grpc-java 无法发出越界 grpc-status</h2>
 *
 * <p>grpc-java 的 {@link Status} 只认 17 个标准码（合法值 0–16），公开 API 里没有任何构造
 * 任意码值的入口 —— {@code Status.fromCode} 的签名只接受 {@code Code} 枚举，传 int 连编译都过不了。
 * 实测（本仓库 {@code pom.xml} 的 {@code <grpc.version>} = 1.63.0）：
 * <pre>
 * Status.fromCodeValue(2001) → code=UNKNOWN, desc="Unknown code 2001"
 * Status.fromCodeValue(5003) → code=UNKNOWN, desc="Unknown code 5003"
 * </pre>
 * ⇒ 业务码与原始 msg <b>双双丢失</b>。源码位置 {@code Status.java} 的 {@code fromCodeValue} 分支。
 *
 * <p><b>往 trailing Metadata 里塞 {@code grpc-status} 也无效</b>：grpc-java 在
 * {@code ServerCallImpl.close} 里用 {@code status.getCode().value()} 覆写该 trailer。
 *
 * <p>因此本类采取「<b>等价信息保全 + 载体迁移</b>」：
 * <ul>
 *   <li><b>业务码</b>（Go 放在 grpc-status）→ 放进自定义 trailer
 *       {@link #BIZ_CODE_TRAILER_KEY} 的 <b>ASCII 数值</b>，
 *       实测可无损抵达客户端（见 {@code CommentGrpcErrorTest#bizCodeTrailerRoundTripsLosslessly}）。</li>
 *   <li><b>文案</b>（Go 放在 description）→ 仍放 description，<b>逐字不变</b>，
 *       因此 comment-api 最终呈现给用户的 msg 与 Go 完全一致。</li>
 *   <li><b>gRPC 标准码</b> → 只承载「错误类别」，见 {@link #mapToGrpcCode}。</li>
 * </ul>
 *
 * <p><b>净差异</b>：仅「grpc-status 字段的数值」这一项不可复原（平台限制，非实现选择）；
 * 业务码、文案、错误分类三者对客户端仍然完全可得且无损。
 */
public final class CommentGrpcError {

    private CommentGrpcError() {
        // 工具类，禁止 new
    }

    /**
     * 自定义 trailer key，承载 comment 业务码（Go 端该值位于 {@code grpc-status}）。
     *
     * <p>名字刻意加 {@code rag-} 前缀：gRPC 规范允许应用自定义 trailer，
     * 加前缀可避免与未来标准 trailer 撞名，也与其他域的 {@code rag-like-biz-code} 区分开
     * —— 多域共存于同一条链路时不会互相误读。
     */
    public static final String BIZ_CODE_TRAILER_KEY = "rag-comment-biz-code";

    /** 上述 trailer 的强类型访问器。 */
    public static final Metadata.Key<String> BIZ_CODE_TRAILER =
            Metadata.Key.of(BIZ_CODE_TRAILER_KEY, Metadata.ASCII_STRING_MARSHALLER);

    /**
     * comment 业务码 → gRPC 标准码的<b>类别</b>映射。
     *
     * <h3>⚠️ 这里必须先讲清楚：Go 端根本没有这层映射</h3>
     * Go 把业务码<b>原样</b>写进 {@code grpc-status}（5003 就真的是 {@code grpc-status: 5003}），
     * 客户端 {@code int(st.Code())} 再原样取回。本方法产出的 17 个标准码是<b>新增信息</b>，
     * 不是对某个 Go 值的翻译 —— 换句话说，Go 侧不存在「5003 属于哪个类别」这个问题。
     *
     * <p>因此选型只能按「语义最贴近 + 不制造新风险」来定：
     * <ul>
     *   <li>{@link CommentErrorCode#ERROR_INPUT_WRONG}(2001) → {@link Status.Code#INVALID_ARGUMENT}
     *       —— 入参校验失败，语义最贴近；</li>
     *   <li>其余 → {@link Status.Code#UNKNOWN} —— <b>刻意不用 {@code INTERNAL}</b>：
     *       「DB 查询失败」「评论区不存在」「权限不足」这些在 Go 里都是<b>正常业务错误</b>，
     *       标成 {@code INTERNAL} 会让下游熔断器/重试器把它们当依赖故障处理
     *       （{@code INTERNAL} 与 {@code UNAVAILABLE} 几乎总是出现在重试白名单里），
     *       那是<b>新增行为</b>不是对齐。{@code UNKNOWN} 与 Go「码无法归类」的实情相符。</li>
     * </ul>
     *
     * <p><b>⚠️ 交给 api 层的一条硬约束（2026-10-24 订正：原措辞「若未来引入熔断器」<b>已过时</b>）</b>：
     * <b>comment-api 早已引入熔断器</b> ——
     * {@code service/common/protection/GoStyleGrpcClientBreakerInterceptor}
     * （{@code @Component} + {@code @GrpcGlobalClientInterceptor}），
     * 开关 {@code rag.protection.breaker.enabled: true}（{@code application.yml:88-89}）。
     * <p>Go 侧同样默认开：{@code zrpc/internal/config.go:14}
     * {@code Breaker bool json:",default=true"} ⇒ {@code zrpc/internal/client.go:112-113}
     * 装 {@code BreakerInterceptor}；{@code etc/commentservice.yaml} 未配 {@code Middlewares} ⇒ 生效。
     * <p>⇒ <b>两侧的「失败名单」逐条一致</b>：Go {@code zrpc/internal/codes/accept.go} 只把
     * {@code DeadlineExceeded / Internal / Unavailable / DataLoss / Unimplemented / ResourceExhausted}
     * 算作失败；RAG {@code GoStyleGrpcClientBreakerInterceptor.java:242-251} 的
     * {@code isAcceptable} 同样是这 6 个码 ⇒
     * <b>业务错误（{@code UNKNOWN(2)} / {@code INVALID_ARGUMENT(3)}）不计入熔断失败</b>
     * ⇒ <b>原文担心的「Java 侧比 Go 侧更容易打开熔断」不成立</b>。
     * <p>⚠️ <b>该一致性是「当前成立」，不是「结构上保证」</b> ⇒
     * 若将来调整 {@code isAcceptable} 的名单，<b>必须</b>同步核对 Go 的
     * {@code codes/accept.go}，否则这条约束会真的被破坏。
     * 由 {@code CommentGrpcErrorTest#businessCodesNeverCollideWithRetryableCategories} 锁死选型。
     */
    private static Status.Code mapToGrpcCode(int bizCode) {
        if (bizCode == CommentErrorCode.ERROR_INPUT_WRONG.getCode()) {
            return Status.Code.INVALID_ARGUMENT;
        }
        return Status.Code.UNKNOWN;
    }

    /**
     * 对应 Go {@code status.Error(codes.Code(code), msg)}。
     *
     * <p><b>⚠️ 调用方必须交给 {@code responseObserver.onError(...)}，不能 {@code throw}</b>。
     * grpc-java 的 {@code ServerCalls} 是从流监听回调
     * {@code UnaryServerCallListener.onHalfClose} 里调用业务方法的，
     * 若在那里 {@code throw}，异常会<b>逃出流监听回调</b>，被 grpc-java 当成<b>传输层内部错误</b>，
     * 并把 description 替换成 {@code "Application error processing RPC"} —— <b>文案当场全丢</b>。
     * 正确用法：
     * <pre>{@code
     * responseObserver.onError(CommentGrpcError.grpcError(
     *         CommentErrorCode.ERROR_DB_SELECT.getCode(), "DB查询失败"));
     * }</pre>
     * {@code onError(Throwable)} 不要求 checked，所以返回 {@link StatusException} 不会带来
     * {@code throws} 负担（protoc 生成的方法签名也不声明 throws）。
     */
    public static StatusException grpcError(int bizCode, String msg) {
        Metadata trailers = new Metadata();
        trailers.put(BIZ_CODE_TRAILER, String.valueOf(bizCode));
        return Status.fromCode(mapToGrpcCode(bizCode))
                .withDescription(msg)
                .asException(trailers);
    }

    /**
     * 对应 Go comment-api 侧 {@code st, ok := status.FromError(err); int(st.Code())} 的<b>还原</b>方向：
     * 从任意 Throwable 里恢复 comment 业务码。
     *
     * <p>判定优先级（顺序即语义）：
     * <ol>
     *   <li><b>降级 description 捞码</b> —— 灰度期 comment-api 调到<b>尚未迁移的 Go comment-rpc</b>：
     *       Go 发的是 {@code grpc-status: 5003} 且无本类 trailer，grpc-java 会降级成
     *       {@code UNKNOWN + "Unknown code 5003"}。这是唯一还能拿到真实码的线索。
     *       <b>只捞码不捞 msg</b> —— 原始 description 已在传输中被覆盖，不可复原，也不猜测。</li>
     *   <li><b>trailer</b> —— 本域自己发出去的错误，无损、最权威；</li>
     *   <li><b>gRPC 标准码原值</b> —— 见下段「为什么这里不用语义映射」。</li>
     * </ol>
     *
     * <h3>为什么标准码走「原值」而不是像 like/message 那样做语义映射</h3>
     * Go 的还原动作是<b>字面搬运</b>：{@code int(st.Code())} 把 grpc-status 上的整数
     * 原封不动塞进 HTTP envelope 的 {@code code} 字段。所以收到 {@code UNAUTHENTICATED}(16)
     * 时，Go 给客户端的就是 {@code code:16}。
     * 若 Java 在此处改判成 {@code 2001}（像 {@code LikeGrpcError} 那样），
     * 就是<b>凭空造出一个 Go 永远产生不到的码</b> —— 按 AGENTS.md「出现行为差异时默认判 RAG 错」
     * 与「不要擅自修复 Go 的行为」，此处以 Go 为准：<b>返回 {@link Status.Code#value()} 原值</b>。
     *
     * <p><b>⚠️ 2026-10-24 补充：这段「字面搬运」的论证<b>只对 Go-rpc 对端成立</b>。</b>
     * 对 <b>RAG-rpc 对端</b>，Go 式的「字面搬运」对象其实是<b>业务码</b>
     * —— 因为业务码在 RAG-rpc 侧放在<b>自定义 trailer</b> 里，<b>不在</b> grpc-status 里
     * ⇒ 两边「搬」的<b>不是同一个东西</b>：
     * <ul>
     *   <li><b>Go-rpc 对端</b>：grpc-status 上就是业务码（Go 直接写 {@code grpc-status: 1012}），
     *       grpc-java 降级成 {@code UNKNOWN + "Unknown code 1012"} ⇒ 码可从 description 文本捞回
     *       ⇒ 本方法的<b>第①优先级</b>正是为它而设；</li>
     *   <li><b>RAG-rpc 对端</b>：业务码在 trailer 里 ⇒ <b>必须先读 trailer（本方法第②优先级）</b>
     *       ⇒ 若只按「标准码原值」处理，<b>会把业务码整个丢掉</b>，端到端退化成 {@code UNKNOWN(2)}。</li>
     * </ul>
     * ⇒ 结论：<b>api 层读 RAG-rpc 的错误时，业务码来自 trailer，不是 grpc-status</b>
     * ⇒ 这一点与 [`docs/DRIFT/comment/proto.md`](../../../../../../docs/DRIFT/comment/proto.md) §3.1
     * 记的「跨栈业务码方向不对称」是同一件事。
     *
     * <p><b>非 status 错误</b>（Go {@code ok == false}）→ {@link CommentErrorCode#CODE_SERVER_BUSY}。
     * 严格说 Go 侧此刻走的是 {@code NewErrCode(ErrorServerCommon)}(5001)，
     * 但那是 <b>api 层</b>在 {@code createcommentlogic.go:56-58} 里显式选的兜底值，不是本函数职责；
     * api 层应直接用 {@link CommentErrorCode#ERROR_SERVER_COMMON}，不要依赖这里的 1015。
     * 由 {@code CommentGrpcErrorTest#standardGrpcCodeIsReturnedVerbatimLikeGoIntStCode} 锁死。
     *
     * <p>{@code null} 输入返回 {@link CommentErrorCode#SUCCESS}。这是<b>防御性取值</b>：
     * Go 侧 6 个调用点都在 {@code if err != nil} 之内，该分支实际不可达，
     * 因此不构成与 Go 的行为差异。
     */
    public static int bizCodeFromError(Throwable err) {
        if (err == null) {
            return CommentErrorCode.SUCCESS.getCode();
        }

        Status status = extractStatus(err);
        if (status == null) {
            if (err instanceof CommentCodeError codeError) {
                return codeError.getCode();
            }
            return CommentErrorCode.CODE_SERVER_BUSY.getCode();
        }

        int fromGo = recoverFromGoDegradedDescription(status.getDescription());
        if (fromGo != 0) {
            return fromGo;
        }

        Metadata trailers = Status.trailersFromThrowable(err);
        if (trailers != null) {
            String raw = trailers.get(BIZ_CODE_TRAILER);
            if (raw != null) {
                try {
                    return Integer.parseInt(raw);
                } catch (NumberFormatException ignore) {
                    // 落到下面的标准码原值，不让脏 trailer 让整条错误解析失败
                }
            }
        }

        // Go 是 int(st.Code()) 的字面搬运 ⇒ 返回标准码原值，不做语义改判
        return status.getCode().value();
    }

    /**
     * 灰度期兜底：识别 grpc-java 对越界 {@code grpc-status} 生成的
     * {@code "Unknown code 5003"} description，捞回业务码。
     *
     * <p>返回 {@code 0} 表示「不是这种降级形态」，交由调用方继续正常判定。
     * <b>只捞码不捞 msg</b> —— 原始 description 已在传输中被覆盖，不可复原，也不做猜测性还原。
     */
    private static int recoverFromGoDegradedDescription(String description) {
        if (description == null) {
            return 0;
        }
        String prefix = "Unknown code ";
        if (!description.startsWith(prefix)) {
            return 0;
        }
        try {
            return Integer.parseInt(description.substring(prefix.length()).trim());
        } catch (NumberFormatException ignore) {
            return 0;
        }
    }

    /** 同时识别 server 端 {@link StatusException} 与 client 端 {@link StatusRuntimeException}。 */
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