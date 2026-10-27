package com.rag.common.http;

import com.rag.common.http.GoStyleFormLimitMode.DomainReachability;
import com.rag.common.http.GoStyleFormLimitMode.Mode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 🔴 守门：form 闸门的「开 / 关」判据 = <b>语义可达性</b>，且这张表<b>覆盖全部 api 域</b>。
 *
 * <h2>这条守门守的是什么（G2 反馈单 FB-02 + G3 反馈单「轴 ② 判据」）</h2>
 * <p>旧版共享层把「{@code off} 的依据」写成「<b>favorite 域不在 global 的写权范围内</b>」
 * ⇒ ⇒ 那是一条<b>与 Go 行为无关</b>的理由 ⇒ ⇒ 后果：
 * <ul>
 *   <li>① 它回答不了「下一个域该开还是该关」⇒ 下一个<b>裸 decoder</b> 域会照抄邻居；</li>
 *   <li>② 漏声明的域会<b>静默落到缺省 {@code off}</b> ⇒ 而缺省只对「Go 侧不解析」的域才正确。</li>
 * </ul>
 * ⇒ ⇒ 判据改成「<b>Go 侧该域的 handler 是否真的解析 form/query</b>」，并把<b>逐域取证落成代码</b>
 * ⇒ ⇒ 本类把那张表钉死。
 *
 * <h3>🔴 2026-10-27（G3）：判据补上<b>第一判据</b>「域是否自捕获」</h3>
 * <p>G3 指出：favorite 的 Go 侧<b>每个 handler 都自己捕获</b> {@code httpx.Parse} 的错
 * ⇒ ⇒ 拒绝形状是<b>域自定义信封</b>（200 + {code:5003,…}）
 * ⇒ ⇒ 既不是 {@link Mode#RAW}（400 + text/plain）也不是 {@link Mode#ENVELOPE}（200 + 5001）
 * ⇒ ⇒ 而旧表把 favorite 记成 {@link Mode#RAW} ⇒ <b>错</b>。
 * ⇒ ⇒ 本轮把判据改成<b>两条</b>：
 * <ol>
 *   <li><b>域自捕获</b>（{@code goSideHandlesParseError == true}）
 *       ⇒ ⇒ 拒绝形状由<b>域自己</b>定 ⇒ ⇒ <b>共享层不装闸门</b> ⇒ 语义模式必须是 {@link Mode#OFF}；</li>
 *   <li><b>域不自捕获</b> ⇒ ⇒ 回到「Go 侧是否真的解析 form/query」
 *       ⇒ ⇒ {@link Mode#OFF} ⇔ 不解析。</li>
 * </ol>
 * ⇒ ⇒ favorite 的语义答案因此是 {@link Mode#OFF}（<b>与它「不声明 mode」一致</b>
 * ⇒ ⇒ 与 {@code tools/difftest/check-tomcat-limits.py} 的
 * 「favorite <b>不许</b>声明 mode」也一致 ⇒ ⇒ 两侧不再打架）。
 *
 * <h3>Go 出处（判据本身）</h3>
 * <pre>
 * go-zero@v1.10.2/rest/httpx/requests.go   Parse → ParseForm → GetFormValues
 * go-zero@v1.10.2/rest/httpx/util.go:15     maxFormParamCount = 2048   （数【非空值】）
 * go-zero@v1.10.2/rest/httpx/util.go:48-53  超过即 "too many form values" ⇒ Parse 失败
 * ⇒ ⇒ 只有「handler 真的调 httpx.Parse」的域，r.Form 才被求值 ⇒ 两道上限才可达
 * </pre>
 *
 * <h3>🔴 本表是「目标态」，不是「现状快照」</h3>
 * <p>各域 yaml 里声明的 {@code mode} 是<b>现状</b>；本表的 {@code semanticMode} 是<b>应当</b>。
 * ⇒ 两者不一致 ⇒ <b>就是一条待修的漂移</b>（由各域自己改 yaml 修；共享层只记账）。
 *
 * <h2>🔴 本类的事实来源与「它验不到什么」</h2>
 * <p>⚠️ 本类<b>只能钉住「表里写的是什么」</b>，<b>验不到</b>「表里写的对不对」——
 * 后者需要 Go 真源 + 双侧活体对拍 ⇒ ⇒ 由 {@code tools/api-envelope-probe/favorite}
 * （Go 真源实测 ↔ expected.txt ↔ RAG 真 HTTP 逐字节）承担。
 * ⇒ ⇒ 本类与该探针<b>互补</b>：探针验「favorite 的形状真的是 200+5003」，
 * 本类验「共享层不会把 favorite 误改成 RAW / 误登记成偏离」。
 */
@DisplayName("🔴 form 闸门的开关判据 = 语义可达性（FB-02 + G3 轴② · 逐域取证表）")
class GoStyleFormLimitSemanticCriterionTest {

    /** 10 个 api 域的模块路径（新增 / 删除 api 域 ⇒ 本表必须同步 ⇒ 守门会红）。 */
    private static final List<String> ALL_API_MODULES = List.of(
            "service/article/article-api",
            "service/comment/comment-api",
            "service/favorite/favorite-api",
            "service/follow/follow-api",
            "service/hot/hot-api",
            "service/like/like-api",
            "service/message/message-api",
            "service/task/task-api",
            "service/users/user/user-api",
            "service/users/admin/admin-api");

    @Test
    @DisplayName("① 表覆盖**全部** api 域，且不多不少（新增域 ⇒ 必须补取证）")
    void tableCoversEveryApiModule() {
        Set<String> covered = GoStyleFormLimitMode.SEMANTIC_REACHABILITY.stream()
                .map(DomainReachability::module)
                .collect(java.util.stream.Collectors.toSet());

        assertEquals(Set.copyOf(ALL_API_MODULES), covered,
                "语义可达性表必须与 api 域清单**逐条一致**"
                        + "（新增/删除 api 域 ⇒ 必须同步补/删取证行，否则下一个域会没有判据）");
    }

    @Test
    @DisplayName("② 每一行都**自洽**：域自捕获 ⇒ OFF；否则「Go 侧解析 ⇔ 语义模式非 OFF」")
    void everyRowIsSelfConsistent() {
        for (DomainReachability r : GoStyleFormLimitMode.SEMANTIC_REACHABILITY) {
            assertTrue(r.selfConsistent(),
                    "语义模式必须与「域是否自捕获」+「Go 侧是否解析 form/query」自洽："
                            + r.module() + " ⇒ parses=" + r.goSideParsesForm()
                            + " ⇒ selfHandlesParseError=" + r.goSideHandlesParseError()
                            + " ⇒ mode=" + r.semanticMode());
        }
    }

    @Test
    @DisplayName("③ 语义答案 = **不可达 ⇒ OFF**（task：Go 侧 httpx.Parse 命中数 = 0 ⇒ 闸门必须关）")
    void taskIsSemanticallyOff() {
        DomainReachability task = GoStyleFormLimitMode.reachabilityOf("service/task/task-api");
        assertNotNull(task, "task 必须在表里");
        assertFalse(task.goSideParsesForm(),
                "task/api 的 httpx.Parse / ParseForm / GetFormValues 命中数 = 0"
                        + "（gettaskhandler.go:19 用 json.NewDecoder(r.Body)，只 import 了 httpx 但只用 httpx.ErrorCtx）"
                        + " ⇒ r.Form 从不被求值 ⇒ 两道上限不可达");
        assertFalse(task.goSideHandlesParseError(),
                "task 不自捕获（Go 侧没有「自己捕获 httpx.Parse 的错」的写法）");
        assertEquals(Mode.OFF, task.semanticMode(),
                "语义答案必须是 OFF（与 task 当前 yaml 里的 raw 不一致 ⇒ 那是一条待修的漂移，"
                        + "由 task 域自己改 yaml；共享层只记账）");
    }

    @Test
    @DisplayName("④ favorite：**域自捕获** ⇒ 语义答案是 OFF（共享层不装闸门）⇒ **不是**「模式偏离」")
    void favoriteIsSemanticallyOffBecauseTheDomainHandlesItItself() {
        DomainReachability favorite = GoStyleFormLimitMode.reachabilityOf("service/favorite/favorite-api");
        assertNotNull(favorite, "favorite 必须在表里（否则就没有判据 ⇒ 下次会瞎猜）");

        assertTrue(favorite.goSideParsesForm(),
                "favorite/api/internal/handler/favorite/** 的 httpx.Parse 命中 7 处 ⇒ 语义上可达");
        assertTrue(favorite.goSideHandlesParseError(),
                "favorite 的 **7 个 handler 各自捕获** httpx.Parse 的错"
                        + "（Go 真源：service/favorite/api/internal/handler/favorite/*.go:18-22"
                        + " ⇒ httpx.OkJsonCtx + {Code: favoritecommon.ErrorInvalidParam(=5003),"
                        + " Msg:\"参数错误\", Data:nil}）⇒ 拒绝形状由**域自己**定");
        assertEquals(Mode.OFF, favorite.semanticMode(),
                "域自捕获 ⇒ 共享层**不能**装闸门（装了会抢先于域自己的捕获"
                        + " ⇒ 形状会变成 400+text/plain 或 200+5001 ⇒ 与 Go 不符）"
                        + " ⇒ 语义答案必须是 OFF");
        assertFalse(GoStyleFormLimitMode.hasRegisteredDeviation("service/favorite/favorite-api"),
                "favorite 的实际模式（off）== 语义答案（off）⇒ 它**不是**「模式偏离」"
                        + " ⇒ 不该登记在 REGISTERED_DEVIATIONS"
                        + "（否则下一个 agent 会去销一条并不存在的偏离）");
        assertTrue(favorite.goEvidence().contains("5003") && favorite.goEvidence().contains("捕获"),
                "取证串必须写清「域自捕获」与「域自定义信封（5003）」这两个事实"
                        + "（这张表是判据的**唯一**事实来源 ⇒ 写错了下一个人就会照着错的方向修）");
    }

    @Test
    @DisplayName("⑤ **刻意保留的偏离表当前为空**（各域的实际模式 == 语义答案）")
    void noRegisteredDeviationIsKept() {
        assertEquals(0, GoStyleFormLimitMode.REGISTERED_DEVIATIONS.size(),
                "刻意保留的偏离表当前应为空："
                        + "favorite 的 OFF 是**语义正确**的（域自捕获 ⇒ 不是偏离）；"
                        + "task 的 raw 是**待修**漂移（各域自己改 yaml，不进本表）"
                        + " ⇒ 新增条目必须先核对前提并说明为什么不能修");

        Set<String> registered = GoStyleFormLimitMode.REGISTERED_DEVIATIONS.stream()
                .map(d -> d.module())
                .collect(java.util.stream.Collectors.toSet());
        assertFalse(registered.contains("service/favorite/favorite-api"),
                "favorite **不在**偏离表里（它的 OFF 是语义正确的，不是刻意保留的偏离）");
        assertFalse(registered.contains("service/task/task-api"),
                "task **不在**偏离表里（它是各域自己改 yaml 的待修漂移，不是刻意保留）");
    }

    @Test
    @DisplayName("⑥ 查询入口：按模块路径查语义模式 / 查是否登记偏离 / 未知域返回 null（不许瞎猜）")
    void lookupApiBehaves() {
        assertEquals(Mode.OFF, GoStyleFormLimitMode.semanticModeOf("service/task/task-api"));
        assertEquals(Mode.ENVELOPE, GoStyleFormLimitMode.semanticModeOf("service/like/like-api"));
        assertEquals(Mode.RAW, GoStyleFormLimitMode.semanticModeOf("service/users/admin/admin-api"));
        assertEquals(Mode.OFF, GoStyleFormLimitMode.semanticModeOf("service/favorite/favorite-api"),
                "favorite 的语义答案是 OFF（域自捕获 ⇒ 共享层不装闸门）");

        assertNull(GoStyleFormLimitMode.semanticModeOf("service/does/not-exist"),
                "未知域必须返回 null（让调用方自己处理「没有判据」），不许默认猜一个");
        assertNull(GoStyleFormLimitMode.reachabilityOf("service/does/not-exist"));
        assertFalse(GoStyleFormLimitMode.hasRegisteredDeviation("service/does/not-exist"));

        assertEquals(10, GoStyleFormLimitMode.reachabilityIndex().size());
    }
}
