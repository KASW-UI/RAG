package com.rag.comment.comment.rpc.proto;

import com.google.protobuf.Message;
import com.rag.comment.comment.proto.CommentItem;
import com.rag.comment.comment.proto.CommentState;
import com.rag.comment.comment.proto.CreateCommentReq;
import com.rag.comment.comment.proto.GetCommentResp;
import com.rag.comment.comment.proto.LikeCommentReq;
import com.rag.comment.comment.proto.LikeCommentResp;
import com.rag.comment.comment.proto.ReportCommentReq;
import com.rag.comment.comment.proto.ReportReason;
import com.rag.comment.comment.proto.SubjectInfo;
import com.rag.comment.comment.proto.SubjectState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 🔴🔴 comment proto 的 <b>protobuf 消息体 wire 字节</b>双侧对拍。
 *
 * <h2>它补的是哪个缺口</h2>
 * <p>AGENTS.md §3.4 只保证「{@code .proto} <b>文件本身</b>两侧一致」
 * （Go 侧生成码 {@code service/comment/rpc/pb/comment.pb.go:1}），
 * <b>推不出</b>「两侧<b>生成码</b>产出的 wire 字节一致」——
 * 字段号、零值落不落 wire、repeated 的编码方式，<b>只有序列化出来才看得见</b>。
 * <p>⇒ 本类用<b>同一组消息</b>在两侧各序列化一次，比对<b>十六进制字节</b>。
 * <p>⚠️ 两侧的运行时<b>不同</b>：Go 侧是 {@code google.golang.org/protobuf}
 * （protoc-gen-go 的运行时），Java 侧是 {@code protobuf-java}
 * （protoc 的 Java 运行时）⇒ 这不是「同一份实现自证」。
 *
 * <h2>期望值的来源</h2>
 * <p>🔴 <b>全部期望值来自 Go 真源的实测</b>：{@code tools/proto-wire-probe/comment/}
 * 用<b>从 Go 真源逐字节复制</b>的 {@code service/comment/rpc/pb/comment.pb.go}
 * （Go 侧<b>真正在用</b>的生成码）算出字节，落在 {@code expected.txt}。
 * ⇒ <b>Go 真源换了生成码 ⇒ 探针的 diff 失败</b> ⇒ 提醒更新本测试。
 *
 * <h2>comment 域最容易漂的三处</h2>
 * <ol>
 *   <li><b>int64 极多</b>（{@code id} / {@code user_id} / {@code root_id} / {@code like_count} …）
 *       ⇒ <b>负 int64 在 wire 上是 10 字节 varint</b>（符号扩展成 64 位补码）。
 *       若某一侧编成 {@code sint32}/zigzag ⇒ 字节不同 ⇒ 本测试立刻红。</li>
 *   <li><b>4 个枚举</b>（{@code CommentState} / {@code SubjectState} /
 *       {@code ReportReason} / {@code ManageType}）⇒ <b>零值不落 wire</b>
 *       ⇒ {@code STATE_NORMAL} 的 {@code CommentItem} 与空消息<b>同形</b>（空字节）。</li>
 *   <li><b>5 个 {@code bool success}</b>（like / delete / manage / report / updateSubjectState）
 *       ⇒ {@code false} <b>完全不落 wire</b> ⇒ 整条响应是<b>空字节</b>；
 *       而 api 层交给客户端的 JSON 会写 {@code "success": false}
 *       ⇒ <b>两层的表达完全不同</b> ⇒ 接收端只能靠「没这个字段」判 false。</li>
 * </ol>
 *
 * <h2>🔴 本探针<b>不</b>覆盖的一格：{@code owner_id}</h2>
 * <p>Go 真源的 {@code proto/comment.proto:105} 有 {@code int64 owner_id = 7}，
 * 但 Go 侧<b>真正在用的生成码</b> {@code service/comment/rpc/pb/comment.pb.go}
 * 里<b>根本没有</b> {@code owner_id} / {@code OwnerId}（连 rawDesc 都没有
 * ⇒ 该生成码比 {@code .proto} 旧）。
 * <p>⇒ 两侧的 proto 定义<b>本身就不等</b> ⇒ 这正是「{@code .proto} 一致 ⇒ 生成码一致」
 * 这条假设的<b>反例</b>（AGENTS.md §3.4 效力边界第 1 条）。
 * <p>⇒ <b>两侧都不落这个字段</b>：Go 侧无字段可设；RAG 侧虽生成出了 {@code ownerId}，
 * 但 rpc 层刻意不设（{@code CommentDomainService} 组装 {@code SubjectInfo} 时逐字段赋值、
 * 无 {@code owner_id}）⇒ ⇒ 字节一致。
 * <p>⇒ <b>因此该字段不进本测试的用例集</b>（Go 侧无法对称设置 ⇒ 比不了），
 * 且<b>不在</b>本测试的「用例集一致」断言里。
 *
 * <h2>为什么这个测试住在 comment-rpc 而不是 comment-proto</h2>
 * <p>⚠️ {@code comment-proto} 模块<b>没有测试依赖</b>（它的 pom 里只有生成代码所需的
 * protobuf / grpc 依赖，<b>一行 junit 都没有</b>）⇒ 往那儿加测试要先改 pom。
 * <p>⇒ 放在<b>消费方</b> comment-rpc：它本来就依赖 comment-proto，
 * 且 proto 本来就是「给 rpc 用的」⇒ 归属清晰，且不必动 proto 模块的构建。
 * <p>⇒ 副作用是好的：<b>proto 组件从「只有生成码级证据」变成「有字节级守门」</b>
 * （此前 {@code comment/vs-common.md} 的 {@code C-5}（A7 半边）一直开着）。
 */
class CommentProtoWireParityTest {

    // ══════════════════ 期望值（Go 真源实测） ══════════════════

    private static Map<String, String> expected() {
        Map<String, String> map = new LinkedHashMap<>();
        for (String line : readExpected()) {
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.split("\\|", 3);
            map.put(parts[0] + "|" + parts[1], parts[2]);
        }
        return map;
    }

    private static String expectedHex(String name) {
        String hex = expected().get("WIRE|" + name);
        if (hex == null) {
            throw new IllegalStateException(
                    "expected.txt 里没有 WIRE|" + name + "（探针与本测试的用例集不一致）");
        }
        return hex;
    }

    private static List<String> readExpected() {
        List<Path> candidates = List.of(
                Path.of("tools/proto-wire-probe/comment/expected.txt"),
                Path.of("../tools/proto-wire-probe/comment/expected.txt"),
                Path.of("../../../tools/proto-wire-probe/comment/expected.txt"));
        for (Path p : candidates) {
            if (Files.isRegularFile(p)) {
                try {
                    return Files.readAllLines(p, StandardCharsets.UTF_8);
                } catch (Exception e) {
                    throw new IllegalStateException("读不到 expected.txt：" + p.toAbsolutePath(), e);
                }
            }
        }
        throw new IllegalStateException(
                "找不到 expected.txt（试过 " + candidates + "）—— 先跑 tools/proto-wire-probe/comment/run.sh");
    }

    // ══════════════════ 产出 RAG 侧的字节 ══════════════════

    private static String hex(Message message) {
        return HexFormat.of().formatHex(message.toByteArray());
    }

    // ══════════════════ 用例 ══════════════════

    @Test
    @DisplayName("🔴 CommentItem 的 wire 字节与 Go 真源逐字节一致")
    void commentItemWireMatchesGo() {
        // ① 全字段：int64 是 varint；string 是 length-delimited；**枚举非零**才落 wire。
        CommentItem full = CommentItem.newBuilder()
                .setId(9007199254740993L)
                .setUserId(1001L)
                .setContent("你好")
                .setLikeCount(7L)
                .setState(CommentState.STATE_DELETED)
                .setCreatedAt("2026-01-02 03:04:05")
                .build();
        assertEquals(expectedHex("item_full"), hex(full));

        // ② 全零值：proto3 的标量零值**不落 wire** ⇒ 必须是**空字节**。
        //    ⚠️ 这条能抓住「某一侧给零值写了 presence」。
        assertEquals(expectedHex("item_zero"), hex(CommentItem.getDefaultInstance()));
        assertEquals("", hex(CommentItem.getDefaultInstance()),
                "零值消息在 wire 上必须是空字节（proto3 的隐式 presence）");

        // ③ 🔴 负 int64 ⇒ wire 上是 **10 字节 varint**（符号扩展成 64 位补码）。
        //    ⚠️ 这条能抓住「某一侧把 int64 编成 sint32/zigzag」（那是另一种编码 ⇒ 字节不同）。
        assertEquals(expectedHex("item_id_negative"),
                hex(CommentItem.newBuilder().setId(-1L).build()));

        // ④ int64 上界 9223372036854775807 ⇒ **10 字节 varint**。
        //    ⚠️ 这条能抓住「某一侧用 int32 装它 ⇒ 截断 ⇒ 字节不同」。
        assertEquals(expectedHex("item_id_max"),
                hex(CommentItem.newBuilder().setId(Long.MAX_VALUE).build()));
    }

    @Test
    @DisplayName("🔴 枚举的零值在 wire 上完全不落（STATE_NORMAL ⇒ 空字节）")
    void enumZeroValueIsNotOnTheWire() {
        // ⑤ 枚举零值（STATE_NORMAL = 0）⇒ **不落 wire** ⇒ 与「全零值」同形（空字节）。
        //    ⚠️ 这条能抓住「某一侧把枚举编成带 presence 的 optional」。
        CommentItem normal = CommentItem.newBuilder().setState(CommentState.STATE_NORMAL).build();
        assertEquals(expectedHex("item_state_normal"), hex(normal));
        assertEquals("", hex(normal),
                "STATE_NORMAL 在 wire 上必须不落（proto3 的隐式 presence）");

        // ⑥⑦ 枚举非零 ⇒ 落 wire（tag 10 ⇒ 0x50，值分别是 2 / 3）。
        assertEquals(expectedHex("item_state_deleted"),
                hex(CommentItem.newBuilder().setState(CommentState.STATE_DELETED).build()));
        assertEquals(expectedHex("item_state_blocked"),
                hex(CommentItem.newBuilder().setState(CommentState.STATE_BLOCKED).build()));
    }

    @Test
    @DisplayName("🔴 repeated 消息字段与嵌套消息的 wire 字节与 Go 真源逐字节一致")
    void repeatedAndNestedWireMatchesGo() {
        // ⑧ repeated 消息：一条子评论 ⇒ 「tag 13 的 length-delimited」。
        //    ⚠️ 这条能抓住「某一侧把 repeated 编成 map 或 JSON 数组」。
        CommentItem withChild = CommentItem.newBuilder()
                .setId(1L)
                .addChildren(CommentItem.newBuilder().setId(2L).build())
                .build();
        assertEquals(expectedHex("item_children"), hex(withChild));

        // ⑨ 响应：两条评论 ＋ 一个嵌套的 SubjectInfo ⇒ repeated + 嵌套消息同时验。
        GetCommentResp resp = GetCommentResp.newBuilder()
                .addComment(CommentItem.newBuilder().setId(1L).setUserId(1001L).setContent("一").build())
                .addComment(CommentItem.newBuilder().setId(2L).setUserId(1001L).setContent("二").build())
                .setSubject(SubjectInfo.newBuilder()
                        .setTargetType("article")
                        .setTargetId("2001")
                        .setTotalCount(2L)
                        .setRootCount(1L)
                        .setState(SubjectState.SUBJECT_ONLY)
                        .setAttribute(3L)
                        .build())
                .build();
        assertEquals(expectedHex("resp_two"), hex(resp));

        // ⑩ 空响应：repeated 为空 ＋ 嵌套消息为 nil ⇒ **空字节**（tag 都不落）。
        assertEquals(expectedHex("resp_empty"), hex(GetCommentResp.getDefaultInstance()));
        assertEquals("", hex(GetCommentResp.getDefaultInstance()),
                "空 repeated + 无嵌套消息在 wire 上必须是空字节");
    }

    @Test
    @DisplayName("🔴 int32 action_type 与 bool success 的 wire 字节与 Go 真源逐字节一致")
    void actionTypeAndBoolWireMatchesGo() {
        // ⑪ 负 int32（action_type）⇒ 同样 **10 字节 varint**（int32 也符号扩展成 64 位）。
        //    ⚠️ 这条能抓住「某一侧把 int32 编成 sint32」（zigzag ⇒ 字节完全不同）。
        assertEquals(expectedHex("like_req_action_negative"),
                hex(LikeCommentReq.newBuilder()
                        .setUserId(1001L)
                        .setCommentId(2001L)
                        .setActionType(-1)
                        .build()));

        // ⑫ true ⇒ varint 1。
        assertEquals(expectedHex("like_resp_true"),
                hex(LikeCommentResp.newBuilder().setSuccess(true).build()));

        // ⑬ 🔴 false ⇒ proto3 零值**不落 wire** ⇒ 整条消息是**空字节**。
        //    ⚠️ api 层给的 JSON 是 {"success": false} ⇒ 两层表达不同 ⇒ 只有字节级对拍钉得住。
        LikeCommentResp failed = LikeCommentResp.newBuilder().setSuccess(false).build();
        assertEquals(expectedHex("like_resp_false"), hex(failed));
        assertEquals("", hex(failed),
                "success=false 在 wire 上必须是空字节（proto3 的隐式 presence）—— "
                        + "api 层的 JSON 会写 \"success\": false，两层表达本就不同");
    }

    @Test
    @DisplayName("🔴 枚举 99（REASON_OTHER）与嵌套 SubjectInfo 的 wire 字节与 Go 真源逐字节一致")
    void enum99AndSubjectWireMatchesGo() {
        // ⑭ 枚举 99（REASON_OTHER）⇒ **单字节 varint**（99 < 128 ⇒ 0x63）。
        //    ⚠️ 这条能抓住「某一侧把枚举编成 string」或「用了 sint32」。
        assertEquals(expectedHex("report_req_reason_other"),
                hex(ReportCommentReq.newBuilder()
                        .setUserId(1001L)
                        .setCommentId(2001L)
                        .setReason(ReportReason.REASON_OTHER)
                        .setDetail("详情")
                        .build()));

        // ⑮ 嵌套消息全字段（SubjectInfo 的**两侧共有**那 6 个字段；owner_id 两侧都不发）。
        assertEquals(expectedHex("subject_full"),
                hex(SubjectInfo.newBuilder()
                        .setTargetType("article")
                        .setTargetId("2001")
                        .setTotalCount(12L)
                        .setRootCount(5L)
                        .setState(SubjectState.SUBJECT_CLOSED)
                        .setAttribute(0L)
                        .build()));
    }

    @Test
    @DisplayName("🔴 中文 UTF-8 的 string 按字节长度算 length-delimited")
    void utf8StringLengthIsInBytes() {
        // ⑯ 中文 UTF-8：length-delimited 的长度是**字节数**（不是字符数）。
        //    ⚠️ 这条能抓住「某一侧按 rune 数算长度」。
        assertEquals(expectedHex("create_req_utf8"),
                hex(CreateCommentReq.newBuilder()
                        .setUserId(1001L)
                        .setContent("中文评论")
                        .build()));
    }

    @Test
    @DisplayName("🔴 探针与本测试的用例集必须完全一致（防止有人只改了一边）")
    void expectedFileAndThisTestCoverTheSameCases() {
        // ⚠️ 本测试逐条断言 expected.txt 里的行 ⇒ 少一条会 NPE、多一条不会报错。
        // ⇒ 这里显式锁住「用例集」：Go 探针产的与 Java 侧产的必须是同一组。
        TreeSet<String> expectedNames = new TreeSet<>(expected().keySet());
        TreeSet<String> producedNames = new TreeSet<>(List.of(
                "WIRE|item_full",
                "WIRE|item_zero",
                "WIRE|item_id_negative",
                "WIRE|item_id_max",
                "WIRE|item_state_normal",
                "WIRE|item_state_deleted",
                "WIRE|item_state_blocked",
                "WIRE|item_children",
                "WIRE|resp_two",
                "WIRE|resp_empty",
                "WIRE|like_req_action_negative",
                "WIRE|like_resp_true",
                "WIRE|like_resp_false",
                "WIRE|report_req_reason_other",
                "WIRE|subject_full",
                "WIRE|create_req_utf8"));
        assertEquals(expectedNames, producedNames,
                "Go 探针与 Java 侧的用例集必须一致（少一条 = 假绿，多一条 = 没人在比）");
    }

    /** 兜底：expected.txt 的行数不该变化（防止有人手改冻结文件）。 */
    @Test
    @DisplayName("📌 expected.txt 的行数与用例数一致")
    void expectedFileLineCountIsStable() {
        List<String> lines = new ArrayList<>();
        for (String line : readExpected()) {
            if (!line.isBlank()) {
                lines.add(line);
            }
        }
        assertEquals(16, lines.size(),
                "expected.txt 应当正好 16 条用例（新增用例时同步更新本断言与本测试）");
    }
}
