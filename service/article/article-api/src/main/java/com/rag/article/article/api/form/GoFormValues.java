package com.rag.article.article.api.form;

import com.rag.article.article.api.http.GoStyleBadRequestException;

/**
 * <b>query string 的输入解释规则</b> —— Go {@code httpx.ParseForm} 的 Java 等价物。
 *
 * <h2>为什么必须自己写（不能交给 Spring 的默认转换）</h2>
 *
 * <p>Go 侧 {@code GET /v1/article/:id} 与 {@code GET /v1/articles} 的
 * {@code form:} 字段走的是 {@code httpx/requests.go:27-31} 那个 Unmarshaler：
 * <pre>
 *   formUnmarshaler = mapping.NewUnmarshaler(formKey,
 *       mapping.WithStringValues(),      // ← 标量按字符串解释
 *       mapping.WithOpaqueKeys(),
 *       mapping.WithFromArray())         // ← 单元素数组拆成标量
 *   formValues = httpx.GetFormValues(r)  // ← requests.go:22-65
 * </pre>
 * 两条规则的组合，Spring 的默认转换<b>都不满足</b>：
 *
 * <h3>规则 1：空值被<b>整个丢掉</b>（不是报错，也不是「空串」）</h3>
 * <pre>
 *   httpx/util.go:44-45
 *       for _, v := range values {
 *           if len(v) == 0 { continue }        // ← ?incr_view= 与 ?incr_view 都命中这里
 *       }
 *       ...
 *       if len(filtered) > 0 { params[name] = filtered }   // ← 值为空的 key 根本不进 map
 * </pre>
 * ⇒ {@code ?incr_view=} 与 {@code ?incr_view} 与<b>完全不传</b>三者<b>完全等价</b>：
 * 都取 Go 零值 {@code false}，<b>不报错</b>。
 * Spring 的 {@code @RequestParam(defaultValue="false") boolean} 会先尝试把 {@code ""}
 * 转 boolean 而<b>抛</b> {@code MethodArgumentTypeMismatchException}（400）——
 * 这是凭空多出一条 Go 没有的拒绝路径。
 *
 * <h3>规则 2：bool 的真值表是 {@code 1/0}，不是 Spring 的 {@code true/false}</h3>
 * <pre>
 *   core/mapping/utils.go  convertTypeFromString
 *       case reflect.Bool:
 *           if str == "1" || strings.EqualFold(str, "true") { return true, nil }
 *           if str == "0" || strings.EqualFold(str, "false") { return false, nil }
 *           return false, errTypeMismatch
 * </pre>
 * ⚠️ 这里<b>不是</b> {@code strconv.ParseBool}（那会额外接受 {@code "t"/"T"/"f"/"F"}）。
 * 对照 Spring 的 {@code StringToBooleanConverter}：只认 {@code "true"}/{@code "false"}
 * （{@code Boolean.valueOf} 语义），<b>拒绝 {@code "1"}</b>。
 * ⇒ {@code ?incr_view=1} 在 Go 是 {@code true}，在 Spring 默认绑定是 400。
 *
 * <h3>规则 3：整数是 {@code strconv.ParseInt(str, 10, bits)}</h3>
 * <pre>
 *       case reflect.Int32: return strconv.ParseInt(str, 10, 32)
 * </pre>
 * Spring 的 {@code StringToNumber} 内部走 {@code NumberFormat.parse} /
 * {@code Integer.decode}，两者与 Go 都不同：
 * <ul>
 *   <li>{@code Integer.decode("010")} = <b>8</b>（八进制），Go 的 base-10 解析 = <b>10</b>；</li>
 *   <li>{@code Integer.decode("0x10")} = <b>16</b>，Go 直接<b>报错</b>（400）。</li>
 * </ul>
 *
 * <h2>为什么这些规则值得逐条对齐</h2>
 *
 * <p>{@code incr_view} 是<b>有副作用</b>的：它决定 RPC 侧要不要给文章
 * {@code +1} 阅读量（{@code GetArticleRequest.incr_view}）。
 * 把 {@code ?incr_view=1} 从「true」变成「400」不是形状差异，
 * 而是<b>一个原本成功的请求变成失败</b>。
 *
 * <p>⚠️ 反过来说，本类<b>不</b>做「值域校验」：
 * {@code page=-1} / {@code page_size=99999} 在 Go 侧同样只是原样透传给 rpc，
 * 由 article-rpc 判定（{@code list_articles_logic.go}）。
 * api 层提前拦会与 rpc 重复，且可能不一致。
 */
public final class GoFormValues {

    private GoFormValues() {
    }

    /**
     * Go {@code GetFormValues} 的「空值丢弃」规则。
     *
     * @return {@code null} 表示「这个 key 在 Go 侧<b>不存在</b>」，
     *         调用方据此走默认值分支
     */
    public static String raw(String queryValue) {
        if (queryValue == null || queryValue.isEmpty()) {
            return null;
        }
        return queryValue;
    }

    /**
     * Go {@code convertTypeFromString} 的 {@code reflect.Bool} 分支。
     *
     * @param rawValue Spring {@code @RequestParam} 拿到的原始值（可能为 {@code null} 或 {@code ""}）
     * @param fieldName 字段名，只用于错误文案（对齐 Go 的 {@code unmarshal field %q ...}）
     * @param defaultValue Go tag 上的 {@code ,default=...}；无 default 时传 Go 零值 {@code false}
     * @throws GoStyleBadRequestException 值既不是 1/true 也不是 0/false
     */
    public static boolean bool(String rawValue, String fieldName, boolean defaultValue) {
        String v = raw(rawValue);
        if (v == null) {
            return defaultValue;
        }
        if ("1".equals(v) || "true".equalsIgnoreCase(v)) {
            return true;
        }
        if ("0".equals(v) || "false".equalsIgnoreCase(v)) {
            return false;
        }
        // Go: convertTypeFromString → errTypeMismatch → unmarshaler 包成
        //     `type mismatch for field %q`（newTypeMismatchError）
        throw new GoStyleBadRequestException("type mismatch for field \"" + fieldName + "\"");
    }

    /**
     * Go {@code strconv.ParseInt(str, 10, 32)}（{@code ListArticlesReq.page/page_size}）。
     *
     * <p>⚠️ 刻意<b>不</b>用 {@code Integer.decode} / {@code Integer.parseInt}：
     * 前者接受八进制与 {@code 0x} 前缀，后者的错误文案与 Go 不同。
     * 见类注释「规则 3」。
     */
    public static int int32(String rawValue, String fieldName, int defaultValue) {
        String v = raw(rawValue);
        if (v == null) {
            return defaultValue;
        }
        try {
            // Go: strconv.ParseInt(str, 10, 32) —— 位宽 32，越界即 error
            return Integer.parseInt(v, 10);
        } catch (NumberFormatException e) {
            // Go 的报错文案（strconv.NumError）形如：
            //   strconv.ParseInt: parsing "abc": invalid syntax
            // 这里不逐字复刻（属 Go 标准库文案，非本组件契约），但保留字段名，
            // 便于与 Go 日志对照。
            throw new GoStyleBadRequestException(
                    "strconv.ParseInt: parsing \"" + v + "\": invalid syntax (field \"" + fieldName + "\")");
        }
    }

    /**
     * String 字段：<b>原样透传</b>，不 trim、不校验。
     *
     * <p>Go 侧 {@code convertTypeFromString} 的 {@code reflect.String} 分支就是
     * {@code return str, nil}；空白串是<b>合法值</b>（空串与缺失等价，见规则 1）。
     */
    public static String text(String rawValue, String defaultValue) {
        String v = raw(rawValue);
        return v == null ? defaultValue : v;
    }
}