package com.rag.favorite.favorite.rpc.fact;

import com.rag.favorite.favorite.rpc.bootstrap.FavoriteFactProcessLogging;
import com.rag.favorite.favorite.rpc.config.FavoriteRpcProperties;
import com.rag.favorite.favorite.rpc.mapper.FavoriteFactOutboxMapper;
import com.rag.favorite.favorite.rpc.mapper.FavoriteFactOutboxMapper.FavoriteLegacyPreflightRow;
import com.rag.favorite.favorite.rpc.service.FavoriteDeliveryValidator;
import com.rag.favorite.favorite.rpc.service.FavoriteFactEnvelopeFactory;
import com.rag.favorite.favorite.rpc.domain.FavoriteItemEntity;
import com.rag.favorite.favorite.rpc.service.FavoriteRpcSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 🔴 legacy 只读预检 —— 对应 Go
 * {@code service/favorite/rpc/cmd/fact-legacy-preflight/main.go:18-54} 与
 * {@code internal/model/favorite_legacy_preflight.go:30-96} 的 {@code FavoriteLegacyPreflight}。
 *
 * <h2>它回答什么</h2>
 * <p>「当前库里有没有<b>没有 assert 事实</b>的收藏行，以及它们有没有被逐字段签字的 legacy 标记？」
 *
 * <p>Go 注释（favorite_legacy_preflight.go:26-28）说得很直接：
 * <pre>
 * // FavoriteLegacyPreflight never mutates business, markers or Outbox. A clear
 * // local report is only one release gate: real source writers must be frozen
 * // while operators enumerate, approve and re-run the production snapshot.
 * </pre>
 * ⇒ <b>只读</b>，且「本地 clear」<b>不是</b>发布许可。
 *
 * <h2>🔴 {@code Clear()} 的判定逐字保留（favorite_legacy_preflight.go:22-25）</h2>
 * <pre>
 * return r.Contract == "rtw.favorite.legacy-preflight.v1" &amp;&amp;
 *        r.MissingAssert == r.ApprovedLegacy &amp;&amp; r.Blocked == 0 &amp;&amp; r.OrphanRetracts == 0
 * </pre>
 * 四条<b>全部</b>满足才算 clear。
 *
 * <h2>⚠️ Go 的退出码语义在 Spring 进程内<b>没有对应物</b></h2>
 * <table border="1">
 *   <caption>Go 的退出码（main.go:24,30,38,50）</caption>
 *   <tr><th>码</th><th>含义</th><th>Java 侧</th></tr>
 *   <tr><td>0</td><td>clear</td><td>日志 {@code outcome=succeeded}</td></tr>
 *   <tr><td>1</td><td>读取失败</td><td>日志 {@code outcome=failed} + {@code error_code=PREFLIGHT_READ_FAILED}</td></tr>
 *   <tr><td>2</td><td>未裁决（blocked）</td><td>日志 {@code outcome=not_reconciled} + {@code error_code=LEGACY_RECONCILIATION_REQUIRED}</td></tr>
 * </table>
 *
 * <p>⚠️ <b>刻意不调 {@code System.exit}</b>：那会连带杀掉同一个 JVM 里的 gRPC 服务。
 * 代价是 CI 无法靠退出码判门禁 ⇒ 需由人工/CI 解析这行结构化日志。
 * 这是<b>有意的形态变更</b>，记录在剩余问题里。
 */
@Component
public class FavoriteLegacyPreflightRunner {

    private static final Logger LOG = LoggerFactory.getLogger(FavoriteLegacyPreflightRunner.class);

    /** Go {@code favorite_legacy_preflight.go:31} 的契约串。 */
    private static final String CONTRACT = "rtw.favorite.legacy-preflight.v1";

    private final FavoriteRpcProperties properties;
    private final FavoriteFactOutboxMapper outboxMapper;
    private final FavoriteFactProcessLogging processLogging;

    public FavoriteLegacyPreflightRunner(FavoriteRpcProperties properties,
                                         FavoriteFactOutboxMapper outboxMapper,
                                         FavoriteFactProcessLogging processLogging) {
        this.properties = properties;
        this.outboxMapper = outboxMapper;
        this.processLogging = processLogging;
    }

    /** Go {@code FavoriteLegacyPreflightReport}（favorite_legacy_preflight.go:14-20）的 Java 形状。 */
    public static final class Report {

        @com.fasterxml.jackson.annotation.JsonProperty("contract")
        public String contract = CONTRACT;
        @com.fasterxml.jackson.annotation.JsonProperty("missing_assert")
        public long missingAssert;
        @com.fasterxml.jackson.annotation.JsonProperty("approved_legacy")
        public long approvedLegacy;
        @com.fasterxml.jackson.annotation.JsonProperty("blocked")
        public long blocked;
        @com.fasterxml.jackson.annotation.JsonProperty("orphan_retracts")
        public long orphanRetracts;

        /** Go {@code func (r FavoriteLegacyPreflightReport) Clear() bool}（:22-25）。 */
        public boolean isClear() {
            return CONTRACT.equals(contract)
                    && missingAssert == approvedLegacy
                    && blocked == 0L
                    && orphanRetracts == 0L;
        }
    }

    /**
     * Go {@code main()} 的执行入口（main.go:32-53）。
     *
     * <p>对应 Go 的 {@code context.WithTimeout(ctx, 2min)}：单次预检最多 2 分钟。
     * ⚠️ 本实现**不设**该超时（Spring 进程内无法安全中断一个长事务）⇒ 已知差异。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void run() {
        if (!properties.getFact().getLegacyPreflight().isEnabled()) {
            LOG.info("FavoriteLegacyPreflightRunner: 未启用（favorite.fact.legacy-preflight.enabled=false）");
            return;
        }
        Logger processLogger =
                processLogging.loggerFor(FavoriteFactProcessLogging.SERVICE_PREFLIGHT, "legacy-preflight");
        try {
            Report report = preflight();
            String outcome = report.isClear() ? "succeeded" : "not_reconciled";
            String errorCode = report.isClear() ? null : "LEGACY_RECONCILIATION_REQUIRED";
            processLogger.atInfo()
                    .addKeyValue("event", "favorite.legacy_preflight")
                    .addKeyValue("outcome", outcome)
                    .addKeyValue("error_code", errorCode)
                    .addKeyValue("contract", report.contract)
                    .addKeyValue("missing_assert", report.missingAssert)
                    .addKeyValue("approved_legacy", report.approvedLegacy)
                    .addKeyValue("blocked", report.blocked)
                    .addKeyValue("orphan_retracts", report.orphanRetracts)
                    .log("favorite legacy preflight report");
            LOG.warn("""
                    FavoriteLegacyPreflightRunner: legacy 预检报告（clear={}）
                      contract={} missing_assert={} approved_legacy={} blocked={} orphan_retracts={}
                      ⚠️ 这**不是**发布许可：Go 侧要求先冻结真实写入方，再在生产快照上重跑
                        （favorite_legacy_preflight.go:26-28 的注释）。
                      ⚠️ Go 用退出码 2 表达「未裁决」，Spring 进程内没有退出码
                        ⇒ CI 需解析本行日志的 outcome=not_reconciled。
                    """, report.isClear(), report.contract, report.missingAssert,
                    report.approvedLegacy, report.blocked, report.orphanRetracts);
        } catch (RuntimeException e) {
            // Go: :38-44 —— 读取失败 ⇒ exit 1
            processLogger.atError()
                    .addKeyValue("event", "favorite.legacy_preflight")
                    .addKeyValue("outcome", "failed")
                    .addKeyValue("error_code", "PREFLIGHT_READ_FAILED")
                    .setCause(e)
                    .log("favorite legacy preflight failed");
            LOG.error("FavoriteLegacyPreflightRunner: 预检失败", e);
        }
    }

    /**
     * 🔴 对应 Go {@code FavoriteLegacyPreflight}（favorite_legacy_preflight.go:30-96）。
     *
     * <p><b>只读</b>：{@code readOnly = true} + {@code REPEATABLE READ}
     * （Go 的 {@code sql.TxOptions{Isolation: sql.LevelRepeatableRead, ReadOnly: true}}，:35）。
     * ⇒ Java 侧必须同样声明，否则一条误写的 UPDATE 会污染「证据快照」。
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Report preflight() {
        Report report = new Report();

        // Go: :40-47 —— 一条 LEFT JOIN 拉出「无 v1 断言」的行 + 它们的 marker
        List<FavoriteLegacyPreflightRow> rows = outboxMapper.selectRowsWithoutAssertion();

        // Go: :50-78 —— 逐行比对 12 项，分 Blocked / ApprovedLegacy
        for (FavoriteLegacyPreflightRow row : rows) {
            report.missingAssert++;
            if (!markerMatches(row)) {
                report.blocked++;
                continue;
            }
            report.approvedLegacy++;
        }

        // Go: :86-91 —— 孤儿撤回统计
        report.orphanRetracts = outboxMapper.countOrphanRetracts();
        return report;
    }

    /**
     * Go {@code favorite_legacy_preflight.go:62-77} 的逐行判定。
     *
     * <p>⚠️ <b>与 {@code favoriteAssertionSchema} 的 legacy 分支是同一条规则的两次独立表达</b>：
     * 预检在<b>只读快照</b>里统计，撤回路径在<b>写事务</b>里阻断。
     * 两者必须给出一致的结论，否则运维会看到「预检说 clear、删除却仍被 5304 阻断」。
     * ⇒ 这里的比对项与 {@code FavoriteAssertionSchemaResolver#resolveLegacy} 逐条对齐。
     */
    private boolean markerMatches(FavoriteLegacyPreflightRow row) {
        if (row.markerFavoriteId == null || row.markerUserId == null || row.markerFolderId == null
                || row.markerTargetType == null || row.markerTargetId == null
                || row.markerSourceSnapshotSha256 == null || row.markerApprovalRef == null) {
            return false;
        }
        // Go: :69-73 —— 逐字段相等
        if (!row.markerFavoriteId.equals(row.favoriteId)
                || !row.markerUserId.equals(row.userId)
                || !row.markerFolderId.equals(row.folderId)
                || !row.markerTargetType.equals(row.targetType)
                || !row.markerTargetId.equals(row.targetId)) {
            return false;
        }
        // Go: :72 —— markerRevision.Valid != revision.Valid ⇒ 不匹配（null 与空串不可混）
        if (!FavoriteDeliveryValidator.sameFavoriteRevision(row.markerTargetRevision, row.targetRevision)) {
            return false;
        }
        // Go: :70 —— markerApproval.String == "" ⇒ 不匹配
        if (row.markerApprovalRef.isEmpty()) {
            return false;
        }
        // Go: :70 —— markerHash.String != hash ⇒ 不匹配（重新算一遍业务行快照）
        return row.markerSourceSnapshotSha256.equals(snapshotHash(row));
    }

    /** 重算业务行的不可变快照哈希 —— 与 Go 的 {@code favoriteLegacySnapshotHash} 同一口径。 */
    private String snapshotHash(FavoriteLegacyPreflightRow row) {
        FavoriteItemEntity item = new FavoriteItemEntity();
        item.setFavoriteId(row.favoriteId);
        item.setUserId(row.userId);
        item.setFolderId(row.folderId);
        item.setTargetType(row.targetType);
        item.setTargetId(row.targetId);
        item.setTargetRevision(row.targetRevision);
        return new FavoriteFactEnvelopeFactory().legacySnapshotHash(item);
    }

    /** 保留引用：预检的输出字段名必须与 Go 的 JSON tag 一致。 */
    @SuppressWarnings("unused")
    private static Class<?> mapper() {
        return FavoriteRpcSupport.class;
    }
}