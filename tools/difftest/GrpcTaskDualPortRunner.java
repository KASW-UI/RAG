/*
 * GrpcTaskDualPortRunner —— task 域 **gRPC 层**双侧活体对拍（单组件，不经 HTTP）
 *
 * 【为什么必须有这个文件】
 *   HTTP 对拍会把 gRPC status 的 code 与 description 全部抹掉
 *   （见 GrpcAdminDualPortRunner 的类注释；admin 域的 UNRESOLVED-ARPC-001 就是这么漏的）。
 *   而 task 域更极端：**rpc 的 INVALID_ARGUMENT 分支在 HTTP 面上根本走不到** ——
 *   Go api 的 `req.Userid.Int64()` 先把 uid<=0 拦掉并本地返回 400
 *   （api/internal/types/types.go:40-47），压根不会调 rpc。
 *   ⇒ 只有直连两个 rpc 端口，才能验到：
 *       · status code 是否两侧一致
 *       · status description 是否**逐字节**一致（Go 写的是 "user_id invalid" 这个字面量）
 *       · OK 路径的载荷字段是否逐字段一致
 *
 * 【判定口径】照 GrpcAdminDualPortRunner：
 *   1. code 不同            ⇒ CODE_MISMATCH（硬失败）
 *   2. code 相同、description 不同 ⇒ DESCRIPTION_DIFF（记录并单独计数，不判失败）
 *   3. OK 路径比逐字段载荷（两侧 uid 相同，无需归一）
 *
 * 【不进 Maven 模块的原因】与 grpc-dual-port-runner 同款：运行一次、留档、退出。
 *
 * 用法：
 *   javac -cp <task-proto jar>:<grpc jars> GrpcTaskDualPortRunner.java
 *   java  -cp .:<...> GrpcTaskDualPortRunner
 * 系统属性：go.task.port / rag.task.port（默认 19005 / 59005）、go.host / rag.host
 */
import com.rag.task.task.proto.GetTaskReq;
import com.rag.task.task.proto.GetTaskResp;
import com.rag.task.task.proto.Task;
import com.rag.task.task.proto.TaskServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import io.grpc.Status;

import java.util.concurrent.TimeUnit;

public final class GrpcTaskDualPortRunner {

    private static int codeDiff = 0;
    private static int descDiff = 0;
    private static int payloadDiff = 0;
    private static int okCases = 0;
    private static int errCases = 0;

    public static void main(String[] args) throws Exception {
        String goPort = prop("go.task.port", "19005");
        String ragPort = prop("rag.task.port", "59005");
        String host = prop("go.host", "127.0.0.1");

        ManagedChannel go = ManagedChannelBuilder.forAddress(host, Integer.parseInt(goPort))
                .usePlaintext().build();
        ManagedChannel rag = ManagedChannelBuilder.forAddress(host, Integer.parseInt(ragPort))
                .usePlaintext().build();

        System.out.println("== task 域 gRPC 层双侧对拍 ==");
        System.out.println("   Go  task-rpc : " + host + ":" + goPort);
        System.out.println("   RAG task-rpc : " + host + ":" + ragPort + "\n");

        // 🔴 本矩阵的核心：前三组在 HTTP 面上**永远走不到**，只能直连 rpc 才验得到。
        long[] uids = {
                0L,              // => InvalidArgument "user_id invalid"
                -1L,             // => 同上（uid <= 0）
                Long.MIN_VALUE,  // => 同上
                Long.MAX_VALUE,  // => OK（正数，DB 无行 ⇒ 两任务 progress=0）
                1L,              // => OK
                1001L,           // => OK（种子数据里有两行）
                1003L,           // => OK（DB 无行）
        };

        for (long uid : uids) {
            compare("uid=" + uid, uid, go, rag);
        }

        System.out.println("\n---- 汇总 ----");
        System.out.println("用例            : " + (okCases + errCases));
        System.out.println("  OK 路径      : " + okCases + "   载荷不一致: " + payloadDiff);
        System.out.println("  错误路径     : " + errCases);
        System.out.println("  CODE_MISMATCH        : " + codeDiff);
        System.out.println("  DESCRIPTION_DIFF     : " + descDiff);

        go.shutdownNow();
        rag.shutdownNow();
        System.exit(codeDiff == 0 && payloadDiff == 0 ? 0 : 1);
    }

    private static void compare(String label, long uid,
                                ManagedChannel go, ManagedChannel rag) {
        Result g = call(go, uid);
        Result r = call(rag, uid);

        System.out.println("── " + label);
        System.out.println("   Go  code=" + g.code + (g.description != null
                ? "  desc=\"" + g.description + "\"" : "") + payloadSuffix(g));
        System.out.println("   RAG code=" + r.code + (r.description != null
                ? "  desc=\"" + r.description + "\"" : "") + payloadSuffix(r));

        if (!g.code.equals(r.code)) {
            codeDiff++;
            System.out.println("   ⇒ 🔴 CODE_MISMATCH（硬失败）");
            return;
        }
        if (g.code.equals("OK")) {
            okCases++;
            if (!g.payload.equals(r.payload)) {
                payloadDiff++;
                System.out.println("   ⇒ 🔴 PAYLOAD_MISMATCH");
                System.out.println("      Go  payload=" + g.payload);
                System.out.println("      RAG payload=" + r.payload);
            } else {
                System.out.println("   ⇒ ✅ 载荷逐字段一致");
            }
            return;
        }
        errCases++;
        String gd = g.description == null ? "" : g.description;
        String rd = r.description == null ? "" : r.description;
        if (!gd.equals(rd)) {
            descDiff++;
            System.out.println("   ⇒ ⚠️ DESCRIPTION_DIFF（不判失败，记录）");
            System.out.println("      Go  desc  = \"" + gd + "\"");
            System.out.println("      RAG desc  = \"" + rd + "\"");
        } else {
            System.out.println("   ⇒ ✅ code 与 description 逐字节一致");
        }
    }

    private static String payloadSuffix(Result res) {
        return res.code.equals("OK") ? "  payload=" + res.payload : "";
    }

    private static Result call(ManagedChannel ch, long uid) {
        TaskServiceGrpc.TaskServiceBlockingStub stub = TaskServiceGrpc.newBlockingStub(ch)
                .withDeadlineAfter(10, TimeUnit.SECONDS);
        try {
            GetTaskResp resp = stub.getTask(GetTaskReq.newBuilder().setUserId(uid).build());
            return new Result("OK", null, render(resp));
        } catch (StatusRuntimeException e) {
            Status s = e.getStatus();
            return new Result(s.getCode().name(), s.getDescription(), "");
        }
    }

    /** 把响应渲染成可逐字比对的字符串：任务顺序 + 每个字段。 */
    private static String render(GetTaskResp resp) {
        StringBuilder sb = new StringBuilder();
        for (Task t : resp.getTaskList()) {
            if (sb.length() > 0) {
                sb.append(" ; ");
            }
            sb.append("{name=").append(t.getName())
              .append(", desc=").append(t.getDesc())
              .append(", taskId=").append(t.getTaskId())
              .append(", completionProgress=").append(t.getCompletionProgress())
              .append(", requiredProgress=").append(t.getRequiredProgress())
              .append("}");
        }
        return sb.toString();
    }

    private static String prop(String key, String def) {
        String v = System.getProperty(key);
        return (v == null || v.isEmpty()) ? def : v;
    }

    private record Result(String code, String description, String payload) {
    }
}