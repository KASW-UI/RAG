package com.rag.article.article.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.rag.users.common.http.GoStyleRequired;
import com.rag.users.common.http.GoStyleRequiredFields;

import java.util.List;

/**
 * 创建文章请求 —— 对应 Go {@code types.go:26-33} 的 {@code CreateArticleReq}。
 *
 * <pre>
 * Go:
 *   type CreateArticleReq struct {
 *       Title         string   `json:"title"`                  ← 必填
 *       Brief         string   `json:"brief,optional"`
 *       Content       string   `json:"content"`                ← 必填
 *       CoverImageUrl string   `json:"cover_image_url,optional"`
 *       ManualTypeTag string   `json:"manual_type_tag,optional"`
 *       SecondaryTags []string `json:"secondary_tags,optional"`
 *   }
 * </pre>
 *
 * <h2>「必填」的判定依据是 <b>tag 里有没有 {@code ,optional}</b>，不是「值是否为空」</h2>
 *
 * <p>go-zero 的 {@code core/mapping/utils.go:372} {@code parseOption} <b>只认三种</b>选项：
 * {@code inherit} / {@code string} / 以 {@code optional} 开头的。
 * <b>{@code omitempty} 不在其中</b>，它不设置任何 unmarshal 选项
 * ⇒ {@code Optional} 保持 {@code false} ⇒ 该字段<b>必填</b>。
 * ⇒ <b>不要把 {@code ,omitempty} 当成「可以缺省」</b>（AGENTS.md §DRIFT-MSGAPI-003 记的就是这个误解）。
 *
 * <h3>「缺席」与「显式 null」是两条不同的失败，文案不同（都是 HTTP 400）</h3>
 * <pre>
 *   缺席:     unmarshaler.go:835 → processNamedFieldWithoutValue → newInitError
 *             ⇒ fmt.Errorf("field %q is not set", fullName)
 *   显式 null: unmarshaler.go:856-862
 *             ⇒ fmt.Errorf("field %q mustn't be nil", key)
 *   类型不符:  unmarshaler.go:1202 newTypeMismatchError
 *             ⇒ fmt.Errorf("type mismatch for field %q", name)
 * </pre>
 * 两者都经 {@code httpx.ErrorCtx} 落到<b>同一个</b> 400 text/plain 响应，
 * 但<b>body 文案不同</b>，且是<b>客户端可见</b>的。Jackson 默认把「缺席」与
 * 「显式 null」都映射成 {@code null}，无法区分，因此本类用
 * {@link JsonProperty} setter 记录 presence，把两条路径分开。
 *
 * <p>⚠️ setter 形参刻意是 {@link Object} 而非 {@code String}：
 * <b>只有「不声明目标类型、让 Jackson 原样递出」才能区分 null 与缺席</b>。
 * 类型不符的情况<b>不会</b>走到这里 —— {@code GoStyleCoercion} 规则 1
 * （Textual + Integer/Float/Boolean ⇒ Fail）已经先把它变成
 * {@code HttpMessageNotReadableException} ⇒ 400 text/plain，
 * 与 Go 的 {@code type mismatch} 落在同一个响应形状上。
 *
 * <p><b>空串是合法值</b>：Go 侧 {@code processFieldPrimitive} 对 String 直接写入，
 * 只判 null 不判空串。所以 {@code {"title":""}} 会<b>成功</b> 并把空标题发给 article-rpc。
 * 标题是否为空由 rpc 判定，本层不重复校验。
 */
public class CreateArticleReq implements GoStyleRequiredFields {

    private String title;
    private String brief;
    private String content;
    private String coverImageUrl;
    private String manualTypeTag;
    private List<String> secondaryTags;

    private boolean titlePresent;
    private boolean contentPresent;

    @JsonProperty("title")
    public void setTitle(Object raw) {
        this.titlePresent = true;
        this.title = GoStyleStringFields.parse("title", raw);
    }

    @JsonProperty("brief")
    public void setBrief(Object raw) {
        this.brief = GoStyleStringFields.parse("brief", raw);
    }

    @JsonProperty("content")
    public void setContent(Object raw) {
        this.contentPresent = true;
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
     * Go {@code unmarshaler.go} 的两条必填失败路径，逐条对应。
     *
     * <p>⚠️ <b>顺序有意义</b>：先判「缺席」（{@code is not set}），
     * 再判「显式 null」（{@code mustn't be nil}）。反过来写会把
     * {@code {"title":null}} 报成 {@code is not set}。
     */
    @Override
    public void assertGoRequiredFields() {
        if (!titlePresent) {
            GoStyleRequired.field("title", null);
        }
        GoStyleRequired.explicitNull("title", titlePresent, title);

        if (!contentPresent) {
            GoStyleRequired.field("content", null);
        }
        GoStyleRequired.explicitNull("content", contentPresent, content);
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

    /**
     * ⚠️ 返回 <b>可能为 null</b>：Go 的 {@code SecondaryTags []string} 缺席时是 nil slice。
     * 调用点 {@code ArticleCreateService} 会把它原样透传给 proto 的
     * {@code addAllSecondaryTags}（空 list ⇒ proto 不写任何元素），
     * <b>不能</b>在这里替换成空 List —— 那会让「显式传空数组」与「完全不传」在 proto 层同形，
     * 而 Go 侧 rpc 是靠 {@code len(secondary_tags)==0} 判定的（两者本来都为空，语义无差，
     * 但保持 null 能让 DTO 的字面形状与 Go 一致，便于对照）。
     */
    public List<String> getSecondaryTags() {
        return secondaryTags;
    }
}