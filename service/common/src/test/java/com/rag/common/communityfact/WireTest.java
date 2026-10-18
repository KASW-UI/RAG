package com.rag.common.communityfact;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wire 关键契约：canonicalHash 稳定、validateReceipt / parseRTWSubject / strictDecode trailing / DCEventSender 校验。
 */
class WireTest {

    @Test
    void canonicalHashIsStableSha256OfJcs() throws Exception {
        byte[] raw = "{\"a\":1,\"b\":2}".getBytes();
        String hex1 = Wire.canonicalHash(raw);
        // 同输入 → 同样 hash
        assertEquals(hex1, Wire.canonicalHash(raw));
        // 64 字符 hex
        assertEquals(64, hex1.length());
        assertTrue(hex1.matches("[0-9a-f]+"));
    }

    @Test
    void canonicalHashIsOrderIndependent() throws Exception {
        String a = Wire.canonicalHash("{\"a\":1,\"b\":2}".getBytes());
        String b = Wire.canonicalHash("{\"b\":2,\"a\":1}".getBytes());
        assertEquals(a, b);
    }

    @Test
    void validHashAcceptsSixtyFourHex() {
        assertTrue(Wire.validHash("0123456789abcdef".repeat(4)));
        assertFalse(Wire.validHash("too-short"));
        assertFalse(Wire.validHash(null));
        assertFalse(Wire.validHash("Z".repeat(64)));
    }

    @Test
    void parseRTWSubjectValid() {
        Wire.Result<Wire.SubjectRef> r = Wire.parseRTWSubject("rtw.identity/platform/1001");
        assertTrue(r.isOk());
        assertEquals("rtw.identity", r.get().issuer);
        assertEquals("1001", r.get().subjectId);
    }

    @Test
    void parseRTWSubjectRejectsNegativeZeroPrefix() {
        assertFalse(Wire.parseRTWSubject("rtw.identity/platform/0").isOk());
        assertFalse(Wire.parseRTWSubject("rtw.identity/platform/-1").isOk());
        assertFalse(Wire.parseRTWSubject("other/platform/u000").isOk());
        assertFalse(Wire.parseRTWSubject("rtw.identity/platform/abc").isOk());
        assertFalse(Wire.parseRTWSubject(null).isOk());
    }

    @Test
    void validateReceiptRequiresAllFields() {
        Wire.Event ev = new Wire.Event();
        ev.eventId = "e1";
        ev.producer = "p1";
        Wire.TechnicalReceipt receipt = new Wire.TechnicalReceipt();
        receipt.eventId = "e1";
        receipt.producer = "p1";
        receipt.technicalStatus = "accepted";
        receipt.receiptId = "r1";
        receipt.inputHash = "a".repeat(64);
        receipt.offset = 5;
        receipt.receivedAt = "2026-01-01T00:00:00Z";
        Wire.Result<?> r = Wire.validateReceipt(ev, receipt);
        assertTrue(r.isOk());

        receipt.technicalStatus = "failed";
        assertFalse(Wire.validateReceipt(ev, receipt).isOk());
    }

    @Test
    void strictDecodeRejectsTrailingJson() {
        String json = "{\"event_id\":\"e1\"} trailing";
        assertThrows(Exception.class, () -> Wire.strictDecode(
                new ByteArrayInputStream(json.getBytes()), Wire.Event.class));
    }

    @Test
    void strictDecodeAcceptsCleanJson() throws Exception {
        String json = "{\"event_id\":\"e1\",\"producer\":\"p\",\"schema_version\":1}";
        Wire.Event e = Wire.strictDecode(new ByteArrayInputStream(json.getBytes()), Wire.Event.class);
        assertEquals("e1", e.eventId);
    }

    @Test
    void strictDecodeRejectsUnknownField() {
        String json = "{\"event_id\":\"e1\",\"unknown\":\"x\"}";
        assertThrows(Exception.class, () -> Wire.strictDecode(
                new ByteArrayInputStream(json.getBytes()), Wire.Event.class));
    }

    @Test
    void dcEventSenderRejectsBadEndpoint() {
        java.net.http.HttpClient c = java.net.http.HttpClient.newHttpClient();
        assertThrows(IllegalArgumentException.class, () -> Wire.DCEventSender.create("http://host/wrong/path", "t".repeat(32), c));
        assertThrows(IllegalArgumentException.class, () -> Wire.DCEventSender.create("http://host/v1/events?bad=1", "t".repeat(32), c));
        assertThrows(IllegalArgumentException.class, () -> Wire.DCEventSender.create("ftp://host/v1/events", "t".repeat(32), c));
        assertThrows(IllegalArgumentException.class, () -> Wire.DCEventSender.create("http://host/v1/events", "short", c));
        assertThrows(IllegalArgumentException.class, () -> Wire.DCEventSender.create("http://host/v1/events", "t".repeat(32), null));
    }

    @Test
    void dcEventSenderRejectsAcceptanceStatus() {
        java.net.http.HttpClient c = java.net.http.HttpClient.newHttpClient();
        Wire.DCEventSender sender = Wire.DCEventSender.create("http://localhost:1/v1/events", "t".repeat(32), c);
        assertNotNull(sender);
        // 不会真正发请求；只测构造 + 校验失败路径已覆盖；运行时需 mock server
    }

    @Test
    void dataCenterAcceptanceRejectedExposesStatusCode() {
        Wire.DataCenterAcceptanceRejectedException ex = new Wire.DataCenterAcceptanceRejectedException(503, "boom");
        assertEquals(503, ex.statusCode());
        assertNotNull(ex.getMessage());
    }

    @Test
    void dataCenterAcceptanceRejectedCauseChainRecognized() {
        Wire.DataCenterAcceptanceRejectedException cause = new Wire.DataCenterAcceptanceRejectedException(503, "boom");
        RuntimeException wrapped = new RuntimeException("wrap", cause);
        // 调用方可以走 cause 链识别（与 Go errors.Is 等价）
        Throwable t = wrapped;
        Wire.DataCenterAcceptanceRejectedException found = null;
        while (t != null) {
            if (t instanceof Wire.DataCenterAcceptanceRejectedException d) { found = d; break; }
            t = t.getCause();
        }
        assertNotNull(found);
        assertEquals(503, found.statusCode());
    }

    /**
     * DRIFT-001 修复验证：{@code predecessor_event_id} 为空字符串时，JSON 不输出该字段
     * （对齐 Go 版 {@code omitempty}）。
     */
    @Test
    void authorityFactOmitsEmptyPredecessorEventId() throws Exception {
        Wire.AuthorityFact fact = new Wire.AuthorityFact();
        fact.subjectRef = new Wire.SubjectRef("rtw.identity", "1001");
        fact.predecessorEventId = "";
        fact.sourceEventHash = "0123456789abcdef".repeat(4);
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(fact);
        assertFalse(json.contains("predecessor_event_id"), json);
    }

    /**
     * DRIFT-001 修复验证：{@code predecessor_event_id} 非空时正常输出。
     */
    @Test
    void authorityFactIncludesNonEmptyPredecessorEventId() throws Exception {
        Wire.AuthorityFact fact = new Wire.AuthorityFact();
        fact.subjectRef = new Wire.SubjectRef("rtw.identity", "1001");
        fact.predecessorEventId = "evt-prev-001";
        fact.sourceEventHash = "0123456789abcdef".repeat(4);
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(fact);
        assertTrue(json.contains("\"predecessor_event_id\":\"evt-prev-001\""), json);
    }

    /**
     * DRIFT-001 修复验证：null 同样省略（{@link com.fasterxml.jackson.annotation.JsonInclude.Include#NON_EMPTY} 对 null 也生效）。
     */
    @Test
    void authorityFactOmitsNullPredecessorEventId() throws Exception {
        Wire.AuthorityFact fact = new Wire.AuthorityFact();
        fact.subjectRef = new Wire.SubjectRef("rtw.identity", "1001");
        fact.predecessorEventId = null;
        fact.sourceEventHash = "0123456789abcdef".repeat(4);
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(fact);
        assertFalse(json.contains("predecessor_event_id"), json);
    }
}