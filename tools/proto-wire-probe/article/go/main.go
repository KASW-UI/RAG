// tools/proto-wire-probe/article 的 Go 半边。
//
// ⚠️ 本文件**不是**生产代码，它是「把 Go 真源的 protobuf 消息体字节原样算出来」的探针。
//
// 它用**从 Go 真源逐字节复制**的 `service/article/rpc/pb/article.pb.go`
// （即 Go 侧**真正在用**的生成码，不是重新生成的）序列化一组消息，输出十六进制字节。
// 复制件由 run.sh 做 sha256 比对 ⇒ **真源换了生成码探针立刻响**。
//
// 输出格式（两侧必须逐字节相同）：
//
//	WIRE|<name>|<hex>
//
// ⚠️ 为什么这件事值得单独做：
//  ① AGENTS.md §3.4 只保证「`.proto` 文件本身两侧一致」；
//     但**两侧的生成码**是否产出**同样的 wire 字节**，是另一件事 —— 字段号、
//     presence 语义、repeated / map 的编码方式，只有序列化出来才看得见。
//  ② Go 侧用 `google.golang.org/protobuf`（protoc-gen-go 的运行时），
//     Java 侧用 `protobuf-java`（protoc 的 Java 运行时）—— **两个不同的实现**。
//  ③ 🔴 **本域是四域里唯一有 proto3 `optional`（显式 presence）的域**
//     （`docs/DRIFT/article/proto.md` §2 列了 11 个 optional 字段）
//     ⇒ 「`optional` presence」这条边界在**字节层**才看得见 ——
//     文件里 `optional string brief = 2;` 与 `string brief = 2;` 只差三个词，
//     在 wire 上却是「显式设的空串**落不落 wire**」。
package main

import (
	"encoding/hex"
	"fmt"
	"os"

	pb "articleprotobeprobe/pb"

	"google.golang.org/protobuf/proto"
)

// wire 序列化一条消息并打印它的十六进制字节。
func wire(name string, m proto.Message) {
	b, err := proto.Marshal(m)
	if err != nil {
		fmt.Fprintf(os.Stderr, "!! %s 序列化失败：%v\n", name, err)
		os.Exit(1)
	}
	fmt.Printf("WIRE|%s|%s\n", name, hex.EncodeToString(b))
}

func main() {
	// ① 全字段都有值的文章。
	//    ⚠️ `create_time` 用 2^53+1：它是 **int64**，wire 上是 varint，**不会**丢精度 ——
	//    但如果某一侧把它当 double 或先转成 JSON 数字，字节就会不同。
	//    ⚠️ `ext_info` 只有**一个**条目 —— map 的**多条目**在两个运行时里
	//    **遍历顺序都不保证**（Go 的 map 随机序、Java 的 MapField 未定义序）
	//    ⇒ 多条目做逐字节比对 = 制造假红 ⇒ 只用单条目。
	wire("article_full", &pb.Article{
		Id:              "9007199254740993",
		Title:           "标题",
		Brief:           "摘要",
		MarkdownContent: "# 正文",
		CoverImageUrl:   "https://example.invalid/c.png",
		ManualTypeTag:   "评测",
		SecondaryTags:   []string{"标签一", "标签二"},
		AuthorId:        "1001",
		CreateTime:      9007199254740993,
		UpdateTime:      1700000000456,
		Status:          pb.ArticleStatus_PUBLISHED,
		ViewCount:       120,
		LikeCount:       7,
		CommentCount:    3,
		ShareCount:      2,
		ExtInfo:         map[string]string{"game_id": "2001"},
	})

	// ② 全零值：proto3 的**隐式 presence** 标量零值**不落 wire** ⇒ 两侧都必须是**空字节**。
	//    ⚠️ 这条与 ③ 是一对：③（显式 presence）**要落**，②（隐式 presence）**不落**。
	wire("article_zero", &pb.Article{})

	// ③ 🔴🔴 显式 presence 被**设成空串**：proto3 `optional` 字段**显式设了就落 wire**
	//    （哪怕值是空串）⇒ Go 侧用**非 nil 的指针**表达「设过」。
	//    ⇒ 期望字节里**有** `brief`（tag 2）与 `cover_image_url`（tag 4）的「长度 0」标记。
	empty := ""
	wire("create_req_optional_present", &pb.CreateArticleRequest{
		Title:         "标题",
		Brief:         &empty,
		CoverImageUrl: &empty,
	})

	// ④ 🔴🔴 与 ③ 同一组字段、但**没设过** optional ⇒ 那两个字段**一个字节都不落**。
	//    ⇒ ③ 与 ④ 的字节差 = presence 标记本身（`12 00` 与 `22 00`）。
	//    ⇒ 这一对是本探针**最有鉴别力**的两条：它能抓住「某一侧把 optional 当普通 string」
	//    （普通 string 的空串**不落** ⇒ 两侧字节就会**完全相同** ⇒ presence 语义被抹平）。
	wire("create_req_optional_absent", &pb.CreateArticleRequest{
		Title: "标题",
	})

	// ⑤ 请求消息全字段（含 repeated string 两条 + 两个 optional 都设了非空值）。
	wire("create_req_full", &pb.CreateArticleRequest{
		Title:           "标题",
		Brief:           ptr("摘要"),
		MarkdownContent: "# 正文",
		CoverImageUrl:   ptr("https://example.invalid/c.png"),
		ManualTypeTag:   "评测",
		SecondaryTags:   []string{"标签一", "标签二"},
		AuthorId:        "1001",
	})

	// ⑥ repeated message + varint 分页字段。
	wire("list_resp", &pb.ListArticlesResponse{
		Articles: []*pb.Article{
			{Id: "1", Title: "甲", CreateTime: 1, Status: pb.ArticleStatus_DRAFT},
			{Id: "2", Title: "乙", CreateTime: 2, Status: pb.ArticleStatus_PUBLISHED},
		},
		Total:    42,
		Page:     2,
		PageSize: 20,
	})

	// ⑦ `bytes` 字段（不是 string）⇒ wire 上是 length-delimited 的**原始字节**。
	wire("upload_req", &pb.UploadFileRequest{
		Content:  []byte{0x01, 0x02, 0xff},
		FileName: "a.png",
	})
}

// ptr 取字符串地址 —— Go 侧 proto3 optional 用**指针**表达「设过这个字段」。
func ptr(s string) *string { return &s }
