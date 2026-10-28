package com.rag.common.http;

import com.rag.common.http.GoStyleFormLimitMode.DomainReachability;
import com.rag.common.http.GoStyleFormLimitMode.Mode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    /**
     * 🔴 2026-10-28：<b>本类不再持有「api 域清单」的字面量</b> —— 清单的真源改成
     * <b>磁盘上真实存在的 {@code *-api} 模块目录</b>。
     *
     * <p><b>为什么必须改</b>：旧版是一条
     * {@code assertEquals(Set.copyOf(ALL_API_MODULES), covered, …)}，
     * 而 {@code ALL_API_MODULES} 是<b>测试里手抄的 10 个路径字面量</b>
     * ⇒ ⇒ 全类<b>没有任何一条断言去扫文件系统</b>
     * ⇒ ⇒ <b>新增第 11 个 api 域时，只要没人记得同步那份 {@code List.of(...)}，守门照绿</b>
     * ⇒ ⇒ 与本类自己的 DisplayName「新增域 ⇒ 必须补取证」<b>直接矛盾</b>
     * （AGENTS.md §3.3.3：「跑绿」≠「对」；判据必须能被人为破坏而变红）。
     *
     * <p><b>判据的形状</b>：<b>磁盘上的 api 模块目录集合</b> == <b>语义可达性表的覆盖集</b>
     * ⇒ ⇒ 增删任一 api 域 ⇒ 两边必有一边对不上 ⇒ ⇒ <b>守门变红</b>。
     *
     * <p>⚠️ <b>只按目录名判定，不要求里面有 {@code pom.xml}</b>：
     * 「新增一个空目录」是<b>最弱</b>的变异 ⇒ 判据必须连它都能抓住
     * ⇒ ⇒ 目录名即判据（与 {@code GoStyleRedisClientShadowingGateTest} 的同款纪律一致）。
     */
    private static final String API_MODULE_DIR_SUFFIX = "-api";

    @Test
    @DisplayName("① 表覆盖**全部** api 域，且不多不少（新增域 ⇒ 必须补取证）")
    void tableCoversEveryApiModule() {
        Set<String> covered = GoStyleFormLimitMode.SEMANTIC_REACHABILITY.stream()
                .map(DomainReachability::module)
                .collect(Collectors.toSet());

        Set<String> actual = actualApiModules(repoRoot());

        assertEquals(actual, covered,
                "语义可达性表必须与**磁盘上真实存在的 api 模块目录**逐条一致"
                        + "（新增/删除 api 域 ⇒ 必须同步补/删取证行，否则下一个域会没有判据）"
                        + "\n磁盘上实际存在的 api 模块（" + actual.size() + " 个）：" + actual
                        + "\n表里登记的（" + covered.size() + " 个）：" + covered
                        + "\n只在一侧出现的模块 ⇒ " + symmetricDifference(actual, covered));
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
    @DisplayName("⑥ 查询入口：按模块路径查语义模式 / 查是否登记偏离 / 未知域返回 null（不许瞎猜）"
            + " + 索引与磁盘上的 api 域一一对应（不锁死数字）")
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

        // 🔴 2026-10-28：旧版这里是 assertEquals(10, reachabilityIndex().size())
        //    ⇒ 那是**硬编码的数字**：域一增减，它要么变成假绿、要么变成无谓的红
        //    ⇒ ⇒ 改成不变量式：「索引条数 == 磁盘上真实存在的 api 域数」
        //    （索引按 module 去重 ⇒ 这一条同时钉住「不重」与「不漏」）。
        Set<String> actual = actualApiModules(repoRoot());
        assertEquals(actual.size(), GoStyleFormLimitMode.reachabilityIndex().size(),
                "查询索引必须**每个真实存在的 api 域恰好一条**（不重不漏）"
                        + " ⇒ ⇒ 判据取自磁盘，不锁死数字（域一增减，这条自动跟着变）"
                        + "\n磁盘上实际存在的 api 模块（" + actual.size() + " 个）：" + actual);
    }

    // ==================================================================
    // ⑦ 判据自检：扫描器不是空转（否则①可能是「什么都没扫到」的假绿）
    // ==================================================================

    @Test
    @DisplayName("⑦ 判据自检：临时目录里造一个假 api 域目录 ⇒ 扫描器**必须**报出它")
    void scannerActuallyReportsAnApiModule() throws IOException {
        Path fakeRoot = Files.createTempDirectory("g4-formlimit-selftest");
        try {
            Path fake = fakeRoot.resolve("service/fake-domain/fake-domain-api");
            Files.createDirectories(fake);

            Set<String> found = actualApiModules(fakeRoot);

            assertEquals(Set.of("service/fake-domain/fake-domain-api"), found,
                    "扫描器必须在临时目录里报出那个假 api 域目录（**哪怕它是空目录**）"
                            + " ⇒ ⇒ 否则断言① 可能是「什么都没扫到」的**假绿**"
                            + " ⇒ ⇒ 实际扫到：" + found);
        } finally {
            deleteRecursively(fakeRoot);
        }
    }

    // ==================================================================
    // helpers
    // ==================================================================

    /**
     * 扫出<b>真实存在</b>的 api 模块目录（相对仓库根，{@code service/…} 形式）。
     *
     * <p>⚠️ <b>只按目录名判定</b>（名字以 {@code -api} 结尾），<b>不要求</b>里面有 {@code pom.xml}
     * ⇒ ⇒ 连「新增一个空目录」这种最弱变异都抓得住。
     *
     * @param root 仓库根（或自检用的临时目录）
     * @return 相对路径集合（已排除构建产物目录 {@code target}）
     */
    private static Set<String> actualApiModules(Path root) {
        Set<String> out = new TreeSet<>();
        Path serviceDir = root.resolve("service");
        if (!Files.isDirectory(serviceDir)) {
            throw new IllegalStateException("找不到 service 目录：" + serviceDir
                    + " ⇒ ⇒ 扫描器无法工作 ⇒ 不能当成「没有多余模块」⇒ 直接炸");
        }
        try (Stream<Path> stream = Files.walk(serviceDir, 10)) {
            for (Path p : (Iterable<Path>) stream.filter(Files::isDirectory)::iterator) {
                Path name = p.getFileName();
                if (name == null || !name.toString().endsWith(API_MODULE_DIR_SUFFIX)) {
                    continue;
                }
                String rel = root.relativize(p).toString().replace('\\', '/');
                if (rel.contains("/target/")) {
                    // 构建产物目录，不是模块
                    continue;
                }
                out.add(rel);
            }
        } catch (IOException e) {
            // 扫不动 ⇒ 不能当成「干净」⇒ 直接炸（AGENTS.md §3.3：不报错的失效）
            throw new IllegalStateException("扫 api 模块目录失败：" + e.getMessage(), e);
        }
        return out;
    }

    /** 两个集合的对称差（只在一侧出现的元素）⇒ 用于把差异直接印在断言消息里。 */
    private static Set<String> symmetricDifference(Set<String> a, Set<String> b) {
        Set<String> out = new TreeSet<>(a);
        out.removeAll(b);
        Set<String> onlyInB = new TreeSet<>(b);
        onlyInB.removeAll(a);
        out.addAll(onlyInB);
        return out;
    }

    /**
     * 仓库根目录 —— <b>从本类的 code source 向上找</b>（不依赖工作目录）。
     *
     * <p>⚠️ 不能用 {@code user.dir}：surefire 的工作目录是<b>模块目录</b>（{@code service/common}），
     * 而从 IDE 跑时又是仓库根 ⇒ ⇒ 两种布局都对 ⇒ ⇒ 必须走 code source
     * （与 {@code GoStyleRedisClientShadowingGateTest#repoRoot()} 同款纪律）。
     */
    private static Path repoRoot() {
        Path here = Path.of(GoStyleFormLimitSemanticCriterionTest.class.getProtectionDomain()
                .getCodeSource().getLocation().getPath());
        for (Path p = here; p != null; p = p.getParent()) {
            if (Files.isDirectory(p.resolve("service")) && Files.isRegularFile(p.resolve("pom.xml"))) {
                return p;
            }
        }
        throw new IllegalStateException("找不到仓库根（从 " + here + " 向上没有同时含 service/ 与 pom.xml 的目录）");
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(root)) {
            for (Path p : (Iterable<Path>) stream.sorted((a, b) -> b.getNameCount() - a.getNameCount())::iterator) {
                Files.deleteIfExists(p);
            }
        }
    }
}
