package com.rag.follow.follow.rpc.service;

import com.rag.common.logger.WithUserID;
import com.rag.follow.follow.common.errmsg.FollowErrorCode;
import com.rag.follow.follow.common.errmsg.FollowErrorMessages;
import com.rag.follow.follow.common.errmsg.FollowGrpcError;
import com.rag.follow.follow.proto.BaseResp;
import com.rag.follow.follow.proto.ListReq;
import com.rag.follow.follow.proto.RecommendResp;
import com.rag.follow.follow.proto.RelationReq;
import com.rag.follow.follow.proto.UserListResp;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 跨 RPC 共享业务工具 —— 对应 Go 端 {@code service/follow/rpc/internal/logic/helper.go}
 * 里的 package-level 函数集（Go 没有 class，故这些函数被包在一个类里）。
 *
 * <p>承担：
 * <ul>
 *   <li>请求校验 / 归一化：{@link #validateRelationReq} / {@link #normalizeListReq}</li>
 *   <li>响应工厂：{@link #successBaseResp} / {@link #successUserListResp} / {@link #successRecommendResp}</li>
 *   <li>推荐算法的纯函数：{@link #toSet} / {@link #sortedKeys} /
 *       {@link #scoreWeight} / {@link #clampRecommendDepth} / {@link #clampFanout}</li>
 *   <li>日志选项：{@link #userLogOption}</li>
 * </ul>
 *
 * <p><b>⚠️ 本类刻意不持有 {@code UserRpcClient}</b>：
 * Go 侧 {@code ensureUserExists} 与这些纯函数同在 helper.go，但它<b>依赖 svcCtx</b>，
 * 属于跨组件调用。Java 侧把它拆到 {@link FollowUserExistenceService}，
 * 这样一个纯工具类不会被迫依赖 gRPC stub（便于单测，也避免 Spring 把 client 拉进来）。
 *
 * <p><b>⚠️ 校验失败一律抛 {@code StatusException} 而非返回错误对象</b>：
 * 因为 Go 侧的 {@code GRPCError} 在<b>发错误的那一刻</b>就已经把
 * 「gRPC 码 + 业务文案」定死了，响应对象一律是 {@code nil}。
 * Java 侧若改成「返回 Result 再由入口转换」，就会多出「可能忘了转换」的分支。
 * 出口只有一处（{@code FollowServiceGrpcImpl} 的 {@code onError}），符合 AGENTS.md §3.3。
 */
@Component
public class FollowRpcSupport {

    // =============================================================================
    // 错误构造桥接
    // =============================================================================

    /**
     * 🔴 业务错误的<b>唯一构造入口</b> —— 把 follow-common 的映射转成 unchecked 异常抛出。
     *
     * <h3>为什么需要这层桥</h3>
     * <p>follow-common 的 {@code FollowGrpcError.grpcError(...)} 返回
     * {@link StatusException}，而 grpc-java 里它是 <b>checked</b> 异常
     * （{@code public class StatusException extends Exception}）。
     * 这带来两个真实问题：
     * <ol>
     *   <li><b>不能直接 throw</b>：否则 {@code FollowDomainService} 里
     *       {@code catch (RuntimeException e)} <b>抓不到</b>业务错误 ⇒ finally 里的
     *       {@code failure} 保持 null ⇒ {@code ObserveRPC} / {@code ObserveRelation}
     *       把<b>失败记成 success</b>。这是指标口径级别的静默错误，
     *       而且测试很难发现（响应确实是错的，只是仪表盘反了）。</li>
     *   <li>被迫在 ~15 个方法签名上加 {@code throws StatusException}，
     *       与本仓其它 service 的惯例不符，也让 {@code Supplier} 型 lambda 不可用。</li>
     * </ol>
     *
     * <h3>为什么用 StatusRuntimeException 而不是自己造异常类</h3>
     * <p>{@link StatusRuntimeException} 是 grpc-java <b>自己提供的 unchecked 兄弟</b>，
     * 两者持有<b>同一个 {@link Status}</b> ⇒ 上 wire 的字节<b>完全相同</b>
     * （{@code ServerCallImpl.close()} 对两者的处理路径一致）。
     * ⇒ 它只是「同样的错误，unchecked 的投递方式」，不是新增机制。
     *
     * <h3>⚠️ 业务码的映射仍然只有一个真源</h3>
     * <p>本方法先向 follow-common 要 {@code Status}（含 description = 业务文案），
     * 再只做 {@code asRuntimeException()} 的包装 ——
     * <b>不复制任何 {@code bizCode → msg} 的映射表</b>。
     * 若改成在这里自己 {@code Status.fromCode(...).withDescription(...)}，
     * 就多出第二张映射表，follow-common 改文案时这里会静默不同步。
     *
     * <h3>⚠️ 对 {@code BizCodeFromError} 的反查没有影响</h3>
     * <p>{@code FollowGrpcError.bizCodeFromError} 的第①级判定同时认
     * {@code StatusException} 与 {@code StatusRuntimeException}，
     * 且第①级<b>原样保留 description</b> ⇒ 日志里记的业务码与 Go 完全一致。
     * （若改用自定义异常包装，会被迫走第②级、description 被外层消息覆盖而反查落空 ——
     * 那正是本方法要避免的坑。）
     */
    public static StatusRuntimeException rpcError(Status.Code grpcCode, int bizCode) {
        StatusException checked = FollowGrpcError.grpcError(grpcCode, bizCode);
        return checked.getStatus().asRuntimeException();
    }

    // =============================================================================
    // 日志选项 —— userLogOption
    // =============================================================================

    /**
     * 对应 Go {@code func userLogOption(userID int64) logger.LogOption}
     * （{@code helper.go:23-25}）：{@code logger.WithUserID(strconv.FormatInt(userID, 10))}。
     *
     * <p>Go 用 {@code strconv.FormatInt} 而不是 {@code fmt.Sprint}，说明<b>不传负数</b>；
     * Java 侧 {@code String.valueOf(long)} 语义相同。
     *
     * <p>做成 <b>static</b>：它不需要本类的任何状态，
     * 而 {@code FollowUserExistenceService} 的日志方法要在没有注入本类的地方构造它。
     */
    public static WithUserID userLogOptionOf(long userId) {
        return WithUserID.of(String.valueOf(userId));
    }

    // =============================================================================
    // 校验与归一化
    // =============================================================================

    /**
     * 对应 Go {@code validateRelationReq}（{@code helper.go:27-35}）：
     * <pre>
     * if in == nil || in.GetUserId() &lt;= 0 || in.GetTargetId() &lt;= 0 {
     *     return GRPCError(codes.InvalidArgument, ErrorInvalidParam)   // 5003
     * }
     * if in.GetUserId() == in.GetTargetId() {
     *     return GRPCError(codes.InvalidArgument, ErrorSelfRelation)   // 5201
     * }
     * </pre>
     *
     * <p><b>⚠️ 两条错误的 gRPC 码相同（INVALID_ARGUMENT），业务码不同（5003 / 5201）</b>。
     * 而 follow-common 的文案反查表能把 5201 还原出来（"self relation is not allowed"），
     * 所以 follow-api 拿到的是 5201 而不是被 switch 兜底成 5003 ——
     * 这条链路依赖 follow-common 的 {@code BIZ_CODE_BY_MSG}，别绕过它自己造错误。
     *
     * <p><b>⚠️ {@code in == nil} 分支在 Java 侧不单独实现</b>：
     * protoc 生成的请求对象永远非 null（gRPC runtime 保证），
     * 且 {@code getUserId()} 在未设置时返回 0 ⇒ 会被 {@code <= 0} 捕获并给出同一个 5003。
     * 行为等价，不需要也不应该为此加一个不可达的分支。
     */
    public void validateRelationReq(RelationReq in) {
        if (in.getUserId() <= 0 || in.getTargetId() <= 0) {
            throw rpcError(Status.Code.INVALID_ARGUMENT,
                    FollowErrorCode.ERROR_INVALID_PARAM.getCode());
        }
        if (in.getUserId() == in.getTargetId()) {
            throw rpcError(Status.Code.INVALID_ARGUMENT,
                    FollowErrorCode.ERROR_SELF_RELATION.getCode());
        }
    }

    /**
     * {@link #normalizeListReq} 的返回值 —— 对应 Go 的多返回值
     * {@code (int64, int32, int32, error)}。
     *
     * <p>⚠️ Go 侧<b>出错时返回 {@code (0, 0, 0, err)}</b>；Java 侧改为抛异常，
     * 调用点拿不到「三个 0」。这是本模块唯一的表达层差异，语义一致
     * （Go 调用点同样在 {@code if err != nil} 处直接 return，不使用那三个 0）。
     */
    public record NormalizedListReq(long userId, int offset, int limit) {
    }

    /**
     * 对应 Go {@code normalizeListReq(defaultLimit, maxLimit, in)}（{@code helper.go:37-56}）：
     * <pre>
     * if in == nil || in.GetUserId() &lt;= 0 { return 0,0,0, GRPCError(InvalidArgument, ErrorInvalidParam) }
     * offset := in.GetOffset();  if offset &lt; 0 { offset = 0 }
     * limit  := in.GetLimit();   if limit  &lt;= 0 { limit = defaultLimit }
     * if maxLimit &gt; 0 &amp;&amp; limit &gt; maxLimit { limit = maxLimit }
     * </pre>
     *
     * <p><b>⚠️ 三段归一化的顺序不可调换</b>：
     * <ol>
     *   <li>先夹 <b>offset</b>：负数归零（不是报错）。</li>
     *   <li>再补 <b>limit</b>：{@code <= 0} 换成 {@code defaultLimit}。</li>
     *   <li>最后<b>截断</b>：{@code limit > maxLimit} 压到 {@code maxLimit}。</li>
     * </ol>
     * 若把截断放到补默认值之前，{@code defaultLimit > maxLimit} 的配置会让 limit 卡在越界值上。
     *
     * <p><b>⚠️ {@code maxLimit <= 0} 时整条截断短路</b>（Go 的 {@code maxLimit > 0 &&} 前置条件）：
     * 意味着「配 0 = 不限制」。这是 Go 的实际行为，照搬，不加校验。
     *
     * <p>三个列表方法传 {@code (List.DefaultLimit, List.MaxLimit)} = (20, 100)，
     * {@code GetRecommendations} 传 {@code (Recommend.DefaultLimit, Recommend.MaxLimit)} = (20, <b>50</b>)。
     */
    public NormalizedListReq normalizeListReq(int defaultLimit, int maxLimit, ListReq in) {
        if (in.getUserId() <= 0) {
            throw rpcError(Status.Code.INVALID_ARGUMENT,
                    FollowErrorCode.ERROR_INVALID_PARAM.getCode());
        }

        int offset = in.getOffset();
        if (offset < 0) {
            offset = 0;
        }

        int limit = in.getLimit();
        if (limit <= 0) {
            limit = defaultLimit;
        }
        if (maxLimit > 0 && limit > maxLimit) {
            limit = maxLimit;
        }

        return new NormalizedListReq(in.getUserId(), offset, limit);
    }

    // =============================================================================
    // 响应工厂 —— successBaseResp / successUserListResp / successRecommendResp
    // =============================================================================

    /**
     * 对应 Go {@code successBaseResp}（{@code helper.go:58-63}）：
     * {@code BaseResp{Code: int32(Success), Msg: GetErrMsg(Success)}} ⇒ {@code code=200, msg="OK"}。
     *
     * <p>⚠️ <b>成功也走 body 而不是空响应</b>：proto 里 {@code BaseResp} 是有字段的，
     * Go 明确填了 200 / "OK"，Java 侧必须同样填，不能返回 {@code null} 或默认实例。
     */
    public BaseResp successBaseResp() {
        return BaseResp.newBuilder()
                .setCode(FollowErrorCode.SUCCESS.getCode())
                .setMsg(FollowErrorMessages.getMsg(FollowErrorCode.SUCCESS.getCode()))
                .build();
    }

    /**
     * 对应 Go {@code successUserListResp}（{@code helper.go:65-71}）。
     *
     * <p>⚠️ <b>空列表与 nil 在 wire 上不可区分</b>：proto3 的 repeated 字段为空时
     * 序列化出来就是「字段缺失」，Go 的 {@code nil} 切片与 Java 的 {@code addAll(emptyList)}
     * 产生<b>逐字节相同</b>的编码。因此这里统一用空列表，不必区分。
     */
    public UserListResp successUserListResp(List<Long> userIds) {
        return UserListResp.newBuilder()
                .setCode(FollowErrorCode.SUCCESS.getCode())
                .setMsg(FollowErrorMessages.getMsg(FollowErrorCode.SUCCESS.getCode()))
                .addAllUserIds(userIds)
                .build();
    }

    /**
     * 对应 Go {@code successRecommendResp}（{@code helper.go:73-79}）。
     *
     * <p>⚠️ Go 在两条「推不出推荐」的路径上传的是<b>字面量 {@code nil}</b>
     * （{@code getrecommendationslogic.go:64} 与 {@code :168}），
     * 对应本方法的 {@code null} 入参 —— 这里刻意<b>不</b>用空列表代替，
     * 因为两者的 Java 侧语义要说清楚：{@code null} 是 Go 的 nil（proto 不写字段），
     * 与 {@link #successUserListResp} 里「Go 传的是 make(...,0,n) 非 nil 空切片」不同。
     * <b>wire 上二者仍然一致</b>，区别只在代码可读性。
     */
    public RecommendResp successRecommendResp(List<RecommendResp.RecommendUser> users) {
        RecommendResp.Builder builder = RecommendResp.newBuilder()
                .setCode(FollowErrorCode.SUCCESS.getCode())
                .setMsg(FollowErrorMessages.getMsg(FollowErrorCode.SUCCESS.getCode()));
        if (users != null && !users.isEmpty()) {
            builder.addAllUsers(users);
        }
        return builder.build();
    }

    // =============================================================================
    // 推荐算法纯函数 —— toSet / mapKeys / scoreWeight / clamp*
    // =============================================================================

    /**
     * 对应 Go {@code toSet}（{@code helper.go:96-102}）：切片 → 去重集合。
     *
     * <p>⚠️ <b>输入本身可能已含重复</b>：{@code ListAllFollowTargets} 走的是
     * {@code SELECT target_id}，若历史数据里存在同一 target 的多行（uk_follow_pair
     * 保证不了历史导入数据），去重是必需的。
     */
    public java.util.Set<Long> toSet(List<Long> userIds) {
        return new java.util.HashSet<>(userIds);
    }

    /**
     * 对应 Go {@code mapKeys}（{@code helper.go:104-113}）：集合 → <b>升序</b>切片。
     *
     * <p><b>⚠️ 排序不是可有可无的整理，是确定性来源</b>：
     * Go 的 map 遍历顺序随机，这段代码用 {@code sort.Slice} 把它钉成升序，
     * 从而让 BFS 的「本层节点遍历顺序」确定 ⇒ 分数累加顺序确定 ⇒ 最终排序确定。
     * Java 的 {@code HashSet} 同样无序 ⇒ <b>必须</b>排序，
     * 否则同一份数据在不同 JVM 上可能给出不同的推荐结果。
     * {@link java.util.TreeSet} 是这份升序契约的直接实现。
     */
    public List<Long> sortedKeys(java.util.Set<Long> values) {
        return new ArrayList<>(new java.util.TreeSet<>(values));
    }

    /**
     * 对应 Go {@code scoreWeight}（{@code helper.go:115-128}）：
     * <pre>
     * if maxDepth &lt; 2 { maxDepth = 3 }
     * if depth    &lt; 2 { depth    = 2 }
     * weight := (maxDepth - depth + 1) * 10
     * if weight &lt; 10 { weight = 10 }
     * </pre>
     *
     * <p><b>⚠️ 两处「看起来多余」的下界修正各有来由，不能删</b>：
     * <ul>
     *   <li>{@code maxDepth < 2 ⇒ 3}：深度为 0/1 时权重会算出负数或 0。</li>
     *   <li>{@code depth < 2 ⇒ 2}：BFS 的 {@code currentDepth} 从 2 起（{@code :108}），
     *       这条修正实际不可达，但它保证该函数是<b>纯函数</b> —— 任意入参都无负权重。</li>
     * </ul>
     * 权重恒 &gt;= 10 ⇒ <b>不存在 0 分候选</b>，所以 {@code :149} 的
     * {@code if score &lt;= 0 { continue }} 是纯防御性代码（Java 侧同样保留）。
     */
    public int scoreWeight(int maxDepth, int depth) {
        int effectiveMaxDepth = (maxDepth < 2) ? 3 : maxDepth;
        int effectiveDepth = (depth < 2) ? 2 : depth;

        int weight = (effectiveMaxDepth - effectiveDepth + 1) * 10;
        if (weight < 10) {
            weight = 10;
        }
        return weight;
    }

    /**
     * 对应 Go {@code clampRecommendDepth}（{@code helper.go:130-138}）：
     * {@code depth < 2 ⇒ 3}（<b>注意是 3 不是 2</b>）、{@code depth > 5 ⇒ 5}、否则原值。
     *
     * <p>⚠️ 「小于 2 时回到 3」看着像手滑，实则是 Go 的实际行为：
     * 配 {@code MaxDepth: 1} 的结果是 {@code 3} 而不是 {@code 2}。照搬。
     */
    public int clampRecommendDepth(int depth) {
        if (depth < 2) {
            return 3;
        }
        if (depth > 5) {
            return 5;
        }
        return depth;
    }

    /**
     * 对应 Go {@code clampFanout}（{@code helper.go:140-147}）：
     * {@code fanout <= 0 ⇒ 50}、{@code fanout > 200 ⇒ 200}、否则原值。
     *
     * <p>⚠️ 上界 200 是<b>硬编码魔数</b>，Go yaml 里没有对应字段 ——
     * 不新增配置项（AGENTS.md §「不新增 Go 侧不存在的开关」）。
     */
    public int clampFanout(int fanout) {
        if (fanout <= 0) {
            return 50;
        }
        if (fanout > 200) {
            return 200;
        }
        return fanout;
    }
}