package com.rag.users.admin.rpc.service;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DRIFT-018 对齐的回归网：admin-rpc 侧 <b>12</b> 个业务方法必须都有 deadline 入口守卫。
 *
 * <p>⚠️ <b>口径：13 个业务方法里，{@code logout} 例外</b>（Go 侧 logoutlogic 无 ctx 预检 ⇒ 域内不设闸门；
 * 理由与出处见下方 {@code GUARDED} 字段的说明）⇒ ⇒ 本类守 <b>12</b> 个，logout 由
 * {@code AdminLogoutDeadlineHandoffTest} 单独守。
 *
 * <p><b>背景（2026-10-21）</b>：AGENTS.md §3.4.1 B-1 原写
 * 「当前 {@code user-rpc} 一处都未使用 {@code DeadlineState}」——
 * <b>该描述已过时</b>：{@code UserDomainService} 实有 <b>11 处</b> {@code checkDeadline()}
 * （代码注释标 DRIFT-018），而 {@code AdminDomainService} 此前<b>一处都没有</b>。本类补齐。
 *
 * <p><b>为什么是行为断言、不是数行数 / 扫字节码</b>：
 * 本条要防的回退是「有人删掉了 13 处里的某一处」。试过三种做法，各有各的坑：
 * <ul>
 *   <li>{@code grep} 源文件数出现次数 —— 会被 javadoc / 注释里的方法名干扰（假绿）；</li>
 *   <li>扫 class 常量池找方法名后 400 字节 —— 常量池顺序 ≠ 字节码顺序，不可靠；</li>
 *   <li>反射统计方法体内调用 —— 只要方法体<b>任意位置</b>调过就算数，
 *       而守卫若被挪到真正发起 IO <b>之后</b>，对第一步已无意义（仍是假绿）。</li>
 * </ul>
 * <b>行为断言没有这些洞</b>：把线程标记为 interrupt 后逐个调用，
 * 只有当守卫真的在<b>入口</b>、且在触碰任何依赖之前触发，才会抛
 * {@code DEADLINE_EXCEEDED} —— 挪到中途或挪到末尾都会让这条测试变红。
 */
class AdminDomainServiceDeadlineTest {

    /**
     * admin-api 对 admin-rpc 的 <b>12</b> 个业务调用，缺一即视为回退。
     *
     * <p>⚠️ <b>为什么清单里没有 {@code logout}</b>（2026-10-30 订正；上一版把它列在内 ⇒ 必假红）：
     * Go 侧 {@code service/user/admin/rpc/internal/logic/logoutlogic.go}（全文 46 行）
     * <b>没有 ctx 预检</b> —— 它直接做 {@code SetexCtx(l.ctx, …)}（<b>写绑 ctx</b>），
     * 失败落 {@code codes.Internal}「注销失败,Redis写入异常」（{@code logoutlogic.go:38-42}）。
     * ⇒ RAG 侧若在 logout 入口设 deadline 闸门，handler 级码会是 {@code DEADLINE_EXCEEDED}(1015)，
     * 与 Go 的 {@code INTERNAL}(5001) <b>不同形</b> ⇒ ⇒ 域内闸门已撤（依据见
     * {@code AdminDomainService.logout} 的 javadoc「L4 期限感知上移到共享层」段）。
     * ⇒ 该条<b>另</b>由 {@code AdminLogoutDeadlineHandoffTest} 守门（域侧不再抢先拦 ＋ handler 级码映射）。
     */
    private static final List<String> GUARDED = List.of(
            "login", "getSelf", "updateSelf", "createAdmin",
            "createInviteCode", "banUser", "unBanUser", "getUser",
            "getUserList", "updateUser", "deleteUser", "resetUserPassword");

    private AdminDomainService svc;

    @BeforeEach
    void setUp() {
        // 依赖全传 null：守卫必须比任何依赖解引用都早，否则下面测不到它
        svc = new AdminDomainService(null, null, null, null, null, null, null);
    }

    @AfterEach
    void clearInterrupt() {
        // 必须清：JUnit 同线程跑所有方法，残留的 interrupt 标记会让别的用例假红
        Thread.interrupted();
    }

    @Test
    @DisplayName("DRIFT-018：admin-rpc 的 12 个业务方法入口都拦下已过期请求（logout 例外 · 见 GUARDED 说明）")
    void everyBusinessMethodRejectsInterruptedThreadAtEntry() {
        List<String> notGuarded = new ArrayList<>();

        for (String name : GUARDED) {
            Method m = findBusinessMethod(name);
            if (m == null) {
                notGuarded.add(name + "（方法不存在）");
                continue;
            }
            Thread.currentThread().interrupt();
            try {
                invokeGuarded(m);
                notGuarded.add(name + "（未拦下）");
            } catch (StatusRuntimeException e) {
                assertThat(Status.fromThrowable(e).getCode())
                        .as("%s 应抛 DEADLINE_EXCEEDED", name)
                        .isEqualTo(Status.Code.DEADLINE_EXCEEDED);
            } finally {
                Thread.interrupted();
            }
        }

        assertThat(notGuarded)
                .as("这些方法入口没拦下已过期请求（admin-rpc 此前零覆盖；user-rpc 有 11 处）")
                .isEmpty();
    }

    private Method findBusinessMethod(String name) {
        for (Method m : AdminDomainService.class.getDeclaredMethods()) {
            if (m.getName().equals(name)) return m;
        }
        return null;
    }

    private void invokeGuarded(Method m) {
        Object[] args = new Object[m.getParameterCount()];
        Class<?>[] types = m.getParameterTypes();
        for (int i = 0; i < args.length; i++) {
            // 基本类型不能传 null，用 0；引用类型传 null 即可（守卫不该解引用它们）
            args[i] = types[i].isPrimitive() ? (Object) Integer.valueOf(0) : null;
        }
        try {
            m.setAccessible(true);
            m.invoke(svc, args);
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) throw re;
            if (cause instanceof Error err) throw err;
            throw new IllegalStateException(cause);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * 反向：未过期、无 interrupt 时守卫必须是 no-op，否则会打断正常请求。
     *
     * <p><b>断言为什么是「不是 DEADLINE_EXCEEDED」而不是「不抛异常」</b>：
     * {@code getSelf} 内部是 {@code try { findOneAdminByUid(uid) } catch (Exception e) {
     * throw Status.INTERNAL... }}，而本测试的 mapper 传的是 {@code null}
     * ⇒ 会 NPE 并被转成 {@code Status.INTERNAL}（它也是 {@code StatusRuntimeException}）。
     * 所以「抛了 gRPC 异常」不能证明守卫有问题，<b>只有状态码能证明</b>：
     * 若是 {@code INTERNAL} ⇒ 守卫放行了、请求走到了业务层，正是我们要的。
     */
    @Test
    @DisplayName("DRIFT-018 反向：未过期时守卫放行（走到业务层抛 INTERNAL，而非 DEADLINE_EXCEEDED）")
    void freshRequestIsNotRejected() {
        assertThatThrownBy(() -> svc.getSelf(1L))
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(t -> assertThat(Status.fromThrowable(t).getCode())
                        .as("未过期时守卫应放行；走到 mapper 后 NPE 被转成 INTERNAL 是预期的")
                        .isEqualTo(Status.Code.INTERNAL));
    }
}