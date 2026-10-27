package com.rag.follow.follow.rpc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 🔴 **共享层 artifact 新鲜度守门**（G1 lane · 2026-10-28）
 *
 * <p><b>它守的是什么</b>：本模块解析到的 {@code service/common} artifact
 * <b>必须真的携带共享层那两个自动配置</b>
 * （排序桥 + Redis 客户端自动配置）⇒ ⇒ 否则「域内副本已删 ⇒ 共享层那份真生效」在**产物层**就不成立。
 *
 * <p><b>为什么这条必须存在</b>（G2 回执②实测 · 本域已独立复核）：
 * <ul>
 *   <li>真源侧机制：任何 <b>单模块</b> 构建（{@code mvn -pl <module> ...} <b>不带 {@code -am}</b>）
 *       ⇒ {@code service/common} 不在 reactor ⇒ 依赖从 {@code ~/.m2} 取
 *       ⇒ 拿到的是**陈旧**的 {@code common-0.0.1-SNAPSHOT.jar}；</li>
 *   <li>本域实测（2026-10-28 01:3x）：{@code ~/.m2} 里那份是
 *       <b>215 837 B / 2026-10-27 12:00</b> ⇒ 它的
 *       {@code AutoConfiguration.imports} <b>只有 2 行</b>（otel + json content type）
 *       ⇒ <b>排序桥 0 行</b> ⇒ <b>连 class 文件都没有</b>
 *       ⇒ ⇒ 单模块构建出来的产物里<b>没有共享层那份</b>；</li>
 *   <li>⇒ ⇒ 那种构建出来的 fat jar 里嵌的是旧 common ⇒
 *       Boot 4 下 MyBatis-Plus 早于 DataSource 求值 ⇒ <b>进程启动即挂</b>
 *       （{@code Property 'sqlSessionFactory' or 'sqlSessionTemplate' are required}）。</li>
 * </ul>
 *
 * <p><b>为什么本类不读 {@code target/*.jar}</b>（一个实测踩过的坑）：
 * {@code service/common/target/common-0.0.1-SNAPSHOT.jar} 在**只跑 {@code test} 阶段**时
 * <b>不会被重建</b> ⇒ ⇒ 实测它停在 <b>2026-10-26 23:36</b>（旧于本仓最近一次共享层落码）
 * ⇒ ⇒ <b>拿它当证据 = 拿 stale 产物当证据</b> ⇒ ⇒ 本类读的是
 * <b>「本模块真正解析到的那个 artifact」</b>（由 classloader 的 code source 给出）⇒ 那是**当次构建**的产物。
 *
 * <p><b>期望值必须是独立字面量</b>（AGENTS.md §3.3 的同款纪律）：
 * 断言里的 FQCN <b>不许</b>从被测代码 import 过来 ⇒ ⇒ 共享层改包名时本守门会<b>如实转红</b>，
 * 而不是跟着一起变、继续绿。
 */
class FollowRpcSharedLayerArtifactFreshnessGateTest {

    /** 共享层那份 MP 排序桥（独立字面量 ⇒ 不从被测代码 import）。 */
    private static final String ORDERING_BRIDGE_FQCN =
            "com.rag.common.persistence.MybatisPlusOrderingBridgeAutoConfiguration";

    /** 共享层那份 Redis 客户端自动配置（独立字面量）。 */
    private static final String REDIS_AUTOCONFIG_FQCN =
            "com.rag.common.redis.GoStyleRedisClientAutoConfiguration";

    /** 域内那份排序桥的 FQCN（收编前住在本域内）—— 现在必须**不在**。 */
    private static final String DOMAIN_SIDE_ORDERING_BRIDGE_FQCN =
            "com.rag.follow.follow.rpc.config.MybatisPlusOrderingBridgeAutoConfiguration";

    private static final String IMPORTS_RESOURCE =
            "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";

    @Test
    @DisplayName("🔴 本模块解析到的共享层 artifact 必须携带「排序桥 + Redis 自动配置」两个 FQCN")
    void resolvedSharedLayerArtifactMustCarryBothAutoConfigurations() {
        for (String fqcn : new String[] {ORDERING_BRIDGE_FQCN, REDIS_AUTOCONFIG_FQCN}) {
            URL artifact = resolvedArtifactOf(fqcn);
            String imports = readImportsFrom(artifact);
            assertTrue(imports.contains(fqcn),
                    "本模块解析到的共享层 artifact（" + artifact + "）里【没有】" + fqcn + "。"
                            + "\n实测读到的 imports 内容：\n" + imports
                            + "\n⇒ ⇒ 极可能拿到的是 ~/.m2 里的**陈旧** common jar"
                            + "（本域实测：那份是 215 837 B / 2026-10-27 12:00，imports 只有 2 行、排序桥 0 行）"
                            + "\n⇒ ⇒ **修法 = 构建命令加 `-am`**（让 service/common 进 reactor），"
                            + "或先 `mvn -pl service/common install` 再构建本模块");
        }
    }

    @Test
    @DisplayName("🔴 域内那份排序桥必须不在源码里（收编真的发生了，不是两份叠加）")
    void domainSideOrderingBridgeCopyMustNotExist() {
        Path moduleRoot = moduleRoot();
        Path javaRoot = moduleRoot.resolve("src/main/java");
        assertTrue(Files.isDirectory(javaRoot), "找不到本模块源码目录：" + javaRoot);

        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(javaRoot)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String body = readString(p);
                if (body.contains(DOMAIN_SIDE_ORDERING_BRIDGE_FQCN)
                        || body.contains("class MybatisPlusOrderingBridgeAutoConfiguration")) {
                    offenders.add(moduleRoot.relativize(p).toString());
                }
            }
        } catch (IOException e) {
            fail("扫本模块源码失败：" + e);
        }
        assertTrue(offenders.isEmpty(),
                "域内那份排序桥又出现了（" + offenders + "）⇒ ⇒ 两份桥会同时注册 ⇒ "
                        + "共享层那份后续的改动在本域**静默不生效** ⇒ 收编等于没做。");

        // 域内自己的 imports 清单（若存在）也不该再登记那份
        Path importsFile = moduleRoot.resolve("src/main/resources").resolve(IMPORTS_RESOURCE);
        if (Files.isRegularFile(importsFile)) {
            String body = readString(importsFile);
            assertFalse(body.contains(DOMAIN_SIDE_ORDERING_BRIDGE_FQCN),
                    "域内 imports 清单仍登记着域内那份排序桥：" + importsFile);
        }
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /** 解析某个类**当次**被加载自哪个 artifact（jar 或 classes 目录）。 */
    private static URL resolvedArtifactOf(String fqcn) {
        try {
            Class<?> cls = Class.forName(fqcn);
            URL loc = cls.getProtectionDomain().getCodeSource().getLocation();
            if (loc == null) {
                fail("拿不到 " + fqcn + " 的 code source ⇒ 本条断言无意义");
            }
            return loc;
        } catch (ClassNotFoundException e) {
            fail("共享层的 " + fqcn + " **不在本模块解析到的 classpath 上** ⇒ "
                    + "极可能拿到的是 ~/.m2 里的**陈旧** common jar（本域实测：那份的 imports 只有 2 行、"
                    + "排序桥 0 行、连 class 都没有）⇒ ⇒ **修法 = 构建命令加 `-am`**"
                    + "（让 service/common 进 reactor），或先 `mvn -pl service/common install`。");
            throw new AssertionError("unreachable");
        }
    }

    /** 从「jar」或「classes 目录」里读出那份 {@code AutoConfiguration.imports}。 */
    private static String readImportsFrom(URL artifact) {
        try {
            if ("file".equals(artifact.getProtocol())) {
                Path p = Path.of(URI.create(artifact.toString()));
                if (Files.isDirectory(p)) {
                    Path f = p.resolve(IMPORTS_RESOURCE);
                    return Files.isRegularFile(f) ? readString(f) : "";
                }
                try (ZipFile zip = new ZipFile(p.toFile())) {
                    ZipEntry entry = zip.getEntry(IMPORTS_RESOURCE);
                    if (entry == null) {
                        return "";
                    }
                    try (InputStream in = zip.getInputStream(entry)) {
                        return new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    }
                }
            }
            // 非 file 协议（理论上不会发生）⇒ 退回到 classloader 资源流
            try (InputStream in = FollowRpcSharedLayerArtifactFreshnessGateTest.class
                    .getClassLoader().getResourceAsStream(IMPORTS_RESOURCE)) {
                return in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new IllegalStateException("读 artifact 内容失败：" + artifact, e);
        }
    }

    private static Path moduleRoot() {
        try {
            URL loc = FollowRpcSharedLayerArtifactFreshnessGateTest.class
                    .getProtectionDomain().getCodeSource().getLocation();
            Path p = Path.of(URI.create(loc.toString())).toAbsolutePath();
            // target/test-classes ⇒ 上溯两级到模块根
            return p.getParent() != null && p.getParent().getParent() != null
                    ? p.getParent().getParent()
                    : p;
        } catch (Exception e) {
            throw new IllegalStateException("定位模块根失败", e);
        }
    }

    private static String readString(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("读文件失败：" + p, e);
        }
    }
}
