package com.rag.task.task.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 响应体 —— 对应 Go {@code api/internal/types/types.go:56-58}：
 *
 * <pre>
 *   type GetTaskResp struct {
 *       Tasks []TaskInfo `json:"tasks"`
 *   }
 * </pre>
 *
 * <h2>⚠️ 成功响应<b>没有</b> {@code {code,msg,data}} 信封（这一条最容易抄错）</h2>
 * Go 的 {@code api/task.go} 全文 37 行，<b>没有调用 {@code httpx.SetOkHandler}</b>。
 * 于是在 {@code rest/httpx/responses.go:56-64}：
 * <pre>
 *   func OkJsonCtx(ctx, w, v) {
 *       ...
 *       if handlerCtx != nil { v = handlerCtx(ctx, v) }   // ← handlerCtx 为 nil，不包装
 *       WriteJsonCtx(ctx, w, http.StatusOK, v)          // :63 直接写原始对象
 *   }
 * </pre>
 * ⇒ 成功响应体就是 {@code {"tasks":[...]}}。
 * <p>对比：{@code like/api/likecenter.go:41-47} <b>注册了</b> okHandler（返回信封），
 * {@code hot/api/hotcenter.go:53}、{@code message/api/messagecenter.go:53} 同理。
 * <b>把那几个域的 {@code GlobalResponseAdvice} 照抄到本模块就是漂移</b>（AGENTS.md §3.1
 * 「不要拿另一个域的实测结论当对照基准」）。⇒ 本模块<b>不建</b> {@code GlobalResponseAdvice}。
 *
 * <h2>⚠️ {@code tasks} 永远是数组，绝不为 {@code null}</h2>
 * Go 侧 {@code gettasklogic.go:43} 是
 * {@code out := make([]types.TaskInfo, 0, len(rpcResp.Task))} ——
 * <b>无条件</b>创建、且长度可能为 0 但<b>非 nil</b> ⇒ 输出 {@code "tasks":[]}。
 * <p>proto 的 {@code repeated} 在 Java 侧天然返回空 List（不会为 null），
 * 但本类在构造时仍<b>再兜一层</b>：任何一条路径都不允许产出 {@code {"tasks":null}}。
 */
public class GetTaskResp {

    /** Go {@code types.go:57} {@code Tasks []TaskInfo `json:"tasks"`}。 */
    @JsonProperty("tasks")
    private List<TaskInfo> tasks;

    public GetTaskResp() {
        this.tasks = List.of();
    }

    public GetTaskResp(List<TaskInfo> tasks) {
        this.tasks = tasks == null ? List.of() : tasks;
    }

    public List<TaskInfo> getTasks() {
        return tasks;
    }

    public void setTasks(List<TaskInfo> tasks) {
        this.tasks = tasks == null ? List.of() : tasks;
    }
}