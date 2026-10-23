# task-rpc — Go → Spring Boot 文件树

> Component：`Sea-RideTheWind_golang/service/task/rpc`
> RAG 模块：`service/task/task-rpc`
> 目标根包：`com.rag.task.task.rpc`
> 状态：**理论 Java 文件树为空（待推导）** · RAG 侧当前 Java 文件数 `0`

## Go 原始文件树

```
├── task.go
├── etc/
│   └── task.yaml
├── internal/
│   ├── Init/
│   │   ├── initArticleFilter.go
│   │   ├── initGoKa.go
│   │   ├── initKafkaRaw.go
│   │   ├── initReward.go
│   │   └── initUserFilter.go
│   ├── Model/
│   │   └── taskModel.go
│   ├── config/
│   │   └── config.go
│   ├── logic/
│   │   └── get_task_logic.go
│   ├── reward/
│   │   ├── producer.go
│   │   ├── type.go
│   │   └── worker.go
│   ├── server/
│   │   └── task_service_server.go
│   ├── sink/
│   │   ├── article_sink.go
│   │   └── like_sink.go
│   └── svc/
│       └── service_context.go
└── taskservice/
    └── task_service.go
```

## RAG 当前文件树

```
├── pom.xml
└── src/
    ├── main/
    │   ├── java/
    │   └── resources/
    └── test/
        └── java/
```

## 理论 Java 文件树

```
com.rag.task.task.rpc/
└── （待推导）
```
