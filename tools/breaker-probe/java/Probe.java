package com.rag.common.protection;

import io.grpc.Status;

import java.io.PrintStream;

/**
 * RAG 侧熔断语义探针 —— 与 {@code ../go/main.go} 对拍。
 *
 * <h3>为什么放在 {@code com.rag.common.protection} 包里</h3>
 * <p>本探针要直接调用<b>包私有</b>的 {@link GoStyleRpcFailurePolicy} 与 {@link GoStyleBreaker}
 * （它们是包私有的 {@code final class}，正是"复刻件"的形态）。
 * ⇒ 探针必须与被测类<b>同包</b>，才能验到**真实实现**而不是"另写一份"（§3.3）。
 *
 * <h3>三组用例</h3>
 * <table border="1">
 *   <caption>探针分组</caption>
 *   <tr><th>组</th><th>内容</th><th>比对口径</th></tr>
 *   <tr><td>A</td><td>失败判据（Go 的 6 码白名单）逐条</td><td><b>必须逐条相同</b></td></tr>
 *   <tr><td>B</td><td>冷启动保护窗口（前 5 次必须全部放行）</td><td><b>必须逐字相同</b>（与随机源无关）</td></tr>
 *   <tr><td>C</td><td>大量失败后的拒绝率</td><td><b>只能按统计比对</b>（Go 的随机源用墙钟播种，不可复现）</td></tr>
 * </table>
 *
 * <h3>⚠️ A 组用<b>序号</b>而不是码名配对</h3>
 * <p>Go 的 {@code codes.Code.String()} 是 {@code "DeadlineExceeded"}，
 * Java 的 {@code Status.Code.name()} 是 {@code "DEADLINE_EXCEEDED"} ⇒ 拼写不同 ⇒ 必须按序号配对。
 *
 * <h3>⚠️ C 组的失败必须用「不可接受」的错误</h3>
 * <p>Go 侧用 {@code status.Error(codes.Internal, "boom")}（INTERNAL 在 6 码名单里 ⇒ 记 failure）；
 * 若用普通 {@code fmt.Errorf} ⇒ {@code status.Code}=UNKNOWN ⇒ <b>可接受</b> ⇒ Go 会记成 success
 * ⇒ 与 RAG 侧 {@code markFailure()} 不同源 ⇒ 分布不可比。
 */
public final class Probe {

    /** 顺序必须与 Go 侧 {@code allCodes} 逐条一致。 */
    private static final Status.Code[] ALL_CODES = {
            Status.Code.OK,
            Status.Code.CANCELLED,
            Status.Code.UNKNOWN,
            Status.Code.INVALID_ARGUMENT,
            Status.Code.DEADLINE_EXCEEDED,
            Status.Code.NOT_FOUND,
            Status.Code.ALREADY_EXISTS,
            Status.Code.PERMISSION_DENIED,
            Status.Code.RESOURCE_EXHAUSTED,
            Status.Code.FAILED_PRECONDITION,
            Status.Code.ABORTED,
            Status.Code.OUT_OF_RANGE,
            Status.Code.UNIMPLEMENTED,
            Status.Code.INTERNAL,
            Status.Code.UNAVAILABLE,
            Status.Code.DATA_LOSS,
            Status.Code.UNAUTHENTICATED,
    };

    private static void sleepOneBucket() {
        try {
            Thread.sleep(300L);     // Go 的桶宽 250ms（googlebreaker.go:12-21）
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted", e);
        }
    }

    public static void main(String[] args) {
        PrintStream out = System.out;

        // ── A 组：失败判据 ────────────────────────────────────────────────
        for (int i = 0; i < ALL_CODES.length; i++) {
            out.printf("A %d acceptable=%s%n", i, GoStyleRpcFailurePolicy.isAcceptable(ALL_CODES[i]));
        }

        // ── B 组：锚点（冷启动保护窗口）────────────────────────────────────
        GoStyleBreaker brk = new GoStyleBreaker("probe");
        StringBuilder prefix = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            if (brk.accept()) {
                prefix.append('A');
                brk.markSuccess();
            } else {
                prefix.append('D');
            }
        }
        out.printf("B prefix=%s%n", prefix);

        // ── C 组：分布（大量失败后的拒绝率）────────────────────────────────
        GoStyleBreaker brk2 = new GoStyleBreaker("probe2");
        for (int i = 0; i < 50; i++) {
            brk2.accept();
            brk2.markSuccess();
        }
        // ⚠️ 必须跨过一个桶边界（>250ms）：Go 的 history() 忽略当前未完成桶
        sleepOneBucket();
        for (int i = 0; i < 50; i++) {
            brk2.accept();
            brk2.markFailure();
        }
        sleepOneBucket();
        final int n = 20;
        int rejected = 0;
        for (int i = 0; i < n; i++) {
            // ⚠️ 测量请求用「成功」：放行时记 success ⇒ dropRatio 缓慢衰减（幅度小）；
            //    N=20 的循环两侧都是亚毫秒 ⇒ 不会跨桶 ⇒ 测到的拒绝率 ≈ 建状态时的 dropRatio。
            if (brk2.accept()) {
                brk2.markSuccess();
            } else {
                rejected++;
            }
        }
        out.printf("C rejected=%d total=%d rate=%.4f%n", rejected, n, (double) rejected / n);
    }
}
