# DevKit Sync Backend — Phase A Implementation Plan

- Date: 2026-08-09
- Spec: `../specs/2026-08-09-sync-backend-phase-a.md`
- Backend repository: `/Users/syuro/Workspace/PERSONAL/dev-kit`
- Desktop repository: `/Users/syuro/Workspace/dev-kit`
- Delivery style: vertical slice, test-first, review after every task

## Execution status

Implemented on 2026-08-09:

- Tasks 0–11: architecture foundation, bounded wire policy, PostgreSQL 18
  schema, gateway JWT, identity/session, replication contract, arbitration,
  atomic push and account-scoped pull;
- Task 12: safe audit events, request correlation and Actuator health/HTTP
  observations. Fine-grained business metrics remain an optional follow-up;
- Task 13: signed-JWT, isolation, revocation, boundary, rollback and concurrency
  integration coverage;
- Task 14: real Go `HTTPAuthenticator`/`HTTPTransport` contract E2E against a
  random-port backend with PostgreSQL 18 and signed gateway-compatible JWT;
- Task 15: local-development/security operations docs and release gates.

Implementation uses Spring JDBC rather than the JPA adapter names originally
suggested below. This keeps SQL locking, JSONB and transaction behavior explicit
while preserving the same outbound ports and Hexagonal dependency direction.

## 1. Execution rules

1. Spec là contract; nếu implementation buộc phải đổi contract, dừng task và cập
   nhật/duyệt spec trước, không âm thầm drift.
2. Không xóa/recreate Docker volume hiện có. Chỉ kiểm tra read-only trước khi chạy
   migration hoặc integration test vào database riêng.
3. Không dùng `.env` production/local thật trong automated test. Test dùng
   Testcontainers và test signing key.
4. Không log/print `.env`, token, JWT, ciphertext hoặc request body.
5. Mỗi task bắt đầu bằng failing test phù hợp, implement tối thiểu, chạy task gate,
   sau đó chạy `./gradlew test` trước checkpoint.
6. Domain/application package không import Spring/JDBC/Jackson; architecture test
   phải fail ngay khi vi phạm.
7. Liquibase changeset đã merge không sửa lại; thay đổi tiếp theo thêm changeset.
8. PostgreSQL là correctness source. Không thêm Redis vào Phase A critical path.
9. Không sửa Go client contract để làm backend test pass nếu backend response đang
   sai protocol version `1`.
10. Không coi mock-only test là production contract evidence; final gate phải chạy
    Go HTTP transport thật với backend.

## 2. Target dependency direction

```text
adapter/in/web ──► application/port/in ◄── application/service
                                                    │
                                                    ▼
                                                  domain
                                                    │
application/service ──► application/port/out ◄── adapter/out/*

bootstrap ──► concrete wiring, Spring Security, transactions, configuration
```

Suggested root packages:

```text
com.synx.devkit.bootstrap
com.synx.devkit.identity
com.synx.devkit.replication
com.synx.devkit.audit
com.synx.devkit.shared
```

## 3. Task sequence

### Task 0 — Baseline and environment audit

Goal: xác nhận scaffold và dependency đang chạy mà không thay đổi external state.

Read-only checks:

```bash
git status --short --branch
./gradlew test
docker compose config --quiet
docker compose ps
```

Inspect without printing secrets:

- Java toolchain resolves version 25;
- Spring Boot application context starts;
- `devkit-db` và Keycloak containers healthy nếu integration environment đang bật;
- service DB và Keycloak DB là container/volume/user/network riêng;
- current branch/worktree và user changes được ghi nhận trước edit.

Do not:

- run `docker compose down -v`;
- recreate database volume;
- print `.env`;
- run Liquibase against the user's normal database in this task.

Gate:

- baseline test result được ghi lại;
- mọi baseline failure được phân loại trước khi bắt đầu feature code.

Known baseline tại thời điểm viết plan: `./gradlew test` compile thành công nhưng
`DevKitApplicationTests.contextLoads()` fail khi tạo datasource vì test profile
chưa có datasource/Testcontainers configuration. Task 1/3 phải thay baseline này
bằng test configuration cô lập; không trỏ test vào database local đang chạy.

### Task 1 — Build dependencies, configuration skeleton and architecture guard

Goal: tạo test/build foundation và khóa Hexagonal dependency direction trước khi
business package tăng lên.

Modify:

- `build.gradle`;
- `src/main/resources/application.yaml`.

Add dependencies:

- `spring-boot-starter-security`;
- `spring-boot-starter-oauth2-resource-server`;
- `spring-boot-starter-validation`;
- `spring-boot-starter-actuator`;
- `spring-security-test`;
- `spring-boot-testcontainers`;
- Testcontainers JUnit/PostgreSQL;
- ArchUnit JUnit 5.

Add:

- `src/test/java/com/synx/devkit/architecture/ArchitectureRulesTest.java`;
- `src/main/java/com/synx/devkit/bootstrap/configuration/ClockConfiguration.java`;
- `src/main/java/com/synx/devkit/shared/domain/SyncProtocol.java`;
- `src/main/java/com/synx/devkit/shared/domain/WireLimits.java`;
- `src/test/resources/application-test.yaml`.

Architecture rules:

- `..domain..` imports JDK/domain only;
- `..application..` không import `..adapter..`, Spring, Jakarta Persistence hoặc
  Jackson;
- inbound adapters không import outbound adapter packages;
- persistence/security adapter không import inbound adapter;
- bootstrap có thể depend vào mọi layer để wire;
- package cycle bị cấm.

Configuration defaults:

- protocol `1`;
- replication envelope `2`;
- request/response 4 MiB;
- ciphertext 1 MiB;
- batch/pull 1000;
- cursor 512;
- fail startup ngoài test nếu datasource/gateway issuer/audience thiếu.

Tests first:

- architecture fixture chứng minh rule bắt được forbidden dependency;
- configuration binding reject limit vượt client contract;
- fixed protocol/envelope version không bị override bằng environment.

Gate:

```bash
./gradlew test --tests '*ArchitectureRulesTest'
./gradlew test
```

Review checkpoint: dependency có thực sự cần thiết; không thêm library chỉ vì dự
kiến Phase B.

### Task 2 — Shared wire policy, safe errors and bounded JSON

Goal: chặn malformed/oversized request ở inbound boundary trước khi use case hoặc
persistence allocation lớn.

Add framework-free contract helpers:

- `src/main/java/com/synx/devkit/shared/domain/SyncProtocol.java`;
- `src/main/java/com/synx/devkit/shared/domain/WireLimits.java`;
- `src/main/java/com/synx/devkit/shared/domain/SyncIdentifier.java`;
- `src/main/java/com/synx/devkit/shared/error/DomainException.java`;
- `src/main/java/com/synx/devkit/shared/error/ValidationException.java`;
- `src/main/java/com/synx/devkit/shared/error/AuthenticationException.java`;
- `src/main/java/com/synx/devkit/shared/error/ForbiddenException.java`.

Add inbound web components:

- `src/main/java/com/synx/devkit/shared/adapter/in/web/ApiErrorResponse.java`;
- `src/main/java/com/synx/devkit/shared/adapter/in/web/ApiExceptionHandler.java`;
- `src/main/java/com/synx/devkit/shared/adapter/in/web/RequestIdFilter.java`;
- `src/main/java/com/synx/devkit/shared/adapter/in/web/BoundedRequestFilter.java`;
- `src/main/java/com/synx/devkit/bootstrap/configuration/JacksonConfiguration.java`.

Behavior:

- reject unknown JSON field và duplicate JSON key;
- reject body >4 MiB cho cả Content-Length và chunked request;
- public error message cố định, có request ID;
- raw exception không đi vào response;
- identifier validation mirror Go: required, max length, no control, no slash.

Tests first:

- `SyncIdentifierTest` với slash/backslash/control/boundary length;
- `BoundedRequestFilterTest` cho Content-Length và chunked body;
- `ApiExceptionHandlerTest` chứng minh error không lộ fixture secret;
- Jackson test cho unknown field, duplicate key và trailing JSON.

Gate:

```bash
./gradlew test --tests '*SyncIdentifierTest' --tests '*BoundedRequestFilterTest' --tests '*ApiExceptionHandlerTest'
```

### Task 3 — Liquibase schema foundation

Goal: tạo schema Phase A đầy đủ và test migration trên PostgreSQL 18 thật.

Add:

- `src/main/resources/db/changelog/db.changelog-master.yaml`;
- `src/main/resources/db/changelog/changes/001-create-accounts.yaml`;
- `src/main/resources/db/changelog/changes/002-create-devices.yaml`;
- `src/main/resources/db/changelog/changes/003-create-replication-log.yaml`;
- `src/main/resources/db/changelog/changes/004-create-entity-heads.yaml`;
- `src/main/resources/db/changelog/changes/005-create-audit-events.yaml`;
- `src/test/java/com/synx/devkit/database/DatabaseMigrationIT.java`;
- `src/test/java/com/synx/devkit/database/DatabaseConstraintsIT.java`;

Schema must match spec:

- UUID v7 PK;
- no foreign key;
- unique subject/device/operation/idempotency/head constraints;
- check device status, operation and positive version;
- global identity sequence + account/sequence index;
- JSONB envelope and bytea digest;
- explicit UTC timestamps.

Tests:

- migrate empty PostgreSQL 18;
- rerun migration idempotently;
- application startup does not mutate schema outside Liquibase;
- every unique/check constraint rejects an invalid fixture;
- application DB role cannot access Keycloak DB is an environment smoke check,
  not a Testcontainers unit test.

Gate:

```bash
./gradlew test --tests '*DatabaseMigrationIT' --tests '*DatabaseConstraintsIT'
```

Review checkpoint: changeset ID/order/index names; no migration changes after
checkpoint except a new numbered changeset.

### Task 4 — Gateway identity JWT security adapter

Goal: backend chỉ tạo trusted principal từ gateway-signed JWT đã verify đầy đủ.

Add:

- `src/main/java/com/synx/devkit/bootstrap/security/GatewayJwtProperties.java`;
- `src/main/java/com/synx/devkit/bootstrap/security/SecurityConfiguration.java`;
- `src/main/java/com/synx/devkit/bootstrap/security/RequiredAudienceValidator.java`;
- `src/main/java/com/synx/devkit/bootstrap/security/UpstreamIdentityValidator.java`;
- `src/main/java/com/synx/devkit/identity/adapter/in/security/GatewayIdentity.java`;
- `src/main/java/com/synx/devkit/identity/adapter/in/security/GatewayIdentityResolver.java`;
- `src/test/java/com/synx/devkit/security/GatewayJwtSecurityIT.java`.

Validate:

- gateway JWT signature/JWKS;
- exact issuer;
- audience contains `devkit-sync-api`;
- normal `nbf`/`exp`;
- non-empty `sub`;
- exact expected `upstream_iss`;
- `upstream_exp` exists and remains in future;
- gateway token length bounded at edge.

Do not:

- accept `X-User-Id`/`X-Roles`;
- fall back to unsigned or decoded-only JWT;
- accept both direct Keycloak and gateway issuer in one production profile;
- persist raw JWT or all claims.

Tests first:

- valid signed token accepted;
- wrong signature/issuer/audience/upstream issuer rejected `401`;
- expired gateway token and expired upstream token rejected;
- forged identity headers do not affect resolved subject;
- logs do not contain JWT fixture.

Gate:

```bash
./gradlew test --tests '*GatewayJwtSecurityIT'
```

External prerequisite: gateway must validate the original Keycloak access token,
strip identity headers and replace Authorization with the internal JWT before
proxying. Gateway implementation is not hidden inside this backend task.

### Task 5 — Identity domain and persistence ports

Goal: implement account/device business rule independently HTTP.

Add domain:

- `identity/domain/model/Account.java`;
- `identity/domain/model/IdentitySubject.java`;
- `identity/domain/model/Device.java`;
- `identity/domain/model/DeviceStatus.java`;
- `identity/domain/service/DevicePolicy.java`.

Add application:

- `identity/application/port/in/EstablishSyncSessionUseCase.java`;
- `identity/application/port/in/EstablishSyncSessionCommand.java`;
- `identity/application/port/in/SyncSession.java`;
- `identity/application/port/out/AccountRepository.java`;
- `identity/application/port/out/DeviceRepository.java`;
- `identity/application/service/EstablishSyncSessionService.java`;
- `shared/application/port/out/TransactionRunner.java`.

Add persistence adapters:

- `identity/adapter/out/persistence/JdbcAccountRepository.java`;
- `identity/adapter/out/persistence/JdbcDeviceRepository.java`;
- `bootstrap/persistence/SpringTransactionRunner.java`.

Rules:

- find/create account by subject;
- email/username never become identity key;
- find/create active device per account/device ID;
- revoked stays revoked;
- concurrent create catches unique conflict, reloads boundedly, does not loop;
- current Clock supplied to service;
- no Spring annotation in domain/application.

Tests first:

- pure service tests with fake ports;
- revoked device test;
- concurrent account/device PostgreSQL integration test;
- entity/domain mapping round-trip test.

Gate:

```bash
./gradlew test --tests '*EstablishSyncSessionServiceTest' --tests '*IdentityPersistenceIT'
```

### Task 6 — Session HTTP vertical slice

Goal: hoàn thành endpoint production đầu tiên end-to-end.

Add:

- `identity/adapter/in/web/SyncHeaders.java`;
- `identity/adapter/in/web/SyncHeaderResolver.java`;
- `identity/adapter/in/web/SessionResponse.java`;
- `identity/adapter/in/web/SyncSessionController.java`;
- `identity/adapter/in/web/SyncSessionControllerIT.java`.

Flow:

```text
gateway JWT -> GatewayIdentityResolver -> validated device/protocol headers
-> EstablishSyncSessionUseCase -> PostgreSQL -> exact JSON response
```

Tests:

- exact response keys only: `account_id`, `device_id`, `expires_at`;
- same subject/device returns stable IDs;
- same subject/new device shares account;
- different subject gets different account;
- response expiry equals `upstream_exp` in UTC RFC3339;
- missing/invalid protocol/device rejected;
- revoked device `403`;
- no token/subject/profile metadata in response.

Gate:

```bash
./gradlew test --tests '*SyncSessionControllerIT'
./gradlew test
```

Checkpoint: run a local gateway-compatible signed-token smoke request before
starting replication. Direct Keycloak JWT is not the production contract.

### Task 7 — Replication wire model, digest and cursor codec

Goal: mirror Go wire contract exactly without đưa framework DTO vào domain.

Add inbound DTOs:

- `replication/adapter/in/web/PushRequest.java`;
- `replication/adapter/in/web/PushOperationRequest.java`;
- `replication/adapter/in/web/ReplicationEnvelopeRequest.java`;
- `replication/adapter/in/web/PushResponse.java`;
- `replication/adapter/in/web/ConflictResponse.java`;
- `replication/adapter/in/web/PullResponse.java`.

Add application/domain models:

- `replication/domain/model/OperationType.java`;
- `replication/domain/model/ReplicationEnvelope.java`;
- `replication/domain/model/ReplicationOperation.java`;
- `replication/domain/model/ContentDigest.java`;
- `replication/application/service/ReplicationRequestValidator.java`;
- `replication/application/service/OperationDigestService.java`;
- `replication/application/service/CursorCodec.java`.

Rules:

- Jackson `byte[]` maps Go base64 ciphertext;
- server validates routing metadata but never parses ciphertext contents;
- digest uses stable length-prefixed fields, not raw JSON property order;
- cursor exact `v1.<base64url uint64>` and canonical re-encode;
- top/envelope operation must match;
- account/device/protocol must match request identity context;
- duplicate idempotency/operation ID within batch rejected before transaction.

Tests first using fixtures generated from Go JSON:

- serialize/deserialize parity;
- base64 and size boundary;
- uint32 max/overflow;
- exact response field names;
- digest changes for every authenticated routing field/ciphertext byte;
- cursor empty/zero/round-trip/invalid prefix/base64/overflow/length.

Gate:

```bash
./gradlew test --tests '*ReplicationWireContractTest' --tests '*OperationDigestServiceTest' --tests '*CursorCodecTest'
```

### Task 8 — Pure arbitration domain

Goal: chốt state machine bằng unit test không Spring/database.

Add:

- `replication/domain/model/EntityHead.java`;
- `replication/domain/model/ArbitrationDecision.java`;
- `replication/domain/model/Accepted.java`;
- `replication/domain/model/Replayed.java`;
- `replication/domain/model/Conflicted.java`;
- `replication/domain/service/ArbitrationPolicy.java`;
- `replication/domain/exception/VersionGapException.java`;
- `replication/domain/exception/ReplayMismatchException.java`;
- `replication/domain/exception/RecordTypeMismatchException.java`.

Decision table tests:

| Existing state | Incoming | Expected |
|---|---|---|
| no head | version 1 | accepted |
| no head | version >1 | version gap |
| head N | version N+1 | accepted |
| head N | version <=N | conflict |
| head N | version >N+1 | version gap |
| existing operation ID + same key/digest | replayed |
| existing operation ID + mismatch | reject |
| reused idempotency key for another operation | reject |
| same record ID + different type | reject |

Operation type create/update/delete không thay đổi version state machine.

Gate:

```bash
./gradlew test --tests '*ArbitrationPolicyTest'
```

Review checkpoint: domain không import persistence/Jackson/Spring và không biết
SQL lock implementation.

### Task 9 — Replication persistence, locking and transaction adapters

Goal: implement atomic log/head mutation và deterministic PostgreSQL locking.

Add application ports:

- `replication/application/port/out/ReplicationLogRepository.java`;
- `replication/application/port/out/EntityHeadRepository.java`;
- `replication/application/port/out/ReplicationLock.java`;
- `replication/application/port/out/ReplicationLogPage.java`.

Add outbound adapters:

- `replication/adapter/out/persistence/JdbcReplicationLogRepository.java`;
- `replication/adapter/out/persistence/JdbcEntityHeadRepository.java`;
- `PostgresReplicationLock.java`;
- mapping tests.

Lock behavior:

- derive stable advisory-lock key from account UUID + record ID;
- acquire distinct keys sorted before processing batch;
- use `pg_advisory_xact_lock` so release follows transaction automatically;
- never use Java-local mutex as production correctness control.

Persistence integration tests:

- insert log + head update commits together;
- thrown exception rolls both back;
- operation/idempotency unique constraints map to safe application exception;
- JSONB envelope round-trip retains every wire value and ciphertext byte;
- account-scoped page query orders by sequence;
- two transaction lock test proves serialization for same entity and concurrency
  remains possible for different entities.

Gate:

```bash
./gradlew test --tests '*ReplicationPersistenceIT' --tests '*PostgresReplicationLockIT'
```

### Task 10 — Push application service and endpoint

Goal: hoàn thành production push path với atomic batch semantics.

Add:

- `replication/application/port/in/PushReplicationUseCase.java`;
- `replication/application/port/in/PushReplicationCommand.java`;
- `replication/application/port/in/PushReplicationResult.java`;
- `replication/application/service/PushReplicationService.java`;
- `replication/adapter/in/web/PushController.java`;
- `replication/adapter/in/web/PushControllerIT.java`;
- `replication/adapter/out/persistence/PushConcurrencyIT.java`.

Service order:

1. validate all operations and identity context;
2. start one transaction;
3. acquire sorted distinct entity locks;
4. process request order through arbitration policy;
5. append accepted log + advance head;
6. build replay acknowledgement/conflict metadata;
7. record safe mutation audit in transaction;
8. commit and return exact response.

Tests:

- accepted first version and fast-forward;
- mixed accepted/replayed/conflicted batch;
- exact client-compatible response;
- version gap/mismatch rolls back whole batch;
- duplicate batch keys rejected before persistence;
- cross-account/body account mismatch rejected;
- revoked device rejected;
- two concurrent device pushes for same next version: one sent, one conflict;
- concurrent distinct records both progress;
- remote/local conflict keys differ;
- ciphertext never appears in logs/audit.

Gate:

```bash
./gradlew test --tests '*PushReplicationServiceTest' --tests '*PushControllerIT' --tests '*PushConcurrencyIT'
./gradlew test
```

### Task 11 — Pull application service and endpoint

Goal: trả account-scoped append log đúng cursor, limit và response budget.

Add:

- `replication/application/port/in/PullReplicationUseCase.java`;
- `replication/application/port/in/PullReplicationQuery.java`;
- `replication/application/port/in/PullReplicationResult.java`;
- `replication/application/service/PullReplicationService.java`;
- `replication/adapter/in/web/PullController.java`;
- `replication/adapter/in/web/PullControllerIT.java`.

Implementation:

- validate identity/device/protocol/cursor/limit;
- fetch `seq > cursor` scoped account, ordered ascending;
- build page up to requested limit and 4 MiB serialized response;
- return exact account/request-device/protocol wrapper;
- retain source device inside each operation;
- next cursor = last returned seq or canonical input cursor.

Tests:

- initial empty cursor;
- empty page stable cursor;
- ordered multi-page traversal without duplicate/gap;
- requested limit and max 1000;
- invalid/tampered cursor;
- account isolation;
- revoked request device;
- source-device preservation;
- response budget with large ciphertext;
- exact Go-compatible JSON field set.

Gate:

```bash
./gradlew test --tests '*PullReplicationServiceTest' --tests '*PullControllerIT'
```

### Task 12 — Audit, observability and operational hardening

Goal: production failures diagnosable nhưng không lộ sensitive data.

Add:

- `audit/application/port/out/AuditEventSink.java`;
- `audit/domain/AuditEvent.java`;
- `audit/adapter/out/persistence/JdbcAuditEventSink.java`;
- `bootstrap/observability/HttpObservationConfiguration.java`;
- `bootstrap/observability/RedactionPolicy.java`;
- actuator health configuration.

Event vocabulary:

- `session.accepted`, `session.denied`;
- `push.completed`, `push.rejected`;
- `pull.completed`, `pull.rejected`;
- `device.denied`.

Detail only contains request ID, aggregate counts, status/reason code and safe
internal identifiers where justified. No raw request, envelope, token, email,
username, record ID metric label or ciphertext.

Tests:

- fixture token/ciphertext/secret absent from captured logs and audit JSON;
- request ID preserved/generated;
- mutation audit transaction rolls back with failed push;
- health endpoint không expose environment/config details;
- metrics labels bounded cardinality.

Gate:

```bash
./gradlew test --tests '*RedactionTest' --tests '*AuditPersistenceIT' --tests '*ObservabilityIT'
```

### Task 13 — Full security and concurrency integration matrix

Goal: review cross-task interaction, không chỉ cộng các test đơn lẻ.

Add/complete:

- `src/test/java/com/synx/devkit/e2e/SyncBackendSecurityIT.java`;
- `src/test/java/com/synx/devkit/e2e/TwoDeviceConvergenceIT.java`;
- reusable PostgreSQL 18 container fixture;
- signed gateway JWT test fixture with rotating key IDs.

Matrix:

- valid/wrong issuer/audience/signature/expiry/upstream expiry;
- account A/B session/push/pull isolation;
- active/revoked device on all endpoints;
- 4 MiB/1 MiB/1000 exact boundary and one-over boundary;
- replay same content vs same ID/different content;
- batch rollback;
- same-record concurrency and different-record concurrency;
- create/update/delete ciphertext opaque round-trip;
- pagination across note/db/ssh record types;
- database exception mapping and no stack trace leak.

Gate:

```bash
./gradlew test
./gradlew check
./gradlew bootJar
```

Whole-branch review checkpoint: trust boundary, transaction ownership,
idempotency, lock ordering, response caps, redaction and architecture dependency.

### Task 14 — Go desktop contract E2E

Goal: chứng minh backend khớp chính client production, không chỉ Java DTO test.

This task is intentionally cross-repository and must be staged separately.

Backend side:

- add a test-only gateway identity fixture endpoint/process or pre-signed token
  harness that preserves production JWT validation code;
- start backend against ephemeral PostgreSQL 18 on loopback;
- never enable unsigned/no-auth profile.

Desktop side (`/Users/syuro/Workspace/dev-kit`):

- add a contract test using `internal/sync.NewHTTPAuthenticator` and
  `internal/sync.NewHTTPTransport` against backend URL;
- cover session, push, pull, replay, stale conflict and two-device convergence;
- include multiple record types and delete tombstone;
- simulate `401/403`, `408/429/5xx` where a safe test proxy can do so;
- verify response unknown-field strictness stays green.

Gate:

```bash
# Backend
./gradlew test

# Desktop contract/core
cd /Users/syuro/Workspace/dev-kit
go test ./internal/... ./cmd/... -count=1
cd frontend && bun run build
```

Phase A không được đánh dấu hoàn tất nếu Java tests pass nhưng Go HTTP transport
contract test chưa chạy.

### Task 15 — Operations documentation and final release gate

Goal: làm rõ cách cấu hình/chạy mà không biến README thành secret runbook.

Update/add:

- `README.md`: status, docs links, local run overview;
- `docs/operations/local-development.md`;
- `docs/operations/security-configuration.md`;
- `.env.example`: chỉ placeholder/config name, không secret;
- `application.yaml`: safe defaults và profile separation.

Document:

- existing Keycloak/PostgreSQL Docker dependency;
- gateway JWT issuer/audience/JWKS requirement;
- datasource least-privilege role;
- migration on startup policy;
- health endpoint and safe troubleshooting;
- backup/restore is deferred, not implicitly solved;
- Redis is not required in Phase A.

Final verification:

```bash
./gradlew clean check bootJar
docker compose config --quiet
git diff --check
git status --short --branch
```

Also rerun the cross-repo Go contract gate from Task 14 and capture commit/status
for both repositories. Do not commit/push until explicitly requested.

## 4. Review gates by risk

| Gate | Must inspect |
|---|---|
| Architecture | dependency direction, DTO/entity leakage, package cycles |
| Identity | gateway signature/issuer/audience, upstream expiry, forged headers |
| Database | changeset order, unique/check constraints, no accidental FK/schema update |
| Push | full-batch transaction, lock order, replay mismatch, head/log atomicity |
| Pull | account scope, cursor monotonicity, response cap, source-device preservation |
| Security | token/ciphertext redaction, safe errors, bounded inputs, revoked device |
| Contract | exact JSON fields/types/status mapping against Go client |
| Release | both repo states, all gates, no secret/unrelated file staged |

## 5. Suggested checkpoints

Suggested logical commits after review, not mandatory automatic commits:

1. `build(backend): add architecture and test foundation`
2. `feat(database): add sync backend schema`
3. `feat(auth): validate gateway identity and establish device session`
4. `feat(sync): add replication wire contract and arbitration`
5. `feat(sync): persist atomic push operations`
6. `feat(sync): add account-scoped pull cursor`
7. `test(sync): add security concurrency and Go contract e2e`
8. `docs(backend): add operations and security configuration`

Never combine a failed/unreviewed migration with unrelated application changes.

## 6. Definition of done

Phase A is done only when:

- all acceptance criteria in the spec pass;
- `./gradlew clean check bootJar` passes;
- PostgreSQL 18 migrations pass from empty database;
- Go production HTTP client passes contract E2E;
- two-device conflict/convergence passes on real backend persistence;
- architecture, security and whole-branch reviews have no unresolved High/Critical;
- README/operations docs reflect actual behavior, not target behavior;
- no token, `.env`, private key, ciphertext fixture or unrelated local file is
  committed;
- user explicitly approves any commit/push/release action.
