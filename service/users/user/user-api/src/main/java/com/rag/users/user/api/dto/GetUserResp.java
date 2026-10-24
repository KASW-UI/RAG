package com.rag.users.user.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * getUser 用例的 HTTP 响应 DTO。
 * 对应 Go: service/user/user/api/internal/types/types.go 的 GetUserResp。
 *
 * 字段：user（4 字段：uid/username/email/extra_info）、found（始终 true）。
 *
 * D-011 / score 字段不返回：Go 端 GetUserResp.UserInfo 不含 score 字段（HTTP 层不暴露）。
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