package com.rag.follow.follow.api.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 推荐列表响应 —— 对应 Go {@code types.go} 的 {@code RecommendListResp}。
 *
 * <pre>
 * Go:
 *   type RecommendListResp struct {
 *       Users []RecommendUser `json:"users"`
 *   }
 * </pre>
 *
 * <p><b>唯一使用它的端点</b>：{@code /follow/v1/recommendations}。
 *
 * <h3>⚠️ 空列表输出 {@code []} 而不是 {@code null} —— 这里 Go 侧是<b>无条件</b>保证</h3>
 * <p>与 {@link FollowUserListResp} 不同，本类的空列表形态<b>不依赖 rpc 的返回</b>，
 * 而是被 api 层自己的代码<b>写死</b>了：
 * <pre>
 *   // getrecommendationslogic.go:70-81
 *   users := make([]types.RecommendUser, 0, len(rpcResp.Users))
 *   for _, user := range rpcResp.Users {
 *       if user == nil { continue }
 *       users = append(users, types.RecommendUser{...})
 *   }
 *   return &amp;types.RecommendListResp{Users: users}, nil
 * </pre>
 * {@code make(..., 0, n)} <b>永远是非 nil 的空切片</b> ⇒ 输出恒为 {@code "users":[]}。
 * <p>⇒ 本类把字段初始化成空 list，是 Go 那行 {@code make} 的逐字等价物；
 * 改成 {@code null} 会在「没有推荐」这一<b>高频</b>场景上输出 Go 从不输出的
 * {@code "users":null}。
 */
public class RecommendListResp {

    private List<RecommendUser> users = new ArrayList<>();

    public List<RecommendUser> getUsers() {
        return users;
    }

    public void setUsers(List<RecommendUser> users) {
        this.users = users;
    }
}