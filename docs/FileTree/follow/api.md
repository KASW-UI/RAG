# follow-api — Go → Spring Boot 文件树

> Component：`Sea-RideTheWind_golang/service/follow/api`
> RAG 模块：`service/follow/follow-api`
> 目标根包：`com.rag.follow.follow.api`
> 状态：**理论 Java 文件树为空（待推导）** · RAG 侧当前 Java 文件数 `0`

## Go 原始文件树

```
├── followcenter.go
├── etc/
│   └── followcenter.yaml
└── internal/
    ├── config/
    │   └── config.go
    ├── handler/
    │   ├── routes.go
    │   └── follow/
    │       ├── blockhandler.go
    │       ├── followhandler.go
    │       ├── getblocklisthandler.go
    │       ├── getfollowerlisthandler.go
    │       ├── getfollowlisthandler.go
    │       ├── getrecommendationshandler.go
    │       ├── unblockhandler.go
    │       └── unfollowhandler.go
    ├── logic/
    │   └── follow/
    │       ├── blocklogic.go
    │       ├── followlogic.go
    │       ├── getblocklistlogic.go
    │       ├── getfollowerlistlogic.go
    │       ├── getfollowlistlogic.go
    │       ├── getrecommendationslogic.go
    │       ├── helper.go
    │       ├── unblocklogic.go
    │       └── unfollowlogic.go
    ├── metrics/
    │   └── metrics.go
    ├── middleware/
    │   └── checkblacklistmiddleware.go
    ├── svc/
    │   └── servicecontext.go
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
com.rag.follow.follow.api/
└── （待推导）
```
