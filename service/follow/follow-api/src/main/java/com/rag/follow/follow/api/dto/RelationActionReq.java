package com.rag.follow.follow.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.rag.users.common.http.GoStyleRequired;
import com.rag.users.common.http.GoStyleRequiredFields;
import com.rag.users.common.http.GoStyleStringInt64;

/**
 * 关注 / 取关 / 拉黑 / 解拉黑四个动作的请求 —— 对应 Go
 * {@code service/follow/api/internal/types/types.go} 的 {@code RelationActionReq}。
 *
 * <pre>
 * Go:
 *   type RelationActionReq struct {
 *       TargetId int64 `json:"target_id,string"`     // ← 注意：没有 ,optional
 *   }
 * </pre>
 *
 * <p><b>使用它的 4 个端点</b>：{@code /follow/v1/follow}、{@code /follow/v1/unfollow}、
 * {@code /follow/v1/block}、{@code /follow/v1/unblock}。四者的请求形状<b>完全相同</b>
 * （Go 侧也共用同一个 struct），但<b>响应不同</b>：动作类返回
 * {@link RelationActionResp}，列表类返回 {@code FollowUserListResp} / {@code RecommendListResp}。
 *
 * <h3>{@code target_id} <b>没有</b> {@code ,optional} ⇒ 必填</h3>
 * <p>Go 侧 {@code core/mapping/unmarshaler.go:918-969} 的
 * {@code processNamedFieldWithoutValue} 对标量字段在 {@code !opts.optional()} 时
 * 走 {@code newInitError} ⇒ {@code field "target_id" is not set} ⇒ 解析失败 ⇒
 * <b>HTTP 200 + {code:5001}</b>（本服务注册了 {@code httpx.SetErrorHandler}，
 * 解析失败也是 200 + envelope，不是 400）。
 * <p>缺席与显式 {@code null} 都落到 {@link GoStyleRequired#field}：Go 分别是
 * 「is not set」与「mustn't be nil」两条文本，但本服务把两者都折叠成
 * {@code {code:5001}}（msg 恒为 {@code "internal server error"}），文本差异不可观测。
 *
 * <h3>{@code target_id} 带 {@code ,string} ⇒ 字符串与数字都接受</h3>
 * <p>逐条对照见 {@link GoStyleStringInt64} 的类注释。setter 入参声明成 {@code Object}，
 * 让 Jackson 不做任何强转，由该工具类完全接管判定。
 *
 * <p><b>⚠️ 本层不校验 {@code target_id > 0}</b>：Go 侧同样不校验 ——
 * {@code blocklogic.go} 等 4 个 logic 都是「拿到 {@code userID} 就直接透传给 rpc」，
 * {@code target_id <= 0} 的判定在 follow-rpc 侧（{@code ErrorSelfRelation} / 参数校验）。
 * api 层提前拒会把业务码从 rpc 的那个换成 5003/5001，是新增行为。
 *
 * <p><b>访问器叫 {@code targetId()} 而不是 {@code getTargetId()}</b>：见
 * {@link FollowListReq} 类注释的同一条 Jackson 属性内省歧义。
 */
public class RelationActionReq implements GoStyleRequiredFields {

    private Long targetId;

    /**
     * {@code target_id} 的<b>唯一</b>反序列化入口。
     *
     * @param raw JSON 原始值：<b>字符串或整数都接受</b>（Go 侧两者等价，见类注释）
     * @throws IllegalArgumentException 浮点 / 布尔 / 非十进制整数 / 超出 int64（→ 5001）
     */
    @JsonProperty("target_id")
    public void setTargetId(Object raw) {
        this.targetId = GoStyleStringInt64.parse(raw, "target_id");
    }

    @Override
    public void assertGoRequiredFields() {
        GoStyleRequired.field("target_id", targetId);
    }

    /** 目标用户 id；必填校验通过后非 {@code null}，但<b>可能非正</b>（Go 侧同样放行）。 */
    public Long targetId() {
        return targetId;
    }
}