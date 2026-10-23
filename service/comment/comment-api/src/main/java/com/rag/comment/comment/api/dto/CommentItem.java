package com.rag.comment.comment.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * 单条评论 —— 对应 Go {@code types.go:6-20}。
 *
 * <pre>
 * Go:
 *   type CommentItem struct {
 *       Id, UserId, RootId, ParentId       int64
 *       Content, CreatedAt, Meta           string
 *       LikeCount, DislikeCount, ReplyCount int64
 *       Attribute                          int64
 *       State                              int32
 *       Children                           []CommentItem `json:"children"`
 *   }
 * </pre>
 *
 * <p><b>⚠️ {@code children} 在 wire 上恒为 {@code []}，绝不是 {@code null}</b>。
 * Go 出处：{@code getcommentlogic.go:124-132} 在<b>每一层</b>都先
 * {@code Children: make([]types.CommentItem, 0)}，再按需 append；
 * 即使 proto 侧没有子评论，序列化结果也是 {@code "children":[]}。
 * 若 Java 侧留 {@code null}，客户端会拿到 {@code "children":null} ——
 * 对「遍历子评论」的前端代码是 NPE 级别的形状变化。
 * ⇒ 构造时一律 {@code new ArrayList<>()}。
 *
 * <p><b>⚠️ {@code state} 是 {@code int}（int32）而非枚举</b>：
 * Go {@code getcommentlogic.go:121} 写的是 {@code State: int32(in.GetState())}
 * —— 把 proto 枚举<b>降成数值</b>。所以 {@code state: 0..3} 以 JSON 数字出现。
 *
 * <p><b>本 DTO 只出现在响应里</b>，故用基本类型（Go 侧这些字段永不为 null）。
 */
public class CommentItem {

    @JsonProperty("id")
    private long id;

    @JsonProperty("user_id")
    private long userId;

    @JsonProperty("content")
    private String content;

    @JsonProperty("root_id")
    private long rootId;

    @JsonProperty("parent_id")
    private long parentId;

    @JsonProperty("like_count")
    private long likeCount;

    @JsonProperty("dislike_count")
    private long dislikeCount;

    @JsonProperty("reply_count")
    private long replyCount;

    @JsonProperty("attribute")
    private long attribute;

    @JsonProperty("state")
    private int state;

    @JsonProperty("created_at")
    private String createdAt;

    @JsonProperty("meta")
    private String meta;

    /** 恒为非 null 空列表，见类注释。 */
    @JsonProperty("children")
    private List<CommentItem> children = new ArrayList<>();

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public long getRootId() {
        return rootId;
    }

    public void setRootId(long rootId) {
        this.rootId = rootId;
    }

    public long getParentId() {
        return parentId;
    }

    public void setParentId(long parentId) {
        this.parentId = parentId;
    }

    public long getLikeCount() {
        return likeCount;
    }

    public void setLikeCount(long likeCount) {
        this.likeCount = likeCount;
    }

    public long getDislikeCount() {
        return dislikeCount;
    }

    public void setDislikeCount(long dislikeCount) {
        this.dislikeCount = dislikeCount;
    }

    public long getReplyCount() {
        return replyCount;
    }

    public void setReplyCount(long replyCount) {
        this.replyCount = replyCount;
    }

    public long getAttribute() {
        return attribute;
    }

    public void setAttribute(long attribute) {
        this.attribute = attribute;
    }

    public int getState() {
        return state;
    }

    public void setState(int state) {
        this.state = state;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getMeta() {
        return meta;
    }

    public void setMeta(String meta) {
        this.meta = meta;
    }

    public List<CommentItem> getChildren() {
        return children;
    }

    public void setChildren(List<CommentItem> children) {
        this.children = children == null ? new ArrayList<>() : children;
    }
}
