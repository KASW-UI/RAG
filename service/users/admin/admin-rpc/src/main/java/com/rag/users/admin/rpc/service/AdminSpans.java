package com.rag.users.admin.rpc.service;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * admin-rpc 的 <b>手写 span</b>（DRIFT-ARPC-006）。
 *
 * <h2>修的是什么</h2>
 * <p>Go 侧 <b>13 个 logic 里有 8 个</b>在方法开头手写
 * {@code tracer := otel.Tracer("admin-rpc"); ctx, span := tracer.Start(l.ctx, "Action-Admin-…"); defer span.End()}，
 * 并打上 {@code audit.*} 属性。RAG 侧此前<b>一个都没有</b>
 * ⇒ 运维在 trace 里看不到 admin 域的审计维度。
 *
 * <h2>Go 出处（逐条，声明点 + 消费点）</h2>
 * <p>tracer 名 {@code admin-rpc} 与 span 名的<b>完整清单</b>（只有这 8 个，其余 5 个 logic
 * <b>本来就没有 span</b>，见下方表格）：
 * <table border="1">
 *   <tr><th>Go logic</th><th>span 名</th><th>属性</th></tr>
 *   <tr><td>loginlogic.go</td><td>{@code Action-Admin-Login}</td>
 *       <td>{@code audit.admin_username = in.Username}</td></tr>
 *   <tr><td>createadminlogic.go</td><td>{@code Action-Admin-CreateAdmin}</td>
 *       <td>{@code audit.admin_username = username}<b>（trim 之后）</b></td></tr>
 *   <tr><td>createinvitecodelogic.go</td><td>{@code Action-Admin-CreateInviteCode}</td>
 *       <td>{@code audit.operator_id}</td></tr>
 *   <tr><td>updateuserlogic.go</td><td>{@code Action-Admin-UpdateUser}</td>
 *       <td>{@code audit.operator_id}、{@code audit.target_uid}</td></tr>
 *   <tr><td>banuserlogic.go</td><td>{@code Action-Admin-BanUser}</td>
 *       <td>{@code audit.operator_id}、{@code audit.target_uid}</td></tr>
 *   <tr><td>unbanuserlogic.go</td><td>{@code Action-Admin-UnBanUser}</td>
 *       <td>{@code audit.operator_id}、{@code audit.target_uid}</td></tr>
 *   <tr><td>deleteuserlogic.go</td><td>{@code Action-Admin-DeleteUser}</td>
 *       <td>{@code audit.operator_id}、{@code audit.target_uid}</td></tr>
 *   <tr><td>resetuserpasswordlogic.go</td><td>{@code Action-Admin-ResetUserPassword}</td>
 *       <td>{@code audit.operator_id}、{@code audit.target_uid}</td></tr>
 * </table>
 *
 * <p>🔴 <b>另外 5 个 logic 在 Go 里<b>没有</b>任何 span / otel import</b>：
 * {@code getselflogic.go}、{@code getuserlogic.go}、{@code getuserlistlogic.go}、
 * {@code logoutlogic.go}、{@code updateselflogic.go}。
 * ⇒ 本类<b>不给这 5 个加 span</b>。
 * <b>这不是遗漏，是对齐</b>：Go 没有的，RAG 也不该凭空加
 * （那会让 trace 里的 admin span 数从 8 变 13，与真源不符）。
 * 该「8 而非 13」的事实由 {@code AdminSpanWiringTest} 双向钉死（有 span 的 / 没 span 的）。
 *
 * <p>⚠️ 台账此前把这条写成「Go 逐 Logic 打 audit 属性」，
 * 容易让人以为 <b>13 个都有</b>。真实计数是 <b>8 个 logic / 13 个 attribute</b>
 * —— 13 是属性条数，不是 logic 数。
 *
 * <h2>L4 idiomatization（§3.2 三段式）</h2>
 * <p><b>比 Go 多做了什么</b>：Go 用 {@code ctx} 显式传递（handler 收到 {@code timeoutCtx}），
 * Java 没有 ctx 参数这一说 ⇒ 用 {@link Scope} 模拟「span 进入当前上下文、退出时还原」，
 * 并用 {@code Supplier} 代替 Go 的「后续代码内联在同一个函数里」。
 * <b>语义等价论证</b>：Go 的 {@code defer span.End()} ⇔ 本类的 {@code finally}；
 * Go 的 {@code span.RecordError(err)} ⇔ 本类的 {@code span.recordException(e)} + 置 ERROR 状态。
 * <b>差异</b>：Go 的 {@code RecordError} 分布在具体错误分支上（本轮<b>未逐点复刻</b>，
 * 本类在<b>异常逃出</b>时统一记录）⇒ 属 IMPLEMENTATION DIFFERENCE，不影响 span 名与属性。
 *
 * <h2>⚠️ 与签字偏离 N-4 的关系（必须知情）</h2>
 * <p>Go 的 {@code audit.operator_id} <b>恒为 0</b>（读 {@code ctx["uid"]} 而 claim 名是 {@code userId}），
 * RAG 传<b>真实 uid</b> —— 这是已签字的 <b>N-4</b>。
 * ⇒ 补了 span 之后，<b>这个差异第一次真正落到 trace 数据里</b>：
 * 对拍/排障时两侧的 {@code audit.operator_id} 会不同，<b>属预期差异，不是回归</b>。
 *
 * <h2>为什么不需要动 {@code service/common}</h2>
 * <p>本类直接用 {@link GlobalOpenTelemetry#getTracer(String)}，
 * 与 {@code service/common} 的 {@code MqConsumer.java:81} 用的是<b>同一个 API</b>
 * （Go 侧也是 {@code otel.Tracer(...)}）⇒ <b>admin-rpc 模块内即可完成</b>，
 * 不需要改共享模块。台账此前写「需动 service/common 的 OTel 装配」是<b>过度保守</b>，已订正。
 */
public final class AdminSpans {

    /** Go 出处：13 个 logic 里全部写死 {@code otel.Tracer("admin-rpc")}。 */
    public static final String TRACER_NAME = "admin-rpc";

    public static final String LOGIN = "Action-Admin-Login";
    public static final String CREATE_ADMIN = "Action-Admin-CreateAdmin";
    public static final String CREATE_INVITE_CODE = "Action-Admin-CreateInviteCode";
    public static final String UPDATE_USER = "Action-Admin-UpdateUser";
    public static final String BAN_USER = "Action-Admin-BanUser";
    public static final String UNBAN_USER = "Action-Admin-UnBanUser";
    public static final String DELETE_USER = "Action-Admin-DeleteUser";
    public static final String RESET_USER_PASSWORD = "Action-Admin-ResetUserPassword";

    public static final String ATTR_OPERATOR_ID = "audit.operator_id";
    public static final String ATTR_TARGET_UID = "audit.target_uid";
    public static final String ATTR_ADMIN_USERNAME = "audit.admin_username";

    private AdminSpans() {
    }

    /**
     * 在一个<b>手写 span</b> 里执行 {@code body}。
     *
     * @param spanName   上面 8 个常量之一（<b>不要</b>给 Go 没打 span 的那 5 个方法用）
     * @param attributes 设置 {@code audit.*} 属性；Go 在 {@code Start} 之后立刻设
     * @param body       业务体；抛出的 {@link RuntimeException} 会被记进 span 后原样上抛
     */
    public static <T> T traced(String spanName, Consumer<Span> attributes, Supplier<T> body) {
        Tracer tracer = GlobalOpenTelemetry.getTracer(TRACER_NAME);
        Span span = tracer.spanBuilder(spanName).startSpan();
        try (Scope scope = span.makeCurrent()) {
            if (attributes != null) {
                attributes.accept(span);
            }
            return body.get();
        } catch (RuntimeException | Error e) {
            // Go 的 span.RecordError(err) + defer span.End()
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage() == null ? e.getClass().getName() : e.getMessage());
            throw e;
        } finally {
            span.end();
        }
    }
}