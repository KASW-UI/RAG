// =============================================================================
// flyway-concurrent-boot.java —— **L4 lane** 的并发迁移驱动（单文件 source launcher）
// =============================================================================
//
// 【为什么是它】
//   AGENTS.md §3.3：「配置里没写 ≠ 没开」⇒ 光看 yaml 不算证据。
//   ⇒ 本驱动**起真的 Spring Boot 上下文**（只 import DataSource + Flyway 两个
//     auto-configuration，不引 Kafka/Nacos/Redis），让 **Spring Boot 自己的
//     FlywayAutoConfiguration** 去读**该模块自己的 application.yml** ⇒
//     测的就是**生产真正用的那条接线**（spring.flyway.* → Flyway 引擎）。
//   ⇒ 绝不使用手写 FluentConfiguration 映射（那会把「yaml 键名写错」这类
//     真实缺陷变成假绿）。
//
// 【三种模式】
//   migrate  --module=<rpc 模块名> --url= --user= --pass= --start-at=<epochMs> --out=<file>
//        ⇒ 等到 --start-at 这个墙钟瞬间才起上下文 ⇒ 多个进程**真并发**起
//        ⇒ 把 Flyway 的**生效配置**（表名 / baseline 版本 / baseline-on-migrate）
//          与实际执行的迁移写进 --out 的 JSON。
//   inspect  --url= --user= --pass= --out=<file>
//        ⇒ 把 public schema 的表 / 索引 / 每本账本的行 dump 成 JSON。
//   createdb --url= --user= --pass= --db=<name>
//        ⇒ 建一个空库（每个场景一个 ⇒ 场景之间互不污染）。
//
// 【⛔ 本文件不碰任何 service/ 下的代码】
//   它只**读**各模块的 application.yml 与 db/migration/*.sql（放在 classpath 上），
//   跑的是**真 Flyway 引擎 + 真 PG** ⇒ 是 L4 的唯一新文件之一。
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
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class FlywayConcurrentBoot {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("usage: flyway-concurrent-boot <migrate|inspect|createdb> --k=v ...");
            System.exit(2);
        }
        String mode = args[0];
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

        switch (mode) {
            case "migrate" -> migrate(opt);
            case "inspect" -> {
                inspect(opt);
                System.exit(0);
            }
            case "createdb" -> {
                createDb(opt);
                System.exit(0);
            }
            default -> {
                System.err.println("unknown mode: " + mode);
                System.exit(2);
            }
        }
    }

    // ==================================================================
    // migrate —— 起真 Spring Boot 上下文，让 Spring Boot 的 Flyway 自动配置去迁移
    // ==================================================================
    private static void migrate(Map<String, String> opt) {
        String module = opt.get("module");
        String url = opt.get("url");
        String user = opt.get("user");
        String pass = opt.get("pass");
        String out = opt.get("out");
        long startAt = Long.parseLong(opt.getOrDefault("start-at", "0"));

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("module", module);
        res.put("url", url);

        // 🔴 **不覆盖 datasource**：DSN 由**模块自己的 yaml**（`${POSTGRES_HOST:...}` 等占位符）
        //    拼出来，宿主/端口/库名由**环境变量**指到本 lane 的临时容器
        //    ⇒ 测的是「模块自己那条 DSN 拼装 + 模块自己的 flyway 配置」这条生产接线。
        //    （覆盖 system property 会把 message-rpc 的 `?sslmode=…&options=-c TimeZone=…`
        //      之类参数抹掉 ⇒ 反而不是生产形态。）
        // 日志：root 压到 WARN，但**保留 Flyway 的 INFO**（那是「账本表真的被建出来」的证据）
        System.setProperty("logging.level.root", "WARN");
        System.setProperty("logging.level.org.flywaydb", "INFO");

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
            ctx = new SpringApplicationBuilder(FlywayConcurrentMinimalApp.class)
                    .web(WebApplicationType.NONE)
                    .bannerMode(Banner.Mode.OFF)
                    .logStartupInfo(false)
                    .run();

            long t1 = System.currentTimeMillis();
            res.put("endAtEpochMs", t1);
            res.put("durationMs", t1 - t0);
            res.put("ok", true);

            Flyway flyway = ctx.getBean(Flyway.class);
            var cfg = flyway.getConfiguration();
            // 🔴 生效配置（Spring Boot 真的把 yaml 的值交给引擎了吗？）
            res.put("effectiveTable", cfg.getTable());
            res.put("effectiveBaselineVersion", String.valueOf(cfg.getBaselineVersion()));
            res.put("effectiveBaselineOnMigrate", cfg.isBaselineOnMigrate());
            res.put("effectiveSchemas", Arrays.toString(cfg.getSchemas()));
            res.put("effectiveLocations", Arrays.toString(cfg.getLocations()));
            // 生效 DSN（模块自己的 yaml + 环境变量拼出来的那一条）
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
            // 打出根因链（Flyway 的异常常把真因包在 cause 里）
            Throwable c = e.getCause();
            int depth = 0;
            while (c != null && depth++ < 8) {
                String cm = c.getMessage() == null ? "" : c.getMessage();
                res.put("cause" + depth, c.getClass().getName() + ": "
                        + (cm.length() > 1500 ? cm.substring(0, 1500) : cm));
                c = c.getCause();
            }
            System.err.println("[L4] module=" + module + " FAILED: " + e);
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
    // inspect —— dump public schema 的表 / 索引 / 每本账本的行
    // ==================================================================
    private static void inspect(Map<String, String> opt) throws Exception {
        String url = opt.get("url");
        String user = opt.get("user");
        String pass = opt.get("pass");
        String out = opt.get("out");

        Map<String, Object> res = new LinkedHashMap<>();
        List<String> tables = new ArrayList<>();
        List<String> indexes = new ArrayList<>();
        Map<String, List<Map<String, Object>>> ledgers = new LinkedHashMap<>();

        try (Connection c = DriverManager.getConnection(url, user, pass);
             Statement st = c.createStatement()) {

            try (ResultSet rs = st.executeQuery(
                    "select table_name from information_schema.tables "
                            + "where table_schema='public' and table_type='BASE TABLE' order by table_name")) {
                while (rs.next()) {
                    tables.add(rs.getString(1));
                }
            }
            try (ResultSet rs = st.executeQuery(
                    "select indexname from pg_indexes where schemaname='public' order by indexname")) {
                while (rs.next()) {
                    indexes.add(rs.getString(1));
                }
            }
            for (String t : tables) {
                if (!t.startsWith("flyway_schema_history")) {
                    continue;
                }
                List<Map<String, Object>> rows = new ArrayList<>();
                try (ResultSet rs = st.executeQuery(
                        "select installed_rank, version, type, description, success from "
                                + "\"" + t + "\" order by installed_rank")) {
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("rank", rs.getInt(1));
                        row.put("version", rs.getString(2));
                        row.put("type", rs.getString(3));
                        row.put("description", rs.getString(4));
                        row.put("success", rs.getBoolean(5));
                        rows.add(row);
                    }
                }
                ledgers.put(t, rows);
            }
        }
        res.put("tables", tables);
        res.put("indexes", indexes);
        res.put("ledgers", ledgers);
        write(out, res);
    }

    // ==================================================================
    // createdb —— 每个场景一个空库
    // ==================================================================
    private static void createDb(Map<String, String> opt) throws SQLException {
        String url = opt.get("url");
        String user = opt.get("user");
        String pass = opt.get("pass");
        String db = opt.get("db");
        try (Connection c = DriverManager.getConnection(url, user, pass);
             Statement st = c.createStatement()) {
            st.execute("CREATE DATABASE \"" + db + "\"");
        }
        System.out.println("[L4] created database " + db);
    }

    // ==================================================================
    // helpers
    // ==================================================================
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
 * <p>⚠️ 必须是**顶层类**（不能是 {@code FlywayConcurrentBoot} 的嵌套成员）：
 * Spring 的 {@code ConfigurationClassParser} 会扫描「成员类」并用
 * {@code ClassUtils.forName("Outer$Inner")} 去加载 ⇒ source launcher 把编译产物放在
 * **另一个 ClassLoader** 里 ⇒ 那样写会抛 {@code ClassNotFoundException}。
 * 顶层类没有这个扫描路径 ⇒ 安全。
 */
@Configuration(proxyBeanMethods = false)
@ImportAutoConfiguration({DataSourceAutoConfiguration.class, FlywayAutoConfiguration.class})
class FlywayConcurrentMinimalApp {
}
