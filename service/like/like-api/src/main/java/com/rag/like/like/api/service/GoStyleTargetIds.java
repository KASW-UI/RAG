package com.rag.like.like.api.service;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code target_ids} 在 Go 侧对 {@code null} 元素的真实语义 —— 两个读服务共用。
 *
 * <h2>为什么必须专门处理</h2>
 *
 * <p>protobuf 的 {@code addAllTargetIds(...)} 对 {@code null} 元素是<b>硬失败</b>：
 * {@code NullPointerException: Element at index 0 is null.}。
 * 若把 DTO 里的 {@code null} 原样透传过去，异常会被 {@code GlobalExceptionHandler#handleAny}
 * 兜成 {@code 200 + {"code":5001}}，<b>下游 RPC 根本没发出</b> —— 而 Go 侧是<b>正常发出</b>的。
 * 方向是「本该成功的读变成 5001」，且被 NPE 兜底掩盖，排查成本高（DRIFT-LIKEAPI-013）。
 *
 * <h2>Go 的真实语义（消费点：{@code core/mapping/unmarshaler.go:156-183}）</h2>
 *
 * <table border="1">
 *   <caption>逐条</caption>
 *   <tr><th>输入</th><th>Go 结果</th><th>后果</th></tr>
 *   <tr><td>{@code 缺席}</td><td>slice 为 nil</td><td>空 target_ids</td></tr>
 *   <tr><td>{@code []}</td><td>空 slice</td><td>空 target_ids</td></tr>
 *   <tr><td>{@code [null]}</td><td><b>slice 保持 nil</b>（{@code valid} 标志从未置位 ⇒ {@code value.Set} 不执行）</td>
 *       <td>空 target_ids ⇒ like-rpc 回 2001</td></tr>
 *   <tr><td>{@code ["x", null, "y"]}</td><td><b>{@code ["x", "", "y"]}</b> —— {@code conv} 长度仍是 3，
 *       null 位留成零值空串（{@code unmarshaler.go:158-160} 的 {@code continue}）</td>
 *       <td><b>不是过滤</b>，Go 不会缩短数组</td></tr>
 * </table>
 *
 * <p>⚠️ 最后一行最容易做错：把 {@code null} 过滤掉会把长度 3 缩成 2，
 * 于是 <b>下一页的 limit 边界与 offset 全部错位</b> —— 这是分页语义，不是长度洁癖。
 *
 * <h2>⚠️ 不要在这里顺手实现 DRIFT-LIKEAPI-012</h2>
 *
 * <p>012 是另一条独立漂移：{@code target_ids} 里放 <b>JSON 数字</b>时 Go <b>接受</b>
 * （{@code fillSliceValue} 的 {@code case fmt.Stringer} 命中 {@code json.Number}），
 * 而 RAG 的 {@code LogicalType.Textual ← Integer = Fail} 把它拒了。
 * 那是<b>解析层</b>（DTO setter）的责任，<b>不是</b>本方法的责任 ——
 * 数字若已被解析层拒掉，这里根本看不到。
 */
public final class GoStyleTargetIds {

    private GoStyleTargetIds() {
        // 工具类，禁止 new
    }

    /**
     * 把 DTO 里可能含 {@code null} 的 {@code target_ids} 还原成 Go 会发出的形态。
     *
     * @param ids DTO 的原始列表，可能为 {@code null}、为空、或含 {@code null} 元素
     * @return <b>永不</b>返回 {@code null}，也<b>永不</b>含 {@code null} 元素（protobuf 安全）
     */
    public static List<String> normalize(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        // 全 null ⇒ Go 侧 slice 保持 nil ⇒ 等价于空列表（会打出 2001）
        boolean allNull = true;
        for (String id : ids) {
            if (id != null) {
                allNull = false;
                break;
            }
        }
        if (allNull) {
            return List.of();
        }

        // 部分 null ⇒ 该位变空串，**长度不变**（Go 的 continue 不是过滤）
        List<String> normalized = new ArrayList<>(ids.size());
        for (String id : ids) {
            normalized.add(id == null ? "" : id);
        }
        return normalized;
    }
}