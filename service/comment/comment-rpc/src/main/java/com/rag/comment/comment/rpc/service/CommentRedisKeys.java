package com.rag.comment.comment.rpc.service;

import org.springframework.stereotype.Component;

/**
 * comment 域 Redis key 构造 —— 对应 Go 端 {@code service/comment/rpc/internal/cache/keys.go:5-20}。
 *
 * <p><b>key 格式是缓存契约，不是实现细节</b>：comment-rpc 写、comment-api 读（若未来加读路径），
 * 任何一处改格式都会让对方的缓存全部失效。因此本类逐字复刻，不做「规范化」。
 */
@Component
public class CommentRedisKeys {

    /**
     * 对应 Go {@code SubjectKey(subjectID)}：{@code "comment:subject:%s"}。
     *
     * <p>⚠️ <b>该 key 只含 subjectID、不含 targetType</b>（Go 源码就是这么写的）。
     * 后果：两个不同 targetType 但 ID 相同的 target 会撞同一个 key
     * ⇒ A 类型的评论区可能被当成 B 类型的返回。
     *
     * <p><b>本模块保留该缺陷</b>，理由：
     * <ol>
     *   <li>修它需要改 key 格式 ⇒ 灰度期新旧进程互相读不到对方的缓存（放大回源），可观察；</li>
     *   <li>Go 侧已有的缓存里就是这个格式，改格式会让存量 key 全部变成孤儿；</li>
     *   <li>它是 Go 的既有行为（AGENTS.md「出现行为差异时默认判 RAG 错」）。</li>
     * </ol>
     * 正确修法（若将来要修）必须是「改格式 + 双读 + 灰度期自然过期」三步，
     * 不能单点替换。登记在 {@code docs/DRIFT/comment/rpc.md} 的待办里。
     */
    public static String subjectKey(String subjectId) {
        return "comment:subject:" + subjectId;
    }

    /** 对应 Go {@code ReplyContentKey(commentID)}：{@code "comment:content:%d"}。 */
    public static String replyContentKey(long commentId) {
        return "comment:content:" + commentId;
    }

    /**
     * 对应 Go {@code ReplyIndexPageKey(targetType, targetID, rootID, sort, offset, limit)}：
     * {@code "comment:index:%s:%s:%d:%s:%d:%d"}。
     *
     * @param sort {@code "time"} 或 {@code "hot"}（Go 的 {@code model.ReplySort} 字符串值）
     */
    public static String replyIndexPageKey(String targetType, String targetId, long rootId,
                                           String sort, int offset, int limit) {
        return "comment:index:" + targetType + ":" + targetId + ":" + rootId + ":" + sort + ":" + offset + ":" + limit;
    }

    /**
     * 对应 Go {@code CommentIndexKey(commentID)}：{@code "comment:index:item:%d"}。
     *
     * <p>⚠️ 它<b>与分页 key 同前缀</b>（{@code comment:index:}），
     * 而 {@link #targetPageScanPattern} 的 SCAN 模式是
     * {@code comment:index:<type>:<id>:*}。
     * 若某个 targetType 字面值恰好是 {@code "item"}，则 item key 会被误删。
     * Go 侧有同样的风险（同一套命名），照搬不修。
     */
    public static String commentIndexKey(long commentId) {
        return "comment:index:item:" + commentId;
    }

    /**
     * 对应 Go {@code comment_invalidation_cache.go:19} 的 SCAN 模式：
     * {@code "comment:index:%s:%s:*"}。
     *
     * <p>⚠️ 它<b>只</b>覆盖分页 key，<b>不</b>覆盖 {@link #commentIndexKey}（单条索引）
     * 与 {@link #replyContentKey}（正文）。
     * Go 的理由写在注释里（FileTree/comment/rpc.md §缓存表）：
     * 「不删 comment:content:*（内容不可变，24h TTL 自然过期）」——
     * 评论被删后正文缓存仍可命中，是 Go 的既有行为。
     */
    public static String targetPageScanPattern(String targetType, String targetId) {
        return "comment:index:" + targetType + ":" + targetId + ":*";
    }
}