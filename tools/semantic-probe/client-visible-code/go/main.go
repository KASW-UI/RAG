// ============================================================================
// 【Go 半边活体探针】客户端可见码的「**集合**」—— 请求 deadline **已过期**时，Go 侧客户端到底能看到哪些码
//
// 定位
// ----
// 这条探针回答的是一个**决定 logout 修法**的问题：
//
//     RAG 的 admin-rpc `logout` 要不要删掉域内的 `checkDeadline()` 预检？
//
//     判据不是「Go 的 handler 级码是什么」（那是 5001 = codes.Internal），
//     而是「**客户端能看到什么**」—— 因为只有客户端可见面才是漂移判定对象。
//
// 真源侧的关键事实（逐行取证，文件:行号）：
//
//   · service/common/observability/rpc.go:38
//       timeoutCtx, cancel := context.WithTimeout(ctx, timeout)
//     ⇒ timeoutCtx 的 deadline = min(父 ctx 的 deadline, now+timeout)
//     ⇒ **父 ctx 已过期时 ⇒ timeoutCtx 一创建就是「已到期」的** ⇒ Done() 立即闭。
//
//   · service/common/observability/rpc.go:47-57
//       go func() { lock.Lock(); defer lock.Unlock(); resp, err = handler(timeoutCtx, req); close(done) }()
//     ⇒ handler 拿到的是 timeoutCtx ⇒ **出向 I/O 立刻失败**（go-redis 的
//       internal/pool/pool.go:1098-1104 的 waitTurn fast path 无条件先查 ctx.Done()）
//     ⇒ ⇒ **handler 会「立刻失败」**（这正是 fast_handler 这个 case 的建模依据）。
//
//   · service/common/observability/rpc.go:59-72  ← **本探针的核心**
//       select {
//       case <-done:              // :63
//           lock.Lock()           // :64   ← done 分支也要拿锁
//           ...
//           return resp, err      // :67   ← **返回 handler 的 err**（= codes.Internal）
//       case <-timeoutCtx.Done(): // :68
//           err := status.Error(codes.DeadlineExceeded, ...)  // :69
//           return nil, err       // :71   ← 1015
//       }
//     ⇒ ⇒ **两个分支可能同时就绪**（父 ctx 已过期 ⇒ timeoutCtx.Done() 立即闭；
//        handler 立刻失败 ⇒ done 也很快闭）⇒ ⇒ **Go 的 select 在多个就绪分支间是随机选的**
//     ⇒ ⇒ 走 done 分支时，客户端拿到的是 **handler 的 codes.Internal（= 业务码 5001）**。
//
//   · service/user/admin/rpc/internal/logic/logoutlogic.go:38-41
//       BizRedis.SetexCtx(l.ctx, blackListKey, "banned", int(expireTime))
//       ... status.Error(codes.Internal, "注销失败,Redis写入异常")
//     ⇒ ⇒ **Go 的 handler 级码 = codes.Internal（业务码 5001）**（见 service/user/common/errmsg/errmsg.go:7）。
//
// ⇒ ⇒ ⇒ **本探针要产出的事实**：Go 侧在「deadline 已过期」场景下的**客户端可见码集合**。
//     · 集合 = {4 (DeadlineExceeded), 13 (Internal)} ⇒ ⇒ **handler 级码是可观察的（窄窗口）**
//       ⇒ ⇒ 「保留域侧守卫 ⇒ RAG 的集合 = {4} = Go 集合的真子集」⇒ ⇒ 删掉守卫才能同集合。
//     · 集合 = {4} ⇒ ⇒ 「handler 级码观察不到」⇒ ⇒ 保留守卫即可。
//
// ⚠️ 为什么这个数以前不可复算
//    这个数曾在一次临时 module 里跑出来（N=20000 ⇒ 1015=19995 / 5001=5），
//    但**探针源码没有落仓** ⇒ ⇒ 「跑某条命令会得到 N」变成了不可复算的口径。
//    ⇒ ⇒ 本文件就是把那一次**落进仓**（AGENTS.md §3.3 的「运行证据」要求）。
//
// ⚠️ 为什么用独立 module + replace
//    冻结仓是**只读**的，不能往里写探针文件 ⇒ 用 `replace sea-try-go => <冻结仓>`
//    直接**引用**它（不复制、不改写）⇒ 跑的就是冻结仓那份源码。
//
// ⚠️ 离线构建
//    · `CGO_ENABLED=0`：本机没有 Xcode 头文件，cgo 会报 'stdlib.h' file not found
//    · `GOPROXY=off`：全部依赖必须已在 module cache 里
//
// 输出格式：`PROBE|<case>|<key>=<value>|...`
//   ⚠️ **只输出稳定值**（码 → 计数 / 码集合）⇒ 不输出 trace-id、端口、耗时
// ============================================================================

package main

import (
	"context"
	"fmt"
	"os"
	"sort"
	"strconv"
	"time"

	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"

	"sea-try-go/service/common/observability"
)

// iterations：跑多少轮。
//
// ⚠️ **为什么这么大**：窄窗口的命中概率是**调度竞争**决定的（handler goroutine
// 能不能赶在父 goroutine 走到 select 之前完成）⇒ ⇒ 单次概率在千分之几量级
// ⇒ ⇒ 轮数不够就会「一次都没命中」⇒ ⇒ 门禁会**假红**。
// ⇒ ⇒ 这里取 200000（期望命中数十次）⇒ ⇒ 「一次都没命中」在这种量级上才是**真信号**。
const iterations = 200000

// rpcTimeout：与服务端配置口径一致（真源 go-zero zrpc/config.go:46 的 default=2000ms）。
const rpcTimeout = 2 * time.Second

// methodName：只是给观测打标签用的（markRPCResult 会拿它打 metric）。
const methodName = "/admin.rpc.AdminService/Logout"

// internalMsg：Go 侧 logoutlogic.go:39-41 的文案（逐字照抄）。
const internalMsg = "注销失败,Redis写入异常"

func main() {
	interceptor := observability.NewUnaryServerInterceptor(rpcTimeout, 0)

	// ── case 1 · fast_handler ─────────────────────────────────────────────
	// 建模：请求 deadline **已过期** ⇒ 真源的 Redis 写**立刻失败**（pool.go:1098-1104）
	//       ⇒ logoutlogic.go:39-41 立刻返回 codes.Internal ⇒ **handler 极快返回**。
	// 这是**唯一有竞争**的 case ⇒ 它决定「客户端可见码集合」是什么。
	fastCounts := run(interceptor, func(ctx context.Context, _ any) (any, error) {
		return nil, status.Error(codes.Internal, internalMsg)
	})

	// ── case 2 · slow_handler ─────────────────────────────────────────────
	// 建模：写要花时间（handler 跑满 50ms）⇒ ⇒ 父 goroutine 先看到 timeoutCtx.Done()
	//       ⇒ ⇒ **确定性地**走超时分支 ⇒ 1015。
	// 它是**对照组**：证明「1015 是主体形态」不是因为探针写错了。
	slowCounts := run(interceptor, func(ctx context.Context, _ any) (any, error) {
		time.Sleep(50 * time.Millisecond)
		return nil, status.Error(codes.Internal, internalMsg)
	})

	printCounts("fast_handler", fastCounts)
	printCounts("slow_handler", slowCounts)
	fmt.Printf("PROBE|iterations|%d\n", iterations)
}

// run 跑一轮统计：每一轮都用**已过期**的 ctx 调真源拦截器，统计它返回的码。
func run(interceptor grpc.UnaryServerInterceptor, handler grpc.UnaryHandler) map[codes.Code]int {
	counts := map[codes.Code]int{}
	info := &grpc.UnaryServerInfo{FullMethod: methodName}
	for i := 0; i < iterations; i++ {
		// ⚠️ 父 ctx **已经过期** ⇒ timeoutCtx 一创建就是到期的（rpc.go:38）
		ctx, cancel := context.WithDeadline(context.Background(), time.Now().Add(-time.Second))
		_, err := interceptor(ctx, nil, info, handler)
		cancel()
		if err == nil {
			counts[codes.OK]++
			continue
		}
		st, _ := status.FromError(err)
		counts[st.Code()]++
	}
	return counts
}

// printCounts 输出「逐码计数」+「码集合」两行。
func printCounts(caseName string, counts map[codes.Code]int) {
	codesSlice := make([]int, 0, len(counts))
	for c := range counts {
		codesSlice = append(codesSlice, int(c))
	}
	sort.Ints(codesSlice)

	parts := make([]string, 0, len(codesSlice))
	for _, ci := range codesSlice {
		parts = append(parts, fmt.Sprintf("code_%d=%d", ci, counts[codes.Code(ci)]))
	}
	fmt.Printf("PROBE|%s|%s\n", caseName, join(parts, "|"))

	set := make([]string, 0, len(codesSlice))
	for _, ci := range codesSlice {
		set = append(set, strconv.Itoa(ci))
	}
	fmt.Printf("PROBE|%s|code_set=%s\n", caseName, join(set, ","))
}

func join(items []string, sep string) string {
	out := ""
	for i, s := range items {
		if i > 0 {
			out += sep
		}
		out += s
	}
	return out
}

var _ = os.Stdout
