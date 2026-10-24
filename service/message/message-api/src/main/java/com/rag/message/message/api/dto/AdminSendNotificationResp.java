package com.rag.message.message.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;



/**
 * 管理员发通知响应 —— 对应 Go {@code types.go} 的 {@code AdminSendNotificationResp}。
 *
 * <p><b>⚠️ 本类的 JSON 形态是【签字偏离】，不是「与 Go 一致」</b>
 * （2026-10-23 更正，{@code SD-2026-10-23-03}）。
 *
 * <p>真源 {@code service/message/api/internal/types/types.go:21-23}：
 * <pre>
 *   type AdminSendNotificationResp struct {
 *       Affected int64 `json:"affected"`      // ← **没有** ,string
 *   }
 * </pre>
 * ⇒ Go 输出 JSON <b>数字</b>：{@code {"affected":3}}；
 * 本类加了 {@link JsonFormat.Shape#STRING} ⇒ 输出<b>字符串</b>：{@code {"affected":"3"}}。
 *
 * <p><b>为什么保留</b>：用户 2026-10-23 逐条拍板「保留 RAG 的字符串形态，不改 wire 契约」。
 * 日后要改回数字<b>须重新签字</b>，不能以「proto 里写着 int64」为由单方面改回 ——
 * 那会把已签字的偏离变成未受控的漂移。
 *
 * <p><b>🔴 本注释曾写着一句错误的「Go: …」</b>：原文写的是
 * {@code Affected int64 `json:"affected,string"`}，比真源多一个 {@code ,string} ——
 * 而<b>那句错的描述正是当年加 {@code @JsonFormat(STRING)} 的理由</b>
 * （AGENTS.md §3.3.2 的典型样本：以断言口气写的注释比没有注释更危险，
 * 因为 review 时会拿它当依据）。⇒ 已改为「先陈述真源事实，再陈述签字偏离」两段式。
 *
 * <p><b>探针在哪</b>：{@code tools/semantic-probe} 的 X1/X2（出参序列化面）会在双侧对拍时
 * 把这一条打成红，并按白名单标为 {@code SD-2026-10-23-03 预期差异}
 * （<b>不计失败，但每次都打印</b>）。
 *
 * <p><b>语义</b>：{@code affected} = 实际写入的通知条数。
 * 广播时等于目标用户总数（message-rpc 按
 * {@code message.broadcast.page-size=200} 分批插库），定向时为 0 或 1
 * （被 message-rpc 的存在性 / 状态校验拒掉时是 0，Go 侧同样返回 0 而非错误）。
 */
public class AdminSendNotificationResp {

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private long affected;

    public long getAffected() {
        return affected;
    }

    public void setAffected(long affected) {
        this.affected = affected;
    }
}
