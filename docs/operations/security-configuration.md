# Security configuration

## Trust boundary

Backend không validate trực tiếp token desktop nhận từ Keycloak. Gateway phải:

1. validate chữ ký, issuer, audience và expiry của Keycloak access token;
2. xóa `Authorization` và mọi identity header do client gửi;
3. ký identity JWT ngắn hạn mới;
4. đặt JWT mới vào `Authorization: Bearer ...` khi proxy tới backend;
5. chỉ proxy qua private network/TLS phù hợp với môi trường.

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

Không trỏ `DEVKIT_GATEWAY_JWK_SET_URI` thẳng tới Keycloak JWKS. Làm vậy phá vỡ
ownership boundary và khiến backend có nguy cơ chấp nhận trực tiếp external
token ngoài gateway policy.

## Database role

`DEVKIT_DB_USER` chỉ cần quyền connect/schema/table/sequence trên service
database. User này không nên là superuser, không dùng chung credential và không
có quyền vào Keycloak database. PostgreSQL uniqueness, transaction và advisory
lock là correctness source; Redis không cần cho Phase A.

Liquibase hiện chạy bằng cùng datasource lúc startup. Nếu production tách
migration role, chạy migration trong deployment job rồi cấp application role
chỉ quyền DML/sequence cần thiết.

## Network và observability

- Backend mặc định bind loopback; khi chạy container, chỉ expose trên private
  network mà gateway truy cập được.
- `/actuator/health` và `/actuator/info` public nhưng không trả config detail;
  endpoint khác yêu cầu authentication.
- Audit chỉ lưu internal account/device ID, request ID và aggregate count.
- Không log Authorization, JWT, email, username, raw request, envelope hoặc
  ciphertext.
- Request body giới hạn 4 MiB, ciphertext 1 MiB, JWT 8 KiB, batch/pull 1000.

Rate limit phân tán, key rotation runbook, backup/restore và revoke-management UI
là follow-up. Việc đó không thay đổi nguyên tắc PostgreSQL vẫn là correctness
source và backend vẫn chỉ tin gateway-signed identity.
