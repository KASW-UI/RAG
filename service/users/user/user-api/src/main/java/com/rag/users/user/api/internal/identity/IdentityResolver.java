package com.rag.users.user.api.internal.identity;

import com.rag.users.user.proto.UserInfo;
import com.rag.users.user.proto.UserServiceGrpc;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import java.util.Objects;

/**
 * 对应 Go 端 service/user/user/identity/resolve.go + service/user/user/api/internal/identity/resolve.go。
 *
 * <p>L4 实现机制差异：Go 端有共享包 + api 适配器两层（解决 import path 限制）；
 * Java 端直接一个 {@code IdentityResolver} 类，user-api 单进程内用，不放 users-common。
 *
 * <p>六个可观察面逐项对齐（identity_contract.md 锁定的合同）：
 * <ul>
 *   <li><b>Return</b>：{@link SubjectRef} = {@code {authority_id, tenant_id, subject_id}}；
 *       subject_id 是 UID 的十进制字符串（不是 hex、不是 base64）</li>
 *   <li><b>Error</b>：5 个 sentinel error —— {@link #ErrInvalidClaim}, {@link #ErrUserNotFound},
 *       {@link #ErrIdentityMismatch}, {@link #ErrUserInactive}, {@link #ErrStatusUnavailable}；
 *       对应 5 个业务码（由 GetUserLogic 映射到 ErrorCode 端）</li>
 *   <li><b>Log</b>：本类不打日志；调用方（GetUserLogic）打 logger.LogBusinessErr</li>
 *   <li><b>Trace/Context</b>：从 request attribute "userId" 取正整数 UID；
 *       userId 由 {@code JwtAuthenticationFilter} 从 JWT claims.userId 写入</li>
 *   <li><b>Metric</b>：本类不写 metrics；调用方（GetUserLogic）按 5 种错误 → 5 种业务码 映射后写 metrics</li>
 *   <li><b>Lifecycle</b>：纯函数，无状态；按 method 调用顺序：{@code ClaimedUID → GetUser → status check → SubjectRef}</li>
 * </ul>
 *
 * <p>L1 外部契约冻结（identity_contract.md §2）：
 * <ul>
 *   <li>{@link #AuthorityID} = {@code "rtw.identity"} —— RTW 进程内稳定的 issuer 串，<b>不</b>从 JWT / 客户端事件取</li>
 *   <li>{@link #PlatformTenantID} = {@code "platform"} —— RTW v1 唯一用户空间固定命名空间，<b>不是</b>组织、团队、付费租户</li>
 *   <li>{@code SubjectRef.subject_id} = UID 的十进制字符串</li>
 * </ul>
 *
 * <p>L3 运行时约束（冻结）：
 * <ul>
 *   <li>JWT 请求体、客户端事件、任意 {@code X-User-ID} 头 <b>不参与解析</b></li>
 *   <li>缺失 / 非法 / 已删除 / RPC 错配 / JWT 验签失败都拒绝</li>
 *   <li>proto3 presence 必须保留：{@code status=0} 且字段存在才表示活跃；字段缺失返回 {@link #ErrStatusUnavailable}；
 *       非零 status 返回 {@link #ErrUserInactive}</li>
 * </ul>
 */
public final class IdentityResolver {

    // ============== L1 冻结常量（对应 Go identity/resolve.go:27-33） ==============

    public static final String AuthorityID = "rtw.identity";
    public static final String PlatformTenantID = "platform";

    // ============== L1 冻结错误消息（对应 Go identity/resolve.go:19-25） ==============

    /**
     * L4 实现差异：Go 用 sentinel error 单例（{@code var ErrInvalidClaim = errors.New(...)}），
     * 但 Java exception 复用同一实例会被 JUnit / 框架判定为重复报告。
     * 这里只冻结错误消息字符串，每次 throw 都 new 一个实例 —— 调用方按 {@code instanceof} 判断类型，
     * 不依赖引用相等。
     */
    private static final String MSG_INVALID_CLAIM = "invalid authenticated user claim";
    private static final String MSG_USER_NOT_FOUND = "authenticated user not found";
    private static final String MSG_IDENTITY_MISMATCH = "authenticated user RPC identity mismatch";
    private static final String MSG_USER_INACTIVE = "authenticated user inactive";
    private static final String MSG_STATUS_UNAVAILABLE = "authenticated user status unavailable";

    private IdentityResolver() {}

    // ============== L1 冻结 wire type（对应 Go SubjectRef） ==============

    /**
     * 对应 Go {@code SubjectRef struct { AuthorityID, TenantID, SubjectID string }}。
     * wire JSON = {@code {"authority_id":"rtw.identity","tenant_id":"platform","subject_id":"<UID>"}}
     * —— 与 identity_contract.md §2 锁定的三元组格式一致。
     */
    public static final class SubjectRef {
        private String authorityId;
        private String tenantId;
        private String subjectId;

        public SubjectRef() {}

        public SubjectRef(String authorityId, String tenantId, String subjectId) {
            this.authorityId = authorityId;
            this.tenantId = tenantId;
            this.subjectId = subjectId;
        }

        public String getAuthorityId() { return authorityId; }
        public void setAuthorityId(String authorityId) { this.authorityId = authorityId; }
        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
        public String getSubjectId() { return subjectId; }
        public void setSubjectId(String subjectId) { this.subjectId = subjectId; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof SubjectRef)) return false;
            SubjectRef r = (SubjectRef) o;
            return Objects.equals(authorityId, r.authorityId)
                    && Objects.equals(tenantId, r.tenantId)
                    && Objects.equals(subjectId, r.subjectId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(authorityId, tenantId, subjectId);
        }
    }

    // ============== L3 冻结逻辑（对应 Go identity/resolve.go:66-127） ==============

    /**
     * 对应 Go {@code ClaimedUID(ctx) (int64, error)}。
     *
     * <p>从 request attribute "userId" 取正整数 UID。
     * {@link com.rag.users.user.api.internal.filter.JwtAuthenticationFilter} 在 JWT 验签通过后
     * 把 {@code Long} 类型的 userId 写进 request attribute（Go 端写 ctx，这里是 request attribute，
     * 这是 L4 实现差异，但语义一致）。
     *
     * <p>L3 行为（冻结）：
     * <ul>
     *   <li>ctx / request 为 null → {@link #ErrInvalidClaim}</li>
     *   <li>userId 不是 {@link Long}（Go 端是 json.Number）→ {@link #ErrInvalidClaim}</li>
     *   <li>userId {@code <= 0} → {@link #ErrInvalidClaim}</li>
     * </ul>
     */
    public static long claimedUID(jakarta.servlet.http.HttpServletRequest request) {
        if (request == null) {
            throw new ErrInvalidClaimException(MSG_INVALID_CLAIM);
        }
        Object v = request.getAttribute("userId");
        if (!(v instanceof Long)) {
            throw new ErrInvalidClaimException(MSG_INVALID_CLAIM);
        }
        long uid = (Long) v;
        if (uid <= 0) {
            throw new ErrInvalidClaimException(MSG_INVALID_CLAIM);
        }
        return uid;
    }

    /**
     * 对应 Go {@code ResolveUser(ctx, users UserReader) (*pb.UserInfo, error)}。
     *
     * <p>验证已认证的 UID 仍然在 User RPC 中存在且 status=0（活跃）。
     *
     * <p>L3 行为（冻结）：
     * <ol>
     *   <li>{@code claimedUID(request)} → uid；失败抛 {@link #ErrInvalidClaim}</li>
     *   <li>{@code users.GetUser(GetUserReq{Uid: uid})} → resp；gRPC {@code NotFound} 抛 {@link #ErrUserNotFound}，其它 RPC 错透传</li>
     *   <li>{@code resp == nil || !resp.Found || resp.User == null} → {@link #ErrUserNotFound}</li>
     *   <li>{@code resp.User.Uid != uid} → {@link #ErrIdentityMismatch}</li>
     *   <li>{@code resp.User.Status == null} → {@link #ErrStatusUnavailable}</li>
     *   <li>{@code resp.User.getStatus() != 0} → {@link #ErrUserInactive}（status=1 banned 和 status=2 unknown nonzero 都走这条）</li>
     *   <li>返回 {@code resp.User}</li>
     * </ol>
     */
    public static UserInfo resolveUser(jakarta.servlet.http.HttpServletRequest request,
                                       UserServiceGrpc.UserServiceBlockingStub users) {
        long uid = claimedUID(request);
        if (users == null) {
            // 对齐 Go 端 fmt.Errorf("user RPC is nil: %w", ErrIdentityMismatch) 的 message 前缀
            throw new ErrIdentityMismatchException("user RPC is nil: " + MSG_IDENTITY_MISMATCH);
        }
        com.rag.users.user.proto.GetUserReq req = com.rag.users.user.proto.GetUserReq.newBuilder()
                .setUid(uid)
                .build();
        com.rag.users.user.proto.GetUserResp resp;
        try {
            resp = users.getUser(req);
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == Status.Code.NOT_FOUND) {
                // 对齐 Go 端 fmt.Errorf("user RPC: %w", ErrUserNotFound) 的 message 前缀
                throw new ErrUserNotFoundException("user RPC: " + MSG_USER_NOT_FOUND);
            }
            throw e;   // 其它 RPC 错透传 —— 由调用方（GetUserLogic）按 codes.Internal / default 处理
        }
        if (resp == null || !resp.getFound()) {
            throw new ErrUserNotFoundException(MSG_USER_NOT_FOUND);
        }
        UserInfo user = resp.getUser();
        // L4 语义对齐：Go 端 response.User == nil → Java 端 UserInfo equals default instance
        // （Java proto3 build() 会把未设置的子消息初始化为 default instance，与 Go 的 nil 不对齐）
        if (user == null || user.equals(com.rag.users.user.proto.UserInfo.getDefaultInstance())) {
            throw new ErrUserNotFoundException(MSG_USER_NOT_FOUND);
        }
        if (user.getUid() != uid) {
            throw new ErrIdentityMismatchException(MSG_IDENTITY_MISMATCH);
        }
        // proto3 presence 必须保留：UserInfo.status 是 optional int64
        // 用户没有 setStatus() → status field is missing → hasStatus() 返回 false
        if (!user.hasStatus()) {
            throw new ErrStatusUnavailableException(MSG_STATUS_UNAVAILABLE);
        }
        if (user.getStatus() != 0L) {
            throw new ErrUserInactiveException(MSG_USER_INACTIVE);
        }
        return user;
    }

    /**
     * 对应 Go {@code ResolveSubjectRef(ctx, users) (SubjectRef, error)}。
     *
     * <p>当前 RTW v1 唯一签发的三元组：
     * <pre>
     *   {authority_id: "rtw.identity", tenant_id: "platform", subject_id: "&lt;UID 十进制&gt;"}
     * </pre>
     *
     * <p>L1 冻结：tenant_id 固定为 {@code "platform"}（RTW v1 兼容位，<b>不是</b>租户/组织）。
     */
    public static SubjectRef resolveSubjectRef(jakarta.servlet.http.HttpServletRequest request,
                                               UserServiceGrpc.UserServiceBlockingStub users) {
        UserInfo user = resolveUser(request, users);
        return new SubjectRef(
                AuthorityID,
                PlatformTenantID,
                Long.toString(user.getUid())
        );
    }

    // ============== 异常类型（对应 Go 的 sentinel error pattern） ==============

    /** 对应 Go {@code ErrInvalidClaim}。 */
    public static class ErrInvalidClaimException extends RuntimeException {
        public ErrInvalidClaimException(String msg) { super(msg); }
    }

    /** 对应 Go {@code ErrUserNotFound}。 */
    public static class ErrUserNotFoundException extends RuntimeException {
        public ErrUserNotFoundException(String msg) { super(msg); }
    }

    /** 对应 Go {@code ErrIdentityMismatch}。 */
    public static class ErrIdentityMismatchException extends RuntimeException {
        public ErrIdentityMismatchException(String msg) { super(msg); }
    }

    /** 对应 Go {@code ErrUserInactive}。 */
    public static class ErrUserInactiveException extends RuntimeException {
        public ErrUserInactiveException(String msg) { super(msg); }
    }

    /** 对应 Go {@code ErrStatusUnavailable}。 */
    public static class ErrStatusUnavailableException extends RuntimeException {
        public ErrStatusUnavailableException(String msg) { super(msg); }
    }
}