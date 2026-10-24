// tools/proto-wire-probe/favorite 的 Go 半边。
//
// ⚠️ 本文件**不是**生产代码，它是「把 Go 真源的 protobuf 消息体字节原样算出来」的探针。
//
// 它用**从 Go 真源逐字节复制**的 `service/favorite/rpc/pb/favorite.pb.go`
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
//     零值落不落 wire、repeated 的编码方式，只有序列化出来才看得见。
//  ② Go 侧用 `google.golang.org/protobuf`（protoc-gen-go 的运行时），
//     Java 侧用 `protobuf-java`（protoc 的 Java 运行时）—— **两个不同的实现**。
//  ③ 零值不落 wire 这条，docs/DRIFT/favorite/proto.md §2 此前只有**生成码级**证据
//     （读 FavoriteFolder.writeTo 的条件表达式）⇒ 本探针把它升级为**字节级**。
package main

import (
	"encoding/hex"
	"fmt"
	"os"

	pb "favoriteprotobeprobe/pb"

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
	// ① 全部字段都有值的收藏夹。
	//    ⚠️ 用 2^53+1 的雪花 ID：int64 在 wire 上是 varint，**不会**丢精度 ——
	//    但如果某一侧把它当 double 或先转成 JSON 数字，字节就会不同。
	wire("folder_full", &pb.FavoriteFolder{
		FolderId:  9007199254740993,
		UserId:    1001,
		Name:      "技术",
		CreatedAt: 1700000000123,
		UpdatedAt: 1700000000456,
	})

	// ② 全零值：proto3 的标量零值**不落 wire** ⇒ 两侧都必须是**空字节**。
	//    ⚠️ 这条能抓住「某一侧给零值写了 presence」（Go 的 `,string`/optional 语义渗透到 proto）。
	wire("folder_zero", &pb.FavoriteFolder{})

	// ③ 全部字段都有值的收藏条目。
	wire("item_full", &pb.FavoriteItem{
		FavoriteId: 9007199254740993,
		FolderId:   7001,
		UserId:     1001,
		TargetId:   "2001",
		TargetType: "article",
		Title:      "标题",
		Cover:      "https://example.invalid/c.png",
		CreatedAt:  1700000000123,
	})

	// ④ 只设了两个字段 ⇒ **只有这两个字段落 wire**（其余不落）。
	//    ⚠️ 这条能抓住「某一侧把空字符串也写上去」（那是 JSON 的行为，不是 proto3 的）。
	wire("item_partial", &pb.FavoriteItem{
		FavoriteId: 1,
		TargetType: "post",
	})

	// ⑤ repeated 字段：两个收藏夹 ⇒ 两次 tag 1 的 length-delimited。
	//    ⚠️ 这条能抓住「某一侧把 repeated 编成 map 或 JSON 数组」。
	wire("folders_resp_two", &pb.GetFavoriteFoldersResp{
		Folders: []*pb.FavoriteFolder{
			{FolderId: 7, UserId: 1001, Name: "收藏夹", CreatedAt: 1, UpdatedAt: 2},
			{FolderId: 8, UserId: 1002, Name: "另一个", CreatedAt: 3, UpdatedAt: 4},
		},
	})

	// ⑥ 请求消息（folderId 是 int64 ⇒ wire 上是 varint，不是字符串）。
	wire("create_req", &pb.CreateFavoriteReq{
		UserId:     1001,
		FolderId:   7001,
		TargetId:   "2001",
		TargetType: "article",
		Title:      "标题",
	})
}
