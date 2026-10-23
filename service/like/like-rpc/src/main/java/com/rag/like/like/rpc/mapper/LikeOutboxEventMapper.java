package com.rag.like.like.rpc.mapper;

import com.rag.like.like.rpc.domain.LikeOutboxEventEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 热点事件发件箱数据访问 —— 对应 Go 端
 * {@code service/like/rpc/internal/model/like_outbox.go} 的 {@code LikeOutboxEventModel}。
 *
 * <p>消费方是 {@code LikeHotEventOutboxRelay}（Go: {@code internal/mq/internal/mqs/like_outbox_sender.go}），
 * 每秒扫一批、逐条推送、失败则 {@code MarkFailed} 并 continue、成功则 {@code MarkSent}。
 */
@Mapper
public interface LikeOutboxEventMapper {

    /**
     * 对应 Go {@code tx.Clauses(clause.OnConflict{DoNothing: true}).Create(p.Outbox)}。
     *
     * <p>只在「首次点赞」路径被调用（{@code oldState == 0 && newState == 1}），
     * 依赖 {@code uk_event_key} 保证同一 (user,type,id) 只产生一条事件。
     *
     * <p>⚠️ 2026-10-23 修正：原实现写的是 {@code ON CONFLICT (event_key) DO NOTHING}，
     * <b>与 Go 不等价</b>。本表有<b>两个</b>唯一约束（{@code event_id} 主键 + {@code uk_event_key}），
     * 带上仲裁目标后，撞 {@code event_id} 主键时 PG 会<b>抛唯一约束异常</b>；
     * 而 Go 的 {@code clause.OnConflict{DoNothing:true}} <b>不带目标</b>，任意唯一约束冲突都静默忽略。
     * ⇒ 去掉目标列，对齐 Go（DRIFT-LIKE-011）。
     * 对照：{@code LikeRecordMapper#insertSeedIfAbsent} 本来就是无目标的写法。
     */
    @Insert("""
            INSERT INTO like_outbox_event
                (event_id, event_key, event_type, aggregate_id, payload, status, retry_count, created_at, updated_at)
            VALUES (#{eventId}, #{eventKey}, #{eventType}, #{aggregateId}, #{payload}::json, 0, 0, now(), now())
            ON CONFLICT DO NOTHING
            """)
    int insertIfAbsent(@Param("eventId") String eventId,
                       @Param("eventKey") String eventKey,
                       @Param("eventType") String eventType,
                       @Param("aggregateId") String aggregateId,
                       @Param("payload") String payload);

    /**
     * 对应 Go {@code FetchPending}：
     * {@code WHERE status IN (0,2) AND retry_count < 3 ORDER BY created_at asc LIMIT n}。
     *
     * <p>⚠️ {@code retry_count < 3} 是<b>硬性停止重试</b>的条件：失败 3 次后该事件被永久搁置，
     * 不再进入本方法的返回集。没有告警、没有死信队列 —— 这是 Go 的既有行为，照搬。
     */
    @Select("""
            SELECT event_id, event_key, event_type, aggregate_id, payload,
                   status, retry_count, created_at, updated_at
            FROM like_outbox_event
            WHERE status IN (0, 2) AND retry_count < 3
            ORDER BY created_at ASC
            LIMIT #{limit}
            """)
    List<LikeOutboxEventEntity> selectPending(@Param("limit") int limit);

    /** 对应 Go {@code MarkSent}：{@code Update("status", 1)}。 */
    @Update("UPDATE like_outbox_event SET status = 1, updated_at = now() WHERE event_id = #{eventId}")
    int markSent(@Param("eventId") String eventId);

    /**
     * 对应 Go {@code MarkFailed}：{@code Updates({"status": 2, "retry_count": gorm.Expr("retry_count + 1")})}。
     *
     * <p>用 {@code retry_count = retry_count + 1} 而不是 {@code retry_count = #{n}}：
     * 与 relay 的其它并发实例共享同一计数，不能被读-改-写覆盖。
     */
    @Update("""
            UPDATE like_outbox_event
            SET status = 2, retry_count = retry_count + 1, updated_at = now()
            WHERE event_id = #{eventId}
            """)
    int markFailed(@Param("eventId") String eventId);
}