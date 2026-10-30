// Redis 期限「写面」Go 半边活体探针（E66 的 Go 侧那半 · 2026-10-31 · 合并收口执行者）。
//
// 测什么
// ----
// 「请求 deadline **已耗尽** ⇒ 命令**根本没下发**到 Redis」——
//
//	观测量 = **读回来还是基线值**（**不是**靠异常类型推断）
//	⇒ ⇒ 与 RAG 侧那道活体守门（GoStyleRedisDeadlineLiveRedisWriteFaceGateTest）
//	    **用同一个观测量** ⇒ ⇒ 两侧的 PROBE 行才可比。
//
// Go 出处（真源 · 冻结仓 · 只读）
// ---------------------------
//
//	service/comment/rpc/internal/cache/comment_subject_cache.go:76
//	    func (c *CommentCache) GetSubjectCache(ctx context.Context, subjectID string) (...)
//	service/comment/rpc/internal/cache/comment_subject_cache.go:84
//	    val, err := c.rdb.Get(ctx, SubjectKey(subjectID)).Result()
//	service/comment/rpc/internal/cache/comment_subject_cache.go:103
//	    SetSubjectCache(ctx, …) ⇒ c.rdb.Setex(ctx, …)
//
//	⇒ ⇒ **每次缓存读写都吃 ctx**（不是「有些吃、有些不吃」）
//	⇒ ⇒ `c.rdb` 是 `*redis.Client`（`github.com/redis/go-redis/v9` · 冻结仓 go.mod:15）
//	⇒ ⇒ **命令下发前**由 go-redis 的 `internal/pool` 先查 `ctx.Done()`
//	    （`waitTurn(ctx)` ⇒ ctx 已到期 ⇒ 直接 `return ctx.Err()` ⇒ **命令不下发**）
//	⇒ ⇒ ⇒ **「预算耗尽 ⇒ 不写」就是 Go 的形态**。
//
// ⚠️ 为什么本探针**不 import 真源的 internal 包**
// --------------------------------------------
//
//	`service/comment/rpc/internal/...` 是 **internal** ⇒ 外部模块 import 不到
//	⇒ ⇒ 本探针用**同一套依赖**（go-redis/v9，版本从冻结仓 go.mod **钉死**）
//	⇒ ⇒ 复刻的调用形态就是真源那一行（`rdb.Get(ctx,…)` / `rdb.Set(ctx,…)`）
//	⇒ ⇒ **一行形态复刻，不是「推测行为」**。
//
// 输出
// ----
//
//	PROBE|redis_deadline|form=exhausted|write_applied=<bool>|err_is_deadline=<bool>|value_after=<str>
//	PROBE|redis_deadline|form=alive|write_applied=<bool>|err_is_deadline=<bool>|value_after=<str>
//
//	form=exhausted ⇒ 预算耗尽的那一格（**必须** write_applied=false）
//	form=alive     ⇒ **负控**（预算充足 ⇒ 必须 write_applied=true ⇒ 证明没误伤正常路径）
//
// 退出码：0 = 两种形态都产出 PROBE 行；1 = 形态与预期不符；2 = 环境不满足（连不上 Redis）
package main

import (
	"context"
	"errors"
	"fmt"
	"os"
	"time"

	"github.com/redis/go-redis/v9"
)

const probeKey = "rag-e66-redis-deadline-probe"

func main() {
	addr := os.Getenv("REDIS_ADDR")
	if addr == "" {
		addr = "127.0.0.1:6379"
	}
	rdb := redis.NewClient(&redis.Options{Addr: addr})
	defer func() { _ = rdb.Close() }()

	// ── 连通性：连不上 ⇒ 退出码 2（**环境不满足**，不是「形态变了」）──────────
	pingCtx, cancelPing := context.WithTimeout(context.Background(), 3*time.Second)
	err := rdb.Ping(pingCtx).Err()
	cancelPing()
	if err != nil {
		fmt.Fprintf(os.Stderr, "❌ 连不上 Redis（%s）：%v\n", addr, err)
		os.Exit(2)
	}

	// ── 形态①：预算耗尽 ⇒ 命令不该下发 ───────────────────────────────
	// ① 基线：活着的 ctx ⇒ 真写进去
	baseCtx, cancelBase := context.WithTimeout(context.Background(), 5*time.Second)
	err = rdb.Set(baseCtx, probeKey, "baseline", 0).Err()
	cancelBase()
	if err != nil {
		fmt.Fprintf(os.Stderr, "❌ 基线写入失败：%v\n", err)
		os.Exit(2)
	}

	// ② 过期 ctx（**已经到期** ⇒ waitTurn 立刻 return ⇒ 命令不下发）
	expiredCtx, cancelExpired := context.WithDeadline(context.Background(), time.Now().Add(-time.Second))
	writeErr := rdb.Set(expiredCtx, probeKey, "overwritten", 0).Err()
	cancelExpired()

	// ③ 独立通道（**不带预算**）读回 ⇒ 值仍是基线 ⇒ **命令真的没落到 Redis 里**
	readCtx, cancelRead := context.WithTimeout(context.Background(), 5*time.Second)
	after, readErr := rdb.Get(readCtx, probeKey).Result()
	cancelRead()
	if readErr != nil {
		fmt.Fprintf(os.Stderr, "❌ 读回失败：%v\n", readErr)
		os.Exit(2)
	}

	errIsDeadline := errors.Is(writeErr, context.DeadlineExceeded)
	writeApplied := after == "overwritten"
	fmt.Printf("PROBE|redis_deadline|form=exhausted|write_applied=%t|err_is_deadline=%t|value_after=%s\n",
		writeApplied, errIsDeadline, after)

	// ── 形态②（负控）：预算充足 ⇒ 真写进去 ───────────────────────────
	aliveKey := probeKey + "-alive"
	aliveCtx, cancelAlive := context.WithTimeout(context.Background(), 5*time.Second)
	aliveErr := rdb.Set(aliveCtx, aliveKey, "written", 0).Err()
	aliveVal, aliveReadErr := rdb.Get(aliveCtx, aliveKey).Result()
	cancelAlive()
	if aliveErr != nil || aliveReadErr != nil {
		fmt.Fprintf(os.Stderr, "❌ 负控写/读失败：%v / %v\n", aliveErr, aliveReadErr)
		os.Exit(2)
	}
	aliveApplied := aliveVal == "written"
	aliveIsDeadline := errors.Is(aliveErr, context.DeadlineExceeded)
	fmt.Printf("PROBE|redis_deadline|form=alive|write_applied=%t|err_is_deadline=%t|value_after=%s\n",
		aliveApplied, aliveIsDeadline, aliveVal)
}
