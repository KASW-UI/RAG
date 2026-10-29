package com.rag.common.logger;

/**
 * <p><b>Go 出处</b>：{@code service/common/logger/logger.go:42-46}
 * （{@code func WithArticleID(articleID string) LogOption} ＋ 闭包体 {@code func(opts *LogOptions) { opts.ArticleID = articleID }}`）
 * ⇒ <b>语义等价</b>（同 {@link WithUserID}）。
 * 对应 Go 版 service/common/logger/logger.go 的 WithArticleID 函数：
 *
 * <pre>
 * func WithArticleID(articleID string) LogOption {
 *     return func(opts *LogOptions) { opts.ArticleID = articleID }
 * }
 * </pre>
 *
 * <p>同 {@link WithUserID}，把 Go 的闭包包装成具名 {@link LogOption}，同时保留 {@link FunctionalInterface} 兼容 lambda 写法。
 */
public final class WithArticleID implements LogOption {

    private final String articleId;

    private WithArticleID(String articleId) {
        this.articleId = articleId;
    }

    /**
     * 对应 Go 版 {@code func WithArticleID(articleID string) LogOption} 签名。
     */
    public static WithArticleID of(String articleId) {
        return new WithArticleID(articleId);
    }

    /**
     * 对应 Go 版闭包体 {@code func(opts *LogOptions) { opts.ArticleID = articleID }}。
     */
    @Override
    public void apply(LogOptions opts) {
        opts.setArticleId(articleId);
    }

    /**
     * 对应 Go 版字段 {@code ArticleID string}。
     */
    public String getArticleId() {
        return articleId;
    }
}