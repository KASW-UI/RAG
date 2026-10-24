package com.rag.task.task.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 单条任务 —— 对应 Go {@code api/internal/types/types.go:60-66}：
 *
 * <pre>
 *   type TaskInfo struct {
 *       Name                string `json:"name"`
 *       Desc                string `json:"desc,optional"`
 *       Task_id             int64  `json:"taskId"`
 *       Completion_progress int64  `json:"completionProgress"`
 *       Required_progress   int64  `json:"requiredProgress"`
 *   }
 * </pre>
 *
 * <h2>⚠️ Go 的字段名是 snake，JSON key 是 camel —— 两个都要对上</h2>
 * {@code Task_id} / {@code Completion_progress} 是 goctl 生成的 Go 字段名，
 * 而 tag 上写的是 camelCase。落到 wire 上只有 camelCase 可见，但<b>字段名也按 Go 保留</b>，
 * 便于逐行对照 {@code types.go}。
 *
 * <h2>⚠️ {@code desc} 绝不能加 {@code NON_EMPTY}</h2>
 * tag 里的 {@code ,optional} 是 goctl 的标记，<b>不是</b> {@code encoding/json} 的
 * {@code omitempty}：{@code encoding/json} 不认识 {@code optional} 这个选项，
 * 于是空 {@code Desc} 照样输出 {@code "desc":""}。
 * <p>本模块走的是裸 {@code encoding/json}（{@code gettaskhandler.go:19}），没有 go-zero 的
 * {@code httpx.Parse} 参与 ⇒ <b>这个 {@code ,optional} 是彻底的死标记</b>。
 * 加了 {@code JsonInclude.NON_EMPTY} 会让该键整个消失，客户端按 {@code desc} 取值会拿到
 * {@code undefined} 而不是空串。
 *
 * <h2>⚠️ 字段顺序即字节顺序</h2>
 * Go 的 {@code encoding/json} 按<b>结构体字段声明顺序</b>输出。Jackson 对无序容器的
 * 普通 POJO 同样按声明顺序输出；本类的声明顺序与 {@code types.go:60-66} 逐行一致，
 * 因此 {@code {"name":..,"desc":..,"taskId":..,"completionProgress":..,"requiredProgress":..}}
 * 的键序两侧相同。
 *
 * <h2>为什么用 {@code long} 而不是 {@code Long}</h2>
 * Go 是 {@code int64}，零值必须是数字 {@code 0} 而不是 {@code null}。
 * 用包装类型一旦漏赋值就会输出 {@code null}，那是新增行为。
 */
public class TaskInfo {

    /** Go {@code types.go:61} {@code Name string `json:"name"`}。 */
    @JsonProperty("name")
    private String name;

    /** Go {@code types.go:62} {@code Desc string `json:"desc,optional"`} —— 见类注释，<b>不加 NON_EMPTY</b>。 */
    @JsonProperty("desc")
    private String desc;

    /** Go {@code types.go:63} {@code Task_id int64 `json:"taskId"`}。 */
    @JsonProperty("taskId")
    private long taskId;

    /** Go {@code types.go:64} {@code Completion_progress int64 `json:"completionProgress"`}。 */
    @JsonProperty("completionProgress")
    private long completionProgress;

    /** Go {@code types.go:65} {@code Required_progress int64 `json:"requiredProgress"`}。 */
    @JsonProperty("requiredProgress")
    private long requiredProgress;

    public TaskInfo() {
    }

    public TaskInfo(String name, String desc, long taskId, long completionProgress, long requiredProgress) {
        this.name = name;
        this.desc = desc;
        this.taskId = taskId;
        this.completionProgress = completionProgress;
        this.requiredProgress = requiredProgress;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

    public long getTaskId() {
        return taskId;
    }

    public void setTaskId(long taskId) {
        this.taskId = taskId;
    }

    public long getCompletionProgress() {
        return completionProgress;
    }

    public void setCompletionProgress(long completionProgress) {
        this.completionProgress = completionProgress;
    }

    public long getRequiredProgress() {
        return requiredProgress;
    }

    public void setRequiredProgress(long requiredProgress) {
        this.requiredProgress = requiredProgress;
    }
}