/*
 * ════════════════════════════════════════════════════════════════════════════
 *  GrpcUserNineRpcDualPortRunner — UNRESOLVED-USERRPC-007 的执行主体
 * ════════════════════════════════════════════════════════════════════════════
 *
 * 【它补的是哪个缺口】
 *   docs/DRIFT/users/user/rpc.md 的 §9 表里写着：
 *     「UNRESOLVED-USERRPC-007：双侧活体对拍从未在本组件上跑过 ——
 *       grpc-dual-port-runner.java 只覆盖 4 档 status code，
 *       **9 个 RPC 的字段级响应、error message、边界输入全部未对拍**；
 *       本轮 91 条测试**全是 RAG 单侧**。」
 *   ⇒ 本文件把那三项补齐：**9 个 RPC 全覆盖** + **字段级响应** +
 *     **error message 逐字** + **边界输入**。
 *
 * 【与既有 grpc-dual-port-runner.java 的关系】
 *   既有那个是 **4 档 status code** 的 E-2 动态半（§6.2.6），
 *   覆盖面窄但结论明确。本文件**不替换**它，而是把范围扩到 9 个 RPC 的全字段。
 *   断言方式也不同：既有那个只比 `Status.getCode()`，
 *   本文件**还比 `Status.getDescription()`**（gRPC 的 `grpc-message` 头，
 *   **wire 可见**）与响应体字段。
 *
 * 【为什么「error message 逐字」值得单列一档】
 *   gRPC 的错误文案走 `grpc-message` 头，**客户端能直接看到**。
 *   ⇒ 两边文案不同 = 客户端看到的内容不同 = **可观察差异**，
 *   而且它**不会**被任何状态码断言发现。
 *
 * 【两侧状态如何隔离】
 *   Go 与 RAG 各连**自己的库**（uu_go_db / uu_rag_db），两侧表名也不同
 *   （Go `users` vs RAG `users_user`）⇒ 同一套输入在两侧产生同构但独立的状态。
 *   ⇒ 本文件对**每个场景**在两侧各跑一遍，再比对两侧的观测结果。
 *   ⚠️ **跑之前本文件自己清空两侧业务表**（resetState）——
 *     否则「空用户名 / 空邮箱」两个场景会被上一轮遗留的 '' 行撞上 UNIQUE 约束，
 *     制造出 2 处**假漂移**（2026-10-24 实测踩过，见 resetState 的 javadoc）。
 *
 * 【字段归一化的约定】
 *   - 普通字段（found / success / username / email / extra_info /
 *     avatar_url / history.* …）⇒ **两侧必须逐字相同**；
 *   - 带 `~` 前缀的字段（uid / token / history.id / create_time）⇒
 *     **每侧自有**（雪花 ID / JWT / 时间戳），只断言「存在且非空」，
 *     不比对字面量；
 *   - `user.has_status` ⇒ 专测 proto3 `optional` 字段的**存在性**
 *     （Go 的 GetUser 会填 `Status`，UpdateUser **不填** ⇒ 「有没有这个字段」
 *     本身就是 wire 级差异）。
 *
 * 【输入（-Dproperty=... 系统属性）】
 *   go.user.port  默认 19004   rag.user.port 默认 59210
 *   go.host / rag.host 默认 127.0.0.1
 *   go.db.url  默认 jdbc:postgresql://127.0.0.1:55434/uu_go_db
 *   rag.db.url 默认 jdbc:postgresql://127.0.0.1:55434/uu_rag_db
 *   db.user / db.password 默认 admin / difftest
 *   cleanup    默认 true —— 跑前清空两侧业务表（**幂等的关键**）
 *                     置 false 可跳过（⚠️ 那会产生假漂移，不要在正式对拍时用）
 *
 * 【运行方式】
 *   PROTO_CP=service/users/user/user-proto/target/classes
 *   TEST_CP=$(mvn -q -pl service/users/user/user-rpc dependency:build-classpath \
 *               -Dmdep.outputFile=/tmp/cp.txt 2>/dev/null && cat /tmp/cp.txt)
 *   mkdir -p /tmp/build
 *   javac -d /tmp/build tools/difftest/grpc-user9-dual-port-runner.java -cp "$PROTO_CP:$TEST_CP"
 *   java -cp "/tmp/build:$PROTO_CP:$TEST_CP" \
 *        -Dgo.user.port=19005 -Drag.user.port=59211 tools.difftest.GrpcUserNineRpcDualPortRunner
 *
 * 【输出】
 *   每个场景一行：[✓|✗] <场景>  go=<code>  rag=<code>，差异逐条列在其下
 *   末尾：场景数 + 差异数 + 进程 exit code（0 = 全等价，1 = 有差异）
 */
package tools.difftest;

import com.rag.users.user.proto.AvatarHistoryItem;
import com.rag.users.user.proto.CreateUserReq;
import com.rag.users.user.proto.DeleteUserReq;
import com.rag.users.user.proto.GetAvatarHistoryReq;
import com.rag.users.user.proto.GetUserReq;
import com.rag.users.user.proto.LoginReq;
import com.rag.users.user.proto.LogoutReq;
import com.rag.users.user.proto.SelectAvatarReq;
import com.rag.users.user.proto.UpdateUserReq;
import com.rag.users.user.proto.UploadAvatarReq;
import com.rag.users.user.proto.UserInfo;
import com.rag.users.user.proto.UserServiceGrpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

final class GrpcUserNineRpcDualPortRunner {

    private static final String GO_HOST  = sys("go.host",  "127.0.0.1");
    private static final String RAG_HOST = sys("rag.host", "127.0.0.1");
    private static final int    GO_PORT  = Integer.parseInt(sys("go.user.port",  "19004"));
    private static final int    RAG_PORT = Integer.parseInt(sys("rag.user.port", "59210"));

    /** 绝不存在的 uid —— 用于「查无此人」分支。 */
    private static final long GHOST_UID = 99_999_999_999L;

    /**
     * 状态清理（幂等化）—— 详见 {@link #resetState()} 的 javadoc。
     * 默认值与 tools/difftest/exp-difftest-grpc-user9.sh 占用的资源一致。
     */
    private static final String GO_DB_URL  = sys("go.db.url",  "jdbc:postgresql://127.0.0.1:55434/uu_go_db");
    private static final String RAG_DB_URL = sys("rag.db.url", "jdbc:postgresql://127.0.0.1:55434/uu_rag_db");
    private static final String DB_USER    = sys("db.user", "admin");
    private static final String DB_PASS    = sys("db.password", "difftest");
    /**
     * 两侧各自的业务表（**表名本来就不同**：Go 是 users，RAG 是 users_user）。
     * 顺序＝先子表后父表（头像历史表引用用户表），避免外键报错。
     */
    private static final String[] GO_TABLES  = {"user_avatar_history", "users"};
    private static final String[] RAG_TABLES = {"users_user_avatar_history", "users_user"};

    private static ManagedChannel goCh;
    private static ManagedChannel ragCh;
    private static int cases = 0;
    private static int diffs = 0;

    /** 每侧独立的状态（uid / history id 两侧不同）。 */
    static final class SideCtx {
        long uid;
        String historyId = "";
    }

    /** 一次调用的观测结果。 */
    static final class Obs {
        Status.Code code = Status.Code.OK;
        String desc = "";
        final Map<String, String> fields = new LinkedHashMap<>();
    }

    /** 一个场景在某一侧要做什么，并把观测到的字段填进 Obs。 */
    interface Step {
        void run(UserServiceGrpc.UserServiceBlockingStub stub, Obs o) throws Exception;
    }

    private GrpcUserNineRpcDualPortRunner() {}

    /**
     * ════════════════════════════════════════════════════════════════════════
     *  resetState() —— 跑之前把两侧的业务表清空（**幂等化的关键**）
     * ════════════════════════════════════════════════════════════════════════
     *
     * 【为什么必须有这一步 —— 2026-10-24 实测踩出来的假漂移】
     *   本对拍里有**两个场景的输入不是「按 run 唯一」的**：
     *     · 「Register: 空用户名」⇒ username = ''   （每次都一样）
     *     · 「Register: 空邮箱」  ⇒ email    = ''   （每次都一样）
     *   而两侧的用户表上都有 UNIQUE(username) / UNIQUE(email)。
     *   ⇒ 上一轮跑完留下的 '' 行，会让下一轮的这个场景变成：
     *       Go 侧：库里没有 ''      ⇒ 插入成功 ⇒ OK
     *       RAG 侧：还留着上一轮的 '' ⇒ 命中 UNIQUE ⇒ 报错
     *   ⇒ **同一套输入、同一份代码，第二次跑却多出 2 处「漂移」** ——
     *     而那 2 处**根本不是产品差异**，是脚本的状态残留。
     *
     * 【实测证据（2026-10-24，本组件首轮 U-007 对拍）】
     *   第一轮（uu_rag_db 里有上一轮遗留的 username='' 与 email=''）：
     *       Register: 空用户名   go=OK  rag=ALREADY_EXISTS   ← 假漂移
     *       Register: 空邮箱     go=OK  rag=INTERNAL          ← 假漂移
     *   两侧同时清空后复跑：43 场景 / **4 处**差异（只剩 Go 侧乱码文案）
     *   ⇒ 那 2 处是**假阳性**；顺带**反证**了两侧 UNIQUE 约束的行为是一致的。
     *
     * 【为什么在这里清，而不是靠脚本 DROP DATABASE】
     *   脚本确实会 DROP/CREATE 库，但那只保证「按脚本跑」这一条路径是干净的；
     *   而实际调试最常见的用法是「复用已起好的栈，只重跑 runner」——
     *   那条路径下状态一路累积，于是**第二次跑出来的差异是假的**。
     *   ⇒ 清状态的责任必须落在 **runner 自己**身上，否则它只在一种用法下正确。
     *
     * 【安全边界】
     *   只删**两侧自己的库**（默认 uu_go_db / uu_rag_db）里的**业务表**；
     *   不碰共享的 go_db / rag_db，不碰 admin / message 域的表。
     */
    private static void resetState() {
        if (!Boolean.parseBoolean(sys("cleanup", "true"))) {
            System.out.println("  [cleanup] 已按 -Dcleanup=false 跳过状态清理（⚠️ 重跑会产生假漂移）");
            return;
        }
        try {
            int go  = wipe(GO_DB_URL, GO_TABLES);
            int rag = wipe(RAG_DB_URL, RAG_TABLES);
            System.out.println("  [cleanup] 两侧业务表已清空：go=" + go + " 行, rag=" + rag + " 行");
        } catch (Exception e) {
            System.out.println("  [cleanup] ⚠️ 清理失败（继续跑，但结果可能含假漂移）：" + e);
        }
    }

    /** 清空给定库里的给定业务表；表不存在则跳过。返回删除的行数。 */
    private static int wipe(String url, String[] tables) throws Exception {
        int total = 0;
        try (Connection c = DriverManager.getConnection(url, DB_USER, DB_PASS);
             Statement st = c.createStatement()) {
            for (String t : tables) {
                try (ResultSet rs = st.executeQuery("select to_regclass('public." + t + "')")) {
                    if (rs.next() && rs.getString(1) == null) continue;   // 表不存在 ⇒ 跳过
                }
                total += st.executeUpdate("delete from " + t);
            }
        }
        return total;
    }

    public static void main(String[] args) throws Exception {
        final String uniq = "uu_e2e_" + System.currentTimeMillis() + "_"
                + Long.toHexString(UUID.randomUUID().hashCode() & 0xffffffffL);

        System.out.println("== UNRESOLVED-USERRPC-007：9 个 RPC 的字段级 + error message 逐字 + 边界输入 ==");
        System.out.printf("  go  user-rpc = %s:%d%n", GO_HOST, GO_PORT);
        System.out.printf("  rag user-rpc = %s:%d%n", RAG_HOST, RAG_PORT);
        System.out.println("  unique username = " + uniq);
        System.out.println();

        // ⚠️ 必须先清状态再跑 —— 否则「空用户名 / 空邮箱」两个场景会被
        //    上一轮遗留的 '' 行撞上 UNIQUE 约束，制造出 2 处**假漂移**。
        //    详见 resetState() 的 javadoc。
        resetState();
        System.out.println();

        goCh  = ManagedChannelBuilder.forAddress(GO_HOST, GO_PORT).usePlaintext().build();
        ragCh = ManagedChannelBuilder.forAddress(RAG_HOST, RAG_PORT).usePlaintext().build();

        SideCtx goCtx = new SideCtx();
        SideCtx ragCtx = new SideCtx();

        try {
            // ═══════════ 1. Register ═══════════
            scenario("Register: 新用户名（happy）", Status.Code.OK,
                    (s, o) -> {
                        long uid = s.register(CreateUserReq.newBuilder()
                                .setUsername(uniq).setPassword("pw123456")
                                .setEmail(uniq + "@example.com")
                                .putExtraInfo("k1", "v1").putExtraInfo("k2", "v2")
                                .build()).getUid();
                        goCtx.uid = uid;
                        o.fields.put("~uid", String.valueOf(uid));
                    },
                    (s, o) -> {
                        long uid = s.register(CreateUserReq.newBuilder()
                                .setUsername(uniq).setPassword("pw123456")
                                .setEmail(uniq + "@example.com")
                                .putExtraInfo("k1", "v1").putExtraInfo("k2", "v2")
                                .build()).getUid();
                        ragCtx.uid = uid;
                        o.fields.put("~uid", String.valueOf(uid));
                    });

            scenario("Register: 同一用户名再注册（重复）", Status.Code.ALREADY_EXISTS,
                    (s, o) -> s.register(CreateUserReq.newBuilder().setUsername(uniq)
                            .setPassword("pw2").setEmail(uniq + "@example.com").build()),
                    (s, o) -> s.register(CreateUserReq.newBuilder().setUsername(uniq)
                            .setPassword("pw2").setEmail(uniq + "@example.com").build()));

            scenario("Register: 空用户名（边界）", null,
                    (s, o) -> s.register(CreateUserReq.newBuilder().setUsername("")
                            .setPassword("pw").setEmail(uniq + "_emptyuser@e").build()),
                    (s, o) -> s.register(CreateUserReq.newBuilder().setUsername("")
                            .setPassword("pw").setEmail(uniq + "_emptyuser@e").build()));

            scenario("Register: 空密码（边界）", null,
                    (s, o) -> s.register(CreateUserReq.newBuilder().setUsername(uniq + "_np")
                            .setPassword("").setEmail(uniq + "_np@e").build()),
                    (s, o) -> s.register(CreateUserReq.newBuilder().setUsername(uniq + "_np")
                            .setPassword("").setEmail(uniq + "_np@e").build()));

            scenario("Register: 空邮箱（边界）", null,
                    (s, o) -> s.register(CreateUserReq.newBuilder().setUsername(uniq + "_ne")
                            .setPassword("pw").setEmail("").build()),
                    (s, o) -> s.register(CreateUserReq.newBuilder().setUsername(uniq + "_ne")
                            .setPassword("pw").setEmail("").build()));

            scenario("Register: extra_info 空串值（边界）", null,
                    (s, o) -> s.register(CreateUserReq.newBuilder().setUsername(uniq + "_xe")
                            .setPassword("pw").setEmail(uniq + "_xe@e").putExtraInfo("empty", "").build()),
                    (s, o) -> s.register(CreateUserReq.newBuilder().setUsername(uniq + "_xe")
                            .setPassword("pw").setEmail(uniq + "_xe@e").putExtraInfo("empty", "").build()));

            // ═══════════ 2. Login ═══════════
            scenario("Login: 正确口令（happy；业务码在 status 里）", Status.Code.OK,
                    (s, o) -> {
                        var r = s.login(LoginReq.newBuilder().setUsername(uniq).setPassword("pw123456").build());
                        o.fields.put("status", String.valueOf(r.getStatus()));
                        o.fields.put("~uid", String.valueOf(r.getUid()));
                    },
                    (s, o) -> {
                        var r = s.login(LoginReq.newBuilder().setUsername(uniq).setPassword("pw123456").build());
                        o.fields.put("status", String.valueOf(r.getStatus()));
                        o.fields.put("~uid", String.valueOf(r.getUid()));
                    });

            scenario("Login: 错口令（业务码 1，非 gRPC 错误）", Status.Code.OK,
                    (s, o) -> o.fields.put("status", String.valueOf(s.login(LoginReq.newBuilder()
                            .setUsername(uniq).setPassword("wrong-pw").build()).getStatus())),
                    (s, o) -> o.fields.put("status", String.valueOf(s.login(LoginReq.newBuilder()
                            .setUsername(uniq).setPassword("wrong-pw").build()).getStatus())));

            scenario("Login: 用户不存在（业务码 1）", Status.Code.OK,
                    (s, o) -> o.fields.put("status", String.valueOf(s.login(LoginReq.newBuilder()
                            .setUsername(uniq + "_ghost").setPassword("pw").build()).getStatus())),
                    (s, o) -> o.fields.put("status", String.valueOf(s.login(LoginReq.newBuilder()
                            .setUsername(uniq + "_ghost").setPassword("pw").build()).getStatus())));

            scenario("Login: 空用户名（边界）", null,
                    (s, o) -> s.login(LoginReq.newBuilder().setUsername("").setPassword("pw").build()),
                    (s, o) -> s.login(LoginReq.newBuilder().setUsername("").setPassword("pw").build()));

            scenario("Login: 空密码（边界）", null,
                    (s, o) -> s.login(LoginReq.newBuilder().setUsername(uniq).setPassword("").build()),
                    (s, o) -> s.login(LoginReq.newBuilder().setUsername(uniq).setPassword("").build()));

            // ═══════════ 3. GetUser ═══════════
            scenario("GetUser: 存在（happy；字段级 + optional status 存在性）", Status.Code.OK,
                    (s, o) -> {
                        var r = s.getUser(GetUserReq.newBuilder().setUid(goCtx.uid).build());
                        putUser(o, "user", r.getUser());
                        o.fields.put("found", String.valueOf(r.getFound()));
                    },
                    (s, o) -> {
                        var r = s.getUser(GetUserReq.newBuilder().setUid(ragCtx.uid).build());
                        putUser(o, "user", r.getUser());
                        o.fields.put("found", String.valueOf(r.getFound()));
                    });

            scenario("GetUser: 查无此人（NOT_FOUND + 文案逐字）", Status.Code.NOT_FOUND,
                    (s, o) -> s.getUser(GetUserReq.newBuilder().setUid(GHOST_UID).build()),
                    (s, o) -> s.getUser(GetUserReq.newBuilder().setUid(GHOST_UID).build()));

            scenario("GetUser: uid=0（边界）", null,
                    (s, o) -> s.getUser(GetUserReq.newBuilder().setUid(0).build()),
                    (s, o) -> s.getUser(GetUserReq.newBuilder().setUid(0).build()));

            scenario("GetUser: uid=-1（边界）", null,
                    (s, o) -> s.getUser(GetUserReq.newBuilder().setUid(-1).build()),
                    (s, o) -> s.getUser(GetUserReq.newBuilder().setUid(-1).build()));

            // ═══════════ 4. UpdateUser ═══════════
            scenario("UpdateUser: 改邮箱 + extra_info（字段级）", Status.Code.OK,
                    (s, o) -> {
                        var r = s.updateUser(UpdateUserReq.newBuilder().setUid(goCtx.uid)
                                .setEmail(uniq + "@updated.com").putExtraInfo("k3", "v3").build());
                        putUser(o, "user", r.getUser());
                    },
                    (s, o) -> {
                        var r = s.updateUser(UpdateUserReq.newBuilder().setUid(ragCtx.uid)
                                .setEmail(uniq + "@updated.com").putExtraInfo("k3", "v3").build());
                        putUser(o, "user", r.getUser());
                    });

            // 先注册第二个用户，制造一个**真实存在**的冲突用户名
            scenario("Register: 第二个用户（为 UpdateUser 冲突分支铺路）", Status.Code.OK,
                    (s, o) -> {
                        long uid = s.register(CreateUserReq.newBuilder()
                                .setUsername(uniq + "_taken").setPassword("pw123456")
                                .setEmail(uniq + "_taken@example.com").build()).getUid();
                        o.fields.put("~uid", String.valueOf(uid));
                    },
                    (s, o) -> {
                        long uid = s.register(CreateUserReq.newBuilder()
                                .setUsername(uniq + "_taken").setPassword("pw123456")
                                .setEmail(uniq + "_taken@example.com").build()).getUid();
                        o.fields.put("~uid", String.valueOf(uid));
                    });

            scenario("UpdateUser: 改成一个**已存在**的用户名（冲突）", Status.Code.ALREADY_EXISTS,
                    (s, o) -> s.updateUser(UpdateUserReq.newBuilder().setUid(goCtx.uid)
                            .setUsername(uniq + "_taken").build()),
                    (s, o) -> s.updateUser(UpdateUserReq.newBuilder().setUid(ragCtx.uid)
                            .setUsername(uniq + "_taken").build()));

            scenario("UpdateUser: 查无此人（边界）", null,
                    (s, o) -> s.updateUser(UpdateUserReq.newBuilder().setUid(GHOST_UID)
                            .setEmail("x@x").build()),
                    (s, o) -> s.updateUser(UpdateUserReq.newBuilder().setUid(GHOST_UID)
                            .setEmail("x@x").build()));

            scenario("UpdateUser: 全空（边界=不改任何字段）", null,
                    (s, o) -> s.updateUser(UpdateUserReq.newBuilder().setUid(goCtx.uid).build()),
                    (s, o) -> s.updateUser(UpdateUserReq.newBuilder().setUid(ragCtx.uid).build()));

            scenario("UpdateUser: 空 extra_info map（边界）", null,
                    (s, o) -> s.updateUser(UpdateUserReq.newBuilder().setUid(goCtx.uid)
                            .setEmail(uniq + "@e2.com").build()),
                    (s, o) -> s.updateUser(UpdateUserReq.newBuilder().setUid(ragCtx.uid)
                            .setEmail(uniq + "@e2.com").build()));

            // ═══════════ 5. UploadAvatar ═══════════
            scenario("UploadAvatar: happy（字段级）", Status.Code.OK,
                    (s, o) -> {
                        var r = s.uploadAvatar(UploadAvatarReq.newBuilder().setUid(goCtx.uid)
                                .setAvatarUrl("http://cdn/a.png").setContentType("image/png")
                                .setSizeBytes(1234).build());
                        o.fields.put("avatar_url", r.getAvatarUrl());
                        putHistory(o, "history", r.getHistory());
                        goCtx.historyId = r.getHistory().getId();
                    },
                    (s, o) -> {
                        var r = s.uploadAvatar(UploadAvatarReq.newBuilder().setUid(ragCtx.uid)
                                .setAvatarUrl("http://cdn/a.png").setContentType("image/png")
                                .setSizeBytes(1234).build());
                        o.fields.put("avatar_url", r.getAvatarUrl());
                        putHistory(o, "history", r.getHistory());
                        ragCtx.historyId = r.getHistory().getId();
                    });

            scenario("UploadAvatar: 空 avatar_url（边界）", Status.Code.INVALID_ARGUMENT,
                    (s, o) -> s.uploadAvatar(UploadAvatarReq.newBuilder().setUid(goCtx.uid)
                            .setAvatarUrl("").build()),
                    (s, o) -> s.uploadAvatar(UploadAvatarReq.newBuilder().setUid(ragCtx.uid)
                            .setAvatarUrl("").build()));

            scenario("UploadAvatar: 纯空白 avatar_url（边界）", null,
                    (s, o) -> s.uploadAvatar(UploadAvatarReq.newBuilder().setUid(goCtx.uid)
                            .setAvatarUrl("   ").build()),
                    (s, o) -> s.uploadAvatar(UploadAvatarReq.newBuilder().setUid(ragCtx.uid)
                            .setAvatarUrl("   ").build()));

            scenario("UploadAvatar: 查无此人（边界）", null,
                    (s, o) -> s.uploadAvatar(UploadAvatarReq.newBuilder().setUid(GHOST_UID)
                            .setAvatarUrl("http://cdn/b.png").build()),
                    (s, o) -> s.uploadAvatar(UploadAvatarReq.newBuilder().setUid(GHOST_UID)
                            .setAvatarUrl("http://cdn/b.png").build()));

            scenario("UploadAvatar: 负 size_bytes（边界）", null,
                    (s, o) -> s.uploadAvatar(UploadAvatarReq.newBuilder().setUid(goCtx.uid)
                            .setAvatarUrl("http://cdn/c.png").setContentType("image/png")
                            .setSizeBytes(-1).build()),
                    (s, o) -> s.uploadAvatar(UploadAvatarReq.newBuilder().setUid(ragCtx.uid)
                            .setAvatarUrl("http://cdn/c.png").setContentType("image/png")
                            .setSizeBytes(-1).build()));

            scenario("UploadAvatar: 空 content_type（边界）", null,
                    (s, o) -> s.uploadAvatar(UploadAvatarReq.newBuilder().setUid(goCtx.uid)
                            .setAvatarUrl("http://cdn/d.png").build()),
                    (s, o) -> s.uploadAvatar(UploadAvatarReq.newBuilder().setUid(ragCtx.uid)
                            .setAvatarUrl("http://cdn/d.png").build()));

            // ═══════════ 6. GetAvatarHistory ═══════════
            scenario("GetAvatarHistory: happy（列表长度 + 首项字段）", Status.Code.OK,
                    (s, o) -> {
                        var r = s.getAvatarHistory(GetAvatarHistoryReq.newBuilder()
                                .setUid(goCtx.uid).setLimit(10).build());
                        o.fields.put("count", String.valueOf(r.getListCount()));
                        if (r.getListCount() > 0) {
                            putHistory(o, "item0", r.getList(0));
                        }
                    },
                    (s, o) -> {
                        var r = s.getAvatarHistory(GetAvatarHistoryReq.newBuilder()
                                .setUid(ragCtx.uid).setLimit(10).build());
                        o.fields.put("count", String.valueOf(r.getListCount()));
                        if (r.getListCount() > 0) {
                            putHistory(o, "item0", r.getList(0));
                        }
                    });

            scenario("GetAvatarHistory: 查无此人（边界）", null,
                    (s, o) -> s.getAvatarHistory(GetAvatarHistoryReq.newBuilder()
                            .setUid(GHOST_UID).setLimit(10).build()),
                    (s, o) -> s.getAvatarHistory(GetAvatarHistoryReq.newBuilder()
                            .setUid(GHOST_UID).setLimit(10).build()));

            scenario("GetAvatarHistory: limit=0（边界）", null,
                    (s, o) -> {
                        var r = s.getAvatarHistory(GetAvatarHistoryReq.newBuilder()
                                .setUid(goCtx.uid).setLimit(0).build());
                        o.fields.put("count", String.valueOf(r.getListCount()));
                    },
                    (s, o) -> {
                        var r = s.getAvatarHistory(GetAvatarHistoryReq.newBuilder()
                                .setUid(ragCtx.uid).setLimit(0).build());
                        o.fields.put("count", String.valueOf(r.getListCount()));
                    });

            scenario("GetAvatarHistory: limit=-1（边界）", null,
                    (s, o) -> {
                        var r = s.getAvatarHistory(GetAvatarHistoryReq.newBuilder()
                                .setUid(goCtx.uid).setLimit(-1).build());
                        o.fields.put("count", String.valueOf(r.getListCount()));
                    },
                    (s, o) -> {
                        var r = s.getAvatarHistory(GetAvatarHistoryReq.newBuilder()
                                .setUid(ragCtx.uid).setLimit(-1).build());
                        o.fields.put("count", String.valueOf(r.getListCount()));
                    });

            scenario("GetAvatarHistory: limit=9999（边界）", null,
                    (s, o) -> {
                        var r = s.getAvatarHistory(GetAvatarHistoryReq.newBuilder()
                                .setUid(goCtx.uid).setLimit(9999).build());
                        o.fields.put("count", String.valueOf(r.getListCount()));
                    },
                    (s, o) -> {
                        var r = s.getAvatarHistory(GetAvatarHistoryReq.newBuilder()
                                .setUid(ragCtx.uid).setLimit(9999).build());
                        o.fields.put("count", String.valueOf(r.getListCount()));
                    });

            // ═══════════ 7. SelectAvatar ═══════════
            scenario("SelectAvatar: happy（字段级）", Status.Code.OK,
                    (s, o) -> {
                        var r = s.selectAvatar(SelectAvatarReq.newBuilder().setUid(goCtx.uid)
                                .setHistoryId(goCtx.historyId).build());
                        o.fields.put("avatar_url", r.getAvatarUrl());
                        putHistory(o, "history", r.getHistory());
                    },
                    (s, o) -> {
                        var r = s.selectAvatar(SelectAvatarReq.newBuilder().setUid(ragCtx.uid)
                                .setHistoryId(ragCtx.historyId).build());
                        o.fields.put("avatar_url", r.getAvatarUrl());
                        putHistory(o, "history", r.getHistory());
                    });

            scenario("SelectAvatar: 空 history_id（边界）", Status.Code.INVALID_ARGUMENT,
                    (s, o) -> s.selectAvatar(SelectAvatarReq.newBuilder().setUid(goCtx.uid)
                            .setHistoryId("").build()),
                    (s, o) -> s.selectAvatar(SelectAvatarReq.newBuilder().setUid(ragCtx.uid)
                            .setHistoryId("").build()));

            scenario("SelectAvatar: 纯空白 history_id（边界）", null,
                    (s, o) -> s.selectAvatar(SelectAvatarReq.newBuilder().setUid(goCtx.uid)
                            .setHistoryId("   ").build()),
                    (s, o) -> s.selectAvatar(SelectAvatarReq.newBuilder().setUid(ragCtx.uid)
                            .setHistoryId("   ").build()));

            scenario("SelectAvatar: 不存在的 history_id（边界）", null,
                    (s, o) -> s.selectAvatar(SelectAvatarReq.newBuilder().setUid(goCtx.uid)
                            .setHistoryId("1234567890").build()),
                    (s, o) -> s.selectAvatar(SelectAvatarReq.newBuilder().setUid(ragCtx.uid)
                            .setHistoryId("1234567890").build()));

            scenario("SelectAvatar: 查无此人（边界）", null,
                    (s, o) -> s.selectAvatar(SelectAvatarReq.newBuilder().setUid(GHOST_UID)
                            .setHistoryId("1234567890").build()),
                    (s, o) -> s.selectAvatar(SelectAvatarReq.newBuilder().setUid(GHOST_UID)
                            .setHistoryId("1234567890").build()));

            // ═══════════ 8. Logout ═══════════
            scenario("Logout: happy（字段级）", Status.Code.OK,
                    (s, o) -> o.fields.put("success",
                            String.valueOf(s.logout(LogoutReq.newBuilder()
                                    .setToken(uniq + "-tok").build()).getSuccess())),
                    (s, o) -> o.fields.put("success",
                            String.valueOf(s.logout(LogoutReq.newBuilder()
                                    .setToken(uniq + "-tok").build()).getSuccess())));

            scenario("Logout: 空 token（边界）", Status.Code.INVALID_ARGUMENT,
                    (s, o) -> s.logout(LogoutReq.newBuilder().setToken("").build()),
                    (s, o) -> s.logout(LogoutReq.newBuilder().setToken("").build()));

            scenario("Logout: 纯空白 token（边界）", null,
                    (s, o) -> s.logout(LogoutReq.newBuilder().setToken("   ").build()),
                    (s, o) -> s.logout(LogoutReq.newBuilder().setToken("   ").build()));

            // ═══════════ 9. DeleteUser ═══════════
            scenario("DeleteUser: happy（字段级）", Status.Code.OK,
                    (s, o) -> o.fields.put("success",
                            String.valueOf(s.deleteUser(DeleteUserReq.newBuilder()
                                    .setUid(goCtx.uid).build()).getSuccess())),
                    (s, o) -> o.fields.put("success",
                            String.valueOf(s.deleteUser(DeleteUserReq.newBuilder()
                                    .setUid(ragCtx.uid).build()).getSuccess())));

            scenario("DeleteUser: 查无此人（边界）", null,
                    (s, o) -> s.deleteUser(DeleteUserReq.newBuilder().setUid(GHOST_UID).build()),
                    (s, o) -> s.deleteUser(DeleteUserReq.newBuilder().setUid(GHOST_UID).build()));

            scenario("DeleteUser: uid=0（边界）", null,
                    (s, o) -> s.deleteUser(DeleteUserReq.newBuilder().setUid(0).build()),
                    (s, o) -> s.deleteUser(DeleteUserReq.newBuilder().setUid(0).build()));
        } finally {
            goCh.shutdownNow();
            ragCh.shutdownNow();
        }

        System.out.println();
        System.out.printf("场景数=%d  差异=%d%n", cases, diffs);
        if (diffs == 0) {
            System.out.println("✓ UNRESOLVED-USERRPC-007：9 个 RPC 全字段 + error message 逐字，双端等价");
            System.exit(0);
        } else {
            System.out.println("✗ UNRESOLVED-USERRPC-007：发现 " + diffs + " 处双端差异（详见上方输出）");
            System.exit(1);
        }
    }

    // ── 执行与比对 ────────────────────────────────────────────────────────

    private static void scenario(String label, Status.Code expected, Step go, Step rag) {
        cases++;
        Obs o1 = observe(goCh, go);
        Obs o2 = observe(ragCh, rag);
        List<String> notes = compare(o1, o2, expected);
        boolean ok = notes.isEmpty();
        if (!ok) {
            diffs++;
        }
        System.out.printf("  %s %-58s go=%-18s rag=%-18s%n",
                ok ? "✓" : "✗", label, o1.code, o2.code);
        for (String n : notes) {
            System.out.println("        · " + n);
        }
    }

    private static Obs observe(ManagedChannel ch, Step step) {
        Obs o = new Obs();
        try {
            step.run(UserServiceGrpc.newBlockingStub(ch), o);
            o.code = Status.Code.OK;
        } catch (StatusRuntimeException e) {
            o.code = e.getStatus().getCode();
            o.desc = e.getStatus().getDescription() == null ? "" : e.getStatus().getDescription();
        } catch (Exception e) {
            o.code = Status.Code.UNKNOWN;
            o.desc = "EXCEPTION:" + e;
        }
        return o;
    }

    private static List<String> compare(Obs o1, Obs o2, Status.Code expected) {
        List<String> notes = new ArrayList<>();
        if (o1.code != o2.code) {
            notes.add("状态码 go=" + o1.code + " rag=" + o2.code);
        }
        if (expected != null && o1.code != expected) {
            notes.add("go 不等于期望 " + expected);
        }
        if (expected != null && o2.code != expected) {
            notes.add("rag 不等于期望 " + expected);
        }
        if (!o1.desc.equals(o2.desc)) {
            notes.add("错误文案不一致 go=[" + o1.desc + "] rag=[" + o2.desc + "]"
                    + "  goHex=" + hex(o1.desc) + " ragHex=" + hex(o2.desc));
        }
        for (Map.Entry<String, String> e : o1.fields.entrySet()) {
            String k = e.getKey();
            String v2 = o2.fields.get(k);
            if (k.startsWith("~")) {
                if (v2 == null || v2.isEmpty()) {
                    notes.add("每侧自有字段 " + k + " 在 rag 侧为空");
                }
                if (e.getValue() == null || e.getValue().isEmpty()) {
                    notes.add("每侧自有字段 " + k + " 在 go 侧为空");
                }
            } else if (!e.getValue().equals(v2)) {
                notes.add("字段 " + k + " 不一致 go=[" + e.getValue() + "] rag=[" + v2 + "]");
            }
        }
        for (String k : o2.fields.keySet()) {
            if (!o1.fields.containsKey(k)) {
                notes.add("字段集不一致：rag 有 " + k + "，go 没有");
            }
        }
        return notes;
    }

    // ── 字段提取 ──────────────────────────────────────────────────────────

    private static void putUser(Obs o, String prefix, UserInfo u) {
        if (u == null) {
            o.fields.put(prefix + ".present", "false");
            return;
        }
        o.fields.put(prefix + ".present", "true");
        o.fields.put("~" + prefix + ".uid", String.valueOf(u.getUid()));
        o.fields.put(prefix + ".username", u.getUsername());
        o.fields.put(prefix + ".email", u.getEmail());
        o.fields.put(prefix + ".score", String.valueOf(u.getScore()));
        // proto3 optional int64 status —— presence 本身是 wire 级可观察的
        o.fields.put(prefix + ".has_status", String.valueOf(u.hasStatus()));
        o.fields.put(prefix + ".status", u.hasStatus() ? String.valueOf(u.getStatus()) : "<absent>");
        o.fields.put(prefix + ".extra_info", new TreeMap<>(u.getExtraInfoMap()).toString());
    }

    private static void putHistory(Obs o, String prefix, AvatarHistoryItem h) {
        if (h == null) {
            o.fields.put(prefix + ".present", "false");
            return;
        }
        o.fields.put(prefix + ".present", "true");
        o.fields.put("~" + prefix + ".id", h.getId());
        o.fields.put(prefix + ".avatar_url", h.getAvatarUrl());
        o.fields.put(prefix + ".content_type", h.getContentType());
        o.fields.put(prefix + ".size_bytes", String.valueOf(h.getSizeBytes()));
        o.fields.put(prefix + ".is_current", String.valueOf(h.getIsCurrent()));
        o.fields.put("~" + prefix + ".create_time", h.getCreateTime());
    }

    private static String hex(String s) {
        if (s == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : s.getBytes(StandardCharsets.UTF_8)) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static String sys(String k, String d) {
        return System.getProperty(k, d);
    }
}
