package com.rag.users.admin.api.service;

import com.rag.users.admin.proto.AdminInfo;
import com.rag.users.admin.proto.AdminServiceGrpc;
import com.rag.users.admin.proto.GetSelfReq;
import com.rag.users.admin.proto.GetSelfResp;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Identity 解析器 —— 6 个观察面 1:1 对齐 Go 端 admin 域 identity_contract.md。
 *
 * 对应 Go: service/user/admin/api/internal/identity/resolve.go。
 *
 * <p><b>关键差异（与 user-api IdentityResolver 不同）</b>：
 * <ul>
 *   <li>user.proto 的 {@code UserInfo} 有 status 字段（proto3 optional），user-api IdentityResolver 校验 status=0 并抛
 *       {@code ErrUserInactiveException} / {@code ErrStatusUnavailableException}（映射业务码 1011 / 1015）</li>
 *   <li>admin.proto 的 {@code AdminInfo} <b>没有</b> status 字段，所以本 admin IdentityResolver
 *       <b>不</b>抛 {@code ErrAdminInactiveException} / {@code ErrStatusUnavailableException} —— 1:1 对齐 Go 端 admin 域</li>
 *   <li>user-api IdentityResolver 抛 {@code ErrUserNotFoundException}；admin-api 改为
 *       {@link #ErrAdminNotFoundException}，调用方仍映射业务码 1003（ErrorUserNotExist）</li>
 * </ul>
 *
 * <p>L1 外部契约冻结：
 * <ul>
 *   <li>{@link #AuthorityID} = {@code "rtw.identity"}</li>
 *   <li>{@link #PlatformTenantID} = {@code "platform"}</li>
 *   <li>{@code SubjectRef.subject_id} = UID 的十进制字符串</li>
 * </ul>
 *
 * <p>L3 运行时约束：JWT 请求体 / X-Admin-ID 头 <b>不参与解析</b>；缺失 / 非法 / RPC 错配 / JWT 验签失败都拒绝。
 */
@Component
public class IdentityResolver {

    // ============== L1 冻结常量 ==============

    public static final String AuthorityID = "rtw.identity";
    public static final String PlatformTenantID = "platform";

    // ============== L1 冻结错误消息 ==============

    private static final String MSG_INVALID_CLAIM = "invalid authenticated admin claim";
    private static final String MSG_ADMIN_NOT_FOUND = "authenticated admin not found";
    private static final String MSG_IDENTITY_MISMATCH = "authenticated admin RPC identity mismatch";

    // ============== SubjectRef wire type ==============

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
        public int hashCode() { return Objects.hash(authorityId, tenantId, subjectId); }
    }

    // ============== L3 冻结逻辑 ==============

    /**
     * 从 request attribute "userId" 取正整数 UID。
     * AdminJwtAuthInterceptor 在 JWT 验签通过后把 Long 类型的 userId 写进 attribute。
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
     * 验证已认证的 UID 仍然在 admin-rpc 中存在。
     * 注：admin.proto AdminInfo <b>没有</b> status 字段，所以不抛 inactive / status_unavailable。
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
            if (e.getStatus().getCode() == Status.Code.NOT_FOUND) {
                throw new ErrAdminNotFoundException("admin RPC: " + MSG_ADMIN_NOT_FOUND);
            }
            throw e;
        }
        if (resp == null) {
            throw new ErrAdminNotFoundException(MSG_ADMIN_NOT_FOUND);
        }
        AdminInfo admin = resp.getAdmin();
        if (admin == null || admin.equals(AdminInfo.getDefaultInstance())) {
            throw new ErrAdminNotFoundException(MSG_ADMIN_NOT_FOUND);
        }
        if (admin.getUid() != uid) {
            throw new ErrIdentityMismatchException(MSG_IDENTITY_MISMATCH);
        }
        return admin;
    }

    public static SubjectRef resolveSubjectRef(jakarta.servlet.http.HttpServletRequest request,
                                               AdminServiceGrpc.AdminServiceBlockingStub adminRpc) {
        AdminInfo admin = resolveAdmin(request, adminRpc);
        return new SubjectRef(AuthorityID, PlatformTenantID, Long.toString(admin.getUid()));
    }

    // ============== 异常类型 ==============

    public static class ErrInvalidClaimException extends RuntimeException {
        public ErrInvalidClaimException(String msg) { super(msg); }
    }
    public static class ErrAdminNotFoundException extends RuntimeException {
        public ErrAdminNotFoundException(String msg) { super(msg); }
    }
    public static class ErrIdentityMismatchException extends RuntimeException {
        public ErrIdentityMismatchException(String msg) { super(msg); }
    }
}
