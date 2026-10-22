package com.rag.article.article.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 更新文章请求 —— 对应 Go {@code types.go:61-71} 的 {@code UpdateArticleReq}。
 *
 * <pre>
 * Go:
 *   type UpdateArticleReq struct {
 *       ArticleId     string   `path:"id"`                       ← 来自 URL，不是 JSON
 *       Title         string   `json:"title,optional"`
 *       Brief         string   `json:"brief,optional"`
 *       Content       string   `json:"content,optional"`
 *       CoverImageUrl string   `json:"cover_image_url,optional"`
 *       ManualTypeTag string   `json:"manual_type_tag,optional"`
 *       SecondaryTags []string `json:"secondary_tags,optional"`
 *       Status        int32    `json:"status,optional"`
 *   }
 * </pre>
 *
 * <h2>七个 JSON 字段<b>全部</b>是 {@code ,optional} ⇒ 本类不做任何必填校验</h2>
 *
 * <p>因此本类<b>不</b>实现 {@code GoStyleRequiredFields}：Go 侧
 * {@code processNamedFieldWithoutValue} 的 {@code default:} 分支
 * （{@code if !opts.optional() { return newInitError } }）永远不触发。
 * 加一个「title 不能为空」的校验会是<b>凭空新增的拒绝路径</b> ——
 * 而且是本项目最容易被「好心」加上去的那种。
 *
 * <h2>⚠️ 「optional」只管解析，管不了<b>空串</b>怎么用</h2>
 * <p>「字段可缺席」与「非空值才更新」是两件事，后者在
 * {@code logic/article/update_article_logic.go:74-92}：
 * <pre>
 *   if req.Title != "" { rpcReq.Title = &amp;req.Title }        ← 非空才设置 proto 的 optional 字段
 *   ...
 *   if req.Status != 0 { rpcReq.Status = status.Enum() }       ← 非 0 才设置
 * </pre>
 * ⇒ <b>本 DTO 不做「空 ⇒ 缺席」的转换</b>，那个判断属于业务语义，
 * 留在 {@code ArticleUpdateService}。放错层的代价是「无法表达
 * 把标题改成空串」与「不修改标题」的差别。
 *
 * <h2>{@code articleId} 刻意<b>不在</b>本类里</h2>
 *
 * <p>Go 侧它带的是 {@code path:} tag 而非 {@code json:}，语义上就是 URL 段。
 * 若把它留在 DTO 里，就必须靠「不写 @JsonProperty」让 Jackson 拒绝绑定 ——
 * 而 Jackson 默认会按<b>字段名</b> {@code articleId} / {@code ArticleId} 绑定它，
 * 正好落进 Go 侧那条「无 json tag ⇒ 回落到字段名」的意外路径。
 * 把它交给 controller 的 {@code @PathVariable} 参数，从类型上就断掉了这条路径。
 */
public class UpdateArticleReq {

    private String title;
    private String brief;
    private String content;
    private String coverImageUrl;
    private String manualTypeTag;
    private List<String> secondaryTags;
    private Integer status;

    @JsonProperty("title")
    public void setTitle(Object raw) {
        this.title = GoStyleStringFields.parse("title", raw);
    }

    @JsonProperty("brief")
    public void setBrief(Object raw) {
        this.brief = GoStyleStringFields.parse("brief", raw);
    }

    @JsonProperty("content")
    public void setContent(Object raw) {
        this.content = GoStyleStringFields.parse("content", raw);
    }

    @JsonProperty("cover_image_url")
    public void setCoverImageUrl(Object raw) {
        this.coverImageUrl = GoStyleStringFields.parse("cover_image_url", raw);
    }

    @JsonProperty("manual_type_tag")
    public void setManualTypeTag(Object raw) {
        this.manualTypeTag = GoStyleStringFields.parse("manual_type_tag", raw);
    }

    @JsonProperty("secondary_tags")
    public void setSecondaryTags(List<String> secondaryTags) {
        this.secondaryTags = secondaryTags;
    }

    /**
     * ⚠️ 用 {@link Integer} 而非 {@code int}：Go 侧是 {@code int32}，
     * 「缺席」与「0」在 proto 侧要分开表达（{@code optional ArticleStatus status}）。
     * 用 {@code int} 会把两者压成同一个 0。
     * <p>⚠️ JSON 数字才能通过；{@code {"status":"2"}} 会被
     * {@code GoStyleCoercion} 规则 2（Integer ⇒ 整数字段 Fail）拒成 400，
     * 与 Go 的 {@code type mismatch} 同形。
     */
    @JsonProperty("status")
    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getTitle() {
        return title;
    }

    public String getBrief() {
        return brief;
    }

    public String getContent() {
        return content;
    }

    public String getCoverImageUrl() {
        return coverImageUrl;
    }

    public String getManualTypeTag() {
        return manualTypeTag;
    }

    public List<String> getSecondaryTags() {
        return secondaryTags;
    }

    /** 缺席时为 {@code null}；显式 {@code 0} 是与缺席<b>不同</b>的值（Go 侧 {@code != 0} 判定）。 */
    public Integer getStatus() {
        return status;
    }
}