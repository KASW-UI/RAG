package com.rag.article.article.rpc.mapper;

import com.rag.article.article.rpc.domain.ArticleEntity;
import com.rag.article.article.rpc.domain.ArticleRevisionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 公开读路径的数据访问 —— 对应 Go 端
 * {@code service/article/rpc/internal/model/article_public.go} 的<b>全部</b>内容。
 *
 * <h2>为什么必须独立于 {@link ArticleMapper}</h2>
 * "内部/作者视角"与"读者视角"是<b>两套不同的可见性规则</b>：
 * 作者看到自己<b>当前的草稿</b>（status 可能是 REVIEWING），读者只能看到<b>已批准的冻结 revision</b>。
 * 两者的 where、join、排序、计数字段全部不同。
 * 合并进 {@code ArticleMapper} 会让"读者能看到什么"这条规则无法被单独 review 与单独测试
 * （这正是 FileTree/article/rpc.md §12.4 SPLIT 的依据）。
 *
 * <h2>🔴 三条不可改的 SQL 语义</h2>
 * <ol>
 *   <li><b>复合可见性谓词（含 legacy 回退）</b>：
 *       <pre>
 *       (p.state = 'published' AND p.current_revision &lt;&gt; ''
 *          AND EXISTS (SELECT 1 FROM article_revision r
 *                        WHERE r.article_id = a.id AND r.revision_id = p.current_revision))
 *       OR (p.article_id IS NULL AND a.status = 2)
 *       </pre>
 *       第二支是<b>显式的 legacy 兼容</b>：一个 PUBLISHED(2) 且<b>从未有过</b> publication 行的
 *       老数据仍然公开可见（正文回落到原 MinIO 对象，响应带
 *       {@code publication_gap=legacy_revision_missing} 标记）。
 *       <b>不要"清理"掉这一支</b>：删掉它会让全部历史 PUBLISHED 文章从公开列表消失。
 *       Go 测试 {@code article_public_test.go} 的 {@code "legacy"} 行专门验证这一点。</li>
 *   <li><b>标签过滤匹配 revision 而非 article</b>：读者看到的分类是<b>批准时</b>的值。
 *       作者改了草稿标签不应泄漏给读者（Go {@code article_public.go:67-85}）。
 *       legacy 行没有 revision，落到 {@code a.secondary_tags @> …} 分支。</li>
 *   <li><b>count 与 page 必须在同一只读快照里</b>：Go 用
 *       {@code Transaction(fn, &sql.TxOptions{Isolation: LevelRepeatableRead, ReadOnly: true})}
 *       把 count + 分页 + revision 取值包在一起（{@code article_public.go:65,128}），
 *       否则并发编辑/撤回会让 total 与本页内容来自<b>不同时刻</b>。
 *       Java 侧的等价物是 {@code ArticlePublicDomainService} 上的
 *       {@code @Transactional(isolation = REPEATABLE_READ, readOnly = true)}。</li>
 * </ol>
 */
@Mapper
public interface ArticlePublicMapper {

    /**
     * 可见性谓词的<b>单一来源</b>，供 count / page 两处引用同一份规则。
     *
     * <p>⚠️ Go 侧也是这么做的：{@code publicArticlePredicate} 常量 +
     * {@code publicArticleQuery(db)} 复用（{@code article_public.go:20-32}）。
     * count 与 page 各写一份谓词、改一处忘另一处，就会让 total 与实际页内容对不上。
     *
     * <p><b>别名约定</b>：{@code a} = article、{@code p} = article_publication、
     * {@code r} = article_revision。与 Go 的子查询别名保持一致，便于逐行比对。
     */
    String PUBLIC_ARTICLE_PREDICATE =
            "((p.state = 'published' AND p.current_revision <> ''\n"
            + "        AND EXISTS (SELECT 1 FROM article_revision r\n"
            + "                     WHERE r.article_id = a.id AND r.revision_id = p.current_revision))\n"
            + " OR (p.article_id IS NULL AND a.status = 2))";

    /**
     * 同一谓词的 <b>CDATA 包装版</b> —— 供 {@code <script>} 内使用。
     *
     * <p>🔴 <b>不是可有可无的装饰</b>：{@code PUBLIC_ARTICLE_PREDICATE} 里含
     * {@code p.current_revision <> ''}，而 MyBatis 的 {@code XMLLanguageDriver}
     * 会把 {@code @Select("<script>…")} 的内容<b>当 XML 文档解析</b>
     * （{@code XPathParser}）。裸的 {@code <>} 在 XML 里是标签起始符 ⇒ 解析直接失败：
     * <pre>BuilderException: Error creating document instance.
     *   Cause: SAXParseException; lineNumber: 4; columnNumber: 49;
     *   元素内容必须由格式正确的字符数据或标记组成。</pre>
     * ⇒ <b>countPublic / selectPublicPage 在上下文启动时就会炸</b>，
     * 而纯编译期（不启动上下文）看不出来 —— 与 §3.3 同型。
     *
     * <p>为什么保留两个常量而不是直接把谓词写死成 CDATA 版：
     * {@link #selectPublicById} 用的是<b>非 script</b> 的 {@code @Select}，
     * 那里内容不按 XML 解析，加了 CDATA 反而会变成 SQL 里的字面垃圾。
     */
    String PUBLIC_ARTICLE_PREDICATE_CDATA = "<![CDATA[" + PUBLIC_ARTICLE_PREDICATE + "]]>";

    /** 对应 Go {@code FindPublicOne}：可见谓词 + {@code a.id = ?} + {@code Select("article.*")}。 */
    @Select("SELECT a.* FROM article a\n"
            + "LEFT JOIN article_publication p ON p.article_id = a.id\n"
            + "WHERE a.deleted_at IS NULL AND a.id = #{id} AND\n"
            + PUBLIC_ARTICLE_PREDICATE)
    @ResultMap("com.rag.article.article.rpc.mapper.ArticleMapper.articleEntityResultMap")
    ArticleEntity selectPublicById(@Param("id") String id);

    /**
     * 对应 Go {@code ListPublic} 的 <b>count</b> 部分。
     *
     * <p>⚠️ 与 {@link #selectPublicPage} 共用 {@link #PUBLIC_ARTICLE_PREDICATE}
     * 与同一组标签过滤常量 —— 两边口径必须一致，否则 total 与实际页内容对不上。
     */
    @Select("<script>"
            + "SELECT COUNT(*) FROM article a\n"
            + "LEFT JOIN article_publication p ON p.article_id = a.id\n"
            + "WHERE a.deleted_at IS NULL AND\n"
            + PUBLIC_ARTICLE_PREDICATE_CDATA + "\n"
            + "<where>"
            + "<if test=\"manualTypeTag != null and manualTypeTag != ''\">"
            + "<![CDATA[" + " AND ((p.state = 'published' AND EXISTS (\n"
            + "        SELECT 1 FROM article_revision r WHERE r.article_id = a.id\n"
            + "          AND r.revision_id = p.current_revision AND r.manual_type_tag = #{manualTypeTag}))\n"
            + "   OR (p.article_id IS NULL AND a.manual_type_tag = #{manualTypeTag}))" + "]]>"
            + "</if>"
            + "<if test=\"secondaryTag != null and secondaryTag != ''\">"
            + "<![CDATA[" + " AND ((p.state = 'published' AND EXISTS (\n"
            + "        SELECT 1 FROM article_revision r WHERE r.article_id = a.id\n"
            + "          AND r.revision_id = p.current_revision\n"
            + "          AND r.secondary_tags @> CAST(#{secondaryTagJson} AS jsonb)))\n"
            + "   OR (p.article_id IS NULL AND a.secondary_tags @> CAST(#{secondaryTagJson} AS jsonb)))" + "]]>"
            + "</if>"
            + "<if test=\"authorId != null and authorId != ''\">"
            + "<![CDATA[ AND a.author_id = #{authorId}]]>"
            + "</if>"
            + "</where>"
            + "</script>")
    long countPublic(@Param("manualTypeTag") String manualTypeTag,
                     @Param("secondaryTagJson") String secondaryTagJson,
                     @Param("authorId") String authorId);

    /**
     * 对应 Go {@code ListPublic} 的 <b>取数</b> 部分：
     * {@code query.Select("article.*").Order(publicArticleOrder(opt)).Offset((page-1)*size).Limit(size)}。
     *
     * <p><b>⚠️ 排序与作者视角不同</b>：公开路径的 {@code publicArticleOrder}
     * （{@code article_public.go:132-147}）支持
     * {@code view_count / like_count / comment_count}（<b>比作者路径多 comment_count</b>），
     * 且<b>总是附加 {@code , article.id 同方向} 作为二级排序键</b>，
     * 并且<b>无条件应用 desc</b>（作者路径在 {@code SortBy == ""} 时忽略 desc）。
     * 两处差异都影响返回顺序，必须分别复刻。
     *
     * <p>⚠️ 本方法返回的行<b>不含正文</b>：Go 侧列表对每条都调
     * {@code publishedArticleResponse(article, "")}，markdown 传空串
     * （{@code list_articles_logic.go:122}）—— 公开列表<b>永不返回正文，也不返回对象 key</b>。
     */
    @Select("<script>"
            + "SELECT a.* FROM article a\n"
            + "LEFT JOIN article_publication p ON p.article_id = a.id\n"
            + "WHERE a.deleted_at IS NULL AND\n"
            + PUBLIC_ARTICLE_PREDICATE_CDATA + "\n"
            + "<where>"
            + "<if test=\"manualTypeTag != null and manualTypeTag != ''\">"
            + "<![CDATA[" + " AND ((p.state = 'published' AND EXISTS (\n"
            + "        SELECT 1 FROM article_revision r WHERE r.article_id = a.id\n"
            + "          AND r.revision_id = p.current_revision AND r.manual_type_tag = #{manualTypeTag}))\n"
            + "   OR (p.article_id IS NULL AND a.manual_type_tag = #{manualTypeTag}))" + "]]>"
            + "</if>"
            + "<if test=\"secondaryTag != null and secondaryTag != ''\">"
            + "<![CDATA[" + " AND ((p.state = 'published' AND EXISTS (\n"
            + "        SELECT 1 FROM article_revision r WHERE r.article_id = a.id\n"
            + "          AND r.revision_id = p.current_revision\n"
            + "          AND r.secondary_tags @> CAST(#{secondaryTagJson} AS jsonb)))\n"
            + "   OR (p.article_id IS NULL AND a.secondary_tags @> CAST(#{secondaryTagJson} AS jsonb)))" + "]]>"
            + "</if>"
            + "<if test=\"authorId != null and authorId != ''\">"
            + "<![CDATA[ AND a.author_id = #{authorId}]]>"
            + "</if>"
            + "</where>"
            + " ORDER BY\n"
            + "<choose>"
            + "  <when test=\"sortBy == 'view_count'\">a.view_count</when>\n"
            + "  <when test=\"sortBy == 'like_count'\">a.like_count</when>\n"
            + "  <when test=\"sortBy == 'comment_count'\">a.comment_count</when>\n"
            + "  <otherwise>a.created_at</otherwise>\n"
            + "</choose>\n"
            + "<choose>\n"
            + "  <when test=\"desc\">DESC</when>\n"
            + "  <otherwise>ASC</otherwise>\n"
            + "</choose>,\n"
            + "a.id\n"
            + "<choose>\n"
            + "  <when test=\"desc\">DESC</when>\n"
            + "  <otherwise>ASC</otherwise>\n"
            + "</choose>\n"
            + "LIMIT #{limit} OFFSET #{offset}\n"
            + "</script>")
    @ResultMap("com.rag.article.article.rpc.mapper.ArticleMapper.articleEntityResultMap")
    List<ArticleEntity> selectPublicPage(@Param("manualTypeTag") String manualTypeTag,
                                          @Param("secondaryTagJson") String secondaryTagJson,
                                          @Param("authorId") String authorId,
                                          @Param("sortBy") String sortBy,
                                          @Param("desc") boolean desc,
                                          @Param("limit") int limit,
                                          @Param("offset") long offset);

    /**
     * 对应 Go {@code ListPublic} 里批量取 revision 的那段（{@code article_public.go:109-123}）。
     *
     * <p>⚠️ <b>join 条件包含 {@code current_revision = revision_id}</b>：
     * 只按 {@code article_id IN} 取会把该文章<b>所有历史版本</b>都拉回来，
     * 而 Go 侧靠这个 join 条件<b>只取当前指针指向的那一个</b>。
     * 另加 {@code state = 'published'}：撤回后指针仍指向旧 revision，
     * 但那篇文章已不可见，不该出现在结果里。
     *
     * <p>Go 侧用 {@code byID[revisions[i].ArticleID]} 建 map，
     * 每篇文章最多一个 revision（由 join 条件保证），Java 侧按 articleId 建 Map 等价。
     */
    @Select("<script>"
            + "SELECT r.* FROM article_revision r\n"
            + "JOIN article_publication p ON p.article_id = r.article_id AND p.current_revision = r.revision_id\n"
            + "WHERE p.state = 'published' AND r.article_id IN\n"
            + "<foreach item='id' collection='articleIds' open='(' separator=',' close=')'>#{id}</foreach>\n"
            + "</script>")
    List<ArticleRevisionEntity> selectRevisionsForPublished(@Param("articleIds") List<String> articleIds);

    /**
     * 对应 Go {@code FindPublicOne} 里的 {@code m.Publication(ctx, id)}（{@code community_revision.go:65-69}）。
     *
     * <p>⚠️ <b>返回 null 与"指针存在但状态不对"必须区分</b>：
     * Go 侧 {@code FindPublicOne} 的逻辑是
     * <pre>
     * pointer, err := m.Publication(ctx, id)
     * if err == gorm.ErrRecordNotFound &amp;&amp; article.Status == 2 { return result, nil }  // legacy：Revision 保持 nil
     * if err != nil { return PublicArticle{}, err }
     * if pointer.State != "published" || pointer.CurrentRevision == "" { return PublicArticle{}, gorm.ErrRecordNotFound }
     * </pre>
     * 即三种结果：<b>无指针行</b>（legacy，看 article.status 决定）、
     * <b>指针非 published / 空版本</b>（NotFound）、<b>正常</b>（取 revision）。
     * 由 {@code ArticlePublicDomainService} 承载这段三分支，本方法只负责取行。
     */
    @Select("SELECT * FROM article_publication WHERE article_id = #{articleId}")
    ArticlePublicationRow selectPublication(@Param("articleId") String articleId);

    /**
     * 对应 Go {@code IncrPublicViewCount}（{@code article_public.go:151-161}）：
     * 带谓词的<b>原子</b> {@code UpdateColumn}，{@code RowsAffected == 1} 才算成功。
     *
     * <h2>🔴 为什么不拆成"先查后写"</h2>
     * Go 注释写得很直白：<b>「A withdrawal that won the race cannot gain a view or return an old result」</b>。
     * 若先 {@code SELECT} 判可见、再 {@code UPDATE}，两步之间发生撤回时，
     * 已下架的文章<b>仍会拿到一次阅读量</b>并返回一份陈旧结果。
     * 单条带谓词的 UPDATE 让"判定 + 计数"在<b>同一条语句</b>内由 PG 完成。
     *
     * <p>⚠️ 谓词是 {@link #PUBLIC_ARTICLE_PREDICATE} 的<b>对偶形式</b>：
     * UPDATE 的目标表不能 join 自己，故改写成 NOT EXISTS / EXISTS 子查询。
     * 两份谓词<b>必须保持等价</b>：改成"与 select 不同"就会让
     * "查得到但计不了数"或"查不到却计了数"分裂。
     * <br>逐条对照 Go {@code article_public.go:152-158}：legacy 支的
     * {@code status = 2 AND NOT EXISTS(publication)}、published 支的
     * {@code EXISTS(publication JOIN revision … state='published')} 完全一致。
     *
     * @return 1 = 可见且已计数；0 = 撤回赢了竞态（上层据此返回 NotFound）
     */
    @Update("UPDATE article SET view_count = view_count + 1\n"
            + "WHERE id = #{id} AND deleted_at IS NULL\n"
            + "  AND ((status = 2 AND NOT EXISTS (SELECT 1 FROM article_publication\n"
            + "                 WHERE article_publication.article_id = article.id))\n"
            + "       OR EXISTS (SELECT 1 FROM article_publication\n"
            + "                      JOIN article_revision\n"
            + "                        ON article_revision.article_id = article_publication.article_id\n"
            + "                       AND article_revision.revision_id = article_publication.current_revision\n"
            + "                    WHERE article_publication.article_id = article.id\n"
            + "                      AND article_publication.state = 'published'))")
    int incrPublicViewCount(@Param("id") String id);

    /**
     * {@code article_publication} 的行投影 —— 只声明本模块真实读取的三列。
     *
     * <p><b>为什么不直接复用 {@code ArticlePublicationEntity}</b>：
     * 那是给写入路径（insert / {@code FOR UPDATE}）用的完整实体；
     * 公开读路径只需要「指针状态 + 指向哪个 revision」，
     * 用窄投影能让"读路径依赖哪几列"在类型上可见，避免误把写入实体当读模型扩散出去。
     */
    final class ArticlePublicationRow {
        private String articleId;
        private String currentRevision;
        private long pointerVersion;
        private String state;
        private String lastEventId;

        public String getArticleId() {
            return articleId;
        }

        public void setArticleId(String articleId) {
            this.articleId = articleId;
        }

        public String getCurrentRevision() {
            return currentRevision;
        }

        public void setCurrentRevision(String currentRevision) {
            this.currentRevision = currentRevision;
        }

        public long getPointerVersion() {
            return pointerVersion;
        }

        public void setPointerVersion(long pointerVersion) {
            this.pointerVersion = pointerVersion;
        }

        public String getState() {
            return state;
        }

        public void setState(String state) {
            this.state = state;
        }

        public String getLastEventId() {
            return lastEventId;
        }

        public void setLastEventId(String lastEventId) {
            this.lastEventId = lastEventId;
        }
    }
}