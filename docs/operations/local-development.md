# Local development

## Phạm vi

Backend dùng PostgreSQL của service và Keycloak đã chạy bằng Docker. Gateway là
process riêng: desktop gửi Keycloak access token tới gateway, còn backend chỉ
nhận internal identity JWT do gateway ký.

Không chạy `docker compose down -v` trong workflow thông thường vì lệnh đó xóa
volume database. Automated test không dùng database local; Testcontainers tạo
PostgreSQL 18 riêng rồi tự hủy sau suite.

## 1. Khởi động dependency

Tạo `.env` cá nhân từ `.env.example`, điền secret local và giữ file ngoài Git.
Sau đó:

```bash
docker compose config --quiet
docker compose up -d
docker compose ps
```

Compose tách `keycloak-db` và `devkit-db` bằng database, user, volume và network
riêng. Backend không kết nối vào Keycloak database.

## 2. Cấu hình backend

Spring Boot không tự đọc file `.env`. Export biến bằng shell hoặc IDE run
configuration. Ví dụ macOS/zsh:

```bash
set -a
source .env
set +a
export DEVKIT_DB_URL="jdbc:postgresql://localhost:${DEVKIT_DB_PORT}/${DEVKIT_DB_NAME}"
export DEVKIT_GATEWAY_ISSUER="https://gateway.local"
export DEVKIT_GATEWAY_JWK_SET_URI="https://gateway.local/.well-known/jwks.json"
export DEVKIT_GATEWAY_AUDIENCE="devkit-sync-api"
export DEVKIT_KEYCLOAK_ISSUER="http://localhost:8081/realms/devkit"
```

Không copy các giá trị thật vào README, test fixture hoặc log build.

## 3. Chạy application

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

## 4. Chạy test và build

```bash
./gradlew clean check bootJar
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

## Troubleshooting an toàn

- Startup fail datasource: kiểm tra tên biến và URL, không in password.
- Startup fail JWT: kiểm tra issuer/audience/JWKS/upstream issuer bằng metadata,
  không paste token vào issue hoặc chat log.
- `401`: gateway JWT thiếu/sai chữ ký, issuer, audience hoặc expiry.
- `403`: device chưa session, đã revoke hoặc protocol registration không khớp.
- `413`: request vượt 4 MiB; chia batch ở client, không tăng server limit riêng.
- Migration fail: giữ changeset đã áp dụng bất biến; thêm changeset mới để sửa.

Backup/restore, retention và disaster-recovery drill chưa thuộc Phase A.
