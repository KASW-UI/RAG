package com.rag.hot.hot.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

/**
 * 热门文章列表响应 —— 对应 Go {@code api/internal/types/types.go:27-33}：
 *
 * <pre>
 *   type HotArticlesResp struct {
 *       Items    []HotArticleItem `json:"items"`
 *       Total    int64            `json:"total"`
 *       Page     int32            `json:"page"`
 *       PageSize int32            `json:"page_size"`
 *       Scope    string           `json:"scope"`
 *   }
 * </pre>
 *
 * <h2>⚠️ 字段顺序必须显式钉住（字节级 L1）</h2>
 * Go 的 {@code encoding/json} 按结构体声明序输出（{@code types.go:27-33}）：
 * {@code items, total, page, page_size, scope}。
 * ⇒ {@link JsonPropertyOrder} 显式钉死：注解优先于
 * {@code MapperFeature.SORT_PROPERTIES_ALPHABETICALLY} 这类全局特性，
 * 无论运行时 mapper 怎么配，输出都等于 Go 的声明序。依据见
 * {@link HotArticleCard} 的同名小节。
 *
 * <h2>⚠️ {@code items} 必须是空列表而不是 null（L1 字节级）</h2>
 * Go 侧 {@code getHotArticlesLogic.go:80} 是 {@code pagedItems := items[start:end]}，
 * 而 {@code items} 由 {@code make([]*types.HotArticleItem, len(hotItems))} 无条件创建
 * ⇒ 即使 {@code start==end==0}（空页 / 越界页），切出来的也是一个 <b>非 nil 的空切片</b>
 * ⇒ JSON 输出 {@code "items":[]}。
 * 任何返回 {@code null} 的路径都会产出 {@code "items":null}，客户端按 {@code items.length}
 * 遍历时行为不同。构造处因此无条件给空列表。
 *
 * <h2>⚠️ {@code total} 不是「全站热门文章总数」</h2>
 * Go {@code getHotArticlesLogic.go:78}：{@code total := int64(len(items))}，
 * 这里的 {@code items} 是<b>补全 + 过滤之后</b>的列表，长度 ≤ {@code maxHotArticles(100)}。
 * ⇒ 它是「<b>本次滚动窗口内可公开展示的条目数</b>」，不是历史累计值、也不随分页参数变化。
 * 这一点决定了客户端不能用 {@code total} 做「一共有多少篇」的推断。
 *
 * <h2>⚠️ {@code scope} 是编译期常量（不是从下游读的）</h2>
 * Go {@code getHotArticlesLogic.go:87} 直接写 {@code Scope: rollingScope}，
 * 常量定义在同文件 {@code :30}。即 hot-rpc 当前<b>不返回</b>窗口类型，本字段恒为
 * {@code "rolling"}。将来若下游区分「滚动 / 日榜」，改动点在这里。
 */
@JsonPropertyOrder({"items", "total", "page", "page_size", "scope"})
public class HotArticlesResp {

    @JsonProperty("items")
    private List<HotArticleCard> items;

    @JsonProperty("total")
    private long total;

    @JsonProperty("page")
    private int page;

    @JsonProperty("page_size")
    private int pageSize;

    @JsonProperty("scope")
    private String scope;

    public HotArticlesResp() {
    }

    public HotArticlesResp(List<HotArticleCard> items, long total, int page, int pageSize, String scope) {
        this.items = items;
        this.total = total;
        this.page = page;
        this.pageSize = pageSize;
        this.scope = scope;
    }

    public List<HotArticleCard> getItems() {
        return items;
    }

    public void setItems(List<HotArticleCard> items) {
        this.items = items;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }
}