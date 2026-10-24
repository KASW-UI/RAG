package com.rag.favorite.favorite.rpc.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.rag.common.communityfact.Wire;
import com.rag.favorite.favorite.rpc.domain.FavoriteAuthorityFact;
import com.rag.favorite.favorite.rpc.domain.FavoriteFactOutboxEntity;
import com.rag.favorite.favorite.rpc.domain.FavoriteItemEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 🔴 冻结信封链路的<b>确定性语义</b>回归网 —— 覆盖 Go
 * {@code service/favorite/rpc/internal/model/favorite_fact.go} +
 * {@code favorite_delivery.go} + {@code favorite_authority.go} 中
 * <b>不依赖数据库</b>的全部判定。
 *
 * <p><b>为什么不需要真 PG</b>：Go 侧这 10 个验收资产
 * （{@code FAVORITE_FACT_ACCEPTANCE.md} / {@code FAVORITE_REVISION_ACCEPTANCE.md} /
 * {@code FAVORITE_SUBJECTREF_V2_ACCEPTANCE.md} 等）在 RAG 侧<b>不可等价迁移</b>
 * （它们依赖 PG16 二进制与跨仓 DataCenter 检出，见
 * docs/FileTree/favorite/rpc.md §12.3 末表）。
 * ⇒ 本类承接其中<b>纯函数部分</b>的行为断言，事务与并发部分由
 * {@code FavoriteFactSchemaAndChainTest}（Testcontainers 真 PG）承接。
 *
 * <p>⚠️ 本类<b>不</b>断言「能编译通过」或「Bean 存在」——
 * 那是 §3.3.3 点名的伪断言形态。每一���用例都必须能说清：
 * 「哪一行 Go 代码 ⇒ 哪个可观察行为」。
 */
class FavoriteFactSemanticsTest {

    private final FavoriteFactEnvelopeFactory factory = new FavoriteFactEnvelopeFactory();
    private final FavoriteDeliveryValidator validator = new FavoriteDeliveryValidator();

    // ==================== 归一化：L2 业务语义 ====================

    @Nested
    @DisplayName("归一化 —— Go logic/helper.go:56-62")
    class Normalization {

        @Test
        @DisplayName("normalizeFolderName 只 TrimSpace，不小写、不压缩内部空白")
        void folderNameIsTrimOnly() {
            // Go: return strings.TrimSpace(name)
            assertEquals("My Folder", FavoriteRpcSupport.normalizeFolderName("  My Folder  "));
            // ⚠️ 不可"顺手"加 toLowerCase —— 唯一键 uk_user_folder_name 建在规范化后的值上
            assertEquals("MyFolder", FavoriteRpcSupport.normalizeFolderName("MyFolder"));
            assertEquals("A  B", FavoriteRpcSupport.normalizeFolderName("  A  B "));
            assertEquals("", FavoriteRpcSupport.normalizeFolderName("   "));
            assertEquals("", FavoriteRpcSupport.normalizeFolderName(null));
        }

        @Test
        @DisplayName("normalizeTargetType 强制小写 —— 这是唯一键 uk_folder_target 的前提")
        void targetTypeIsLowercased() {
            // Go: strings.ToLower(strings.TrimSpace(targetType))
            assertEquals("article", FavoriteRpcSupport.normalizeTargetType("  Article  "));
            assertEquals("post", FavoriteRpcSupport.normalizeTargetType("POST"));
            // 🔴 Locale 必须与 Go 的 Unicode ToLower 一致；土耳其语 locale 下 "I".toLowerCase() = "ı"
            assertEquals("video", FavoriteRpcSupport.normalizeTargetType("VİDEO"));
            assertEquals("", FavoriteRpcSupport.normalizeTargetType("   "));
            assertEquals("", FavoriteRpcSupport.normalizeTargetType(null));
        }
    }

    // ==================== RFC3339Nano：L1 wire ====================

    @Nested
    @DisplayName("rfc3339Nano —— Go 的 time.RFC3339Nano 版式")
    class Rfc3339Nano {

        @Test
        @DisplayName("纳秒尾零被裁掉，整秒不输出小数点")
        void matchesGoLayout() {
            // Go 的 ".999999999" 语义：去掉尾随零；全零时整个小数部分消失
            assertEquals("2026-10-23T10:00:00Z",
                    FavoriteFactEnvelopeFactory.rfc3339Nano(Instant.parse("2026-10-23T10:00:00Z")));
            assertEquals("2026-10-23T10:00:00.5Z",
                    FavoriteFactEnvelopeFactory.rfc3339Nano(Instant.parse("2026-10-23T10:00:00.500Z")));
            // ⚠️ .123456 的尾零是 0 吗？不是 —— 末位是 6，整段保留。
            //    被裁的只有**末尾连续的 0**（下面的 .123000000 → .123 才演示这一点）
            assertEquals("2026-10-23T10:00:00.123456Z",
                    FavoriteFactEnvelopeFactory.rfc3339Nano(Instant.parse("2026-10-23T10:00:00.123456Z")));
            assertEquals("2026-10-23T10:00:00.123Z",
                    FavoriteFactEnvelopeFactory.rfc3339Nano(Instant.parse("2026-10-23T10:00:00.123000000Z")));
            assertEquals("2026-10-23T10:00:00.123456789Z",
                    FavoriteFactEnvelopeFactory.rfc3339Nano(Instant.parse("2026-10-23T10:00:00.123456789Z")));
        }

        @Test
        @DisplayName("能被 Go 的 time.Parse(RFC3339Nano) 解析的形态全集")
        void alwaysUtc() {
            // Go 侧一律 now.UTC().Format(...) ⇒ 必须带 Z，不能是 +08:00
            assertTrue(FavoriteFactEnvelopeFactory.rfc3339Nano(Instant.now()).endsWith("Z"));
        }
    }

    // ==================== parseAuthorityID：favorite_authority.go:74-94 ====================

    @Nested
    @DisplayName("parseAuthorityId —— 规范十进制 + 2^53 上限")
    class ParseAuthorityId {

        private JsonNode node(String raw) {
            try {
                return FavoriteRpcSupport.STRICT_JSON.readTree(raw);
            } catch (Exception e) {
                throw new AssertionError("bad test json", e);
            }
        }

        @Test
        @DisplayName("十进制字符串：任意精度都接受（含 > 2^53 的雪花 ID）")
        void acceptsDecimalStrings() {
            // Go: raw[0] == '"' ⇒ 解包成 text，再走 ParseInt
            assertEquals(1001L, FavoriteDeliveryValidator.parseAuthorityId(node("\"1001\"")));
            // 🔴 生产雪花 ID 超过 2^53 ⇒ 只有字符串形态才允许
            assertEquals(9007199254740993L,
                    FavoriteDeliveryValidator.parseAuthorityId(node("\"9007199254740993\"")));
        }

        @Test
        @DisplayName("裸 JSON 数字：必须 <= 2^53-1")
        void rejectsBareNumbersAbove53Bits() {
            // Go: if raw[0] != '"' && id > maxExactJSONInteger { return 0, false }
            assertEquals(1001L, FavoriteDeliveryValidator.parseAuthorityId(node("1001")));
            assertEquals(9007199254740991L,
                    FavoriteDeliveryValidator.parseAuthorityId(node("9007199254740991")));
            // 2^53 = 9007199254740992 超过上限 ⇒ 拒绝
            assertEquals(0L, FavoriteDeliveryValidator.parseAuthorityId(node("9007199254740992")));
        }

        @Test
        @DisplayName("非规范十进制一律拒绝：前导零 / 正号 / 负数 / 空串")
        void rejectsNonCanonicalForms() {
            // Go: strconv.FormatInt(id, 10) != text ⇒ false
            assertEquals(0L, FavoriteDeliveryValidator.parseAuthorityId(node("\"01001\"")));
            assertEquals(0L, FavoriteDeliveryValidator.parseAuthorityId(node("\"+1001\"")));
            assertEquals(0L, FavoriteDeliveryValidator.parseAuthorityId(node("\"-1\"")));
            assertEquals(0L, FavoriteDeliveryValidator.parseAuthorityId(node("\"0\"")));
            assertEquals(0L, FavoriteDeliveryValidator.parseAuthorityId(node("\"\"")));
            assertEquals(0L, FavoriteDeliveryValidator.parseAuthorityId(node("null")));
            assertEquals(0L, FavoriteDeliveryValidator.parseAuthorityId(null));
            // 非字符串非数字（bool / object / array）
            assertEquals(0L, FavoriteDeliveryValidator.parseAuthorityId(node("true")));
            assertEquals(0L, FavoriteDeliveryValidator.parseAuthorityId(node("{}")));
            assertEquals(0L, FavoriteDeliveryValidator.parseAuthorityId(node("[]")));
        }
    }

    // ==================== favoriteSubjectV1：favorite_authority.go:105-124 ====================

    @Nested
    @DisplayName("favoriteSubjectV1 —— v1 三字段 / v2 两字段的归一化")
    class SubjectV1 {

        private JsonNode node(String raw) {
            try {
                return FavoriteRpcSupport.STRICT_JSON.readTree(raw);
            } catch (Exception e) {
                throw new AssertionError("bad test json", e);
            }
        }

        @Test
        @DisplayName("schema=1 接受三字段，且 authority_id/tenant_id 必须字面匹配")
        void acceptsV1Shape() {
            FavoriteDeliveryValidator.SubjectResult ok = FavoriteDeliveryValidator.subjectV1(
                    1, node("{\"authority_id\":\"rtw.identity\",\"tenant_id\":\"platform\",\"subject_id\":\"1001\"}"));
            assertTrue(ok.ok);
            assertEquals("rtw.identity", ok.value.authorityId);
            assertEquals("platform", ok.value.tenantId);
            assertEquals("1001", ok.value.subjectId);

            // tenant_id 必须是 "platform" —— Go: legacy.TenantID != "platform" ⇒ false
            assertFalse(FavoriteDeliveryValidator.subjectV1(
                    1, node("{\"authority_id\":\"rtw.identity\",\"tenant_id\":\"other\",\"subject_id\":\"1001\"}")).ok);
        }

        @Test
        @DisplayName("schema=2 接受两字段，并归一化成 v1 三元组")
        void acceptsV2Shape() {
            // Go: :118 —— legacy = {v2.Issuer, "platform", v2.SubjectID}
            FavoriteDeliveryValidator.SubjectResult ok = FavoriteDeliveryValidator.subjectV1(
                    2, node("{\"issuer\":\"rtw.identity\",\"subject_id\":\"1001\"}"));
            assertTrue(ok.ok);
            assertEquals("rtw.identity", ok.value.authorityId);
            assertEquals("platform", ok.value.tenantId);
            assertEquals("1001", ok.value.subjectId);
        }

        @Test
        @DisplayName("🔴 形状与 schema 不匹配即拒绝（v2 对象喂给 schema=1 会因未知字段失败）")
        void rejectsShapeMismatch() {
            // v2 少 tenant_id ⇒ strict decode 因「缺字段外的未知/缺失」路径被拒
            assertFalse(FavoriteDeliveryValidator.subjectV1(
                    1, node("{\"issuer\":\"rtw.identity\",\"subject_id\":\"1001\"}")).ok);
            // v1 对象喂给 schema=2 ⇒ issuer 缺失 ⇒ null ⇒ 拒绝
            assertFalse(FavoriteDeliveryValidator.subjectV1(
                    2, node("{\"authority_id\":\"rtw.identity\",\"tenant_id\":\"platform\",\"subject_id\":\"1\"}")).ok);
            // 其它 schema 值
            assertFalse(FavoriteDeliveryValidator.subjectV1(
                    3, node("{\"authority_id\":\"rtw.identity\",\"tenant_id\":\"platform\",\"subject_id\":\"1\"}")).ok);
        }

        @Test
        @DisplayName("subject_id 必须是非规范正整数的反面：规范正整数")
        void subjectIdMustBeCanonicalPositive() {
            assertFalse(FavoriteDeliveryValidator.subjectV1(
                    1, node("{\"authority_id\":\"rtw.identity\",\"tenant_id\":\"platform\",\"subject_id\":\"0\"}")).ok);
            assertFalse(FavoriteDeliveryValidator.subjectV1(
                    1, node("{\"authority_id\":\"rtw.identity\",\"tenant_id\":\"platform\",\"subject_id\":\"-5\"}")).ok);
            assertFalse(FavoriteDeliveryValidator.subjectV1(
                    1, node("{\"authority_id\":\"rtw.identity\",\"tenant_id\":\"platform\",\"subject_id\":\"007\"}")).ok);
        }

        @Test
        @DisplayName("🔴 SubjectRefV1 的 equals 是值相等 —— retract 前驱一致性校验的正确性载体")
        void subjectRefValueEquality() {
            // Go: prior.SubjectRef != fact.SubjectRef 用的是 struct 值比较。
            // Java 若用引用比较，所有 retract 的权威读都会永远失败。
            FavoriteAuthorityFact.SubjectRefV1 a =
                    new FavoriteAuthorityFact.SubjectRefV1("rtw.identity", "platform", "1001");
            FavoriteAuthorityFact.SubjectRefV1 b =
                    new FavoriteAuthorityFact.SubjectRefV1("rtw.identity", "platform", "1001");
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
            assertFalse(a.equals(new FavoriteAuthorityFact.SubjectRefV1("rtw.identity", "platform", "1002")));
        }
    }

    // ==================== 信封构造：favorite_fact.go:135-198 ====================

    @Nested
    @DisplayName("信封构造 —— v1 / v2 只差 subject_ref")
    class EnvelopeBuild {

        private FavoriteItemEntity item() {
            FavoriteItemEntity item = new FavoriteItemEntity();
            item.setFavoriteId(189238374627920128L);   // 真实雪花 ID：> 2^53
            item.setFolderId(189238374627920129L);
            item.setUserId(1001L);
            item.setTargetId("123456789012345678");
            item.setTargetType("article");
            item.setTargetRevision("123456789012345678:r3");
            item.setTitle("t");
            item.setCover("c");
            return item;
        }

        @Test
        @DisplayName("业务 ID 一律编码成十进制字符串 —— 否则 JCS hash 对 >2^53 会丢精度")
        void businessIdsAreDecimalStrings() throws Exception {
            FavoriteFactOutboxEntity row =
                    factory.build(item(), 1L, "assert", Instant.parse("2026-10-23T10:00:00Z"), 1);
            JsonNode event = FavoriteRpcSupport.STRICT_JSON.readTree(row.getPayload());
            JsonNode payload = event.get("payload");

            assertTrue(event.get("aggregate_id").isTextual(),
                    "aggregate_id 必须是字符串，否则 validDelivery 的逐字比对会失败");
            assertEquals("189238374627920128", event.get("aggregate_id").asText());
            assertTrue(payload.get("favorite_id").isTextual());
            assertTrue(payload.get("folder_id").isTextual());
            assertTrue(payload.get("subject_ref").get("subject_id").isTextual());

            // ⚠️ aggregate_version 是**数字**（不是字符串）—— Go 的 int64
            assertTrue(event.get("aggregate_version").isNumber());
            assertEquals(1, event.get("aggregate_version").asInt());
        }

        @Test
        @DisplayName("v1 的 subject_ref 三字段 / v2 两字段（缺 tenant_id 是 v2 的定义）")
        void subjectRefShapePerSchema() throws Exception {
            JsonNode v1 = FavoriteRpcSupport.STRICT_JSON
                    .readTree(factory.build(item(), 1L, "assert", Instant.parse("2026-10-23T10:00:00Z"), 1)
                            .getPayload())
                    .get("payload").get("subject_ref");
            assertEquals(3, v1.size());
            assertEquals("rtw.identity", v1.get("authority_id").asText());
            assertEquals("platform", v1.get("tenant_id").asText());

            JsonNode v2 = FavoriteRpcSupport.STRICT_JSON
                    .readTree(factory.build(item(), 1L, "assert", Instant.parse("2026-10-23T10:00:00Z"), 2)
                            .getPayload())
                    .get("payload").get("subject_ref");
            assertEquals(2, v2.size());
            assertEquals("rtw.identity", v2.get("issuer").asText());
            // 🔴 多写 tenant_id 会让 strictFavoriteJSON 因未知字段拒绝整条事实
            assertFalse(v2.has("tenant_id"));
        }

        @Test
        @DisplayName("target_revision 为 null 时写 JSON null，不是省略字段")
        void nullRevisionIsWrittenAsJsonNull() throws Exception {
            FavoriteItemEntity item = item();
            item.setTargetRevision(null);
            JsonNode payload = FavoriteRpcSupport.STRICT_JSON
                    .readTree(factory.build(item, 1L, "assert", Instant.parse("2026-10-23T10:00:00Z"), 1)
                            .getPayload())
                    .get("payload");
            // ⚠️ 省略会让同FavoriteItemEntity 的 legacy 快照 hash 与有 null 时不一致
            assertTrue(payload.has("target_revision"));
            assertTrue(payload.get("target_revision").isNull());
        }

        @Test
        @DisplayName("6 条前置断言：version / operation / 非正 ID / 空 target / revision 作用域")
        void preconditionsAreEnforced() {
            FavoriteItemEntity item = item();
            // version ∉ {1,2}
            assertThrows(IllegalArgumentException.class,
                    () -> factory.build(item, 3L, "assert", Instant.now(), 1));
            // operation ∉ {assert, retract}
            assertThrows(IllegalArgumentException.class,
                    () -> factory.build(item, 1L, "delete", Instant.now(), 1));
            // schema ∉ {1,2}
            assertThrows(IllegalArgumentException.class,
                    () -> factory.build(item, 1L, "assert", Instant.now(), 3));

            FavoriteItemEntity badId = item();
            badId.setFavoriteId(0L);
            assertThrows(IllegalArgumentException.class,
                    () -> factory.build(badId, 1L, "assert", Instant.now(), 1));

            FavoriteItemEntity emptyTarget = item();
            emptyTarget.setTargetId("");
            assertThrows(IllegalArgumentException.class,
                    () -> factory.build(emptyTarget, 1L, "assert", Instant.now(), 1));

            // 🔴 target_revision 非空 ⇒ target_type 必须是 article 且 revision 非空串
            FavoriteItemEntity wrongScope = item();
            wrongScope.setTargetType("post");
            assertThrows(IllegalArgumentException.class,
                    () -> factory.build(wrongScope, 1L, "assert", Instant.now(), 1));

            FavoriteItemEntity emptyRevision = item();
            emptyRevision.setTargetRevision("");
            assertThrows(IllegalArgumentException.class,
                    () -> factory.build(emptyRevision, 1L, "assert", Instant.now(), 1));
        }

        @Test
        @DisplayName("legacy 快照 hash 不含 title/cover —— 否则每次改名都要重批标记")
        void legacySnapshotHashIgnoresMutableFields() {
            FavoriteItemEntity a = item();
            a.setTitle("旧标题");
            a.setCover("旧封面");
            FavoriteItemEntity b = item();
            b.setTitle("新标题");
            b.setCover("新封面");
            assertEquals(factory.legacySnapshotHash(a), factory.legacySnapshotHash(b));

            // 但 target_revision 变了 ⇒ hash 必须变（那是不可变锚点）
            FavoriteItemEntity c = item();
            c.setTargetRevision("123456789012345678:r4");
            assertFalse(factory.legacySnapshotHash(a).equals(factory.legacySnapshotHash(c)));
        }
    }

    // ==================== validFavoriteDelivery：favorite_delivery.go:94-116 ====================

    @Nested
    @DisplayName("validFavoriteDelivery —— 12 条交叉校验")
    class ValidDelivery {

        private FavoriteFactOutboxEntity goodRow(int schema) {
            FavoriteItemEntity item = new FavoriteItemEntity();
            item.setFavoriteId(9007199254740993L);    // > 2^53：必须走字符串路径
            item.setFolderId(2001L);
            item.setUserId(1001L);
            item.setTargetId("9001");
            item.setTargetType("article");
            item.setTargetRevision("9001:r1");
            item.setTitle("t");
            item.setCover("c");
            return factory.build(item, 1L, "assert", Instant.parse("2026-10-23T10:00:00Z"), schema);
        }

        @Test
        @DisplayName("自建信封通过全部 12 条")
        void acceptsSelfBuiltEnvelope() {
            for (int schema : new int[]{1, 2}) {
                FavoriteFactOutboxEntity row = goodRow(schema);
                FavoriteDeliveryValidator.FrozenEvent event =
                        FavoriteDeliveryValidator.decodeLenient(row.getPayload());
                assertNotNull(event, "schema=" + schema);
                assertTrue(validator.validDelivery(row, event), "schema=" + schema);
            }
        }

        @Test
        @DisplayName("event_id 与行主键不一致 ⇒ 拒绝")
        void rejectsEventIdMismatch() {
            FavoriteFactOutboxEntity row = goodRow(1);
            FavoriteDeliveryValidator.FrozenEvent event =
                    FavoriteDeliveryValidator.decodeLenient(row.getPayload());
            event.eventId = "favorite.1.v1";
            assertFalse(validator.validDelivery(row, event));
        }

        @Test
        @DisplayName("aggregate_id 必须与行内 favorite_id 逐字一致")
        void rejectsAggregateIdMismatch() {
            FavoriteFactOutboxEntity row = goodRow(1);
            FavoriteDeliveryValidator.FrozenEvent event =
                    FavoriteDeliveryValidator.decodeLenient(row.getPayload());
            event.aggregateId = "9007199254740994";
            assertFalse(validator.validDelivery(row, event));
        }

        @Test
        @DisplayName("producer 固定为 rtw.community.favorite")
        void rejectsForeignProducer() {
            FavoriteFactOutboxEntity row = goodRow(1);
            FavoriteDeliveryValidator.FrozenEvent event =
                    FavoriteDeliveryValidator.decodeLenient(row.getPayload());
            event.producer = "someone.else";
            assertFalse(validator.validDelivery(row, event));
        }

        @Test
        @DisplayName("event_type 只接受 assert / retract")
        void rejectsUnknownEventType() {
            FavoriteFactOutboxEntity row = goodRow(1);
            FavoriteDeliveryValidator.FrozenEvent event =
                    FavoriteDeliveryValidator.decodeLenient(row.getPayload());
            event.eventType = "rtw.favorite.delete";
            assertFalse(validator.validDelivery(row, event));
        }

        @Test
        @DisplayName("occurred_at 不可解析 ⇒ 拒绝")
        void rejectsBadTimestamp() {
            FavoriteFactOutboxEntity row = goodRow(1);
            FavoriteDeliveryValidator.FrozenEvent event =
                    FavoriteDeliveryValidator.decodeLenient(row.getPayload());
            event.occurredAt = "not-a-time";
            assertFalse(validator.validDelivery(row, event));
        }

        @Test
        @DisplayName("payload 里的未知字段 ⇒ strict decode 失败 ⇒ 拒绝（这就是 blocked 的来源）")
        void rejectsUnknownPayloadField() throws Exception {
            FavoriteFactOutboxEntity row = goodRow(1);
            // 在 payload 里塞一个额外字段
            String tampered = row.getPayload().replaceFirst(
                    "\"payload\":\\{", "\"payload\":{\"injected_field\":\"x\",");
            FavoriteDeliveryValidator.FrozenEvent event = FavoriteDeliveryValidator.decodeLenient(tampered);
            assertNotNull(event);
            assertFalse(validator.validDelivery(row, event));
        }

        @Test
        @DisplayName("🔴 投递路径用宽松解码、权威读路径用严格解码 —— 两者不可合并")
        void lenientVsStrict() {
            FavoriteFactOutboxEntity row = goodRow(1);
            String tampered = row.getPayload().replaceFirst(
                    "\"payload\":\\{", "\"payload\":{\"injected_field\":\"x\",");

            // ① 两种解码都能解出**外层**事件：Go 的 payload 是 json.RawMessage，
            //    DisallowUnknownFields 不会进入 RawMessage 内部 —— 两侧在此完全一致。
            assertNotNull(FavoriteDeliveryValidator.decodeLenient(tampered),
                    "投递侧必须能解出 event，随后由 validDelivery 判它不可信");
            assertNotNull(FavoriteDeliveryValidator.decodeStrict(tampered,
                    FavoriteDeliveryValidator.FrozenEvent.class));

            // ② 真正拒绝发生在**第二层**：把 payload 节点解成 authorityPayload。
            //    这一步在 Go 的 validFavoriteDelivery 内部（favorite_delivery.go:109）。
            FavoriteDeliveryValidator.FrozenEvent event =
                    FavoriteDeliveryValidator.decodeStrict(tampered, FavoriteDeliveryValidator.FrozenEvent.class);
            assertThrows(IllegalArgumentException.class,
                    () -> FavoriteDeliveryValidator.decodeStrict(event.payload.toString(),
                            FavoriteDeliveryValidator.AuthorityPayload.class),
                    "payload 里的未知字段必须在这一层被拒");

            // ③ 端到端：validDelivery 判 false ⇒ 投递侧会把该行标成 status=3 blocked
            assertFalse(validator.validDelivery(row, event));
        }

        @Test
        @DisplayName("尾随 JSON 被严格解码拒绝")
        void rejectsTrailingJson() {
            FavoriteFactOutboxEntity row = goodRow(1);
            assertThrows(IllegalArgumentException.class,
                    () -> FavoriteDeliveryValidator.decodeStrict(row.getPayload() + "{}",
                            FavoriteDeliveryValidator.FrozenEvent.class));
        }
    }

    // ==================== sameFavoriteRevision ====================

    @Test
    @DisplayName("🔴 null 与空串不可混用 —— 这正是 target_revision 的语义边界")
    void sameFavoriteRevisionDistinguishesNullFromEmpty() {
        // Go: if left == nil || right == nil { return left == nil && right == nil }
        assertTrue(FavoriteDeliveryValidator.sameFavoriteRevision(null, null));
        assertFalse(FavoriteDeliveryValidator.sameFavoriteRevision(null, ""));
        assertFalse(FavoriteDeliveryValidator.sameFavoriteRevision("", null));
        assertFalse(FavoriteDeliveryValidator.sameFavoriteRevision(null, "r1"));
        assertTrue(FavoriteDeliveryValidator.sameFavoriteRevision("r1", "r1"));
        assertFalse(FavoriteDeliveryValidator.sameFavoriteRevision("r1", "r2"));
    }

    // ==================== JCS hash：favorite_authority.go:179-186 ====================

    @Test
    @DisplayName("JCS hash 与 key 顺序无关，但与值强相关")
    void jcsHashIsCanonical() throws Exception {
        String a = "{\"a\":1,\"b\":2}";
        String b = "{\"b\":2,\"a\":1}";
        assertEquals(Wire.canonicalHash(a.getBytes(StandardCharsets.UTF_8)),
                Wire.canonicalHash(b.getBytes(StandardCharsets.UTF_8)));
        String c = "{\"a\":1,\"b\":3}";
        assertFalse(Wire.canonicalHash(a.getBytes(StandardCharsets.UTF_8))
                .equals(Wire.canonicalHash(c.getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    @DisplayName("严格解码未知码时抛 IllegalArgumentException 而不是静默通过")
    void strictDecodeNeverSilentlyPasses() {
        assertThrows(IllegalArgumentException.class,
                () -> FavoriteDeliveryValidator.decodeStrict("{\"unknown\":1}",
                        FavoriteDeliveryValidator.FrozenEvent.class));
        assertNull(FavoriteDeliveryValidator.decodeLenient("not json at all"));
    }
}