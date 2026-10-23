/*
 * ════════════════════════════════════════════════════════════════════════
 *  GrpcAdminDeadlineProbe — UNRESOLVED-ARPC-002 的 **gRPC 层客户端 deadline** 探针
 * ════════════════════════════════════════════════════════════════════════
 *
 * 【要证明什么】
 *   两侧的 **rpc 服务端超时都是 2s**（Go: go-zero 默认 2000ms；RAG: rpc-timeout: 2s）。
 *   当**客户端**带一个比 2s 更短的 deadline（如 100ms）时：
 *
 *     Go  : service/common/observability/rpc.go:38
 *             timeoutCtx, cancel := context.WithTimeout(ctx, timeout)
 *             ⇒ ctx 是 gRPC 入站 ctx，**已含客户端 deadline**
 *             ⇒ 有效期限 = min(客户端 deadline, 2s) = 100ms
 *             ⇒ handler 拿到的 timeoutCtx 在 100ms 到期
 *
 *     RAG : service/common/.../RpcInterceptor.java:170-172
 *             deadlineEpochMs = System.currentTimeMillis() + timeout.toMillis()
 *             ⇒ **只算服务端自己的 2s**，没有读 Context.current().getDeadline()
 *             ⇒ 有效期限 = 2s（客户端的 100ms 被忽略）
 *
 *   ⇒ **客户端看到的返回时刻两侧相同**（都是客户端自己的 deadline 在起作用，
 *      ≈100ms），**差异不在客户端，而在服务端的工作寿命**：
 *
 *      Go  : timeoutCtx 到期 → GORM `m.conn.WithContext(ctx)` → pgx 发 CancelRequest
 *            ⇒ **PG 里的阻塞查询在 ≈100ms 消失**
 *      RAG : DeadlineQueryTimeoutInjector 按 RpcInterceptor 的 2s deadline 注入
 *            `Statement.setQueryTimeout(2)` ⇒ **PG 里的阻塞查询要活到 ≈2s**
 *
 * 【本探针只负责「客户端这一半」】
 *   它量的是**客户端侧**的返回时刻与 status。
 *   ⚠️ **客户端返回时刻两侧必然相同**（都是客户端自己的 deadline 说了算）
 *   ⇒ 单看本探针的输出**得不出结论**，必须与
 *      `exp-grpc-admin-deadline-probe.sh` 的 **PG 侧观测**合起来读。
 *   ⚠️ 这是本实验最容易读错的一步：客户端时间相同**不代表**两侧行为相同。
 *
 * 【为什么不用现成的 GrpcAdminDualPortRunner】
 *   它比的是「两侧的 code / description / payload 是否一致」，**不设客户端 deadline**
 *   ⇒ 结构上抓不到这条（见 docs/DRIFT/users/admin/rpc.md 定性轮 §1.4）。
 *
 * 【输入（全部 -Dproperty=... 系统属性）】
 *   go.host / go.port      默认 127.0.0.1 / 18081
 *   rag.host / rag.port    默认 127.0.0.1 / 58081
 *   deadline.ms            默认 100（客户端 deadline）
 *   username / password    默认 admin / admin
 *
 * 【输出】
 *   每侧一行：  SIDE=<go|rag> elapsed_ms=<n> status=<CODE> desc=<原文>
 *   最后一行：  PROBE_DONE
 *
 * 用法（编译 + 跑）：
 *   javac -d /tmp/deadline-probe-build -cp "$PROTO_CP:$LIBCP" GrpcAdminDeadlineProbe.java
 *   java -cp "/tmp/deadline-probe-build:$PROTO_CP:$LIBCP" \
 *        -Dgo.port=18081 -Drag.port=58081 -Ddeadline.ms=100 tools.difftest.GrpcAdminDeadlineProbe
 */
package tools.difftest;

import com.rag.users.admin.proto.AdminServiceGrpc;
import com.rag.users.admin.proto.LoginReq;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import java.util.concurrent.TimeUnit;

public final class GrpcAdminDeadlineProbe {

    private static String prop(String k, String def) {
        String v = System.getProperty(k);
        return (v == null || v.isBlank()) ? def : v;
    }

    /**
     * 打一侧。
     *
     * @return 该侧的观测结果（单行文本）
     */
    private static String probe(String side, String host, int port, long deadlineMs,
                                String username, String password) {
        ManagedChannel channel = ManagedChannelBuilder
                .forAddress(host, port)
                .usePlaintext()
                .build();
        try {
            AdminServiceGrpc.AdminServiceBlockingStub stub =
                    AdminServiceGrpc.newBlockingStub(channel)
                            .withDeadlineAfter(deadlineMs, TimeUnit.MILLISECONDS);

            LoginReq req = LoginReq.newBuilder()
                    .setUsername(username)
                    .setPassword(password)
                    .build();

            long t0 = System.nanoTime();
            long wallStart = System.currentTimeMillis();
            String status;
            String desc;
            try {
                stub.login(req);
                status = "OK";
                desc = "(无)";
            } catch (StatusRuntimeException e) {
                Status s = e.getStatus();
                status = s.getCode().name();
                desc = s.getDescription() == null ? "(null)" : s.getDescription();
            }
            long elapsedMs = (System.nanoTime() - t0) / 1_000_000L;
            long wallEnd = System.currentTimeMillis();
            // 报出**绝对墙钟时刻**：编排脚本要拿它与 PG 服务端日志里的取消时刻做差 ——
            // 相对「进程启动时刻」的偏移里混着 JVM 启动时间（每次几百 ms，且每次都不同），
            // 直接比会得出错误的差值。
            return String.format("SIDE=%s elapsed_ms=%d status=%s desc=%s rpc_start_ms=%d rpc_end_ms=%d",
                    side, elapsedMs, status, desc.replace('\n', ' '), wallStart, wallEnd);
        } finally {
            channel.shutdownNow();
            try {
                channel.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public static void main(String[] args) {
        String goHost = prop("go.host", "127.0.0.1");
        int goPort = Integer.parseInt(prop("go.port", "18081"));
        String ragHost = prop("rag.host", "127.0.0.1");
        int ragPort = Integer.parseInt(prop("rag.port", "58081"));
        long deadlineMs = Long.parseLong(prop("deadline.ms", "100"));
        String username = prop("username", "admin");
        String password = prop("password", "admin");

        // 只跑一侧：编排脚本要「一侧一次 + 一次只锁一张表」，
        // 否则两侧的阻塞查询会混在同一段 PG 采样里，无法归因。
        String sides = prop("sides", "go,rag");

        System.out.println("# client_deadline_ms=" + deadlineMs
                + "  sides=" + sides
                + "  go=" + goHost + ":" + goPort + "  rag=" + ragHost + ":" + ragPort);
        if (sides.contains("go")) {
            System.out.println(probe("go", goHost, goPort, deadlineMs, username, password));
        }
        if (sides.contains("rag")) {
            System.out.println(probe("rag", ragHost, ragPort, deadlineMs, username, password));
        }
        System.out.println("PROBE_DONE");
    }
}
