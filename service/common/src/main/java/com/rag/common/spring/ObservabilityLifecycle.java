package com.rag.common.spring;

import com.rag.common.observability.RpcInterceptor;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

/**
 * <p><b>Go 出处</b>：{@code service/common/observability/observability.go:19-30}
 * （观测层的常量与阈值口径）＋ {@code service/common/observability/http.go:36}（观测层的工作线程池来源）
 * ⇒ ⇒ Go 侧由<b>进程生命周期</b>托管、无显式关闭钩子 ⇒ ⇒ 本类是 <b>表达层补齐</b> ⇒ <b>无 Go 对应物</b>。
 * M9 修复：给观测层的静态线程池补上真正的关闭钩子。
 *
 * <p><b>背景</b>：相关类的 javadoc 都写着
 * 「业务模块在 {@code @PreDestroy} 里显式调 {@link #shutdown()}」——
 * 但全仓<b>没有任何一个调用者</b>，那段 javadoc 描述的是一个不存在的契约。
 * 两个池分别是：
 * <ul>
 *   <li>{@code HttpMiddleware.HTTP_EXECUTOR}（cached pool，线程名 {@code obs-http-worker}）
 *       —— <b>2026-10-27 起改由 {@link HttpPoolLifecycle} 负责</b>（见下）；</li>
 *   <li>{@code RpcInterceptor.TIMEOUT_SCHEDULER} + {@code RpcInterceptor.HANDLE_EXECUTOR}
 *       —— <b>本类负责</b>。</li>
 * </ul>
 *
 * <p>因为线程都 setDaemon(true)，不关也不会阻止 JVM 退出，所以问题不显形；
 * 但 Spring 的 graceful shutdown 与「容器已关闭、观测层线程池仍在收尾」的时序会错开，
 * 优雅停机期间可能丢最后几个 span。
 *
 * <p><b>为什么用 Spring Bean 而不是继续让业务各写一遍</b>：这两个池是 static 的，
 * 每模块各写一份 {@code @PreDestroy} 只是把同一个调用复制 N 份、漏一份就静默失效。
 * 收口到本 Bean 后，<b>凡是注册了本 Bean 的模块都自动生效</b>。
 *
 * <p><b>注册方式</b>（两类模块走两条路，<b>互斥</b>，不要同时做）：
 * <ul>
 *   <li>全包扫描 {@code com.rag.common} 的 <b>api</b> 模块（10 个：user/admin/article/hot/like/
 *       message/task/follow/favorite/comment 各自的 api）—— 扫到即生效；</li>
 *   <li>rpc 进程<b>一律不扫</b> {@code com.rag.common}（会把 servlet 类拖进来），
 *       必须在 {@code @Import} 列表里显式带上本类。</li>
 * </ul>
 * ⚠️ 本注释此前把 {@code message-rpc} 列为「全包扫描」类模块，属失准：
 * {@code MessageRpcApplication} 实际是 <b>裸 {@code @SpringBootApplication}</b> + {@code @Import} 路线
 * （该文件第 64 行注释亦如此自述），与 api 模块不同路。
 * 同理，{@code ObservabilityProperties} 仍带 {@code @Component} 也不构成双 Bean ——
 * 扫描路径（api）与 {@code @Import} 路径（rpc）互斥，同一模块只会注册一次。
 *
 * <p>幂等：{@code shutdown()} 是 {@code shutdownNow()}，重复调用安全。
 *
 * <h2>🔴 为什么 HTTP 池<b>不能</b>在这里关（2026-10-27 · G1 派单 #8 · PITFALL-FOLLOW-005）</h2>
 *
 * <p><b>本类此前第一句是 {@code HttpMiddleware.shutdown();}</b> ⇒ 在<b>纯 gRPC 进程</b>里
 * 关闭路径会抛 {@code NoClassDefFoundError: jakarta/servlet/Filter}。根因链（逐条可核）：
 * <ol>
 *   <li>{@code HttpMiddleware} 引用 {@code jakarta.servlet.Filter}
 *       —— 不只是 import：{@code newHttpMiddleware(...)} 的<b>返回类型</b>是 {@code Filter}，
 *       且方法体里有 {@code new Filter() { … }}（<b>匿名类</b>）；</li>
 *   <li>⇒ 该类<b>被加载时</b>，字节码验证器就要确认「这个匿名类是 {@code Filter} 的实现」
 *       ⇒ <b>必须先加载 {@code jakarta.servlet.Filter}</b> ⇒ 缺类即 {@code NoClassDefFoundError}；</li>
 *   <li>纯 gRPC 进程的<b>运行时</b> classpath 上<b>没有</b> servlet-api
 *       （实测 {@code mvn dependency:list}：只有 {@code spring-web}，
 *       <b>无</b> {@code jakarta.servlet-api} / {@code tomcat-embed-core}）;</li>
 *   <li>⇒ 纯 gRPC 进程里 {@code HttpMiddleware} <b>从不加载</b>（没有 servlet filter 要注册），
 *       它的首次加载<b>恰好落在</b> {@code @PreDestroy} 这条关闭路径上 ⇒ 炸在关停瞬间。</li>
 * </ol>
 *
 * <p>⚠️ <b>连带损伤</b>：本方法<b>不吞异常</b> ⇒ 第一句抛出 ⇒ <b>第二句 {@code RpcInterceptor.shutdown()} 根本不会执行</b>
 * ⇒ RPC 池<b>没被回收</b>，且真正的关闭原因被这行 {@code NoClassDefFoundError} 掩盖。
 *
 * <p><b>修法</b>：把 HTTP 池的回收挪进 {@link HttpPoolLifecycle}
 * （{@code @ConditionalOnWebApplication(type = SERVLET)}）⇒ 只有 servlet 进程才会去加载
 * {@code HttpMiddleware} ⇒ 纯 gRPC 进程的关闭路径<b>不再碰任何 web 类型</b>。
 *
 * <p><b>默认行为零变化</b>：api 侧两个池仍然都被回收（HTTP 池改由新 Bean 关）；
 * rpc 侧的 HTTP 池<b>从未被创建过</b>（{@code HTTP_EXECUTOR} 是 {@code HttpMiddleware} 的 static 字段，
 * 而该类在 rpc 进程里从不加载）⇒ 本来就没什么可关 ⇒ 语义等价。
 *
 * <p>⚠️ <b>本类不可改名、不可移除</b>：10 个 rpc 组件里有 <b>9 个</b>在
 * {@code @Import(...)} 列表里点名了本类（{@code hot-rpc} 没有 ⇒ 它不受影响）
 * ⇒ 改名/移除要动 9 个<b>域侧</b>文件 ⇒ 越共享层写权 ⇒ 只能改本类<b>内部</b>。
 */
@Component
public class ObservabilityLifecycle {

    /**
     * Spring 容器关闭时回收<b>RPC 侧</b>的观测层线程池。
     *
     * <p>⚠️ <b>本方法只碰 {@link RpcInterceptor}</b>（该类零 servlet / 零 {@code HttpMiddleware} 引用）
     * ⇒ 在<b>纯 gRPC 进程</b>里也是安全的。
     *
     * <p>HTTP 侧的池由 {@link HttpPoolLifecycle} 负责（只有 servlet 进程才注册那个 Bean）
     * ⇒ 详见本类 javadoc 的「为什么 HTTP 池不能在这里关」。
     *
     * <p>🔴 <b>单池口径</b>（2026-10-27 订正 · G1 反馈单）：本方法<b>只关一个池</b>
     * （{@link RpcInterceptor}）⇒ ⇒ 原先那句「即使某个池关闭出错，也要保证<b>其他池</b>
     * 被尝试关闭，因此调用各自独立」<b>已随 HTTP 池拆出而过期</b>
     * （HTTP 池改由 {@link HttpPoolLifecycle} 关 ⇒ <b>不在本方法里</b>）
     * ⇒ ⇒ 现在只剩「本方法不吞异常、异常向上抛」这一条
     * ⇒ ⇒ ⚠️ <b>读者不要误以为这里仍有「多个池各自独立关闭」的异常隔离保证</b>
     * —— 现在的形态是「一个池、一次调用、异常直接上抛」
     * ⇒ ⇒ 若将来再加池进来，<b>必须同时把这段改回多池口径</b>（否则又是一处过期 javadoc）。
     */
    @PreDestroy
    public void shutdown() {
        RpcInterceptor.shutdown();
    }
}
