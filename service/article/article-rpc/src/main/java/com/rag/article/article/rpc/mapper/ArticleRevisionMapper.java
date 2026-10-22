package com.rag.article.article.rpc.mapper;

import com.rag.article.article.rpc.domain.ArticleRevisionEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 不可变文章版本的数据访问 —— 对应 Go 端
 * {@code service/article/rpc/internal/model/community_revision.go} 的
 * {@code RevisionByID} 与 {@code handleUpsertResult} 里的 {@code MAX(revision)} 查询。
 *
 * <p><b>⚠️ 本 Mapper 刻意没有 update / delete 方法</b>：
 * {@code article_revision} 由 PG 触发器 {@code article_revision_immutable}
 * 强制不可变，提供写方法只会诱导调用方写出必然失败的 SQL。
 * 这与"框架不提供就等于不该做"是同一条纪律。
 */
@Mapper
public interface ArticleRevisionMapper {

    /**
     * 对应 Go {@code RevisionByID}：
     * {@code Where("article_id = ? AND revision_id = ?", …).First(&revision)}。
     *
     * <p><b>两个条件缺一不可</b>：只按 {@code revision_id} 查虽然有 UNIQUE 索引支撑，
     * 但语义上 revision_id 是"文章内唯一"，跨文章复用会取到错误的行；
     * Go 侧显式带上了 article_id。
     *
     * @return null 表示不存在（Go 侧对应 {@code gorm.ErrRecordNotFound}）
     */
    @Select("SELECT * FROM article_revision WHERE article_id = #{articleId} AND revision_id = #{revisionId}")
    ArticleRevisionEntity selectByRevisionId(@Param("articleId") String articleId,
                                             @Param("revisionId") String revisionId);

    /**
     * 对应 Go {@code article_sync_result_consumer.go:108-111}：
     * {@code tx.Model(&ArticleRevision{}).Where("article_id = ?", …).Select("COALESCE(MAX(revision),0)").Scan(&highest)}。
     *
     * <p>⚠️ <b>{@code COALESCE(MAX(revision), 0)} 不能简化成 {@code MAX(revision)}</b>：
     * 无行时 MAX 返回 NULL，而 Go 侧扫描进 {@code int64} 得到 0，
     * 两者等价；但若漏掉 COALESCE，Java 侧会拿到 {@code null} 包装类型，
     * 在 {@code highest + 1} 处抛 NPE —— 这是一个只在"该文章首个版本"路径上出现的缺陷。
     *
     * <p><b>无行不是异常</b>：发布第一版时就是空表，返回 0 → 新版本号 1。
     */
    @Select("SELECT COALESCE(MAX(revision), 0) FROM article_revision WHERE article_id = #{articleId}")
    long selectMaxRevision(@Param("articleId") String articleId);

    /**
     * 对应 Go {@code tx.Create(&revision)}。
     *
     * <p>⚠️ <b>裸 INSERT，无 ON CONFLICT</b>：Go 侧同样如此。
     * {@code sync_event_id} 的 UNIQUE 约束是<b>幂等的最后一道数据库兜底</b> ——
     * 应用层已比对 event_id，但并发下两个回调可能都算出 {@code max+1} 而撞复合主键，
     * 此时必须让事务失败（由 Kafka 重投收敛），而不是静默跳过。
     *
     * @return 影响行数；撞唯一约束时抛 {@code DuplicateKeyException}
     */
    @Insert("""
            INSERT INTO article_revision
                (article_id, revision, revision_id, author_id, source_object, content_sha256,
                 title, brief, cover_image_url, manual_type_tag, secondary_tags,
                 markdown, published_at, sync_event_id)
            VALUES (#{articleId}, #{revision}, #{revisionId}, #{authorId}, #{sourceObject}, #{contentSha256},
                    #{title}, #{brief}, #{coverImageUrl}, #{manualTypeTag},
                    CAST(#{secondaryTagsJson} AS jsonb),
                    #{markdown}, #{publishedAt}, #{syncEventId})
            """)
    int insert(@Param("articleId") String articleId,
               @Param("revision") long revision,
               @Param("revisionId") String revisionId,
               @Param("authorId") String authorId,
               @Param("sourceObject") String sourceObject,
               @Param("contentSha256") String contentSha256,
               @Param("title") String title,
               @Param("brief") String brief,
               @Param("coverImageUrl") String coverImageUrl,
               @Param("manualTypeTag") String manualTypeTag,
               @Param("secondaryTagsJson") String secondaryTagsJson,
               @Param("markdown") String markdown,
               @Param("publishedAt") java.time.OffsetDateTime publishedAt,
               @Param("syncEventId") String syncEventId);

    /**
     * 测试与运维用的批量读取 —— Go 侧对应测试里的
     * {@code repo.Db.Model(&model.ArticleRevision{}).Count(&revisions)}。
     *
     * <p>生产路径不需要它（生产只按 revision_id 取单条）。
     * 保留是因为<b>回滚断言需要它</b>：验证"域事件写失败 ⇒ revision 一条都没留下"
     * 必须能数出行数。
     */
    @Select("SELECT * FROM article_revision WHERE article_id = #{articleId} ORDER BY revision")
    List<ArticleRevisionEntity> selectByArticleId(@Param("articleId") String articleId);
}