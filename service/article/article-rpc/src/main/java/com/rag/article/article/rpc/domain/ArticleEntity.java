package com.rag.article.article.rpc.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.rag.article.article.rpc.mapper.JsonbTypeHandler;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 文章主表 ORM 实体 —— 对应 Go 端
 * {@code service/article/rpc/internal/model/article.go: Article}。
 *
 * <p>表结构：{@code V8__create_article.sql}（照抄 Go 的 {@code article.sql}）。
 *
 * <p><b>🔴 {@code autoResultMap = true} 不是可选项</b>：MyBatis-Plus <b>只有</b>在
 * {@code @TableName(autoResultMap = true)} 时才会把 {@code @TableField(typeHandler = ...)}
 * 应用到<b>自动生成的 SELECT 结果映射</b>上。少了它，读 jsonb 列拿到的是 {@code PGobject}，
 * 映射成 {@code List}/{@code Map} 直接失败。
 * 这与 AGENTS.md §3.3「断言 Bean 存在 ≠ 断言机制生效」同源——
 * <b>typeHandler 写在字段上 ≠ 它在 SELECT 上生效</b>。
 * 锁死用例：{@code ArticleJsonbMappingTest}。
 *
 * <h2>L3 语义：三列各自承载不同的业务含义，不可互相替代</h2>
 * <ul>
 *   <li>{@link #content} 存的是 <b>MinIO 对象 key</b>，不是 Markdown 正文。
 *       作者详情路径用它去 MinIO 取字节；公开路径<b>不用它</b>（用冻结的 revision.markdown）。
 *       这正是 {@code get_article_logic.go:137-155} 里 revision/legacy 分叉的原因。</li>
 *   <li>{@link #status} 对应 proto 的 {@code ArticleStatus} 枚举数值
 *       （0 UNSPECIFIED / 1 DRAFT / 2 PUBLISHED / 3 REVIEWING / 4 REJECTED / 5 DELETED）。
 *       ⚠️ <b>Go 的 DDL 注释只列到 4</b>，proto 里的 DELETED(5) 没有落库路径：
 *       删除走的是 {@code gorm.DeletedAt} 软删除（{@link #deletedAt}），
 *       不是把 status 置 5。照搬 DDL 注释，别被它误导。</li>
 *   <li>{@link #extInfo} 是全部同步状态机的载体（publish_stage / reco_sync_state /
 *       review_nonce / published_once …），键名见 {@code mq/ArticleSyncEventTypes}。
 *       它是 <b>string→string</b> 的扁平 map，版本号等数值也<b>存成字符串</b>
 *       （Go 侧 {@code strconv.FormatInt}），Java 侧保持 String，不要改成 Long。</li>
 * </ul>
 *
 * <p>对应 Go：{@code Article}（该类型没有 {@code TableName()} 方法，
 * 靠 {@code NamingStrategy{SingularTable: true}} 得到表名 {@code article}）。
 */
@TableName(value = "article", autoResultMap = true)
public class ArticleEntity {

    /** 对应 Go {@code ID string `gorm:"primaryKey;type:varchar(32)"`}。 */
    @TableId(type = IdType.INPUT)
    private String id;

    /** 对应 Go {@code Title string `gorm:"type:varchar(255);not null"`}。 */
    private String title;

    /** 对应 Go {@code Brief string `gorm:"type:varchar(512)"`}。 */
    private String brief;

    /**
     * 对应 Go {@code Content string `gorm:"type:text"`}。
     *
     * <p><b>存的是 MinIO 对象 key，不是 Markdown 正文</b>（见类级说明）。
     * ⚠️ Go 的列注释写「Markdown正文」，但 create/update logic 写进去的是
     * {@code objectName}（{@code articles/content/<id>.md}），取值也一律走 MinIO。
     */
    private String content;

    /** 对应 Go {@code CoverImageURL string `gorm:"type:varchar(255)"`}。 */
    private String coverImageUrl;

    /** 对应 Go {@code ManualTypeTag string `gorm:"type:varchar(64);index"`}。 */
    private String manualTypeTag;

    /**
     * 对应 Go {@code SecondaryTags StringArray `gorm:"type:jsonb"`}。
     *
     * <p>⚠️ Go 的 {@code StringArray.Scan} 在 {@code value == nil} 时置 <b>nil</b>（不是空切片）。
     * 本实现同样保持 null，这样 {@code list_articles_logic.go:184} 的
     * {@code len(in.SecondaryTags) > 0} 与之保持同一语义。
     */
    @TableField(typeHandler = JsonbTypeHandler.StringListTypeHandler.class)
    private List<String> secondaryTags;

    /** 对应 Go {@code AuthorID string `gorm:"type:varchar(32);index"`}（注意是 string 不是 int64）。 */
    private String authorId;

    /**
     * 对应 Go {@code Status int32 `gorm:"type:smallint;default:0"`}。
     *
     * <p>取值 = proto {@code ArticleStatus} 的枚举数值。用 primitive int 而非包装类型，
     * 与 Go 的 {@code int32} 非指针语义一致（proto 的 {@code optional ArticleStatus}
     * 是否"设置过"由 <b>请求对象</b>表达，不落在实体上）。
     */
    private int status;

    /** 对应 Go {@code ViewCount int32 `gorm:"default:0"`}。 */
    private int viewCount;

    /** 对应 Go {@code LikeCount int32 `gorm:"default:0"`}。 */
    private int likeCount;

    /** 对应 Go {@code CommentCount int32 `gorm:"default:0"`}。 */
    private int commentCount;

    /** 对应 Go {@code ShareCount int32 `gorm:"default:0"`}。 */
    private int shareCount;

    /**
     * 对应 Go {@code ExtInfo JSONMap `gorm:"type:jsonb"`}。
     *
     * <p>⚠️ <b>值一律是 String</b>：版本号 {@code last_sync_version_ms} 在 Go 侧是
     * {@code strconv.FormatInt(versionMs, 10)} 存成字符串的，
     * {@code published_once} 是 {@code "true"} 字符串而非布尔。
     * 把这个字段声明成 {@code Map<String, Object>} 会让「比较
     * {@code ExtLastSyncVersion != strconv.FormatInt(...)}」这类判据失效。
     */
    @TableField(typeHandler = JsonbTypeHandler.StringMapTypeHandler.class)
    private Map<String, String> extInfo;

    /**
     * 对应 Go {@code CreatedAt time.Time `gorm:"autoCreateTime"`}。
     *
     * <p>用 {@link OffsetDateTime} 而非 LocalDateTime：本域三张表的时间列都是
     * <b>timestamptz</b>（D-1），LocalDateTime 丢时区信息。
     * 响应里要写 {@code article.CreatedAt.UnixMilli()}（Go 侧是绝对时刻），
     * 用 OffsetDateTime 才能一字对应。
     */
    private OffsetDateTime createdAt;

    /** 对应 Go {@code UpdatedAt time.Time `gorm:"autoUpdateTime"`}。 */
    private OffsetDateTime updatedAt;

    /**
     * 对应 Go {@code DeletedAt gorm.DeletedAt `gorm:"index"`}（软删除）。
     *
     * <p>⚠️ <b>关键行为点</b>：GORM 的软删除让默认查询自动带 {@code deleted_at IS NULL}，
     * 而 {@code Unscoped()} 才看得到已删除行。Go 侧有两个显式入口：
     * <ul>
     *   <li>{@code FindOne}（默认）—— 已删文章查不到 ⇒ {@code DeleteArticleLogic} 二次删除
     *       返回 NotFound，{@code GetArticle} 也返回 NotFound（Go 测试
     *       {@code community_article_test.go:303-305} 断言"旧路由不再返回已删文章"）。</li>
     *   <li>{@code FindOneUnscoped} —— <b>只有</b> {@code ArticleSyncResultConsumer}
     *       用它，让迟到的同步结果仍能找到已删文章（{@code handleDeleteResult}）。</li>
     * </ul>
     * Java 侧不引入 MyBatis-Plus 的 {@code @TableLogic}（那会让"哪些查询要 Unscoped"
     * 变成全局开关），而是<b>在 Mapper 上把两种语义写成两个明确方法</b>——
     * 与 Go 的方法划分一致，也让"哪条路径能看到已删行"可被 review。
     */
    private OffsetDateTime deletedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBrief() {
        return brief;
    }

    public void setBrief(String brief) {
        this.brief = brief;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getCoverImageUrl() {
        return coverImageUrl;
    }

    public void setCoverImageUrl(String coverImageUrl) {
        this.coverImageUrl = coverImageUrl;
    }

    public String getManualTypeTag() {
        return manualTypeTag;
    }

    public void setManualTypeTag(String manualTypeTag) {
        this.manualTypeTag = manualTypeTag;
    }

    public List<String> getSecondaryTags() {
        return secondaryTags;
    }

    public void setSecondaryTags(List<String> secondaryTags) {
        this.secondaryTags = secondaryTags;
    }

    public String getAuthorId() {
        return authorId;
    }

    public void setAuthorId(String authorId) {
        this.authorId = authorId;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public int getViewCount() {
        return viewCount;
    }

    public void setViewCount(int viewCount) {
        this.viewCount = viewCount;
    }

    public int getLikeCount() {
        return likeCount;
    }

    public void setLikeCount(int likeCount) {
        this.likeCount = likeCount;
    }

    public int getCommentCount() {
        return commentCount;
    }

    public void setCommentCount(int commentCount) {
        this.commentCount = commentCount;
    }

    public int getShareCount() {
        return shareCount;
    }

    public void setShareCount(int shareCount) {
        this.shareCount = shareCount;
    }

    public Map<String, String> getExtInfo() {
        return extInfo;
    }

    public void setExtInfo(Map<String, String> extInfo) {
        this.extInfo = extInfo;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(OffsetDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }
}