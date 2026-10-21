package com.rag.users.common.http;

import java.util.function.Supplier;

/**
 * Controller 侧的分发器：把「空 body → 零值 struct」与「必填校验」合成<b>一次</b>调用。
 *
 * <p>用法（取代 controller 里原先手写的 {@code if (req == null) req = new XxxReq();}）：
 * <pre>
 *   public RegisterResp register(@RequestBody(required = false) RegisterReq req, HttpServletRequest r) {
 *       return service.register(GoStyleRequest.normalize(req, RegisterReq::new), r);
 *   }
 * </pre>
 *
 * <p><b>为什么仍保留 {@code required = false}</b>：Spring 在 {@code required = true} 时
 * 空 body 抛 {@code HttpMessageNotReadableException}，那是<b>另一条</b>错误路径、
 * 错误文本也不是 Go 的 {@code field "xxx" is not set}。这里要让 {@code req == null}
 * 正常进来、由本方法归一化成零值 DTO，再走与 Go 相同的必填判定。
 *
 * <p><b>Go 出处（这条修正了两个 controller javadoc 里的错误论证）</b>：
 * 原文写「空 body ⇒ {@code withJsonBody} 为 false ⇒ {@code UnmarshalJsonMap(nil, v)} ⇒
 * 对 struct 是 <b>no-op、不报错</b>」。前半句对，后半句<b>错</b>。
 * {@code core/mapping/jsonunmarshaler.go:19-21} 的
 * {@code UnmarshalJsonMap(m map[string]any, v any)} 收的是<b>有类型的 nil map</b>，
 * 传进 {@code Unmarshal(i any, v any)} 后在 {@code unmarshaler.go:988} 的 type switch
 * 里<b>命中 {@code case map[string]any}</b>（而不是落到 {@code default} 的
 * {@code errUnsupportedType}），于是 {@code unmarshalValuer(mapValuer(nil), ...)}
 * 照常遍历全部字段 —— 空 map 下 {@code getValue} 全部落空，
 * 标量必填字段照样走到 {@code newInitError}。
 *
 * <p>实测坐实：真空 body（Content-Length: 0）与 {@code {}} 都返回
 * {@code 400 field "username" is not set}。
 *
 * <p>这与 AGENTS.md §3.4.4 DRIFT-009 是<b>同一类病灶</b>：引用了真实文件、
 * 但把行为读反了 —— 而且当时正是这个错误结论让 22 个端点全部设成
 * {@code required = false} + 零值归一化。
 */
public final class GoStyleRequest {

    private GoStyleRequest() {
    }

    /**
     * 空 body 归一化成零值 DTO，然后执行 go-zero 必填校验。
     *
     * @param req     Spring 反序列化结果，可能为 {@code null}（空 body）
     * @param factory 零值 DTO 的构造器（Go 的零值 struct）
     * @param <T>     请求 DTO 类型
     * @return 非 null 的 DTO，必填字段已通过校验
     * @throws GoRequiredFieldException 任一必填字段缺失
     */
    public static <T extends GoStyleRequiredFields> T normalize(T req, Supplier<T> factory) {
        T target = (req != null) ? req : factory.get();
        target.assertGoRequiredFields();
        return target;
    }
}