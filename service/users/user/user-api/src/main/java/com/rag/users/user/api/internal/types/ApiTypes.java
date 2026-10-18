package com.rag.users.user.api.internal.types;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * 对应 Go 端 internal/types/types.go（98 行，19 个类型）。
 *
 * <p>L4 实现机制差异：Go 一个文件装 19 个 top-level struct；
 * Java 侧按"一个 public top-level class per file"的限制，**用嵌套静态类**（public static class）
 * 保留"一个 types 文件 = 一组 HTTP DTO"的语义。
 *
 * <p>L1 wire 契约冻结（json tag 1:1 对齐）：
 * <ul>
 *   <li>{@code int64} 字段带 json:"uid,string" —— <b>必须序列化为字符串</b>，避免前端 number 精度丢失
 *       （uid 是 Snowflake ID，可能超过 2^53）
 *       → 用 Jackson {@code @JsonProperty("uid")} + 在 setter / getter 上包 {@code Long.toString()}</li>
 *   <li>Go json:"xxx,omitempty" → Java {@code @JsonInclude(JsonInclude.Include.NON_NULL)}
 *       + 字段用 boxed 类型（{@link Long} / {@link Integer}）而非 primitive</li>
 *   <li>Go json:"xxx,optional" 为 go-zero 自定义 tag（不是标准 encoding/json），
 *       仅表示"可缺省字段，缺省时为零值"，Jackson 默认就支持 —— 不需要额外注解</li>
 *   <li>Go 内嵌 struct（如 {@code GetUserResp.User UserInfo}）→ Java 嵌套 static class 实例化</li>
 * </ul>
 *
 * <p>19 个类型逐项对应：
 * <pre>
 *  Go types.go                       →         ApiTypes 嵌套类
 *  ============================================================
 *  CreateUserReq                     →         CreateUserReq
 *  CreateUserResp                    →         CreateUserResp
 *  DeleteUserReq                     →         DeleteUserReq
 *  DeleteUserResp                    →         DeleteUserResp
 *  GetUserReq                        →         GetUserReq
 *  GetUserResp                       →         GetUserResp
 *  LoginReq                          →         LoginReq
 *  LoginResp                         →         LoginResp
 *  LogoutReq                         →         LogoutReq
 *  LogoutResp                        →         LogoutResp
 *  UpdateUserReq                     →         UpdateUserReq
 *  UpdateUserResp                    →         UpdateUserResp
 *  UploadAvatarReq                   →         UploadAvatarReq
 *  UploadAvatarResp                  →         UploadAvatarResp
 *  AvatarHistoryItem                 →         AvatarHistoryItem
 *  AvatarHistoryResp                 →         AvatarHistoryResp
 *  SelectAvatarReq                   →         SelectAvatarReq
 *  SelectAvatarResp                  →         SelectAvatarResp
 *  UserInfo                          →         UserInfo
 * </pre>
 */
public class ApiTypes {

    private ApiTypes() {}

    // ============== 1. CreateUserReq ==============

    /**
     * 对应 Go {@code CreateUserReq}。
     * 字段：Username, Password, Email, Extrainfo（Email/Extrainfo 可缺省）
     */
    public static class CreateUserReq {
        private String username;
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

    // ============== 2. CreateUserResp ==============

    /**
     * 对应 Go {@code CreateUserResp}。字段：Uid（json:"uid,string"，序列化为字符串）。
     */
    public static class CreateUserResp {
        @JsonProperty("uid")
        private Long uid;

        /** 返回序列化为字符串（与 Go json:"uid,string" 对齐）。 */
        public String getUid() {
            return uid == null ? null : uid.toString();
        }
        public void setUid(Long uid) { this.uid = uid; }
    }

    // ============== 3. DeleteUserReq ==============

    /**
     * 对应 Go {@code DeleteUserReq}（空 struct，无字段）。
     * Java 侧保留为占位空类，对应 Go 文件 1:1；Handler 用 @DeleteMapping 不带 @RequestBody。
     */
    public static class DeleteUserReq {
    }

    // ============== 4. DeleteUserResp ==============

    /** 对应 Go {@code DeleteUserResp}。字段：Success。 */
    public static class DeleteUserResp {
        private boolean success;

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
    }

    // ============== 5. GetUserReq ==============

    /** 对应 Go {@code GetUserReq}（空 struct）。Handler 用 @GetMapping 不带 @RequestBody。 */
    public static class GetUserReq {
    }

    // ============== 6. GetUserResp ==============

    /**
     * 对应 Go {@code GetUserResp}。字段：User, Found。
     * 注意：Go 端 {@code User UserInfo `json:"user,omitempty"`}，
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

    // ============== 7. LoginReq ==============

    /** 对应 Go {@code LoginReq}。字段：Username, Password。 */
    public static class LoginReq {
        private String username;
        private String password;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    // ============== 8. LoginResp ==============

    /** 对应 Go {@code LoginResp}。字段：Token。 */
    public static class LoginResp {
        private String token;

        public String getToken() { return token; }
        public void setToken(String token) { this.token = token; }
    }

    // ============== 9. LogoutReq ==============

    /** 对应 Go {@code LogoutReq}（空 struct）。Handler 用 @PostMapping 不带 @RequestBody。 */
    public static class LogoutReq {
    }

    // ============== 10. LogoutResp ==============

    /** 对应 Go {@code LogoutResp}。字段：Success。 */
    public static class LogoutResp {
        private boolean success;

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
    }

    // ============== 11. UpdateUserReq ==============

    /**
     * 对应 Go {@code UpdateUserReq}。所有字段 json:"xxx,optional"（go-zero 自定义 tag）。
     * Java 用 boxed 类型 + Jackson 默认即可支持"可缺省"。
     */
    public static class UpdateUserReq {
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

    // ============== 12. UpdateUserResp ==============

    /** 对应 Go {@code UpdateUserResp}。字段：User。 */
    public static class UpdateUserResp {
        private UserInfo user;

        public UserInfo getUser() { return user; }
        public void setUser(UserInfo user) { this.user = user; }
    }

    // ============== 13. UploadAvatarReq ==============

    /**
     * 对应 Go {@code UploadAvatarReq}。
     * 字段：AvatarUrl（必填）、ContentType（optional）、SizeBytes（optional）。
     */
    public static class UploadAvatarReq {
        @JsonProperty("avatar_url")
        private String avatarUrl;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonProperty("content_type")
        private String contentType;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonProperty("size_bytes")
        private Long sizeBytes;

        @JsonProperty("avatar_url")
        public String getAvatarUrl() { return avatarUrl; }
        public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
        @JsonProperty("content_type")
        public String getContentType() { return contentType; }
        public void setContentType(String contentType) { this.contentType = contentType; }
        @JsonProperty("size_bytes")
        public Long getSizeBytes() { return sizeBytes; }
        public void setSizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; }
    }

    // ============== 14. UploadAvatarResp ==============

    /**
     * 对应 Go {@code UploadAvatarResp}。字段：AvatarUrl, History。
     */
    public static class UploadAvatarResp {
        @JsonProperty("avatar_url")
        private String avatarUrl;
        private AvatarHistoryItem history;

        @JsonProperty("avatar_url")
        public String getAvatarUrl() { return avatarUrl; }
        public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
        public AvatarHistoryItem getHistory() { return history; }
        public void setHistory(AvatarHistoryItem history) { this.history = history; }
    }

    // ============== 15. AvatarHistoryItem ==============

    /**
     * 对应 Go {@code AvatarHistoryItem}。6 个字段 1:1 对齐（含 wire JSON 名）。
     * 注意：本类型也作为响应载荷被嵌套在 {@link UploadAvatarResp} / {@link SelectAvatarResp} /
     * {@link AvatarHistoryResp.List} 里 —— 与 Go 类型复用一致。
     *
     * <p>L1 wire 契约冻结：所有字段 JSON 名用 snake_case，对齐 Go 端
     * <pre>
     *   json:"id"
     *   json:"avatar_url"
     *   json:"content_type"
     *   json:"size_bytes"
     *   json:"is_current"
     *   json:"create_time"
     * </pre>
     * Jackson 默认按 bean getter 推断属性名（{@code isCurrent()} → {@code current}，
     * {@code sizeBytes()} → {@code sizeBytes}），必须显式 {@code @JsonProperty} 才能
     * 输出 {@code is_current} / {@code size_bytes}。修 U-010 / U-011。
     */
    public static class AvatarHistoryItem {
        @JsonProperty("id")
        private String id;
        @JsonProperty("avatar_url")
        private String avatarUrl;
        @JsonProperty("content_type")
        private String contentType;
        @JsonProperty("size_bytes")
        private long sizeBytes;
        @JsonProperty("is_current")
        private boolean isCurrent;
        @JsonProperty("create_time")
        private String createTime;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getAvatarUrl() { return avatarUrl; }
        public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
        public String getContentType() { return contentType; }
        public void setContentType(String contentType) { this.contentType = contentType; }
        @JsonProperty("size_bytes")
        public long getSizeBytes() { return sizeBytes; }
        public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }
        @JsonProperty("is_current")
        public boolean isCurrent() { return isCurrent; }
        public void setCurrent(boolean current) { isCurrent = current; }
        public String getCreateTime() { return createTime; }
        public void setCreateTime(String createTime) { this.createTime = createTime; }
    }

    // ============== 16. AvatarHistoryResp ==============

    /** 对应 Go {@code AvatarHistoryResp}。字段：List。 */
    public static class AvatarHistoryResp {
        private List<AvatarHistoryItem> list;

        public List<AvatarHistoryItem> getList() { return list; }
        public void setList(List<AvatarHistoryItem> list) { this.list = list; }
    }

    // ============== 17. SelectAvatarReq ==============

    /** 对应 Go {@code SelectAvatarReq}。字段：HistoryId（wire: {@code history_id}）。 */
    public static class SelectAvatarReq {
        @JsonProperty("history_id")
        private String historyId;

        @JsonProperty("history_id")
        public String getHistoryId() { return historyId; }
        public void setHistoryId(String historyId) { this.historyId = historyId; }
    }

    // ============== 18. SelectAvatarResp ==============

    /** 对应 Go {@code SelectAvatarResp}。字段：AvatarUrl（wire: {@code avatar_url}）, History。 */
    public static class SelectAvatarResp {
        @JsonProperty("avatar_url")
        private String avatarUrl;
        private AvatarHistoryItem history;

        @JsonProperty("avatar_url")
        public String getAvatarUrl() { return avatarUrl; }
        public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
        public AvatarHistoryItem getHistory() { return history; }
        public void setHistory(AvatarHistoryItem history) { this.history = history; }
    }

    // ============== 19. UserInfo ==============

    /**
     * 对应 Go {@code UserInfo}。字段：Uid（json:"uid,string" 序列化为字符串）、
     * Score（uint32）、Username、Email（optional）、Extrainfo（optional）。
     */
    public static class UserInfo {
        @JsonProperty("uid")
        private Long uid;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private Long score;
        private String username;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String email;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonProperty("extra_info")
        private Map<String, String> extraInfo;

        /** 返回序列化为字符串（与 Go json:"uid,string" 对齐）。 */
        public String getUid() {
            return uid == null ? null : uid.toString();
        }
        public void setUid(Long uid) { this.uid = uid; }
        public Long getScore() { return score; }
        public void setScore(Long score) { this.score = score; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        @JsonProperty("extra_info")
        public Map<String, String> getExtraInfo() { return extraInfo; }
        public void setExtraInfo(Map<String, String> extraInfo) { this.extraInfo = extraInfo; }
    }
}