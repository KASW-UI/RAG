--
-- V1__create_message_notification.sql
-- 对应 Go 版 service/message/rpc/internal/model/data_model.go: Notification
-- 表结构与 Go 端 Notification struct @gorm tag 1:1
--

CREATE TABLE IF NOT EXISTS message_notification (
    id              BIGINT       PRIMARY KEY,
    recipient_id    BIGINT       NOT NULL,
    sender_id       BIGINT       NOT NULL,
    sender_role     INTEGER      NOT NULL,
    kind            INTEGER      NOT NULL,
    title           VARCHAR(255) NOT NULL,
    content         TEXT         NOT NULL,
    is_read         BOOLEAN      NOT NULL DEFAULT FALSE,
    read_at         TIMESTAMP,
    extra           JSONB,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 对应 Go 端 Notification 上的 @gorm:"index:idx_notification_recipient_time,priority:1|2"
-- 复合索引 (recipient_id, created_at) 覆盖 ListNotifications 的 ORDER BY created_at desc
CREATE INDEX IF NOT EXISTS idx_notification_recipient_time
    ON message_notification (recipient_id, created_at);

-- 对应 Go 端 Notification 上的 @gorm:"index:idx_notification_recipient_read,priority:2"
-- 复合索引 (recipient_id, is_read) 覆盖 CountNotifications(unreadOnly=true) + MarkAllNotificationsRead
CREATE INDEX IF NOT EXISTS idx_notification_recipient_read
    ON message_notification (recipient_id, is_read);
