# 出向 stub 全表（P2-T01 落点表）

> **本表由脚本生成** ⇒ **不要手改** ⇒ 改完请跑
> `python3 docs/RoundPlan/round-02/count-outbound-stubs.py --write-inventory` 重生成。
> 生成器与口径见同目录 [`count-outbound-stubs.py`](count-outbound-stubs.py) 的模块 docstring。

- **出向 stub 提供者** = **21 个类**
- **三态分布** = ① 走统一载体 **7** · ② 手搓 remaining **1** · ③ 裸 stub **13**（自检：①+②+③ == 总数 ⇒ ✅）
- **零上界类** = **0**
- ⚠️ **形态轴 ≠ 上界轴**：形态①（走 `applyTo`）**只注入入向预算**，**不含** Go 的 2s 客户端上界 ⇒ ⇒ 一个类可以「有载体」却**零上界**。

## 一、全表

| # | 模块 | 文件:行号 | `@GrpcClient` 目标 | 形态 | 硬上界来源 | 硬上界? | 调用点数 | Go 对照（Timeout） |
|---|---|---|---|---|---|---|---|---|
| 1 | `article/article-api` | `service/article/article-api/src/main/java/com/rag/article/article/api/client/ArticleRpcClient.java:18` | `article-rpc` | ① 走统一载体 | 共享层 per-service cap（按下游服务分档 · article.ArticleService=70000ms） | ✅ | 9 | `article-rpc` = **70000ms**（service/article/api/etc/article-api.yaml:12） |
| 2 | `article/article-api` | `service/article/article-api/src/main/java/com/rag/article/article/api/client/UserRpcClient.java:60` | `user-rpc` | ② 手搓 remaining | 本地 min cap · 共享层 per-service cap（按下游服务分档 · user.UserService=2000ms） | ✅ | 1 | `user-rpc` = **2000ms**（go-zero@v1.10.0/zrpc/config.go:31（Go yaml 无 `UserRpcConf` 段 ⇒ 走默认）） |
| 3 | `article/article-rpc` | `service/article/article-rpc/src/main/java/com/rag/article/article/rpc/client/MessageRpcClient.java:53` | `message-rpc` | ③ 裸 stub | 共享层 cap-millis=2000 | ✅ | 0 | `message-rpc` = **2000ms**（go-zero@v1.10.0/zrpc/config.go:31（Go yaml 无 `MessageRpcConf` 段 ⇒ 走默认）） |
| 4 | `comment/comment-api` | `service/comment/comment-api/src/main/java/com/rag/comment/comment/api/client/CommentRpcClient.java:23` | `comment-rpc` | ③ 裸 stub | 共享层 cap-millis=${COMMENT_API_OUTBOUND_DEADLINE_CAP_MILLIS:2000} | ✅ | 7 | `comment-rpc` = **2000ms**（go-zero@v1.10.0/zrpc/config.go:31（Go yaml 无 `CommentRpcConf` 段 ⇒ 走默认）） |
| 5 | `comment/comment-rpc` | `service/comment/comment-rpc/src/main/java/com/rag/comment/comment/rpc/client/ArticleRpcClient.java:53` | `article-rpc` | ③ 裸 stub | 共享层 cap-millis=2000 | ✅ | 0 | `article-rpc` = **2000ms**（go-zero@v1.10.0/zrpc/config.go:31（Go yaml 无 `ArticleRpcConf` 段 ⇒ 走默认）） |
| 6 | `comment/comment-rpc` | `service/comment/comment-rpc/src/main/java/com/rag/comment/comment/rpc/client/MessageRpcClient.java:48` | `message-rpc` | ③ 裸 stub | 共享层 cap-millis=2000 | ✅ | 0 | `message-rpc` = **2000ms**（go-zero@v1.10.0/zrpc/config.go:31（Go yaml 无 `MessageRpcConf` 段 ⇒ 走默认）） |
| 7 | `favorite/favorite-api` | `service/favorite/favorite-api/src/main/java/com/rag/favorite/favorite/api/client/FavoriteRpcClient.java:29` | `favorite-rpc` | ③ 裸 stub | 共享层 cap-millis=${FAVORITE_API_OUTBOUND_DEADLINE_CAP_MILLIS:2000} | ✅ | 7 | `favorite-rpc` = **2000ms**（go-zero@v1.10.0/zrpc/config.go:31（Go yaml 无 `FavoriteRpcConf` 段 ⇒ 走默认）） |
| 8 | `favorite/favorite-rpc` | `service/favorite/favorite-rpc/src/main/java/com/rag/favorite/favorite/rpc/client/ArticleRpcClient.java:42` | `article-rpc` | ③ 裸 stub | 共享层 cap-millis=2000 | ✅ | 0 | `article-rpc` = **2000ms**（go-zero@v1.10.0/zrpc/config.go:31（Go yaml 无 `ArticleRpcConf` 段 ⇒ 走默认）） |
| 9 | `favorite/favorite-rpc` | `service/favorite/favorite-rpc/src/main/java/com/rag/favorite/favorite/rpc/client/UserRpcClient.java:29` | `user-rpc` | ③ 裸 stub | 共享层 cap-millis=2000 | ✅ | 1 | `user-rpc` = **2000ms**（go-zero@v1.10.0/zrpc/config.go:31（Go yaml 无 `UserRpcConf` 段 ⇒ 走默认）） |
| 10 | `follow/follow-api` | `service/follow/follow-api/src/main/java/com/rag/follow/follow/api/client/FollowRpcClient.java:35` | `follow-rpc` | ① 走统一载体 | 共享层 cap-millis=${FOLLOW_API_OUTBOUND_DEADLINE_CAP_MILLIS:2000} | ✅ | 1 | `follow-rpc` = **2000ms**（go-zero@v1.10.0/zrpc/config.go:31（Go yaml 无 `FollowRpcConf` 段 ⇒ 走默认）） |
| 11 | `follow/follow-rpc` | `service/follow/follow-rpc/src/main/java/com/rag/follow/follow/rpc/client/UserRpcClient.java:34` | `user-rpc` | ③ 裸 stub | 共享层 cap-millis=${FOLLOW_RPC_OUTBOUND_DEADLINE_CAP_MILLIS:2000} | ✅ | 0 | `user-rpc` = **2000ms**（go-zero@v1.10.0/zrpc/config.go:31（Go yaml 无 `UserRpcConf` 段 ⇒ 走默认）） |
| 12 | `hot/hot-api` | `service/hot/hot-api/src/main/java/com/rag/hot/hot/api/client/ArticleRpcClient.java:66` | `article-rpc` | ① 走统一载体 | 共享层 cap-millis=${HOT_API_OUTBOUND_DEADLINE_CAP_MILLIS:2000} | ✅ | 1 | `article-rpc` = **2000ms**（go-zero@v1.10.0/zrpc/config.go:31（Go yaml 无 `ArticleRpcConf` 段 ⇒ 走默认）） |
| 13 | `hot/hot-api` | `service/hot/hot-api/src/main/java/com/rag/hot/hot/api/client/HotRpcClient.java:19` | `hot-rpc` | ① 走统一载体 | 共享层 cap-millis=${HOT_API_OUTBOUND_DEADLINE_CAP_MILLIS:2000} | ✅ | 1 | `hot-rpc` = **2000ms**（go-zero@v1.10.0/zrpc/config.go:31（Go yaml 无 `HotRpcConf` 段 ⇒ 走默认）） |
| 14 | `like/like-api` | `service/like/like-api/src/main/java/com/rag/like/like/api/client/LikeRpcClient.java:24` | `like-rpc` | ③ 裸 stub | 共享层 cap-millis=${LIKE_API_OUTBOUND_DEADLINE_CAP_MILLIS:2000} | ✅ | 6 | `like-rpc` = **2000ms**（go-zero@v1.10.0/zrpc/config.go:31（Go yaml 无 `LikeRpcConf` 段 ⇒ 走默认）） |
| 15 | `like/like-rpc` | `service/like/like-rpc/src/main/java/com/rag/like/like/rpc/client/MessageRpcClient.java:52` | `message-rpc` | ③ 裸 stub | 共享层 cap-millis=${LIKE_RPC_OUTBOUND_DEADLINE_CAP_MILLIS:2000} | ✅ | 0 | `message-rpc` = **2000ms**（go-zero@v1.10.0/zrpc/config.go:31（Go yaml 无 `MessageRpcConf` 段 ⇒ 走默认）） |
| 16 | `message/message-api` | `service/message/message-api/src/main/java/com/rag/message/message/api/client/MessageRpcClient.java:23` | `message-rpc` | ① 走统一载体 | 共享层 cap-millis=${MESSAGE_API_OUTBOUND_DEADLINE_CAP_MILLIS:2000} | ✅ | 10 | `message-rpc` = **2000ms**（go-zero@v1.10.0/zrpc/config.go:31（Go yaml 无 `MessageRpcConf` 段 ⇒ 走默认）） |
| 17 | `message/message-rpc` | `service/message/message-rpc/src/main/java/com/rag/message/message/rpc/client/AdminRpcClient.java:42` | `admin-rpc` | ③ 裸 stub | 共享层 cap-millis=${MESSAGE_RPC_OUTBOUND_DEADLINE_CAP_MILLIS:2000} | ✅ | 0 | `admin-rpc` = **2000ms**（go-zero@v1.10.0/zrpc/config.go:31（Go yaml 无 `AdminRpcConf` 段 ⇒ 走默认）） |
| 18 | `message/message-rpc` | `service/message/message-rpc/src/main/java/com/rag/message/message/rpc/client/UserRpcClient.java:28` | `user-rpc` | ③ 裸 stub | 共享层 cap-millis=${MESSAGE_RPC_OUTBOUND_DEADLINE_CAP_MILLIS:2000} | ✅ | 0 | `user-rpc` = **2000ms**（go-zero@v1.10.0/zrpc/config.go:31（Go yaml 无 `UserRpcConf` 段 ⇒ 走默认）） |
| 19 | `task/task-api` | `service/task/task-api/src/main/java/com/rag/task/task/api/client/TaskRpcClient.java:24` | `task-rpc` | ③ 裸 stub | 共享层 cap-millis=${TASK_API_OUTBOUND_DEADLINE_CAP_MILLIS:2000} | ✅ | 0 | `task-rpc` = **2000ms**（go-zero@v1.10.0/zrpc/config.go:31（Go yaml 无 `TaskRpcConf` 段 ⇒ 走默认）） |
| 20 | `users/admin/admin-api` | `service/users/admin/admin-api/src/main/java/com/rag/users/admin/api/client/AdminRpcClient.java:20` | `admin-rpc` | ① 走统一载体 | 共享层 cap-millis=${ADMIN_API_OUTBOUND_DEADLINE_CAP_MILLIS:2000} | ✅ | 13 | `admin-rpc` = **2000ms**（go-zero@v1.10.0/zrpc/config.go:31（Go yaml 无 `AdminRpcConf` 段 ⇒ 走默认）） |
| 21 | `users/user/user-api` | `service/users/user/user-api/src/main/java/com/rag/users/user/api/client/UserRpcClient.java:19` | `user-rpc` | ① 走统一载体 | 共享层 cap-millis=${USER_API_OUTBOUND_DEADLINE_CAP_MILLIS:2000} | ✅ | 9 | `user-rpc` = **2000ms**（go-zero@v1.10.0/zrpc/config.go:31（Go yaml 无 `UserRpcConf` 段 ⇒ 走默认）） |

## 二、零上界类清单（硬上界三个来源全无）

**（无）** ⇒ 全仓出向 stub 提供者均有硬上界。

## 三、Go 对照口径

- Go 侧客户端硬上界的**默认出处** = `go-zero@v1.10.0/zrpc/config.go:31`（`json:",default=2000"`）
- Go yaml 里**显式**写了 `Timeout:` 的，按显式值算（实测只有 `article-api.yaml` 的 `ArticleRpcConf: Timeout: 70000` 是显式值）
- RAG 模块 → Go yaml 目录的映射是脚本里的**显式表**（`GO_YAML_MAP`）⇒ 可逐条复算
