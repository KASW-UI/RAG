package com.rag.follow.follow.api.service;

import com.rag.common.exception.BusinessException;
import com.rag.follow.follow.api.dto.FollowUserListResp;
import com.rag.follow.follow.api.dto.RecommendListResp;
import com.rag.follow.follow.api.dto.RecommendUser;
import com.rag.follow.follow.api.interceptor.FollowJwtAuthInterceptor;
import com.rag.follow.follow.common.errmsg.FollowErrorCode;
import com.rag.follow.follow.common.errmsg.FollowErrorMessages;
import com.rag.follow.follow.common.errmsg.FollowGrpcError;
import com.rag.follow.follow.proto.RecommendResp;
import com.rag.follow.follow.proto.UserListResp;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * follow-api 跨 8 个 Service 共享的行为 —— 对应 Go 端
 * {@code service/follow/api/internal/logic/follow/helper.go}（47 行）。
 *
 * <p><b>Go 出处逐条对应</b>：
 * <table border="1">
 *   <caption>helper.go → 本类</caption>
 *   <tr><th>Go</th><th>Java</th></tr>
 *   <tr><td>{@code extractUserID}</td><td>{@link #userId}</td></tr>
 *   <tr><td>{@code resolveUserID}</td><td>{@link #resolveUserId}</td></tr>
 *   <tr><td>{@code codeFromRPCError}</td><td>{@link #rpcError}</td></tr>
 *   <tr><td>{@code userLogOption}</td><td><b>不迁</b>：Go 侧只喂结构化日志，
 *       本模块用 SLF4J 占位符打同样的字段（见各 Service）</td></tr>
 *   <tr><td>recommend 列表转换（内联在 logic 里）</td><td>{@link #toRecommendListResp}</td></tr>
 *   <tr><td>user id 列表转换（内联在 logic 里）</td><td>{@link #toUserListResp}</td></tr>
 * </table>
 *
 * <p><b>框架职责</b>：Spring {@code @Component}；proto → DTO 的映射逻辑收口在<b>唯一一份</b>，
 * 8 个 Service 里不重复出现字段拷贝（Go 侧也是收口在 helper.go 的）。
 *
 * <h3>⚠️ Go 的第三个判断 {@code if rpcResp == nil} <b>刻意不迁</b></h3>
 * <p>8 个 logic 里都有一段：
 * <pre>
 * if rpcResp == nil { return nil, followcommon.NewErrCode(followcommon.ErrorServerCommon) }
 * </pre>
 * 它在 Java 侧<b>不可达</b>：grpc-java 的 {@code blockingStub.xxx()} 要么返回非 null 响应、
 * 要么抛 {@code StatusRuntimeException}，不存在 Go 侧「err == nil 且 resp == nil」的中间态。
 * <p><b>而它缺失也不改变任何可观察行为</b>：真发生 NPE 时兜底在
 * {@code GlobalExceptionHandler#handleAny}，输出是
 * {@code {code:5001,msg:"internal server error"}} ——
 * 与那个分支的 code/msg <b>完全相同</b>。
 * <p>按本仓既有处置（零消费者的代码不能算已对齐，见 message-api 跳过
 * {@code shared.InvalidContextError} 的先例），本类<b>不提供</b>对应的方法 ——
 * 建一个没人调用的入口等于给后人留第二个出口。
 */
@Component
public class FollowApiSupport {

    /** Go {@code errmsg.go:34} {@code Success = 200}。 */
    public static final int SUCCESS = FollowErrorCode.SUCCESS.getCode();

    /**
     * 对应 Go {@code logic/follow/helper.go:13-33} 的 {@code extractUserID}：
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
     * <h3>⚠️ 本域比 message 域<b>宽</b>：字符串 claim 是被接受的</h3>
     * <p>Go 的类型分支里有 {@code case string: strconv.ParseInt(...)} ⇒
     * {@code {"userId":"123"}} 这种<b>字符串</b> claim 在 Go 侧是<b>合法</b>的，返回 123。
     * （message 域的 {@code shared.UserIDFromContext} 只接受 {@code json.Number}，
     * 字符串 claim 落 {@code !ok} 分支返回 5004 —— <b>两域行为不同，勿「顺手统一」</b>，
     * 本组件按 follow 的真源实现。）
     *
     * <h3>三种失败都归到 5004（而不是 401）</h3>
     * <ol>
     *   <li><b>claim 缺席</b>：鉴权层已验过签名，走到这里说明 token 有效；
     *       Go 返回 {@code ErrorUnauthorized} ⇒ <b>HTTP 200 + {code:5004}</b>。</li>
     *   <li><b>类型不匹配</b>（bool / 数组 / 对象 claim）⇒ Go 的 {@code default} 分支。</li>
     *   <li><b>数值不可解析</b>：{@code json.Number("1.5").Int64()} 内部是
     *       {@code strconv.ParseInt("1.5",10,64)} ⇒ 失败。jjwt 对同样的 JSON 会给
     *       {@code Double}，本方法同样判失败。
     *       ⚠️ <b>不能像 Jackson 默认那样静默截断</b> —— 那会把 Go 判 5004 的输入变成合法 uid，
     *       是实打实的身份伪造风险。</li>
     * </ol>
     *
     * <p>注意本方法<b>不</b>拒绝 {@code uid <= 0}：Go 侧同样不拒绝，
     * {@code uid=0} 会原样透传给 follow-rpc，由 rpc 侧的存在性校验判 5101。
     * api 层提前拒会把业务码换成 5004。
     *
     * @throws BusinessException 任一失败路径，code = 5004
     */
    public long userId(HttpServletRequest request) {
        Object claim = request.getAttribute(FollowJwtAuthInterceptor.CLAIM_ATTRIBUTE);
        if (claim == null) {
            throw unauthorized();
        }
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
            // Go: strconv.ParseInt 对超出 int64 的字面量报 range error（json.Number("1e30").Int64() 同样失败）
            if (bi.bitLength() >= 64) {
                throw unauthorized();
            }
            return bi.longValue();
        }
        if (claim instanceof String s) {
            // Go: strconv.ParseInt(s, 10, 64)
            //   拒绝 "" / " 12" / "1.5" / "1e2" / 超 long 范围；接受 "+123"（Java parseLong 同样接受）
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException e) {
                throw unauthorized();
            }
        }
        if (claim instanceof BigDecimal || claim instanceof Double || claim instanceof Float) {
            // Go: JSON 浮点字面量经 WithJSONNumber 保留原样字符串，Int64() 的 ParseInt 一律失败
            throw unauthorized();
        }
        // bool / List / Map / 其它类型 ⇒ Go 的 default 分支
        throw unauthorized();
    }

    /**
     * 对应 Go {@code logic/follow/helper.go:43-47}：
     * <pre>
     * func resolveUserID(currentUserID, requestedUserID int64) int64 {
     *     if requestedUserID &gt; 0 { return requestedUserID }
     *     return currentUserID
     * }
     * </pre>
     *
     * <p><b>⚠️ 这不是「权限校验」，只是「取值归一化」</b>：它<b>不</b>判断请求者是否有权
     * 查目标用户。是否比对由调用方决定，而 4 个 list 端点里<b>只有 2 个</b>比对
     * （{@code block/list} 与 {@code recommendations}）——
     * 这是 Go 侧各自 logic 里的分支，不在本方法内。详见 {@link #requireSelfScope}。
     *
     * @param currentUserID 当前登录用户 id（来自 JWT claim）
     * @param requestedUserID 请求里的 {@code user_id}；{@code null} / 非正数都归一化成当前用户
     */
    public long resolveUserId(long currentUserID, Long requestedUserID) {
        if (requestedUserID != null && requestedUserID > 0) {
            return requestedUserID;
        }
        return currentUserID;
    }

    /**
     * 对应 Go 侧两处<b>重复出现</b>的归属校验（各 logic 各写一份，字面量相同）：
     * <pre>
     * queryUserID := resolveUserID(currentUserID, req.UserId)
     * if queryUserID != currentUserID {
     *     metrics.ObserveReject(route, "forbidden_scope")
     *     return nil, followcommon.NewErrCode(followcommon.ErrorForbidden)
     * }
     * </pre>
     *
     * <p><b>⚠️ 它的存在本身就是一条业务语义</b>：Go 的 4 个 list 端点里
     * <b>只有 {@code GetBlockListLogic:46} 与 {@code GetRecommendationsLogic:46} 有这段</b>，
     * 而 {@code GetFollowListLogic} / {@code GetFollowerListLogic} <b>没有</b> ——
     * 即<b>关注列表与粉丝列表是公开可查的，黑名单与推荐是私有的</b>。
     * <p>这个不对称<b>看起来像遗漏、实际是既定行为</b>，必须原样保留：
     * 若给 4 个端点统一加上校验，就是把「可查他人关注列表」变成 5005，
     * 是新增的拒绝行为（会打破已有客户端）。
     *
     * @return 校验通过后的目标用户 id
     * @throws BusinessException code = 5005（HTTP 恒为 200）
     */
    public long requireSelfScope(long currentUserID, Long requestedUserID) {
        long queryUserID = resolveUserId(currentUserID, requestedUserID);
        if (queryUserID != currentUserID) {
            int code = FollowErrorCode.ERROR_FORBIDDEN.getCode();
            throw new BusinessException(code, FollowErrorMessages.getMsg(code));
        }
        return queryUserID;
    }

    /**
     * 对应 Go {@code codeFromRPCError} → {@code followcommon.BizCodeFromError(err)}。
     *
     * <p>Go 侧 {@code errmsg.go:101-133} 的判定顺序<b>不能颠倒</b>：
     * <ol>
     *   <li>先按 <b>status description 文案</b>反查业务码表
     *       （{@code bizCodeByMsg}，Go 侧 rpc 用 {@code GRPCError(codes.X, bizCode)}
     *       把 bizCode 的文案塞进 description 传来传去 ⇒ 跨进程保真）；</li>
     *   <li>查不到才按 <b>gRPC status code</b> 映射：
     *       {@code InvalidArgument→5003 / Unauthenticated→5004 / PermissionDenied→5005 /
     *       AlreadyExists→5006 / NotFound→5002 / Internal→5001 / 其它→1015}。</li>
     * </ol>
     * ⇒ <b>文本先于码值</b>：一个 {@code Internal} 携带 description「user not found」时，
     * Go 判 <b>5101</b> 而非 5001。这条顺序由 {@code FollowGrpcError} 实现，本类只做转发。
     *
     * @throws BusinessException 永远抛出（Go 侧对应 {@code return nil, NewErrCode(code)}）
     */
    public BusinessException rpcError(Throwable err) {
        int code = FollowGrpcError.bizCodeFromError(err);
        return new BusinessException(code, FollowErrorMessages.getMsg(code));
    }

    /**
     * 对应 Go 侧 8 个 logic 里<b>重复出现</b>的三段判断中的第三段：
     * <pre>
     * if rpcResp.Code != int32(followcommon.Success) {
     *     return nil, followcommon.NewErrCodeMsg(int(rpcResp.Code), rpcResp.Msg)
     * }
     * </pre>
     *
     * <p><b>⚠️ 用 rpc 回传的 msg，不查本地码表</b>：Go 这里是 {@code NewErrCodeMsg} 而不是
     * {@code NewErrCode} ⇒ msg 就是 follow-rpc 写进 {@code BaseResp.msg} 的那个字符串。
     * 改成本地查表会在「rpc 侧文案与 api 侧码表不一致」时产生不同的 body。
     * 本域两侧共用同一份 {@code errmsg.go}，所以实际取值相同 —— 但<b>保持传值</b>，
     * 才能让 rpc 侧将来单独改文案时不需要回头改 api。
     *
     * @throws BusinessException {@code code != 200} 时，code = rpc 回传值，msg = rpc 回传值
     */
    public void requireRpcSuccess(int code, String msg) {
        if (code != SUCCESS) {
            throw new BusinessException(code, msg);
        }
    }

    /**
     * 对应 Go {@code successUserListResp} 的响应 → {@code types.FollowUserListResp} 透传：
     * {@code return &types.FollowUserListResp{UserIds: rpcResp.UserIds}, nil}。
     *
     * <p>出参是 <b>JSON 数字</b>（Go tag {@code json:"user_ids"} 无 {@code ,string}）。
     *
     * <h3>⚠️ 空结果必须输出 {@code null} 而不是 {@code []}（2026-10-24 双侧活体对拍修正）</h3>
     * <p>Go 侧的 {@code rpcResp.UserIds} 是 <b>protobuf 反序列化后的切片</b>：
     * proto3 的 repeated 字段在为空时整个字段不上 wire ⇒ 反序列化得到 <b>nil</b> ⇒
     * {@code encoding/json}（无 {@code omitempty}）输出 {@code "user_ids":null}。
     * <p>⚠️ 「rpc 侧是非 nil 空切片 ⇒ 输出 []」是<b>错</b>的：那是 rpc <b>进程内</b>的值，
     * 中间隔着一次 protobuf 编解码，wire 上字段已经消失。
     * <p>⇒ 这里在 rpc 返回空列表时显式置 {@code null}，
     * 使 RAG 的 body 与 Go 的 body 逐字节相同。
     * <p>对照：{@link #toRecommendListResp} <b>不需要</b>这个处理 ——
     * Go 的推荐列表是在 api 进程内 {@code make(...)} 出来的，恒非 nil ⇒ 输出 {@code []}。
     */
    public FollowUserListResp toUserListResp(UserListResp resp) {
        FollowUserListResp out = new FollowUserListResp();
        int count = resp.getUserIdsCount();
        if (count == 0) {
            // Go 侧此刻是 nil 切片 ⇒ JSON null。给空 list 会输出 "user_ids":[]，与 Go 不同。
            out.setUserIds(null);
            return out;
        }
        List<Long> ids = new ArrayList<>(count);
        for (Long id : resp.getUserIdsList()) {
            ids.add(id);
        }
        out.setUserIds(ids);
        return out;
    }

    /**
     * 对应 Go {@code getrecommendationslogic.go:70-81}：
     * <pre>
     * users := make([]types.RecommendUser, 0, len(rpcResp.Users))
     * for _, user := range rpcResp.Users {
     *     if user == nil { continue }
     *     users = append(users, types.RecommendUser{TargetId: user.TargetId, MutualScore: user.MutualScore})
     * }
     * return &types.RecommendListResp{Users: users}, nil
     * </pre>
     *
     * <p><b>⚠️ {@code if user == nil { continue }} 在 Java 侧不需要</b>：
     * proto3 的 {@code repeated} 消息字段在 grpc-java 里<b>永远不会出现 null 元素</b>
     * （解出来的就是 {@code RecommendUser} 实例），Go 侧那个判空是防御性代码。
     * 保留它等于引入一条永不执行的分支（AGENTS.md：零消费者的代码不能算已对齐）。
     *
     * <p><b>{@code target_id} 在出参里是字符串</b>（Go tag 带 {@code ,string}），
     * 转换只发生在这一处，见 {@link RecommendUser} 的注释。
     */
    public RecommendListResp toRecommendListResp(RecommendResp resp) {
        RecommendListResp out = new RecommendListResp();
        List<RecommendUser> users = new ArrayList<>(resp.getUsersCount());
        for (RecommendResp.RecommendUser user : resp.getUsersList()) {
            RecommendUser item = new RecommendUser();
            item.setTargetId(String.valueOf(user.getTargetId()));
            item.setMutualScore(user.getMutualScore());
            users.add(item);
        }
        out.setUsers(users);
        return out;
    }

    /**
     * 对应 Go {@code NewErrCode(ErrorUnauthorized)} —— 8 个 logic 的第一段出口。
     *
     * <p><b>HTTP 恒为 200</b>：Go 注册了 {@code httpx.SetErrorHandler}，
     * 业务错误一律 {@code http.StatusOK} + envelope（见 {@code GlobalExceptionHandler}）。
     */
    public BusinessException unauthorized() {
        int code = FollowErrorCode.ERROR_UNAUTHORIZED.getCode();
        return new BusinessException(code, FollowErrorMessages.getMsg(code));
    }
}