package com.rag.users.user.rpc.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * users_user_avatar_history 表实体 —— MyBatis-Plus @TableName 风格。
 *
 * 对应 Go: service/user/model/user.go 的 UserAvatarHistory struct。
 *   ⚠️ 表名 users_user_avatar_history 与用户 2026-10-22 修改 HUMAN_ONLY.MD 后的版本一致。
 *
 * 框架职责:
 *   - 与 UserEntity 一致的 @TableName / @TableId / @TableField 注解套路
 *   - 取代了原 internal.model.UserAvatarHistory.java（JPA 风格）
 *   - historyId 是 Snowflake 生成的字符串 ID（D-013：服务端生成不接受请求传入）
 *   - isCurrent Boolean getter 用 getIsCurrent()，匹配 Go 端 UserAvatarHistory.IsCurrent 字段
 */
@TableName("users_user_avatar_history")
public class UserAvatarHistoryEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("history_id")
    private String historyId;

    @TableField("uid")
    private Long uid;

    @TableField("avatar_url")
    private String avatarUrl;

    @TableField("content_type")
    private String contentType;

    @TableField("size_bytes")
    private Long sizeBytes;

    @TableField("is_current")
    private Boolean isCurrent = false;

    /**
     * 🔴 类型为 {@link OffsetDateTime} 而非 {@code LocalDateTime}（2026-10-23，DRIFT-USERRPC-V-004 的**可观察半边**）。
     *
     * <p><b>为什么这一处是「可观察」的</b>：本表的 {@code create_time} 经
     * {@code AvatarHistoryItem.create_time}（proto {@code string}）<b>直接进 wire</b>，
     * 渲染形态是客户端可见字符串 —— 与 {@code users_user.create_time}（不进任何 wire 字段）不同。
     *
     * <p><b>Go 出处</b>：声明点 {@code service/user/user/rpc/internal/model/data_model.go:30-31}
     * 是 {@code time.Time} + {@code gorm:"column:create_time;autoCreateTime"}，<b>无 type tag</b>；
     * 消费点 {@code gorm.io/driver/postgres@v1.6.0/postgres.go:251-256} 的
     * {@code case schema.Time} 分支在 {@code field.Precision == 0} 时返回 {@code "timestamptz"}。
     *
     * <p><b>偏移量由谁决定（2026-10-23 双侧活体对拍实测）</b>：
     * <b>进程本地时区</b>，<b>不是</b> PG 会话时区。
     * <pre>
     *   Go 进程 TZ=Asia/Shanghai（DSN 仍钉 TimeZone=Asia/Shanghai） → "2026-10-23T21:01:48+08:00"
     *   Go 进程 TZ=UTC        （DSN 仍钉 TimeZone=Asia/Shanghai） → "2026-10-23T13:02:19Z"
     * </pre>
     * 源码依据：pgx v5.10.0 {@code pgtype/timestamptz.go:270-276}
     * —— {@code tim := time.Unix(…); if plan.location != nil { tim = tim.In(plan.location) }}，
     * 而 {@code TimestamptzCodec.ScanLocation} 默认 nil（:133-137）⇒ 落回 {@code time.Local}；
     * pgx 走二进制解码，PG 会话时区不参与。
     * ⇒ RAG 侧用 {@link OffsetDateTime} 承接 pgjdbc 的解码（pgjdbc 同样解到 JVM 默认时区），
     * 渲染时**保留该偏移量**即可与 Go 同形；<b>不要</b>再叠一层 {@code atOffset(UTC)}。
     *
     * <p>⚠️ <b>与 DDL 必须同一提交</b>：列改 timestamptz 而实体仍是 LocalDateTime，
     * 会让 JVM 默认时区参与换算，把「列类型不一致」换成「值算错」——比现状更糟。
     * 见 {@code users-common/src/main/resources/db/02_create_user_avatar_history.sql}。
     */
    @TableField("create_time")
    private OffsetDateTime createTime;

    /**
     * 🔴 同 {@link #createTime}（DRIFT-USERRPC-V-004）。Go 声明点
     * {@code data_model.go:31}，消费点同为 {@code postgres.go:251-256}。
     * <p>⚠️ 本列**不进 wire**（proto 的 {@code AvatarHistoryItem} 没有 update_time 字段），
     * 所以它的偏移量差异属 DB 层、不可观察。
     */
    @TableField("update_time")
    private OffsetDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getHistoryId() { return historyId; }
    public void setHistoryId(String historyId) { this.historyId = historyId; }
    public Long getUid() { return uid; }
    public void setUid(Long uid) { this.uid = uid; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public Long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; }
    public Boolean getIsCurrent() { return isCurrent; }
    public void setIsCurrent(Boolean isCurrent) { this.isCurrent = isCurrent; }
    public OffsetDateTime getCreateTime() { return createTime; }
    public void setCreateTime(OffsetDateTime createTime) { this.createTime = createTime; }
    public OffsetDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(OffsetDateTime updateTime) { this.updateTime = updateTime; }
}