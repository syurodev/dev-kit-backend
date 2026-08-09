# DevKit Sync Backend

Backend này cung cấp dịch vụ đồng bộ nhiều thiết bị cho ứng dụng DevKit desktop.
DevKit desktop vẫn là ứng dụng local-first; backend chỉ là đích đồng bộ tùy chọn
và không tham gia vào các workflow local khi người dùng chưa bật sync.

Repository hiện có hai Spring Boot process: sync API theo Hexagonal Architecture
ở root project và Spring Cloud Gateway Server Web MVC ở `gateway/`. Keycloak,
hai PostgreSQL database, API và Gateway có thể chạy cùng Docker Compose nhưng
vẫn dùng process, credential, volume và network boundary riêng.

## Vai trò của backend

Backend chịu trách nhiệm:

- xác thực identity JWT ngắn hạn do gateway ký sau khi gateway đã kiểm tra
  Keycloak access token, rồi ánh xạ `sub` sang account nội bộ;
- đăng ký thiết bị đầu tiên và enrollment thiết bị tiếp theo bằng token một lần;
- kiểm tra và thu hồi quyền đồng bộ của từng thiết bị;
- nhận các encrypted envelope từ desktop và lưu ciphertext như dữ liệu opaque;
- bảo đảm account isolation, device binding và giới hạn phiên bản protocol;
- xử lý idempotency và phân xử version theo từng `(account, record)`;
- cung cấp append-only replication log để client pull theo cursor;
- trả conflict cho client tự giải quyết thay vì đọc hoặc merge plaintext ở server;
- ghi audit metadata an toàn, không ghi token, secret, plaintext hay ciphertext.

Backend **không** giữ Master Password, KEK, DEK hoặc recovery key; không giải mã
vault item; không tìm kiếm nội dung plaintext; và không tự chọn bản thắng khi có
conflict. Khóa mã hóa và quyết định merge luôn thuộc về DevKit desktop.

## Trust boundary

```text
DevKit desktop
    │  Keycloak access token + device ID + encrypted envelope
    ▼
Gateway: validate external token, strip identity headers, sign identity context
    │  Gateway identity JWT + device ID + encrypted envelope
    ▼
Inbound HTTP adapter
    │  validated command/query
    ▼
Application use case ─────► PostgreSQL outbound adapter
    │                       audit outbound adapter
    ▼
Domain rules: account isolation, idempotency, version arbitration, cursor
```

JWT claim, request body và database row đều là input không đáng tin cho tới khi
được kiểm tra tại boundary tương ứng. Backend chỉ tin identity context có chữ ký
của gateway; không tin identity header do client gửi. `account_id` dùng trong
business operation phải được suy ra từ authenticated identity, không lấy từ
request body.

## Kiến trúc Hexagonal

Project sử dụng Hexagonal Architecture (Ports and Adapters), tổ chức theo
business capability trước, sau đó mới chia layer bên trong mỗi capability. Không
tổ chức toàn project thành các package ngang kiểu `controller/`, `service/`,
`repository/`, `entity/`.

### Quy tắc phụ thuộc

```text
adapter/in  ──► application/port/in ◄── application/service
                                              │
                                              ▼
                                            domain
                                              │
application/service ──► application/port/out ◄── adapter/out

bootstrap ──► composition/wiring của toàn bộ các layer
```

- `domain` chỉ chứa business model và invariant; không phụ thuộc Spring, JDBC,
  HTTP, Keycloak, Redis hoặc PostgreSQL.
- `application/port/in` định nghĩa use case mà inbound adapter được phép gọi.
- `application/port/out` định nghĩa dependency mà application cần từ hạ tầng.
- `application/service` điều phối use case, transaction boundary và domain rule;
  không biết chi tiết HTTP/JDBC.
- `adapter/in` chuyển protocol bên ngoài thành input của use case.
- `adapter/out` triển khai outbound port cho persistence, security và hạ tầng.
- `bootstrap` là composition root duy nhất được phép biết cả core và adapter.
- Inbound adapter không gọi trực tiếp outbound adapter.
- Database row và API DTO không được dùng làm domain model.

## Cấu trúc project

```text
src/
├── main/
│   ├── java/com/synx/devkit/
│   │   ├── DevKitApplication.java
│   │   ├── bootstrap/
│   │   │   ├── configuration/
│   │   │   └── security/
│   │   ├── identity/
│   │   │   ├── domain/
│   │   │   │   └── model/
│   │   │   ├── application/
│   │   │   │   ├── port/in/
│   │   │   │   ├── port/out/
│   │   │   │   └── service/
│   │   │   └── adapter/
│   │   │       ├── in/
│   │   │       │   ├── security/
│   │   │       │   └── web/
│   │   │       └── out/persistence/
│   │   ├── replication/
│   │   │   ├── domain/
│   │   │   │   ├── model/
│   │   │   │   └── service/
│   │   │   ├── application/
│   │   │   │   ├── port/in/
│   │   │   │   ├── port/out/
│   │   │   │   └── service/
│   │   │   └── adapter/
│   │   │       ├── in/web/
│   │   │       └── out/
│   │   │           └── persistence/
│   │   ├── audit/
│   │   │   ├── application/port/out/
│   │   │   └── adapter/out/
│   │   └── shared/
│   │       ├── adapter/in/web/
│   │       ├── application/port/out/
│   │       ├── domain/
│   │       └── error/
│   └── resources/
│       ├── application.yaml
│       └── db/changelog/
│           ├── db.changelog-master.yaml
│           └── changes/
└── test/
    └── java/com/synx/devkit/
        ├── architecture/
        ├── identity/
        └── replication/
```

Gateway là một deployment adapter độc lập, không phải domain layer của sync API:

```text
gateway/src/main/java/com/synx/devkit/gateway/
├── configuration/   # typed Keycloak và internal-token settings
├── security/        # external JWT validation, header sanitizing, token signing
└── web/             # public JWKS endpoint
```

Tên package có thể được bổ sung khi xuất hiện capability thật, nhưng chiều phụ
thuộc không thay đổi. `shared` phải luôn nhỏ; business rule thuộc capability nào
thì nằm trong capability đó, không đưa vào `shared` chỉ để tái sử dụng sớm.

### Trách nhiệm theo capability

| Capability | Trách nhiệm |
|---|---|
| `identity` | Authenticated account, device registration, session và revocation |
| `replication` | Push, pull, encrypted envelope, entity head, arbitration, conflict và cursor |
| `audit` | Ghi security/audit metadata đã redact qua outbound port |
| `bootstrap` | Spring configuration, security chain, bean wiring và application startup |

## Persistence

PostgreSQL lưu account/device metadata, append-only replication log, entity head
và audit event. Ciphertext trong envelope là opaque đối với backend.

Persistence adapter dùng Spring JDBC với SQL tường minh. Cách này giữ advisory
lock, JSONB, append sequence và account scope dễ review mà không cần JPA entity
trung gian.

Liquibase migration phải forward-only và được include theo thứ tự từ
`db.changelog-master.yaml`. Business code không tự tạo hoặc sửa schema. Mọi
constraint quan trọng cho idempotency và concurrency phải được bảo vệ ở cả
application layer lẫn database bằng unique constraint/index phù hợp.

Transaction boundary thuộc application use case. Push arbitration của một
record phải chạy atomically; không được advance entity head nếu replication log
chưa được ghi thành công.

## Security invariants

- Chỉ chấp nhận gateway identity JWT đã được Spring Security kiểm tra chữ ký,
  issuer, audience và expiry. Gateway chịu trách nhiệm validate Keycloak token.
- Mọi query phải scope theo account lấy từ identity context.
- Device bị revoke hoặc protocol không tương thích phải bị từ chối trước khi
  đọc/ghi replication data.
- Không log access token, credential, plaintext, encryption key hoặc ciphertext.
- Không deserialize ciphertext thành business object phía server.
- Request size, batch size, ciphertext size và cursor length phải có giới hạn.
- Device mới sau bootstrap phải có enrollment token do device active tạo; token
  được hash, ràng buộc target device, hết hạn và chỉ dùng một lần.
- Gateway phải giới hạn request theo IP/subject và concurrency; PostgreSQL phải
  áp quota lưu trữ atomically trước khi append operation mới.
- Replay phải idempotent; entity version không được rollback.
- Error trả ra API không chứa stack trace hoặc chi tiết hạ tầng nhạy cảm.

## Testing strategy

- **Domain unit test:** arbitration, version gap, replay và conflict không cần
  Spring context.
- **Application test:** use case với fake outbound ports, kiểm tra orchestration
  và transaction semantics.
- **Adapter test:** HTTP contract, JWT mapping, JDBC mapping và Liquibase migration.
- **Integration test:** PostgreSQL/Keycloak-compatible auth bằng container hoặc
  fixture cô lập.
- **Contract E2E:** chạy chính sync HTTP client của DevKit desktop với backend
  thật để kiểm tra session, push, pull, cursor, conflict và account isolation.
- **Architecture test:** khóa dependency rule của Hexagonal Architecture để
  domain/application không import adapter hoặc framework ngoài ý muốn.

## Trạng thái hiện tại

Phase A backend đã implement `session`, device enrollment, `push`, `pull`; gateway JWT
validation; account/device registration; Liquibase schema; atomic arbitration;
account-scoped cursor; safe audit; request/response limits; PostgreSQL 18
integration test và contract E2E bằng production Go HTTP transport.

Gateway MVC hiện đã có trong `gateway/`: validate chữ ký/issuer/audience/expiry
của Keycloak access token, bắt buộc stable `sub`, loại identity/proxy header do
client gửi, ký JWT nội bộ RS256 tối đa 45 giây và publish public JWKS cho backend.
Compose giữ API trên private network; các development port chỉ bind loopback. Realm import
`devkit` cấu hình desktop public client dùng Authorization Code + PKCE S256 và
thêm audience `devkit-sync-gateway`. Backend không có chế độ bỏ qua auth.
Image Keycloak local overlay các dependency runtime đã vá bằng checksum cố định;
Admin CLI và SQL Server driver không dùng được loại khỏi image.
Image PostgreSQL local áp Alpine security updates và rebuild `gosu` bằng Go
toolchain đã vá thay vì dùng binary cũ từ base image.

## Chạy local

1. Tạo `.env` từ `.env.example` và chạy
   `./scripts/generate-gateway-keypair.sh` một lần cho local development.
2. Khởi động stack: `docker compose up -d --build`.
   Nếu realm đã tồn tại, chạy `./scripts/apply-keycloak-security.sh` để áp policy
   mới mà không xóa volume.
3. Kiểm tra Gateway: `GET http://127.0.0.1:8082/actuator/health` và Keycloak
   discovery tại `http://127.0.0.1:8081/realms/devkit/.well-known/openid-configuration`.
4. Khi chạy process ngoài Docker, dùng `./gradlew bootRun` cho API và
   `./gradlew :gateway:bootRun` cho Gateway với environment tương ứng.

Chi tiết cấu hình và troubleshooting:

- [Phase A specification](docs/specs/2026-08-09-sync-backend-phase-a.md)
- [Implementation plan và execution status](docs/plans/2026-08-09-sync-backend-phase-a-implementation.md)
- [Local development](docs/operations/local-development.md)
- [Security configuration](docs/operations/security-configuration.md)
