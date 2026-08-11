# Local development

## Phạm vi

Backend dùng PostgreSQL riêng và Gateway là process riêng: desktop gửi Keycloak
access token tới Gateway, còn backend chỉ nhận internal identity JWT do Gateway
ký. Compose chạy đầy đủ IdP, database, migration và application service nhưng
chỉ publish Keycloak, PostgreSQL development port và Gateway trên `127.0.0.1`;
API chỉ nằm trên private network.

Không chạy `docker compose down -v` trong workflow thông thường vì lệnh đó xóa
volume database. Automated test không dùng database local; Testcontainers tạo
PostgreSQL 18 riêng rồi tự hủy sau suite.

## 1. Khởi động dependency

Tạo `.env` cá nhân từ `.env.example`, điền secret local và giữ file ngoài Git.
Tạo RSA keypair local một lần; script từ chối overwrite key hiện có:

```bash
./scripts/generate-gateway-keypair.sh
```

Sau đó:

```bash
docker compose config --quiet
docker compose up -d --build
docker compose ps
```

Compose tách `keycloak-db` và `devkit-db` bằng database, user, volume và network
riêng. Backend không kết nối vào Keycloak database. Redis (`redis`) chạy luôn
trên `sync-private-network` cho API và Gateway; đặt `DEVKIT_REDIS_PASSWORD` trong
`.env` (Compose inject `DEVKIT_REDIS_HOST=redis`, port `6379`). Redis không
publish port ra host — chỉ dùng trong stack Compose.


Lần đầu Keycloak start với volume mới, `keycloak/realm-devkit.json` được
import thành realm `devkit`. Import không tạo user. Tạo user local qua Admin
Console nếu cần kiểm thử login. Nếu realm `devkit` đã tồn tại, Keycloak không
ghi đè cấu hình đang có. Chạy helper idempotent sau khi Keycloak healthy để áp
brute-force protection, password policy, token TTL và default TOTP action mà
không xóa database volume:

```bash
./scripts/apply-keycloak-security.sh
```

Compose chạy role-init và Liquibase bằng database owner trước, rồi khởi động API
bằng DML-only role. Với `.env` cũ, Compose có fallback để không làm mất volume;
nên chuyển sang bốn biến `DEVKIT_DB_ADMIN_*` và `DEVKIT_DB_APP_*` riêng.

## 2. Cấu hình backend

Spring Boot không tự đọc file `.env`. Export biến bằng shell hoặc IDE run
configuration. Ví dụ macOS/zsh:

```bash
set -a
source .env
set +a
export DEVKIT_DB_URL="jdbc:postgresql://localhost:${DEVKIT_DB_PORT}/${DEVKIT_DB_NAME}"
export DEVKIT_DB_USER="$DEVKIT_DB_APP_USER"
export DEVKIT_DB_PASSWORD="$DEVKIT_DB_APP_PASSWORD"
export DEVKIT_GATEWAY_ISSUER="https://gateway.local"
export DEVKIT_GATEWAY_JWK_SET_URI="https://gateway.local/.well-known/jwks.json"
export DEVKIT_GATEWAY_AUDIENCE="devkit-sync-api"
export DEVKIT_KEYCLOAK_ISSUER="http://localhost:8081/realms/devkit"
export DEVKIT_REDIS_HOST="localhost"
export DEVKIT_REDIS_PORT="6379"
export DEVKIT_REDIS_PASSWORD="$DEVKIT_REDIS_PASSWORD"
```

Khi chạy API/Gateway ngoài Docker, cần Redis reachable từ host (ví dụ container
riêng hoặc `docker run` loopback); stack Compose đã cấp Redis nội bộ cho cả hai
service.

Không copy các giá trị thật vào README, test fixture hoặc log build.

## 3. Chạy application ngoài Docker

```bash
./gradlew bootRun
```

Mặc định backend bind `127.0.0.1:8080`. Có thể đổi bằng
`DEVKIT_SERVER_ADDRESS` và `DEVKIT_SERVER_PORT`. Khi application khởi động,
Liquibase chạy các changeset còn thiếu theo thứ tự master changelog.

Health check public không trả chi tiết database/config:

```bash
curl --fail http://127.0.0.1:8080/actuator/health
```

`/actuator/metrics` cần authenticated gateway JWT. Không expose trực tiếp
backend ra Internet; gateway là public entrypoint.

Ở terminal khác, cấu hình Gateway và chạy:

```bash
export DEVKIT_BACKEND_URL="http://127.0.0.1:8080"
export DEVKIT_KEYCLOAK_JWK_SET_URI="http://localhost:8081/realms/devkit/protocol/openid-connect/certs"
export DEVKIT_KEYCLOAK_AUDIENCE="devkit-sync-gateway"
export DEVKIT_GATEWAY_SIGNING_PRIVATE_KEY_PATH="$PWD/.local/gateway-keys/private.pem"
export DEVKIT_GATEWAY_SIGNING_PUBLIC_KEY_PATH="$PWD/.local/gateway-keys/public.pem"
./gradlew :gateway:bootRun
```

Gateway bind loopback `127.0.0.1:8082` khi chạy trực tiếp. `/.well-known/jwks.json`
và health là public; chỉ `/v1/sync/**` được route và bắt buộc bearer token.

## 4. Chạy test và build

```bash
./gradlew clean check bootJar
./gradlew :gateway:check :gateway:bootJar
```

Suite dùng PostgreSQL `postgres:18-alpine`, JWT RSA/JWKS thật và gọi contract
test trong checkout desktop mặc định tại `../../dev-kit`. Nếu workspace khác,
đặt `DEVKIT_DESKTOP_REPO=/absolute/path/to/desktop-repo`. Khi checkout desktop
không tồn tại, Java suite vẫn chạy nhưng cross-repo gate được skip có lý do.

Để chạy gate Go trực tiếp, backend phải đang chạy và token phải là JWT hợp lệ:

```bash
DEVKIT_BACKEND_E2E_URL=http://127.0.0.1:8080 \
DEVKIT_BACKEND_E2E_TOKEN='<signed-gateway-jwt>' \
go test ./internal/sync -run '^TestBackendContractE2E$' -count=1
```

## 5. Deployed sync matrix (Phase 3 closure)

Gate G1 trong desktop repo (`cmd/synce2e`) kiểm tra pull/apply đa loại vault
item (`note`, `db-profile`, `ssh-profile`) qua Gateway Compose. Test chỉ chạy
khi export hai biến môi trường; thiếu biến thì skip, có biến mà stack sai thì
fail.

### Khởi động stack

```bash
docker compose up -d --build
```

Đợi Gateway healthy:

```bash
curl --fail http://127.0.0.1:8082/actuator/health
```

### Lấy bearer token (Keycloak realm `devkit`)

Gateway chấp nhận Keycloak access token cho audience `devkit-sync-gateway`.
Không dán token thật vào doc, commit hay log.

Realm import mặc định không tạo user. Tạo user test qua Admin Console
(`http://localhost:8081`) hoặc Admin API. Nếu realm local tắt self-registration
và direct grant, chỉ dùng user/password đã tạo thủ công trong `.env` cá nhân
(không commit).

Lấy access token bằng password grant qua client `devkit-desktop` (mapper thêm
audience `devkit-sync-gateway`; thay placeholder bằng giá trị local):

```bash
export DEVKIT_E2E_USERNAME='<local-test-user>'
export DEVKIT_E2E_PASSWORD='<local-test-password>'
export DEVKIT_DEPLOYED_E2E_TOKEN="$(
  curl --fail --silent --show-error \
    -X POST "http://localhost:8081/realms/devkit/protocol/openid-connect/token" \
    -H 'Content-Type: application/x-www-form-urlencoded' \
    -d "grant_type=password" \
    -d "client_id=devkit-desktop" \
    -d "username=${DEVKIT_E2E_USERNAME}" \
    -d "password=${DEVKIT_E2E_PASSWORD}" \
  | jq -r '.access_token'
)"
export DEVKIT_DEPLOYED_E2E_URL=http://127.0.0.1:8082
```

Nếu client `devkit-desktop` chưa bật direct access grants, bật tạm trên
client local hoặc dùng flow phù hợp với cấu hình realm (ví dụ device code /
authorization code qua browser) rồi export token vào
`DEVKIT_DEPLOYED_E2E_TOKEN`.

### Chạy matrix

Từ checkout desktop (worktree Phase 3 hoặc repo chính):

```bash
go test ./cmd/synce2e/ -count=1
```

Chỉ gate G1:

```bash
go test ./cmd/synce2e/ -run '^TestG1' -count=1 -timeout 5m
```

Kỳ vọng: `TestG1MultiEntityPullApply` PASS khi stack healthy và token còn hạn.

## Troubleshooting an toàn

- Startup fail datasource: kiểm tra tên biến và URL, không in password.
- Startup fail JWT: kiểm tra issuer/audience/JWKS/upstream issuer bằng metadata,
  không paste token vào issue hoặc chat log.
- `401`: gateway JWT thiếu/sai chữ ký, issuer, audience hoặc expiry.
- `403`: device chưa session, đã revoke hoặc protocol registration không khớp.
- `429`: vượt rate limit; tôn trọng `Retry-After`, không retry tight loop.
- `507`: account hết operation/byte quota; cần nâng quota hoặc compaction policy.
- `413`: request vượt 4 MiB; chia batch ở client, không tăng server limit riêng.
- Migration fail: giữ changeset đã áp dụng bất biến; thêm changeset mới để sửa.

Backup/restore và disaster-recovery drill chưa thuộc Phase A. Audit retention đã
có; replication log không tự xóa cho tới khi có safe compaction checkpoint.
