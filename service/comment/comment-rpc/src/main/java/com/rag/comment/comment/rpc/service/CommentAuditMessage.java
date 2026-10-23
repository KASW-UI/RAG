package com.rag.comment.comment.rpc.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * 评论审计消息契约 —— 逐字对应 Go 端
 * {@code service/comment/rpc/common/types/kqueue.go:3-15}：
 *
 * <pre>
 * type CommentKafkaMsg struct {
 *     CommentId  int64  `json:"comment_id"`
 *     TargetType string `json:"target_type"`
 *     TargetId   string `json:"target_id"`
 *     UserId     int64  `json:"user_id"`
 *     RootId     int64  `json:"root_id"`
 *     ParentId   int64  `json:"parent_id"`
 *     Content    string `json:"content"`
 *     Meta       string `json:"meta"`
 *     OwnerId    int64  `json:"owner_id"`
 *     Attribute  int64  `json:"attribute"`
 *     CreateTime int64  `json:"create_time"`
 * }
 * </pre>
 *
 * <h2>⚠️ 三条把它变成「跨进程契约」而不是 DTO 的细节</h2>
 * <ol>
 *   <li><b>字段顺序是 {@code @JsonPropertyOrder} 显式钉死的</b>。
 *       Go 的 {@code encoding/json} 按<b>结构体声明顺序</b>输出，
 *       而 Jackson 默认按字母序 ⇒ 不钉死就会产出
 *       {@code {"attribute":0,"comment_id":…}} 这种与 Go 不同的字节序列。
 *       字节不同不影响 JSON 语义，但会让「按原始字节比对两侧消息」的排查手段失效。</li>
 *   <li><b>没有 {@code omitempty}</b>。Go 侧 11 个字段全部无条件输出，
 *       即使是空字符串的 {@code content}/{@code meta}（默认值）也会出现。
 *       ⇒ Java 侧<b>不要</b>加 {@code JsonInclude.NON_EMPTY}，
 *       否则一条默认消息的字节数就变了。</li>
 *   <li><b>{@code create_time} 是<b>秒</b></b>（{@code now.Unix()}，create_comment_logic.go:114），
 *       不是毫秒。落库时用 {@code Instant.ofEpochSecond} 还原
 *       （{@code InsertCommentTx} 的 {@code time.Unix(msg.CreateTime, 0)}）。</li>
 * </ol>
 *
 * <p><b>它为什么是 11 字段而不是 proto 消息</b>：这条消息走 Kafka 而不是 gRPC，
 * Go 侧用的是自己的 {@code kqtypes.CommentKafkaMsg}。wire 契约由本类的 JSON 决定，
 * 与 {@code comment.proto} 无关。
 */
@JsonPropertyOrder({
        "comment_id", "target_type", "target_id", "user_id", "root_id", "parent_id",
        "content", "meta", "owner_id", "attribute", "create_time"
})
@JsonIgnoreProperties(ignoreUnknown = true)
public class CommentAuditMessage {

    @JsonProperty("comment_id")
    private long commentId;

    @JsonProperty("target_type")
    private String targetType;

    @JsonProperty("target_id")
    private String targetId;

    @JsonProperty("user_id")
    private long userId;

    @JsonProperty("root_id")
    private long rootId;

    @JsonProperty("parent_id")
    private long parentId;

    @JsonProperty("content")
    private String content;

    @JsonProperty("meta")
    private String meta;

    @JsonProperty("owner_id")
    private long ownerId;

    @JsonProperty("attribute")
    private long attribute;

    /** Unix <b>秒</b>（Go {@code now.Unix()}）。 */
    @JsonProperty("create_time")
    private long createTime;

    public long getCommentId() {
        return commentId;
    }

    public void setCommentId(long commentId) {
        this.commentId = commentId;
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

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
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

    public long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(long ownerId) {
        this.ownerId = ownerId;
    }

    public long getAttribute() {
        return attribute;
    }

    public void setAttribute(long attribute) {
        this.attribute = attribute;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }
}