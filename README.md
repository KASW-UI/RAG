# RAG

## What is it

RAG is the user service module of the SeaRideTheWind Java reimplementation.
It exposes user identity and user information through gRPC: registration,
login, session, profile management, and avatar handling. The service sits
behind a gRPC server entry and persists into PostgreSQL with Redis and
MinIO as the supporting stores.

Position in the system:
- service name: `user-rpc`
- role: user identity and profile provider
- main consumers: client-side calls, other microservices via gRPC
- core capability: registration, login, profile CRUD, avatar handling

## Features

- user registration
- user login
- user logout
- user profile query
- user profile update
- user deletion
- user avatar upload
- user avatar selection
- user avatar history query

The full list and any per-feature limitations live in
[docs/FEATURES.md](docs/FEATURES.md).

## Architecture

```
Client
   |
   v
UserServiceServer (gRPC server entry)
   |
   v
Logic layer (RegisterLogic / LoginLogic / GetUserLogic / UpdateUserLogic /
             / DeleteUserLogic / LogoutLogic / UploadAvatarLogic /
             / SelectAvatarLogic / GetAvatarHistoryLogic / AvatarHelpers)
   |
   v
ServiceContext (DB / Redis / MinIO clients)
   |
   +---> PostgreSQL (user data)
   +---> Redis (token store / cache)
   +---> MinIO (avatar object storage)
```

Tech stack:
- Spring Boot 4.0.8
- Spring Cloud 2025.1.0
- Spring Cloud Alibaba 2025.1.0
- gRPC 3.1.0
- JWT (jjwt)
- PostgreSQL 18.4
- Redis 8.6.3
- MinIO

## Requirements

- Java 21
- Maven 3.9 or newer
- PostgreSQL 16 or newer (docker-compose ships 18.4)
- Redis 7 or newer (docker-compose ships 8.6.3)
- MinIO (for avatar object storage)
- Docker / Docker Compose (for the local dependency stack)

## Build

```bash
mvn clean package
```

Artifacts land in
`service/user/user/user-rpc/target/user-rpc-<version>.jar`.

Skip tests when you do not need the full gate:

```bash
mvn clean package -DskipTests
```

## Configuration

Credentials and external endpoints live in the untracked `.env`; the
service shape lives in `application.yml`. See `.env.example` for the
variable names.

| variable | role | required |
|---|---|---|
| `POSTGRES_USER` / `POSTGRES_PASSWORD` / `POSTGRES_DB` | database credentials | yes |
| `JWT_SECRET` | JWT signing key | yes |
| `MINIO_ENDPOINT` / `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` | avatar object store | yes (if avatars used) |
| `GRPC_SERVER_PORT` | gRPC listen port | yes |

The full `application.yml` lives in
`service/user/user/user-rpc/src/main/resources/application.yml`.

## Usage / Run

1. Start the local dependencies:
   ```bash
   docker compose up -d postgres redis minio
   ```
2. Fill `.env` from `.env.example`.
3. Start the service:
   ```bash
   mvn spring-boot:run -pl service/user/user/user-rpc
   ```
4. Confirm startup from the banner line:
   ```text
   Started UserRpcApplication in X seconds
   ```
5. Smoke-call the server:
   ```bash
   grpcurl -plaintext localhost:9090 grpc.USER_SERVICE/Login
   ```

The full usage notes and recipes live in [docs/USAGE.md](docs/USAGE.md).

## API

The contracts live as `.proto` files in
`service/user/user/user-proto/src/main/proto/`. The full RPC surface and
error codes live in [docs/USAGE.md](docs/USAGE.md).

Main RPCs:
- `Login(LoginRequest) -> LoginResponse`
- `Register(RegisterRequest) -> RegisterResponse`
- `GetUser(GetUserRequest) -> GetUserResponse`
- `UpdateUser(UpdateUserRequest) -> UpdateUserResponse`
- `DeleteUser(DeleteUserRequest) -> DeleteUserResponse`
- `Logout(LogoutRequest) -> LogoutResponse`
- `UploadAvatar(stream UploadAvatarRequest) -> UploadAvatarResponse`
- `SelectAvatar(SelectAvatarRequest) -> SelectAvatarResponse`
- `GetAvatarHistory(GetAvatarHistoryRequest) -> GetAvatarHistoryResponse`

Error codes live in
`service/user/user/user-rpc/src/main/java/com/rag/user/user/rpc/internal/model/UserExceptions.java`.

## Contributing

The development flow lives in [CONTRIBUTING.md](CONTRIBUTING.md). The
agent-facing protocol entry point is [AGENTS.md](AGENTS.md).