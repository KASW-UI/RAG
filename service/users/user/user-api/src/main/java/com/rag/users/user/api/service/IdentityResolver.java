package com.rag.users.user.api.service;

import com.rag.users.user.proto.GetUserReq;
import com.rag.users.user.proto.UserServiceGrpc;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Identity 解析器 —— 6 个观察面 1:1 对齐 Go 端 identity_contract.md。
 *
 * 对应 Go: service/user/user/identity/resolve.go + service/user/user/api/internal/identity/resolve.go。
 *
 * 6 个可观察面（identity_contract.md §1 冻结）：
 *   1. Return：SubjectRef = {authority_id, tenant_id, subject_id}，subject_id = UID 十进制字符串
 *   2. Error：5 个 sentinel exception → 5 个业务码
 *   3. Log：本类不打日志；调用方（Service）打 logger
 *   4. Trace/Context：从 request attribute "userId" 取正整数 UID（由 JwtAuthInterceptor 写入）
 *   5. Metric：本类不写 metrics；调用方按 5 种错误 → 5 种业务码映射后写 metrics
 *   6. Lifecycle：纯函数，无状态；method 调用顺序：ClaimedUID → GetUser → status check → SubjectRef
 *
 * L1 外部契约冻结（identity_contract.md §2）：
 *   - AuthorityID = "rtw.identity"（RTW 进程内稳定的 issuer 串，<b>不</b>从 JWT/客户端事件取）
 *   - PlatformTenantID = "platform"（RTW v1 唯一用户空间固定命名空间，<b>不是</b>组织/团队/付费租户）
 *   - SubjectRef.subject_id = UID 的十进制字符串
 *
 * L3 运行时约束（冻结）：
 *   - JWT 请求体 / 客户端事件 / 任意 X-User-ID 头 <b>不参与解析</b>
 *   - 缺失 / 非法 / 已删除 / RPC 错配 / JWT 验签失败都拒绝
 *   - proto3 presence 必须保留：status=0 且字段存在才表示活跃
 */
@Component
public class IdentityResolver {

    // ============== L1 冻结常量 ==============

    public static final String AuthorityID = "rtw.identity";
    public static final String PlatformTenantID = "platform";

    // ============== L1 冻结错误消息 ==============

    private static final String MSG_INVALID_CLAIM = "invalid authenticated user claim";
    private static final String MSG_USER_NOT_FOUND = "authenticated user not found";
    private static final String MSG_IDENTITY_MISMATCH = "authenticated user RPC identity mismatch";
    private static final String MSG_USER_INACTIVE = "authenticated user inactive";
    private static final String MSG_STATUS_UNAVAILABLE = "authenticated user status unavailable";

    // ============== SubjectRef wire type ==============

    /**
     * wire JSON = {"authority_id":"rtw.identity","tenant_id":"platform","subject_id":"<UID>"}
     * 与 identity_contract.md §2 锁定的三元组格式一致。
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
        public int hashCode() { return Objects.hash(authorityId, tenantId, subjectId); }
    }

    // ============== L3 冻结逻辑 ==============

    /**
     * 从 request attribute "userId" 取正整数 UID。
     * JwtAuthInterceptor 在 JWT 验签通过后把 Long 类型的 userId 写进 attribute。
     */
    public static long claimedUID(HttpServletRequest request) {
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
     * 验证已认证的 UID 仍然在 user-rpc 中存在且 status=0（活跃）。
     */
    public static com.rag.users.user.proto.UserInfo resolveUser(HttpServletRequest request,
                                                               UserServiceGrpc.UserServiceBlockingStub users) {
        long uid = claimedUID(request);
        if (users == null) {
            throw new ErrIdentityMismatchException("user RPC is nil: " + MSG_IDENTITY_MISMATCH);
        }
        GetUserReq req = GetUserReq.newBuilder().setUid(uid).build();
        com.rag.users.user.proto.GetUserResp resp;
        try {
            resp = users.getUser(req);
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == Status.Code.NOT_FOUND) {
                throw new ErrUserNotFoundException("user RPC: " + MSG_USER_NOT_FOUND);
            }
            throw e;
        }
        if (resp == null || !resp.getFound()) {
            throw new ErrUserNotFoundException(MSG_USER_NOT_FOUND);
        }
        com.rag.users.user.proto.UserInfo user = resp.getUser();
        if (user == null || user.equals(com.rag.users.user.proto.UserInfo.getDefaultInstance())) {
            throw new ErrUserNotFoundException(MSG_USER_NOT_FOUND);
        }
        if (user.getUid() != uid) {
            throw new ErrIdentityMismatchException(MSG_IDENTITY_MISMATCH);
        }
        // proto3 presence: status 是 optional int64
        if (!user.hasStatus()) {
            throw new ErrStatusUnavailableException(MSG_STATUS_UNAVAILABLE);
        }
        if (user.getStatus() != 0L) {
            throw new ErrUserInactiveException(MSG_USER_INACTIVE);
        }
        return user;
    }

    /**
     * 当前 RTW v1 唯一签发的三元组：
     * {authority_id: "rtw.identity", tenant_id: "platform", subject_id: <UID 十进制>}
     */
    public static SubjectRef resolveSubjectRef(HttpServletRequest request,
                                               UserServiceGrpc.UserServiceBlockingStub users) {
        com.rag.users.user.proto.UserInfo user = resolveUser(request, users);
        return new SubjectRef(
                AuthorityID,
                PlatformTenantID,
                Long.toString(user.getUid())
        );
    }

    // ============== 异常类型（对应 Go 的 sentinel error pattern） ==============

    public static class ErrInvalidClaimException extends RuntimeException {
        public ErrInvalidClaimException(String msg) { super(msg); }
    }
    public static class ErrUserNotFoundException extends RuntimeException {
        public ErrUserNotFoundException(String msg) { super(msg); }
    }
    public static class ErrIdentityMismatchException extends RuntimeException {
        public ErrIdentityMismatchException(String msg) { super(msg); }
    }
    public static class ErrUserInactiveException extends RuntimeException {
        public ErrUserInactiveException(String msg) { super(msg); }
    }
    public static class ErrStatusUnavailableException extends RuntimeException {
        public ErrStatusUnavailableException(String msg) { super(msg); }
    }
}
