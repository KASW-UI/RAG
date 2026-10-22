package com.rag.message.message.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.rag.users.common.http.GoStyleRequired;
import com.rag.users.common.http.GoStyleRequiredFields;



/**
 * 会话消息列表请求 —— 对应 Go {@code types.go} 的 {@code ConversationMessagesReq}。
 *
 * <pre>
 * Go:
 *   type ConversationMessagesReq struct {
 *       ConversationId int64 `json:"conversation_id,string"`            // 无 ,optional ⇒ 必填
 *       Offset         int32 `json:"offset,optional"`
 *       Limit          int32 `json:"limit,optional"`
 *   }
 * </pre>
 *
 * <p><b>{@code conversation_id} 带 {@code ,string}</b> ⇒ Go 侧解析层把这一字段标记为
 * {@code FromString}（{@code core/mapping/utils.go:376 parseOption} 的
 * {@code case option == stringOption: fieldOpts.FromString = true}），
 * 于是 {@code core/mapping/unmarshaler.go:889 processNamedFieldWithValueFromString} 第一行就是
 * <pre>
 *   valueKind := reflect.TypeOf(mapValue).Kind()
 *   if valueKind != reflect.String { return fmt.Errorf("the value in map is not string, but %s", valueKind) }
 * </pre>
 * <p>⇒ <b>JSON 字符串<b>与</b> JSON 数字都被接受</b>：这不是笔误，根因是
 * {@code encoding/json} 里 {@code type Number string} —— {@code json.Number} 的
 * {@code reflect.Kind()} 返回 {@code string} 而不是 {@code number}
 * （实测 {@code reflect.TypeOf(json.Number("123")).Kind() = string}）。
 * 于是上面那行 {@code if valueKind != reflect.String} 对 JSON 数字<b>放行</b>，
 * 再由 {@code fillPrimitive}（{@code unmarshaler.go:1115-1125}）里
 * <b>{@code case string} 与 {@code case json.Number} 两条分支</b>共同走
 * {@code strconv.ParseInt}。完整逐条对照见 {@link GoStyleStringInt64} 的类注释。
 *
 * <p><b>Java 侧如何逐条复刻这条规则</b>：setter 入参声明成 {@code Object}，
 * 由 {@link GoStyleStringInt64} 完全接管判定 —— 因为<b>本字段同时接受 JSON 字符串和 JSON 数字</b>，
 * 只有「不声明具体目标类型、让 Jackson 原样递出」才能表达这两条都通。
 *
 * <p>（上一版这里是 {@code String} 入参 + {@code GoStyleCoercion} 的
 * {@code LogicalType.Textual=Integer/Fail}，那条规则把<b>合法的</b> JSON 数字一起拒了，
 * 构成 DRIFT-MSGAPI-001。已于 2026-10-22 修正，详见 AGENTS.md §0 与 §3.3。）
 *
 * <p><b>为什么访问器叫 {@code conversationId()} 而不是 {@code getConversationId()}</b>：
 * 若同时存在返回 {@code Long} 的 getter 与接受 {@code Object} 的 setter，
 * Jackson 属性内省会看到 getter/setter 类型不一致，存在被字段优先解析的歧义。
 * 请求 DTO 从不需要序列化，用非 getter 命名彻底切断这条歧义路径。
 */
public class ConversationMessagesReq implements GoStyleRequiredFields {

    private Long conversationId;
    private int offset;
    private int limit;

    /**
     * {@code conversation_id} 的<b>唯一</b>反序列化入口。
     *
     * @param raw JSON 原始值：<b>字符串或整数都接受</b>（Go 侧两者等价，见类注释）；
     *               {@code null} 表示该 key 在 body 里显式为 null
     * @throws IllegalArgumentException 浮点 / 布尔 / 非十进制整数 / 超出 int64（→ 5001）
     */
    @JsonProperty("conversation_id")
    public void setConversationId(Object raw) {
        this.conversationId = GoStyleStringInt64.parse(raw, "conversation_id");
    }

    @Override
    public void assertGoRequiredFields() {
        // 缺席与显式 null 都落到这里：Go 分别是 "is not set" / "mustn't be nil"，
        // 但本服务把两者都折叠成 {code:5001}，文本差异不可观测。
        GoStyleRequired.field("conversation_id", conversationId);
    }

    /** 会话 id；缺席 / 非法时为 {@code null}。 */
    public Long conversationId() {
        return conversationId;
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