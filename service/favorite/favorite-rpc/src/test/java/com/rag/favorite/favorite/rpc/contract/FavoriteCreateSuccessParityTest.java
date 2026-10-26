package com.rag.favorite.favorite.rpc.contract;

import com.rag.favorite.favorite.proto.CreateFavoriteFolderReq;
import com.rag.favorite.favorite.proto.CreateFavoriteFolderResp;
import com.rag.favorite.favorite.proto.CreateFavoriteReq;
import com.rag.favorite.favorite.proto.FavoriteServiceGrpc;
import io.grpc.Channel;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 🔴🔴🔴 <b>create 成功路径</b>的双侧活体对拍 —— favorite 域。
 *
 * <h2>它补的是什么缺口</h2>
 * <p>台账 {@code docs/DRIFT/favorite/rpc.md} §24.2 的 <b>U8</b>：
 * 原有的 {@code FavoriteGrpcContractParityTest} <b>故意不接</b> user-rpc
 * ⇒ {@code ensureUserExists} <b>必失败</b> ⇒
 * 「<b>uid 合法 + user-rpc 在场 ⇒ 真创建成功</b>」这条路径
 * <b>一次都没被跑到过</b>。
 * <p>⇒ 落库、雪花 ID、{@code favorite_item} / {@code favorite_fact_outbox} 的联动，
 * 以及 {@code ensureUserExists} 的<b>其余四条分支</b>，都只有源码级证据。
 *
 * <h2>上游是「两侧共同的一个桩」</h2>
 * <p>探针自带一个<b>最小 user-rpc / article-rpc 桩</b>（Go 写的，
 * {@code tools/grpc-contract-probe/favorite/go/service/favorite/rpc/cmd/upstreamstub/main.go}），
 * <b>两侧都打它</b> ⇒ 两边收到的字节相同 ⇒ 差出来的才是
 * 「favorite 域自己的行为差异」，不是「两个桩的差异」。
 *
 * <p>🔴 <b>桩的边界</b>：桩 ≠ 真 user-rpc / article-rpc ⇒ 本探针证明的是
 * 「<b>favorite 域自己在这条路径上的行为两侧一致</b>」，
 * <b>不</b>证明「favorite 与 user-rpc / article-rpc 之间的交互契约」。
 * ⇒ 期望值<b>只能</b>由「Go 真源 favorite-rpc + 真 PG」产出。
 *
 * <h2>本类比原探针多出来的四类证据</h2>
 * <ol>
 *   <li><b>成功路径本身</b>：{@code CreateFavoriteFolder} / {@code CreateFavorite} 真成功；
 *       响应体只含雪花 ID（两侧必然不同）⇒ 只断言「ID &gt; 0」，
 *       <b>真正的内容等价性靠第 4 条（落库比对）</b>。</li>
 *   <li><b>{@code ensureUserExists} 的五条分支</b>（Go helper.go:99-120）逐条跑到：
 *       RPC NotFound / resp.found=false / uid 不符 / <b>status 字段缺失</b> / status≠0。</li>
 *   <li><b>成功后的重复创建</b>：{@code AlreadyExists} 两条（收藏夹重名 / 内容已收藏）。</li>
 *   <li><b>落库等价性</b>：两侧库里的 folder / item / outbox 三张表的<b>投影</b>逐字段相同
 *       —— 这是「写下去的东西一样」的<b>直接</b>证据，
 *       也顺带钉住 favorite 最核心的规则
 *       「<b>title / cover / revision 只来自公开投影，请求参数不能覆盖</b>」
 *       （Go create_favorite_logic.go:102-120）。</li>
 * </ol>
 *
 * <h2>为什么观测代码是<b>复用</b>的</h2>
 * <p>{@link FavoriteGrpcContractParityTest} 的观测工具（{@code observe} / {@code Shape} /
 * {@code Case} / {@code keys} / {@code hex}）被放宽成 package-private 后<b>原样复用</b>
 * ⇒ 两个探针的「观测口径」<b>不可能漂移</b>。
 * 若这里另写一套渲染逻辑，两个探针的冻结值格式就会各走各的 ⇒ 失守。
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
@EnabledIfEnvironmentVariable(named = "PROBE_GO_TARGET_SUCCESS", matches = ".+")
class FavoriteCreateSuccessParityTest {

    // ══════════════════════════ 环境 ══════════════════════════

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> env("PROBE_JDBC_URL"));
        registry.add("spring.datasource.username", () -> env("PROBE_JDBC_USER"));
        registry.add("spring.datasource.password", () -> env("PROBE_JDBC_PASSWORD"));
        registry.add("grpc.server.port", () -> env("PROBE_RAG_PORT_SUCCESS"));
        // 🔴 本探针**接上**上游：user-rpc / article-rpc 都指向探针自带的那个桩。
        //    （原探针把这两个指到 127.0.0.1:1 ⇒ 走的是「下游不可用」那条分支。）
        registry.add("grpc.client.user-rpc.address", () -> env("PROBE_USER_RPC_ADDR"));
        registry.add("grpc.client.article-rpc.address", () -> env("PROBE_ARTICLE_RPC_ADDR"));
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

    @Autowired
    private JdbcTemplate ragJdbc;

    /** Go 真源那一侧的库（godb）—— 用来做「两侧落库等价性」比对。 */
    private static JdbcTemplate goJdbc;

    private static ManagedChannel goChannel;
    private static ManagedChannel ragChannel;

    // ══════════════════════════ 桩约定（与 upstreamstub/main.go 一一对应）══════════════════

    /** 正常用户：status=0 且字段**存在** ⇒ 走成功路径。 */
    private static final long USER_ACTIVE = 2001L;
    /** resp.found=false ⇒ Go helper.go:109-112 ⇒ NotFound/5101。 */
    private static final long USER_NOT_FOUND_RESP = 2002L;
    /** User 存在但 **status 字段缺失** ⇒ Go helper.go:113-116 ⇒ Unavailable/5001。 */
    private static final long USER_STATUS_ABSENT = 2003L;
    /** status=1 ⇒ Go helper.go:117-120 ⇒ PermissionDenied/5005。 */
    private static final long USER_FORBIDDEN = 2004L;
    /** User 存在但 uid 与请求不符 ⇒ Go helper.go:113-116 ⇒ Unavailable/5001。 */
    private static final long USER_UID_MISMATCH = 2005L;
    /** RPC 直接返回 NotFound ⇒ Go helper.go:103-106 ⇒ NotFound/5101。 */
    private static final long USER_NOT_FOUND_RPC = 2006L;
    /** RPC 返回 Internal ⇒ Go helper.go:107 ⇒ Internal/5001。 */
    private static final long USER_INTERNAL_RPC = 2007L;

    /** 成功路径用的收藏夹名（两侧同名 ⇒ 重名用例才有意义）。 */
    private static final String FOLDER_NAME = "ParityFolder";

    // ══════════════════════════ 矩阵 ══════════════════════════

    /**
     * 成功路径之后的用例矩阵。
     *
     * @param folderId <b>该侧自己</b>刚建出来的收藏夹 ID（两侧的雪花 ID 必然不同 ⇒ 各用各的）
     */
    private static List<FavoriteGrpcContractParityTest.Case> matrix(long folderId) {
        List<FavoriteGrpcContractParityTest.Case> cs = new ArrayList<>();

        // create_favorite_folder_logic.go:75-87 —— 重名 ⇒ AlreadyExists/5203
        cs.add(new FavoriteGrpcContractParityTest.Case("s02_create_folder_duplicate",
                FavoriteGrpcContractParityTest.Shape.NONE,
                s -> s.createFavoriteFolder(CreateFavoriteFolderReq.newBuilder()
                        .setUserId(USER_ACTIVE).setName(FOLDER_NAME).build())));

        // helper.go:109-112 —— resp.found=false ⇒ NotFound/5101
        cs.add(new FavoriteGrpcContractParityTest.Case("s03_create_folder_user_notfound_resp",
                FavoriteGrpcContractParityTest.Shape.NONE,
                s -> s.createFavoriteFolder(CreateFavoriteFolderReq.newBuilder()
                        .setUserId(USER_NOT_FOUND_RESP).setName("probe-nf-resp").build())));

        // helper.go:103-106 —— RPC NotFound ⇒ NotFound/5101
        cs.add(new FavoriteGrpcContractParityTest.Case("s04_create_folder_user_notfound_rpc",
                FavoriteGrpcContractParityTest.Shape.NONE,
                s -> s.createFavoriteFolder(CreateFavoriteFolderReq.newBuilder()
                        .setUserId(USER_NOT_FOUND_RPC).setName("probe-nf-rpc").build())));

        // helper.go:113-116 —— **status 字段缺失** ⇒ Unavailable/5001
        //   （Java 侧若用 getStatus()!=0 而不是 hasStatus() ⇒ 会变成 PermissionDenied/5005）
        cs.add(new FavoriteGrpcContractParityTest.Case("s05_create_folder_user_status_absent",
                FavoriteGrpcContractParityTest.Shape.NONE,
                s -> s.createFavoriteFolder(CreateFavoriteFolderReq.newBuilder()
                        .setUserId(USER_STATUS_ABSENT).setName("probe-status-absent").build())));

        // helper.go:113-116 —— uid 与请求不符 ⇒ Unavailable/5001
        cs.add(new FavoriteGrpcContractParityTest.Case("s06_create_folder_user_uid_mismatch",
                FavoriteGrpcContractParityTest.Shape.NONE,
                s -> s.createFavoriteFolder(CreateFavoriteFolderReq.newBuilder()
                        .setUserId(USER_UID_MISMATCH).setName("probe-uid-mismatch").build())));

        // helper.go:117-120 —— status≠0 ⇒ PermissionDenied/5005
        cs.add(new FavoriteGrpcContractParityTest.Case("s07_create_folder_user_forbidden",
                FavoriteGrpcContractParityTest.Shape.NONE,
                s -> s.createFavoriteFolder(CreateFavoriteFolderReq.newBuilder()
                        .setUserId(USER_FORBIDDEN).setName("probe-forbidden").build())));

        // helper.go:107 —— RPC 其它错误 ⇒ Internal/5001
        cs.add(new FavoriteGrpcContractParityTest.Case("s08_create_folder_user_rpc_internal",
                FavoriteGrpcContractParityTest.Shape.NONE,
                s -> s.createFavoriteFolder(CreateFavoriteFolderReq.newBuilder()
                        .setUserId(USER_INTERNAL_RPC).setName("probe-rpc-internal").build())));

        // create_favorite_logic.go:102-120 —— target_type ≠ "article" ⇒ title/cover 用请求里的值
        cs.add(new FavoriteGrpcContractParityTest.Case("s09_create_item_ok_post",
                FavoriteGrpcContractParityTest.Shape.FAVORITE_ID,
                s -> s.createFavorite(CreateFavoriteReq.newBuilder()
                        .setUserId(USER_ACTIVE).setFolderId(folderId)
                        .setTargetId("t-77").setTargetType("post")
                        .setTitle("req-title-post").setCover("req-cover-post").build())));

        // create_favorite_logic.go:105-120 —— target_type == "article" ⇒
        //   title/cover/revision **只**来自 article 公开投影（请求里的被无条件覆盖）
        cs.add(new FavoriteGrpcContractParityTest.Case("s10_create_item_ok_article",
                FavoriteGrpcContractParityTest.Shape.FAVORITE_ID,
                s -> s.createFavorite(CreateFavoriteReq.newBuilder()
                        .setUserId(USER_ACTIVE).setFolderId(folderId)
                        .setTargetId("art-1").setTargetType("article")
                        .setTitle("req-title-ignored").setCover("req-cover-ignored").build())));

        // articleCover 的**二级回退**：桩不返回 cover_image_url，只在 ext_info["cover"] 里给
        cs.add(new FavoriteGrpcContractParityTest.Case("s11_create_item_ok_article_ext_cover",
                FavoriteGrpcContractParityTest.Shape.FAVORITE_ID,
                s -> s.createFavorite(CreateFavoriteReq.newBuilder()
                        .setUserId(USER_ACTIVE).setFolderId(folderId)
                        .setTargetId("art-2").setTargetType("article")
                        .setTitle("req-title-ignored-2").setCover("req-cover-ignored-2").build())));

        // create_favorite_logic.go:122-127 —— 同一 folder + 同一 target ⇒ AlreadyExists/5302
        cs.add(new FavoriteGrpcContractParityTest.Case("s12_create_item_duplicate",
                FavoriteGrpcContractParityTest.Shape.NONE,
                s -> s.createFavorite(CreateFavoriteReq.newBuilder()
                        .setUserId(USER_ACTIVE).setFolderId(folderId)
                        .setTargetId("t-77").setTargetType("post")
                        .setTitle("req-title-post").setCover("req-cover-post").build())));

        return cs;
    }

    // ══════════════════════════ 执行 ══════════════════════════

    @BeforeAll
    static void openChannels() throws Exception {
        goChannel = ManagedChannelBuilder.forTarget(env("PROBE_GO_TARGET_SUCCESS")).usePlaintext().build();
        ragChannel = ManagedChannelBuilder.forTarget(env("PROBE_RAG_TARGET_SUCCESS")).usePlaintext().build();
        goJdbc = new JdbcTemplate(new DriverManagerDataSource(
                env("PROBE_JDBC_URL_GO"), env("PROBE_JDBC_USER"), env("PROBE_JDBC_PASSWORD")));
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
    @DisplayName("create 成功路径 + ensureUserExists 五条分支 + 落库等价性：Go 真源 vs RAG（同客户端双打）")
    void createSuccessParity() throws Exception {
        Map<String, String> goValues = new LinkedHashMap<>();
        Map<String, String> ragValues = new LinkedHashMap<>();

        // ── 第一步：真创建出一个收藏夹（成功路径的入口）──────────────────────
        //    响应体只含雪花 ID（两侧必然不同）⇒ 观测值只断言「ID > 0」，
        //    真正的内容等价性由后面的「落库等价性」三个用例承担。
        long[] goFolderId = new long[1];
        long[] ragFolderId = new long[1];
        FavoriteGrpcContractParityTest.Shape goFolderShape = captureFolderId(goFolderId);
        FavoriteGrpcContractParityTest.Shape ragFolderShape = captureFolderId(ragFolderId);

        FavoriteGrpcContractParityTest.Case goCreate =
                new FavoriteGrpcContractParityTest.Case("s01_create_folder_ok", goFolderShape,
                        s -> s.createFavoriteFolder(CreateFavoriteFolderReq.newBuilder()
                                .setUserId(USER_ACTIVE).setName(FOLDER_NAME).build()));
        FavoriteGrpcContractParityTest.Case ragCreate =
                new FavoriteGrpcContractParityTest.Case("s01_create_folder_ok", ragFolderShape,
                        s -> s.createFavoriteFolder(CreateFavoriteFolderReq.newBuilder()
                                .setUserId(USER_ACTIVE).setName(FOLDER_NAME).build()));

        goValues.put(goCreate.id(), FavoriteGrpcContractParityTest.observe(goCreate, goChannel));
        ragValues.put(ragCreate.id(), FavoriteGrpcContractParityTest.observe(ragCreate, ragChannel));

        assertTrue(goFolderId[0] > 0, "Go 侧没有真的建出收藏夹（folder_id=" + goFolderId[0] + "）");
        assertTrue(ragFolderId[0] > 0, "RAG 侧没有真的建出收藏夹（folder_id=" + ragFolderId[0] + "）");

        // ── 第二步：其余用例（两侧各用自己那个 folder_id）────────────────────
        for (FavoriteGrpcContractParityTest.Case c : matrix(goFolderId[0])) {
            goValues.put(c.id(), FavoriteGrpcContractParityTest.observe(c, goChannel));
        }
        for (FavoriteGrpcContractParityTest.Case c : matrix(ragFolderId[0])) {
            ragValues.put(c.id(), FavoriteGrpcContractParityTest.observe(c, ragChannel));
        }

        // ── 第三步：落库等价性（三张表的投影逐字段比对）──────────────────────
        goValues.put("d01_db_folder_rows", projectFolders(goJdbc));
        ragValues.put("d01_db_folder_rows", projectFolders(ragJdbc));
        goValues.put("d02_db_item_rows", projectItems(goJdbc));
        ragValues.put("d02_db_item_rows", projectItems(ragJdbc));
        goValues.put("d03_db_outbox_rows", projectOutbox(goJdbc));
        ragValues.put("d03_db_outbox_rows", projectOutbox(ragJdbc));

        writeResults(Paths.get(env("PROBE_OUT_GO_SUCCESS")), goValues);
        writeResults(Paths.get(env("PROBE_OUT_RAG_SUCCESS")), ragValues);

        // ── 与冻结的 expected-success.txt 比对（跑 mvn test 单独也能当回归门禁）──
        Path expectedPath = Paths.get(env("PROBE_EXPECTED_SUCCESS"));
        if (!Files.exists(expectedPath)) {
            System.out.println("[create-success-probe] expected-success.txt 尚不存在 ⇒ 本次只测量不判定");
            return;
        }
        Map<String, String[]> expected = readExpected(expectedPath);
        assertEquals(expected.size(), goValues.size(),
                "expected-success.txt 的 case 数与矩阵不一致（探针改了矩阵就同步更新 expected-success.txt）");

        int same = 0;
        int sameCode = 0;
        int obs = 0;
        int skip = 0;
        for (Map.Entry<String, String[]> e : expected.entrySet()) {
            String cid = e.getKey();
            String kind = e.getValue()[0];
            String frozen = e.getValue()[1];
            String go = goValues.get(cid);
            String rag = ragValues.get(cid);

            assertNotNull(go, "Go 侧没有跑这个 case：" + cid);
            assertNotNull(rag, "RAG 侧没有跑这个 case：" + cid);

            // 哨兵：Go 真源变了 ⇒ 这里立刻响
            assertEquals(frozen, go, "Go 真源的行为变了：" + cid
                    + "\n  冻结值：" + frozen + "\n  实测值：" + go);

            switch (kind) {
                case "SAME" -> {
                    same++;
                    assertEquals(go, rag, "两侧不一致：" + cid
                            + "\n  Go : " + go + "\n  RAG: " + rag);
                }
                case "SAME_CODE" -> {
                    sameCode++;
                    assertEquals(field(go, "code"), field(rag, "code"), "两侧 status code 不一致：" + cid
                            + "\n  Go : " + go + "\n  RAG: " + rag);
                }
                case "OBS" -> {
                    obs++;
                    assertTrue(!go.equals(rag), "OBS 段两侧变成一样了（请重评台账）：" + cid
                            + "\n  Go : " + go + "\n  RAG: " + rag);
                }
                case "SKIP" -> skip++;
                default -> throw new IllegalStateException("expected-success.txt 出现未知的 kind：" + kind + "（" + cid + "）");
            }
        }
        System.out.printf("[create-success-probe] SAME %d 条 / SAME_CODE %d 条 / OBS %d 条 / SKIP %d 条（共 %d 个 case）%n",
                same, sameCode, obs, skip, expected.size());
    }

    // ══════════════════════════ 落库投影 ══════════════════════════

    /**
     * 收藏夹投影：<b>只取名字</b>。
     *
     * <p>folder_id 是雪花 ID（两侧必然不同）⇒ 不进投影。
     */
    private String projectFolders(JdbcTemplate jdbc) {
        List<String> names = jdbc.queryForList(
                "SELECT name FROM favorite_folder WHERE user_id = ? ORDER BY name",
                String.class, USER_ACTIVE);
        return "rows=" + names.size() + ";names=" + String.join(",", names);
    }

    /**
     * 收藏项投影。
     *
     * <p>🔴 <b>title / cover 必须等于「公开投影里的值」</b>（对 target_type=article 的两条）
     * ⇒ 这就是「请求参数不能覆盖冻结修订」这条规则的<b>直接</b>证据。
     * <p>folder_id 是雪花 ID ⇒ 不进投影；改成<b>它所属收藏夹的 owner</b>
     * ⇒ 既保住了「归属」这条不变量，又不引入雪花 ID。
     */
    private String projectItems(JdbcTemplate jdbc) {
        List<String> rows = jdbc.queryForList(
                "SELECT i.target_id || '~' || i.target_type || '~' || coalesce(i.target_revision,'') || '~' "
                        + "|| coalesce(i.title,'') || '~' || coalesce(i.cover,'') || '~owner=' "
                        + "|| coalesce((SELECT cast(f.user_id as text) FROM favorite_folder f "
                        + "           WHERE f.folder_id = i.folder_id), 'NO-FOLDER') "
                        + "FROM favorite_item i WHERE i.user_id = ? ORDER BY i.target_id, i.target_type",
                String.class, USER_ACTIVE);
        return "rows=" + rows.size() + ";items=" + String.join(",", rows);
    }

    /**
     * 事实 outbox 投影。
     *
     * <p>🔴 <b>payload 里的 favorite_id / folder_id / event_id / 时间戳都是雪花或时刻</b>
     * ⇒ 不进投影；只取「契约形状」：版本 / 事件类型 / schema / 生产者 /
     * 目标三元组 / 操作。
     */
    private String projectOutbox(JdbcTemplate jdbc) {
        List<String> rows = jdbc.queryForList(
                "SELECT o.aggregate_version || '~' || o.status || '~' "
                        + "|| coalesce(o.payload->>'event_type','') || '~' || coalesce(o.payload->>'schema_version','') || '~' "
                        + "|| coalesce(o.payload->>'producer','') || '~' || coalesce(o.payload->'payload'->>'target_type','') || '~' "
                        + "|| coalesce(o.payload->'payload'->>'target_id','') || '~' "
                        + "|| coalesce(o.payload->'payload'->>'target_revision','') || '~' "
                        + "|| coalesce(o.payload->'payload'->>'operation','') "
                        + "FROM favorite_fact_outbox o "
                        + "WHERE o.favorite_id IN (SELECT favorite_id FROM favorite_item WHERE user_id = ?) "
                        + "ORDER BY o.aggregate_version, o.payload->>'event_type'",
                String.class, USER_ACTIVE);
        return "rows=" + rows.size() + ";facts=" + String.join(",", rows);
    }

    // ══════════════════════════ 辅助 ══════════════════════════

    /** 建一个会「顺手把 folder_id 记进 holder」的响应呈现方式。 */
    private static FavoriteGrpcContractParityTest.Shape captureFolderId(long[] holder) {
        return response -> {
            if (response == null) {
                return "-";
            }
            long id = ((CreateFavoriteFolderResp) response).getFolderId();
            holder[0] = id;
            return "shape:folder_id>0=" + (id > 0);
        };
    }

    /** 取 {@code key=value} 形式里的 value（值本身可能含 '='，故只切第一个）。 */
    private static String field(String value, String key) {
        for (String part : value.split("\\|")) {
            int eq = part.indexOf('=');
            if (eq > 0 && part.substring(0, eq).equals(key)) {
                return part.substring(eq + 1);
            }
        }
        throw new IllegalStateException("取值里没有字段 " + key + "：" + value);
    }

    private static void writeResults(Path path, Map<String, String> values) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : values.entrySet()) {
            sb.append(e.getKey()).append('\t').append(e.getValue()).append('\n');
        }
        Files.writeString(path, sb.toString(), StandardCharsets.UTF_8);
    }

    /** 读 expected-success.txt ⇒ case_id → [kind, value]。 */
    private static Map<String, String[]> readExpected(Path path) throws Exception {
        Map<String, String[]> out = new LinkedHashMap<>();
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            String l = line.strip();
            if (l.isEmpty() || l.startsWith("#")) {
                continue;
            }
            String[] cols = l.split("\t", 3);
            if (cols.length < 3) {
                throw new IllegalStateException("expected-success.txt 行格式错误（应为 case_id\\tkind\\tvalue）：" + line);
            }
            out.put(cols[0], new String[] {cols[1], cols[2]});
        }
        return out;
    }
}
