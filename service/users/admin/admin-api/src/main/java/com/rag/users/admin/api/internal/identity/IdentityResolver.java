package com.rag.users.admin.api.internal.identity;

import com.rag.users.admin.proto.AdminInfo;
import com.rag.users.admin.proto.AdminServiceGrpc;
import com.rag.users.admin.proto.GetSelfReq;
import com.rag.users.admin.proto.GetSelfResp;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import java.util.Objects;

/**
 * 对应 Go 端 admin/api/internal/logic/admin/getselflogic.go 中
 * <pre>
 *   userId, ok := l.ctx.Value("userId").(json.Number)
 *   if !ok { return ErrorTokenRuntime }
 *   uid, err := userId.Int64()
 *   if err != nil { return ErrorTokenRuntime }
 *   rpcReq := &pb.GetSelfReq{Uid: uid}
 *   rpcResp, err := l.svcCtx.AdminRpc.GetSelf(...)
 * </pre>
 * 这一段语义在 Java 端被抽象为 {@link #claimedUID(HttpServletRequest)} +
 * {@link #resolveAdmin(HttpServletRequest, AdminServiceGrpc.AdminServiceBlockingStub)}。
 *
 * <p><b>L4 实现机制差异</b>：Go 端在每个 Logic 里都重复写"取 ctx userId → 调 AdminRpc.GetSelf"，
 * Java 端用 IdentityResolver 集中起来（与 user-api/internal/identity/IdentityResolver.java 风格一致），
 * 后续 11 个 protected RPC（banuser/delete/getuser/getuserlist/invite/create/
 * resetuserpassword/unbanuser/update/updateuser/logout 之外的 query 类）共用。
 *
 * <p><b>关键差异（与 user-api IdentityResolver 不同）</b>：
 * <ul>
 *   <li>user.proto 的 {@code UserInfo} 有 {@code status} 字段（proto3 optional），user-api IdentityResolver
 *       校验 {@code hasStatus() / getStatus() == 0} 并抛 {@link #ErrUserInactiveException}
 *       / {@link #ErrStatusUnavailableException}（映射业务码 1011 / 1015）</li>
 *   <li>admin.proto 的 {@code AdminInfo} <b>没有</b> status 字段（admin 第 7-12 行只有 uid/username/email/extra_info），
 *       所以本 admin IdentityResolver <b>不</b>抛 {@code ErrAdminInactiveException} /
 *       {@code ErrStatusUnavailableException} —— 1:1 对齐 Go 端 admin 域的语义</li>
 *   <li>user-api IdentityResolver 抛 {@code ErrUserNotFoundException}；admin-api 改为
 *       {@link #ErrAdminNotFoundException}（命名按 domain），调用方仍映射业务码 1003（ErrorUserNotExist）</li>
 * </ul>
 *
 * <p>L1 外部契约冻结：
 * <ul>
 *   <li>{@link #AuthorityID} = {@code "rtw.identity"} —— RTW 进程内稳定的 issuer 串，
 *       <b>不</b>从 JWT / 客户端事件取（与 user-api IdentityResolver 同源）</li>
 *   <li>{@link #PlatformTenantID} = {@code "platform"} —— RTW v1 唯一用户空间固定命名空间，
 *       <b>不是</b>组织、团队、付费租户（与 user-api IdentityResolver 同源）</li>
 *   <li>{@code SubjectRef.subject_id} = UID 的十进制字符串</li>
 * </ul>
 *
 * <p>L3 运行时约束（冻结）：
 * <ul>
 *   <li>JWT 请求体、客户端事件、任意 {@code X-Admin-ID} 头 <b>不参与解析</b></li>
 *   <li>缺失 / 非法 / 已删除 / RPC 错配 / JWT 验签失败都拒绝</li>
 *   <li>claim 名 = {@code "userId"}（与 JwtUtil.getToken 一致）</li>
 * </ul>
 */
public final class IdentityResolver {

    // ============== L1 冻结常量（与 user-api IdentityResolver 同源） ==============

    public static final String AuthorityID = "rtw.identity";
    public static final String PlatformTenantID = "platform";

    // ============== L1 冻结错误消息（对应 Go getselflogic.go:38-44 的 2 个 LogBusinessErr msg） ==============

    /** 对应 Go {@code "ctx userId is not json.Number"}。Java 端 attribute 类型是 {@link Long}，
     * 不是 json.Number；attribute 不是 Long 也走这条。 */
    private static final String MSG_INVALID_CLAIM = "invalid authenticated admin claim";
    /** 对应 Go 端 gRPC {@code codes.NotFound}（admin-rpc.GetSelf 找不到该 uid 的 admin）。 */
    private static final String MSG_ADMIN_NOT_FOUND = "authenticated admin not found";
    /** JWT 中的 uid 与 admin-rpc 返回的 AdminInfo.uid 不一致 —— Go 端不会触发（Go 端不二次校验），
     * Java 端 IdentityResolver 引入后才会触发（防御性 invariant check）。 */
    private static final String MSG_IDENTITY_MISMATCH = "authenticated admin RPC identity mismatch";

    private IdentityResolver() {}

    // ============== L1 冻结 wire type（与 user-api IdentityResolver 同款） ==============

    /**
     * 对应 user-api IdentityResolver.SubjectRef（admin 与 user 共享 RTW 三元组模型）。
     * wire JSON = {@code {"authority_id":"rtw.identity","tenant_id":"platform","subject_id":"<UID>"}}
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

    // ============== L3 冻结逻辑（对应 Go getselflogic.go:36-49） ==============

    /**
     * 对应 Go {@code userId, ok := l.ctx.Value("userId").(json.Number); if !ok { return ErrorTokenRuntime }} +
     * {@code uid, err := userId.Int64(); if err != nil { return ErrorTokenRuntime }}。
     *
     * <p>从 request attribute {@code "userId"} 取正整数 UID。
     * {@link com.rag.users.admin.api.internal.filter.JwtAuthenticationFilter} 在 JWT 验签通过后
     * 把 {@code Long} 类型的 userId 写进 request attribute（Go 端写 ctx，这里是 request attribute，
     * 这是 L4 实现差异，但语义一致）。
     *
     * <p>L3 行为（冻结，对应 Go 第 36-45 行）：
     * <ul>
     *   <li>request 为 null → {@link #ErrInvalidClaimException}</li>
     *   <li>userId 不是 {@link Long}（Go 端是 json.Number） → {@link #ErrInvalidClaimException}</li>
     *   <li>userId {@code <= 0} → {@link #ErrInvalidClaimException}</li>
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
     * 对应 Go {@code rpcResp, err := l.svcCtx.AdminRpc.GetSelf(l.ctx, &pb.GetSelfReq{Uid: uid})} +
     * rpcResp 失败时的 switch（NotFound→ErrorUserNotExist；Internal→ErrorServerCommon；default→CodeServerBusy）。
     *
     * <p>验证已认证的 UID 仍然在 admin-rpc 中存在（status=0 在 admin 域不强制，admin.proto AdminInfo
     * <b>没有</b> status 字段）。
     *
     * <p>L3 行为（冻结，对应 Go 第 49-61 行 + 增补 invariant check）：
     * <ol>
     *   <li>{@link #claimedUID(HttpServletRequest)} → uid；失败抛 {@link #ErrInvalidClaimException}</li>
     *   <li>{@code adminRpc.getSelf(GetSelfReq{Uid: uid})} → resp；
     *       gRPC {@code NotFound} 抛 {@link #ErrAdminNotFoundException}，其它 RPC 错透传</li>
     *   <li>{@code resp == null} → {@link #ErrAdminNotFoundException}</li>
     *   <li>{@code resp.Admin == null || equals(default instance)} → {@link #ErrAdminNotFoundException}</li>
     *   <li>{@code resp.Admin.Uid != uid} → {@link #ErrIdentityMismatchException}</li>
     *   <li>返回 {@code resp.Admin}</li>
     * </ol>
     */
    public static AdminInfo resolveAdmin(jakarta.servlet.http.HttpServletRequest request,
                                         AdminServiceGrpc.AdminServiceBlockingStub adminRpc) {
        long uid = claimedUID(request);
        if (adminRpc == null) {
            throw new ErrIdentityMismatchException("admin RPC is nil: " + MSG_IDENTITY_MISMATCH);
        }
        GetSelfReq req = GetSelfReq.newBuilder().setUid(uid).build();
        GetSelfResp resp;
        try {
            resp = adminRpc.getSelf(req);
        } catch (StatusRuntimeException e) {
            // 对齐 Go 端 getselflogic.go:53-60 的 switch
            if (e.getStatus().getCode() == Status.Code.NOT_FOUND) {
                throw new ErrAdminNotFoundException("admin RPC: " + MSG_ADMIN_NOT_FOUND);
            }
            throw e;   // 其它 RPC 错（INTERNAL 等）透传 —— 由调用方（GetSelfLogic）按 codes.Internal / default 处理
        }
        if (resp == null) {
            throw new ErrAdminNotFoundException(MSG_ADMIN_NOT_FOUND);
        }
        AdminInfo admin = resp.getAdmin();
        // L4 语义对齐：Go 端 response.Admin == nil → Java 端 AdminInfo equals default instance
        // （Java proto3 build() 会把未设置的子消息初始化为 default instance，与 Go 的 nil 不对齐）
        if (admin == null || admin.equals(AdminInfo.getDefaultInstance())) {
            throw new ErrAdminNotFoundException(MSG_ADMIN_NOT_FOUND);
        }
        if (admin.getUid() != uid) {
            throw new ErrIdentityMismatchException(MSG_IDENTITY_MISMATCH);
        }
        return admin;
    }

    /**
     * 对应 user-api IdentityResolver.SubjectRef —— admin 与 user 共享 RTW v1 三元组。
     */
    public static SubjectRef resolveSubjectRef(jakarta.servlet.http.HttpServletRequest request,
                                               AdminServiceGrpc.AdminServiceBlockingStub adminRpc) {
        AdminInfo admin = resolveAdmin(request, adminRpc);
        return new SubjectRef(
                AuthorityID,
                PlatformTenantID,
                Long.toString(admin.getUid())
        );
    }

    // ============== 异常类型（对应 Go 的 sentinel error pattern，但精简到 admin 域需要的 3 个） ==============

    /** 对应 Go {@code fmt.Errorf("ctx userId is not json.Number")} + {@code fmt.Errorf("parse uid failed: %v", err)}。 */
    public static class ErrInvalidClaimException extends RuntimeException {
        public ErrInvalidClaimException(String msg) { super(msg); }
    }

    /** 对应 Go 端 gRPC {@code codes.NotFound}（admin-rpc.GetSelf 找不到该 uid 的 admin）。 */
    public static class ErrAdminNotFoundException extends RuntimeException {
        public ErrAdminNotFoundException(String msg) { super(msg); }
    }

    /** JWT uid 与 admin-rpc 返回 uid 不一致 —— Go 端不会触发，Java 端 IdentityResolver 引入后才可能触发。 */
    public static class ErrIdentityMismatchException extends RuntimeException {
        public ErrIdentityMismatchException(String msg) { super(msg); }
    }

    // 注：admin.proto AdminInfo 没有 status 字段 —— 不引入 ErrAdminInactiveException / ErrStatusUnavailableException
    // （与 user-api IdentityResolver 不同；DRIFT-016 决策 1:1 保留 admin 域的"无 status"语义）
}