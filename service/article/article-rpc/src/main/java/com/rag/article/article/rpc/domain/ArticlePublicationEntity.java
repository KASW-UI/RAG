package com.rag.article.article.rpc.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * 社区发布指针 ORM 实体 —— 对应 Go 端
 * {@code service/article/rpc/internal/model/community_revision.go: ArticlePublication}。
 *
 * <p>表结构：{@code V10__create_article_community_revision.sql}。
 *
 * <h2>L2 语义：它是「读者能看到的版本」的唯一权威</h2>
 * <ul>
 *   <li>{@link #state} 只有两个取值（DDL CHECK：{@code 'published'} / {@code 'retracted'}）。</li>
 *   <li>{@link #pointerVersion} <b>单调递增且每次 +1</b>，是 H03 域事件的
 *       {@code aggregate_version}（{@code RetractPublicationTx:126}、
 *       {@code article_sync_result_consumer.go:130}）。DDL 有 {@code CHECK (> 0)} 与
 *       {@code UNIQUE(aggregate_id, aggregate_version)} 双重保证事件不乱序、不重复。</li>
 *   <li><b>「新审核在自身结果被接受前不改变它」</b>（Go 结构体注释）——
 *       这正是 {@code UpdateArticleLogic} 路径 B/C 不碰指针、只有路径 A 与
 *       发布成功回调才推进指针的原因。</li>
 *   <li>不存在 {@link #currentRevision} 为空的 published 行：
 *       {@code IncrPublicViewCount} 的谓词要求 {@code EXISTS(revision)}，
 *       {@code FindPublicOne} 也对 {@code current_revision == ""} 返回 NotFound。</li>
 * </ul>
 *
 * <p><b>行锁</b>：retract / publish 两条路径都要 {@code SELECT ... FOR UPDATE} 取它
 * （Go {@code clause.Locking{Strength: "UPDATE"}}），
 * 载体见 {@code ArticlePublicationMapper#selectForUpdate}。
 */
@TableName("article_publication")
public class ArticlePublicationEntity {

    /** 对应 Go {@code ArticleID string `gorm:"primaryKey;type:varchar(32)"`}。 */
    @TableId(type = IdType.INPUT)
    private String articleId;

    /** 对应 Go {@code CurrentRevision string `gorm:"type:varchar(96)"`}：指向 {@link ArticleRevisionEntity#getRevisionId()}。 */
    private String currentRevision;

    /** 对应 Go {@code PointerVersion int64 `gorm:"not null"`}。DDL CHECK 约束 {@code > 0}。 */
    private long pointerVersion;

    /** 对应 Go {@code State string `gorm:"type:varchar(16);not null"`}：{@code published} / {@code retracted}。 */
    private String state;

    /** 对应 Go {@code LastEventID string `gorm:"type:varchar(128);not null"`}：最后一次域事件的 event_id。 */
    private String lastEventId;

    /** 对应 Go {@code UpdatedAt time.Time `gorm:"autoUpdateTime;not null"`}。 */
    private OffsetDateTime updatedAt;

    public String getArticleId() {
        return articleId;
    }

    public void setArticleId(String articleId) {
        this.articleId = articleId;
    }

    public String getCurrentRevision() {
        return currentRevision;
    }

    public void setCurrentRevision(String currentRevision) {
        this.currentRevision = currentRevision;
    }

    public long getPointerVersion() {
        return pointerVersion;
    }

    public void setPointerVersion(long pointerVersion) {
        this.pointerVersion = pointerVersion;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getLastEventId() {
        return lastEventId;
    }

    public void setLastEventId(String lastEventId) {
        this.lastEventId = lastEventId;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}