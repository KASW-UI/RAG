# favorite-api — Go → Spring Boot 文件树

> Component：`Sea-RideTheWind_golang/service/favorite/api`
> RAG 模块：`service/favorite/favorite-api`
> 目标根包：`com.rag.favorite.favorite.api`
> 状态：**理论 Java 文件树为空（待推导）** · RAG 侧当前 Java 文件数 `0`

## Go 原始文件树

```
├── favorite.go
├── etc/
│   └── favorite.yaml
└── internal/
    ├── config/
    │   └── config.go
    ├── handler/
    │   ├── routes.go
    │   └── favorite/
    │       ├── create_favorite_folder_handler.go
    │       ├── create_favorite_handler.go
    │       ├── delete_favorite_folder_handler.go
    │       ├── delete_favorite_handler.go
    │       ├── get_favorite_folders_handler.go
    │       ├── get_favorites_by_folder_handler.go
    │       └── update_favorite_folder_name_handler.go
    ├── logic/
    │   └── favorite/
    │       ├── create_favorite_folder_logic.go
    │       ├── create_favorite_logic.go
    │       ├── delete_favorite_folder_logic.go
    │       ├── delete_favorite_logic.go
    │       ├── get_favorite_folders_logic.go
    │       ├── get_favorites_by_folder_logic.go
    │       ├── helper.go
    │       └── update_favorite_folder_name_logic.go
    ├── middleware/
    │   └── checkblacklist_middleware.go
    ├── svc/
    │   └── service_context.go
    └── types/
        └── types.go
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
com.rag.favorite.favorite.api/
└── （待推导）
```
