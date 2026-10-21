package com.rag.message.message.rpc.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 通知表 ORM 实体。
 *
 * <p>对应 Go 端 {@code service/message/rpc/internal/model/data_model.go: Notification}。
 * <br>表结构：V1__create_message_notification.sql（已搬，含 2 个复合索引）。
 *
 * <p>字段命名 1:1：HUMAN_ONLY.MD 强制 @TableName + @TableId 取代 JPA；
 * 列名 snake_case 与 PG 表 1:1（mybatis-plus config 开启 map-underscore-to-camel-case）。
 *
 * <p>主键：Go 端 Snowflake；Java 端 MyBatis-Plus IdType.ASSIGN_ID（雪花）。
 */
@TableName("message_notification")
public class NotificationEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 对应 Go {@code RecipientID int64 gorm:"column:recipient_id;not null"}。 */
    private long recipientId;

    /** 对应 Go {@code SenderID int64 gorm:"column:sender_id;not null"}。 */
    private long senderId;

    /** 对应 Go {@code SenderRole int32 gorm:"column:sender_role;not null"}（pb.SenderRole 的 int 形式）。 */
    private int senderRole;

    /** 对应 Go {@code Kind int32 gorm:"column:kind;not null"}（pb.NotificationKind 的 int 形式）。 */
    private int kind;

    /** 对应 Go {@code Title string gorm:"column:title;type:varchar(255);not null"}。 */
    private String title;

    /** 对应 Go {@code Content string gorm:"column:content;type:text;not null"}。 */
    private String content;

    /**
     * 对应 Go {@code IsRead bool gorm:"column:is_read;not null;default:false"}。
     *
     * <p><b>字段名必须是 {@code read} 而不是 {@code isRead}（2026-10-21 修，运行期致命）</b>：
     * MyBatis-Plus 的 {@code LambdaXxxWrapper} 解析 {@code Entity::method} 时，
     * 会按 JavaBean 约定剥掉 {@code get}/{@code is} 前缀再拿<b>属性名</b>去查
     * {@code TableInfo.propertyMap}。
     * <ul>
     *   <li>字段叫 {@code isRead} ⇒ 属性名 {@code isRead}、getter {@code isRead()} ⇒
     *       lambda 解析成属性 {@code read} ⇒ <b>propertyMap 里没有 ⇒ 运行期抛
     *       {@code MybatisPlusException: can not find lambda cache for this property [read]}</b></li>
     *   <li>字段叫 {@code read}、getter {@code isRead()} ⇒ lambda 解析成属性 {@code read} ⇒ 命中。</li>
     * </ul>
     *
     * <p>实测（{@code Probe} 临时探针，MP 3.5.14）：
     * <pre>
     * 修复前：keySet=[..., isRead-&gt;isRead, ...]
     *         NotificationEntity::isRead → FAILED: can not find lambda cache for this property [read]
     *         NotificationEntity::getReadAt → OK
     * 修复后（字段 read + @TableField("is_read")）：
     *         keySet=[read-&gt;is_read, readAt-&gt;read_at, recipientId-&gt;recipient_id]
     *         E::isRead → (is_read = ? AND recipient_id = ?)   ✅
     * </pre>
     *
     * <p><b>影响面</b>：{@code NotificationMapper.countUnread} /
     * {@code markAllReadByRecipient} / {@code markRead} 三处都用
     * {@code NotificationEntity::isRead} 构造条件 ⇒ 修复前
     * <b>listNotifications(unreadOnly) / getUnreadSummary / markAllNotificationsRead /
     * markNotificationRead 四条业务路径一调即抛异常</b>。
     * 之所以长期没暴露：message-rpc <b>此前没有任何一条会真正执行 SQL 的测试</b>，
     * 全部是纯 Mockito mock（§3.3：mock 数据层不装配 MP 元数据，测不到这条）。
     *
     * <p>{@code @TableField("is_read")} 显式指定列名，不依赖
     * {@code map-underscore-to-camel-case} 的双向推断。
     */
    @TableField("is_read")
    private boolean read;

    /** 对应 Go {@code ReadAt *time.Time gorm:"column:read_at"}。 */
    private LocalDateTime readAt;

    /**
     * 对应 Go {@code Extra JSONMap gorm:"column:extra;type:jsonb"}。
     * JSONB 类型：MyBatis-Plus 用 Jackson TypeHandler 自动绑定；
     * 入参默认空 map（不为 null），避免 wire 序列化丢字段。
     */
    @TableField(typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    private Map<String, String> extra = new HashMap<>();

    /** 对应 Go {@code CreatedAt time.Time gorm:"column:created_at;autoCreateTime"}。 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 对应 Go {@code UpdatedAt time.Time gorm:"column:updated_at;autoUpdateTime"}。 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public long getRecipientId() { return recipientId; }
    public void setRecipientId(long recipientId) { this.recipientId = recipientId; }
    public long getSenderId() { return senderId; }
    public void setSenderId(long senderId) { this.senderId = senderId; }
    public int getSenderRole() { return senderRole; }
    public void setSenderRole(int senderRole) { this.senderRole = senderRole; }
    public int getKind() { return kind; }
    public void setKind(int kind) { this.kind = kind; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }
    public LocalDateTime getReadAt() { return readAt; }
    public void setReadAt(LocalDateTime readAt) { this.readAt = readAt; }
    public Map<String, String> getExtra() { return extra; }
    public void setExtra(Map<String, String> extra) { this.extra = extra; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}