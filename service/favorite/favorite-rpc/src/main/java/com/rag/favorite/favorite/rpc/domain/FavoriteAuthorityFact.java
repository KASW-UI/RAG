package com.rag.favorite.favorite.rpc.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.rag.common.communityfact.Wire;

/**
 * 🔴 权威事实的 <b>v1 形状</b> —— 对应 Go
 * {@code service/favorite/rpc/internal/model/favorite_authority.go:27-43} 的
 * {@code FavoriteAuthorityFact} 与 {@code :106-112} 的 {@code FavoriteSubjectRef}。
 *
 * <h2>为什么不能用 {@code Wire.AuthorityFact}</h2>
 *
 * <p>{@code common/communityfact/Wire.AuthorityFact} 的 {@code subject_ref} 是
 * {@code Wire.SubjectRef}，只有 <b>2 个字段</b>：
 * <pre>
 * { "issuer": "rtw.identity", "subject_id": "1001" }
 * </pre>
 * 而 Go favorite 的 v1 权威响应里 {@code subject_ref} 是 <b>3 个字段</b>：
 * <pre>
 * { "authority_id": "rtw.identity", "tenant_id": "platform", "subject_id": "1001" }
 * </pre>
 * （Go {@code favorite_fact.go:78-82}；样例见 Go 的
 * {@code FACT_AUTHORITY_ACCEPTANCE.md:33,45}。）
 *
 * <p>⇒ {@code /internal/v1/favorite/facts/{producer}/{event_id}} 的响应体
 * <b>不能</b>用 {@code Wire.AuthorityFact}；只有 {@code /internal/v2/...} 才可以
 * （v2 的 payload 里 {@code subject_ref} 本来就是 2 字段，
 * 见 {@code FavoriteSubjectRefV2} 的注释「The legacy fixed platform slot is
 * deliberately absent」）。
 *
 * <p>⚠️ 这是一个 <b>L1 响应体形状</b>：下游 binder（BTW）按字段名解析。
 * 多写、少写、改名都是 wire 破坏。
 *
 * <h2>{@code event} / {@code technical_receipt} 直接复用 {@code Wire}</h2>
 *
 * <p>逐字段核对过（docs/FileTree/favorite/rpc.md §12.1）：Go 的
 * {@code FavoriteWireEvent}（favorite_delivery.go:26-36，9 字段）与
 * {@code FavoriteTechnicalReceipt}（:38-46，7 字段）与 {@code Wire.Event} /
 * {@code Wire.TechnicalReceipt} <b>逐字段逐 tag 一致</b> ⇒ 直接复用，不另立类型。
 */
public final class FavoriteAuthorityFact {

    private FavoriteAuthorityFact() {
        // 响应体，只用于序列化
    }

    /**
     * Go {@code FavoriteSubjectRef}（favorite_fact.go:78-82）。
     *
     * <p><b>三字段是 v1 的硬契约</b>：{@code tenant_id} 在 v2 里被刻意去掉
     * （{@code FavoriteSubjectRefV2} 的注释明写「The legacy fixed platform slot is
     * deliberately absent from the v2 EventSpec」）。
     */
    public static final class SubjectRefV1 {

        @JsonProperty("authority_id")
        public String authorityId;

        @JsonProperty("tenant_id")
        public String tenantId;

        @JsonProperty("subject_id")
        public String subjectId;

        public SubjectRefV1() {
        }

        public SubjectRefV1(String authorityId, String tenantId, String subjectId) {
            this.authorityId = authorityId;
            this.tenantId = tenantId;
            this.subjectId = subjectId;
        }

        /**
         * 🔴 Go 在比较时用<b>值相等</b>（favorite_authority.go:275
         * {@code prior.SubjectRef != fact.SubjectRef}）。
         *
         * <p>Go 的 struct 可直接用 {@code !=}，Java 侧必须显式给 {@code equals}，
         * 否则引用比较永远不等 ⇒ <b>retract 的前驱一致性校验会永远失败</b>
         * ⇒ 所有 retract 的权威读都返回 404。
         * ⇒ 这个方法不是"锦上添花"，它是那条断言的正确性载体。
         */
        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof SubjectRefV1 other)) {
                return false;
            }
            return eq(authorityId, other.authorityId)
                    && eq(tenantId, other.tenantId)
                    && eq(subjectID(), other.subjectID());
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(authorityId, tenantId, subjectID());
        }

        private String subjectID() {
            return subjectId == null ? "" : subjectId;
        }

        private static boolean eq(String a, String b) {
            return a == null ? b == null : a.equals(b);
        }
    }

    /**
     * Go {@code FavoriteAuthorityFact}（favorite_authority.go:27-33）。
     *
     * <p>字段顺序与 Go 的 struct 声明顺序逐条一致（JSON 对象顺序无语义，但便于逐行 review）。
     */
    public static final class FactV1 {

        /** Go {@code Event FavoriteWireEvent `json:"event"`} ⇒ 复用 {@code Wire.Event}。 */
        @JsonProperty("event")
        public Wire.Event event;

        /** Go {@code SubjectRef FavoriteSubjectRef `json:"subject_ref"`} ⇒ 见 {@link SubjectRefV1}。 */
        @JsonProperty("subject_ref")
        public SubjectRefV1 subjectRef;

        /**
         * Go {@code PredecessorEventID string `json:"predecessor_event_id,omitempty"`}。
         *
         * <p>⚠️ {@code omitempty}：assert 行<b>不带</b>这个字段（favorite_authority.go:173-175
         * 只在 {@code AggregateVersion == 2} 时赋值）⇒ 必须用
         * {@link JsonInclude.Include#NON_EMPTY}，与 Go 的 omitempty 语义一致。
         */
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        @JsonProperty("predecessor_event_id")
        public String predecessorEventId;

        /** Go {@code TechnicalReceipt FavoriteTechnicalReceipt `json:"technical_receipt"`}。 */
        @JsonProperty("technical_receipt")
        public Wire.TechnicalReceipt technicalReceipt;

        /**
         * Go {@code SourceEventHash string `json:"source_event_hash"`}。
         *
         * <p>= {@code favoriteJCSHash([]byte(row.Payload))}，且必须与
         * {@code row.TechnicalInputHash} 相等（favorite_authority.go:164-166）——
         * 这就是「RTW 的不可变 outbox」与「DC 的技术回执」互相印证的地方。
         */
        @JsonProperty("source_event_hash")
        public String sourceEventHash;

        public FactV1() {
        }

        public FactV1(Wire.Event event, SubjectRefV1 subjectRef, String predecessorEventId,
                      Wire.TechnicalReceipt technicalReceipt, String sourceEventHash) {
            this.event = event;
            this.subjectRef = subjectRef;
            this.predecessorEventId = predecessorEventId;
            this.technicalReceipt = technicalReceipt;
            this.sourceEventHash = sourceEventHash;
        }
    }

    /**
     * 🔴 对应 Go {@code AuthoritativeFavoriteFactV2}（favorite_authority.go:195-204）——
     * 把 v1 的权威事实<b>压扁</b>成 v2 形状。
     *
     * <pre>
     * return FavoriteAuthorityFactV2{Event: fact.Event,
     *     SubjectRef:         FavoriteSubjectRefV2{Issuer: fact.SubjectRef.AuthorityID, SubjectID: fact.SubjectRef.SubjectID},
     *     PredecessorEventID: fact.PredecessorEventID,
     *     TechnicalReceipt:   fact.TechnicalReceipt,
     *     SourceEventHash:    fact.SourceEventHash}, nil
     * </pre>
     *
     * <p>⇒ <b>只有 {@code subject_ref} 变形状</b>：三字段 → 两字段
     * （{@code authority_id} <b>改名</b>成 {@code issuer}，<b>{@code tenant_id} 被丢弃</b>），
     * 其余 4 个字段原样带过。
     *
     * <h2>为什么可以复用 {@code Wire.AuthorityFact}</h2>
     * <p>它与 Go 的 {@code FavoriteAuthorityFactV2} <b>逐字段逐 tag 一致</b>
     * （event / subject_ref / predecessor_event_id(omitempty) / technical_receipt / source_event_hash）
     * ⇒ <b>不另立类型</b> —— 与本类复用 {@code Wire.Event} / {@code Wire.TechnicalReceipt} 同一理由。
     *
     * <h2>⚠️ {@code tenant_id} 为什么被丢弃</h2>
     * <p>Go 的 {@code FavoriteSubjectRefV2} 注释明写：
     * <pre>
     * // FavoriteSubjectRefV2 contains only the RTW-owned identity pair. The legacy
     * // fixed platform slot is deliberately absent from the v2 EventSpec.
     * </pre>
     * ⇒ v2 的 {@code subject_ref} <b>本就没有</b> {@code tenant_id}。
     * ⇒ <b>保留它就是 wire 破坏</b>：下游 binder（BTW）按字段名解析，多一个字段就失配。
     */
    public static Wire.AuthorityFact toV2(FactV1 v1) {
        if (v1 == null) {
            throw new IllegalArgumentException("null favorite authority fact");
        }
        Wire.AuthorityFact v2 = new Wire.AuthorityFact();
        v2.event = v1.event;
        Wire.SubjectRef subjectRef = new Wire.SubjectRef();
        if (v1.subjectRef != null) {
            // Go: service/favorite/rpc/internal/model/favorite_authority.go:201 —— Issuer 来自 v1 的 AuthorityID，SubjectID 来自 v1 的 SubjectID。
            // ⚠️ v1 的 TenantID 在 Go 侧【没有被带过来】—— 不要"顺手"补上。
            subjectRef.issuer = v1.subjectRef.authorityId;
            subjectRef.subjectId = v1.subjectRef.subjectId;
        }
        v2.subjectRef = subjectRef;
        v2.predecessorEventId = v1.predecessorEventId;
        v2.technicalReceipt = v1.technicalReceipt;
        v2.sourceEventHash = v1.sourceEventHash;
        return v2;
    }
}