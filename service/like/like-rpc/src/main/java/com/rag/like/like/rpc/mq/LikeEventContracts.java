package com.rag.like.like.rpc.mq;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * 本域在 Kafka 上流通的三种消息体 —— 1:1 对应 Go 端
 * {@code service/like/rpc/internal/types/types.go}。
 *
 * <p><b>这是 L1 外部契约</b>：Java like-rpc 与尚未迁移的 Go like-mq 在灰度期消费同一个 topic
 * {@code like_promotion_topic}，字段名必须逐字相同。
 *
 * <p>⚠️ 三处易错点：
 * <ol>
 *   <li>{@code KafkaLikeMsg.state} 携带的是 <b>action 值 1~4</b>
 *       （like/unlike/dislike/undislike），而 {@code like_record.state} 存的是 <b>0/1/2</b>。
 *       两套编码由消费侧的 {@code applyLikeMessage} switch 转换。</li>
 *   <li>{@code ArticleHotEvent.UserId} 是 <b>{@code string}</b>（不是 int64），
 *       Go 侧用 {@code fmt.Sprintf("%d", userID)} 转出来的；
 *       而 {@code TaskArticleProgressMsg.UserID} 同样是 string。</li>
 *   <li>{@code created_at} / {@code timestamp} 都是 <b>Unix 秒</b>（不是毫秒），
 *       与 Redis ZSet score 里那个毫秒级游标是两回事。</li>
 * </ol>
 *
 * <p>{@code @JsonPropertyOrder} 显式固定字段顺序：Go 的 {@code encoding/json} 按 struct 字段声明序输出，
 * 这里保持一致，便于灰度期逐行 diff payload。
 */
public final class LikeEventContracts {

    private LikeEventContracts() {
        // 纯数据契约，禁止实例化
    }

    /**
     * 点赞动作事件 —— Go {@code types.go:3-12} {@code KafkaLikeMsg}。
     *
     * <p>由 {@code LikeAction} 在 Lua 返回非 0 时推入 {@code like_promotion_topic}。
     */
    @JsonIgnoreProperties(ignoreUnknown = false)
    @JsonPropertyOrder({"msg_id", "user_id", "target_type", "target_id", "author_id", "state", "is_first", "created_at"})
    public static class KafkaLikeMsg {

        /** 雪花 ID 的<b>字符串</b>形式，消费侧要 {@code ParseInt} 成 operationID 用。 */
        @JsonProperty("msg_id")
        public String msgId;

        @JsonProperty("user_id")
        public long userId;

        @JsonProperty("target_type")
        public String targetType;

        @JsonProperty("target_id")
        public String targetId;

        @JsonProperty("author_id")
        public long authorId;

        /** action 值：1 like / 2 unlike / 3 dislike / 4 undislike。 */
        @JsonProperty("state")
        public int state;

        /** 仅当 Lua 返回 2（首次点赞）时为 true，消费侧据此建 first_like outbox。 */
        @JsonProperty("is_first")
        public boolean isFirst;

        /** Unix <b>秒</b>。 */
        @JsonProperty("created_at")
        public long createdAt;

        public KafkaLikeMsg() {
        }

        public KafkaLikeMsg(String msgId, long userId, String targetType, String targetId, long authorId,
                            int state, boolean isFirst, long createdAt) {
            this.msgId = msgId;
            this.userId = userId;
            this.targetType = targetType;
            this.targetId = targetId;
            this.authorId = authorId;
            this.state = state;
            this.isFirst = isFirst;
            this.createdAt = createdAt;
        }
    }

    /**
     * 文章热点事件 —— Go {@code types.go:14-20} {@code ArticleHotEvent}。
     *
     * <p>由 {@code LikeHotEventOutboxRelay} 从 {@code like_outbox_event} 捞出后推入
     * {@code article-hot-events}。
     */
    @JsonIgnoreProperties(ignoreUnknown = false)
    @JsonPropertyOrder({"article_id", "type", "user_id", "timestamp", "is_first"})
    public static class ArticleHotEvent {

        @JsonProperty("article_id")
        public String articleId;

        /** Go 侧固定字面量 {@code "like"}。 */
        @JsonProperty("type")
        public String type;

        /** <b>string</b>，不是数字。 */
        @JsonProperty("user_id")
        public String userId;

        /** Unix <b>秒</b>。 */
        @JsonProperty("timestamp")
        public long timestamp;

        /** Go 侧固定 {@code true}（只有首次点赞才会产生该事件）。 */
        @JsonProperty("is_first")
        public boolean isFirst;

        public ArticleHotEvent() {
        }

        public ArticleHotEvent(String articleId, String type, String userId, long timestamp, boolean isFirst) {
            this.articleId = articleId;
            this.type = type;
            this.userId = userId;
            this.timestamp = timestamp;
            this.isFirst = isFirst;
        }
    }

    /**
     * 任务进度事件 —— Go {@code types.go:22-25} {@code TaskArticleProgressMsg}。
     *
     * <p>推给推广结算系统 {@code task.logs.filtered.Article}。
     */
    @JsonIgnoreProperties(ignoreUnknown = false)
    @JsonPropertyOrder({"user_id", "cur"})
    public static class TaskArticleProgressMsg {

        /** <b>string</b>，存的是 <b>作者</b> ID。 */
        @JsonProperty("user_id")
        public String userId;

        /** 该文章当前点赞数。 */
        @JsonProperty("cur")
        public long cur;

        public TaskArticleProgressMsg() {
        }

        public TaskArticleProgressMsg(String userId, long cur) {
            this.userId = userId;
            this.cur = cur;
        }
    }

    // ==================== Go 侧的字面量常量 ====================

    /** Go {@code buildFirstLikeOutbox} 里的 {@code Type: "like"}。 */
    public static final String EVENT_TYPE_LIKE = "like";

    /** Go {@code buildFirstLikeOutbox} 里的 {@code EventType: "article_hot_like"}。 */
    public static final String OUTBOX_EVENT_TYPE_ARTICLE_HOT_LIKE = "article_hot_like";

    /** Go {@code appendLikeFact} 里的 {@code OperationID: p.Inbox.MsgId} 前缀常量对应的 event_id 前缀。 */
    public static final String FACT_EVENT_ID_PREFIX = "rtw.like.";

    /** Go {@code domain_fact.go:91} 的 {@code Producer: "rtw.like-mq"}。 */
    public static final String FACT_PRODUCER = "rtw.like-mq";

    /** Go {@code domain_fact.go:90} 的 {@code SchemaVersion: "rtw.community-fact.v1"}。 */
    public static final String FACT_SCHEMA_VERSION = "rtw.community-fact.v1";

    /** Go {@code domain_fact.go:90} 的 {@code EventType: "community.target.interaction"}。 */
    public static final String FACT_EVENT_TYPE = "community.target.interaction";

    /**
     * Go 消费侧写死到 {@code likeMessage.Topic} / {@code Consumer} 的两个字面量
     * （{@code likemq.go:109-110}）。它们会随 inbox 行一起落库，属于可查询的事实，不能随手改。
     */
    public static final String INBOX_TOPIC = "like-topic";

    public static final String INBOX_CONSUMER = "like_update_service";
}