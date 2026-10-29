package com.rag.common.logger;

/**
 * <p><b>Go 出处</b>：{@code service/common/logger/logger.go:35-39}
 * （{@code func WithUserID(userID string) LogOption} ＋ 闭包体 {@code func(opts *LogOptions) { opts.UserID = userID }}`）
 * ⇒ <b>语义等价</b>：Go 返回闭包 / Java 返回 {@link LogOption} 实现 ⇒ 调用点写法不同、行为一致。
 * 对应 Go 版 service/common/logger/logger.go 的 WithUserID 函数：
 *
 * <pre>
 * func WithUserID(userID string) LogOption {
 *     return func(opts *LogOptions) { opts.UserID = userID }
 * }
 * </pre>
 *
 * <p>Go 版返回的是个 LogOption 闭包；Java 版用具名 {@link LogOption} 实现，更易在 IDE 里跳转 / 单测 mock。
 * 因为 {@link LogOption} 是 {@link FunctionalInterface}，调用方依然可以用 lambda 写法。
 *
 * <p>框架职责：
 * <ul>
 *   <li>Go 版函数是一等公民，直接返回闭包</li>
 *   <li>Java 版用静态工厂方法返回一个 {@link LogOption} 实例（lambda 实现）</li>
 * </ul>
 */
public final class WithUserID implements LogOption {

    private final String userId;

    private WithUserID(String userId) {
        this.userId = userId;
    }

    /**
     * 对应 Go 版 {@code func WithUserID(userID string) LogOption} 签名。
     */
    public static WithUserID of(String userId) {
        return new WithUserID(userId);
    }

    /**
     * 对应 Go 版闭包体 {@code func(opts *LogOptions) { opts.UserID = userID }}。
     */
    @Override
    public void apply(LogOptions opts) {
        opts.setUserId(userId);
    }

    /**
     * 对应 Go 版字段 {@code UserID string}。
     */
    public String getUserId() {
        return userId;
    }
}