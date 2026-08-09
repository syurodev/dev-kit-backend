# DevKit Sync Backend — Phase A Specification

- Status: Implemented (gateway deployment remains an external prerequisite)
- Date: 2026-08-09
- Backend repository: `/Users/syuro/Workspace/PERSONAL/dev-kit`
- Desktop contract source: `/Users/syuro/Workspace/dev-kit/internal/sync`
- Related plan: `../plans/2026-08-09-sync-backend-phase-a-implementation.md`

## 1. Purpose

Phase A cung cấp production backend tối thiểu để DevKit desktop đồng bộ dữ liệu
đa thiết bị mà server không biết plaintext. Backend xác thực identity đã được
gateway tin cậy, đăng ký thiết bị, nhận encrypted operation, phân xử entity
version, lưu append-only replication log và cho client pull bằng cursor.

Phase A phải khớp wire contract đang chạy thật trong:

- `internal/sync/httptransport.go`;
- `internal/sync/envelope.go`;
- `internal/sync/transport.go`;
- `internal/sync/session.go`;
- reference behavior ở `cmd/mocksyncserver`.

Nếu tài liệu và client Go khác nhau, client Go là nguồn sự thật cho wire
compatibility. Không được sửa client chỉ để che một response/backend contract
không tương thích nếu chưa có quyết định version protocol riêng.

## 2. Goals

1. Cung cấp ba endpoint tương thích sync protocol version `1`:
   - `GET /v1/sync/session`;
   - `POST /v1/sync/push`;
   - `GET /v1/sync/pull`.
2. Giữ zero-knowledge invariant: ciphertext là opaque và backend không có key.
3. Giữ account isolation dựa trên trusted identity, không dựa trên request body.
4. Hỗ trợ device registration và fail closed với device đã revoke.
5. Bảo đảm idempotency, monotonic entity version và chống rollback.
6. Trả conflict metadata đủ để client Git-style resolve; server không auto-merge.
7. Dùng PostgreSQL làm correctness source cho transaction, uniqueness và lock.
8. Giữ domain/application độc lập framework theo Hexagonal Architecture.
9. Có test evidence từ domain đến PostgreSQL và từ Go HTTP client thật.

## 3. Non-goals

Phase A không bao gồm:

- login UI, password, passkey, MFA hoặc GitHub federation configuration trong
  backend; các flow đó thuộc Keycloak;
- refresh token endpoint; desktop/gateway/IdP quản lý token lifecycle;
- device-management UI hoặc self-service revoke API;
- team vault, sharing, organization/role authorization;
- server-side plaintext search, decrypt, merge hoặc content inspection;
- retention, log compaction, backup orchestration hoặc multi-region;
- Redis trong correctness path;
- marketplace/plugin APIs;
- realtime collaboration hoặc push notification;
- admin API public.

Redis có thể bổ sung sau cho rate limit/idempotency cache. PostgreSQL uniqueness,
transaction và advisory/row lock vẫn là nguồn đúng cuối cùng; hệ thống không
được yêu cầu Redis để xử lý đúng một push.

## 4. Fixed architecture decisions

### 4.1 Trust ownership

Production flow:

```text
DevKit desktop
    │ Keycloak access token
    ▼
Gateway
    │ validate Keycloak JWT via issuer/JWKS
    │ strip client-supplied identity headers
    │ mint short-lived gateway identity JWT
    ▼
DevKit Sync Backend
    │ validate gateway JWT signature/issuer/audience/expiry
    ▼
Application use case
```

Keycloak là source trust cho user authentication. Gateway sở hữu external-token
validation. Backend chỉ chấp nhận identity context có chữ ký từ gateway và
không tin `X-User-Id`, `X-Roles`, `account_id` trong body hoặc một header identity
do desktop tự gửi.

Gateway identity JWT tối thiểu có:

| Claim | Requirement |
|---|---|
| `iss` | Gateway issuer cấu hình cố định |
| `aud` | Chứa `devkit-sync-api` |
| `sub` | Stable Keycloak subject của user |
| `iat`, `nbf`, `exp` | NumericDate; gateway JWT có TTL ngắn |
| `upstream_iss` | Keycloak issuer đã được gateway validate |
| `upstream_exp` | Expiry của Keycloak access token gốc |
| `email` | Optional, không dùng làm identity key |
| `preferred_username` | Optional, chỉ là profile metadata |

`GET /session` trả `expires_at` từ `upstream_exp`, không dùng expiry ngắn của
gateway JWT. Mỗi request tiếp theo vẫn phải đi qua gateway và được validate lại.

Backend chỉ cấu hình một trusted gateway issuer/audience trong production.
Local/test có thể dùng test issuer và test signing key, nhưng không có code path
cho phép unsigned JWT hoặc identity header.

### 4.2 Hexagonal Architecture

Project tổ chức theo capability trước:

```text
com.synx.devkit
├── bootstrap
├── identity
│   ├── domain
│   ├── application
│   │   ├── port.in
│   │   ├── port.out
│   │   └── service
│   └── adapter
│       ├── in.web
│       └── out.persistence|security
├── replication
│   ├── domain
│   ├── application
│   │   ├── port.in
│   │   ├── port.out
│   │   └── service
│   └── adapter
│       ├── in.web
│       └── out.persistence|locking
├── audit
└── shared
```

Dependency rules:

- domain không import Spring, Jakarta Persistence, Jackson hoặc adapter;
- inbound adapter chỉ gọi inbound port;
- application service chỉ phụ thuộc domain và port;
- outbound adapter implement outbound port;
- adapter không gọi trực tiếp adapter khác;
- API DTO và persistence entity không phải domain model;
- transaction boundary được khai báo ở application service hoặc một application
  transaction port, không đặt business rule trong controller/repository;
- `bootstrap` là composition root duy nhất biết concrete adapter.

Architecture test phải tự động khóa các rule này.

### 4.3 Persistence and migration

- PostgreSQL 18 là database của service, tách biệt Keycloak database.
- Liquibase là migration owner duy nhất.
- Migration forward-only, được include có thứ tự từ master changelog.
- Persistence dùng Spring JDBC; project không có Hibernate schema-generation.
- Phase A không tạo foreign key theo design hiện tại; application phải validate
  logical relationship và database vẫn phải có unique/check/index constraints.
- UUID primary key dùng PostgreSQL 18 `uuidv7()`.
- Timestamp lưu `timestamptz` UTC.

## 5. Domain model

### 5.1 Identity capability

`Account`

- `id`: internal UUID v7, là `account_id` trên sync protocol;
- `subject`: stable `sub` từ trusted gateway JWT, immutable;
- `primaryEmail`: optional profile metadata;
- `username`: optional profile metadata;
- `createdAt`, `updatedAt`.

`Device`

- `id`: internal UUID v7;
- `accountId`: logical owner;
- `deviceId`: client-stable identifier;
- `status`: `ACTIVE` hoặc `REVOKED`;
- `protocolVersion`;
- `firstSeenAt`, `lastSeenAt`.

Rules:

- account lookup/create dùng `subject`, không dùng email/username;
- cùng `(accountId, deviceId)` chỉ có một device;
- device đầu tiên được bootstrap `ACTIVE` tại session handshake dưới account lock;
- device tiếp theo cần enrollment token một lần do một device `ACTIVE` tạo;
- session với device `REVOKED` trả `403`; session không tự reactivate;
- request push/pull phải check device vẫn active, không chỉ tin session trước đó;
- protocol khác `1` bị từ chối trước business operation.

### 5.2 Replication capability

`ReplicationOperation`

- idempotency key;
- operation: `create`, `update`, `delete`;
- envelope routing metadata;
- opaque ciphertext bytes.

`EntityHead`

- account ID;
- record ID;
- stable record type;
- current entity version;
- head replication sequence;
- head idempotency key.

`ReplicationLogEntry`

- global monotonic `seq` dùng cho account-scoped cursor;
- account/device/record routing columns;
- operation ID, idempotency key, entity version và operation;
- full envelope JSONB để trả lại client;
- SHA-256 content digest tính trên stable length-prefixed wire fields;
- created time.

Server không có domain type cho nội dung note/database/SSH. Ciphertext không
được deserialize thành crypto envelope hoặc plaintext object.

## 6. Persistence model

### 6.1 `accounts`

| Column | Type | Constraint |
|---|---|---|
| `id` | `uuid` | PK, default `uuidv7()` |
| `identity_subject` | `text` | not null, unique |
| `primary_email` | `text` | nullable |
| `username` | `text` | nullable |
| `created_at` | `timestamptz` | not null |
| `updated_at` | `timestamptz` | not null |

### 6.2 `devices`

| Column | Type | Constraint |
|---|---|---|
| `id` | `uuid` | PK, default `uuidv7()` |
| `account_id` | `uuid` | not null, logical reference |
| `device_id` | `text` | not null |
| `status` | `text` | check `active|revoked` |
| `protocol_version` | `integer` | positive |
| `first_seen_at` | `timestamptz` | not null |
| `last_seen_at` | `timestamptz` | not null |

Unique `(account_id, device_id)` và index `(account_id, status)`.

### 6.3 `replication_log`

| Column | Type | Constraint |
|---|---|---|
| `id` | `uuid` | PK, default `uuidv7()` |
| `seq` | `bigint generated always as identity` | not null, unique |
| `account_id` | `uuid` | not null |
| `record_id` | `text` | not null |
| `record_type` | `text` | not null |
| `device_id` | `text` | not null |
| `entity_version` | `bigint` | check `> 0` |
| `operation` | `text` | check `create|update|delete` |
| `idempotency_key` | `text` | not null |
| `operation_id` | `text` | not null |
| `envelope` | `jsonb` | not null; ciphertext remains opaque |
| `content_digest` | `bytea` | SHA-256, not null |
| `created_at` | `timestamptz` | not null |

Constraints/indexes:

- unique `(account_id, operation_id)`;
- unique `(account_id, idempotency_key)`;
- index `(account_id, seq)`;
- index `(account_id, record_id, entity_version)`.

### 6.4 `entity_heads`

| Column | Type | Constraint |
|---|---|---|
| `id` | `uuid` | PK, default `uuidv7()` |
| `account_id` | `uuid` | not null |
| `record_id` | `text` | not null |
| `record_type` | `text` | not null |
| `current_entity_version` | `bigint` | check `> 0` |
| `head_seq` | `bigint` | not null |
| `head_idempotency_key` | `text` | not null |
| `updated_at` | `timestamptz` | not null |

Unique `(account_id, record_id)`.

### 6.5 `audit_events`

| Column | Type | Constraint |
|---|---|---|
| `id` | `uuid` | PK, default `uuidv7()` |
| `request_id` | `text` | not null |
| `account_id` | `uuid` | nullable for pre-auth reject |
| `device_id` | `text` | nullable |
| `event_type` | `text` | not null |
| `detail` | `jsonb` | safe metadata only |
| `occurred_at` | `timestamptz` | not null |

Audit detail không chứa token, full envelope, ciphertext, email, username hoặc
raw exception. Push mutation audit được ghi cùng transaction với accepted log.

## 7. Common HTTP contract

### 7.1 Headers

Mọi endpoint yêu cầu:

```http
Authorization: Bearer <gateway-identity-jwt>
X-DevKit-Device-ID: <device-id>
X-DevKit-Sync-Protocol: 1
Accept: application/json
```

Gateway thay access token bên ngoài bằng gateway identity JWT trước khi request
đến backend. Với `POST`, `Content-Type` phải là `application/json`.

### 7.2 Limits

| Item | Limit |
|---|---|
| Request body | 4 MiB |
| Response body | 4 MiB |
| Push batch | 1–1000 operations |
| Decoded ciphertext per operation | 1 MiB |
| Pull limit | 1–1000 |
| Cursor | 512 characters |
| Access/gateway JWT at edge | 8 KiB |
| Account/device/record/operation/idempotency ID | 256 characters |
| Record type | 128 characters |

Identifier không được rỗng, chứa control character, `/` hoặc `\`. Numeric wire
field mang kiểu Go `uint32` phải nằm trong `1..4294967295`.

JSON request phải reject unknown field, duplicate logical key trong batch,
trailing token và invalid base64. Response chỉ chứa field client đã định nghĩa vì
Go client decode bằng `DisallowUnknownFields`.

### 7.3 Status mapping

| Status | Meaning |
|---|---|
| `200` | Successful session/push/pull |
| `400` | Malformed HTTP/JSON/query |
| `401` | Missing/invalid/expired gateway identity JWT |
| `403` | Revoked device hoặc authenticated identity không được phép |
| `405` | Wrong HTTP method |
| `413` | Body vượt giới hạn |
| `415` | Unsupported content type |
| `422` | Protocol/envelope/version/identifier/business validation failed |
| `429` | Rate limited khi control này được bật |
| `507` | Account đã dùng hết storage/operation quota |
| `500/503` | Safe internal/unavailable response; không lộ stack trace |

Conflict hợp lệ không dùng HTTP `409`; nó nằm trong `200 push` response để client
có thể nhận đồng thời acknowledgements và conflicts.

Error body dùng shape ổn định nhưng desktop Phase A không phụ thuộc nội dung:

```json
{
  "code": "validation_failed",
  "message": "Request is invalid",
  "request_id": "..."
}
```

`message` là public message cố định, không nối raw exception/database/JWT error.

## 8. Session endpoint

### `GET /v1/sync/session`

Flow:

1. Validate gateway identity JWT.
2. Validate protocol header và device ID syntax.
3. Find-or-create account bằng trusted `sub`.
4. Với device đã biết: reject nếu `REVOKED`, nếu active thì update `lastSeenAt`.
5. Với device chưa biết: bootstrap nếu account chưa có device; ngược lại bắt buộc
   `X-DevKit-Enrollment-Token` hợp lệ, chưa dùng và đúng target device.
6. Consume enrollment token atomically rồi tạo device `ACTIVE`.
7. Trả internal account ID, echo đúng device ID và upstream token expiry.

Response:

```json
{
  "account_id": "019...",
  "device_id": "desktop-device-id",
  "expires_at": "2026-08-09T12:00:00Z"
}
```

Response không trả token, Keycloak subject, email, username hoặc roles.

### `POST /v1/sync/devices/enrollments`

Endpoint yêu cầu header của một device đang `ACTIVE` và body:

```json
{
  "target_device_id": "new-desktop-device-id"
}
```

Response trả `enrollment_token`, `target_device_id`, `expires_at`. Token có 256
bit entropy, backend chỉ lưu SHA-256 digest, TTL mặc định 10 phút và một token
chỉ consume được một lần. Token không thể dùng cho target device khác.

Concurrent first session của cùng subject/device phải hội tụ qua unique constraint
và retry bounded; không tạo account/device trùng.

## 9. Push endpoint

### `POST /v1/sync/push`

Request shape:

```json
{
  "operations": [
    {
      "idempotency_key": "...",
      "operation": "create",
      "envelope": {
        "record_id": "...",
        "record_type": "note",
        "key_version": 1,
        "envelope_version": 2,
        "account_id": "...",
        "device_id": "...",
        "protocol_version": 1,
        "operation_id": "...",
        "entity_version": 1,
        "operation": "create",
        "ciphertext": "<base64>"
      }
    }
  ]
}
```

Validation trước transaction:

- batch và body trong limit;
- top-level operation bằng envelope operation;
- envelope version bằng `2`, protocol bằng `1`;
- envelope account bằng authenticated internal account;
- envelope device bằng request device;
- key/entity version dương và trong uint32;
- ciphertext non-empty, decoded size không quá 1 MiB;
- identifier hợp lệ;
- không trùng idempotency key hoặc operation ID trong batch.

### 9.1 Transaction and locking

Một push batch chạy trong một PostgreSQL transaction để invalid operation/version
gap không tạo partial batch commit.

Trước khi process, adapter lấy transaction-scoped advisory lock cho toàn bộ
distinct `(accountId, recordId)` theo thứ tự deterministic. Hash collision chỉ
làm serialize thêm, không làm sai correctness. Sau lock, đọc entity head hiện
tại và process operation theo đúng thứ tự request.

Không dùng Redis lock cho correctness.

### 9.2 Arbitration

Với mỗi operation:

1. Nếu `(account, operation_id)` đã tồn tại:
   - idempotency key và content digest phải giống row đã lưu;
   - nếu giống: replay, thêm request idempotency key vào `sent`;
   - nếu khác: reject `422`, rollback batch.
2. Nếu idempotency key đã thuộc operation khác: reject `422`, rollback batch.
3. Nếu chưa có head:
   - chỉ `entity_version == 1` được accept;
   - operation có thể là create/update/delete vì compaction/local history có thể
     khiến mutation đầu server thấy không phải create.
4. Nếu có head:
   - record type phải giống head, nếu khác reject `422`;
   - `entity_version == head + 1`: fast-forward;
   - `entity_version <= head`: conflict, không append log và không advance head;
   - `entity_version > head + 1`: version gap, reject `422`, rollback batch.
5. Accepted operation:
   - insert replication log;
   - insert/update entity head trong cùng transaction;
   - append idempotency key vào `sent`.

Conflict response:

```json
{
  "id": "...",
  "record_id": "...",
  "record_type": "note",
  "local_change_key": "<request idempotency key>",
  "remote_change_key": "<current head idempotency key>",
  "detected_at": "2026-08-09T12:00:00Z"
}
```

`local_change_key` và `remote_change_key` phải khác nhau. Conflict không được
persist thành plaintext merge state; client sẽ capture remote operation qua pull.

Push response:

```json
{
  "sent_idempotency_keys": ["..."],
  "conflicts": []
}
```

Mỗi input operation xuất hiện tối đa một lần trong `sent` hoặc `conflicts`.

## 10. Pull endpoint

### `GET /v1/sync/pull?cursor=<opaque>&limit=<1..1000>`

- Empty cursor nghĩa là bắt đầu từ sequence `0`.
- Cursor format Phase A: `v1.<base64url-encoded unsigned sequence>`.
- Client coi cursor là opaque; server reject sai prefix, invalid base64, negative,
  overflow hoặc dài hơn 512 ký tự.
- Query luôn scope `WHERE account_id = authenticatedAccount AND seq > cursor`.
- Order bắt buộc `seq ASC`, limit theo request và response không quá 4 MiB.
- Trả cả operation từ chính source device; client chịu trách nhiệm apply/replay.
- Envelope được dựng lại đúng field/value đã lưu; ciphertext bytes không đổi.
- Nếu không có row mới, `next_cursor` giữ nguyên cursor đã decode/re-encode.
- Nếu có row, `next_cursor` là sequence cuối cùng đã trả.

Response:

```json
{
  "account_id": "...",
  "device_id": "<request device>",
  "protocol_version": 1,
  "operations": [],
  "next_cursor": "v1.AAAAAAAAAAA"
}
```

Operation bên trong có thể mang source `device_id` khác request device. Account
và protocol của mọi operation phải khớp authenticated session.

## 11. Zero-knowledge and security invariants

1. Backend không nhận hoặc lưu Master Password, KEK, DEK, recovery key.
2. Backend không gọi decrypt và không parse nested crypto envelope trong
   `ciphertext`; chỉ base64 decode để enforce byte-size cap.
3. `account_id` lấy từ trusted subject mapping; body chỉ được dùng để đối chiếu.
4. Mọi persistence query dùng explicit account scope.
5. Device status được check trên mỗi session/push/pull.
6. Gateway JWT issuer, audience, signature, `nbf`, `exp` phải validate fail closed.
7. Redirect, CORS và public exposure thuộc gateway; backend không được expose
   trực tiếp ra Internet trong production network.
8. Database application role không phải superuser và không sở hữu Keycloak DB.
9. Log/audit không chứa Authorization header, JWT claims ngoài internal account
   ID, email, username, envelope, ciphertext hoặc raw request body.
10. Request/response/batch/ciphertext đều bounded trước allocation lớn.
11. Idempotency và entity monotonicity được database constraint + transaction
    bảo vệ, không chỉ check trong Java memory.
12. Clock lấy qua application `Clock` dependency để test expiry/audit ổn định.

## 12. Observability

Mỗi request có `request_id` do gateway truyền sau khi overwrite hoặc backend tạo
mới. Structured log tối thiểu gồm request ID, route, status, latency, internal
account ID nếu đã auth, device ID đã sanitize và aggregate count.

Metrics Phase A:

- request count/latency theo route và status;
- session accepted/denied;
- push accepted/replayed/conflicted/rejected operation count;
- pull operation count và response bytes;
- database transaction/lock failure count.

Không dùng record ID, account ID hoặc device ID làm metric label vì cardinality.

## 13. Configuration

Secrets chỉ đến từ environment/secret manager, không commit. Configuration groups:

- datasource URL/user/password;
- Liquibase enable/changelog path;
- trusted gateway issuer URI hoặc JWKS URI;
- required audience `devkit-sync-api`;
- expected upstream issuer (Keycloak realm);
- request/body/ciphertext limits với secure defaults không vượt client contract;
- server bind/port và management endpoint exposure;
- logging/redaction mode.

Production startup phải fail nếu issuer/audience/datasource secret thiếu. Test
profile dùng ephemeral PostgreSQL và test signing key, không dùng `.env` thật.

## 14. Acceptance criteria

Phase A chỉ đạt `Implemented` khi tất cả điều kiện sau pass:

1. Architecture tests chứng minh domain/application không phụ thuộc adapter/framework.
2. Liquibase migrate thành công trên PostgreSQL 18 từ database trống và chạy lại
   không tạo drift.
3. Session concurrent create không tạo duplicate account/device.
4. Invalid/expired/wrong-audience gateway JWT bị `401`.
5. Revoked device bị `403` trên session/push/pull.
6. Account A không thể push envelope account B hoặc pull log account B.
7. Push unit/integration tests cover first version, fast-forward, replay, reused
   key mismatch, stale conflict, version gap và record-type mismatch.
8. Hai concurrent device push cùng next version cho một record cho kết quả đúng
   một accepted, một conflict.
9. Version gap rollback toàn batch; không có partial log/head mutation.
10. Pull cursor order/limit/account isolation/empty page/4 MiB cap đều pass.
11. Logs và audit test không tìm thấy token, ciphertext hoặc fixture secret.
12. Backend test suite và static/architecture checks pass.
13. Go `internal/sync.HTTPTransport` thật hoàn tất session/push/pull/conflict với
    backend qua gateway-compatible test identity.
14. Hai desktop device hội tụ trên note và secret item qua backend thật.

## 15. Deferred Phase B

- refresh-token UX và reconnect automation;
- device list/rename/revoke API + UI;
- Keycloak admin/event integration cho global session revocation;
- Redis-backed distributed rate limit và cache optimization;
- retention/compaction/backup/restore drills;
- JetBrains-style three-pane conflict UI;
- multi-region and disaster recovery;
- team/account sharing model.
