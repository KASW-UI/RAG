package com.rag.hot.hot.api.advice;

/**
 * 异常 → HTTP 200 + {code,msg,null}；不接管 404/405（Spring 默认已是裸响应）
 *
 * <p>Go 真源：{@code httpx.SetErrorHandler（api/hotcenter.go:53-66）}
 * <p>推导依据：{@code FileTree/hot/api.md §12.5}
 *
 * <p><b>本文件是空骨架</b>，仅有类型声明，尚无任何实现；按上面的 FileTree 出处逐个翻译即可。
 */
public class GlobalExceptionHandler {
}
