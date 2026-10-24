package com.rag.comment.comment.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.rag.users.common.http.GoStyleRequired;
import com.rag.users.common.http.GoStyleRequiredFields;

/**
 * 改评论区状态请求 —— 对应 Go {@code types.go:107-111}。
 *
 * <pre>
 *   type UpdateSubjectStateReq struct {
 *       TargetType string `json:"target_type"`
 *       TargetId   string `json:"target_id"`
 *       State      int32  `json:"state"`
 *   }
 * </pre>
 *
 * <p><b>三个字段全部必填</b>（tag 均无 {@code ,optional}）。
 *
 * <p><b>⚠️ {@code state} 必须按 open enum 原样透传</b>：
 * Go 侧 {@code updatesubjectstatelogic.go:44} 是 {@code State: pb.SubjectState(req.State)}。
 * 真正的权限判定（是否 up 主）在 comment-rpc，api 层<b>不</b>预判。
 */
public class UpdateSubjectStateReq implements GoStyleRequiredFields {

    @JsonProperty("target_type")
    private String targetType;

    @JsonProperty("target_id")
    private String targetId;

    @JsonProperty("state")
    private Integer state;

    @Override
    public void assertGoRequiredFields() {
        GoStyleRequired.field("target_type", targetType);
        GoStyleRequired.field("target_id", targetId);
        GoStyleRequired.field("state", state);
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

    public Integer getState() {
        return state;
    }

    public void setState(Integer state) {
        this.state = state;
    }
}
