package com.rag.users.admin.api.internal.types;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * 对应 Go 端 internal/types/types.go（139 行，13 个 top-level struct）。
 *
 * <p>L4 实现机制差异：Go 一个文件装 13 个 top-level struct；
 * Java 侧按"一个 public top-level class per file"的限制，<b>用嵌套静态类</b>（public static class）
 * 保留"一个 types 文件 = 一组 HTTP DTO"的语义。
 *
 * <p>L1 wire 契约冻结（json tag 1:1 对齐）：
 * <ul>
 *   <li>{@code int64} 字段带 {@code json:"uid,string"} —— <b>必须序列化为字符串</b>，避免前端 number 精度丢失
 *       （uid 是 Snowflake ID，可能超过 2^53）
 *       → 用 Jackson {@code @JsonProperty("uid")} + 在 setter / getter 上包 {@code Long.toString()}</li>
 *   <li>Go {@code json:"xxx,omitempty"} → Java {@code @JsonInclude(JsonInclude.Include.NON_NULL)}
 *       + 字段用 boxed 类型（{@link Long} / {@link Integer}）而非 primitive</li>
 *   <li>Go {@code json:"xxx,optional"} 为 go-zero 自定义 tag（不是标准 encoding/json），
 *       仅表示"可缺省字段，缺省时为零值"，Jackson 默认就支持 —— 不需要额外注解</li>
 *   <li>Go 内嵌 struct（如 {@code GetSelfResp.Admin AdminInfo}）→ Java 嵌套 static class 实例化</li>
 * </ul>
 *
 * <p>13 个类型逐项对应：
 * <pre>
 *  Go types.go                       →         ApiTypes 嵌套类
 *  ============================================================
 *  AdminInfo                         →         AdminInfo
 *  BanUserReq                        →         BanUserReq
 *  BanUserResp                       →         BanUserResp
 *  CreateAdminReq                    →         CreateAdminReq
 *  CreateAdminResp                   →         CreateAdminResp
 *  CreateInviteReq                   →         CreateInviteReq
 *  CreateInviteResp                  →         CreateInviteResp
 *  DeleteUserReq                     →         DeleteUserReq
 *  DeleteUserResp                    →         DeleteUserResp
 *  GetSelfReq                        →         GetSelfReq
 *  GetSelfResp                       →         GetSelfResp
 *  GetUserListReq                    →         GetUserListReq
 *  GetUserListResp                   →         GetUserListResp
 *  GetUserReq                        →         GetUserReq
 *  GetUserResp                       →         GetUserResp
 *  LoginReq                          → LoginReq
 *  LoginResp                         → LoginResp
 *  LogoutReq                         → LogoutReq
 *  LogoutResp                        → LogoutResp
 *  ResetUserPasswordReq              → ResetUserPasswordReq
 *  ResetUserPasswordResp             → ResetUserPasswordResp
 *  UnBanUserReq                      → UnBanUserReq
 *  UnBanUserResp                     → UnBanUserResp
 *  UpdateSelfReq                     → UpdateSelfReq
 *  UpdateSelfResp                    → UpdateSelfResp
 *  UpdateUserReq                     → UpdateUserReq
 *  UpdateUserResp                    → UpdateUserResp
 *  UserInfo                          → UserInfo
 * </pre>
 */
public class ApiTypes {

    private ApiTypes() {}

    // ============== 1. AdminInfo ==============

    /**
     * 对应 Go {@code AdminInfo}。
     * 字段：Uid（json:"uid,string"），Username，Email（optional），Extrainfo（optional，wire: {@code extra_info}）。
     */
    public static class AdminInfo {
        @JsonProperty("uid")
        private Long uid;
        private String username;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String email;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonProperty("extra_info")
        private Map<String, String> extraInfo;

        /** 返回序列化为字符串（与 Go {@code json:"uid,string"} 对齐）。 */
        public String getUid() {
            return uid == null ? null : uid.toString();
        }
        public void setUid(Long uid) { this.uid = uid; }

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        @JsonProperty("extra_info")
        public Map<String, String> getExtraInfo() { return extraInfo; }
        public void setExtraInfo(Map<String, String> extraInfo) { this.extraInfo = extraInfo; }
    }

    // ============== 2. BanUserReq ==============

    /** 对应 Go {@code BanUserReq}。字段：Uid（json:"uid,string"）。 */
    public static class BanUserReq {
        @JsonProperty("uid")
        private Long uid;

        public String getUid() { return uid == null ? null : uid.toString(); }
        public void setUid(Long uid) { this.uid = uid; }
    }

    // ============== 3. BanUserResp ==============

    /** 对应 Go {@code BanUserResp}。字段：Success。 */
    public static class BanUserResp {
        private boolean success;

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
    }

    // ============== 4. CreateAdminReq ==============

    /**
     * 对应 Go {@code CreateAdminReq}。
     * 字段：Username，Password，Email（optional），InviteCode，Extrainfo（optional）。
     */
    public static class CreateAdminReq {
        private String username;
        private String password;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String email;
        @JsonProperty("invite_code")
        private String inviteCode;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonProperty("extra_info")
        private Map<String, String> extraInfo;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        @JsonProperty("invite_code")
        public String getInviteCode() { return inviteCode; }
        public void setInviteCode(String inviteCode) { this.inviteCode = inviteCode; }

        @JsonProperty("extra_info")
        public Map<String, String> getExtraInfo() { return extraInfo; }
        public void setExtraInfo(Map<String, String> extraInfo) { this.extraInfo = extraInfo; }
    }

    // ============== 5. CreateAdminResp ==============

    /** 对应 Go {@code CreateAdminResp}。字段：Uid（json:"uid,string"）。 */
    public static class CreateAdminResp {
        @JsonProperty("uid")
        private Long uid;

        public String getUid() { return uid == null ? null : uid.toString(); }
        public void setUid(Long uid) { this.uid = uid; }
    }

    // ============== 6. CreateInviteReq ==============

    /** 对应 Go {@code CreateInviteReq}（空 struct，无字段）。 */
    public static class CreateInviteReq {
    }

    // ============== 7. CreateInviteResp ==============

    /** 对应 Go {@code CreateInviteResp}。字段：InviteCode，ExpiresAt（unix 秒）。 */
    public static class CreateInviteResp {
        @JsonProperty("invite_code")
        private String inviteCode;
        @JsonProperty("expires_at")
        private long expiresAt;

        @JsonProperty("invite_code")
        public String getInviteCode() { return inviteCode; }
        public void setInviteCode(String inviteCode) { this.inviteCode = inviteCode; }

        @JsonProperty("expires_at")
        public long getExpiresAt() { return expiresAt; }
        public void setExpiresAt(long expiresAt) { this.expiresAt = expiresAt; }
    }

    // ============== 8. DeleteUserReq ==============

    /** 对应 Go {@code DeleteUserReq}。字段：Uid（json:"uid,string"）。 */
    public static class DeleteUserReq {
        @JsonProperty("uid")
        private Long uid;

        public String getUid() { return uid == null ? null : uid.toString(); }
        public void setUid(Long uid) { this.uid = uid; }
    }

    // ============== 9. DeleteUserResp ==============

    /** 对应 Go {@code DeleteUserResp}。字段：Success。 */
    public static class DeleteUserResp {
        private boolean success;

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
    }

    // ============== 10. GetSelfReq ==============

    /** 对应 Go {@code GetSelfReq}（空 struct）。Handler 用 @GetMapping 不带 @RequestBody。 */
    public static class GetSelfReq {
    }

    // ============== 11. GetSelfResp ==============

    /** 对应 Go {@code GetSelfResp}。字段：Admin（嵌套 {@link AdminInfo}）。 */
    public static class GetSelfResp {
        private AdminInfo admin;

        public AdminInfo getAdmin() { return admin; }
        public void setAdmin(AdminInfo admin) { this.admin = admin; }
    }

    // ============== 12. GetUserListReq ==============

    /**
     * 对应 Go {@code GetUserListReq}。
     * 字段：Page（form，default 1），PageSize（form，default 20），Keyword（form，optional）。
     */
    public static class GetUserListReq {
        private long page;
        @JsonProperty("page_size")
        private long pageSize;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String keyword;

        public long getPage() { return page; }
        public void setPage(long page) { this.page = page; }

        @JsonProperty("page_size")
        public long getPageSize() { return pageSize; }
        public void setPageSize(long pageSize) { this.pageSize = pageSize; }

        public String getKeyword() { return keyword; }
        public void setKeyword(String keyword) { this.keyword = keyword; }
    }

    // ============== 13. GetUserListResp ==============

    /** 对应 Go {@code GetUserListResp}。字段：List（{@link UserInfo} 数组），Total。 */
    public static class GetUserListResp {
        private List<UserInfo> list;
        private long total;

        public List<UserInfo> getList() { return list; }
        public void setList(List<UserInfo> list) { this.list = list; }

        public long getTotal() { return total; }
        public void setTotal(long total) { this.total = total; }
    }

    // ============== 14. GetUserReq ==============

    /** 对应 Go {@code GetUserReq}。字段：Uid（form，json:"uid,string"）。 */
    public static class GetUserReq {
        @JsonProperty("uid")
        private Long uid;

        public String getUid() { return uid == null ? null : uid.toString(); }
        public void setUid(Long uid) { this.uid = uid; }
    }

    // ============== 15. GetUserResp ==============

    /**
     * 对应 Go {@code GetUserResp}。字段：User（optional），Found。
     * <p>注意：Go 端 {@code User UserInfo `json:"user,omitempty"`}，
     * Java 侧 {@code @JsonInclude(NON_NULL)} 让 Found=false 时不输出 user 字段。
     */
    public static class GetUserResp {
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private UserInfo user;
        private boolean found;

        public UserInfo getUser() { return user; }
        public void setUser(UserInfo user) { this.user = user; }

        public boolean isFound() { return found; }
        public void setFound(boolean found) { this.found = found; }
    }

    // ============== 16. LoginReq ==============

    /** 对应 Go {@code LoginReq}。字段：Username，Password（均来自 JSON body）。 */
    public static class LoginReq {
        private String username;
        private String password;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    // ============== 17. LoginResp ==============

    /** 对应 Go {@code LoginResp}。字段：Token。 */
    public static class LoginResp {
        private String token;

        public String getToken() { return token; }
        public void setToken(String token) { this.token = token; }
    }

    // ============== 18. LogoutReq ==============

    /** 对应 Go {@code LogoutReq}（空 struct）。Handler 用 @PostMapping 不带 @RequestBody。 */
    public static class LogoutReq {
    }

    // ============== 19. LogoutResp ==============

    /** 对应 Go {@code LogoutResp}。字段：Success。 */
    public static class LogoutResp {
        private boolean success;

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
    }

    // ============== 20. ResetUserPasswordReq ==============

    /** 对应 Go {@code ResetUserPasswordReq}。字段：Uid（json:"uid,string"）。 */
    public static class ResetUserPasswordReq {
        @JsonProperty("uid")
        private Long uid;

        public String getUid() { return uid == null ? null : uid.toString(); }
        public void setUid(Long uid) { this.uid = uid; }
    }

    // ============== 21. ResetUserPasswordResp ==============

    /** 对应 Go {@code ResetUserPasswordResp}。字段：Success。 */
    public static class ResetUserPasswordResp {
        private boolean success;

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
    }

    // ============== 22. UnBanUserReq ==============

    /** 对应 Go {@code UnBanUserReq}。字段：Uid（json:"uid,string"）。 */
    public static class UnBanUserReq {
        @JsonProperty("uid")
        private Long uid;

        public String getUid() { return uid == null ? null : uid.toString(); }
        public void setUid(Long uid) { this.uid = uid; }
    }

    // ============== 23. UnBanUserResp ==============

    /** 对应 Go {@code UnBanUserResp}。字段：Success。 */
    public static class UnBanUserResp {
        private boolean success;

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
    }

    // ============== 24. UpdateSelfReq ==============

    /**
     * 对应 Go {@code UpdateSelfReq}。所有字段 json:"xxx,optional"（go-zero 自定义 tag）。
     * Java 用 boxed 类型 + Jackson 默认即可支持"可缺省"。
     */
    public static class UpdateSelfReq {
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String username;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String password;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String email;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonProperty("extra_info")
        private Map<String, String> extraInfo;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        @JsonProperty("extra_info")
        public Map<String, String> getExtraInfo() { return extraInfo; }
        public void setExtraInfo(Map<String, String> extraInfo) { this.extraInfo = extraInfo; }
    }

    // ============== 25. UpdateSelfResp ==============

    /** 对应 Go {@code UpdateSelfResp}。字段：Success，Admin。 */
    public static class UpdateSelfResp {
        private boolean success;
        private AdminInfo admin;

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public AdminInfo getAdmin() { return admin; }
        public void setAdmin(AdminInfo admin) { this.admin = admin; }
    }

    // ============== 26. UpdateUserReq ==============

    /**
     * 对应 Go {@code UpdateUserReq}。
     * 字段：Uid（json:"uid,string"），Username / Password / Email / Extrainfo（均 optional）。
     */
    public static class UpdateUserReq {
        @JsonProperty("uid")
        private Long uid;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String username;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String password;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String email;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonProperty("extra_info")
        private Map<String, String> extraInfo;

        public String getUid() { return uid == null ? null : uid.toString(); }
        public void setUid(Long uid) { this.uid = uid; }

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        @JsonProperty("extra_info")
        public Map<String, String> getExtraInfo() { return extraInfo; }
        public void setExtraInfo(Map<String, String> extraInfo) { this.extraInfo = extraInfo; }
    }

    // ============== 27. UpdateUserResp ==============

    /** 对应 Go {@code UpdateUserResp}。字段：Success，User。 */
    public static class UpdateUserResp {
        private boolean success;
        private UserInfo user;

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public UserInfo getUser() { return user; }
        public void setUser(UserInfo user) { this.user = user; }
    }

    // ============== 28. UserInfo ==============

    /**
     * 对应 Go {@code UserInfo}。字段：Uid（json:"uid,string"），Status（int64），
     * Username，Email（optional），Extrainfo（optional）。
     */
    public static class UserInfo {
        @JsonProperty("uid")
        private Long uid;
        private long status;
        private String username;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String email;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonProperty("extra_info")
        private Map<String, String> extraInfo;

        /** 返回序列化为字符串（与 Go {@code json:"uid,string"} 对齐）。 */
        public String getUid() {
            return uid == null ? null : uid.toString();
        }
        public void setUid(Long uid) { this.uid = uid; }

        public long getStatus() { return status; }
        public void setStatus(long status) { this.status = status; }

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        @JsonProperty("extra_info")
        public Map<String, String> getExtraInfo() { return extraInfo; }
        public void setExtraInfo(Map<String, String> extraInfo) { this.extraInfo = extraInfo; }
    }
}