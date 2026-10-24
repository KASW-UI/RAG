package com.rag.users.admin.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * admin getUser 用例的 HTTP 响应 DTO。
 * 对应 Go: service/user/admin/api/internal/types/types.go 的 GetUserResp。
 *
 * 字段：user（optional，@JsonInclude(NON_NULL)）、found。
 */
public class GetUserResp {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private UserInfo user;
    private boolean found;

    public UserInfo getUser() { return user; }
    public void setUser(UserInfo user) { this.user = user; }

    public boolean isFound() { return found; }
    public void setFound(boolean found) { this.found = found; }
}
