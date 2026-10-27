package com.rag.favorite.favorite.rpc;

import com.rag.article.article.proto.GetArticleResponse;
import com.rag.common.spring.SnowflakeBean;
import com.rag.favorite.favorite.proto.CreateFavoriteFolderReq;
import com.rag.favorite.favorite.proto.CreateFavoriteReq;
import com.rag.favorite.favorite.proto.DeleteFavoriteFolderReq;
import com.rag.favorite.favorite.proto.DeleteFavoriteReq;
import com.rag.favorite.favorite.proto.GetFavoriteFoldersReq;
import com.rag.favorite.favorite.proto.GetFavoritesByFolderReq;
import com.rag.favorite.favorite.proto.UpdateFavoriteFolderNameReq;
import com.rag.favorite.favorite.rpc.client.ArticleRpcClient;
import com.rag.favorite.favorite.rpc.client.UserRpcClient;
import com.rag.favorite.favorite.rpc.exception.FavoriteExceptions;
import com.rag.favorite.favorite.rpc.mapper.FavoriteFolderMapper;
import com.rag.favorite.favorite.rpc.mapper.FavoriteItemMapper;
import com.rag.favorite.favorite.rpc.metrics.FavoriteRpcMetrics;
import com.rag.favorite.favorite.rpc.service.FavoriteArticleSnapshotResolver;
import com.rag.favorite.favorite.rpc.service.FavoriteDomainService;
import com.rag.favorite.favorite.rpc.service.FavoriteFactWriteService;
import com.rag.favorite.favorite.rpc.service.FavoriteUserGuard;
import com.rag.users.user.proto.GetUserReq;
import com.rag.users.user.proto.GetUserResp;
import com.rag.users.user.proto.UserServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

/**
 * 🔴 DRIFT-FAV-RPC-L4 守门 —— favorite-rpc 侧 <b>9 个手写业务 span</b>
 * （7 个 {@code FavoriteRPC.*} ＋ 2 个 {@code Dependency.*}）必须真的被导出，
 * 且带齐 Go 侧的 span 名 / tracer 名 / 属性名 / 属性值 / <b>记错判据</b>。
 *
 * <h2>为什么不能只写「结构断言」</h2>
 * <p>AGENTS.md §3.3：<b>不得凭注解 / 方法名称断定机制已启用</b>。
 * 断言「{@code FavoriteSpans.tracedRpc 被调用了」只能证明<b>代码写成了这样</b>，
 * 证明不了<b>运行时真的产生了 span</b>。
 * ⇒ 本类走<b>装配层</b>：自己装一个 SDK ＋ 收集型 {@link SpanExporter}，
 * 让 {@code GlobalOpenTelemetry} 指向它，然后断言<b>导出的 span</b>。
 *
 * <h2>🔴 本类<b>不依赖</b> Docker / Testcontainers / 真 PG / 真 Redis</h2>
 * <p>favorite-rpc 的 {@code application.yml} 里 {@code spring.flyway.enabled=true}
 * ⇒ <b>真 Spring 上下文必须连上真 PG</b>（否则 Flyway 直接失败）⇒ 本类<b>不起 Spring 上下文</b>，
 * 改为<b>直接装配生产对象</b>：真 {@link FavoriteDomainService} ＋ 真 {@link FavoriteUserGuard}
 * ＋ 真 {@link FavoriteArticleSnapshotResolver} ＋ 真 {@link FavoriteRpcMetrics}
 * ＋ 真 {@link com.rag.favorite.favorite.common.span.FavoriteSpans} ＋ 真 OTel SDK。
 * <br>被 mock 的只有<b>两条外部边界</b>：数据层 mapper（{@code interface} ＋ mock）
 * 与下游 gRPC 客户端（<b>真 in-process server</b>，不是 mock ⇒ 走的是真传输层）。
 * <br>⚠️ <b>证据边界</b>：本类<b>不</b>证明 SQL / DDL / 事务行为（那些归
 * {@code FavoriteDdlParityTest} / {@code FavoriteSqlStatementParityTest} 等）；
 * 本类只证明<b>「这 9 个 span 真的被产生并导出」+「属性与判据与 Go 一致」</b>。
 *
 * <h2>Go 出处（决定「有几个 span」）</h2>
 * <p>Go 的 favorite-rpc 手写了 <b>9 个</b> span，全部 {@code otel.Tracer("favorite-rpc")}：
 * 7 个 logic 各 1 个（{@code create_favorite_folder_logic.go:43} ·
 * {@code delete_favorite_folder_logic.go:42} · {@code create_favorite_logic.go:44} ·
 * {@code update_favorite_folder_name_logic.go:42} · {@code get_favorites_by_folder_logic.go:42} ·
 * {@code get_favorite_folders_logic.go:40} · {@code delete_favorite_logic.go:42}）
 * ＋ {@code helper.go:93}（{@code Dependency.User.GetUser}）
 * ＋ {@code helper.go:127}（{@code Dependency.Article.GetArticle}）。
 *
 * <h2>🔴 三类「Go 记不记这个错误」的判据（本域<b>不是</b>「有异常就记」）</h2>
 * <ol>
 *   <li><b>rpc 侧 7 个 {@code FavoriteRPC.*}：任何出错都记</b>
 *       （含 {@code InvalidArgument} / 用户不存在 / 名称重复这类<b>纯业务失败</b>）
 *       ⇒ <b>不按业务码过滤</b>。</li>
 *   <li><b>依赖 span：只有下游 RPC 调用失败才记</b>
 *       （{@code helper.go:100} / {@code helper.go:139}）；
 *       <b>业务性失败不记</b> —— {@code helper.go:109-111}（resp 为空 / 未找到）、
 *       {@code helper.go:113-115}（uid 不符 / status 缺失）、
 *       {@code helper.go:117-119}（status ≠ 0 ⇒ 禁止）三处<b>都没有 RecordError</b>。</li>
 *   <li><b>属性在 Start 之后立刻设</b>（Go 的 {@code SetAttributes} <b>先于任何入参校验</b>）
 *       ⇒ <b>非法入参的 span 也带完整属性</b>。</li>
 * </ol>
 *
 * <h2>🔴 span 状态恒为 {@code UNSET}</h2>
 * <p>Go 侧 favorite 域 {@code span.SetStatus(...)} <b>零调用</b>；
 * 且实测 {@code opentelemetry-sdk-trace-1.55.0} 的 {@code SdkSpan#recordException} 字节码
 * <b>全程无 {@code setStatus} 调用</b> ⇒ Java 的 {@code recordException} 与 Go 的
 * {@code RecordError} 语义一致（只加 exception event）⇒ <b>状态必须是 UNSET</b>。
 */
class FavoriteRpcSpanWiringTest {

    // ══════════════════════════ Go 侧的**字面量**期望值（**独立于被测代码**） ══════════════════════════
    //
    // 🔴🔴 **期望值一律写 Go 真源的字面量**，**绝不引用 `FavoriteSpans` 的常量** ——
    //
    // ⚠️ **为什么**（2026-10-27 实测踩到的一颗哑守门）：
    //    第一版守门把期望值写成 `FavoriteSpans.API_CREATE_FAVORITE` 这样的**生产代码常量** ⇒
    //    变异 MUT-E 把那个常量的**值**从 "FavoriteAPI.CreateFavorite" 改成 "...V2" ⇒
    //    **业务代码跟着用新名字** ⇒ **导出的 span 名与断言期望值一起变**
    //    ⇒ ⇒ **守门 0 红** ⇒ ⇒ **守门是哑的**（看着全绿，实际什么都没守住）。
    //    ⇒ ⇒ 判据式：**断言的期望值不能与被测代码同源** ⇒ 必须用 Go 侧字面量。
    //    ⇒ ⇒ 这样「常量被改坏」「业务代码被改坏」「属性 key 被改坏」三类变异都会**精确转红**。
    //
    // Go 出处（`Sea-RideTheWind_golang` · `service/favorite/`，冻结只读）：
    //   rpc/internal/logic/create_favorite_folder_logic.go:43      ⇒ FavoriteRPC.CreateFavoriteFolder
    //   rpc/internal/logic/delete_favorite_folder_logic.go:42      ⇒ FavoriteRPC.DeleteFavoriteFolder
    //   rpc/internal/logic/create_favorite_logic.go:44             ⇒ FavoriteRPC.CreateFavorite
    //   rpc/internal/logic/update_favorite_folder_name_logic.go:42 ⇒ FavoriteRPC.UpdateFavoriteFolderName
    //   rpc/internal/logic/get_favorites_by_folder_logic.go:42     ⇒ FavoriteRPC.GetFavoritesByFolder
    //   rpc/internal/logic/get_favorite_folders_logic.go:40        ⇒ FavoriteRPC.GetFavoriteFolders
    //   rpc/internal/logic/delete_favorite_logic.go:42             ⇒ FavoriteRPC.DeleteFavorite
    //   rpc/internal/logic/helper.go:93                            ⇒ Dependency.User.GetUser
    //   rpc/internal/logic/helper.go:127                           ⇒ Dependency.Article.GetArticle
    private static final String GO_TRACER_RPC = "favorite-rpc";

    private static final String GO_RPC_CREATE_FAVORITE_FOLDER = "FavoriteRPC.CreateFavoriteFolder";
    private static final String GO_RPC_DELETE_FAVORITE_FOLDER = "FavoriteRPC.DeleteFavoriteFolder";
    private static final String GO_RPC_CREATE_FAVORITE = "FavoriteRPC.CreateFavorite";
    private static final String GO_RPC_UPDATE_FAVORITE_FOLDER_NAME = "FavoriteRPC.UpdateFavoriteFolderName";
    private static final String GO_RPC_GET_FAVORITE_FOLDERS = "FavoriteRPC.GetFavoriteFolders";
    private static final String GO_RPC_GET_FAVORITES_BY_FOLDER = "FavoriteRPC.GetFavoritesByFolder";
    private static final String GO_RPC_DELETE_FAVORITE = "FavoriteRPC.DeleteFavorite";
    private static final String GO_DEPENDENCY_USER_GET_USER = "Dependency.User.GetUser";
    private static final String GO_DEPENDENCY_ARTICLE_GET_ARTICLE = "Dependency.Article.GetArticle";

    // 属性 key：Go 侧是**硬编码字面量**（rpc/internal/logic/*.go:43~51 + helper.go:96/131），无集中常量表
    //   biz.user_id · biz.folder_id · biz.favorite_id · biz.folder_name · biz.target_id · biz.target_type · biz.article_id
    private static final String GO_ATTR_USER_ID = "biz.user_id";
    private static final String GO_ATTR_FOLDER_ID = "biz.folder_id";
    private static final String GO_ATTR_FAVORITE_ID = "biz.favorite_id";
    private static final String GO_ATTR_FOLDER_NAME = "biz.folder_name";
    private static final String GO_ATTR_TARGET_ID = "biz.target_id";
    private static final String GO_ATTR_TARGET_TYPE = "biz.target_type";
    private static final String GO_ATTR_ARTICLE_ID = "biz.article_id";

    /** 探针用的用户 ID。 */
    private static final long PROBE_USER_ID = 1001L;

    // ══════════════════════════ OTel 收集器 ══════════════════════════

    static final class CollectingExporter implements SpanExporter {
        final List<SpanData> spans = Collections.synchronizedList(new ArrayList<>());

        @Override
        public CompletableResultCode export(Collection<SpanData> batch) {
            spans.addAll(batch);
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode flush() {
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode shutdown() {
            return CompletableResultCode.ofSuccess();
        }

        void clear() {
            spans.clear();
        }
    }

    private static final CollectingExporter EXPORTER = new CollectingExporter();

    @BeforeAll
    static void installSdk() {
        GlobalOpenTelemetry.resetForTest();
        GlobalOpenTelemetry.set(OpenTelemetrySdk.builder()
                .setTracerProvider(SdkTracerProvider.builder()
                        .addSpanProcessor(SimpleSpanProcessor.create(EXPORTER))
                        .build())
                .build());
    }

    @AfterAll
    static void uninstallSdk() {
        GlobalOpenTelemetry.resetForTest();
    }

    // ══════════════════════════ 真 in-process 下游（user-rpc） ══════════════════════════

    /**
     * 下游 user-rpc 的假实现 —— 可切换「失败」与「用户不存在」两种响应，
     * 用来分别钉死依赖 span 的两条判据。
     */
    static final class FakeUserService extends UserServiceGrpc.UserServiceImplBase {

        final List<GetUserReq> reqs = Collections.synchronizedList(new ArrayList<>());

        /** {@code true} ⇒ 下游调用失败（UNAVAILABLE）；{@code false} ⇒ 正常返回「用户不存在」。 */
        volatile boolean fail;

        @Override
        public void getUser(GetUserReq request, StreamObserver<GetUserResp> obs) {
            reqs.add(request);
            if (fail) {
                obs.onError(Status.UNAVAILABLE
                        .withDescription("span-probe: user-rpc is down")
                        .asRuntimeException());
                return;
            }
            // found=false ⇒ Go: helper.go:109-111 这条**业务性**失败分支（**不记错**）
            obs.onNext(GetUserResp.newBuilder().setFound(false).build());
            obs.onCompleted();
        }
    }

    private static FakeUserService fakeUser;
    private static Server userServer;
    private static ManagedChannel userChannel;

    @BeforeAll
    static void startUserDownstream() throws IOException {
        String name = InProcessServerBuilder.generateName();
        fakeUser = new FakeUserService();
        userServer = InProcessServerBuilder.forName(name).directExecutor().addService(fakeUser).build().start();
        userChannel = InProcessChannelBuilder.forName(name).directExecutor().build();
    }

    @AfterAll
    static void stopUserDownstream() throws InterruptedException {
        if (userChannel != null) {
            userChannel.shutdownNow();
            userChannel.awaitTermination(5, TimeUnit.SECONDS);
        }
        if (userServer != null) {
            userServer.shutdownNow();
            userServer.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @BeforeEach
    void resetProbe() {
        EXPORTER.clear();
        fakeUser.reqs.clear();
        fakeUser.fail = false;
        // 🔴 必须 reset：articleRpcClient 是**静态共享**的 mock，
        //    前一个用例的 doThrow(...) 桩会**跨用例泄漏**到下一个用例
        //    （实测过一次：下游失败桩泄漏 ⇒ 「业务失败不记错」那个用例直接 error）。
        reset(articleRpcClient);
    }

    // ══════════════════════════ 被测对象（真实现 + mock 的两条外部边界） ══════════════════════════

    private static FavoriteDomainService domain;
    private static FavoriteUserGuard userGuard;
    private static FavoriteArticleSnapshotResolver articleResolver;
    private static ArticleRpcClient articleRpcClient;

    @BeforeAll
    static void wireDomain() {
        FavoriteFolderMapper folderMapper = mock(FavoriteFolderMapper.class);
        FavoriteItemMapper itemMapper = mock(FavoriteItemMapper.class);
        FavoriteFactWriteService factWriteService = mock(FavoriteFactWriteService.class);
        SnowflakeBean snowflake = mock(SnowflakeBean.class);

        UserRpcClient userRpcClient = mock(UserRpcClient.class);
        when(userRpcClient.userRpc()).thenReturn(UserServiceGrpc.newBlockingStub(userChannel));

        articleRpcClient = mock(ArticleRpcClient.class);

        FavoriteRpcMetrics metrics = new FavoriteRpcMetrics(new SimpleMeterRegistry());
        userGuard = new FavoriteUserGuard(userRpcClient, metrics);
        articleResolver = new FavoriteArticleSnapshotResolver(articleRpcClient, metrics);

        domain = new FavoriteDomainService(folderMapper, itemMapper, factWriteService,
                userGuard, articleResolver, metrics, snowflake);
    }

    // ══════════════════════════ 断言工具 ══════════════════════════

    private static Optional<SpanData> findSpan(String name) {
        synchronized (EXPORTER.spans) {
            return EXPORTER.spans.stream().filter(s -> name.equals(s.getName())).findFirst();
        }
    }

    private static List<String> exportedSpanNames() {
        synchronized (EXPORTER.spans) {
            return EXPORTER.spans.stream().map(SpanData::getName).toList();
        }
    }

    private static SpanData requireSpan(String name) {
        return findSpan(name).orElseThrow(() -> new AssertionError(
                "没有导出名为 " + name + " 的 span。实际导出的 = " + exportedSpanNames()));
    }

    private static long longAttr(SpanData span, String key) {
        return span.getAttributes().get(io.opentelemetry.api.common.AttributeKey.longKey(key));
    }

    private static String stringAttr(SpanData span, String key) {
        return span.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey(key));
    }

    // ══════════════════════════ ① 7 个 FavoriteRPC.* span ══════════════════════════

    @Test
    @DisplayName("✅ createFavoriteFolder 真的导出 FavoriteRPC.CreateFavoriteFolder（tracer=favorite-rpc）＋ biz.user_id / biz.folder_name（原样）")
    void createFolderExportsGoSpan() {
        CreateFavoriteFolderReq req = CreateFavoriteFolderReq.newBuilder()
                .setUserId(PROBE_USER_ID)
                .setName("  ")                       // ← 归一后为空 ⇒ Go: :59-66 ⇒ InvalidArgument/5202
                .build();

        assertThrows(FavoriteExceptions.Rpc.class, () -> domain.createFavoriteFolder(req),
                "名称为空 ⇒ 必须失败（探针前提）");

        SpanData span = requireSpan(GO_RPC_CREATE_FAVORITE_FOLDER);
        assertEquals(GO_TRACER_RPC, span.getInstrumentationScopeInfo().getName(),
                "Go: create_favorite_folder_logic.go:43 ⇒ otel.Tracer(\"favorite-rpc\")");
        assertEquals(PROBE_USER_ID, longAttr(span, GO_ATTR_USER_ID));
        assertEquals("  ", stringAttr(span, GO_ATTR_FOLDER_NAME),
                "Go: :46 ⇒ attribute.String(\"biz.folder_name\", in.GetName()) ⇒ **原样**");
        // 🔴 Go 侧 SetAttributes 在 Start 之后立刻设 ⇒ **非法入参的 span 也带完整属性**
        assertEquals(2, span.getAttributes().size(),
                "Go 侧这个 span 有 2 个属性。实际 = " + span.getAttributes());
        // 🔴 rpc 侧判据：任何出错都记
        assertEquals(1, span.getEvents().size(),
                "rpc 侧任何失败都记错。实际事件 = " + span.getEvents());
        assertEquals(StatusCode.UNSET, span.getStatus().getStatusCode(),
                "Go 侧 SetStatus 零调用 ⇒ 状态必须保持 UNSET");
    }

    @Test
    @DisplayName("✅ createFavorite 真的导出 FavoriteRPC.CreateFavorite ＋ 4 个属性（target_id 已 trim / target_type 原样）")
    void createFavoriteExportsGoSpan() {
        CreateFavoriteReq req = CreateFavoriteReq.newBuilder()
                .setUserId(PROBE_USER_ID)
                .setFolderId(2002L)
                .setTargetId("  t-1  ")
                .setTargetType("Article")
                .build();

        // 探针：用户不存在 ⇒ Go: helper.go:109-111 ⇒ NotFound
        assertThrows(FavoriteExceptions.Rpc.class, () -> domain.createFavorite(req),
                "用户不存在 ⇒ 必须失败（探针前提）");

        SpanData span = requireSpan(GO_RPC_CREATE_FAVORITE);
        assertEquals(GO_TRACER_RPC, span.getInstrumentationScopeInfo().getName());
        assertEquals(PROBE_USER_ID, longAttr(span, GO_ATTR_USER_ID));
        assertEquals(2002L, longAttr(span, GO_ATTR_FOLDER_ID));
        // 🔴 Go: create_favorite_logic.go:50 ⇒ strings.TrimSpace(in.GetTargetId()) ⇒ **trim 过**
        assertEquals("t-1", stringAttr(span, GO_ATTR_TARGET_ID),
                "biz.target_id 在 Go 侧是 TrimSpace 过的");
        // 🔴 Go: create_favorite_logic.go:51 ⇒ attribute.String(\"biz.target_type\", in.GetTargetType()) ⇒ **原样**
        assertEquals("Article", stringAttr(span, GO_ATTR_TARGET_TYPE),
                "biz.target_type 在 Go 侧是原样 ⇒ 不得 lower / trim");
        assertEquals(4, span.getAttributes().size(),
                "Go 侧这个 span 有 4 个属性。实际 = " + span.getAttributes());
    }

    @Test
    @DisplayName("✅ deleteFavoriteFolder 真的导出 FavoriteRPC.DeleteFavoriteFolder ＋ biz.user_id / biz.folder_id")
    void deleteFolderExportsGoSpan() {
        DeleteFavoriteFolderReq req = DeleteFavoriteFolderReq.newBuilder()
                .setUserId(PROBE_USER_ID)
                .setFolderId(2002L)
                .build();

        // 收藏夹不存在 ⇒ Go: delete_favorite_folder_logic.go:81 ⇒ NotFound
        assertThrows(FavoriteExceptions.Rpc.class, () -> domain.deleteFavoriteFolder(req),
                "收藏夹不存在 ⇒ 必须失败（探针前提）");

        SpanData span = requireSpan(GO_RPC_DELETE_FAVORITE_FOLDER);
        assertEquals(GO_TRACER_RPC, span.getInstrumentationScopeInfo().getName());
        assertEquals(PROBE_USER_ID, longAttr(span, GO_ATTR_USER_ID));
        assertEquals(2002L, longAttr(span, GO_ATTR_FOLDER_ID));
        assertEquals(2, span.getAttributes().size(), "实际 = " + span.getAttributes());
    }

    @Test
    @DisplayName("✅ deleteFavorite 真的导出 FavoriteRPC.DeleteFavorite ＋ biz.user_id / biz.favorite_id")
    void deleteFavoriteExportsGoSpan() {
        DeleteFavoriteReq req = DeleteFavoriteReq.newBuilder()
                .setUserId(PROBE_USER_ID)
                .setFavoriteId(3003L)
                .build();

        assertThrows(FavoriteExceptions.Rpc.class, () -> domain.deleteFavorite(req),
                "收藏不存在 ⇒ 必须失败（探针前提）");

        SpanData span = requireSpan(GO_RPC_DELETE_FAVORITE);
        assertEquals(GO_TRACER_RPC, span.getInstrumentationScopeInfo().getName());
        assertEquals(PROBE_USER_ID, longAttr(span, GO_ATTR_USER_ID));
        assertEquals(3003L, longAttr(span, GO_ATTR_FAVORITE_ID));
        assertEquals(2, span.getAttributes().size(), "实际 = " + span.getAttributes());
    }

    @Test
    @DisplayName("✅ updateFavoriteFolderName 真的导出 FavoriteRPC.UpdateFavoriteFolderName ＋ biz.folder_name 原样")
    void updateNameExportsGoSpan() {
        UpdateFavoriteFolderNameReq req = UpdateFavoriteFolderNameReq.newBuilder()
                .setUserId(PROBE_USER_ID)
                .setFolderId(2002L)
                .setName("  ")
                .build();

        assertThrows(FavoriteExceptions.Rpc.class, () -> domain.updateFavoriteFolderName(req),
                "名称为空 ⇒ 必须失败（探针前提）");

        SpanData span = requireSpan(GO_RPC_UPDATE_FAVORITE_FOLDER_NAME);
        assertEquals(GO_TRACER_RPC, span.getInstrumentationScopeInfo().getName());
        assertEquals(PROBE_USER_ID, longAttr(span, GO_ATTR_USER_ID));
        assertEquals(2002L, longAttr(span, GO_ATTR_FOLDER_ID));
        assertEquals("  ", stringAttr(span, GO_ATTR_FOLDER_NAME),
                "Go: update_favorite_folder_name_logic.go:46 ⇒ **原样**");
        assertEquals(3, span.getAttributes().size(), "实际 = " + span.getAttributes());
    }

    @Test
    @DisplayName("✅ getFavoriteFolders 真的导出 FavoriteRPC.GetFavoriteFolders（Go 侧只有 biz.user_id 一个属性）＋ 成功时**不记错**")
    void getFoldersExportsGoSpan() {
        GetFavoriteFoldersReq req = GetFavoriteFoldersReq.newBuilder()
                .setUserId(PROBE_USER_ID)
                .build();

        // 空集合是**成功**（Go: :62-65 ⇒ 空数组不是 null）
        assertNotNull(domain.getFavoriteFolders(req), "空收藏夹列表是成功路径（探针前提）");

        SpanData span = requireSpan(GO_RPC_GET_FAVORITE_FOLDERS);
        assertEquals(GO_TRACER_RPC, span.getInstrumentationScopeInfo().getName());
        assertEquals(PROBE_USER_ID, longAttr(span, GO_ATTR_USER_ID));
        // 🔴 Go: get_favorite_folders_logic.go:43 ⇒ **只有** biz.user_id
        assertEquals(1, span.getAttributes().size(),
                "Go 侧这个 span 只有一个属性 ⇒ 多一个都是漂移。实际 = " + span.getAttributes());
        assertTrue(span.getEvents().isEmpty(), "成功路径不该有异常事件。实际 = " + span.getEvents());
        assertEquals(StatusCode.UNSET, span.getStatus().getStatusCode());
    }

    @Test
    @DisplayName("✅ getFavoritesByFolder 真的导出 FavoriteRPC.GetFavoritesByFolder ＋ biz.user_id / biz.folder_id")
    void getItemsExportsGoSpan() {
        GetFavoritesByFolderReq req = GetFavoritesByFolderReq.newBuilder()
                .setUserId(PROBE_USER_ID)
                .setFolderId(2002L)
                .build();

        assertThrows(FavoriteExceptions.Rpc.class, () -> domain.getFavoritesByFolder(req),
                "收藏夹不存在 ⇒ 必须失败（探针前提）");

        SpanData span = requireSpan(GO_RPC_GET_FAVORITES_BY_FOLDER);
        assertEquals(GO_TRACER_RPC, span.getInstrumentationScopeInfo().getName());
        assertEquals(PROBE_USER_ID, longAttr(span, GO_ATTR_USER_ID));
        assertEquals(2002L, longAttr(span, GO_ATTR_FOLDER_ID));
        assertEquals(2, span.getAttributes().size(), "实际 = " + span.getAttributes());
    }

    // ══════════════════════════ ② 2 个 Dependency.* span ══════════════════════════

    @Test
    @DisplayName("🔴 依赖判据①：下游 RPC 调用失败 ⇒ Dependency.User.GetUser **记错**（Go: helper.go:100）")
    void userDependencyRecordsErrorWhenDownstreamFails() {
        fakeUser.fail = true;

        assertThrows(FavoriteExceptions.Rpc.class, () -> userGuard.ensureExists(PROBE_USER_ID),
                "下游不可达 ⇒ 必须失败（探针前提）");

        SpanData span = requireSpan(GO_DEPENDENCY_USER_GET_USER);
        assertEquals(GO_TRACER_RPC, span.getInstrumentationScopeInfo().getName(),
                "Go: helper.go:93 ⇒ otel.Tracer(\"favorite-rpc\")");
        assertEquals(PROBE_USER_ID, longAttr(span, GO_ATTR_USER_ID));
        assertEquals(1, span.getEvents().size(),
                "Go: helper.go:100 ⇒ span.RecordError(err) ⇒ 必须恰好 1 个异常事件。实际 = " + span.getEvents());
        assertEquals(StatusCode.UNSET, span.getStatus().getStatusCode(),
                "🔴 recordException 不改状态 ⇒ 必须是 UNSET");
        assertEquals(1, fakeUser.reqs.size(), "下游应当收到恰好 1 个请求");
        assertEquals(PROBE_USER_ID, fakeUser.reqs.get(0).getUid(), "Go: helper.go:98 ⇒ Uid: userID");
    }

    @Test
    @DisplayName("🔴 依赖判据②：业务性失败（用户不存在）⇒ Dependency.User.GetUser **不记错**（Go: helper.go:109-111 无 RecordError）")
    void userDependencyDoesNotRecordErrorOnBusinessFailure() {
        fakeUser.fail = false;   // 下游正常返回 found=false

        assertThrows(FavoriteExceptions.Rpc.class, () -> userGuard.ensureExists(PROBE_USER_ID),
                "用户不存在 ⇒ 必须失败（探针前提）");

        SpanData span = requireSpan(GO_DEPENDENCY_USER_GET_USER);
        assertEquals(PROBE_USER_ID, longAttr(span, GO_ATTR_USER_ID));
        assertTrue(span.getEvents().isEmpty(),
                "Go: helper.go:109-111 这条业务分支**没有** RecordError ⇒ 不该有异常事件。实际 = " + span.getEvents());
        assertEquals(StatusCode.UNSET, span.getStatus().getStatusCode());
    }

    @Test
    @DisplayName("🔴 依赖判据①：下游 RPC 调用失败 ⇒ Dependency.Article.GetArticle **记错**（Go: helper.go:139）")
    void articleDependencyRecordsErrorWhenDownstreamFails() {
        doThrow(Status.UNAVAILABLE.withDescription("span-probe: article-rpc is down")
                        .asRuntimeException())
                .when(articleRpcClient).getPublicArticle(anyString());

        assertThrows(FavoriteExceptions.Rpc.class, () -> articleResolver.resolve("t-1"),
                "下游不可达 ⇒ 必须失败（探针前提）");

        SpanData span = requireSpan(GO_DEPENDENCY_ARTICLE_GET_ARTICLE);
        assertEquals(GO_TRACER_RPC, span.getInstrumentationScopeInfo().getName(),
                "Go: helper.go:127 ⇒ otel.Tracer(\"favorite-rpc\")");
        assertEquals("t-1", stringAttr(span, GO_ATTR_ARTICLE_ID),
                "Go: helper.go:130-131 ⇒ biz.article_id 是 **TrimSpace 过**的");
        assertEquals(1, span.getEvents().size(),
                "Go: helper.go:139 ⇒ span.RecordError(err)。实际 = " + span.getEvents());
        assertEquals(StatusCode.UNSET, span.getStatus().getStatusCode());
    }

    @Test
    @DisplayName("🔴 依赖判据②：业务性失败（文章不存在）⇒ Dependency.Article.GetArticle **不记错**（Go: helper.go:148-151 无 RecordError）")
    void articleDependencyDoesNotRecordErrorOnBusinessFailure() {
        // 下游正常返回「没有 article」⇒ 走到 Go: helper.go:148-151 那条业务分支
        when(articleRpcClient.getPublicArticle(anyString()))
                .thenReturn(GetArticleResponse.getDefaultInstance());

        assertThrows(FavoriteExceptions.Rpc.class, () -> articleResolver.resolve("  t-1  "),
                "文章不存在 ⇒ 必须失败（探针前提）");

        SpanData span = requireSpan(GO_DEPENDENCY_ARTICLE_GET_ARTICLE);
        assertEquals("t-1", stringAttr(span, GO_ATTR_ARTICLE_ID),
                "Go: helper.go:130 ⇒ normalizedTargetID 是 TrimSpace 过的");
        assertTrue(span.getEvents().isEmpty(),
                "Go: helper.go:148-151 这条业务分支**没有** RecordError ⇒ 不该有异常事件。实际 = " + span.getEvents());
        assertEquals(StatusCode.UNSET, span.getStatus().getStatusCode());
    }

    // ══════════════════════════ ③ 名字集合 ══════════════════════════

    @Test
    @DisplayName("✅ 9 个 rpc 侧 span 的名字集合与 Go 侧逐条一致（不多不少）")
    void rpcSpanNameSetMatchesGo() {
        createFolderExportsGoSpan();
        createFavoriteExportsGoSpan();
        deleteFolderExportsGoSpan();
        deleteFavoriteExportsGoSpan();
        updateNameExportsGoSpan();
        getFoldersExportsGoSpan();
        getItemsExportsGoSpan();
        userDependencyDoesNotRecordErrorOnBusinessFailure();
        articleDependencyDoesNotRecordErrorOnBusinessFailure();

        List<String> expected = List.of(
                GO_RPC_CREATE_FAVORITE_FOLDER,
                GO_RPC_CREATE_FAVORITE,
                GO_RPC_DELETE_FAVORITE_FOLDER,
                GO_RPC_DELETE_FAVORITE,
                GO_RPC_UPDATE_FAVORITE_FOLDER_NAME,
                GO_RPC_GET_FAVORITE_FOLDERS,
                GO_RPC_GET_FAVORITES_BY_FOLDER,
                GO_DEPENDENCY_USER_GET_USER,
                GO_DEPENDENCY_ARTICLE_GET_ARTICLE);
        List<String> actual = exportedSpanNames();
        // 🔴 **总数是 10 而不是 9** —— 因为 FavoriteRPC.CreateFavorite 的**内部**
        //    会调 ensureUserExists ⇒ 再产生**一个**嵌套的 Dependency.User.GetUser。
        //    Go 侧同构（create_favorite_logic.go 先调 helper.go:93 的 ensureUserExists
        //    ⇒ 那个依赖 span 是业务 span 的**子 span**）⇒ 10 才是对的，9 反而是漂移。
        assertEquals(10, actual.size(),
                "9 个用例各产生 1 个 span ＋ CreateFavorite 内部嵌套的 1 个 Dependency.User.GetUser。实际 = " + actual);
        List<String> distinct = actual.stream().distinct().toList();
        assertEquals(9, distinct.size(),
                "去重后必须恰好是 Go 侧的 9 个 span 名。实际 = " + distinct);
        for (String name : expected) {
            assertNotNull(findSpan(name).orElse(null), "缺少 span: " + name);
        }
    }
}
