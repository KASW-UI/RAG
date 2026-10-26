package com.rag.common.http;

import com.rag.common.http.GoStyleFormLimitMode.DomainReachability;
import com.rag.common.http.GoStyleFormLimitMode.Mode;
import com.rag.common.http.GoStyleFormLimitMode.RegisteredDeviation;
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
 * <h2>这条守门守的是什么（G2 反馈单 FB-02）</h2>
 * <p>旧版共享层把「{@code off} 的依据」写成「<b>favorite 域不在 global 的写权范围内</b>」
 * ⇒ ⇒ 那是一条<b>与 Go 行为无关</b>的理由 ⇒ ⇒ 后果：
 * <ul>
 *   <li>① 它回答不了「下一个域该开还是该关」⇒ 下一个<b>裸 decoder</b> 域会照抄邻居；</li>
 *   <li>② 漏声明的域会<b>静默落到缺省 {@code off}</b> ⇒ 而缺省只对「Go 侧不解析」的域才正确。</li>
 * </ul>
 * ⇒ ⇒ 判据改成「<b>Go 侧该域的 handler 是否真的解析 form/query</b>」，并把<b>逐域取证落成代码</b>
 * ⇒ ⇒ 本类把那张表钉死。
 *
 * <h3>Go 出处（判据本身）</h3>
 * <pre>
 * go-zero@v1.10.2/rest/httpx/requests.go   Parse → ParseForm → GetFormValues
 * go-zero@v1.10.2/rest/httpx/util.go:15     maxFormParamCount = 2048   （数【非空值】）
 * go-zero@v1.10.2/rest/httpx/util.go:48-53  超过即 "too many form values" ⇒ Parse 失败
 * ⇒ ⇒ 只有「handler 真的调 httpx.Parse」的域，r.Form 才被求值 ⇒ 两道上限才可达
 * </pre>
 *
 * <h2>🔴 本表是「目标态」，不是「现状快照」</h2>
 * <p>各域 yaml 里声明的 {@code mode} 是<b>现状</b>；本表的 {@code semanticMode} 是<b>应当</b>。
 * ⇒ 两者不一致 ⇒ <b>就是一条待修的漂移</b>（由各域自己改 yaml 修；共享层只记账）。
 */
@DisplayName("🔴 form 闸门的开关判据 = 语义可达性（FB-02 · 逐域取证表）")
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
    @DisplayName("② 每一行都**自洽**：Go 侧解析 ⇒ 语义模式非 OFF；Go 侧不解析 ⇒ 语义模式必是 OFF")
    void everyRowIsSelfConsistent() {
        for (DomainReachability r : GoStyleFormLimitMode.SEMANTIC_REACHABILITY) {
            assertTrue(r.selfConsistent(),
                    "「Go 侧是否解析 form/query」与「语义模式」必须自洽："
                            + r.module() + " ⇒ parses=" + r.goSideParsesForm()
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
        assertEquals(Mode.OFF, task.semanticMode(),
                "语义答案必须是 OFF（与 task 当前 yaml 里的 raw 不一致 ⇒ 那是一条待修的漂移，"
                        + "由 task 域自己改 yaml；共享层只记账）");
    }

    @Test
    @DisplayName("④ favorite 的语义答案是 RAW，但**刻意保留**为 off ⇒ 已登记为已知偏离")
    void favoriteDeviationIsRegisteredNotSilentlyAligned() {
        DomainReachability favorite = GoStyleFormLimitMode.reachabilityOf("service/favorite/favorite-api");
        assertNotNull(favorite, "favorite 必须在表里（否则就没有判据 ⇒ 下次会瞎猜）");
        assertTrue(favorite.goSideParsesForm(),
                "favorite/api/internal/handler/favorite/** 的 httpx.Parse 命中 7 处 ⇒ 语义上可达");
        assertEquals(Mode.RAW, favorite.semanticMode(),
                "语义答案必须是 RAW（= Go 的拒绝形状：没注册 SetErrorHandler ⇒ 400 + text/plain + Go 原文）");

        assertTrue(GoStyleFormLimitMode.hasRegisteredDeviation("service/favorite/favorite-api"),
                "favorite 的实际模式（off）与语义答案（raw）不一致 ⇒ 必须登记为**已知偏离**"
                        + "，否则下一个 agent 会把它当成「已对齐」销账");
        RegisteredDeviation d = GoStyleFormLimitMode.REGISTERED_DEVIATIONS.stream()
                .filter(x -> x.module().equals("service/favorite/favorite-api"))
                .findFirst()
                .orElseThrow();
        assertEquals(Mode.OFF, d.actualMode(), "登记的偏离必须写清 favorite 的**实际**模式");
        assertTrue(d.observable().contains("400"),
                "偏离登记必须写清**可观察差异**（状态码 / 响应体形状），不能只写「不一致」");
    }

    @Test
    @DisplayName("⑤ favorite 是**唯一**被登记的刻意偏离（其余域要么一致，要么是各域自己修的待修漂移）")
    void favoriteIsTheOnlyRegisteredDeviation() {
        assertEquals(1, GoStyleFormLimitMode.REGISTERED_DEVIATIONS.size(),
                "刻意保留的偏离只应登记 favorite 一条；新增条目必须同时说明为什么不能修");

        Set<String> registered = GoStyleFormLimitMode.REGISTERED_DEVIATIONS.stream()
                .map(RegisteredDeviation::module)
                .collect(java.util.stream.Collectors.toSet());
        for (DomainReachability r : GoStyleFormLimitMode.SEMANTIC_REACHABILITY) {
            if (registered.contains(r.module())) {
                continue;
            }
            // 未登记 ⇒ 该域要么与语义一致，要么是「各域自己改 yaml」的待修漂移 ⇒ 不在本守门范围
            assertNotNull(GoStyleFormLimitMode.semanticModeOf(r.module()),
                    "表里的每一行都必须能按模块路径查到语义模式");
        }
    }

    @Test
    @DisplayName("⑥ 查询入口：按模块路径查语义模式 / 查是否登记偏离 / 未知域返回 null（不许瞎猜）")
    void lookupApiBehaves() {
        assertEquals(Mode.OFF, GoStyleFormLimitMode.semanticModeOf("service/task/task-api"));
        assertEquals(Mode.ENVELOPE, GoStyleFormLimitMode.semanticModeOf("service/like/like-api"));
        assertEquals(Mode.RAW, GoStyleFormLimitMode.semanticModeOf("service/users/admin/admin-api"));

        assertNull(GoStyleFormLimitMode.semanticModeOf("service/does/not-exist"),
                "未知域必须返回 null（让调用方自己处理「没有判据」），不许默认猜一个");
        assertNull(GoStyleFormLimitMode.reachabilityOf("service/does/not-exist"));
        assertFalse(GoStyleFormLimitMode.hasRegisteredDeviation("service/does/not-exist"));

        assertEquals(10, GoStyleFormLimitMode.reachabilityIndex().size());
    }
}
