package com.rag.message.message.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.rag.users.common.http.GoStyleRequired;
import com.rag.users.common.http.GoStyleRequiredFields;
import java.util.Map;




/**
 * 管理员发通知请求 —— 对应 Go {@code types.go} 的 {@code AdminSendNotificationReq}。
 *
 * <pre>
 * Go:
 *   type AdminSendNotificationReq struct {
 *       Broadcast bool              `json:"broadcast,optional"`
 *       TargetId  int64             `json:"target_id,string,optional"`
 *       Kind      int32             `json:"kind,optional"`
 *       Title     string            `json:"title"`
 *       Content   string            `json:"content"`
 *       Extra     map[string]string `json:"extra,optional"`
 *   }
 * </pre>
 *
 * <p><b>{@code title} / {@code content} 必填</b>（无 {@code ,optional}）；
 * {@code target_id} 与 {@code kind} 可选。
 *
 * <p><b>⚠️ {@code extra} 的「显式 null」<b>不</b>报错</b>（2026-10-22 修正，DRIFT-MSGAPI-003）。
 *
 * <p>Go 侧 tag 是 {@code json:"extra,optional"} ⇒ {@code core/mapping/utils.go:376} 的
 * {@code parseOption} 置 {@code Optional = true} ⇒
 * {@code core/mapping/unmarshaler.go:857-862} 命中
 * <pre>
 *   if mapValue == nil {
 *       if optional { return nil }                      ← 本字段走这条，null 放行
 *       return fmt.Errorf("field %q mustn't be nil", key)
 *   }
 * </pre>
 * 探针实测（v1.10.2 真源）：{@code {"title":"t","content":"c","extra":null}} → <b>ACCEPTED</b>。
 *
 * <p><b>为什么差点写错（这正是 AGENTS.md §3.1「不要拿别处的结论当基准」的实例）</b>：
 * users-common 的 {@code GoStyleRequired.explicitNull} 注释写着「Go {@code extra_info:null} → 400 mustn't be nil」，
 * 而那条实测针对的是 {@code service/user/user/api} 的 {@code CreateUserReq.Extrainfo}，
 * 它的 tag 是 {@code json:"extra_info,omitempty"} ——
 * <b>{@code omitempty} 不是 {@code optional}</b>（go-zero 的 parseOption 只认 {@code optional}），
 * 所以它才走 non-optional 分支报错。
 * 同一文件里 {@code UpdateUserReq.Extrainfo} 是 {@code json:"extra_info,optional"}，
 * 它<b>接受</b> null。把「一个 struct 的实测」当成「所有 map 字段的规则」就抄错了。
 *
 * <p><b>{@code target_id} 的越界检查不在这里</b>：Go 的
 * {@code sendadminnotificationlogic.go} 在<b>身份解析之后</b>才做
 * {@code if !req.Broadcast && req.TargetId <= 0 → ErrorInvalidParam(5003)}，
 * 且计入 {@code message_api_reject_total{reason="target_missing"}}。
 * 顺序不可颠倒：身份缺失（5004）先于参数非法（5003）。
 *
 * <p><b>{@code kind} 的语义</b>：0 表示「按 broadcast 推断」
 * （{@code shared.AdminNotificationKind}：broadcast ⇒ ADMIN_BROADCAST，否则 ADMIN_NOTICE）；
 * {@code >0} 则按客户端给的原值下发。见 {@code MessageApiSupport#adminNotificationKind}。
 */
public class AdminSendNotificationReq implements GoStyleRequiredFields {

    private boolean broadcast;

    /** 显式为 null 时为 {@code null}；缺席与显式 0 都在这里归一到 {@code null}。 */
    private Long targetId;

    private int kind;
    private String title;
    private String content;
    private Map<String, String> extra;

    public void setBroadcast(boolean broadcast) {
        this.broadcast = broadcast;
    }

    /** {@code target_id}：可选；JSON 字符串与整数<b>都接受</b>（Go 语义，见 {@link GoStyleStringInt64}）。 */
    @JsonProperty("target_id")
    public void setTargetId(Object raw) {
        this.targetId = GoStyleStringInt64.parse(raw, "target_id");
    }

    public void setKind(int kind) {
        this.kind = kind;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setContent(String content) {
        this.content = content;
    }

    /**
     * {@code extra} 的唯一反序列化入口。
     *
     * <p>存 {@code null} 是<b>合法</b>形态：Go 侧 tag 是 {@code json:"extra,optional"}
     * ⇒ {@code core/mapping/unmarshaler.go:857-862} 的
     * {@code if mapValue == nil { if optional { return nil } ... }} 走 optional 分支<b>放行</b>。
     * 探针实测：{@code {"title":"t","content":"c","extra":null}} → <b>ACCEPTED</b>。
     */
    @JsonProperty("extra")
    public void setExtra(Map<String, String> extra) {
        this.extra = extra;
    }

    @Override
    public void assertGoRequiredFields() {
        GoStyleRequired.field("title", title);
        GoStyleRequired.field("content", content);
        // ⚠️ 这里<b>刻意不</b>对 extra 做「显式 null」校验 —— 见类注释里 ,optional vs ,omitempty 的取证。
    }

    public boolean broadcast() {
        return broadcast;
    }

    public Long targetId() {
        return targetId;
    }

    public int kind() {
        return kind;
    }

    public String title() {
        return title;
    }

    public String content() {
        return content;
    }

    public Map<String, String> extra() {
        return extra;
    }
}