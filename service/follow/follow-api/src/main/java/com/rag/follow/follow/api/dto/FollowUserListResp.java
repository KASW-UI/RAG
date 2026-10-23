package com.rag.follow.follow.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
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
 * <h3>⚠️ 空列表输出 {@code []} 而不是 {@code null}</h3>
 * <p>Go 侧 api 的三个 list logic 都是 {@code return &types.FollowUserListResp{UserIds: rpcResp.UserIds}, nil}，
 * 看似是原样透传（理论上 rpc 返回 nil 切片时会输出 {@code null}）；
 * 但<b>在本组件能观测到的全部路径上，rpc 都不会返回 nil 切片</b>：
 * follow-rpc 的 {@code successUserListResp}（{@code helper.go:65}）拿到的是
 * {@code ListFollowTargets} / {@code ListBlockTargets} 的返回值，
 * 而那两处都是 {@code userIDs := make([]int64, 0, len(relations)); ...; return userIDs, nil}
 * —— <b>空结果也是非 nil 空切片</b>。
 * ⇒ 两侧的最终输出一致：{@code "user_ids":[]}。
 * 本类把字段初始化成空 list，与那个 {@code make(..., 0, ...)} 对齐；
 * 若改成 {@code null}，就会在「查无结果」这个最常见的场景上多出一个 Go 没有的
 * {@code "user_ids":null}。
 *
 * <p><b>⚠️ {@link JsonProperty} 不是装饰，是承载 wire 契约的</b>：Spring Boot 4 / Jackson 3
 * 默认按 Java 属性名（camelCase）序列化，没有全局 snake_case 策略。
 * 漏掉这个注解时实际输出会是 {@code "userIds":[...]} ——
 * 客户端按 Go 的契约读 {@code user_ids} 就<b>永远取不到值</b>，
 * 而且<b>不报任何错</b>（多出来的字段被忽略，缺失的字段为 null）。
 */
public class FollowUserListResp {

    @JsonProperty("user_ids")
    private List<Long> userIds = new ArrayList<>();

    @JsonProperty("user_ids")
    public List<Long> getUserIds() {
        return userIds;
    }

    @JsonProperty("user_ids")
    public void setUserIds(List<Long> userIds) {
        this.userIds = userIds;
    }
}