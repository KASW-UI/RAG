package com.rag.task.task.rpc.service;

import com.rag.task.task.rpc.domain.TaskProgressEntity;
import com.rag.task.task.rpc.mapper.TaskProgressMapper;
import com.rag.task.task.proto.GetTaskReq;
import com.rag.task.task.proto.GetTaskResp;
import com.rag.task.task.proto.Task;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code GetTask} 的行为测试 —— 断言的是 <b>Go 侧的可观察行为</b>，不是「代码写成了这样」。
 *
 * <p>Go 出处：{@code service/task/rpc/internal/logic/get_task_logic.go:40-139}。
 *
 * <h2>为什么这部分用 Mockito 而不接真 Redis</h2>
 * <p>本类验证的是三件<b>编排层面</b>的事：分支走向、缺省值来源、以及"哪些错误被吞掉"。
 * 真依赖（PG 的 ON CONFLICT / GREATEST / COALESCE 语义）在
 * {@code TaskProgressSqlParityTest} 用 Testcontainers + 真 PG 断言；
 * 装配层在 {@code TaskRpcSpringContextWiringTest} 断言。
 * AGENTS.md §3.3 反对的是"用纯 mock 假装验证了框架机制"，不是反对对纯业务分支用 mock。
 */
class TaskDomainServiceTest {

    // ==================== 1. 参数校验 ====================

    @ParameterizedTest(name = "user_id={0} ⇒ INVALID_ARGUMENT")
    @ValueSource(longs = {0L, -1L, Long.MIN_VALUE})
    @DisplayName("uid <= 0 ⇒ INVALID_ARGUMENT + 文案逐字为 user_id invalid")
    void invalidUidIsRejected(long uid) {
        TaskDomainService svc = serviceWith(null, List.of());

        StatusRuntimeException ex = assertThrows(StatusRuntimeException.class,
                () -> svc.getTask(GetTaskReq.newBuilder().setUserId(uid).build()));

        assertEquals(Status.Code.INVALID_ARGUMENT, ex.getStatus().getCode());
        // ⚠️ 文案是 L1 契约：Go 的 task-api 把 gRPC error 原样往上抛给 HTTP 层
        assertEquals("user_id invalid", ex.getStatus().getDescription());
    }

    @Test
    @DisplayName("uid <= 0 时不查任何外部依赖（Go 在校验后就 return）")
    void invalidUidDoesNotTouchRedisOrDb() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        TaskProgressMapper mapper = mock(TaskProgressMapper.class);
        TaskDomainService svc = new TaskDomainService(redis, mapper);

        assertThrows(StatusRuntimeException.class,
                () -> svc.getTask(GetTaskReq.newBuilder().setUserId(0L).build()));

        verify(mapper, never()).selectByUserId(anyLong());
        verify(redis, never()).opsForHash();
    }

    // ==================== 2. 合并语义：required 来自 defs，不来自 DB ====================

    @Test
    @DisplayName("required_progress 取任务定义，不取 DB 的 target（Go get_task_logic.go:86-87）")
    void requiredProgressComesFromDefsNotDbTarget() {
        // DB 里 target 被写成 999 —— 若实现改成读 row.getTarget()，断言就会红
        TaskProgressEntity row = row(1L, 190L, "doing", 7L, 999L);
        TaskDomainService svc = serviceWith(null, List.of(row));

        GetTaskResp resp = svc.getTask(GetTaskReq.newBuilder().setUserId(1L).build());

        Task t190 = resp.getTask(0);
        assertEquals(190L, t190.getTaskId());
        assertEquals(7L, t190.getCompletionProgress());
        assertEquals(3L, t190.getRequiredProgress(), "required 必须来自 TaskDefs，不是 DB target");
    }

    @Test
    @DisplayName("无进度时 progress 缺省 0，但仍返回全部两条任务定义")
    void missingProgressDefaultsToZero() {
        TaskDomainService svc = serviceWith(null, List.of());

        GetTaskResp resp = svc.getTask(GetTaskReq.newBuilder().setUserId(5L).build());

        assertEquals(2, resp.getTaskCount(), "必须恒返回 AllTaskDefs 的全部条目");
        for (Task t : resp.getTaskList()) {
            assertEquals(0L, t.getCompletionProgress());
        }
    }

    @Test
    @DisplayName("响应顺序 = AllTaskDefs 顺序（190 在前、1902 在后），且文案逐字一致")
    void responseOrderAndCopyFollowDefs() {
        TaskDomainService svc = serviceWith(null, List.of());

        GetTaskResp resp = svc.getTask(GetTaskReq.newBuilder().setUserId(5L).build());

        assertEquals(List.of(190L, 1902L),
                resp.getTaskList().stream().map(Task::getTaskId).toList());
        assertEquals("文章获赞任务", resp.getTask(0).getName());
        assertEquals("有一篇文章点赞大于3", resp.getTask(0).getDesc());
        assertEquals("点赞任务", resp.getTask(1).getName());
        assertEquals("给文章点赞5次", resp.getTask(1).getDesc());
        assertEquals(3L, resp.getTask(0).getRequiredProgress());
        assertEquals(5L, resp.getTask(1).getRequiredProgress());
    }

    // ==================== 3. Redis 是优化不是 source of truth ====================

    @Test
    @DisplayName("Redis 命中 ⇒ 不查库，且直接用缓存里的 packed 值")
    void cacheHitSkipsDb() {
        Map<String, String> packed = new HashMap<>();
        packed.put("190", "doing|3|3");
        packed.put("1902", "done|5|5");

        TaskProgressMapper mapper = mock(TaskProgressMapper.class);
        TaskDomainService svc = serviceWith(packed, List.of(), mapper);

        GetTaskResp resp = svc.getTask(GetTaskReq.newBuilder().setUserId(1L).build());

        verify(mapper, never()).selectByUserId(anyLong());
        assertEquals(3L, resp.getTask(0).getCompletionProgress());
        assertEquals(5L, resp.getTask(1).getCompletionProgress());
    }

    @Test
    @DisplayName("Redis 抛异常 ⇒ 当作未命中并回源查库（Go: if rerr != nil || len(pm)==0）")
    void redisFailureFallsBackToDb() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashOps = mock(HashOperations.class);
        when(redis.opsForHash()).thenReturn(hashOps);
        doThrow(new RuntimeException("redis down")).when(hashOps).entries(anyString());

        TaskProgressMapper mapper = mock(TaskProgressMapper.class);
        when(mapper.selectByUserId(1L)).thenReturn(List.of(row(1L, 190L, "doing", 2L, 3L)));
        TaskDomainService svc = new TaskDomainService(redis, mapper);

        // 🔴 Redis 挂掉不能让接口失败 —— Go 是回源，不是返回错误
        GetTaskResp resp = svc.getTask(GetTaskReq.newBuilder().setUserId(1L).build());

        verify(mapper, times(1)).selectByUserId(1L);
        assertEquals(2L, resp.getTask(0).getCompletionProgress());
    }

    @Test
    @DisplayName("DB 失败 ⇒ 异常上抛（Go: return nil, err ⇒ grpc-go 转 UNKNOWN），不得静默返回 0 进度")
    void dbFailurePropagates() {
        // ⚠️ 这里**不能**走 serviceWith(...)：那个 helper 内部会用 thenReturn 再 stub 一次，
        //    把这里的 thenThrow 覆盖掉 ⇒ 测试断言的其实是 helper 的行为，与被测代码无关（本次实测踩到过）。
        TaskProgressMapper mapper = mock(TaskProgressMapper.class);
        when(mapper.selectByUserId(anyLong())).thenThrow(new RuntimeException("pg down"));

        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashOps = mock(HashOperations.class);
        when(redis.opsForHash()).thenReturn(hashOps);
        when(hashOps.entries(anyString())).thenReturn(new HashMap<>());

        TaskDomainService svc = new TaskDomainService(redis, mapper);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> svc.getTask(GetTaskReq.newBuilder().setUserId(1L).build()));
        assertEquals("pg down", ex.getMessage());
    }

    // ==================== 4. packed 值的解析边界 ====================

    @Test
    @DisplayName("packed 段数 != 3 ⇒ progress 回落 0（Go: if len(parts) != 3）")
    void malformedPackedYieldsZero() {
        TaskDomainService svc = serviceWith(Map.of(
                "190", "doing|3",        // 2 段
                "1902", "a|b|c|d"), List.of());     // 4 段

        GetTaskResp resp = svc.getTask(GetTaskReq.newBuilder().setUserId(1L).build());

        assertEquals(0L, resp.getTask(0).getCompletionProgress());
        assertEquals(0L, resp.getTask(1).getCompletionProgress());
    }

    @Test
    @DisplayName("尾部空串必须保留（Go strings.Split 保留，Java 默认 split 会丢）")
    void trailingEmptySegmentIsPreserved() {
        // Go: strings.Split("doing|5|", "|") = ["doing","5",""] 长度 3 ⇒ progress = parse("5") = 5
        // ⚠️ Java 默认 split("\\|") 会得 ["doing","5"] 长度 2 ⇒ progress = 0（方向相反的漂移）
        TaskDomainService svc = serviceWith(Map.of("190", "doing|5|"), List.of());

        GetTaskResp resp = svc.getTask(GetTaskReq.newBuilder().setUserId(1L).build());

        assertEquals(5L, resp.getTask(0).getCompletionProgress(),
                "Go strings.Split 保留尾部空串；用默认 split 会得到 0");
    }

    @Test
    @DisplayName("progress 段非法数字 ⇒ 回落 0（Go parseInt64Default）")
    void nonNumericProgressYieldsZero() {
        TaskDomainService svc = serviceWith(Map.of("190", "doing|abc|3"), List.of());

        GetTaskResp resp = svc.getTask(GetTaskReq.newBuilder().setUserId(1L).build());

        assertEquals(0L, resp.getTask(0).getCompletionProgress());
    }

    @Test
    @DisplayName("status / target 段不参与输出（Go 用 _, p, _ 解包，只取第 2 段）")
    void statusAndTargetSegmentsAreIgnored() {
        TaskDomainService svc = serviceWith(Map.of("190", "weird|4|999"), List.of());

        GetTaskResp resp = svc.getTask(GetTaskReq.newBuilder().setUserId(1L).build());

        assertEquals(4L, resp.getTask(0).getCompletionProgress());
        assertEquals(3L, resp.getTask(0).getRequiredProgress());
    }

    // ==================== 5. 回填缓存：key 与 TTL 都是契约 ====================

    @Test
    @DisplayName("回源有行 ⇒ 对 task:progress:<uid> 做 HSET + EXPIRE 15m（Go get_task_logic.go:73-76）")
    void backfillHitsTheReadKeyWithGoTtl() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        TaskProgressMapper mapper = mock(TaskProgressMapper.class);
        when(mapper.selectByUserId(1L))
                .thenReturn(List.of(row(1L, 190L, "doing", 4L, 3L),
                        row(1L, 1902L, "doing", 1L, 5L)));
        TaskDomainService svc = new TaskDomainService(redis, mapper);

        svc.getTask(GetTaskReq.newBuilder().setUserId(1L).build());

        // 取出真正提交给 Redis 的 SessionCallback，在记录型 RedisOperations 上跑一遍
        @SuppressWarnings("unchecked")
        ArgumentCaptor<SessionCallback<Object>> cap = ArgumentCaptor.forClass(SessionCallback.class);
        verify(redis).execute(cap.capture());

        RedisOperations<String, String> ops = mock(RedisOperations.class);
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashOps = mock(HashOperations.class);
        when(ops.opsForHash()).thenReturn(hashOps);
        when(ops.exec()).thenReturn(List.of());
        cap.getValue().execute(ops);

        // ⚠️ key 必须是 task:progress:1 —— 写路径用的是另一个 key（见 TaskRedisKeys 类注释）
        verify(hashOps).putAll(eq("task:progress:1"), any());
        verify(ops).expire("task:progress:1", TaskRedisKeys.PROGRESS_CACHE_TTL);
    }

    @Test
    @DisplayName("回源无行 ⇒ 只 EXPIRE 30s，不写任何 Hash（Go 对不存在的 key 设 TTL，等效 no-op）")
    void backfillOnEmptyRowsOnlyExpires() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        TaskProgressMapper mapper = mock(TaskProgressMapper.class);
        when(mapper.selectByUserId(9L)).thenReturn(List.of());
        TaskDomainService svc = new TaskDomainService(redis, mapper);

        svc.getTask(GetTaskReq.newBuilder().setUserId(9L).build());

        // 该分支走的是单条 expire（不是 pipeline），Redis 异常也被吞掉
        verify(redis).expire("task:progress:9", TaskRedisKeys.EMPTY_PROGRESS_CACHE_TTL);
        verify(redis, never()).execute(any(SessionCallback.class));
    }

    @Test
    @DisplayName("回填失败不影响返回（Go: _, _ = pipe.Exec(ctx)）")
    void backfillFailureDoesNotBreakResponse() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        doThrow(new RuntimeException("redis down on write")).when(redis).execute(any(SessionCallback.class));

        TaskProgressMapper mapper = mock(TaskProgressMapper.class);
        when(mapper.selectByUserId(1L)).thenReturn(List.of(row(1L, 190L, "doing", 4L, 3L)));
        TaskDomainService svc = new TaskDomainService(redis, mapper);

        // 🔴 不能抛异常
        GetTaskResp resp = svc.getTask(GetTaskReq.newBuilder().setUserId(1L).build());

        assertEquals(4L, resp.getTask(0).getCompletionProgress());
    }

    // ==================== 6. Redis key 构造（跨域契约） ====================

    @Test
    @DisplayName("Redis key 逐字对齐 Go：读路径与两条写路径三套形状都保留")
    void redisKeysMatchGoLiterals() {
        // 读路径 / 缓存失效
        assertEquals("task:progress:123", TaskRedisKeys.userProgressCache(123L));

        // 点赞路：init 单数、uid 在前；done 也是 uid 在前
        assertEquals("task:progress:123:1902", TaskRedisKeys.likeTaskProgress(123L, 1902L));
        assertEquals("task:init:123:1902", TaskRedisKeys.likeTaskInit(123L, 1902L));
        assertEquals("task:done:123:1902", TaskRedisKeys.likeTaskDone(123L, 1902L));
        assertEquals("like:total:123", TaskRedisKeys.userLikeTotal(123L));

        // 文章路：inits 复数、taskId 在前、带 articleId；done 的前两段顺序相反
        assertEquals("task:inits:190:123:456", TaskRedisKeys.articleTaskInit(190L, 123L, 456L));
        assertEquals("task:progress:190:123:456", TaskRedisKeys.articleTaskProgress(190L, 123L, 456L));
        assertEquals("task:done:190:123", TaskRedisKeys.articleTaskDone(190L, 123L));
        assertEquals("article:like:cnt:456", TaskRedisKeys.articleLikeCount(456L));

        // 奖励流
        assertEquals("stream:task:reward", TaskRedisKeys.rewardStream());
    }

    @Test
    @DisplayName("TTL 取值对齐 Go：进度缓存 15m / 空结果 30s / 点赞 90d / 文章 init 24h / 文章 done 120d")
    void ttlsMatchGo() {
        assertEquals(15 * 60L, TaskRedisKeys.PROGRESS_CACHE_TTL.getSeconds());
        assertEquals(30L, TaskRedisKeys.EMPTY_PROGRESS_CACHE_TTL.getSeconds());
        assertEquals(Duration.ofDays(90), TaskRedisKeys.LIKE_TASK_TTL);
        assertEquals(Duration.ofHours(24), TaskRedisKeys.ARTICLE_INIT_TTL);
        assertEquals(Duration.ofDays(120), TaskRedisKeys.ARTICLE_DONE_TTL);
    }

    // ==================== helpers ====================

    private static TaskProgressEntity row(long uid, long taskId, String status,
                                          long progress, long target) {
        TaskProgressEntity e = new TaskProgressEntity();
        e.setUserId(uid);
        e.setTaskId(taskId);
        e.setStatus(status);
        e.setProgress(progress);
        e.setTarget(target);
        return e;
    }

    private static TaskDomainService serviceWith(Map<String, String> cache,
                                                 List<TaskProgressEntity> rows) {
        return serviceWith(cache, rows, mock(TaskProgressMapper.class));
    }

    private static TaskDomainService serviceWith(Map<String, String> cache,
                                                 List<TaskProgressEntity> rows,
                                                 TaskProgressMapper mapper) {
        when(mapper.selectByUserId(anyLong())).thenReturn(rows);

        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashOps = mock(HashOperations.class);
        when(redis.opsForHash()).thenReturn(hashOps);
        when(hashOps.entries(anyString()))
                .thenAnswer(inv -> new HashMap<Object, Object>(cache == null ? Map.of() : cache));

        return new TaskDomainService(redis, mapper);
    }
}