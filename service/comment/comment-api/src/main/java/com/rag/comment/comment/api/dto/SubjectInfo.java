package com.rag.comment.comment.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 评论区信息 —— 对应 Go {@code types.go:97-105}。
 *
 * <pre>
 *   type SubjectInfo struct {
 *       TargetType string `json:"target_type"`
 *       TargetId   string `json:"target_id"`
 *       TotalCount int64  `json:"total_count"`
 *       RootCount  int64  `json:"root_count"`
 *       State      int32  `json:"state"`
 *       Attribute  int64  `json:"attribute"`
 *       OwnerId    int64  `json:"owner_id"`
 *   }
 * </pre>
 *
 * <p><b>⚠️ {@code owner_id} 恒为 0</b>，尽管 proto 的 {@code SubjectInfo} 里有
 * {@code int64 owner_id = 7}。Go 出处：{@code getcommentlogic.go:84-92} 在组装
 * {@code resp.Subject} 时把 {@code OwnerId} <b>硬编码成字面量 0</b>，
 * 并没有用 {@code rpcResp.GetSubject().GetOwnerId()}。
 * 这是 Go 侧既有的可观察行为（客户端拿到的 owner_id 一直是 0），
 * 按 AGENTS.md §15「不擅自修复 Go 行为」原样保留，
 * 见 {@code GetCommentService} 里的对应注释。
 *
 * <p><b>{@code state} 同样是 int32 数值</b>（Go {@code int32(...GetState())}），
 * 不是枚举名字符串。
 *
 * <p><b>⚠️ 本 DTO 在 Go 里是值类型</b>：即使 rpc 没返回 subject，
 * {@code resp.Subject} 也会是一个全零对象（不是 null、也不会被省略），
 * 所以 Java 侧必须始终返回<b>非 null</b> 的实例。
 */
public class SubjectInfo {

    @JsonProperty("target_type")
    private String targetType = "";

    @JsonProperty("target_id")
    private String targetId = "";

    @JsonProperty("total_count")
    private long totalCount;

    @JsonProperty("root_count")
    private long rootCount;

    @JsonProperty("state")
    private int state;

    @JsonProperty("attribute")
    private long attribute;

    @JsonProperty("owner_id")
    private long ownerId;

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

    public long getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(long totalCount) {
        this.totalCount = totalCount;
    }

    public long getRootCount() {
        return rootCount;
    }

    public void setRootCount(long rootCount) {
        this.rootCount = rootCount;
    }

    public int getState() {
        return state;
    }

    public void setState(int state) {
        this.state = state;
    }

    public long getAttribute() {
        return attribute;
    }

    public void setAttribute(long attribute) {
        this.attribute = attribute;
    }

    public long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(long ownerId) {
        this.ownerId = ownerId;
    }
}
