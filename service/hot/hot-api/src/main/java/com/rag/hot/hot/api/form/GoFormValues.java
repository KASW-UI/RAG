package com.rag.hot.hot.api.form;

import com.rag.common.exception.BusinessException;
import com.rag.hot.hot.common.errmsg.HotErrorCode;
import com.rag.hot.hot.common.errmsg.HotErrorMessages;

/**
 * query string 的<b>输入解释规则</b> —— Go {@code httpx.ParseForm} 的 Java 等价物。
 *
 * <p>本组件唯一的入参面就是 {@code GET /hot/v1/articles?page=&page_size=}（Go
 * {@code routes.go:16-24} + {@code types.go:22-25}），所以这一层规则直接决定 L1。
 *
 * <h2>为什么必须自己写（不能交给 Spring 的默认类型转换）</h2>
 * 写成 {@code @RequestParam(defaultValue = "20") int pageSize} 会得到三条与 Go 不同的行为，
 * 每一条都是<b>「Go 成功 / Java 失败」</b>方向的可观察漂移：
 *
 * <h3>规则 1：空值被<b>整个丢掉</b>（不是报错、也不是「空串」）</h3>
 * <pre>
 *   httpx/util.go:35-62   GetFormValues
 *       for _, v := range values {
 *           if len(v) == 0 { continue }              // ← ?page= 与 ?page 都命中这里
 *       }
 *       if len(filtered) > 0 { params[name] = filtered }   // ← 值为空的 key 根本不进 map
 * </pre>
 * ⇒ {@code ?page=} 与 {@code ?page} 与<b>完全不传</b>三者<b>完全等价</b>：都取 tag 默认值
 * {@code 1}，<b>不报错</b>。
 * <p>Spring 侧 {@code @RequestParam(defaultValue="20") int} 拿到 {@code ""}（不是 null）
 * ⇒ 转换失败抛 {@code MethodArgumentTypeMismatchException}
 * ⇒ 这是一条 Go 根本不存在的拒绝路径（{@code ?page=} 在 Go 是成功的 200）。
 *
 * <h3>规则 2：整数按 {@code strconv.ParseInt(str, 10, 32)} 解释</h3>
 * <pre>
 *   core/mapping/utils.go:104-125   convertTypeFromString
 *       case reflect.Int32: return strconv.ParseInt(str, 10, 32)
 *   （调用链：httpx/requests.go:85-92 ParseForm → formUnmarshaler.Unmarshal
 *     → unmarshaler.go:880 fromString 分支 → :1099 fillPrimitive
 *     → :521 setValueFromString → convertTypeFromString）
 * </pre>
 * Spring 的默认转换走 {@code NumberUtils.parseNumber}，对 {@code Integer} 走
 * {@code Integer.decode}（base 0 语义）⇒ 三条分歧：
 * <table border="1">
 *   <caption>base 0 vs base 10</caption>
 *   <tr><th>输入</th><th>Go（base 10）</th><th>Spring 默认</th></tr>
 *   <tr><td>{@code ?page=010}</td><td><b>10</b></td><td><b>8</b>（八进制）</td></tr>
 *   <tr><td>{@code ?page=0x10}</td><td><b>解析失败</b> → 5001</td><td><b>16</b>，静默通过</td></tr>
 *   <tr><td>{@code ?page=99999999999}</td><td>超出 int32 → 解析失败 → 5001</td><td>溢出后截断</td></tr>
 * </table>
 * ⇒ 这里显式用 {@link Integer#parseInt(String, int)}（base 10、32 位）。
 *
 * <h3>规则 3：解析失败的出口是 <b>HTTP 200 + {code:5001}</b>，不是 400</h3>
 * Go 的 handler（{@code getHotArticlesHandler.go:18-21}）把 {@code httpx.Parse} 的错误交给
 * {@code httpx.ErrorCtx}，而 {@code hotcenter.go:53-66} 的 {@code SetErrorHandler}
 * <b>返回 {@code http.StatusOK}</b>，且该错误<b>不是</b> {@code *hotcommon.CodeError}
 * ⇒ {@code errors.As} 取不到 code ⇒ 用默认值 {@code ErrorServerCommon(5001)} +
 * {@code GetErrMsg(5001)="internal server error"}，{@code Data} 恒 {@code nil}。
 * <p>⇒ 本类在解析失败时<b>直接抛 {@link BusinessException}(5001, "internal server error")}</b>：
 * 由 {@code GlobalExceptionHandler} 走与 Go 完全相同的出口。
 * <b>不要</b>抛「400 类」异常 —— 那会变成裸 400，与 Go 不一致。
 *
 * <h2>本类<b>不做</b>值域校验（与 article 域的同名工具刻意不同）</h2>
 * {@code page=-1} / {@code page_size=99999} 在 Go 侧<b>不会</b>在解析阶段被拒：
 * 它们先落到 {@code types.HotArticlesReq}，再由 logic 的 {@code normalizePagination}
 * 归一化（{@code <=0 → 默认}、{@code >50 → 50}）。提前在 Web 层夹一次会让
 * 「logic 的归一化逻辑」失去可测性，也与 Go 的分层不符。
 *
 * <h2>与 {@code com.rag.article.article.api.form.GoFormValues} 的关系</h2>
 * article-api 有一份同名工具，实现的是<b>同一批 go-zero 解析规则</b>
 * （空值丢弃 / bool 真值表 {@code 1|0} / base-10 整数）。本组件<b>不</b>跨模块依赖它：
 * {@code hot-api} 依赖 article-api 会把两个业务域耦在一起，而把该工具下沉到
 * {@code service/common} 属于<b>超出本次 hot/api 迁移范围</b>的改动
 * （会同时改动 article-api）。此处按「同一规则、同域内自持」处理，
 * 两份各自带测试锁定；若日后要统一，那是独立的一次决策。
 */
public final class GoFormValues {

    private GoFormValues() {
    }

    /**
     * Go {@code GetFormValues} 的「空值丢弃」规则（{@code httpx/util.go:44-45}）。
     *
     * @return {@code null} 表示「这个 key 在 Go 侧<b>不存在</b>」→ 调用方走 tag 默认值
     */
    public static String raw(String queryValue) {
        if (queryValue == null || queryValue.isEmpty()) {
            return null;
        }
        return queryValue;
    }

    /**
     * Go {@code strconv.ParseInt(str, 10, 32)} 对应的 {@code int32} form 字段解析
     * （{@code types.go:23-24} 的 {@code page} / {@code page_size}）。
     *
     * @param rawValue    Spring {@code @RequestParam} 拿到的<b>原始字符串</b>（可能是 {@code null} 或 {@code ""}）
     * @param fieldName   Go tag 上的键名（{@code "page"} / {@code "page_size"}），只用于日志可读性
     * @param defaultValue Go tag 上的 {@code ,default=...}（{@code 1} / {@code 20}）
     * @return 解析后的值；key 缺席或值为空时返回 {@code defaultValue}
     * @throws BusinessException 解析失败 → HTTP 200 + {@code {code:5001,msg:"internal server error",data:null}}
     */
    public static int int32(String rawValue, String fieldName, int defaultValue) {
        String v = raw(rawValue);
        if (v == null) {
            return defaultValue;
        }
        try {
            // Go: strconv.ParseInt(str, 10, 32) —— base 10、32 位，越界即 error
            return Integer.parseInt(v, 10);
        } catch (NumberFormatException e) {
            // ⚠️ msg 必须**恰好**是 GetErrMsg(5001) = "internal server error"。
            // Go 侧 parse 错误不是 *CodeError ⇒ errors.As 取不到 ⇒ msg 走 GetErrMsg(code) 兜底，
            // 响应里不会出现任何解析细节。把字段名/原值拼进 msg 会造成 L1 字节级漂移，
            // 因此细节只挂到 cause 上（日志可见，wire 不可见）。
            int code = HotErrorCode.ERROR_SERVER_COMMON.getCode();
            BusinessException failure = new BusinessException(code, HotErrorMessages.getMsg(code));
            failure.initCause(new NumberFormatException(
                    "unmarshal field \"" + fieldName + "\": strconv.ParseInt: parsing \"" + v
                            + "\": invalid syntax"));
            throw failure;
        }
    }
}