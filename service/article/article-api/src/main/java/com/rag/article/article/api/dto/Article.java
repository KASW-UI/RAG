package com.rag.article.article.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;
import java.util.Map;

/**
 * 文章响应投影 —— 对应 Go {@code service/article/api/internal/types/types.go:6-24} 的 {@code Article}。
 *
 * <pre>
 * Go:
 *   type Article struct {
 *       Id            string            `json:"id"`
 *       Title         string            `json:"title"`
 *       Brief         string            `json:"brief"`
 *       Content       string            `json:"content"`
 *       CoverImageUrl string            `json:"cover_image_url"`
 *       ManualTypeTag string            `json:"manual_type_tag"`
 *       SecondaryTags []string          `json:"secondary_tags"`
 *       AuthorId      string            `json:"author_id"`
 *       AuthorName    string            `json:"author_name,omitempty"`
 *       CreateTime    int64             `json:"create_time"`
 *       UpdateTime    int64             `json:"update_time"`
 *       Status        int32             `json:"status"`
 *       ViewCount     int32             `json:"view_count"`
 *       LikeCount     int32             `json:"like_count"`
 *       CommentCount  int32             `json:"comment_count"`
 *       ShareCount    int32             `json:"share_count"`
 *       ExtInfo       map[string]string `json:"ext_info"`
 *   }
 * </pre>
 *
 * <h2>三个 omitempty / nil 相关的字段是<b>字节级契约</b>，不能顺手统一</h2>
 * <table border="1">
 *   <caption>「缺席」与「null」在 Go JSON 里的区别</caption>
 *   <tr><th>字段</th><th>Go tag</th><th>零值时 Go 输出</th><th>本类</th></tr>
 *   <tr>
 *     <td>{@code author_name}</td>
 *     <td><b>{@code ,omitempty}</b></td>
 *     <td><b>整个 key 消失</b></td>
 *     <td>{@link JsonInclude} NON_EMPTY</td>
 *   </tr>
 *   <tr>
 *     <td>{@code secondary_tags}</td>
 *     <td>无 omitempty</td>
 *     <td>{@code "secondary_tags":null}（nil slice）</td>
 *     <td>不设默认值，保持 null</td>
 *   </tr>
 *   <tr>
 *     <td>{@code ext_info}</td>
 *     <td>无 omitempty</td>
 *     <td>{@code "ext_info":null}（nil map，见 {@code cloneStringMap}）</td>
 *     <td>不设默认值，保持 null</td>
 *   </tr>
 * </table>
 * ⇒ <b>只有 {@code author_name} 用 {@code @JsonInclude(NON_EMPTY)}，
 * 另外两个必须保持 null。</b>
 * 把三者一起「优化」成缺席，客户端读 {@code article.ext_info} 时会拿到 undefined 而不是 null。
 *
 * <p>⚠️ {@code secondary_tags} 之所以容易写错：proto 侧是 {@code repeated string}，
 * Java protobuf 的 {@code getSecondaryTagsList()} <b>永远返回非 null 的空 List</b>。
 * 若在映射处直接 {@code setSecondaryTags(article.getSecondaryTagsList())}，
 * 空文章就会输出 {@code "secondary_tags":[]} 而不是 Go 的 {@code null}。
 * 转换点在 {@code ArticleApiSupport#toArticle}，那里做了显式处理。
 *
 * <h2>🔴 字段顺序是<b>字节级契约</b>（2026-10-29 · G3 阶段 1 · A-1 双侧活体实测）</h2>
 * <p>Go 的 {@code encoding/json} 对 struct <b>按字段声明序</b>输出；
 * Jackson 默认按<b>字母序</b>（{@code MapperFeature.SORT_PROPERTIES_ALPHABETICALLY} 的历史行为）⇒
 * ⇒ 两侧<b>字段与值完全相同</b>，但<b>字节不同</b>。
 * <p>⇒ 实测（两侧同时起服务、同一行数据）：
 * <pre>
 * Go : {"id":…,"title":…,"brief":…,…,"ext_info":{…}}
 * RAG: {"author_id":…,"author_name":…,"brief":…,…,"view_count":1}
 * </pre>
 * ⇒ 与 hot 域 <b>D15</b>（{@code docs/DRIFT/hot/api.md}）<b>同型</b> ⇒
 * ⇒ 按 hot 域的既定口径（{@code @JsonPropertyOrder} 固定声明序 ⇒ 双侧活体逐字节相同
 * ⇒ <b>不需要</b>登记为有意偏离）。
 * <p>⇒ 守门：{@code ArticleApiJsonFieldOrderTest}（<b>去掉注解即变红</b>）。
 *
 * <h2>字段名大小写</h2>
 * <p>本仓库<b>没有</b>全局 snake_case 命名策略（已实测：全仓无
 * {@code property-naming-strategy} 配置），所以 snake_case 一律显式
 * {@link JsonProperty}，与 message-api 的 DTO 约定一致。
 */
@JsonPropertyOrder({
        "id", "title", "brief", "content", "cover_image_url", "manual_type_tag",
        "secondary_tags", "author_id", "author_name", "create_time", "update_time",
        "status", "view_count", "like_count", "comment_count", "share_count", "ext_info"
})
public class Article {

    @JsonProperty("id")
    private String id;

    private String title;

    private String brief;

    private String content;

    @JsonProperty("cover_image_url")
    private String coverImageUrl;

    @JsonProperty("manual_type_tag")
    private String manualTypeTag;

    @JsonProperty("secondary_tags")
    private List<String> secondaryTags;

    @JsonProperty("author_id")
    private String authorId;

    /** Go {@code ,omitempty} ⇒ 空串时整个 key 消失。 */
    @JsonProperty("author_name")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private String authorName;

    @JsonProperty("create_time")
    private long createTime;

    @JsonProperty("update_time")
    private long updateTime;

    private int status;

    @JsonProperty("view_count")
    private int viewCount;

    @JsonProperty("like_count")
    private int likeCount;

    @JsonProperty("comment_count")
    private int commentCount;

    @JsonProperty("share_count")
    private int shareCount;

    @JsonProperty("ext_info")
    private Map<String, String> extInfo;

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

    public String getAuthorName() {
        return authorName;
    }

    public void setAuthorName(String authorName) {
        this.authorName = authorName;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    public long getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(long updateTime) {
        this.updateTime = updateTime;
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
}