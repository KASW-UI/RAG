/*
 * ════════════════════════════════════════════════════════════════════════════
 *  GrpcMessageDualPortRunner — message 域 Go ↔ RAG 双端口对拍
 * ════════════════════════════════════════════════════════════════════════════
 *
 * 【为什么有这个文件】
 *    AGENTS.md §6.6 审计出 message-rpc 的 3 项 P0（事务失效 / conversation_id 恒 0 /
 *    ServerInterceptor 未挂载）。静态检查能证明「代码写成这样」，但证明不了
 *    「两端在同一条件下真的做出同一件事」—— §3.1 要求的是行为等价，不是代码相似。
 *
 *    既有 diff.py 是 **HTTP wire 层**的对拍，走 api 入口。而 message-rpc 是**纯 gRPC**，
 *    没有 HTTP 面 ⇒ 现有 harness 完全覆盖不到它。
 *
 *    本文件沿用 grpc-dual-port-runner.java（user 域，已验收）的范式：
 *    一个 Java 进程、两个 ManagedChannel（Go 端口 + RAG 端口）、
 *    同一个 stub 发同一请求，比对 Status.getCode() 与响应体的关键字段。
 *
 * 【位置与不进 Maven 的原因】
 *    落在 tools/difftest/ 下，与 grpc-dual-port-runner.java / probe-jdbc-cancel.java 同期同形态：
 *    都是「为对拍留档而单独运行的 Java 程序」，不进 service/ 的任何 module，
 *    不污染 RAG 业务代码，不被回归网引用。用 javac 单文件编译 + java -cp 跑。
 *
 * 【输入（全部 -Dproperty=... 系统属性）】
 *    go.message.port   默认 18082（run.sh 起的 Go message-rpc）
 *    rag.message.port  默认 58210（run.sh 起的 RAG message-rpc）
 *    go.host / rag.host 默认 127.0.0.1
 *
 * 【断言分两类】
 *
 *    A. 状态码等价（gRPC wire-level code）
 *       每档断言 go 的 Status.getCode() == rag 的 Status.getCode() == expected。
 *       expected 全部**逐条核对自冻结的 Go 真源**，出处写在每档的注释里。
 *       这一类刻意**只挑不依赖任何种子数据的分支** —— 「参数非法」「查无此人」
 *       「自己给自己发消息」在两端都是纯函数式的，不需要往库里塞任何东西，
 *       因此不会因为两套库的存量数据不同而产生噪声。
 *
 *    B. 响应体等价（happy path 的关键字段）
 *       sendChatMessage 走一遍真实写入，比对 conversation_id / message.conversation_id /
 *       status / can_send。**这一档就是 DRIFT-MSG-002 的直接判据** ——
 *       修复前 RAG 侧的 message.conversation_id 恒为 0。
 *
 * 【静态半前提】
 *    tools/difftest/check-proto-static.sh 已跑过且输出 ✓：两端 message.proto 除 option 外
 *    逐字节一致（19 个 message / 3 个枚举 / 9 个 RPC 的字段号与类型全同）。
 *    因此同 stub 双端口发的请求，响应 wire 字节兼容，无需按侧适配。
 *
 * 【不覆盖的（诚实声明）】
 *    - 事务回滚语义：需要真 PG 上制造约束冲突，属于集成测试而非双端口对拍，
 *      由 service/message/message-rpc 的 MessageTransactionWiringTest 承担。
 *    - 分页 total / unread_count 的差异（DRIFT-MSG-004）：需要在两端库里有
 *      不对称的存量数据，暂列为 UNRESOLVED。
 *    - 广播通知（依赖 admin.GetUserList 分页）、admin 域交叉行为。
 *
 * 【运行方式】
 *    1. 起两套栈（另一个终端）：
 *         ./tools/difftest/run.sh --keep --serve-only
 *    2. 跑本程序（或直接用编排脚本）：
 *         ./tools/difftest/exp-difftest-grpc-errors-message.sh
 *
 * 【输出】
 *    每档：
 *      [✓|✗] <label>    go=<code>    rag=<code>    expected=<code>
 *    末尾：
 *      ✓ message 域双端口对拍通过 / ✗ N 处不符，进程 exit code 0 / 1
 */
package tools.difftest;

import com.rag.message.message.proto.ConversationMessageListReq;
import com.rag.message.message.proto.MarkAllNotificationsReq;
import com.rag.message.message.proto.MarkNotificationReq;
import com.rag.message.message.proto.MessageServiceGrpc;
import com.rag.message.message.proto.NotificationListReq;
import com.rag.message.message.proto.SendChatMessageReq;
import com.rag.message.message.proto.SendChatMessageResp;
import com.rag.message.message.proto.SenderRole;
import com.rag.message.message.proto.UnreadSummaryReq;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import java.util.function.Function;

final class GrpcMessageDualPortRunner {

    private static final String GO_HOST  = sys("go.host",  "127.0.0.1");
    private static final String RAG_HOST = sys("rag.host", "127.0.0.1");
    private static final int    GO_PORT  = Integer.parseInt(sys("go.message.port",  "18082"));
    private static final int    RAG_PORT = Integer.parseInt(sys("rag.message.port", "58210"));

    /** 绝不可能存在的 id，用于「查无此人」分支。 */
    private static final long GHOST_ID = 99_999_999_999L;

    private GrpcMessageDualPortRunner() {}

    public static void main(String[] args) {
        System.out.println("== message 域 Go ↔ RAG 双端口对拍 ==");
        System.out.printf("  go  message-rpc = %s:%d%n", GO_HOST,  GO_PORT);
        System.out.printf("  rag message-rpc = %s:%d%n", RAG_HOST, RAG_PORT);
        System.out.println();

        ManagedChannel goCh  = ManagedChannelBuilder.forAddress(GO_HOST,  GO_PORT)
                .usePlaintext().build();
        ManagedChannel ragCh = ManagedChannelBuilder.forAddress(RAG_HOST, RAG_PORT)
                .usePlaintext().build();

        int fails = 0;
        try {
            // ── A. 状态码等价 ───────────────────────────────────────────────

            // 出处：listnotificationslogic.go:37-40
            //   if in.UserId <= 0 { errWithBizCode(codes.InvalidArgument, ErrorInvalidParam) }
            fails += check(goCh, ragCh, "INVALID_ARGUMENT: listNotifications(userId=0)",
                    c -> stub(c).listNotifications(
                            NotificationListReq.newBuilder().setUserId(0).build()),
                    Status.Code.INVALID_ARGUMENT);

            // 出处：getunreadsummarylogic.go:37-40
            fails += check(goCh, ragCh, "INVALID_ARGUMENT: getUnreadSummary(userId=0)",
                    c -> stub(c).getUnreadSummary(
                            UnreadSummaryReq.newBuilder().setUserId(0).build()),
                    Status.Code.INVALID_ARGUMENT);

            // 出处：marknotificationreadlogic.go:39-42
            //   if in.UserId <= 0 || in.NotificationId <= 0 { InvalidArgument }
            fails += check(goCh, ragCh, "INVALID_ARGUMENT: markNotificationRead(uid=1, nid=0)",
                    c -> stub(c).markNotificationRead(
                            MarkNotificationReq.newBuilder().setUserId(1).setNotificationId(0).build()),
                    Status.Code.INVALID_ARGUMENT);

            // 出处：markallnotificationsreadlogic.go:37-40
            fails += check(goCh, ragCh, "INVALID_ARGUMENT: markAllNotificationsRead(userId=0)",
                    c -> stub(c).markAllNotificationsRead(
                            MarkAllNotificationsReq.newBuilder().setUserId(0).build()),
                    Status.Code.INVALID_ARGUMENT);

            // 出处：marknotificationreadlogic.go:44-52
            //   MarkNotificationRead 返回 RowsAffected==0 ⇒ ErrRecordNotFound
            //   ⇒ errWithBizCode(codes.NotFound, ErrorNotificationMiss)
            fails += check(goCh, ragCh, "NOT_FOUND: markNotificationRead(ghost notificationId)",
                    c -> stub(c).markNotificationRead(
                            MarkNotificationReq.newBuilder()
                                    .setUserId(1).setNotificationId(GHOST_ID).build()),
                    Status.Code.NOT_FOUND);

            // 出处：getconversationmessageslogic.go:39-42（先过参数校验）
            //   if OperatorId <= 0 || ConversationId <= 0 || !validRole(OperatorRole) → InvalidArgument
            fails += check(goCh, ragCh, "INVALID_ARGUMENT: getConversationMessages(conversationId=0)",
                    c -> stub(c).getConversationMessages(
                            ConversationMessageListReq.newBuilder()
                                    .setOperatorId(1).setOperatorRole(SenderRole.USER)
                                    .setConversationId(0).build()),
                    Status.Code.INVALID_ARGUMENT);

            // 出处：getconversationmessageslogic.go:44-52
            //   FindConversationByID 报 ErrRecordNotFound ⇒ codes.NotFound + ErrorConversationMiss
            fails += check(goCh, ragCh, "NOT_FOUND: getConversationMessages(ghost conversationId)",
                    c -> stub(c).getConversationMessages(
                            ConversationMessageListReq.newBuilder()
                                    .setOperatorId(1).setOperatorRole(SenderRole.USER)
                                    .setConversationId(GHOST_ID).build()),
                    Status.Code.NOT_FOUND);

            // 出处：sendchatmessagelogic.go:42-45
            //   if SenderId <= 0 || content == "" || !validRole(SenderRole) → InvalidArgument
            fails += check(goCh, ragCh, "INVALID_ARGUMENT: sendChatMessage(content=\"\")",
                    c -> stub(c).sendChatMessage(
                            SendChatMessageReq.newBuilder()
                                    .setSenderId(1).setSenderRole(SenderRole.USER)
                                    .setRecipientId(2).setRecipientRole(SenderRole.USER)
                                    .setContent("").build()),
                    Status.Code.INVALID_ARGUMENT);

            // 出处：sendchatmessagelogic.go:76-79
            //   if recipientID == senderId && recipientRole == senderRole
            //     ⇒ errWithBizCode(codes.InvalidArgument, ErrorSelfChat)
            fails += check(goCh, ragCh, "INVALID_ARGUMENT: sendChatMessage(self-chat USER 1 → USER 1)",
                    c -> stub(c).sendChatMessage(
                            SendChatMessageReq.newBuilder()
                                    .setSenderId(1).setSenderRole(SenderRole.USER)
                                    .setRecipientId(1).setRecipientRole(SenderRole.USER)
                                    .setContent("hello").build()),
                    Status.Code.INVALID_ARGUMENT);

            // 出处：sendchatmessagelogic.go:154-161
            //   USER → 非 USER 且会话不存在 ⇒ PermissionDenied + ErrorAdminOnlyInit
            //   （ADMIN → ADMIN 也走这一支：senderRole==ADMIN 但 recipientRole != USER）
            fails += check(goCh, ragCh, "PERMISSION_DENIED: sendChatMessage(USER 1 → ADMIN 2, no conversation)",
                    c -> stub(c).sendChatMessage(
                            SendChatMessageReq.newBuilder()
                                    .setSenderId(1).setSenderRole(SenderRole.USER)
                                    .setRecipientId(2).setRecipientRole(SenderRole.ADMIN)
                                    .setContent("hello").build()),
                    Status.Code.PERMISSION_DENIED);

            // 出处：sendchatmessagelogic.go:154-161
            //   USER 发起的首条 > 50 rune ⇒ InvalidArgument + ErrorFirstMsgTooLong
            //   ⚠️ 必须用「恰好 51 个汉字」而不是 51 个 ASCII：
            //      Go 判的是 runeLen(content) > 50，Java 侧对应 codePointCount。
            //      用 ASCII 测不出 char/rune 差异，用中文才是真的在测这条规则。
            fails += check(goCh, ragCh, "INVALID_ARGUMENT: sendChatMessage(first message 51 runes > 50)",
                    c -> stub(c).sendChatMessage(
                            SendChatMessageReq.newBuilder()
                                    .setSenderId(1).setSenderRole(SenderRole.USER)
                                    .setRecipientId(2).setRecipientRole(SenderRole.USER)
                                    .setContent("汉".repeat(51)).build()),
                    Status.Code.INVALID_ARGUMENT);

            // ── B. 响应体等价：DRIFT-MSG-002 的直接判据 ────────────────────
            fails += checkHappyPath(goCh, ragCh);
        } finally {
            goCh.shutdownNow();
            ragCh.shutdownNow();
        }

        System.out.println();
        if (fails == 0) {
            System.out.println("✓ message 域双端口对拍通过");
            System.exit(0);
        } else {
            System.out.println("✗ message 域双端口对拍 FAILED: " + fails + " 处不符");
            System.exit(1);
        }
    }

    // ── B. 响应体等价 ───────────────────────────────────────────────────

    /**
     * 走一遍真实写入，比对 happy path 的关键字段。
     *
     * <p><b>为什么这一档最关键</b>：DRIFT-MSG-002 修复前，RAG 侧构造
     * {@code ConversationMessageEntity} 时漏了 {@code setConversationId}，
     * 而该字段是 primitive {@code long}（默认 0）⇒
     * 响应里的 {@code message.conversation_id} 与 DB 行都是 0。
     *
     * <p><b>为什么用变化的 uid</b>：两端是<b>两个独立数据库</b>（go_db / rag_db），
     * 但都从 (USER,1)↔(USER,2) 起步。第一次跑之后这一对会话就存在了，
     * 再跑就会走「已有会话追加消息」分支，测的不再是新建路径。
     * 用 {@code System.nanoTime()} 派生一对高位 uid，保证每次都是全新参与者对
     * ⇒ 每次都走「会话不存在 → 新建」这条最需要验证的分支。
     */
    private static int checkHappyPath(ManagedChannel go, ManagedChannel rag) {
        long uid = System.nanoTime() % 90_000_000_000L + 10_000_000_000L;
        long peer = uid + 1;

        SendChatMessageReq req = SendChatMessageReq.newBuilder()
                .setSenderId(uid).setSenderRole(SenderRole.USER)
                .setRecipientId(peer).setRecipientRole(SenderRole.USER)
                .setContent("difftest happy path")
                .build();

        SendChatMessageResp goResp  = call(go,  c -> stub(c).sendChatMessage(req));
        SendChatMessageResp ragResp = call(rag, c -> stub(c).sendChatMessage(req));

        boolean ok = true;
        if (goResp == null || ragResp == null) {
            System.out.printf("  ✗ %-58s  go=%s rag=%s（至少一侧未返回成功响应）%n",
                    "OK: sendChatMessage(fresh USER pair) 建立新会话",
                    goResp == null ? "null" : "resp",
                    ragResp == null ? "null" : "resp");
            return 1;
        }

        ok &= report("conversation_id 非 0",
                goResp.getConversationId() != 0, ragResp.getConversationId() != 0,
                () -> String.valueOf(ragResp.getConversationId()));

        // DRIFT-MSG-002 的核心判据：内层 message 的 conversation_id 必须等于外层
        ok &= report("message.conversation_id == conversation_id",
                goResp.getMessage().getConversationId() == goResp.getConversationId(),
                ragResp.getMessage().getConversationId() == ragResp.getConversationId(),
                () -> "rag: msg=" + ragResp.getMessage().getConversationId()
                        + " conv=" + ragResp.getConversationId());

        // USER→USER 首条建出来的是 PENDING，can_send 必须 false
        // （Go 出处 sendchatmessagelogic.go:190-197）
        ok &= report("status == PENDING（USER→USER 首条）",
                goResp.getStatus() == com.rag.message.message.proto.ConversationStatus.PENDING,
                ragResp.getStatus() == com.rag.message.message.proto.ConversationStatus.PENDING,
                () -> String.valueOf(ragResp.getStatus()));

        ok &= report("can_send == false（PENDING 态发起方不能连发）",
                !goResp.getCanSend(), !ragResp.getCanSend(),
                () -> String.valueOf(ragResp.getCanSend()));

        System.out.printf("      ↳ conversation_id go=%d rag=%d | message.conversation_id go=%d rag=%d%n",
                goResp.getConversationId(), ragResp.getConversationId(),
                goResp.getMessage().getConversationId(), ragResp.getMessage().getConversationId());

        if (!ok) {
            System.out.printf("  ✗ %s%n", "OK: sendChatMessage(fresh USER pair) 响应体等价");
        }
        return ok ? 0 : 1;
    }

    private static boolean report(String label, boolean goOk, boolean ragOk,
                                  java.util.function.Supplier<Object> ragValue) {
        boolean ok = goOk && ragOk;
        System.out.printf("  %s   └─ %-53s rag=%s%n",
                ok ? "✓" : "✗", label, ragValue.get());
        return ok;
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private static MessageServiceGrpc.MessageServiceBlockingStub stub(ManagedChannel ch) {
        return MessageServiceGrpc.newBlockingStub(ch);
    }

    private static String sys(String k, String d) {
        return System.getProperty(k, d);
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

    private static <T> T call(ManagedChannel ch, Function<ManagedChannel, T> call) {
        try {
            return call.apply(ch);
        } catch (RuntimeException e) {
            System.out.println("      ↳ 调用失败: " + e.getMessage());
            return null;
        }
    }
}