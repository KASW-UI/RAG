package com.rag.article.article.rpc.mapper;

import com.rag.article.article.rpc.domain.ArticleSyncOutboxEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 文章同步发件箱数据访问 —— 对应 Go 端
 * {@code service/article/rpc/internal/model/article_sync_outbox.go} 的
 * {@code ArticleSyncOutboxModel}（CreateTx / FetchPending / MarkSent / MarkFailed）。
 *
 * <p>四个方法构成 outbox 状态机：{@code pending(0) → sent(1)} 或
 * {@code pending(0) → failed(2) → pending}（retry_count+1，超过 maxRetry 后不再捞取）。
 */
@Mapper
public interface ArticleSyncOutboxMapper {

    /**
     * 对应 Go {@code CreateTx}：{@code tx.WithContext(ctx).Create(data)}。
     *
     * <p><b>⚠️ 没有 ON CONFLICT DO NOTHING</b>：Go 侧是<b>裸 INSERT</b>，
     * 唯一键 {@code event_key} 冲突会直接让整个事务失败。
     * 这与 like-rpc 的 {@code insertIfAbsent}（有 DO NOTHING）<b>不同</b>，
     * 不要照抄那边：加 DO NOTHING 会让「重复事件被静默吞掉」，
     * 而 Go 是让事务回滚、消息重投 —— 两种失败语义对上游可见。
     *
     * <p>⚠️ {@code payload} 用 {@code CAST(? AS jsonb)}：Go 侧该列是 {@code jsonb}，
     * 但值是 {@code MustMarshalSyncEvent} 产出的 JSON 文本。
     *
     * <p>⚠️ {@code created_at}/{@code updated_at} 由 SQL 侧 {@code now()} 填，
     * 与 Go 的 {@code autoCreateTime}/{@code autoUpdateTime} 时点一致。
     */
    @Insert("""
            INSERT INTO article_sync_outbox
                (event_id, event_key, event_type, aggregate_id, payload, status, retry_count, created_at, updated_at)
            VALUES (#{eventId}, #{eventKey}, #{eventType}, #{aggregateId}, CAST(#{payload} AS jsonb),
                    0, 0, now(), now())
            """)
    int insert(@Param("eventId") String eventId,
               @Param("eventKey") String eventKey,
               @Param("eventType") String eventType,
               @Param("aggregateId") String aggregateId,
               @Param("payload") String payload);

    /**
     * 对应 Go {@code FetchPending}：
     * <pre>
     * query := db.Where("status IN (?, ?)", StatusPending, StatusFailed)
     * if maxRetry &gt; 0 { query = query.Where("retry_count &lt; ?", maxRetry) }
     * query.Order("created_at asc").Limit(limit)
     * </pre>
     *
     * <p>⚠️ <b>{@code maxRetry &lt;= 0} 时不加该过滤</b>（Go 的 {@code if maxRetry > 0}），
     * 此时失败事件<b>无限重试</b>。yaml 配的是 10，不要改成"恒为 10"。
     *
     * <p>⚠️ 与 like-rpc 的 {@code selectPending} <b>硬编码 {@code retry_count < 3}</b> 不同：
     * 本域的阈值是可配置的 {@code articleSyncOutbox.maxRetry}。别照抄常量。
     */
    @Select("<script>"
            + "SELECT * FROM article_sync_outbox\n"
            + "WHERE status IN (0, 2)\n"
            + "<if test=\"maxRetry != null and maxRetry > 0\"> AND retry_count &lt; #{maxRetry}</if>\n"
            + "ORDER BY created_at ASC\n"
            + "LIMIT #{limit}\n"
            + "</script>")
    List<ArticleSyncOutboxEntity> fetchPending(@Param("limit") int limit, @Param("maxRetry") int maxRetry);

    /**
     * 对应 Go {@code MarkSent}：
     * {@code Updates(map[string]any{"status": StatusSent, "last_error": ""})}。
     *
     * <p>⚠️ <b>要把 {@code last_error} 清空</b>：Go 侧显式写了 {@code "last_error": ""}。
     * 漏掉会让上一轮的失败原因在成功后残留，运维看板会把已成功事件标成失败过。
     */
    @Update("UPDATE article_sync_outbox SET status = 1, last_error = '', updated_at = now() "
            + "WHERE event_id = #{eventId}")
    int markSent(@Param("eventId") String eventId);

    /**
     * 对应 Go {@code MarkFailed}：
     * {@code Updates(map[string]any{"status": StatusFailed, "retry_count": gorm.Expr("retry_count + 1"), "last_error": lastError})}。
     *
     * <p>⚠️ 用 {@code retry_count = retry_count + 1} 而不是 {@code = #{n}}：
     * 多个 relay 实例共享同一计数，读-改-写会丢增量。
     */
    @Update("UPDATE article_sync_outbox SET status = 2, retry_count = retry_count + 1, "
            + "last_error = #{lastError}, updated_at = now() WHERE event_id = #{eventId}")
    int markFailed(@Param("eventId") String eventId, @Param("lastError") String lastError);

    /**
     * 对应 Go {@code article_sync_result_consumer.go:96} 的
     * {@code tx.Where("event_id = ?", result.EventID).First(&syncOutbox)}。
     *
     * <p><b>唯一调用点</b>：发布回调要读回<b>冻结的</b> outbox payload，
     * 与结果消息做六项逐字段比对（op / event_id / version_ms / article_id / author_id …）。
     * 读不到（返回 null）会让整个发布事务失败并重投 —— 这是 Go 的行为。
     *
     * <p>⚠️ <b>不加 {@code status} 过滤</b>：Go 侧也没有；已 sent 的行同样要能读到，
     * 因为 Kafka 重投的同一 event_id 仍需比对冻结输入。
     */
    @Select("SELECT * FROM article_sync_outbox WHERE event_id = #{eventId}")
    ArticleSyncOutboxEntity selectByEventId(@Param("eventId") String eventId);
}