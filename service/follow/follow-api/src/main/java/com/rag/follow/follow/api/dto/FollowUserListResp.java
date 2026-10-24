package com.rag.follow.follow.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 用户 id 列表响应 —— 对应 Go {@code types.go} 的 {@code FollowUserListResp}。
 *
 * <pre>
 * Go:
 *   type FollowUserListResp struct {
 *       UserIds []int64 `json:"user_ids"`      // ← 注意：没有 ,string
 *   }
 * </pre>
 *
 * <p><b>使用它的 3 个端点</b>：{@code /follow/v1/follow/list}、{@code /follow/v1/follower/list}、
 * {@code /follow/v1/block/list}。
 *
 * <h3>⚠️ 出参是 <b>JSON 数字</b>，不是字符串</h3>
 * <p>{@code UserIds} 的 tag <b>没有 {@code ,string}</b> ⇒ Go 的 {@code encoding/json}
 * 按 {@code []int64} 正常序列化 ⇒ 输出 {@code "user_ids":[1,2,3]}，
 * <b>不是</b> {@code ["1","2","3"]}。
 * 这与同域 {@link RecommendUser#targetId}（<b>带</b> {@code ,string}，出参是字符串）
 * 构成一组<b>必须记住的不对称</b>，详见该类的注释。
 *
 * <h3>⚠️ 空列表输出 {@code null}（不是 {@code []}）—— 2026-10-24 双侧活体对拍修正</h3>
 * <p>Go 侧 api 的三个 list logic 都是 {@code return &types.FollowUserListResp{UserIds: rpcResp.UserIds}, nil}，
 * 看似是原样透传。关键在 <b>中间隔着一次 protobuf 序列化</b>：
 * <pre>
 *   follow-rpc 进程内：UserListResp{UserIds: make([]int64, 0, 0)}   ← 非 nil 空切片
 *        │  protobuf wire（proto3 repeated 空 ⇒ 字段整体缺失）
 *        ▼
 *   follow-api 进程内：rpcResp.UserIds == nil                        ← Go 反序列化得 nil
 *        │  encoding/json（无 omitempty）
 *        ▼
 *   HTTP body：{"user_ids":null}
 * </pre>
 * ⇒ <b>Go 输出的是 {@code null}</b>，不是 {@code []}。
 * ⚠️ 早先的注释据此断言「rpc 不会返回 nil 切片 ⇒ 输出 []」——
 * 那只看了 rpc 进程内的值，<b>漏了 wire 这一跳</b>；
 * 2026-10-24 的 Go↔RAG 双侧活体对拍实测证实 Go 输出 {@code "user_ids":null}。
 * ⇒ 本类的 {@code userIds} <b>允许为 null</b>，由
 * {@link com.rag.follow.follow.api.service.FollowApiSupport#toUserListResp}
 * 在「rpc 返回空列表」时显式置 null。
 *
 * <p><b>⚠️ 与 {@link RecommendListResp} 的不对称</b>：推荐列表走的是
 * {@code users := make([]types.RecommendUser, 0, len(rpcResp.Users))} ——
 * <b>在 api 进程内重新 make 的</b>（Go {@code getrecommendationslogic.go:70-81}），
 * 不是 rpc 返回值的透传 ⇒ 恒为非 nil 空切片 ⇒ 输出 {@code []}。
 * 对拍实测：两侧的 {@code "users":[]} 一致。
 *
 * <p><b>⚠️ {@link JsonProperty} 不是装饰，是承载 wire 契约的</b>：Spring Boot 4 / Jackson 3
 * 默认按 Java 属性名（camelCase）序列化，没有全局 snake_case 策略。
 * 漏掉这个注解时实际输出会是 {@code "userIds":[...]} ——
 * 客户端按 Go 的契约读 {@code user_ids} 就<b>永远取不到值</b>，
 * 而且<b>不报任何错</b>（多出来的字段被忽略，缺失的字段为 null）。
 */
public class FollowUserListResp {

    @JsonProperty("user_ids")
    private List<Long> userIds;

    @JsonProperty("user_ids")
    public List<Long> getUserIds() {
        return userIds;
    }

    @JsonProperty("user_ids")
    public void setUserIds(List<Long> userIds) {
        this.userIds = userIds;
    }
}