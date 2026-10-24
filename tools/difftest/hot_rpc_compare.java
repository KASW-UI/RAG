/*
 * ═══════════════════════════════════════════════════════════════════════════
 *  hot-rpc 双侧活体对拍（D1）的 gRPC 比对器
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * 【为什么不是 JSON 语义比对】
 *    本域最常踩的三个坑**只有逐字节比才抓得到**：
 *      · 字段顺序（proto3 wire 上按字段号，但 repeated 元素的顺序是语义的一部分）
 *      · null vs []（Go 返 `Items: []*pb.HotArticleItem{}` = 空数组，
 *        不是 null；RAG 侧返 new ArrayList<>()）
 *      · uint32 溢出 / 截断（Go `uint32(z.Score)` 向零截断，
 *        RAG `HeavyKeeper.scoreToCount(score)`）
 *    ⇒ 本程序用**自定义 Marshaller**直接拿两侧的**原始 wire 字节**，
 *      不经过任何 JSON / 结构化中间层。
 *
 * 【为什么用 Java 而不是 grpcurl】
 *    · grpcurl 依赖 reflection；Go 侧只在 DevMode/TestMode 注册 reflection
 *      （hot.go:38-40），真源 yaml 是 pro 模式 ⇒ 为了比对而改 Mode 会**改变对照条件**。
 *    · RAG 侧 application.yml 显式 `reflection-service-enabled: false`。
 *    ⇒ 两侧都没有 reflection 可用 ⇒ 用生成代码的 stub 是唯一不改变被测条件的方式。
 *
 * 【输入】系统属性或命令行参数：
 *    --go  127.0.0.1:23983   Go 真源 hot-rpc（gRPC）
 *    --rag 127.0.0.1:23984   RAG hot-rpc（gRPC）
 *    --topks 0,1,5,20,100,101,200,1000,-1,-100
 *
 * 【退出码】0 = 全部 top_k 逐字节一致；1 = 存在差异；2 = 调用失败
 * ═══════════════════════════════════════════════════════════════════════════
 */

import com.rag.hot.hot.proto.GetHotArticlesRequest;
import com.rag.hot.hot.proto.GetHotArticlesResponse;
import com.rag.hot.hot.proto.HotArticleItem;

import io.grpc.CallOptions;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.MethodDescriptor;
// ⚠️ ClientCalls 在 **io.grpc.stub** 子包里（不是 io.grpc）——
//    grpc-stub-1.66.0.jar 里只有 io/grpc/stub/ClientCalls.class，
//    写 io.grpc.ClientCalls 会直接 "找不到符号"。
import io.grpc.stub.ClientCalls;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class hot_rpc_compare {

    /** 直接搬运 wire 字节的 Marshaller（不做任何结构化解析）。 */
    static final class ByteMarshaller implements MethodDescriptor.Marshaller<byte[]> {
        @Override
        public InputStream stream(byte[] value) {
            return new ByteArrayInputStream(value);
        }

        @Override
        public byte[] parse(InputStream stream) {
            try {
                return stream.readAllBytes();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    static final MethodDescriptor<byte[], byte[]> RAW_GET_HOT_ARTICLES =
            MethodDescriptor.<byte[], byte[]>newBuilder()
                    .setType(MethodDescriptor.MethodType.UNARY)
                    .setFullMethodName(MethodDescriptor.generateFullMethodName(
                            "hot.HotService", "GetHotArticles"))
                    .setRequestMarshaller(new ByteMarshaller())
                    .setResponseMarshaller(new ByteMarshaller())
                    .build();

    static byte[] call(ManagedChannel channel, int topK) {
        byte[] req = GetHotArticlesRequest.newBuilder().setTopK(topK).build().toByteArray();
        return ClientCalls.blockingUnaryCall(channel, RAW_GET_HOT_ARTICLES, CallOptions.DEFAULT, req);
    }

    static String sha256(byte[] b) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(b);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                sb.append(String.format("%02x", d[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return "ERR";
        }
    }

    static String hexPreview(byte[] b, int max) {
        int n = Math.min(b.length, max);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            sb.append(String.format("%02x", b[i]));
            if ((i + 1) % 32 == 0) {
                sb.append('\n');
            }
        }
        if (b.length > n) {
            sb.append("...");
        }
        return sb.toString();
    }

    static List<String> parseItems(byte[] wire) {
        List<String> out = new ArrayList<>();
        try {
            GetHotArticlesResponse resp = GetHotArticlesResponse.parseFrom(wire);
            for (HotArticleItem it : resp.getItemsList()) {
                out.add(it.getArticleId() + "=" + it.getHotScore());
            }
        } catch (Exception e) {
            out.add("<<parse error: " + e + ">>");
        }
        return out;
    }

    /** 断言 items 是否**按 hot_score 降序**（并列允许）。 */
    static boolean isDescending(byte[] wire) {
        try {
            GetHotArticlesResponse resp = GetHotArticlesResponse.parseFrom(wire);
            long prev = Long.MAX_VALUE;
            for (HotArticleItem it : resp.getItemsList()) {
                long cur = Integer.toUnsignedLong(it.getHotScore());
                if (cur > prev) {
                    return false;
                }
                prev = cur;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static void main(String[] args) throws Exception {
        String goTarget = "127.0.0.1:23983";
        String ragTarget = "127.0.0.1:23984";
        int[] topKs = {0, 1, 5, 20, 100, 101, 200, 1000, -1, -100};

        for (int i = 0; i < args.length; i++) {
            if ("--go".equals(args[i]) && i + 1 < args.length) {
                goTarget = args[++i];
            } else if ("--rag".equals(args[i]) && i + 1 < args.length) {
                ragTarget = args[++i];
            } else if ("--topks".equals(args[i]) && i + 1 < args.length) {
                String[] parts = args[++i].split(",");
                topKs = new int[parts.length];
                for (int j = 0; j < parts.length; j++) {
                    topKs[j] = Integer.parseInt(parts[j].trim());
                }
            }
        }

        ManagedChannel goCh = ManagedChannelBuilder.forTarget(goTarget).usePlaintext().build();
        ManagedChannel ragCh = ManagedChannelBuilder.forTarget(ragTarget).usePlaintext().build();

        System.out.println("go  target = " + goTarget);
        System.out.println("rag target = " + ragTarget);
        System.out.println();

        int mismatch = 0;
        for (int topK : topKs) {
            byte[] goWire;
            byte[] ragWire;
            try {
                goWire = call(goCh, topK);
            } catch (Exception e) {
                System.out.println("top_k=" + topK + " GO CALL FAILED: " + e);
                mismatch++;
                continue;
            }
            try {
                ragWire = call(ragCh, topK);
            } catch (Exception e) {
                System.out.println("top_k=" + topK + " RAG CALL FAILED: " + e);
                mismatch++;
                continue;
            }

            boolean same = Arrays.equals(goWire, ragWire);
            List<String> goItems = parseItems(goWire);
            List<String> ragItems = parseItems(ragWire);

            System.out.println("=========================================================");
            System.out.println("top_k=" + topK
                    + "   |  wire bytes go=" + goWire.length + " rag=" + ragWire.length
                    + "  |  sha256[0:16] go=" + sha256(goWire) + " rag=" + sha256(ragWire)
                    + "  |  " + (same ? "*** BYTE-IDENTICAL ***" : "*** BYTE-DIFFERENT ***"));
            System.out.println("  go  items=" + goItems.size() + "  descending=" + isDescending(goWire));
            System.out.println("  rag items=" + ragItems.size() + "  descending=" + isDescending(ragWire));
            if (!same) {
                mismatch++;
                System.out.println("  --- go  wire hex (first 128B) ---");
                System.out.println("  " + hexPreview(goWire, 128));
                System.out.println("  --- rag wire hex (first 128B) ---");
                System.out.println("  " + hexPreview(ragWire, 128));
                int n = Math.max(goItems.size(), ragItems.size());
                for (int i = 0; i < n; i++) {
                    String g = i < goItems.size() ? goItems.get(i) : "<none>";
                    String r = i < ragItems.size() ? ragItems.get(i) : "<none>";
                    System.out.println("    [" + i + "] go=" + g + "   rag=" + r + (g.equals(r) ? "" : "   <<< DIFF"));
                }
            } else {
                System.out.println("  first items: " + (goItems.size() > 5
                        ? goItems.subList(0, 5) + " ..." : goItems));
            }
        }

        System.out.println();
        System.out.println("=========================================================");
        System.out.println(mismatch == 0
                ? "RESULT: ALL top_k BYTE-IDENTICAL between Go and RAG"
                : "RESULT: " + mismatch + " top_k case(s) DIFFER");
        goCh.shutdownNow();
        ragCh.shutdownNow();
        System.exit(mismatch == 0 ? 0 : 1);
    }
}
