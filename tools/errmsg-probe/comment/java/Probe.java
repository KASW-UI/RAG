package com.rag.comment.comment.common.errmsg;

import io.grpc.Status;
import io.grpc.StatusException;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * ⚠️ tools/errmsg-probe/comment 的 Java 半边，**不是生产代码**。
 *
 * <p>它用 <b>真实的</b> {@code com.rag.comment.comment.common.errmsg.*}
 * （comment-common 模块的编译产物，不是自己 new 出来的一个副本）输出规范化 dump，
 * 交给 {@code run-parity.sh} 与 Go 半边逐字节比对。
 *
 * <p>输出格式必须与 Go 半边（{@code tools/errmsg-probe/comment/go/main.go}）逐字节相同：
 * <pre>
 * CONST|&lt;code&gt;                 枚举登记的码值（升序）
 * TABLE|&lt;code&gt;|&lt;msg&gt;           每个码经 CommentErrorMessages.getMsg(int) 取回的文案（升序）
 * FALLBACK|&lt;code&gt;|&lt;msg&gt;        未登记码的回落结果
 * CODEERR|&lt;code&gt;|&lt;errText&gt;     CommentCodeError.newCode(int) 的 getMessage()（升序）
 * CODEERRMSG|&lt;code&gt;|&lt;msg&gt;|&lt;errText&gt;  CommentCodeError.newCodeMsg(int, String) 的 getMessage()
 * WIRE|&lt;bizCode&gt;|&lt;desc&gt;        CommentGrpcError.grpcError 构造出的 description
 * OBS|&lt;case&gt;|&lt;bizCode&gt;|&lt;wireGrpcStatusValue&gt;|&lt;wireGrpcStatusName&gt;|&lt;desc&gt;|&lt;recoveredBizCode&gt;
 *                              **已知不可达差异**（业务码的 wire 载体不同，单列）
 * </pre>
 *
 * <p>⚠️ 三条必须注意的细节：
 * <ol>
 *   <li><b>不得抄写文案</b>：本探针只调真实产物里的方法，
 *       自己不持有任何码值/文案常量 —— 否则探针就变成了「拿 RAG 的值验 RAG 的值」，
 *       必然自洽、必然假绿（AGENTS.md §3.3「绿测试可能是错误答案的守卫」）。
 *       ⚠️ 例外：prodCalls 里的文案是<b>生产调用点的输入</b>（不是期望输出），
 *       必须写字面量才能复刻真实链路上的「码 → 文案」组合。</li>
 *   <li><b>必须显式用 {@code \n}</b>：{@code System.out.println} 用的是平台行分隔符，
 *       跨平台会变成 {@code \r\n} 导致字节不一致 ⇒ 这里走
 *       {@link PrintStream#write(byte[])} 直接吐 UTF-8 字节。</li>
 *   <li><b>排序</b>：Go 的 map 遍历顺序随机，Java 的 {@code values()} 是声明顺序，
 *       两侧都必须按码值升序才能逐字节比对。</li>
 * </ol>
 */
public final class Probe {

    private Probe() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("usage: Probe <cases-file>");
            System.exit(2);
        }

        List<Integer> cases = readCases(Path.of(args[0]));

        // ── Java 侧自检：枚举登记的码集合 ≡ MSG_BY_CODE 的键集合 ──────────────
        // 与 Go 半边的 selfCheck 对称：任何一侧「加了码忘了加文案」都会在这里响。
        List<Integer> constCodes = new ArrayList<>();
        for (CommentErrorCode c : CommentErrorCode.values()) {
            constCodes.add(c.getCode());
        }
        List<Integer> tableCodes = new ArrayList<>(CommentErrorMessages.MSG_BY_CODE.keySet());
        if (!constCodes.containsAll(tableCodes) || !tableCodes.containsAll(constCodes)) {
            System.err.println("JAVA SELF-CHECK FAILED: enum codes != MSG_BY_CODE keys");
            System.exit(3);
        }

        Integer[] codes = constCodes.stream().sorted().toArray(Integer[]::new);

        StringBuilder b = new StringBuilder();

        // CONST
        for (int code : codes) {
            b.append("CONST|").append(code).append('\n');
        }
        // TABLE
        for (int code : codes) {
            b.append("TABLE|").append(code).append('|').append(CommentErrorMessages.getMsg(code)).append('\n');
        }
        // FALLBACK
        for (int code : cases) {
            b.append("FALLBACK|").append(code).append('|').append(CommentErrorMessages.getMsg(code)).append('\n');
        }
        // CODEERR
        for (int code : codes) {
            b.append("CODEERR|").append(code).append('|')
                    .append(CommentCodeError.newCode(code).getMessage()).append('\n');
        }
        // CODEERRMSG：生产调用点的真实「码 → 文案」组合（msg 用传入值，**不查表**）。
        for (ProdCall pc : PROD_CALLS) {
            b.append("CODEERRMSG|").append(pc.code).append('|').append(pc.msg).append('|')
                    .append(CommentCodeError.newCodeMsg(pc.code, pc.msg).getMessage()).append('\n');
        }
        // WIRE：业务码**不在** grpc-status 里（RAG 挪到自定义 trailer）⇒
        //       但**文案逐字节在 description 里** ⇒ 这一层两侧必须一致。
        for (ProdCall pc : PROD_CALLS) {
            StatusException se = CommentGrpcError.grpcError(pc.code, pc.msg);
            b.append("WIRE|").append(pc.code).append('|').append(se.getStatus().getDescription()).append('\n');
        }

        // OBS：业务码的 wire 载体不同（两侧必然不同，单列）。
        for (ObsCall oc : OBS_CALLS) {
            StatusException se = CommentGrpcError.grpcError(oc.code, oc.msg);
            Status st = se.getStatus();
            // ⚠️ recoveredBizCode 走 CommentGrpcError.bizCodeFromError（**真实产物**）：
            //    它会从自定义 trailer `rag-comment-biz-code` 里把业务码无损取回
            //    ⇒ 业务码本身**没有丢**，丢的只是 grpc-status 字段的数值。
            b.append("OBS|").append(oc.name)
                    .append('|').append(oc.code)
                    .append('|').append(st.getCode().value())
                    .append('|').append(st.getCode().name().toLowerCase())
                    .append('|').append(st.getDescription())
                    .append('|').append(CommentGrpcError.bizCodeFromError(se))
                    .append('\n');
        }

        PrintStream out = new PrintStream(System.out, false, StandardCharsets.UTF_8);
        out.write(b.toString().getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    /** 一条「业务码 + 该调用点传入的文案」。 */
    private record ProdCall(int code, String msg) {
    }

    /** 一条 OBS 样本。 */
    private record ObsCall(String name, int code, String msg) {
    }

    /**
     * Go comment-rpc 的**真实调用点**：业务码 + 该调用点传入的文案。
     *
     * <p>⚠️ 这些文案<b>故意不等于</b> {@code CommentErrorMessages} 表里的文案：
     * {@code ERROR_DB_SELECT} 的表文案是「查询数据库失败」，而 rpc 层传的是「DB查询失败」
     * ⇒ 两侧都保留了这个差值（见 {@code docs/DRIFT/comment/common.md} §2.2）。
     *
     * <p>⚠️ 出处：{@code service/comment/rpc/internal/logic/} 下 18 处 {@code NewGrpcErr} 调用点
     * （create_comment_logic.go / delete_comment_logic.go / like_comment_logic.go /
     * manage_comment_logic.go / report_comment_logic.go / update_subject_state_logic.go）。
     */
    private static final List<ProdCall> PROD_CALLS = List.of(
            new ProdCall(CommentErrorCode.ERROR_COMMENT_NOT_EXIST.getCode(), "评论不存在"),
            new ProdCall(CommentErrorCode.ERROR_DB_INSERT.getCode(), "DB插入失败"),
            new ProdCall(CommentErrorCode.ERROR_DB_SELECT.getCode(), "DB查询失败"),
            new ProdCall(CommentErrorCode.ERROR_DB_UPDATE.getCode(), "DB修改失败"),
            new ProdCall(CommentErrorCode.ERROR_DB_UPDATE.getCode(), "DB更新失败"),
            new ProdCall(CommentErrorCode.ERROR_INPUT_WRONG.getCode(), "DB更新失败"),
            new ProdCall(CommentErrorCode.ERROR_INPUT_WRONG.getCode(), "Type和ID不能为空"),
            new ProdCall(CommentErrorCode.ERROR_INPUT_WRONG.getCode(), "操作类型输入错误"),
            new ProdCall(CommentErrorCode.ERROR_INPUT_WRONG.getCode(), "目标类型和ID不能为空"),
            new ProdCall(CommentErrorCode.ERROR_INPUT_WRONG.getCode(), "评论ID不能为空"),
            new ProdCall(CommentErrorCode.ERROR_INPUT_WRONG.getCode(), "评论ID和目标不能为空"),
            new ProdCall(CommentErrorCode.ERROR_INPUT_WRONG.getCode(), "评论内容不能为空"),
            new ProdCall(CommentErrorCode.ERROR_JSON_MARSHAL.getCode(), "Json序列化失败"),
            new ProdCall(CommentErrorCode.ERROR_KAFKA_PUSH.getCode(), "Kafka推送失败"),
            new ProdCall(CommentErrorCode.ERROR_SNOWFLAKE_ID.getCode(), "雪花算法生成ID出错"),
            new ProdCall(CommentErrorCode.ERROR_SUBJECT_NOT_EXIST.getCode(), "评论区不存在"),
            new ProdCall(CommentErrorCode.ERROR_USER_NO_RIGHT.getCode(), "无权执行删除操作"),
            new ProdCall(CommentErrorCode.ERROR_USER_NO_RIGHT.getCode(), "非up主无法评论"));

    /**
     * OBS：两侧<b>必然不同</b>的已知差异 —— 业务码在 wire 上的<b>载体</b>不同。
     *
     * <p>Go 侧把业务码<b>直接写进 grpc-status</b>（{@code errmsg.go:33-35}
     * {@code status.Error(codes.Code(code), msg)}）⇒ 线上真实字节是 {@code grpc-status: 1012}；
     * Java 侧 grpc-java <b>只能发 17 个标准码</b>（0-16）⇒ 发不出 1012
     * ⇒ RAG 把业务码挪到自定义 trailer {@code rag-comment-biz-code}
     * ⇒ {@code grpc-status} 字段的数值<b>不可复原</b>，业务码与文案仍无损。
     *
     * <p>⚠️ 这条已在 {@code docs/DRIFT/comment/common.md} §3-D1 登记为 OBSERVABLE DIFFERENCE。
     */
    private static final List<ObsCall> OBS_CALLS = List.of(
            new ObsCall("obs_wire_grpcstatus_1012",
                    CommentErrorCode.ERROR_SUBJECT_NOT_EXIST.getCode(), "评论区不存在"),
            new ObsCall("obs_wire_grpcstatus_2001",
                    CommentErrorCode.ERROR_INPUT_WRONG.getCode(), "目标类型和ID不能为空"),
            new ObsCall("obs_wire_grpcstatus_5003",
                    CommentErrorCode.ERROR_DB_SELECT.getCode(), "DB查询失败"));

    /** 读 cases 文件：一行一个 int，空行与 {@code #} 注释行忽略。 */
    private static List<Integer> readCases(Path path) throws Exception {
        List<Integer> out = new ArrayList<>();
        for (String raw : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            out.add(Integer.valueOf(line));
        }
        return out;
    }
}
