package com.rag.users.admin.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * admin getUserList 用例的 HTTP 响应 DTO。
 * 对应 Go: service/user/admin/api/internal/types/types.go 的 GetUserListResp。
 *
 * 字段：list（UserInfo 数组，null 时不出现）、total（始终返回，对齐 Go）。
 */
public class GetUserListResp {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<UserInfo> list;
    private long total;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public List<UserInfo> getList() { return list; }
    public void setList(List<UserInfo> list) { this.list = list; }

    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }
}
