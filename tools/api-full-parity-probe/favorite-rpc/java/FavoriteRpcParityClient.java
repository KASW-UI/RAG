package probe;

import com.rag.favorite.favorite.proto.CreateFavoriteFolderReq;
import com.rag.favorite.favorite.proto.CreateFavoriteFolderResp;
import com.rag.favorite.favorite.proto.CreateFavoriteReq;
import com.rag.favorite.favorite.proto.CreateFavoriteResp;
import com.rag.favorite.favorite.proto.DeleteFavoriteFolderReq;
import com.rag.favorite.favorite.proto.DeleteFavoriteFolderResp;
import com.rag.favorite.favorite.proto.DeleteFavoriteReq;
import com.rag.favorite.favorite.proto.DeleteFavoriteResp;
import com.rag.favorite.favorite.proto.GetFavoriteFoldersReq;
import com.rag.favorite.favorite.proto.GetFavoriteFoldersResp;
import com.rag.favorite.favorite.proto.GetFavoritesByFolderReq;
import com.rag.favorite.favorite.proto.GetFavoritesByFolderResp;
import com.rag.favorite.favorite.proto.UpdateFavoriteFolderNameReq;
import com.rag.favorite.favorite.proto.UpdateFavoriteFolderNameResp;
import io.grpc.CallOptions;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.ClientCalls;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * favorite 域 **gRPC 层**双侧活体对拍客户端（探针代码，不是产品代码）。
 *
 * <p><b>它比的是「wire 上的字节」，不是「字段看起来一样」</b>：
 * <ul>
 *   <li>成功路径 ⇒ 取 <b>gRPC 响应消息的原始字节</b>（不是「解析后重新序列化」的字节）；
 *       实现方式见 {@link RawCapture}：它包住响应 marshaller，
 *       在 grpc-java 把消息体交给上层之前把<b>原始字节</b>截下来。</li>
 *   <li>失败路径 ⇒ 取 <b>gRPC status code + description</b>（本域把业务码藏在 description 文案里，
 *       所以 description 必须逐字节相同）。</li>
 * </ul>
 *
 * <p><b>为什么用「同一个客户端」打两侧</b>（与 tools/grpc-contract-probe/favorite/ 同款纪律）：
 * 帧层的原始字节由 gRPC 库决定（grpc-go ≠ grpc-java），客户端不可观测；
 * 要测的「favorite 域自己的行为」必须消掉客户端自身差异 ⇒ 两侧共用本客户端、同一份 stub。
 *
 * <p><b>为什么不用生成好的 stub</b>：{@code FavoriteServiceGrpc} 的 stub 把 marshaller 写死了，
 * 拿不到「原始字节」。这里用 {@link MethodDescriptor} 手工重建同一个方法描述符
 * （full method name 与生成码逐字相同），只为把 marshaller 换成会截获原始字节的那个。
 *
 * <p>输出格式（每行一条用例，{@code |} 分隔）：
 * <pre>
 * CASE|&lt;name&gt;|&lt;tier&gt;|&lt;grpc_code&gt;|&lt;description_hex&gt;|&lt;body_hex&gt;
 * </pre>
 * {@code tier} = {@code FROZEN}（结果只由 favorite 域自己决定 ⇒ 进 expected.txt）
 * 或 {@code AMBIENT}（结果取决于下游 user.rpc 在不在场 ⇒ 不冻结，只判 Go == RAG）。
 * 成功 ⇒ code=OK、description 为空、body 为响应消息的原始字节（hex）；
 * 失败 ⇒ code=服务端返回的 gRPC 码、description 为文案（UTF-8 → hex）、body 为空。
 */
public final class FavoriteRpcParityClient {

    /** 与 proto 的 {@code package favorite} + {@code service FavoriteService} 一致。 */
    private static final String SERVICE = "favorite.FavoriteService";

    // ── 种子里的业务 ID（与 seed.sql 逐字一致）────────────────────────────────
    private static final long F1 = 7000000000000000001L;
    private static final long F2 = 7000000000000000002L;
    private static final long F3 = 7000000000000000003L;
    private static final long F4 = 7000000000000000004L;
    private static final long ITEM2 = 8000000000000000002L;

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    public static void main(String[] args) throws Exception {
        String target = null;
        String only = null;
        for (String a : args) {
            if (a.startsWith("--target=")) {
                target = a.substring("--target=".length());
            } else if (a.startsWith("--only=")) {
                only = a.substring("--only=".length());
            }
        }
        if (target == null || target.isEmpty()) {
            System.err.println("usage: FavoriteRpcParityClient --target=host:port [--only=<caseName>]");
            System.exit(2);
        }
        ManagedChannel channel = ManagedChannelBuilder.forTarget(target).usePlaintext().build();
        try {
            for (Case c : cases()) {
                if (only != null && !only.equals(c.name)) {
                    continue;
                }
                System.out.println(render(channel, c));
                System.out.flush();
            }
        } finally {
            channel.shutdownNow();
            channel.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    /** 跑一条用例，渲染成一行输出。 */
    private static String render(ManagedChannel channel, Case c) {
        RawCapture capture = new RawCapture();
        try {
            call(channel, c, capture);
            return "CASE|" + c.name + "|" + c.tier + "|OK||" + hex(capture.last());
        } catch (StatusRuntimeException e) {
            Status st = e.getStatus();
            String desc = st.getDescription() == null ? "" : st.getDescription();
            return "CASE|" + c.name + "|" + c.tier + "|" + st.getCode().name()
                    + "|" + hex(desc.getBytes(StandardCharsets.UTF_8)) + "|";
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object call(ManagedChannel channel, Case c, RawCapture capture) {
        MethodDescriptor md = MethodDescriptor.newBuilder()
                .setType(MethodDescriptor.MethodType.UNARY)
                .setFullMethodName(MethodDescriptor.generateFullMethodName(SERVICE, c.method))
                .setRequestMarshaller(c.reqMarshaller)
                .setResponseMarshaller(capture.wrap(c.respMarshaller))
                .build();
        return ClientCalls.blockingUnaryCall(channel, md,
                CallOptions.DEFAULT.withDeadlineAfter(20, TimeUnit.SECONDS), c.request);
    }

    /**
     * 🔴 <b>截获 wire 上的原始字节</b>的 marshaller 包装。
     *
     * <p>grpc-java 的 {@code ClientCallImpl.StreamListenerToCallListenerAdapter.onMessage(InputStream)}
     * 会把<b>未经解析的消息体字节</b>交给 {@code MethodDescriptor.parseResponse(stream)}
     * ⇒ 只要把 {@code responseMarshaller} 换成会先把这串字节读出来再委托解析的实现，
     * 就能拿到「服务端真正发在 wire 上的那串字节」，而不是「解析后再序列化」的字节。
     *
     * <p>⚠️ <b>为什么不能用「解析后重新序列化」</b>：那会把「服务端发的字段顺序」抹平成
     * 「客户端库的字段顺序」⇒ 字段序漂移就看不见了。本域的 proto 全是标量 + repeated，
     * 两侧序列化顺序一旦不同，重新序列化会把差异吃掉 ⇒ 探针变成假绿。
     */
    static final class RawCapture {
        private byte[] last = new byte[0];

        @SuppressWarnings({"rawtypes", "unchecked"})
        MethodDescriptor.Marshaller wrap(final MethodDescriptor.Marshaller delegate) {
            return new MethodDescriptor.Marshaller() {
                @Override
                public InputStream stream(Object value) {
                    return delegate.stream(value);
                }

                @Override
                public Object parse(InputStream stream) {
                    try {
                        byte[] raw = stream.readAllBytes();
                        last = raw;
                        return delegate.parse(new ByteArrayInputStream(raw));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            };
        }

        byte[] last() {
            return last;
        }
    }

    @SuppressWarnings("rawtypes")
    private static final class Case {
        final String name;
        final String method;
        final Object request;
        final MethodDescriptor.Marshaller reqMarshaller;
        final MethodDescriptor.Marshaller respMarshaller;
        final String tier;

        Case(String name, String method, Object request,
             MethodDescriptor.Marshaller reqMarshaller, MethodDescriptor.Marshaller respMarshaller,
             String tier) {
            this.name = name;
            this.method = method;
            this.request = request;
            this.reqMarshaller = reqMarshaller;
            this.respMarshaller = respMarshaller;
            this.tier = tier;
        }
    }

    /**
     * 🔴 <b>用例分两档</b>（2026-10-29 实测踩出来的）：
     *
     * <ul>
     *   <li>{@code FROZEN} —— 结果<b>只由 favorite 域自己决定</b> ⇒ 进 {@code expected.txt}，
     *       判据 = <b>Go == RAG == expected.txt</b>。</li>
     *   <li>{@code AMBIENT} —— 结果<b>依赖下游 user.rpc 是否在场</b> ⇒ <b>不进</b> {@code expected.txt}，
     *       只判 <b>Go == RAG</b>（且不一致也只告警、不判红）。
     *       <br>原因：<b>两侧用的是不同的注册中心</b> —— Go 侧走 <b>etcd</b>（{@code Etcd.Key: user.rpc}），
     *       RAG 侧走 <b>Nacos</b>（{@code discovery:///user-rpc} ⇒ 服务名 {@code user-rpc}）
     *       ⇒ 这两者是**各自独立的** ⇒ 「下游在不在场」是**环境态**，
     *       而且两侧可以**同时在场**（别的 lane 的探针会往两个注册中心各注册一个 user-rpc 桩）。
     *       ⇒ 冻结它的值 = 冻结一个**环境态** ⇒ 下一轮必假红。</li>
     * </ul>
     */
    private static final String FROZEN = "FROZEN";
    private static final String AMBIENT = "AMBIENT";

    @SuppressWarnings("rawtypes")
    private static Case c(String name, String method, Object request) {
        return new Case(name, method, request, reqMarshaller(method), respMarshaller(method), FROZEN);
    }

    @SuppressWarnings("rawtypes")
    private static Case ambient(String name, String method, Object request) {
        return new Case(name, method, request, reqMarshaller(method), respMarshaller(method), AMBIENT);
    }

    @SuppressWarnings("rawtypes")
    private static MethodDescriptor.Marshaller reqMarshaller(String method) {
        switch (method) {
            case "GetFavoriteFolders":
                return ProtoUtils.marshaller(GetFavoriteFoldersReq.getDefaultInstance());
            case "GetFavoritesByFolder":
                return ProtoUtils.marshaller(GetFavoritesByFolderReq.getDefaultInstance());
            case "UpdateFavoriteFolderName":
                return ProtoUtils.marshaller(UpdateFavoriteFolderNameReq.getDefaultInstance());
            case "DeleteFavoriteFolder":
                return ProtoUtils.marshaller(DeleteFavoriteFolderReq.getDefaultInstance());
            case "DeleteFavorite":
                return ProtoUtils.marshaller(DeleteFavoriteReq.getDefaultInstance());
            case "CreateFavoriteFolder":
                return ProtoUtils.marshaller(CreateFavoriteFolderReq.getDefaultInstance());
            default:
                throw new IllegalArgumentException("未知方法：" + method);
        }
    }

    @SuppressWarnings("rawtypes")
    private static MethodDescriptor.Marshaller respMarshaller(String method) {
        switch (method) {
            case "GetFavoriteFolders":
                return ProtoUtils.marshaller(GetFavoriteFoldersResp.getDefaultInstance());
            case "GetFavoritesByFolder":
                return ProtoUtils.marshaller(GetFavoritesByFolderResp.getDefaultInstance());
            case "UpdateFavoriteFolderName":
                return ProtoUtils.marshaller(UpdateFavoriteFolderNameResp.getDefaultInstance());
            case "DeleteFavoriteFolder":
                return ProtoUtils.marshaller(DeleteFavoriteFolderResp.getDefaultInstance());
            case "DeleteFavorite":
                return ProtoUtils.marshaller(DeleteFavoriteResp.getDefaultInstance());
            case "CreateFavoriteFolder":
                return ProtoUtils.marshaller(CreateFavoriteFolderResp.getDefaultInstance());
            default:
                throw new IllegalArgumentException("未知方法：" + method);
        }
    }

    /**
     * 用例矩阵。
     *
     * <p>🔴 <b>顺序 = 副作用顺序</b>：只读全在前，写在后，且写之后不再读
     * （「改名」会推进 {@code update_time} ⇒ 之后的读会拿到「本次运行的时刻」⇒ 冻结值必假红）。
     *
     * <p>分组：a=读收藏夹 / b=读条目 / c=读错误 / d=改名 / e=删除 / f=建收藏夹 / g=级联删除。
     */
    private static List<Case> cases() {
        List<Case> l = new ArrayList<>();

        // ── A 读成功（种子数据可见 ⇒ 「两侧都空」被排除）────────────────────
        l.add(c("a01_get_folders_user1001", "GetFavoriteFolders",
                GetFavoriteFoldersReq.newBuilder().setUserId(1001L).build()));
        l.add(c("a02_get_folders_user1002", "GetFavoriteFolders",
                GetFavoriteFoldersReq.newBuilder().setUserId(1002L).build()));
        l.add(c("a03_get_folders_user9999", "GetFavoriteFolders",
                GetFavoriteFoldersReq.newBuilder().setUserId(9999L).build()));

        // ── B 读条目 ───────────────────────────────────────────────────────
        l.add(c("b01_get_items_folder1", "GetFavoritesByFolder",
                GetFavoritesByFolderReq.newBuilder().setUserId(1001L).setFolderId(F1).build()));
        l.add(c("b02_get_items_folder2", "GetFavoritesByFolder",
                GetFavoritesByFolderReq.newBuilder().setUserId(1001L).setFolderId(F2).build()));

        // ── C 读错误（Go: get_favorite_folders_logic.go:49 / get_favorites_by_folder_logic.go:54）──
        l.add(c("c01_get_folders_uid0", "GetFavoriteFolders",
                GetFavoriteFoldersReq.newBuilder().setUserId(0L).build()));
        l.add(c("c02_get_folders_uid_neg", "GetFavoriteFolders",
                GetFavoriteFoldersReq.newBuilder().setUserId(-7L).build()));
        l.add(c("c03_get_items_uid0", "GetFavoritesByFolder",
                GetFavoritesByFolderReq.newBuilder().setUserId(0L).setFolderId(F1).build()));
        l.add(c("c04_get_items_folder_missing", "GetFavoritesByFolder",
                GetFavoritesByFolderReq.newBuilder().setUserId(1001L).setFolderId(7999999999999999999L).build()));
        l.add(c("c05_get_items_other_user", "GetFavoritesByFolder",
                GetFavoritesByFolderReq.newBuilder().setUserId(2002L).setFolderId(F1).build()));

        // ── D 改名（无下游依赖：Go update_favorite_folder_name_logic.go）──────
        l.add(c("d01_update_uid0", "UpdateFavoriteFolderName",
                UpdateFavoriteFolderNameReq.newBuilder().setUserId(0L).setFolderId(F1).setName("x").build()));
        l.add(c("d02_update_name_empty", "UpdateFavoriteFolderName",
                UpdateFavoriteFolderNameReq.newBuilder().setUserId(1001L).setFolderId(F1).setName("   ").build()));
        l.add(c("d03_update_not_found", "UpdateFavoriteFolderName",
                UpdateFavoriteFolderNameReq.newBuilder().setUserId(1001L)
                        .setFolderId(7999999999999999999L).setName("x").build()));
        l.add(c("d04_update_other_user", "UpdateFavoriteFolderName",
                UpdateFavoriteFolderNameReq.newBuilder().setUserId(2002L).setFolderId(F1).setName("x").build()));
        l.add(c("d05_update_conflict_name", "UpdateFavoriteFolderName",
                UpdateFavoriteFolderNameReq.newBuilder().setUserId(1001L).setFolderId(F1).setName("收藏夹B").build()));
        l.add(c("d06_update_same_name", "UpdateFavoriteFolderName",
                UpdateFavoriteFolderNameReq.newBuilder().setUserId(1001L).setFolderId(F1).setName("收藏夹A").build()));
        l.add(c("d07_update_new_name", "UpdateFavoriteFolderName",
                UpdateFavoriteFolderNameReq.newBuilder().setUserId(1001L).setFolderId(F1).setName("收藏夹C").build()));

        // ── E 删除（只打失败分支 ⇒ 无副作用）─────────────────────────────────
        l.add(c("e01_delete_folder_uid0", "DeleteFavoriteFolder",
                DeleteFavoriteFolderReq.newBuilder().setUserId(0L).setFolderId(F4).build()));
        l.add(c("e02_delete_folder_not_found", "DeleteFavoriteFolder",
                DeleteFavoriteFolderReq.newBuilder().setUserId(1001L)
                        .setFolderId(7999999999999999999L).build()));
        l.add(c("e03_delete_folder_other_user", "DeleteFavoriteFolder",
                DeleteFavoriteFolderReq.newBuilder().setUserId(2002L).setFolderId(F3).build()));
        l.add(c("e04_delete_item_not_found", "DeleteFavorite",
                DeleteFavoriteReq.newBuilder().setUserId(1001L)
                        .setFavoriteId(8999999999999999999L).build()));
        l.add(c("e05_delete_item_other_user", "DeleteFavorite",
                DeleteFavoriteReq.newBuilder().setUserId(2002L).setFavoriteId(ITEM2).build()));

        // ── F 建收藏夹（ensureUserExists 在 user.rpc 缺席 ⇒ 走「下游不可用」分支）──
        l.add(c("f01_create_uid0", "CreateFavoriteFolder",
                CreateFavoriteFolderReq.newBuilder().setUserId(0L).setName("新夹").build()));
        l.add(c("f02_create_name_empty", "CreateFavoriteFolder",
                CreateFavoriteFolderReq.newBuilder().setUserId(1001L).setName("  ").build()));
        // ⚠️ **AMBIENT 档**：这一步会走 ensureUserExists ⇒ 结果取决于「下游 user.rpc 在不在场」。
        //    两侧的注册中心**不同**（Go=etcd 的 user.rpc · RAG=Nacos 的 user-rpc）⇒ 在场与否是环境态
        //    ⇒ 只判 Go == RAG，不冻结。
        l.add(ambient("f03_create_downstream_absent", "CreateFavoriteFolder",
                CreateFavoriteFolderReq.newBuilder().setUserId(1001L).setName("新夹").build()));

        // ── G 级联删除（最后一条；有副作用）──────────────────────────────────
        l.add(c("g01_delete_folder_cascade", "DeleteFavoriteFolder",
                DeleteFavoriteFolderReq.newBuilder().setUserId(1001L).setFolderId(F4).build()));

        return l;
    }

    private static String hex(byte[] b) {
        char[] out = new char[b.length * 2];
        for (int i = 0; i < b.length; i++) {
            int v = b[i] & 0xFF;
            out[i * 2] = HEX[v >>> 4];
            out[i * 2 + 1] = HEX[v & 0x0F];
        }
        return new String(out);
    }
}
