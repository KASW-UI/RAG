package com.rag.message.message.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;



/**
 * 管理员发通知响应 —— 对应 Go {@code types.go} 的 {@code AdminSendNotificationResp}。
 *
 * <pre>
 * Go:
 *   type AdminSendNotificationResp struct {
 *       Affected int64 `json:"affected,string"`   // ← ,string ⇒ JSON 字符串
 *   }
 * </pre>
 *
 * <p>{@link JsonFormat.Shape#STRING} 是 {@code ,string} 的 Java 侧等价物，
 * 序列化结果是 {@code "affected":"3"}（带引号）。
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
