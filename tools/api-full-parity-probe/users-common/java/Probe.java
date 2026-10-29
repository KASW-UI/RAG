// users-common 双侧活体对拍探针 —— **RAG 侧**。
//
// ⚠️ 本文件**调用 `service/users/users-common` 的真实类**（不重写任何实现）：
//   com.rag.users.common.errmsg.ErrorCode
//   com.rag.users.common.jwt.JwtUtil / BearerTokenExtractor
//   com.rag.users.common.cryptx.PasswordUtil
//   com.rag.users.common.http.GoStyleStringInt64 / GoStyleRequired
//   com.rag.users.common.ddl.SchemaInitializer（**真的在真 PG 上跑那四份 DDL**）
//
// 输出：每行 `case_id<TAB>value`，与 Go 侧**逐字节**比对（run.sh）。
//
// ⚠️ 为什么本文件在仓内而不是编译进 users-common：
//   探针**不是产品代码** ⇒ 不能进共享层模块（写权归 G4）⇒ 放 tools/ 下，
//   由 run.sh 用 `javac -cp <users-common 的 classes + 依赖>` 现编现跑。

import com.rag.users.common.cryptx.PasswordUtil;
import com.rag.users.common.ddl.SchemaInitializer;
import com.rag.users.common.errmsg.ErrorCode;
import com.rag.users.common.http.GoStyleRequired;
import com.rag.users.common.http.GoStyleStringInt64;
import com.rag.users.common.jwt.BearerTokenExtractor;
import com.rag.users.common.jwt.JwtUtil;
import io.jsonwebtoken.Jwts;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class Probe {

    // ── 固定夹具（与 Go 侧逐字一致；任何一项改动都会让 expected.txt 失配）────
    static final String FIXED_BCRYPT_HASH =
            "$2a$10$k5Q0/H16AzxsOsbbng9Mz.S0S21mTo6/xYlKwLm.9wUw9UGJPoBMm";
    static final String FIXED_PASSWORD = "probe-pass-123";
    static final long FIXED_IAT = 1700000000L;
    static final long FIXED_EXP = 1700003600L;
    static final long FIXED_USER_ID = 1234567890L;
    /** 交叉验签用的 userId = 2^53+1（顺带钉死 UNRESOLVED-USERSCOMMON-005 的精度问题）。 */
    static final long XVERIFY_USER_ID = 9007199254740993L;
    static final long SEED_UID = 9007199254740993L;

    static final int[] KEY_LENS = {15, 16, 31, 32, 36, 47, 48, 63, 64, 65, 128};
    static final int[] XVERIFY_LENS = {15, 32, 36, 47, 48, 64};

    static final int[] ERRMSG_CODES = {
            200, 500, 1015,
            5001, 5002, 5003, 5004, 5005, 5006, 5007, 5008, 5009, 5010,
            1001, 1002, 1003, 1004, 1005, 1006, 1007, 1008, 1009, 1010, 1011, 1012,
            1016, 1017, 1018,
            3001, 3002, 3003,
            0, 1, 999, 1014, 1019, 4000, 99999, -1,
    };

    /** bearer 的 11 个输入（与 Go 侧逐字一致）。 */
    static final String[] BEARER_INPUTS = {
            "Bearer eyJhbGciOiJIUzI1NiJ9.eyJ1c2VySWQiOjF9.sig",
            "bearer eyJhbGciOiJIUzI1NiJ9.eyJ1c2VySWQiOjF9.sig",
            "BEARER eyJhbGciOiJIUzI1NiJ9.eyJ1c2VySWQiOjF9.sig",
            "Bearer  eyJhbGciOiJIUzI1NiJ9.eyJ1c2VySWQiOjF9.sig",
            "XXBearer eyJhbGciOiJIUzI1NiJ9.eyJ1c2VySWQiOjF9.sig",
            "Bearer ",
            "Bearer",
            "eyJhbGciOiJIUzI1NiJ9.eyJ1c2VySWQiOjF9.sig",
            "",
            " Bearer eyJhbGciOiJIUzI1NiJ9.eyJ1c2VySWQiOjF9.sig",
            "Bearer\teyJhbGciOiJIUzI1NiJ9.eyJ1c2VySWQiOjF9.sig",
    };

    /** `,string` int64 的 17 个 body（与 Go 侧逐字一致）。 */
    static final String[] INT64_BODIES = {
            "{\"value\":\"123\"}",
            "{\"value\":123}",
            "{\"value\":1.5}",
            "{\"value\":1.0}",
            "{\"value\":1e2}",
            "{\"value\":true}",
            "{\"value\":null}",
            "{\"value\":\"abc\"}",
            "{\"value\":\"\"}",
            "{\"value\":\" 12\"}",
            "{\"value\":\"+123\"}",
            "{\"value\":\"9223372036854775807\"}",
            "{\"value\":\"9223372036854775808\"}",
            "{\"value\":\"-9223372036854775808\"}",
            "{\"value\":\"-9223372036854775809\"}",
            "{\"value\":9223372036854775807}",
            "{\"value\":9223372036854775808}",
    };

    /** bcrypt 判定谱的固定夹具（与 Go 侧逐字一致）。 */
    static final String[][] BCRYPT_FIXTURES = {
            {"match", FIXED_BCRYPT_HASH, FIXED_PASSWORD},
            {"mismatch", FIXED_BCRYPT_HASH, "wrong-password"},
            {"empty_hash", "", FIXED_PASSWORD},
            {"not_bcrypt", "abc", FIXED_PASSWORD},
            {"truncated", "$2a$10$tooshort", FIXED_PASSWORD},
            {"cost_zero", "$2a$00$" + FIXED_BCRYPT_HASH.substring(7), FIXED_PASSWORD},
            {"cost_overflow", "$2a$99$" + FIXED_BCRYPT_HASH.substring(7), FIXED_PASSWORD},
            {"prefix_2b", "$2b$10$" + FIXED_BCRYPT_HASH.substring(7), FIXED_PASSWORD},
            {"prefix_2y", "$2y$10$" + FIXED_BCRYPT_HASH.substring(7), FIXED_PASSWORD},
            {"prefix_2x", "$2x$10$" + FIXED_BCRYPT_HASH.substring(7), FIXED_PASSWORD},
            {"prefix_1", "$1$10$" + FIXED_BCRYPT_HASH.substring(7), FIXED_PASSWORD},
            {"empty_pwd", FIXED_BCRYPT_HASH, ""},
            {"unicode_pwd", FIXED_BCRYPT_HASH, "密码🔒"},
            {"pwd_72", FIXED_BCRYPT_HASH, "a".repeat(72)},
    };

    static final ObjectMapper MAPPER = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        argsCache = args;
        String outPath = arg("--out=", "");
        String tokensPath = arg("--tokens=", "");
        String peerTokensPath = arg("--peer-tokens=", "");
        String pgHost = arg("--pg-host=", "127.0.0.1");
        String pgPort = arg("--pg-port=", "");
        String pgUser = arg("--pg-user=", "admin");
        String pgPass = arg("--pg-pass=", "sea");
        String pgDb = arg("--pg-db=", "first_db");
        // phase 1 = 库面 + DDL 面；phase 2 = 只做交叉验签（避免重复输出同一批 case）。
        String phase = arg("--phase=", "1");
        if (outPath.isEmpty()) {
            System.err.println("usage: Probe --out=<file> [--tokens=<file>] [--peer-tokens=<file>] [--pg-*]");
            System.exit(2);
        }

        try (Emitter em = new Emitter(outPath)) {
            if ("2".equals(phase)) {
                emitJwt(em, tokensPath, peerTokensPath);
                emitCryptx(em, tokensPath, peerTokensPath);
                return;
            }
            emitErrmsg(em);
            emitJwt(em, tokensPath, peerTokensPath);
            emitCryptx(em, tokensPath, peerTokensPath);
            emitBearer(em);
            emitInt64(em);
            emitRequired(em);
            if (!pgPort.isEmpty()) {
                emitDdl(em, pgHost, pgPort, pgUser, pgPass, pgDb);
            }
        }
    }

    // ── G1 errmsg ─────────────────────────────────────────────────────────────
    static void emitErrmsg(Emitter em) throws IOException {
        for (int code : ERRMSG_CODES) {
            em.emit("c.errmsg." + code, ErrorCode.getMsg(code));
        }
    }

    // ── G2 jwt ────────────────────────────────────────────────────────────────
    static void emitJwt(Emitter em, String tokensPath, String peerTokensPath) throws Exception {
        for (int n : KEY_LENS) {
            String secret = secretFor(n);
            String tok = JwtUtil.getToken(secret, FIXED_IAT, FIXED_EXP - FIXED_IAT, FIXED_USER_ID);
            String[] parts = tok.split("\\.");
            if (parts.length != 3) {
                em.emit("c.jwt.error." + n, "ERR:bad token shape");
                continue;
            }
            String headerRaw = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            String payloadRaw = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            Map<String, Object> header = MAPPER.readValue(headerRaw, new TypeReference<Map<String, Object>>() {});
            Map<String, Object> payload = MAPPER.readValue(payloadRaw, new TypeReference<Map<String, Object>>() {});
            em.emit("c.jwt.alg." + n, String.valueOf(header.get("alg")));
            em.emit("c.jwt.typ." + n, String.valueOf(header.get("typ")));
            em.emit("c.jwt.header_keys." + n, sortedKeys(header));
            em.emit("c.jwt.payload_keys." + n, sortedKeys(payload));
            em.emit("c.jwt.siglen." + n, String.valueOf(parts[2].length()));
            em.emit("c.jwt.userid_json_type." + n, wireJsonType(payload.get("userId")));
            // 规范化 signing input 的签名（证明「差异仅来自 JSON 键序」）。
            em.emit("c.jwt.canon_sig." + n,
                    canonSig(secret, canonicalHeader(header), canonicalPayload(payload)));
        }

        // 原始字节（**键序**差异 ⇒ 两侧不同 ⇒ 登记为 REG 行，不参与「必须相同」判定）。
        {
            String secret = secretFor(36);
            String tok = JwtUtil.getToken(secret, FIXED_IAT, FIXED_EXP - FIXED_IAT, FIXED_USER_ID);
            String[] parts = tok.split("\\.");
            em.emit("c.jwt.raw.header",
                    new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8));
            em.emit("c.jwt.raw.payload",
                    new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8));
        }

        // 交叉验签：本侧签的 token 写给对侧；对侧再回验本侧的 token。
        if (!tokensPath.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            long iat = System.currentTimeMillis() / 1000L;
            for (int n : XVERIFY_LENS) {
                String secret = secretFor(n);
                String tok = JwtUtil.getToken(secret, iat, 3600L, XVERIFY_USER_ID);
                sb.append(n).append(' ').append(tok).append('\n');
            }
            Files.writeString(Path.of(tokensPath), sb.toString());
        }
        if (!peerTokensPath.isEmpty()) {
            for (String line : Files.readAllLines(Path.of(peerTokensPath))) {
                if (line.isBlank()) {
                    continue;
                }
                String[] f = line.split(" ", 2);
                if (f.length != 2) {
                    continue;
                }
                int n = Integer.parseInt(f[0]);
                String secret = secretFor(n);
                String verdict;
                try {
                    // **真实 api 侧的验签路径**（JwtSignatureVerifier.java:67 + :103-106）：
                    //   key = Keys.hmacShaKeyFor(JwtUtil.padToHmacKeySize(secret))
                    //   Jwts.parser().verifyWith(key).build().parseSignedClaims(token)
                    //   claim = payload.get("userId"); ((Number) claim).longValue()
                    javax.crypto.SecretKey key =
                            io.jsonwebtoken.security.Keys.hmacShaKeyFor(JwtUtil.padToHmacKeySize(secret));
                    var jws = Jwts.parser().verifyWith(key).build().parseSignedClaims(f[1]);
                    Object claim = jws.getPayload().get("userId");
                    if (!(claim instanceof Number)) {
                        verdict = "REJECTED:userId claim is not a Number";
                    } else {
                        verdict = "peer=VERIFIED,uid=" + ((Number) claim).longValue();
                    }
                } catch (Exception e) {
                    verdict = "REJECTED:" + e.getClass().getSimpleName() + ": " + e.getMessage();
                }
                em.emit("c.jwt.xverify.rag." + n, verdict);
            }
        }
    }

    /** 规范化 header：键排序后的紧凑 JSON（与 Go 侧 canonicalJSON 同规则）。 */
    static String canonicalHeader(Map<String, Object> header) {
        StringBuilder b = new StringBuilder("{");
        boolean first = true;
        for (String k : new TreeMap<>(header).keySet()) {
            if (!first) {
                b.append(',');
            }
            first = false;
            b.append(jsonString(k)).append(':').append(jsonValue(header.get(k)));
        }
        return b.append('}').toString();
    }

    /** 规范化 payload：键排序后的紧凑 JSON。 */
    static String canonicalPayload(Map<String, Object> payload) {
        StringBuilder b = new StringBuilder("{");
        boolean first = true;
        for (String k : new TreeMap<>(payload).keySet()) {
            if (!first) {
                b.append(',');
            }
            first = false;
            b.append(jsonString(k)).append(':').append(jsonValue(payload.get(k)));
        }
        return b.append('}').toString();
    }

    /** 数字按原样输出（Jackson 给的是 Integer/Long/BigInteger ⇒ toString 即 JSON 字面量）。 */
    static String jsonValue(Object v) {
        if (v instanceof String s) {
            return jsonString(s);
        }
        if (v instanceof Number n) {
            return n.toString();
        }
        if (v instanceof Boolean b) {
            return b ? "true" : "false";
        }
        if (v == null) {
            return "null";
        }
        return jsonString(String.valueOf(v));
    }

    /** JSON 字符串转义（与 Go 的 encoding/json 对齐：< > & 也转成 \\u00xx）。 */
    static String jsonString(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                case '<' -> b.append("\\u003c");
                case '>' -> b.append("\\u003e");
                case '&' -> b.append("\\u0026");
                default -> {
                    if (c < 0x20) {
                        b.append(String.format("\\u%04x", (int) c));
                    } else {
                        b.append(c);
                    }
                }
            }
        }
        return b.append('"').toString();
    }

    /** canonSig = 对「规范化 signing input」的 HMAC-SHA256（hex）。 */
    static String canonSig(String secret, String header, String payload) throws Exception {
        Base64.Encoder enc = Base64.getUrlEncoder().withoutPadding();
        String input = enc.encodeToString(header.getBytes(StandardCharsets.UTF_8)) + "."
                + enc.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] sig = mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : sig) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    static String sortedKeys(Map<String, Object> m) {
        return String.join(",", new TreeMap<>(m).keySet());
    }

    static String wireJsonType(Object v) {
        if (v instanceof Number) {
            return "number";
        }
        if (v instanceof String) {
            return "string";
        }
        if (v instanceof Boolean) {
            return "bool";
        }
        if (v == null) {
            return "null";
        }
        return v.getClass().getName();
    }

    // ── G3 cryptx ─────────────────────────────────────────────────────────────
    static void emitCryptx(Emitter em, String tokensPath, String peerTokensPath) throws Exception {
        String h = PasswordUtil.encrypt(FIXED_PASSWORD);
        em.emit("c.cryptx.encrypt_shape",
                "prefix=" + h.substring(0, 7) + ",len=" + h.length() + ",charset=" + bcryptCharsetOk(h));

        for (String[] f : BCRYPT_FIXTURES) {
            em.emit("c.cryptx.check." + f[0], String.valueOf(PasswordUtil.check(f[2], f[1])));
        }

        if (!tokensPath.isEmpty()) {
            Files.writeString(Path.of(tokensPath + ".cryptx"), PasswordUtil.encrypt(FIXED_PASSWORD) + "\n");
        }
        if (!peerTokensPath.isEmpty()) {
            for (String line : Files.readAllLines(Path.of(peerTokensPath + ".cryptx"))) {
                if (line.isBlank()) {
                    continue;
                }
                em.emit("c.cryptx.xverify.rag", String.valueOf(PasswordUtil.check(FIXED_PASSWORD, line)));
            }
        }
    }

    static String bcryptCharsetOk(String h) {
        final String cs = "./ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        String body = h.substring(7);
        for (int i = 0; i < body.length(); i++) {
            if (cs.indexOf(body.charAt(i)) < 0) {
                return "bad";
            }
        }
        return "ok";
    }

    // ── G4 bearer ─────────────────────────────────────────────────────────────
    static void emitBearer(Emitter em) throws IOException {
        for (int i = 0; i < BEARER_INPUTS.length; i++) {
            String in = BEARER_INPUTS[i];
            em.emit("c.bearer.sig." + i, BearerTokenExtractor.tokenForSignatureVerification(in));
            em.emit("c.bearer.trim." + i, BearerTokenExtractor.trimBearerPrefix(in));
        }
    }

    // ── G5 `,string` int64 ────────────────────────────────────────────────────
    static void emitInt64(Emitter em) throws IOException {
        for (int i = 0; i < INT64_BODIES.length; i++) {
            String verdict;
            try {
                // **真实路径**：Jackson 反序列化 ⇒ 原始标量交给 GoStyleStringInt64 判定。
                Map<String, Object> m = MAPPER.readValue(INT64_BODIES[i],
                        new TypeReference<Map<String, Object>>() {});
                Object raw = m.get("value");
                Long parsed = GoStyleStringInt64.parse(raw, "value");
                // 必填校验（Go 的 mapping 在同一层做：null ⇒ 报错）。
                GoStyleRequired.field("value", parsed);
                verdict = "OK:" + parsed;
            } catch (Exception e) {
                verdict = "REJECT";
            }
            em.emit("c.int64." + i, verdict);
        }
    }

    // ── G6 必填校验 ───────────────────────────────────────────────────────────
    static void emitRequired(Emitter em) throws IOException {
        // 1) 缺席（Go：field "name" is not set）
        em.emit("c.required.scalar_absent", requiredVerdict(() -> GoStyleRequired.field("name", null)));
        // 2) 空串（Go：合法值，不报错）
        em.emit("c.required.scalar_empty", requiredVerdict(() -> GoStyleRequired.field("name", "")));
        // 3) 显式 null（Go：field "name" mustn't be nil）
        em.emit("c.required.scalar_explicit_null",
                requiredVerdict(() -> GoStyleRequired.explicitNull("name", true, null)));
        // 4) map 字段缺席（Go：Map 分支不报错）
        em.emit("c.required.map_absent",
                requiredVerdict(() -> GoStyleRequired.explicitNull("extra_info", false, null)));
    }

    static String requiredVerdict(Runnable r) {
        try {
            r.run();
            return "OK";
        } catch (RuntimeException e) {
            return e.getMessage();
        }
    }

    // ── G7 DDL + 播种（真源路径 = SchemaInitializer.applyAll）──────────────────
    static void emitDdl(Emitter em, String host, String port, String user, String pass, String dbName)
            throws Exception {
        String dsn = "jdbc:postgresql://" + host + ":" + port + "/" + dbName + "?user=" + user
                + (pass.isEmpty() ? "" : "&password=" + pass);
        try (Connection conn = DriverManager.getConnection(dsn)) {
            // **真的跑 users-common 的四份 DDL**（不是抄一份 SQL）。
            SchemaInitializer.applyAll(conn, "users-common-probe");

            // 播种（两侧同一行 ⇒ 不是「两边都空」那种无信息量的对拍）。
            try (var ps = conn.prepareStatement(
                    "INSERT INTO users_user (uid, username, password, email, status, score, extra_info) "
                            + "VALUES (?,?,?,?,?,?,?)")) {
                ps.setLong(1, SEED_UID);
                ps.setString(2, "probe-user");
                ps.setString(3, FIXED_BCRYPT_HASH);
                ps.setString(4, "probe@example.invalid");
                ps.setLong(5, 1L);
                ps.setInt(6, 7);
                ps.setString(7, "{\"a\":\"1\",\"b\":\"2\"}");
                ps.executeUpdate();
            }
            try (var ps = conn.prepareStatement(
                    "INSERT INTO users_user_avatar_history "
                            + "(history_id, uid, avatar_url, content_type, size_bytes, is_current) "
                            + "VALUES (?,?,?,?,?,?)")) {
                ps.setString(1, "probe-history-1");
                ps.setLong(2, SEED_UID);
                ps.setString(3, "https://example.invalid/a.png");
                ps.setString(4, "image/png");
                ps.setLong(5, 4096L);
                ps.setBoolean(6, true);
                ps.executeUpdate();
            }
            try (var ps = conn.prepareStatement(
                    "INSERT INTO admin_admin (uid, username, password, email, extra_info) VALUES (?,?,?,?,?)")) {
                ps.setLong(1, 9007199254740994L);
                ps.setString(2, "probe-admin");
                ps.setString(3, FIXED_BCRYPT_HASH);
                ps.setString(4, "probe-admin@example.invalid");
                ps.setString(5, "{\"a\":\"1\",\"b\":\"2\"}");
                ps.executeUpdate();
            }
            try (var ps = conn.prepareStatement(
                    "INSERT INTO admin_invite (code, inviter_uid, expires_at) VALUES (?,?,?)")) {
                ps.setString(1, "ADM-PROBE0001");
                ps.setLong(2, 9007199254740994L);
                ps.setObject(3, java.time.OffsetDateTime.parse("2026-01-02T03:04:05Z"));
                ps.executeUpdate();
            }

            // 读回（users-common 没有 reader ⇒ 用 JDBC 原样读回，与 Go 侧的 GORM 读回对拍）。
            try (var ps = conn.prepareStatement(
                    "SELECT uid, username, password, email, status, score, extra_info "
                            + "FROM users_user WHERE uid = ?")) {
                ps.setLong(1, SEED_UID);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        em.emit("c.seed.users_user.error", "ERR:row not found");
                        return;
                    }
                    em.emit("c.seed.users_user.uid", String.valueOf(rs.getLong("uid")));
                    em.emit("c.seed.users_user.username", rs.getString("username"));
                    em.emit("c.seed.users_user.password", rs.getString("password"));
                    em.emit("c.seed.users_user.email", rs.getString("email"));
                    em.emit("c.seed.users_user.status", String.valueOf(rs.getLong("status")));
                    em.emit("c.seed.users_user.score", String.valueOf(rs.getInt("score")));
                    em.emit("c.seed.users_user.extra_info", canonicalExtraInfo(rs.getString("extra_info")));
                }
            }
            try (var ps = conn.prepareStatement(
                    "SELECT history_id, uid, avatar_url, content_type, size_bytes, is_current "
                            + "FROM users_user_avatar_history WHERE history_id = ?")) {
                ps.setString(1, "probe-history-1");
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        em.emit("c.seed.users_user_avatar_history.error", "ERR:row not found");
                        return;
                    }
                    em.emit("c.seed.users_user_avatar_history.history_id", rs.getString("history_id"));
                    em.emit("c.seed.users_user_avatar_history.uid", String.valueOf(rs.getLong("uid")));
                    em.emit("c.seed.users_user_avatar_history.avatar_url", rs.getString("avatar_url"));
                    em.emit("c.seed.users_user_avatar_history.content_type", rs.getString("content_type"));
                    em.emit("c.seed.users_user_avatar_history.size_bytes",
                            String.valueOf(rs.getLong("size_bytes")));
                    em.emit("c.seed.users_user_avatar_history.is_current",
                            String.valueOf(rs.getBoolean("is_current")));
                }
            }
            try (var ps = conn.prepareStatement(
                    "SELECT uid, username, password, email, extra_info FROM admin_admin WHERE uid = ?")) {
                ps.setLong(1, 9007199254740994L);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        em.emit("c.seed.admin_admin.error", "ERR:row not found");
                        return;
                    }
                    em.emit("c.seed.admin_admin.uid", String.valueOf(rs.getLong("uid")));
                    em.emit("c.seed.admin_admin.username", rs.getString("username"));
                    em.emit("c.seed.admin_admin.password", rs.getString("password"));
                    em.emit("c.seed.admin_admin.email", rs.getString("email"));
                    em.emit("c.seed.admin_admin.extra_info", canonicalExtraInfo(rs.getString("extra_info")));
                }
            }
            try (var ps = conn.prepareStatement(
                    "SELECT code, inviter_uid, expires_at, used_by_uid, used_at FROM admin_invite WHERE code = ?")) {
                ps.setString(1, "ADM-PROBE0001");
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        em.emit("c.seed.admin_invite.error", "ERR:row not found");
                        return;
                    }
                    em.emit("c.seed.admin_invite.code", rs.getString("code"));
                    em.emit("c.seed.admin_invite.inviter_uid", String.valueOf(rs.getLong("inviter_uid")));
                    var exp = rs.getObject("expires_at", java.time.OffsetDateTime.class);
                    em.emit("c.seed.admin_invite.expires_at_epoch",
                            String.valueOf(exp == null ? "" : exp.toEpochSecond()));
                    long usedBy = rs.getLong("used_by_uid");
                    em.emit("c.seed.admin_invite.used_by_uid", rs.wasNull() ? "" : String.valueOf(usedBy));
                    var usedAt = rs.getObject("used_at", java.time.OffsetDateTime.class);
                    em.emit("c.seed.admin_invite.used_at_epoch",
                            String.valueOf(usedAt == null ? "" : usedAt.toEpochSecond()));
                }
            }

            emitColumns(em, conn, "users_user");
            emitColumns(em, conn, "users_user_avatar_history");
            emitColumns(em, conn, "admin_admin");
            emitColumns(em, conn, "admin_invite");
            // 表清单（⚠️ 两侧**表名不同** ⇒ 已登记的命名约定 ⇒ 登记为 REG 行）。
            List<String> tables = new ArrayList<>();
            try (var ps = conn.prepareStatement(
                    "SELECT table_name FROM information_schema.tables "
                            + "WHERE table_schema = current_schema() AND table_type = 'BASE TABLE' "
                            + "ORDER BY table_name");
                 var rs = ps.executeQuery()) {
                while (rs.next()) {
                    tables.add(rs.getString("table_name"));
                }
            }
            em.emit("c.ddl.tables", String.join(",", tables));
        }
    }

    /** canonicalExtraInfo = 把 extra_info 的 JSON 文本解析成 map 再按键排序输出。 */
    static String canonicalExtraInfo(String text) throws Exception {
        if (text == null) {
            return "";
        }
        Map<String, Object> m = MAPPER.readValue(text, new TypeReference<Map<String, Object>>() {});
        StringBuilder b = new StringBuilder("{");
        boolean first = true;
        for (String k : new TreeMap<>(m).keySet()) {
            if (!first) {
                b.append(',');
            }
            first = false;
            b.append(jsonString(k)).append(':').append(jsonValue(m.get(k)));
        }
        return b.append('}').toString();
    }

    /** emitColumns = 一行拍平一张表的列定义（列名:类型:可空:默认值,…）。 */
    static void emitColumns(Emitter em, Connection conn, String table) throws Exception {
        StringBuilder parts = new StringBuilder();
        Map<String, String> colValues = new java.util.LinkedHashMap<>();
        List<String> colNames = new ArrayList<>();
        try (var ps = conn.prepareStatement(
                "SELECT column_name, data_type, is_nullable, COALESCE(column_default, '') AS column_default "
                        + "FROM information_schema.columns "
                        + "WHERE table_schema = current_schema() AND table_name = ? ORDER BY ordinal_position")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                boolean first = true;
                while (rs.next()) {
                    String col = rs.getString("column_name");
                    String type = rs.getString("data_type");
                    String nullable = rs.getString("is_nullable");
                    String def = rs.getString("column_default");
                    if (!first) {
                        parts.append(',');
                    }
                    first = false;
                    parts.append(col).append(':').append(type).append(':').append(nullable).append(':').append(def);
                    colNames.add(col);
                    colValues.put(col, type + ":" + nullable + ":" + normalizeDefault(def));
                }
            }
        }
        // 逐列（**默认值已归一化**：序列名抹掉、大小写统一）⇒ 绝大多数列是「两侧逐字节相同」
        for (String col : colNames) {
            em.emit("c.ddl.col." + table + "." + col, colValues.get(col));
        }
    }

    /** normalizeDefault = 抹掉序列名 + 统一小写（与 Go 侧同规则）。 */
    static String normalizeDefault(String def) {
        return def.replaceAll("nextval\\('[^']*'(::regclass)?\\)", "nextval(...)").toLowerCase();
    }

    // ── 小工具 ────────────────────────────────────────────────────────────────
    static String secretFor(int n) {
        final String alphabet = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n; i++) {
            b.append(alphabet.charAt(i % alphabet.length()));
        }
        return b.toString();
    }

    static String arg(String prefix, String def) {
        for (String a : args()) {
            if (a.startsWith(prefix)) {
                return a.substring(prefix.length());
            }
        }
        return def;
    }

    static String[] args() {
        return Probe.argsCache;
    }

    static String[] argsCache;

    static {
        argsCache = new String[0];
    }

    /** Emitter = 每行 `id<TAB>value` 的写出器（value 里的 TAB/换行做转义）。 */
    static final class Emitter implements AutoCloseable {
        private final BufferedWriter w;

        Emitter(String path) throws IOException {
            this.w = Files.newBufferedWriter(Path.of(path), StandardCharsets.UTF_8);
        }

        void emit(String id, String value) throws IOException {
            String v = value.replace("\\", "\\\\")
                    .replace("\t", "\\t")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r");
            w.write(id);
            w.write('\t');
            w.write(v);
            w.write('\n');
        }

        @Override
        public void close() throws IOException {
            w.flush();
            w.close();
        }
    }
}
