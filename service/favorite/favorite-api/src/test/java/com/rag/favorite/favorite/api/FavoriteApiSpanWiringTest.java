package com.rag.favorite.favorite.api;

import com.rag.common.exception.BusinessException;
import com.rag.common.spring.LoggerFactoryBean;
import com.rag.favorite.favorite.api.client.FavoriteRpcClient;
import com.rag.favorite.favorite.api.dto.CreateFavoriteFolderReq;
import com.rag.favorite.favorite.api.dto.CreateFavoriteReq;
import com.rag.favorite.favorite.api.dto.DeleteFavoriteFolderReq;
import com.rag.favorite.favorite.api.dto.DeleteFavoriteReq;
import com.rag.favorite.favorite.api.dto.GetFavoritesByFolderReq;
import com.rag.favorite.favorite.api.dto.UpdateFavoriteFolderNameReq;
import com.rag.favorite.favorite.api.interceptor.FavoriteJwtAuthInterceptor;
import com.rag.favorite.favorite.api.service.FavoriteApiSupport;
import com.rag.favorite.favorite.api.service.FavoriteCreateFolderService;
import com.rag.favorite.favorite.api.service.FavoriteCreateItemService;
import com.rag.favorite.favorite.api.service.FavoriteDeleteFolderService;
import com.rag.favorite.favorite.api.service.FavoriteDeleteItemService;
import com.rag.favorite.favorite.api.service.FavoriteListFoldersService;
import com.rag.favorite.favorite.api.service.FavoriteListItemsService;
import com.rag.favorite.favorite.api.service.FavoriteUpdateFolderNameService;
import com.rag.favorite.favorite.proto.FavoriteServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
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
import org.springframework.mock.web.MockHttpServletRequest;

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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 🔴 DRIFT-FAV-API-L4 守门 —— favorite-api 侧 <b>7 个手写业务 span 必须真的被导出</b>，
 * 且带齐 Go 侧的 span 名 / tracer 名 / 属性名 / 属性值 / <b>记错判据</b>。
 *
 * <h2>为什么不能只写「结构断言」</h2>
 * <p>AGENTS.md §3.3：<b>不得凭注解 / 方法名称断定机制已启用</b>。
 * 断言「{@code FavoriteSpans.tracedApi 被调用了」只能证明<b>代码写成了这样</b>，
 * 证明不了<b>运行时真的产生了 span</b>。
 * ⇒ 本类走<b>装配层</b>：自己装一个 SDK ＋ 收集型 {@link SpanExporter}，
 * 让 {@code GlobalOpenTelemetry} 指向它，然后断言<b>导出的 span</b>。
 *
 * <h2>🔴 本类<b>不依赖</b> Docker / Testcontainers / 真 PG / 真 Redis</h2>
 * <p>favorite-api <b>没有数据源</b>（它只是 HTTP → gRPC 的网关），唯一的外部依赖是
 * {@link FavoriteRpcClient} 的那一次下游调用。
 * ⇒ 本类用<b>真 in-process gRPC server</b>（{@code grpc-inprocess}，runtime scope ⇒ 在 test classpath 上）
 * 顶上那一次调用 ⇒ <b>零容器、零端口、零真 DB</b>，同时还能<b>抓下游真实收到的请求</b>
 * （用来钉死「span 属性 trim 过、下游请求没 trim」这条不对称）。
 *
 * <h2>Go 出处（决定「有几个 span」，不是「有几个方法」）</h2>
 * <p>Go 的 favorite-api <b>7 个 logic 每个手写 1 个 span</b>，全部 {@code otel.Tracer("favorite-api")}：
 * {@code create_favorite_folder_logic.go:33} · {@code delete_favorite_folder_logic.go:33} ·
 * {@code create_favorite_logic.go:34} · {@code update_favorite_folder_name_logic.go:33} ·
 * {@code get_favorites_by_folder_logic.go:33} · {@code get_favorite_folders_logic.go:33} ·
 * {@code delete_favorite_logic.go:33}。
 * ⇒ 本类<b>双向</b>钉死：7 个名字必须出现、且 <b>每个都带各自的属性集</b>
 * （否则 trace 里的 favorite span 会多出或少掉属性维度，与真源不符）。
 *
 * <h2>🔴 三条「Go 侧的判据」必须逐字保留（本类逐条断言）</h2>
 * <ol>
 *   <li><b>鉴权失败 ⇒ span 存在、<b>无任何属性</b>、<b>不记错</b></b>
 *       （Go：{@code extractUserID(ctx)} 失败 ⇒ 直接 return，
 *       位置在 {@code span.SetAttributes} <b>之前</b>，且<b>没有</b> {@code RecordError}）。</li>
 *   <li><b>鉴权成功之后的任何失败 ⇒ 记错</b>
 *       （Go：每个 span 恰好 2 处 {@code RecordError}：rpcErr ＋ err）。</li>
 *   <li><b>取值三处不对称</b>：{@code biz.target_id} <b>trim 过</b>、
 *       {@code biz.target_type} <b>原样</b>、{@code biz.folder_name} <b>原样</b>
 *       ⇒ 且<b>下游请求带的是未 trim 的原值</b>。</li>
 * </ol>
 *
 * <h2>🔴 span 状态恒为 {@code UNSET}</h2>
 * <p>Go 侧 favorite 域 {@code span.SetStatus(...)} <b>零调用</b>；
 * 且实测 {@code opentelemetry-sdk-trace-1.55.0} 的 {@code SdkSpan#recordException} 字节码
 * <b>全程无 {@code setStatus} 调用</b> ⇒ Java 的 {@code recordException} 与 Go 的
 * {@code RecordError} 语义一致（只加 exception event）⇒ <b>状态必须是 UNSET</b>。
 */
class FavoriteApiSpanWiringTest {

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
    //   api/internal/logic/favorite/create_favorite_folder_logic.go:33      ⇒ FavoriteAPI.CreateFavoriteFolder
    //   api/internal/logic/favorite/delete_favorite_folder_logic.go:33      ⇒ FavoriteAPI.DeleteFavoriteFolder
    //   api/internal/logic/favorite/create_favorite_logic.go:34             ⇒ FavoriteAPI.CreateFavorite
    //   api/internal/logic/favorite/update_favorite_folder_name_logic.go:33 ⇒ FavoriteAPI.UpdateFavoriteFolderName
    //   api/internal/logic/favorite/get_favorites_by_folder_logic.go:33     ⇒ FavoriteAPI.GetFavoritesByFolder
    //   api/internal/logic/favorite/get_favorite_folders_logic.go:33        ⇒ FavoriteAPI.GetFavoriteFolders
    //   api/internal/logic/favorite/delete_favorite_logic.go:33            ⇒ FavoriteAPI.DeleteFavorite
    private static final String GO_TRACER_API = "favorite-api";

    private static final String GO_API_CREATE_FAVORITE_FOLDER = "FavoriteAPI.CreateFavoriteFolder";
    private static final String GO_API_DELETE_FAVORITE_FOLDER = "FavoriteAPI.DeleteFavoriteFolder";
    private static final String GO_API_CREATE_FAVORITE = "FavoriteAPI.CreateFavorite";
    private static final String GO_API_UPDATE_FAVORITE_FOLDER_NAME = "FavoriteAPI.UpdateFavoriteFolderName";
    private static final String GO_API_GET_FAVORITES_BY_FOLDER = "FavoriteAPI.GetFavoritesByFolder";
    private static final String GO_API_GET_FAVORITE_FOLDERS = "FavoriteAPI.GetFavoriteFolders";
    private static final String GO_API_DELETE_FAVORITE = "FavoriteAPI.DeleteFavorite";

    // 属性 key：Go 侧是**硬编码字面量**（api/internal/logic/favorite/*.go:42~47），无集中常量表
    //   biz.user_id · biz.folder_id · biz.favorite_id · biz.folder_name · biz.target_id · biz.target_type
    private static final String GO_ATTR_USER_ID = "biz.user_id";
    private static final String GO_ATTR_FOLDER_ID = "biz.folder_id";
    private static final String GO_ATTR_FAVORITE_ID = "biz.favorite_id";
    private static final String GO_ATTR_FOLDER_NAME = "biz.folder_name";
    private static final String GO_ATTR_TARGET_ID = "biz.target_id";
    private static final String GO_ATTR_TARGET_TYPE = "biz.target_type";

    /** 探针用的用户 ID（Go 侧 ctx 里的 {@code userId} claim）。 */
    private static final long PROBE_USER_ID = 1001L;

    // ══════════════════════════ OTel 收集器 ══════════════════════════

    /**
     * 自建收集型 exporter —— <b>不引 {@code opentelemetry-sdk-testing}</b>
     * （引入它只为测试反而扩大依赖面）。
     */
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
        // 🔴 关键：把 GlobalOpenTelemetry 指向本测试的收集器。
        // FavoriteSpans 用的是 GlobalOpenTelemetry.getTracer(...)，不装 SDK 就是 no-op（永远导不出 span）。
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

    // ══════════════════════════ 真 in-process 下游（favorite-rpc） ══════════════════════════

    /**
     * 下游 favorite-rpc 的<b>假实现</b> —— 只做两件事：
     * ① <b>记录</b>它真实收到的请求（用来钉死「下游拿到的值 vs span 里的值」）；
     * ② <b>失败</b>（UNAVAILABLE）⇒ 触发 api 侧的「鉴权成功之后一律记错」那条判据。
     */
    static final class FakeFavoriteService extends FavoriteServiceGrpc.FavoriteServiceImplBase {

        final List<com.rag.favorite.favorite.proto.CreateFavoriteFolderReq> createFolderReqs =
                Collections.synchronizedList(new ArrayList<>());
        final List<com.rag.favorite.favorite.proto.DeleteFavoriteFolderReq> deleteFolderReqs =
                Collections.synchronizedList(new ArrayList<>());
        final List<com.rag.favorite.favorite.proto.CreateFavoriteReq> createItemReqs =
                Collections.synchronizedList(new ArrayList<>());
        final List<com.rag.favorite.favorite.proto.UpdateFavoriteFolderNameReq> updateNameReqs =
                Collections.synchronizedList(new ArrayList<>());
        final List<com.rag.favorite.favorite.proto.GetFavoriteFoldersReq> listFoldersReqs =
                Collections.synchronizedList(new ArrayList<>());
        final List<com.rag.favorite.favorite.proto.GetFavoritesByFolderReq> listItemsReqs =
                Collections.synchronizedList(new ArrayList<>());
        final List<com.rag.favorite.favorite.proto.DeleteFavoriteReq> deleteItemReqs =
                Collections.synchronizedList(new ArrayList<>());

        private static <T> void down(StreamObserver<T> obs) {
            obs.onError(Status.UNAVAILABLE
                    .withDescription("span-probe: favorite-rpc is down")
                    .asRuntimeException());
        }

        @Override
        public void createFavoriteFolder(com.rag.favorite.favorite.proto.CreateFavoriteFolderReq request,
                                         StreamObserver<com.rag.favorite.favorite.proto.CreateFavoriteFolderResp> obs) {
            createFolderReqs.add(request);
            down(obs);
        }

        @Override
        public void deleteFavoriteFolder(com.rag.favorite.favorite.proto.DeleteFavoriteFolderReq request,
                                         StreamObserver<com.rag.favorite.favorite.proto.DeleteFavoriteFolderResp> obs) {
            deleteFolderReqs.add(request);
            down(obs);
        }

        @Override
        public void createFavorite(com.rag.favorite.favorite.proto.CreateFavoriteReq request,
                                   StreamObserver<com.rag.favorite.favorite.proto.CreateFavoriteResp> obs) {
            createItemReqs.add(request);
            down(obs);
        }

        @Override
        public void updateFavoriteFolderName(com.rag.favorite.favorite.proto.UpdateFavoriteFolderNameReq request,
                                             StreamObserver<com.rag.favorite.favorite.proto.UpdateFavoriteFolderNameResp> obs) {
            updateNameReqs.add(request);
            down(obs);
        }

        @Override
        public void getFavoriteFolders(com.rag.favorite.favorite.proto.GetFavoriteFoldersReq request,
                                       StreamObserver<com.rag.favorite.favorite.proto.GetFavoriteFoldersResp> obs) {
            listFoldersReqs.add(request);
            down(obs);
        }

        @Override
        public void getFavoritesByFolder(com.rag.favorite.favorite.proto.GetFavoritesByFolderReq request,
                                         StreamObserver<com.rag.favorite.favorite.proto.GetFavoritesByFolderResp> obs) {
            listItemsReqs.add(request);
            down(obs);
        }

        @Override
        public void deleteFavorite(com.rag.favorite.favorite.proto.DeleteFavoriteReq request,
                                   StreamObserver<com.rag.favorite.favorite.proto.DeleteFavoriteResp> obs) {
            deleteItemReqs.add(request);
            down(obs);
        }
    }

    private static FakeFavoriteService fake;
    private static Server server;
    private static ManagedChannel channel;

    @BeforeAll
    static void startDownstream() throws IOException {
        String name = InProcessServerBuilder.generateName();
        fake = new FakeFavoriteService();
        server = InProcessServerBuilder.forName(name).directExecutor().addService(fake).build().start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
    }

    @AfterAll
    static void stopDownstream() throws InterruptedException {
        if (channel != null) {
            channel.shutdownNow();
            channel.awaitTermination(5, TimeUnit.SECONDS);
        }
        if (server != null) {
            server.shutdownNow();
            server.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @BeforeEach
    void resetProbe() {
        EXPORTER.clear();
        fake.createFolderReqs.clear();
        fake.deleteFolderReqs.clear();
        fake.createItemReqs.clear();
        fake.updateNameReqs.clear();
        fake.listFoldersReqs.clear();
        fake.listItemsReqs.clear();
        fake.deleteItemReqs.clear();
    }

    // ══════════════════════════ 被测对象（真实现 + 真下游 stub） ══════════════════════════

    private static FavoriteRpcClient rpcClient;

    private static FavoriteCreateFolderService createFolderSvc;
    private static FavoriteDeleteFolderService deleteFolderSvc;
    private static FavoriteCreateItemService createItemSvc;
    private static FavoriteUpdateFolderNameService updateNameSvc;
    private static FavoriteListFoldersService listFoldersSvc;
    private static FavoriteListItemsService listItemsSvc;
    private static FavoriteDeleteItemService deleteItemSvc;

    @BeforeAll
    static void wireServices() {
        rpcClient = mock(FavoriteRpcClient.class);
        when(rpcClient.favoriteRpc()).thenReturn(
                FavoriteServiceGrpc.newBlockingStub(channel));

        FavoriteApiSupport support = new FavoriteApiSupport(new LoggerFactoryBean("favorite-api"));
        createFolderSvc = new FavoriteCreateFolderService(rpcClient, support);
        deleteFolderSvc = new FavoriteDeleteFolderService(rpcClient, support);
        createItemSvc = new FavoriteCreateItemService(rpcClient, support);
        updateNameSvc = new FavoriteUpdateFolderNameService(rpcClient, support);
        listFoldersSvc = new FavoriteListFoldersService(rpcClient, support);
        listItemsSvc = new FavoriteListItemsService(rpcClient, support);
        deleteItemSvc = new FavoriteDeleteItemService(rpcClient, support);
    }

    /** 带 userId claim 的请求（对应 Go 侧鉴权成功后 ctx 里的 {@code userId}）。 */
    private static MockHttpServletRequest authedRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(FavoriteJwtAuthInterceptor.CLAIM_ATTRIBUTE, PROBE_USER_ID);
        return request;
    }

    /** 不带 claim 的请求（对应 Go 侧 {@code extractUserID(ctx)} 失败）。 */
    private static MockHttpServletRequest anonymousRequest() {
        return new MockHttpServletRequest();
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

    // ══════════════════════════ ① 7 个 api span：名字 / tracer / 属性 ══════════════════════════

    @Test
    @DisplayName("✅ createFavoriteFolder 真的导出 FavoriteAPI.CreateFavoriteFolder（tracer=favorite-api）＋ biz.user_id / biz.folder_name")
    void createFolderExportsGoSpan() {
        CreateFavoriteFolderReq req = new CreateFavoriteFolderReq();
        req.setName("  我的收藏夹  ");

        assertThrows(BusinessException.class,
                () -> createFolderSvc.createFavoriteFolder(req, authedRequest()),
                "下游不可达 ⇒ 必须失败（探针前提）");

        SpanData span = requireSpan(GO_API_CREATE_FAVORITE_FOLDER);
        assertEquals(GO_TRACER_API, span.getInstrumentationScopeInfo().getName(),
                "Go: create_favorite_folder_logic.go:33 ⇒ otel.Tracer(\"favorite-api\")");
        assertEquals(PROBE_USER_ID, longAttr(span, GO_ATTR_USER_ID));
        // 🔴 Go 记的是**未 trim 的原值**（create_favorite_folder_logic.go:45）
        assertEquals("  我的收藏夹  ", stringAttr(span, GO_ATTR_FOLDER_NAME),
                "biz.folder_name 在 Go 侧是原样 ⇒ 不得在这里 trim");
    }

    @Test
    @DisplayName("✅ deleteFavoriteFolder 真的导出 FavoriteAPI.DeleteFavoriteFolder ＋ biz.user_id / biz.folder_id")
    void deleteFolderExportsGoSpan() {
        DeleteFavoriteFolderReq req = new DeleteFavoriteFolderReq();
        req.setFolderId(2002L);

        assertThrows(BusinessException.class,
                () -> deleteFolderSvc.deleteFavoriteFolder(req, authedRequest()),
                "下游不可达 ⇒ 必须失败（探针前提）");

        SpanData span = requireSpan(GO_API_DELETE_FAVORITE_FOLDER);
        assertEquals(GO_TRACER_API, span.getInstrumentationScopeInfo().getName());
        assertEquals(PROBE_USER_ID, longAttr(span, GO_ATTR_USER_ID));
        assertEquals(2002L, longAttr(span, GO_ATTR_FOLDER_ID));
    }

    @Test
    @DisplayName("✅ createFavorite 真的导出 FavoriteAPI.CreateFavorite ＋ 4 个属性（target_id 已 trim / target_type 原样）")
    void createItemExportsGoSpan() {
        CreateFavoriteReq req = new CreateFavoriteReq();
        req.setFolderId(2002L);
        req.setTargetId("  t-1  ");
        req.setTargetType("Article");

        assertThrows(BusinessException.class,
                () -> createItemSvc.createFavorite(req, authedRequest()),
                "下游不可达 ⇒ 必须失败（探针前提）");

        SpanData span = requireSpan(GO_API_CREATE_FAVORITE);
        assertEquals(GO_TRACER_API, span.getInstrumentationScopeInfo().getName());
        assertEquals(PROBE_USER_ID, longAttr(span, GO_ATTR_USER_ID));
        assertEquals(2002L, longAttr(span, GO_ATTR_FOLDER_ID));
        // 🔴 Go: create_favorite_logic.go:47 ⇒ strings.TrimSpace(req.TargetId) ⇒ **trim 过**
        assertEquals("t-1", stringAttr(span, GO_ATTR_TARGET_ID),
                "biz.target_id 在 Go 侧是 TrimSpace 过的");
        // 🔴 Go: create_favorite_logic.go:48 ⇒ attribute.String(\"biz.target_type\", req.TargetType) ⇒ **原样**
        assertEquals("Article", stringAttr(span, GO_ATTR_TARGET_TYPE),
                "biz.target_type 在 Go 侧是原样 ⇒ 不得 lower / trim");
    }

    @Test
    @DisplayName("✅ updateFavoriteFolderName 真的导出 FavoriteAPI.UpdateFavoriteFolderName ＋ biz.folder_name 原样")
    void updateNameExportsGoSpan() {
        UpdateFavoriteFolderNameReq req = new UpdateFavoriteFolderNameReq();
        req.setFolderId(2002L);
        req.setName("  新名字  ");

        assertThrows(BusinessException.class,
                () -> updateNameSvc.updateFavoriteFolderName(req, authedRequest()),
                "下游不可达 ⇒ 必须失败（探针前提）");

        SpanData span = requireSpan(GO_API_UPDATE_FAVORITE_FOLDER_NAME);
        assertEquals(GO_TRACER_API, span.getInstrumentationScopeInfo().getName());
        assertEquals(PROBE_USER_ID, longAttr(span, GO_ATTR_USER_ID));
        assertEquals(2002L, longAttr(span, GO_ATTR_FOLDER_ID));
        assertEquals("  新名字  ", stringAttr(span, GO_ATTR_FOLDER_NAME),
                "biz.folder_name 在 Go 侧是原样");
    }

    @Test
    @DisplayName("✅ getFavoritesByFolder 真的导出 FavoriteAPI.GetFavoritesByFolder ＋ biz.user_id / biz.folder_id")
    void listItemsExportsGoSpan() {
        // ⚠️ 本 DTO 走 form 通道（Go 的 `form:"folderId,string"`）⇒ 只有 fromForm 工厂，没有 setter。
        GetFavoritesByFolderReq req = GetFavoritesByFolderReq.fromForm("2002");

        assertThrows(BusinessException.class,
                () -> listItemsSvc.getFavoritesByFolder(req, authedRequest()),
                "下游不可达 ⇒ 必须失败（探针前提）");

        SpanData span = requireSpan(GO_API_GET_FAVORITES_BY_FOLDER);
        assertEquals(GO_TRACER_API, span.getInstrumentationScopeInfo().getName());
        assertEquals(PROBE_USER_ID, longAttr(span, GO_ATTR_USER_ID));
        assertEquals(2002L, longAttr(span, GO_ATTR_FOLDER_ID));
    }

    @Test
    @DisplayName("✅ getFavoriteFolders 真的导出 FavoriteAPI.GetFavoriteFolders（Go 侧只有 biz.user_id 一个属性）")
    void listFoldersExportsGoSpan() {
        assertThrows(BusinessException.class,
                () -> listFoldersSvc.getFavoriteFolders(authedRequest()),
                "下游不可达 ⇒ 必须失败（探针前提）");

        SpanData span = requireSpan(GO_API_GET_FAVORITE_FOLDERS);
        assertEquals(GO_TRACER_API, span.getInstrumentationScopeInfo().getName());
        assertEquals(PROBE_USER_ID, longAttr(span, GO_ATTR_USER_ID));
        // 🔴 Go: get_favorite_folders_logic.go:42 ⇒ **只有** biz.user_id ⇒ 不得多出 folder_id 等属性
        assertEquals(1, span.getAttributes().size(),
                "Go 侧这个 span 只有一个属性 ⇒ 多一个都是漂移。实际 = " + span.getAttributes());
    }

    @Test
    @DisplayName("✅ deleteFavorite 真的导出 FavoriteAPI.DeleteFavorite ＋ biz.user_id / biz.favorite_id")
    void deleteItemExportsGoSpan() {
        DeleteFavoriteReq req = new DeleteFavoriteReq();
        req.setFavoriteId(3003L);

        assertThrows(BusinessException.class,
                () -> deleteItemSvc.deleteFavorite(req, authedRequest()),
                "下游不可达 ⇒ 必须失败（探针前提）");

        SpanData span = requireSpan(GO_API_DELETE_FAVORITE);
        assertEquals(GO_TRACER_API, span.getInstrumentationScopeInfo().getName());
        assertEquals(PROBE_USER_ID, longAttr(span, GO_ATTR_USER_ID));
        assertEquals(3003L, longAttr(span, GO_ATTR_FAVORITE_ID));
    }

    // ══════════════════════════ ② 三条判据 ══════════════════════════

    @Test
    @DisplayName("🔴 判据①：鉴权失败 ⇒ span 存在、**无任何属性**、**不记错**（Go: extractUserID 失败直接 return）")
    void authFailureProducesSpanWithoutAttributesAndWithoutError() {
        CreateFavoriteReq req = new CreateFavoriteReq();
        req.setFolderId(2002L);
        req.setTargetId("  t-1  ");
        req.setTargetType("Article");

        assertThrows(BusinessException.class,
                () -> createItemSvc.createFavorite(req, anonymousRequest()),
                "没有 userId claim ⇒ 必须失败（探针前提）");

        SpanData span = requireSpan(GO_API_CREATE_FAVORITE);
        assertTrue(span.getAttributes().isEmpty(),
                "Go 侧属性在鉴权成功之后才设 ⇒ 鉴权失败时 span 必须**没有**属性。实际 = " + span.getAttributes());
        assertTrue(span.getEvents().isEmpty(),
                "Go 侧 extractUserID 失败直接 return，**没有** RecordError ⇒ 不该有异常事件。实际 = " + span.getEvents());
        assertEquals(StatusCode.UNSET, span.getStatus().getStatusCode(),
                "Go 侧 SetStatus 零调用 ⇒ 状态必须保持 UNSET");
        // 🔴 鉴权失败时**根本没有下游调用**
        assertTrue(fake.createItemReqs.isEmpty(),
                "鉴权失败 ⇒ 不该发出下游请求。实际收到 = " + fake.createItemReqs);
    }

    @Test
    @DisplayName("🔴 判据②：鉴权成功之后的失败 ⇒ **记错**（Go: 每个 span 2 处 RecordError）")
    void downstreamFailureIsRecordedAsSpanEvent() {
        CreateFavoriteReq req = new CreateFavoriteReq();
        req.setFolderId(2002L);
        req.setTargetId("t-1");
        req.setTargetType("article");

        assertThrows(BusinessException.class,
                () -> createItemSvc.createFavorite(req, authedRequest()),
                "下游不可达 ⇒ 必须失败（探针前提）");

        SpanData span = requireSpan(GO_API_CREATE_FAVORITE);
        assertEquals(1, span.getEvents().size(),
                "Go 侧 rpcErr 分支有 span.RecordError(rpcErr) ⇒ 必须恰好 1 个异常事件。实际 = " + span.getEvents());
        assertEquals(StatusCode.UNSET, span.getStatus().getStatusCode(),
                "🔴 Java 的 recordException 与 Go 的 RecordError 一样**不改状态** ⇒ 必须是 UNSET");
    }

    @Test
    @DisplayName("🔴 判据③：span 属性 trim 过，但**下游请求带的是未 trim 的原值**（Go: create_favorite_logic.go:47 vs :58）")
    void spanTrimsTargetIdButDownstreamRequestKeepsRawValue() {
        CreateFavoriteReq req = new CreateFavoriteReq();
        req.setFolderId(2002L);
        req.setTargetId("  t-1  ");
        req.setTargetType("Article");

        assertThrows(BusinessException.class,
                () -> createItemSvc.createFavorite(req, authedRequest()),
                "下游不可达 ⇒ 必须失败（探针前提）");

        SpanData span = requireSpan(GO_API_CREATE_FAVORITE);
        assertEquals("t-1", stringAttr(span, GO_ATTR_TARGET_ID), "span 属性是 trim 过的");

        assertEquals(1, fake.createItemReqs.size(), "下游应当收到恰好 1 个请求");
        assertEquals("  t-1  ", fake.createItemReqs.get(0).getTargetId(),
                "🔴 Go: create_favorite_logic.go:58 ⇒ TargetId: req.TargetId（**未 trim**）⇒ 下游拿到的必须是原值");
        assertEquals("Article", fake.createItemReqs.get(0).getTargetType(),
                "🔴 Go: create_favorite_logic.go:59 ⇒ TargetType: req.TargetType（原样，未规范化）");
    }

    @Test
    @DisplayName("✅ 7 个 api span 的名字集合与 Go 侧逐条一致（不多不少）")
    void apiSpanNameSetMatchesGo() {
        createFolderExportsGoSpan();
        deleteFolderExportsGoSpan();
        createItemExportsGoSpan();
        updateNameExportsGoSpan();
        listItemsExportsGoSpan();
        listFoldersExportsGoSpan();
        deleteItemExportsGoSpan();

        List<String> expected = List.of(
                GO_API_CREATE_FAVORITE_FOLDER,
                GO_API_DELETE_FAVORITE_FOLDER,
                GO_API_CREATE_FAVORITE,
                GO_API_UPDATE_FAVORITE_FOLDER_NAME,
                GO_API_GET_FAVORITES_BY_FOLDER,
                GO_API_GET_FAVORITE_FOLDERS,
                GO_API_DELETE_FAVORITE);
        List<String> actual = exportedSpanNames();
        assertEquals(expected.size(), actual.size(),
                "api 侧每个用例只应产生 1 个 span。实际 = " + actual);
        for (String name : expected) {
            assertNotNull(findSpan(name).orElse(null), "缺少 span: " + name);
        }
    }
}
