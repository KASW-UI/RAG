package com.rag.article.article.rpc.proto;

import com.google.protobuf.ByteString;
import com.google.protobuf.Message;
import com.rag.article.article.proto.Article;
import com.rag.article.article.proto.ArticleStatus;
import com.rag.article.article.proto.CreateArticleRequest;
import com.rag.article.article.proto.ListArticlesResponse;
import com.rag.article.article.proto.UploadFileRequest;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 🔴🔴 article proto 的 <b>protobuf 消息体 wire 字节</b>双侧对拍。
 *
 * <h2>它补的是哪个缺口</h2>
 * <p>本域<b>已有</b>一道契约层守门（{@code ArticleProtoContractTest} ⇒ 比<b>描述符</b>），
 * 但它<b>自己写明</b>「<b>不覆盖 wire 字节级</b>」（见该类 javadoc「覆盖边界」）。
 * <p>⇒ 而 AGENTS.md §3.4 的效力边界第 ① 条正是「<b>复制忠实 ≠ 消费方式已验</b>
 * （<b>{@code optional} presence</b> / 字段号硬编码 / Jackson 差异）」⇒
 * <b>本域是四域里唯一有 proto3 {@code optional}（显式 presence）的域</b>
 * （{@code docs/DRIFT/article/proto.md} §2 列了 <b>11 个</b> optional 字段）
 * ⇒ ⇒ 「presence 语义两侧是否同形」<b>只有字节级才看得见</b>：
 * 文件里 {@code optional string brief = 2;} 与 {@code string brief = 2;} <b>只差三个词</b>，
 * 在 wire 上却是「<b>显式设的空串落不落 wire</b>」。
 *
 * <h2>🔴 期望值的来源</h2>
 * <p><b>全部期望值来自 Go 真源的实测</b>：{@code tools/proto-wire-probe/article/}
 * 用<b>从 Go 真源逐字节复制</b>的 {@code service/article/rpc/pb/article.pb.go}
 * （Go 侧<b>真正在用</b>的生成码）算出字节，落在 {@code expected.txt}。
 * ⇒ <b>Go 真源换了生成码 ⇒ 探针的 diff 失败</b> ⇒ 提醒更新本测试。
 * <br>⚠️ <b>两个运行时不同</b>：Go 侧是 {@code google.golang.org/protobuf}
 * （protoc-gen-go 的运行时），Java 侧是 {@code protobuf-java}
 * （protoc 的 Java 运行时）⇒ 这<b>不是</b>「同一份实现自证」。
 *
 * <h2>🔴 这几对用例各自钉住什么</h2>
 * <table border="1">
 *   <caption>7 组用例</caption>
 *   <tr><th>用例</th><th>钉住的事实</th></tr>
 *   <tr><td>{@code article_full}</td>
 *       <td>全字段编码：varint（int64 用 2^53+1 ⇒ <b>不丢精度</b>）/ length-delimited /
 *           枚举（<b>枚举值编号进 wire</b>）/ repeated string（<b>两条独立 tag</b>，不是 packed blob）/
 *           map（<b>单条目</b> ⇒ 两侧顺序才可复现）</td></tr>
 *   <tr><td>{@code article_zero}</td>
 *       <td>proto3 <b>隐式 presence</b> 的零值<b>不落 wire</b> ⇒ 必须是<b>空字节</b></td></tr>
 *   <tr><td>{@code create_req_optional_present}</td>
 *       <td>🔴🔴 <b>显式 presence</b>：{@code optional} 字段<b>显式设成空串也落 wire</b>
 *           （Go 侧 = <b>非 nil 的指针</b>；Java 侧 = {@code hasBrief()} 为真）</td></tr>
 *   <tr><td>{@code create_req_optional_absent}</td>
 *       <td>🔴🔴 与上一条<b>同一组字段、只是没设过</b> ⇒ 那两个字段<b>一个字节都不落</b>
 *           ⇒ 两条的字节差 = <b>presence 标记本身</b>（{@code 12 00} / {@code 22 00}）</td></tr>
 *   <tr><td>{@code create_req_full}</td>
 *       <td>请求消息全字段（含两个 optional 都设了非空值 ＋ repeated string 两条）</td></tr>
 *   <tr><td>{@code list_resp}</td>
 *       <td>repeated message（两次 tag 1 的 length-delimited）＋ 分页 varint</td></tr>
 *   <tr><td>{@code upload_req}</td>
 *       <td>{@code bytes} 字段（<b>不是 string</b>）⇒ length-delimited 的<b>原始字节</b></td></tr>
 * </table>
 *
 * <h2>为什么这个测试住在 article-rpc 而不是 article-proto</h2>
 * <p>⚠️ {@code article-proto} 模块<b>没有测试依赖</b>（它的 pom 里只有生成代码所需的
 * protobuf / grpc 依赖，<b>一行 junit 都没有</b>）⇒ 往那儿加测试要先改 pom。
 * <p>⇒ 放在<b>消费方</b> {@code article-rpc}：它本来就依赖 article-proto，
 * 且 proto 本来就是「给 rpc 用的」（与 favorite 域同款做法）。
 *
 * <p><b>证据强度</b>：L1 真源逐行（Go 1.25.11 {@code net/url} 之外的 protobuf 运行时 ＋
 * {@code service/article/rpc/pb/article.pb.go}）＋ 本域<b>真字节级</b>双侧对拍
 * （两侧<b>不同运行时</b> ⇒ 不是自证）＋ 变异验证
 * （{@code tools/proto-wire-probe/article/mutate-wire.sh} ⇒ 三条变异精确转红）。
 */
class ArticleProtoWireParityTest {

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
                Path.of("tools/proto-wire-probe/article/expected.txt"),
                Path.of("../tools/proto-wire-probe/article/expected.txt"),
                Path.of("../../../tools/proto-wire-probe/article/expected.txt"));
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
                "找不到 expected.txt（试过 " + candidates + "）—— 先跑 tools/proto-wire-probe/article/run.sh");
    }

    // ══════════════════ 产出 RAG 侧的字节 ══════════════════

    private static String hex(Message message) {
        return HexFormat.of().formatHex(message.toByteArray());
    }

    // ══════════════════ 用例 ══════════════════

    @Test
    @DisplayName("🔴 Article 的 wire 字节与 Go 真源逐字节一致")
    void articleWireMatchesGo() {
        // ① 全字段都有值。
        //    ⚠️ createTime 用 2^53+1：int64 在 wire 上是 varint，不会丢精度 ——
        //    某一侧若先转成 JSON 数字或当 double，字节就不同。
        //    ⚠️ extInfo 只有**一个**条目：map 的**多条目**在两个运行时里遍历顺序都不保证
        //    （Go 的 map 随机序、Java 的 MapField 未定义序）⇒ 多条目逐字节比对 = 假红。
        Article full = Article.newBuilder()
                .setId("9007199254740993")
                .setTitle("标题")
                .setBrief("摘要")
                .setMarkdownContent("# 正文")
                .setCoverImageUrl("https://example.invalid/c.png")
                .setManualTypeTag("评测")
                .addSecondaryTags("标签一")
                .addSecondaryTags("标签二")
                .setAuthorId("1001")
                .setCreateTime(9007199254740993L)
                .setUpdateTime(1700000000456L)
                .setStatus(ArticleStatus.PUBLISHED)
                .setViewCount(120)
                .setLikeCount(7)
                .setCommentCount(3)
                .setShareCount(2)
                .putExtInfo("game_id", "2001")
                .build();
        assertEquals(expectedHex("article_full"), hex(full));

        // ② 全零值：proto3 **隐式 presence** 的零值**不落 wire** ⇒ 必须是空字节。
        assertEquals(expectedHex("article_zero"), hex(Article.getDefaultInstance()));
        assertEquals("", hex(Article.getDefaultInstance()),
                "零值消息在 wire 上必须是空字节（proto3 的隐式 presence）");
    }

    @Test
    @DisplayName("🔴🔴 proto3 optional 的 presence 语义两侧同形（显式设的空串落 wire · 没设的不落）")
    void optionalPresenceIsObservableOnWire() {
        // 🔴 显式 presence：两个 optional 字段都被**显式设成空串** ⇒ 落 wire（长度 0）。
        CreateArticleRequest present = CreateArticleRequest.newBuilder()
                .setTitle("标题")
                .setBrief("")
                .setCoverImageUrl("")
                .build();
        assertTrue(present.hasBrief(), "显式设过 brief ⇒ presence 必须为真");
        assertTrue(present.hasCoverImageUrl(), "显式设过 coverImageUrl ⇒ presence 必须为真");
        assertEquals(expectedHex("create_req_optional_present"), hex(present));

        // 🔴 与上一条**同一组字段、只是没设过** ⇒ 那两个字段一个字节都不落。
        CreateArticleRequest absent = CreateArticleRequest.newBuilder()
                .setTitle("标题")
                .build();
        assertFalse(absent.hasBrief(), "没设过 brief ⇒ presence 必须为假");
        assertFalse(absent.hasCoverImageUrl(), "没设过 coverImageUrl ⇒ presence 必须为假");
        assertEquals(expectedHex("create_req_optional_absent"), hex(absent));

        // 🔴🔴 这条断言是本类**最有鉴别力**的一行：
        //    若某一侧把 optional 当成普通 string（隐式 presence）⇒ 「显式设空串」与「没设」
        //    会产出**同样的字节** ⇒ 下面这行会红 ⇒ presence 语义被抹平这件事**当场暴露**。
        assertNotEquals(hex(present), hex(absent),
                "显式设成空串与没设过，必须产出不同的 wire 字节（否则 presence 语义被抹平）");
    }

    @Test
    @DisplayName("🔴 CreateArticleRequest 的 wire 字节与 Go 真源逐字节一致")
    void createRequestWireMatchesGo() {
        // ⑤ 全字段（含两个 optional 都设了非空值 ＋ repeated string 两条）。
        //    ⚠️ repeated string 在 wire 上是**两条独立的 tag 6**，不是 packed blob。
        CreateArticleRequest full = CreateArticleRequest.newBuilder()
                .setTitle("标题")
                .setBrief("摘要")
                .setMarkdownContent("# 正文")
                .setCoverImageUrl("https://example.invalid/c.png")
                .setManualTypeTag("评测")
                .addSecondaryTags("标签一")
                .addSecondaryTags("标签二")
                .setAuthorId("1001")
                .build();
        assertEquals(expectedHex("create_req_full"), hex(full));
    }

    @Test
    @DisplayName("🔴 ListArticlesResponse 的 wire 字节与 Go 真源逐字节一致")
    void listResponseWireMatchesGo() {
        // ⑥ repeated message（两次 tag 1 的 length-delimited）＋ 分页 varint。
        ListArticlesResponse resp = ListArticlesResponse.newBuilder()
                .addArticles(Article.newBuilder()
                        .setId("1").setTitle("甲").setCreateTime(1L)
                        .setStatus(ArticleStatus.DRAFT).build())
                .addArticles(Article.newBuilder()
                        .setId("2").setTitle("乙").setCreateTime(2L)
                        .setStatus(ArticleStatus.PUBLISHED).build())
                .setTotal(42L)
                .setPage(2)
                .setPageSize(20)
                .build();
        assertEquals(expectedHex("list_resp"), hex(resp));
    }

    @Test
    @DisplayName("🔴 UploadFileRequest 的 bytes 字段与 Go 真源逐字节一致")
    void uploadRequestWireMatchesGo() {
        // ⑦ bytes 字段（不是 string）⇒ wire 上是 length-delimited 的**原始字节**。
        UploadFileRequest req = UploadFileRequest.newBuilder()
                .setContent(ByteString.copyFrom(new byte[]{0x01, 0x02, (byte) 0xff}))
                .setFileName("a.png")
                .build();
        assertEquals(expectedHex("upload_req"), hex(req));
    }

    @Test
    @DisplayName("🔴 探针与本测试的用例集必须完全一致（防止有人只改了一边）")
    void expectedFileAndThisTestCoverTheSameCases() {
        // ⚠️ 本测试逐条断言 expected.txt 里的行 ⇒ 少一条会 NPE、多一条不会报错。
        // ⇒ 这里显式锁住「用例集」：Go 探针产的与 Java 侧产的必须是同一组。
        TreeSet<String> expectedNames = new TreeSet<>(expected().keySet());
        TreeSet<String> producedNames = new TreeSet<>(List.of(
                "WIRE|article_full",
                "WIRE|article_zero",
                "WIRE|create_req_optional_present",
                "WIRE|create_req_optional_absent",
                "WIRE|create_req_full",
                "WIRE|list_resp",
                "WIRE|upload_req"));
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
        assertEquals(7, lines.size(),
                "expected.txt 应当正好 7 条用例（新增用例时同步更新本断言与本测试）");
    }
}
