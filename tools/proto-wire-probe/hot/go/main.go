// tools/proto-wire-probe/hot 的 Go 半边。
//
// ⚠️ 本文件**不是**生产代码，它是「把 Go 真源的 protobuf 消息体字节原样算出来」的探针。
//
// 它用**从 Go 真源逐字节复制**的 `service/hot/rpc/pb/hot.pb.go`
// （即 Go 侧**真正在用**的生成码，不是重新生成的）序列化一组消息，输出十六进制字节。
// 复制件由 run.sh 做 sha256 比对 ⇒ **真源换了生成码探针立刻响**。
//
// 输出格式（两侧必须逐字节相同）：
//
//	WIRE|<name>|<hex>
//
// ⚠️ 为什么这件事值得单独做（AGENTS.md §3.4 效力边界第 1 条）：
//  ① AGENTS.md §3.4 只保证「`.proto` 文件本身两侧一致」；
//     但**两侧的生成码**是否产出**同样的 wire 字节**，是另一件事 —— 字段号、
//     零值落不落 wire、repeated 的编码方式，只有序列化出来才看得见。
//  ② Go 侧用 `google.golang.org/protobuf`（protoc-gen-go 的运行时），
//     Java 侧用 `protobuf-java`（protoc 的 Java 运行时）—— **两个不同的实现**。
//  ③ hot 域特有：**`bool` 零值在 wire 上完全不落**（`success=false` ⇒ 整条消息是空字节）
//     —— api 层给的 JSON 是 `{"success": false}` ⇒ **两层的表达完全不同**，
//     只有字节级对拍才钉得住这件事。
package main

import (
	"encoding/hex"
	"fmt"
	"os"

	pb "hotprotobeprobe/pb"

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
	// ① 请求消息：int32 top_k 是 varint。
	wire("req_full", &pb.GetHotArticlesRequest{
		TopK: 20,
	})

	// ② 全零值：proto3 的标量零值**不落 wire** ⇒ 两侧都必须是**空字节**。
	//    ⚠️ 这条能抓住「某一侧给零值写了 presence」。
	wire("req_zero", &pb.GetHotArticlesRequest{})

	// ③ 负数 int32：wire 上是 **10 字节 varint**（补码形式）。
	//    ⚠️ 这条能抓住「某一侧把 int32 编成 zigzag / sint32」（那是另一种编码 ⇒ 字节不同）。
	//       hot.proto 里 `top_k` 是 `int32`（**不是** sint32）⇒ 字节必须是补码 10 字节。
	wire("req_negative", &pb.GetHotArticlesRequest{
		TopK: -1,
	})

	// ④ 条目全字段：string 是 length-delimited；**uint32 最大值**（0xFFFFFFFF）是 **5 字节 varint**。
	//    ⚠️ 这条能抓住「某一侧把 uint32 当 int32 编」（uint32 > 2^31 时字节不同）：
	//       int32 装不下 4294967295 ⇒ 会被截成 -1 ⇒ 变成 10 字节 varint ⇒ 字节立刻不同。
	//    ⚠️ 对照上面 `req_negative`：**负 int32** 是 **10 字节** varint（符号扩展成 64 位补码），
	//       而 **uint32 最大值** 是 5 字节 ⇒ 两个边界各钉一条。
	wire("item_full", &pb.HotArticleItem{
		ArticleId: "2001",
		HotScore:  4294967295,
	})

	// ⑤ 只设 hot_score ⇒ **只有字段 2 落 wire**（空字符串不落 —— 那是 JSON 的行为，不是 proto3 的）。
	wire("item_partial", &pb.HotArticleItem{
		HotScore: 1,
	})

	// ⑥ repeated 字段：两个条目 ⇒ 两次 tag 1 的 length-delimited。
	//    ⚠️ 这条能抓住「某一侧把 repeated 编成 map 或 JSON 数组」。
	wire("resp_two", &pb.GetHotArticlesResponse{
		Items: []*pb.HotArticleItem{
			{ArticleId: "2001", HotScore: 1},
			{ArticleId: "2002", HotScore: 2},
		},
	})

	// ⑦ 空响应：repeated 为空 ⇒ **空字节**（tag 都不落）。
	wire("resp_empty", &pb.GetHotArticlesResponse{})

	// ⑧ 点赞请求：两个 string 字段。
	wire("like_req", &pb.LikeArticleRequest{
		ArticleId: "2001",
		UserId:    "1001",
	})

	// ⑨ bool = true ⇒ varint 1。
	wire("like_resp_true", &pb.LikeArticleResponse{
		Success: true,
	})

	// ⑩ 🔴 bool = false ⇒ proto3 的零值**不落 wire** ⇒ **整条消息是空字节**。
	//    ⚠️ 这条是 hot 域最容易漂的一处：api 层给的 JSON 是 `{"success": false}`，
	//    而 wire 上**什么都没有** ⇒ 接收端必须靠「没这个字段」判 false。
	//    若某一侧给 false 写了 presence（proto2 语义 / `optional`）⇒ 字节不同 ⇒ 这里立刻响。
	wire("like_resp_false", &pb.LikeArticleResponse{
		Success: false,
	})

	// ⑪ 评论请求：与点赞请求同形（两个 string）。
	wire("comment_req", &pb.CommentArticleRequest{
		ArticleId: "2001",
		UserId:    "1001",
	})

	// ⑫ 投币请求：string + string + int32（coin_count 是 varint，不是字符串）。
	wire("coin_req", &pb.CoinArticleRequest{
		ArticleId: "2001",
		UserId:    "1001",
		CoinCount: 2,
	})

	// ⑬ 分享请求：三个 string（channel 是自由文本，不做枚举）。
	wire("share_req", &pb.ShareArticleRequest{
		ArticleId: "2001",
		UserId:    "1001",
		Channel:   "wechat",
	})
}
