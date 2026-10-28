package com.rag.favorite.favorite.common.span;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * favorite 域的 <b>手写 otel span</b>（A9 轴 · G3 域侧自修 · 2026-10-27）。
 *
 * <h2>修的是什么</h2>
 * <p>Go 真源（{@code Sea-RideTheWind_golang}，{@code go.mod:17} 锁 go-zero v1.10.2）在 favorite 域
 * <b>手写了 16 个 span</b>：<b>api 侧 7 个</b> ＋ <b>rpc 侧 7 个</b> ＋ <b>rpc 侧 2 个依赖 span</b>，
 * 共用 <b>7 个属性 key</b>。RAG 侧此前<b>一个都没有</b>
 * ⇒ 遥测（jaeger）里 favorite 的业务维度全空（trace 上只有共享层建的 SERVER span 与 {@code sea.*} 属性）。
 *
 * <h2>Go 出处（逐条：声明点 + 属性写入点）</h2>
 * <table border="1">
 *   <caption>16 个 span（api 侧 7 + rpc 侧 7 + 依赖 2）</caption>
 *   <tr><th>Go 文件:行号</th><th>tracer</th><th>span 名</th><th>属性（写入点）</th></tr>
 *   <tr><td>{@code api/internal/logic/favorite/create_favorite_folder_logic.go:33,42-45}</td>
 *       <td>{@code favorite-api}</td><td>{@code FavoriteAPI.CreateFavoriteFolder}</td>
 *       <td>{@code biz.user_id} · {@code biz.folder_name}</td></tr>
 *   <tr><td>{@code api/internal/logic/favorite/delete_favorite_folder_logic.go:33,42-45}</td>
 *       <td>{@code favorite-api}</td><td>{@code FavoriteAPI.DeleteFavoriteFolder}</td>
 *       <td>{@code biz.user_id} · {@code biz.folder_id}</td></tr>
 *   <tr><td>{@code api/internal/logic/favorite/create_favorite_logic.go:34,43-48}</td>
 *       <td>{@code favorite-api}</td><td>{@code FavoriteAPI.CreateFavorite}</td>
 *       <td>{@code biz.user_id} · {@code biz.folder_id} · {@code biz.target_id} · {@code biz.target_type}</td></tr>
 *   <tr><td>{@code api/internal/logic/favorite/update_favorite_folder_name_logic.go:33,42-46}</td>
 *       <td>{@code favorite-api}</td><td>{@code FavoriteAPI.UpdateFavoriteFolderName}</td>
 *       <td>{@code biz.user_id} · {@code biz.folder_id} · {@code biz.folder_name}</td></tr>
 *   <tr><td>{@code api/internal/logic/favorite/get_favorites_by_folder_logic.go:33,42-45}</td>
 *       <td>{@code favorite-api}</td><td>{@code FavoriteAPI.GetFavoritesByFolder}</td>
 *       <td>{@code biz.user_id} · {@code biz.folder_id}</td></tr>
 *   <tr><td>{@code api/internal/logic/favorite/get_favorite_folders_logic.go:33,42}</td>
 *       <td>{@code favorite-api}</td><td>{@code FavoriteAPI.GetFavoriteFolders}</td>
 *       <td>{@code biz.user_id}</td></tr>
 *   <tr><td>{@code api/internal/logic/favorite/delete_favorite_logic.go:33,42-45}</td>
 *       <td>{@code favorite-api}</td><td>{@code FavoriteAPI.DeleteFavorite}</td>
 *       <td>{@code biz.user_id} · {@code biz.favorite_id}</td></tr>
 *   <tr><td>{@code rpc/internal/logic/create_favorite_folder_logic.go:43,46-49}</td>
 *       <td>{@code favorite-rpc}</td><td>{@code FavoriteRPC.CreateFavoriteFolder}</td>
 *       <td>{@code biz.user_id} · {@code biz.folder_name}</td></tr>
 *   <tr><td>{@code rpc/internal/logic/delete_favorite_folder_logic.go:42,45-48}</td>
 *       <td>{@code favorite-rpc}</td><td>{@code FavoriteRPC.DeleteFavoriteFolder}</td>
 *       <td>{@code biz.user_id} · {@code biz.folder_id}</td></tr>
 *   <tr><td>{@code rpc/internal/logic/create_favorite_logic.go:44,47-52}</td>
 *       <td>{@code favorite-rpc}</td><td>{@code FavoriteRPC.CreateFavorite}</td>
 *       <td>{@code biz.user_id} · {@code biz.folder_id} · {@code biz.target_id} · {@code biz.target_type}</td></tr>
 *   <tr><td>{@code rpc/internal/logic/update_favorite_folder_name_logic.go:42,45-49}</td>
 *       <td>{@code favorite-rpc}</td><td>{@code FavoriteRPC.UpdateFavoriteFolderName}</td>
 *       <td>{@code biz.user_id} · {@code biz.folder_id} · {@code biz.folder_name}</td></tr>
 *   <tr><td>{@code rpc/internal/logic/get_favorites_by_folder_logic.go:42,45-48}</td>
 *       <td>{@code favorite-rpc}</td><td>{@code FavoriteRPC.GetFavoritesByFolder}</td>
 *       <td>{@code biz.user_id} · {@code biz.folder_id}</td></tr>
 *   <tr><td>{@code rpc/internal/logic/get_favorite_folders_logic.go:40,43}</td>
 *       <td>{@code favorite-rpc}</td><td>{@code FavoriteRPC.GetFavoriteFolders}</td>
 *       <td>{@code biz.user_id}</td></tr>
 *   <tr><td>{@code rpc/internal/logic/delete_favorite_logic.go:42,45-48}</td>
 *       <td>{@code favorite-rpc}</td><td>{@code FavoriteRPC.DeleteFavorite}</td>
 *       <td>{@code biz.user_id} · {@code biz.favorite_id}</td></tr>
 *   <tr><td>{@code rpc/internal/logic/helper.go:93,96}（{@code ensureUserExists}）</td>
 *       <td>{@code favorite-rpc}</td><td>{@code Dependency.User.GetUser}</td>
 *       <td>{@code biz.user_id}</td></tr>
 *   <tr><td>{@code rpc/internal/logic/helper.go:127,131}（{@code resolveArticleSnapshot}）</td>
 *       <td>{@code favorite-rpc}</td><td>{@code Dependency.Article.GetArticle}</td>
 *       <td>{@code biz.article_id}</td></tr>
 * </table>
 *
 * <h2>🔴 属性名全集只有 7 个（Go 侧是<b>硬编码字面量</b>，没有集中常量表）</h2>
 * <p>{@code biz.user_id}(Int64) · {@code biz.folder_id}(Int64) · {@code biz.favorite_id}(Int64) ·
 * {@code biz.folder_name}(String) · {@code biz.target_id}(String) ·
 * {@code biz.target_type}(String) · {@code biz.article_id}(String)
 * <br>⚠️ <b>注意本域用 {@code biz.*} 前缀</b>（不是共享层的 {@code sea.*}，也不是 admin 域的 {@code audit.*}）。
 * <br>⚠️ Go 侧属性只有 {@code attribute.Int64(...)} 与 {@code attribute.String(...)} <b>两种</b>构造器
 * ⇒ Java 侧对应 {@code span.setAttribute(key, long)} 与 {@code span.setAttribute(key, String)}。
 *
 * <h2>🔴 三处「Go 侧的取值陷阱」必须逐字复刻，<b>不许顺手修正</b></h2>
 * <ol>
 *   <li><b>{@code biz.folder_name} 记的是<b>未 trim 的原值</b></b>
 *       （api 侧 {@code req.Name} / rpc 侧 {@code in.GetName()}），
 *       而真正落库用的是 {@code normalizeFolderName(...)}（trim 后）
 *       ⇒ ⇒ <b>span 记的值 ≠ 落库的值</b>。</li>
 *   <li><b>{@code biz.target_type} 记的是<b>未规范化（原样大小写）的原值</b></b>
 *       （{@code in.GetTargetType()}），而落库用 {@code normalizeTargetType(...)}（lower + trim）
 *       ⇒ ⇒ 同上。</li>
 *   <li><b>{@code biz.target_id} 两端都已 trim</b>
 *       （api 侧 {@code strings.TrimSpace(req.TargetId)} / rpc 侧 {@code strings.TrimSpace(in.GetTargetId())}）
 *       ⇒ ⇒ <b>只有它被 trim</b>，与 ① ② <b>不对称</b>。</li>
 * </ol>
 *
 * <h2>🔴 三类「Go 记不记这个错误」的判据（本域<b>不是</b>「有异常就记」）</h2>
 * <ol>
 *   <li><b>api 侧 7 个 span</b>：Go 的 {@code SetAttributes} 在<b>鉴权成功之后</b>
 *       （{@code extractUserID} 失败 ⇒ 直接 return，<b>不 RecordError</b>）；
 *       鉴权失败之后<b>任何</b>出错都记
 *       （每个 span 恰好 2 处 {@code RecordError}：rpcErr ＋ err）
 *       ⇒ ⇒ 「未鉴权请求」这一条：<b>span 存在、但无任何属性、且不记错</b>。</li>
 *   <li><b>rpc 侧 7 个 {@code FavoriteRPC.*} span</b>：{@code SetAttributes} <b>紧跟 Start</b>，
 *       <b>先于任何入参校验</b> ⇒ <b>非法入参的 span 也带完整属性</b>；
 *       且<b>任何</b>出错都记（含 {@code InvalidArgument} / {@code AlreadyExists} /
 *       用户不存在 / 名称重复这类<b>纯业务失败</b>）⇒ <b>不按业务码过滤</b>。</li>
 *   <li><b>rpc 侧 2 个 {@code Dependency.*} span</b>：<b>只有下游 RPC 调用失败才记</b>
 *       （{@code helper.go:100} / {@code helper.go:139}）；
 *       <b>业务性失败不记</b> —— {@code helper.go:109-111}（resp 为空 / 未找到）、
 *       {@code helper.go:113-115}（uid 不符 / status 缺失）、
 *       {@code helper.go:117-119}（status ≠ 0 ⇒ 禁止）三处<b>都没有 RecordError</b>。</li>
 * </ol>
 *
 * <h2>🔴 span 状态恒为 {@code UNSET}（<b>本类不提供任何 setStatus 入口</b>）</h2>
 * <p>Go 侧 favorite 域 <b>{@code span.SetStatus(...)} 零调用</b>（实测：{@code SetStatus} 命中数 = 0）
 * ⇒ 错误状态<b>只</b>由 {@code span.RecordError(err)} 表达。
 * <br>🔴 <b>已实测的 SDK 事实</b>（{@code opentelemetry-sdk-trace-1.55.0.jar} 的
 * {@code io.opentelemetry.sdk.trace.SdkSpan#recordException(Throwable, Attributes)} 字节码）：
 * 构造 exception attributes ⇒ {@code ExceptionEventData.create(...)} ⇒ {@code addTimedEvent(...)}
 * ⇒ {@code return this} ⇒ <b>全程没有任何 {@code setStatus} 调用</b>
 * ⇒ ⇒ Java 的 {@code span.recordException(e)} <b>只加 exception event、不改 span status</b>
 * ⇒ ⇒ <b>与 Go 的 {@code span.RecordError(err)} 语义一致</b>
 * ⇒ ⇒ <b>RAG 侧 span 的 status 必须保持 {@code UNSET}</b>
 * ⇒ ⇒ <b>任何 {@code setStatus(ERROR)} 都是引入 Go 侧不存在的状态</b> ⇒ 本类<b>刻意不提供</b>该入口。
 *
 * <h2>🔴 与共享层的关系（<b>不需要动 {@code service/common}</b>）</h2>
 * <p>本类直接用 {@link GlobalOpenTelemetry#getTracer(String)}，与共享层
 * {@code GoStyleOtelAgent.tracer(name)} 是<b>同一个 API</b>（Go 侧也是 {@code otel.Tracer(...)}）
 * ⇒ <b>favorite 域内即可完成</b>，不需要改共享模块。
 * <br>⇒ 子 span 会<b>正确挂到共享层的 SERVER span 之下</b>：
 * 共享层 {@code RpcInterceptor} 有 {@code span.makeCurrent()} ⇒ 当前上下文里有 SERVER span ⇒
 * {@code tracer.spanBuilder(...).startSpan()}（隐式取 {@code Context.current()}）自动成为它的子 span
 * ⇔ Go 侧 {@code tracer.Start(l.ctx, …)} 用的 {@code l.ctx} 也带着 SERVER span ⇒ <b>同构</b>。
 *
 * <h2>L4 idiomatization（AGENTS.md §3.2 三段式）</h2>
 * <p><b>比 Go 多做了什么</b>：Go 用 {@code ctx} 显式传递；Java 没有 ctx 参数这一说
 * ⇒ 用 {@link Scope} 模拟「span 进入当前上下文、退出时还原」，
 * 并用 {@link Supplier} / {@link Function} 代替 Go 的「后续代码内联在同一个函数里」。
 * <b>语义等价论证</b>：Go 的 {@code defer span.End()} ⇔ 本类的 {@code finally}；
 * Go 的 {@code span.RecordError(err)} ⇔ 本类的 {@code span.recordException(e)}；
 * Go 的「属性在鉴权之后 / 在校验之前」这个<b>时序</b> ⇔ 本类的
 * 「<b>属性阶段</b>（{@code attributes}）与<b>业务阶段</b>（{@code body}）分离」两段式。
 * <p><b>已知差异（表达层）</b>：① Go 侧 api 的 {@code RecordError} 逐点分布（每 span 2 处，
 * 且记的是<b>原始 gRPC 错误</b>），本类统一在「异常逃出业务阶段时」记一次
 * ⇒ 事件<b>存在性</b>一致、<b>消息文本</b>不同（RAG 记的是翻译后的业务异常）；
 * ② Go 的依赖 span 把「下游调用」与「调用后的业务判定」写在同一个函数里，
 * 本类用 {@link #tracedDependency} 的两段式（{@code call} / {@code after}）表达同一个时序。
 */
public final class FavoriteSpans {

    // ══════════════════════════ tracer 名（Go 侧硬编码在调用点） ══════════════════════════

    /**
     * Go 出处：api 侧 7 个 logic 全部写死 {@code otel.Tracer("favorite-api")}
     * （{@code create_favorite_folder_logic.go:33} · {@code delete_favorite_folder_logic.go:33} ·
     * {@code create_favorite_logic.go:34} · {@code update_favorite_folder_name_logic.go:33} ·
     * {@code get_favorites_by_folder_logic.go:33} · {@code get_favorite_folders_logic.go:33} ·
     * {@code delete_favorite_logic.go:33}）。
     */
    public static final String TRACER_NAME_API = "favorite-api";

    /**
     * Go 出处：rpc 侧 7 个 logic ＋ helper.go 的 2 个依赖 span 全部写死 {@code otel.Tracer("favorite-rpc")}
     * （{@code create_favorite_folder_logic.go:43} · {@code delete_favorite_folder_logic.go:42} ·
     * {@code create_favorite_logic.go:44} · {@code update_favorite_folder_name_logic.go:42} ·
     * {@code get_favorites_by_folder_logic.go:42} · {@code get_favorite_folders_logic.go:40} ·
     * {@code delete_favorite_logic.go:42} · {@code helper.go:93} · {@code helper.go:127}）。
     */
    public static final String TRACER_NAME_RPC = "favorite-rpc";

    // ══════════════════════════ api 侧 7 个 span 名 ══════════════════════════

    /** Go {@code api/internal/logic/favorite/create_favorite_folder_logic.go:33}。 */
    public static final String API_CREATE_FAVORITE_FOLDER = "FavoriteAPI.CreateFavoriteFolder";

    /** Go {@code api/internal/logic/favorite/delete_favorite_folder_logic.go:33}。 */
    public static final String API_DELETE_FAVORITE_FOLDER = "FavoriteAPI.DeleteFavoriteFolder";

    /** Go {@code api/internal/logic/favorite/create_favorite_logic.go:34}。 */
    public static final String API_CREATE_FAVORITE = "FavoriteAPI.CreateFavorite";

    /** Go {@code api/internal/logic/favorite/update_favorite_folder_name_logic.go:33}。 */
    public static final String API_UPDATE_FAVORITE_FOLDER_NAME = "FavoriteAPI.UpdateFavoriteFolderName";

    /** Go {@code api/internal/logic/favorite/get_favorites_by_folder_logic.go:33}。 */
    public static final String API_GET_FAVORITES_BY_FOLDER = "FavoriteAPI.GetFavoritesByFolder";

    /** Go {@code api/internal/logic/favorite/get_favorite_folders_logic.go:33}。 */
    public static final String API_GET_FAVORITE_FOLDERS = "FavoriteAPI.GetFavoriteFolders";

    /** Go {@code api/internal/logic/favorite/delete_favorite_logic.go:33}。 */
    public static final String API_DELETE_FAVORITE = "FavoriteAPI.DeleteFavorite";

    // ══════════════════════════ rpc 侧 7 个 span 名 ══════════════════════════

    /** Go {@code rpc/internal/logic/create_favorite_folder_logic.go:43}。 */
    public static final String RPC_CREATE_FAVORITE_FOLDER = "FavoriteRPC.CreateFavoriteFolder";

    /** Go {@code rpc/internal/logic/delete_favorite_folder_logic.go:42}。 */
    public static final String RPC_DELETE_FAVORITE_FOLDER = "FavoriteRPC.DeleteFavoriteFolder";

    /** Go {@code rpc/internal/logic/create_favorite_logic.go:44}。 */
    public static final String RPC_CREATE_FAVORITE = "FavoriteRPC.CreateFavorite";

    /** Go {@code rpc/internal/logic/update_favorite_folder_name_logic.go:42}。 */
    public static final String RPC_UPDATE_FAVORITE_FOLDER_NAME = "FavoriteRPC.UpdateFavoriteFolderName";

    /** Go {@code rpc/internal/logic/get_favorites_by_folder_logic.go:42}。 */
    public static final String RPC_GET_FAVORITES_BY_FOLDER = "FavoriteRPC.GetFavoritesByFolder";

    /** Go {@code rpc/internal/logic/get_favorite_folders_logic.go:40}。 */
    public static final String RPC_GET_FAVORITE_FOLDERS = "FavoriteRPC.GetFavoriteFolders";

    /** Go {@code rpc/internal/logic/delete_favorite_logic.go:42}。 */
    public static final String RPC_DELETE_FAVORITE = "FavoriteRPC.DeleteFavorite";

    // ══════════════════════════ rpc 侧 2 个依赖 span 名 ══════════════════════════

    /** Go {@code rpc/internal/logic/helper.go:93}（{@code ensureUserExists}）。 */
    public static final String DEPENDENCY_USER_GET_USER = "Dependency.User.GetUser";

    /** Go {@code rpc/internal/logic/helper.go:127}（{@code resolveArticleSnapshot}）。 */
    public static final String DEPENDENCY_ARTICLE_GET_ARTICLE = "Dependency.Article.GetArticle";

    // ══════════════════════════ 7 个属性 key（Go 侧硬编码字面量） ══════════════════════════

    /** {@code biz.user_id}（Int64）—— 16 个 span 里 15 个用（除 {@code Dependency.Article.GetArticle}）。 */
    public static final String ATTR_USER_ID = "biz.user_id";

    /** {@code biz.folder_id}（Int64）—— api 侧 3 个 ＋ rpc 侧 3 个。 */
    public static final String ATTR_FOLDER_ID = "biz.folder_id";

    /** {@code biz.favorite_id}（Int64）—— api 侧 1 个 ＋ rpc 侧 1 个。 */
    public static final String ATTR_FAVORITE_ID = "biz.favorite_id";

    /**
     * 🔴 {@code biz.folder_name}（String）—— <b>记的是未 trim 的原值</b>
     * （见类注释「取值陷阱」①）⇒ <b>不要</b>在这里做 trim。
     */
    public static final String ATTR_FOLDER_NAME = "biz.folder_name";

    /**
     * 🔴 {@code biz.target_id}（String）—— <b>两端都已 trim</b>
     * （见类注释「取值陷阱」③）⇒ 调用点负责 trim。
     */
    public static final String ATTR_TARGET_ID = "biz.target_id";

    /**
     * 🔴 {@code biz.target_type}（String）—— <b>记的是未规范化的原值</b>
     * （见类注释「取值陷阱」②）⇒ <b>不要</b>做 lower / trim。
     */
    public static final String ATTR_TARGET_TYPE = "biz.target_type";

    /** {@code biz.article_id}（String）—— 只给 {@code Dependency.Article.GetArticle} 用（值 = trim 后的目标 ID）。 */
    public static final String ATTR_ARTICLE_ID = "biz.article_id";

    private FavoriteSpans() {
        // 纯静态工具，禁止实例化
    }

    // ══════════════════════════ api 侧入口 ══════════════════════════

    /**
     * 在 api 侧的<b>一个手写 span</b> 里执行 {@code body}。
     *
     * <h2>🔴 本入口的「不记错」判据（与 rpc 侧不同）</h2>
     * <p>Go 侧 api 的 {@code SetAttributes} 在<b>鉴权成功之后</b>：
     * {@code extractUserID(ctx)} 失败 ⇒ 直接 return（<b>不 RecordError</b>）⇒
     * ⇒ <b>未鉴权请求的 span 存在、但一个属性都没有、且不记错</b>。
     * <br>本类把「取 userId」放在<b>属性阶段</b>：它在 Go 里的位置就是
     * 「{@code Start} 之后、{@code SetAttributes} 之前」⇒
     * <ul>
     *   <li>取 userId <b>抛异常</b> ⇒ 视为<b>鉴权失败</b> ⇒ <b>不记错、不设任何属性</b>，
     *       但 <b>span 仍然存在并结束</b>（Go 的 {@code defer span.End()} 一样会跑）；</li>
     *   <li>取 userId 成功 ⇒ 设属性 ⇒ 进入业务阶段 ⇒ <b>业务阶段的任何异常都记</b>
     *       （Go：鉴权成功后 {@code RecordError} 逐点分布 ⇒ 存在性一致）。</li>
     * </ul>
     *
     * <p>⚠️ <b>状态恒为 {@code UNSET}</b>：Go 侧本域 {@code SetStatus} 零调用，
     * 且 Java 的 {@code recordException} 也<b>不改状态</b>（见类注释）⇒ 本方法<b>不</b>调 {@code setStatus}。
     *
     * @param spanName   本域 7 个 api span 名常量之一
     * @param userId     取当前请求的 userId（对应 Go 的 {@code extractUserID(ctx)}）——<b>可能抛异常</b>
     * @param attributes 设属性；Go 在鉴权成功之后才 {@code SetAttributes}
     * @param body       业务体；抛出的异常按「鉴权成功后一律记」处理后原样上抛
     */
    public static <T> T tracedApi(String spanName,
                                  LongSupplier userId,
                                  BiConsumer<Span, Long> attributes,
                                  Function<Long, T> body) {
        Tracer tracer = GlobalOpenTelemetry.getTracer(TRACER_NAME_API);
        Span span = tracer.spanBuilder(spanName).startSpan();
        try (Scope scope = span.makeCurrent()) {
            long uid;
            try {
                uid = userId.getAsLong();
            } catch (RuntimeException | Error e) {
                // 🔴 Go: extractUserID 失败 ⇒ 直接 return，**不 RecordError**
                //    ⇒ 不记错、不设任何属性；但 span 照样 End（Go 的 defer）。
                throw e;
            }
            attributes.accept(span, uid);
            try {
                return body.apply(uid);
            } catch (RuntimeException | Error e) {
                // Go: 鉴权成功之后的每一个错误分支都有 span.RecordError(...)
                // ⇒ Java 的 recordException 与之语义一致（只加事件、不改状态）
                span.recordException(e);
                throw e;
            }
        } finally {
            span.end();
        }
    }

    // ══════════════════════════ rpc 侧入口 ══════════════════════════

    /**
     * 在 rpc 侧的<b>一个手写 span</b> 里执行 {@code body}。
     *
     * <h2>🔴 本入口的判据（与 api 侧不同）</h2>
     * <p>Go 侧 rpc 的 {@code SetAttributes} <b>紧跟 {@code Start}、先于任何入参校验</b>
     * ⇒ <b>非法入参的 span 也带完整属性</b>；
     * 且<b>任何</b>出错都记（含 {@code InvalidArgument} / {@code AlreadyExists} /
     * 用户不存在 / 名称重复这类<b>纯业务失败</b>）⇒ <b>不按业务码过滤</b>。
     * <br>⇒ 本方法的属性阶段放在业务阶段<b>之前</b>（proto getter 不会抛，故属性阶段实际不会失败）。
     *
     * <p>⚠️ <b>状态恒为 {@code UNSET}</b>（见类注释）。
     *
     * @param spanName   本域 7 个 rpc span 名常量之一
     * @param attributes 设属性；Go 在 {@code Start} 之后立刻设
     * @param body       业务体；抛出的异常<b>一律</b>记后原样上抛
     */
    public static <T> T tracedRpc(String spanName,
                                  Consumer<Span> attributes,
                                  Supplier<T> body) {
        Tracer tracer = GlobalOpenTelemetry.getTracer(TRACER_NAME_RPC);
        Span span = tracer.spanBuilder(spanName).startSpan();
        try (Scope scope = span.makeCurrent()) {
            attributes.accept(span);
            try {
                return body.get();
            } catch (RuntimeException | Error e) {
                span.recordException(e);
                throw e;
            }
        } finally {
            span.end();
        }
    }

    // ══════════════════════════ rpc 侧依赖 span 入口 ══════════════════════════

    /**
     * 下游 RPC 调用 —— 对应 Go 的 {@code svcCtx.XxxRpc.Yyy(depCtx, …)} 那一行。
     *
     * <p>⚠️ 它<b>只包住那一次调用</b>：调用失败 ⇒ 记错（Go 的 {@code span.RecordError(err)}），
     * 调用成功之后的业务判定失败 ⇒ <b>不记</b>（Go 侧那三处业务分支都没有 {@code RecordError}）。
     */
    @FunctionalInterface
    public interface DependencyCall<T> {
        T call();
    }

    /**
     * 在<b>依赖 span</b>里执行「一次下游调用 ＋ 调用后的业务判定」。
     *
     * <h2>🔴 本入口的判据（与 api / rpc 两个入口都不同）</h2>
     * <p>Go 侧的两个依赖 span（{@code helper.go:93} / {@code helper.go:127}）<b>只有下游 RPC 调用失败才记错</b>：
     * <pre>
     * resp, err := svcCtx.UserRpc.GetUser(depCtx, &amp;userservice.GetUserReq{Uid: userID})
     * if err != nil {
     *     span.RecordError(err)                      // ← helper.go:100：**只有这里**记
     *     ...
     * }
     * if resp == nil || !resp.Found || resp.User == nil {   // ← helper.go:109-111：**不记**
     * if resp.User.Uid != userID || resp.User.Status == nil { // ← helper.go:113-115：**不记**
     * if resp.User.GetStatus() != 0 {                       // ← helper.go:117-119：**不记**
     * </pre>
     * ⇒ 本方法用<b>两段式</b>表达同一个时序：{@code call} 失败 ⇒ 记错 ＋ 走 {@code errorMapper}；
     * {@code after}（调用后的业务判定）失败 ⇒ <b>不记</b>、原样上抛。
     *
     * <p>⚠️ <b>状态恒为 {@code UNSET}</b>（见类注释）。
     *
     * @param spanName    {@link #DEPENDENCY_USER_GET_USER} 或 {@link #DEPENDENCY_ARTICLE_GET_ARTICLE}
     * @param attributes  设属性（Go 在 {@code Start} 之后立刻设）
     * @param call        下游调用；抛出的异常 ⇒ <b>记错</b> ＋ 交给 {@code errorMapper} 翻译
     * @param errorMapper 把「下游调用失败」翻译成业务异常（Go 的 {@code GRPCError(...)} 那一支）
     * @param after       调用成功后的业务判定；抛出的异常 ⇒ <b>不记错</b>
     */
    public static <T, R> R tracedDependency(String spanName,
                                             Consumer<Span> attributes,
                                             DependencyCall<T> call,
                                             Function<Throwable, RuntimeException> errorMapper,
                                             Function<T, R> after) {
        Tracer tracer = GlobalOpenTelemetry.getTracer(TRACER_NAME_RPC);
        Span span = tracer.spanBuilder(spanName).startSpan();
        try (Scope scope = span.makeCurrent()) {
            attributes.accept(span);
            T result;
            try {
                result = call.call();
            } catch (RuntimeException | Error e) {
                // 🔴 Go: span.RecordError(err) 只落在「下游 RPC 调用失败」这一支
                //    （helper.go:100 / helper.go:139）
                span.recordException(e);
                throw errorMapper.apply(e);
            }
            // Go: 调用成功之后的业务性失败分支**不** RecordError
            return after.apply(result);
        } finally {
            span.end();
        }
    }
}
