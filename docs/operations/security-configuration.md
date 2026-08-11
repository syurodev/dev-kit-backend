# Security configuration

## Trust boundary

Backend không validate trực tiếp token desktop nhận từ Keycloak. Gateway phải:

1. validate chữ ký, issuer, audience và expiry của Keycloak access token;
2. xóa `Authorization` và mọi identity header do client gửi;
3. ký identity JWT ngắn hạn mới;
4. đặt JWT mới vào `Authorization: Bearer ...` khi proxy tới backend;
5. chỉ proxy qua private network/TLS phù hợp với môi trường.

Các bước này được implement trong subproject `gateway/` bằng Spring Cloud
Gateway Server Web MVC và Spring Security Resource Server. Gateway chỉ route
`/v1/sync/**`; path khác deny mặc định, ngoại trừ health/info và public JWKS.

Backend không tin `X-User-Id`, `X-Roles` hoặc `account_id` trong request body.
Account nội bộ luôn được ánh xạ từ claim `sub` đã ký.

## Gateway JWT contract

JWT phải được ký bằng key xuất bản tại `DEVKIT_GATEWAY_JWK_SET_URI` và có:

- `iss` đúng `DEVKIT_GATEWAY_ISSUER`;
- `aud` chứa `DEVKIT_GATEWAY_AUDIENCE` (mặc định `devkit-sync-api`);
- `sub` là stable Keycloak subject, không dùng email làm identity;
- `iat`, `nbf`, `exp` hợp lệ;
- `upstream_iss` đúng `DEVKIT_KEYCLOAK_ISSUER`;
- `upstream_exp` còn hiệu lực;
- `email`, `preferred_username` là metadata optional.

`GET /v1/sync/session` trả `expires_at` từ `upstream_exp`. Gateway JWT có thể có
TTL ngắn hơn vì gateway ký lại identity cho từng request.

Gateway nạp private key PKCS#8 và public key X.509 từ read-only file path khi
startup; thiếu key hoặc key không khớp làm startup fail. Local helper ghi key vào
`.local/` đã gitignore. Production phải mount key từ secret manager, giữ `kid`
ổn định trong một rotation window và không bake private key vào image.

Không trỏ `DEVKIT_GATEWAY_JWK_SET_URI` thẳng tới Keycloak JWKS. Làm vậy phá vỡ
ownership boundary và khiến backend có nguy cơ chấp nhận trực tiếp external
token ngoài gateway policy.

## Database role

Compose dùng `DEVKIT_DB_ADMIN_USER` cho migration job và tạo
`DEVKIT_DB_APP_USER` chỉ có CONNECT, schema usage, table DML và sequence usage.
API tắt Liquibase và chỉ chạy bằng application role; role này không có
superuser, createdb hoặc createrole. Hai role không dùng chung với Keycloak.

`devkit-db-role-init` tạo/cập nhật DML role và default privilege; sau đó
`devkit-migrate` chạy Liquibase bằng owner trước khi API được phép start.
PostgreSQL uniqueness, transaction, advisory lock và atomic quota reservation
là correctness source; Redis không cần cho Phase A. Phase B device-revoke
(design) thêm Redis **chỉ** làm edge denylist TTL 60s sau revoke — không đưa
Redis vào correctness path của push/pull.

## Device enrollment và abuse protection

- Thiết bị đầu tiên của account được bootstrap dưới account registration lock.
- Thiết bị tiếp theo cần token 256-bit do một device active tạo qua
  `POST /v1/sync/devices/enrollments`.
- Token lưu dưới dạng SHA-256, gắn với target device, hết hạn sau 10 phút và bị
  xóa atomically khi sử dụng.
- Gateway giới hạn request theo source IP trước JWT parsing, theo authenticated
  subject sau validation, đồng thời giới hạn request concurrency.
- Backend reserve operation/byte quota trong cùng transaction với replication
  append. Audit metadata mặc định được xóa sau 90 ngày; replication data dùng
  quota backpressure và chưa tự xóa khi chưa có safe compaction checkpoint.

## Network và observability

- Backend mặc định bind loopback; khi chạy container, chỉ expose trên private
  network mà gateway truy cập được.
- Gateway nối Keycloak network và private sync network; API không nối Keycloak
  network và không publish host port trong Compose.
- `/actuator/health` và `/actuator/info` public nhưng không trả config detail;
  endpoint khác yêu cầu authentication.
- Audit chỉ lưu internal account/device ID, request ID và aggregate count.
- Không log Authorization, JWT, email, username, raw request, envelope hoặc
  ciphertext.
- Request body giới hạn 4 MiB, ciphertext 1 MiB, JWT 8 KiB, batch/pull 1000.
- API/Gateway chạy non-root, read-only root filesystem, drop Linux capabilities
  và dùng tmpfs cho `/tmp`; host development ports chỉ bind `127.0.0.1`.

Rate limit hiện là per-instance để không thêm Redis quá sớm. Deployment nhiều
gateway cần thêm distributed limiter ở trusted edge. Key rotation runbook,
backup/restore và revoke-management UI polish vẫn là follow-up. Self-service
device list/revoke API + Redis device denylist TTL 60s: xem
`docs/specs/2026-08-11-sync-backend-phase-b-device-revoke.md`.

## Keycloak image patch policy

Compose build `Dockerfile.keycloak` từ image Keycloak chính thức. Image dẫn xuất
overlay các patch release đã có cho dependency runtime HIGH severity và kiểm tra
checksum ngay lúc build; standalone Admin CLI và SQL Server JDBC driver không dùng
được loại bỏ. Các identity-brokering API và Twitter broker cũ cũng bị disable vì
DevKit không dùng chúng. Khi nâng Keycloak, cần quét lại image và ưu tiên xóa
overlay ngay khi image chính thức đã chứa dependency tương đương hoặc mới hơn.

`Dockerfile.postgres` vẫn dựa trên image PostgreSQL 18 Alpine chính thức, sau đó
áp package security updates và rebuild đúng gosu 1.19 bằng Go toolchain đã vá.
Xóa lớp dẫn xuất này khi image PostgreSQL upstream đã cập nhật cả Alpine package
và gosu binary.
