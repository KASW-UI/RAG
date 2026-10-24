package com.rag.message.message.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.rag.users.common.http.GoStyleRequired;
import com.rag.users.common.http.GoStyleRequiredFields;



/**
 * 管理员发私信请求 —— 对应 Go {@code types.go} 的 {@code AdminSendChatReq}。
 *
 * <pre>
 * Go:
 *   type AdminSendChatReq struct {
 *       RecipientId    int64  `json:"recipient_id,string"`              // 必填
 *       ConversationId int64  `json:"conversation_id,string,optional"`
 *       Content        string `json:"content"`                          // 必填
 *   }
 * </pre>
 *
 * <p><b>{@code recipient_id} 必填（与 user 侧的可选形成对照）</b>：
 * 管理员必须显式指定收件人，Go 侧 {@code ,string} 无 {@code ,optional}
 * ⇒ 缺席即报必填 → 本服务折叠为 {@code {code:5001}}。
 * 这是本 DTO 与 {@link UserSendChatReq} 唯一的结构性差别，<b>不要</b>统一成同一套。
 *
 * <p>{@code ,string} 规则的原理见 {@link ConversationMessagesReq} 的类注释。
 *
 * <p>使用方：{@code SendAdminChatMessage}（sender_role=ADMIN / recipient_role=<b>USER</b>）。
 * 注意 {@code recipient_role} 在 api 层被<b>硬编码</b>为 USER —— Go 侧
 * {@code sendadminchatmessagelogic.go} 同样写死 {@code RecipientRole: pb.SenderRole_USER}，
 * 管理员不能给别的管理员发私信。硬编码是行为，不要暴露成请求字段。
 */
public class AdminSendChatReq implements GoStyleRequiredFields {

    private Long recipientId;
    private Long conversationId;
    private String content;

    /** {@code recipient_id}：必填；JSON 字符串与整数<b>都接受</b>（Go 语义，见 {@link GoStyleStringInt64}）。 */
    @JsonProperty("recipient_id")
    public void setRecipientId(Object raw) {
        this.recipientId = GoStyleStringInt64.parse(raw, "recipient_id");
    }

    /** {@code conversation_id}：可选；JSON 字符串与整数<b>都接受</b>（Go 语义，见 {@link GoStyleStringInt64}）。 */
    @JsonProperty("conversation_id")
    public void setConversationId(Object raw) {
        this.conversationId = GoStyleStringInt64.parse(raw, "conversation_id");
    }

    public void setContent(String content) {
        this.content = content;
    }

    @Override
    public void assertGoRequiredFields() {
        GoStyleRequired.field("recipient_id", recipientId);
        GoStyleRequired.field("content", content);
    }

    /** 必填校验通过后非 null。 */
    public Long recipientId() {
        return recipientId;
    }

    public Long conversationId() {
        return conversationId;
    }

    public String content() {
        return content;
    }
}