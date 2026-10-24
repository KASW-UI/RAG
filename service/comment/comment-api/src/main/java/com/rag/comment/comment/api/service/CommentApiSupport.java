package com.rag.comment.comment.api.service;

import com.rag.comment.comment.api.interceptor.CommentJwtAuthInterceptor;
import com.rag.comment.comment.common.errmsg.CommentErrorCode;
import com.rag.comment.comment.common.errmsg.CommentErrorMessages;
import com.rag.comment.comment.common.errmsg.CommentGrpcError;
import com.rag.common.exception.BusinessException;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * comment-api 的业务支撑 —— 身份解析 + gRPC 错误转换。
 *
 * <p>本类是 Go 侧 {@code service/comment/api/internal/logic/comment/helper.go}
 * 与 7 个 logic 里重复出现的两段样板（{@code extractUserID} 与
 * {@code status.FromError} 分支）的收敛点。<b>行为按 Go 逐条复刻，不做「顺手改良」</b>。
 */
@Component
public class CommentApiSupport {

    /**
     * 复刻 Go {@code helper.go:10-29} 的 {@code extractUserID}。
     *
     * <pre>
     * func extractUserID(ctx context.Context) (int64, error) {
     *     raw := ctx.Value("userId")
     *     if raw == nil { return 0, fmt.Errorf("ctx userId is nil") }
     *     switch value := raw.(type) {
     *     case json.Number: return value.Int64()
     *     case string:      return strconv.ParseInt(value, 10, 64)
     *     case int64:       return value, nil
     *     case int:         return int64(value), nil
     *     case float64:     return int64(value), nil
     *     default:          return 0, fmt.Errorf("ctx userId has unexpected type %T", raw)
     *     }
     * }
     * </pre>
     *
     * <p><b>失败 ⇒ {@code NewErrCode(ErrorTokenRuntime)} = 1006</b>
     * （Go 侧 6 个 logic 的第一个分支，字面量 {@code errmsg.ErrorTokenRuntime}），
     * 即 <b>HTTP 200 + {code:1006, msg:"TOKEN已过期"}</b>。
     *
     * <h2>⚠️ Go 的 {@code json.Number} 分支是<b>真路径</b>，不是死代码</h2>
     *
     * <p><b>2026-10-24 更正</b>：本方法此前注释断言「golang-jwt 没有用 {@code UseNumber()}」，
     * <b>与真源相反</b>。实测 go-zero v1.10.2：
     * <pre>
     * rest/token/tokenparser.go:123:	return jwt.NewParser(jwt.WithJSONNumber())
     * </pre>
     * ⇒ Go 侧数值 claim <b>就是</b> {@code json.Number}，{@code case float64} 才是死代码。
     *
     * <p>而 {@code json.Number.Int64()} 的实现是 {@code strconv.ParseInt(string(n), 10, 64)}
     * —— 它解析的是 <b>JSON 字面量原文</b>，不是数值。⇒ 后果：
     * <table border="1">
     *   <caption>Go 的实际判定</caption>
     *   <tr><th>claim 字面量</th><th>Go 的 {@code Int64()}</th><th>RAG 必须</th></tr>
     *   <tr><td>{@code 31337}</td><td>31337 ✓</td><td>31337</td></tr>
     *   <tr><td>{@code 1.5}</td><td>❌ 报错（"1.5" 不是合法 int 文本）⇒ 1006</td><td>❌ 1006</td></tr>
     *   <tr><td>{@code 1.0}</td><td>❌ 报错（"1.0" 同理）⇒ 1006</td><td>❌ 1006</td></tr>
     *   <tr><td>{@code 9223372036854775808}（越界）</td><td>❌ 溢出 ⇒ 1006</td><td>❌ 1006</td></tr>
     * </table>
     *
     * <p>⚠️ <b>因此 {@code Double} / {@code Float} / {@code BigInteger} 三类必须一律 1006</b>：
     * <ul>
     *   <li>JSON <b>浮点</b>字面量（Double/Float）⇒ Go 侧原文必然带 {@code .} 或 {@code e}
     *       ⇒ {@code ParseInt} 必然失败 ⇒ <b>没有例外</b>；</li>
     *   <li>JSON <b>越界整数</b>（BigInteger）⇒ Go 侧 {@code ParseInt} 必然溢出 ⇒ <b>必然失败</b>。</li>
     * </ul>
     * ⇒ 之前把 {@code 1.5} 截断成 {@code 1}、把越界值<b>回绕</b>成负数，
     * 是<b>静默产出错误身份</b>（拿它去调 rpc 就是越权面），**不是**对齐。
     *
     * <p><b>三条必须保留的语义</b>：
     * <ol>
     *   <li><b>不拒绝 {@code uid <= 0}</b>：Go 侧同样不拒绝，{@code uid=0} 会原样透传给
     *       comment-rpc，由 rpc 侧的存在性校验判 1012/1013。api 层提前拒会把码换掉。</li>
     *   <li><b>字符串走 {@code ParseInt}，解析失败即 1006</b>：{@code "abc"} / {@code "1.5"}
     *       / {@code ""} 全部失败。<b>绝不能</b>用 {@code Long.parseLong} 之外更宽松的解析
     *       （如先 {@code parseDouble}）——那会让本该失败的 token 变成成功的身份，
     *       是实打实的越权风险。</li>
     *   <li><b>msg 必须是 Go 的原文</b>：失败时统一走 {@code tokenRuntime()}（1006 + "TOKEN已过期"）。</li>
     * </ol>
     */
    public long userId(HttpServletRequest request) {
        Object claim = request.getAttribute(CommentJwtAuthInterceptor.CLAIM_ATTRIBUTE);

        // Go: case int64 / case int —— jjwt 对范围内的整数字面量给 Integer / Long
        if (claim instanceof Long l) {
            return l;
        }
        if (claim instanceof Integer i) {
            return i.longValue();
        }
        if (claim instanceof Short s) {
            return s.longValue();
        }
        if (claim instanceof Byte b) {
            return b.longValue();
        }

        // ⚠️ Go: case json.Number → value.Int64() → strconv.ParseInt(字面量原文, 10, 64)
        //    JSON 浮点字面量与越界整数在 Go 侧**必然**解析失败 ⇒ 一律 1006。
        //    禁止截断（1.5 ⇒ 1）或回绕（BigInteger ⇒ 负数）：那是静默的错误身份。
        if (claim instanceof BigInteger bi) {
            logTokenClaimRejected("integer claim out of int64 range: " + bi);
            throw tokenRuntime();
        }
        if (claim instanceof Double d) {
            logTokenClaimRejected("float claim is not an int64 literal: " + d);
            throw tokenRuntime();
        }
        if (claim instanceof Float f) {
            logTokenClaimRejected("float claim is not an int64 literal: " + f);
            throw tokenRuntime();
        }
        if (claim instanceof BigDecimal bd) {
            logTokenClaimRejected("decimal claim is not an int64 literal: " + bd);
            throw tokenRuntime();
        }

        // Go: case string —— strconv.ParseInt，失败即整体失败
        if (claim instanceof String s) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException e) {
                logTokenClaimRejected("string not parseable as int64");
                throw tokenRuntime();
            }
        }

        // Go: raw == nil 或 default 分支
        logTokenClaimRejected(claim == null ? "claim absent" : "unexpected claim type");
        throw tokenRuntime();
    }

    /**
     * 复刻 6 个 logic 的 gRPC 错误出口。
     *
     * <pre>
     * st, ok := status.FromError(err)
     * if ok { return nil, errmsg.NewErrCodeMsg(int(st.Code()), st.Message()) }
     * logger.LogBusinessErr(l.ctx, errmsg.ErrorServerCommon, err)
     * return nil, errmsg.NewErrCode(errmsg.ErrorServerCommon)
     * </pre>
     *
     * <h2>⚠️ 两条分支的差异是本方法最容易做错的地方</h2>
     * <table border="1">
     *   <caption>Go 的 ok 标志决定一切</caption>
     *   <tr><th>错误形态</th><th>Go 侧结果</th></tr>
     *   <tr>
     *     <td>gRPC status 错误</td>
     *     <td>{@code NewErrCodeMsg(int(st.Code()), st.Message())} —— <b>msg 用 status 原文，不查表</b></td>
     *   </tr>
     *   <tr>
     *     <td>其它任何错误（如熔断）</td>
     *     <td>{@code NewErrCode(ErrorServerCommon)} ⇒ <b>5001 + "系统内部错误"</b></td>
     *   </tr>
     * </table>
     *
     * <p><b>不能直接用 {@code status.getCode().value()} 当业务码</b> —— 那是<b>本轮修掉的缺陷</b>：
     * <ul>
     *   <li><b>Go 侧</b>：comment-rpc 用 {@code errmsg.NewGrpcErr(code, msg)}
     *       （{@code service/comment/common/errmsg/errmsg.go:33-35} {@code status.Error(codes.Code(code), msg)}）
     *       ⇒ 业务码（1012 / 1013 / 1008 / 5003 / 2001…）<b>原样写进 grpc-status</b>
     *       ⇒ api 侧 {@code int(st.Code())} <b>原样取回</b>业务码。</li>
     *   <li><b>RAG 侧</b>：grpc-java 的 {@code Status} 只能承载 0..16 的标准码
     *       （{@code Status.java:438} 只存 Code 枚举），业务码装不下
     *       ⇒ {@code CommentGrpcError.grpcError} 把业务码放进<b>自定义 trailer</b>
     *       {@code rag-comment-biz-code}，{@code grpc-status} 只剩 UNKNOWN(2) / INVALID_ARGUMENT(3)。</li>
     * </ul>
     * ⇒ <b>两边「字面搬运」搬的不是同一个东西</b>：Go 搬的是 grpc-status 里的业务码，
     * RAG 必须搬 trailer 里的业务码。⇒ 只读 {@code status.getCode().value()} 会让
     * 客户端拿到 {@code 2} / {@code 3} 而不是 1012 / 1013 / 1008 / 5003。
     *
     * <p>⇒ 本方法的 {@code bizCode} 取自 {@link CommentGrpcError#bizCodeFromError(Throwable)}，
     * 它按三条线索依次判定，<b>三条在本域都成立</b>：
     * <ol>
     *   <li><b>降级 description 捞码</b>（{@code "Unknown code 5003"}）—— 对端是 <b>Go comment-rpc</b> 时
     *       （grpc-go 把越界码发出去了，grpc-java 收成 UNKNOWN + 降级 description）
     *       ⇒ 捞回 5003，与 Go 的 {@code int(st.Code())} <b>完全一致</b>；</li>
     *   <li><b>trailer</b> —— 对端是 <b>RAG comment-rpc</b> 时 ⇒ 无损取回业务码；</li>
     *   <li><b>标准码原值</b> —— 对端返回的是标准码（如 NOT_FOUND=5）时 ⇒ 原值，与 Go 一致。</li>
     * </ol>
     *
     * <p><b>msg 取 {@code status.getDescription()}，绝不查表</b>：
     * comment-rpc 发的是<b>自定义文案</b>（如 5003 的 "DB查询失败"），
     * 而码表里 5003 的文案是 "查询数据库失败"，<b>两者不相等</b>。
     * 查表会把客户端看到的错误信息换掉。
     *
     * <p><b>description 为 null 时按空串处理</b>：Go 的 {@code st.Message()} 在无 message 时
     * 返回 {@code ""} ⇒ {@code NewErrCodeMsg(code, "")}，msg 是<b>空串</b>。
     *
     * <p>⚠️ <b>同仓正确范式</b>：{@code like-api} 的 {@code LikeApiSupport#rpcError} 用的是同一套
     * （先判 status、再 {@code bizCodeFromError}、msg 取 description）。
     * 本方法此前把这一步漏掉了 ⇒ 两个模块各自都对、拼起来就错。
     */
    public BusinessException rpcError(Throwable err) {
        Status status = statusOf(err);
        if (status == null) {
            // Go: status.FromError 的 ok == false 分支 —— 注意是 5001，不是 1015
            return new BusinessException(
                    CommentErrorCode.ERROR_SERVER_COMMON.getCode(),
                    CommentErrorMessages.getMsg(CommentErrorCode.ERROR_SERVER_COMMON.getCode()));
        }
        int bizCode = CommentGrpcError.bizCodeFromError(err);
        String description = status.getDescription();
        return new BusinessException(bizCode, description == null ? "" : description);
    }

    /**
     * 取 gRPC status；<b>不是</b> status 异常时返回 {@code null}，
     * 对应 Go {@code status.FromError} 的 {@code ok=false}。
     */
    private static Status statusOf(Throwable err) {
        if (err instanceof StatusRuntimeException sre) {
            return sre.getStatus();
        }
        if (err instanceof StatusException se) {
            return se.getStatus();
        }
        return null;
    }

    /** Go {@code errmsg.NewErrCode(errmsg.ErrorTokenRuntime)}：码 1006，msg 查表。 */
    private static BusinessException tokenRuntime() {
        int code = CommentErrorCode.ERROR_TOKEN_RUNTIME.getCode();
        return new BusinessException(code, CommentErrorMessages.getMsg(code));
    }

    /** 对应 Go {@code logger.LogBusinessErr(ctx, ErrorTokenRuntime, err)} 的可观测性部分。 */
    private static void logTokenClaimRejected(String reason) {
        org.slf4j.LoggerFactory.getLogger(CommentApiSupport.class)
                .warn("extract userId from context failed: {}", reason);
    }
}