# How To Update

use
```sh
cd '/Users/pr/Documents/java&golang/RAG' && tree
```
to update ProjectTree

# WorkTree
``
.
├── AGENTS.md
├── CONTRIBUTING.md
├── docker-compose.yaml
├── docs
│   ├── FEATURES.md
│   └── USAGE.md
├── graph
│   ├── agents
│   │   ├── bugfixing.md
│   │   ├── developer-preferences-example.md
│   │   ├── developer-preferences.md
│   │   ├── NOW.md
│   │   ├── project-context
│   │   │   └── ProjectTree.md
│   │   ├── prompts
│   │   │   ├── implementer.md
│   │   │   ├── operator.md
│   │   │   └── reviewer.md
│   │   ├── reachability.md
│   │   ├── specs
│   │   │   └── Spec-Template.md
│   │   ├── style
│   │   │   ├── commit.md
│   │   │   └── prose.md
│   │   ├── UPDATE.md
│   │   ├── verification.md
│   │   └── workflow.md
│   ├── AGENTS.md
│   ├── githooks
│   │   ├── pre-push.md
│   │   └── README.md
│   ├── github
│   │   ├── pull-request-template.md
│   │   └── workflows
│   │       ├── ci.md
│   │       ├── containers.md
│   │       ├── gh-pages.md
│   │       ├── qodana.md
│   │       ├── release-postpublish-audit.md
│   │       └── release.md
│   ├── README.md
│   └── scripts
│       ├── agent-integration.md
│       ├── agent-issue-index.md
│       ├── agent-onboard.md
│       ├── agent-pr-body.md
│       ├── agent-preflight.md
│       ├── agent-ready.md
│       ├── agent-role.md
│       ├── agent-start.md
│       ├── audit-live-rows.md
│       ├── check-agent-record.md
│       ├── check-commit-style.md
│       ├── check-commit-trailers.md
│       ├── check-conflict-markers.md
│       ├── check-now-current.md
│       ├── check-prompt-contract.md
│       ├── check-readme-structure.md
│       ├── check-role-discipline.md
│       ├── claim-view.md
│       ├── now.md
│       └── ready-for-helper.md
├── HELP.md
├── mvnw
├── mvnw.cmd
├── pom.xml
├── qodana.yaml
├── README.md
├── scripts
│   ├── __pycache__
│   │   ├── agent-onboard.cpython-311.pyc
│   │   ├── agent-role.cpython-311.pyc
│   │   ├── agent-start.cpython-311.pyc
│   │   ├── check-agent-record.cpython-311.pyc
│   │   └── now.cpython-311.pyc
│   ├── agent-integration.py
│   ├── agent-issue-index.py
│   ├── agent-onboard.py
│   ├── agent-pr-body.py
│   ├── agent-preflight.sh
│   ├── agent-ready.py
│   ├── agent-role.py
│   ├── agent-start.py
│   ├── audit-live-rows.py
│   ├── check-agent-record.py
│   ├── check-commit-style.py
│   ├── check-commit-trailers.py
│   ├── check-conflict-markers.py
│   ├── check-now-current.py
│   ├── check-prompt-contract.py
│   ├── check-readme-structure.py
│   ├── check-role-discipline.py
│   ├── claim-view.py
│   ├── now.py
│   └── ready-for-helper.py
├── service
│   ├── pom.xml
│   └── user
│       ├── admin
│       │   ├── admin-api
│       │   │   └── pom.xml
│       │   ├── admin-rpc
│       │   │   └── pom.xml
│       │   └── pom.xml
│       ├── common
│       │   ├── pom.xml
│       │   └── src
│       │       └── main
│       │           └── java
│       │               └── com
│       │                   └── rag
│       │                       └── user
│       │                           └── common
│       │                               ├── cryptx
│       │                               │   └── PasswordUtil.java
│       │                               ├── errmsg
│       │                               │   └── ErrorCode.java
│       │                               └── jwt
│       │                                   └── JwtUtil.java
│       ├── pom.xml
│       ├── user
│       │   ├── identity
│       │   │   └── pom.xml
│       │   ├── pom.xml
│       │   ├── user-api
│       │   │   └── pom.xml
│       │   └── user-rpc
│       │       ├── pom.xml
│       │       └── src
│       │           ├── main
│       │           │   ├── java
│       │           │   │   └── com
│       │           │   │       └── rag
│       │           │   │           └── user
│       │           │   │               └── user
│       │           │   │                   └── rpc
│       │           │   │                       ├── internal
│       │           │   │                       │   ├── config
│       │           │   │                       │   │   └── Config.java
│       │           │   │                       │   ├── logic
│       │           │   │                       │   │   ├── AvatarHelpers.java
│       │           │   │                       │   │   ├── DeleteUserLogic.java
│       │           │   │                       │   │   ├── GetAvatarHistoryLogic.java
│       │           │   │                       │   │   ├── GetUserLogic.java
│       │           │   │                       │   │   ├── LoginLogic.java
│       │           │   │                       │   │   ├── LogoutLogic.java
│       │           │   │                       │   │   ├── RegisterLogic.java
│       │           │   │                       │   │   ├── SelectAvatarLogic.java
│       │           │   │                       │   │   ├── UpdateUserLogic.java
│       │           │   │                       │   │   └── UploadAvatarLogic.java
│       │           │   │                       │   ├── metrics
│       │           │   │                       │   │   └── Metrics.java
│       │           │   │                       │   ├── model
│       │           │   │                       │   │   ├── DataModel.java
│       │           │   │                       │   │   ├── User.java
│       │           │   │                       │   │   ├── UserAvatarHistory.java
│       │           │   │                       │   │   └── UserExceptions.java
│       │           │   │                       │   ├── server
│       │           │   │                       │   │   └── UserServiceServer.java
│       │           │   │                       │   └── svc
│       │           │   │                       │       └── ServiceContext.java
│       │           │   │                       └── UserRpcApplication.java
│       │           │   └── resources
│       │           │       └── application.yaml
│       │           └── test
│       │               └── java
│       │                   └── com
│       │                       └── rag
│       │                           └── user
│       │                               └── user
│       │                                   └── rpc
│       │                                       └── internal
│       │                                           └── logic
│       │                                               └── GetUserLogicTest.java
│       └── user-proto
│           ├── pom.xml
│           ├── src
│           │   └── main
│           │       └── proto
│           │           └── user.proto
│           └── target
│               ├── classes
│               │   ├── user
│               │   │   ├── User.class
│               │   │   ├── User$AvatarHistoryItem.class
│               │   │   ├── User$AvatarHistoryItem$1.class
│               │   │   ├── User$AvatarHistoryItem$Builder.class
│               │   │   ├── User$AvatarHistoryItemOrBuilder.class
│               │   │   ├── User$CreateUserReq.class
│               │   │   ├── User$CreateUserReq$1.class
│               │   │   ├── User$CreateUserReq$Builder.class
│               │   │   ├── User$CreateUserReq$ExtraInfoDefaultEntryHolder.class
│               │   │   ├── User$CreateUserReqOrBuilder.class
│               │   │   ├── User$CreateUserResp.class
│               │   │   ├── User$CreateUserResp$1.class
│               │   │   ├── User$CreateUserResp$Builder.class
│               │   │   ├── User$CreateUserRespOrBuilder.class
│               │   │   ├── User$DeleteUserReq.class
│               │   │   ├── User$DeleteUserReq$1.class
│               │   │   ├── User$DeleteUserReq$Builder.class
│               │   │   ├── User$DeleteUserReqOrBuilder.class
│               │   │   ├── User$DeleteUserResp.class
│               │   │   ├── User$DeleteUserResp$1.class
│               │   │   ├── User$DeleteUserResp$Builder.class
│               │   │   ├── User$DeleteUserRespOrBuilder.class
│               │   │   ├── User$GetAvatarHistoryReq.class
│               │   │   ├── User$GetAvatarHistoryReq$1.class
│               │   │   ├── User$GetAvatarHistoryReq$Builder.class
│               │   │   ├── User$GetAvatarHistoryReqOrBuilder.class
│               │   │   ├── User$GetAvatarHistoryResp.class
│               │   │   ├── User$GetAvatarHistoryResp$1.class
│               │   │   ├── User$GetAvatarHistoryResp$Builder.class
│               │   │   ├── User$GetAvatarHistoryRespOrBuilder.class
│               │   │   ├── User$GetUserReq.class
│               │   │   ├── User$GetUserReq$1.class
│               │   │   ├── User$GetUserReq$Builder.class
│               │   │   ├── User$GetUserReqOrBuilder.class
│               │   │   ├── User$GetUserResp.class
│               │   │   ├── User$GetUserResp$1.class
│               │   │   ├── User$GetUserResp$Builder.class
│               │   │   ├── User$GetUserRespOrBuilder.class
│               │   │   ├── User$LoginReq.class
│               │   │   ├── User$LoginReq$1.class
│               │   │   ├── User$LoginReq$Builder.class
│               │   │   ├── User$LoginReqOrBuilder.class
│               │   │   ├── User$LoginResp.class
│               │   │   ├── User$LoginResp$1.class
│               │   │   ├── User$LoginResp$Builder.class
│               │   │   ├── User$LoginRespOrBuilder.class
│               │   │   ├── User$LogoutReq.class
│               │   │   ├── User$LogoutReq$1.class
│               │   │   ├── User$LogoutReq$Builder.class
│               │   │   ├── User$LogoutReqOrBuilder.class
│               │   │   ├── User$LogoutResp.class
│               │   │   ├── User$LogoutResp$1.class
│               │   │   ├── User$LogoutResp$Builder.class
│               │   │   ├── User$LogoutRespOrBuilder.class
│               │   │   ├── User$SelectAvatarReq.class
│               │   │   ├── User$SelectAvatarReq$1.class
│               │   │   ├── User$SelectAvatarReq$Builder.class
│               │   │   ├── User$SelectAvatarReqOrBuilder.class
│               │   │   ├── User$SelectAvatarResp.class
│               │   │   ├── User$SelectAvatarResp$1.class
│               │   │   ├── User$SelectAvatarResp$Builder.class
│               │   │   ├── User$SelectAvatarRespOrBuilder.class
│               │   │   ├── User$UpdateUserReq.class
│               │   │   ├── User$UpdateUserReq$1.class
│               │   │   ├── User$UpdateUserReq$Builder.class
│               │   │   ├── User$UpdateUserReq$ExtraInfoDefaultEntryHolder.class
│               │   │   ├── User$UpdateUserReqOrBuilder.class
│               │   │   ├── User$UpdateUserResp.class
│               │   │   ├── User$UpdateUserResp$1.class
│               │   │   ├── User$UpdateUserResp$Builder.class
│               │   │   ├── User$UpdateUserRespOrBuilder.class
│               │   │   ├── User$UploadAvatarReq.class
│               │   │   ├── User$UploadAvatarReq$1.class
│               │   │   ├── User$UploadAvatarReq$Builder.class
│               │   │   ├── User$UploadAvatarReqOrBuilder.class
│               │   │   ├── User$UploadAvatarResp.class
│               │   │   ├── User$UploadAvatarResp$1.class
│               │   │   ├── User$UploadAvatarResp$Builder.class
│               │   │   ├── User$UploadAvatarRespOrBuilder.class
│               │   │   ├── User$UserInfo.class
│               │   │   ├── User$UserInfo$1.class
│               │   │   ├── User$UserInfo$Builder.class
│               │   │   ├── User$UserInfo$ExtraInfoDefaultEntryHolder.class
│               │   │   ├── User$UserInfoOrBuilder.class
│               │   │   ├── UserServiceGrpc.class
│               │   │   ├── UserServiceGrpc$1.class
│               │   │   ├── UserServiceGrpc$2.class
│               │   │   ├── UserServiceGrpc$3.class
│               │   │   ├── UserServiceGrpc$AsyncService.class
│               │   │   ├── UserServiceGrpc$MethodHandlers.class
│               │   │   ├── UserServiceGrpc$UserServiceBaseDescriptorSupplier.class
│               │   │   ├── UserServiceGrpc$UserServiceBlockingStub.class
│               │   │   ├── UserServiceGrpc$UserServiceFileDescriptorSupplier.class
│               │   │   ├── UserServiceGrpc$UserServiceFutureStub.class
│               │   │   ├── UserServiceGrpc$UserServiceImplBase.class
│               │   │   ├── UserServiceGrpc$UserServiceMethodDescriptorSupplier.class
│               │   │   └── UserServiceGrpc$UserServiceStub.class
│               │   └── user.proto
│               ├── generated-sources
│               │   ├── annotations
│               │   └── protobuf
│               │       ├── grpc-java
│               │       │   └── user
│               │       │       └── UserServiceGrpc.java
│               │       └── java
│               │           └── user
│               │               └── User.java
│               ├── maven-status
│               │   └── maven-compiler-plugin
│               │       └── compile
│               │           └── default-compile
│               │               ├── createdFiles.lst
│               │               └── inputFiles.lst
│               ├── protoc-dependencies
│               │   ├── 0dcdc253d4de2dd9eb42260ba807ec04
│               │   │   └── google
│               │   │       └── protobuf
│               │   │           ├── any.proto
│               │   │           ├── api.proto
│               │   │           ├── duration.proto
│               │   │           ├── empty.proto
│               │   │           ├── field_mask.proto
│               │   │           ├── source_context.proto
│               │   │           ├── struct.proto
│               │   │           ├── timestamp.proto
│               │   │           ├── type.proto
│               │   │           └── wrappers.proto
│               │   ├── 90a371fa5d139ab1cde0266001b4bf20
│               │   │   └── google
│               │   │       └── protobuf
│               │   │           ├── any.proto
│               │   │           ├── api.proto
│               │   │           ├── descriptor.proto
│               │   │           ├── duration.proto
│               │   │           ├── empty.proto
│               │   │           ├── field_mask.proto
│               │   │           ├── source_context.proto
│               │   │           ├── struct.proto
│               │   │           ├── timestamp.proto
│               │   │           ├── type.proto
│               │   │           └── wrappers.proto
│               │   └── caaf5442b574ce71bb94413ffac91d57
│               │       └── google
│               │           ├── api
│               │           │   ├── annotations.proto
│               │           │   ├── auth.proto
│               │           │   ├── backend.proto
│               │           │   ├── billing.proto
│               │           │   ├── client.proto
│               │           │   ├── config_change.proto
│               │           │   ├── consumer.proto
│               │           │   ├── context.proto
│               │           │   ├── control.proto
│               │           │   ├── distribution.proto
│               │           │   ├── documentation.proto
│               │           │   ├── endpoint.proto
│               │           │   ├── error_reason.proto
│               │           │   ├── field_behavior.proto
│               │           │   ├── field_info.proto
│               │           │   ├── http.proto
│               │           │   ├── httpbody.proto
│               │           │   ├── label.proto
│               │           │   ├── launch_stage.proto
│               │           │   ├── log.proto
│               │           │   ├── logging.proto
│               │           │   ├── metric.proto
│               │           │   ├── monitored_resource.proto
│               │           │   ├── monitoring.proto
│               │           │   ├── policy.proto
│               │           │   ├── quota.proto
│               │           │   ├── resource.proto
│               │           │   ├── routing.proto
│               │           │   ├── service.proto
│               │           │   ├── source_info.proto
│               │           │   ├── system_parameter.proto
│               │           │   ├── usage.proto
│               │           │   └── visibility.proto
│               │           ├── apps
│               │           │   └── card
│               │           │       └── v1
│               │           │           └── card.proto
│               │           ├── cloud
│               │           │   ├── audit
│               │           │   │   └── audit_log.proto
│               │           │   ├── extended_operations.proto
│               │           │   └── location
│               │           │       └── locations.proto
│               │           ├── geo
│               │           │   └── type
│               │           │       └── viewport.proto
│               │           ├── logging
│               │           │   └── type
│               │           │       ├── http_request.proto
│               │           │       └── log_severity.proto
│               │           ├── longrunning
│               │           │   └── operations.proto
│               │           ├── rpc
│               │           │   ├── code.proto
│               │           │   ├── context
│               │           │   │   ├── attribute_context.proto
│               │           │   │   └── audit_context.proto
│               │           │   ├── error_details.proto
│               │           │   └── status.proto
│               │           ├── shopping
│               │           │   └── type
│               │           │       └── types.proto
│               │           └── type
│               │               ├── calendar_period.proto
│               │               ├── color.proto
│               │               ├── date.proto
│               │               ├── datetime.proto
│               │               ├── dayofweek.proto
│               │               ├── decimal.proto
│               │               ├── expr.proto
│               │               ├── fraction.proto
│               │               ├── interval.proto
│               │               ├── latlng.proto
│               │               ├── localized_text.proto
│               │               ├── money.proto
│               │               ├── month.proto
│               │               ├── phone_number.proto
│               │               ├── postal_address.proto
│               │               ├── quaternion.proto
│               │               └── timeofday.proto
│               └── protoc-plugins
│                   ├── protoc-3.25.5-osx-x86_64.exe
│                   └── protoc-gen-grpc-java-1.66.0-osx-x86_64.exe
└── tests
    └── scripts
        └── test_check_commit_trailers.py
``

