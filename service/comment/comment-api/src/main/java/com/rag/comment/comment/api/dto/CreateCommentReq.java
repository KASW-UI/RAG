package com.rag.comment.comment.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.rag.users.common.http.GoStyleRequired;
import com.rag.users.common.http.GoStyleRequiredFields;

/**
 * 建评论请求 —— 对应 Go {@code service/comment/api/internal/types/types.go:22-29}。
 *
 * <pre>
 * Go:
 *   type CreateCommentReq struct {
 *       TargetType string `json:"target_type"`
 *       TargetId   string `json:"target_id"`
 *       RootId     int64  `json:"root_id"`
 *       ParentId   int64  `json:"parent_id"`
 *       Content    string `json:"content"`
 *       Meta       string `json:"meta"`
 *   }
 * </pre>
 *
 * <p><b>⚠️ 六个字段全部必填，且整数字段用包装类型</b>。
 * 判据：tag 里没有 {@code ,optional} ⇒ go-zero 视其为必填
 * （{@code core/mapping/utils.go:23 optionalOption} 只有 {@code ,optional} 会置
 * {@code Optional=true}；{@code unmarshaler.go:968} 的 {@code default:} 分支在
 * {@code !opts.optional()} 时抛 {@code field %q is not set}）。
 * ⇒ 缺字段 / 显式 {@code null} 都是解析失败 → 200 + 5001。
 *
 * <p><b>为什么 int64 必须是 {@link Long} 而不是 {@code long}</b>：Java 基本类型无法区分
 * 「body 里没给这个字段」与「客户端显式给了 0」，而 Go 侧两者行为不同 ——
 * 前者报错、后者是合法值。用包装类型让「缺席」停在 {@code null}，
 * 由 {@link #assertGoRequiredFields()} 判出来。
 *
 * <p><b>无 {@code ,string}</b>：这六个字段在 wire 上都是 JSON 原生类型
 * （字符串 / 数字），不是字符串化的数字。
 */
public class CreateCommentReq implements GoStyleRequiredFields {

    @JsonProperty("target_type")
    private String targetType;

    @JsonProperty("target_id")
    private String targetId;

    @JsonProperty("root_id")
    private Long rootId;

    @JsonProperty("parent_id")
    private Long parentId;

    @JsonProperty("content")
    private String content;

    @JsonProperty("meta")
    private String meta;

    @Override
    public void assertGoRequiredFields() {
        GoStyleRequired.field("target_type", targetType);
        GoStyleRequired.field("target_id", targetId);
        GoStyleRequired.field("root_id", rootId);
        GoStyleRequired.field("parent_id", parentId);
        GoStyleRequired.field("content", content);
        GoStyleRequired.field("meta", meta);
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

    public Long getRootId() {
        return rootId;
    }

    public void setRootId(Long rootId) {
        this.rootId = rootId;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getMeta() {
        return meta;
    }

    public void setMeta(String meta) {
        this.meta = meta;
    }
}
