package com.rag.like.like.rpc.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 基础件（游标 ID / JSON 兼容 / Lua / Redis key）的守门测试。
 *
 * <p>这些看起来都是「小函数」，但每一处都是<b>跨语言契约</b>，改错了不会编译报错、
 * 只会在灰度期表现为「数据读不出来」或「哈希对不上」。
 */
class LikePrimitivesTest {

    // ==================== 游标 ID ====================

    @Test
    @DisplayName("游标 ID 的高 22 位必须是 Unix 毫秒 —— ZSet 的 score>>22 靠它反解时间")
    void cursorIdPacksUnixMillis() {
        long id = LikeCursorIdGenerator.nextId();
        long recovered = LikeCursorIdGenerator.timestampOf(id);

        long before = System.currentTimeMillis();
        long expectedAfter = System.currentTimeMillis();

        assertTrue(recovered >= before - 1000 && recovered <= expectedAfter + 1000,
                "score>>22 反解出的时间应接近当前毫秒，实际=" + recovered);
    }

    @Test
    @DisplayName("scoreOf / timestampOf 互逆，且位移常量与生成器一致")
    void scorePackingRoundTrips() {
        long millis = 1_700_000_000_123L;
        long score = LikeCursorIdGenerator.scoreOf(millis);
        assertEquals(millis, LikeCursorIdGenerator.timestampOf(score));
    }

    @Test
    @DisplayName("并发下 2000 次调用不产生重复 ID（Go 用 sync.Mutex 保证，Java 用 synchronized）")
    void cursorIdsAreUniqueUnderConcurrency() throws Exception {
        int threads = 8;
        int perThread = 250;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<List<Long>>> tasks = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                tasks.add(() -> {
                    List<Long> ids = new ArrayList<>(perThread);
                    for (int i = 0; i < perThread; i++) {
                        ids.add(LikeCursorIdGenerator.nextId());
                    }
                    return ids;
                });
            }
            List<Future<List<Long>>> futures = pool.invokeAll(tasks);
            Set<Long> all = new java.util.HashSet<>();
            int total = 0;
            for (Future<List<Long>> f : futures) {
                for (Long id : f.get()) {
                    assertTrue(all.add(id), "出现重复游标 ID: " + id);
                    total++;
                }
            }
            assertEquals(threads * perThread, total);
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    // ==================== JSON 跨语言兼容 ====================

    @Test
    @DisplayName("Go encoding/json 的 HTML 转义：< > & 必须写成 \\u003c/3e/26 —— 否则灰度期哈希对不上")
    void htmlEscapingMatchesGoEncodingJson() throws Exception {
        var mapper = new ObjectMapper();
        var node = mapper.createObjectNode().put("target_id", "a<b>c&d");

        // Jackson 默认输出：原样
        String jackson = node.toString();
        assertTrue(jackson.contains("a<b>c&d"), "Jackson 默认不转义，前提成立");

        // Go 兼容层输出：转义
        String goStyle = GoCompatibleJson.write(node);
        assertTrue(goStyle.contains("a\\u003cb\\u003ec\\u0026d"), "实际: " + goStyle);
    }

    @Test
    @DisplayName("消息哈希对同一输入恒定、对任一字段变化敏感 —— 它是 msg_id 复用的判据")
    void digestIsStableAndSensitive() {
        LikeMessageDigest digest = new LikeMessageDigest(new ObjectMapper());
        String base = digest.hash(7L, "article", "t1", 9L, 1, 1000L, true);

        assertEquals(base, digest.hash(7L, "article", "t1", 9L, 1, 1000L, true), "同输入必须同哈希");
        assertEquals(64, base.length(), "SHA-256 hex 是 64 位");
        assertTrue(base.matches("[0-9a-f]{64}"), "必须是**小写** hex（Go hex.EncodeToString）");

        assertNotEquals(base, digest.hash(8L, "article", "t1", 9L, 1, 1000L, true));
        assertNotEquals(base, digest.hash(7L, "video", "t1", 9L, 1, 1000L, true));
        assertNotEquals(base, digest.hash(7L, "article", "t2", 9L, 1, 1000L, true));
        assertNotEquals(base, digest.hash(7L, "article", "t1", 10L, 1, 1000L, true));
        assertNotEquals(base, digest.hash(7L, "article", "t1", 9L, 2, 1000L, true));
        assertNotEquals(base, digest.hash(7L, "article", "t1", 9L, 1, 1001L, true));
        assertNotEquals(base, digest.hash(7L, "article", "t1", 9L, 1, 1000L, false));
    }

    @Test
    @DisplayName("RFC3339Nano 去掉小数末尾零、全零时省略小数点 —— Java 的 ISO_OFFSET_DATE_TIME 不等价")
    void rfc3339NanoDropsTrailingZeros() {
        // 1970-01-01T00:00:00Z，没有小数
        assertEquals("1970-01-01T00:00:00Z", GoCompatibleJson.rfc3339Nano(Instant.ofEpochSecond(0)));
        // .100000000 → .1
        assertEquals("1970-01-01T00:00:00.1Z", GoCompatibleJson.rfc3339Nano(Instant.ofEpochSecond(0, 100_000_000)));
        // .123456000 → .123456
        assertEquals("1970-01-01T00:00:00.123456Z",
                GoCompatibleJson.rfc3339Nano(Instant.ofEpochSecond(0, 123_456_000)));
        // 9 位全保留
        assertEquals("1970-01-01T00:00:00.123456789Z",
                GoCompatibleJson.rfc3339Nano(Instant.ofEpochSecond(0, 123_456_789)));
    }

    // ==================== Lua 脚本 ====================

    @Test
    @DisplayName("Lua 脚本的 KEYS 顺序注释与实际调用顺序一致（6 keys / 4 args）")
    void luaScriptShapeIsStable() {
        assertTrue(LikeActionScript.LUA.contains("KEYS[1]"), "脚本必须引用 KEYS[1]");
        assertTrue(LikeActionScript.LUA.contains("KEYS[6]"), "脚本必须引用 KEYS[6]");
        assertTrue(LikeActionScript.LUA.contains("ARGV[4]"), "脚本必须引用 ARGV[4]");

        String[] args = LikeActionScript.args("t1", 7L, 1, 123L);
        assertEquals(4, args.length);
        assertEquals("t1", args[0]);
        assertEquals("7", args[1]);
        assertEquals("1", args[2]);
        assertEquals("123", args[3]);
    }

    @Test
    @DisplayName("Lua 的三条返回值语义被常量固定：0 幂等 / 1 变更 / 2 首次点赞")
    void luaReturnCodesArePinned() {
        // 这三个值决定 isFirst 与 Kafka 消息是否发出，改动即破坏调用方
        assertEquals(0L, LikeActionScript.RESULT_IDEMPOTENT);
        assertEquals(1L, LikeActionScript.RESULT_CHANGED);
        assertEquals(2L, LikeActionScript.RESULT_FIRST_LIKE);
    }

    // ==================== Redis key ====================

    @Test
    @DisplayName("6 个 Redis key 的字面量与 Go 的 fmt.Sprintf 逐字一致（读写两侧共用，改错会静默 miss）")
    void redisKeyFormatsMatchGo() {
        assertEquals("like_count:article", LikeRedisKeys.likeCountHash("article"));
        assertEquals("dislike_count:article", LikeRedisKeys.dislikeCountHash("article"));
        assertEquals("like_state:article:t1", LikeRedisKeys.likeStateHash("article", "t1"));
        assertEquals("user_like_list:7:article", LikeRedisKeys.userLikeListZSet(7L, "article"));
        assertEquals("target_liker_list:article:t1", LikeRedisKeys.targetLikerListZSet("article", "t1"));
        assertEquals("user_total_like:9", LikeRedisKeys.userTotalLikeString(9L));
    }
}