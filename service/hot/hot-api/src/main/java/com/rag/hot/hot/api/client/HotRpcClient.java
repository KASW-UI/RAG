package com.rag.hot.hot.api.client;

/**
 * 取 hot-rpc 的热度 TopK。Go 侧 2s deadline 须显式 withDeadlineAfter 补上
 *
 * <p>Go 真源：{@code api/internal/svc/serviceContext.go 的 HotRpc}
 * <p>推导依据：{@code FileTree/hot/api.md §12.5}
 *
 * <p><b>本文件是空骨架</b>，仅有类型声明，尚无任何实现；按上面的 FileTree 出处逐个翻译即可。
 */
public interface HotRpcClient {
}
