package com.rag.like.like.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.annotation.JsonDeserialize;

import com.rag.users.common.http.GoStyleRequired;
import com.rag.users.common.http.GoStyleRequiredFields;

import java.util.List;

/**
 * 对应 Go {@code api/internal/types/types.go:15-18}：
 * <pre>
 * type GetLikeStateReq struct {
 *     TargetType string   `json:"target_type"`
 *     TargetIds  []string `json:"target_ids"`
 * }
 * </pre>
 *
 * <p><b>两个字段都必填</b>（DRIFT-LIKEAPI-002，2026-10-22 修正）。
 * 本类<b>曾经</b>认为「Slice 不计必填」，该判断已被独立复核（活体 Go 探针）推翻；
 * 完整推导见 {@link GetLikeCountReq} 类注释中
 * {@code unmarshaler.go:944-950 → processFieldNotFromString(emptyMap)
 * → typeKind(Slice) != valueKind(Map) → newTypeMismatchError} 这一段。
 *
 * <p>可观察后果：{@code {"target_type":"article"}} 两侧都是
 * <b>HTTP 200 + {code:5001}</b> 且<b>不发出 RPC</b>；
 * {@code target_ids: []} 是合法输入，会一路走到 like-rpc
 * （{@code getlikestatelogic.go:43-46} 的 2001 分支）。
 *
 * <p><b>⚠️ 本 DTO 关联的是「有 uid」的端点</b>：Go 侧 {@code getlikestatelogic.go:37-49}
 * 会从 ctx 取 {@code userId}，取不到返回 <b>1006</b>。但那属于<b>业务层</b>的判定
 * （{@code LikeApiSupport#userId}），<b>不</b>是 DTO 的必填校验 —— 两者不要混。
 */
public class GetLikeStateReq implements GoStyleRequiredFields {

    @JsonProperty("target_type")
    private String targetType;
    @JsonDeserialize(using = GoStyleRawStringListDeserializer.class)
    @JsonProperty("target_ids")
    private List<String> targetIds;

    @Override
    public void assertGoRequiredFields() {
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