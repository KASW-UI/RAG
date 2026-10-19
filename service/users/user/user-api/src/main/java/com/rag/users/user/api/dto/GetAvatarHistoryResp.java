package com.rag.users.user.api.dto;

import java.util.List;

/**
 * getAvatarHistory 用例的 HTTP 响应 DTO。
 * 对应 Go: service/user/user/api/internal/types/types.go 的 AvatarHistoryResp。
 *
 * 字段：list（AvatarHistoryItem 数组）。
 */
public class GetAvatarHistoryResp {

    private List<AvatarHistoryItem> list;

    public List<AvatarHistoryItem> getList() { return list; }
    public void setList(List<AvatarHistoryItem> list) { this.list = list; }
}