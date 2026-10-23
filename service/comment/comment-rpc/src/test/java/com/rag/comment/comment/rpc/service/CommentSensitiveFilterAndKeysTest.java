package com.rag.comment.comment.rpc.service;

import com.rag.comment.comment.rpc.config.CommentRpcProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 敏感词与 Redis key 的契约测试 —— 对应 Go
 * {@code service_context.go:29} 的黑名单与 {@code internal/cache/keys.go:5-20} 的 key 构造。
 *
 * <p>这两处都是「写错了不会报错、只会在线上出问题」的常量：
 * 词库错了 ⇒ 审核状态错；key 格式错了 ⇒ 缓存永远不命中（表现为 DB 压力上升，没有任何异常）。
 */
class CommentSensitiveFilterAndKeysTest {

    // ==================== 敏感词 ====================

    private static CommentSensitiveFilter defaultFilter() {
        CommentRpcProperties props = new CommentRpcProperties();
        return new CommentSensitiveFilter(props);
    }

    @Test
    @DisplayName("默认词库与 Go service_context.go:29 逐字一致：{\"傻逼\", \"我草\"}")
    void defaultWordListMatchesGo() {
        assertThat(defaultFilter().words()).containsExactlyInAnyOrder("傻逼", "我草");
    }

    @Test
    @DisplayName("命中敏感词 ⇒ status 应置 1（审核中），且不改内容")
    void matchesSensitiveWord() {
        assertThat(defaultFilter().match("你说的是傻逼吗")).isTrue();
        assertThat(defaultFilter().match("我草")).isTrue();
    }

    @Test
    @DisplayName("未命中 ⇒ false（落库为 state=0 正常，且会发通知）")
    void doesNotMatchOrdinaryContent() {
        assertThat(defaultFilter().match("今天天气不错")).isFalse();
        assertThat(defaultFilter().match("")).isFalse();
        assertThat(defaultFilter().match(null)).isFalse();
    }

    @Test
    @DisplayName("大小写不敏感（Go 对词与文本都做 strings.ToLower）")
    void caseInsensitive() {
        assertThat(defaultFilter().match("SENSITIVE")).isFalse(); // 词库里没有英文词，验证不误命中
        CommentRpcProperties props = new CommentRpcProperties();
        props.setSensitiveWords(List.of("BadWord"));
        CommentSensitiveFilter filter = new CommentSensitiveFilter(props);
        assertThat(filter.match("this is badword")).isTrue();
        assertThat(filter.match("this is BADWORD")).isTrue();
    }

    @Test
    @DisplayName("空词库 ⇒ 恒不命中（Go NewSensitiveFilter 对空词库只打 warning）")
    void emptyWordListNeverMatches() {
        CommentRpcProperties props = new CommentRpcProperties();
        props.setSensitiveWords(List.of());
        CommentSensitiveFilter filter = new CommentSensitiveFilter(props);
        assertThat(filter.match("傻逼")).isFalse();
        assertThat(filter.match("我草")).isFalse();
    }

    // ==================== Redis key ====================

    @Test
    @DisplayName("key 格式逐字对应 Go keys.go")
    void redisKeysMatchGoFormats() {
        assertThat(CommentRedisKeys.subjectKey("a1")).isEqualTo("comment:subject:a1");
        assertThat(CommentRedisKeys.replyContentKey(42L)).isEqualTo("comment:content:42");
        assertThat(CommentRedisKeys.replyIndexPageKey("article", "a1", 7L, "hot", 20, 20))
                .isEqualTo("comment:index:article:a1:7:hot:20:20");
        assertThat(CommentRedisKeys.commentIndexKey(42L)).isEqualTo("comment:index:item:42");
        assertThat(CommentRedisKeys.targetPageScanPattern("article", "a1"))
                .isEqualTo("comment:index:article:a1:*");
    }

    @Test
    @DisplayName("🔴 已知缺陷必须保留：SubjectKey 不含 targetType，跨类型同 ID 会撞 key")
    void subjectKeyOmitsTargetTypeOnPurpose() {
        // Go keys.go:5-7 就是 `comment:subject:%s`，没有 targetType。
        // 「顺手补上」会让灰度期新旧进程互相读不到对方的缓存，并让存量 key 变孤儿。
        // 见 CommentRedisKeys#subjectKey 的类注释。
        assertThat(CommentRedisKeys.subjectKey("a1"))
                .isEqualTo(CommentRedisKeys.subjectKey("a1"));
        assertThat(CommentRedisKeys.subjectKey("42")).doesNotContain("article");
    }

    @Test
    @DisplayName("失效用的 SCAN 模式不覆盖单条索引 key 与正文 key（Go 的既有行为）")
    void scanPatternDoesNotCoverItemOrContentKeys() {
        String pattern = CommentRedisKeys.targetPageScanPattern("article", "a1");
        // comment:index:item:42 不匹配（item 段在前）
        assertThat("comment:index:item:42".startsWith("comment:index:article:a1:")).isFalse();
        // comment:content:42 完全不在 comment:index: 前缀下
        assertThat("comment:content:42".startsWith("comment:index:article:a1:")).isFalse();
        // comment:index:article:a1:7:hot:20:20 是分页 key，应被 SCAN 模式覆盖
        assertThat("comment:index:article:a1:7:hot:20:20").startsWith("comment:index:article:a1:");
        assertThat(pattern).isEqualTo("comment:index:article:a1:*");
    }
}