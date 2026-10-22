package com.rag.like.like.api.service;

import com.rag.like.like.api.interceptor.LikeJwtAuthInterceptor;
import com.rag.like.like.api.metrics.LikeApiMetrics;
import com.rag.like.like.common.errmsg.CodeError;
import com.rag.like.like.common.errmsg.LikeErrorCode;
import com.rag.like.like.common.errmsg.LikeGrpcError;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.math.BigInteger;

/**
 * like-api 6 个 Service 共享的行为 —— 对应 Go 端 6 个 logic 里那段<b>逐字重复</b>的模板。
 *
 * <p><b>Go 侧为什么是 6 份重复</b>：goctl 生成器为每个 logic 独立产出，
 * 所以 {@code ctx.Value("userId").(json.Number)} 与
 * {@code status.FromError(err)} 两段在 6 个文件里各写了一遍
 * （{@code getlikecountlogic.go} / {@code getlikestatelogic.go} /
 * {@code gettargetlikerlistlogic.go} / {@code getuserlikelistlogic.go} /
 * {@code getusertotallikelogic.go} / {@code likeactionlogic.go}）。
 * <b>行为完全一致</b>，且「重复」本身没有可观察语义 —— 故在此收口为一份
 * （Java 侧收敛是 L4 实现机制的允许变更，不是行为改动）。
 *
 * <p>⚠️ <b>但两段模板的行为并不对称，必须分开看</b>：
 * <ul>
 *   <li>{@link #userId} 的失败码是 <b>1006</b>（{@code ErrorTokenRuntime}），
 *       4 个端点用（{@code getlikestate} / {@code getuserlikelist} /
 *       {@code getusertotallike} / {@code likeaction}），
 *       <b>另外 2 个端点根本不做身份解析</b>（见类末尾的鉴权不对称说明）；</li>
 *   <li>{@link #rpcError} 的失败码<b>依赖错误是不是 gRPC status 错误</b>，
 *       两条分支的码与 msg 规则完全不同。</li>
 * </ul>
 */
@Component
public class LikeApiSupport {

    private final LikeApiMetrics metrics;

    public LikeApiSupport(LikeApiMetrics metrics) {
        this.metrics = metrics;
    }

    /**
     * 对应 Go 侧 4 处 {@code ctx.Value("userId").(json.Number)} + {@code .Int64()}：
     * <pre>
     * userId, ok := l.ctx.Value("userId").(json.Number)
     * if !ok {
     *     metrics.ApiRejectCount.WithLabelValues("/like/likeaction", "token_invalid").Inc()
     *     return nil, errmsg.NewErrCode(errmsg.ErrorTokenRuntime)
     * }
     * uid, err := userId.Int64()
     * if err != nil {
     *     metrics.ApiRejectCount.WithLabelValues("/like/likeaction", "token_parse_error").Inc()
     *     return nil, errmsg.NewErrCode(errmsg.ErrorTokenRuntime)
     * }
     * </pre>
     *
     * <h3>四条必须逐条复刻的规则</h3>
     * <ol>
     *   <li><b>失败码是 1006，不是 401，也不是 5001。</b>
     *       Go 归成 {@code ErrorTokenRuntime}（{@code errmsg.go:58}，文案 "TOKEN已过期"），
     *       输出 <b>HTTP 200 + {code:1006,msg:"TOKEN已过期"}</b>。
     *       改写成 401 会让客户端的「token 过期需重新登录」流程走错分支。</li>
     *   <li><b>msg 查表</b>：Go 这里是 {@code NewErrCode}（不是 {@code NewErrCodeMsg}），
     *       所以 msg 来自码表，<b>不是</b> ctx 里那条 "ctx userId is not json.Number"
     *       的日志文本（那条只进 logger）。</li>
     *   <li><b>claim 不是数字 ⇒ 1006</b>：Go 的类型断言目标是 {@code json.Number}，
     *       字符串 claim（{@code "userId":"abc"}）直接落 {@code !ok} 分支。
     *       这里只接受<b>整型</b>（Byte/Short/Integer/Long/BigInteger）。</li>
     *   <li><b>数字不是整数 ⇒ 1006</b>：Go 的 {@code json.Number.Int64()} 内部是
     *       {@code strconv.ParseInt(s,10,64)}，对 {@code "1.5"} / {@code "1e2"} / {@code "1.0"}
     *       一律失败。因此 {@link Double} / {@link java.math.BigDecimal} <b>一律拒</b> ——
     *       否则 RAG 会把 Go 判 1006 的输入静默截断成合法 uid，那是<b>实打实的越权风险</b>。</li>
     * </ol>
     *
     * <p><b>🔴 JSON 字符串 claim（含 {@code "123"}）一律拒绝 ⇒ 1006</b>
     * （DRIFT-LIKEAPI-010，2026-10-22 更正；此前本段注释是<b>反的</b>，代码却是对的）。
     * <p>错误注释曾写：「字符串形态 Go 侧同样是 {@code json.Number("123")} ⇒ 接受」。
     * 那是错的。{@code UseNumber()} 只影响 <b>JSON 数字</b>的解码形态，
     * <b>不改变 JSON 字符串本身的类型</b>：golang-jwt v4.5.2 {@code parser.go:154-156}
     * 之后，{@code "userId":"123"} 解出来就是 Go 的 {@code string}，
     * {@code .(json.Number)} 断言<b>失败</b> ⇒ Go 返 1006。
     * <p><b>为什么这条必须钉死</b>：一旦有人「把代码改成和注释一致」而放行字符串 claim，
     * 任何 {@code "userId":"<任意 id>"} 形态的 claim 都会被静默接受 ⇒
     * <b>越权取数</b>（可以把 uid 换成别人的）。这比 1006 变成 200 严重得多。
     * <p>已由 {@code LikeApiMvcPipelineTest#nonNumericUserIdClaimYields1006Not401} 锁死。
     *
     * <p><b>本方法不拒绝 {@code uid <= 0}</b>：Go 侧同样不拒绝，
     * {@code uid=0} 会原样透传给 like-rpc 走它自己的校验。api 层提前拒会把业务码换掉。
     */
    public long userId(HttpServletRequest request) {
        Object claim = request.getAttribute(LikeJwtAuthInterceptor.CLAIM_ATTRIBUTE);
        if (claim instanceof Byte b) {
            return b.longValue();
        }
        if (claim instanceof Short s) {
            return s.longValue();
        }
        if (claim instanceof Integer i) {
            return i.longValue();
        }
        if (claim instanceof Long l) {
            return l;
        }
        if (claim instanceof BigInteger bi) {
            // Go 的 json.Number.Int64() 对越界字面量报 range error ⇒ 落在「有值但解析不出」的分支。
            // ⚠️ bitLength() 去掉符号位，所以 Long.MIN_VALUE 是 63；>=64 才真的越界。
            //   （曾有一次复核误以为 -2^63 的 bitLength 是 64 而判定此处有缺陷 —— 实测是 63，
            //    原判据本就正确。见 GoStyleStringInt64 同名注释里的推导。）
            if (bi.bitLength() >= 64) {
                metrics.incApiReject(LikeApiMetrics.ROUTE_LABEL_GO_HARDCODED,
                        LikeApiMetrics.REASON_TOKEN_PARSE_ERROR);
                throw tokenRuntime();
            }
            return bi.longValue();
        }
        // 缺席 / String / Double / BigDecimal / Boolean / Map / 数组
        // ⇒ Go 的 `.(json.Number)` 类型断言失败，落 token_invalid 分支
        metrics.incApiReject(LikeApiMetrics.ROUTE_LABEL_GO_HARDCODED,
                LikeApiMetrics.REASON_TOKEN_INVALID);
        throw tokenRuntime();
    }

    /**
     * Go {@code NewErrCode(ErrorTokenRuntime)} 的等价物：<b>查表</b>得到
     * {@code "TOKEN已过期"}，而不是把 Go 那条日志文本塞进 msg。
     */
    private static CodeError tokenRuntime() {
        return CodeError.newCode(LikeErrorCode.ERROR_TOKEN_RUNTIME.getCode());
    }

    /**
     * 对应 Go 侧 6 处完全相同的错误映射：
     * <pre>
     * st, ok := status.FromError(err)
     * if ok {
     *     return nil, errmsg.NewErrCodeMsg(int(st.Code()), st.Message())
     * }
     * logger.LogBusinessErr(l.ctx, errmsg.ErrorServerCommon, err)
     * return nil, errmsg.NewErrCode(errmsg.ErrorServerCommon)
     * </pre>
     *
     * <h3>⚠️⚠️ 两条分支的差异是本类最容易做错的地方</h3>
     * <table border="1">
     *   <caption>Go 的 ok 标志决定一切</caption>
     *   <tr><th>错误形态</th><th>Go 侧结果</th></tr>
     *   <tr>
     *     <td>gRPC status 错误</td>
     *     <td>{@code NewErrCodeMsg(业务码, st.Message())} —— <b>msg 用 status 原文，不查表</b></td>
     *   </tr>
     *   <tr>
     *     <td>其它任何错误</td>
     *     <td>{@code NewErrCode(ErrorServerCommon)} ⇒ <b>5001 + "系统内部错误"</b></td>
     *   </tr>
     * </table>
     *
     * <p><b>为什么不能直接调 {@link LikeGrpcError#bizCodeFromError(Throwable)} 就完事</b>：
     * 那个方法对<b>非 status</b> 异常返回 {@link LikeErrorCode#CODE_SERVER_BUSY}（1015），
     * 而 Go 在这条分支上给的是 <b>5001</b>。两者相差一整个业务码段，
     * 且 msg 来源完全不同（1015 查表得"服务繁忙"，5001 查表得"系统内部错误"）。
     * ⇒ <b>必须先自己判定「是不是 status 错误」</b>（即 Go 的 {@code ok}），
     * 再分别走两条分支。
     *
     * <p><b>msg 取 {@code status.getDescription()}，绝不查表</b>：
     * Go like-rpc 用 {@code NewGrpcErr(5003, "DB查询失败")} 发的是<b>自定义文案</b>，
     * 码表里 5003 的文案是"查询数据库失败"，<b>两者不相等</b>。
     * 查表会把客户端看到的错误信息换掉 —— 这也是
     * {@code LikeGrpcErrorTest#descriptionKeepsGoMessageVerbatim} 锁死的那条。
     *
     * <p><b>description 为 null 时按空串处理</b>：Go 的 {@code st.Message()} 在无 message 时
     * 返回 {@code ""} ⇒ {@code NewErrCodeMsg(code, "")}，msg 是<b>空串</b>。
     */
    public CodeError rpcError(Throwable err) {
        Status status = extractStatus(err);
        if (status == null) {
            // Go: status.FromError 的 ok == false 分支 —— 注意是 5001，不是 1015
            return CodeError.newCode(LikeErrorCode.ERROR_SERVER_COMMON.getCode());
        }
        int bizCode = LikeGrpcError.bizCodeFromError(err);
        String description = status.getDescription();
        return CodeError.newCodeMsg(bizCode, description == null ? "" : description);
    }

    /**
     * 判定「是不是 gRPC status 错误」—— 对应 Go {@code status.FromError(err)} 的
     * 第二个返回值 {@code ok}。
     *
     * <p>Go 侧 {@code status.FromError} 对<b>非</b> status 错误返回
     * {@code ok == false}；grpc-java 没有等价的「ok」，
     * 所以用类型判定：{@link StatusException}（server 端抛）与
     * {@link StatusRuntimeException}（<b>blocking stub 实际抛的是这个</b>）都算 status 错误，
     * 其它一律不算。
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