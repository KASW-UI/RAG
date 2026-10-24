/*
 * ════════════════════════════════════════════════════════════════════════
 *  GrpcAdminDualPortRunner — admin-rpc 的 **gRPC 层**双侧活体对拍
 * ════════════════════════════════════════════════════════════════════════
 *
 * 【为什么必须有这个文件】
 *   tools/difftest/diff.py 做的是 **HTTP wire 层**对拍（admin-api 的 13 个端点）。
 *   它证明的是「admin-api 的对外行为两侧一致」，**不证明 admin-rpc 的 gRPC 层一致**：
 *
 *     · HTTP 层把 gRPC 状态**码**翻译成业务码（1003/1001/1016/5001…），
 *       **status description 根本不进响应体**（GlobalExceptionHandler.handleRPC:74-75
 *       用的是 ErrorCode.getMsg(businessCode)，不是 e.getStatus().getDescription()）；
 *     · 所以 proto 字段语义、gRPC status code、status description 这三样
 *       —— §3.1 明写「对照必须覆盖」的项目 —— 在 HTTP 对拍里**全部被抹掉**；
 *     · docs/DRIFT/users/admin/rpc.md 的 UNRESOLVED-ARPC-001 就是这么来的：
 *       「本轮零条双侧活体对拍」。
 *
 *   本文件是 admin 域的 gRPC 层对拍执行体：**同一个 stub、双端口**，
 *   抓 Status.getCode() **与** Status.getDescription() 逐条比对。
 *
 * 【与 grpc-dual-port-runner.java（user 域）的关系】
 *   同 model、不同域。user 域那个只覆盖 4 档、且只比 code；
 *   admin 域要覆盖 **13 个 RPC**，并且**必须比 description** ——
 *   因为 admin 域有一类只有 description 才暴露的差异（DRIFT-ARPC-007）。
 *
 * 【判定口径】
 *   1. **code 不同** ⇒ 硬失败（SEMANTIC MISMATCH 级），exit 1。
 *   2. **code 相同、description 不同** ⇒ 记为 DESCRIPTION_DIFF，
 *      **不判失败**，但逐条打印两侧原文 + hex，并单独计数。
 *      理由：description 是 gRPC status 的载荷，Go 侧有 12 处字面量被转码损坏
 *      （见 DRIFT-ARPC-007），把它判成「失败」会逼着 RAG 去复刻乱码 ——
 *      那是把仓库级数据损坏搬进产品，不是对齐。
 *      ⇒ 本 runner 的作用是**把差异量化并留证**，决策权在用户。
 *   3. OK 路径比**归一化后的**载荷（uid 两侧本就不同，必须归一）。
 *
 * 【输入（全部 -Dproperty=... 系统属性）】
 *   go.admin.port   默认 18081（run.sh --serve-only 之后 Go 真源 admin-rpc）
 *   rag.admin.port  默认 58081（run.sh --serve-only 之后 RAG admin-rpc）
 *   go.host / rag.host  默认 127.0.0.1
 *
 * 【不进 Maven 模块的原因】
 *   与 grpc-dual-port-runner.java 同款：它本来就是「运行一次、留档、退出」的程序，
 *   不进 service/users 的 module，不污染 RAG 业务代码，不参与 mvn verify。
 *
 * 用法（编译 + 跑）：
 *   javac -d /tmp/dual-admin-build -cp "$PROTO_CP:$LIBCP" GrpcAdminDualPortRunner.java
 *   java -cp "/tmp/dual-admin-build:$PROTO_CP:$LIBCP" \
 *        -Dgo.admin.port=18081 -Drag.admin.port=58081 tools.difftest.GrpcAdminDualPortRunner
 */
package tools.difftest;

import com.rag.users.admin.proto.AdminInfo;
import com.rag.users.admin.proto.AdminServiceGrpc;
import com.rag.users.admin.proto.BanUserReq;
import com.rag.users.admin.proto.CreateAdminReq;
import com.rag.users.admin.proto.CreateInviteCodeReq;
import com.rag.users.admin.proto.DeleteUserReq;
import com.rag.users.admin.proto.GetSelfReq;
import com.rag.users.admin.proto.GetUserListReq;
import com.rag.users.admin.proto.GetUserReq;
import com.rag.users.admin.proto.LoginReq;
import com.rag.users.admin.proto.LogoutReq;
import com.rag.users.admin.proto.ResetUserPasswordReq;
import com.rag.users.admin.proto.UnBanUserReq;
import com.rag.users.admin.proto.UpdateSelfReq;
import com.rag.users.admin.proto.UpdateUserReq;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * admin-rpc 13 个 RPC 的双侧活体对拍执行体。
 */
public final class GrpcAdminDualPortRunner {

    /** 两侧共用的「一定不存在」的 uid —— 大到任何 snowflake 都不会撞上。 */
    private static final long NO_SUCH_UID = 8_999_999_999_999_999_999L;

    /** 两侧 seeder 造的默认管理员账号（Go: servicecontext.go:18 常量 "admin"；RAG: AdminDomainService.DEFAULT_ADMIN_PASSWORD）。 */
    private static final String SEED_ADMIN = "admin";
    private static final String SEED_PASSWORD = "admin";

    /** 每个用例的观测结果。 */
    private static final class Outcome {
        final Status.Code code;
        final String description;
        final String payload;

        Outcome(Status.Code code, String description, String payload) {
            this.code = code;
            this.description = description;
            this.payload = payload;
        }

        static Outcome ok(String payload) {
            return new Outcome(Status.Code.OK, "", payload);
        }

        static Outcome err(Throwable t) {
            if (t instanceof StatusRuntimeException) {
                StatusRuntimeException e = (StatusRuntimeException) t;
                return new Outcome(e.getStatus().getCode(),
                        e.getStatus().getDescription() == null ? "" : e.getStatus().getDescription(),
                        "");
            }
            return new Outcome(Status.Code.UNKNOWN, String.valueOf(t.getMessage()), "");
        }
    }

    /** 一个用例：同一个函数分别喂给 Go stub 与 RAG stub。 */
    private interface Call {
        Object run() throws Exception;
    }

    private static final class Row {
        final String name;
        final String why;
        final Outcome go;
        final Outcome rag;

        Row(String name, String why, Outcome go, Outcome rag) {
            this.name = name;
            this.why = why;
            this.go = go;
            this.rag = rag;
        }
    }

    public static void main(String[] args) throws Exception {
        String goHost = prop("go.host", "127.0.0.1");
        String ragHost = prop("rag.host", "127.0.0.1");
        int goPort = Integer.parseInt(prop("go.admin.port", "18081"));
        int ragPort = Integer.parseInt(prop("rag.admin.port", "58081"));

        ManagedChannel goCh = ManagedChannelBuilder.forAddress(goHost, goPort)
                .usePlaintext().maxInboundMessageSize(16 * 1024 * 1024).build();
        ManagedChannel ragCh = ManagedChannelBuilder.forAddress(ragHost, ragPort)
                .usePlaintext().maxInboundMessageSize(16 * 1024 * 1024).build();

        List<Row> rows = new ArrayList<>();
        try {
            AdminServiceGrpc.AdminServiceBlockingStub go = AdminServiceGrpc.newBlockingStub(goCh);
            AdminServiceGrpc.AdminServiceBlockingStub rag = AdminServiceGrpc.newBlockingStub(ragCh);

            // ── 0. 先各自登录，拿到「属于自己的」admin uid ──────────────
            // 两侧的 uid 是各自 snowflake 生成的，必然不同 ⇒ 后续凡涉及
            // 「自己的 uid」的用例都必须**分侧传各自的 uid**，不能共用一个常量。
            long goUid = loginUid(go);
            long ragUid = loginUid(rag);

            // 2026-10-23：成功路径的 updateUser 需要一个**存在的 users_user 行**。
            // 由编排脚本 exp-difftest-grpc-admin-errors.sh 用 psql 在两侧各造一行，
            // uid 刻意取不同值（两侧是独立库，但归一化时 uid 一律不比）。
            long goProbeUid = Long.parseLong(prop("go.probe.uid", "7700000000000000001"));
            long ragProbeUid = Long.parseLong(prop("rag.probe.uid", "7700000000000000002"));

            System.out.println("== 两侧 admin uid ==");
            System.out.println("   go  admin-rpc(" + goHost + ":" + goPort + ")  uid=" + goUid);
            System.out.println("   rag admin-rpc(" + ragHost + ":" + ragPort + ")  uid=" + ragUid);
            System.out.println("== 两侧 probe user uid（updateUser 成功路径用）==");
            System.out.println("   go  probe uid=" + goProbeUid + "   rag probe uid=" + ragProbeUid);
            System.out.println();

            // ── 1. Login ───────────────────────────────────────────
            rows.add(new Row("login.ok", "seeder 造的 admin 登录成功 → OK（uid 归一化）",
                    call(() -> go.login(LoginReq.newBuilder()
                            .setUsername(SEED_ADMIN).setPassword(workingPassword(go)).build())),
                    call(() -> rag.login(LoginReq.newBuilder()
                            .setUsername(SEED_ADMIN).setPassword(workingPassword(rag)).build()))));

            rows.add(new Row("login.notfound", "用户不存在 → NOT_FOUND",
                    call(() -> go.login(LoginReq.newBuilder()
                            .setUsername("zzz_no_such_admin").setPassword("x").build())),
                    call(() -> rag.login(LoginReq.newBuilder()
                            .setUsername("zzz_no_such_admin").setPassword("x").build()))));

            rows.add(new Row("login.wrongpw", "密码错 → UNAUTHENTICATED",
                    call(() -> go.login(LoginReq.newBuilder()
                            .setUsername(SEED_ADMIN).setPassword("zzz_wrong").build())),
                    call(() -> rag.login(LoginReq.newBuilder()
                            .setUsername(SEED_ADMIN).setPassword("zzz_wrong").build()))));

            // ── 2. Logout ──────────────────────────────────────────
            rows.add(new Row("logout.empty", "token 空 → INVALID_ARGUMENT",
                    call(() -> go.logout(LogoutReq.newBuilder().setToken("").build())),
                    call(() -> rag.logout(LogoutReq.newBuilder().setToken("").build()))));

            rows.add(new Row("logout.ok", "token 非空 → OK（写黑名单，success 恒 false）",
                    call(() -> go.logout(LogoutReq.newBuilder().setToken("dual-probe-token").build())),
                    call(() -> rag.logout(LogoutReq.newBuilder().setToken("dual-probe-token").build()))));

            // ── 3. GetSelf ─────────────────────────────────────────
            rows.add(new Row("getself.notfound", "uid 不存在 → NOT_FOUND（**Go desc 乱码**，DRIFT-ARPC-007）",
                    call(() -> go.getSelf(GetSelfReq.newBuilder().setUid(NO_SUCH_UID).build())),
                    call(() -> rag.getSelf(GetSelfReq.newBuilder().setUid(NO_SUCH_UID).build()))));

            rows.add(new Row("getself.ok", "自己的 uid → OK（uid 归一化，比 username/email）",
                    call(() -> go.getSelf(GetSelfReq.newBuilder().setUid(goUid).build())),
                    call(() -> rag.getSelf(GetSelfReq.newBuilder().setUid(ragUid).build()))));

            // ── 4. GetUser ─────────────────────────────────────────
            rows.add(new Row("getuser.notfound", "uid 不存在 → NOT_FOUND",
                    call(() -> go.getUser(GetUserReq.newBuilder().setUid(NO_SUCH_UID).build())),
                    call(() -> rag.getUser(GetUserReq.newBuilder().setUid(NO_SUCH_UID).build()))));

            // ── 5. GetUserList ─────────────────────────────────────
            rows.add(new Row("getuserlist.empty", "keyword 匹配不到 → OK + 空列表 + total=0",
                    call(() -> go.getUserList(GetUserListReq.newBuilder()
                            .setPage(1).setPageSize(20).setKeyword("zzz_no_such_keyword").build())),
                    call(() -> rag.getUserList(GetUserListReq.newBuilder()
                            .setPage(1).setPageSize(20).setKeyword("zzz_no_such_keyword").build()))));

            // ── 6. BanUser / UnBanUser ─────────────────────────────
            rows.add(new Row("banuser.notfound", "uid 不存在 → NOT_FOUND",
                    call(() -> go.banUser(BanUserReq.newBuilder()
                            .setUid(NO_SUCH_UID).setOperatorId(1).build())),
                    call(() -> rag.banUser(BanUserReq.newBuilder()
                            .setUid(NO_SUCH_UID).setOperatorId(1).build()))));

            rows.add(new Row("unbanuser.notfound", "uid 不存在 → NOT_FOUND",
                    call(() -> go.unBanUser(UnBanUserReq.newBuilder()
                            .setUid(NO_SUCH_UID).setOperatorId(1).build())),
                    call(() -> rag.unBanUser(UnBanUserReq.newBuilder()
                            .setUid(NO_SUCH_UID).setOperatorId(1).build()))));

            // ── 7. DeleteUser ──────────────────────────────────────
            rows.add(new Row("deleteuser.notfound", "uid 不存在 → NOT_FOUND（desc 含 uid，两侧同输入同文本）",
                    call(() -> go.deleteUser(DeleteUserReq.newBuilder()
                            .setUid(NO_SUCH_UID).setOperatorId(1).build())),
                    call(() -> rag.deleteUser(DeleteUserReq.newBuilder()
                            .setUid(NO_SUCH_UID).setOperatorId(1).build()))));

            // ── 8. ResetUserPassword ───────────────────────────────
            rows.add(new Row("resetuserpassword.notfound", "uid 不存在 → NOT_FOUND",
                    call(() -> go.resetUserPassword(ResetUserPasswordReq.newBuilder()
                            .setUid(NO_SUCH_UID).setOperatorId(1).build())),
                    call(() -> rag.resetUserPassword(ResetUserPasswordReq.newBuilder()
                            .setUid(NO_SUCH_UID).setOperatorId(1).build()))));

            // ── 9. UpdateUser ──────────────────────────────────────
            rows.add(new Row("updateuser.notfound", "uid 不存在 → NOT_FOUND",
                    call(() -> go.updateUser(UpdateUserReq.newBuilder()
                            .setUid(NO_SUCH_UID).setUsername("zzz_probe").build())),
                    call(() -> rag.updateUser(UpdateUserReq.newBuilder()
                            .setUid(NO_SUCH_UID).setUsername("zzz_probe").build()))));

            // 2026-10-23 新增（关闭 DRIFT-ARPC-002 的验证缺口）：
            // 此前 updateUser **只有 notfound 错误路径**，成功路径从未被双侧活体对拍，
            // 而 DRIFT-ARPC-002（UpdateUserResp.success：Go=false / RAG=true）恰好
            // 只在**成功响应**里可见 ⇒ 缺这一条，那条漂移改了也没人能发现。
            rows.add(new Row("updateuser.ok", "存在的用户 → OK（**success 字段两侧必须相同**，DRIFT-ARPC-002）",
                    call(() -> go.updateUser(UpdateUserReq.newBuilder()
                            .setUid(goProbeUid).setUsername("probe_renamed").build())),
                    call(() -> rag.updateUser(UpdateUserReq.newBuilder()
                            .setUid(ragProbeUid).setUsername("probe_renamed").build()))));

            // ── 10. UpdateSelf ─────────────────────────────────────
            rows.add(new Row("updateself.notfound", "uid 不存在 → NOT_FOUND（**Go desc 乱码**，DRIFT-ARPC-007）",
                    call(() -> go.updateSelf(UpdateSelfReq.newBuilder().setUid(NO_SUCH_UID).build())),
                    call(() -> rag.updateSelf(UpdateSelfReq.newBuilder().setUid(NO_SUCH_UID).build()))));

            // ── 11. CreateAdmin ────────────────────────────────────
            rows.add(new Row("createadmin.emptyinvite", "invite_code 空 → PERMISSION_DENIED",
                    call(() -> go.createAdmin(CreateAdminReq.newBuilder()
                            .setUsername("zzz_probe_admin").setPassword("p").setInviteCode("").build())),
                    call(() -> rag.createAdmin(CreateAdminReq.newBuilder()
                            .setUsername("zzz_probe_admin").setPassword("p").setInviteCode("").build()))));

            rows.add(new Row("createadmin.badinvite", "invite_code 不存在 → PERMISSION_DENIED",
                    call(() -> go.createAdmin(CreateAdminReq.newBuilder()
                            .setUsername("zzz_probe_admin").setPassword("p")
                            .setInviteCode("ADM-0000000000000000").build())),
                    call(() -> rag.createAdmin(CreateAdminReq.newBuilder()
                            .setUsername("zzz_probe_admin").setPassword("p")
                            .setInviteCode("ADM-0000000000000000").build()))));

            // ── 12. CreateInviteCode ────────────────────────────────
            rows.add(new Row("createinvitecode.notfound", "operator_id 不存在 → NOT_FOUND（**Go desc 乱码**，DRIFT-ARPC-007）",
                    call(() -> go.createInviteCode(CreateInviteCodeReq.newBuilder()
                            .setOperatorId(NO_SUCH_UID).build())),
                    call(() -> rag.createInviteCode(CreateInviteCodeReq.newBuilder()
                            .setOperatorId(NO_SUCH_UID).build()))));
        } finally {
            goCh.shutdownNow();
            ragCh.shutdownNow();
            goCh.awaitTermination(5, TimeUnit.SECONDS);
            ragCh.awaitTermination(5, TimeUnit.SECONDS);
        }

        // ── 汇总 ───────────────────────────────────────────────────
        int codeMismatch = 0;
        int descDiff = 0;
        int payloadDiff = 0;
        System.out.println("== admin-rpc gRPC 层双侧活体对拍 ==");
        for (Row r : rows) {
            boolean codeSame = r.go.code == r.rag.code;
            boolean descSame = r.go.description.equals(r.rag.description);
            boolean payloadSame = r.go.payload.equals(r.rag.payload);
            String verdict;
            if (!codeSame) {
                verdict = "CODE_MISMATCH";
                codeMismatch++;
            } else if (!payloadSame) {
                verdict = "PAYLOAD_DIFF";
                payloadDiff++;
            } else if (!descSame) {
                verdict = "DESCRIPTION_DIFF";
                descDiff++;
            } else {
                verdict = "MATCH";
            }
            System.out.printf("  [%-17s] %-32s  %s%n", verdict, r.name, r.why);
            System.out.printf("        go  : code=%-18s desc=%s%n", r.go.code, r.go.description);
            System.out.printf("        rag : code=%-18s desc=%s%n", r.rag.code, r.rag.description);
            if (!payloadSame) {
                System.out.printf("        go  payload: %s%n", r.go.payload);
                System.out.printf("        rag payload: %s%n", r.rag.payload);
            }
            if (!descSame) {
                System.out.printf("        go  desc hex : %s%n", hex(r.go.description));
                System.out.printf("        rag desc hex : %s%n", hex(r.rag.description));
            }
        }
        System.out.println();
        System.out.printf("cases=%d  code_mismatch=%d  payload_diff=%d  description_diff=%d%n",
                rows.size(), codeMismatch, payloadDiff, descDiff);
        System.out.println("code_mismatch>0 ⇒ 语义级差异，exit 1");
        System.out.println("description_diff>0 ⇒ 只有 status description 不同（见 DRIFT-ARPC-007），不判失败");
        System.exit(codeMismatch > 0 ? 1 : 0);
    }

    // ── helpers ────────────────────────────────────────────────────

    private static Outcome call(Call c) {
        try {
            Object o = c.run();
            return Outcome.ok(normalize(o));
        } catch (Throwable t) {
            return Outcome.err(t);
        }
    }

    /**
     * 把成功响应归一化成可比字符串。
     * uid 两侧必然不同（各自 snowflake）⇒ 只断言「&gt;0」；email/username/列表条数等跨侧可比。
     */
    private static String normalize(Object o) {
        if (o instanceof com.rag.users.admin.proto.LoginResp) {
            return "uid>0:" + (((com.rag.users.admin.proto.LoginResp) o).getUid() > 0);
        }
        if (o instanceof com.rag.users.admin.proto.GetSelfResp) {
            AdminInfo a = ((com.rag.users.admin.proto.GetSelfResp) o).getAdmin();
            return "username=" + a.getUsername() + ";email='" + a.getEmail()
                    + "';extraKeys=" + a.getExtraInfoMap().keySet();
        }
        if (o instanceof com.rag.users.admin.proto.GetUserListResp) {
            return "total=" + ((com.rag.users.admin.proto.GetUserListResp) o).getTotal()
                    + ";listSize=" + ((com.rag.users.admin.proto.GetUserListResp) o).getListCount();
        }
        if (o instanceof com.rag.users.admin.proto.LogoutResp) {
            return "success=" + ((com.rag.users.admin.proto.LogoutResp) o).getSuccess();
        }
        if (o instanceof com.rag.users.admin.proto.CreateInviteCodeResp) {
            return "inviteCodePrefixIsADM="
                    + ((com.rag.users.admin.proto.CreateInviteCodeResp) o).getInviteCode().startsWith("ADM-");
        }
        if (o instanceof com.rag.users.admin.proto.BanUserResp) {
            return "success=" + ((com.rag.users.admin.proto.BanUserResp) o).getSuccess();
        }
        if (o instanceof com.rag.users.admin.proto.UpdateUserResp) {
            // 2026-10-23 新增（DRIFT-ARPC-002）。**success 必须进归一化** ——
            // 它就是那条漂移的载体；只比 hasUser 会漏掉它。
            // uid 一律不比（两侧各自 snowflake，必然不同）。
            com.rag.users.admin.proto.UpdateUserResp r = (com.rag.users.admin.proto.UpdateUserResp) o;
            StringBuilder sb = new StringBuilder();
            sb.append("success=").append(r.getSuccess()).append(";hasUser=").append(r.hasUser());
            if (r.hasUser()) {
                com.rag.users.admin.proto.UserInfo u = r.getUser();
                sb.append(";username=").append(u.getUsername())
                  .append(";email='").append(u.getEmail()).append("'")
                  .append(";status=").append(u.getStatus())
                  .append(";score=").append(u.getScore())
                  .append(";extraKeys=").append(u.getExtraInfoMap().keySet());
            }
            return sb.toString();
        }
        if (o instanceof com.rag.users.admin.proto.UnBanUserResp) {
            return "success=" + ((com.rag.users.admin.proto.UnBanUserResp) o).getSuccess();
        }
        if (o instanceof com.rag.users.admin.proto.DeleteUserResp) {
            return "success=" + ((com.rag.users.admin.proto.DeleteUserResp) o).getSuccess();
        }
        if (o instanceof com.rag.users.admin.proto.ResetUserPasswordResp) {
            return "success=" + ((com.rag.users.admin.proto.ResetUserPasswordResp) o).getSuccess();
        }
        if (o instanceof com.rag.users.admin.proto.UpdateSelfResp) {
            return "success=" + ((com.rag.users.admin.proto.UpdateSelfResp) o).getSuccess()
                    + ";username=" + ((com.rag.users.admin.proto.UpdateSelfResp) o).getAdmin().getUsername();
        }
        if (o instanceof com.rag.users.admin.proto.UpdateUserResp) {
            com.rag.users.admin.proto.UpdateUserResp r = (com.rag.users.admin.proto.UpdateUserResp) o;
            return "success=" + r.getSuccess() + ";hasUser=" + r.hasUser();
        }
        if (o instanceof com.rag.users.admin.proto.GetUserResp) {
            com.rag.users.admin.proto.GetUserResp r = (com.rag.users.admin.proto.GetUserResp) o;
            return "found=" + r.getFound() + ";hasUser=" + r.hasUser();
        }
        if (o instanceof com.rag.users.admin.proto.CreateAdminResp) {
            return "uid>0:" + (((com.rag.users.admin.proto.CreateAdminResp) o).getUid() > 0);
        }
        return String.valueOf(o);
    }

    /** 探测该侧当前可用的 admin 密码（seeder 默认 "admin"，diff.py 改过后是 "newadmin123"）。 */
    private static String workingPassword(AdminServiceGrpc.AdminServiceBlockingStub stub) {
        for (String pwd : new String[]{SEED_PASSWORD, "newadmin123"}) {
            try {
                stub.login(LoginReq.newBuilder().setUsername(SEED_ADMIN).setPassword(pwd).build());
                return pwd;
            } catch (StatusRuntimeException ignored) {
                // 试下一个
            }
        }
        return SEED_PASSWORD;
    }

    private static long loginUid(AdminServiceGrpc.AdminServiceBlockingStub stub) throws Exception {
        // seeder 造的默认密码是 "admin"；但 tools/difftest/diff.py 的 admin.update 步骤
        // 会把 admin 的密码改成 "newadmin123" ⇒ 同一个库上第二次跑本探针时，
        // "admin" 会认证失败。这里两种都试一遍，让脚本**可重复运行**。
        String[] candidates = {SEED_PASSWORD, "newadmin123"};
        RuntimeException last = null;
        for (String pwd : candidates) {
            try {
                return stub.login(LoginReq.newBuilder()
                        .setUsername(SEED_ADMIN).setPassword(pwd).build()).getUid();
            } catch (StatusRuntimeException e) {
                last = e;
            }
        }
        throw last;
    }

    private static String hex(String s) {
        if (s == null || s.isEmpty()) {
            return "(empty)";
        }
        StringBuilder sb = new StringBuilder();
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < b.length; i++) {
            sb.append(String.format("%02x", b[i]));
        }
        return sb.toString();
    }

    private static String prop(String k, String d) {
        String v = System.getProperty(k);
        return v == null || v.isEmpty() ? d : v;
    }
}
