package com.rag.favorite.favorite.rpc.proto;

import com.google.protobuf.Message;
import com.rag.favorite.favorite.proto.CreateFavoriteReq;
import com.rag.favorite.favorite.proto.FavoriteFolder;
import com.rag.favorite.favorite.proto.FavoriteItem;
import com.rag.favorite.favorite.proto.GetFavoriteFoldersResp;
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
 * 🔴🔴 favorite proto 的 <b>protobuf 消息体 wire 字节</b>双侧对拍。
 *
 * <h2>它补的是哪个缺口</h2>
 * <p>AGENTS.md §3.4 只保证「{@code .proto} <b>文件本身</b>两侧一致」，
 * <b>推不出</b>「两侧<b>生成码</b>产出的 wire 字节一致」——
 * 字段号、零值落不落 wire、repeated 的编码方式，<b>只有序列化出来才看得见</b>。
 * <p>⇒ 本类用<b>同一组消息</b>在两侧各序列化一次，比对<b>十六进制字节</b>。
 * <p>⚠️ 两侧的运行时<b>不同</b>：Go 侧是 {@code google.golang.org/protobuf}
 * （protoc-gen-go 的运行时），Java 侧是 {@code protobuf-java}
 * （protoc 的 Java 运行时）⇒ 这不是「同一份实现自证」。
 *
 * <h2>期望值的来源</h2>
 * <p>🔴 <b>全部期望值来自 Go 真源的实测</b>：{@code tools/proto-wire-probe/favorite/}
 * 用<b>从 Go 真源逐字节复制</b>的 {@code service/favorite/rpc/pb/favorite.pb.go}
 * （Go 侧<b>真正在用</b>的生成码）算出字节，落在 {@code expected.txt}。
 * ⇒ <b>Go 真源换了生成码 ⇒ 探针的 diff 失败</b> ⇒ 提醒更新本测试。
 *
 * <h2>为什么这个测试住在 favorite-rpc 而不是 favorite-proto</h2>
 * <p>⚠️ {@code favorite-proto} 模块<b>没有测试依赖</b>（它的 pom 里只有生成代码所需的
 * protobuf / grpc 依赖，<b>一行 junit 都没有</b>）⇒ 往那儿加测试要先改 pom。
 * <p>⇒ 放在<b>消费方</b> favorite-rpc：它本来就依赖 favorite-proto，
 * 且 proto 本来就是「给 rpc 用的」⇒ 归属清晰，且不必动 proto 模块的构建。
 * <p>⇒ 副作用是好的：<b>proto 组件从「0 例测试」变成「有 1 个字节级守门」</b>
 * （此前 proto.md §2 的「零值不落 wire」只有<b>生成码级</b>证据）。
 */
class FavoriteProtoWireParityTest {

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
                Path.of("tools/proto-wire-probe/favorite/expected.txt"),
                Path.of("../tools/proto-wire-probe/favorite/expected.txt"),
                Path.of("../../../tools/proto-wire-probe/favorite/expected.txt"));
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
                "找不到 expected.txt（试过 " + candidates + "）—— 先跑 tools/proto-wire-probe/favorite/run.sh");
    }

    // ══════════════════ 产出 RAG 侧的字节 ══════════════════

    private static String hex(Message message) {
        return HexFormat.of().formatHex(message.toByteArray());
    }

    // ══════════════════ 用例 ══════════════════

    @Test
    @DisplayName("🔴 FavoriteFolder 的 wire 字节与 Go 真源逐字节一致")
    void favoriteFolderWireMatchesGo() {
        // ① 全部字段都有值。
        //    ⚠️ 用 2^53+1 的雪花 ID：int64 在 wire 上是 varint，不会丢精度 ——
        //    但某一侧若先转成 JSON 数字，字节就不同。
        FavoriteFolder full = FavoriteFolder.newBuilder()
                .setFolderId(9007199254740993L)
                .setUserId(1001L)
                .setName("技术")
                .setCreatedAt(1700000000123L)
                .setUpdatedAt(1700000000456L)
                .build();
        assertEquals(expectedHex("folder_full"), hex(full));

        // ② 全零值：proto3 的标量零值**不落 wire** ⇒ 必须是**空字节**。
        //    ⚠️ 这条能抓住「某一侧给零值写了 presence」。
        assertEquals(expectedHex("folder_zero"), hex(FavoriteFolder.getDefaultInstance()));
        assertEquals("", hex(FavoriteFolder.getDefaultInstance()),
                "零值消息在 wire 上必须是空字节（proto3 的隐式 presence）");
    }

    @Test
    @DisplayName("🔴 FavoriteItem 的 wire 字节与 Go 真源逐字节一致")
    void favoriteItemWireMatchesGo() {
        // ③ 全部字段都有值。
        FavoriteItem full = FavoriteItem.newBuilder()
                .setFavoriteId(9007199254740993L)
                .setFolderId(7001L)
                .setUserId(1001L)
                .setTargetId("2001")
                .setTargetType("article")
                .setTitle("标题")
                .setCover("https://example.invalid/c.png")
                .setCreatedAt(1700000000123L)
                .build();
        assertEquals(expectedHex("item_full"), hex(full));

        // ④ 只设两个字段 ⇒ **只有这两个字段落 wire**。
        //    ⚠️ 这条能抓住「某一侧把空字符串也写上去」（那是 JSON 的行为，不是 proto3 的）。
        FavoriteItem partial = FavoriteItem.newBuilder()
                .setFavoriteId(1L)
                .setTargetType("post")
                .build();
        assertEquals(expectedHex("item_partial"), hex(partial));
    }

    @Test
    @DisplayName("🔴 repeated 字段（收藏夹列表）的 wire 字节与 Go 真源逐字节一致")
    void repeatedFieldWireMatchesGo() {
        // ⑤ 两个收藏夹 ⇒ 两次 tag 1 的 length-delimited。
        //    ⚠️ 这条能抓住「某一侧把 repeated 编成 map 或 JSON 数组」。
        GetFavoriteFoldersResp resp = GetFavoriteFoldersResp.newBuilder()
                .addFolders(FavoriteFolder.newBuilder()
                        .setFolderId(7L).setUserId(1001L).setName("收藏夹")
                        .setCreatedAt(1L).setUpdatedAt(2L).build())
                .addFolders(FavoriteFolder.newBuilder()
                        .setFolderId(8L).setUserId(1002L).setName("另一个")
                        .setCreatedAt(3L).setUpdatedAt(4L).build())
                .build();
        assertEquals(expectedHex("folders_resp_two"), hex(resp));
    }

    @Test
    @DisplayName("🔴 请求消息的 wire 字节与 Go 真源逐字节一致")
    void requestMessageWireMatchesGo() {
        // ⑥ folderId 是 int64 ⇒ wire 上是 varint（**不是**字符串）。
        //    ⚠️ 这条能抓住「某一侧把 int64 编成 string」（那是 api 层的 JSON 约定，不是 proto 的）。
        CreateFavoriteReq req = CreateFavoriteReq.newBuilder()
                .setUserId(1001L)
                .setFolderId(7001L)
                .setTargetId("2001")
                .setTargetType("article")
                .setTitle("标题")
                .build();
        assertEquals(expectedHex("create_req"), hex(req));
    }

    @Test
    @DisplayName("🔴 探针与本测试的用例集必须完全一致（防止有人只改了一边）")
    void expectedFileAndThisTestCoverTheSameCases() {
        // ⚠️ 本测试逐条断言 expected.txt 里的行 ⇒ 少一条会 NPE、多一条不会报错。
        // ⇒ 这里显式锁住「用例集」：Go 探针产的与 Java 侧产的必须是同一组。
        TreeSet<String> expectedNames = new TreeSet<>(expected().keySet());
        TreeSet<String> producedNames = new TreeSet<>(List.of(
                "WIRE|folder_full",
                "WIRE|folder_zero",
                "WIRE|item_full",
                "WIRE|item_partial",
                "WIRE|folders_resp_two",
                "WIRE|create_req"));
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
        assertEquals(6, lines.size(),
                "expected.txt 应当正好 6 条用例（新增用例时同步更新本断言与本测试）");
    }
}
