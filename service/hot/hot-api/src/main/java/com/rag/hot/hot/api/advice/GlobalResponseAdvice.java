package com.rag.hot.hot.api.advice;

/**
 * 把 handler 返回值包装成 {code:200,msg:"OK",data}；body==null 或已是 Response 时必须放行
 *
 * <p>Go 真源：{@code httpx.SetOkHandler（api/hotcenter.go:45-51）}
 * <p>推导依据：{@code FileTree/hot/api.md §12.5}
 *
 * <p><b>本文件是空骨架</b>，仅有类型声明，尚无任何实现；按上面的 FileTree 出处逐个翻译即可。
 */
public class GlobalResponseAdvice {
}
