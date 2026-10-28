package com.rag.message.message.rpc.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 🔴 **第二十一轮 · message 域「真容器固定宿主口」的段覆盖守门**（源扫型 · 无容器 · 秒级）。
 *
 * <h2>🔴 它钉住什么</h2>
 * <p>全局段表 §4.10.19.3 把 **OS 临时段 `49152–65535`** 标为**禁占**
 * （= Testcontainers 的**随机宿主口**所在段 ⇒ 与 `tools/difftest/ddl-parity.sh` 的固定口
 * `55560–55569` 同属一个池子）⇒ ⇒ **message 域的真容器必须钉在域段 `22820–22849` 内**。
 * <p>⇒ ⇒ 本守门**逐个测试类**核：① 凡声明了 Testcontainers 容器的类 ⇒ **必须**有一个
 * {@code FIXED_HOST_PORT} 常量；② 该常量**必须**落在本域段内；③ **全域不得重号**
 * （重号 ⇒ 两个并发 fork 抢同一个宿主口 ⇒ 撞车 ⇒ 与「轮询分配」同款故障）。
 *
 * <h2>🔴 为什么用**源扫**而不是「运行时看 getMappedPort()」</h2>
 * <p>运行时读端口需要**把每个容器都起一遍**（本域 6 个容器 ⇒ 6 次 PG/Redis 启动）⇒ ⇒ 慢且重；
 * 而「这个类声明了容器却没绑口」这个漂移**在源码上就是可见的** ⇒ ⇒ 源扫是**判据更硬、成本更低**的那一层。
 * <p>⇒ ⇒ **两层不重复**：机制层由 {@code MessagePostgresFixedHostPortGateTest}（真容器 · 钉「映射口 == 固定口」）
 * 负责；覆盖层由本类（源扫 · 钉「每个容器都绑了、都在段内、都不重号」）负责。
 *
 * <h2>🔴 扫描范围</h2>
 * <p><b>本模块</b>的 {@code src/test/java} 递归下的全部 {@code .java} 文件（surefire 的工作目录 = 模块 basedir）⇒ ⇒
 * 只管本域 ⇒ ⇒ **不外推到别的域**（别的域的段由别的域自己守）。
 */
class MessageContainerFixedHostPortSegmentGateTest {

    /** 容器声明的标记（**带 `new`** ⇒ 不会把 javadoc / import 里的类型名算进来）。 */
    private static final Pattern CONTAINER_DECL =
            Pattern.compile("new\\s+(?:PostgreSQLContainer|GenericContainer)<>");

    /** 本类声明的固定宿主口常量。 */
    private static final Pattern FIXED_PORT =
            Pattern.compile("FIXED_HOST_PORT\\s*=\\s*(\\d+)");

    @Test
    @DisplayName("① message 域每个真容器类都必须绑【本域段 22820–22849 内】的宿主口，且【全域不重号】")
    void everyContainerBindsADedicatedPortInsideTheDomainSegment() throws IOException {
        Path root = Paths.get("src", "test", "java");
        assertThat(root)
                .as("🔴 扫描根必须是本模块的 src/test/java（surefire 工作目录 = 模块 basedir）")
                .exists();

        Map<Integer, List<String>> byPort = new LinkedHashMap<>();
        List<String> unbound = new ArrayList<>();
        int containerCount = 0;
        int fileCount = 0;

        try (Stream<Path> walk = Files.walk(root)) {
            for (Path f : walk.filter(p -> p.toString().endsWith(".java")).toList()) {
                String src = Files.readString(f, StandardCharsets.UTF_8);
                int decls = count(CONTAINER_DECL, src);
                if (decls == 0) {
                    continue;
                }
                fileCount++;
                containerCount += decls;

                List<Integer> ports = new ArrayList<>();
                Matcher m = FIXED_PORT.matcher(src);
                while (m.find()) {
                    ports.add(Integer.parseInt(m.group(1)));
                }

                if (ports.size() != decls) {
                    unbound.add(f.getFileName() + "（声明 " + decls + " 个容器 · 固定口常量 "
                            + ports.size() + " 个）");
                    continue;
                }
                for (int p : ports) {
                    byPort.computeIfAbsent(p, k -> new ArrayList<>()).add(f.getFileName().toString());
                }
            }
        }

        assertThat(unbound)
                .as("🔴 凡是声明了 Testcontainers 容器的测试类，都必须声明【一个】`FIXED_HOST_PORT` 常量"
                        + " 并用 `RealContainerPort.withFixedHostPort(...)` 绑上去"
                        + " ⇒ ⇒ 一个文件只许一个容器（否则常量重名）"
                        + " ⇒ ⇒ 未绑口的类：")
                .isEmpty();

        assertThat(containerCount)
                .as("🔴 扫描到的容器数不能是 0（= 扫描面没生效 ⇒ 守门恒绿 = 假绿）")
                .isGreaterThan(0);

        for (Map.Entry<Integer, List<String>> e : byPort.entrySet()) {
            assertThat(e.getValue())
                    .as("🔴 宿主口 " + e.getKey() + " 被多个类占用：" + e.getValue()
                            + " ⇒ ⇒ 重号 ⇒ 两个并发 fork 抢同一个口 ⇒ 撞车"
                            + " ⇒ ⇒ 每个容器类必须一个**独占**的固定口")
                    .hasSize(1);
            assertThat(RealContainerPort.inSegment(e.getKey()))
                    .as("🔴 宿主口 " + e.getKey() + " 不在 message 域段 "
                            + RealContainerPort.SEGMENT_START + "–" + RealContainerPort.SEGMENT_END
                            + " 内 ⇒ ⇒ 全局段表 §4.10.19.3：49152–65535 是 OS 临时段，禁占")
                    .isTrue();
        }

        System.out.printf("PROBE segment-guard files=%d containers=%d distinctPorts=%d segment=[%d,%d]%n",
                fileCount, containerCount, byPort.size(),
                RealContainerPort.SEGMENT_START, RealContainerPort.SEGMENT_END);
    }

    private static int count(Pattern p, String s) {
        Matcher m = p.matcher(s);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }
}
