# comment-rpc — Go → Spring Boot 文件树

> Component：`Sea-RideTheWind_golang/service/comment/rpc`
> RAG 模块：`service/comment/comment-rpc`
> 目标根包：`com.rag.comment.comment.rpc`
> 状态：**理论 Java 文件树为空（待推导）** · RAG 侧当前 Java 文件数 `0`

## Go 原始文件树

```
├── COMMUNITY_FACTS_ACCEPTANCE.md
├── comment.go
├── cmd/
│   ├── fact-authority/
│   │   └── main.go
│   └── fact-dispatch/
│       └── main.go
├── commentservice/
│   └── comment_service.go
├── common/
│   └── types/
│       └── kqueue.go
├── etc/
│   └── comment.yaml
└── internal/
    ├── cache/
    │   ├── comment_cache.go
    │   ├── comment_content_cache.go
    │   ├── comment_ids_page_cache.go
    │   ├── comment_index_cache.go
    │   ├── comment_invalidation_cache.go
    │   ├── comment_subject_cache.go
    │   ├── keys.go
    │   └── redis.go
    ├── config/
    │   └── config.go
    ├── logic/
    │   ├── create_comment_logic.go
    │   ├── delete_comment_logic.go
    │   ├── get_comment_logic.go
    │   ├── like_comment_logic.go
    │   ├── manage_comment_logic.go
    │   ├── report_comment_logic.go
    │   ├── subject_fallback.go
    │   └── update_subject_state_logic.go
    ├── metrics/
    │   └── metrics.go
    ├── model/
    │   ├── comment.go
    │   ├── data_model.go
    │   ├── db.go
    │   ├── domain_fact.go
    │   ├── fact_authority.go
    │   ├── fact_delivery.go
    │   ├── test-community-postgres.sh
    │   ├── vars.go
    │   └── migrations/
    │       ├── 001_comment_domain_fact_outbox.sql
    │       ├── 002_comment_dc_wire.sql
    │       └── 003_comment_fact_delivery.sql
    ├── mqs/
    │   └── audit_consumer.go
    ├── server/
    │   └── comment_service_server.go
    ├── svc/
    │   └── service_context.go
    ├── trace/
    │   └── trace.go
    └── utils/
        └── sensitive.go
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
com.rag.comment.comment.rpc/
└── （待推导）
```
