package com.rag.hot.hot.rpc.proto;

import com.google.protobuf.Message;
import com.rag.hot.hot.proto.CoinArticleRequest;
import com.rag.hot.hot.proto.CommentArticleRequest;
import com.rag.hot.hot.proto.GetHotArticlesRequest;
import com.rag.hot.hot.proto.GetHotArticlesResponse;
import com.rag.hot.hot.proto.HotArticleItem;
import com.rag.hot.hot.proto.LikeArticleRequest;
import com.rag.hot.hot.proto.LikeArticleResponse;
import com.rag.hot.hot.proto.ShareArticleRequest;
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
 * 🔴🔴 hot proto 的 <b>protobuf 消息体 wire 字节</b>双侧对拍。
 *
 * <h2>它补的是哪个缺口</h2>
 * <p>AGENTS.md §3.4 只保证「{@code .proto} <b>文件本身</b>两侧一致」
 * （Go 侧生成码 {@code service/hot/rpc/pb/hot.pb.go:1}），
 * <b>推不出</b>「两侧<b>生成码</b>产出的 wire 字节一致」——
 * 字段号、零值落不落 wire、repeated 的编码方式，<b>只有序列化出来才看得见</b>。
 * <p>⇒ 本类用<b>同一组消息</b>在两侧各序列化一次，比对<b>十六进制字节</b>。
 * <p>⚠️ 两侧的运行时<b>不同</b>：Go 侧是 {@code google.golang.org/protobuf}
 * （protoc-gen-go 的运行时），Java 侧是 {@code protobuf-java}
 * （protoc 的 Java 运行时）⇒ 这不是「同一份实现自证」。
 *
 * <h2>期望值的来源</h2>
 * <p>🔴 <b>全部期望值来自 Go 真源的实测</b>：{@code tools/proto-wire-probe/hot/}
 * 用<b>从 Go 真源逐字节复制</b>的 {@code service/hot/rpc/pb/hot.pb.go}
 * （Go 侧<b>真正在用</b>的生成码）算出字节，落在 {@code expected.txt}。
 * ⇒ <b>Go 真源换了生成码 ⇒ 探针的 diff 失败</b> ⇒ 提醒更新本测试。
 *
 * <h2>hot 域最容易漂的一处：{@code bool} 的零值</h2>
 * <p>🔴 {@code success = false} 在 proto3 里是<b>零值 ⇒ 完全不落 wire</b>
 * ⇒ 整条响应在 wire 上是<b>空字节</b>。
 * 而 api 层交给客户端的 JSON 是 {@code {"success": false}} ⇒
 * <b>两层表达完全不同</b>：接收端只能靠「没这个字段」判 false。
 * <p>⇒ 若某一侧给 {@code false} 写了 presence（proto2 语义 / {@code optional}）
 * ⇒ 字节不同 ⇒ 本测试的 {@code like_resp_false} 立刻红。
 *
 * <h2>为什么这个测试住在 hot-rpc 而不是 hot-proto</h2>
 * <p>⚠️ {@code hot-proto} 模块<b>没有测试依赖</b>（它的 pom 里只有生成代码所需的
 * protobuf / grpc 依赖，<b>一行 junit 都没有</b>）⇒ 往那儿加测试要先改 pom。
 * <p>⇒ 放在<b>消费方</b> hot-rpc：它本来就依赖 hot-proto，
 * 且 proto 本来就是「给 rpc 用的」⇒ 归属清晰，且不必动 proto 模块的构建。
 * <p>⇒ 副作用是好的：<b>proto 组件从「只有生成码级证据」变成「有字节级守门」</b>
 * （此前 hot/proto.md §3 的 {@code H-3} 一直开着）。
 */
class HotProtoWireParityTest {

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
                Path.of("tools/proto-wire-probe/hot/expected.txt"),
                Path.of("../tools/proto-wire-probe/hot/expected.txt"),
                Path.of("../../../tools/proto-wire-probe/hot/expected.txt"));
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
                "找不到 expected.txt（试过 " + candidates + "）—— 先跑 tools/proto-wire-probe/hot/run.sh");
    }

    // ══════════════════ 产出 RAG 侧的字节 ══════════════════

    private static String hex(Message message) {
        return HexFormat.of().formatHex(message.toByteArray());
    }

    // ══════════════════ 用例 ══════════════════

    @Test
    @DisplayName("🔴 GetHotArticlesRequest 的 wire 字节与 Go 真源逐字节一致")
    void getHotArticlesRequestWireMatchesGo() {
        // ① int32 top_k ⇒ varint。
        GetHotArticlesRequest full = GetHotArticlesRequest.newBuilder()
                .setTopK(20)
                .build();
        assertEquals(expectedHex("req_full"), hex(full));

        // ② 全零值：proto3 的标量零值**不落 wire** ⇒ 必须是**空字节**。
        //    ⚠️ 这条能抓住「某一侧给零值写了 presence」。
        assertEquals(expectedHex("req_zero"), hex(GetHotArticlesRequest.getDefaultInstance()));
        assertEquals("", hex(GetHotArticlesRequest.getDefaultInstance()),
                "零值消息在 wire 上必须是空字节（proto3 的隐式 presence）");

        // ③ 🔴 负 int32 ⇒ wire 上是 **10 字节 varint**（符号扩展成 64 位补码）。
        //    ⚠️ 这条能抓住「某一侧把 int32 编成 sint32/zigzag」（那是另一种编码 ⇒ 字节不同）。
        //       hot.proto 里 `top_k` 是 `int32`（**不是** sint32）⇒ 必须是补码 10 字节。
        GetHotArticlesRequest negative = GetHotArticlesRequest.newBuilder()
                .setTopK(-1)
                .build();
        assertEquals(expectedHex("req_negative"), hex(negative));
    }

    @Test
    @DisplayName("🔴 HotArticleItem 的 wire 字节与 Go 真源逐字节一致")
    void hotArticleItemWireMatchesGo() {
        // ④ 全字段：string 是 length-delimited；**uint32 最大值**（0xFFFFFFFF）是 **5 字节 varint**。
        //    ⚠️ protobuf-java 把 uint32 映射成 Java `int` ⇒ 传值要用 `(int) 4294967295L`
        //       （0xFFFFFFFF 的位模式）⇒ 若哪一侧把它当 int32 装，就会变成 -1 ⇒ 10 字节 ⇒ 字节不同。
        HotArticleItem full = HotArticleItem.newBuilder()
                .setArticleId("2001")
                .setHotScore((int) 4294967295L)
                .build();
        assertEquals(expectedHex("item_full"), hex(full));

        // ⑤ 只设 hot_score ⇒ **只有字段 2 落 wire**（空字符串不落 —— 那是 JSON 的行为，不是 proto3 的）。
        HotArticleItem partial = HotArticleItem.newBuilder()
                .setHotScore(1)
                .build();
        assertEquals(expectedHex("item_partial"), hex(partial));
    }

    @Test
    @DisplayName("🔴 repeated 字段（热门条目列表）的 wire 字节与 Go 真源逐字节一致")
    void repeatedFieldWireMatchesGo() {
        // ⑥ 两个条目 ⇒ 两次 tag 1 的 length-delimited。
        //    ⚠️ 这条能抓住「某一侧把 repeated 编成 map 或 JSON 数组」。
        GetHotArticlesResponse resp = GetHotArticlesResponse.newBuilder()
                .addItems(HotArticleItem.newBuilder().setArticleId("2001").setHotScore(1).build())
                .addItems(HotArticleItem.newBuilder().setArticleId("2002").setHotScore(2).build())
                .build();
        assertEquals(expectedHex("resp_two"), hex(resp));

        // ⑦ 空列表 ⇒ **空字节**（tag 都不落）。
        assertEquals(expectedHex("resp_empty"), hex(GetHotArticlesResponse.getDefaultInstance()));
        assertEquals("", hex(GetHotArticlesResponse.getDefaultInstance()),
                "空 repeated 在 wire 上必须是空字节");
    }

    @Test
    @DisplayName("🔴 bool success 的零值在 wire 上完全���落（false ⇒ 空字节）")
    void boolFalseIsNotOnTheWire() {
        // ⑧ true ⇒ varint 1。
        LikeArticleResponse ok = LikeArticleResponse.newBuilder()
                .setSuccess(true)
                .build();
        assertEquals(expectedHex("like_resp_true"), hex(ok));

        // ⑨ 🔴 false ⇒ proto3 零值**不落 wire** ⇒ 整条消息是**空字节**。
        //    ⚠️ api 层给的 JSON 是 {"success": false} ⇒ 两层表达不同 ⇒ 只有字节级对拍钉得住。
        LikeArticleResponse failed = LikeArticleResponse.newBuilder()
                .setSuccess(false)
                .build();
        assertEquals(expectedHex("like_resp_false"), hex(failed));
        assertEquals("", hex(failed),
                "success=false 在 wire 上必须是空字节（proto3 的隐式 presence）—— "
                        + "api 层的 JSON 会写 \"success\": false，两层表达本就不同");
    }

    @Test
    @DisplayName("🔴 互动请求消息的 wire 字节与 Go 真源逐字节一致")
    void interactionRequestsWireMatchesGo() {
        // ⑩ 点赞请求：两个 string 字段。
        LikeArticleRequest like = LikeArticleRequest.newBuilder()
                .setArticleId("2001")
                .setUserId("1001")
                .build();
        assertEquals(expectedHex("like_req"), hex(like));

        // ⑪ 评论请求：与点赞请求同形（两个 string）。
        CommentArticleRequest comment = CommentArticleRequest.newBuilder()
                .setArticleId("2001")
                .setUserId("1001")
                .build();
        assertEquals(expectedHex("comment_req"), hex(comment));

        // ⑫ 投币请求：string + string + int32（coin_count 是 varint，不是字符串）。
        CoinArticleRequest coin = CoinArticleRequest.newBuilder()
                .setArticleId("2001")
                .setUserId("1001")
                .setCoinCount(2)
                .build();
        assertEquals(expectedHex("coin_req"), hex(coin));

        // ⑬ 分享请求：三个 string（channel 是自由文本，不做枚举）。
        ShareArticleRequest share = ShareArticleRequest.newBuilder()
                .setArticleId("2001")
                .setUserId("1001")
                .setChannel("wechat")
                .build();
        assertEquals(expectedHex("share_req"), hex(share));
    }

    @Test
    @DisplayName("🔴 探针与本测试的用例集必须完全一致（防止有人只改了一边）")
    void expectedFileAndThisTestCoverTheSameCases() {
        // ⚠️ 本测试逐条断言 expected.txt 里的行 ⇒ 少一条会 NPE、多一条不会报错。
        // ⇒ 这里显式锁住「用例集」：Go 探针产的与 Java 侧产的必须是同一组。
        TreeSet<String> expectedNames = new TreeSet<>(expected().keySet());
        TreeSet<String> producedNames = new TreeSet<>(List.of(
                "WIRE|req_full",
                "WIRE|req_zero",
                "WIRE|req_negative",
                "WIRE|item_full",
                "WIRE|item_partial",
                "WIRE|resp_two",
                "WIRE|resp_empty",
                "WIRE|like_req",
                "WIRE|like_resp_true",
                "WIRE|like_resp_false",
                "WIRE|comment_req",
                "WIRE|coin_req",
                "WIRE|share_req"));
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
        assertEquals(13, lines.size(),
                "expected.txt 应当正好 13 条用例（新增用例时同步更新本断言与本测试）");
    }
}
