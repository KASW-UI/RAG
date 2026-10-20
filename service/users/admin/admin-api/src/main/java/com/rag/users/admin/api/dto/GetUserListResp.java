package com.rag.users.admin.api.dto;

import java.util.List;

/**
 * admin getUserList 用例的 HTTP 响应 DTO。
 * 对应 Go: service/user/admin/api/internal/types/types.go 的 GetUserListResp。
 *
 * <p>字段：list（UserInfo 数组）、total（始终返回）。
 *
 * <p><b>A-01（已修）· {@code list} 故意不加 {@code @JsonInclude(NON_NULL)}</b>：
 * Go 端 {@code GetUserListResp.List} 的 tag 是 {@code `json:"list"`} —— <b>没有 omitempty</b>。
 * 空结果时 api 侧 {@code getuserlistlogic.go:51} 的 {@code var list []types.UserInfo}
 * 保持 nil，序列化结果是 <b>{@code "list": null}</b>（字段出现、值为 null），
 * 而<b>不是</b> {@code []}，也不是「字段消失」。
 * <p>本 DTO 此前挂着 {@code NON_NULL}，配合 Service 无条件 {@code setList(new ArrayList<>())}，
 * 输出 {@code "list": []}。两处都要改才对齐：Service 侧空结果置 null，
 * DTO 侧去掉 {@code NON_NULL} 让 null 序列化成字面量 {@code null}。
 */
public class GetUserListResp {
    private List<UserInfo> list;
    private long total;

    public List<UserInfo> getList() { return list; }
    public void setList(List<UserInfo> list) { this.list = list; }

    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }
}