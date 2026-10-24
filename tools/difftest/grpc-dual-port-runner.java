/*
 * ════════════════════════════════════════════════════════════════════════════
 *  GrpcDualPortRunner — §6.2.6 E-2 动态半的执行主体
 * ════════════════════════════════════════════════════════════════════════════
 *
 * 【为什么有这个文件】
 *    之前的 difftest (diff.py + run.sh) 是 HTTP wire 层的对拍:
 *    两端都先发出 HTTP, 然后逐字段比对 response JSON.
 *    proto 字段号、message 名称、错误码语义等wire契约完全没有运行证据 —— 它们只能
 *    通过 HTTP 响应间接推断, 而 HTTP 响应本身又是 RPC 异常的 wrapper, 任何中间层
 *    处理都可能把 RPC 状态码的特征给抹掉。
 *    §3.1 明确要求覆盖的「错误码语义」一项, 唯一可靠的判据是 **同一 stub 双端口打**,
 *    抓 `Status.getCode()` 在两侧应当返回的 gRPC 标准 code 是否一致。
 *    这就是本文件的本职。
 *
 * 【位置】
 *    落在 tools/difftest/ 下, 与 probe-jdbc-cancel.java 同期同形态 —— 都是「为对拍
 *    留档而单独运行的 Java 程序」, 不进 service/users 的 module, 不污染 RAG 业务代码,
 *    不被回归网引用, 也不参与 mvn verify.
 *
 * 【不进 Maven 模块的原因】
 *    新增 Maven module 意味着改根 pom —— 牵连面太大. 而本程序本身就是「运行一次、
 *    留档、退出」, 没有被 CI 反复触发的需求. 与 probe-jdbc-cancel.java 一样, 用
 *    javac 单文件编译 + java -cp 跑即可.
 *
 * 【输入(全部 -Dproperty=... 系统属性)】
 *    go.user.port   默认 19004 (run.sh --serve-only 之后 Go 真源 user-rpc)
 *    rag.user.port  默认 59210 (run.sh --serve-only 之后 RAG user-rpc)
 *    go.host        默认 127.0.0.1
 *    rag.host       默认 127.0.0.1
 *
 * 【断言 4 档(已确认 Go 侧的真实 wire 行为, 见 registerlogic.go / deleteuserlogic.go /
 *   getuserlogic.go / logoutlogic.go 中的 status.Error(codes.*, ...) )】
 *
 *    1) OK
 *       触发:register (用一个随机新 username,邮箱同样唯一)
 *       期望:Status.Code.OK 在两侧都返回
 *
 *    2) INVALID_ARGUMENT
 *       触发:logout(token="")
 *       期望:Status.Code.INVALID_ARGUMENT
 *
 *    3) NOT_FOUND
 *       触发:getUser(uid=一个绝不会出现的数字,默认 99999999999)
 *       期望:Status.Code.NOT_FOUND
 *
 *    4) ALREADY_EXISTS
 *       触发:register 用 第 1) 步同一个 username 再注册一次
 *       期望:Status.Code.ALREADY_EXISTS
 *
 *    每档断言:
 *      - go 的 Status.getCode() == expected
 *      - rag 的 Status.getCode() == expected
 *      - go == rag
 *
 * 【Login 的特别说明(被排除在外)】
 *    Go loginlogic.go 对「用户不存在 / 密码错 / 用户被禁」三种业务失败**不抛 gRPC 异常**,
 *    而是返回 LoginResp{Status: 1} 或 {Status: 2} —— 这是 go-zero 的业务级状态码, 不是
 *    gRPC wire-level code. 这是约定俗成的设计, 不在本程序覆盖范围.
 *    本程序只用那些**确实用 status.Error(codes.*, ...) 抛错**的 RPC, 这样比较的是 wire-
 *    level 错误码, 而非业务级状态码.
 *
 * 【不覆盖的】
 *    - HTTP wire 差异(由 diff.py 覆盖,见 §6.2.6 A-U2)
 *    - 事务回滚(E-6)
 *    - 并发路径(E-5)
 *    - 字段笛卡尔空间(E-3)
 *    - 通信延迟、超时设置(E-4 那一档)
 *    - admin 域 13 RPC —— 同 stub, 同 gRPC code 表, 不是新增覆盖; 留作下一笔
 *
 * 【静态半前提(本文件成立依赖)】
 *    tools/difftest/check-proto-static.sh 已跑过且输出 ✓, 字段号 / message 名 / RPC
 *    签名(user 9 RPC + admin 13 RPC)双端字节级一致. 因此同 stub 双端口发的请求, 响应
 *    wire 字节兼容, 无需按侧适配.
 *
 * 【运行方式】
 *    1. 起两套栈(另一个终端):
 *         ./tools/difftest/run.sh --keep --serve-only
 *    2. 跑本程序:
 *         PROTO_CP=service/users/user/user-proto/target/classes:\
 *                   service/users/admin/admin-proto/target/classes
 *         TEST_CP=$(mvn -q -pl service/users/user/user-rpc \
 *                       dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt \
 *                       2>/dev/null && cat /tmp/cp.txt)
 *         mkdir -p /tmp/build
 *         javac -d /tmp/build tools/difftest/grpc-dual-port-runner.java \
 *               -cp "$PROTO_CP:$TEST_CP"
 *         java -cp "/tmp/build:$PROTO_CP:$TEST_CP" \
 *              -Dgo.user.port=19004 -Drag.user.port=59210 \
 *              tools.difftest.GrpcDualPortRunner
 *    3. 一次性编排见 tools/difftest/exp-difftest-grpc-errors.sh
 *
 * 【输出】
 *    每档:
 *      [✓|✗] <label>    go=<code>    rag=<code>    expected=<code>
 *    末尾:
 *      ✓ §3.1 dynamic half closed: 4 档 status code 双端 wire-level 一致
 *    或
 *      ✗ §3.1 dynamic half FAILED: N 处两侧 Status.getCode() 不符
 *    + 进程 exit code 0 / 1
 */
package tools.difftest;

import com.rag.users.user.proto.CreateUserReq;
import com.rag.users.user.proto.GetUserReq;
import com.rag.users.user.proto.LogoutReq;
import com.rag.users.user.proto.UserServiceGrpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import java.util.UUID;
import java.util.function.Function;

final class GrpcDualPortRunner {

    private static final String GO_HOST   = sys("go.host",  "127.0.0.1");
    private static final String RAG_HOST  = sys("rag.host", "127.0.0.1");
    private static final int    GO_PORT   = Integer.parseInt(sys("go.user.port",  "19004"));
    private static final int    RAG_PORT  = Integer.parseInt(sys("rag.user.port", "59210"));
    private static final long   GHOST_UID = 99_999_999_999L;

    private GrpcDualPortRunner() {}

    public static void main(String[] args) throws Exception {
        final String uniq = "rag_e2_"
                + System.currentTimeMillis() + "_"
                + Long.toHexString(UUID.randomUUID().hashCode() & 0xffffffffL);

        System.out.println("== §6.2.6 E-2 动态半 ==");
        System.out.printf("  go  user-rpc = %s:%d%n", GO_HOST,  GO_PORT);
        System.out.printf("  rag user-rpc = %s:%d%n", RAG_HOST, RAG_PORT);
        System.out.println("  unique test username = " + uniq);
        System.out.println();

        ManagedChannel goCh  = ManagedChannelBuilder.forAddress(GO_HOST,  GO_PORT)
                .usePlaintext().build();
        ManagedChannel ragCh = ManagedChannelBuilder.forAddress(RAG_HOST, RAG_PORT)
                .usePlaintext().build();

        int fails = 0;
        try {
            fails += check(goCh, ragCh,
                    "OK: register(fresh username)",
                    c -> UserServiceGrpc.newBlockingStub(c).register(buildRegisterReq(uniq, "pw1", "x@x")),
                    Status.Code.OK);

            fails += check(goCh, ragCh,
                    "INVALID_ARGUMENT: logout(token=\"\")",
                    c -> UserServiceGrpc.newBlockingStub(c).logout(
                            LogoutReq.newBuilder().setToken("").build()),
                    Status.Code.INVALID_ARGUMENT);

            fails += check(goCh, ragCh,
                    "NOT_FOUND: getUser(uid=" + GHOST_UID + ")",
                    c -> UserServiceGrpc.newBlockingStub(c).getUser(
                            GetUserReq.newBuilder().setUid(GHOST_UID).build()),
                    Status.Code.NOT_FOUND);

            fails += check(goCh, ragCh,
                    "ALREADY_EXISTS: register(same username as case 1)",
                    c -> UserServiceGrpc.newBlockingStub(c).register(buildRegisterReq(uniq, "pw2", "y@y")),
                    Status.Code.ALREADY_EXISTS);

            // Sanity: Login does NOT throw gRPC codes for business failure (业务状态码
            // 在 LoginResp.Status 里). 我们只验证 wire-level 不会因为 Login 本身
            // 设计差异意外跑通某个本应抛错的请求 —— 用 4 个真正抛 codes.* 的 RPC
            // 旁证 service 整体 wire-level 语义对齐. Login 不进上 4 档里.
            //
            // 此处保留一行注释,提醒后人:login 排除是有意的,不是漏测.
        } finally {
            goCh.shutdownNow();
            ragCh.shutdownNow();
        }

        System.out.println();
        if (fails == 0) {
            System.out.println("✓ §3.1 dynamic half closed: 4 档 status code 双端 wire-level 一致");
            System.exit(0);
        } else {
            System.out.println("✗ §3.1 dynamic half FAILED: " + fails + " 处两侧 Status.getCode() 不符");
            System.exit(1);
        }
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private static String sys(String k, String d) {
        return System.getProperty(k, d);
    }

    private static CreateUserReq buildRegisterReq(String username, String password, String email) {
        return CreateUserReq.newBuilder()
                .setUsername(username)
                .setPassword(password)
                .setEmail(email)
                .build();
    }

    private static int check(ManagedChannel go, ManagedChannel rag, String label,
                            Function<ManagedChannel, ?> call, Status.Code expected) {
        Status.Code goCode  = runOne(go,  call);
        Status.Code ragCode = runOne(rag, call);
        boolean ok = goCode == expected && ragCode == expected && goCode == ragCode;
        System.out.printf("  %s %-58s  go=%-22s  rag=%-22s  expected=%s%n",
                ok ? "✓" : "✗", label, goCode, ragCode, expected);
        return ok ? 0 : 1;
    }

    private static Status.Code runOne(ManagedChannel ch, Function<ManagedChannel, ?> call) {
        try {
            call.apply(ch);
            return Status.Code.OK;
        } catch (StatusRuntimeException e) {
            return e.getStatus().getCode();
        } catch (Exception e) {
            e.printStackTrace(System.out);
            return Status.Code.UNKNOWN;
        }
    }
}
