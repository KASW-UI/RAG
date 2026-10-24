package com.rag.message.message.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.rag.users.common.http.GoStyleRequired;
import com.rag.users.common.http.GoStyleRequiredFields;



/**
 * 标记单条通知已读请求 —— 对应 Go {@code types.go} 的 {@code MarkNotificationReq}。
 *
 * <pre>
 * Go:
 *   type MarkNotificationReq struct {
 *       NotificationId int64 `json:"notification_id,string"`   // 必填 + ,string
 *   }
 * </pre>
 *
 * <p>归属校验<b>不在 api 层</b>：Go 的
 * {@code logic/message/marknotificationreadlogic.go} 只是把 {@code userId}（来自 JWT）
 * 与 {@code notification_id} 一起透传给 rpc，由 message-rpc 的单条原子
 * {@code UPDATE ... WHERE id = ? AND recipient_id = ?} 保证越权读不到
 * （Go 出处 {@code rpc/internal/logic/marknotificationreadlogic.go}；
 * RAG 侧已按同款条件折叠，回归锁定见 {@code MessageMarkNotificationReadTest}）。
 * api 层<b>不得</b>提前查库判断归属 —— 那会引入 Go 侧不存在的 TOCTOU 窗口。
 */
public class MarkNotificationReq implements GoStyleRequiredFields {

    private Long notificationId;

    /** {@code notification_id} 的唯一反序列化入口；JSON 字符串与整数<b>都接受</b>（Go 语义，见 {@link GoStyleStringInt64}）。 */
    @JsonProperty("notification_id")
    public void setNotificationId(Object raw) {
        this.notificationId = GoStyleStringInt64.parse(raw, "notification_id");
    }

    @Override
    public void assertGoRequiredFields() {
        GoStyleRequired.field("notification_id", notificationId);
    }

    public Long notificationId() {
        return notificationId;
    }
}