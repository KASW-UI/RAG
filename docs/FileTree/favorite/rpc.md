# favorite-rpc — Go → Spring Boot 文件树

> Component：`Sea-RideTheWind_golang/service/favorite/rpc`
> RAG 模块：`service/favorite/favorite-rpc`
> 目标根包：`com.rag.favorite.favorite.rpc`
> 状态：**理论 Java 文件树为空（待推导）** · RAG 侧当前 Java 文件数 `0`

## Go 原始文件树

```
├── DELIVERY_ACCEPTANCE.md
├── FACT_AUTHORITY_ACCEPTANCE.md
├── FAVORITE_FACT_ACCEPTANCE.md
├── FAVORITE_FULL_CHAIN_ACCEPTANCE.md
├── FAVORITE_REVISION_ACCEPTANCE.md
├── FAVORITE_SUBJECTREF_V2_ACCEPTANCE.md
├── TWO_USER_COVERAGE_ACCEPTANCE.md
├── acceptance-dc.sh
├── acceptance-full.sh
├── acceptance.sh
├── favorite.go
├── cmd/
│   ├── fact-authority/
│   │   └── main.go
│   ├── fact-dispatch/
│   │   └── main.go
│   └── fact-legacy-preflight/
│       └── main.go
├── etc/
│   └── favorite.yaml
├── favoriteservice/
│   └── favorite_service.go
└── internal/
    ├── authority/
    │   └── http.go
    ├── config/
    │   └── config.go
    ├── logic/
    │   ├── create_favorite_folder_logic.go
    │   ├── create_favorite_logic.go
    │   ├── delete_favorite_folder_logic.go
    │   ├── delete_favorite_logic.go
    │   ├── get_favorite_folders_logic.go
    │   ├── get_favorites_by_folder_logic.go
    │   ├── helper.go
    │   └── update_favorite_folder_name_logic.go
    ├── metrics/
    │   └── metrics.go
    ├── model/
    │   ├── 001_favorite_fact_outbox.sql
    │   ├── 002_favorite_fact_delivery.sql
    │   ├── 003_favorite_target_revision.sql
    │   ├── 004_favorite_legacy_fact_marker.sql
    │   ├── data_model.go
    │   ├── db.go
    │   ├── favorite.go
    │   ├── favorite_authority.go
    │   ├── favorite_delivery.go
    │   ├── favorite_fact.go
    │   ├── favorite_legacy_preflight.go
    │   └── vars.go
    ├── server/
    │   └── favorite_service_server.go
    └── svc/
        └── service_context.go
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
com.rag.favorite.favorite.rpc/
└── （待推导）
```
