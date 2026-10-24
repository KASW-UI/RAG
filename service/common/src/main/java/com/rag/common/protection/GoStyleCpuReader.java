package com.rag.common.protection;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;

/**
 * CPU 采样 —— Go {@code core/stat.CpuUsage()}（整机千分比）的 Java 复刻。
 *
 * <h3>Go 出处</h3>
 * <pre>
 *   core/load/sheddingstat.go:64   c := stat.CpuUsage()          // 降载统计日志里的 cpu
 *   core/load/adaptiveshedder.go:41-43
 *       systemOverloadChecker = func() bool { return stat.CpuUsage() &gt; threshold }
 * </pre>
 *
 * <p>Go 的 {@code stat.CpuUsage()} 在 Linux 下读 {@code /proc/stat} 的<b>整机</b> CPU，
 * 返回 <b>千分比</b>（0..1000，900 = 90%）。
 *
 * <p>⚠️ <b>不可用时返回 -1</b>，上层据此 <b>fail-open</b>（读不到 ⇒ 视作未过载 ⇒ 不降载），
 * 与 Go 侧 {@code systemOverloadChecker} 返回 false 的分支一致。
 */
final class GoStyleCpuReader {

    /** 不可用（对应 Go 侧读不到 /proc/stat 的情形）。 */
    static final int UNAVAILABLE = -1;

    private static final int PERMILLE = 1000;

    private static final OperatingSystemMXBean OS_BEAN = ManagementFactory.getOperatingSystemMXBean();

    private GoStyleCpuReader() {
    }

    /** 整机 CPU 千分比（Go {@code stat.CpuUsage()} 口径），不可用时 {@link #UNAVAILABLE}。 */
    static int systemCpuPermille() {
        double load = systemCpuLoad();
        return load < 0D ? UNAVAILABLE : (int) Math.round(load * PERMILLE);
    }

    /** 仅本 JVM 进程的 CPU 千分比（偏离 Go，需显式配置）。 */
    static int processCpuPermille() {
        double load = processCpuLoad();
        return load < 0D ? UNAVAILABLE : (int) Math.round(load * PERMILLE);
    }

    /**
     * ⚠️ {@code getSystemCpuLoad()} <b>不在</b> {@link OperatingSystemMXBean} 上，
     * 而在 {@code com.sun.management.OperatingSystemMXBean} 这个 JDK 扩展接口上。
     * 若运行时不提供该扩展（非 HotSpot 系），返回 {@code -1} ⇒ 上层 fail-open。
     */
    private static double systemCpuLoad() {
        if (OS_BEAN instanceof com.sun.management.OperatingSystemMXBean sun) {
            return sun.getSystemCpuLoad();
        }
        return -1D;
    }

    private static double processCpuLoad() {
        if (OS_BEAN instanceof com.sun.management.OperatingSystemMXBean sun) {
            return sun.getProcessCpuLoad();
        }
        return -1D;
    }
}
