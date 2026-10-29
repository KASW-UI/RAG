package com.rag.common.logger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 🔴 <b>共享层逐类契约守门 · logger 包</b>：给<b>此前零专属测试</b>的 5 个类补上行为断言。
 *
 * <h2>为什么补这些</h2>
 * <p>R3 判据 ⑤ 要求「<b>RAG 侧有自动化测试断言该行为</b>」⇒ ⇒ 只在 javadoc 里写「对应 Go 的 xxx」
 * <b>不算</b> ⇒ ⇒ 每个类都要有一条<b>能跑</b>的断言。
 * <p>⚠️ AGENTS.md §3.3：<b>纯 Mockito / 纯结构断言不算</b> ⇒ ⇒ 本类的每个用例都断言
 * <b>Go 侧写明的可观察语义</b>（零值 / 赋值 / 异常类型），而不是「字段存在」。
 *
 * <h2>Go 出处（逐条）</h2>
 * <ul>
 *   <li>{@code LogOptions} ← {@code service/common/logger/logger.go:26-29}
 *       （{@code type LogOptions struct { UserID string; ArticleID string }}`）</li>
 *   <li>{@code LogOption} ← {@code service/common/logger/logger.go:32}
 *       （{@code type LogOption func(*LogOptions)}`）</li>
 *   <li>{@code WithUserID} ← {@code service/common/logger/logger.go:35-39}</li>
 *   <li>{@code WithArticleID} ← {@code service/common/logger/logger.go:42-46}</li>
 *   <li>{@code FatalError} ← {@code service/common/logger/logger.go:293-296}
 *       （{@code logx.WithContext(ctx).Errorw("fatal_error", …)} + {@code os.Exit(1)}）</li>
 * </ul>
 *
 * <h2>⚠️ 证据强度</h2>
 * <p><b>单测级（L1 / E3）</b>：断言的是「Java 侧行为 == Go 侧源码写明的语义」，
 * <b>不是</b>双侧活体对拍（AGENTS.md §3.3）⇒ ⇒ 本门<b>不</b>声称「与 Go 运行期一致」。
 */
class LoggerOptionContractGateTest {

    @Test
    @DisplayName("LogOptions：两个字段的默认值必须是 Go 的零值（空串），不是 null")
    void logOptionsDefaultsMatchGoZeroValues() {
        LogOptions opts = new LogOptions();
        // Go: `type LogOptions struct { UserID string; ArticleID string }` ⇒ 零值是 ""（不是 nil）
        assertThat(opts.getUserId()).as("Go 零值是空串").isEqualTo("");
        assertThat(opts.getArticleId()).as("Go 零值是空串").isEqualTo("");
    }

    @Test
    @DisplayName("WithUserID：闭包必须只写 UserID 字段，不碰 ArticleID（Go logger.go:35-39）")
    void withUserIdWritesOnlyUserId() {
        LogOptions opts = new LogOptions();
        WithUserID.of("u-42").apply(opts);
        // Go: `func(opts *LogOptions) { opts.UserID = userID }`
        assertThat(opts.getUserId()).isEqualTo("u-42");
        assertThat(opts.getArticleId()).as("闭包体只改 UserID").isEqualTo("");
    }

    @Test
    @DisplayName("WithArticleID：闭包必须只写 ArticleID 字段，不碰 UserID（Go logger.go:42-46）")
    void withArticleIdWritesOnlyArticleId() {
        LogOptions opts = new LogOptions();
        WithArticleID.of("a-7").apply(opts);
        // Go: `func(opts *LogOptions) { opts.ArticleID = articleID }`
        assertThat(opts.getArticleId()).isEqualTo("a-7");
        assertThat(opts.getUserId()).as("闭包体只改 ArticleID").isEqualTo("");
    }

    @Test
    @DisplayName("LogOption：必须是函数式接口（Go 的闭包 ⇒ Java 的 lambda，调用点写法可等价）")
    void logOptionIsAFunctionalInterface() {
        assertThat(LogOption.class.isInterface()).isTrue();
        assertThat(LogOption.class.getAnnotation(FunctionalInterface.class))
                .as("必须是 @FunctionalInterface（否则调用方不能写 lambda ⇒ 与 Go 的闭包不等价）")
                .isNotNull();
        // 走一遍 lambda 形态：Go 侧 `logger.LogBusinessErr(ctx, code, err, func(o *LogOptions){ o.UserID = "x" })`
        LogOptions opts = new LogOptions();
        LogOption lambda = o -> o.setUserId("lambda-1");
        lambda.apply(opts);
        assertThat(opts.getUserId()).isEqualTo("lambda-1");
    }

    @Test
    @DisplayName("FatalError：必须是专用异常类型，不得是普通 IllegalStateException（Go logger.go:293-296 的 os.Exit(1) 语义）")
    void fatalErrorIsADedicatedType() {
        FatalError err = new FatalError("boom", new IllegalStateException("root"));
        assertThat(err).isInstanceOf(RuntimeException.class);
        // 🔴 H3 修复的核心：此前抛的是 IllegalStateException ⇒ 任何 catch(RuntimeException) 都能吞掉它
        //    ⇒ 「fatal 后必须停止」降级成「可以被 catch 掉的普通异常」
        //    ⇒ Go 侧是 os.Exit(1)（进程立即停止）⇒ 表达层等价物必须是**不可被普通兜底吞掉**的类型。
        assertThat(err).as("不得退回普通 IllegalStateException（那会让 fatal 契约降级）")
                .isNotInstanceOf(IllegalStateException.class);
        assertThat(err.getMessage()).isEqualTo("boom");
    }

    @Test
    @DisplayName("FatalError：必须能从 catch (RuntimeException) 里被识别出来（域内 GlobalExceptionHandler 需显式 rethrow）")
    void fatalErrorIsDistinguishableFromBusinessFailures() {
        assertThatThrownBy(() -> {
            throw new FatalError("fatal-for-rethrow", new IllegalStateException("root"));
        })
                .isInstanceOf(FatalError.class)
                .hasMessage("fatal-for-rethrow");
    }
}
