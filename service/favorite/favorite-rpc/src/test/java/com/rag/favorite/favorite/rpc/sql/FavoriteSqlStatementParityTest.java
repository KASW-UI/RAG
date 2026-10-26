package com.rag.favorite.favorite.rpc.sql;

import com.rag.favorite.favorite.proto.CreateFavoriteFolderReq;
import com.rag.favorite.favorite.proto.CreateFavoriteReq;
import com.rag.favorite.favorite.proto.DeleteFavoriteFolderReq;
import com.rag.favorite.favorite.proto.DeleteFavoriteReq;
import com.rag.favorite.favorite.proto.FavoriteServiceGrpc;
import com.rag.favorite.favorite.proto.GetFavoriteFoldersReq;
import com.rag.favorite.favorite.proto.GetFavoriteFoldersResp;
import com.rag.favorite.favorite.proto.GetFavoritesByFolderReq;
import com.rag.favorite.favorite.proto.GetFavoritesByFolderResp;
import com.rag.favorite.favorite.proto.UpdateFavoriteFolderNameReq;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * 🔴🔴🔴 <b>SQL 语句层</b>的双侧活体对拍 —— favorite 域。
 *
 * <h2>它补的是什么缺口</h2>
 * <p>台账 {@code docs/DRIFT/favorite/rpc.md} §29 的最后一层：
 * <b>DDL 层</b>（{@code tools/ddl-parity-probe/favorite/}）、
 * <b>错误码契约层</b>（{@code tools/errmsg-probe/}）、
 * <b>事务语义层</b>（三笔事务写的同生共死，已由
 * {@code FavoriteFactTransactionTest} + 第二轮落库等价性覆盖）
 * <b>三层都已闭</b>，剩下的就是<b>语句层</b>：
 * 「两侧为了让同一个业务结果，<b>各自对数据库说了什么话</b>」。
 *
 * <p>这句话之所以重要，是因为<b>落库等价性只证伪「结果一样」</b>，
 * 证不了<b>「过程一样」</b>：少一句 {@code FOR UPDATE}、多一次全表扫、
 * 事务边界切得不一样 —— 这些都可能<b>在单次运行里看不出差别</b>，
 * 却会在<b>并发</b>下变成死锁或脏写。
 *
 * <h2>为什么用 <b>PG 服务端日志</b>取证（而不是各自的 ORM 日志）</h2>
 * <ul>
 *   <li><b>零侵入</b>：Go 侧是<b>冻结</b>的真源（不能改它的 logger 配置）；
 *       RAG 侧改 MyBatis 日志级别会<b>改变被测面</b>。
 *       ⇒ 服务端日志是<b>唯一</b>能让两侧都「零改动」地暴露真实 SQL 的位置。</li>
 *   <li><b>看到的是真语句</b>：服务端记的是<b>真发出去的那条 SQL</b>，
 *       不是「ORM 打算生成什么」。</li>
 *   <li><b>顺带看到事务边界</b>：{@code BEGIN} / {@code COMMIT} / {@code ROLLBACK}
 *       与语句在同一条时间线上 ⇒ 事务边界可判。</li>
 * </ul>
 *
 * <h2>两侧怎么分流</h2>
 * <ul>
 *   <li><b>Go 侧</b>：真源的 DSN 是硬编码的（{@code internal/model/db.go:21-28}，
 *       <b>没有</b> application_name 字段）⇒ 走 <b>{@code PGAPPNAME} 环境变量</b>
 *       （pgx v5 支持：{@code pgconn/config.go:456} 把 {@code PGAPPNAME} 映射成
 *       {@code application_name}）⇒ <b>不改真源一行</b>。</li>
 *   <li><b>RAG 侧</b>：JDBC URL 挂 {@code ApplicationName=rag_side}
 *       （pgjdbc 的连接属性）。</li>
 *   <li><b>驱动自己</b>：独立的 {@code probe_marker} 连接 ⇒
 *       标记语句<b>不落进</b>任何一侧的桶里。</li>
 * </ul>
 *
 * <h2>marker 协议（为什么需要它）</h2>
 * <p>两侧的 SQL 在**同一个 PG 实例**里 ⇒ 必须能把「哪个用例产生的」标出来，
 * 否则只能比「整段序列」，一旦某侧多一句或少一句，后面全部错位。
 * <p>做法：<b>每个用例调用前</b>，驱动用一条<b>独立连接</b>执行
 * {@code SELECT 'SQLMARK::<side>::<case-id>::<seq>'} ⇒ 服务端日志里出现一条可识别的语句
 * ⇒ 解析时按 marker 切段 ⇒ <b>逐用例</b>比对。
 *
 * <h2>🔴 为什么「驱动自己的 SQL」必须先于第一个 marker</h2>
 * <p>本类用 Spring 的 {@code jdbcTemplate} 灌 RAG 侧种子，而那个数据源
 * <b>就是</b> RAG 应用自己的数据源（{@code application_name=rag_side}）
 * ⇒ 种子语句也会被记进 RAG 桶。
 * <p>⇒ 灌种子<b>必须在</b>第一个 marker <b>之前</b>完成（下面就是这么排的），
 * 且解析时<b>丢弃第一个 marker 之前的一切</b>。
 *
 * <h2>本类<b>不</b>做什么</h2>
 * <ul>
 *   <li><b>不</b>断言两侧 SQL 文本相同 —— GORM 与手写 SQL 的生成差异是
 *       <b>库级噪声</b>（占位符风格、{@code SELECT *} vs 显式列、GORM 给
 *       {@code First} 偷偷加的 {@code ORDER BY pk} …）⇒ 比的是<b>语义签名</b>。</li>
 *   <li><b>不</b>替代 §0 的落库等价性（那是「结果一样」的直接证据）——
 *       本类是「过程一样」的证据，两者是<b>互补</b>的。</li>
 * </ul>
 *
 * <h2>上游是「两侧共同的一个桩」</h2>
 * <p>与 {@code FavoriteCreateSuccessParityTest} 同一个桩（探针自带），
 * 两侧都打它 ⇒ 差出来的才是 favorite 域自己的行为差异。
 * ⚠️ 桩 ≠ 真 user-rpc / article-rpc ⇒ 本探针<b>不</b>证明 favorite 与
 * user-rpc / article-rpc 之间的交互契约。
 */
@SpringBootTest(
        classes = com.rag.favorite.favorite.rpc.FavoriteRpcApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                // Nacos：devh 的 @GrpcClient 在 Bean 初始化期就建 channel ⇒ 必须指到一个死地址
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN"
        })
@EnabledIfEnvironmentVariable(named = "PROBE_GO_TARGET", matches = ".+")
class FavoriteSqlStatementParityTest {

    // ══════════════════════════ 环境 ══════════════════════════

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> env("PROBE_JDBC_URL"));
        registry.add("spring.datasource.username", () -> env("PROBE_JDBC_USER"));
        registry.add("spring.datasource.password", () -> env("PROBE_JDBC_PASSWORD"));
        registry.add("grpc.server.port", () -> env("PROBE_RAG_PORT"));
        // 🔴 本探针**接上**上游：user-rpc / article-rpc 都指向探针自带的那个桩
        //    （与 FavoriteCreateSuccessParityTest 同一个桩 ⇒ create 成功路径可达）。
        registry.add("grpc.client.user-rpc.address", () -> env("PROBE_USER_RPC_ADDR"));
        registry.add("grpc.client.article-rpc.address", () -> env("PROBE_ARTICLE_RPC_ADDR"));
        // 🔴 后台派发/权威/预检**全关** ⇒ 否则它们会自己往 PG 上写语句，
        //    混进「某个用例产生的 SQL」里 ⇒ 分段全错。
        registry.add("favorite.fact.dispatch.enabled", () -> "false");
        registry.add("favorite.fact.authority.enabled", () -> "false");
        registry.add("favorite.fact.legacy-preflight.enabled", () -> "false");
    }

    private static String env(String key) {
        String v = System.getenv(key);
        if (v == null || v.isBlank()) {
            throw new IllegalStateException("缺少环境变量 " + key + "（探针必须由 run.sh 编排，不要单跑本测试）");
        }
        return v;
    }

    /** RAG 侧数据源（{@code application_name=rag_side}）—— 只用来灌种子。 */
    @Autowired
    private JdbcTemplate ragJdbc;

    /** 驱动自己的连接（{@code application_name=probe_marker}）—— 只用来发 marker。 */
    private static JdbcTemplate markerJdbc;

    private static ManagedChannel goChannel;
    private static ManagedChannel ragChannel;

    // ══════════════════════════ 桩约定（与 upstreamstub/main.go 一一对应）══════════════════

    /** 正常用户：status=0 且字段**存在** ⇒ 走成功路径（上游桩的约定）。 */
    private static final long USER_ACTIVE = 2001L;

    /** 本探针新建的收藏夹名（两侧同名 ⇒ 重名用例才有意义）。 */
    private static final String FOLDER_NAME = "SqlProbeFolder";

    // ══════════════════════════ 用例 ══════════════════════════

    /**
     * 一个用例。
     *
     * @param id       用例 id（同时是 marker 里的一段 ⇒ 两侧分段靠它对齐）
     * @param call     调用体；通过 {@link Ids} 回报该侧自己新建出来的 id
     * @param shape    <b>可选</b>的 L1 响应呈现方式（屏蔽两侧必然不同的雪花 ID，
     *                 只留**可比较**的字段）⇒ 用来把「SQL 层的差异」一路追到
     *                 <b>对外响应</b>上
     */
    record SqlCase(String id, Call call, java.util.function.Function<Object, String> shape) {
        SqlCase(String id, Call call) {
            this(id, call, null);
        }
    }

    /**
     * 该侧自己新建出来的 id。
     *
     * <p>🔴 <b>两侧的雪花 ID 必然不同</b> ⇒ 每个用例必须用**该侧自己**的 id，
     * 不能共用一个常量。
     */
    static final class Ids {
        long folder;
        long item;
        long item2;
    }

    interface Call {
        Object invoke(Ids ids, FavoriteServiceGrpc.FavoriteServiceBlockingStub stub);
    }

    /**
     * 用例矩阵。
     *
     * <p>🔴 <b>顺序即契约</b>：只读用例全在前，写入用例全在后；
     * 且「读同一行的用例」必须排在「写同一行的用例」之前
     * （与 {@code FavoriteGrpcContractParityTest} 同一纪律，见其 c30/c33 的说明）。
     *
     * <p>⚠️ <b>为什么只挑这些用例</b>：本层比的是「<b>对数据库说了什么话</b>」，
     * ⇒ 用例必须<b>真的碰到数据库</b>才有话说。纯参数校验类用例（userId&lt;=0 之类）
     * 在 Go 侧<b>根本不查库</b> ⇒ 两侧都是零语句 ⇒ 比不出东西，还占矩阵。
     * ⇒ 只保留<b>至少会发出 SQL</b>的用例。
     *
     * <p>🔴 <b>为什么写入段要覆盖「已产事实的条目」</b>：
     * 删除一条收藏时，Go 走 {@code favoriteAssertionSchema}（favorite_fact.go:225-244）
     * 判断这条收藏**有没有被下游接受过的 assert 事实** ——
     * <b>种子里的行没有事实</b> ⇒ 两侧都会走「legacy 未批准 ⇒ 拒绝撤回」那条分支
     * （只查不删）⇒ <b>DELETE / retract 那一段永远跑不到</b>。
     * ⇒ 必须先在本次运行里<b>真创建</b>一条（w03/w04）⇒ 它带 assert 事实
     * ⇒ 随后的删除（w07/w11）才会走完整的 v2 retract 路径。
     */
    private static List<SqlCase> matrix() {
        List<SqlCase> cs = new ArrayList<>();

        // ─────────── 只读段（先读后写：避免写入用例推进 updated_at 影响后续读的观测）───────────
        // Go: get_favorite_folders_logic.go:45-60 ⇒ SELECT favorite_folder WHERE user_id ORDER BY create_time DESC
        cs.add(new SqlCase("r01_list_folders_ok",
                (ids, s) -> s.getFavoriteFolders(GetFavoriteFoldersReq.newBuilder().setUserId(1001).build())));
        // Go: 同上 ⇒ 用户不存在 ⇒ 空集（仍是一次 SELECT）
        cs.add(new SqlCase("r02_list_folders_empty",
                (ids, s) -> s.getFavoriteFolders(GetFavoriteFoldersReq.newBuilder().setUserId(4242).build())));
        // Go: get_favorites_by_folder_logic.go:60-88 ⇒ SELECT favorite_item WHERE folder_id ORDER BY create_time DESC
        cs.add(new SqlCase("r03_list_items_ok",
                (ids, s) -> s.getFavoritesByFolder(GetFavoritesByFolderReq.newBuilder()
                        .setUserId(1001).setFolderId(9001).build())));
        // Go: get_favorites_by_folder_logic.go:60-67 ⇒ 收藏夹不存在 ⇒ 先查后报错
        cs.add(new SqlCase("r04_list_items_notfound",
                (ids, s) -> s.getFavoritesByFolder(GetFavoritesByFolderReq.newBuilder()
                        .setUserId(1001).setFolderId(9999).build())));
        // Go: get_favorites_by_folder_logic.go:73-79 ⇒ 归属不符 ⇒ 先查后报错
        cs.add(new SqlCase("r05_list_items_forbidden",
                (ids, s) -> s.getFavoritesByFolder(GetFavoritesByFolderReq.newBuilder()
                        .setUserId(2002).setFolderId(9001).build())));
        // Go: delete_favorite_logic.go:60-67 ⇒ 不存在 ⇒ 先查后报错
        cs.add(new SqlCase("r06_delete_item_notfound",
                (ids, s) -> s.deleteFavorite(DeleteFavoriteReq.newBuilder()
                        .setUserId(1001).setFavoriteId(999999).build())));
        // Go: delete_favorite_logic.go:73-79 ⇒ 归属不符 ⇒ 先查后报错
        cs.add(new SqlCase("r07_delete_item_forbidden",
                (ids, s) -> s.deleteFavorite(DeleteFavoriteReq.newBuilder()
                        .setUserId(2002).setFavoriteId(7001).build())));
        // Go: delete_favorite_folder_logic.go:60-67 ⇒ 不存在 ⇒ 先查后报错
        cs.add(new SqlCase("r08_delete_folder_notfound",
                (ids, s) -> s.deleteFavoriteFolder(DeleteFavoriteFolderReq.newBuilder()
                        .setUserId(1001).setFolderId(999999).build())));
        // Go: delete_favorite_folder_logic.go:73-79 ⇒ 归属不符 ⇒ 先查后报错
        cs.add(new SqlCase("r09_delete_folder_forbidden",
                (ids, s) -> s.deleteFavoriteFolder(DeleteFavoriteFolderReq.newBuilder()
                        .setUserId(2002).setFolderId(9001).build())));

        // ═══════════════ 写入段 ═══════════════
        // Go: create_favorite_folder_logic.go:75-87 ⇒ 先查重（SELECT）再 INSERT
        cs.add(new SqlCase("w01_create_folder_ok",
                (ids, s) -> ids.folder = s.createFavoriteFolder(CreateFavoriteFolderReq.newBuilder()
                        .setUserId(USER_ACTIVE).setName(FOLDER_NAME).build()).getFolderId()));
        // Go: create_favorite_folder_logic.go:75-87 ⇒ 命中重名 ⇒ 只查不写
        cs.add(new SqlCase("w02_create_folder_duplicate",
                (ids, s) -> s.createFavoriteFolder(CreateFavoriteFolderReq.newBuilder()
                        .setUserId(USER_ACTIVE).setName(FOLDER_NAME).build())));
        // Go: create_favorite_logic.go:102-134 ⇒ 查重 → INSERT item → 事务里锁 folder + INSERT outbox
        cs.add(new SqlCase("w03_create_item_ok",
                (ids, s) -> ids.item = s.createFavorite(CreateFavoriteReq.newBuilder()
                        .setUserId(USER_ACTIVE).setFolderId(ids.folder)
                        .setTargetId("t-77").setTargetType("post")
                        .setTitle("req-title").setCover("req-cover").build()).getFavoriteId()));
        // 同上，**第二条**（留到 w11 的级联里被撤 ⇒ 走完整的 retract 路径）
        cs.add(new SqlCase("w04_create_item_2nd_ok",
                (ids, s) -> ids.item2 = s.createFavorite(CreateFavoriteReq.newBuilder()
                        .setUserId(USER_ACTIVE).setFolderId(ids.folder)
                        .setTargetId("t-78").setTargetType("post")
                        .setTitle("req-title-2").setCover("req-cover-2").build()).getFavoriteId()));
        // Go: create_favorite_logic.go:122-127 ⇒ 命中已收藏 ⇒ 只查不写
        cs.add(new SqlCase("w05_create_item_duplicate",
                (ids, s) -> s.createFavorite(CreateFavoriteReq.newBuilder()
                        .setUserId(USER_ACTIVE).setFolderId(ids.folder)
                        .setTargetId("t-77").setTargetType("post").build())));
        // Go: update_favorite_folder_name_logic.go:91-126 ⇒ 先查后 UPDATE（只写 name 一列）
        cs.add(new SqlCase("w06_update_folder_ok",
                (ids, s) -> s.updateFavoriteFolderName(UpdateFavoriteFolderNameReq.newBuilder()
                        .setUserId(1001).setFolderId(9001).setName("Alpha2").build())));
        // Go: delete_favorite_logic.go:81-95 ⇒ 事务：锁 item FOR UPDATE → 事实判定 → INSERT retract → DELETE
        //   （这条是本次运行建的 ⇒ 带 assert 事实 ⇒ 走**完整的 v2 retract** 路径）
        cs.add(new SqlCase("w07_delete_created_item_ok",
                (ids, s) -> s.deleteFavorite(DeleteFavoriteReq.newBuilder()
                        .setUserId(USER_ACTIVE).setFavoriteId(ids.item).build())));
        // 同上，但对象是**种子行**（没有 assert 事实）⇒ 两侧都走「legacy 未批准 ⇒ 拒绝撤回」
        cs.add(new SqlCase("w08_delete_seed_item_ok",
                (ids, s) -> s.deleteFavorite(DeleteFavoriteReq.newBuilder()
                        .setUserId(1001).setFavoriteId(7001).build())));
        // Go: delete_favorite_folder_logic.go:81-95 ⇒ 空夹级联（**不产任何事件**）
        cs.add(new SqlCase("w09_delete_folder_empty_ok",
                (ids, s) -> s.deleteFavoriteFolder(DeleteFavoriteFolderReq.newBuilder()
                        .setUserId(1001).setFolderId(9002).build())));
        // Go: delete_favorite_folder_logic.go:336-378 ⇒ 种子行的级联（legacy ⇒ 不产事件）
        cs.add(new SqlCase("w10_delete_folder_cascade_ok",
                (ids, s) -> s.deleteFavoriteFolder(DeleteFavoriteFolderReq.newBuilder()
                        .setUserId(2002).setFolderId(9003).build())));

        // ═══════════════ L1 读段（**刻意放在写入之后**）═══════════════
        // 🔴 目的：把「SQL 层的差异」一路追到**对外响应**上。
        //    上面三个用例改的是：收藏夹的 create_time/update_time、条目的 create_time。
        //    ⇒ 读回来时，Go 侧是真实时钟值、RAG 侧若是 NULL 就会变成 0
        //       （FavoriteRpcSupport.unixMillis：null → 0L，FavoriteRpcSupport.java:213-215）。
        //    ⇒ 这三个用例的形状里**保留时间戳、屏蔽雪花 ID** ⇒ 两侧一比即见。
        //
        // ⚠️ 为什么排在 w11（删掉新建的收藏夹）**之前**：w11 会把 ids.folder 连同条目一起删掉。
        // ⚠️ 为什么可以排在写段**之后**：本探针**不冻结**这些用例的期望值 ——
        //    它比的是「**同一次运行内**两侧的响应」，不是「与某个冻结值」⇒ 不受写序影响。
        cs.add(new SqlCase("z01_list_folders_created",
                (ids, s) -> s.getFavoriteFolders(GetFavoriteFoldersReq.newBuilder()
                        .setUserId(USER_ACTIVE).build()),
                FavoriteSqlStatementParityTest::renderFolderList));
        cs.add(new SqlCase("z02_list_items_created",
                (ids, s) -> s.getFavoritesByFolder(GetFavoritesByFolderReq.newBuilder()
                        .setUserId(USER_ACTIVE).setFolderId(ids.folder).build()),
                FavoriteSqlStatementParityTest::renderItemList));
        cs.add(new SqlCase("z03_list_folders_user1001_after_rename",
                (ids, s) -> s.getFavoriteFolders(GetFavoriteFoldersReq.newBuilder()
                        .setUserId(1001).build()),
                FavoriteSqlStatementParityTest::renderFolderList));

        // Go: delete_favorite_folder_logic.go:336-378 ⇒ 本次建的夹 ⇒ 里面还剩一条**带事实**的条目
        //   ⇒ 逐条 retract（INSERT outbox）→ DELETE items → DELETE folder 的**完整**路径
        cs.add(new SqlCase("w11_delete_created_folder_ok",
                (ids, s) -> s.deleteFavoriteFolder(DeleteFavoriteFolderReq.newBuilder()
                        .setUserId(USER_ACTIVE).setFolderId(ids.folder).build())));

        return cs;
    }

    /**
     * 收藏夹列表的 L1 形状。
     *
     * <p>🔴 <b>屏蔽 folder_id</b>（两侧的雪花 ID 必然不同），<b>保留 name / created_at / updated_at</b>
     * —— 后两者正是本轮要验的东西。
     */
    static String renderFolderList(Object response) {
        GetFavoriteFoldersResp resp = (GetFavoriteFoldersResp) response;
        StringBuilder sb = new StringBuilder();
        for (com.rag.favorite.favorite.proto.FavoriteFolder f : resp.getFoldersList()) {
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(f.getName()).append("|c=").append(f.getCreatedAt())
                    .append("|u=").append(f.getUpdatedAt());
        }
        return "n=" + resp.getFoldersCount() + " [" + sb + "]";
    }

    /**
     * 条目列表的 L1 形状。
     *
     * <p>🔴 <b>屏蔽 favorite_id</b>，保留 target / title / cover / created_at，
     * 并且**保留列表顺序** ⇒ 顺带能看出「排序是否一致」。
     */
    static String renderItemList(Object response) {
        GetFavoritesByFolderResp resp = (GetFavoritesByFolderResp) response;
        StringBuilder sb = new StringBuilder();
        for (com.rag.favorite.favorite.proto.FavoriteItem it : resp.getFavoritesList()) {
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(it.getTargetId()).append("|").append(it.getTargetType())
                    .append("|").append(it.getTitle())
                    .append("|c=").append(it.getCreatedAt());
        }
        return "n=" + resp.getFavoritesCount() + " [" + sb + "]";
    }

    // ══════════════════════════ 执行 ══════════════════════════

    @BeforeAll
    static void openChannels() {
        goChannel = ManagedChannelBuilder.forTarget(env("PROBE_GO_TARGET")).usePlaintext().build();
        ragChannel = ManagedChannelBuilder.forTarget(env("PROBE_RAG_TARGET")).usePlaintext().build();
        // ⚠️ 标记连接必须是**独立**的连接 ⇒ 它的 application_name 与两侧都不同
        //    ⇒ 标记语句不会落进任何一侧的桶里。
        //    ⚠️ URL 由 run.sh **单独**给（PROBE_JDBC_URL_MARKER）——
        //    不在 PROBE_JDBC_URL 后面追加 ApplicationName：pgjdbc 解析重复键时
        //    「谁覆盖谁」是实现细节 ⇒ 万一被 app 侧的 rag_side 覆盖，
        //    marker 就会落进 RAG 桶 ⇒ 分段全错，而且是**静默**的。
        markerJdbc = new JdbcTemplate(new DriverManagerDataSource(
                env("PROBE_JDBC_URL_MARKER"), env("PROBE_JDBC_USER"), env("PROBE_JDBC_PASSWORD")));
    }

    @AfterAll
    static void closeChannels() {
        if (goChannel != null) {
            goChannel.shutdownNow();
        }
        if (ragChannel != null) {
            ragChannel.shutdownNow();
        }
    }

    @Test
    @DisplayName("SQL 语句层双侧活体对拍：Go 真源 vs RAG（同一 PG 实例 · application_name 分流 · marker 分段）")
    void sqlStatementParity() throws Exception {
        // ── 第一步：灌 RAG 侧种子（**必须在第一个 marker 之前**）────────────
        //    Go 侧种子由 run.sh 用 psql 灌（psql 的 application_name 不是 go_side ⇒ 不进 Go 桶）。
        applySeed(Paths.get(env("PROBE_SEED")));

        // ── 第二步：逐用例「Go 一遍 → RAG 一遍」，每遍之前发 marker ────────
        List<SqlCase> cases = matrix();
        StringBuilder report = new StringBuilder();
        Ids goIds = new Ids();
        Ids ragIds = new Ids();
        int seq = 0;

        for (SqlCase c : cases) {
            seq++;
            Side go = runSide(c, goChannel, goIds, "go", seq);
            Side rag = runSide(c, ragChannel, ragIds, "rag", seq);
            report.append(c.id()).append('\t').append(go.outcome).append('\t').append(rag.outcome)
                    .append('\t').append(go.shape).append('\t').append(rag.shape).append('\n');
        }

        Path out = Paths.get(env("PROBE_OUT_CASES"));
        Files.writeString(out, report.toString(), StandardCharsets.UTF_8);

        // 🔴 两侧的**业务出口**必须一致 —— 否则「SQL 不同」可能只是「走了不同分支」的假象。
        //    这一条不成立时，SQL 层的差异不可比（脚本会先报这条）。
        // ⚠️ **L1 响应形状（后两列）不在这里判** —— 形状不同是**要报告的发现**，
        //    不是「探针跑不起来」⇒ 判它会让探针在出报告**之前**就挂掉。
        //    形状差异由 compare.py 汇总进报告与退出码。
        String[] lines = report.toString().split("\n");
        int mismatch = 0;
        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.split("\t");
            if (parts.length >= 3 && !parts[1].equals(parts[2])) {
                mismatch++;
            }
        }
        if (mismatch > 0) {
            throw new IllegalStateException("两侧业务出口不一致（" + mismatch + " 例）⇒ SQL 层不可比，"
                    + "先看 " + out);
        }
    }

    /** 一侧跑完一个用例的结果：业务出口 + L1 响应形状。 */
    private static final class Side {
        final String outcome;
        final String shape;

        Side(String outcome, String shape) {
            this.outcome = outcome;
            this.shape = shape;
        }
    }

    /**
     * 跑一个用例的一侧：<b>先发 marker，再调用</b>。
     *
     * @return 该侧的出口描述（{@code OK} 或 {@code CODE:description}）+ L1 响应形状
     */
    private static Side runSide(SqlCase c, ManagedChannel channel, Ids ids,
                                String side, int seq) {
        marker(side, c.id(), seq);
        FavoriteServiceGrpc.FavoriteServiceBlockingStub stub =
                FavoriteServiceGrpc.newBlockingStub(channel);
        try {
            Object resp = c.call().invoke(ids, stub);
            String shape = "-";
            if (c.shape() != null && resp != null) {
                shape = c.shape().apply(resp);
            }
            return new Side("OK", shape);
        } catch (StatusRuntimeException e) {
            return new Side(e.getStatus().getCode() + ":" + e.getStatus().getDescription(), "-");
        }
    }

    /**
     * 发一条 marker 语句。
     *
     * <p>🔴 marker 文本必须<b>每条不同</b>（带 seq）⇒ 否则 pgjdbc 会复用服务端预备语句，
     * 日志里出现的是「同一个 SQL 文本被反复执行」，切段就切不准了。
     */
    private static void marker(String side, String caseId, int seq) {
        markerJdbc.queryForObject(
                "SELECT 'SQLMARK::" + side + "::" + caseId + "::" + seq + "'", String.class);
    }

    // ── 种子（与 grpc-contract-probe 共用同一份 seed.sql ⇒ 两侧初始数据完全一致）──────────────

    /**
     * 灌种子（与 grpc-contract-probe 共用同一份 seed.sql ⇒ 两侧初始数据完全一致）。
     *
     * 🔴 <b>坑（2026-10-26 第二十轮实测踩到）</b>：<b>必须先剥掉 {@code --} 注释行，再按 {@code ;} 切</b>。
     * <p>反过来（先切 {@code ;} 再跳过「以 -- 开头的块」）会**静默丢掉第一条 INSERT** ——
     * 因为 seed.sql 的头部注释块和第一条 INSERT 之间<b>没有分号</b>，
     * 切出来的第一块是「注释 + INSERT」，以 {@code --} 开头 ⇒ 被整块跳过。
     * <p>症状离根因很远：RAG 侧<b>没有收藏夹</b> ⇒ 读用例返回空集、
     * 归属用例变成 NOT_FOUND ⇒ 看起来像「两侧行为不一致」，实际是<b>种子没灌进去</b>。
     */
    private void applySeed(Path seedPath) throws Exception {
        String sql = Files.readString(seedPath, StandardCharsets.UTF_8);
        StringBuilder cleaned = new StringBuilder();
        for (String line : sql.split("\n")) {
            String trimmed = line.strip();
            if (trimmed.startsWith("--") || trimmed.isEmpty()) {
                continue;
            }
            cleaned.append(line).append('\n');
        }
        for (String stmt : cleaned.toString().split(";")) {
            String s = stmt.strip();
            if (!s.isEmpty()) {
                ragJdbc.execute(s);
            }
        }
    }
}
