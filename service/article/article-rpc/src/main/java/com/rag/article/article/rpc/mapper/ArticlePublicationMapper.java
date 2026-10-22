package com.rag.article.article.rpc.mapper;

import com.rag.article.article.rpc.domain.ArticlePublicationEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 社区发布指针的数据访问 —— 对应 Go 端
 * {@code service/article/rpc/internal/model/community_revision.go} 的 {@code Publication}、
 * 以及 {@code RetractPublicationTx} / {@code handleUpsertResult} 里的指针读写。
 *
 * <h2>⚠️ upsert 语义必须与 Go 的 {@code tx.Save(&pointer)} 区分</h2>
 * Go 侧有<b>两种</b>指针写法，行为不同：
 * <ol>
 *   <li><b>retract</b>（{@code community_article_event.go:136}）：
 *       {@code tx.Save(&pointer)} —— 行<b>必然已存在</b>（前面刚 {@code FOR UPDATE} 取到，
 *       取不到就 early-return 了），Save 是 UPDATE。</li>
 *   <li><b>publish</b>（{@code article_sync_result_consumer.go:138-141}）：
 *       先 {@code tx.Where("article_id = ?").First(&pointer)}，<b>ErrRecordNotFound 也继续</b>，
 *       然后 {@code tx.Save(&pointer)} —— GORM 的 Save 对<b>主键为零值</b>的行会走 INSERT。
 *       即：首次发布 INSERT，已发布过则 UPDATE。</li>
 * </ol>
 * ⇒ Java 侧统一用 <b>UPSERT</b>（{@link #upsert}）表达第 2 种，
 * 第 1 种的 UPDATE 用 {@link #updateRetracted}。
 * <b>不要</b>把两者都写成 {@code INSERT … ON CONFLICT DO UPDATE}：
 * retract 路径若误用 upsert，会在指针不存在时凭空插出一行 published=false 的脏指针。
 */
@Mapper
public interface ArticlePublicationMapper {

    /**
     * 对应 Go {@code Publication}：
     * {@code Where("article_id = ?", articleID).First(&pointer)}（不加锁）。
     *
     * <p>调用点：{@code FindPublicOne} 的 legacy 判定（{@code article_public.go:41}）。
     *
     * @return null 表示无指针行（Go 侧对应 {@code gorm.ErrRecordNotFound}）
     */
    @Select("SELECT * FROM article_publication WHERE article_id = #{articleId}")
    ArticlePublicationEntity selectByArticleId(@Param("articleId") String articleId);

    /**
     * 对应 Go {@code RetractPublicationTx} 的
     * {@code tx.Clauses(clause.Locking{Strength: "UPDATE"}).Where("article_id = ?").First(&pointer)}。
     *
     * <h2>🔴 行锁不可省</h2>
     * retract 与 publish 两条路径都要推进 {@code pointer_version}（并用它当
     * {@code aggregate_version}）。没有 {@code FOR UPDATE}，两条并发路径会读到同一个
     * {@code pointer_version}，各自算出相同的 {@code +1}，撞
     * {@code UNIQUE(aggregate_id, aggregate_version)} 或让事件版本倒退。
     *
     * <p>⚠️ <b>行不存在时返回 null 而不是抛异常</b>：
     * Go 侧 {@code RetractPublicationTx} 对 {@code ErrRecordNotFound} 的处理是
     * <b>标记 legacy 迁移缺口并正常返回 nil</b>
     * （{@code community_article_event.go:115-119}），不是错误。
     * 那个分支由 {@code ArticleAtomicService} 依据返回值决定。
     *
     * <p>⚠️ <b>不加 {@code deleted_at} 条件</b>：本表没有软删除列，
     * 且已删文章的指针<b>必须</b>仍可被 retract 路径读到。
     */
    @Select("SELECT * FROM article_publication WHERE article_id = #{articleId} FOR UPDATE")
    ArticlePublicationEntity selectForUpdate(@Param("articleId") String articleId);

    /**
     * 对应 retract 路径的 {@code tx.Save(&pointer)}（{@code community_article_event.go:135-136}）。
     *
     * <p>三个字段全部推进：{@code state = 'retracted'}、
     * {@code pointer_version = 指针版本 + 1}、{@code last_event_id = 域事件 ID}。
     *
     * <p>⚠️ <b>不改 {@code current_revision}</b>：Go 侧刻意保留指向，
     * 这样"最后一次批准的是什么"仍可追溯，且历史消费者能定位到被撤回的版本。
     */
    @Update("UPDATE article_publication SET state = 'retracted', pointer_version = #{pointerVersion}, "
            + "last_event_id = #{lastEventId}, updated_at = now() WHERE article_id = #{articleId}")
    int markRetracted(@Param("articleId") String articleId,
                      @Param("pointerVersion") long pointerVersion,
                      @Param("lastEventId") String lastEventId);

    /**
     * 对应 publish 路径的 {@code tx.Save(&pointer)}（{@code article_sync_result_consumer.go:138-141}）。
     *
     * <p>⚠️ <b>{@code DO UPDATE} 的分支要逐字对齐 Go 的字段</b>：
     * <pre>
     * ON CONFLICT (article_id) DO UPDATE SET
     *   current_revision = EXCLUDED.current_revision,
     *   pointer_version  = EXCLUDED.pointer_version,
     *   state            = EXCLUDED.state,
     *   last_event_id    = EXCLUDED.last_event_id
     * </pre>
     * Go 侧的 Save 会写<b>整个结构体</b>（含这 5 列）。
     * ⚠️ 但 DDL 有 {@code CHECK (pointer_version > 0)} 与
     * {@code CHECK (state IN ('published','retracted'))} ——
     * 若传入的 {@code pointerVersion} 为 0（指针行缺失时 Go 的 {@code pointer.PointerVersion + 1}
     * 会得到 1，不会是 0），CHECK 会拦下。这是 Go 侧同样存在的保护。
     *
     * <p><b>为什么不用 {@code last_event_id} 做冲突键</b>：它是普通列不是唯一键，
     * 冲突键必须是主键 {@code article_id}。
     */
    @Insert("""
            INSERT INTO article_publication
                (article_id, current_revision, pointer_version, state, last_event_id, updated_at)
            VALUES (#{articleId}, #{currentRevision}, #{pointerVersion}, #{state}, #{lastEventId}, now())
            ON CONFLICT (article_id) DO UPDATE SET
                current_revision = EXCLUDED.current_revision,
                pointer_version  = EXCLUDED.pointer_version,
                state            = EXCLUDED.state,
                last_event_id    = EXCLUDED.last_event_id,
                updated_at       = now()
            """)
    int upsert(@Param("articleId") String articleId,
               @Param("currentRevision") String currentRevision,
               @Param("pointerVersion") long pointerVersion,
               @Param("state") String state,
               @Param("lastEventId") String lastEventId);
}