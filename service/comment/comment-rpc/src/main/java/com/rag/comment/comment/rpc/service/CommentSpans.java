package com.rag.comment.comment.rpc.service;

import com.rag.comment.comment.rpc.exception.CommentExceptions;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * comment-rpc 的 <b>手写 span</b>（DRIFT-COMMENT-C-3 · G3 域侧自修 · 2026-10-27）。
 *
 * <h2>修的是什么</h2>
 * <p>Go 侧 comment-rpc 的 <b>7 个 logic 里有 6 个</b>在方法开头手写
 * {@code tracer := otel.Tracer("comment-rpc"); ctx, span := tracer.Start(l.ctx, "Action-Comment-…"); defer span.End()}，
 * 并打上 {@code audit.*} 属性（<b>5 个 span / 9 个属性名 / 23 处写入</b>）；
 * 另有 <b>1 个</b>（{@code GetComment}）用<b>另一个 tracer 名</b>与<b>无前缀</b>的 6 个属性。
 * RAG 侧此前<b>一个都没有</b> ⇒ 运维在 trace 里看不到 comment 域的审计维度。
 *
 * <h2>Go 出处（逐条：声明点 + 消费点）</h2>
 * <table border="1">
 *   <caption>comment-rpc 的 6 个 span（<b>逐条实测</b>，不是按方法名推的）</caption>
 *   <tr><th>Go logic</th><th>tracer</th><th>span 名</th><th>属性</th></tr>
 *   <tr><td>{@code create_comment_logic.go:41-42}</td><td>{@code comment-rpc}</td>
 *       <td>{@code Action-Comment-Create}</td>
 *       <td>{@code audit.operator_id}（Int64）· {@code audit.target_type}（String）·
 *           {@code audit.target_id}（String）· {@code audit.root_id}（Int64）·
 *           {@code audit.parent_id}（Int64）<br>（写入点 {@code :56-60}）</td></tr>
 *   <tr><td>{@code delete_comment_logic.go:37-38}</td><td>{@code comment-rpc}</td>
 *       <td>{@code Action-Comment-Delete}</td>
 *       <td>{@code audit.operator_id} · {@code audit.comment_id}（Int64）·
 *           {@code audit.target_type} · {@code audit.target_id}<br>（写入点 {@code :52-55}）</td></tr>
 *   <tr><td>{@code manage_comment_logic.go:37-38}</td><td>{@code comment-rpc}</td>
 *       <td>{@code Action-Comment-Manage}</td>
 *       <td>{@code audit.operator_id} · {@code audit.comment_id} ·
 *           {@code audit.action_type}（String ＝ <b>枚举的 proto 值名</b>）·
 *           {@code audit.target_type} · {@code audit.target_id}<br>（写入点 {@code :52-56}）</td></tr>
 *   <tr><td>{@code report_comment_logic.go:37-38}</td><td>{@code comment-rpc}</td>
 *       <td>{@code Action-Comment-Report}</td>
 *       <td>{@code audit.operator_id} · {@code audit.comment_id} ·
 *           {@code audit.target_type} · {@code audit.target_id} ·
 *           {@code audit.reason}（String ＝ <b>枚举的 proto 值名</b>）<br>（写入点 {@code :53-57}）</td></tr>
 *   <tr><td>{@code update_subject_state_logic.go:37-38}</td><td>{@code comment-rpc}</td>
 *       <td>{@code Action-Comment-UpdateSubjectState}</td>
 *       <td>{@code audit.operator_id} · {@code audit.target_type} · {@code audit.target_id} ·
 *           {@code audit.target_state}（Int64 ＝ <b>枚举的编号</b>，非名字）<br>（写入点 {@code :53-56}）</td></tr>
 *   <tr><td>{@code get_comment_logic.go:52-53}</td><td>🔴 <b>{@code comment.rpc}</b>（<b>带点</b>）</td>
 *       <td>{@code GetComment}</td>
 *       <td>🔴 <b>无前缀</b>：{@code target_id}（String）· {@code root_id}（Int64）·
 *           {@code page}（Int64）· {@code page_size}（Int64）·
 *           {@code target_type}（String）· {@code sort_type}（Int64）<br>（写入点 {@code :70-75}）</td></tr>
 * </table>
 *
 * <h2>🔴 三处「Go 侧的不一致」必须逐字保留，不能顺手统一</h2>
 * <ol>
 *   <li><b>tracer 名不统一</b>：5 个 {@code Action-*} 用 {@code "comment-rpc"}，
 *       而 {@code GetComment} 用 {@code "comment.rpc"}（<b>带点</b>）⇒ 两侧的 instrumentation scope 名不同。
 *       「统一成 {@code comment-rpc}」会让 {@code GetComment} 的 scope 名与 Go 不一致 ⇒ <b>可观察差异</b>。</li>
 *   <li><b>属性前缀不统一</b>：5 个 {@code Action-*} 用 {@code audit.*}，
 *       而 {@code GetComment} 的 6 个属性<b>没有前缀</b>（{@code target_id} / {@code page} / …）。
 *       ⇒ 因此本类的常量分成两组，<b>不能</b>复用同一个 {@code ATTR_TARGET_ID}。</li>
 *   <li><b>状态语义不统一</b>：Go 的 {@code span.RecordError(err)}
 *       （otel-go v1.x）<b>只加异常事件、不改状态</b>；
 *       而 {@code get_comment_logic.go:85-86 / 101-102 / 110-111 / 117-118}
 *       <b>额外显式</b> {@code span.SetStatus(codes.Error, err.Error())}。
 *       ⇒ ⇒ <b>5 个 {@code Action-*} span 失败时状态保持 UNSET</b>，
 *       <b>{@code GetComment} 失败时状态是 ERROR</b> ⇒ 本类用
 *       {@link #traced} 与 {@link #tracedWithErrorStatus} 两个入口分别对齐。</li>
 * </ol>
 *
 * <h2>🔴 「Go 记不记这个错误」的判据（不是「有异常就记」）</h2>
 * <p>Go 侧的 {@code span.RecordError(err)} <b>精确落在 {@code result = "sys_fail"} 的分支</b>，
 * 而 {@code result = "biz_fail"} 的分支（输入错 / 实体不存在 / 无权限）<b>一律不记</b>。
 * 实测落点（15 处）：{@code create_comment_logic.go:87,96,120,128} ·
 * {@code delete_comment_logic.go:75,91,117} · {@code manage_comment_logic.go:98} ·
 * {@code report_comment_logic.go:89} · {@code update_subject_state_logic.go:78,103} ·
 * {@code get_comment_logic.go:85,101,110,117}。
 * <p>⇒ <b>判据 = 业务码落在哪一段</b>：Go {@code service/comment/common/errmsg/errmsg.go:35-48}
 * 的 const 块里码是<b>分段</b>的 —— <b>5xxx ＝ 系统类</b>（{@code ErrorServerCommon 5001} …
 * {@code ErrorDelayMsg 5010}）· <b>1xxx/2xxx ＝ 业务类</b>（{@code ErrorUserExist 1001} …
 * {@code CodeServerBusy 1015} · {@code ErrorInputWrong 2001}）。
 * ⇒ <b>业务码 ≥ 5000 ⇒ 系统失败 ⇒ Go 会 RecordError</b>；否则（业务类）⇒ Go 不记 ⇒ 本类也不记。
 * <p>⚠️ 这条判据<b>必须落在真源上可求值</b>（码段是 Go 自己 const 块里的事实），
 * <b>不能</b>用「这是不是权限问题」「这个错误重不重要」这种语义化理由。
 *
 * <h2>🔴 本类<b>不给</b> {@code likeComment} 加 span</h2>
 * <p>Go 的 {@code like_comment_logic.go} <b>没有任何 otel / span 代码</b>
 * （实测 {@code tracer.Start} 命中数 = 0）⇒ 本类<b>不加</b>。
 * <b>这不是遗漏，是对齐</b>：Go 没有的，RAG 也不该凭空加
 * （那会让 trace 里的 comment span 数从 6 变 7，与真源不符）。
 * 该「6 而非 7」的事实由 {@code CommentSpanWiringTest} 双向钉死（有 span 的 / 没 span 的）。
 *
 * <h2>🔴 与共享层的关系（不需要动 {@code service/common}）</h2>
 * <p>本类直接用 {@link GlobalOpenTelemetry#getTracer(String)}，
 * 与共享层用的是<b>同一个 API</b>（Go 侧也是 {@code otel.Tracer(...)}）⇒
 * <b>comment-rpc 模块内即可完成</b>，不需要改共享模块，也不需要改 pom
 * （实测 {@code mvn dependency:list -pl service/comment/comment-rpc} ⇒
 * {@code io.opentelemetry:opentelemetry-api:1.55.0:compile} <b>已在 classpath 上</b>）。
 * <p>⇒ 子 span 会<b>正确挂到共享层的 SERVER span 之下</b>：
 * 共享层 {@code RpcInterceptor:429} 有 {@code span.makeCurrent()} ⇒ 当前上下文里有 SERVER span ⇒
 * {@code spanBuilder(...).startSpan()}（隐式取 {@code Context.current()}）自动成为它的子 span
 * ⇔ Go 侧 {@code tracer.Start(l.ctx, …)} 用的 {@code l.ctx} 也带着 SERVER span ⇒ <b>同构</b>。
 *
 * <h2>L4 idiomatization（AGENTS.md §3.2 三段式）</h2>
 * <p><b>比 Go 多做了什么</b>：Go 用 {@code ctx} 显式传递；Java 没有 ctx 参数这一说
 * ⇒ 用 {@link Scope} 模拟「span 进入当前上下文、退出时还原」，
 * 并用 {@link Supplier} 代替 Go 的「后续代码内联在同一个函数里」。
 * <b>语义等价论证</b>：Go 的 {@code defer span.End()} ⇔ 本类的 {@code finally}；
 * Go 的 {@code span.RecordError(err)} ⇔ 本类的 {@code span.recordException(e)}（<b>且只在系统失败时</b>，见上）；
 * Go 的 {@code span.SetStatus(codes.Error, err.Error())} ⇔ 本类的 {@code span.setStatus(ERROR, …)}（<b>只有 GetComment 有</b>）。
 * <p><b>已知差异（表达层）</b>：① Go 记的是<b>原始错误</b>（如 PG 的 err 文本），
 * 而 RAG 的业务异常在多数分支里<b>没有串联 cause</b>
 * （如 {@code throw CommentExceptions.dbSelectForOwner();} 不带 cause）⇒
 * ⇒ 事件<b>存在性一致</b>、<b>消息文本不同</b>（RAG 记的是 {@code ErrCode:5003, Errmsg:DB查询失败}）。
 * ② Go 的 {@code RecordError} 逐点分布（15 处），本类统一在「异常逃出时」判断一次
 * ⇒ 对<b>系统失败</b>等价；<b>业务失败</b>两侧都不记 ⇒ 也等价。
 */
public final class CommentSpans {

    /**
     * Go 出处：5 个 {@code Action-Comment-*} span 都写死 {@code otel.Tracer("comment-rpc")}
     * （{@code create_comment_logic.go:41} · {@code delete_comment_logic.go:37} ·
     * {@code manage_comment_logic.go:37} · {@code report_comment_logic.go:37} ·
     * {@code update_subject_state_logic.go:37}）。
     */
    public static final String TRACER_NAME = "comment-rpc";

    /**
     * Go 出处：{@code get_comment_logic.go:52} 写死 {@code otel.Tracer("comment.rpc")}
     * —— 🔴 <b>带点</b>，与上面 5 个<b>不是同一个</b> ⇒ 逐字保留，不统一。
     */
    public static final String TRACER_NAME_LIST = "comment.rpc";

    public static final String CREATE = "Action-Comment-Create";
    public static final String DELETE = "Action-Comment-Delete";
    public static final String MANAGE = "Action-Comment-Manage";
    public static final String REPORT = "Action-Comment-Report";
    public static final String UPDATE_SUBJECT_STATE = "Action-Comment-UpdateSubjectState";
    public static final String GET_COMMENT = "GetComment";

    // ---------- audit.* ：5 个 Action-Comment-* span 用的 9 个属性名 ----------

    public static final String ATTR_OPERATOR_ID = "audit.operator_id";
    public static final String ATTR_TARGET_TYPE = "audit.target_type";
    public static final String ATTR_TARGET_ID = "audit.target_id";
    public static final String ATTR_ROOT_ID = "audit.root_id";
    public static final String ATTR_PARENT_ID = "audit.parent_id";
    public static final String ATTR_COMMENT_ID = "audit.comment_id";
    public static final String ATTR_ACTION_TYPE = "audit.action_type";
    public static final String ATTR_REASON = "audit.reason";
    public static final String ATTR_TARGET_STATE = "audit.target_state";

    // ---------- 无前缀 ：GetComment 的 6 个属性名（与上面同名但**不同 key**）----------

    public static final String ATTR_LIST_TARGET_ID = "target_id";
    public static final String ATTR_LIST_ROOT_ID = "root_id";
    public static final String ATTR_LIST_PAGE = "page";
    public static final String ATTR_LIST_PAGE_SIZE = "page_size";
    public static final String ATTR_LIST_TARGET_TYPE = "target_type";
    public static final String ATTR_LIST_SORT_TYPE = "sort_type";

    /**
     * Go 侧 {@code errmsg.go} 的<b>系统类码段下界</b>：5xxx ＝ 系统类（DB / 雪花 / 序列化 / Kafka / 兜底），
     * 1xxx 与 2xxx ＝ 业务类（存在性 / 权限 / 输入）。
     *
     * <p>⇒ 「Go 会不会 {@code RecordError} 这个错误」的判据就是它落在这段的哪一边。
     */
    private static final int SYSTEM_ERROR_CODE_FLOOR = 5000;

    private CommentSpans() {
        // 纯静态工具，禁止实例化
    }

    /**
     * 在一个<b>手写 span</b> 里执行 {@code body}（<b>不</b>改 span 状态）。
     *
     * <p>用于 Go 侧<b>只有 {@code span.RecordError(err)}、没有 {@code SetStatus}</b> 的那 5 个
     * {@code Action-Comment-*} span ⇒ 失败时 Go 的状态保持 {@code UNSET}，本类也保持 {@code UNSET}。
     *
     * @param tracerName {@link #TRACER_NAME}（这 5 个 span 用它）
     * @param spanName   上面 5 个常量之一（<b>不要</b>给 {@code LikeComment} 用 —— Go 侧它没有 span）
     * @param attributes 设置属性；Go 在 {@code Start} 之后立刻设
     * @param body       业务体；抛出的异常按「系统失败才记」的规则处理后原样上抛
     */
    public static <T> T traced(String tracerName, String spanName,
                               Consumer<Span> attributes, Supplier<T> body) {
        return doTraced(tracerName, spanName, attributes, body, false);
    }

    /**
     * 在一个<b>手写 span</b> 里执行 {@code body}，且失败时<b>把状态置为 ERROR</b>。
     *
     * <p>只给 {@code GetComment} 用：Go 的 {@code get_comment_logic.go:85-86 / 101-102 / 110-111 / 117-118}
     * 在四个错误分支上<b>显式</b> {@code span.SetStatus(codes.Error, err.Error())} ⇒ 状态是 ERROR。
     *
     * @see #traced
     */
    public static <T> T tracedWithErrorStatus(String tracerName, String spanName,
                                               Consumer<Span> attributes, Supplier<T> body) {
        return doTraced(tracerName, spanName, attributes, body, true);
    }

    private static <T> T doTraced(String tracerName, String spanName,
                                  Consumer<Span> attributes, Supplier<T> body,
                                  boolean errorStatusOnFailure) {
        Tracer tracer = GlobalOpenTelemetry.getTracer(tracerName);
        Span span = tracer.spanBuilder(spanName).startSpan();
        try (Scope scope = span.makeCurrent()) {
            if (attributes != null) {
                attributes.accept(span);
            }
            return body.get();
        } catch (RuntimeException | Error e) {
            // 🔴 只在「系统失败」时记 —— Go 的 RecordError 精确落在 result="sys_fail" 的分支上。
            if (isSystemFailure(e)) {
                span.recordException(e);
                if (errorStatusOnFailure) {
                    span.setStatus(StatusCode.ERROR,
                            e.getMessage() == null ? e.getClass().getName() : e.getMessage());
                }
            }
            throw e;
        } finally {
            span.end();
        }
    }

    /**
     * 这个失败在 Go 侧会不会被 {@code span.RecordError(err)} 记下。
     *
     * <p><b>判据 = 业务码落在哪一段</b>（见类级说明）：{@code Rpc} 且码 ≥ 5000 ⇒ 系统类 ⇒ Go 会记；
     * {@code Rpc} 且码 &lt; 5000 ⇒ 业务类 ⇒ Go 不记；<b>不是 {@code Rpc} 的异常</b>
     * （如 {@code GetComment} 那条链上的裸 {@code RuntimeException} 与三个 sentinel）
     * ⇒ 一律按系统失败处理（Go 侧那些分支都是 {@code result = "sys_fail"}）。
     */
    public static boolean isSystemFailure(Throwable e) {
        if (e instanceof CommentExceptions.Rpc rpc) {
            return rpc.getBizCode() >= SYSTEM_ERROR_CODE_FLOOR;
        }
        return true;
    }

    /**
     * Go 的 proto 枚举 {@code String()} 语义（{@code protoimpl.X.EnumStringOf}）：
     * <b>已知值 ⇒ proto 值名</b>；<b>未知值 ⇒ 该数字的字符串</b>。
     *
     * <p>Go 侧的 {@code audit.action_type}（{@code in.ActionType.String()}）与
     * {@code audit.reason}（{@code in.Reason.String()}）用的就是它 ⇒
     * 「未知枚举值」在 Go 侧会写成 {@code "9"} 这样的<b>数字</b>，
     * 而 Java 侧 {@code value.name()} 会写成 {@code "UNRECOGNIZED"} ⇒ ⇒ 必须用本方法对齐。
     *
     * @param value     枚举实例（protobuf-java 的生成物）
     * @param rawNumber 该枚举的<b>原始编号</b>（用 {@code getXxxValue()} 取，<b>不要</b>用 {@code getXxx().getNumber()}：
     *                   proto3 未知值在生成物里是 {@code UNRECOGNIZED}，它的 {@code getNumber()} <b>抛异常</b>）
     */
    public static String enumName(Enum<?> value, int rawNumber) {
        if (value == null) {
            return "";
        }
        if ("UNRECOGNIZED".equals(value.name())) {
            // Go 的 EnumStringOf 对未知编号返回数字字符串
            return String.valueOf(rawNumber);
        }
        return value.name();
    }
}
