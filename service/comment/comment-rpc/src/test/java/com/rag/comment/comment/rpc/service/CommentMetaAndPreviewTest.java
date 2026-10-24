package com.rag.comment.comment.rpc.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 纯函数的边界行为测试 —— 对应 Go
 * {@code model/comment.go:222-240} 的 {@code normalizeCommentMeta} 与
 * {@code mqs/audit_consumer.go:122-132} 的 {@code previewComment}。
 *
 * <p>这两处都是「错了不报错」的地方：
 * meta 归一化错了 ⇒ 扩展字段解析行为漂移；预览截断错了 ⇒ 通知文案里出现半个 emoji。
 */
class CommentMetaAndPreviewTest {

    private final CommentTransactionalService service = new CommentTransactionalService(
            null, null, null, null, null, null,
            new com.rag.comment.comment.rpc.metrics.CommentRpcMetrics(
                    new io.micrometer.core.instrument.simple.SimpleMeterRegistry()),
            new com.fasterxml.jackson.databind.ObjectMapper());

    // ==================== normalizeCommentMeta ====================

    @Test
    @DisplayName("空 / 空白 ⇒ \"{}\"（Go comment.go:224-226）")
    void emptyBecomesEmptyObject() {
        assertThat(service.normalizeCommentMeta("")).isEqualTo("{}");
        assertThat(service.normalizeCommentMeta("   ")).isEqualTo("{}");
        assertThat(service.normalizeCommentMeta(null)).isEqualTo("{}");
    }

    @Test
    @DisplayName("⚠️ 合法 JSON 必须原样返回（不重排、不规范化）")
    void validJsonIsReturnedVerbatim() {
        String input = "{\"b\":1,  \"a\":[2,3]}";
        assertThat(service.normalizeCommentMeta(input)).isEqualTo(input);
    }

    @Test
    @DisplayName("⚠️ 非法 JSON ⇒ 包成 {\"raw\": 原文}")
    void invalidJsonIsWrappedAsRaw() {
        assertThat(service.normalizeCommentMeta("not json at all"))
                .isEqualTo("{\"raw\":\"not json at all\"}");
    }

    @Test
    @DisplayName("⚠️ HTML 字符按 Go 的 encoding/json 转义（< > & ⇒ \\u003c \\u003e \\u0026）")
    void htmlCharactersUseGoEscaping() {
        // Go 的 json.Marshal 默认 SetEscapeHTML(true)；Jackson 默认不转义。
        // 若这里产出裸 '<', 缓存与库里的字节就与 Go 不同。
        String wrapped = service.normalizeCommentMeta("<b>&</b>");
        assertThat(wrapped).isEqualTo("{\"raw\":\"\\u003cb\\u003e\\u0026\\u003c/b\\u003e\"}");
    }

    @Test
    @DisplayName("JSON 标量也算合法（Go Unmarshal 成 any 成功即原样返回）")
    void jsonScalarsAreValid() {
        assertThat(service.normalizeCommentMeta("123")).isEqualTo("123");
        assertThat(service.normalizeCommentMeta("true")).isEqualTo("true");
        assertThat(service.normalizeCommentMeta("\"str\"")).isEqualTo("\"str\"");
    }

    @Test
    @DisplayName("控制字符与引号按 Go 规则转义")
    void controlCharsEscaped() {
        assertThat(CommentTransactionalService.goJsonQuote("a\"b\\c"))
                .isEqualTo("\"a\\\"b\\\\c\"");
        assertThat(CommentTransactionalService.goJsonQuote("a\nb"))
                .isEqualTo("\"a\\nb\"");
    }

    // ==================== previewComment ====================

    @Test
    @DisplayName("空 ⇒ \"(no content)\"")
    void emptyPreview() {
        assertThat(CommentAuditListener.previewComment("")).isEqualTo("(no content)");
        assertThat(CommentAuditListener.previewComment("   ")).isEqualTo("(no content)");
        assertThat(CommentAuditListener.previewComment(null)).isEqualTo("(no content)");
    }

    @Test
    @DisplayName("≤40 rune 原样返回；>40 取前 40 + \"...\"")
    void truncationAtFortyRunes() {
        assertThat(CommentAuditListener.previewComment("短评论")).isEqualTo("短评论");
        String forty = "一".repeat(40);
        assertThat(CommentAuditListener.previewComment(forty)).isEqualTo(forty);
        String fortyOne = "一".repeat(41);
        assertThat(CommentAuditListener.previewComment(fortyOne)).isEqualTo(forty + "...");
    }

    @Test
    @DisplayName("🔴 按 rune 而非 UTF-16 code unit 截断（emoji 是代理对）")
    void truncatesByCodePointNotChar() {
        // 41 个 emoji：Java 的 String.length() 会算成 82，若按 char 截会只留 20 个
        String emoji = "😀".repeat(41);
        assertThat(emoji.length()).isEqualTo(82);          // 前提：确实含代理对
        String preview = CommentAuditListener.previewComment(emoji);
        assertThat(preview).endsWith("...");
        assertThat(preview.codePointCount(0, preview.length())).isEqualTo(43); // 40 + "..."
    }
}