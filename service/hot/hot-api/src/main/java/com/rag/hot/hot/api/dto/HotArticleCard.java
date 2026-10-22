package com.rag.hot.hot.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 热门文章卡片的 <b>HTTP 层投影</b> —— 对应 Go {@code api/internal/types/types.go:6-20} 的
 * {@code HotArticleItem}。
 *
 * <pre>
 * Go:
 *   type HotArticleItem struct {
 *       ArticleId     string   `json:"article_id"`
 *       Rank          int32    `json:"rank"`
 *       HotScore      uint32   `json:"hot_score"`
 *       Title         string   `json:"title"`
 *       Brief         string   `json:"brief"`
 *       CoverImageUrl string   `json:"cover_image_url"`
 *       AuthorId      string   `json:"author_id"`
 *       CreateTime    int64    `json:"create_time"`
 *       ViewCount     int32    `json:"view_count"`
 *       LikeCount     int32    `json:"like_count"`
 *       CommentCount  int32    `json:"comment_count"`
 *       ManualTypeTag string   `json:"manual_type_tag"`
 *       SecondaryTags []string `json:"secondary_tags"`
 *   }
 * </pre>
 *
 * <h2>为什么类名不叫 {@code HotArticleItem}</h2>
 * {@code com.rag.hot.hot.proto.HotArticleItem}（由 {@code hot.proto:15-18} 生成）<b>同名但只有
 * 2 个字段</b>（{@code article_id} / {@code hot_score}）。本类 13 个字段里有 <b>11 个是 api 层
 * 补全的</b>（全部来自 article-rpc 的公开投影）。两者不是同一个形状，叫同一个名字会诱导下游
 * 把二者当成「同一个东西的两种投影」——那正是 Go 侧最容易读错的地方。
 *
 * <h2>字段取值的来源分工（L2，不可互换）</h2>
 * <pre>
 *   hot_score  ← hot-rpc（热度分，article 侧没有这个概念）
 *   其余 11 个  ← article-rpc 的公开投影（public_only=true 的 frozen revision）
 *   article_id ← article.GetId()，**不是** hot-rpc 返回的 hotItem.GetArticleId()
 *                 （Go getHotArticlesLogic.go:157；两者通常是同一个值，但契约上取的是 article 侧）
 * </pre>
 *
 * <h2>⚠️ 两处「Go 的值与 Java 默认值不同」的字段</h2>
 *
 * <h3>1. {@code secondary_tags}：Go 无值时是 {@code null}，不是 {@code []}</h3>
 * Go tag <b>没有</b> {@code ,omitempty}，而 {@code article.GetSecondaryTags()} 对
 * proto3 repeated 字段在 wire 上没有元素时返回 <b>nil slice</b>
 * （protobuf-go 的 unmarshal 对 0 元素不做 append，gRPC 的 codec 又是逐条新建 message）
 * ⇒ Go 的 JSON 输出是 {@code "secondary_tags":null}。
 * Java 的 {@code Article#getSecondaryTagsList()} <b>永远返回非 null 列表</b>
 * （空列表时是 {@code ProtocolStringList.EMPTY_LIST}），直接塞进去会输出 {@code []} ——
 * <b>字节级差异</b>。因此构造处显式转换：空 ⇒ null。
 *
 * <h3>2. {@code hot_score}：Go 是 {@code uint32}，Java 侧 proto 生成的是有符号 {@code int}</h3>
 * proto 字段是 {@code uint32 hot_score = 2}（{@code hot.proto:17}），protobuf-java 把
 * {@code uint32} 映射成 Java 的 {@code int}（补码位模式相同，取值域 −2^31..2^31−1）。
 * 直接输出会在 score ≥ 2^31 时写成负数，而 Go 的 {@code encoding/json} 对
 * {@code uint32} 写的是<b>无符号十进制</b>。
 * ⇒ 本字段声明为 {@code long}，构造处用 {@link Integer#toUnsignedLong(int)} 还原无符号值，
 * JSON 输出对 <b>全部</b> uint32 取值都与 Go 逐字节相同。
 * （热度分实际来自 HeavyKeeper 的衰减估计，量级远小于 2^31，这里做的是「不依赖取值假设」的写法。）
 *
 * <h2>{@code rank} 的含义（L2，最容易被写错的一条）</h2>
 * 它是<b>过滤之后</b>的稠密序号，且<b>跨页连续</b>：第 2 页第一条是 {@code rank=2}，
 * 而不是 1（Go {@code getHotArticlesLogic.go:119} 的 {@code item.Rank = int32(len(result) + 1)}，
 * 在分页<b>之前</b>重算）。对照 Go 测试
 * {@code getHotArticlesLogic_test.go:135-147}（第 2 页首条断言 {@code Rank != 2} 即失败）。
 */
public class HotArticleCard {

    @JsonProperty("article_id")
    private String articleId;

    @JsonProperty("rank")
    private int rank;

    @JsonProperty("hot_score")
    private long hotScore;

    @JsonProperty("title")
    private String title;

    @JsonProperty("brief")
    private String brief;

    @JsonProperty("cover_image_url")
    private String coverImageUrl;

    @JsonProperty("author_id")
    private String authorId;

    @JsonProperty("create_time")
    private long createTime;

    @JsonProperty("view_count")
    private int viewCount;

    @JsonProperty("like_count")
    private int likeCount;

    @JsonProperty("comment_count")
    private int commentCount;

    @JsonProperty("manual_type_tag")
    private String manualTypeTag;

    @JsonProperty("secondary_tags")
    private List<String> secondaryTags;

    public HotArticleCard() {
    }

    public String getArticleId() {
        return articleId;
    }

    public void setArticleId(String articleId) {
        this.articleId = articleId;
    }

    public int getRank() {
        return rank;
    }

    public void setRank(int rank) {
        this.rank = rank;
    }

    public long getHotScore() {
        return hotScore;
    }

    public void setHotScore(long hotScore) {
        this.hotScore = hotScore;
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

    public String getCoverImageUrl() {
        return coverImageUrl;
    }

    public void setCoverImageUrl(String coverImageUrl) {
        this.coverImageUrl = coverImageUrl;
    }

    public String getAuthorId() {
        return authorId;
    }

    public void setAuthorId(String authorId) {
        this.authorId = authorId;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
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
}