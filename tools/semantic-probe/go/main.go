// Go 侧解析语义探针 —— 真源直接调用 go-zero v1.10.2 的 core/mapping。
//
// ⚠️ 本文件是 AGENTS.md §3.3「凡写『与 Go 一致』，必须有两侧同时跑的探针」的 Go 半边。
// 它的价值是【回归守门】：RAG 侧（java/Probe.java）的一致率是**预期基线**，
// 任何人把 DTO 的 ,string setter 改回 String 类型，run-parity.sh 立刻 exit 1。
//
// struct 必须是真源 Sea-RideTheWind_golang/service/message/api/internal/types/types.go 的忠实镜像，
// 漏字段＝漏用例（第一版就漏了 target_id / limit 两个字段，见 README「已知边界」）。
//
// ══════════════════════════════════════════════════════════════════════
// 2026-10-23 扩充：A / B / C / D / X 五组，覆盖 DRIFT-MSGAPI-005~009
// ══════════════════════════════════════════════════════════════════════
// 扩充的两条纪律（AGENTS.md §3.3）：
//
//  ① **比对解析出的值，不只比 ACCEPTED/REJECTED** —— 首轮探针自认只比判定，
//     于是「两侧都接受但落的值不同」这一类漂移根本看不见（`affected` 那一类
//     出参漂移就是这么漏掉的）。
//  ② **出参序列化面** —— 首轮完全没有，于是 23 条全绿仍漏掉 wire 契约漂移。
package main

import (
	"encoding/json"
	"fmt"
	"sort"
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

// types.go:21 AdminSendNotificationResp —— 仅用于出参序列化组（X）
type NotifResp struct {
	Affected int64 `json:"affected"`
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

// tv 与 t 同构，但**额外打印解析出的值**（纪律①）。
// extract 必须返回两侧逐字相同的字符串（整数不带千分位、bool 用 Go 的 true/false、
// map 按键排序后拼 k=v）—— 格式不一致会被误判成漂移。
func tv(label, body string, v any, extract func() string) {
	err := mapping.UnmarshalJsonReader(&sr{s: body}, v)
	if err != nil {
		fmt.Printf("%s|REJECTED  %s\n", label, strings.Split(err.Error(), "\n")[0])
		return
	}
	fmt.Printf("%s|ACCEPTED %s\n", label, extract())
}

// ts 输出序列化结果（纪律②）。Go 真源的 HTTP 响应体由 go-zero 的
// rest/httpx → internal/json 产出，v1.10.2 默认就是 encoding/json。
func ts(label string, v any) {
	b, err := json.Marshal(v)
	if err != nil {
		fmt.Printf("%s|REJECTED  %v\n", label, err)
		return
	}
	fmt.Printf("%s|ACCEPTED %s\n", label, string(b))
}

func mapStr(m map[string]string) string {
	if m == nil {
		return "null"
	}
	keys := make([]string, 0, len(m))
	for k := range m {
		keys = append(keys, k)
	}
	sort.Strings(keys)
	pairs := make([]string, 0, len(keys))
	for _, k := range keys {
		pairs = append(pairs, k+"="+m[k])
	}
	return "{" + strings.Join(pairs, ",") + "}"
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

	// ─────────────────────────────────────────────────────────────────
	// A 组（2026-10-23）：布尔字段收 JSON **数字** —— DRIFT-MSGAPI-005
	// Go 出处：core/mapping/unmarshaler.go:663-715 processFieldPrimitiveWithJSONNumber
	//        的 `switch typeKind` **没有 reflect.Bool 分支** ⇒ default ⇒
	//        newTypeMismatchErrorWithHint ⇒ 一律 REJECTED。
	// 业务面：AdminSendNotificationReq.broadcast 决定「定向」还是「**全量广播**」。
	// ─────────────────────────────────────────────────────────────────
	a1 := &PlainReq{}
	tv(`A1 unread_only 收 JSON 数字 1`, `{"unread_only":1}`, a1, func() string {
		return fmt.Sprintf("unread_only=%v", a1.UnreadOnly)
	})
	a2 := &PlainReq{}
	tv(`A2 unread_only 收 JSON 数字 0`, `{"unread_only":0}`, a2, func() string {
		return fmt.Sprintf("unread_only=%v", a2.UnreadOnly)
	})
	a3 := &PlainReq{}
	tv(`A3 unread_only 收 JSON 数字 2`, `{"unread_only":2}`, a3, func() string {
		return fmt.Sprintf("unread_only=%v", a3.UnreadOnly)
	})
	a4 := &PlainReq{}
	tv(`A4 unread_only 收 JSON 数字 1.0`, `{"unread_only":1.0}`, a4, func() string {
		return fmt.Sprintf("unread_only=%v", a4.UnreadOnly)
	})
	a5 := &PlainReq{}
	tv(`A5 unread_only 收 JSON 数字 1.5`, `{"unread_only":1.5}`, a5, func() string {
		return fmt.Sprintf("unread_only=%v", a5.UnreadOnly)
	})
	a7 := &NotifReq{}
	tv(`A7 broadcast 收 JSON 数字 1`, `{"broadcast":1,"title":"t","content":"c"}`, a7, func() string {
		return fmt.Sprintf("broadcast=%v", a7.Broadcast)
	})
	a8 := &NotifReq{}
	tv(`A8 broadcast 收 JSON 数字 0`, `{"broadcast":0,"title":"t","content":"c"}`, a8, func() string {
		return fmt.Sprintf("broadcast=%v", a8.Broadcast)
	})

	// ─────────────────────────────────────────────────────────────────
	// B 组：map[string]string 的**元素级**判定 —— DRIFT-MSGAPI-006 / -007
	// Go 出处：core/mapping/unmarshaler.go:385-417 generateMap 的 default 分支
	//   case json.Number → setValueFromString(...)   ⇒ **数字收下并字符串化**
	//   case bool       → 要求 elem kind == Bool    ⇒ 对 string map 报 errTypeMismatch
	//   default(nil/其它) → 要求 elem kind == 该值 kind ⇒ nil ⇒ Invalid ≠ String ⇒ 报错
	// ⚠️ 与顶层标量字段（processFieldPrimitiveWithJSONNumber）**判据不同**，
	//    这正是 006/007 的全部根因。
	// ─────────────────────────────────────────────────────────────────
	b1 := &NotifReq{}
	tv(`B1 extra 值收 JSON 数字 1`, `{"title":"t","content":"c","extra":{"k":1}}`, b1, func() string {
		return "extra=" + mapStr(b1.Extra)
	})
	b2 := &NotifReq{}
	tv(`B2 extra 值收 JSON 数字 1.5`, `{"title":"t","content":"c","extra":{"k":1.5}}`, b2, func() string {
		return "extra=" + mapStr(b2.Extra)
	})
	b3 := &NotifReq{}
	tv(`B3 extra 值显式 null`, `{"title":"t","content":"c","extra":{"k":null}}`, b3, func() string {
		return "extra=" + mapStr(b3.Extra)
	})
	b4 := &NotifReq{}
	tv(`B4 extra 值收 JSON bool`, `{"title":"t","content":"c","extra":{"k":true}}`, b4, func() string {
		return "extra=" + mapStr(b4.Extra)
	})
	b5 := &NotifReq{}
	tv(`B5 extra 值收 JSON 空串`, `{"title":"t","content":"c","extra":{"k":""}}`, b5, func() string {
		return "extra=" + mapStr(b5.Extra)
	})
	b6 := &NotifReq{}
	tv(`B6 extra 值收 JSON 数组`, `{"title":"t","content":"c","extra":{"k":[]}}`, b6, func() string {
		return "extra=" + mapStr(b6.Extra)
	})
	b7 := &NotifReq{}
	tv(`B7 extra 键收 JSON 数字`, `{"title":"t","content":"c","extra":{"1":"v"}}`, b7, func() string {
		return "extra=" + mapStr(b7.Extra)
	})
	b8 := &NotifReq{}
	tv(`B8 extra 整体收 JSON 字符串`, `{"title":"t","content":"c","extra":"x"}`, b8, func() string {
		return "extra=" + mapStr(b8.Extra)
	})
	// B9/B10 钉住「**保留原始字面量**」而不是数值规范化：
	// Go 走 v.String()（json.Number 的原文）⇒ 1.0 落 "1.0"、1e2 落 "1e2"，
	// 若 RAG 侧改用 getDoubleValue()/long 格式化，这两条会变红。
	b9 := &NotifReq{}
	tv(`B9 extra 值收 JSON 数字 1.0`, `{"title":"t","content":"c","extra":{"k":1.0}}`, b9, func() string {
		return "extra=" + mapStr(b9.Extra)
	})
	b10 := &NotifReq{}
	tv(`B10 extra 值收 JSON 数字 1e2`, `{"title":"t","content":"c","extra":{"k":1e2}}`, b10, func() string {
		return "extra=" + mapStr(b10.Extra)
	})

	// ─────────────────────────────────────────────────────────────────
	// C 组：,optional 标量字段的**显式 null** —— DRIFT-MSGAPI-008
	// Go 出处：core/mapping/unmarshaler.go:854-863
	//     if mapValue == nil { if opts.optional() { return nil } ; return mustn't-be-nil }
	// ⇒ optional 字段收显式 null = **接受**，值保持零值。
	// ⚠️ 与「必填字段收 null 必须仍被拒」是两条独立路径（见 RAG 的 GoStyleRequired）。
	// ─────────────────────────────────────────────────────────────────
	c1 := &PlainReq{}
	tv(`C1 offset 显式 null`, `{"offset":null}`, c1, func() string {
		return fmt.Sprintf("offset=%d", c1.Offset)
	})
	c2 := &PlainReq{}
	tv(`C2 limit 显式 null`, `{"limit":null}`, c2, func() string {
		return fmt.Sprintf("limit=%d", c2.Limit)
	})
	c3 := &PlainReq{}
	tv(`C3 unread_only 显式 null`, `{"unread_only":null}`, c3, func() string {
		return fmt.Sprintf("unread_only=%v", c3.UnreadOnly)
	})
	c4 := &NotifReq{}
	tv(`C4 kind 显式 null`, `{"kind":null,"title":"t","content":"c"}`, c4, func() string {
		return fmt.Sprintf("kind=%d", c4.Kind)
	})
	c5 := &NotifReq{}
	tv(`C5 ,string optional target_id 显式 null`, `{"target_id":null,"title":"t","content":"c"}`, c5, func() string {
		return fmt.Sprintf("target_id_present=%v", c5.TargetId != 0)
	})
	c6 := &PlainReq{}
	tv(`C6 offset 收 int32 上限`, `{"offset":2147483647}`, c6, func() string {
		return fmt.Sprintf("offset=%d", c6.Offset)
	})
	c7 := &PlainReq{}
	tv(`C7 offset 收 int32 上限+1`, `{"offset":2147483648}`, c7, func() string {
		return fmt.Sprintf("offset=%d", c7.Offset)
	})
	c8 := &PlainReq{}
	tv(`C8 offset 收负数 -1`, `{"offset":-1}`, c8, func() string {
		return fmt.Sprintf("offset=%d", c8.Offset)
	})

	// ─────────────────────────────────────────────────────────────────
	// D 组：`,string,optional` 的值解析（GoStyleStringInt64 的边界形态）
	// 首轮 README 自认这 6 个形态一条都没覆盖，本轮补齐。
	// ─────────────────────────────────────────────────────────────────
	d1 := &StrOnlyReq{}
	tv(`D1 ,string 收 "+123"`, `{"conversation_id":"+123"}`, d1, func() string {
		return fmt.Sprintf("conversation_id=%d", d1.ConversationId)
	})
	d2 := &StrOnlyReq{}
	tv(`D2 ,string 收 "0012"`, `{"conversation_id":"0012"}`, d2, func() string {
		return fmt.Sprintf("conversation_id=%d", d2.ConversationId)
	})
	d3 := &StrOnlyReq{}
	tv(`D3 ,string 收 " 12"`, `{"conversation_id":" 12"}`, d3, func() string {
		return fmt.Sprintf("conversation_id=%d", d3.ConversationId)
	})
	d4 := &StrOnlyReq{}
	tv(`D4 ,string 收空串 ""`, `{"conversation_id":""}`, d4, func() string {
		return fmt.Sprintf("conversation_id=%d", d4.ConversationId)
	})
	d5 := &StrOnlyReq{}
	tv(`D5 ,string 收 "0x10"`, `{"conversation_id":"0x10"}`, d5, func() string {
		return fmt.Sprintf("conversation_id=%d", d5.ConversationId)
	})
	d6 := &StrOnlyReq{}
	tv(`D6 ,string 收 1e2`, `{"conversation_id":1e2}`, d6, func() string {
		return fmt.Sprintf("conversation_id=%d", d6.ConversationId)
	})

	// ─────────────────────────────────────────────────────────────────
	// X 组：**出参**序列化 —— 首轮完全没有这一面
	// ⚠️ X1/X2 是**已知签字偏离** SD-2026-10-23-03（见 run-parity.sh 的 known-dev 清单），
	//    预期为红，不计入 bad。保留在探针里是为了让「这条差异确实存在」可见，
	//    而不是靠人记。
	// ─────────────────────────────────────────────────────────────────
	ts(`X1 出参 affected=3`, &NotifResp{Affected: 3})
	ts(`X2 出参 affected=0`, &NotifResp{Affected: 0})
}