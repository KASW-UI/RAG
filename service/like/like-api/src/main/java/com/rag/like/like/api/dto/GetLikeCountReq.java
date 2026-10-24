package com.rag.like.like.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.annotation.JsonDeserialize;

import com.rag.users.common.http.GoStyleRequired;
import com.rag.users.common.http.GoStyleRequiredFields;

import java.util.List;

/**
 * 对应 Go {@code api/internal/types/types.go:6-9}：
 * <pre>
 * type GetLikeCountReq struct {
 *     TargetType string   `json:"target_type"`
 *     TargetIds  []string `json:"target_ids"`
 * }
 * </pre>
 *
 * <p><b>⚠️ 两个字段都必填（DRIFT-LIKEAPI-002，2026-10-22 修正）</b>。
 * 本类<b>曾经</b>把 {@code target_ids} 当作「Slice 不计必填」，那份判断是错的，
 * 且被两条绿测试以「Go: Slice 永不计必填」的措辞锁死。现已更正。
 *
 * <h3>Slice 到底必不必填 —— 只看 tag 会得出错误结论</h3>
 * 直觉是「必填只约束标量，Slice/Map 缺席就填空值放行」。Go 的实际控制流不是这样：
 * <pre>
 * // core/mapping/unmarshaler.go:944-950（processNamedFieldWithoutValue）
 * case reflect.Array, reflect.Map, reflect.Slice:
 *     if !opts.optional() {
 *         return u.processFieldNotFromString(fieldType, value, valueWithParent{
 *             value: emptyMap,            ← 喂进去的是一个**空 map**，不是「保持 nil」
 *         }, opts, fullName)
 *     }
 * ...
 * default:                             ← String / Int 等标量才落到这里
 *     if !opts.optional() { return newInitError(fullName) }   ← field %q is not set
 * </pre>
 * Slice 分支<b>不是 {@code return nil}</b>，而是把 {@code emptyMap} 交给
 * {@code processFieldNotFromString}，后者落到 {@code default:} →
 * {@code processFieldPrimitive}，此时
 * <b>{@code typeKind(Slice) != valueKind(Map)}</b> → {@code newTypeMismatchError}
 * ⇒ 报的是 {@code type mismatch for field "target_ids"}。
 *
 * <p><b>⇒ 结论：无 tag 的 Slice 与标量一样必填</b>，只是<b>错误文案不同</b>
 * （标量是 {@code field "x" is not set}，Slice 是 {@code type mismatch}）。
 * 显式 {@code null} 走另一条：{@code unmarshaler.go:856-862} 的
 * {@code if mapValue == nil { if optional {return nil}; return mustn't be nil }}。
 *
 * <p><b>对客户端的可观察后果</b>：{@code {"target_type":"article"}} 在两侧都是
 * <b>HTTP 200 + {code:5001}</b>，且<b>都不会发出这次 RPC</b>。
 * 反过来，「必填」= 键必须在，{@code target_ids: []} 是<b>合法</b>输入，
 * 它会一路走到 like-rpc 的 2001「输入需要查询的ID为空」。
 *
 * <p><b>本条的两个出处</b>：上面 {@code unmarshaler.go:944-950} 是声明点，
 * 真正决定行为的是它<b>调用链下游</b>的 {@code processFieldNotFromString → processFieldPrimitive}
 * （AGENTS.md §3.3.1 要求证据指向消费点而非声明点）。
 */
public class GetLikeCountReq implements GoStyleRequiredFields {

    @JsonProperty("target_type")
    private String targetType;
    @JsonDeserialize(using = GoStyleRawStringListDeserializer.class)
    @JsonProperty("target_ids")
    private List<String> targetIds;

    @Override
    public void assertGoRequiredFields() {
        // 两个字段都必填。Slice 的依据见类注释（unmarshaler.go:944-950 → type mismatch）
        GoStyleRequired.field("target_type", targetType);
        GoStyleRequired.field("target_ids", targetIds);
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public List<String> getTargetIds() {
        return targetIds;
    }

    public void setTargetIds(List<String> targetIds) {
        this.targetIds = targetIds;
    }
}