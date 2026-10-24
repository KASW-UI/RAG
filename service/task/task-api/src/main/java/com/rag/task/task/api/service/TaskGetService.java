package com.rag.task.task.api.service;

import com.rag.task.task.api.client.TaskRpcClient;
import com.rag.task.task.api.dto.GetTaskReq;
import com.rag.task.task.api.dto.GetTaskResp;
import com.rag.task.task.api.dto.TaskInfo;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 本域<b>全部</b>业务 —— 对应 Go {@code api/internal/logic/task/gettasklogic.go:30-56}。
 *
 * <p>Go 的这个 logic 是<b>纯转发 + 字段投影</b>：无状态转换、无缓存、无 DB、无分支。
 * 逐行对照：
 * <pre>
 *   func (l *GetTaskLogic) GetTask(req *types.GetTaskReq) (resp *types.GetTaskResp, err error) {
 *       uid, err := req.Userid.Int64()                              // :31
 *       if err != nil { return nil, err }                            // :32-34
 *
 *       rpcResp, err := l.svcCtx.TaskCli.GetTask(l.ctx, &amp;taskpb.GetTaskReq{UserId: uid})   // :36
 *       if err != nil { return nil, err }                            // :39-41
 *
 *       out := make([]types.TaskInfo, 0, len(rpcResp.Task))          // :43
 *       for _, t := range rpcResp.Task {                             // :44
 *           out = append(out, types.TaskInfo{                        // :45-51
 *               Name: t.Name, Desc: t.Desc, Task_id: t.TaskId,
 *               Completion_progress: t.CompletionProgress,
 *               Required_progress:   t.RequiredProgress,
 *           })
 *       }
 *       return &amp;types.GetTaskResp{Tasks: out}, nil                // :53-55
 *   }
 * </pre>
 *
 * <h2>⚠️ 本类不做「更聪明的」事——三处最容易顺手加、但都是漂移的地方</h2>
 * <ol>
 *   <li><b>不按用户过滤任务</b>：返回的是 task-rpc 给的<b>全部</b> task（Go {@code :44} 不带任何条件）。
 *       「只回未完成的任务」会凭空改变响应长度。</li>
 *   <li><b>不改写任何字段</b>：{@code completionProgress} / {@code requiredProgress} 都<b>原样透传</b>。
 *       在 api 侧做「进度封顶」「按百分比换算」都是新增业务规则。</li>
 *   <li><b>不捕获 gRPC 异常</b>：Go 的 {@code err != nil} 是「返回 error」，
 *       落到 HTTP 上就是 {@code errcode.CodeFromGrpcError} 的状态码映射。
 *       在这里包一层 {@code try/catch} 换成别的错误类型，那条映射链就断了。</li>
 * </ol>
 *
 * <h2>为什么 proto→DTO 的投影是<b>私有方法</b>而不是独立 mapper</h2>
 * 单一消费者、5 个字段、没有第二个调用方 ⇒ 开一个类/一个包都是净负担。
 * 反过来，若本域将来出现第二个「rpc 响应→HTTP 响应」的用例，再抽出去也不迟。
 */
@Service
public class TaskGetService {

    private final TaskRpcClient taskRpcClient;

    public TaskGetService(TaskRpcClient taskRpcClient) {
        this.taskRpcClient = taskRpcClient;
    }

    /**
     * {@code POST /taskcenter/v1/task/get} 的唯一用例。
     *
     * @param req 已完成①（string|number 归一化）的请求体；②（正整数校验）在第 1 行做
     * @throws IllegalArgumentException       {@code id} 为空 / 非正 / 非法字面量
     *                                       ⇒ advice 映成 {@code 400 "user_id invalid"}
     * @throws io.grpc.StatusRuntimeException task-rpc 失败（<b>原样透传</b>）
     *                                       ⇒ advice 按 {@code CodeFromGrpcError} 映射状态码
     */
    public GetTaskResp getTask(GetTaskReq req) {
        long userId = req.userIdOrThrow(); // Go gettasklogic.go:31-34

        com.rag.task.task.proto.GetTaskResp rpcResp = taskRpcClient.getTask(userId); // :36-41

        // Go 侧 rpcResp 为 nil 时会在 `range rpcResp.Task` 处 panic，被 go-zero 的 Recover
        // 中间件接住后同样走 httpx.ErrorCtx ⇒ 仍是 400 text/plain。这里显式抛同一个形状的
        // 异常，而不是靠 NPE 偶然落到兜底分支。
        if (rpcResp == null) {
            throw new IllegalStateException("task-rpc returned a nil GetTaskResp");
        }

        List<TaskInfo> out = new ArrayList<>(rpcResp.getTaskCount()); // Go :43 的 make(..., 0, n)
        for (com.rag.task.task.proto.Task t : rpcResp.getTaskList()) { // :44-52
            out.add(new TaskInfo(
                    t.getName(),
                    t.getDesc(),
                    t.getTaskId(),
                    t.getCompletionProgress(),
                    t.getRequiredProgress()));
        }
        return new GetTaskResp(out);
    }
}