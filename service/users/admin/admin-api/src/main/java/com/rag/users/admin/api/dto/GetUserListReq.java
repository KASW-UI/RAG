package com.rag.users.admin.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * admin getUserList 用例的 HTTP 请求 DTO（GET query 参数）。
 * 对应 Go: service/user/admin/api/internal/types/types.go 的 GetUserListReq。
 *
 * 字段：page（default 1）、page_size（default 20，wire: page_size）、keyword（optional）。
 */
public class GetUserListReq {
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
