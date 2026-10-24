package com.rag.follow.follow.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.rag.users.common.http.GoStyleStringInt64;

/**
 * 列表类请求 —— 对应 Go {@code service/follow/api/internal/types/types.go} 的 {@code FollowListReq}。
 *
 * <pre>
 * Go:
 *   type FollowListReq struct {
 *       UserId int64 `json:"user_id,string,optional"`
 *       Offset int32 `json:"offset,optional"`
 *       Limit  int32 `json:"limit,optional"`
 *   }
 * </pre>
 *
 * <p><b>使用它的 4 个端点</b>：{@code /follow/v1/follow/list}、{@code /follow/v1/follower/list}、
 * {@code /follow/v1/block/list}、{@code /follow/v1/recommendations}。
 *
 * <p><b>三个字段全部 {@code ,optional}</b> ⇒ 永不做必填校验，缺席即零值
 * （Go 的零值 struct：user_id=0 / offset=0 / limit=0）。
 *
 * <h3>{@code user_id} 带 {@code ,string} ⇒ 字符串与数字都接受</h3>
 * <p>根因与逐条对照见 {@link GoStyleStringInt64} 的类注释：
 * {@code encoding/json} 里 {@code json.Number} 的 {@code reflect.Kind()} 是 {@code string}，
 * 于是 go-zero {@code core/mapping/unmarshaler.go:889} 的
 * {@code if valueKind != reflect.String} 对 JSON 数字<b>放行</b>，
 * 再由 {@code fillPrimitive} 的 {@code case json.Number} 一起走 {@code strconv.ParseInt}。
 * ⇒ setter 入参声明成 {@code Object}，让 Jackson 原样递出，由
 * {@link GoStyleStringInt64} 完全接管判定。
 *
 * <p><b>{@code offset} / {@code limit} 没有 {@code ,string} ⇒ JSON 字符串被拒</b>
 * （{@code {"offset":"10"}} 在 Go 侧是 {@code type mismatch}）。
 * 这条由 {@code FollowGoStyleCoercion} 的 {@code LogicalType.Integer + String→Fail} 承担。
 *
 * <p><b>⚠️ {@code user_id} 的语义不是「查谁」，而是「请求谁的视角」</b>：
 * 4 个端点里有 2 个（{@code block/list} 与 {@code recommendations}）会把它与当前登录用户比对，
 * 不一致就返回 {@code ErrorForbidden}（5005）；另外 2 个（{@code follow/list}、
 * {@code follower/list}）<b>不比对</b>，即任意登录用户可查任意人的关注/粉丝列表。
 * 这个不对称是 Go 侧 {@code resolveUserID} + 各 logic 自带的分支，
 * 详见 {@code FollowApiSupport#resolveUserId} 与 4 个 Service 的注释。
 *
 * <p><b>为什么访问器叫 {@code userId()} 而不是 {@code getUserId()}</b>：
 * 若同时存在返回 {@code Long} 的 getter 与接受 {@code Object} 的 setter，
 * Jackson 属性内省会看到 getter/setter 类型不一致，存在被字段优先解析的歧义。
 * 请求 DTO 从不需要序列化，用非 getter 命名彻底切断这条歧义路径。
 */
public class FollowListReq {

    private Long userId;
    private int offset;
    private int limit;

    /**
     * {@code user_id} 的<b>唯一</b>反序列化入口。
     *
     * @param raw JSON 原始值：<b>字符串或整数都接受</b>（Go 侧两者等价，见类注释）；
     *               {@code null} 表示该 key 在 body 里显式为 null
     * @throws IllegalArgumentException 浮点 / 布尔 / 非十进制整数 / 超出 int64（→ 5001）
     */
    @JsonProperty("user_id")
    public void setUserId(Object raw) {
        this.userId = GoStyleStringInt64.parse(raw, "user_id");
    }

    /**
     * 请求的用户视角；缺席 / 显式 null / 非正数时为 {@code null} 或非正值，
     * 由 {@code resolveUserId} 归一化成「当前登录用户」。
     */
    public Long userId() {
        return userId;
    }

    public int offset() {
        return offset;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }

    public int limit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }
}