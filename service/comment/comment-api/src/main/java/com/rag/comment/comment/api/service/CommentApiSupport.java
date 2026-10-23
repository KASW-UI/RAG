package com.rag.comment.comment.api.service;

import com.rag.comment.comment.api.interceptor.CommentJwtAuthInterceptor;
import com.rag.comment.comment.common.errmsg.CommentErrorCode;
import com.rag.comment.comment.common.errmsg.CommentErrorMessages;
import com.rag.common.exception.BusinessException;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

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
     * <p><b>三条必须保留的语义</b>：
     * <ol>
     *   <li><b>不拒绝 {@code uid <= 0}</b>：Go 侧同样不拒绝，{@code uid=0} 会原样透传给
     *       comment-rpc，由 rpc 侧的存在性校验判 1012/1013。api 层提前拒会把码换掉。</li>
     *   <li><b>字符串走 {@code ParseInt}，解析失败即 1006</b>：{@code "abc"} / {@code "1.5"}
     *       / {@code ""} 全部失败。<b>绝不能</b>用 {@code Long.parseLong} 之外更宽松的解析
     *       （如先 {@code parseDouble}）——那会让本该失败的 token 变成成功的身份，
     *       是实打实的越权风险。</li>
     *   <li><b>数字走截断</b>：Go 的 {@code int64(float64)} 是<b>截断</b>（1.5 ⇒ 1，不报错），
     *       {@code longValue()} 同为截断，一致。</li>
     * </ol>
     *
     * <p><b>为什么 Java 侧看不到 {@code json.Number} 分支</b>：
     * golang-jwt/v4 用 {@code encoding/json} 把 claims 解进 {@code MapClaims}
     * （{@code map[string]interface{}}），数字字面量默认解成 {@code float64} ——
     * {@code json.Number} 只在调用方显式用了 {@code Decoder.UseNumber()} 时才出现，
     * golang-jwt <b>没有</b>这么做。jjwt 同样不会产生 {@code json.Number}。
     * ⇒ 该分支在本实现里不可达，用 {@code Double} 分支覆盖它等价。
     */
    public long userId(HttpServletRequest request) {
        Object claim = request.getAttribute(CommentJwtAuthInterceptor.CLAIM_ATTRIBUTE);

        // Go: case int64 / case int —— jjwt 对整数字面量给 Integer / Long / BigInteger
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
        if (claim instanceof BigInteger bi) {
            // 溢出长整型时截断，与 Go 的 float64 → int64 同为「不报错、截断」
            return bi.longValue();
        }

        // Go: case float64 —— int64(value) 是截断转换
        if (claim instanceof Double d) {
            return d.longValue();
        }
        if (claim instanceof Float f) {
            return f.longValue();
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
     * <p><b>⚠️⚠️ 本方法刻意<b>不</b>使用 {@code CommentGrpcError.bizCodeFromError}</b>。
     * 这是本模块最容易写错、且错了立刻产生线上不一致的一点：
     * <ul>
     *   <li>Go 侧的 code 是 <b>{@code int(st.Code())}</b> —— gRPC <b>标准码的数值</b>
     *       （InvalidArgument=3 / NotFound=5 / AlreadyExists=6 / PermissionDenied=7 /
     *        ResourceExhausted=8 / FailedPrecondition=9 / Internal=13 / Unavailable=14 /
     *        Unauthenticated=16），<b>不是</b> comment 域自己的业务码（1012 / 1013 …）。
     *       换句话说，下游 comment-rpc 返回 {@code NotFound}，客户端在 HTTP 上看到的就是
     *       {@code code:5}，而不是 {@code 1013}。</li>
     *   <li>若改用 {@code bizCodeFromError}（按 description 反查业务码表），
     *       会把 5 变成 1013 —— <b>这是新增行为，且与 Go 不一致</b>。
     *       message-api 用它是<b>因为</b> Go message 侧确实有 {@code shared.RPCError}
     *       → {@code BizCodeFromError} 那一层；comment 侧<b>没有</b>。</li>
     * </ul>
     *
     * <p><b>非 status 错误（如熔断）⇒ 5001</b>：Go 的 {@code status.FromError} 对不实现
     * {@code GRPCStatus()} 的 error 返回 {@code ok=false} ⇒ 落进兜底分支。
     * Java 侧对应「不是 {@link StatusRuntimeException} / {@link StatusException}」。
     */
    public BusinessException rpcError(Throwable err) {
        Status status = statusOf(err);
        if (status != null) {
            return new BusinessException(
                    status.getCode().value(),
                    status.getDescription() == null ? "" : status.getDescription());
        }
        return new BusinessException(
                CommentErrorCode.ERROR_SERVER_COMMON.getCode(),
                CommentErrorMessages.getMsg(CommentErrorCode.ERROR_SERVER_COMMON.getCode()));
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