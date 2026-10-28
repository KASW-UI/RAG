// redis-disconnect 探针（Go 腿）—— 测「Redis 连接被服务端优雅关闭（FIN）后，客户端第一条命令多久失败」。
//
// ⚠️ **Options 按真源逐字取**（出处见 docs/DRIFT/hot/rpc.md §12.9.2），**其余全部走 go-redis v9.20.0 库默认**：
//   hot     : service/hot/rpc/internal/svc/service_context.go:17-20  ⇒ {Addr, Password}
//   comment : service/comment/rpc/internal/cache/redis.go:17-19        ⇒ {Addr}（Password 留空）
//   zeroway : go-zero@v1.10.2/core/stores/redis/redisclientmanager.go:32-40 ⇒ {MaxRetries:3, MinIdleConns:8}
//
// ⚠️ **不要自己发明任何字段**（§12.9.2：其余走库默认）。
// ⚠️ **不要 `go mod tidy`** ⇒ 版本集与 hash 由**继承自真源的 go.mod / go.sum** 固定。
//
// 源码规格见 docs/DRIFT/hot/rpc.md §12.9.3（逐字来源）。
package main

import (
	"context"
	"flag"
	"fmt"
	"os"
	"os/exec"
	"time"

	"github.com/redis/go-redis/v9"
)

// 判定「快速失败」与「排队到命令超时」的阈值。
//
// ⚠️ **口径来源**：Lettuce 侧 `commandTimeout: 3s` ⇒ 「排队到超时」形态 ≈ **3007 ms**（实测 · RAG 腿 B 组）；
// ⇒ ⇒ 而 Go 腿实测是 **1669–1808 ms**（§12.9.7）⇒ ⇒ 取 **2500 ms** 作分界
// ⇒ ⇒ 它落在「Go 实测上界 1808」与「Lettuce 超时 3007」**之间** ⇒ ⇒ 两类形态不会互相误判。
const fastFailThresholdMs = 2500

func main() {
	addr := flag.String("addr", "", "redis host:port")
	container := flag.String("container", "", "docker container name（用于 docker stop）")
	mode := flag.String("mode", "hot", "hot | comment | zeroway")
	password := flag.String("password", "", "redis password")
	wait := flag.Int("wait-ms", 1000, "静默等待毫秒（期间不发任何命令）")
	flag.Parse()

	if *addr == "" || *container == "" {
		fmt.Println("PROBE-FAIL missing -addr/-container")
		os.Exit(2)
	}

	opts := &redis.Options{Addr: *addr}
	switch *mode {
	case "hot":
		opts.Password = *password
	case "comment":
		// 真源只设了 Addr ⇒ Password 留空
	case "zeroway":
		opts.MaxRetries = 3
		opts.MinIdleConns = 8
	default:
		fmt.Println("PROBE-FAIL unknown -mode")
		os.Exit(2)
	}

	client := redis.NewClient(opts)
	ctx := context.Background()

	// ① 连上 + PING（测的必须是「断连」而不是「连不上」）
	if err := client.Ping(ctx).Err(); err != nil {
		fmt.Println("PROBE-FAIL ping:", err)
		os.Exit(2)
	}
	fmt.Println("PROBE-READY")

	// ② 静默等待：**期间不发任何命令**（发了会掩盖「断连后第一条命令」的排队窗口）
	time.Sleep(time.Duration(*wait) * time.Millisecond)

	// ③ 外部优雅关闭（docker stop = SIGTERM ⇒ redis 优雅退出 ⇒ 客户端看到 FIN，不是 RST）
	out, err := exec.Command("docker", "stop", *container).CombinedOutput()
	fmt.Printf("PROBE-DOCKER-STOP err=%v out=%s", err, string(out))

	// ④ 断连后第一条命令 ⇒ 量它多久失败
	//
	// ⚠️🔴 **为什么用 PING 而不是 GET**（本探针第一版的真 bug · 由变异 M2 抓出）：
	//   `client.Get(ctx, "g3-probe-key").Err()` 在**连接完全健康**时返回 **`redis: nil`**
	//   （go-redis 的「key 不存在」哨兵）⇒ ⇒ `err != nil` **恒真**
	//   ⇒ ⇒ 分不清「**连着但 key 不存在**」与「**断了**」⇒ ⇒ 「第一条命令失败了」这个判据是**死的**
	//   ⇒ ⇒ 且耗时也会被误判（健康时 ~4ms ⇒ 落在 `fast-fail` 桶里 ⇒ diff 永远绿）
	//   ⇒ ⇒ **PING 才有判别力**：连接健康 ⇒ `PONG, nil`（err == nil）· 断连 ⇒ err != nil
	//   ⇒ ⇒ 这一条与初始连通性检查用**同一个命令** ⇒ ⇒ 前后对称、判据自洽。
	t0 := time.Now()
	cmdErr := client.Ping(ctx).Err()
	elapsed := time.Since(t0)
	fmt.Printf("PROBE-RESULT mode=%s first_command_ms=%d err=%v\n", *mode, elapsed.Milliseconds(), cmdErr)

	// ⑤ **归一化结论**（给 run.sh 逐字节 diff 用 ⇒ 不含任何随机量）
	class := "timeout"
	switch {
	case cmdErr == nil:
		// 连接还活着 ⇒ **断连根本没发生** ⇒ 这次测的不是断连
		class = "not-disconnected"
	case elapsed.Milliseconds() < fastFailThresholdMs:
		class = "fast-fail"
	}
	errPresent := cmdErr != nil
	fmt.Printf("PROBE-VERDICT mode=%s class=%s err_present=%t\n", *mode, class, errPresent)
	if class == "not-disconnected" {
		fmt.Println("PROBE-FAIL first command unexpectedly succeeded ⇒ 断连没有发生（时序/环境问题）")
		os.Exit(2)
	}

	_ = client.Close()
}
