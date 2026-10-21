package com.rag.message.message.rpc.service;

import com.rag.message.message.rpc.domain.NotificationEntity;
import com.rag.message.message.rpc.mapper.NotificationMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.verify;

/**
 * {@code markNotificationReadTx} 的 Go 对齐契约（AGENTS.md §6.2 P2，2026-10-21 修）。
 *
 * <p><b>Go 出处逐字</b>（{@code service/message/rpc/internal/model/message.go:72-86}）：
 * <pre>
 * res := conn.Model(&amp;Notification{}).
 *      Where("id = ? AND recipient_id = ?", notificationID, recipientID).
 *      Updates(map[string]any{"is_read": true, "read_at": &amp;now})
 * if res.Error != nil { return res.Error }
 * if res.RowsAffected == 0 { return ErrRecordNotFound }
 * </pre>
 *
 * <p><b>修复前</b>：DomainService 先 {@code selectById} 校验归属、再 {@code updateById} 写入。
 * 两个问题：
 * <ol>
 *   <li>{@code updateById} 生成的 WHERE 只有 {@code id = ?}，<b>不含 recipient_id</b>
 *       ——归属判断与写入分处两条语句，中间存在 TOCTOU 窗口；</li>
 *   <li>受影响行数被丢弃，「更新不到行」被静默当成成功，而 Go 会返回
 *       {@code ErrRecordNotFound} → logic 映射成 {@code NotFound + ErrorNotificationMiss}。</li>
 * </ol>
 *
 * <p><b>为什么断言打在 SQL 上</b>（§3.3）：纯 Mockito mock 数据层时，
 * {@code markRead} 无论内部拼出什么 WHERE 都会返回预设值 —— 断言返回值等于什么都没测。
 * 必须用 {@code ArgumentCaptor} 抓 {@link LambdaUpdateWrapper} 并读出它生成的
 * {@code sqlSegment} 与参数，才真正验证到 {@code recipient_id} 是否进了 WHERE。
 */
class MessageMarkNotificationReadTest {

    /**
     * MyBatis-Plus 的 {@code LambdaUpdateWrapper} 靠 {@code TableInfo} 缓存把
     * {@code Entity::getXxx} 方法引用解析成列名；缓存不初始化就直接抛
     * 「can not find lambda cache for this entity」。
     *
     * <p>本测试不引数据库（§3.3：mock 数据层测不到 SQL），
     * 因此用 MP 官方的 {@code TableInfoHelper.initTableInfo} 手工建一次元数据缓存，
     * 让 wrapper 能生成真实 SQL 片段供断言。
     */
    @org.junit.jupiter.api.BeforeAll
    static void initMybatisPlusMetadata() {
        // 用 MP 自己的 MybatisConfiguration（而非原生 Configuration），
        // 否则 GlobalConfig 拿不到默认值，dbColumnUnderline 不生效，
        // 列名会渲染成 recipientId / readAt 而不是真实 SQL 里的 recipient_id / read_at。
        org.apache.ibatis.session.Configuration configuration =
                new com.baomidou.mybatisplus.core.MybatisConfiguration();
        org.apache.ibatis.builder.MapperBuilderAssistant assistant =
                new org.apache.ibatis.builder.MapperBuilderAssistant(configuration, "");
        assistant.setCurrentNamespace(MessageMarkNotificationReadTest.class.getName());
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper
                .initTableInfo(assistant, NotificationEntity.class);
    }

    @Test
    @DisplayName("A-P2：UPDATE 的 WHERE 必须同时含 id 与 recipient_id（Go 的归属校验）")
    void whereClauseContainsIdAndRecipientId() {
        NotificationMapper mapper = mock(NotificationMapper.class, CALLS_REAL_METHODS);
        ArgumentCaptor<LambdaUpdateWrapper<NotificationEntity>> cap = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        // 默认返回值给 1（命中）
        org.mockito.Mockito.doReturn(1).when(mapper).update(isNull(), cap.capture());

        LocalDateTime readAt = LocalDateTime.of(2026, 10, 21, 8, 0);
        int affected = mapper.markRead(123L, 456L, readAt);

        assertEquals(1, affected);
        LambdaUpdateWrapper<NotificationEntity> w = cap.getValue();
        String sql = w.getSqlSegment();
        // 列名由 Lambda 元数据解析而来；断言的是"两个条件都在"而非字面列名，
        // 避免@TableField 改名时误报。
        assertTrue(sql.contains("id"), "WHERE 缺 id 条件：" + sql);
        assertTrue(sql.contains("recipient_id"), "WHERE 缺 recipient_id —— 归属校验会失效：" + sql);
        // 且不能把 recipient_id 误写进 SET
        String setPart = w.getSqlSet();
        assertTrue(!setPart.contains("recipient_id"),
                "recipient_id 是条件不是被更新字段：" + setPart);
    }

    @Test
    @DisplayName("A-P2：SET 必须含 is_read / read_at，且用 .set() 而非 update(entity) 避免丢 typeHandler")
    void setClauseUpdatesReadFlagAndTimestamp() {
        NotificationMapper mapper = mock(NotificationMapper.class, CALLS_REAL_METHODS);
        ArgumentCaptor<LambdaUpdateWrapper<NotificationEntity>> cap = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        org.mockito.Mockito.doReturn(1).when(mapper).update(isNull(), cap.capture());

        LocalDateTime readAt = LocalDateTime.of(2026, 10, 21, 8, 0);
        mapper.markRead(1L, 2L, readAt);

        String setPart = cap.getValue().getSqlSet();
        assertTrue(setPart.contains("is_read"), "缺 is_read：" + setPart);
        assertTrue(setPart.contains("read_at"), "缺 read_at：" + setPart);
    }

    @Test
    @DisplayName("A-P2：受影响行数必须原样返回 —— 0 对齐 Go 的 RowsAffected == 0 → ErrRecordNotFound")
    void affectedRowsPropagatedToCaller() {
        NotificationMapper mapper = mock(NotificationMapper.class, CALLS_REAL_METHODS);
        org.mockito.Mockito.doReturn(0).when(mapper).update(isNull(), any(LambdaUpdateWrapper.class));

        int affected = mapper.markRead(999L, 888L, LocalDateTime.now());
        assertEquals(0, affected,
                "受影响行数必须透出，上层据此映射 NotFound；静默丢弃会让「不存在」被当成成功");
    }

    /**
     * 锁住 {@code NotificationEntity.read} 这个字段名 —— 它是<b>运行期致命</b>的，不是风格问题。
     *
     * <p>把字段从 {@code read} 改回 {@code isRead}，MyBatis-Plus 的 lambda 解析会退化成
     * 属性 {@code read}，而 propertyMap 里是 {@code isRead} ⇒
     * {@code MybatisPlusException: can not find lambda cache for this property [read]}。
     * 这里用反射是刻意的：静态引用（{@code NotificationEntity::isRead}）在编译期看不出问题，
     * 只有真正解析 lambda 时才炸，而那时是运行期、且没有任何测试覆盖（§3.3）。
     */
    @Test
    @DisplayName("A-P2：实体字段名必须是 read（不是 isRead），否则 MP lambda 运行期抛异常")
    void entityFieldMustBeNamedReadNotIsRead() throws Exception {
        java.lang.reflect.Field f = NotificationEntity.class.getDeclaredField("read");
        assertEquals("boolean", f.getType().getSimpleName());
        // 显式列映射：不能依赖 map-underscore-to-camel-case 的双向推断
        com.baomidou.mybatisplus.annotation.TableField ann =
                f.getAnnotation(com.baomidou.mybatisplus.annotation.TableField.class);
        assertTrue(ann != null && "is_read".equals(ann.value()),
                "read 字段必须有 @TableField(\"is_read\")，否则列名推断不可靠");
        // 且确实不存在 isRead 字段（防止有人"顺手改回去"）
        for (java.lang.reflect.Field each : NotificationEntity.class.getDeclaredFields()) {
            assertTrue(!"isRead".equals(each.getName()),
                    "不得存在 isRead 字段：MP 会把 NotificationEntity::isRead 解析成属性 read 并抛异常");
        }
    }

    /** 占位依赖：确保 LambdaUpdateWrapper 在本测试的 classpath 上可见。 */
    @SuppressWarnings("unused")
    private LambdaUpdateWrapper<NotificationEntity> typeAnchor() {
        return Wrappers.lambdaUpdate();
    }
}