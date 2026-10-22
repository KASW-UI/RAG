// Go 侧解析语义探针 —— 真源直接调用 go-zero v1.10.2 的 core/mapping。
//
// ⚠️ 本文件是 AGENTS.md §3.3「凡写『与 Go 一致』，必须有两侧同时跑的探针」的 Go 半边。
// 它的价值是【回归守门】：RAG 侧（java/Probe.java）现在 23/23 一致是**预期基线**，
// 任何人把 DTO 的 ,string setter 改回 String 类型，run-parity.sh 立刻 exit 1。
//
// struct 必须是真源 Sea-RideTheWind_golang/service/message/api/internal/types/types.go 的忠实镜像，
// 漏字段＝漏用例（第一版就漏了 target_id / limit 两个字段，见 README「已知边界」）。
package main

import (
	"fmt"
	"strings"

	"github.com/zeromicro/go-zero/core/mapping"
)

// 与 Go service/message/api/internal/types/types.go 逐字段对齐
// 仅含 ,string 字段，无必填噪音（对应 ConversationMessagesReq / MarkNotificationReq 等）
type StrOnlyReq struct {
	ConversationId int64 `json:"conversation_id,string"`
}

// ConversationMessagesReq + UserSendChatReq 合并，用于「必填 / String 字段」用例
type StrReq struct {
	ConversationId int64  `json:"conversation_id,string"` // ConversationMessagesReq / MarkConversationReadReq
	Content        string `json:"content"`                // UserSendChatReq
}

// types.go:101 PageReq
type PlainReq struct {
	Offset     int32 `json:"offset,optional"`
	Limit      int32 `json:"limit,optional"`
	UnreadOnly bool  `json:"unread_only,optional"`
}

// types.go:12 AdminSendNotificationReq
type NotifReq struct {
	Broadcast bool              `json:"broadcast,optional"`
	TargetId  int64             `json:"target_id,string,optional"`
	Kind      int32             `json:"kind,optional"`
	Title     string            `json:"title"`
	Content   string            `json:"content"`
	Extra     map[string]string `json:"extra,optional"`
}

// mapping.UnmarshalJsonReader 的入口是 io.Reader（不是 string），需要一个最小字符串 reader。
type sr struct {
	s string
	i int
}

func (r *sr) Read(p []byte) (int, error) {
	if r.i >= len(r.s) {
		return 0, fmt.Errorf("EOF")
	}
	n := copy(p, r.s[r.i:])
	r.i += n
	return n, nil
}

func t(label, body string, v any) {
	err := mapping.UnmarshalJsonReader(&sr{s: body}, v)
	status := "ACCEPTED"
	if err != nil {
		status = "REJECTED  " + strings.Split(err.Error(), "\n")[0]
	}
	fmt.Printf("%s|%s\n", label, status)
}

func main() {
	// ⚠️ 这一行不含 "|" —— run-parity.sh 靠"过滤掉不含 | 的行"来排除 banner。
	//    改动它会导致两侧计数对不上。
	fmt.Println("Go v1.10.2 core/mapping 解析探针（真源）")
	base := `{"conversation_id":"1"`

	t(`,string int64 收 JSON 字符串 "123"`, `{"conversation_id":"123"}`, &StrOnlyReq{})
	t(`,string int64 收 JSON 数字 123`, `{"conversation_id":123}`, &StrOnlyReq{})
	t(`,string int64 收 JSON 数字 1.5`, `{"conversation_id":1.5}`, &StrOnlyReq{})
	t(`,string int64 收 JSON 数字 1.0`, `{"conversation_id":1.0}`, &StrOnlyReq{})
	t(`,string int64 收 JSON bool`, `{"conversation_id":true}`, &StrOnlyReq{})
	t(`,string int64 收超范围大整数`, `{"conversation_id":12345678901234567890123}`, &StrOnlyReq{})

	t(`无 ,string int32 收 JSON 字符串 "10"`, `{"offset":"10"}`, &PlainReq{})
	t(`无 ,string bool 收 JSON 字符串 "true"`, `{"unread_only":"true"}`, &PlainReq{})
	t(`无 ,string int32 收 JSON 数字 10`, `{"offset":10}`, &PlainReq{})
	t(`无 ,string int32 收 JSON 数字 1.5`, `{"offset":1.5}`, &PlainReq{})
	t(`无 ,string int32 limit 收 JSON 字符串 "20"`, `{"limit":"20"}`, &PlainReq{})
	t(`无 ,string int32 limit 收 JSON 数字 20`, `{"limit":20}`, &PlainReq{})

	t(`String 字段 content 收 JSON 数字`, base+`,"content":123}`, &StrReq{})
	t(`未知字段被忽略`, base+`,"content":"x","nope":1}`, &StrReq{})
	t(`required 缺席(conversation_id)`, `{"content":"x"}`, &StrReq{})
	t(`required 显式 null(conversation_id)`, `{"conversation_id":null,"content":"x"}`, &StrReq{})
	t(`content 缺席(必填)`, `{"conversation_id":"1"}`, &StrReq{})

	t(`extra 显式 null`, `{"title":"t","content":"c","extra":null}`, &NotifReq{})
	t(`extra 缺席`, `{"title":"t","content":"c"}`, &NotifReq{})
	t(`admin bool 收 JSON 字符串 "true"`, `{"broadcast":"true","title":"t","content":"c"}`, &NotifReq{})
	t(`admin int32 kind 收 JSON 字符串 "1"`, `{"kind":"1","title":"t","content":"c"}`, &NotifReq{})
	t(`,string optional target_id 收 JSON 字符串 "9"`, `{"target_id":"9","title":"t","content":"c"}`, &NotifReq{})
	t(`,string optional target_id 收 JSON 数字 9`, `{"target_id":9,"title":"t","content":"c"}`, &NotifReq{})
}