package com.rag.like.like.rpc.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.like.like.rpc.mapper.LikeConsumeInboxMapper;
import com.rag.like.like.rpc.mapper.LikeOutboxEventMapper;
import com.rag.like.like.rpc.mapper.LikeRecordMapper;
import com.rag.like.like.rpc.domain.LikeConsumeInboxEntity;
import com.rag.like.like.rpc.domain.LikeRecordEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 消费落库的<b>幂等与状态机</b>守门测试 —— 对应 Go
 * {@code model/like.go:148-245}（{@code ProcessLikeMessageBatch} / {@code applyLikeMessage}）。
 *
 * <p>这层是整个写路径的唯一正确性防线，Go 侧只有一条「Kafka 只能等 inbox + record + fact
 * 全部提交后才确认」的注释守着。全部用 mock 验证<b>调用与抛错</b>，不需要真 PG。
 */
class LikeMessageIngestServiceTest {

    private LikeRecordMapper recordMapper;
    private LikeConsumeInboxMapper inboxMapper;
    private LikeOutboxEventMapper outboxMapper;
    private LikeDomainFactService factService;
    private LikeMessageIngestService service;

    @BeforeEach
    void setUp() {
        recordMapper = Mockito.mock(LikeRecordMapper.class);
        inboxMapper = Mockito.mock(LikeConsumeInboxMapper.class);
        outboxMapper = Mockito.mock(LikeOutboxEventMapper.class);
        factService = Mockito.mock(LikeDomainFactService.class);
        service = new LikeMessageIngestService(recordMapper, inboxMapper, outboxMapper,
                new LikeMessageDigest(new ObjectMapper()), factService);
    }

    private static LikeMessageIngestService.LikeProcessPayload payload(int action, boolean isFirst) {
        LikeMessageIngestService.LikeProcessPayload p = new LikeMessageIngestService.LikeProcessPayload();
        p.msgId = "1000";
        p.userId = 7L;
        p.targetType = "article";
        p.targetId = "t1";
        p.authorId = 9L;
        p.action = action;
        p.occurredAt = 1_700_000_000L;
        p.isFirst = isFirst;
        return p;
    }

    private LikeRecordEntity existing(int state, long lastOperationId, boolean deleted) {
        LikeRecordEntity e = new LikeRecordEntity();
        e.setId(1L);
        e.setUserId(7L);
        e.setTargetType("article");
        e.setTargetId("t1");
        e.setAuthorId(9L);
        e.setState(state);
        e.setLastOperationId(lastOperationId);
        e.setDeleteAt(deleted ? java.time.OffsetDateTime.now() : null);
        return e;
    }

    // ==================== 幂等三层 ====================

    @Test
    @DisplayName("inbox 插入成功 ⇒ 正常应用状态变更并标记完成")
    void freshMessageIsApplied() {
        when(inboxMapper.insertInboxIfAbsent(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(1);
        when(recordMapper.selectForUpdateIncludingDeleted(anyLong(), anyString(), anyString()))
                .thenReturn(existing(0, 0, false));

        service.processBatch(List.of(payload(1, true)));

        verify(recordMapper).updateState(1L, 1, 1000L, 9L);
        verify(factService).appendFact("1000", 7L, "article", "t1", 9L, 1, 0, 1, 1_700_000_000L);
        verify(inboxMapper).markDone("1000");
    }

    @Test
    @DisplayName("inbox 冲突 + status=1 ⇒ 整条跳过，**不重复应用**状态变更")
    void alreadyAppliedMessageIsSkipped() {
        when(inboxMapper.insertInboxIfAbsent(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(0);
        LikeConsumeInboxEntity prior = new LikeConsumeInboxEntity();
        prior.setMsgId("1000");
        // ⚠️ 必须用**真实**哈希：Go 的判定顺序是「先比 hash，再看 status」，
        // 填一个假的 hash 会先命中冲突分支，测的就不是 status==1 这条路径了。
        prior.setPayloadHash(new LikeMessageDigest(new ObjectMapper())
                .hash(7L, "article", "t1", 9L, 1, 1_700_000_000L, true));
        prior.setStatus(1);
        when(inboxMapper.selectInboxForUpdate("1000")).thenReturn(prior);

        service.processBatch(List.of(payload(1, true)));

        verify(recordMapper, never()).selectForUpdateIncludingDeleted(anyLong(), anyString(), anyString());
        verify(recordMapper, never()).updateState(anyLong(), anyInt(), anyLong(), anyLong());
        verify(factService, never()).appendFact(anyString(), anyLong(), anyString(), anyString(),
                anyLong(), anyInt(), anyInt(), anyInt(), anyLong());
    }

    @Test
    @DisplayName("⚠️ 同 msg_id 但 payload_hash 不同 ⇒ 报错（消息 ID 被复用），不是跳过")
    void conflictingPayloadIsRejected() {
        when(inboxMapper.insertInboxIfAbsent(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(0);
        LikeConsumeInboxEntity prior = new LikeConsumeInboxEntity();
        prior.setMsgId("1000");
        prior.setPayloadHash("deadbeef");
        prior.setStatus(0);   // 上次没跑完
        when(inboxMapper.selectInboxForUpdate("1000")).thenReturn(prior);

        IllegalStateException err = assertThrows(IllegalStateException.class,
                () -> service.processBatch(List.of(payload(1, true))));

        // Go: "like message %s reused with conflicting payload"
        assertEquals(true, err.getMessage().contains("reused with conflicting payload"),
                "实际: " + err.getMessage());
        verify(recordMapper, never()).updateState(anyLong(), anyInt(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("payload_hash 为空的既有行不参与冲突判定（Go: prior.PayloadHash != \"\" 才比）")
    void emptyPriorHashIsNotAConflict() {
        when(inboxMapper.insertInboxIfAbsent(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(0);
        LikeConsumeInboxEntity prior = new LikeConsumeInboxEntity();
        prior.setMsgId("1000");
        prior.setPayloadHash("");
        prior.setStatus(0);
        when(inboxMapper.selectInboxForUpdate("1000")).thenReturn(prior);
        when(recordMapper.selectForUpdateIncludingDeleted(anyLong(), anyString(), anyString()))
                .thenReturn(existing(0, 0, false));

        service.processBatch(List.of(payload(1, true)));

        verify(recordMapper).updateState(1L, 1, 1000L, 9L);
    }

    // ==================== 乱序与 operation_id ====================

    @Test
    @DisplayName("operation_id 不大于 last_operation_id ⇒ 只标记完成，不回放状态变更")
    void staleOperationIsOnlyMarkedDone() {
        when(inboxMapper.insertInboxIfAbsent(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(1);
        // lastOperationId=2000 > 本次 1000 ⇒ 这是条乱序的旧消息
        when(recordMapper.selectForUpdateIncludingDeleted(anyLong(), anyString(), anyString()))
                .thenReturn(existing(1, 2000, false));

        service.processBatch(List.of(payload(2, false)));

        verify(recordMapper, never()).updateState(anyLong(), anyInt(), anyLong(), anyLong());
        verify(factService, never()).appendFact(anyString(), anyLong(), anyString(), anyString(),
                anyLong(), anyInt(), anyInt(), anyInt(), anyLong());
        verify(inboxMapper).markDone("1000");
    }

    @Test
    @DisplayName("msg_id 非法（非数字或 <=0）⇒ 直接报错，不写 inbox")
    void invalidOperationIdIsRejectedBeforeAnyWrite() {
        LikeMessageIngestService.LikeProcessPayload bad = payload(1, true);
        bad.msgId = "not-a-number";
        IllegalStateException err = assertThrows(IllegalStateException.class,
                () -> service.processBatch(List.of(bad)));
        assertEquals(true, err.getMessage().contains("invalid like operation id"), err.getMessage());

        verify(inboxMapper, never()).insertInboxIfAbsent(anyString(), anyString(), anyString(), anyString());
    }

    // ==================== 状态机 ====================

    @Test
    @DisplayName("状态机：unlike 只在 oldState==1 时归 0；其它情况是无操作且不发事实")
    void unlikeOnlyRevertsAnActualLike() {
        // oldState=0 + unlike ⇒ newState 仍为 0 ⇒ 不产生事实
        when(inboxMapper.insertInboxIfAbsent(anyString(), anyString(), anyString(), anyString())).thenReturn(1);
        when(recordMapper.selectForUpdateIncludingDeleted(anyLong(), anyString(), anyString()))
                .thenReturn(existing(0, 0, false));

        service.processBatch(List.of(payload(2, false)));

        verify(recordMapper).updateState(1L, 0, 1000L, 9L);   // state 不变，但 last_operation_id 前进了
        verify(factService, never()).appendFact(anyString(), anyLong(), anyString(), anyString(),
                anyLong(), anyInt(), anyInt(), anyInt(), anyLong());
    }

    @Test
    @DisplayName("undislike 只在 oldState==2 时归 0，且 state==2 必须有 last_operation_id 才不被判为遗留数据")
    void undislikeRevertsDislikeAndLegacyStateIsGuarded() {
        when(inboxMapper.insertInboxIfAbsent(anyString(), anyString(), anyString(), anyString())).thenReturn(1);
        // state=2 且 last_operation_id=0 ⇒ Go 判定为 legacy，要求人工对齐
        when(recordMapper.selectForUpdateIncludingDeleted(anyLong(), anyString(), anyString()))
                .thenReturn(existing(2, 0, false));

        IllegalStateException err = assertThrows(IllegalStateException.class,
                () -> service.processBatch(List.of(payload(4, false))));
        assertEquals(true, err.getMessage().contains("requires reconciliation"), err.getMessage());
        verify(recordMapper, never()).updateState(anyLong(), anyInt(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("软删记录 ⇒ 报错，绝不当作新行重建")
    void softDeletedRecordIsRejected() {
        when(inboxMapper.insertInboxIfAbsent(anyString(), anyString(), anyString(), anyString())).thenReturn(1);
        when(recordMapper.selectForUpdateIncludingDeleted(anyLong(), anyString(), anyString()))
                .thenReturn(existing(1, 500, true));

        IllegalStateException err = assertThrows(IllegalStateException.class,
                () -> service.processBatch(List.of(payload(1, true))));
        assertEquals(true, err.getMessage().contains("soft deleted"), err.getMessage());
    }

    // ==================== 首次点赞 outbox ====================

    @Test
    @DisplayName("只有 0→1 且带 firstLikeOutbox 时才写热点事件；1→0 不写")
    void hotEventOutboxOnlyOnFirstLike() {
        LikeMessageIngestService.LikeProcessPayload p = payload(1, true);
        LikeMessageIngestService.LikeOutboxDraft draft = new LikeMessageIngestService.LikeOutboxDraft();
        draft.eventId = "555";
        draft.eventKey = "first_like:7:article:t1";
        draft.eventType = "article_hot_like";
        draft.aggregateId = "t1";
        draft.payload = "{}";
        p.firstLikeOutbox = draft;

        when(inboxMapper.insertInboxIfAbsent(anyString(), anyString(), anyString(), anyString())).thenReturn(1);
        when(recordMapper.selectForUpdateIncludingDeleted(anyLong(), anyString(), anyString()))
                .thenReturn(existing(0, 0, false));

        service.processBatch(List.of(p));

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(outboxMapper).insertIfAbsent(Mockito.eq("555"), key.capture(),
                Mockito.eq("article_hot_like"), Mockito.eq("t1"), Mockito.eq("{}"));
        assertEquals("first_like:7:article:t1", key.getValue());
    }

    @Test
    @DisplayName("buildFirstLikeOutbox：只有 action==1 && isFirst 才产出草稿，且 event_key 格式固定")
    void outboxDraftIsBuiltOnlyForFirstLike() {
        assertEquals(null, service.buildFirstLikeOutbox(payload(1, false)), "非首次点赞不建草稿");
        assertEquals(null, service.buildFirstLikeOutbox(payload(3, true)), "点踩不建草稿");

        LikeMessageIngestService.LikeOutboxDraft draft = service.buildFirstLikeOutbox(payload(1, true));
        assertEquals("first_like:7:article:t1", draft.eventKey);
        assertEquals("article_hot_like", draft.eventType);
        assertEquals("t1", draft.aggregateId);
    }
}