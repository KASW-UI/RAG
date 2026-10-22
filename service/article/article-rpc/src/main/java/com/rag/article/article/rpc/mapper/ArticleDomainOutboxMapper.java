package com.rag.article.article.rpc.mapper;

import com.rag.article.article.rpc.domain.ArticleDomainOutboxEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * H03 社区域事件发件箱的数据访问 —— 对应 Go 端
 * {@code service/article/rpc/internal/model/community_revision.go} 的 {@code ArticleDomainOutbox}
 * 与 {@code DomainOutboxPending}。
 *
 * <h2>⚠️ 本 Mapper 只有 insert 与 select，没有 update</h2>
 * Go 侧 article-rpc 对这张表<b>只写不投递</b>（全仓无 dispatcher 进程）。
 * 因此 {@code status} / {@code retry_count} 没有推进路径 ——
 * <b>照搬这个事实，不要补 MarkSent</b>：补了就等于在本组件里发明一个投递协议。
 * 投递由独立组件承担（见 {@code ArticleDomainOutboxEntity} 的类级说明）。
 *
 * <p>唯一约束 {@code UNIQUE(aggregate_id, aggregate_version)} 由 DDL 保证，
 * 它的语义是「同一聚合的同一次版本推进只产生一条域事件」。
 */
@Mapper
public interface ArticleDomainOutboxMapper {

    /**
     * 对应 Go {@code tx.Create(&event)}（两处：{@code community_article_event.go:132}
     * 与 {@code article_sync_result_consumer.go:143}）。
     *
     * <p>⚠️ <b>裸 INSERT，无 ON CONFLICT</b>：Go 侧同样如此。
     * 唯一约束冲突 ⇒ 整个发布/撤回事务失败 ⇒ Kafka 重投 ⇒ 收敛。
     * 加 DO NOTHING 会让"本应失败的并发"变成静默成功，破坏
     * 「域事件与业务状态严格同生共死」这条不变量
     * （Go 测试 {@code TestArticleDomainOutboxFailureRollsBackPublication} 依赖它）。
     *
     * @param payload 冻结的 {@code communityArticleEnvelope} JSON 文本
     */
    @Insert("""
            INSERT INTO article_domain_outbox
                (event_id, aggregate_id, aggregate_version, event_type, payload, status, retry_count,
                 created_at, updated_at)
            VALUES (#{eventId}, #{aggregateId}, #{aggregateVersion}, #{eventType},
                    CAST(#{payload} AS jsonb), 0, 0, now(), now())
            """)
    int insert(@Param("eventId") String eventId,
               @Param("aggregateId") String aggregateId,
               @Param("aggregateVersion") long aggregateVersion,
               @Param("eventType") String eventType,
               @Param("payload") String payload);

    /**
     * 对应 Go {@code DomainOutboxPending}：
     * {@code Where("status IN ?", []int32{0,2}).Order("created_at,event_id").Limit(limit)}。
     *
     * <p>⚠️ <b>{@code limit} 越界时回落为 100</b>（Go {@code community_revision.go:72-74}：
     * {@code if limit &lt; 1 || limit &gt; 100 { limit = 100 }}）。
     * 这个回落与 like-rpc 的一模一样，注意别照抄成别的阈值。
     *
     * <p>本模块生产路径不调用它（无投递器）；保留它是为了让外部投递组件能复用同一判定，
     * 以及让回滚测试能验证「域事件一条都没留下」。
     *
     * @param limit 期望条数；调用方传 0 或 &gt;100 会被本方法归一到 100
     */
    @Select("SELECT * FROM article_domain_outbox WHERE status IN (0, 2) "
            + "ORDER BY created_at, event_id LIMIT #{limit}")
    List<ArticleDomainOutboxEntity> selectPending(@Param("limit") int limit);

    /**
     * 测试与运维用的全量读取 —— Go 测试里的 {@code domainRows(t, repo)}。
     *
     * <p>按 {@code aggregate_version} 排序，因为 Go 测试正是按版本序断言
     * 「首次发布 1 条、第二次发布 2 条、撤回后 3 条」。
     */
    @Select("SELECT * FROM article_domain_outbox ORDER BY aggregate_version, event_id")
    List<ArticleDomainOutboxEntity> selectAll();
}