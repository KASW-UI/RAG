package com.rag.follow.follow.api.dto;

/**
 * 动作类响应 —— 对应 Go {@code types.go} 的 {@code RelationActionResp}。
 *
 * <pre>
 * Go:
 *   type RelationActionResp struct {
 *       Success bool `json:"success"`
 *   }
 * </pre>
 *
 * <p><b>使用它的 4 个端点</b>：{@code /follow/v1/follow}、{@code /follow/v1/unfollow}、
 * {@code /follow/v1/block}、{@code /follow/v1/unblock}。
 *
 * <p><b>⚠️ {@code success} 永远是 {@code true}，而且这是 Go 的既定行为</b>：
 * 4 个 logic 的成功出口<b>全都</b>是
 * <pre>
 *   return &amp;types.RelationActionResp{Success: true}, nil
 * </pre>
 * （{@code followlogic.go:62} / {@code unfollowlogic.go:62} / {@code blocklogic.go:62} /
 * {@code unblocklogic.go:62}），而<b>所有失败路径都是 {@code return nil, err}</b> ——
 * 失败时 {@code resp} 是 {@code nil}，根本走不到序列化。
 * ⇒ 所以本 DTO <b>只存在「true」这一个取值</b>，
 * <b>不需要也不应该</b>加 {@code false} 分支（Go 里没有这个状态）。
 *
 * <p>换句话说，「关注失败」在两侧都是靠<b>envelope 的 code</b> 表达的
 * （{@code {code:5201,...}} 之类），而不是 {@code data.success=false}。
 * 若本类加一个可传 {@code false} 的构造器，就是凭空造出一个 Go 不存在的响应形状。
 *
 * <p><b>为什么这个类存在、而不是直接返回 {@code Map.of("success", true)}</b>：
 * Go 侧是一个具名 struct，proto 侧对应 {@code BaseResp}；保留同名 DTO 让
 * 「Go struct ↔ Java DTO」的对应关系在 review 时可逐条核对（L1 的可审性）。
 */
public class RelationActionResp {

    private boolean success;

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    /**
     * Go 四个 logic 成功出口的唯一构造方式（恒为 {@code true}）。
     *
     * @return {@code {"success":true}}
     */
    public static RelationActionResp ok() {
        RelationActionResp resp = new RelationActionResp();
        resp.setSuccess(true);
        return resp;
    }
}