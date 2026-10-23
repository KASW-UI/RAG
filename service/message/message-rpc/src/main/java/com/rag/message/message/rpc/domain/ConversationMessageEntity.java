package com.rag.message.message.rpc.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 会话内单条消息表 ORM 实体。
 *
 * <p>对应 Go 端 {@code service/message/rpc/internal/model/data_model.go: ConversationMessage}。
 * <br>表结构：V3__create_message_conversation_message.sql（含 2 个复合索引）。
 */
@TableName("message_conversation_message")
public class ConversationMessageEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 对应 Go {@code ConversationID int64 gorm:"column:conversation_id;not null"}。 */
    private long conversationId;

    /** 对应 Go {@code SenderID int64 gorm:"column:sender_id;not null"}。 */
    private long senderId;

    /** 对应 Go {@code SenderRole int32 gorm:"column:sender_role;not null"}。 */
    private int senderRole;

    /** 对应 Go {@code RecipientID int64 gorm:"column:recipient_id;not null"}。 */
    private long recipientId;

    /** 对应 Go {@code RecipientRole int32 gorm:"column:recipient_role;not null"}。 */
    private int recipientRole;

    /** 对应 Go {@code Content string gorm:"column:content;type:text;not null"}。 */
    private String content;

    /**
     * 对应 Go {@code IsRead bool `gorm:"column:is_read;not null;default:false"`}。
     *
     * <p>🔴 <b>字段名必须是 {@code read} 而不是 {@code isRead}（2026-10-23 修，DRIFT-MSG-V-017）</b>，
     * 与 {@link NotificationEntity#read} 是<b>同一条教训、同一个修法</b>
     * （那个类 2026-10-21 已经付过运行期代价，本模块当时只改了一半）。
     *
     * <p><b>机理</b>：MyBatis-Plus 的 lambda 列名解析依赖
     * {@code TableInfo.propertyMap}，其 key 由<b>字段</b>决定；
     * 而 {@code E::isRead} 这种方法引用指向的是<b>方法名</b>。
     * 字段叫 {@code isRead} 时两者对不上 ⇒ 抛
     * {@code MybatisPlusException: can not find lambda cache for this property [read]}。
     * 字段叫 {@code read}、getter 仍写 {@code isRead()} 时两者统一到 {@code read} ⇒ 命中。
     *
     * <p>⚠️ <b>getter/setter 签名刻意保持 {@code isRead()} / {@code setRead(boolean)} 不变</b>：
     * 改签名会波及 {@code MessageRpcSupport:130/149} 等调用点，
     * 而那与本条缺陷<b>无关</b>（AGENTS.md §3.2：不顺手做无行为变更的重命名）。
     *
     * <p>⚠️ <b>本条修复前无调用方</b>：生产路径不走 {@code markAllReadByRecipient}
     * （走的是手写 {@code @Update} 的 {@code markReadByConversationAndRecipient}），
     * 所以这是<b>潜伏缺陷</b>，今天<b>无可观察差异</b> —— 修它是为了拆掉
     * 「一旦有人接线就抛异常」那颗雷，<b>不是为了改变今天的行为</b>。
     */
    @TableField("is_read")
    private boolean read;

    /** 对应 Go {@code ReadAt *time.Time gorm:"column:read_at"}。 */
    private LocalDateTime readAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public long getConversationId() { return conversationId; }
    public void setConversationId(long conversationId) { this.conversationId = conversationId; }
    public long getSenderId() { return senderId; }
    public void setSenderId(long senderId) { this.senderId = senderId; }
    public int getSenderRole() { return senderRole; }
    public void setSenderRole(int senderRole) { this.senderRole = senderRole; }
    public long getRecipientId() { return recipientId; }
    public void setRecipientId(long recipientId) { this.recipientId = recipientId; }
    public int getRecipientRole() { return recipientRole; }
    public void setRecipientRole(int recipientRole) { this.recipientRole = recipientRole; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }
    public LocalDateTime getReadAt() { return readAt; }
    public void setReadAt(LocalDateTime readAt) { this.readAt = readAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}