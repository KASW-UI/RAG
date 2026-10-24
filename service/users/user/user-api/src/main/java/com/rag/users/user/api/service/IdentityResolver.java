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
     *
     * <p>⚠️ <b>本方法只适用于 Go 侧「拒绝 uid&lt;=0」的端点</b>：{@code get} / {@code avatar/*}。
     * {@code delete} / {@code update} 在 Go 侧<b>不</b>拒绝，必须改用
     * {@link #claimedUIDAllowNonPositive(HttpServletRequest)}。
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
     * 🔴 2026-10-23（DRIFT-USERAPI-002 已修）：<b>不</b>做 {@code uid <= 0} 判定的取值。
     *
     * <p><b>Go 出处（消费点）</b>：Go 侧<b>并存两套 uid 语义</b>，而且 delete/update 与
     * get/avatar <b>不一样</b>：
     * <ul>
     *   <li>{@code get} / {@code avatar/history} / {@code avatar} / {@code avatar/select}
     *       → 走 {@code identity.ClaimedUID}
     *       （{@code service/user/user/identity/resolve.go:66-79}）：
     *       <pre>
     *       claim, ok := ctx.Value("userId").(json.Number)
     *       if !ok { return 0, ErrInvalidClaim }
     *       uid, err := claim.Int64()
     *       if err != nil || uid &lt;= 0 { return 0, ErrInvalidClaim }   // ← :75 有 uid&lt;=0 判定
     *       </pre></li>
     *   <li>{@code delete} / {@code update}
     *       → <b>Logic 内联</b>，<b>没有</b> uid&lt;=0 判定：
     *       <pre>
     *       // service/user/user/api/internal/logic/user/deleteuserlogic.go:37-47
     *       userId, ok := l.ctx.Value("userId").(json.Number)
     *       if !ok { … 1006 }
     *       uid, err := userId.Int64()
     *       if err != nil { … 1006 }
     *       // ← 此处**没有** uid &lt;= 0 的分支 ⇒ uid=0 / 负数照常进 rpc
     *       // service/user/user/api/internal/logic/user/updateuserlogic.go:36-46 同款
     *       </pre></li>
     * </ul>
     *
     * <p><b>可观察差异</b>（双侧活体对拍实测，2026-10-23 21:12，真进程 + 真 PG + 真 Redis）：
     * <pre>
     *   token 签名合法、userId = 0，打 /user/delete 与 /user/update
     *   Go  : 200 {"code":1003,"msg":"用户不存在","data":null}   ← uid=0 透传到 rpc，rpc 查无此人 ⇒ NOT_FOUND
     *   RAG : 200 {"code":1006,"msg":"TOKEN已过期","data":null}   ← 修复前：在 api 层就拒了，**0 次** rpc 调用
     * </pre>
     * ⇒ 差异不止于返回值，还包括<b>是否触达下游</b>（可观测的下游调用次数）。
     *
     * <p>⚠️ <b>为什么这仍然是安全的</b>：uid&lt;=0 进 rpc 后，user-rpc 的
     * {@code FindOneByUid(uid)} 必然查无此人 ⇒ {@code NOT_FOUND} ⇒ 1003，
     * <b>不会</b>删掉/改掉任何真实用户的数据（uid 是主键，&lt;=0 的行不存在）。
     *
     * <p>⚠️ <b>不要把它合并回 {@link #claimedUID}</b>：那正是 §3.4.4 记的
     * 「安全规则的第二份实现本身就是缺陷来源」的同款形态 —— Go 自己就是两套，
     * 合并成一套等于擅自"修好"Go 的不对称，制造一处新的语义漂移。
     */
    public static long claimedUIDAllowNonPositive(HttpServletRequest request) {
        if (request == null) {
            throw new ErrInvalidClaimException(MSG_INVALID_CLAIM);
        }
        Object v = request.getAttribute("userId");
        if (!(v instanceof Long)) {
            // claim 缺失 / 类型不对 → 与 Go 的 type assertion 失败同款 ⇒ 1006
            throw new ErrInvalidClaimException(MSG_INVALID_CLAIM);
        }
        // 不做 uid <= 0 判定：Go 的 delete / update 不做。
        return (Long) v;
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
