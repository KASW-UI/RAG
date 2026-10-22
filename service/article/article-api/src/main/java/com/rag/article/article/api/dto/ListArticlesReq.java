package com.rag.article.article.api.dto;

import com.rag.article.article.api.form.GoFormValues;

/**
 * 文章列表入参 —— 对应 Go {@code types.go:49-58} 的 {@code ListArticlesReq}。
 *
 * <pre>
 * Go:
 *   type ListArticlesReq struct {
 *       ManualTypeTag string `form:"manual_type_tag,optional"`
 *       SecondaryTag  string `form:"secondary_tag,optional"`
 *       RelatedGameId string `form:"related_game_id,optional"`
 *       AuthorId      string `form:"author_id,optional"`
 *       Page          int32  `form:"page,default=1"`
 *       PageSize      int32  `form:"page_size,default=20"`
 *       SortBy        string `form:"sort_by,default=create_time"`
 *       Desc          bool   `form:"desc,default=true"`
 *   }
 * </pre>
 *
 * <h2>四个 {@code ,default=} 是本类唯一的非平凡部分</h2>
 *
 * <p>Go 的 {@code processNamedFieldWithoutValue}：
 * <pre>
 *   if defaultValue, ok := opts.getDefault(); ok {          ← unmarshaler.go:919
 *       switch fieldKind {
 *       case reflect.Array, reflect.Slice: return u.fillSliceWithDefault(...)
 *       default: return setValueFromString(fieldKind, value, defaultValue)
 *       }
 *   }
 *   ...
 *   default: if !opts.optional() { return newInitError(fullName) }
 * </pre>
 * ⇒ <b>默认值只在「key 缺席」时填</b>；key 存在（哪怕值是空串以外的任意串）就按值解析，
 * 解析失败（如 {@code page=abc}）是 <b>400</b> 而<b>不是</b>回落默认值。
 *
 * <h2>⚠️ 空值 ≠ 缺席：{@code ?page=} 在 Go 里走「缺席」分支</h2>
 * 因为 {@code GetFormValues}（{@code httpx/util.go:44-45}）把空值整个丢掉。
 * {@code ?page=0} 则是<b>显式 0</b>，会原样传给 rpc（不分页、不回落成 1）。
 * 这一条由 {@link GoFormValues#raw} 承担，<b>不要</b>在 controller 里用
 * {@code @RequestParam(defaultValue="1")} 代替 —— Spring 的 defaultValue 对
 * {@code ?page=}（空串）<b>也会</b>生效，语义恰好一致；但对 {@code ?page=abc}
 * Spring 抛 400、Go 也抛 400，两边一致，所以真正的风险只在
 * 「用 String 接再自己判空」和「用 int 接让 Spring 转换」之间的选择。
 * 本类统一收 String，在<b>唯一</b>一处套 Go 规则。
 *
 * <h2>⚠️ 本类不做任何「值域」判断</h2>
 * {@code page=-1} / {@code page_size=99999} / {@code sort_by=未知列} 在 Go 侧
 * 一律<b>原样透传</b>给 article-rpc（{@code list_articles_logic.go} 判 sortBy 白名单、
 * 判 page 上界）。api 层提前拦会与 rpc 重复且可能不一致。
 */
public class ListArticlesReq {

    /** Go {@code form:"page,default=1"}。 */
    public static final int DEFAULT_PAGE = 1;
    /** Go {@code form:"page_size,default=20"}。 */
    public static final int DEFAULT_PAGE_SIZE = 20;
    /** Go {@code form:"sort_by,default=create_time"}。 */
    public static final String DEFAULT_SORT_BY = "create_time";
    /** Go {@code form:"desc,default=true"}。 */
    public static final boolean DEFAULT_DESC = true;

    private final String manualTypeTag;
    private final String secondaryTag;
    private final String relatedGameId;
    private final String authorId;
    private final int page;
    private final int pageSize;
    private final String sortBy;
    private final boolean desc;

    /**
     * @param rawXxx 全部是<b>原始 query string 值</b>（Spring {@code @RequestParam String}），
     *               可能为 {@code null}（缺席）或 {@code ""}（空值）。
     */
    public ListArticlesReq(String rawManualTypeTag,
                           String rawSecondaryTag,
                           String rawRelatedGameId,
                           String rawAuthorId,
                           String rawPage,
                           String rawPageSize,
                           String rawSortBy,
                           String rawDesc) {
        this.manualTypeTag = GoFormValues.text(rawManualTypeTag, "");
        this.secondaryTag = GoFormValues.text(rawSecondaryTag, "");
        this.relatedGameId = GoFormValues.text(rawRelatedGameId, "");
        this.authorId = GoFormValues.text(rawAuthorId, "");
        this.page = GoFormValues.int32(rawPage, "page", DEFAULT_PAGE);
        this.pageSize = GoFormValues.int32(rawPageSize, "page_size", DEFAULT_PAGE_SIZE);
        this.sortBy = GoFormValues.text(rawSortBy, DEFAULT_SORT_BY);
        this.desc = GoFormValues.bool(rawDesc, "desc", DEFAULT_DESC);
    }

    public String getManualTypeTag() {
        return manualTypeTag;
    }

    public String getSecondaryTag() {
        return secondaryTag;
    }

    public String getRelatedGameId() {
        return relatedGameId;
    }

    public String getAuthorId() {
        return authorId;
    }

    public int getPage() {
        return page;
    }

    public int getPageSize() {
        return pageSize;
    }

    public String getSortBy() {
        return sortBy;
    }

    public boolean isDesc() {
        return desc;
    }
}
