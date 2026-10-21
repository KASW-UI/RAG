package com.rag.message.message.rpc.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code trimPreview} 的 Go 对齐契约（AGENTS.md §6.2 P2，2026-10-21 修）。
 *
 * <p><b>Go 出处逐字</b>（{@code service/message/rpc/internal/model/message.go:234-240}）：
 * <pre>
 * func (m *MessageModel) TrimPreview(content string) string {
 *     content = strings.TrimSpace(content)
 *     runes := []rune(content)
 *     if len(runes) &lt;= 80 { return content }
 *     return string(runes[:80])
 * }
 * </pre>
 *
 * <p><b>修复前本方法有两处错</b>：上限写 255（抄了 {@code last_message_preview} 的列宽，
 * 不是 Go 的截断规则），且用 {@code String.length()} / {@code substring} 按 UTF-16
 * code unit 计数 —— 中文 1 字符算 1，代理对算 2 ⇒ 截断点可能落在代理对中间。
 */
class MessageRpcSupportTrimPreviewTest {

    /** 复刻 Go：{"code":1006, "msg":1006, "call_chain":1006, "label":1006} 的四元组。 */
    @Test
    @DisplayName("A-P2：未超 80 rune 时原样返回（trim 后）")
    void shortContentReturnedAsIs() {
        assertEquals("hello", MessageRpcSupport.trimPreview("  hello  "));
        assertEquals("", MessageRpcSupport.trimPreview(null));
    }

    @Test
    @DisplayName("A-P2：恰好 80 rune 不截断")
    void exactly80RunesNotTruncated() {
        String s = "字".repeat(80);
        assertEquals(s, MessageRpcSupport.trimPreview(s));
    }

    @Test
    @DisplayName("A-P2：81 rune 截到 80 —— 上限是 80 不是 255")
    void runes81TruncatedTo80() {
        String s = "字".repeat(81);
        String out = MessageRpcSupport.trimPreview(s);
        assertEquals(80, out.codePointCount(0, out.length()),
                "截断上限必须是 80 个 rune（Go 字面量），不是 255 —— 见 message.go:236");
    }

    /**
     * <b>本项最关键的一条</b>：中文是 BMP 字符，{@code String.length()} 与 rune 计数相等，
     * 所以「255 → 80」这个错误用中文测不出来。必须用<b>代理对</b>字符才能暴露 char ≠ rune。
     */
    @Test
    @DisplayName("A-P2：代理对（emoji）必须按 rune 计数，且不得截出半个代理对")
    void surrogatePairCountedAsOneRune() {
        // 100 个 emoji = 100 rune，但 String.length() 是 200
        String emoji = "😀".repeat(100);
        assertEquals(200, emoji.length(), "前置条件：emoji 是代理对，UTF-16 占 2 个 code unit");
        assertEquals(100, emoji.codePointCount(0, emoji.length()), "前置条件：码点数为 100");

        String out = MessageRpcSupport.trimPreview(emoji);
        assertEquals(80, out.codePointCount(0, out.length()),
                "必须截到 80 个码点；按 length() 截会得到 80 个 code unit = 40 个 emoji");
        // 不含孤立代理对 —— 半截代理对序列化后是乱码
        for (int i = 0; i < out.length(); i++) {
            char c = out.charAt(i);
            assertTrue(!Character.isHighSurrogate(c) || (i + 1 < out.length()
                            && Character.isLowSurrogate(out.charAt(i + 1))),
                    "位置 " + i + " 出现孤立的高代理对 —— 截断点落在代理对中间");
        }
    }

    @Test
    @DisplayName("A-P2：emoji 恰好 80 个不截断")
    void exactly80EmojiNotTruncated() {
        String emoji = "😀".repeat(80);
        assertEquals(emoji, MessageRpcSupport.trimPreview(emoji),
                "80 个 emoji 应原样返回；按 length()=160 判断会误截");
    }

    @Test
    @DisplayName("A-P2：混合中文 + emoji 也按 rune 对齐 Go")
    void mixedChineseAndEmoji() {
        // 78 个中文 + 10 个 emoji = 88 rune
        String mixed = "字".repeat(78) + "😀".repeat(10);
        String out = MessageRpcSupport.trimPreview(mixed);
        assertEquals(80, out.codePointCount(0, out.length()));
        assertEquals(78, out.substring(0, out.offsetByCodePoints(0, 78)).codePointCount(0, 78));
    }
}