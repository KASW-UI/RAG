package com.rag.comment.comment.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.rag.users.common.http.GoStyleRequired;
import com.rag.users.common.http.GoStyleRequiredFields;

/**
 * 拉评论请求 —— 对应 Go {@code types.go:48-55}。
 *
 * <pre>
 *   type GetCommentReq struct {
 *       TargetType string `json:"target_type"`
 *       TargetId   string `json:"target_id"`
 *       SortType   int64  `json:"sort_type"`
 *       RootId     int64  `json:"root_id"`
 *       Page       int64  `json:"page"`
 *       PageSize   int64  `json:"page_size"`
 *   }
 * </pre>
 *
 * <p><b>六个字段全部必填</b>（tag 均无 {@code ,optional}）。
 * ⚠️ 这与「分页有兜底默认值」<b>不矛盾</b>：兜底发生在 {@code GetCommentService}
 * （Go {@code getcommentlogic.go:47-55}，处理 {@code page<=0} / {@code pageSize<=0}），
 * 是<b>业务层</b>；解析层仍然要求这些 key 必须出现在 body 里。
 * 两者是先后两道关卡。
 */
public class GetCommentReq implements GoStyleRequiredFields {

    @JsonProperty("target_type")
    private String targetType;

    @JsonProperty("target_id")
    private String targetId;

    @JsonProperty("sort_type")
    private Long sortType;

    @JsonProperty("root_id")
    private Long rootId;

    @JsonProperty("page")
    private Long page;

    @JsonProperty("page_size")
    private Long pageSize;

    @Override
    public void assertGoRequiredFields() {
        GoStyleRequired.field("target_type", targetType);
        GoStyleRequired.field("target_id", targetId);
        GoStyleRequired.field("sort_type", sortType);
        GoStyleRequired.field("root_id", rootId);
        GoStyleRequired.field("page", page);
        GoStyleRequired.field("page_size", pageSize);
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

    public Long getSortType() {
        return sortType;
    }

    public void setSortType(Long sortType) {
        this.sortType = sortType;
    }

    public Long getRootId() {
        return rootId;
    }

    public void setRootId(Long rootId) {
        this.rootId = rootId;
    }

    public Long getPage() {
        return page;
    }

    public void setPage(Long page) {
        this.page = page;
    }

    public Long getPageSize() {
        return pageSize;
    }

    public void setPageSize(Long pageSize) {
        this.pageSize = pageSize;
    }
}
