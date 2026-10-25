package com.rag.favorite.favorite.rpc.contract;

import com.rag.favorite.favorite.proto.CreateFavoriteFolderReq;
import com.rag.favorite.favorite.proto.CreateFavoriteFolderResp;
import com.rag.favorite.favorite.proto.CreateFavoriteReq;
import com.rag.favorite.favorite.proto.CreateFavoriteResp;
import com.rag.favorite.favorite.proto.DeleteFavoriteFolderReq;
import com.rag.favorite.favorite.proto.DeleteFavoriteFolderResp;
import com.rag.favorite.favorite.proto.DeleteFavoriteReq;
import com.rag.favorite.favorite.proto.DeleteFavoriteResp;
import com.rag.favorite.favorite.proto.FavoriteServiceGrpc;
import com.rag.favorite.favorite.proto.GetFavoriteFoldersReq;
import com.rag.favorite.favorite.proto.GetFavoriteFoldersResp;
import com.rag.favorite.favorite.proto.GetFavoritesByFolderReq;
import com.rag.favorite.favorite.proto.GetFavoritesByFolderResp;
import com.rag.favorite.favorite.proto.UpdateFavoriteFolderNameReq;
import com.rag.favorite.favorite.proto.UpdateFavoriteFolderNameResp;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ClientInterceptors;
import io.grpc.ForwardingClientCall;
import io.grpc.ForwardingClientCallListener;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.ClientCalls;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 🔴🔴🔴 gRPC 级<b>可观测契约</b>的双侧活体对拍 —— favorite 域。
 *
 * <h2>它替代了什么</h2>
 * <p>台账 {@code docs/DRIFT/favorite/proto.md} §4 最后一行的「🔴 未验：帧层
 * （HTTP2 / length-prefix / gRPC status 帧）」，以及 {@code common.md} §4 的
 * 「真 channel 上的 wire 字节」行。本类把这条从「🔴 未验」变成
 * 「✅ 已验（可观测契约部分）+ 🟡 库级差异已量化（原始帧字节部分）」。
 *
 * <h2>为什么用<b>同一个</b>客户端打两侧</h2>
 * <p>帧层的原始字节（HPACK 编码 / 帧序 / WINDOW_UPDATE / PING）由 <b>gRPC 库</b>决定，
 * grpc-go 与 grpc-java 必然不同，且<b>客户端不可观测</b> ⇒ 那部分不比对。
 * <p>但「帧层之上、消息层之下」还有一层<b>客户端可观测</b>的契约：
 * <ul>
 *   <li>gRPC status code（标准码，0-16）</li>
 *   <li>status description（= 本域业务文案的载体，<b>业务码靠它反查</b>）</li>
 *   <li>响应消息的 protobuf 字节</li>
 *   <li>响应头 / trailer 的 metadata key 集合</li>
 * </ul>
 * <p>要用<b>同一个</b>客户端（同一份 grpc-java stub、同一份 marshaller、同一套 metadata
 * 处理）分别调 Go 真源 server 与 RAG server，才能把「客户端自身差异」消掉，
 * 只留下「服务端差异」。
 *
 * <h2>两侧怎么跑</h2>
 * <ul>
 *   <li><b>Go 侧</b> = 真源 {@code service/favorite/rpc/favorite.go} 的<b>逐字节复制件</b>
 *       （run.sh 做 sha256 校验）+ 真源的 pb / logic / model / common，
 *       用真源自己的 {@code zrpc.MustNewServer} + {@code RegisterFavoriteServiceServer}
 *       + {@code AddUnaryInterceptors} 起<b>真 gRPC server</b>；数据面是<b>真 PG</b>。</li>
 *   <li><b>RAG 侧</b> = favorite-rpc 的<b>真 Spring 上下文</b>（devh 的真 netty gRPC server）
 *       + 真 Flyway 迁移 + 真 MyBatis + <b>真 PG</b>。</li>
 *   <li>两侧<b>共用同一份 seed.sql</b> ⇒ 读到同一批行 ⇒ 响应字节可比。</li>
 * </ul>
 *
 * <h2>为什么写入类用例排在最后</h2>
 * <p>删除 / 改名类用例会改库。矩阵里<b>所有只读用例在前、所有写入用例在后</b>，
 * 这样即便某个写入用例两侧行为分叉，也不会把后续只读用例的差异<b>放大成级联噪声</b>。
 *
 * <h2>expected.txt 的三态</h2>
 * <ul>
 *   <li><b>SAME</b> —— 两侧必须逐字节相同（含 status / description / 消息字节 / metadata key）</li>
 *   <li><b>SAME_CODE</b> —— 两侧 <b>status code 相同</b>（语义等价），description 措辞允许不同
 *       （只用于「措辞由 gRPC 库决定」的用例 ⇒ 库级差异，见台账 §20.4）</li>
 *   <li><b>OBS</b> —— 两侧<b>必须不同</b>（已登记的框架层差异）⇒ 变成一样反而是回归</li>
 *   <li><b>SKIP</b> —— 显式跳过，理由必须写在 expected.txt 的注释里</li>
 * </ul>
 *
 * <h2>Go 侧代码出处（本探针跑的每一行都是真源）</h2>
 * <ul>
 *   <li>真源的 gRPC server 接线：{@code service/favorite/rpc/favorite.go:42-49}
 *       （{@code zrpc.MustNewServer} + {@code RegisterFavoriteServiceServer}
 *        + dev/test 的 reflection + {@code AddUnaryInterceptors}）</li>
 *   <li>本域<b>唯一的跨域身份校验</b>：{@code service/favorite/rpc/internal/logic/helper.go:92-124}
 *       （{@code ensureUserExists} ⇒ 「下游不可用」那条分支的出口）</li>
 *   <li>错误出口的 wire 形态：{@code service/favorite/common/errmsg.go:104-106}
 *       （{@code GRPCError} ⇒ 标准 gRPC 码 + 业务文案）</li>
 * </ul>
 */
@SpringBootTest(
        classes = com.rag.favorite.favorite.rpc.FavoriteRpcApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                // Nacos：devh 的 @GrpcClient 在 Bean 初始化期就建 channel（不是懒建）⇒ 必须指到一个死地址
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                // user-rpc / article-rpc：本探针**故意不接**这两个依赖
                // ⇒ 走的是「下游不可用」这条契约分支（Go helper.go:103-107 / RAG FavoriteUserGuard:78-90）
                "grpc.client.user-rpc.address=static://127.0.0.1:1",
                "grpc.client.article-rpc.address=static://127.0.0.1:1",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN"
        })
@EnabledIfEnvironmentVariable(named = "PROBE_GO_TARGET", matches = ".+")
class FavoriteGrpcContractParityTest {

    // ══════════════════════════ 环境 ══════════════════════════

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> env("PROBE_JDBC_URL"));
        registry.add("spring.datasource.username", () -> env("PROBE_JDBC_USER"));
        registry.add("spring.datasource.password", () -> env("PROBE_JDBC_PASSWORD"));
        registry.add("grpc.server.port", () -> env("PROBE_RAG_PORT"));
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
    private JdbcTemplate jdbcTemplate;

    private static ManagedChannel goChannel;
    private static ManagedChannel ragChannel;

    // ══════════════════════════ 矩阵 ══════════════════════════

    /** 调用体：返回 protobuf 响应对象；出错时抛 {@link StatusRuntimeException}。 */
    private interface Call {
        Object invoke(FavoriteServiceGrpc.FavoriteServiceBlockingStub stub);
    }

    /** 响应体的呈现方式。 */
    private interface Shape {
        String render(Object response);

        /** 错误用例：没有响应体。 */
        Shape NONE = response -> "-";

        /**
         * 确定性响应：比对 protobuf 字节。
         *
         * <p>🔴 <b>null-safe 是必须的</b>：用例声明了 HEX（预期成功）但服务端实际返回了错误时，
         * 错误分支会把 null 传进来 ⇒ 若不判空，这里会抛 NPE，
         * 把「两侧行为不一致」这个**真结论**变成一个看不懂的测试崩溃。
         */
        Shape HEX = response -> response == null
                ? "-"
                : "hex:" + hex(((com.google.protobuf.MessageLite) response).toByteArray());

        /** 裸 byte[] 响应（health / 未知方法这类非 proto 服务）。 */
        Shape RAW = response -> response == null ? "-" : "hex:" + hex((byte[]) response);

        /** 雪花 ID 响应：ID 两侧必然不同（Go / Java 各生成一次）⇒ 只比对「字段存在且为正」。 */
        Shape FOLDER_ID = response -> response == null
                ? "-"
                : "shape:folder_id>0=" + (((CreateFavoriteFolderResp) response).getFolderId() > 0);

        Shape FAVORITE_ID = response -> response == null
                ? "-"
                : "shape:favorite_id>0=" + (((CreateFavoriteResp) response).getFavoriteId() > 0);
    }

    private record Case(String id, Shape shape, Call call, boolean fullHeaders) {
        Case(String id, Shape shape, Call call) {
            this(id, shape, call, false);
        }
    }

    /**
     * 记录响应头 / trailer 时的过滤规则。
     *
     * <p><b>为什么要滤掉 {@code grpc-*} 前缀的 key</b>：
     * <ul>
     *   <li>这些 key 由 <b>gRPC 协议与传输库</b>产生（{@code grpc-accept-encoding} /
     *       {@code grpc-encoding} / {@code grpc-status} / {@code grpc-message} …），
     *       <b>不是 favorite 域的行为</b>。grpc-go 与 grpc-java 在这里必然不同
     *       （实测：grpc-java 的响应头多出 {@code grpc-accept-encoding} 与
     *       {@code grpc-encoding}，grpc-go 不发）。</li>
     *   <li>把这类噪声混进业务用例的值里，会让 8 个本来<b>逐字节一致</b>的用例
     *       退化成 OBS ⇒ <b>白白丢掉消息字节的对比能力</b>。</li>
     *   <li>⇒ 折中：业务用例只比对<b>非 grpc-* 的头</b>（含 content-type 的值），
     *       同时用<b>一个专门的用例</b>把完整的头 key 集合记下来、显式登记为 OBS。
     *       这样「库级差异」既被量化、又不掩盖任何域级差异
     *       （任何<b>非</b> grpc-* 的头差异都仍然会被抓出来）。</li>
     * </ul>
     */
    private static final String GRPC_NAMESPACE_PREFIX = "grpc-";

    /**
     * 请求矩阵。
     *
     * <p>⚠️ <b>顺序即契约</b>：只读用例全部在前，写入用例全部在后。
     *
     * <p>每个用例的期望出口都标了 Go 出处行号（Go 的分支顺序决定了「哪个校验先触发」），
     * 对应真源 {@code service/favorite/rpc/internal/logic/} 下的 7 个 logic 文件
     * （例如 {@code create_favorite_folder_logic.go:37-118} 是第 1 个用例的全部出口来源）。
     */
    private static List<Case> matrix() {
        List<Case> cs = new ArrayList<>();

        // ─────────── 1. CreateFavoriteFolder（Go: create_favorite_folder_logic.go:37-118）───────────
        // create_favorite_folder_logic.go:51-57  userId <= 0 ⇒ InvalidArgument / 5003
        cs.add(new Case("c01_create_folder_uid0", Shape.NONE,
                s -> s.createFavoriteFolder(CreateFavoriteFolderReq.newBuilder().setUserId(0).setName("X").build())));
        // create_favorite_folder_logic.go:59-66  name 规范化后为空 ⇒ InvalidArgument / 5202
        cs.add(new Case("c02_create_folder_name_empty", Shape.NONE,
                s -> s.createFavoriteFolder(CreateFavoriteFolderReq.newBuilder().setUserId(1001).setName("").build())));
        // create_favorite_folder_logic.go:59-66  normalizeFolderName = TrimSpace ⇒ 纯空白也空 ⇒ 5202
        cs.add(new Case("c03_create_folder_name_blank", Shape.NONE,
                s -> s.createFavoriteFolder(CreateFavoriteFolderReq.newBuilder().setUserId(1001).setName("   ").build())));
        // create_favorite_folder_logic.go:68-73  ensureUserExists ⇒ user-rpc 不在 ⇒ Go helper.go:107 ⇒ Internal / 5001
        cs.add(new Case("c04_create_folder_user_rpc_absent", Shape.NONE,
                s -> s.createFavoriteFolder(CreateFavoriteFolderReq.newBuilder().setUserId(1001).setName("NewFolder").build())));

        // ─────────── 2. CreateFavorite（Go: create_favorite_logic.go:38-169）───────────
        // create_favorite_logic.go:54-60  userId <= 0 || folderId <= 0 ⇒ InvalidArgument / 5003
        cs.add(new Case("c05_create_item_uid0", Shape.NONE,
                s -> s.createFavorite(CreateFavoriteReq.newBuilder().setUserId(0).setFolderId(9001)
                        .setTargetId("t-9").setTargetType("post").build())));
        cs.add(new Case("c06_create_item_folder0", Shape.NONE,
                s -> s.createFavorite(CreateFavoriteReq.newBuilder().setUserId(1001).setFolderId(0)
                        .setTargetId("t-9").setTargetType("post").build())));
        // create_favorite_logic.go:62-70  target_id 空 ⇒ InvalidArgument / 5303
        cs.add(new Case("c07_create_item_target_empty", Shape.NONE,
                s -> s.createFavorite(CreateFavoriteReq.newBuilder().setUserId(1001).setFolderId(9001)
                        .setTargetId("").setTargetType("post").build())));
        // create_favorite_logic.go:62-70  target_type 规范化后为空 ⇒ 5303
        cs.add(new Case("c08_create_item_target_type_blank", Shape.NONE,
                s -> s.createFavorite(CreateFavoriteReq.newBuilder().setUserId(1001).setFolderId(9001)
                        .setTargetId("t-9").setTargetType("   ").build())));
        // create_favorite_logic.go:72-77  ensureUserExists ⇒ user-rpc 不在 ⇒ Internal / 5001
        cs.add(new Case("c09_create_item_user_rpc_absent", Shape.NONE,
                s -> s.createFavorite(CreateFavoriteReq.newBuilder().setUserId(1001).setFolderId(9001)
                        .setTargetId("t-9").setTargetType("post").build())));

        // ─────────── 3. GetFavoriteFolders（Go: get_favorite_folders_logic.go:34-69）───────────
        // get_favorite_folders_logic.go:45-51  userId <= 0 ⇒ InvalidArgument / 5003
        cs.add(new Case("c10_list_folders_uid0", Shape.NONE,
                s -> s.getFavoriteFolders(GetFavoriteFoldersReq.newBuilder().setUserId(0).build())));
        // get_favorite_folders_logic.go:53-60  正常读 ⇒ 响应字节应逐字节一致（seed 的时间列写死 ⇒ UnixMilli 相同）
        cs.add(new Case("c11_list_folders_ok", Shape.HEX,
                s -> s.getFavoriteFolders(GetFavoriteFoldersReq.newBuilder().setUserId(1001).build())));
        cs.add(new Case("c12_list_folders_empty", Shape.HEX,
                s -> s.getFavoriteFolders(GetFavoriteFoldersReq.newBuilder().setUserId(4242).build())));

        // ─────────── 4. GetFavoritesByFolder（Go: get_favorites_by_folder_logic.go:36-97）───────────
        // get_favorites_by_folder_logic.go:50-56  userId <= 0 || folderId <= 0 ⇒ InvalidArgument / 5003
        cs.add(new Case("c13_list_items_uid0", Shape.NONE,
                s -> s.getFavoritesByFolder(GetFavoritesByFolderReq.newBuilder().setUserId(0).setFolderId(9001).build())));
        cs.add(new Case("c14_list_items_folder0", Shape.NONE,
                s -> s.getFavoritesByFolder(GetFavoritesByFolderReq.newBuilder().setUserId(1001).setFolderId(0).build())));
        // get_favorites_by_folder_logic.go:60-67  收藏夹不存在 ⇒ NotFound / 5201
        cs.add(new Case("c15_list_items_notfound", Shape.NONE,
                s -> s.getFavoritesByFolder(GetFavoritesByFolderReq.newBuilder().setUserId(1001).setFolderId(9999).build())));
        // get_favorites_by_folder_logic.go:73-79  归属不符 ⇒ PermissionDenied / 5005
        cs.add(new Case("c16_list_items_forbidden", Shape.NONE,
                s -> s.getFavoritesByFolder(GetFavoritesByFolderReq.newBuilder().setUserId(2002).setFolderId(9001).build())));
        // get_favorites_by_folder_logic.go:81-88  正常读 ⇒ 字节应一致
        cs.add(new Case("c17_list_items_ok", Shape.HEX,
                s -> s.getFavoritesByFolder(GetFavoritesByFolderReq.newBuilder().setUserId(1001).setFolderId(9001).build())));
        cs.add(new Case("c18_list_items_empty", Shape.HEX,
                s -> s.getFavoritesByFolder(GetFavoritesByFolderReq.newBuilder().setUserId(1001).setFolderId(9002).build())));

        // ─────────── 5. DeleteFavoriteFolder（Go: delete_favorite_folder_logic.go:36-95）───────────
        // delete_favorite_folder_logic.go:50-56  参数 ⇒ InvalidArgument / 5003
        cs.add(new Case("c19_delete_folder_uid0", Shape.NONE,
                s -> s.deleteFavoriteFolder(DeleteFavoriteFolderReq.newBuilder().setUserId(0).setFolderId(0).build())));
        // delete_favorite_folder_logic.go:60-67  不存在 ⇒ NotFound / 5201
        cs.add(new Case("c20_delete_folder_notfound", Shape.NONE,
                s -> s.deleteFavoriteFolder(DeleteFavoriteFolderReq.newBuilder().setUserId(1001).setFolderId(9999).build())));
        // delete_favorite_folder_logic.go:73-79  归属不符 ⇒ PermissionDenied / 5005
        cs.add(new Case("c21_delete_folder_forbidden", Shape.NONE,
                s -> s.deleteFavoriteFolder(DeleteFavoriteFolderReq.newBuilder().setUserId(2002).setFolderId(9001).build())));

        // ─────────── 6. DeleteFavorite（Go: delete_favorite_logic.go:36-95）───────────
        // delete_favorite_logic.go:50-56  参数 ⇒ InvalidArgument / 5003
        cs.add(new Case("c22_delete_item_uid0", Shape.NONE,
                s -> s.deleteFavorite(DeleteFavoriteReq.newBuilder().setUserId(0).setFavoriteId(0).build())));
        // delete_favorite_logic.go:60-67  不存在 ⇒ NotFound / 5301
        cs.add(new Case("c23_delete_item_notfound", Shape.NONE,
                s -> s.deleteFavorite(DeleteFavoriteReq.newBuilder().setUserId(1001).setFavoriteId(9999).build())));
        // delete_favorite_logic.go:73-79  归属不符 ⇒ PermissionDenied / 5005
        cs.add(new Case("c24_delete_item_forbidden", Shape.NONE,
                s -> s.deleteFavorite(DeleteFavoriteReq.newBuilder().setUserId(2002).setFavoriteId(7001).build())));

        // ─────────── 7. UpdateFavoriteFolderName（Go: update_favorite_folder_name_logic.go:36-126）───────────
        // update_favorite_folder_name_logic.go:51-57  参数 ⇒ InvalidArgument / 5003
        cs.add(new Case("c25_update_folder_uid0", Shape.NONE,
                s -> s.updateFavoriteFolderName(UpdateFavoriteFolderNameReq.newBuilder()
                        .setUserId(0).setFolderId(0).setName("Z").build())));
        // update_favorite_folder_name_logic.go:59-66  name 空 ⇒ InvalidArgument / 5202
        cs.add(new Case("c26_update_folder_name_empty", Shape.NONE,
                s -> s.updateFavoriteFolderName(UpdateFavoriteFolderNameReq.newBuilder()
                        .setUserId(1001).setFolderId(9001).setName("").build())));
        // update_favorite_folder_name_logic.go:68-75  不存在 ⇒ NotFound / 5201
        cs.add(new Case("c27_update_folder_notfound", Shape.NONE,
                s -> s.updateFavoriteFolderName(UpdateFavoriteFolderNameReq.newBuilder()
                        .setUserId(1001).setFolderId(9999).setName("Z").build())));
        // update_favorite_folder_name_logic.go:83-89  归属不符 ⇒ PermissionDenied / 5005
        cs.add(new Case("c28_update_folder_forbidden", Shape.NONE,
                s -> s.updateFavoriteFolderName(UpdateFavoriteFolderNameReq.newBuilder()
                        .setUserId(2002).setFolderId(9001).setName("Z").build())));
        // update_favorite_folder_name_logic.go:91-95  同名 ⇒ 直接成功、**不写库**（updated_at 不推进）
        cs.add(new Case("c29_update_folder_same_name", Shape.HEX,
                s -> s.updateFavoriteFolderName(UpdateFavoriteFolderNameReq.newBuilder()
                        .setUserId(1001).setFolderId(9001).setName("Alpha").build())));

        // ═══════════════ 写入用例（全部在只读用例之后）═══════════════
        // 🔴🔴 **c30_response_header_surface 必须排在写入用例之前**（见下方说明）：
        //    它读的是 user 1001 的收藏夹列表，而 c33_update_folder_ok 会**改名** folder 9001
        //    ⇒ updated_at 被推成「本次运行的时刻」⇒ 观测值每次都不同 ⇒ 冻结值失效 ⇒ 假红。
        //    ⇒ 「读同一行的用例」必须排在「写同一行的用例」之前。
        //    这是本探针第一版真实踩到的坑（2026-10-25 第十三轮）。
        cs.add(new Case("c30_response_header_surface", Shape.HEX,
                s -> s.getFavoriteFolders(GetFavoriteFoldersReq.newBuilder().setUserId(1001).build()), true));

        // update_favorite_folder_name_logic.go:81-95  正常删 ⇒ success=true
        cs.add(new Case("c31_delete_folder_ok", Shape.HEX,
                s -> s.deleteFavoriteFolder(DeleteFavoriteFolderReq.newBuilder().setUserId(1001).setFolderId(9002).build())));
        // update_favorite_folder_name_logic.go:81-95  正常删 ⇒ success=true
        cs.add(new Case("c32_delete_item_ok", Shape.HEX,
                s -> s.deleteFavorite(DeleteFavoriteReq.newBuilder().setUserId(1001).setFavoriteId(7001).build())));
        // update_favorite_folder_name_logic.go:111-126  改名字 ⇒ success=true
        cs.add(new Case("c33_update_folder_ok", Shape.HEX,
                s -> s.updateFavoriteFolderName(UpdateFavoriteFolderNameReq.newBuilder()
                        .setUserId(1001).setFolderId(9001).setName("Alpha2").build())));

        // ═══════════════ 服务面（框架层，非本域行为）═══════════════
        // go-zero 的 RpcServerConf.Health 默认 true ⇒ 真源注册 grpc.health.v1.Health
        cs.add(new Case("c34_health_check", Shape.NONE, null));
        // 未注册的**方法**（服务名用真源的 favorite.FavoriteService）⇒ 两侧都应是 UNIMPLEMENTED
        cs.add(new Case("c35_unknown_method", Shape.NONE, null));
        // 未注册的**服务** ⇒ 同样 UNIMPLEMENTED（与上一条区分开：库级措辞差异有两种形态）
        cs.add(new Case("c36_unknown_service", Shape.NONE, null));

        return cs;
    }

    // ══════════════════════════ 执行 ══════════════════════════

    @BeforeAll
    static void openChannels() throws Exception {
        goChannel = ManagedChannelBuilder.forTarget(env("PROBE_GO_TARGET")).usePlaintext().build();
        ragChannel = ManagedChannelBuilder.forTarget(env("PROBE_RAG_TARGET")).usePlaintext().build();
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
    @DisplayName("gRPC 级可观测契约：Go 真源 server vs RAG server（同客户端双打）")
    void grpcContractParity() throws Exception {
        // ── 灌种子（RAG 侧：Flyway 已建好表，这一步与 Go 侧 run.sh 的 psql 灌种子等价）──
        applySeed(Paths.get(env("PROBE_SEED")));

        List<Case> cases = matrix();
        Map<String, String> goValues = new LinkedHashMap<>();
        Map<String, String> ragValues = new LinkedHashMap<>();

        for (Case c : cases) {
            goValues.put(c.id(), observe(c, goChannel));
            ragValues.put(c.id(), observe(c, ragChannel));
        }

        writeResults(Paths.get(env("PROBE_OUT_GO")), goValues);
        writeResults(Paths.get(env("PROBE_OUT_RAG")), ragValues);

        // ── 与冻结的 expected.txt 比对（跑 mvn test 单独也能当回归门禁）──
        Path expectedPath = Paths.get(env("PROBE_EXPECTED"));
        if (!Files.exists(expectedPath)) {
            // 🔴 首次运行（expected.txt 还没冻结）：只测量、不判定。
            //    由 run.sh 打印 Go / RAG 两侧的逐条对照，人工分类成 SAME / OBS / SKIP 后冻结。
            System.out.println("[grpc-contract-probe] expected.txt 尚不存在 ⇒ 本次只测量不判定");
            return;
        }
        Map<String, String[]> expected = readExpected(expectedPath);
        assertEquals(expected.size(), cases.size(),
                "expected.txt 的 case 数与矩阵不一致（探针改了矩阵就同步更新 expected.txt）");

        int same = 0;
        int sameCode = 0;
        int obs = 0;
        int skip = 0;
        for (Case c : cases) {
            String[] row = expected.get(c.id());
            assertNotNull(row, "expected.txt 缺 case：" + c.id());
            String kind = row[0];
            String frozen = row[1];
            String go = goValues.get(c.id());
            String rag = ragValues.get(c.id());

            // 哨兵：Go 真源变了 ⇒ 这里立刻响
            assertEquals(frozen, go, "Go 真源的 gRPC 契约变了：" + c.id()
                    + "\n  冻结值：" + frozen + "\n  实测值：" + go);

            switch (kind) {
                case "SAME" -> {
                    same++;
                    assertEquals(go, rag, "两侧 gRPC 契约不一致：" + c.id()
                            + "\n  Go : " + go + "\n  RAG: " + rag);
                }
                case "SAME_CODE" -> {
                    sameCode++;
                    // 只断言 **status code 相同**（语义等价）；
                    // description 允许不同 —— 那是 gRPC 库的措辞，不是 favorite 域的行为。
                    String goCode = field(go, "code");
                    String ragCode = field(rag, "code");
                    assertEquals(goCode, ragCode, "两侧 status code 不一致：" + c.id()
                            + "\n  Go : " + go + "\n  RAG: " + rag);
                }
                case "OBS" -> {
                    obs++;
                    assertNotEquals(go, rag, "OBS 段两侧变成一样了（请重评台账）：" + c.id()
                            + "\n  Go : " + go + "\n  RAG: " + rag);
                }
                case "SKIP" -> skip++;
                default -> throw new IllegalStateException("expected.txt 出现未知的 kind：" + kind + "（" + c.id() + "）");
            }
        }
        System.out.printf("[grpc-contract-probe] SAME %d 条 / SAME_CODE %d 条 / OBS %d 条 / SKIP %d 条（共 %d 个 case）%n",
                same, sameCode, obs, skip, cases.size());
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

    /** grpc-java 的 assertNotEquals 没有重载（String, String, String）⇒ 自己写。 */
    private static void assertNotEquals(String a, String b, String message) {
        if (a.equals(b)) {
            throw new AssertionError(message);
        }
    }

    // ══════════════════════════ 单次观测 ══════════════════════════

    private String observe(Case c, Channel rawChannel) throws Exception {
        CapturingInterceptor capture = new CapturingInterceptor();
        Channel channel = ClientInterceptors.intercept(rawChannel, capture);

        if (c.call() == null) {
            return observeRaw(c.id(), channel, capture);
        }

        FavoriteServiceGrpc.FavoriteServiceBlockingStub stub =
                FavoriteServiceGrpc.newBlockingStub(channel);
        try {
            Object resp = c.call().invoke(stub);
            return c.fullHeaders()
                    ? renderFullHeaders(Status.OK, resp, capture, c.shape())
                    : render(Status.OK, resp, capture, c.shape());
        } catch (StatusRuntimeException e) {
            return c.fullHeaders()
                    ? renderFullHeaders(e.getStatus(), null, capture, c.shape())
                    : render(e.getStatus(), null, capture, c.shape());
        }
    }

    /** 服务面探针（health / 未知方法）：用裸 byte[] marshaller，不依赖任何 proto。 */
    private String observeRaw(String caseId, Channel channel, CapturingInterceptor capture) throws Exception {
        String method = switch (caseId) {
            case "c34_health_check" -> "grpc.health.v1.Health/Check";
            // ⚠️ 服务名必须是真源 proto 里的 **favorite.FavoriteService**
            //    （favorite.pb.go:22 FullMethodName = "/favorite.FavoriteService/..."）
            //    ⇒ 用错名字会测到「未知服务」而不是「未知方法」，两种措辞不一样。
            case "c35_unknown_method" -> "/favorite.FavoriteService/DoesNotExist";
            case "c36_unknown_service" -> "/favorite.NoSuchService/DoesNotExist";
            default -> throw new IllegalStateException("未登记的裸方法用例：" + caseId);
        };
        MethodDescriptor<byte[], byte[]> md = MethodDescriptor.<byte[], byte[]>newBuilder()
                .setType(MethodDescriptor.MethodType.UNARY)
                .setFullMethodName(method)
                .setRequestMarshaller(BYTE_MARSHALLER)
                .setResponseMarshaller(BYTE_MARSHALLER)
                .build();
        ClientCall<byte[], byte[]> call = channel.newCall(md, CallOptions.DEFAULT);
        try {
            byte[] resp = ClientCalls.futureUnaryCall(call, new byte[0]).get(20, TimeUnit.SECONDS);
            return render(Status.OK, resp, capture, Shape.RAW);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof StatusRuntimeException sre) {
                return render(sre.getStatus(), null, capture, Shape.NONE);
            }
            throw e;
        }
    }

    private String render(Status status, Object response, CapturingInterceptor capture, Shape shape) {
        StringBuilder sb = new StringBuilder();
        sb.append("code=").append(status.getCode().name());
        String desc = status.getDescription();
        sb.append("|desc=").append((desc == null || desc.isEmpty()) ? "-" : desc);
        sb.append("|body=").append(shape.render(response));
        sb.append("|rhdr=").append(keys(capture.responseHeaders, false));
        sb.append("|rtrl=").append(keys(capture.trailers, false));
        return sb.toString();
    }

    /** 完整（不过滤）的头 key 集合 —— 供「服务面」用例显式量化库级差异。 */
    private String renderFullHeaders(Status status, Object response, CapturingInterceptor capture, Shape shape) {
        StringBuilder sb = new StringBuilder();
        sb.append("code=").append(status.getCode().name());
        String desc = status.getDescription();
        sb.append("|desc=").append((desc == null || desc.isEmpty()) ? "-" : desc);
        sb.append("|body=").append(shape.render(response));
        sb.append("|rhdr=").append(keys(capture.responseHeaders, true));
        sb.append("|rtrl=").append(keys(capture.trailers, true));
        return sb.toString();
    }

    /**
     * metadata 的 key（+ value）集合。
     *
     * @param full true = 不过滤 grpc-*；false = 只保留<b>非</b> grpc-* 的 key
     */
    private static String keys(Metadata metadata, boolean full) {
        if (metadata == null || metadata.keys().isEmpty()) {
            return "-";
        }
        TreeSet<String> sorted = new TreeSet<>();
        for (String key : metadata.keys()) {
            if (!full && key.startsWith(GRPC_NAMESPACE_PREFIX)) {
                continue;
            }
            sorted.add(key + "=" + String.join("/", metadata.getAll(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER))));
        }
        if (sorted.isEmpty()) {
            return "-";
        }
        return String.join(",", sorted);
    }

    // ══════════════════════════ 辅助 ══════════════════════════

    private static final MethodDescriptor.Marshaller<byte[]> BYTE_MARSHALLER =
            new MethodDescriptor.Marshaller<>() {
                @Override
                public InputStream stream(byte[] value) {
                    return new ByteArrayInputStream(value);
                }

                @Override
                public byte[] parse(InputStream stream) {
                    try {
                        return stream.readAllBytes();
                    } catch (java.io.IOException e) {
                        throw new IllegalStateException(e);
                    }
                }
            };

    /** 抓响应头与 trailer 的 metadata key 集合。 */
    private static final class CapturingInterceptor implements ClientInterceptor {
        private Metadata responseHeaders;
        private Metadata trailers;

        @Override
        public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
                MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
            return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
                @Override
                public void start(Listener<RespT> responseListener, Metadata headers) {
                    super.start(new ForwardingClientCallListener.SimpleForwardingClientCallListener<>(responseListener) {
                        @Override
                        public void onHeaders(Metadata headers) {
                            responseHeaders = headers;
                            super.onHeaders(headers);
                        }

                        @Override
                        public void onClose(Status status, Metadata trailers) {
                            CapturingInterceptor.this.trailers = trailers;
                            super.onClose(status, trailers);
                        }
                    }, headers);
                }
            };
        }
    }

    private void applySeed(Path seed) throws Exception {
        String sql = Files.readString(seed, StandardCharsets.UTF_8);
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
                jdbcTemplate.execute(s);
            }
        }
    }

    private static void writeResults(Path path, Map<String, String> values) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : values.entrySet()) {
            sb.append(e.getKey()).append('\t').append(e.getValue()).append('\n');
        }
        Files.writeString(path, sb.toString(), StandardCharsets.UTF_8);
    }

    /** 读 expected.txt ⇒ case_id → [kind, value]。 */
    private static Map<String, String[]> readExpected(Path path) throws Exception {
        Map<String, String[]> out = new LinkedHashMap<>();
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            String l = line.strip();
            if (l.isEmpty() || l.startsWith("#")) {
                continue;
            }
            String[] cols = l.split("\t", 3);
            if (cols.length < 3) {
                throw new IllegalStateException("expected.txt 行格式错误（应为 case_id\\tkind\\tvalue）：" + line);
            }
            out.put(cols[0], new String[] {cols[1], cols[2]});
        }
        return out;
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    @SuppressWarnings("unused")
    private static List<String> unusedGuard() {
        return Arrays.asList();
    }
}
