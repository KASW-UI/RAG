package com.rag.common.logger;

/**
 * 对应 Go 版 service/common/logger/logger.go 的 LogOption 函数选项类型：
 *
 * <pre>
 * type LogOption func(*LogOptions)
 * </pre>
 *
 * <p>Go 版用函数类型实现 Functional Options 模式；Java 21 没有"函数类型"，所以用 {@link FunctionalInterface} 显式标注。
 *
 * <p>框架职责：
 * <ul>
 *   <li>Go 版：函数是一等公民，可以直接传入</li>
 *   <li>Java 版：用 {@code @FunctionalInterface} 标记单方法接口，可被 lambda 表达式代替</li>
 * </ul>
 */
@FunctionalInterface
public interface LogOption {

    /**
     * 对应 Go 版 {@code func(opt *LogOptions) { opts.UserID = userID }} 这种闭包体。
     *
     * @param opts 选项容器（Go 版 *LogOptions → Java 的 LogOptions 实例引用）
     */
    void apply(LogOptions opts);
}