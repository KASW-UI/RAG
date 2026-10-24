package com.rag.like.like.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.annotation.JsonDeserialize;

import com.rag.users.common.http.GoStyleRequired;
import com.rag.users.common.http.GoStyleRequiredFields;
import com.rag.users.common.http.GoStyleStringInt64;

/**
 * 对应 Go {@code api/internal/types/types.go:56-64}：
 * <pre>
 * type LikeActionReq struct {
 *     TargetType  string  `json:"target_type"`
 *     TargetId    string  `json:"target_id"`
 *     ActionType  int64   `json:"action_type,options=1|2|3|4"`
 *     AuthorId    int64   `json:"author_id,string"`
 *     Weight      float64 `json:"weight,optional"`
 *     ClientIp    string  `json:"client_ip,optional"`
 *     PromotionId string  `json:"promotion_id,optional"`
 * }
 * </pre>
 *
 * <p><b>本 DTO 是整个 like-api 里唯一同时用到三种 go-zero tag 的地方</b>，
 * 三种各自对应完全不同的判定，必须逐条分开处理：
 *
 * <h3>① 无 tag 的标量（target_type / target_id）⇒ 必填</h3>
 * <p>见 {@link GetTargetLikerListReq}。
 *
 * <h3>② {@code options=1|2|3|4}（action_type）⇒ 必填 <b>+</b> 取值域校验</h3>
 * <p>Go 出处：{@code core/mapping/utils.go:372-395} 的 {@code parseOption} 命中
 * {@code optionsOption} 分支 → {@code fieldOpts.Options = parseOptions(val)}
 * ⇒ {@code ["1","2","3","4"]}。随后在解析期校验
 * （{@code core/mapping/unmarshaler.go:665-670} 的
 * {@code processFieldPrimitiveWithJSONNumber}）：
 * <pre>
 *   if err := validateValueInOptions(v, opts.options()); err != nil { return err }
 * </pre>
 * {@code validateValueInOptions} 对 {@code json.Number} 走
 * {@code slices.Contains(options, Repr(v))}，而 {@code Repr(json.Number("2")) == "2"}
 * （{@code json.Number} 是 string 的命名类型，{@code Repr} 命中 {@code fmt.Stringer}）。
 * ⇒ <b>JSON 数字 {@code 2} 合法</b>（"2" ∈ options）；
 * <b>{@code 0} / {@code 5} / {@code 1.0} 都非法</b>
 * （分别是 "0" / "5" / "1.0"，都不在 options 里）。
 *
 * <p><b>而 JSON 字符串 {@code "2"} 非法</b>：无 {@code ,string} ⇒ 走
 * {@code processFieldPrimitive} 的 default 分支，
 * {@code typeKind(Int64) != valueKind(String)} ⇒ {@code newTypeMismatchError}。
 * 这条由 {@code LikeGoStyleCoercion} 的「Integer ← String = Fail」兜住。
 *
 * <h3>③ {@code ,string}（author_id）⇒ 必填，且<b>入参方向宽松</b></h3>
 * <p>由 {@link GoStyleStringInt64} 处理：JSON <b>字符串与数字都接受</b>，
 * 浮点 / 布尔 / 缺字段一律拒绝。详见该类的反射级推导。
 * setter 入参必须是 {@code Object} —— 声明成 {@code Long} 会让 Jackson 先做强转，
 * 把「宽松接受」压回「只收数字」。
 *
 * <h3>④ {@code ,optional}（weight / client_ip / promotion_id）⇒ 缺席与显式 null 都合法</h3>
 * <p>Go 出处：{@code core/mapping/unmarshaler.go:856-862}
 * {@code if mapValue == nil { if opts.optional() { return nil } ... }}。
 * <b>注意 {@code ,optional} 与 {@code ,omitempty} 是两回事</b> —— 本 DTO 里
 * 三个可选字段的 tag 都是 {@code ,optional}，所以显式传 {@code null} 是被接受的。
 *
 * <p><b>⚠️ 不要给这三个字段加必填校验</b>：加了就把「合法请求」变成解析失败。
 */
public class LikeActionReq implements GoStyleRequiredFields {

    /**
     * Go {@code options=1|2|3|4} 的四个合法值，逐字对应 proto 的
     * {@code ActionType}：{@code ACTION_LIKE=1 / ACTION_UNLIKE=2 /
     * ACTION_DISLIKE=3 / ACTION_UNDISLIKE=4}。
     *
     * <p><b>刻意用 {@code Set<String>} 而不是 {@code Set<Integer>}</b>：
     * Go 的 {@code validateValueInOptions} 是把值 {@code Repr} 成<b>字符串</b>再比，
     * 比的是 {@code ["1","2","3","4"]} 这四个<b>字面量</b>。
     * 用字符串集合能让「{@code 1.0} 不等于 "1"}」这件事在类型上就是显然的。
     */
    private static final java.util.Set<String> ALLOWED_ACTION_TYPES =
            java.util.Set.of("1", "2", "3", "4");

    @JsonProperty("target_type")
    private String targetType;
    @JsonProperty("target_id")
    private String targetId;
    @JsonProperty("action_type")
    private Long actionType;
    @JsonProperty("author_id")
    private Long authorId;
    @JsonDeserialize(using = GoStyleFloat64Deserializer.class)
    @JsonProperty("weight")
    private Double weight;
    @JsonProperty("client_ip")
    private String clientIp;
    @JsonProperty("promotion_id")
    private String promotionId;

    @Override
    public void assertGoRequiredFields() {
        GoStyleRequired.field("target_type", targetType);
        GoStyleRequired.field("target_id", targetId);
        GoStyleRequired.field("action_type", actionType);
        GoStyleRequired.field("author_id", authorId);

        // Go: validateValueInOptions(v, opts.options()) —— 比较的是 Repr 后的字符串
        if (!ALLOWED_ACTION_TYPES.contains(String.valueOf(actionType))) {
            throw new IllegalArgumentException(
                    "error: value \"" + actionType + "\" is not defined in options \"[1 2 3 4]\"");
        }
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    public Long getActionType() {
        return actionType;
    }

    public void setActionType(Long actionType) {
        this.actionType = actionType;
    }

    public Long getAuthorId() {
        return authorId;
    }

    /**
     * <b>入参必须是 {@code Object}</b> —— Go 的 {@code ,string} 是「入参方向宽松
     * （字符串与数字都收）」，只有不声明目标类型、让 Jackson 原样递出，
     * 才能同时表达「两种都收」。详见 {@link GoStyleStringInt64} 类注释。
     */
    public void setAuthorId(Object raw) {
        this.authorId = GoStyleStringInt64.parse(raw, "author_id");
    }

    public Double getWeight() {
        return weight;
    }

    public void setWeight(Double weight) {
        this.weight = weight;
    }

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    public String getPromotionId() {
        return promotionId;
    }

    public void setPromotionId(String promotionId) {
        this.promotionId = promotionId;
    }
}