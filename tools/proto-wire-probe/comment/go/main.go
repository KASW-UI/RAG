// tools/proto-wire-probe/comment 的 Go 半边。
//
// ⚠️ 本文件**不是**生产代码，它是「把 Go 真源的 protobuf 消息体字节原样算出来」的探针。
//
// 它用**从 Go 真源逐字节复制**的 `service/comment/rpc/pb/comment.pb.go`
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
//  ③ 🔴 **comment 域特有的「两侧 proto 定义本身不等」**：
//     Go 真源的 `proto/comment.proto:105` 有 `int64 owner_id = 7`，
//     但 Go 侧**真正在用的生成码** `service/comment/rpc/pb/comment.pb.go`
//     里**根本没有** `owner_id` / `OwnerId`（连 rawDesc 都没有 ⇒ 该生成码比 .proto 旧）。
//     ⇒ **本探针的用例集只能用「两侧生成码都有」的字段**（Go 侧无法设置 owner_id
//     ⇒ 无法对称比较 ⇒ 该字段**两侧都不落 wire** ⇒ 不进用例集）。
//     ⇒ 这条差异本身已登记（见本目录 README「证据边界」），
//     它恰恰是「`.proto` 一致 ⇒ 生成码一致」这条假设的**反例**。
//
// ④ comment 域的 wire 边界集中在四处（每处都单独钉一条用例）：
//     · **int64 极多**（id / user_id / root_id / like_count …）⇒ 负数 = 10 字节 varint；
//     · **4 个枚举**（CommentState / SubjectState / ReportReason / ManageType）⇒ 零值不落 wire；
//     · **5 个 bool success** ⇒ false 不落 wire ⇒ 整条消息是空字节；
//     · **repeated 消息 + 嵌套消息**（CommentItem.children / GetCommentResp.comment + .subject）。
package main

import (
	"encoding/hex"
	"fmt"
	"os"

	pb "commentprotobeprobe/pb"

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
	// ① 条目全字段：int64 是 varint；string 是 length-delimited；**枚举非零**才落 wire。
	//    ⚠️ 这条能抓住「某一侧把 int64 编成 sint32/zigzag」「把枚举编成字符串」。
	wire("item_full", &pb.CommentItem{
		Id:        9007199254740993, // 2^53+1 ⇒ JS 安全整数之外 ⇒ 多字节 varint
		UserId:    1001,
		Content:   "你好",
		LikeCount: 7,
		State:     pb.CommentState_STATE_DELETED,
		CreatedAt: "2026-01-02 03:04:05",
	})

	// ② 全零值：proto3 的标量零值**不落 wire** ⇒ 两侧都必须是**空字节**。
	//    ⚠️ 这条能抓住「某一侧给零值写了 presence」。
	wire("item_zero", &pb.CommentItem{})

	// ③ 🔴 负 int64 ⇒ wire 上是 **10 字节 varint**（符号扩展成 64 位补码）。
	//    ⚠️ 这条能抓住「某一侧把 int64 编成 sint32/zigzag」（那是另一种编码 ⇒ 字节不同）。
	wire("item_id_negative", &pb.CommentItem{
		Id: -1,
	})

	// ④ int64 上界 9223372036854775807 ⇒ **10 字节 varint**（最后一个字节是 0x7F）。
	//    ⚠️ 这条能抓住「某一侧用 int32 装它 ⇒ 截断 ⇒ 字节不同」。
	wire("item_id_max", &pb.CommentItem{
		Id: 9223372036854775807,
	})

	// ⑤ 枚举零值：STATE_NORMAL = 0 ⇒ **不落 wire** ⇒ 与「全零值」同形（空字节）。
	//    ⚠️ 这条能抓住「某一侧把枚举编成带 presence 的 optional」。
	wire("item_state_normal", &pb.CommentItem{
		State: pb.CommentState_STATE_NORMAL,
	})

	// ⑥ 枚举非零：STATE_DELETED = 2 ⇒ 落 wire（tag 10 ⇒ 0x50，值 2）。
	wire("item_state_deleted", &pb.CommentItem{
		State: pb.CommentState_STATE_DELETED,
	})

	// ⑦ 枚举 3（STATE_BLOCKED）⇒ 与 ⑥ 同形但值不同 ⇒ 钉住「枚举值本身进 wire」。
	wire("item_state_blocked", &pb.CommentItem{
		State: pb.CommentState_STATE_BLOCKED,
	})

	// ⑧ repeated 消息字段：一条子评论 ⇒ 两次「tag 13 的 length-delimited」。
	//    ⚠️ 这条能抓住「某一侧把 repeated 编成 map 或 JSON 数组」。
	wire("item_children", &pb.CommentItem{
		Id: 1,
		Children: []*pb.CommentItem{
			{Id: 2},
		},
	})

	// ⑨ 响应：两条评论 ＋ 一个嵌套的 SubjectInfo ⇒ repeated + 嵌套消息同时验。
	wire("resp_two", &pb.GetCommentResp{
		Comment: []*pb.CommentItem{
			{Id: 1, UserId: 1001, Content: "一"},
			{Id: 2, UserId: 1001, Content: "二"},
		},
		Subject: &pb.SubjectInfo{
			TargetType: "article",
			TargetId:   "2001",
			TotalCount: 2,
			RootCount:  1,
			State:      pb.SubjectState_SUBJECT_ONLY,
			Attribute:  3,
		},
	})

	// ⑩ 空响应：repeated 为空 ＋ 嵌套消息为 nil ⇒ **空字节**（tag 都不落）。
	wire("resp_empty", &pb.GetCommentResp{})

	// ⑪ 负 int32（action_type）⇒ 同样 **10 字节 varint**（int32 也符号扩展成 64 位）。
	//    ⚠️ 这条能抓住「某一侧把 int32 编成 sint32」（zigzag ⇒ 字节完全不同）。
	wire("like_req_action_negative", &pb.LikeCommentReq{
		UserId:    1001,
		CommentId: 2001,
		ActionType: -1,
	})

	// ⑫ bool = true ⇒ varint 1。
	wire("like_resp_true", &pb.LikeCommentResp{
		Success: true,
	})

	// ⑬ 🔴 bool = false ⇒ proto3 的零值**不落 wire** ⇒ **整条消息是空字节**。
	//    ⚠️ comment 域有 **5 个**这样的 bool（like / delete / manage / report / updateSubjectState）
	//    ⇒ 接收端只能靠「没这个字段」判 false；api 层给的 JSON 却会写 "success": false
	//    ⇒ **两层的表达完全不同** ⇒ 只有字节级对拍钉得住。
	wire("like_resp_false", &pb.LikeCommentResp{})

	// ⑭ 枚举 99（REASON_OTHER）⇒ **单字节 varint**（99 < 128 ⇒ 0x63）。
	//    ⚠️ 这条能抓住「某一侧把枚举编成 string」或「用了 sint32」。
	wire("report_req_reason_other", &pb.ReportCommentReq{
		UserId:    1001,
		CommentId: 2001,
		Reason:    pb.ReportReason_REASON_OTHER,
		Detail:    "详情",
	})

	// ⑮ 嵌套消息全字段（SubjectInfo 的**两侧共有**那 6 个字段；owner_id 两侧都不发）。
	wire("subject_full", &pb.SubjectInfo{
		TargetType: "article",
		TargetId:   "2001",
		TotalCount: 12,
		RootCount:  5,
		State:      pb.SubjectState_SUBJECT_CLOSED,
		Attribute:  0,
	})

	// ⑯ 中文 UTF-8：string 按**字节**长度算 length-delimited（不是字符数）。
	//    ⚠️ 这条能抓住「某一侧按 rune 数算长度」。
	wire("create_req_utf8", &pb.CreateCommentReq{
		UserId:  1001,
		Content: "中文评论",
	})
}
