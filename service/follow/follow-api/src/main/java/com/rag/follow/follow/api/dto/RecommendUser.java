package com.rag.follow.follow.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * 推荐列表里的一项 —— 对应 Go {@code types.go} 的 {@code RecommendUser}。
 *
 * <pre>
 * Go:
 *   type RecommendUser struct {
 *       TargetId    int64 `json:"target_id,string"`     // ← 注意：出参也是 ,string
 *       MutualScore int32 `json:"mutual_score"`
 *   }
 * </pre>
 *
 * <h3>⚠️ 出参是 <b>JSON 字符串</b>，与同域 {@code user_ids} 相反</h3>
 * <p>{@code TargetId} 的 tag <b>带 {@code ,string}</b> ⇒ Go 的 {@code encoding/json}
 * 对该字段走「序列化成带引号的数字」分支 ⇒ 输出 {@code "target_id":"123"}，
 * <b>不是</b> {@code 123}。
 * <p>而 {@link FollowUserListResp#getUserIds()}（无 {@code ,string}）输出的是 {@code [123]}。
 * <b>同一个域里两种 id 的出参形态不同</b>，这是 Go 侧 tag 逐字带来的结果，
 * 客户端必须分别处理 —— 「顺手统一成一种」会造成线上解析错位。
 *
 * <p><b>为什么本类持有的是 {@link String} 而不是 {@code long}</b>：
 * {@code ,string} 的出参形态由 {@code encoding/json} 的<b>类型 + tag</b>共同决定，
 * 而 Jackson 侧没有「按 tag 决定是否加引号」的等价开关（要靠
 * {@code @JsonSerialize(using = ToStringSerializer.class)} 之类的 per-field serializer，
 * 且 Jackson 3 的该类包名与 Jackson 2 不同，属于跨大版本易碎写法）。
 * <p>本 DTO <b>只用于出参</b>（请求侧根本不出现这个结构），
 * 所以「持有 String、原样写出」与「持有 long、由 serializer 加引号」
 * 产出的 JSON <b>字节相同</b>，而前者不依赖任何 Jackson 版本特性。
 * 从 proto {@code long target_id} 到本字段的转换只在一处：
 * {@code FollowApiSupport#toRecommendListResp}。
 *
 * <p><b>{@code mutual_score} 是普通 int32</b>：无 {@code ,string} ⇒ 输出 JSON 数字。
 *
 * <p><b>⚠️ 两个字段都需要 {@link JsonProperty}</b>：Boot 4 / Jackson 3 默认按 Java 属性名
 * （camelCase）序列化。漏掉时实际输出是 {@code {"targetId":"123","mutualScore":7}}，
 * 与 Go 的 {@code {"target_id":"123","mutual_score":7}} <b>两个 key 都不匹配</b>，
 * 而客户端不会报错、只会读到 null。
 *
 * <h3>⚠️ 字段顺序也要对齐（2026-10-24 双侧活体对拍修正）</h3>
 * <p>Go 的 {@code encoding/json} 按 <b>struct 字段声明序</b>输出 ⇒
 * {@code {"target_id":"4","mutual_score":20}}。
 * <p>⚠️ <b>但 Jackson 在「属性全部显式命名」时按字母序排</b> ——
 * 本类的两个字段都带 {@link JsonProperty}（为了 snake_case）⇒ 落入该分支
 * ⇒ 实际输出 {@code {"mutual_score":20,"target_id":"4"}}，<b>与 Go 的字节顺序相反</b>。
 * <p>⚠️ 这类差异<b>不是语义差异</b>（JSON 对象无序），但它是 <b>wire 级字节差异</b>：
 * 做 body 快照比对 / 签名校验 / 严格字符串匹配的客户端会失配。
 * <p>⇒ 用 {@link JsonPropertyOrder} 显式钉死顺序。
 * <p>⚠️ 对照：{@code service/common} 的 {@code Response}（envelope）<b>没有</b> {@code @JsonProperty}
 * ⇒ 走「字段声明序」分支 ⇒ {@code code, msg, data} 与 Go 一致 ⇒ <b>不受影响</b>。
 * <p>本仓已有同款处理先例：{@code like-rpc} 的 {@code LikeEventContracts}、
 * {@code comment-rpc} 的 {@code CommentCacheService}。
 */
@JsonPropertyOrder({"target_id", "mutual_score"})
public class RecommendUser {

    @JsonProperty("target_id")
    private String targetId;
    @JsonProperty("mutual_score")
    private int mutualScore;

    @JsonProperty("target_id")
    public String getTargetId() {
        return targetId;
    }

    @JsonProperty("target_id")
    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    @JsonProperty("mutual_score")
    public int getMutualScore() {
        return mutualScore;
    }

    @JsonProperty("mutual_score")
    public void setMutualScore(int mutualScore) {
        this.mutualScore = mutualScore;
    }
}