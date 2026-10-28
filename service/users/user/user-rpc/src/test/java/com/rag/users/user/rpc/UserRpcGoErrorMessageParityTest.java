package com.rag.users.user.rpc;

import com.rag.users.user.proto.CreateUserReq;
import com.rag.users.user.proto.CreateUserResp;
import com.rag.users.user.proto.DeleteUserReq;
import com.rag.users.user.proto.DeleteUserResp;
import com.rag.users.user.proto.GetAvatarHistoryReq;
import com.rag.users.user.proto.GetAvatarHistoryResp;
import com.rag.users.user.proto.GetUserReq;
import com.rag.users.user.proto.GetUserResp;
import com.rag.users.user.proto.LogoutReq;
import com.rag.users.user.proto.LogoutResp;
import com.rag.users.user.proto.SelectAvatarReq;
import com.rag.users.user.proto.SelectAvatarResp;
import com.rag.users.user.proto.UpdateUserReq;
import com.rag.users.user.proto.UpdateUserResp;
import com.rag.users.user.proto.UploadAvatarReq;
import com.rag.users.user.proto.UploadAvatarResp;
import com.rag.users.user.proto.DeleteUserReq;
import com.rag.users.user.proto.GetAvatarHistoryReq;
import com.rag.users.user.proto.GetUserReq;
import com.rag.users.user.proto.LogoutReq;
import com.rag.users.user.proto.SelectAvatarReq;
import com.rag.users.user.proto.UpdateUserReq;
import com.rag.users.user.proto.UploadAvatarReq;
import com.rag.users.user.rpc.service.impl.UserServiceGrpcImpl;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static com.rag.users.user.rpc.support.GoStyleHostPortPinning.pinContainerPort;

/**
 * 🔴 user-rpc 的 **gRPC status description** 逐条对齐 Go 真源原始字节（N-2 守门）。
 *
 * <h2>这个类补的是哪一层证据</h2>
 *
 * <p>{@code docs/DRIFT/users/user/rpc.md} 的 §9 记着一条
 * <b>UNRESOLVED-USERRPC-007</b>：「双侧活体对拍从未在本组件上跑过 ——
 * 9 个 RPC 的字段级响应、error message、边界输入全部未对拍」。
 * 2026-10-24 补上（{@code tools/difftest/exp-difftest-grpc-user9.sh}，43 场景）后，
 * 唯一剩下的差异就是 <b>Go 源码里那 9 条被转码损坏的中文 message</b>。
 *
 * <h2>为什么「Go 那几处是乱码」是<b>已签字</b>的、不是待修的缺陷</h2>
 *
 * <p>Go 真源里 <b>同一个语义同时存在正常写法与损坏写法</b>（2026-10-24 逐文件 hex 实测）：
 * <pre>
 *   用户不存在   正常：getuserlogic.go:39 / deleteuserlogic.go:46 / updateuserlogic.go:74
 *               损坏：uploadavatarlogic.go:57 / getavatarhistorylogic.go:35 / selectavatarlogic.go:43
 *   DB查询失败   正常：getuserlogic.go:44 / registerlogic.go:54 / loginlogic.go:56 / …
 *               损坏：getavatarhistorylogic.go:38 / :44
 *   DB更新失败   正常：registerlogic.go:86 / updateuserlogic.go:66 / deleteuserlogic.go:60
 *               损坏：selectavatarlogic.go:49
 * </pre>
 * ⇒ 损坏的那几处是 <b>UTF-8 → GBK 误解码 → UTF-8</b> 的转码往返事故
 * （「用户不存在」的 UTF-8 字节 {@code e794a8 e688b7 e4b88d e5ad98 e59ca8}
 * 按 GBK 解码正好得到「鐢ㄦ埛涓嶅瓨鍦」+ 末字节落成 {@code ?}）。
 * ⇒ <b>Go 自己对同一语义会吐两种字节</b> ⇒ 损坏的那份在定义上就不是语义。
 *
 * <p>⇒ 处置（<b>用户 2026-10-21 已拍板，登记为 N-2</b>）：<b>RAG 保留正常中文，不复刻乱码</b>。
 * 先例见 {@code docs/Intentional_Deviations/_cross/signed-deviations.md}。
 *
 * <h2>本类钉住三件事</h2>
 * <ol>
 *   <li><b>未损坏的那些</b>：RAG 的 description 与 <b>Go 原始字节逐字节相等</b>
 *       （Go 那一侧用 hex 常量表达，<b>不写中文字面量</b> —— 免得又被编辑器转码一次）；</li>
 *   <li><b>已损坏的那些</b>：RAG 仍然吐正常中文，
 *       且<b>断言它与 Go 的原始损坏字节不同</b> ——
 *       这是「<b>决策 tripwire</b>」：若有人没走用户签字就把 RAG 改成乱码去对齐 Go，
 *       本条立刻红，并提示「先改台账 + 改本断言」；</li>
 *   <li><b>两者都要能在真 PG 上真的走到那条分支</b> ——
 *       DB 失败路径用<b>真故障注入</b>（改表名 / 加 CHECK 约束）制造，
 *       不是 mock 数据层。</li>
 * </ol>
 *
 * <h2>为什么必须真 PG（不能 mock）</h2>
 *
 * <p>要触发的是<b>真实的 DB 分支</b>：NOT_FOUND 要真的查不到行、
 * DB 查询失败要真的让 SQL 报错、DB 更新/插入失败要真的撞约束。
 * mock 数据层会把这几条分支全部编造出来 —— 那正是 §3.3 说的「绿测试测不到真机制」。
 *
 * <h2>本类的可证伪性</h2>
 *
 * <p>把 {@code UserDomainService} 里任意一处 {@code withDescription("…")} 改掉，
 * 对应用例立刻红（description 是逐字断言）。
 * 反过来，把「已损坏」那几条的 Go 字节常量改成 RAG 的中文，
 * 「决策 tripwire」那几条立刻红 ⇒ 说明这条守卫真的在盯。
 *
 * <h2>⚠️ 已知覆盖缺口（1/9，如实记录）</h2>
 *
 * <p><b>{@code history_id生成失败}</b>（Go 对应 {@code uploadavatarlogic.go:42} 的
 * {@code history_id鐢熸垚澶辫触}）<b>本类进不去</b>：
 * 它要求 {@code Snowflake.nextId()} 抛错，而 {@code Snowflake} 是
 * <b>进程级静态单例</b> —— {@code setWorkerId(null)} 是 no-op（first-wins），
 * {@code workerId} 一旦解析出来就没有 reset 钩子，
 * {@code generateNodeId()} 也不会失败 ⇒ <b>无法在不改动共享模块 {@code service/common} 的前提下诱发</b>。
 * ⇒ 该条目前由 {@code UserDomainServiceTest} 对「用户不存在 / 头像历史不存在」的逐字断言间接覆盖，
 * 但 <b>{@code history_id生成失败} 这一个字面量本身没有运行时护网</b> ——
 * 记在这里，别让下一个人以为 9/9 都守住了。
 */
@SpringBootTest(
        classes = UserRpcApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "grpc.server.port=0",
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN"
        })
@Testcontainers
@org.testcontainers.junit.jupiter.EnabledIfDockerAvailable
class UserRpcGoErrorMessageParityTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("users_rpc_wire_parity_it")
                    .withUsername("rag")
                    .withPassword("rag").withCreateContainerCmdModifier(cmd -> pinContainerPort(cmd, 5432));

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private UserServiceGrpcImpl grpc;
    @Autowired private JdbcTemplate jdbcTemplate;

    /** 一定不存在的 uid（与双侧活体对拍用的量级同款）。 */
    private static final long GHOST_UID = 8_999_999_999_999_999_999L;

    // ==================================================================
    // Go 侧的 description 原始字节
    //
    // ⚠️ 一律写 hex 而不是中文字面量 —— 让「Go 那一侧到底是什么字节」不可被编辑器二次转码。
    //    2026-10-24 双侧活体对拍实测抓到的 hex（与 Go 源文件逐字节一致）。
    // ==================================================================

    /** Go 里**正常**的写法（多处在用）⇒ RAG 必须与之逐字节相同。 */
    private static final String GO_OK_USER_NOT_EXIST = "e794a8e688b7e4b88de5ad98e59ca8";   // 用户不存在
    private static final String GO_OK_USERNAME_TAKEN  = "e794a8e688b7e5908de5b7b2e5ad98e59ca8"; // 用户名已存在
    private static final String GO_OK_DB_SELECT_FAILED = "4442e69fa5e8afa2e5a4b1e8b4a5";   // DB查询失败
    private static final String GO_OK_DB_UPDATE_FAILED = "4442e69bb4e696b0e5a4b1e8b4a5";   // DB更新失败
    private static final String GO_OK_TOKEN_EMPTY      = "546f6b656ee4b88de883bde4b8bae7a9ba"; // Token不能为空
    private static final String GO_OK_AVATAR_URL_REQUIRED = "6176617461725f75726c206973207265717569726564"; // avatar_url is required
    private static final String GO_OK_HISTORY_ID_REQUIRED = "686973746f72795f6964206973207265717569726564"; // history_id is required

    /** 🔴 Go 里**已被转码损坏**的写法（N-2，2026-10-21 用户签字：RAG 保留正常中文）⇒ RAG 必须与之**不同**。 */
    private static final String GO_CORRUPT_USER_NOT_EXIST =
            "e990a2e384a6e59f9be6b693e5b685e793a8e98da63f";                                   // 鐢ㄦ埛涓嶅瓨鍦?
    private static final String GO_CORRUPT_AVATAR_HISTORY_NOT_EXIST =
            "e6beb6e69d91e5849ae98d98e59797e5bdb6e6b693e5b685e793a8e98da63f";                 // 澶村儚鍘嗗彶涓嶅瓨鍦?
    private static final String GO_CORRUPT_DB_SELECT_FAILED =
            "4442e98f8ce383a8ee8797e6beb6e8beabe8a7a6";                                     // DB鏌ヨ澶辫触
    private static final String GO_CORRUPT_DB_UPDATE_FAILED =
            "4442e98f87e5ad98e69f8ae6beb6e8beabe8a7a6";                                     // DB鏇存柊澶辫触
    private static final String GO_CORRUPT_DB_INSERT_FAILED =
            "4442e98ebbe68e91e58f86e6beb6e8beabe8a7a6";                                     // DB鎻掑叆澶辫触

    // ==================================================================
    // 1. NOT_FOUND 路径 —— 正常中文 + 决策 tripwire
    // ==================================================================

    /**
     * 🔴 6 个「查无此人」入口 + 1 个「头像历史不存在」入口。
     *
     * <p>这 7 处正是 2026-10-24 双侧活体对拍实测到的 <b>4 处 wire 级差异</b>
     * （UploadAvatar / GetAvatarHistory / SelectAvatar 各两处）——
     * 那 4 处的<b>状态码两侧完全相同</b>，只有 description 的字节不同。
     */
    @Test
    @DisplayName("🔴 NOT_FOUND 路径：RAG 吐正常中文，且**不等于** Go 的损坏字节（决策 tripwire）")
    void notFoundMessagesAreChineseAndDifferFromGoCorruptedBytes() {
        long existingUid = seedUser("parity-existing-user");

        // 6 个「查无此人」入口
        assertDescription(call(GetUserResp.class, obs -> grpc.getUser(GetUserReq.newBuilder().setUid(GHOST_UID).build(), obs)),
                Status.Code.NOT_FOUND, "用户不存在", GO_CORRUPT_USER_NOT_EXIST);
        assertDescription(call(DeleteUserResp.class, obs -> grpc.deleteUser(DeleteUserReq.newBuilder().setUid(GHOST_UID).build(), obs)),
                Status.Code.NOT_FOUND, "用户不存在", GO_CORRUPT_USER_NOT_EXIST);
        assertDescription(call(UpdateUserResp.class, obs -> grpc.updateUser(UpdateUserReq.newBuilder()
                        .setUid(GHOST_UID).setUsername("parity-ghost-newname").build(), obs)),
                Status.Code.NOT_FOUND, "用户不存在", GO_CORRUPT_USER_NOT_EXIST);
        assertDescription(call(UploadAvatarResp.class, obs -> grpc.uploadAvatar(UploadAvatarReq.newBuilder()
                        .setUid(GHOST_UID).setAvatarUrl("http://example.test/a.png").build(), obs)),
                Status.Code.NOT_FOUND, "用户不存在", GO_CORRUPT_USER_NOT_EXIST);
        assertDescription(call(GetAvatarHistoryResp.class, obs -> grpc.getAvatarHistory(GetAvatarHistoryReq.newBuilder()
                        .setUid(GHOST_UID).build(), obs)),
                Status.Code.NOT_FOUND, "用户不存在", GO_CORRUPT_USER_NOT_EXIST);
        assertDescription(call(SelectAvatarResp.class, obs -> grpc.selectAvatar(SelectAvatarReq.newBuilder()
                        .setUid(GHOST_UID).setHistoryId("whatever").build(), obs)),
                Status.Code.NOT_FOUND, "用户不存在", GO_CORRUPT_USER_NOT_EXIST);

        // 「头像历史不存在」：用户存在、history 不存在
        assertDescription(call(SelectAvatarResp.class, obs -> grpc.selectAvatar(SelectAvatarReq.newBuilder()
                        .setUid(existingUid).setHistoryId("no-such-history-id").build(), obs)),
                Status.Code.NOT_FOUND, "头像历史不存在", GO_CORRUPT_AVATAR_HISTORY_NOT_EXIST);
    }

    // ==================================================================
    // 2. 未损坏的那些 —— 必须与 Go 原始字节逐字节相同
    // ==================================================================

    /**
     * 🔴 Go 里<b>没有</b>损坏的那些文案：RAG 的 description 必须与 Go 的原始字节<b>逐字节相同</b>。
     *
     * <p>这一组是「对齐」的正面证据 —— 与上一组（tripwire）合起来，
     * 说明 RAG 不是「一律用中文」，而是<b>只在 Go 损坏的那几条上保留正常中文</b>。
     */
    @Test
    @DisplayName("🔴 未损坏的文案：RAG 的 description 与 Go 原始字节**逐字节**相同")
    void normalMessagesMatchGoBytesExactly() {
        long existingUid = seedUser("parity-taken-user");
        // 用户名已存在（Go: registerlogic.go:48 / updateuserlogic.go:39）
        assertDescription(call(CreateUserResp.class, obs -> grpc.register(CreateUserReq.newBuilder()
                        .setUsername("parity-taken-user").setPassword("pw").setEmail("parity-taken@mail.test").build(), obs)),
                Status.Code.ALREADY_EXISTS, "用户名已存在", null);
        // Token不能为空（Go: logoutlogic.go:33）
        assertDescription(call(LogoutResp.class, obs -> grpc.logout(LogoutReq.newBuilder().setToken("").build(), obs)),
                Status.Code.INVALID_ARGUMENT, "Token不能为空", null);
        // avatar_url is required（Go: uploadavatarlogic.go:36）
        assertDescription(call(UploadAvatarResp.class, obs -> grpc.uploadAvatar(UploadAvatarReq.newBuilder()
                        .setUid(existingUid).setAvatarUrl("   ").build(), obs)),
                Status.Code.INVALID_ARGUMENT, "avatar_url is required", null);
        // history_id is required（Go: selectavatarlogic.go:35）
        assertDescription(call(SelectAvatarResp.class, obs -> grpc.selectAvatar(SelectAvatarReq.newBuilder()
                        .setUid(existingUid).setHistoryId("  ").build(), obs)),
                Status.Code.INVALID_ARGUMENT, "history_id is required", null);
    }

    // ==================================================================
    // 3. DB 失败路径 —— 真故障注入 + 正常中文 + 决策 tripwire
    // ==================================================================

    /**
     * 🔴 三条 DB 失败路径，用<b>真故障注入</b>制造（不是 mock）：
     * <ul>
     *   <li><b>读路径</b>：把 {@code users_user} 改名 ⇒ 任何 SELECT 都报「表不存在」
     *       ⇒ {@code getUser} 走 {@code DB查询失败}；</li>
     *   <li><b>用户表写入</b>：给 {@code users_user} 加 CHECK 拦住某个 username
     *       ⇒ {@code register} 的 INSERT 撞约束 ⇒ {@code DB更新失败}；</li>
     *   <li><b>头像历史写入</b>：给 {@code users_user_avatar_history} 加 CHECK 拦住某个 avatar_url
     *       ⇒ {@code uploadAvatar} 的 INSERT 撞约束 ⇒ {@code DB插入失败}。</li>
     * </ul>
     *
     * <p>⚠️ 三条都必须在 finally 里复原（改表名 / 删约束）——
     * 否则同容器里后续用例会踩到被改坏的表。
     */
    @Test
    @DisplayName("🔴 DB 失败路径（真故障注入）：RAG 吐正常中文，且**不等于** Go 的损坏字节")
    void dbFailureMessagesAreChineseAndDifferFromGoCorruptedBytes() {
        long existingUid = seedUser("parity-db-fail-user");

        // ── ① 读路径故障：改名 ⇒ DB查询失败 ──
        jdbcTemplate.execute("ALTER TABLE users_user RENAME TO users_user_parity_tmp");
        try {
            assertDescription(call(GetUserResp.class, obs -> grpc.getUser(GetUserReq.newBuilder().setUid(existingUid).build(), obs)),
                    Status.Code.INTERNAL, "DB查询失败", GO_CORRUPT_DB_SELECT_FAILED);
        } finally {
            jdbcTemplate.execute("ALTER TABLE users_user_parity_tmp RENAME TO users_user");
        }

        // ── ② 用户表写入故障 ⇒ DB更新失败 ──
        jdbcTemplate.execute(
                "ALTER TABLE users_user ADD CONSTRAINT ck_parity_db_update CHECK (username <> 'parity_db_update_failed')");
        try {
            assertDescription(call(CreateUserResp.class, obs -> grpc.register(CreateUserReq.newBuilder()
                            .setUsername("parity_db_update_failed").setPassword("pw")
                            .setEmail("parity_db_update_failed@mail.test").build(), obs)),
                    Status.Code.INTERNAL, "DB更新失败", GO_CORRUPT_DB_UPDATE_FAILED);
        } finally {
            jdbcTemplate.execute("ALTER TABLE users_user DROP CONSTRAINT ck_parity_db_update");
        }

        // ── ③ 头像历史写入故障 ⇒ DB插入失败 ──
        jdbcTemplate.execute(
                "ALTER TABLE users_user_avatar_history ADD CONSTRAINT ck_parity_db_insert CHECK (avatar_url <> 'parity_db_insert_failed')");
        try {
            assertDescription(call(UploadAvatarResp.class, obs -> grpc.uploadAvatar(UploadAvatarReq.newBuilder()
                            .setUid(existingUid).setAvatarUrl("parity_db_insert_failed").build(), obs)),
                    Status.Code.INTERNAL, "DB插入失败", GO_CORRUPT_DB_INSERT_FAILED);
        } finally {
            jdbcTemplate.execute("ALTER TABLE users_user_avatar_history DROP CONSTRAINT ck_parity_db_insert");
        }
    }

    // ==================================================================
    // 辅助
    // ==================================================================

    /**
     * 断言 description 的三层：
     * <ol>
     *   <li>gRPC code 符合预期；</li>
     *   <li>description <b>逐字</b>等于期望的中文；</li>
     *   <li>若给了 {@code goCorruptedHex}：<b>必须不等于</b> Go 的损坏字节（决策 tripwire）。</li>
     * </ol>
     */
    private static void assertDescription(Outcome o, Status.Code expectedCode,
                                          String expectedDesc, String goCorruptedHex) {
        assertNotNull(o.error, "应当失败（预期 gRPC code=" + expectedCode + "）");
        assertEquals(expectedCode, o.error.getStatus().getCode(), "gRPC code 与预期不符");
        String desc = o.error.getStatus().getDescription() == null
                ? "" : o.error.getStatus().getDescription();
        assertEquals(expectedDesc, desc, "RAG 的 description 必须是正常中文");
        if (goCorruptedHex != null) {
            assertNotEquals(decodeHex(goCorruptedHex), desc,
                    "RAG 的 description 不该与 Go 的损坏字节相同。"
                            + "若此断言失败，说明有人已把 RAG 改成复刻 Go 的乱码 —— "
                            + "请先在 docs/DRIFT/users/user/rpc.md §7 更新 N-2 签字结论，再改本条断言");
        }
    }

    /** 插一行真实用户，返回其 uid。 */
    private long seedUser(String username) {
        long uid = Math.abs((long) username.hashCode()) + 7_000_000_000L;
        jdbcTemplate.update("""
                INSERT INTO users_user (uid, username, password, email, status, score, extra_info)
                VALUES (?, ?, 'x', ?, 0, 0, NULL)
                ON CONFLICT (uid) DO NOTHING
                """, uid, username, username + "@mail.test");
        return uid;
    }

    /** 收集 gRPC 调用的结果（成功取 value，失败取 error）。 */
    private static final class Outcome {
        Object value;
        StatusRuntimeException error;
    }

    /** 极简 StreamObserver：把 onError / onNext 收进 Outcome。 */
    private static final class Recorder<T> implements StreamObserver<T> {
        final Outcome outcome = new Outcome();

        @Override public void onNext(T value) { outcome.value = value; }

        @Override public void onError(Throwable t) {
            outcome.error = (t instanceof StatusRuntimeException sre)
                    ? sre
                    : Status.INTERNAL.withDescription(String.valueOf(t)).asRuntimeException();
        }

        @Override public void onCompleted() { /* 结果已由 onNext/onError 记录 */ }
    }

    /** 触发一次 gRPC 调用并回收结果。 */
    private static <T> Outcome call(Class<T> respType,
                                   java.util.function.Consumer<StreamObserver<T>> invoker) {
        Recorder<T> rec = new Recorder<>();
        invoker.accept(rec);
        return rec.outcome;
    }

    /** hex → 字符串（UTF-8）。 */
    private static String decodeHex(String hex) {
        byte[] raw = new byte[hex.length() / 2];
        for (int i = 0; i < raw.length; i++) {
            raw[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return new String(raw, java.nio.charset.StandardCharsets.UTF_8);
    }
}
