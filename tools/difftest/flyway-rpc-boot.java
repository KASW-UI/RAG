// =============================================================================
// flyway-rpc-boot.java —— **L11 lane**：真 rpc 进程的 Flyway 并发驱动
// =============================================================================
//
// 【为什么需要它】
//   L4 lane 的 `flyway-concurrent-boot.java` 起的是「只 import DataSource + Flyway
//   两个 auto-configuration」的**最小 Spring 上下文** ⇒ 它证明的是
//   「Flyway 引擎 + 模块 yaml」这一层，**不是**「真实 rpc 服务同时起」。
//   ⇒ L11 的边界 ① 要求**真 rpc 进程**（真 gRPC server + 真 Flyway + 真连同一个库）。
//
//   而「真 rpc 进程」有两个额外难点：
//   ① **真 jar 起不来**：5/7 个 rpc 模块的 yaml 写的是 `discovery://<name>`（两斜杠），
//      而 devh 的 `DiscoveryClientResolverFactory` 要求 `discovery:///<name>`（三斜杠，
//      见 `newNameResolver`：`targetUri.getPath()` 长度必须 > 1 ⇒ 两斜杠形式 path 是空串）
//      ⇒ **与 Nacos 在不在线无关**（检查发生在任何网络调用之前）
//      ⇒ `grpcClientBeanPostProcessor` 先炸 ⇒ **连 Flyway 都跑不到**。
//      ⇒ 本驱动把 gRPC client 的 target **在启动参数里改回三斜杠**，
//         **不改任何生产 yaml**（本 lane 无 `service/**` 写权）。
//   ② **真 jar 缺依赖**：`like-rpc` / `article-rpc` 的 `grpc-inprocess` 是 `test` scope
//      ⇒ 不在 fat jar 里 ⇒ 真启动炸 `NoClassDefFoundError: io/grpc/inprocess/InProcessChannelBuilder`。
//      ⇒ 本驱动用 **classpath 模式**（`target/classes` + 依赖 + `grpc-inprocess` jar）
//         起这两个模块 ⇒ **不改 pom**（无写权）。
//
// 【它做什么】
//   `migrate --module=<模块> --app=<真实 Application 全限定名> --start-at=<epochMs>
//            --out=<file> [--extra-locations=...] [--grpc-port=<端口>] [--k=v ...]`
//      ⇒ 等到 `--start-at` 这个墙钟瞬间才起 Spring 上下文 ⇒ 多个进程**真并发**起
//      ⇒ **`--app` 指向该模块自己的 `*RpcApplication`** ⇒ 起的是**真 rpc 进程**
//         （真 gRPC server + 真 Flyway + 真连库），**不是**「最小 Spring 上下文」
//         （AGENTS.md §3.3：最小上下文证明不了「真服务同时起」那个形态）。
//      ⇒ 把**生效的** Flyway 配置（账本表名 / baseline 版本 / baseline-on-migrate /
//         locations / DSN）与实际执行的迁移写进 `--out` 的 JSON。
//      ⇒ 启动后做一次 **TCP 连通性探测**（`--grpc-port`）⇒ 记录 gRPC server 是否真的在监听。
//      ⇒ `--extra-locations` 用来注入**人造失败迁移**（边界 ②）——
//         通过 `filesystem:` 前缀指向一个临时目录，**不碰仓库里的任何 .sql**。
//      ⇒ 其它 `--k=v` 一律落成 **system property**（优先级高于 application.yml，
//         ConfigData 排在属性源最后 ⇒ system property 压得住 yaml）⇒
//         **不改任何生产 yaml** 就能覆盖 gRPC 端口 / Nacos 地址 / 账本表名。
//
// 【⛔ 本文件不碰任何 service/ 下的代码】
//   它只**读**各模块的 application.yml 与 db/migration/*.sql（放在 classpath 上），
//   跑的是**真 Spring Boot 上下文 + 真 Flyway 引擎 + 真 PG**。
// =============================================================================

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class FlywayRpcBoot {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("usage: flyway-rpc-boot migrate --module= --out= [--start-at=] "
                    + "[--extra-locations=] [--extra-args=]");
            System.exit(2);
        }
        Map<String, String> opt = new LinkedHashMap<>();
        for (int i = 1; i < args.length; i++) {
            String a = args[i];
            if (!a.startsWith("--")) {
                throw new IllegalArgumentException("unexpected arg: " + a);
            }
            int eq = a.indexOf('=');
            if (eq < 0) {
                opt.put(a.substring(2), "true");
            } else {
                opt.put(a.substring(2, eq), a.substring(eq + 1));
            }
        }
        if ("migrate".equals(args[0])) {
            migrate(opt);
        } else {
            System.err.println("unknown mode: " + args[0]);
            System.exit(2);
        }
    }

    // ==================================================================
    // migrate —— 起真 Spring Boot 上下文，让 Spring Boot 的 Flyway 自动配置去迁移
    // ==================================================================
    private static void migrate(Map<String, String> opt) {
        String module = opt.get("module");
        String out = opt.get("out");
        long startAt = Long.parseLong(opt.getOrDefault("start-at", "0"));
        String extraLocations = opt.get("extra-locations");
        String app = opt.get("app");
        String grpcPort = opt.get("grpc-port");

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("module", module);
        res.put("mode", app == null || app.isBlank() ? "minimal-context" : "real-rpc-process");
        res.put("appClass", app);
        res.put("startedAtEpochMs", System.currentTimeMillis());

        // 日志：保留 Flyway 的 INFO（那是「迁移真的跑了」的证据）
        // ⚠️ **不压 root 日志** —— 真 rpc 进程的「gRPC Server started, listening on ...」
        //    是 root=INFO 下的 net.devh INFO ⇒ 压掉就拿不到「真 gRPC server 起来了」的证据。
        System.setProperty("logging.level.org.flywaydb", "INFO");

        // 🔴 **其余 --k=v ⇒ system property**（不改任何生产 yaml）
        //   Spring Boot 的属性源顺序里 `ConfigData`（application.yml）排在**最后**
        //   ⇒ system property **压得住** yaml（实测：--extra-locations 就是这么生效的）
        for (Map.Entry<String, String> e : opt.entrySet()) {
            String k = e.getKey();
            if (k.equals("module") || k.equals("out") || k.equals("start-at")
                    || k.equals("extra-locations") || k.equals("app") || k.equals("grpc-port")) {
                continue;
            }
            System.setProperty(k, e.getValue());
        }

        // 🔴 **注入额外 migration location**（边界 ②：人造一个必然失败的 V_N）
        //   用 **system property** 覆盖 `spring.flyway.locations`：
        //   Spring Boot 的属性源优先级是「OS 环境变量 > system properties > application.yml」
        //   ⇒ system property **压得住**模块 yaml 里的 `locations: classpath:db/migration`
        //   ⇒ **不改任何生产 yaml / .sql**（本 lane 无 service/** 写权）
        //   ⇒ 追加的是 `filesystem:` 前缀 ⇒ Flyway 会去那个目录找迁移文件
        //      （flyway-core-10.22.0 `Location.FILESYSTEM_PREFIX` = "filesystem:"）
        if (extraLocations != null && !extraLocations.isBlank()) {
            System.setProperty("spring.flyway.locations",
                    "classpath:db/migration," + extraLocations);
        }

        // ---- 并发栅栏：所有进程等到同一个墙钟瞬间才起 ----
        long waited = 0;
        while (System.currentTimeMillis() < startAt) {
            try {
                Thread.sleep(3);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            waited++;
        }
        long t0 = System.currentTimeMillis();
        res.put("startAtEpochMs", t0);
        res.put("barrierWaitMs", waited == 0 ? 0 : 3L * waited);

        ConfigurableApplicationContext ctx = null;
        try {
            // 🔴 **起真 rpc 进程**：`--app` 指向该模块自己的 `*RpcApplication`
            //   （@SpringBootApplication ⇒ 扫本模块全部 Bean ⇒ 真 gRPC server / 真 Kafka
            //    / 真 Redis / 真 Flyway 都在里面）⇒ 边界 ① 要的「真 rpc 进程」。
            //   ⚠️ 拿不到 `--app` 时才退回「最小上下文」（只 import DataSource + Flyway）
            //      —— 那个形态**证明不了**「真服务同时起」，台账里必须分开记账。
            Class<?> appClass = (app == null || app.isBlank())
                    ? FlywayRpcMinimalApp.class
                    : Class.forName(app);

            ctx = new SpringApplicationBuilder(appClass)
                    .web(WebApplicationType.NONE)
                    .bannerMode(Banner.Mode.OFF)
                    .logStartupInfo(false)
                    .run();

            long t1 = System.currentTimeMillis();
            res.put("endAtEpochMs", t1);
            res.put("durationMs", t1 - t0);
            res.put("ok", true);

            // 🔴 **gRPC server 真的在监听吗**（§3.3：不能凭「进程活着」推断）
            //   ⇒ 启动后做一次 **TCP 连通性探测**（连不上就是没起来 / 端口被占）。
            if (grpcPort != null && !grpcPort.isBlank()) {
                res.put("grpcPort", grpcPort);
                res.put("grpcServerListening", tcpProbe(Integer.parseInt(grpcPort.trim())));
            }

            Flyway flyway = ctx.getBean(Flyway.class);
            var cfg = flyway.getConfiguration();
            // 🔴 生效配置（Spring Boot 真的把 yaml 的值交给引擎了吗？§3.3）
            res.put("effectiveTable", cfg.getTable());
            res.put("effectiveBaselineVersion", String.valueOf(cfg.getBaselineVersion()));
            res.put("effectiveBaselineOnMigrate", cfg.isBaselineOnMigrate());
            res.put("effectiveSchemas", Arrays.toString(cfg.getSchemas()));
            res.put("effectiveLocations", Arrays.toString(cfg.getLocations()));
            res.put("extraLocations", extraLocations);
            try {
                javax.sql.DataSource ds = ctx.getBean(javax.sql.DataSource.class);
                String jdbcUrl = null;
                if (ds instanceof com.zaxxer.hikari.HikariDataSource hikari) {
                    jdbcUrl = hikari.getJdbcUrl();
                }
                res.put("effectiveJdbcUrl", jdbcUrl);
            } catch (Exception e) {
                res.put("effectiveJdbcUrl", "<取不到：" + e.getClass().getSimpleName() + ">");
            }

            List<Map<String, Object>> applied = new ArrayList<>();
            for (MigrationInfo mi : flyway.info().all()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("version", mi.getVersion() == null ? null : mi.getVersion().getVersion());
                row.put("description", mi.getDescription());
                row.put("state", String.valueOf(mi.getState()));
                row.put("type", mi.getType() == null ? null : String.valueOf(mi.getType()));
                applied.add(row);
            }
            res.put("migrations", applied);
        } catch (Throwable e) {
            res.put("ok", false);
            res.put("endAtEpochMs", System.currentTimeMillis());
            res.put("errorType", e.getClass().getName());
            String msg = e.getMessage() == null ? "" : e.getMessage();
            res.put("error", msg.length() > 4000 ? msg.substring(0, 4000) : msg);
            Throwable c = e.getCause();
            int depth = 0;
            while (c != null && depth++ < 8) {
                String cm = c.getMessage() == null ? "" : c.getMessage();
                res.put("cause" + depth, c.getClass().getName() + ": "
                        + (cm.length() > 1500 ? cm.substring(0, 1500) : cm));
                c = c.getCause();
            }
            System.err.println("[L11] module=" + module + " FAILED: " + e);
        } finally {
            if (ctx != null) {
                try {
                    ctx.close();
                } catch (Exception ignore) {
                    // ignore
                }
            }
            write(out, res);
        }
        System.exit(Boolean.TRUE.equals(res.get("ok")) ? 0 : 1);
    }

    // ==================================================================
    // helpers
    // ==================================================================

    /** TCP 连通性探测：连得上 ⇒ 该端口有东西在监听（真 gRPC server 的证据）。 */
    private static boolean tcpProbe(int port) {
        try (java.net.Socket s = new java.net.Socket()) {
            s.connect(new java.net.InetSocketAddress("127.0.0.1", port), 3000);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void write(String path, Object value) {
        try {
            Path p = Path.of(path);
            if (p.getParent() != null) {
                Files.createDirectories(p.getParent());
            }
            Files.writeString(p, toJson(value), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("cannot write " + path, e);
        }
    }

    /** 极简 JSON 序列化（避免额外依赖）。 */
    private static String toJson(Object v) {
        StringBuilder sb = new StringBuilder();
        json(sb, v);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void json(StringBuilder sb, Object v) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof String s) {
            sb.append('"').append(esc(s)).append('"');
        } else if (v instanceof Boolean || v instanceof Integer || v instanceof Long) {
            sb.append(v);
        } else if (v instanceof Double || v instanceof Float) {
            sb.append(v);
        } else if (v instanceof Map<?, ?> m) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append('"').append(esc(String.valueOf(e.getKey()))).append("\":");
                json(sb, e.getValue());
            }
            sb.append('}');
        } else if (v instanceof Iterable<?> it) {
            sb.append('[');
            boolean first = true;
            for (Object o : it) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                json(sb, o);
            }
            sb.append(']');
        } else {
            sb.append('"').append(esc(String.valueOf(v))).append('"');
        }
    }

    private static String esc(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}

/**
 * 只 import 两个 auto-configuration ⇒ 不会把 Kafka / Nacos / Redis / Web 拉进来。
 *
 * <p>⚠️ 必须是**顶层类**（不能是 {@code FlywayRpcBoot} 的嵌套成员）：
 * Spring 的 {@code ConfigurationClassParser} 会扫描「成员类」并用
 * {@code ClassUtils.forName("Outer$Inner")} 去加载 ⇒ source launcher 把编译产物放在
 * **另一个 ClassLoader** 里 ⇒ 那样写会抛 {@code ClassNotFoundException}。
 * 顶层类没有这个扫描路径 ⇒ 安全。
 */
@Configuration(proxyBeanMethods = false)
@ImportAutoConfiguration({DataSourceAutoConfiguration.class, FlywayAutoConfiguration.class})
class FlywayRpcMinimalApp {
}
