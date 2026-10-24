package com.rag.article.article.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 文章列表响应 —— 对应 Go {@code types.go:60-65} 的 {@code ListArticlesResp}。
 *
 * <pre>
 * Go:
 *   type ListArticlesResp struct {
 *       Articles []Article `json:"articles"`
 *       Total    int64     `json:"total"`
 *       Page     int32     `json:"page"`
 *       PageSize int32     `json:"page_size"`
 *   }
 * </pre>
 *
 * <h2>⚠️ {@code articles} 必须允许为 {@code null}</h2>
 *
 * <p>Go 侧 {@code list_articles_logic.go:62-69} 声明的是
 * <pre>var articles []types.Article   // nil slice</pre>
 * 并只对<b>通过公开状态校验</b>的行 {@code append}。
 * 当 rpc 返回空列表 ⇒ {@code articles} 仍是 nil ⇒ 输出 {@code "articles":null}。
 *
 * <p>本类<b>不</b>用 {@code new ArrayList<>()} 预初始化（message-api 的
 * {@code ConversationListResp} 那样做是因为 Go 侧那里是结构体零值非 nil slice，
 * <b>两个域的 Go 写法不同</b>）。预初始化会把 {@code null} 变成 {@code []}，
 * 客户端 {@code if (data.articles)} 在 JS 侧对 {@code []} 为真、对 {@code null} 为假。
 */
public class ListArticlesResp {

    private List<Article> articles;

    private long total;

    private int page;

    @JsonProperty("page_size")
    private int pageSize;

    public List<Article> getArticles() {
        return articles;
    }

    public void setArticles(List<Article> articles) {
        this.articles = articles;
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
}
