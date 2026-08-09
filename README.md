# DevKit Sync Backend

Backend này cung cấp dịch vụ đồng bộ nhiều thiết bị cho ứng dụng DevKit desktop.
DevKit desktop vẫn là ứng dụng local-first; backend chỉ là đích đồng bộ tùy chọn
và không tham gia vào các workflow local khi người dùng chưa bật sync.

Repository hiện là một ứng dụng Java/Spring Boot. Keycloak và PostgreSQL được
chạy bằng Docker như các dependency bên ngoài; application kết nối tới các dịch
vụ đó qua cấu hình runtime, không khởi tạo hay quản lý vòng đời của chúng trong
business code.

## Vai trò của backend

Backend chịu trách nhiệm:

- xác thực access token do Keycloak phát hành và ánh xạ `sub` sang account nội bộ;
- đăng ký, kiểm tra và thu hồi quyền đồng bộ của từng thiết bị;
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
    │  Bearer token + device ID + encrypted envelope
    ▼
Inbound HTTP adapter
    │  validated command/query
    ▼
Application use case ─────► Keycloak/JWT outbound adapter
    │                       PostgreSQL outbound adapter
    │                       audit/observability outbound adapter
    ▼
Domain rules: account isolation, idempotency, version arbitration, cursor
```

JWT claim, request body và database row đều là input không đáng tin cho tới khi
được kiểm tra tại boundary tương ứng. `account_id` dùng trong business operation
phải được suy ra từ authenticated identity; không tin giá trị do client gửi trong
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

- `domain` chỉ chứa business model và invariant; không phụ thuộc Spring, JPA,
  HTTP, Keycloak, Redis hoặc PostgreSQL.
- `application/port/in` định nghĩa use case mà inbound adapter được phép gọi.
- `application/port/out` định nghĩa dependency mà application cần từ hạ tầng.
- `application/service` điều phối use case, transaction boundary và domain rule;
  không biết chi tiết HTTP/JPA.
- `adapter/in` chuyển protocol bên ngoài thành input của use case.
- `adapter/out` triển khai outbound port cho persistence, security và hạ tầng.
- `bootstrap` là composition root duy nhất được phép biết cả core và adapter.
- Inbound adapter không gọi trực tiếp outbound adapter.
- JPA entity và API DTO không được dùng làm domain model.

## Cấu trúc project mục tiêu

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
│   │   │       ├── in/web/
│   │   │       └── out/
│   │   │           ├── persistence/
│   │   │           └── keycloak/
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
│   │   │           ├── persistence/
│   │   │           └── locking/
│   │   ├── audit/
│   │   │   ├── application/port/out/
│   │   │   └── adapter/out/
│   │   └── shared/
│   │       ├── error/
│   │       └── validation/
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

Tên package có thể được bổ sung khi xuất hiện capability thật, nhưng chiều phụ
thuộc không thay đổi. `shared` phải luôn nhỏ; business rule thuộc capability nào
thì nằm trong capability đó, không đưa vào `shared` chỉ để tái sử dụng sớm.

### Trách nhiệm dự kiến theo capability

| Capability | Trách nhiệm |
|---|---|
| `identity` | Authenticated account, device registration, session và revocation |
| `replication` | Push, pull, encrypted envelope, entity head, arbitration, conflict và cursor |
| `audit` | Ghi security/audit metadata đã redact qua outbound port |
| `bootstrap` | Spring configuration, security chain, bean wiring và application startup |

## Persistence

PostgreSQL lưu account/device metadata, append-only replication log, entity head
và audit event. Ciphertext trong envelope là opaque đối với backend.

Liquibase migration phải forward-only và được include theo thứ tự từ
`db.changelog-master.yaml`. Business code không tự tạo hoặc sửa schema. Mọi
constraint quan trọng cho idempotency và concurrency phải được bảo vệ ở cả
application layer lẫn database bằng unique constraint/index phù hợp.

Transaction boundary thuộc application use case. Push arbitration của một
record phải chạy atomically; không được advance entity head nếu replication log
chưa được ghi thành công.

## Security invariants

- Chỉ chấp nhận authenticated identity đã được Spring Security xác thực từ
  Keycloak JWT.
- Mọi query phải scope theo account lấy từ identity context.
- Device bị revoke hoặc protocol không tương thích phải bị từ chối trước khi
  đọc/ghi replication data.
- Không log access token, credential, plaintext, encryption key hoặc ciphertext.
- Không deserialize ciphertext thành business object phía server.
- Request size, batch size, ciphertext size và cursor length phải có giới hạn.
- Replay phải idempotent; entity version không được rollback.
- Error trả ra API không chứa stack trace hoặc chi tiết hạ tầng nhạy cảm.

## Testing strategy

- **Domain unit test:** arbitration, version gap, replay và conflict không cần
  Spring context.
- **Application test:** use case với fake outbound ports, kiểm tra orchestration
  và transaction semantics.
- **Adapter test:** HTTP contract, JWT mapping, JPA mapping và Liquibase migration.
- **Integration test:** PostgreSQL/Keycloak-compatible auth bằng container hoặc
  fixture cô lập.
- **Contract E2E:** chạy chính sync HTTP client của DevKit desktop với backend
  thật để kiểm tra session, push, pull, cursor, conflict và account isolation.
- **Architecture test:** khóa dependency rule của Hexagonal Architecture để
  domain/application không import adapter hoặc framework ngoài ý muốn.

## Trạng thái hiện tại

Repository đang ở giai đoạn bootstrap Spring Boot. Keycloak và PostgreSQL đã có
thể chạy bằng Docker, nhưng session/push/pull, persistence model, arbitration và
contract E2E chưa được xem là implemented cho tới khi có code cùng test evidence
tương ứng.

Hạng mục triển khai đầu tiên nên là một vertical slice nhỏ cho authenticated
`GET /v1/sync/session`: JWT identity → account/device use case → PostgreSQL
adapter → HTTP response. Slice này thiết lập đúng dependency direction trước khi
mở rộng sang push/pull và arbitration.
